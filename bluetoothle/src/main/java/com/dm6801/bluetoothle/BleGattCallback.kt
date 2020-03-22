package com.dm6801.bluetoothle

import android.bluetooth.*
import com.dm6801.bluetoothle.utilities.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

abstract class BleGattCallback : LogGattCallback() {

    companion object {
        val manager: BluetoothManager? get() = BLE.manager
    }

    abstract val SERVICE_UUID: UUID
    val readable = mutableSetOf<BluetoothGattCharacteristic>()
    val notifiable = mutableSetOf<BluetoothGattCharacteristic>()
    val writable = mutableSetOf<BluetoothGattCharacteristic>()

    protected var gatt: BluetoothGatt? by weakRef(null)
    val state: Int
        get() {
            return (try {
                gatt?.device?.let { device ->
                    manager?.getConnectionState(device, BluetoothProfile.GATT)
                }
            } catch (_: Throwable) {
                return -1
            }) ?: -1
        }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        when (newState) {
            BluetoothGatt.STATE_CONNECTED -> gatt?.discoverServices()
            BluetoothGatt.STATE_DISCONNECTING,
            BluetoothGatt.STATE_DISCONNECTED -> clearCharacteristics()
        }
    }

    protected open fun clearCharacteristics() {
        readable.clear()
        notifiable.clear()
        writable.clear()
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)
        this.gatt = gatt
        if (status != BluetoothGatt.GATT_SUCCESS) return
        val service = gatt?.getService(SERVICE_UUID)
        log("service ${service.hashCode(16)}")
        clearCharacteristics()
        service?.characteristics?.forEach { characteristic ->
            log("characteristic ${characteristic.hashCode(16)}")
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
                log("characteristic ${characteristic.hashCode(16)} readable")
                readable.add(characteristic)
            }
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                log("characteristic ${characteristic.hashCode(16)} notifiable")
                catch { gatt.setCharacteristicNotification(characteristic, true) }
                catch {
                    characteristic.descriptors.forEach { descriptor ->
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                    notifiable.add(characteristic)
                }
            }
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE > 0) {
                log("characteristic ${characteristic.hashCode(16)} writable, setting WRITE_TYPE_DEFAULT")
                catch {
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    writable.add(characteristic)
                }
            }
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, status)
        readQueue.entries.firstOrNull()?.let { callback ->
            readQueue.remove(callback.key)?.complete(characteristic?.value ?: byteArrayOf())
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        super.onCharacteristicChanged(gatt, characteristic)
        readQueue.entries.firstOrNull()?.let { callback ->
            readQueue.remove(callback.key)?.complete(characteristic?.value ?: byteArrayOf())
        }
    }

    protected open var readQueue = ConcurrentHashMap<Long, CompletableDeferred<ByteArray>>()

    @Throws(GattException::class)
    override fun writeAsync(byteArray: ByteArray, timeout: Long): Deferred<ByteArray> {
        val gatt = gatt ?: throw GattException.Undefined()
        if (state != BluetoothGatt.STATE_CONNECTED) throw GattException.NotConnected(state)
        if (writable.isEmpty()) throw GattException.NotWritable(state)
        val completable = CompletableDeferred<ByteArray>()
        val tag = System.currentTimeMillis()
        readQueue[tag] = completable
        writable.forEach {
            it.value = byteArray
            gatt.writeCharacteristic(it)
        }
        main {
            delay(timeout)
            readQueue.remove(tag)?.completeExceptionally(TimeoutException())
        }
        return completable
    }

    @Throws(GattException::class)
    override fun write(byteArray: ByteArray) {
        val gatt = gatt ?: throw GattException.Undefined()
        if (state != BluetoothGatt.STATE_CONNECTED) throw GattException.NotConnected(state)
        if (writable.isEmpty()) throw GattException.NotWritable(state)
        writable.forEach {
            it.value = byteArray
            gatt.writeCharacteristic(it)
        }
    }

}

