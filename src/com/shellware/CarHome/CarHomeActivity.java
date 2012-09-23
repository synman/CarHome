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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Calendar;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.location.Location;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.google.android.mms.APNHelper;
import com.google.android.mms.APNHelper.APN;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduComposer;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.SendReq;
import com.google.android.mms.transaction.HttpUtils;
import com.gtosoft.libvoyager.session.OBD2Session;
import com.shellware.CarHome.helpers.BatteryStatusReceiver;
import com.shellware.CarHome.helpers.MyLocation;
import com.shellware.CarHome.helpers.MyLocation.LocationResult;
import com.shellware.CarHome.helpers.OBDHelper;
import com.shellware.CarHome.media.MusicService;
import com.shellware.CarHome.media.RemoteControlReceiver;
import com.shellware.CarHome.ui.GaugeNeedle;
import com.shellware.CarHome.ui.ProgramsGallery;

public class CarHomeActivity extends Activity implements OnClickListener {

	private final static String TAG = "CarHomeActivity";
    private static final String FEATURE_ENABLE_MMS = "enableMMS";
    private static boolean debugMode = false;
    
    private static final int APN_ALREADY_ACTIVE     = 0;
//    private static final int APN_REQUEST_STARTED    = 1;
//    private static final int APN_TYPE_NOT_AVAILABLE = 2;
//    private static final int APN_REQUEST_FAILED     = 3;
    
    private final static float KPI_TO_PSI_MULTIPLIER = .14503773773020923f;
    
    private final static int ROUTINE_UPDATE_INTERVAL = 250;
    private final static int TPMS_UPDATE_INTERVAL = 30000;
    private final static int MUSIC_UPDATE_INTERVAL = 1000;
    private final static int BRIGHTNESS_UPDATE_INTERVAL = 60000;
    
    private final static float BRIGHTNESS_MINIMUM = .05f;
    private final static float BRIGHTNESS_MAXIMUM = 1f;

	private static Context ctx = null;
	private SharedPreferences prefs;
	private Resources res;
	
    private Handler routineUpdateHandler = new Handler();
    private Handler tpmsUpdateHandler = new Handler();
    private Handler brightnessUpdateHandler = new Handler();
//    private Handler obdHelperHelpHandler = new Handler();
    private Handler musicUpdateHandler = new Handler();

	private AudioManager mAudioManager;
	private static Camera camera = null;
	private static ConnectivityActionReceiver connectivityActionReceiver;
	
	RemoteControlReceiver mMediaButtonReceiver;
	BatteryStatusReceiver mBatteryStatusReceiver;
	IntentFilter mediaFilter;
	
	private OBDHelper obd;
	
	private static SurfaceView mCameraView;
	private static SurfaceHolder mCameraHolder = null;
	
	private Menu myMenu = null;

	private GaugeNeedle waterNeedle;
	private GaugeNeedle voltageNeedle;
	private GaugeNeedle afrNeedle;
	private GaugeNeedle iatNeedle;
	private GaugeNeedle mafNeedle;
	private GaugeNeedle egtNeedle;
	
	private GaugeNeedle tpms1Needle;
	private GaugeNeedle tpms2Needle;
	private GaugeNeedle tpms3Needle;
	private GaugeNeedle tpms4Needle;
	
	private GaugeNeedle temp1Needle;
	private GaugeNeedle temp2Needle;
	private GaugeNeedle temp3Needle;
	private GaugeNeedle temp4Needle;
	
    private Button mPlayButton;
    private Button mStopButton;
    private Button mForwardButton;
    private Button mRewindButton;
    private Button mMuteButton;
    
    private SeekBar mPositionBar;
    private ImageView mArtworkImage;
    private ImageView mBackground;
    private ImageView mLowFuel;
    private ProgramsGallery gallery;

    private static TextView mMiscText;
    
    private boolean mMuted = false;
    private static String originator = "";
    private static boolean wifiEnabled = false;
    private static int activeCamera = 0;
    private long lastFuelAlarm = 0;
	private long lastSongId = 0;
	private boolean tpmsUpdateRunning = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

//		 StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
//		    StrictMode.setThreadPolicy(policy);
		    
		ctx = this;
		
