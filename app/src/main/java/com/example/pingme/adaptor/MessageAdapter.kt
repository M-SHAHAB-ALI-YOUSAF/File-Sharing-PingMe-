package com.example.pingme.adaptor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings.Global.putString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pingme.R
import com.example.pingme.datamodel.MessageItem
import com.example.pingme.ui.contactpreview.ContactDetailsDialogFragment
import com.example.pingme.ui.imagepreview.ImagePreviewFragment
import com.example.pingme.ui.videopreview.VideoPreviewFragment
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val messages: List<MessageItem>,
    private val fragmentManager: FragmentManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isMe) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            val view = layoutInflater.inflate(R.layout.item_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = layoutInflater.inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val messageItem = messages[position]

        // Format the timestamp
        val timestampFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val formattedTime = timestampFormat.format(Date(messageItem.timestamp))

        when {
            messageItem.contactName != null && messageItem.contactPhone != null -> {
                if (holder is SentMessageViewHolder) {
                    holder.contactLayout.visibility = View.VISIBLE
                    holder.contactName.text = messageItem.contactName
                    holder.messageImageView.visibility = View.GONE
                    holder.messageTextView.visibility = View.GONE
                    holder.documentLayout.visibility = View.GONE
                    holder.frameLayout.visibility = View.GONE
                    holder.messageTimestamp.text = formattedTime

                    holder.detailsButton.setOnClickListener {
                        val contactName = messageItem.contactName ?: "Unknown"
                        val contactPhone = messageItem.contactPhone ?: "Unknown"

                        val dialog = ContactDetailsDialogFragment.newInstance(contactName, contactPhone)
                        dialog.show(fragmentManager, "ContactDetailsDialog")
                    }
                } else if (holder is ReceivedMessageViewHolder) {
                    holder.contactLayout.visibility = View.VISIBLE
                    holder.contactName.text = messageItem.contactName
                    holder.messageImageView.visibility = View.GONE
                    holder.frameLayout.visibility = View.GONE
                    holder.messageTextView.visibility = View.GONE
                    holder.documentLayout.visibility = View.GONE
                    holder.messageTimestamp.text = formattedTime

                    holder.detailsButton.setOnClickListener {
                        val contactName = messageItem.contactName ?: "Unknown"
                        val contactPhone = messageItem.contactPhone ?: "Unknown"

                        val dialog = ContactDetailsDialogFragment.newInstance(contactName, contactPhone)
                        dialog.show(fragmentManager, "ContactDetailsDialog")
                    }
                }
            }
            messageItem.documentUri != null -> { // Display document message
                if (holder is SentMessageViewHolder) {
                    holder.documentLayout.visibility = View.VISIBLE
                    holder.documentName.text = messageItem.documentName
                    holder.messageImageView.visibility = View.GONE
                    holder.messageTextView.visibility = View.GONE
                    holder.videoIcon.visibility = View.GONE
                    holder.frameLayout.visibility = View.GONE
                    holder.messageTimestamp.text = formattedTime

                    // Handle document click (if necessary)
                    holder.documentLayout.setOnClickListener {
                        openDocument(holder.itemView.context, messageItem.documentUri)
                    }
                } else if (holder is ReceivedMessageViewHolder) {
                    holder.documentLayout.visibility = View.VISIBLE
                    holder.documentName.text = messageItem.documentName
                    holder.messageImageView.visibility = View.GONE
                    holder.messageTextView.visibility = View.GONE
                    holder.videoIcon.visibility = View.GONE
                    holder.frameLayout.visibility = View.GONE
                    holder.messageTimestamp.text = formattedTime

                    // Handle document click (if necessary)
                    holder.documentLayout.setOnClickListener {
                        openDocument(holder.itemView.context, messageItem.documentUri)
                    }
                }
            }

            messageItem.imageUri != null -> { // Display image from URI
                if (holder is SentMessageViewHolder) {
                    Glide.with(holder.itemView.context)
                        .load(messageItem.imageUri)
                        .placeholder(R.drawable.loader)
                        .into(holder.messageImageView)
                    holder.messageTextView.visibility = View.GONE
                    holder.messageImageView.visibility = View.VISIBLE
                    holder.messageTimestamp.text = formattedTime

                    holder.messageImageView.setOnClickListener {
                        showImagePreview(messageItem.imageUri)
                    }

                    // Hide video icon for image message
                    holder.videoIcon.visibility = View.GONE
                } else if (holder is ReceivedMessageViewHolder) {
                    Glide.with(holder.itemView.context)
                        .load(messageItem.imageUri)
                        .placeholder(R.drawable.loader)
                        .into(holder.messageImageView)
                    holder.messageTextView.visibility = View.GONE
                    holder.messageImageView.visibility = View.VISIBLE
                    holder.messageTimestamp.text = formattedTime

                    holder.messageImageView.setOnClickListener {
                        showImagePreview(messageItem.imageUri)
                    }

                    // Hide video icon for image message
                    holder.videoIcon.visibility = View.GONE
                }
            }

            messageItem.videoUri != null -> { // Display video
                if (holder is SentMessageViewHolder) {
                    Glide.with(holder.itemView.context)
                        .load(messageItem.videoUri) // Load video thumbnail
                        .placeholder(R.drawable.loader)
                        .into(holder.messageImageView)

                    holder.messageTextView.visibility = View.GONE
                    holder.messageImageView.visibility = View.VISIBLE
                    holder.messageTimestamp.text = formattedTime

                    holder.messageImageView.setOnClickListener {
                        showVideoPreview(messageItem.videoUri)
                    }

                    // Show video icon for video message
                    holder.videoIcon.visibility = View.VISIBLE
                } else if (holder is ReceivedMessageViewHolder) {
                    Glide.with(holder.itemView.context)
                        .load(messageItem.videoUri) // Load video thumbnail
                        .placeholder(R.drawable.loader)
                        .into(holder.messageImageView)

                    holder.messageTextView.visibility = View.GONE
                    holder.messageImageView.visibility = View.VISIBLE
                    holder.messageTimestamp.text = formattedTime

                    holder.messageImageView.setOnClickListener {
                        showVideoPreview(messageItem.videoUri)
                    }

                    // Show video icon for video message
                    holder.videoIcon.visibility = View.VISIBLE
                }
            }

            messageItem.imageBitmap != null -> { // Fallback to bitmap if URI is not available
                val byteArray = bitmapToByteArray(messageItem.imageBitmap)

                if (holder is SentMessageViewHolder) {
                    Glide.with(holder.itemView.context)
                        .load(byteArray)
                        .placeholder(R.drawable.loader)
                        .into(holder.messageImageView)
                    holder.messageTextView.visibility = View.GONE
                    holder.messageImageView.visibility = View.VISIBLE
                    holder.messageTimestamp.text = formattedTime

                    holder.messageImageView.setOnClickListener {
                        showImagePreview(null, byteArray)
                    }

                    // Hide video icon for image message
                    holder.videoIcon.visibility = View.GONE
                } else if (holder is ReceivedMessageViewHolder) {
                    Glide.with(holder.itemView.context)
                        .load(byteArray)
                        .placeholder(R.drawable.loader)
                        .into(holder.messageImageView)
                    holder.messageTextView.visibility = View.GONE
                    holder.messageImageView.visibility = View.VISIBLE
                    holder.messageTimestamp.text = formattedTime

                    holder.messageImageView.setOnClickListener {
                        showImagePreview(null, byteArray)
                    }

                    // Hide video icon for image message
                    holder.videoIcon.visibility = View.GONE
                }
            }

            messageItem.message != null -> { // Display text message
                if (holder is SentMessageViewHolder) {
                    holder.messageTextView.text = messageItem.message
                    holder.messageImageView.visibility = View.GONE
                    holder.frameLayout.visibility = View.GONE
                    holder.messageTextView.visibility = View.VISIBLE
                    holder.messageTimestamp.text = formattedTime

                    // Hide video icon for text message
                    holder.videoIcon.visibility = View.GONE
                } else if (holder is ReceivedMessageViewHolder) {
                    holder.messageTextView.text = messageItem.message
                    holder.messageImageView.visibility = View.GONE
                    holder.frameLayout.visibility = View.GONE
                    holder.messageTextView.visibility = View.VISIBLE
                    holder.messageTimestamp.text = formattedTime

                    // Hide video icon for text message
                    holder.videoIcon.visibility = View.GONE
                }
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun showImagePreview(imageUri: String? = null, imageByteArray: ByteArray? = null) {
        val previewFragment = ImagePreviewFragment.newInstance(imageUri, imageByteArray)
        previewFragment.show(fragmentManager, "ImagePreviewFragment")
    }

    private fun showVideoPreview(videoUri: String?) {
        val videoPreviewFragment = VideoPreviewFragment.newInstance(videoUri)
        videoPreviewFragment.show(fragmentManager, "VideoPreviewFragment")
    }

    private fun openDocument(context: Context, documentUri: String?) {
        documentUri?.let {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageTextView: TextView = view.findViewById(R.id.textMessage)
        val messageImageView: ImageView = view.findViewById(R.id.imageMessage)
        val messageTimestamp: TextView = view.findViewById(R.id.messageTimestamp)
        val videoIcon: ImageView = view.findViewById(R.id.videoIcon)
        val frameLayout: FrameLayout = view.findViewById(R.id.frameLayout)
        val progressBar: ProgressBar = view.findViewById(R.id.imageProgressBar)
        val documentLayout: LinearLayout = view.findViewById(R.id.documentLayout)
        val documentName: TextView = view.findViewById(R.id.documentName)
        val contactLayout: LinearLayout = view.findViewById(R.id.contactLayout)
        val contactName: TextView = view.findViewById(R.id.contactName)
        val detailsButton: TextView = view.findViewById(R.id.detailsButton)
    }

    inner class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageTextView: TextView = view.findViewById(R.id.textMessage)
        val messageImageView: ImageView = view.findViewById(R.id.imageMessage)
        val messageTimestamp: TextView = view.findViewById(R.id.messageTimestamp)
        val frameLayout: FrameLayout = view.findViewById(R.id.frameLayout)
        val videoIcon: ImageView = view.findViewById(R.id.videoIcon)
        val progressBar: ProgressBar = view.findViewById(R.id.imageProgressBar)
        val documentLayout: LinearLayout = view.findViewById(R.id.documentLayout)
        val documentName: TextView = view.findViewById(R.id.documentName)
        val contactLayout: LinearLayout = view.findViewById(R.id.contactLayout)
        val contactName: TextView = view.findViewById(R.id.contactName)
        val detailsButton: TextView = view.findViewById(R.id.detailsButton)
    }
}
