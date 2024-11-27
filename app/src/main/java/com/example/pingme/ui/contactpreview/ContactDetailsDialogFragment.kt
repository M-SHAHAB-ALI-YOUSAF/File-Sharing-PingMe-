package com.example.pingme.ui.contactpreview

import android.content.Intent
import android.provider.ContactsContract
import android.widget.Button
import android.widget.TextView
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.pingme.R

class ContactDetailsDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_CONTACT_NAME = "contact_name"
        private const val ARG_CONTACT_PHONE = "contact_phone"

        fun newInstance(contactName: String, contactPhone: String): ContactDetailsDialogFragment {
            val fragment = ContactDetailsDialogFragment()
            val args = Bundle().apply {
                putString(ARG_CONTACT_NAME, contactName)
                putString(ARG_CONTACT_PHONE, contactPhone)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_contact_details_dialog, container, false)

        val contactNameTextView: TextView = view.findViewById(R.id.contactNameTextView)
        val contactPhoneTextView: TextView = view.findViewById(R.id.contactPhoneTextView)
        val saveButton: Button = view.findViewById(R.id.saveNumber)

        val contactName = arguments?.getString(ARG_CONTACT_NAME)
        val contactPhone = arguments?.getString(ARG_CONTACT_PHONE)

        contactNameTextView.text = contactName
        contactPhoneTextView.text = contactPhone

        saveButton.setOnClickListener {
            // Open the default contact saving screen with pre-filled data
            openContactsSaveScreen(contactName ?: "", contactPhone ?: "")
        }

        return view
    }

    private fun openContactsSaveScreen(name: String, phone: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, name)
            putExtra(ContactsContract.Intents.Insert.PHONE, phone)
        }
        startActivity(intent)
    }
}
