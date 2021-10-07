package com.tuckercr.zamzam

import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import com.tuckercr.zamzam.ListenerFragment.MicState
import com.tuckercr.zamzam.prefs.PrefsManager
import com.tuckercr.zamzam.prefs.PrefsManager.Companion.instance

object BindingAdapters {
    @JvmStatic
    @BindingAdapter("micState")
    fun setMicStateImage(view: View?, @MicState micState: Int) {
        if (view !is AppCompatImageView) {
            return
        }
        when (micState) {
            MicState.DISABLED_NO_PERMISSION -> view.setImageResource(R.drawable.ic_mic_off_red_128dp)
            MicState.OFF -> view.setImageResource(R.drawable.ic_mic_light_gray_128dp)
            MicState.LISTENING -> view.setImageResource(R.drawable.ic_mic_gray_128dp)
            MicState.SPEAKING -> view.setImageResource(R.drawable.ic_mic_green_128dp)
        }
    }

    @JvmStatic
    @BindingAdapter(value = ["selectedValue", "selectedValueAttrChanged"], requireAll = false)
    fun bindSpinnerData(
        pAppCompatSpinner: AppCompatSpinner,
        newSelectedValue: String?,
        newTextAttrChanged: InverseBindingListener
    ) {
        pAppCompatSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                newTextAttrChanged.onChange()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        if (newSelectedValue != null) {
            val pos =
                (pAppCompatSpinner.adapter as ArrayAdapter<String>).getPosition(newSelectedValue)
            pAppCompatSpinner.setSelection(pos, true)
        }
    }

    @JvmStatic
    @InverseBindingAdapter(attribute = "selectedValue", event = "selectedValueAttrChanged")
    fun captureSelectedValue(pAppCompatSpinner: AppCompatSpinner): String? {

        // Log.d(TAG, "captureSelectedValue: selectedItem = [" + pAppCompatSpinner.getSelectedItem() + "]");
        if (pAppCompatSpinner.selectedItem == null) {
            return null
        }

        // There's a shared preference listener in the ViewModel that'll notice this changed
        instance!!.putString(PrefsManager.KEY_WAKE_WORD, pAppCompatSpinner.selectedItem.toString())
        return pAppCompatSpinner.selectedItem as String
    }
}