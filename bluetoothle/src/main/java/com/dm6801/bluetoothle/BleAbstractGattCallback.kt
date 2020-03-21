package com.dm6801.bluetoothle

import android.bluetooth.*
import com.dm6801.bluetoothle.utilities.Log
import com.dm6801.bluetoothle.utilities.hashCode
import com.dm6801.bluetoothle.utilities.justify
import kotlinx.coroutines.Deferred

abstract class BleAbstractGattCallback : BluetoothGattCallback() {

    open val READ_CALLBACK_TIMEOUT = 5_000L

    companion object {
        fun getStateString(state: Int): String? {
            return when (state) {
                BluetoothProfile.STATE_CONNECTED -> "STATE_CONNECTED"
                BluetoothProfile.STATE_CONNECTING -> "STATE_CONNECTING"
                BluetoothProfile.STATE_DISCONNECTING -> "STATE_DISCONNECTING"
                BluetoothProfile.STATE_DISCONNECTED -> "STATE_DISCONNECTED"
                else -> null
            }
        }

        val ByteArray?.prettyPrint get() = this?.joinToString(",") { it.toInt().toString() } ?: ""
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        logGatt(
            "onConnectionStateChange():",
            gatt,
            status,
            text = "newState=$newState (${getStateString(newState)})"
        )
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)
        logGatt("onServicesDiscovered():", gatt, status)
        //if (status != BluetoothGatt.GATT_SUCCESS) return
    }

    override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        super.onPhyRead(gatt, txPhy, rxPhy, status)
        logGatt("onPhyRead():", gatt, status, text = "txPhy=$txPhy\trxPhy=$rxPhy")
    }

    override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        super.onPhyUpdate(gatt, txPhy, rxPhy, status)
        logGatt("onPhyUpdate():", gatt, status, text = "txPhy=$txPhy\trxPhy=$rxPhy")
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
        super.onReadRemoteRssi(gatt, rssi, status)
        logGatt("onReadRemoteRssi():", gatt, status, text = "rssi=$rssi")
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        logGatt("onMtuChanged():", gatt, status, text = "mtu=$mtu")
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        super.onDescriptorRead(gatt, descriptor, status)
        logGatt(
            "onDescriptorRead():",
            gatt,
            status,
            descriptor = descriptor,
            text = "value=${descriptor?.value.prettyPrint}"
        )
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        super.onDescriptorWrite(gatt, descriptor, status)
        logGatt(
            "onDescriptorWrite():",
            gatt,
            status,
            descriptor = descriptor,
            text = "value=${descriptor?.value.prettyPrint}"
        )
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        super.onCharacteristicChanged(gatt, characteristic)
        logGatt(
            "onCharacteristicChanged():",
            gatt,
            characteristic = characteristic,
            text = "value=${characteristic?.value.prettyPrint}"
        )
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, status)
        logGatt(
            "onCharacteristicRead():",
            gatt,
            status,
            characteristic = characteristic,
            text = "value=${characteristic?.value.prettyPrint}"
        )
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        logGatt(
            "onCharacteristicWrite():",
            gatt,
            status,
            characteristic = characteristic,
            text = "value=${characteristic?.value.prettyPrint}"
        )
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
        super.onReliableWriteCompleted(gatt, status)
        logGatt("onReliableWriteCompleted():", gatt, status)
    }

    private fun logGatt(
        tag: String,
        gatt: BluetoothGatt? = null,
        status: Int? = null,
        descriptor: BluetoothGattDescriptor? = null,
        characteristic: BluetoothGattCharacteristic? = null,
        text: String? = null
    ) {
        if (text == null)
            Log(
                Thread.currentThread().justify() +
                        gatt?.device?.address.justify() +
                        tag.justify() +
                        (gatt?.hashCode(16)?.justify("gatt ") ?: "") +
                        (descriptor?.hashCode(16)?.justify("descriptor     ", size = 30) ?: "") +
                        (characteristic?.hashCode(16)?.justify("characteristic ", size = 30)
                            ?: "") +
                        status.justify("status ")
            )
        else
            Log(
                Thread.currentThread().justify() +
                        gatt?.device?.address.justify() +
                        (gatt?.hashCode(16)?.justify("gatt ") ?: "") +
                        (descriptor?.hashCode(16)?.justify("descriptor     ", size = 30) ?: "") +
                        (characteristic?.hashCode(16)?.justify("characteristic ", size = 30)
                            ?: "") +
                        status.justify("status ") +
                        tag.justify("\n") +
                        text
            )
    }

    @Throws(NotImplementedError::class)
    open fun write(byteArray: ByteArray) {
        throw NotImplementedError()
    }

    @Throws(NotImplementedError::class)
    open fun writeAsync(
        byteArray: ByteArray,
        timeout: Long = READ_CALLBACK_TIMEOUT
    ): Deferred<ByteArray> {
        throw NotImplementedError()
    }

    sealed class GattException(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : Exception() {
        companion object {
            operator fun invoke(state: Int? = null): GattException =
                GattException::class.java.let { ctor ->
                    (ctor.declaredConstructors.firstOrNull()
                        ?.newInstance(state?.let { "state: ${getStateString(it)}" }) as? GattException)
                        ?: ctor.newInstance()
                }
        }

        class Undefined : GattException("callback is undefined")
        class NotConnected(state: Int) : GattException("state: ${getStateString(state)}")
        class NotWritable(state: Int) : GattException("state: ${getStateString(state)}")
    }

}