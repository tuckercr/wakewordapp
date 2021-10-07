package com.tuckercr.zamzam

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * A simple Fragment for when the hotword is detected
 */
class HotWordDetectedFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_hotword_detected, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.button).setOnClickListener { v: View? -> buttonPressed() }
    }

    private fun buttonPressed() {
        // Go back
        requireActivity().onBackPressed()
    }

    companion object {
        const val FTAG = "HotWordDetectedFragment"
        fun newInstance(): HotWordDetectedFragment {
            return HotWordDetectedFragment()
        }
    }
}