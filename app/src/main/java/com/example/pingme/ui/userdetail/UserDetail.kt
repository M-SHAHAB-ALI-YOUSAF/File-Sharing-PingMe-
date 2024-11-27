package com.example.pingme.ui.userdetail

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.pingme.R
import com.example.pingme.databinding.FragmentSplashScreenBinding
import com.example.pingme.databinding.FragmentUserDetailBinding

class UserDetail : Fragment() {

    private var _binding: FragmentUserDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.save.setOnClickListener {
        //    findNavController().navigate(R.id.action_userDetail_to_searchDevices)
    }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
