package com.example.pingme.ui.homescreen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog

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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDiscoverDevicesBinding.bind(view)

        // Initialize ViewModel
        viewModel.initialize(requireContext())

        // Set up RecyclerView
        deviceAdapter = DeviceAdapter(emptyList()) { selectedDevice ->
            Log.d(TAG, "Selected device: ${selectedDevice.deviceName} (${selectedDevice.deviceAddress})")
            binding.lottieWaiting.visibility= View.VISIBLE
            viewModel.connectToDevice(selectedDevice)
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showExitConfirmationDialog()
                }
            }
        )

        binding.rvDeviceList.layoutManager = LinearLayoutManager(requireContext())

        binding.rvDeviceList.adapter = deviceAdapter

        // Observers
        viewModel.deviceList.observe(viewLifecycleOwner) { devices ->
            if (devices.isNotEmpty()) {
                binding.lottie.visibility = View.GONE
            }

            deviceAdapter.updateDevices(devices)
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                binding.lottieWaiting.visibility=View.GONE
            }
        }

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
        binding.searchDevices.setOnClickListener {
            if (!isWifiEnabled()) {
                promptEnableWifi()
                return@setOnClickListener
            }

            if (isLocationEnabled()) {
                if (isPermissionsGranted()) {
                    binding.lottie.visibility = View.VISIBLE
                    binding.lottieWaiting.visibility= View.GONE
                    viewModel.discoverPeers()
                } else {
                    requestPermissions(
                        getRequiredPermissions(),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            } else {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        }

        viewModel.registerReceiver(requireContext())
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        // Always require fine location for Wi-Fi Direct and nearby devices
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        // For Android 13 (SDK 33) and above, we can also request coarse location and media permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)

        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // Adding coarse location for approximate location
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(Manifest.permission.READ_CONTACTS)
        return permissions.toTypedArray()
    }

    private fun isPermissionsGranted(): Boolean {
        val locationPermission = ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationPermission = ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val contactsPermission = ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        return locationPermission && coarseLocationPermission && contactsPermission &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || // Handle legacy permissions
                        (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED))
    }


    override fun onPause() {
        super.onPause()
        viewModel.unregisterReceiver(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val deniedPermissions = mutableListOf<String>()
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i])
                }
            }

            if (deniedPermissions.isEmpty()) {
                // All necessary permissions granted
                binding.lottie.visibility = View.VISIBLE
                viewModel.discoverPeers()
            } else {
                handleDeniedPermissions(deniedPermissions)
            }
        }
    }


    private fun handleDeniedPermissions(deniedPermissions: List<String>) {
        val permanentlyDenied = deniedPermissions.filter { permission ->
            !ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission)
        }

        if (permanentlyDenied.isNotEmpty()) {
            showAppSettingsPrompt("Some critical permissions are permanently denied. Please enable them in Settings to proceed.")
        } else {
            Snackbar.make(
                binding.root,
                "The app needs these permissions to function. Please grant them.",
                Snackbar.LENGTH_LONG
            )
                .setAction("Retry") {
                    requestPermissions(getRequiredPermissions(), LOCATION_PERMISSION_REQUEST_CODE)
                }
                .show()
        }
    }



    override fun onResume() {
        super.onResume()
        binding.lottie.visibility = View.GONE
        viewModel.registerReceiver(requireContext())
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun isWifiEnabled(): Boolean {
        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        return wifiManager.isWifiEnabled
    }

    private fun promptEnableWifi() {
        Snackbar.make(binding.root, "Wi-Fi is disabled. Please enable Wi-Fi to discover devices.", Snackbar.LENGTH_LONG)
            .setAction("Enable") {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                startActivity(intent)
            }.show()
    }

    private fun showExitConfirmationDialog() {
        // Inflate the custom layout
        val dialogView = layoutInflater.inflate(R.layout.alertbox, null)

        // Create the BottomSheetDialog
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(dialogView)
        bottomSheetDialog.setCancelable(false)
        // Handle buttons
        val btnYes = dialogView.findViewById<TextView>(R.id.btnYes)
        val btnNo = dialogView.findViewById<TextView>(R.id.btnNo)

        btnYes.setOnClickListener {
            requireActivity().finish() // Exit the activity
            bottomSheetDialog.dismiss()
        }

        btnNo.setOnClickListener {
            bottomSheetDialog.dismiss() // Dismiss the dialog
        }

        bottomSheetDialog.show()
    }

    private fun showAppSettingsPrompt(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Permission Required")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}

