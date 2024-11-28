package com.example.pingme.ui.homescreen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pingme.R
import com.example.pingme.adaptor.DeviceAdapter
import com.example.pingme.databinding.FragmentDiscoverDevicesBinding
import com.google.android.material.snackbar.Snackbar

class DiscoverDevices : Fragment(R.layout.fragment_discover_devices) {

    private var _binding: FragmentDiscoverDevicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiscoverDevicesViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceAdapter

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1
        const val STORAGE_PERMISSION_REQUEST_CODE = 2
        const val TAG = "WiFiDirectDemo"
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDiscoverDevicesBinding.bind(view)

        // Initialize ViewModel
        viewModel.initialize(requireContext())

        // Set up RecyclerView
        deviceAdapter = DeviceAdapter(emptyList()) { selectedDevice ->
            Log.d(TAG, "Selected device: ${selectedDevice.deviceName} (${selectedDevice.deviceAddress})")
            viewModel.connectToDevice(selectedDevice)
        }


        binding.rvDeviceList.layoutManager = LinearLayoutManager(requireContext())

        binding.rvDeviceList.adapter = deviceAdapter

        // Observers
        viewModel.deviceList.observe(viewLifecycleOwner) { devices ->
            if (devices.isNotEmpty()) {
                binding.lottie.visibility = View.GONE
            } else {
                binding.lottie.visibility = View.VISIBLE
            }
            deviceAdapter.updateDevices(devices)
        }

        viewModel.connectionStatus.observe(viewLifecycleOwner, Observer { status ->
            Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
        })

        viewModel.errorMessage.observe(viewLifecycleOwner, Observer { error ->
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
        })

        // In your Fragment
        viewModel.navigateToMessageFragment.observe(viewLifecycleOwner, Observer { result ->
            result?.let {
                val (isGroupOwner, groupOwnerAddress) = it
                val navController = findNavController()

                val bundle = Bundle().apply {
                    putBoolean("isGroupOwner", isGroupOwner)
                    putString("groupOwnerAddress", groupOwnerAddress)
                }

                navController.navigate(R.id.action_discoverDevices_to_message2, bundle)
            }
        })

        // Search WiFi button action
        binding.rightImage.setOnClickListener {
            if (isPermissionsGranted()) {
                binding.lottie.visibility = View.VISIBLE
                viewModel.discoverPeers()
            } else {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_CONTACTS
                    ), LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }

        viewModel.registerReceiver(requireContext())
    }

    private fun isPermissionsGranted(): Boolean {
        val locationPermission = ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val storagePermission = ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val contactsPermission = ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        return locationPermission && storagePermission && contactsPermission
    }

    override fun onPause() {
        super.onPause()
        viewModel.unregisterReceiver(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                binding.lottie.visibility = View.VISIBLE
                viewModel.discoverPeers()
            } else {
                Log.e(TAG, "Location permission denied")
            }
        } else if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Storage permission granted")
            } else {
                Log.e(TAG, "Storage permission denied")
            }
        }
    }


    override fun onResume() {
        super.onResume()
        // Re-register the receiver if needed
        viewModel.registerReceiver(requireContext())

        // Recheck the connection info
        viewModel.checkConnectionInfo()
    }



}

