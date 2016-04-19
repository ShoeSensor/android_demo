/*
 * Copyright 2016 Bart Monhemius.
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

package org.bart452.runningshoesensor;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final static int SCAN_PERIOD = 5000;
    private final static int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private final static String LOG_TAG = "MainActivity";
    private final static String ACC_UUID_SERVICE = "1bc56726-0200-658c-e511-21f700cca137";
    private final static String ACC_UUID_X_CHAR = "1bc56727-0200-658c-e511-21f700cca137";
    private final static String ACC_UUID_Y_CHAR = "1bc56728-0200-658c-e511-21f700cca137";
    private final static int BLE_X_MSG = 1;
    private final static int BLE_Y_MSG = 2;
    private final static int BLE_STOP_MSG = 3;

    private static Context context;

    private BluetoothAdapter mBleAdapter;
    private BluetoothLeScanner mBleScanner;
    private BluetoothDevice mBleDevice = null;
    private BluetoothGattService mBleService;
    private BluetoothGatt mBleGatt;
    private BluetoothGattCharacteristic mBleXChar;
    private BluetoothGattCharacteristic mBleYChar;
    private List<BluetoothGattCharacteristic> mCharList;
    private Semaphore mBleSem;
    private BleThread mBleThread;

    private BroadcastReceiver mBleReceiver;

    private TextView mDevNameTv;
    private TextView mRssiTv;
    private TextView mAddrTv;
    private Switch mConnSwitch;
    private Snackbar mSnackBar;

    private ImageView mHeaderImageView;

    private CollapsingToolbarLayout mCollapsingTb;
    private Toolbar mToolbar;

    private GraphView mDataGraph;
    private LineGraphSeries<DataPoint> mDataXSeries;
    private LineGraphSeries<DataPoint> mDataYSeries;

    private RotateAnimation mRotateAnim;
    private FloatingActionButton mScanFab;

    protected long curTime;

    private Handler mBleHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
                double time = (double)(System.currentTimeMillis() - curTime) / 1000;
                mDataGraph.getViewport().setMinX(time - 5);
                switch (msg.what) {
                    case BLE_X_MSG:
                        mDataXSeries.appendData(new DataPoint(time, (double)msg.arg1), true, 50);
                        break;
                    case BLE_Y_MSG:
                        mDataYSeries.appendData(new DataPoint(time, (double)msg.arg1), true, 50);
                        break;
                    case BLE_STOP_MSG:
                        mConnSwitch.setChecked(false);
                        break;
                }
            return true;
        }
    });

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            mBleDevice = result.getDevice();
            mDevNameTv.setText(getString(R.string.device_name) + " "  + result.getDevice().getName());
            mRssiTv.setText(String.format(getString(R.string.rssi_name) + " %d", result.getRssi()));
            mAddrTv.setText(getString(R.string.address_name) + " " + result.getDevice().getAddress());
            mConnSwitch.setEnabled(true);
            mBleScanner.stopScan(this);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            mConnSwitch.setEnabled(false);
        }
    };

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            } else {
                Message msg = Message.obtain();
                msg.what = BLE_STOP_MSG;
                msg.setTarget(mBleHandler);
                msg.sendToTarget();
                mBleThread.stopReadingChars();
            }
            super.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(LOG_TAG, "Found services");
            mBleService = gatt.getService(UUID.fromString(ACC_UUID_SERVICE));
            mBleXChar = mBleService.getCharacteristic(UUID.fromString(ACC_UUID_X_CHAR));
            mBleYChar = mBleService.getCharacteristic(UUID.fromString(ACC_UUID_Y_CHAR));
            mCharList.addAll(Arrays.asList(mBleXChar, mBleYChar));
            mBleThread.startReadingChars(gatt, mCharList);
            curTime = System.currentTimeMillis();
            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            int i = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            Message msg = Message.obtain();
            msg.arg1 = i;
            if(characteristic.equals(mBleXChar))
                msg.what = BLE_X_MSG;
            else if(characteristic.equals(mBleYChar))
                msg.what = BLE_Y_MSG;
            msg.setTarget(mBleHandler);
            msg.sendToTarget();
            mBleSem.release();
            super.onCharacteristicRead(gatt, characteristic, status);
        }
    };

    private void bleScanDevices() {
        mBleHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(mBleAdapter.isEnabled())
                    mBleScanner.stopScan(mScanCallback);
                if(!mConnSwitch.isEnabled())
                    Toast.makeText(context, "Stopped scanning", Toast.LENGTH_SHORT).show();
            }
        }, SCAN_PERIOD);
        mBleScanner.startScan(mScanCallback);
    }

    private void bleEnable() {
        if(!mBleAdapter.isEnabled()) {
            mSnackBar = Snackbar.make(
                    (CoordinatorLayout)findViewById(R.id.coordinator),
                    "Please enable Bluetooth",
                    Snackbar.LENGTH_INDEFINITE);
            mSnackBar.setAction("Enable", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivity(i);
                    mScanFab.setEnabled(true);
                }
            });
            mSnackBar.show();
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSION_REQUEST_COARSE_LOCATION);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermission();
        context = getApplicationContext();

        mHeaderImageView = (ImageView) findViewById(R.id.appBarIv);
        Picasso.with(context)
                .load(R.drawable.sunset_road_landscape)
                .into(mHeaderImageView);

        // Toolbar
        mToolbar = (Toolbar) findViewById(R.id.toolBar);
        setSupportActionBar(mToolbar);
        mCollapsingTb = (CollapsingToolbarLayout) findViewById(R.id.collapsingToolbar);
        mCollapsingTb.setTitle("Shoe sensor");
        mCollapsingTb.setExpandedTitleGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);

        // Bluetooth
        final BluetoothManager mBleMan = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBleAdapter = mBleMan.getAdapter();
        mBleScanner = mBleAdapter.getBluetoothLeScanner();
        mCharList = new ArrayList<>();
        mBleSem = new Semaphore(1);
        mBleThread = new BleThread(mBleSem);
        mBleThread.start();

        mBleReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                    if(mBleAdapter.getState() == BluetoothAdapter.STATE_ON) {
                        Log.d(LOG_TAG, "BT ON");
                        mScanFab.setEnabled(true);
                        if(mSnackBar != null) {
                            if(mSnackBar.isShown())
                                mSnackBar.dismiss();
                        }
                    } else {
                        mScanFab.setEnabled(false);
                        bleEnable();
                    }
                }
            }
        };

        // Text
        mDevNameTv = (TextView)findViewById(R.id.devNameTv);
        mDevNameTv.setText(getString(R.string.device_name) + " No device found");
        mRssiTv = (TextView)findViewById(R.id.rssiTv);
        mAddrTv = (TextView)findViewById(R.id.devAddrTv);

        // Buttons and switches
        mScanFab = (FloatingActionButton)findViewById(R.id.scanFab);
        mScanFab.setOnClickListener(this);
        mConnSwitch = (Switch) findViewById(R.id.connSwitch);
        mConnSwitch.setOnClickListener(this);

        //Graph
        mDataGraph = (GraphView)findViewById(R.id.dataGraph);
        mDataXSeries = new LineGraphSeries<>();
        mDataYSeries = new LineGraphSeries<>();
        mDataYSeries.setBackgroundColor(Color.RED);
        mDataGraph.addSeries(mDataXSeries);
        mDataGraph.addSeries(mDataYSeries);
        mDataGraph.getViewport().setXAxisBoundsManual(true);
        mDataGraph.getViewport().setMinX(0);
        mDataGraph.getViewport().setMaxX(20);

        // Animation
        mRotateAnim = new RotateAnimation(0, 720, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mRotateAnim.setDuration(SCAN_PERIOD);
        mRotateAnim.setInterpolator(new LinearInterpolator());
        bleEnable();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if ((requestCode == PERMISSION_REQUEST_COARSE_LOCATION) &&
                (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            Log.d(LOG_TAG, "coarse location permission granted");
        } else {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.permission_failed_title));
            builder.setMessage(getString(R.string.permission_failed_messsage));
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    System.exit(0);
                }
            });
            builder.show();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.scanFab:
                mScanFab.startAnimation(mRotateAnim);
                bleScanDevices();
                break;
            case R.id.connSwitch:
                if(mConnSwitch.isChecked())
                    mBleGatt = mBleDevice.connectGatt(context, true, mGattCallback);
                else
                    mBleGatt.disconnect();
                break;
        }
    }

    @Override
    protected void onResume() {
        registerReceiver(mBleReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        super.onResume();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mBleReceiver);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mBleReceiver);
        super.onDestroy();
    }
}
