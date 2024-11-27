package com.example.pingme.ui.bottomsheet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.pingme.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class OptionsBottomSheet(private val callback: (OptionType) -> Unit) : BottomSheetDialogFragment() {

    enum class OptionType {
        IMAGE, VIDEO, DOCUMENT, CONTACT
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_options, container, false)

        view.findViewById<View>(R.id.option_image).setOnClickListener {
            callback(OptionType.IMAGE)
            dismiss()
        }

        view.findViewById<View>(R.id.option_video).setOnClickListener {
            callback(OptionType.VIDEO)
            dismiss()
        }

        view.findViewById<View>(R.id.option_document).setOnClickListener {
            callback(OptionType.DOCUMENT)
            dismiss()
        }

        view.findViewById<View>(R.id.option_contact).setOnClickListener {
            callback(OptionType.CONTACT)
            dismiss()
        }

        return view
    }
}
