package com.gitcode.ble;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "GitCode";

    public static final int GATT_CONNECTED = 1;

    public static final int GATT_DISCONNECTED = 2;

    public static final int GATT_SERVICES_DISCOVER = 3;


    public Set<String> macSet = new HashSet();

    private static final long SCAN_TIME = 60 * 1000;

    private boolean firstScan = true;


    public static final int REQUEST_ACCESS_COARSE_LOCATION_PERMISSION = 100;

    private BluetoothAdapter mBluetoothAdapter;

    private List<BluetoothDevice> deviceList;

    private List<BluetoothGattService> serviceList;

    private BLEAdapter deviceAdapter;

    private ListView lv;

    private BluetoothLeScanner scanner;

    private ScanCallback scanCallback;

    private GattCallback gattCallback;

    private BluetoothGatt bluetoothGatt;

    //这里的uuid是随便定义的，需要向硬件工程师或者厂商查询
    private String serviceUuid = "1232-2323-2322-2322";
    private String charUuid = "1232-2323-2322-2222";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();


        if (!isSupportBLE()) {
            showNotSupportBluetoothDialog();
            return;
        }

        initView();

        registerBluetoothReceiver();

        enableBLE();

    }

    private void initView() {
        lv = findViewById(R.id.lvBLE);


        deviceList = new ArrayList<>();

        deviceAdapter = new BLEAdapter(this, deviceList);

        lv.setAdapter(deviceAdapter);


        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = deviceList.get(position);

                bluetoothGatt = device.connectGatt(MainActivity.this, true, gattCallback);
            }
        });

        gattCallback = new GattCallback(handler);
    }

    /**
     * Android 6.0 动态申请授权定位信息权限，否则扫描蓝牙列表为空
     */
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    Toast.makeText(this, "使用蓝牙需要授权定位信息", Toast.LENGTH_LONG).show();
                }
                //请求权限
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_ACCESS_COARSE_LOCATION_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_ACCESS_COARSE_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //用户授权
            } else {
                finish();
            }

        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * 是否支持BLE
     */
    private boolean isSupportBLE() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        mBluetoothAdapter = manager.getAdapter();
        //设备是否支持蓝牙
        if (mBluetoothAdapter != null
                //系统是否支持BLE
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.i(TAG, " support bluetooth");
            return true;
        } else {
            Log.i(TAG, "not support bluetooth");
            return false;
        }

    }

    /**
     * 弹出不支持低功耗蓝牙对话框
     */
    private void showNotSupportBluetoothDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("当前设备不支持BLE").create();
        dialog.show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });

    }

    private void enableBLE() {
        if (mBluetoothAdapter.isEnabled()) {
            startScan();
        } else {
            mBluetoothAdapter.enable();
        }
    }

    /**
     * 开始扫描蓝牙
     */
    private void startScan() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //android 5.0之前的扫描方式
            mBluetoothAdapter.startLeScan(new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

                }
            });
        } else {
            //android 5.0之后的扫描方式
            scanner = mBluetoothAdapter.getBluetoothLeScanner();

            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {

                    //停止扫描
                    if (firstScan) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                scanner.stopScan(scanCallback);

                            }
                        }, SCAN_TIME);

                        firstScan = false;
                    }

                    String mac = result.getDevice().getAddress();

                    Log.i(TAG, result.getScanRecord().getDeviceName() + ":" + mac);
                    //过滤重复的mac
                    if (!macSet.contains(mac)) {
                        macSet.add(result.getDevice().getAddress());
                        deviceList.add(result.getDevice());
                        deviceAdapter.notifyDataSetChanged();
                    }
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    Log.i(TAG, "batch scan result size:" + results.size());
                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                    Log.e(TAG, "扫描失败:" + errorCode);
                }
            };

            scanner.startScan(scanCallback);
        }

    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);
    }

    BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = mBluetoothAdapter.getState();
                if (state == BluetoothAdapter.STATE_ON) {
                    startScan();
                }
            }
        }
    };

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case GATT_CONNECTED:
                    bluetoothGatt.discoverServices();
                    break;

                case GATT_DISCONNECTED:
                    break;

                case GATT_SERVICES_DISCOVER:
                    updateValue();
                    break;
            }
        }
    };

    private void updateValue() {
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUuid));
        if (service == null) return;
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuid));
        enableNotification(characteristic, charUuid);
        characteristic.setValue("on");
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    private void enableNotification(BluetoothGattCharacteristic characteristic, String uuid) {
        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(uuid));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(descriptor);
    }

}
