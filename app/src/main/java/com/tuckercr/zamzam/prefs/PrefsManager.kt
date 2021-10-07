package com.tuckercr.zamzam.prefs

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener

class PrefsManager private constructor(context: Context) {
    private val mPref: SharedPreferences
    fun getInt(key: String, defValue: Int): Int {
        return mPref.getInt(key, defValue)
    }

    fun putInt(key: String, value: Int) {
        mPref.edit().putInt(key, value).apply()
    }

    fun getString(key: String, defValue: String): String? {
        return mPref.getString(key, defValue)
    }

    fun putString(key: String, value: String) {
        mPref.edit().putString(key, value).apply()
    }

    fun getBoolean(key: String): Boolean {
        return mPref.getBoolean(key, false)
    }

    fun putBoolean(key: String, value: Boolean) {
        mPref.edit().putBoolean(key, value).apply()
    }

    fun remove(key: String) {
        mPref.edit().remove(key).apply()
    }

    fun clear(): Boolean {
        return mPref.edit().clear().commit()
    }

    fun registerListener(listener: OnSharedPreferenceChangeListener?) {
        mPref.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: OnSharedPreferenceChangeListener?) {
        mPref.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val KEY_WAKE_WORD = "wake_word"
        private const val PREF_NAME = "com.tuckercr.zamzam.prefs"
        private var sInstance: PrefsManager? = null
        @Synchronized
        fun initialize(context: Context) {
            if (sInstance == null) {
                sInstance = PrefsManager(context)
            }
        }

        @JvmStatic
        @get:Synchronized
        val instance: PrefsManager?
            get() {
                checkNotNull(sInstance) { "Not initialized" }
                return sInstance
            }
    }

    init {
        mPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
}