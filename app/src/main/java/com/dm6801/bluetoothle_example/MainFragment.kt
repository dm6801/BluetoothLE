package com.dm6801.bluetoothle_example

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.dm6801.bluetoothle.*
import com.dm6801.bluetoothle.utilities.catch
import com.dm6801.bluetoothle.utilities.hashCode
import com.dm6801.bluetoothle.utilities.log
import com.dm6801.bluetoothle.utilities.main
import com.dm6801.bluetoothle_example.DevicesRecyclerAdapter.Companion.getTypedAdapter
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.toCollection
import kotlinx.coroutines.channels.withIndex
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class MainFragment : Fragment() {

    private val layout = R.layout.fragment_main
    private val recyclerView: RecyclerView? get() = devices_recycler
    private val adapter: DevicesRecyclerAdapter? get() = recyclerView?.getTypedAdapter()
    private var device: BluetoothDevice? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView()
        initButtons()
    }

    private fun initRecyclerView() {
        recyclerView?.adapter = DevicesRecyclerAdapter()
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    private fun initButtons() {
        enable_bt_button?.setOnClickListener { BLE.enable(activity) }
        scan_ble_button?.setOnClickListener {
            main {
                scan(unique = true).consumeAsFlow().withIndex().map {
                    if (it.index == 0) adapter?.clearList()
                    adapter?.add(it.value)
                }.collect()
            }
        }
        scan_ble_stop_button?.setOnClickListener {
            BLE.stopScan()
        }
        connect_gatt_button?.setOnClickListener {
            device?.connect(BleGattCallback())
        }
        disconnect_gatt_button?.setOnClickListener {
            device?.disconnect()
        }
        close_gatt_button?.setOnClickListener {
            device?.close()
        }
        test_button?.setOnClickListener {
            //sendRTC()
            //fetchSystemInfo()
            writeAsync()
        }
    }

    private fun writeAsync() = main {
        var result: ByteArray?
        try {
            result = device?.asyncWrite(byteArrayOf(0x02))?.await()
            log("BLE", "result=$result")
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        try {
            result = device?.asyncWrite(byteArrayOf(0x07))?.await()
            log("BLE", "result=$result")
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun fetchSystemInfo() = catch {
        device?.write(byteArrayOf(0x07))
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

}