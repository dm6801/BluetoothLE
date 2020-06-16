package com.dm6801.bluetoothle

import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.dm6801.bluetoothle.utilities.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

@Suppress("EXPERIMENTAL_API_USAGE")
object BLE {

    const val REQUEST_ENABLE_BT: Int = 13431
    const val SCAN_TIMEOUT: Long = 5_000

    private var context: Context? = null
    val manager: BluetoothManager? get() = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    val adapter: BluetoothAdapter? get() = manager?.adapter
    val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner
    internal val logger: Logger = Logger.getLogger(javaClass.name)
    private val systemLoggers
        get() = listOf(
            Logger.getLogger(android.bluetooth.BluetoothManager::class.java.name),
            Logger.getLogger(android.bluetooth.BluetoothAdapter::class.java.name),
            Logger.getLogger(android.bluetooth.BluetoothGatt::class.java.name)
        )

    private val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        throwable.printStackTrace()
    }

    fun init(context: Context?, loggingHandler: java.util.logging.Handler? = null) {
        this.context = context
        logger.handlers.forEach(logger::removeHandler)
        (systemLoggers + logger).forEach { logger ->
            logger.level = Level.ALL
            loggingHandler?.let(logger::addHandler)
        }
    }

    fun enable(activity: Activity?) {
        if (adapter == null || adapter?.isEnabled != true && activity != null) catch {
            activity?.startActivityForResult(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                REQUEST_ENABLE_BT
            )
        }
    }

    fun getStateString(state: Int): String? {
        return when (state) {
            BluetoothProfile.STATE_CONNECTED -> "STATE_CONNECTED"
            BluetoothProfile.STATE_CONNECTING -> "STATE_CONNECTING"
            BluetoothProfile.STATE_DISCONNECTING -> "STATE_DISCONNECTING"
            BluetoothProfile.STATE_DISCONNECTED -> "STATE_DISCONNECTED"
            else -> null
        }
    }

    //region scan
    private var scanChannel: ReceiveChannel<ScanResult>? = null

    fun scan(
        scope: CoroutineScope,
        timeout: Long = SCAN_TIMEOUT,
        unique: Boolean? = null
    ): ReceiveChannel<ScanResult> = ensureMainThread {
        stopScan()
        CoroutineScope(scope.coroutineContext).produce {
            val channel = Channel<ScanResult>()
            val scanCallback = ChannelScanCallback(channel, unique)
            try {
                scanner?.startScan(
                    emptyList<ScanFilter>(),
                    ScanSettings.Builder().build(),
                    scanCallback
                )
                launch(exceptionHandler) {
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

    fun stopScan() = ensureMainThread {
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
    val gattClients: MutableMap<String, Pair<BluetoothGatt, BluetoothGattCallback>> =
        ConcurrentHashMap()

    fun findDevice(address: String): BluetoothDevice? = adapter?.getRemoteDevice(address)

    fun findGatt(address: String): BluetoothGatt? = gattClients[address]?.first

    @Suppress("UNCHECKED_CAST")
    fun <T : BluetoothGattCallback> findGattCallback(address: String): T? =
        gattClients[address]?.second as? T

    fun isConnected(address: String): Boolean = findDevice(address)?.let(::isConnected) ?: false

    fun isConnected(device: BluetoothDevice): Boolean {
        return manager
            ?.getConnectedDevices(BluetoothProfile.GATT)
            ?.contains(device) == true
    }

    fun connect(address: String, gattCallback: BluetoothGattCallback) =
        findDevice(address)?.let { connect(it, gattCallback) }

    @Throws(BleException::class)
    fun connect(device: BluetoothDevice, gattCallback: BluetoothGattCallback) = ensureMainThread {
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

    fun disconnect(gatt: BluetoothGatt) = ensureMainThread {
        catch {
            gatt.disconnect()
        }
    }

    fun close(address: String) {
        findGatt(address)?.let(BLE::close)
    }

    fun close(gatt: BluetoothGatt) = ensureMainThread {
        catch {
            gatt.disconnect()
            Handler().postDelayed({
                gatt.close()
                gattClients.remove(gatt.device.address)
            }, 200)
        }
    }

    fun write(address: String, byteArray: ByteArray): Boolean = ensureMainThread {
        val callback = findGattCallback(address) as? LogGattCallback
            ?: throw GattException.Undefined()
        callback.write(byteArray)
    }

    fun writeAsync(address: String, byteArray: ByteArray): Deferred<ByteArray> = ensureMainThread {
        val callback = findGattCallback(address) as? LogGattCallback
            ?: throw GattException.Undefined()
        callback.writeAsync(byteArray)
    }

    fun writeAsync(
        scope: CoroutineScope,
        address: String,
        byteArray: ByteArray,
        opcode: Byte,
        predicate: (ByteArray) -> Boolean
    ): ReceiveChannel<ByteArray> = ensureMainThread {
        val callback = findGattCallback(address) as? LogGattCallback
            ?: throw GattException.Undefined()
        callback.writeAsync(scope, byteArray, opcode, predicate = predicate)
    }
    //endregion

    @Throws(BleException::class)
    private fun <T> ensureMainThread(action: () -> T): T {
        if (!isMainThread) throw NotOnMainThreadException()
        return action()
    }

    private fun log(obj: Any?) {
        //if (log) Log(obj.toString())
        logger.log(Level.INFO, obj.toString())
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
fun BluetoothDevice.writeAsync(byteArray: ByteArray): Deferred<ByteArray> =
    BLE.writeAsync(address, byteArray)

@Throws(Exception::class)
fun BluetoothDevice.writeAsync(
    scope: CoroutineScope,
    byteArray: ByteArray,
    opcode: Byte,
    predicate: (ByteArray) -> Boolean = { _ -> true }
): ReceiveChannel<ByteArray> = BLE.writeAsync(scope, address, byteArray, opcode, predicate)