/*   
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shellware.CarHome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.shellware.CarHome.MusicRetriever.Item;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Service that handles media playback. This is the Service through which we perform all the media
 * handling in our application. Upon initialization, it starts a {@link MediaRetriever} to scan
 * the user's media. Then, it waits for Intents (which come from our main activity,
 * {@link MainActivity}, which signal the service to perform specific operations: Play, Pause,
 * Rewind, Skip, etc.
 */
public class MusicService extends Service implements OnCompletionListener, OnPreparedListener,
                OnErrorListener, MusicFocusable,
                PrepareMusicRetrieverTask.MusicRetrieverPreparedListener {

    private NotificationManager mNotificationManager;

    // our media player
    private static MediaPlayer mPlayer = null;

    // our AudioFocusHelper object, if it's available (it's available on SDK level >= 8)
    // If not available, this will be null. Always check for null before using!
    private AudioFocusHelper mAudioFocusHelper = null;

    // indicates the state our service:
    public enum State {
        Retrieving, // the MediaRetriever is retrieving music
        Stopped,    // media player is stopped and not prepared to play
        Preparing,  // media player is preparing...
        Playing,    // playback active (media player ready!). (but the media player may actually be
                    // paused in this state if we don't have audio focus. But we stay in this state
                    // so that we know we have to resume playback once we get focus back)
        Paused      // playback paused (media player ready!)
    };

    public static State mState = State.Retrieving;

    // if in Retrieving mode, this flag indicates whether we should start playing immediately
    // when we are ready or not.
    private boolean mStartPlayingAfterRetrieve = false;

    // if mStartPlayingAfterRetrieve is true, this variable indicates the URL that we should
    // start playing when we are ready. If null, we should play a random song from the device
    private Uri mWhatToPlayAfterRetrieve = null;

    private enum PauseReason {
        UserRequest,  // paused by user request
        FocusLoss,    // paused because of audio focus loss
    };

    // why did we pause? (only relevant if mState == State.Paused)
    private PauseReason mPauseReason = PauseReason.UserRequest;

    // do we have audio focus?
    private enum AudioFocus {
        NoFocusNoDuck,    // we don't have audio focus, and can't duck
        NoFocusCanDuck,   // we don't have focus, but can play at a low volume ("ducking")
        Focused           // we have full audio focus
    }
    private AudioFocus mAudioFocus = AudioFocus.NoFocusNoDuck;

    // the items (songs) we have queried
    List<Item> mHistory = new ArrayList<Item>();
    
    public static int duration = 0;
    public static int progress = 0;
    
    // title of the song we are currently playing
    private String mSongTitle = "";
    
    // artist of the song we are currently playing
    private String mArtistName = "";

    // whether the song we are playing is streaming from the network
    private boolean mIsStreaming = false;

    // Wifi lock that we hold when streaming files from the internet, in order to prevent the
    // device from shutting off the Wifi radio
    private WifiLock mWifiLock;

    // The tag we put on debug messages
    private final static String TAG = "MusicService";

    // These are the Intent actions that we are prepared to handle. Notice that the fact these
    // constants exist in our class is a mere convenience: what really defines the actions our
    // service can handle are the <action> tags in the <intent-filters> tag for our service in
    // AndroidManifest.xml.
    public static final String ACTION_PLAY = "com.shellware.CarHome.action.PLAY";
    public static final String ACTION_PAUSE = "com.shellware.CarHome.action.PAUSE";
    public static final String ACTION_STOP = "com.shellware.CarHome.action.STOP";
    public static final String ACTION_SKIP = "com.shellware.CarHome.action.SKIP";
    public static final String ACTION_REWIND = "com.shellware.CarHome.action.REWIND";
    public static final String ACTION_URL = "com.shellware.CarHome.action.URL";

    // The volume we set the media player to when we lose audio focus, but are allowed to reduce
    // the volume instead of stopping playback.
    public final float DUCK_VOLUME = 0.1f;

    // The ID we use for the notification (the onscreen alert that appears at the notification
    // area at the top of the screen as an icon -- and as text as well if the user expands the
    // notification area).
    private final int NOTIFICATION_ID = 1;

    // Our instance of our MusicRetriever, which handles scanning for media and
    // providing titles and URIs as we need.
    private MusicRetriever mRetriever;

    private Notification mNotification = null;

    /**
     * Makes sure the media player exists and has been reset. This will create the media player
     * if needed, or reset the existing media player if one already exists.
     */
    private void createMediaPlayerIfNeeded() {
        if (mPlayer == null) {
            mPlayer = new MediaPlayer();

            // Make sure the media player will acquire a wake-lock while playing. If we don't do
            // that, the CPU might go to sleep while the song is playing, causing playback to stop.
            //
            // Remember that to use this, we have to declare the android.permission.WAKE_LOCK
            // permission in AndroidManifest.xml.
            mPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

            // we want the media player to notify us when it's ready preparing, and when it's done
            // playing:
            mPlayer.setOnPreparedListener(this);
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
        }
        else
            mPlayer.reset();
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "debug: Creating service");

        // Create the Wifi lock (this does not acquire the lock, this just creates it)
        mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                        .createWifiLock(WifiManager.WIFI_MODE_FULL, "mylock");

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Create the retriever and start an asynchronous task that will prepare it.
        mRetriever = new MusicRetriever(getContentResolver());
        (new PrepareMusicRetrieverTask(mRetriever,this)).execute();

        // create the Audio Focus Helper, if the Audio Focus feature is available (SDK 8 or above)
        if (android.os.Build.VERSION.SDK_INT >= 8)
            mAudioFocusHelper = new AudioFocusHelper(getApplicationContext(), this);
        else
            mAudioFocus = AudioFocus.Focused; // no focus feature, so we always "have" audio focus
    }

    /**
     * Called when we receive an Intent. When we receive an intent sent to us via startService(),
     * this is the method that gets called. So here we react appropriately depending on the
     * Intent's action, which specifies what is being requested of us.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        
        Log.d(TAG, "intent received " + action);
        
        if (action.equals(ACTION_PLAY)) processPlayRequest();
        else if (action.equals(ACTION_PAUSE)) processPauseRequest();
        else if (action.equals(ACTION_SKIP)) processSkipRequest();
        else if (action.equals(ACTION_STOP)) processStopRequest();
        else if (action.equals(ACTION_REWIND)) processRewindRequest();
        else if (action.equals(ACTION_URL)) processAddRequest(intent);

        return START_NOT_STICKY; // Means we started the service, but don't want it to
                                 // restart in case it's killed.
    }

    void processPlayRequest() {
        if (mState == State.Retrieving) {
            // If we are still retrieving media, just set the flag to start playing when we're
            // ready
            mWhatToPlayAfterRetrieve = null; // play a random song
            mStartPlayingAfterRetrieve = true;
            return;
        }

        tryToGetAudioFocus();

        if (mState == State.Stopped) {
            // If we're stopped, just go ahead to the next song and start playing
            playNextSong(null);
        }
        else if (mState == State.Paused) {
            // If we're paused, just continue playback and restore the 'foreground service' state.
            mState = State.Playing;
            setUpAsForeground(mArtistName + " - " + mSongTitle);
            configAndStartMediaPlayer();
        }
    }

    void processPauseRequest() {
        if (mState == State.Retrieving) {
            // If we are still retrieving media, clear the flag that indicates we should start
            // playing when we're ready
            mStartPlayingAfterRetrieve = false;
            return;
        }

        if (mState == State.Playing) {
            // Pause media player and cancel the 'foreground service' state.
            mState = State.Paused;
            mPlayer.pause();
            relaxResources(false); // while paused, we always retain the MediaPlayer
            giveUpAudioFocus();
        }
    }

    void processRewindRequest() {
        if (mState == State.Playing || mState == State.Paused) {
        	if (mPlayer.getCurrentPosition() > 1000) {
        		mPlayer.seekTo(0);
        	} else {
                try {
                	mHistory.remove(mHistory.size() - 1);
                	
                	if (mHistory.size() == 0) {
                		return;
                	}
            		
                    // set the source of the media player a a content URI
                    createMediaPlayerIfNeeded();
                    mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                	 
					mPlayer.setDataSource(getApplicationContext(), mHistory.get(mHistory.size() - 1).getURI());
	                mSongTitle = mHistory.get(mHistory.size() - 1).getTitle();
	                mArtistName = mHistory.get(mHistory.size() - 1).getArtist();
	                duration = mHistory.get(mHistory.size() - 1).getDuration();
		            mState = State.Preparing;
		            setUpAsForeground(mArtistName + " - " + mSongTitle);
		
		            // starts preparing the media player in the background. When it's done, it will call
		            // our OnPreparedListener (that is, the onPrepared() method on this class, since we set
		            // the listener to 'this').
		            //
		            // Until the media player is prepared, we *cannot* call start() on it!
		            mPlayer.prepareAsync();		
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        }
    }

    void processSkipRequest() {
    	if (mState == State.Stopped || mState == State.Retrieving) {
    		processPlayRequest();
    		return;
    	}
        if (mState == State.Playing || mState == State.Paused) {    	
            tryToGetAudioFocus();
            playNextSong(null);
        }
    }

    void processStopRequest() {
        if (mState == State.Playing || mState == State.Paused) {
            mState = State.Stopped;

            // let go of all resources...
            relaxResources(true);
            giveUpAudioFocus();

            // service is no longer necessary. Will be started again if needed.
            //stopSelf();
        }
    }

    /**
     * Releases resources used by the service for playback. This includes the "foreground service"
     * status and notification, the wake locks and possibly the MediaPlayer.
     *
     * @param releaseMediaPlayer Indicates whether the Media Player should also be released or not
     */
    void relaxResources(boolean releaseMediaPlayer) {
        // stop being a foreground service
        stopForeground(true);

        // stop and release the Media Player, if it's available
        if (releaseMediaPlayer && mPlayer != null) {
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }

        // we can also release the Wifi lock, if we're holding it
        if (mWifiLock.isHeld()) mWifiLock.release();
    }

    void giveUpAudioFocus() {
        if (mAudioFocus == AudioFocus.Focused && mAudioFocusHelper != null
                                && mAudioFocusHelper.abandonFocus())
            mAudioFocus = AudioFocus.NoFocusNoDuck;
    }

    /**
     * Reconfigures MediaPlayer according to audio focus settings and starts/restarts it. This
     * method starts/restarts the MediaPlayer respecting the current audio focus state. So if
     * we have focus, it will play normally; if we don't have focus, it will either leave the
     * MediaPlayer paused or set it to a low volume, depending on what is allowed by the
     * current focus settings. This method assumes mPlayer != null, so if you are calling it,
     * you have to do so from a context where you are sure this is the case.
     */
    void configAndStartMediaPlayer() {
        if (mAudioFocus == AudioFocus.NoFocusNoDuck) {
            // If we don't have audio focus and can't duck, we have to pause, even if mState
            // is State.Playing. But we stay in the Playing state so that we know we have to resume
            // playback once we get the focus back.
            if (mPlayer.isPlaying()) mPlayer.pause();
            return;
        }
        else if (mAudioFocus == AudioFocus.NoFocusCanDuck)
            mPlayer.setVolume(DUCK_VOLUME, DUCK_VOLUME);  // we'll be relatively quiet
        else
            mPlayer.setVolume(1.0f, 1.0f); // we can be loud

        if (!mPlayer.isPlaying()) mPlayer.start();
    }

    void processAddRequest(Intent intent) {
        // user wants to play a song directly by URL or path. The URL or path comes in the "data"
        // part of the Intent. This Intent is sent by {@link MainActivity} after the user
        // specifies the URL/path via an alert box.
        if (mState == State.Retrieving) {
            // we'll play the requested URL right after we finish retrieving
            mWhatToPlayAfterRetrieve = intent.getData();
            mStartPlayingAfterRetrieve = true;
        }
        else if (mState == State.Playing || mState == State.Paused || mState == State.Stopped) {
            Log.i(TAG, "Playing from URL/path: " + intent.getData().toString());
            tryToGetAudioFocus();
            playNextSong(intent.getData().toString());
        }
    }

    /**
     * Shortcut to making and displaying a toast. Seemed cleaner than repeating
     * this code everywhere, at least for this sample.
     */
    void say(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    void tryToGetAudioFocus() {
        if (mAudioFocus != AudioFocus.Focused && mAudioFocusHelper != null
                        && mAudioFocusHelper.requestFocus())
            mAudioFocus = AudioFocus.Focused;
    }

    /**
     * Starts playing the next song. If manualUrl is null, the next song will be randomly selected
     * from our Media Retriever (that is, it will be a random song in the user's device). If
     * manualUrl is non-null, then it specifies the URL or path to the song that will be played
     * next.
     */
    void playNextSong(String manualUrl) {
        mState = State.Stopped;
        relaxResources(false); // release everything except MediaPlayer

        try {
            if (manualUrl != null) {
                // set the source of the media player to a manual URL or path
                createMediaPlayerIfNeeded();
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mPlayer.setDataSource(manualUrl);
                mSongTitle = manualUrl;
                mArtistName = "N/A";
                mIsStreaming = manualUrl.startsWith("http:") || manualUrl.startsWith("https:");
            }
            else {
                mIsStreaming = false; // playing a locally available song

                MusicRetriever.Item item = mRetriever.getRandomItem();
                if (item == null) {
                    say("No song to play :-(");
                    return;
                }

                mHistory.add(item);

                // set the source of the media player a a content URI
                createMediaPlayerIfNeeded();
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mPlayer.setDataSource(getApplicationContext(), item.getURI());
                mSongTitle = item.getTitle();
                mArtistName = item.getArtist();
                duration = item.getDuration();
            }


            mState = State.Preparing;
            setUpAsForeground(mArtistName + " - " + mSongTitle);

            // starts preparing the media player in the background. When it's done, it will call
            // our OnPreparedListener (that is, the onPrepared() method on this class, since we set
            // the listener to 'this').
            //
            // Until the media player is prepared, we *cannot* call start() on it!
            mPlayer.prepareAsync();

            // If we are streaming from the internet, we want to hold a Wifi lock, which prevents
            // the Wifi radio from going to sleep while the song is playing. If, on the other hand,
            // we are *not* streaming, we want to release the lock if we were holding it before.
            if (mIsStreaming) mWifiLock.acquire();
            else if (mWifiLock.isHeld()) mWifiLock.release();
        }
        catch (IOException ex) {
            Log.e("MusicService", "IOException playing next song: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /** Called when media player is done playing current song. */
    public void onCompletion(MediaPlayer player) {
        // The media player finished playing the current song, so we go ahead and start the next.
        playNextSong(null);
    }

    /** Called when media player is done preparing. */
    public void onPrepared(MediaPlayer player) {
        // The media player is done preparing. That means we can start playing!
        mState = State.Playing;
        updateNotification(mArtistName + " - " + mSongTitle);
        configAndStartMediaPlayer();
    }

    /** Updates the notification. */
    void updateNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), CarHomeActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        mNotification.setLatestEventInfo(getApplicationContext(), getString(R.string.app_name), text, pi);
        mNotificationManager.notify(NOTIFICATION_ID, mNotification);
    }

    /**
     * Configures service as a foreground service. A foreground service is a service that's doing
     * something the user is actively aware of (such as playing music), and must appear to the
     * user as a notification. That's why we create the notification here.
     */
    void setUpAsForeground(String text) {
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), CarHomeActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        mNotification = new Notification();
        mNotification.tickerText = text;
        mNotification.icon = R.drawable.rx8logo;
        mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
        mNotification.setLatestEventInfo(getApplicationContext(), getString(R.string.app_name), text, pi);
        startForeground(NOTIFICATION_ID, mNotification);
    }

    /**
     * Called when there's an error playing media. When this happens, the media player goes to
     * the Error state. We warn the user about the error and reset the media player.
     */
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(getApplicationContext(), "Media player error! Resetting.",
            Toast.LENGTH_SHORT).show();
        Log.e(TAG, "Error: what=" + String.valueOf(what) + ", extra=" + String.valueOf(extra));

        mState = State.Stopped;
        relaxResources(true);
        giveUpAudioFocus();
        return true; // true indicates we handled the error
    }

    public void onGainedAudioFocus() {
        Toast.makeText(getApplicationContext(), "gained audio focus.", Toast.LENGTH_SHORT).show();
        mAudioFocus = AudioFocus.Focused;

        // restart media player with new focus settings
        if (mState == State.Playing)
            configAndStartMediaPlayer();
    }

    public void onLostAudioFocus(boolean canDuck) {
        Toast.makeText(getApplicationContext(), "lost audio focus." + (canDuck ? "can duck" :
            "no duck"), Toast.LENGTH_SHORT).show();
        mAudioFocus = canDuck ? AudioFocus.NoFocusCanDuck : AudioFocus.NoFocusNoDuck;

        // start/restart/pause media player with new focus settings
        if (mPlayer != null && mPlayer.isPlaying())
            configAndStartMediaPlayer();
    }

    public void onMusicRetrieverPrepared() {
        // Done retrieving!
        mState = State.Stopped;

        // If the flag indicates we should start playing after retrieving, let's do that now.
        if (mStartPlayingAfterRetrieve) {
            tryToGetAudioFocus();
            playNextSong(mWhatToPlayAfterRetrieve == null ?
                    null : mWhatToPlayAfterRetrieve.toString());
        }
    }


    @Override
    public void onDestroy() {
        // Service is being killed, so make sure we release our resources
        mState = State.Stopped;
        relaxResources(true);
        giveUpAudioFocus();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
    
    public static int getPosition() {
    	return mState == State.Paused || mState == State.Playing ?  mPlayer.getCurrentPosition() : 0;
    }
    public static void setPosition(int position) {
    	if (mState == State.Paused || mState == State.Playing) {
    		mPlayer.seekTo(position);
    	}
    }
}
