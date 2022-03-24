package net.atos.vcs.realtime.demo

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.core.view.setPadding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.navArgs
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import net.atos.vcs.realtime.demo.databinding.RoomActivityBinding
import net.atos.vcs.realtime.sdk.*
import java.lang.ref.WeakReference
import javax.inject.Inject

@AndroidEntryPoint
class RoomActivity : AppCompatActivity() {

    private lateinit var binding: RoomActivityBinding
    private val TAG = "${this.javaClass.kotlin.qualifiedName}"
    private val PERMISSION_REQUEST_CAMERA = 0
    private val PERMISSION_REQUEST_AUDIO = 1

    @Inject
    lateinit var roomManager: RoomManager

    data class RoomParticipant(
        var address: String,
        var renderer: WeakReference<MediaStreamVideoView>
    )
    private val roomParticipants = mutableListOf<RoomParticipant>()

    data class LayoutData(
        val width: Int = 0,
        val height: Int = 0,
        val gravity: List<Int>
    )
    private val layoutParameters = mutableListOf<LayoutData>()

    val args: RoomActivityArgs by navArgs()

    // To use the viewModels() extension function, include
    // "androidx.fragment:fragment-ktx:latest-version" in your app
    // module's build.gradle file.
    private val viewModel: RoomViewModel by viewModels { RoomViewModelFactory(roomManager) }

    private var alertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RoomActivityBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        // Prevent the user from navigating back to sign-in fragment until the user disconnects
        onBackPressedDispatcher.addCallback(this) {
            showMessage(getText(R.string.end_call_to_leave).toString())
        }

        Log.d(TAG, "Received...\nhost: ${args.host}\ntoken: ${args.token}\nroom name: ${args.roomName}" +
                "\nname: ${args.name}\naudio: ${args.audio}\nvideo: ${args.video}")

        setupSdkSettings()

        setupDisplayParameters()

        // Initialize the local rendering view and set it in the local participant if the room is already joined or
        // once the room is joined.
        binding.localView.init(RealtimeSdk.getRootEglContext(), null)
        viewModel.localParticipant.value?.setVideoView(WeakReference(binding.localView))
        viewModel.localParticipant.observe(this) { localParticipant ->
            localParticipant.setVideoView(WeakReference(binding.localView))
        }

        setupObservers()

        setupListeners()

