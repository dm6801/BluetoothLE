package com.dm6801.bluetoothle_example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.dm6801.bluetoothle.*
import com.dm6801.bluetoothle.utilities.catch
import com.dm6801.bluetoothle.utilities.log
import com.dm6801.bluetoothle.utilities.weakRef
import kotlinx.android.synthetic.main.fragment_device.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class DeviceFragment(device: BluetoothDevice) : BaseFragment() {

    private val layout: Int = R.layout.fragment_device
    private val menu: Int = R.menu.menu_device
    private val macAddressTextView: TextView? get() = device_mac_address

    private val logScrollView: ScrollView? get() = device_log_scroll
    private val logTextView: TextView? get() = device_log
    private val commandEditText: EditText? get() = device_write_buffer
    private val sendButton: Button? get() = device_write_button

    private var macAddress: String = device.address ?: ""
    private var device: BluetoothDevice? by weakRef(device)

    private val logHandler: BleLogHandler? get() = (activity as? MainActivity)?.logHandler
    private var commLog: StringBuffer = StringBuffer()

    @Volatile
    private var logLevel: Int = 0

    enum class LogLevel {
        NORMAL, VERBOSE, SYSTEM;

        companion object {
            val size = values().size
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        setMacAddressText()
        initLogTextView()
        initCommandEdit()
        initButtons()
    }

    private fun initToolbar() {
        setHasOptionsMenu(true)
    }

    override fun onStart() {
        super.onStart()
        findDevice()
    }

    override fun onDestroy() {
        logHandler?.closeSystemLog()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(this.menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.device_menu_clear_log -> clearLog()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setMacAddressText() {
        macAddressTextView?.text = macAddress
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initLogTextView() {
        logScrollView?.requestDisallowInterceptTouchEvent(true)
        logScrollView?.setOnTouchListener(object : SwipeDetector() {
            override fun onSwipeLeft() = toggleLog(1)
            override fun onSwipeRight() = toggleLog(-1)
        })
    }

    private fun toggleLog(direction: Int) {
        val newValue = (logLevel + direction) % LogLevel.size
        logLevel = if (newValue >= 0) newValue else LogLevel.size + direction
        updateLogTextView()
    }

    private fun updateLogTextView() = CoroutineScope(Dispatchers.IO).launch {
        when (LogLevel.values()[logLevel]) {
            LogLevel.NORMAL -> {
                logHandler?.closeSystemLog()
                withContext(Dispatchers.Main) { logTextView?.text = commLog.toString() }
                delay(1_000)
                withContext(Dispatchers.Main) { logScrollView?.fullScroll(View.FOCUS_DOWN) }
            }
            LogLevel.VERBOSE -> {
                logHandler?.closeSystemLog()
                withContext(Dispatchers.Main) {
                    logTextView?.text = logHandler?.getLog()?.removeSuffix("\n")
                }
                delay(1_000)
                withContext(Dispatchers.Main) { logScrollView?.fullScroll(View.FOCUS_DOWN) }
            }
            LogLevel.SYSTEM -> {
                val lines = mutableListOf<String>()
                try {
                    logHandler?.getSystemLog()?.let { systemLog ->
                        for (line in systemLog) {
                            lines.add(line)
                            withContext(Dispatchers.Main) {
                                if (logLevel == LogLevel.SYSTEM.ordinal) {
                                    logTextView?.text = lines.joinToString("\n")
                                    logScrollView?.fullScroll(View.FOCUS_DOWN)
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    withContext(Dispatchers.Main) {
                        if (logLevel == LogLevel.SYSTEM.ordinal) {
                            logTextView?.text = lines.joinToString("\n")
                            logScrollView?.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }
            }
        }
    }

    private fun clearLog() {
        commLog.setLength(0)
        logHandler?.clear()
        logTextView?.text = null
        toggleLog(0)
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    private fun findDevice() {
        if (device == null) main {
            scan(unique = true).consumeAsFlow().filter { it.device.address == macAddress }
                .collect { result -> device = result.device }
        }
    }

    private fun initCommandEdit() {
        sendButton?.setOnClickListener {
            commandEditText?.text?.trim()?.takeIf { it.isNotBlank() }?.toString()?.let { sendHex ->
                try {
                    val outByteArray = sendHex.split(" ")
                        .map { java.lang.Long.parseLong(it, 16).toByte() }
                        .toByteArray()
                    main {
                        printUpdate(
                            sendHex,
                            device?.writeAsync(this, outByteArray, outByteArray.first())
                        )
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    }

    private fun printUpdate(sent: String, receive: ReceiveChannel<ByteArray>?) {
        receive?.let {
            main {
                for (update in receive) {
                    val receivedHex = update.joinToString(" ") { String.format("%02X", it) }
                    commLog.appendln(
                        "-> ${sent.toUpperCase(Locale.ROOT)}\n<- ${receivedHex.toUpperCase(
                            Locale.ROOT
                        )}"
                    )
                    updateLogTextView()
                }
            }
        }
    }

    private fun initButtons() {
        connect_gatt_button?.setOnClickListener {
            device?.connect(BleGattCallback())
        }
        disconnect_gatt_button?.setOnClickListener {
            device?.disconnect()
        }
        close_gatt_button?.setOnClickListener {
            device?.close()
        }
        debug_button?.isGone = true
        debug_button?.setOnClickListener {
            debug()
        }
    }

    //region debug
    private fun debug() {
        //fetchSystemInfo()
        getSchedules()
    }

    private fun writeAsync() = main {
        var result: ByteArray?
        try {
            result = device?.writeAsync(this, byteArrayOf(0x02), 0x02)?.receive()
            log("BLE", "result=$result")
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        try {
            result = device?.writeAsync(this, byteArrayOf(0x07), 0x05)?.receive()
            log("BLE", "result=$result")
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun fetchSystemInfo() = lifecycleScope.launchCatch {
        device?.write(byteArrayOf(0x0b, 0x01, 0x01, 0x01, 0x01))
        delay(1_000)
        printUpdate("07", device?.writeAsync(this, byteArrayOf(0x07), 0x05))
    }

    private fun getSchedules() = lifecycleScope.launchCatch {
        device?.write(byteArrayOf(0x0b, 0x01, 0x01, 0x01, 0x01))
        delay(1_000)
        printUpdate("02", device?.writeAsync(this, byteArrayOf(0x02), 0x02))
    }

    private fun sendRTC() = catch {
        val now = Date()
        val offsetFromUtc = TimeZone.getDefault().getOffset(now.time)
        val time = ((now.time + offsetFromUtc) / 1_000).toInt()

        val buffer = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(time)
        }
        device?.write(buffer.array())
    }
    //endregion

}