		setContentView(R.layout.main);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		res = getResources();

        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        
    	mMediaButtonReceiver = new RemoteControlReceiver();
    	mediaFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
    	mediaFilter.setPriority(Integer.MAX_VALUE);

    	registerReceiver(mMediaButtonReceiver, mediaFilter);

    	mBackground = (ImageView) findViewById(R.id.background);
    	mBackground.setOnTouchListener(new backgroundTouchListener());

        waterNeedle = (GaugeNeedle) findViewById(R.id.waterneedle);
        voltageNeedle = (GaugeNeedle) findViewById(R.id.voltageneedle);
        afrNeedle = (GaugeNeedle) findViewById(R.id.afrneedle);
        iatNeedle = (GaugeNeedle) findViewById(R.id.iatneedle);
        mafNeedle = (GaugeNeedle) findViewById(R.id.mafneedle);
        egtNeedle = (GaugeNeedle) findViewById(R.id.egtneedle);
        
        tpms1Needle = (GaugeNeedle) findViewById(R.id.tpms1needle);
        tpms2Needle = (GaugeNeedle) findViewById(R.id.tpms2needle);
        tpms3Needle = (GaugeNeedle) findViewById(R.id.tpms3needle);
        tpms4Needle = (GaugeNeedle) findViewById(R.id.tpms4needle);
        
        temp1Needle = (GaugeNeedle) findViewById(R.id.temp1needle);
        temp2Needle = (GaugeNeedle) findViewById(R.id.temp2needle);
        temp3Needle = (GaugeNeedle) findViewById(R.id.temp3needle);
        temp4Needle = (GaugeNeedle) findViewById(R.id.temp4needle);

        waterNeedle.setPivotPoint(.65f);
        waterNeedle.setMinValue(100);
        waterNeedle.setMaxValue(250);
        waterNeedle.setMinDegrees(-45);
        waterNeedle.setMaxDegrees(45);
        
        voltageNeedle.setPivotPoint(.65f);
        voltageNeedle.setMinValue(100);
        voltageNeedle.setMaxValue(160);
        voltageNeedle.setMinDegrees(-52);
        voltageNeedle.setMaxDegrees(52);
        
        mafNeedle.setPivotPoint(.5f);
        mafNeedle.setMinValue(0);
        mafNeedle.setMaxValue(200);
        mafNeedle.setMinDegrees(-132);
        mafNeedle.setMaxDegrees(132);
        
        afrNeedle.setPivotPoint(.5f);
        afrNeedle.setMinValue(111);
        afrNeedle.setMaxValue(220);
        afrNeedle.setMinDegrees(-180);
        afrNeedle.setMaxDegrees(90);
        
        iatNeedle.setPivotPoint(.5f);
        iatNeedle.setMinValue(0);
        iatNeedle.setMaxValue(200);
        iatNeedle.setMinDegrees(-180);
        iatNeedle.setMaxDegrees(90);
        
        egtNeedle.setPivotPoint(.5f);
        egtNeedle.setMinValue(0);
        egtNeedle.setMaxValue(1800);
        egtNeedle.setMinDegrees(-161);
        egtNeedle.setMaxDegrees(161);
        
        tpms1Needle.setPivotPoint(.5f);
        tpms1Needle.setMinValue(300);
        tpms1Needle.setMaxValue(400);
        tpms1Needle.setMinDegrees(-135);
        tpms1Needle.setMaxDegrees(135);
        
        tpms2Needle.setPivotPoint(.5f);
        tpms2Needle.setMinValue(300);
        tpms2Needle.setMaxValue(400);
        tpms2Needle.setMinDegrees(-135);
        tpms2Needle.setMaxDegrees(135);
        
        tpms3Needle.setPivotPoint(.5f);
        tpms3Needle.setMinValue(300);
        tpms3Needle.setMaxValue(400);
        tpms3Needle.setMinDegrees(-135);
        tpms3Needle.setMaxDegrees(135);
        
        tpms4Needle.setPivotPoint(.5f);
        tpms4Needle.setMinValue(300);
        tpms4Needle.setMaxValue(400);
        tpms4Needle.setMinDegrees(-135);
        tpms4Needle.setMaxDegrees(135);
        
        temp1Needle.setPivotPoint(.5f);
        temp1Needle.setMinValue(-20);
        temp1Needle.setMaxValue(120);
        temp1Needle.setMinDegrees(-150);
        temp1Needle.setMaxDegrees(150);        
        
