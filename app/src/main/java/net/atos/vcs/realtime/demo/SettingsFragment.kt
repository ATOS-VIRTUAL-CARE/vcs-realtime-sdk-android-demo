package net.atos.vcs.realtime.demo

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.view.children
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textview.MaterialTextView

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.apply {
            title = "Settings"
            setTitleTextColor(resources.getColor(R.color.deep_blue))
            isTitleCentered = true
            setNavigationIcon(R.drawable.action_back)
            setNavigationIconTint(resources.getColor(R.color.deep_blue))
            setNavigationOnClickListener {
                val action = SettingsFragmentDirections.actionSettingsFragmentToSignInFragment()
                findNavController().navigate(action)
            }
            for(child in children) {
                if (child is ImageButton) {
                    child.contentDescription = resources.getText(R.string.back_button_desc)
                    break
                }
            }
        }

        // Application Server dialog
        preferenceScreen.preferenceManager.findPreference<EditTextPreference>("app_server")
            ?.setOnBindEditTextListener {
                it.contentDescription = resources.getText(R.string.app_server_input_desc)
            }

        // Username dialog
        preferenceScreen.preferenceManager.findPreference<EditTextPreference>("username")
            ?.setOnBindEditTextListener {
                it.contentDescription = resources.getText(R.string.username_input_desc)
            }

        // Password dialog
        preferenceScreen.preferenceManager.findPreference<EditTextPreference>("password")
            ?.setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT + InputType.TYPE_TEXT_VARIATION_PASSWORD
                it.contentDescription = resources.getText(R.string.password_input_desc)
            }

        // Settings
        view.findViewById<FrameLayout>(android.R.id.list_container)?.let { frameLayout ->
            val recyclerView = frameLayout.findViewById<RecyclerView>(R.id.recycler_view)
            recyclerView?.let {
                it.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(view: View) {
                        setContentDescriptors(view)
                    }
                    override fun onChildViewDetachedFromWindow(view: View) { }
                })
            }
        }
    }

    private fun setContentDescriptors(view: View) {
        if (view is ViewGroup) {
            view.children.forEach { childView ->
                when (childView) {
                    is ViewGroup -> setContentDescriptors(childView)
                    is MaterialTextView -> {
                        val descId = when (childView.text) {
                            resources.getText(R.string.app_server_title) -> { R.string.app_server_desc }
                            resources.getText(R.string.username_title) -> { R.string.username_desc }
                            resources.getText(R.string.password_title) -> { R.string.password_desc }
                            resources.getText(R.string.auto_gain_control_title) -> { R.string.auto_gain_control_desc }
                            resources.getText(R.string.default_hd_video_title) -> { R.string.default_hd_desc }
                            resources.getText(R.string.scale_down_video_resolution_title) -> { R.string.scale_down_video_resolution_desc }
                            resources.getText(R.string.hd_video_title) -> { R.string.hd_video_desc }
                            resources.getText(R.string.monitor_qos_title) -> { R.string.monitor_qos_desc }
                            resources.getText(R.string.delay_local_stream_title) -> { R.string.delay_local_stream_desc }
                            else -> { null }
                        }
                        descId?.let {
                            (view.parent as ViewGroup).contentDescription = resources.getText(it)
                        }
                    }
                    else -> {}
                }
            }
        }

    }
}
