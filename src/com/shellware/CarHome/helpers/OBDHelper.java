package com.shellware.CarHome.helpers;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import com.gtosoft.libvoyager.android.ActivityHelper;
import com.gtosoft.libvoyager.db.DashDB;
import com.gtosoft.libvoyager.session.HybridSession;
import com.gtosoft.libvoyager.session.OBD2Session;
import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.EventCallback;
import com.gtosoft.libvoyager.util.GeneralStats;
import com.gtosoft.libvoyager.util.OOBMessageTypes;

public class OBDHelper {

	private Context ctx;
	
	// General Stats about the internals of the app.
	private GeneralStats mgStats = new GeneralStats();

	// Dash DB. Has OBD PID lookup tables and much more. see assets/schema.sql.
	private DashDB ddb = null;
	
	// Hybridsession is a class that lets us communicate using OBD or passive or
	// direct network commands. It manages various "session" classes to do this.
	private HybridSession hs;

	// ActivityHelper helps us perform Bluetooth discovery so we don't have to
	// write a ton of code.
	private ActivityHelper aHelper = null;

	private float voltage = 0f;
	private float iat = 0f;
	private float fuel = 0f;
	private float coolant = 0f;
	private float maf = 0f;
	private float wideband = 0f;
	private float egt = 0f;
	private String tire1Pres = "";
	
	public OBDHelper(Context ctx) {
	    
		this.ctx = ctx;
		
		aHelper = new ActivityHelper(ctx);
		// Register with libvoyager to receive "we found an ELM device nearby"
		// message so when we perform a discovery, this method gets called.
		aHelper.registerChosenDeviceCallback(chosenCallback);
		// in the onResume method we will make the call to ActivityHelper to
		// actually kick off the bluetooth discovery.
		
		doBestAvailable();
	}
	
	private boolean doBestAvailable() {
		// kick off Bluetooth discovery. At this point the phone looks for
		// nearby bluetooth devices.
		// For each device, ActivityHelper checks its name to see if it's an OBD
		// device.
		// If the device seems to be an ELM OBD device, it calls the
		// "chosenCallback" event with details about the device (its MAC).
		if (hs == null || hs.getEBT().isConnected() != true) {
			connectToBestAvailable();
		} else {
			return false;
		}

		return true;
	}

	/**
	 * Either connects to last known device, or performs a discovery to find
	 * nearby OBD devices.
	 */
	private void connectToBestAvailable() {
		String lastmac = aHelper.getLastUsedMAC();

		// Connect to last known device if possible. Otherwise do the usual
		// bluetooth scan.
		if (lastmac.length() == 17) {
			// if we setupSession ourself, we are skipping the activityhelper
			// conveniences of discovery/etc.
			setupSession(lastmac);
		} else {
			// let the activityhelper find the device and fire off the
			// "chosenCallback" when it finds one.
			aHelper.startDiscovering();
		}
	}

	/**
	 * libVoyager can do the BT discovery and device choosing for you. When it
	 * finds/chooses a device it runs the device chosen callback. This method
	 * defines what to do when a new device is found.
	 */
	private EventCallback chosenCallback = new EventCallback() {
		@Override
		public void onELMDeviceChosen(String MAC) {
			setupSession(MAC);
		}
	};

	/**
	 * This method gets called by the broadcast receiver, for bluetooth devices
	 * which are "OBD" devices. This takes care of any necessary actions to open
	 * a connection to the specified device. Run synchronized in case the
	 * discovery process throws us multiple devices. We only want the first
	 * valid one.
	 * 
	 * @param deviceMACAddress
	 * @return - true on success, false otherwise.
	 */
	private synchronized boolean setupSession(String deviceMACAddress) {
		// If there's an existing hybrid session, shut it down.
		if (hs != null) {
			hs.shutdown();
		}

		// instantiate dashDB if necessary.
		if (ddb == null) {
			ddb = new DashDB(ctx);
		}

		aHelper.setLastUsedMAC(deviceMACAddress);

		// instantiate hybridsession, which is just a class that controls
		// subclasses such as Monitorsession and OBDSession, that communicate
		// with the network in different ways.
		hs = new HybridSession(BluetoothAdapter.getDefaultAdapter(),
				deviceMACAddress, ddb, mLocalecbOOBMessageHandler);
		// after hybridsession is successful at opening the bluetooth
		// connection, we will get an OOB notification that the IO state changed
		// to "1".

		// Sets the session type to OBD2. nothing fancy.
		// hs.setActiveSession(HybridSession.SESSION_TYPE_OBD2);

		// register a method to be called when new data arrives.
		hs.registerDPArrivedCallback(mLocalDPNArrivedHandler);

		return true;
	}