        // Join the room
        viewModel.joinRoom(getRoomToken(), getRoomOptions())
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        viewModel.resumeVideoRendering()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        viewModel.pauseVideoRendering()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        binding.localView.dispose()
        roomParticipants.forEach { rp -> rp.renderer.get()?.dispose() }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CAMERA -> {
                if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    viewModel.toggleVideo()
                }
            }
            PERMISSION_REQUEST_AUDIO -> {
                if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    viewModel.toggleMute()
                }
            }
        }
    }

    private fun getRoomToken(): String {
        return args.token
    }

    private fun getRoomOptions(): RoomOptions {
        var info: Map<String, Any>? = null
        args.country?.let { info = mapOf<String, Any>("country" to it) }
        return RoomOptions(
            host = args.host,
            name = args.name,
            participantInfo = info,
            audio = args.audio,
            video = args.video,
            hdVideo = RealtimeSettings.Options.hdVideo(),
            delayLocalStream = RealtimeSettings.Options.delayLocalStream(),
            monitorStats = RealtimeSettings.Options.monitorQoS()
        )
    }

    private fun setupSdkSettings() {
        RealtimeSdk.Settings.autoGainControl = RealtimeSettings.autoGainControl()
        RealtimeSdk.Settings.defaultHdVideo =  RealtimeSettings.defaultHdVideo()
        RealtimeSdk.Settings.logLevel = LogWriter.LogLevel.DEBUG
    }

    private fun setupObservers() {

        // Observe - MUTE
        viewModel.muted.observe(this) { muted ->
            if (muted) {
                binding.microphoneButton.setImageResource(R.drawable.action_microphone_off)
                binding.microphoneButton.contentDescription = getText(R.string.microphone_off_desc).toString()
            } else {
                binding.microphoneButton.setImageResource(R.drawable.action_microphone)
                binding.microphoneButton.contentDescription = getText(R.string.microphone_desc).toString()
            }
        }

        // Observe - SPEAKER
        viewModel.speakerOn.observe(this) { speakerOn ->
            if (speakerOn) {
                binding.speakerButton.setImageResource(R.drawable.action_speakerphone)
                binding.speakerButton.setPadding(6)
                binding.speakerButton.scaleType = ImageView.ScaleType.FIT_CENTER
                binding.speakerButton.contentDescription = getText(R.string.speaker_desc).toString()
            } else {
                binding.speakerButton.setImageResource(R.drawable.action_speakerphone_off)
                binding.speakerButton.setPadding(0)
                binding.speakerButton.scaleType = ImageView.ScaleType.CENTER
                binding.speakerButton.contentDescription = getText(R.string.speaker_off_desc).toString()
            }
        }

        // Observe - Video
        viewModel.videoEnabled.observe(this) { enabled ->
            showLocalVideo(enabled)
            if (enabled) {
                binding.videoButton.setImageResource(R.drawable.action_video)
                binding.videoButton.contentDescription = getText(R.string.video_desc).toString()
            } else {
                binding.videoButton.setImageResource(R.drawable.action_video_off)
                binding.videoButton.contentDescription = getText(R.string.video_off_desc).toString()
            }
        }

        // Observe - Remote Participants
        viewModel.remoteParticipants.observe(this) { participants ->
            Log.d(TAG, "Remote participant set, count ${participants.size}")
            roomParticipants.forEach { rp -> rp.renderer.get()?.dispose() }
            roomParticipants.clear()
            binding.remoteViewsLayout.removeAllViews()
            participants.forEach { p ->
                addRemoteParticipantVideo(p)
            }
        }

        viewModel.participantJoined.observe(this) { participant ->
            // defensive code to check if the remote participant is already displayed
            roomParticipants.forEach { rp ->
                if (participant.address() == rp.address) {
                    Log.i(TAG, "Remote participant (${participant.name() ?: ""}) already shown")
                    return@observe
                }
            }
            addRemoteParticipantVideo(participant)
        }

        viewModel.participantLeft.observe(this) { participant ->
            removeRemoteParticipantVideo(participant)
        }

        viewModel.leftRoom.observe(this) { left ->
            if (left) {
                navigateToSignIn()
            }
        }

        viewModel.alert.observe(this) { text ->
            showAlert(getText(R.string.error).toString(), text)
        }

        viewModel.message.observe(this) { text ->
            showMessage(text)
        }

        viewModel.roomName.observe(this) { name ->
            binding.roomLabel.text = getString(R.string.room_name, name)
        }
    }

    private fun setupListeners() {
        // Click - LEAVE
        binding.leaveRoomButton.setOnClickListener {
            Log.d(TAG, "Leave session")
            viewModel.leaveRoom()
        }

        // Click - VIDEO
        binding.videoButton.setOnClickListener {
            Log.d(TAG, "Video button selected")
            if (viewModel.videoEnabled.value == true || hasPermission(Manifest.permission.CAMERA)) {
                viewModel.toggleVideo()
            } else {
                askPermission(Manifest.permission.CAMERA)
            }
        }

        // Click - MUTE
        binding.microphoneButton.setOnClickListener {
            Log.d(TAG, "Microphone button selected")
            if (viewModel.muted.value == false || hasPermission(Manifest.permission.RECORD_AUDIO)) {
                viewModel.toggleMute()
            } else {
                askPermission(Manifest.permission.RECORD_AUDIO)
            }
        }

        // Click - SPEAKER
        binding.speakerButton.setOnClickListener {
            Log.d(TAG, "Speaker button selected")
            viewModel.toggleSpeakerphone()
        }

        // Click - CAMERA
        binding.cameraSwitchButton.setOnClickListener {
            Log.d(TAG, "Camera switch button selected")
            viewModel.switchCamera()
        }
    }

    private fun showLocalVideo(enabled: Boolean) {
        if (enabled) {
            binding.localView.visibility = View.VISIBLE
        } else {
            binding.localView.visibility = View.GONE
        }
        binding.cameraSwitchButton.visibility = binding.localView.visibility
    }

    private fun navigateToSignIn() {
        if (alertDialog?.isShowing() != true) {
            finish()
        }
    }

    private fun showMessage(message: String) {
        val contextView = binding.constraintLayout
        Snackbar.make(contextView, message, Snackbar.LENGTH_SHORT).show()
    }

    fun showAlert(title: String?, message: String) {
        val dialog = AlertDialog.Builder(this@RoomActivity).create()
        alertDialog = dialog
        dialog.setTitle(title ?: "Alert")
        dialog.setMessage(message)
        dialog.setButton(
            AlertDialog.BUTTON_NEUTRAL, "OK"
        ) { dlg, _ ->
            dlg.dismiss()
            if (viewModel.leftRoom.value == true) {
                finish()
            }
        }
        dialog.show()
    }

    private fun addRemoteParticipantVideo(participant: RemoteParticipant) {
        val inflater = layoutInflater

        Log.d(TAG, "Remote participant to be added: ${participant.name()}")
        try {
            inflater.inflate(R.layout.remote_surface_view, null)?.let { view ->
                val count = binding.remoteViewsLayout.childCount
                val data = layoutParameters[count]

                binding.remoteViewsLayout.children.forEachIndexed { index, child ->
                    val params = FrameLayout.LayoutParams(data.width, data.height, data.gravity[index])
                    child.layoutParams = params
                }
                val params = FrameLayout.LayoutParams(data.width, data.height, data.gravity[count])
                view.layoutParams = params

                val remoteVideoView = view.findViewById<MediaStreamVideoView>(R.id.remote_view)
                remoteVideoView.init(RealtimeSdk.getRootEglContext(), null)
                val textView = view.findViewById<TextView>(R.id.participant_name)
                textView.text = participant.name() ?: ""
                binding.remoteViewsLayout.addView(view, binding.remoteViewsLayout.childCount)
                participant.setVideoView(WeakReference(remoteVideoView))
                roomParticipants.add(
                    RoomParticipant(
                        address = participant.address(),
                        renderer = WeakReference(remoteVideoView)
                    )
                )
            }
        } catch (e: Exception) {
            showAlert(getText(R.string.error).toString(), "${e.localizedMessage ?: e}")
        }
    }

    private fun removeRemoteParticipantVideo(participant: RemoteParticipant) {
        Log.d(TAG, "Room participant to be removed: ${participant.name()}")
        val index = roomParticipants.indexOfFirst { it.address == participant.address() }
        if (index > -1) {
            binding.remoteViewsLayout.removeViewAt(index)
            roomParticipants.removeAt(index).renderer.get()?.dispose()

            val count = binding.remoteViewsLayout.childCount
            if (count > 0) {
                val data = layoutParameters[count-1]

                binding.remoteViewsLayout.children.forEachIndexed { idx, child ->
                    val params = FrameLayout.LayoutParams(data.width, data.height, data.gravity[idx])
                    child.layoutParams = params
                }
            }
        }
    }

    private fun setupDisplayParameters() {
        val displayMetrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        layoutParameters.clear()
        layoutParameters.add(LayoutData(width = width, height = height, gravity = listOf(Gravity.FILL)))
        layoutParameters.add(LayoutData(width = width, height = height / 2, gravity = listOf(Gravity.TOP, Gravity.BOTTOM)))
        layoutParameters.add(
            LayoutData(
                width = width / 2, height = height / 2, gravity =
                listOf(Gravity.TOP + Gravity.LEFT, Gravity.BOTTOM + Gravity.LEFT, Gravity.BOTTOM + Gravity.RIGHT)
            )
        )
    }

    class RoomViewModelFactory(
        private val roomManager: RoomManager,
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RoomViewModel(roomManager) as T
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCode(permission: String): Int? {
        when (permission) {
            Manifest.permission.CAMERA -> { return PERMISSION_REQUEST_CAMERA }
            Manifest.permission.RECORD_AUDIO -> { return PERMISSION_REQUEST_AUDIO }
        }
        return null
    }

    private fun askPermission(permission: String) {
        when {
            shouldShowRequestPermissionRationale(permission) -> {
                // Provide an additional rationale to the user if the permission was not granted
                // and the user would benefit from additional context for the use of the permission.
                requestCode(permission)?.let { code ->
                    showPermissionDialog(permission, code)
                }
            }
            else -> {
                // You can directly ask for the permission.
                requestCode(permission)?.let { code ->
                    requestPermissions(arrayOf(permission), code)
                }
            }
        }
    }

    private fun showPermissionDialog(permission: String, requestCode: Int) {
        AlertDialog.Builder(this)
            .setTitle("Permission $permission Required")
            .setMessage("Permit application to use $permission")
            .setPositiveButton("Grant") { dialog, _ ->
                Log.i(TAG, "Permission granted: $permission")
                dialog.dismiss()
                requestPermissions(arrayOf(permission), requestCode)
            }
            .setNegativeButton("Deny") { dialog, _ ->
                Log.i(TAG, "Permission denied: $permission")
                dialog.dismiss()
            }
            .show()
    }
}
