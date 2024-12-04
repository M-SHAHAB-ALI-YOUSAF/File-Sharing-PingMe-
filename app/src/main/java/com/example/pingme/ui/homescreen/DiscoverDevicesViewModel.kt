package com.example.pingme.ui.homescreen

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.pingme.broadcast.WifiP2pBroadcastReceiver

class DiscoverDevicesViewModel : ViewModel() {

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var broadcastReceiver: WifiP2pBroadcastReceiver

    private val _deviceList = MutableLiveData<List<WifiP2pDevice>>()
    val deviceList: LiveData<List<WifiP2pDevice>> get() = _deviceList

    private val _showProgressDialog = MutableLiveData<Boolean>()
    val showProgressDialog: LiveData<Boolean> get() = _showProgressDialog

    private val _connectionStatus = MutableLiveData<String>()
    val connectionStatus: LiveData<String> get() = _connectionStatus

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    private val _navigateToMessageFragment = MutableLiveData<Pair<Boolean, String>>()
    val navigateToMessageFragment: LiveData<Pair<Boolean, String>> get() = _navigateToMessageFragment

    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val peers = peerList.deviceList
        _deviceList.postValue(peers.toList())
    }

    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        _showProgressDialog.postValue(false) // Dismiss progress dialog

        if (info.groupFormed) {
            _connectionStatus.postValue(
                if (info.isGroupOwner) "You are the group owner" else "Connected to group owner"
            )
            _navigateToMessageFragment.postValue(
                Pair(info.isGroupOwner, info.groupOwnerAddress?.hostAddress ?: "")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(broadcastReceiver, intentFilter)
        }

        disconnect() // Disconnect any ongoing connections
    }

    fun unregisterReceiver(context: Context) {
        context.unregisterReceiver(broadcastReceiver)
    }

    fun disconnect() {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionStatus.postValue("Disconnected")
            }

            override fun onFailure(reason: Int) {
                Log.d("TAG", "onFailure: ")
            }
        })
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

        _showProgressDialog.postValue(true) // Show progress dialog
        _connectionStatus.postValue("Connecting to ${device.deviceName}")

        // Start connection attempt
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Connection started, wait for result
                wifiP2pManager.requestConnectionInfo(channel, connectionInfoListener)

                // Start the timeout countdown (15 seconds)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (_connectionStatus.value != "Connected") {
                        // Connection timed out, show a message
                        _showProgressDialog.postValue(false) // Dismiss progress dialog
                        _errorMessage.postValue("Connection timed out. You can try again.")
                    }
                }, 15000) // 15 seconds
            }

            override fun onFailure(reason: Int) {
                _showProgressDialog.postValue(false) // Dismiss progress dialog
                _errorMessage.postValue("Connection failed: $reason")
            }
        })
    }


    fun cancelProgressDialog() {
        _showProgressDialog.postValue(false) // Explicitly dismiss progress dialog if needed
    }
}
