package com.example.pingme.ui.homescreen

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues.TAG
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.findNavController
import com.example.pingme.MainActivity
import com.example.pingme.R
import com.example.pingme.broadcast.WifiP2pBroadcastReceiver


class DiscoverDevicesViewModel : ViewModel() {

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var broadcastReceiver: WifiP2pBroadcastReceiver

    private val _deviceList = MutableLiveData<List<WifiP2pDevice>>()
    val deviceList: LiveData<List<WifiP2pDevice>> get() = _deviceList

    private val _connectionStatus = MutableLiveData<String>()
    val connectionStatus: LiveData<String> get() = _connectionStatus

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val peers = peerList.deviceList
        _deviceList.postValue(peers.toList())
    }

    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        if (info.groupFormed) {
            _connectionStatus.postValue(
                if (info.isGroupOwner) "You are the group owner" else "Connected to group owner"
            )
        }
    }

    fun initialize(context: Context) {
        wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(context, context.mainLooper, null)

        broadcastReceiver = WifiP2pBroadcastReceiver(wifiP2pManager, channel, peerListListener)
    }

    fun registerReceiver(context: Context) {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(broadcastReceiver, intentFilter)
    }

    fun unregisterReceiver(context: Context) {
        context.unregisterReceiver(broadcastReceiver)
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionStatus.postValue("Peer discovery started successfully")
            }

            override fun onFailure(reason: Int) {
                _errorMessage.postValue("Peer discovery failed: $reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionStatus.postValue("Connecting to ${device.deviceName}")
                wifiP2pManager.requestConnectionInfo(channel, connectionInfoListener)
            }

            override fun onFailure(reason: Int) {
                _errorMessage.postValue("Connection failed: $reason")
            }
        })
    }

    // In your ViewModel
    private val _navigateToMessageFragment = MutableLiveData<Pair<Boolean, String>>()
    val navigateToMessageFragment: LiveData<Pair<Boolean, String>> get() = _navigateToMessageFragment

    fun checkConnectionInfo() {
        wifiP2pManager.requestConnectionInfo(channel) { info ->
            if (info.groupFormed) {
                // Check if the device is the group owner or connected
                val isGroupOwner = info.isGroupOwner
                val groupOwnerAddress = info.groupOwnerAddress?.hostAddress ?: ""
                Log.d("WiFiP2P", "Group formed: Owner=$isGroupOwner, Address=$groupOwnerAddress")

                // Update the connection status in the UI
                _connectionStatus.postValue(if (isGroupOwner) "You are the group owner" else "Connected to group owner")

                // Trigger navigation
                _navigateToMessageFragment.postValue(Pair(isGroupOwner, groupOwnerAddress))
            }
        }
    }


}
