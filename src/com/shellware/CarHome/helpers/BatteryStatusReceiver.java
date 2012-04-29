package com.shellware.CarHome.helpers;

import com.shellware.CarHome.R;
import com.shellware.CarHome.R.drawable;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Handler;
import android.view.Menu;

public class BatteryStatusReceiver extends BroadcastReceiver {

	private Context ctx = null;
	private OBDHelper obd = null;
	private Menu myMenu = null;
	
	private boolean isCharging = false;
	
	public BatteryStatusReceiver(Context ctx, OBDHelper obd, Menu myMenu) {
		this.ctx = ctx;
		this.obd = obd;
		this.myMenu = myMenu;
	}
	
	@Override
	public void onReceive(Context arg0, Intent arg1) {
		if (arg1.getAction().equals(Intent.ACTION_BATTERY_CHANGED)){

			final int status = arg1.getIntExtra("status", BatteryManager.BATTERY_STATUS_UNKNOWN);
    	    final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();    
			
			if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
					status == BatteryManager.BATTERY_STATUS_FULL) {
				
				isCharging = true;
				
        	    if (bt != null && !bt.isEnabled()) {
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

    	        	new Handler().post(new Runnable() {
						public void run() {
							myMenu.getItem(0).setIcon(R.drawable.bluetooth_on);
						}
    	        	});
        	    } 
        	    
			} else {
				
				isCharging = false;
				
        	    if (bt != null && bt.isEnabled()) {
    	        	new Handler().postDelayed(new Runnable() {
						public void run() {
							if (!isCharging) {								
								if (obd != null) {
			        	    		obd.shutdown();
			        	    		obd = null;
			        	    	}

			        	    	bt.disable();		        	    	
								myMenu.getItem(0).setIcon(R.drawable.bluetooth_off);
							}
						}
    	        	}, 30000);
        	    } 					
			}
		}
	}	
}
