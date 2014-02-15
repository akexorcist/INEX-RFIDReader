package app.akexorcist.rfidreader;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

public class Main extends Activity {
	private final String TAG = Main.class.getSimpleName();

    UsbSerialDriver mSerialDevice;
    UsbManager mUsbManager;

    TextView textData;
    Timer timer;
    String strData = "";
    boolean isBusy = false;
    
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    SerialInputOutputManager mSerialIoManager;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        textData = (TextView) findViewById(R.id.textData);
    }

    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (mSerialDevice != null) {
            try {
                mSerialDevice.close();
            } catch (IOException e) { }
            mSerialDevice = null;
        }
    }

    protected void onResume() {
        super.onResume();
        mSerialDevice = UsbSerialProber.acquire(mUsbManager);
        Log.d(TAG, "Resumed, mSerialDevice=" + mSerialDevice);
        if (mSerialDevice == null) {
            Toast.makeText(getApplicationContext()
            		, "Please connect to serial device", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(getApplicationContext()
            		, getVendorName(mSerialDevice.getDevice().getVendorId())
            		, Toast.LENGTH_SHORT).show();
            try {
                mSerialDevice.open();
            	mSerialDevice.setBaudRate(2400);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                try {
                    mSerialDevice.close();
                } catch (IOException e2) { }
                mSerialDevice = null;
                Toast.makeText(getApplicationContext()
                		, "Error setting up to device"
                		, Toast.LENGTH_SHORT).show();
            }
            
        }
        onDeviceStateChange();
    }
    
    private String getVendorName(int vendorId) {
    	if(vendorId == 1027) 
    		return "FTDI FT232R UART";
    	else if(vendorId == 9025)
    		return "Arduino";
    	else if(vendorId == 5824)
    		return "Teensyduino";
    	else if(vendorId == 4292)
    		return "CP210x UART Bridge";
    	else if(vendorId == 1240)
    		return "MCP2200";
    	
    	return "Unknown";
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (mSerialDevice != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(mSerialDevice, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {
        public void onRunError(Exception e) { }

        public void onNewData(final byte[] data) {
        	if(!isBusy) {
                updateReceivedData(data);
        	}
        }
    };
    
    public void clearText() {
    	runOnUiThread(new Runnable() {
            public void run() {
        		strData = "";
        		textData.setText("");
            }
        });
    }
    
    public void playSound() {
    	AssetFileDescriptor afd;
		try {
			afd = getAssets().openFd("sound/get_data.wav");
	    	MediaPlayer player = new MediaPlayer();
	        player.setDataSource(afd.getFileDescriptor()
	        		, afd.getStartOffset(), afd.getLength());
	        player.prepare();
	        player.start();
		} catch (IOException e) { }
    }
    
    public void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) { }
    }
    
    public void setText(final TextView v, final String str) {
    	runOnUiThread(new Runnable() {
            public void run() {
        		v.setText(str);
            }
        });
    }

    private void updateReceivedData(final byte[] data) {
    	if(timer != null) {
    		timer.cancel();
    	}
    	
    	timer = new Timer();
    	timer.schedule(new TimerTask() {
    	    public void run() {
    	    	clearText();
    	    }
    	}, 1000);

    	for(int i = 0 ; i < data.length ; i++) {

        	Log.i("Check", String.valueOf(data[i]));
    	}
    	strData += new String(data);
    	if(strData.length() >= 40) {
    		new Thread(new Runnable() {
    			public void run() {
    	    		isBusy = true;
    	    		timer.cancel();
    	    		String[] strArray = strData.split(new String(new byte[] { 0x0D, 0x0A }));
    	    		
    				if(strArray[1].equals(strArray[2])) {
    					String id = strArray[1];
    					playSound();
    					setText(textData, id);
    					sleep(3000);
    				}

        	    	clearText();
        	    	sleep(1000);
					setText(textData, "Tap your card");
    	    		isBusy = false;
    			}
    		}).start();
    	}
    }
}