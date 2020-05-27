package com.dm6801.bluetoothle_example

import com.dm6801.bluetoothle.utilities.catch
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.SimpleFormatter

class BleLogHandler : java.util.logging.Handler() {

    private val buffer = StringBuffer()
    private var systemLogChannel: ReceiveChannel<String>? = null

    init {
        level = Level.ALL
        formatter = BleLogFormatter
    }

    override fun publish(record: LogRecord) {
        buffer.appendln(record.message)
    }

    override fun flush() {}

    override fun close() {}

    override fun isLoggable(record: LogRecord?): Boolean = true

    fun getLog(): String = buffer.toString()

    @Suppress("BlockingMethodInNonBlockingContext", "EXPERIMENTAL_API_USAGE")
    @Throws(Exception::class)
    suspend fun getSystemLog(): ReceiveChannel<String>? {
        catch(silent = true) { closeSystemLog() }
        return CoroutineScope(Dispatchers.IO).produce<String> {
            val process = Runtime.getRuntime()
                .exec("logcat BluetoothManager:V BluetoothAdapter:V BluetoothGatt:V *:S")
            launch(Dispatchers.IO) {
                process.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    do {
                        if (!isActive) break
                        line = reader.readLine()
                        if (line != null) send(line)
                    } while (line != null)
                }
            }
            launch(Dispatchers.IO) {
                process.errorStream.bufferedReader().use { reader ->
                    var err: String?
                    do {
                        if (!isActive) break
                        err = reader.readLine()
                        if (err != null) send(err)
                    } while (err != null)
                }
            }
            process.outputStream.close()
            process.waitFor()
            close()
            cancel()
        }.also { systemLogChannel = it }
    }

    fun closeSystemLog() = catch {
        systemLogChannel?.cancel()
        systemLogChannel = null
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun clear() {
        closeSystemLog()
        buffer.setLength(0)
        CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            try {
                Runtime.getRuntime().exec("logcat -c")
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    object BleLogFormatter : SimpleFormatter()
}
