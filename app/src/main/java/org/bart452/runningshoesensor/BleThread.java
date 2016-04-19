package org.bart452.runningshoesensor;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.Semaphore;

public class BleThread extends Thread {

    private Semaphore mSem;
    private Handler mHandler;
    private boolean isStopped = false;

    private int mCharIndex = 0;

    public BleThread(Semaphore sem) {
        this.mSem = sem;
    }

    public void startReadingChars(final BluetoothGatt gatt, final List<BluetoothGattCharacteristic> chars) {

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mSem.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(!isStopped) {
                    gatt.readCharacteristic(chars.get(mCharIndex));
                    mCharIndex = (mCharIndex + 1) % chars.size();
                    mHandler.postDelayed(this, 5);
                }
            }
        });
    }

    public void stopReadingChars() {
        isStopped = true;
    }

    @Override
    public void run() {
        Looper.prepare();
        mHandler = new Handler();
        Looper.loop();
    }
}
