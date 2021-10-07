package com.tuckercr.zamzam

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.IntDef
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.tuckercr.zamzam.databinding.ListenerFragmentBinding

class ListenerFragment : Fragment() {
    private lateinit var mViewModel: ListenerViewModel
    private lateinit var mBinding: ListenerFragmentBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mViewModel = ViewModelProvider(requireActivity()).get(ListenerViewModel::class.java)
        mBinding = DataBindingUtil.inflate(
            inflater, R.layout.listener_fragment,
            container, false
        )
        mBinding.lifecycleOwner = activity
        mBinding.viewModel = mViewModel
        return mBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel.dictionaryList.observe(requireActivity(), { list: List<String?>? ->
            if (list == null) {
                Log.e(TAG, "onViewCreated: list is null")
                val arrayAdapter = ArrayAdapter(
                    requireActivity(),
                    android.R.layout.simple_spinner_item, arrayOf(mViewModel.wakeWord.get())
                )
                mBinding.wakeWordSpinner.adapter = arrayAdapter
                mBinding.wakeWordSpinner.setSelection(0)
                return@observe
            }
//            if (activity == null) {
//                Log.e(TAG, "onViewCreated: activity null")
//                return@observe
//            }

            // Create a new adapter
            val arrayAdapter = ArrayAdapter(
                requireActivity(),
                android.R.layout.simple_spinner_item, list
            )
            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            mBinding.wakeWordSpinner.adapter = arrayAdapter
//            mBinding.wakeWordSpinner.item = list

            // FIXME even with animate=false, this takes forever to scroll.  Also there's a bug
            //  in the library that means it crashes if you begin searching while scrolling.

            // FIXME pt2 - if you uncomment this the spinner doesn't show the wake word
            mBinding.wakeWordSpinner.setSelection(list.indexOf(mViewModel.wakeWord.get()), false)
        })
        mBinding.seekbar.progress = mViewModel.sensitivity.get()
        mBinding.seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // Updating here causes us to repeatedly rebuild the recognizer
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Not required
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val progress = seekBar.progress
                Log.i(TAG, "onStopTrackingTouch: $progress")
                mViewModel.setSensitivity(progress)
            }
        })
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(MicState.DISABLED_NO_PERMISSION, MicState.LISTENING, MicState.SPEAKING, MicState.OFF)
    annotation class MicState {
        companion object {
            const val DISABLED_NO_PERMISSION = 0
            const val LISTENING = 1
            const val SPEAKING = 2
            const val OFF: Int = 3
        }
    }

    companion object {
        const val FTAG = "ListenerFragment"
        private const val TAG = "ListenerFragment"
        fun newInstance(): ListenerFragment {
            return ListenerFragment()
        }
    }
}