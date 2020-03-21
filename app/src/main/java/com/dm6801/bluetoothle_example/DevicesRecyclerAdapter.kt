package com.dm6801.bluetoothle_example

import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_ble_device.view.*

class DevicesRecyclerAdapter : RecyclerView.Adapter<DevicesRecyclerAdapter.ViewHolder>() {

    companion object {
        private const val layout = R.layout.item_ble_device
        @Suppress("UNCHECKED_CAST")
        fun <T> RecyclerView.getTypedAdapter() = adapter as? T
    }

    private val asyncListDiffer = AsyncListDiffer(this, object : DiffUtil.ItemCallback<ScanResult>() {
        override fun areItemsTheSame(oldItem: ScanResult, newItem: ScanResult) = oldItem.device.address == newItem.device.address
        override fun areContentsTheSame(oldItem: ScanResult, newItem: ScanResult) = oldItem == newItem

    })

    fun submitList(results: List<ScanResult>) {
        asyncListDiffer.submitList(results)
    }

    fun clearList() {
        asyncListDiffer.submitList(null)
    }

    fun add(result: ScanResult) {
        submitList(asyncListDiffer.currentList + result)
    }

    override fun getItemCount(): Int = asyncListDiffer.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val list = asyncListDiffer.currentList
        if (position in 0 until list.size)
            holder.bind(list[position], position)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val list = asyncListDiffer.currentList
        if (position in 0 until list.size)
            holder.bind(list[position], position, payloads)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView? get() = itemView.ble_device_name
        private val macAddressTextView: TextView? get() = itemView.ble_device_mac_address

        @Suppress("UNUSED_PARAMETER")
        fun bind(item: ScanResult, position: Int, payloads: MutableList<Any>? = null) {
            nameTextView?.text = item.device?.name
            macAddressTextView?.text = item.device?.address
        }
    }
}