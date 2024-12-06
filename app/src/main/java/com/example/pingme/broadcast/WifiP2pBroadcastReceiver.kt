package com.example.pingme.broadcast

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.navigation.findNavController
import com.example.pingme.MainActivity
import com.example.pingme.R

class WifiP2pBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val peerListListener: WifiP2pManager.PeerListListener
) : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.d("WiFiP2P", "Wi-Fi Direct is enabled")
                } else {
                    Log.d("WiFiP2P", "Wi-Fi Direct is not enabled")
                }
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                manager.requestPeers(channel, peerListListener)
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo =
                    intent.getParcelableExtra<android.net.NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                if (networkInfo != null && networkInfo.isConnected) {
                    Log.d("WiFiP2P", "Connection changed: connected")
                    manager.requestConnectionInfo(channel) { info ->
                        if (info.groupFormed) {
                            val isGroupOwner = info.isGroupOwner
                            val groupOwnerAddress = info.groupOwnerAddress?.hostAddress ?: ""
                            Log.d("WiFiP2P", "Group formed: Owner=$isGroupOwner, Address=$groupOwnerAddress")

                            val navController = (context as? MainActivity)?.findNavController(R.id.nav_host_fragment)
                            val bundle = Bundle().apply {
                                putBoolean("isGroupOwner", isGroupOwner)
                                putString("groupOwnerAddress", groupOwnerAddress)
                            }
                            Handler(Looper.getMainLooper()).post {
                                navController?.navigate(R.id.action_discoverDevices_to_message2, bundle)
                            }
                        }
                    }
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                Log.d("WiFiP2P", "Device state changed")
            }
        }
    }


}