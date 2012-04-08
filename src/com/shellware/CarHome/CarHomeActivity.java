/*
 *   Copyright 2011-2012 Shell M. Shrader
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.shellware.CarHome;

import android.app.Activity;
import android.app.UiModeManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class CarHomeActivity extends Activity implements OnClickListener {

	private SharedPreferences prefs;
	private Resources res;

	private AudioManager mAudioManager;
	
	RemoteControlReceiver mMediaButtonReceiver;
	IntentFilter mediaFilter;

    private Handler handler;

    private Button mPlayButton;
    private Button mStopButton;
    private Button mForwardButton;
    private Button mRewindButton;
    private Button mMuteButton;
    
    private SeekBar mPositionBar;
    private ImageView mArtworkImage;

    private boolean mMuted = false;
    
	private Menu myMenu = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		res = getResources();
		
		handler = new Handler();

        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        
    	mMediaButtonReceiver = new RemoteControlReceiver();
    	mediaFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
    	mediaFilter.setPriority(Integer.MAX_VALUE);

    	registerReceiver(mMediaButtonReceiver, mediaFilter);

        mPlayButton = (Button) findViewById(R.id.playbutton);
        mPlayButton.setOnClickListener(this);
        
        mStopButton = (Button) findViewById(R.id.stopbutton);
        mStopButton.setOnClickListener(this);
        
        mForwardButton = (Button) findViewById(R.id.forwardbutton);
        mForwardButton.setOnClickListener(this);
        
        mRewindButton = (Button) findViewById(R.id.rewindbutton);
        mRewindButton.setOnClickListener(this);
        
        mMuteButton = (Button) findViewById(R.id.mutebutton);
        mMuteButton.setOnClickListener(this);
        
        mPositionBar = (SeekBar) findViewById(R.id.position);
        mPositionBar.setMax(1);
        mPositionBar.setProgress(0);
        mPositionBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			public void onProgressChanged(SeekBar arg0, int position, boolean fromTouch) {
				if (fromTouch) MusicService.setPosition(position);	
			}

			public void onStartTrackingTouch(SeekBar arg0) {	
			}

			public void onStopTrackingTouch(SeekBar arg0) {		
			}   	
        });
        
        mArtworkImage = (ImageView) findViewById(R.id.artwork);
        
        this.registerReceiver(this.myBatteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        
//        ChangeLog cl = new ChangeLog(this);
//        if (cl.firstRun()) {
//            cl.getLogDialog().show();
//        }
	}
	
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
            return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
            return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(oneSecondHandler);
    }
    
    private Runnable oneSecondHandler = new Runnable() {

		public void run() {

			if (MusicService.getPosition() != 0) {
				mPositionBar.setMax(MusicService.getCurrentItem().getDuration());
				mPositionBar.setProgress(MusicService.getPosition());
			} else {
				mPositionBar.setMax(1);
				mPositionBar.setProgress(0);
			}
			
			if (MusicService.mState == MusicService.State.Playing) {
				mPlayButton.setBackgroundResource(R.drawable.pauseicon);
				mArtworkImage.setImageBitmap(MusicService.getCurrentItem().getAlbumArt());
			} else {
				mPlayButton.setBackgroundResource(R.drawable.playicon);
			}
			
			handler.postDelayed(this, 1000);
		} 	
    };
    
    @Override
    public void onPause() {
    	super.onPause();
    	handler.removeCallbacks(oneSecondHandler);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        UiModeManager manager = (UiModeManager)getSystemService(Context.UI_MODE_SERVICE);    
        manager.disableCarMode(UiModeManager.DISABLE_CAR_MODE_GO_HOME);

        unregisterReceiver(mMediaButtonReceiver);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		myMenu = menu;

		BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();    
	    if (bt != null && bt.isEnabled()) {
	        myMenu.getItem(0).setIcon(R.drawable.bluetooth_on);
	    } else { 
	    	myMenu.getItem(0).setIcon(R.drawable.bluetooth_off);
	    }

	    return super.onPrepareOptionsMenu(menu);
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		
        // Handle item selection
        switch (item.getItemId()) {
	        case android.R.id.home:
//	          	ChangeLog cl = new ChangeLog(this);
//	        	cl.getFullLogDialog().show();
	        	return true;
	        	
        	case R.id.BTMenuItem:
        	    BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();    
        	    if (bt != null && !bt.isEnabled()) {
        	    	try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
        	    	bt.enable();
        	    	item.setIcon(R.drawable.bluetooth_on);
        	    } else { 
        	        if (bt != null) bt.disable(); 
        	        item.setIcon(R.drawable.bluetooth_off);
        	    } 
        		return true;
        		
        	case R.id.quitMenuItem:
	           	System.exit(0);
	           	return true;
	           	
        	default:
                return super.onOptionsItemSelected(item);
        }
		
		
    }

	public void onClick(View target) {
		
        if (target == mPlayButton) {
        	if (MusicService.mState == MusicService.State.Paused) {
        		startService(new Intent(MusicService.ACTION_PLAY));
        	} else {
        		if (MusicService.mState == MusicService.State.Playing) {
        		startService(new Intent(MusicService.ACTION_PAUSE));
        		} else {
        			startService(new Intent(MusicService.ACTION_SKIP));
        		}
            }
        }

        if (target == mStopButton) {
            startService(new Intent(MusicService.ACTION_STOP));
        }
        
        if (target == mForwardButton) {
        	startService(new Intent(MusicService.ACTION_SKIP));
        }
        
        if (target == mRewindButton) {
        	startService(new Intent(MusicService.ACTION_REWIND));
        }

        if (target == mMuteButton) {
        	mMuted = ! mMuted;        	
        	mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, mMuted);
        	if (mMuted) {
        		mMuteButton.setBackgroundResource(R.drawable.unmuteicon);
        	} else {
        		mMuteButton.setBackgroundResource(R.drawable.muteicon);
        	}
        }
	}    
	
	private BroadcastReceiver myBatteryReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context arg0, Intent arg1) {

			if (arg1.getAction().equals(Intent.ACTION_BATTERY_CHANGED)){

				final int status = arg1.getIntExtra("status", BatteryManager.BATTERY_STATUS_UNKNOWN);
        	    BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();    
				
				if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
						status == BatteryManager.BATTERY_STATUS_FULL) {
					
	        	    if (bt != null && !bt.isEnabled()) {
	        	    	bt.enable();
//	        	    	myMenu.getItem(0).setIcon(R.drawable.bluetooth_on);
	        	    } 
				} else {
	        	    if (bt != null && bt.isEnabled()) {
	        	    	bt.disable();
//	        	    	myMenu.getItem(0).setIcon(R.drawable.bluetooth_off);
	        	    } 					
				}
			}
		}
	};
	
}