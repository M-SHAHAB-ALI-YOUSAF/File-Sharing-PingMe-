package com.example.pingme.ui.imagepreview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import com.example.pingme.R

class ImagePreviewFragment : DialogFragment() {

    companion object {
        private const val ARG_IMAGE_URI = "image_uri"
        private const val ARG_IMAGE_BYTE_ARRAY = "image_byte_array"

        fun newInstance(imageUri: String?, imageByteArray: ByteArray?): ImagePreviewFragment {
            val args = Bundle().apply {
                putString(ARG_IMAGE_URI, imageUri)
                putByteArray(ARG_IMAGE_BYTE_ARRAY, imageByteArray)
            }
            return ImagePreviewFragment().apply { arguments = args }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_image_preview, container, false)
        val imageView: ImageView = rootView.findViewById(R.id.imagePreview)

        arguments?.let { args ->
            val imageUri = args.getString(ARG_IMAGE_URI)
            val imageByteArray = args.getByteArray(ARG_IMAGE_BYTE_ARRAY)

            when {
                imageUri != null -> {
                    val bitmap = uriToBitmap(imageUri)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    } else {
                        imageView.setImageResource(R.drawable.error)
                    }
                }
                imageByteArray != null -> {
                    val bitmap = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.size)
                    imageView.setImageBitmap(bitmap)
                }
                else -> {
                    imageView.setImageResource(R.drawable.error)
                }
            }
        }

        return rootView
    }

    private fun uriToBitmap(imageUri: String): Bitmap? {
        return try {
            val inputStream = context?.contentResolver?.openInputStream(Uri.parse(imageUri))
            inputStream?.let {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
