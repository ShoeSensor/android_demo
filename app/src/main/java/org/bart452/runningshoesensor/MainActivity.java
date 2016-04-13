package org.bart452.runningshoesensor;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
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
import android.widget.*;
import com.squareup.picasso.Picasso;

import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final static int SCAN_PERIOD = 5000;
    private final static int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private final static String LOG_TAG = "MainActivity";
    private final static String ACC_UUID_SERVICE = "1bc56726-0200-658c-e511-21f700cca137";
    private final static String ACC_UUID_X_CHAR = "1bc56727-0200-658c-e511-21f700cca137";
    private final static String ACC_UUID_Y_CHAR = "1bc56728-0200-658c-e511-21f700cca137";


    private static Context context;

    private BluetoothAdapter mBleAdapter;
    private BluetoothLeScanner mBleScanner;
    private Handler mBleHandler;
    private BluetoothDevice mBleDevice = null;
    private BluetoothGattService mBleService;
    private BluetoothGattCharacteristic mBleXChar;
    private BluetoothGattCharacteristic mBleYChar;


    private TextView mDevNameTv;
    private TextView mRssiTv;
    private TextView mAddrTv;
    private Switch mConnSwitch;

    private ImageView mHeaderImageView;
    private ImageView mBleImageView;
    private ImageView mAnalysisImageView;

    private CollapsingToolbarLayout mCollapsingTb;
    private Toolbar mToolbar;

    private RotateAnimation mRotateAnim;
    private FloatingActionButton scanFab;

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
                Log.d(LOG_TAG, "Connected to BLE device");
                gatt.discoverServices();
            } else {
                mConnSwitch.setChecked(false);
            }
            super.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(LOG_TAG, "Found services");
            mBleService = gatt.getService(UUID.fromString(ACC_UUID_SERVICE));
            mBleXChar = mBleService.getCharacteristic(UUID.fromString(ACC_UUID_X_CHAR));
            mBleYChar = mBleService.getCharacteristic(UUID.fromString(ACC_UUID_Y_CHAR));
            bleReadServices(mBleXChar, mBleYChar, gatt);
            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(characteristic.equals(mBleXChar))
                Log.d(LOG_TAG, "x char value: " + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
            else if(characteristic.equals(mBleYChar))
                Log.d(LOG_TAG, "y char value: " + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
            super.onCharacteristicRead(gatt, characteristic, status);
        }
    };

    private void bleScanDevices() {
        mBleHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBleScanner.stopScan(mScanCallback);
                Toast.makeText(context, "Stopped scanning", Toast.LENGTH_SHORT).show();
            }
        }, SCAN_PERIOD);
        mBleScanner.startScan(mScanCallback);
    }

    private void bleReadServices(final BluetoothGattCharacteristic xChar,
                                 final BluetoothGattCharacteristic yChar, final BluetoothGatt gatt) {
        mBleHandler.post(new Runnable() {
            @Override
            public void run() {
                gatt.readCharacteristic(xChar);
                mBleHandler.postDelayed(this, 10);
            }
        });
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
        mBleImageView = (ImageView)findViewById(R.id.bleIv);
        mAnalysisImageView = (ImageView)findViewById(R.id.analysisIv);

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
        mBleHandler = new Handler();

        // Text
        mDevNameTv = (TextView)findViewById(R.id.devNameTv);
        mDevNameTv.setText(getString(R.string.device_name) + " No device found");
        mRssiTv = (TextView)findViewById(R.id.rssiTv);
        mAddrTv = (TextView)findViewById(R.id.devAddrTv);

        // Buttons and switches
        scanFab = (FloatingActionButton)findViewById(R.id.scanFab);
        scanFab.setOnClickListener(this);
        mConnSwitch = (Switch) findViewById(R.id.connSwitch);
        mConnSwitch.setOnClickListener(this);

        // Animation
        mRotateAnim = new RotateAnimation(0, 720, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mRotateAnim.setDuration(SCAN_PERIOD);
        mRotateAnim.setInterpolator(new LinearInterpolator());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Picasso.with(context)
                .load(R.drawable.sunset_road_landscape)
                .into(mHeaderImageView);
        Picasso.with(context)
                .load(R.drawable.ble_header)
                .resize(mHeaderImageView.getWidth(), mHeaderImageView.getWidth()/4)
                .into(mBleImageView);
        Picasso.with(context)
                .load(R.drawable.analisys_header)
                .resize(mAnalysisImageView.getWidth(), mAnalysisImageView.getWidth()/4)
                .into(mAnalysisImageView);
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
                scanFab.startAnimation(mRotateAnim);
                bleScanDevices();
                break;
            case R.id.connSwitch:
                if(mConnSwitch.isChecked())
                    mBleDevice.connectGatt(context, true, mGattCallback);
                break;
        }
    }
}
