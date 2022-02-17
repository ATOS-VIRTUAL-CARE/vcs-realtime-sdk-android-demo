package net.atos.vcs.realtime.sdk

import android.content.Context
import androidx.preference.PreferenceManager
import java.lang.ref.WeakReference

object RealtimeSettings {
    private var context: WeakReference<Context>? = null

    fun initialize(context: Context) {
        this.context = WeakReference(context)
    }

    fun applicationServer (): String {
        return sharedStringPreference("app_server", "")
    }

    fun username (): String {
        return sharedStringPreference("username", "")
    }

    fun username(value: String) {
        setSharedStringPreference("username", value)
    }

    fun password (): String {
        return sharedStringPreference("password", "")
    }

    fun password(value: String) {
        setSharedStringPreference("password", value)
    }

    fun autoGainControl(): Boolean {
        return RealtimeSettings.sharedBooleanPreference("auto_gain_control", true)
    }

    fun defaultHdVideo(): Boolean {
        return sharedBooleanPreference("default_hd_video", true)
    }

    fun scaleDownVideoResolution(): Boolean {
        return sharedBooleanPreference("scale_down_video_resolution", false)
    }

    object Options {
        fun hdVideo(): Boolean {
            return sharedBooleanPreference("hd_video", true)
        }

        fun monitorQoS(): Boolean {
            return sharedBooleanPreference("monitor_qos", false)
        }

        fun delayLocalStream(): Boolean {
            return sharedBooleanPreference("delay_local_stream", false)
        }
    }

    private fun sharedBooleanPreference(key: String, default: Boolean = false): Boolean {
        context?.get()?.let { context ->
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            return sharedPreferences.getBoolean(key, default)
        }
        return default
    }

    private fun sharedStringPreference(key: String, default: String): String {
        context?.get()?.let { context ->
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            return sharedPreferences.getString(key, default) ?: default
        }
        return default
    }

    private fun setSharedStringPreference(key: String, value: String) {
        context?.get()?.let { context ->
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            sharedPreferences.edit().putString(key, value).commit()
        }
    }
}
