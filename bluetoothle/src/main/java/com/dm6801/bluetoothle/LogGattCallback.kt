package com.dm6801.bluetoothle

import android.bluetooth.*
import com.dm6801.bluetoothle.utilities.Log
import com.dm6801.bluetoothle.utilities.hashCode
import com.dm6801.bluetoothle.utilities.justify
import kotlinx.coroutines.Deferred
import java.util.logging.Level

open class LogGattCallback : BluetoothGattCallback() {

    open val READ_CALLBACK_TIMEOUT = 5_000L
    protected var logger = BLE.logger

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
        log(
            "onConnectionStateChange():",
            gatt,
            status,
            text = "newState=$newState (${getStateString(newState)})"
        )
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)
        log("onServicesDiscovered():", gatt, status)
    }

    override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        super.onPhyRead(gatt, txPhy, rxPhy, status)
        log("onPhyRead():", gatt, status, text = "txPhy=$txPhy\trxPhy=$rxPhy")
    }

    override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        super.onPhyUpdate(gatt, txPhy, rxPhy, status)
        log("onPhyUpdate():", gatt, status, text = "txPhy=$txPhy\trxPhy=$rxPhy")
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
        super.onReadRemoteRssi(gatt, rssi, status)
        log("onReadRemoteRssi():", gatt, status, text = "rssi=$rssi")
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        log("onMtuChanged():", gatt, status, text = "mtu=$mtu")
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        super.onDescriptorRead(gatt, descriptor, status)
        log(
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
        log(
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
        log(
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
        log(
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
        log(
            "onCharacteristicWrite():",
            gatt,
            status,
            characteristic = characteristic,
            text = "value=${characteristic?.value.prettyPrint}"
        )
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
        super.onReliableWriteCompleted(gatt, status)
        log("onReliableWriteCompleted():", gatt, status)
    }

    private fun log(
        tag: String,
        gatt: BluetoothGatt? = null,
        status: Int? = null,
        descriptor: BluetoothGattDescriptor? = null,
        characteristic: BluetoothGattCharacteristic? = null,
        text: String? = null
    ) {
        logger.log(
            Level.INFO,
            if (text == null)
                Thread.currentThread().justify() +
                        gatt?.device?.address.justify() +
                        tag.justify() +
                        (gatt?.hashCode(16)?.justify("gatt ") ?: "") +
                        (descriptor?.hashCode(16)?.justify("descriptor     ", size = 30) ?: "") +
                        (characteristic?.hashCode(16)?.justify("characteristic ", size = 30)
                            ?: "") +
                        status.justify("status ")
            else
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

    protected fun log(obj: Any?, level: Level = Level.INFO) {
        logger.log(level, obj.toString())
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