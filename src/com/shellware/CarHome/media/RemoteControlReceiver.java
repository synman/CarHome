package com.shellware.CarHome.media;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
 
public class RemoteControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
            Toast.makeText(context, "Headphones disconnected.", Toast.LENGTH_SHORT).show();

            // send an intent to our MusicService to telling it to pause the audio
            context.startService(new Intent(MusicService.ACTION_PAUSE));
        } else {
        	if (intent.getAction().equals(Intent.ACTION_MEDIA_BUTTON)) {
	            KeyEvent keyEvent = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
	            
	            if (keyEvent.getAction() != KeyEvent.ACTION_DOWN)
	                return;
	
	            switch (keyEvent.getKeyCode()) {
	                case KeyEvent.KEYCODE_HEADSETHOOK:
	                    Toast.makeText(context, "Headset Hook", Toast.LENGTH_SHORT).show();
	                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
	                    Toast.makeText(context, "Play/Pause Pressed", Toast.LENGTH_SHORT).show();
	            //        context.startService(new Intent(MusicService.ACTION_TOGGLE_PLAYBACK));
	                    break;
	                case KeyEvent.KEYCODE_MEDIA_PLAY:
//	                    Toast.makeText(context, "Play Pressed", Toast.LENGTH_SHORT).show();
	                    context.startService(new Intent(MusicService.ACTION_PLAY));
	                    break;
	                case KeyEvent.KEYCODE_MEDIA_PAUSE:
//	                    Toast.makeText(context, "Pause Pressed", Toast.LENGTH_SHORT).show();
	                    context.startService(new Intent(MusicService.ACTION_PAUSE));
	                    break;
	                case KeyEvent.KEYCODE_MEDIA_STOP:
//	                    Toast.makeText(context, "Stop Pressed", Toast.LENGTH_SHORT).show();
	                    context.startService(new Intent(MusicService.ACTION_PAUSE));
	                    break;
	                case KeyEvent.KEYCODE_MEDIA_NEXT:
//	                    Toast.makeText(context, "Next Track Pressed", Toast.LENGTH_SHORT).show();
	                    context.startService(new Intent(MusicService.ACTION_SKIP));
	                    break;
	                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
//	                    Toast.makeText(context, "Previous Track Pressed", Toast.LENGTH_SHORT).show();
	                    // TODO: ensure that doing this in rapid succession actually plays the
	                    // previous song
	                    context.startService(new Intent(MusicService.ACTION_REWIND));
	                    break;
                    default:
                        Toast.makeText(context, "Unknown key code " + keyEvent.getKeyCode(), Toast.LENGTH_SHORT).show();
                        break;
	            }
	            abortBroadcast();
	        } else {
	        	Log.d("RemoteControlReceiver", "Unknown Remote Control Event Received");
	        }
        }
    }
}