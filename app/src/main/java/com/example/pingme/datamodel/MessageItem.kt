package com.example.pingme.datamodel

import android.graphics.Bitmap

data class MessageItem(
    val message: String? = null,
    val imageBitmap: Bitmap? = null,
    val imageUri: String? = null,
    val videoUri: String? = null,
    val videoThumbnail: Bitmap? = null,
    val isMe: Boolean = false,
    val documentName: String? = null,
    val documentUri: String? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
