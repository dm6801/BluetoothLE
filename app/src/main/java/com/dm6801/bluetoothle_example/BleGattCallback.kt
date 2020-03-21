package com.dm6801.bluetoothle_example

import java.util.*

class BleGattCallback : com.dm6801.bluetoothle.BleGattCallback() {

    override val SERVICE_UUID: UUID = UUID.fromString("00400001-b5a3-f393-e0a9-e50e24dcca9e")

}