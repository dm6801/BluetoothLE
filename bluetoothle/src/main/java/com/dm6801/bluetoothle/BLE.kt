package com.dm6801.bluetoothle

import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.dm6801.bluetoothle.utilities.Log
import com.dm6801.bluetoothle.utilities.catch
import com.dm6801.bluetoothle.utilities.main
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.ConcurrentHashMap

@Suppress("EXPERIMENTAL_API_USAGE")
object BLE {

    const val REQUEST_ENABLE_BT: Int = 13431
    const val SCAN_TIMEOUT: Long = 5_000

    var log: Boolean = true
    private var context: Context? = null
    val manager: BluetoothManager? get() = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    val adapter: BluetoothAdapter? get() = manager?.adapter
    val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    fun init(context: Context?, log: Boolean = true) {
        this.context = context
        this.log = log
    }

    fun enable(activity: Activity?) {
        if (adapter == null || adapter?.isEnabled != true && activity != null) catch {
            activity?.startActivityForResult(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                REQUEST_ENABLE_BT
            )
        }
    }

    //region scan
    private var scanChannel: ReceiveChannel<ScanResult>? = null

    fun scan(
        scope: CoroutineScope,
        timeout: Long = SCAN_TIMEOUT,
        unique: Boolean? = null
    ): ReceiveChannel<ScanResult> {
        stopScan()
        return CoroutineScope(scope.coroutineContext).produce {
            val channel = Channel<ScanResult>()
            val scanCallback = ChannelScanCallback(channel, unique)
            try {
                scanner?.startScan(
                    emptyList<ScanFilter>(),
                    ScanSettings.Builder().build(),
                    scanCallback
                )
                launch {
                    delay(timeout)
                    scanner?.stopScan(scanCallback)
                    channel.close(ScanException.Stop("timeout: $timeout"))
                    scanChannel = null
                }
                for (scanResult in channel) {
                    if (channel.isClosedForSend) break
                    send(scanResult)
                }
            } catch (t: Throwable) {
                t.cause?.printStackTrace()
                    ?: t.printStackTrace()
            } finally {
                try {
                    scanner?.stopScan(scanCallback)
                    channel.close(ScanException.Stop())
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }.also { scanChannel = it }
    }

    fun stopScan() {
        try {
            scanChannel?.cancel(ScanException.Stop().cancellation)
            scanChannel = null
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    class ChannelScanCallback(
        private val channel: SendChannel<ScanResult>,
        private val unique: Boolean? = true
    ) : ScanCallback() {
        private val scanned = mutableSetOf<String>()

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            emitResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach(::emitResult)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            channel.close(ScanException.Failed(errorCode))
        }

        private fun emitResult(result: ScanResult) {
            if (unique == true) {
                result.device?.address?.takeIf { it !in scanned }?.let { address ->
                    log(result)
                    scanned.add(address)
                    channel.offer(result)
                }
            } else {
                log(result)
                channel.offer(result)
            }
        }
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    sealed class ScanException(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : Exception() {
        class Failed(val errorCode: Int) : ScanException("errorCode: $errorCode")
        class Stop(reason: String? = null) :
            ScanException("scan stopped" + (reason?.let { " ($it)" } ?: "")) {
            val cancellation by lazy { CancellationException(message, this) }
        }
    }
    //endregion

    //region gatt
    internal val gattClients: MutableMap<String, Pair<BluetoothGatt, BluetoothGattCallback>> =
        ConcurrentHashMap()

    fun findDevice(address: String): BluetoothDevice? = adapter?.getRemoteDevice(address)

    fun findGatt(address: String): BluetoothGatt? = gattClients[address]?.first

    fun findGattCallback(address: String): BluetoothGattCallback? = gattClients[address]?.second

    fun connect(device: BluetoothDevice, gattCallback: BluetoothGattCallback) = main {
        catch { stopScan() }
        catch {
            findGatt(device.address)?.connect()
                ?: run {
                    gattClients[device.address] = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                            device.connectGatt(
                                context,
                                false,
                                gattCallback,
                                BluetoothDevice.TRANSPORT_LE,
                                BluetoothDevice.PHY_LE_1M_MASK,
                                Handler(Looper.getMainLooper())
                            )
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                            device.connectGatt(
                                context,
                                false,
                                gattCallback,
                                BluetoothDevice.TRANSPORT_LE
                            )
                        else ->
                            device.connectGatt(context, false, gattCallback)
                    } to gattCallback
                }
        }
    }

    fun disconnect(address: String) {
        findGatt(address)?.let(BLE::disconnect)
    }

    fun disconnect(gatt: BluetoothGatt) = catch {
        main { gatt.disconnect() }
    }

    fun close(address: String) {
        findGatt(address)?.let(BLE::close)
    }

    fun close(gatt: BluetoothGatt) = catch {
        main {
            gatt.disconnect()
            delay(200)
            gatt.close()
            gattClients.remove(gatt.device.address)
        }
    }

    @Throws(Exception::class)
    fun write(address: String, byteArray: ByteArray) = main {
        val callback = findGattCallback(address) as? BleAbstractGattCallback
            ?: throw BleAbstractGattCallback.GattException.Undefined()
        callback.write(byteArray)
    }

    @Throws(Exception::class)
    fun asyncWrite(address: String, byteArray: ByteArray): Deferred<ByteArray> {
        val callback = findGattCallback(address) as? BleAbstractGattCallback
            ?: throw BleAbstractGattCallback.GattException.Undefined()
        return callback.writeAsync(byteArray)
    }
    //endregion

    private fun log(obj: Any?) {
        if (log) Log(obj.toString())
    }

}

fun CoroutineScope.scan(
    timeout: Long = 5_000,
    unique: Boolean? = null
): ReceiveChannel<ScanResult> = BLE.scan(this, timeout, unique)

fun BluetoothDevice.connect(gattCallback: BluetoothGattCallback) = BLE.connect(this, gattCallback)

fun BluetoothDevice.disconnect() = BLE.disconnect(address)

fun BluetoothDevice.close() = BLE.close(address)

@Throws(Exception::class)
fun BluetoothDevice.write(byteArray: ByteArray) = BLE.write(address, byteArray)

@Throws(Exception::class)
fun BluetoothDevice.asyncWrite(byteArray: ByteArray): Deferred<ByteArray> =
    BLE.asyncWrite(address, byteArray)