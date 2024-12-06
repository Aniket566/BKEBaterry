package com.newproject.batteryprecentage;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION_CODE = 101;

    // Custom and standard UUIDs
    private static final UUID CUSTOM_SERVICE_UUID = UUID.fromString("0000ca77-0000-1000-a435-311b63657f1f");
    private static final UUID SUSPECTED_BATTERY_UUID = UUID.fromString("0000ca7a-0000-1000-a435-311b63657f1f");
    private static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;

    private TextView batteryPercentageText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI
        batteryPercentageText = findViewById(R.id.tvBatteryPercentage);

        // Initialize Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            finish(); // Exit if Bluetooth is not available or disabled
        }
        // Check permissions
        if (!hasRequiredPermissions()) {
            requestPermissions();
        } else {
            startScan(); // Start scanning if permissions are granted
        }
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }


    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, REQUEST_PERMISSION_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_PERMISSION_CODE);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startScan();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required to use this app.", Toast.LENGTH_SHORT).show();
                finish(); // Close the app if permissions are denied
            }
        }
    }

    private void startScan() {
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show();
        bluetoothLeScanner.startScan(scanCallback);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null && device.getName() != null) {
                bluetoothLeScanner.stopScan(scanCallback);
                Toast.makeText(MainActivity.this, "Found device: " + device.getName(), Toast.LENGTH_SHORT).show();
                connectToDevice(device);
            }
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        Toast.makeText(this, "Connecting to: " + device.getName(), Toast.LENGTH_SHORT).show();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                 runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Connected to device", Toast.LENGTH_SHORT).show();
                    bluetoothGatt.discoverServices();
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Disconnected from device", Toast.LENGTH_SHORT).show());
                bluetoothGatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE_UUID);
                if (batteryService != null) {
                    BluetoothGattCharacteristic batteryLevelChar = batteryService.getCharacteristic(BATTERY_LEVEL_UUID);
                    if (batteryLevelChar != null) {
                        gatt.readCharacteristic(batteryLevelChar);
                    }
                } else {
                    readCustomService(); // Fallback to custom service
                }
            } else {
                Log.e("BLE", "Service discovery failed with status: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    if (BATTERY_LEVEL_UUID.equals(characteristic.getUuid())) {
                        int batteryLevel = data[0]; // Assuming the battery level is in the first byte
                        runOnUiThread(() -> batteryPercentageText.setText("Battery Level: " + batteryLevel + "%"));
                    } else if (SUSPECTED_BATTERY_UUID.equals(characteristic.getUuid())) {
                        int batteryLevel = data[0]; // Fallback logic for custom characteristic
                        runOnUiThread(() -> batteryPercentageText.setText("Battery Level (Custom): " + batteryLevel + "%"));
                    }
                } else {
                    Log.w("BLE", "Characteristic read returned empty or null data for UUID: " + characteristic.getUuid());
                }
            } else {
                Log.e("BLE", "Characteristic read failed with status: " + status);
            }
        }
    };

    private void readCustomService() {
        BluetoothGattService customService = bluetoothGatt.getService(CUSTOM_SERVICE_UUID);
        if (customService != null) {
            for (BluetoothGattCharacteristic characteristic : customService.getCharacteristics()) {
                bluetoothGatt.readCharacteristic(characteristic);
            }
        } else {
            Log.e("BLE", "Custom Service not found.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
        }
    }
}
