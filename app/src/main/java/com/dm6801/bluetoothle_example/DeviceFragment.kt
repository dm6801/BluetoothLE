package com.dm6801.bluetoothle_example

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.dm6801.bluetoothle.*
import com.dm6801.bluetoothle.utilities.main
import com.dm6801.bluetoothle.utilities.weakRef
import kotlinx.android.synthetic.main.fragment_device.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter

class DeviceFragment(device: BluetoothDevice) : Fragment() {

    private val layout: Int = R.layout.fragment_device
    private val macAddressTextView: TextView? get() = device_mac_address
    private val logTextView: TextView? get() = device_log
    private val commandEditText: EditText? get() = device_write_buffer
    private val sendButton: Button? get() = device_write_button

    private var macAddress: String = device.address ?: ""
    private var device: BluetoothDevice? by weakRef(device)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setMacAddressText()
        initCommandEdit()
        initButtons()
    }

    private fun setMacAddressText() {
        macAddressTextView?.text = macAddress
    }

    override fun onStart() {
        super.onStart()
        findDevice()
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
            commandEditText?.text?.trim()?.takeIf { it.isNotBlank() }?.let { sendHex ->
                val outByteArray = sendHex.split(" ")
                    .map { java.lang.Long.parseLong(it, 16).toByte() }
                    .toByteArray()
                main {
                    try {
                        val result = device?.asyncWrite(outByteArray)?.await() ?: return@main
                        val previousLog = logTextView?.text ?: ""
                        val updatedLog = StringBuilder(previousLog)
                        updatedLog.append(
                            if (previousLog.isBlank()) "-> $sendHex"
                            else "\n-> $sendHex"
                        )
                        logTextView?.text = updatedLog.toString()
                        val receivedHex =
                            result.joinToString(" ") { String.format("%02x", it) }
                        updatedLog.append("\n<- $receivedHex")
                        logTextView?.text = updatedLog.toString()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
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
    }

}