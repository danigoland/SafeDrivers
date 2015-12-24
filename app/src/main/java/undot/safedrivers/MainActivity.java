package undot.safedrivers;
import android.content.Context;
import android.os.Bundle;
import android.content.Intent;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import undot.safedrivers.BLE.BluetoothLeService;
import undot.safedrivers.BLE.BlunoLibrary;

public class MainActivity  extends BlunoLibrary {
    private Button buttonScan;
    private Button buttonSerialSend;
    private EditText serialSendText;
    private TextView serialReceivedText;
    private TextView rssiText;
    int rssi_value=0;
    int sensor_value_sum=0;
    int sensor_counter=0;
    int sensor_value=0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
      //  onCreateon();														//onCreate Process by BlunoLibrary

      //  serialBegin(115200);													//set the Uart Baudrate on BLE chip to 115200

        serialReceivedText=(TextView) findViewById(R.id.serialReveicedText);	//initial the EditText of the received data
        serialSendText=(EditText) findViewById(R.id.serialSendText);			//initial the EditText of the sending data
        rssiText = (TextView) findViewById(R.id.rssi);
        buttonSerialSend = (Button) findViewById(R.id.buttonSerialSend);		//initial the button for sending the data
        buttonSerialSend.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

                serialSend(serialSendText.getText().toString());				//send the data to the BLUNO
            }
        });

        buttonScan = (Button) findViewById(R.id.buttonScan);					//initial the button for scanning the BLE device
        buttonScan.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

                buttonScanOnClickProcess();										//Alert Dialog for selecting the BLE device
            }
        });
    }

    protected void onResume(){
        super.onResume();
        System.out.println("BlUNOActivity onResume");
        Intent gattServiceIntent = new Intent(getBaseContext(), BluetoothLeService.class);
        startService(gattServiceIntent);

//        onResumeProcess();														//onResume Process by BlunoLibrary
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        onActivityResultProcess(requestCode, resultCode, data);					//onActivityResult Process by BlunoLibrary
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
      //  onPauseProcess();														//onPause Process by BlunoLibrary
    }

    protected void onStop() {
        super.onStop();
        onStopProcess();														//onStop Process by BlunoLibrary
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onDestroyProcess();														//onDestroy Process by BlunoLibrary
    }

    @Override
    public void onConectionStateChange(connectionStateEnum theConnectionState) {//Once connection state changes, this function will be called
        switch (theConnectionState) {											//Four connection state
            case isConnected:
                buttonScan.setText("Connected");
                break;
            case isConnecting:
                buttonScan.setText("Connecting");
                break;
            case isToScan:
                buttonScan.setText("Scan");
                break;
            case isScanning:
                buttonScan.setText("Scanning");
                break;
            case isDisconnecting:
                buttonScan.setText("isDisconnecting");
                break;
            default:
                break;
        }
    }
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
    @Override
    public void onSerialReceived(String theString) {							//Once connection data received, this function will be called
        // TODO Auto-generated method stub
        String[] values = theString.split("\n");
        serialReceivedText.setText(values[0]);
        if (isNumeric(values[0].replaceAll("[^\\.0123456789]", "")))
            if(Integer.decode(values[0].replaceAll("[^\\.0123456789]", ""))==1 || Integer.decode(values[0].replaceAll("[^\\.0123456789]", ""))==0)
            {
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
        Log.d("rssi value",rssi_value+"");

        if (rssi_value<-80&&sensor_value==1)
        {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            // Vibrate for 500 milliseconds
            v.vibrate(500);
        }
        //append the text into the EditText
        //The Serial data from the BLUNO may be sub-packaged, so using a buffer to hold the String is a good choice.

    }
    @Override
    public void setRSSI(int rssi)
    {
        rssiText.setText("RSSI: "+rssi);
        rssi_value = rssi;
    }
}