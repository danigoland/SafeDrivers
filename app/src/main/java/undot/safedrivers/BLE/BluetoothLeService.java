/*
 * Copyright (C) 2013 The Android Open Source Project
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

package undot.safedrivers.BLE;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import undot.safedrivers.R;


/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private static BluetoothGattCharacteristic mSCharacteristic, mModelNumberCharacteristic, mSerialPortCharacteristic, mCommandCharacteristic;
    BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mScanning =false;
    AlertDialog mScanDeviceDialog;
    private String mDeviceName;
    private String mDeviceAddress;
    public enum connectionStateEnum{isNull, isScanning, isToScan, isConnecting , isConnected, isDisconnecting};
    private static final int REQUEST_ENABLE_BT = 1;

    private Handler mHandler= new Handler();

    public boolean mConnected = false;
    private int mBaudrate=115200;	//set the default baud rate to 115200
    private String mPassword="AT+PASSWOR=DFRobot\r\n";


    private String mBaudrateBuffer = "AT+CURRUART="+mBaudrate+"\r\n";


    boolean notified=false;
    public static final String SerialPortUUID="0000dfb1-0000-1000-8000-00805f9b34fb";
    public static final String CommandUUID="0000dfb2-0000-1000-8000-00805f9b34fb";
    public static final String ModelNumberStringUUID="00002a24-0000-1000-8000-00805f9b34fb";


    int rssi_value=0;
    int sensor_value_sum=0;
    int sensor_counter=0;
    int sensor_value=0;
    int rssicounter=0;


    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
  public static   BluetoothGatt mBluetoothGatt;
    public String mBluetoothDeviceAddress;
    
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    public int mConnectionState = STATE_DISCONNECTED;

    
    //To tell the onCharacteristicWrite call back function that this is a new characteristic, 
    //not the Write Characteristic to the device successfully.
    private static final int WRITE_NEW_CHARACTERISTIC = -1;
    //define the limited length of the characteristic.
    private static final int MAX_CHARACTERISTIC_LENGTH = 17;
    //Show that Characteristic is writing or not.
    private boolean mIsWritingCharacteristic=false;

    //class to store the Characteristic and content string push into the ring buffer.
    private class BluetoothGattCharacteristicHelper{
    	BluetoothGattCharacteristic mCharacteristic;
    	String mCharacteristicValue;
    	BluetoothGattCharacteristicHelper(BluetoothGattCharacteristic characteristic, String characteristicValue){
    		mCharacteristic=characteristic;
    		mCharacteristicValue=characteristicValue;
    	}
    }
    //ring buffer
    private RingBuffer<BluetoothGattCharacteristicHelper> mCharacteristicRingBuffer = new RingBuffer<BluetoothGattCharacteristicHelper>(8);
    
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
//    public final static UUID UUID_HEART_RATE_MEASUREMENT =
//            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);


    @Override
    public void onCreate() {
        super.onCreate();
        initialize();

    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            System.out.println("BluetoothGattCallback----onConnectionStateChange"+newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                // Attempts to discover services after successful connection.
                if(mBluetoothGatt.discoverServices())
                {
                    Log.i(TAG, "Attempting to start service discovery:");

                }
                else{
                    Log.i(TAG, "Attempting to start service discovery:not success");

                }


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.d("rssionremote", rssi + "");
            if(rssi<-90) {
              //  rssi_value = rssi;
                rssicounter++;
            }
            //broadcastUpdate(rssi);
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        	System.out.println("onServicesDiscovered "+status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                for (BluetoothGattService gattService : getSupportedGattServices()) {
                    System.out.println("ACTION_GATT_SERVICES_DISCOVERED  "+
                            gattService.getUuid().toString());
                }
                getGattServices(getSupportedGattServices());
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
        	//this block should be synchronized to prevent the function overloading
			synchronized(this)
			{
				//CharacteristicWrite success
	        	if(status == BluetoothGatt.GATT_SUCCESS)
	        	{
	        		System.out.println("onCharacteristicWrite success:"+ new String(characteristic.getValue()));
            		if(mCharacteristicRingBuffer.isEmpty())
            		{
    	        		mIsWritingCharacteristic = false;
            		}
            		else
	            	{
	            		BluetoothGattCharacteristicHelper bluetoothGattCharacteristicHelper = mCharacteristicRingBuffer.next();
	            		if(bluetoothGattCharacteristicHelper.mCharacteristicValue.length() > MAX_CHARACTERISTIC_LENGTH)
	            		{
	            	        try {
		            			bluetoothGattCharacteristicHelper.mCharacteristic.setValue(bluetoothGattCharacteristicHelper.mCharacteristicValue.substring(0, MAX_CHARACTERISTIC_LENGTH).getBytes("ISO-8859-1"));

	            	        } catch (UnsupportedEncodingException e) {
	            	            // this should never happen because "US-ASCII" is hard-coded.
	            	            throw new IllegalStateException(e);
	            	        }
	            			
	            			
	            	        if(mBluetoothGatt.writeCharacteristic(bluetoothGattCharacteristicHelper.mCharacteristic))
	            	        {
	            	        	System.out.println("writeCharacteristic init "+new String(bluetoothGattCharacteristicHelper.mCharacteristic.getValue())+ ":success");
	            	        }else{
	            	        	System.out.println("writeCharacteristic init "+new String(bluetoothGattCharacteristicHelper.mCharacteristic.getValue())+ ":failure");
	            	        }
	            			bluetoothGattCharacteristicHelper.mCharacteristicValue = bluetoothGattCharacteristicHelper.mCharacteristicValue.substring(MAX_CHARACTERISTIC_LENGTH);
	            		}
	            		else
	            		{
	            	        try {
	            	        	bluetoothGattCharacteristicHelper.mCharacteristic.setValue(bluetoothGattCharacteristicHelper.mCharacteristicValue.getBytes("ISO-8859-1"));
	            	        } catch (UnsupportedEncodingException e) {
	            	            // this should never happen because "US-ASCII" is hard-coded.
	            	            throw new IllegalStateException(e);
	            	        }
	            			
	            	        if(mBluetoothGatt.writeCharacteristic(bluetoothGattCharacteristicHelper.mCharacteristic))
	            	        {
	            	        	System.out.println("writeCharacteristic init "+new String(bluetoothGattCharacteristicHelper.mCharacteristic.getValue())+ ":success");
	            	        }else{
	            	        	System.out.println("writeCharacteristic init "+new String(bluetoothGattCharacteristicHelper.mCharacteristic.getValue())+ ":failure");
	            	        }
	            			bluetoothGattCharacteristicHelper.mCharacteristicValue = "";

//	            			System.out.print("before pop:");
//	            			System.out.println(mCharacteristicRingBuffer.size());
	            			mCharacteristicRingBuffer.pop();
//	            			System.out.print("after pop:");
//	            			System.out.println(mCharacteristicRingBuffer.size());
	            		}
	            	}
	        	}
	        	//WRITE a NEW CHARACTERISTIC
	        	else if(status == WRITE_NEW_CHARACTERISTIC)
	        	{
	        		if((!mCharacteristicRingBuffer.isEmpty()) && mIsWritingCharacteristic==false)
	            	{
	            		BluetoothGattCharacteristicHelper bluetoothGattCharacteristicHelper = mCharacteristicRingBuffer.next();
	            		if(bluetoothGattCharacteristicHelper.mCharacteristicValue.length() > MAX_CHARACTERISTIC_LENGTH)
	            		{
	            			
	            	        try {
		            			bluetoothGattCharacteristicHelper.mCharacteristic.setValue(bluetoothGattCharacteristicHelper.mCharacteristicValue.substring(0, MAX_CHARACTERISTIC_LENGTH).getBytes("ISO-8859-1"));
	            	        } catch (UnsupportedEncodingException e) {
	            	            // this should never happen because "US-ASCII" is hard-coded.
	            	            throw new IllegalStateException(e);
	            	        }
	            			
	            	        if(mBluetoothGatt.writeCharacteristic(bluetoothGattCharacteristicHelper.mCharacteristic))
	            	        {
	            	        	System.out.println("writeCharacteristic init "+new String(bluetoothGattCharacteristicHelper.mCharacteristic.getValue())+ ":success");
	            	        }else{
	            	        	System.out.println("writeCharacteristic init "+new String(bluetoothGattCharacteristicHelper.mCharacteristic.getValue())+ ":failure");
	            	        }
	            			bluetoothGattCharacteristicHelper.mCharacteristicValue = bluetoothGattCharacteristicHelper.mCharacteristicValue.substring(MAX_CHARACTERISTIC_LENGTH);
	            		}
	            		else
	            		{
	            	        try {
		            			bluetoothGattCharacteristicHelper.mCharacteristic.setValue(bluetoothGattCharacteristicHelper.mCharacteristicValue.getBytes("ISO-8859-1"));
	            	        } catch (UnsupportedEncodingException e) {
	            	            // this should never happen because "US-ASCII" is hard-coded.
	            	            throw new IllegalStateException(e);
	            	        }
	            			

	            	        if(mBluetoothGatt.writeCharacteristic(bluetoothGattCharacteristicHelper.mCharacteristic))
	            	        {
	            	        	System.out.println("writeCharacteristic init "+new String(bluetoothGattCharacteristicHelper.mCharacteristic.getValue())+ ":success");
//	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[0]);
//	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[1]);
//	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[2]);
//	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[3]);
//	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[4]);
//	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[5]);

	            	        }else{
	            	        	System.out.println("writeCharacteristic init "+new String(bluetoothGattCharacteristicHelper.mCharacteristic.getValue())+ ":failure");
	            	        }
	            			bluetoothGattCharacteristicHelper.mCharacteristicValue = "";

//		            			System.out.print("before pop:");
//		            			System.out.println(mCharacteristicRingBuffer.size());
		            			mCharacteristicRingBuffer.pop();
//		            			System.out.print("after pop:");
//		            			System.out.println(mCharacteristicRingBuffer.size());
	            		}
	            	}
	        		
    	        	mIsWritingCharacteristic = true;
    	        	
    	        	//clear the buffer to prevent the lock of the mIsWritingCharacteristic
    	        	if(mCharacteristicRingBuffer.isFull())
    	        	{
    	        		mCharacteristicRingBuffer.clear();
        	        	mIsWritingCharacteristic = false;
    	        	}
	        	}
	        	else
					//CharacteristicWrite fail
	        	{
	        		mCharacteristicRingBuffer.clear();
	        		System.out.println("onCharacteristicWrite fail:"+ new String(characteristic.getValue()));
	        		System.out.println(status);
	        	}
			}
        }
        
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.readRemoteRssi();
                System.out.println("onCharacteristicRead  " + characteristic.getUuid().toString());
                if(mSCharacteristic==mModelNumberCharacteristic)
                {
                    if (new String(characteristic.getValue()).toUpperCase().startsWith("DF BLUNO")) {
                        setCharacteristicNotification(mSCharacteristic, false);
                        mSCharacteristic=mCommandCharacteristic;
                        mSCharacteristic.setValue(mPassword);
                        writeCharacteristic(mSCharacteristic);
                        mSCharacteristic.setValue(mBaudrateBuffer);
                        writeCharacteristic(mSCharacteristic);
                        mSCharacteristic=mSerialPortCharacteristic;
                        setCharacteristicNotification(mSCharacteristic, true);
                        mConnectionState = STATE_CONNECTED;

                    }
                    else {
                        Toast.makeText(getBaseContext(), "Please select DFRobot devices",Toast.LENGTH_SHORT).show();
                    }
                }
                else if (mSCharacteristic==mSerialPortCharacteristic) {
                //    Log.d("data",new String(characteristic.getValue()));
                }                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }
        @Override
        public void  onDescriptorWrite(BluetoothGatt gatt, 
        								BluetoothGattDescriptor characteristic,
        								int status){
        	System.out.println("onDescriptorWrite  "+characteristic.getUuid().toString()+" "+status);
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
        	//System.out.println("onCharacteristicChanged  "+new String(characteristic.getValue()));
           //0 Log.d("data", new String(characteristic.getValue()));
            gatt.readRemoteRssi();
            // TODO Auto-generated method stub
            String[] values = new String[1];
            values[0] = new String(characteristic.getValue()).substring(0,1);


              //  Log.d("sitting",Integer.decode(values[0].replaceAll("[^\\.0123456789]", "").substring(0,1))+"");

                if(Integer.decode(values[0].replaceAll("[^\\.0123456789]", ""))==1 || Integer.decode(values[0].replaceAll("[^\\.0123456789]", ""))==0)
                {
                   // Log.d("is","numeric");
                    sensor_value_sum+=Integer.decode(values[0].replaceAll("[^\\.0123456789]", ""));
                    sensor_counter++;
                    if(sensor_counter==10)
                    {
                        sensor_value= (sensor_value_sum/sensor_counter);
                        Log.d("sensor value",sensor_value_sum+" "+ sensor_counter);
                        sensor_counter=0;
                        sensor_value_sum=0;

                    }
                }
            Log.d("rssicounter", rssicounter + "");

            if (rssicounter>10&&sensor_value==1)
            {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                // Vibrate for 500 milliseconds
                v.vibrate(500);
                if(!notified) {
                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(getBaseContext())
                                    .setSmallIcon(R.mipmap.ic_launcher)
                                    .setContentTitle("תינוק באוטו")
                                    .setContentText("רוץ קח אותו!");
                    // Sets an ID for the notification
                    int mNotificationId = 001;
// Gets an instance of the NotificationManager service
                    NotificationManager mNotifyMgr =
                            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
// Builds the notification and issues it.
                    mNotifyMgr.notify(mNotificationId, mBuilder.build());
                    notified=true;
                }
                rssicounter=0;
            }
            else if(rssicounter>10)
            {
                rssicounter=0;
            }
           // broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    public static boolean isNumeric(String str)
    {
        try
        {
            double d = Double.parseDouble(str);
        }
        catch(NumberFormatException nfe)
        {
            return false;
        }
        return true;
    }
    
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        System.out.println("BluetoothLeService broadcastUpdate");
        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
//        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
//            int flag = characteristic.getProperties();
//            int format = -1;
//            if ((flag & 0x01) != 0) {
//                format = BluetoothGattCharacteristic.FORMAT_UINT16;
//                Log.d(TAG, "Heart rate format UINT16.");
//            } else {
//                format = BluetoothGattCharacteristic.FORMAT_UINT8;
//                Log.d(TAG, "Heart rate format UINT8.");
//            }
//            final int heartRate = characteristic.getIntValue(format, 1);
//            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
//            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
//        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                intent.putExtra(EXTRA_DATA, new String(data));
        		sendBroadcast(intent);
            }
//        }
    }
    private void broadcastUpdate(int rssi)
    {
        final Intent intent = new Intent(ACTION_DATA_AVAILABLE);
        intent.putExtra("RSSI", rssi);
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
    	System.out.println("BluetoothLeService initialize"+mBluetoothManager);
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if(mBluetoothAdapter!=null)
        {
            mBluetoothAdapter.startLeScan(mLeScanCallback);

        }
        else  {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
    	System.out.println("BluetoothLeService connect"+address+mBluetoothGatt);
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
//        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
//                && mBluetoothGatt != null) {
//            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
//            if (mBluetoothGatt.connect()) {
//            	System.out.println("mBluetoothGatt connect");
//                mConnectionState = STATE_CONNECTING;
//                return true;
//            } else {
//            	System.out.println("mBluetoothGatt else connect");
//                return false;
//            }
//        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        System.out.println("device.connectGatt connect");
		synchronized(this)
		{
			mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		}
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
    	System.out.println("BluetoothLeService disconnect");
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
    	System.out.println("BluetoothLeService close");
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }
    

    /**
     * Write information to the device on a given {@code BluetoothGattCharacteristic}. The content string and characteristic is 
     * only pushed into a ring buffer. All the transmission is based on the {@code onCharacteristicWrite} call back function, 
     * which is called directly in this function
     *
     * @param characteristic The characteristic to write to.
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        
    	//The character size of TI CC2540 is limited to 17 bytes, otherwise characteristic can not be sent properly,
    	//so String should be cut to comply this restriction. And something should be done here:
        String writeCharacteristicString;
        try {
        	writeCharacteristicString = new String(characteristic.getValue(),"ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            // this should never happen because "US-ASCII" is hard-coded.
            throw new IllegalStateException(e);
        }
        System.out.println("allwriteCharacteristicString:"+writeCharacteristicString);
        
        //As the communication is asynchronous content string and characteristic should be pushed into an ring buffer for further transmission
    	mCharacteristicRingBuffer.push(new BluetoothGattCharacteristicHelper(characteristic,writeCharacteristicString) );
    	System.out.println("mCharacteristicRingBufferlength:"+mCharacteristicRingBuffer.size());


    	//The progress of onCharacteristicWrite and writeCharacteristic is almost the same. So callback function is called directly here
    	//for details see the onCharacteristicWrite function
    	mGattCallback.onCharacteristicWrite(mBluetoothGatt, characteristic, WRITE_NEW_CHARACTERISTIC);

    }    
    
    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        //BluetoothGattDescriptor descriptor = characteristic.getDescriptor(characteristic.getUuid());
        //descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        //mBluetoothGatt.writeDescriptor(descriptor);
    	
        // This is specific to Heart Rate Measurement.
//        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
//            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
//                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
//            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//            mBluetoothGatt.writeDescriptor(descriptor);
//        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             byte[] scanRecord) {
            if(device.getName()!=null) {
                Log.d("device", device.getName() + "");
                if (device.getName().equals("BlunoV1.8")) {
                    connect(device.getAddress());
                   // Log.d("rssi service",rssi+"");

                }
            }
            }

    };

    private void getGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        mModelNumberCharacteristic=null;
        mSerialPortCharacteristic=null;
        mCommandCharacteristic=null;
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();
            System.out.println("displayGattServices + uuid="+uuid);

            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                uuid = gattCharacteristic.getUuid().toString();
                if(uuid.equals(ModelNumberStringUUID)){
                    mModelNumberCharacteristic=gattCharacteristic;
                    System.out.println("mModelNumberCharacteristic  "+mModelNumberCharacteristic.getUuid().toString());
                }
                else if(uuid.equals(SerialPortUUID)){
                    mSerialPortCharacteristic = gattCharacteristic;
                    System.out.println("mSerialPortCharacteristic  "+mSerialPortCharacteristic.getUuid().toString());
//                    updateConnectionState(R.string.comm_establish);
                }
                else if(uuid.equals(CommandUUID)){
                    mCommandCharacteristic = gattCharacteristic;
                    System.out.println("mSerialPortCharacteristic  "+mSerialPortCharacteristic.getUuid().toString());
//                    updateConnectionState(R.string.comm_establish);
                }
            }
            mGattCharacteristics.add(charas);
        }

        if (mModelNumberCharacteristic==null || mSerialPortCharacteristic==null || mCommandCharacteristic==null) {
            Log.d("charactaristics","null");
            Toast.makeText(getApplicationContext(),"characteristics null",Toast.LENGTH_SHORT);
        }
        else {
            mSCharacteristic=mModelNumberCharacteristic;
            setCharacteristicNotification(mSCharacteristic, true);
            readCharacteristic(mSCharacteristic);
        }

    }


}