	private int mLastIOState = 1234;

	private void ioStateChanged(int newState) {

		// Avoid non-events where iostateChanged is fired but no state change
		// actually occurred.
		if (newState == mLastIOState) {
			return;
		} else {
			mLastIOState = newState;
		}

		// Did bluetooth just establish connection? If so then kick off a
		// session detection to see what we're connected to.
		if (newState == 1) {
			// Bluetooth just connected, so kick off a thread that does network
			// detection and prepares the hybridsession class for use.
			detectSessionInBackground();
		} else {
			// Bluetooth just disconnected. ELMBT will try to reconnect a preset
			// number of times, at a preset interval.
			// TODO: Kill the auto detect routine in case it started and is still in progress. 
		}
	}

	public void shutdown() {
		// give hs a chance to properly close the network/bluetooth link.
		try {
			if (aHelper != null) aHelper.shutdown();
			if (hs != null) hs.shutdown();
		} catch (Exception e) {
			e.printStackTrace();
		}
	};

	/**
	 * Kicks off an asynchronous thread which does network/hardware detection
	 * via the hybridSession class.
	 */
	private void detectSessionInBackground() {
		new Thread() {
			public void run() {
				mgStats.incrementStat("netDetectAttempts"); // sets it to
															// initial value, 1.
				// loop until either 1. we detect the network, or 2. bluetooth
				// disconnects.
				// Typically this detection process takes 5-15 seconds depending
				// on type of network. Cacheing optimizations haven't been built
				// in yet but would be quite easy down the road.
				while (hs.runSessionDetection() != true
						&& hs.getEBT().isConnected() == true) {
					mgStats.incrementStat("netDetectAttempts");
					if (!EasyTime.safeSleep(1000))
						break;
				}
			}
		}.start();
	}

	/**
	 * Define what action the hybridsession should take as it decodes data from
	 * the OBD network.
	 */
	private EventCallback mLocalDPNArrivedHandler = new EventCallback() {
		@Override
		public void onDPArrived(String DPN, final String sDecodedData, int iDecodedData) {

			if (DPN.equals("VOLTS")) voltage = getPrimaryDPNValue(sDecodedData, voltage);
			if (DPN.equals("TEMP_INTAKE")) iat = getPrimaryDPNValue(sDecodedData, iat);
			if (DPN.equals("TEMP_COOLANT")) coolant = getPrimaryDPNValue(sDecodedData, coolant);
			if (DPN.equals("MAF_FLOW_RATE")) maf = getPrimaryDPNValue(sDecodedData, maf);
			if (DPN.equals("FUEL_LEVEL")) fuel = getPrimaryDPNValue(sDecodedData, fuel);
			if (DPN.equals("WIDEBAND_O2")) wideband = getPrimaryDPNValue(sDecodedData, wideband);
			if (DPN.equals("CATALYST_TEMP_B1S1")) egt = getPrimaryDPNValue(sDecodedData, egt);
			if (DPN.equals("TPMS_PRES_1")) tire1Pres = sDecodedData;
		}
	};

