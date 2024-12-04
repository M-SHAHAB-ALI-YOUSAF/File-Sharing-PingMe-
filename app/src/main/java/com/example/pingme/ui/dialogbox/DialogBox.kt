package com.example.pingme.ui.dialogbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.example.pingme.R
import com.example.pingme.databinding.FragmentDialogBoxBinding

class DialogBox : DialogFragment() {

    private var _binding: FragmentDialogBoxBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the custom layout for the dialog
        _binding = FragmentDialogBoxBinding.inflate(inflater, container, false)

        // Set a listener for the OK button
        binding.buttonOk.setOnClickListener {
            findNavController().navigate(R.id.action_message2_to_discoverDevices)
            dismiss()
        }

        return binding.root
    }

    // Override to make the dialog non-cancelable
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false // Prevent the dialog from being canceled by tapping outside or back press
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks by clearing the binding
    }
}
