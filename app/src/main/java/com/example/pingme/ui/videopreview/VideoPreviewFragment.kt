package com.example.pingme.ui.videopreview

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.example.pingme.R

class VideoPreviewFragment : DialogFragment() {

    private lateinit var videoView: VideoView
    private var videoUri: Uri? = null

    companion object {
        fun newInstance(videoUri: String?): VideoPreviewFragment {
            val fragment = VideoPreviewFragment()
            val args = Bundle().apply {
                putString("videoUri", videoUri)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        videoView = view.findViewById(R.id.videoView)

        // Get the video URI from the arguments
        val videoUriString = arguments?.getString("videoUri")
        videoUriString?.let {
            videoUri = Uri.parse(it)

            // Set up the VideoView
            videoView.setVideoURI(videoUri)

            // Add MediaController to allow play/pause/seek
            val mediaController = MediaController(context)
            mediaController.setAnchorView(videoView)
            videoView.setMediaController(mediaController)

            // Start video playback
            videoView.start()
        }
    }

    override fun onPause() {
        super.onPause()
        // Pause the video when the fragment is paused
        videoView.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Release resources
        videoView.stopPlayback()
    }
}
