package com.example.pingme.ui.message

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pingme.MainActivity
import com.example.pingme.R
import com.example.pingme.adaptor.MessageAdapter
import com.example.pingme.databinding.FragmentMessageBinding
import com.example.pingme.datamodel.MessageItem
import com.example.pingme.ui.bottomsheet.OptionsBottomSheet
import com.example.pingme.ui.dialogbox.DialogBox
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class Message : Fragment(R.layout.fragment_message) {

    private var _binding: FragmentMessageBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MessageAdapter
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private val messages = mutableListOf<MessageItem>()
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var wifiDirectReceiver: BroadcastReceiver

    companion object {
        const val PORT = 8888
        const val REQUEST_IMAGE_PICK = 1001
        const val STORAGE_PERMISSION_REQUEST = 2002
        const val REQUEST_VIDEO_PICK = 1002
        const val REQUEST_DOCUMENT_PICK = 1003
        const val REQUEST_CONTACT_PICK = 1004

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = requireContext().getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(requireContext(), Looper.getMainLooper(), null)

        wifiDirectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                if (networkInfo != null && !networkInfo.isConnected) {
                    activity?.runOnUiThread {
                        socket?.close()
                        serverSocket?.close()
                        val dialogFragment = DialogBox()
                        dialogFragment.show(parentFragmentManager, "CustomDialogFragment")
                    }
                }
            }
        }
    }
}
    private fun disconnectAndNavigate() {
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                navigateBackToDiscover()
            }

            override fun onFailure(reason: Int) {
                navigateBackToDiscover()
            }
        })
    }

    // Navigate back to DiscoverFragment
    private fun navigateBackToDiscover() {
        findNavController().navigate(R.id.action_message2_to_discoverDevices)
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMessageBinding.bind(view)

        binding.rvDeviceList.layoutManager = LinearLayoutManager(requireContext())
        adapter = MessageAdapter(messages, childFragmentManager)
        binding.rvDeviceList.adapter = adapter

        checkStoragePermission()

        val isGroupOwner = arguments?.getBoolean("isGroupOwner", false) ?: false
        val groupOwnerAddress = arguments?.getString("groupOwnerAddress")

        if (isGroupOwner) {
            setupServer()
        } else {
            setupClient(groupOwnerAddress ?: "")
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle("Exit Chat")
                        .setMessage("Are you sure you want to leave the chat?")
                        .setPositiveButton("Yes") { _, _ ->
                            disconnectAndNavigate()
                        }
                        .setNegativeButton("No") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()
                    dialog.show()
                }
            })

        manager.requestConnectionInfo(channel) { info ->
            if (!info.groupFormed) {
                activity?.runOnUiThread {
                    findNavController().navigate(R.id.action_message2_to_discoverDevices)
                }
            }
        }


        binding.btnSendMessage.setOnClickListener {
            val message = binding.etMessage.text.toString()
            if (message.isNotEmpty()) {
                sendMessage(message)
                binding.etMessage.text.clear()
            }
        }

        binding.camera.setOnClickListener {
            val optionsBottomSheet = OptionsBottomSheet { optionType ->
                when (optionType) {
                    OptionsBottomSheet.OptionType.IMAGE -> {
                        val intent = Intent(
                            Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        ).apply {
                            type = "image/*" // Images only
                        }
                        startActivityForResult(intent, REQUEST_IMAGE_PICK)
                    }

                    OptionsBottomSheet.OptionType.VIDEO -> {
                        val intent = Intent(
                            Intent.ACTION_PICK,
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        ).apply {
                            type = "video/*" // Videos only
                        }
                        startActivityForResult(intent, REQUEST_VIDEO_PICK)
                    }

                    OptionsBottomSheet.OptionType.DOCUMENT -> {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"
                        }
                        startActivityForResult(intent, REQUEST_DOCUMENT_PICK)
                    }

                    OptionsBottomSheet.OptionType.CONTACT -> {
                        val intent =
                            Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                        startActivityForResult(intent, REQUEST_CONTACT_PICK)
                    }
                }
            }
            optionsBottomSheet.show(childFragmentManager, optionsBottomSheet.tag)
        }


    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_IMAGE_PICK -> {
                val imageUri = data?.data
                imageUri?.let { processImageSelection(it) }
            }

            REQUEST_VIDEO_PICK -> {
                val videoUri = data?.data
                videoUri?.let { processVideoSelection(it) }
            }

            REQUEST_DOCUMENT_PICK -> {
                val documentUri = data?.data
                if (documentUri != null) {
                    val documentName =
                        getFileName(documentUri) // Helper function to get the file name
                    sendDocument(documentUri, documentName)
                    addMessage(
                        message = null,
                        isMe = true,
                        documentName = documentName,
                        documentUri = documentUri.toString()
                    )
                }
            }

            REQUEST_CONTACT_PICK -> {
                val contactUri = data?.data
                if (contactUri != null) {
                    val contactDetails = getContactDetails(contactUri)
                    sendContact(contactDetails)
                    addMessage(
                        message = null,
                        isMe = true,
                        contactName = contactDetails.first,
                        contactPhone = contactDetails.second
                    )
                }
            }
        }
    }

    @SuppressLint("Range")
    private fun getContactDetails(contactUri: Uri): Pair<String, String> {
        val cursor = requireContext().contentResolver.query(contactUri, null, null, null, null)
        var contactName = "Unknown"
        var contactPhone = "Unknown"
        cursor?.use {
            if (it.moveToFirst()) {
                // Get contact name
                contactName = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))

                // Get contact phone number
                val contactId = it.getString(it.getColumnIndex(ContactsContract.Contacts._ID))
                val phoneCursor = requireContext().contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )
                phoneCursor?.use { pc ->
                    if (pc.moveToFirst()) {
                        contactPhone = pc.getString(pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    }
                }
            }
        }
        return Pair(contactName, contactPhone)
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "Unknown"
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                fileName =
                    it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
            }
        }
        return fileName
    }


    private fun processImageSelection(imageUri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(imageUri)
            val imageBytes = inputStream?.readBytes()
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes?.size ?: 0)
            sendImage(imageUri)
            addMessage(null, true, bitmap)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun processVideoSelection(videoUri: Uri) {
        try {
            // Generate a thumbnail for the video
            val thumbnail = getVideoThumbnail(videoUri)

            // Obtain the file size without loading the entire file
            val fileDescriptor = requireContext().contentResolver.openFileDescriptor(videoUri, "r")
            val videoSize = fileDescriptor?.statSize ?: 0L
            fileDescriptor?.close()

            // Send video in chunks
            sendVideo(videoUri, null, videoSize)

            // Update the UI with the thumbnail and video URI
            addMessage(null, true, videoUri = videoUri.toString(), videoThumbnail = thumbnail)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getVideoThumbnail(videoUri: Uri): Bitmap? {
        return try {
            MediaStore.Video.Thumbnails.getThumbnail(
                requireContext().contentResolver,
                ContentUris.parseId(videoUri),
                MediaStore.Video.Thumbnails.MINI_KIND,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    private fun sendImage(imageUri: Uri) {
        thread {
            try {
                val inputStream = context?.contentResolver?.openInputStream(imageUri)
                val byteArray = inputStream?.readBytes() ?: return@thread
                val chunkSize = 1024
                var offset = 0

                socket?.getOutputStream()?.let { outputStream ->
                    val dataOutputStream = DataOutputStream(outputStream)
                    dataOutputStream.writeUTF("IMAGE")
                    dataOutputStream.writeInt(byteArray.size)
                    while (offset < byteArray.size) {
                        val sizeToSend = (byteArray.size - offset).coerceAtMost(chunkSize)
                        dataOutputStream.writeInt(sizeToSend)
                        dataOutputStream.write(byteArray, offset, sizeToSend)
                        offset += sizeToSend
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun sendVideo(videoUri: Uri, videoBytes: ByteArray?, videoSize: Long) {
        thread {
            try {
                socket?.getOutputStream()?.let { outputStream ->
                    val dataOutputStream = DataOutputStream(outputStream)

                    // Send metadata
                    dataOutputStream.writeUTF("VIDEO")
                    dataOutputStream.writeLong(videoSize)

                    // Read and send the file in chunks
                    val fileInputStream = requireContext().contentResolver.openInputStream(videoUri)
                    val buffer = ByteArray(1024 * 1024) // 1 MB buffer
                    var bytesRead: Int

                    while (fileInputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                        dataOutputStream.writeInt(bytesRead)
                        dataOutputStream.write(buffer, 0, bytesRead)
                    }

                    fileInputStream?.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    private fun sendDocument(documentUri: Uri, documentName: String) {
        thread {
            try {
                val inputStream = context?.contentResolver?.openInputStream(documentUri)
                val byteArray = inputStream?.readBytes() ?: return@thread
                val chunkSize = 1024
                var offset = 0

                socket?.getOutputStream()?.let { outputStream ->
                    val dataOutputStream = DataOutputStream(outputStream)
                    dataOutputStream.writeUTF("DOCUMENT")
                    dataOutputStream.writeUTF(documentName) // Send document name
                    dataOutputStream.writeInt(byteArray.size) // Send document size
                    while (offset < byteArray.size) {
                        val sizeToSend = (byteArray.size - offset).coerceAtMost(chunkSize)
                        dataOutputStream.writeInt(sizeToSend)
                        dataOutputStream.write(byteArray, offset, sizeToSend)
                        offset += sizeToSend
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun sendContact(contactDetails: Pair<String, String>) {
        thread {
            try {
                socket?.getOutputStream()?.let { outputStream ->
                    val dataOutputStream = DataOutputStream(outputStream)
                    dataOutputStream.writeUTF("CONTACT")
                    dataOutputStream.writeUTF(contactDetails.first)  // Contact name
                    dataOutputStream.writeUTF(contactDetails.second) // Contact phone number
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }



    private fun sendMessage(message: String) {
        thread {
            try {
                socket?.getOutputStream()?.let { outputStream ->
                    val dataOutputStream = DataOutputStream(outputStream)
                    dataOutputStream.writeUTF("TEXT")
                    dataOutputStream.writeUTF(message)
                    activity?.runOnUiThread {
                        addMessage(message, true, null)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun saveDocumentToDeviceStorage(byteArray: ByteArray, fileName: String): String {
        val resolver = requireContext().contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/*")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/ChatDocuments")
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        uri?.let {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(byteArray)
            }
        }
        return uri.toString()
    }


    private fun saveImageToDeviceStorage(bitmap: Bitmap): String? {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        val resolver = requireContext().contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ChatImages")
        }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?.let { uri ->
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                uri.toString()
            }
    }

    private fun listenForMessages() {
        thread {
            try {
                socket?.getInputStream()?.let { inputStream ->
                    val dataInputStream = DataInputStream(inputStream)
                    while (true) {
                        val messageType = dataInputStream.readUTF()
                        when (messageType) {
                            "TEXT" -> {
                                val message = dataInputStream.readUTF()
                                if (message == "DISCONNECT") {
                                    activity?.runOnUiThread {
                                        AlertDialog.Builder(requireContext())
                                            .setTitle("Disconnected")
                                            .setMessage("The other user has left the chat.")
                                            .setPositiveButton("OK") { _, _ ->
                                                navigateBackToDiscover() // Navigate back to discover
                                            }
                                            .show()
                                    }
                                    break
                                } else {
                                activity?.runOnUiThread {
                                    addMessage(message, false, null)
                                }
                            }
                            }

                            "IMAGE" -> {
                                val imageSize = dataInputStream.readInt()
                                val byteArray = ByteArray(imageSize)
                                var offset = 0
                                while (offset < imageSize) {
                                    val chunkSize = dataInputStream.readInt()
                                    dataInputStream.readFully(byteArray, offset, chunkSize)
                                    offset += chunkSize
                                }
                                val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, imageSize)
                                saveImageToDeviceStorage(bitmap)?.let { imagePath ->
                                    activity?.runOnUiThread {
                                        addMessage(null, false, null, imagePath)
                                    }
                                }
                            }

                            "VIDEO" -> {
                                val videoSize = dataInputStream.readLong() // Read the total size of the video
                                Log.d("VideoTransfer", "Total video size to receive: $videoSize bytes")

                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val outputFile = File(downloadsDir, "received_video_${System.currentTimeMillis()}.mp4")
                                val fileOutputStream = FileOutputStream(outputFile)

                                var remainingSize = videoSize
                                var receivedSize: Long = 0

                                // Assume there is a ProgressBar in your UI (updateProgressBar is a function to update its value)
                                activity?.runOnUiThread {
                                    binding.progressContainer.visibility = View.VISIBLE
                                    updateProgressBar(0) }

                                while (remainingSize > 0) {
                                    val chunkSize = dataInputStream.readInt() // Read the size of the current chunk
                                    val buffer = ByteArray(chunkSize)
                                    dataInputStream.readFully(buffer) // Read the chunk into the buffer
                                    fileOutputStream.write(buffer) // Write the buffer to the file
                                    remainingSize -= chunkSize
                                    receivedSize += chunkSize

                                    // Calculate progress
                                    val progress = ((receivedSize * 100) / videoSize).toInt()

                                    // Log progress and update UI
                                    Log.d("VideoTransfer", "Received $chunkSize bytes. Remaining: $remainingSize bytes. Progress: $progress%")
                                    activity?.runOnUiThread { updateProgressBar(progress) }
                                }

                                fileOutputStream.close()

                                // Notify UI with the file path
                                activity?.runOnUiThread {
                                    binding.progressContainer.visibility=View.GONE
                                    addMessage(null, false, videoUri = outputFile.absolutePath)
                                }
                            }


                            "DOCUMENT" -> {
                                val documentName = dataInputStream.readUTF() // Read the document name
                                val documentSize = dataInputStream.readInt() // Read the total document size
                                Log.d("DocumentTransfer", "Total document size to receive: $documentSize bytes")

                                val byteArray = ByteArray(documentSize) // Allocate buffer for the document
                                var offset = 0
                                var receivedSize = 0

                                // Show the progress bar in the UI
                                activity?.runOnUiThread {
                                    binding.progressContainer.visibility = View.VISIBLE
                                    updateProgressBar(0) // Reset progress bar to 0
                                }

                                while (offset < documentSize) {
                                    val chunkSize = dataInputStream.readInt() // Read the size of the current chunk
                                    dataInputStream.readFully(byteArray, offset, chunkSize) // Read the chunk into the buffer
                                    offset += chunkSize
                                    receivedSize += chunkSize

                                    // Calculate progress
                                    val progress = ((receivedSize * 100) / documentSize).toInt()

                                    // Log progress and update the UI
                                    Log.d("DocumentTransfer", "Received $chunkSize bytes. Progress: $progress%")
                                    activity?.runOnUiThread {
                                        updateProgressBar(progress) // Update progress bar
                                    }
                                }

                                // Save the document to device storage
                                val documentUri = saveDocumentToDeviceStorage(byteArray, documentName)

                                // Hide the progress bar and update UI with the document information
                                activity?.runOnUiThread {
                                    binding.progressContainer.visibility = View.GONE
                                    addMessage(
                                        message = null,
                                        isMe = false,
                                        documentName = documentName,
                                        documentUri = documentUri
                                    )
                                }
                            }

                            "CONTACT" -> {
                                val contactName = dataInputStream.readUTF()
                                val contactPhone = dataInputStream.readUTF()
                                activity?.runOnUiThread {
                                    addMessage(
                                        message = null,
                                        isMe = false,
                                        contactName = contactName,
                                        contactPhone = contactPhone
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun updateProgressBar(progress: Int) {
        binding.progressBar.progress = progress
        binding.progressTextView.text = "Progress: $progress%"
    }


    private fun addMessage(
        message: String? = null,
        isMe: Boolean,
        imageBitmap: Bitmap? = null,
        imageUri: String? = null,
        videoUri: String? = null,
        videoThumbnail: Bitmap? = null,
        documentName: String? = null,
        documentUri: String? = null,
        contactName: String? = null,
        contactPhone: String? = null
    ) {
        val timestamp = System.currentTimeMillis()
        val messageItem = MessageItem(
            message = message,
            isMe = isMe,
            imageBitmap = imageBitmap,
            imageUri = imageUri,
            videoUri = videoUri,
            videoThumbnail = videoThumbnail,
            documentName = documentName,
            documentUri = documentUri,
            timestamp = timestamp,
            contactName = contactName,
            contactPhone = contactPhone
        )
        messages.add(messageItem)
        adapter.notifyItemInserted(messages.size - 1)
        binding.rvDeviceList.scrollToPosition(messages.size - 1)
    }


    private fun setupServer() {
        thread {
            try {
                serverSocket = ServerSocket(PORT)
                socket = serverSocket?.accept()
                listenForMessages()
                activity?.runOnUiThread {
                    binding.deviceName.text = getString(R.string.connected)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun setupClient(groupOwnerAddress: String) {
        thread {
            try {
                socket = Socket(groupOwnerAddress, PORT)
                listenForMessages()
                activity?.runOnUiThread {
                    binding.deviceName.text = getString(R.string.connected)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST
            )
        }
    }

    override fun onDestroyView() {
        try {
            socket?.close()
            serverSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        try {
            socket?.close()
            serverSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        super.onDestroy()
    }




    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        requireContext().registerReceiver(wifiDirectReceiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(wifiDirectReceiver)
    }
}
