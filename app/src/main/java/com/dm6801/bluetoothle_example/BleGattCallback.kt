package com.dm6801.bluetoothle_example

import android.bluetooth.BluetoothGattService
import java.util.*

class BleGattCallback : com.dm6801.bluetoothle.BleGattCallback() {

    companion object {
        //val SERVICE_UUID: UUID = UUID.fromString("00400001-b5a3-f393-e0a9-e50e24dcca9e") //W
        val SERVICE_UUID: UUID = UUID.fromString("00400010-b5a3-f393-e0a9-e50e24dcca9e") //P
    }

    override fun setServices(services: Collection<BluetoothGattService>) {
        super.setServices(services.filter { it.uuid == SERVICE_UUID })
    }

}