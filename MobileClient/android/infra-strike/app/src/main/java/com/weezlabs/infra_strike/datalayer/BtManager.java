package com.weezlabs.infra_strike.datalayer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/**
 * Created by WeezLabs on 4/10/16.
 */
public class BtManager {


    public interface OnGetDataListener {
        void onGotData(String data);
    }

    private static String currentIrCode_ = "";
    private static BluetoothDevice currentDevice_;
    private static OnGetDataListener currentListener_;

    static Thread workerThread_;
    static byte[] readBuffer_;
    static int readBufferPosition_;
    static volatile boolean stopWorker_;
    static InputStream mmInputStream_;
    static BluetoothSocket mmSocket_;

    public static boolean isEnabled() {
        return getAdapter().isEnabled();
    }

    public static ArrayList<BluetoothDevice> getDevicesList(){
        Set<BluetoothDevice> pairedDevices = getAdapter().getBondedDevices();
        return new ArrayList<>(pairedDevices);
    }

    /** @return true if ok, or launch activity to turn on BT */
    public static boolean checkBtOrEnable(Activity activity, int requestCode) {
        if (!isEnabled()) {
            Intent turnOnIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(turnOnIntent, requestCode);
            return false;
        } else {
            return true;
        }
    }

    public static String getCurrentIrCode() {
        return currentIrCode_;
    }

    private static BluetoothAdapter getAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    public static void beginListenForData(BluetoothDevice device, OnGetDataListener listener) {
        currentDevice_ = device;
        currentListener_ = listener;

        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        try {
            mmSocket_ = currentDevice_.createRfcommSocketToServiceRecord(uuid);
            mmSocket_.connect();
            mmInputStream_ = mmSocket_.getInputStream();
        } catch (IOException e) {
            //OMG!
            Log.e("IRS", "failed to start");
            closeBT();
            return;
        }

        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker_ = false;
        readBufferPosition_ = 0;
        readBuffer_ = new byte[1024];
        workerThread_ = new Thread(new Runnable() {
            public void run() {
                while(!Thread.currentThread().isInterrupted() && !stopWorker_) {
                    try {
                        int bytesAvailable = mmInputStream_.available();
                        if(bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream_.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++) {
                                byte b = packetBytes[i];
                                if(b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition_];
                                    System.arraycopy(readBuffer_, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition_ = 0;

                                    if (data.length() > 7 && data.length() <10 && !data.startsWith("FFFFFFFF")) {
                                        handler.post(new Runnable() {
                                            public void run() {
                                                currentIrCode_ = data.trim().substring(0, 7);
                                                currentListener_.onGotData(currentIrCode_);
                                            }
                                        });
                                    }
                                } else {
                                    readBuffer_[readBufferPosition_++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        stopWorker_ = true;
                        Log.e("IRS", "failed to read");
                    }
                }
            }
        });

        workerThread_.start();
    }

    public static void closeBT() {
        stopWorker_ = true;
        try {
            if (mmInputStream_ != null) {
                mmInputStream_.close();
            }
        } catch (IOException e) {
            //Ohhhh nooo!
            Log.e("IRS", "failed to stop");
        }
    }

    public static String keepAlive() {
        if (mmSocket_ == null) {
            return "No socket";
        }

        if (mmInputStream_ == null) {
            return "No stream";
        }

        if (workerThread_ == null) {
            return "No thread";
        }

        try {
            if (mmInputStream_.available()>0) {
                return "available data";
            } else {
                return "no data";
            }
        } catch (IOException e) {
            closeBT();
            beginListenForData(currentDevice_, currentListener_);
            return "Socket Error";
        }
    }
}
