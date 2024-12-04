package com.example.pingme.adaptor

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pingme.R
import com.example.pingme.helper.WifiP2pUtils


class DeviceAdapter(
    private var wifiP2pDeviceList: List<WifiP2pDevice>,
    private val onDeviceClick: (WifiP2pDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = wifiP2pDeviceList[position]
        holder.bind(device)
        holder.btnConnect.setOnClickListener { onDeviceClick(device) }
    }

    override fun getItemCount(): Int = wifiP2pDeviceList.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateDevices(newDeviceList: List<WifiP2pDevice>) {
        wifiP2pDeviceList = newDeviceList
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvDeviceAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
        private val tvDeviceDetails: TextView = itemView.findViewById(R.id.tvDeviceDetails)
        val btnConnect: Button = itemView.findViewById(R.id.btnConnect)

        fun bind(device: WifiP2pDevice) {
            tvDeviceName.text = device.deviceName
            tvDeviceAddress.text = device.deviceAddress
            tvDeviceDetails.text = WifiP2pUtils.getDeviceStatus(device.status)
        }
    }
}