        temp2Needle.setPivotPoint(.5f);
        temp2Needle.setMinValue(-20);
        temp2Needle.setMaxValue(120);
        temp2Needle.setMinDegrees(-150);
        temp2Needle.setMaxDegrees(150);        
        
        temp3Needle.setPivotPoint(.5f);
        temp3Needle.setMinValue(-20);
        temp3Needle.setMaxValue(120);
        temp3Needle.setMinDegrees(-150);
        temp3Needle.setMaxDegrees(150);        
        
        temp4Needle.setPivotPoint(.5f);
        temp4Needle.setMinValue(-20);
        temp4Needle.setMaxValue(120);
        temp4Needle.setMinDegrees(-150);
        temp4Needle.setMaxDegrees(150);        
        
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
        mCameraView = (SurfaceView) findViewById(R.id.cameraView); 
        mLowFuel = (ImageView) findViewById(R.id.lowfuel);     
        gallery = (ProgramsGallery) findViewById(R.id.gallery);      
        mMiscText = (TextView) findViewById(R.id.misctext);

//        ChangeLog cl = new ChangeLog(this);
//        if (cl.firstRun()) {
//            cl.getLogDialog().show();
//        }
	}
	
	private class backgroundTouchListener implements OnTouchListener {
		public boolean onTouch(View view, MotionEvent motionevent) {
			if (motionevent.getAction() == MotionEvent.ACTION_DOWN) {
				if (gallery.getVisibility() != View.VISIBLE) { 
					gallery.showGallery();
				} else {
					gallery.hideGallery();
				}
			}
			return true;
		}	
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
        
        // our obd helper class (lotsa stuff happens here)
		if (obd != null) {
			obd.shutdown();
			obd = null;
		}
    	new Handler().postDelayed(new Runnable() {
			public void run() {
    	        // our obd helper class (lotsa stuff happens here)
	        	obd = new OBDHelper(ctx);
			}
    	}, 5000);

    	routineUpdateHandler.post(routineUpdate);
    	tpmsUpdateHandler.postDelayed(tpmsUpdate, TPMS_UPDATE_INTERVAL);
    	brightnessUpdateHandler.post(brightnessUpdate);
    	musicUpdateHandler.post(musicUpdate);
    }
    
	
    @Override
    public void onPause() {
    	super.onPause();
    	
    	if (obd != null) {
        	obd.shutdown();
        	obd = null;    		
    	}
    	
    	routineUpdateHandler.removeCallbacks(routineUpdate);
    	tpmsUpdateHandler.removeCallbacks(tpmsUpdate);
    	brightnessUpdateHandler.removeCallbacks(brightnessUpdate);
    	musicUpdateHandler.removeCallbacks(musicUpdate);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        gallery.destroyGallery();
        
        UiModeManager manager = (UiModeManager)getSystemService(Context.UI_MODE_SERVICE);    
        manager.disableCarMode(UiModeManager.DISABLE_CAR_MODE_GO_HOME);

        unregisterReceiver(mMediaButtonReceiver);
        unregisterReceiver(mBatteryStatusReceiver);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
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

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		myMenu = menu;

		BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();    
	    if (bt != null && bt.isEnabled()) {
	        myMenu.getItem(0).setIcon(R.drawable.bluetooth_on);
	    } else { 
	    	myMenu.getItem(0).setIcon(R.drawable.bluetooth_off);
	    }

    	mBatteryStatusReceiver = new BatteryStatusReceiver(this, obd, menu);
	    registerReceiver(mBatteryStatusReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        
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
        	    	
           	    	sleep(5000);
        	    	bt.enable();

        	    	if (obd != null) {
        	    		obd.shutdown();
        	    		obd = null;        	    		 
        	    	}

    	        	new Handler().postDelayed(new Runnable() {
						public void run() {
		        	        // our obd helper class (lotsa stuff happens here)
		    	        	obd = new OBDHelper(ctx);
						}
    	        	}, 2500);

        	    	
        	    	item.setIcon(R.drawable.bluetooth_on);
        	    } else { 
        	        if (obd != null) {
        	        	obd.shutdown();
        	        	obd = null;
        	        }
        	        
        	        if (bt != null) bt.disable(); 

        	        item.setIcon(R.drawable.bluetooth_off);
        	    } 
        		return true;
        		
        	case R.id.quitMenuItem:
	           	System.exit(0);
	           	return true;
	           	
        	case R.id.cameraMenuItem:
                
        		if (camera == null) {
        			startCameraPreview(Camera.CameraInfo.CAMERA_FACING_FRONT, false);
        		} else {
        			stopCameraPreview();
        		}
                
            	return true;
            	
        	case R.id.celMenuItem:
        		if (obd != null && obd.getHs() != null && obd.getHs().getOBDSession() != null && 
        				obd.getHs().getOBDSession().getCurrentState() == OBD2Session.STATE_OBDCONNECTED) {
        			String cels = obd.getHs().getPIDDecoder().getDataViaOBD("DTC");
        			
        		 	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        	    	builder
	        	    	.setTitle("Stored Trouble Codes")
	        	    	.setMessage(cels + "\n\n" + "Would you like to clear active CEL(s)?")
	        	    	.setIcon(android.R.drawable.ic_dialog_info)
	        	    	.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	        	    	    public void onClick(DialogInterface dialog, int which) {			      	
	        	    	    	//Yes button clicked, do something
	        	    	    	obd.getHs().getOBDSession().sendDTCReset();
	        	    	    }
	        	    	})
	        	    	.setNegativeButton("No", null)						//Do nothing on no
	        	    	.show();
        		}
        		return true;
        	
        	case R.id.debugMenuItem:
        		item.setChecked(!item.isChecked());
        		debugMode = item.isChecked();
        		return false;
    
        	default:
                return super.onOptionsItemSelected(item);
        }
    }

	private Runnable musicUpdate = new Runnable() {

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
				if (lastSongId != MusicService.getCurrentItem().getId()) {
					mArtworkImage.setImageBitmap(MusicService.getCurrentItem().getAlbumArt());
					lastSongId = MusicService.getCurrentItem().getId();
				}
			} else {
				mPlayButton.setBackgroundResource(R.drawable.playicon);
			}

			musicUpdateHandler.postDelayed(this, MUSIC_UPDATE_INTERVAL);					
		}
	};
	
	private Runnable routineUpdate = new Runnable() {
    	
		public void run() {

			try {							
				// if obd isn't around don't bother
				if (obd != null && obd.getHs() != null &&
						   obd.getHs().isOBDReady() && obd.getHs().getOBDSession() != null && 
						   obd.getHs().getOBDSession().getCurrentState() >= OBD2Session.STATE_OBDCONNECTED) {
					
					// set our current routine values
					waterNeedle.setValue(obd.getCoolant());
					voltageNeedle.setValue(obd.getVoltage() * 10);
					mafNeedle.setValue(obd.getMaf());
					afrNeedle.setValue(200 - (obd.getWideband() * 10) + 110);
					iatNeedle.setValue(obd.getIat());
					egtNeedle.setValue(obd.getEgt());
									
					// if we're almost out of fuel do something
					if (obd.getFuel() > 0 && obd.getFuel() < 10f) {
						if (mLowFuel.getVisibility() == View.INVISIBLE) {
							mLowFuel.setVisibility(View.VISIBLE);
							
							if (lastFuelAlarm + 60000 < System.currentTimeMillis()) {
								Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
								Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
								r.play();
								
								lastFuelAlarm = System.currentTimeMillis();
							}
						} else {
							mLowFuel.setVisibility(View.INVISIBLE);
						}
					} else {
						if (mLowFuel.getVisibility() == View.VISIBLE) mLowFuel.setVisibility(View.INVISIBLE);
					}
				} else {
					// we're not connected -- default all values
					waterNeedle.setValue(180);
					voltageNeedle.setValue(130);
					mafNeedle.setValue(100);
					afrNeedle.setValue(200 - 147 + 110);
					iatNeedle.setValue(100);
					egtNeedle.setValue(900);					
				}
			} catch (Exception ex) {
				// do nothing -- null pointer likely from obd
				logger(ex.getMessage() + " " + getStackTrace(ex));
				
			} finally {
				// make sure we keep running
				if (!tpmsUpdateRunning) routineUpdateHandler.postDelayed(this, ROUTINE_UPDATE_INTERVAL);					
			}			
		} 	
    };
    
    private Runnable tpmsUpdate = new Runnable() {

		public void run() {
			
			tpmsUpdateRunning = true;			
			routineUpdateHandler.removeCallbacks(routineUpdate);
			
			try {		
				if (obd != null && obd.getHs() != null &&
						   obd.getHs().isOBDReady() && obd.getHs().getOBDSession() != null && 
						   obd.getHs().getOBDSession().getCurrentState() >= OBD2Session.STATE_OBDCONNECTED) {
					
					// sleep for 250ms to ensure routineupdate isn't active
					Thread.sleep(250);
					
					obd.getHs().getEBT().sendATCommand2("ATSH000751");
					
					final String tpmsPresBuf = obd.getHs().getEBT().sendOBDCommand("22C9011");
					final String tpmsTempBuf = obd.getHs().getEBT().sendOBDCommand("22C9021");
					
					if (debugMode) {
						obd.getHs().getEBT().sendATCommand2("ATD");
						obd.getHs().getEBT().sendATCommand2("ATSH000650");
						
						final String c1 = obd.getHs().getEBT().sendOBDCommand("22A0211");
						final String c2 = obd.getHs().getEBT().sendOBDCommand("22A0216");
						
						logger("c1: " + c1 + " c2: " + c2);
					}
					
					// reset our connection to clear the headers -- need to figure out a better way
					obd.getHs().getEBT().sendATCommand2("ATD");
					
					if (tpmsPresBuf != null && !tpmsPresBuf.contains("NO DATA")) {
						try {
							final String[] tpmsPres = tpmsPresBuf.split(" ");
							
							if (tpmsPres.length > 5) {
								final float tpmsPres4 = Integer.parseInt(tpmsPres[tpmsPres.length - 2], 16) * 1.373f * KPI_TO_PSI_MULTIPLIER;
								final float tpmsPres3 = Integer.parseInt(tpmsPres[tpmsPres.length - 3], 16) * 1.373f * KPI_TO_PSI_MULTIPLIER;
								final float tpmsPres2 = Integer.parseInt(tpmsPres[tpmsPres.length - 4], 16) * 1.373f * KPI_TO_PSI_MULTIPLIER;
								final float tpmsPres1 = Integer.parseInt(tpmsPres[tpmsPres.length - 5], 16) * 1.373f * KPI_TO_PSI_MULTIPLIER;
								
								tpms1Needle.setValue(tpmsPres1 * 10);
								tpms2Needle.setValue(tpmsPres2 * 10);
								tpms3Needle.setValue(tpmsPres3 * 10);
								tpms4Needle.setValue(tpmsPres4 * 10);
							}
						} catch (Exception ex) {
							tpms1Needle.setValue(350);
							tpms2Needle.setValue(350);
							tpms3Needle.setValue(350);
							tpms4Needle.setValue(350);
						}
					}
					
					if (tpmsTempBuf != null && !tpmsTempBuf.contains("NO DATA")) {
						try {					
							final String[] tpmsTemp = tpmsTempBuf.split(" ");
							
							if (tpmsTemp.length > 5) {
								final float tpmsTemp4 = (float) (1.8 * (Integer.parseInt(tpmsTemp[tpmsTemp.length - 2], 16) - 40) + 32);
								final float tpmsTemp3 = (float) (1.8 * (Integer.parseInt(tpmsTemp[tpmsTemp.length - 3], 16) - 40) + 32);
								final float tpmsTemp2 = (float) (1.8 * (Integer.parseInt(tpmsTemp[tpmsTemp.length - 4], 16) - 40) + 32);
								final float tpmsTemp1 = (float) (1.8 * (Integer.parseInt(tpmsTemp[tpmsTemp.length - 5], 16) - 40) + 32);
								
								temp1Needle.setValue(tpmsTemp1);
								temp2Needle.setValue(tpmsTemp2);
								temp3Needle.setValue(tpmsTemp3);
								temp4Needle.setValue(tpmsTemp4);
							}
						} catch (Exception ex) {
							temp1Needle.setValue(50);
							temp2Needle.setValue(50);
							temp3Needle.setValue(50);
							temp4Needle.setValue(50);
						}
					}
				} else {
					tpms1Needle.setValue(350);
					tpms2Needle.setValue(350);
					tpms3Needle.setValue(350);
					tpms4Needle.setValue(350);
					
					temp1Needle.setValue(50);
					temp2Needle.setValue(50);
					temp3Needle.setValue(50);
					temp4Needle.setValue(50);					
				}
			} catch (Exception ex) {
				logger(ex.getMessage() + " " + getStackTrace(ex));
				
			} finally {
				routineUpdateHandler.postDelayed(routineUpdate, ROUTINE_UPDATE_INTERVAL);				

				tpmsUpdateRunning = false;
				tpmsUpdateHandler.postDelayed(tpmsUpdate, TPMS_UPDATE_INTERVAL);
			}
		}
	};
    
	public Runnable brightnessUpdate = new Runnable() {

		public void run() {
			
			Calendar cal = Calendar.getInstance();
			float brightness = .5f;
			
			// Winter
			if (cal.get(Calendar.MONTH) >= Calendar.DECEMBER || cal.get(Calendar.MONTH) < Calendar.APRIL) {
				if (cal.get(Calendar.HOUR_OF_DAY) > 17 || cal.get(Calendar.HOUR_OF_DAY) <= 6) brightness = BRIGHTNESS_MINIMUM;
				if (cal.get(Calendar.HOUR_OF_DAY) <= 17 && cal.get(Calendar.HOUR_OF_DAY) > 6) brightness = BRIGHTNESS_MAXIMUM;
			}			
			// Spring
			if (cal.get(Calendar.MONTH) >= Calendar.APRIL && cal.get(Calendar.MONTH) < Calendar.JULY) {
				if (cal.get(Calendar.HOUR_OF_DAY) > 18 || cal.get(Calendar.HOUR_OF_DAY) <= 6) brightness = BRIGHTNESS_MINIMUM;
				if (cal.get(Calendar.HOUR_OF_DAY) <= 18 && cal.get(Calendar.HOUR_OF_DAY) > 6) brightness = BRIGHTNESS_MAXIMUM;					
			}				
			// Summer
			if (cal.get(Calendar.MONTH) >= Calendar.JULY && cal.get(Calendar.MONTH) < Calendar.OCTOBER) {
				if (cal.get(Calendar.HOUR_OF_DAY) > 19 || cal.get(Calendar.HOUR_OF_DAY) <= 6) brightness = BRIGHTNESS_MINIMUM;
				if (cal.get(Calendar.HOUR_OF_DAY) <= 19 && cal.get(Calendar.HOUR_OF_DAY) > 6) brightness = BRIGHTNESS_MAXIMUM;
			}				
			// Fall
			if (cal.get(Calendar.MONTH) >= Calendar.OCTOBER && cal.get(Calendar.MONTH) < Calendar.DECEMBER) {
				if (cal.get(Calendar.HOUR_OF_DAY) > 18 || cal.get(Calendar.HOUR_OF_DAY) <= 6) brightness = BRIGHTNESS_MINIMUM;
				if (cal.get(Calendar.HOUR_OF_DAY) <= 18 && cal.get(Calendar.HOUR_OF_DAY) > 6) brightness = BRIGHTNESS_MAXIMUM;			
			}       

			WindowManager.LayoutParams lp = getWindow().getAttributes();
			lp.screenBrightness = brightness;
			getWindow().setAttributes(lp); 
			
			brightnessUpdateHandler.postDelayed(brightnessUpdate, BRIGHTNESS_UPDATE_INTERVAL);
		}
	};
	
	public static void toggleCamera() {
		if (camera == null) {
			startCameraPreview(Camera.CameraInfo.CAMERA_FACING_FRONT, false);
		} else {
			stopCameraPreview();
		}
	}
	
	private static void startCameraPreview(int whichCamera, boolean withCallback) {
		
        try {
			if (!withCallback) mCameraView.setVisibility(View.VISIBLE);
			
			activeCamera = whichCamera;
			
			if (mCameraHolder == null) {
				mCameraHolder = mCameraView.getHolder();
				mCameraHolder.addCallback(surfaceCallback);
			}
			
	        Camera.CameraInfo info=new Camera.CameraInfo();
	
	        for (int i=0; i < Camera.getNumberOfCameras(); i++) {
	        	Camera.getCameraInfo(i, info);
	
	        	if (info.facing == whichCamera) {
	        		camera=Camera.open(i);
	        	}
	        }
	
	        if (camera == null) {
	        	camera=Camera.open();
	        }
	        
	        if (withCallback) camera.setPreviewCallback(new CameraPreviewCallback());       

			camera.setPreviewDisplay(mCameraHolder);	        
	        camera.startPreview();

        } catch (Exception e) {
			// hrm
			e.printStackTrace();
		}
	}

	private static void stopCameraPreview() {
	
		if (camera == null) return;
		
		//tear down camera here
		camera.stopPreview();
		camera.release();
		camera = null;
		
		mCameraHolder = null;
		mCameraView.setVisibility(View.INVISIBLE);
	}
	
	  static SurfaceHolder.Callback surfaceCallback=new SurfaceHolder.Callback() {
		    public void surfaceCreated(SurfaceHolder holder) {
		      // no-op -- wait until surfaceChanged()
		    }

		    public void surfaceChanged(SurfaceHolder holder, int format,
		                               int width, int height) {
		    	logger("surfaceChanged");
		    }

		    public void surfaceDestroyed(SurfaceHolder holder) {
		      // no-op
		    }
		  };
	
	public static void whereAreYou(final String origin) {
		
		locationResult.setDestination(origin);
		
		MyLocation myLocation = new MyLocation();
    	myLocation.getLocation(ctx, locationResult);
	}

	public static LocationResult locationResult = new LocationResult() {
		
		String destination = "";
		
	    @Override
	    public void gotLocation(final Location location){

	    	if (location == null) return;
	    	
	    	final String text = "http://maps.google.com/?q=" + location.getLatitude() + "+" + location.getLongitude();
	    	
    		SmsManager sm = SmsManager.getDefault();
        	sm.sendTextMessage(destination, null, text, null, null);
	    }
	    
	    public void setDestination(final String destination) {
	    	this.destination = destination;
	    }
	};
	
	public static void whatDoYouSee(final String origin) {
		
		if (connectivityActionReceiver == null) {
			originator = origin;
			stopCameraPreview();
			startCameraPreview(Camera.CameraInfo.CAMERA_FACING_FRONT, true); 
			
			Handler handler = new Handler();
			handler.postDelayed(doItAgain, 30000);
			
		} else {
			logger("aborting mms request - connectivity intent already exists");
		}
	}

	private static Runnable doItAgain = new Runnable() {
	
		public void run() {
			stopCameraPreview();
			startCameraPreview(Camera.CameraInfo.CAMERA_FACING_BACK, true); 			
		}
		
	};
	
	private static class CameraPreviewCallback implements PreviewCallback {

		private long startTime;
		private boolean fired = false;
		
		public CameraPreviewCallback() {
			super();
			startTime = System.currentTimeMillis();
			fired = false;
		}
		
		public void onPreviewFrame(byte[] arg0, Camera arg1) {
			if (!fired && System.currentTimeMillis() - startTime >= 3000) {
				fired = true;
				
				logger("taking picture");

				camera.stopPreview();
				camera.takePicture(null, null, new CameraPictureCallback());
			}
		}
	}
	
	private static class CameraPictureCallback implements PictureCallback {

		public void onPictureTaken(byte[] data, Camera camera) {

			logger("picture taken");

			stopCameraPreview();	
			
	        WifiManager wifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
	        wifiEnabled = wifi.isWifiEnabled();
	        wifi.setWifiEnabled(false);
	        
	        logger("wifi disabled - was enabled=" + wifiEnabled);

			final ConnectivityManager connMgr = (ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
	        final int result = connMgr.startUsingNetworkFeature( ConnectivityManager.TYPE_MOBILE, FEATURE_ENABLE_MMS);
	        
	        if (result != APN_ALREADY_ACTIVE) {
				logger("registering connectivity intent");
	        	final IntentFilter filter = new IntentFilter();
	        	filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
	        	connectivityActionReceiver = new ConnectivityActionReceiver(data, activeCamera == CameraInfo.CAMERA_FACING_FRONT ? 50 : 10); 	
	        	ctx.registerReceiver(connectivityActionReceiver, filter);
	        	return;
	        }
	        
	        SendMmsPicture worker = new SendMmsPicture(data,  activeCamera == CameraInfo.CAMERA_FACING_FRONT ? 50 : 10);
			worker.start();
		}
	}

	private static class ConnectivityActionReceiver extends BroadcastReceiver {

		private byte[] data;
		private int quality;
		
		public ConnectivityActionReceiver(byte[] data, int quality) {
			super();
			this.data = data;
			this.quality = quality;
		}
		
		@Override
		public void onReceive(Context context, Intent intent) {

		   String action = intent.getAction();
		    if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
		        return;
		    }

		    NetworkInfo mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(
		        ConnectivityManager.EXTRA_NETWORK_INFO);

		    // Check availability of the mobile network.
		    if ((mNetworkInfo == null) ||
		        (mNetworkInfo.getType() != ConnectivityManager.TYPE_MOBILE_MMS)) {
		        return;
		    }

		    if (!mNetworkInfo.isConnected()) {
		        return;
		    } else {
		    	//send mms
				logger("intent reports MMS ready");
				SendMmsPicture worker = new SendMmsPicture(data, quality);
				worker.start();
 		    }
		}
	}

	public static class SendMmsPicture extends Thread {
		
		private byte[] data;
		private int quality;
		
		public SendMmsPicture(byte[] data, int quality) {
			super();
			this.data = data;
			this.quality = quality;
		}
		
		public void run() {

			logger("sending mms message");
			
	        final SendReq sendRequest = new SendReq();
//	        final EncodedStringValue[] sub = EncodedStringValue.extract("What I'm doing. . .");
//	        if (sub != null && sub.length > 0) {
//	            sendRequest.setSubject(sub[0]);
//	        }
	        final EncodedStringValue[] phoneNumbers = EncodedStringValue
	                .extract(originator);
	        if (phoneNumbers != null && phoneNumbers.length > 0) {
	            sendRequest.addTo(phoneNumbers[0]);
	        }

	        final PduBody pduBody = new PduBody();
            final PduPart partPdu = new PduPart();

            partPdu.setContentId("<0>".getBytes());
            partPdu.setName("body".getBytes());
            partPdu.setContentType("image/jpeg".getBytes());
            
            Bitmap bm = BitmapFactory.decodeByteArray(data,0, data.length);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
            bm.compress(CompressFormat.JPEG, quality, baos);
            
            partPdu.setData(baos.toByteArray());

            pduBody.addPart(partPdu);

	        sendRequest.setBody(pduBody);

	        final PduComposer composer = new PduComposer(ctx, sendRequest);
	        final byte[] bytesToSend = composer.make();

	        try {   	
	        	APNHelper apnHelper = new APNHelper(ctx);
	        	List<APN> apns = apnHelper.getMMSApns();
	        			
	        	for (APN apn : apns) {
					HttpUtils.httpConnection(ctx, 4444L, apn.MMSCenterUrl,
					        bytesToSend, HttpUtils.HTTP_POST_METHOD, true, apn.MMSProxy, Integer.parseInt(apn.MMSPort));
					logger("mms message sent");					
	        	}	

			} catch (IOException e) {
				e.printStackTrace();
				
			} finally {
				if (connectivityActionReceiver != null) {
					ctx.unregisterReceiver(connectivityActionReceiver);
					connectivityActionReceiver = null;
			        
					final ConnectivityManager connMgr = (ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
					connMgr.stopUsingNetworkFeature( ConnectivityManager.TYPE_MOBILE, FEATURE_ENABLE_MMS);

					logger("unregistered connectivity intent");
					
			        WifiManager wifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
			        wifi.setWifiEnabled(wifiEnabled);
			        
			        logger("wifi enabled=" + wifiEnabled);
				}
			}
		}
	}
	
	public static void rebootWithSU() {
		try {
			Runtime.getRuntime().exec(new String[]{"/system/xbin/su","-c","ls"});
			sleep(2500);
			Runtime.getRuntime().exec(new String[]{"/system/xbin/su","-c","reboot now"});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void sleep(final int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// do nothing
		}
	}
	
	private static String getStackTrace(Exception ex) {
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		ex.printStackTrace(printWriter);
		
		return result.toString();
	}
	
	private static void logger(final String msg) {
		logger(msg);
		if (debugMode) mMiscText.setText(String.format("%s\n%s", msg, mMiscText.getText()));
	}
}