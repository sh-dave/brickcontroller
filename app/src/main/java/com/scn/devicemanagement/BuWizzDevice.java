package com.scn.devicemanagement;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;

import com.scn.logger.Logger;

/**
 * Created by steve on 2017. 03. 18..
 */

final class BuWizzDevice extends BluetoothDevice {

    //
    // Members
    //

    private static final String TAG = BuWizzDevice.class.getSimpleName();

    // Service UUIDs
    private static final String SERVICE_UUID_REMOTE_CONTROL = "0000ffe0-0000-1000-8000-00805f9b34fb";

    // Characteristic UUIDs
    private static final String CHARACTERISTIC_PARTIAL_UUID_DEVICE_NAME = "2a00";
    private static final String CHARACTERISTIC_UUID_REMOTE_CONTROL = "0000ffe1-0000-1000-8000-00805f9b34fb";

    private BluetoothGattCharacteristic remoteControlCharacteristic;

    private Thread outputThread = null;
    private boolean stopOutputThread = false;

    private final int[] outputValues = new int[4];
    private boolean continueSending = true;

    //
    // Constructor
    //

    BuWizzDevice(@NonNull Context context, @NonNull String name, @NonNull String address, @NonNull BluetoothDeviceManager bluetoothDeviceManager) {
        super(context, name, address, bluetoothDeviceManager);
        Logger.i(TAG, "constructor...");
        Logger.i(TAG, "  name: " + name);
        Logger.i(TAG, "  address: " + address);
    }

    //
    // API
    //

    @Override
    public String getId() {
        return "BuWizz-" + address;
    }

    @Override
    public DeviceType getType() { return DeviceType.BUWIZZ; }

    @Override
    public int getNumberOfChannels() {
        return 4;
    }

    @MainThread
    @Override
    public boolean setOutputLevel(int level) {
        Logger.i(TAG, "setOutputLevel - " + getId());
        throw new RuntimeException("not implemented.");
    }

    @Override
    public void setOutput(int channel, int value) {
        Logger.i(TAG, "setOutput - channel: " + channel + ", value: " + value);
        checkChannel(channel);
        value = limitOutputValue(value);
        outputValues[channel] = value;
        continueSending = true;
    }

    //
    // Protected API
    //

    @Override
    protected void onServiceDiscovered(BluetoothGatt gatt) {
        Logger.i(TAG, "onServiceDiscovered - device: " + BuWizzDevice.this);

        remoteControlCharacteristic = getGattCharacteristic(gatt, SERVICE_UUID_REMOTE_CONTROL, CHARACTERISTIC_UUID_REMOTE_CONTROL);

        startOutputThread();
    }

    @Override
    protected void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        Logger.i(TAG, "onCharacteristicRead - device: " + BuWizzDevice.this);
    }

    @Override
    protected void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        Logger.i(TAG, "onCharacteristicWrite - device: " + BuWizzDevice.this);
    }

    @Override
    protected void disconnectInternal() {
        Logger.i(TAG, "disconnectInternal - device: " + BuWizzDevice.this);
        stopOutputThread = true;
    }

    //
    // Private methods
    //

    private void startOutputThread() {
        Logger.i(TAG, "startOutputThread - device: " + this);

        stopOutputThread = false;
        outputThread = new Thread(() -> {
            Logger.i(TAG, "Entering the output thread - device: " + BuWizzDevice.this);

            while (!stopOutputThread) {
                if (continueSending) {
                    int value0 = outputValues[0];
                    int value1 = outputValues[1];
                    int value2 = outputValues[2];
                    int value3 = outputValues[3];

                    sendOutputValues(value0, value1, value2, value3);

                    continueSending = value0 != 0 || value1 != 0 || value2 != 0 || value3 != 0;
                }

                try { Thread.sleep(60); } catch (InterruptedException e) {}
            }

            Logger.i(TAG, "Exiting from output thread - device: " + BuWizzDevice.this);
        });
        outputThread.start();
    }

    private void sendOutputValues(int v0, int v1, int v2, int v3) {
        byte[] buffer = new byte[] {
                (byte)((Math.abs(v0) >> 2) | (v0 < 0 ? 0x40 : 0) | 0x80),
                (byte)((Math.abs(v1) >> 2) | (v1 < 0 ? 0x40 : 0)),
                (byte)((Math.abs(v2) >> 2) | (v2 < 0 ? 0x40 : 0)),
                (byte)((Math.abs(v3) >> 2) | (v3 < 0 ? 0x40 : 0)),
                (byte)20
        };

        if (remoteControlCharacteristic.setValue(buffer)) {
            bluetoothGatt.writeCharacteristic(remoteControlCharacteristic);
        }
    }
}