	// Defines the logic to take place when an out of band message is generated
	// by the hybrid session layer.
	private EventCallback mLocalecbOOBMessageHandler = new EventCallback() {
		@Override
		public void onOOBDataArrived(String dataName, String dataValue) {

			// state change?
			if (dataName.equals(OOBMessageTypes.IO_STATE_CHANGE)) {
				int newState = 0;
				try {
					newState = Integer.valueOf(dataValue);
					ioStateChanged(newState);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}// end of "if this was a io state change".

			// Bluetooth unable to connect to peer?
			if (dataName.equals(OOBMessageTypes.BLUETOOTH_FAILED_CONNECT)) {

				if (dataValue.equals("0")) {
					aHelper.startDiscovering();
				}
			}

			// session state change?
			if (dataName.equals(OOBMessageTypes.SESSION_STATE_CHANGE)) {
				int newState = 0;

				// convert from string to integer.
				try {
					newState = Integer.valueOf(dataValue);
				} catch (NumberFormatException e) {
					return;
				}

				// just connected? OBD mode?
				if (hs != null && hs.getRoutineScan() != null) {
					if (newState >= OBD2Session.STATE_OBDCONNECTED) {

//						hs.getEBT().sendATCommand2("ATSH000751");

						// Add some datapoints to the "routine scan" which is an
						// automatic loop that continuously scans those PIDs.
						hs.setRoutineScanDelay(500);

						hs.getRoutineScan().addDPN("VOLTS");
						hs.getRoutineScan().addDPN("TEMP_INTAKE");
						hs.getRoutineScan().addDPN("TEMP_COOLANT");
						hs.getRoutineScan().addDPN("MAF_FLOW_RATE");
						hs.getRoutineScan().addDPN("FUEL_LEVEL");
						hs.getRoutineScan().addDPN("WIDEBAND_O2");
						hs.getRoutineScan().addDPN("CATALYST_TEMP_B1S1");
//						hs.getRoutineScan().addDPN("TPMS_PRES_1");
						
					} else {
						hs.getRoutineScan().removeAllDPNs();
					}
				}
			}// end of session state change handler.

			// Did we just perform detection stuff?
			if (dataName.equals(OOBMessageTypes.AUTODETECT_SUMMARY)) {
				if (hs.isDetectionValid() != true)
					return;
				
				SetupSessionBasedOnCapabilities();
			}// end of "if this is a autodetect summary"
		}// end of OOB event arrived handler function definition. 
	};// end of event handler definition. 


	/**
	 * Call this method upon connecting. We'll see what the capabilities are and set up the appropriate session.  
	 */
	private void SetupSessionBasedOnCapabilities() {
		
		if (hs.isDetectionValid() != true) {
			return;
		}
	
		// switch to OBD2 communications mode
		hs.setActiveSession(HybridSession.SESSION_TYPE_OBD2);
		
		// sanity check
		if (hs.getRoutineScan() == null) return;
	
		// add one or more datapoints to the routine scan class so that it actively scans that PID to generate DPN arrived events. 
		hs.setRoutineScanDelay(500);

		hs.getRoutineScan().addDPN("VOLTS");
		hs.getRoutineScan().addDPN("TEMP_INTAKE");
		hs.getRoutineScan().addDPN("TEMP_COOLANT");
		hs.getRoutineScan().addDPN("MAF_FLOW_RATE");
		hs.getRoutineScan().addDPN("FUEL_LEVEL");
		hs.getRoutineScan().addDPN("WIDEBAND_O2");
		hs.getRoutineScan().addDPN("CATALYST_TEMP_B1S1");
//		hs.getRoutineScan().addDPN("TPMS_PRES_1");
	}

	/**
	 * removes commas and takes just the first value, if present.
	 * 
	 * @param decodedData
	 * @return
	 */
//	private float getPrimaryDPNValue(String decodedData) {
//		return getPrimaryDPNValue(decodedData, 0);
//	}
	
	private float getPrimaryDPNValue(String decodedData, final float defaultValue) {
		float Y = 0f;

		// do our best to extract the data value.
		if (decodedData.contains(",")) {
			try {
				Y = Float.valueOf(decodedData.split(",")[0]);
			} catch (NumberFormatException e) {
			}
		} else {
			try {
				Y = Float.valueOf(decodedData);
			} catch (NumberFormatException e) {
			}
		}

		if (Y == 0) Y = defaultValue;
		return Y;
	}

	public HybridSession getHs() {
		return hs;
	}

	public float getVoltage() {
		return voltage;
	}

	public float getIat() {
		return (float) (1.8 * iat + 32);
	}

	public float getFuel() {
		return fuel;
	}

	public float getCoolant() {
		return (float) (1.8 * coolant + 32);
	}

	public float getMaf() {
		return maf;
	}
	
	public float getWideband() {
		return wideband;
	}

	public float getEgt() {
		return (float) (1.8 * egt + 32);
	}

	public String getTire1Pres() {
		return tire1Pres;
	}
	
	
}
