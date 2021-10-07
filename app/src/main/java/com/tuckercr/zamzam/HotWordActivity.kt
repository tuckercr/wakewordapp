package com.tuckercr.zamzam

import android.Manifest.permission
import android.app.NotificationManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.Observable
import androidx.databinding.Observable.OnPropertyChangedCallback
import androidx.databinding.ObservableField
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.tuckercr.zamzam.prefs.PrefsManager

/**
 * This activity implements the Pocket-Sphinx's RecognitionListener and moves opens
 * HotWordDetectedActivity after the wake word has been recognized.
 *
 *
 * While the Wake Word get read from a resource file, to change it, a new wake word would also need
 * to be added the ./assets/sync/models/lm/words.dic
 * Don't forget to generate a new MD5 hash for dictionary after you modified it, and store it in
 * ./assets/sync/models/lm/words.dic.md5
 *
 *
 * Based on code from a tutorial: https://wolfpaulus.com/custom-wakeup-words-for-android/
 *
 * @author colintucker
 */
class HotWordActivity : AppCompatActivity() {

    private lateinit var mViewModel: ListenerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called with: savedInstanceState = [$savedInstanceState]")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotword)
        PrefsManager.initialize(this)
        var openHotWordDetected = false
        if (intent != null) {
            openHotWordDetected = intent.getBooleanExtra(EXTRA_OPEN_HOT_WORD_DETECTED, false)
        }
        mViewModel = ViewModelProvider(this).get(ListenerViewModel::class.java)
        mViewModel.setup()
        ActivityCompat.requestPermissions(
            this@HotWordActivity,
            arrayOf(
                permission.RECORD_AUDIO,
                permission.WRITE_EXTERNAL_STORAGE,
                permission.READ_EXTERNAL_STORAGE,
                permission.VIBRATE
            ),
            PERMISSIONS_REQUEST_CODE
        )
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, ListenerFragment.newInstance(), ListenerFragment.FTAG)
            .commit()
        observeUi()
        if (openHotWordDetected) {
            openHotWordDetectedFragment(false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val openHotWordDetected = intent.getBooleanExtra(EXTRA_OPEN_HOT_WORD_DETECTED, false)
        if (openHotWordDetected) {
            Log.v(TAG, "onNewIntent: opening hot word detected fragment")
            openHotWordDetectedFragment(false)
        }
    }

    private fun observeUi() {
        mViewModel.wakeWordTriggered.addOnPropertyChangedCallback(object :
            OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable, propertyId: Int) {
                val wakeWordTriggered = (sender as ObservableField<*>)
                if (TextUtils.isEmpty(wakeWordTriggered.get() as String)) {
                    Log.d(TAG, "onPropertyChanged: wakeWordTriggered cleared")
                    // Do nothing
                    return
                }
                Log.d(TAG, "onPropertyChanged: wakeWordTriggered: ${wakeWordTriggered.get()}")
                triggerWakeWordActions(wakeWordTriggered.get() as String)
            }
        })

        // TODO observe the wake word
    }

    private fun triggerWakeWordActions(wakeWord: String?) {
        // TODO we should keep listening?
        Log.d(
            TAG,
            "triggerWakeWordActions: matched wake word $wakeWord... shutting down recognizer"
        )
        mViewModel.shutdownRecognizer()
        removeServiceNotification()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            && !isFinishing && !isDestroyed
        ) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            Log.d(TAG, "triggerWakeWordActions: matched wake word... vibrating")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        NotificationUtils.VIBRATION_PATTERN,
                        -1
                    )
                )
            } else {
                vibrator.vibrate(NotificationUtils.VIBRATION_PATTERN, -1)
            }
            Log.d(TAG, "triggerWakeWordActions: matched wake word... opening fragment")
            openHotWordDetectedFragment(false)
        } else {
            Log.d(TAG, "triggerWakeWordActions: lifecycle state is " + lifecycle.currentState)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            NotificationUtils.initChannels(applicationContext)
            val hotWordNotification = NotificationUtils.createHotWordNotification(this)
            notificationManager.notify(
                NotificationUtils.NOTIFICATION_ID_HOT_WORD,
                hotWordNotification
            )
            Log.d(
                TAG,
                "triggerWakeWordActions: matched wake word... launching fragment - allow state loss"
            )
            openHotWordDetectedFragment(true)
        }
    }

    private fun openHotWordDetectedFragment(allowStateLoss: Boolean) {
        Log.d(TAG, "openHotWordDetectedFragment() called")
        val fragmentTransaction = supportFragmentManager
            .beginTransaction()
            .add(
                R.id.container,
                HotWordDetectedFragment.newInstance(),
                HotWordDetectedFragment.FTAG
            )
            .addToBackStack(null)
        if (allowStateLoss) {
            fragmentTransaction.commitAllowingStateLoss()
        } else {
            fragmentTransaction.commit()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio recording permissions denied.", Toast.LENGTH_LONG)
                    .show()
                finish()
            } else {
                mViewModel.setup()
            }
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause() called")
        super.onPause()
        startServiceNotification()
    }

    override fun onResume() {
        Log.d(TAG, "onResume() called")
        super.onResume()
        removeServiceNotification()
    }

    //    @Override
    //    public boolean onCreateOptionsMenu(Menu menu) {
    //        getMenuInflater().inflate(R.menu.main, menu);
    //
    //        return super.onCreateOptionsMenu(menu);
    //    }
    //    @UiThread
    //    @Override
    //    public boolean onOptionsItemSelected(MenuItem item) {
    //        switch (item.getItemId()) {
    //            case R.id.action_sensitivity:
    //                Toast.makeText(this, "Not implemented yet, use main screen slider", Toast.LENGTH_LONG).show();
    //                return true;
    //
    //            case R.id.action_on_off:
    //                Toast.makeText(this, "Not implemented yet, use back button or force quit", Toast.LENGTH_LONG).show();
    //                return true;
    //
    //            default:
    //                // If we got here, the user's action was not recognized.
    //                // Invoke the superclass to handle it.
    //                return super.onOptionsItemSelected(item);
    //
    //        }
    //    }
    override fun onBackPressed() {

        // If our fragment stack is not empty, pop off the top entry
        val fragmentManager = supportFragmentManager
        if (fragmentManager.backStackEntryCount > 0) {

            // Restart the recognizer
            mViewModel.setup()

            // Normally code like this would use "fragmentManager.getBackStackEntryCount() - 1" but
            // we're always going back to the top fragment.
            fragmentManager.popBackStack(
                fragmentManager.getBackStackEntryAt(0).id,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this, 0)
            .setMessage(R.string.do_you_want_to_stop_the_recognizer)
            .setPositiveButton(R.string.no) { _: DialogInterface?, _: Int ->
                moveTaskToBack(false)
                startServiceNotification()
            }
            .setNegativeButton(R.string.yes) { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() called")
        removeServiceNotification()
        super.onDestroy()
    }

    /**
     * Starts the service to display the notification that we're listening
     */
    private fun startServiceNotification() {
        if (isFinishing) {
            Log.d(TAG, "createServiceNotification: activity is finishing - ignoring")
            return
        }
        if (isChangingConfigurations) {
            Log.d(TAG, "createServiceNotification: activity is changing configurations")
            // Continue for now but if we want to support rotation properly we should handle that here
        }
        val wakeWord = mViewModel.wakeWord.get()
        if (TextUtils.isEmpty(wakeWord)) {
            Log.e(TAG, "createServiceNotification: wakeWord is blank")
            return
        }
        if (mViewModel.recognizer != null) {
            Log.d(TAG, "createServiceNotification: recognizer is running... creating")
            val startForegroundIntent =
                ListenerService.createStartForegroundIntent(this, wakeWord!!)
            startService(startForegroundIntent)
        } else {
            Log.d(TAG, "createServiceNotification: recognizer is not running... ignoring")
        }
    }

    /**
     * Stops the service to remove the notification
     */
    private fun removeServiceNotification() {
        val stopForegroundIntent = ListenerService.createStopForegroundIntent(this)
        startService(stopForegroundIntent)
    }

    companion object {
        private const val TAG = "HotWordActivity"
        private const val PERMISSIONS_REQUEST_CODE = 5
        const val EXTRA_OPEN_HOT_WORD_DETECTED = "open_hw_detected"
    }
}