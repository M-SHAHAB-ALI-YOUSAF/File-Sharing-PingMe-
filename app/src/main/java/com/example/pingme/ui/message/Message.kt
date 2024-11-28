package com.example.pingme.ui.message

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pingme.R
import com.example.pingme.adaptor.MessageAdapter
import com.example.pingme.databinding.FragmentMessageBinding
import com.example.pingme.datamodel.MessageItem
import com.example.pingme.ui.bottomsheet.OptionsBottomSheet
import java.io.DataInputStream
import java.io.DataOutputStream
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

    companion object {
        const val PORT = 8888
        const val REQUEST_IMAGE_PICK = 1001
        const val STORAGE_PERMISSION_REQUEST = 2002
        const val REQUEST_VIDEO_PICK = 1002
        const val REQUEST_DOCUMENT_PICK = 1003
        const val REQUEST_CONTACT_PICK = 1004

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMessageBinding.bind(view)

        binding.rvDeviceList.layoutManager = LinearLayoutManager(requireContext())
        adapter = MessageAdapter(messages, childFragmentManager)
        binding.rvDeviceList.adapter = adapter


        checkStoragePermission()


        binding.root.rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            binding.root.rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = binding.root.rootView.height
            val keyboardHeight = screenHeight - r.bottom
            if (keyboardHeight > 100) { // Keyboard is visible
                binding.messageContainer.setPadding(0, 0, 0, keyboardHeight)
            } else { // Keyboard is hidden
                binding.messageContainer.setPadding(0, 0, 0, 0)
            }
        }


        val isGroupOwner = arguments?.getBoolean("isGroupOwner", false) ?: false
        val groupOwnerAddress = arguments?.getString("groupOwnerAddress")

        if (isGroupOwner) {
            setupServer()
        }
        else {
            setupClient(groupOwnerAddress ?: "")
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
                        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                            type = "image/*" // Images only
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // Allow multiple selections
                        }
                        startActivityForResult(intent, REQUEST_IMAGE_PICK)
                    }

                    OptionsBottomSheet.OptionType.VIDEO -> {
                        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).apply {
                            type = "video/*" // Videos only
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // Allow multiple selections
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
            REQUEST_IMAGE_PICK, REQUEST_VIDEO_PICK -> {
                val clipData = data?.clipData
                val uriList = mutableListOf<Uri>()

                val itemSelected = clipData?.itemCount?.let { minOf(it, 10) }
                if (clipData != null) {
                    for (i in 0 until itemSelected!!) {
                        uriList.add(clipData.getItemAt(i).uri)
                    }
                } else {
                    // Single item selected
                    data?.data?.let { uriList.add(it) }
                }

                // Process each selected URI
                uriList.forEach { uri ->
                    if (requestCode == REQUEST_IMAGE_PICK) {
                        processImageSelection(uri)
                    } else if (requestCode == REQUEST_VIDEO_PICK) {
                        processVideoSelection(uri)
                    }
                }
            }

            REQUEST_DOCUMENT_PICK -> {
                val documentUri = data?.data
                if (documentUri != null) {
                    val documentName =
                        getFileName(documentUri)
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
            val thumbnail = getVideoThumbnail(videoUri)
            val videoBytes = requireContext().contentResolver.openInputStream(videoUri)?.readBytes()
            val videoSize = videoBytes?.size ?: 0
            sendVideo(videoUri, videoBytes, videoSize)

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

    private fun sendVideo(videoUri: Uri, videoBytes: ByteArray?, videoSize: Int) {
        thread {
            try {
                socket?.getOutputStream()?.let { outputStream ->
                    val dataOutputStream = DataOutputStream(outputStream)
                    dataOutputStream.writeUTF("VIDEO")
                    dataOutputStream.writeInt(videoSize)


                    var offset = 0
                    val chunkSize = 1024
                    while (offset < videoSize) {
                        val sizeToSend = (videoSize - offset).coerceAtMost(chunkSize)
                        dataOutputStream.writeInt(sizeToSend)
                        dataOutputStream.write(videoBytes, offset, sizeToSend)
                        offset += sizeToSend
                    }
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
                    dataOutputStream.writeUTF(documentName)
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

    private fun sendContact(contactDetails: Pair<String, String>) {
        thread {
            try {
                socket?.getOutputStream()?.let { outputStream ->
                    val dataOutputStream = DataOutputStream(outputStream)
                    dataOutputStream.writeUTF("CONTACT")
                    dataOutputStream.writeUTF(contactDetails.first)
                    dataOutputStream.writeUTF(contactDetails.second)
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
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf") // Adjust MIME type if needed
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/PingMe")
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        uri?.let {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(byteArray)
            }
        }
        return uri.toString()
    }

    private fun saveVideoToDeviceStorage(videoBytes: ByteArray): String? {
        val filename = "VID_${System.currentTimeMillis()}.mp4"
        val resolver = requireContext().contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/PingMeVideos")
        }
        return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?.let { uri ->
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(videoBytes)
                }
                uri.toString()
            }
    }

    private fun saveImageToDeviceStorage(bitmap: Bitmap): String? {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        val resolver = requireContext().contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PingMeImages")
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
                                activity?.runOnUiThread {
                                    addMessage(message, false, null)
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
                                val videoSize = dataInputStream.readInt()
                                val videoBytes = ByteArray(videoSize)
                                var offset = 0
                                while (offset < videoSize) {
                                    val chunkSize = dataInputStream.readInt()
                                    dataInputStream.readFully(videoBytes, offset, chunkSize)
                                    offset += chunkSize
                                }
                                saveVideoToDeviceStorage(videoBytes)?.let { videoPath ->
                                    activity?.runOnUiThread {
                                        addMessage(null, false, videoUri = videoPath)
                                    }
                                }
                            }

                            "DOCUMENT" -> {
                                val documentName = dataInputStream.readUTF()
                                val documentSize = dataInputStream.readInt()
                                val byteArray = ByteArray(documentSize)
                                var offset = 0
                                while (offset < documentSize) {
                                    val chunkSize = dataInputStream.readInt()
                                    dataInputStream.readFully(byteArray, offset, chunkSize)
                                    offset += chunkSize
                                }

                                val documentUri =
                                    saveDocumentToDeviceStorage(byteArray, documentName)
                                activity?.runOnUiThread {
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
            }
            catch (e: IOException) {
                e.printStackTrace()
            }
        }
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
        super.onDestroyView()
        try {
            socket?.close()
            serverSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        _binding = null
    }
}
