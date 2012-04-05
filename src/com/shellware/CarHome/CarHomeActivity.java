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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class CarHomeActivity extends Activity implements OnClickListener {

	private SharedPreferences prefs;
	private Resources res;

	private AudioManager mAudioManager;
    private ComponentName mRemoteControlResponder;

    private Button mPlayButton;
    
//	private GridView statsGrid;
//	private Menu myMenu = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		res = getResources();

        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        mRemoteControlResponder = new ComponentName(getPackageName(),
                RemoteControlReceiver.class.getName());
        
        mPlayButton = (Button) findViewById(R.id.btnPlay);
        mPlayButton.setOnClickListener(this);
        
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
        mAudioManager.registerMediaButtonEventReceiver(
                mRemoteControlResponder);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        UiModeManager manager = (UiModeManager)getSystemService(Context.UI_MODE_SERVICE);    
        manager.disableCarMode(UiModeManager.DISABLE_CAR_MODE_GO_HOME);

        mAudioManager.unregisterMediaButtonEventReceiver(
                mRemoteControlResponder);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
//		myMenu = menu;
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		
		return false;

        // Handle item selection
//        switch (item.getItemId()) {
//	        case android.R.id.home:
//	        	ChangeLog cl = new ChangeLog(this);
//	        	cl.getFullLogDialog().show();
//	        	return true;
//        	case R.id.helpMenuItem:
//        		Intent helpIntent = new Intent()
//        			.setAction(Intent.ACTION_VIEW)
//        			.setData(Uri.parse(Html.fromHtml(res.getString(R.string.help_url)).toString()));
//        		startActivity(helpIntent);
//        		return true;
//        	case R.id.quitMenuItem:
//	           	System.exit(0);
//	           	return true;
//        	default:
//                return super.onOptionsItemSelected(item);
//        }
		
		
    }

	public void onClick(View target) {
        if (target == mPlayButton) {
            startService(new Intent(MusicService.ACTION_PLAY));
            startService(new Intent(MusicService.ACTION_SKIP));
        }
        	
		
	}    


}