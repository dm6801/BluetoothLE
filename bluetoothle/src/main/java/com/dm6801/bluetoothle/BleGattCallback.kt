package com.dm6801.bluetoothle

import android.bluetooth.*
import android.os.Handler
import com.dm6801.bluetoothle.utilities.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

abstract class BleGattCallback : LogGattCallback() {

    companion object {
        val manager: BluetoothManager? get() = BLE.manager
    }

    val services = mutableMapOf<String, BluetoothGattService>()
    val readable = mutableMapOf<String, Set<BluetoothGattCharacteristic>>()
    val notifiable = mutableMapOf<String, Set<BluetoothGattCharacteristic>>()
    val writable = mutableMapOf<String, Set<BluetoothGattCharacteristic>>()

    private val scope = SupervisorJob() + Dispatchers.IO

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

    protected open fun clearCharacteristics(uuid: String? = null) {
        if (uuid != null) {
            readable.remove(uuid)
            notifiable.remove(uuid)
            writable.remove(uuid)
        } else {
            readable.clear()
            notifiable.clear()
            writable.clear()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)
        this.gatt = gatt
        if (status != BluetoothGatt.GATT_SUCCESS) return
        services.putAll(gatt?.services?.map { it.uuid.toString() to it } ?: return)
        setServices(services.values)
    }

    protected open fun setServices(services: Collection<BluetoothGattService>) {
        services.forEach(::setService)
    }

    protected open fun setService(service: BluetoothGattService) {
        log("service ${service.hashCode(16)}")
        val uuid = service.uuid.toString()
        clearCharacteristics(uuid)
        readable[uuid] = mutableSetOf()
        notifiable[uuid] = mutableSetOf()
        writable[uuid] = mutableSetOf()
        service.characteristics?.forEach { characteristic ->
            log("characteristic ${characteristic.hashCode(16)}")
            val readable = readable[uuid] as MutableSet
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
                log("characteristic ${characteristic.hashCode(16)} readable")
                readable.add(characteristic)
            }
            val notifiable = notifiable[uuid] as MutableSet
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                log("characteristic ${characteristic.hashCode(16)} notifiable")
                catch { gatt?.setCharacteristicNotification(characteristic, true) }
                catch {
                    characteristic.descriptors.forEach { descriptor ->
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt?.writeDescriptor(descriptor)
                    }
                    notifiable.add(characteristic)
                }
            }
            val writable = writable[uuid] as MutableSet
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
        parseCharacteristicData(characteristic?.value ?: return)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        super.onCharacteristicChanged(gatt, characteristic)
        parseCharacteristicData(characteristic?.value ?: return)
    }

    private fun parseCharacteristicData(data: ByteArray) {
        if (data.isEmpty()) return
        val opcode = data.first()
        val callback = callbacks[opcode]
        if (callback != null) {
            callback.scope.takeIf { it.isActive }?.launch {
                try {
                    callback.channel.send(data)
                    if (callback.predicate(data)) callbacks.remove(opcode)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        } else {
            queue.entries.firstOrNull()?.let {
                queue.remove(it.key)?.complete(data)
            }
        }
    }

    protected open var callbacks = ConcurrentHashMap<Byte, OnUpdate>()
    protected open var queue = ConcurrentHashMap<Long, CompletableDeferred<ByteArray>>()

    override fun write(byteArray: ByteArray): Boolean {
        val gatt = gatt ?: throw GattException.Undefined()
        if (state != BluetoothGatt.STATE_CONNECTED) throw GattException.NotConnected(state)
        if (writable.isEmpty()) throw GattException.NotWritable(state)
        var result = true
        writable.values.flatten().forEach {
            it.value = byteArray
            result = gatt.writeCharacteristic(it) && result
        }
        return result
    }

    override fun writeAsync(
        byteArray: ByteArray,
        timeout: Long
    ): Deferred<ByteArray> {
        val gatt = gatt ?: throw GattException.Undefined()
        if (state != BluetoothGatt.STATE_CONNECTED) throw GattException.NotConnected(state)
        if (writable.isEmpty()) throw GattException.NotWritable(state)
        val completable = CompletableDeferred<ByteArray>()
        val tag = System.currentTimeMillis()
        queue[tag] = completable
        writable.values.flatten().forEach {
            it.value = byteArray
            gatt.writeCharacteristic(it)
        }
        Handler().postDelayed({
            queue.remove(tag)?.completeExceptionally(TimeoutException())
        }, timeout)
        return completable
    }

    override fun writeAsync(
        scope: CoroutineScope,
        byteArray: ByteArray,
        opcode: Byte,
        timeout: Long,
        predicate: (ByteArray) -> Boolean
    ): ReceiveChannel<ByteArray> {
        val gatt = gatt ?: throw GattException.Undefined()
        if (state != BluetoothGatt.STATE_CONNECTED) throw GattException.NotConnected(state)
        if (writable.isEmpty()) throw GattException.NotWritable(state)
        val channel = Channel<ByteArray>()
        callbacks[opcode] = OnUpdate(opcode, timeout, channel, scope, predicate)
        writable.values.flatten().forEach {
            it.value = byteArray
            gatt.writeCharacteristic(it)
        }
        return channel
    }

    inner class OnUpdate(
        val opcode: Byte,
        val timeout: Long,
        val channel: Channel<ByteArray>,
        val scope: CoroutineScope,
        val predicate: (ByteArray) -> Boolean
    ) {
        init {
            scope.launch {
                delay(timeout)
                channel.close(TimeoutException())
                callbacks.remove(opcode)
            }
        }
    }

}
