package net.atos.vcs.realtime.demo

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import net.atos.vcs.realtime.demo.service.ActiveCallService
import net.atos.vcs.realtime.sdk.*
import java.lang.Exception

class RoomManager(private val context: Context,
                  private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = "${this.javaClass.kotlin.qualifiedName}"

    private val mutableRoomEvents: MutableSharedFlow<RoomEvent> = MutableSharedFlow()
    val roomEvents: SharedFlow<RoomEvent> = mutableRoomEvents

    private var room: Room? = null
    private var joinInProgress = false
    private val roomListener  by lazy { RoomListener() }
    internal val eventScope by lazy { CoroutineScope(defaultDispatcher) }

    suspend fun joinRoom(token: String, options: RoomOptions) {
        if (room != null || joinInProgress) {
            Log.d(TAG, "Room already joined or being joined, ignore.")
            return
        }

        joinInProgress = true

        withContext(defaultDispatcher) {
            try {
                RealtimeSdk.joinRoom(token, options, roomListener)
            } catch (e: Exception) {
                joinInProgress = false
                if (e.cause is CancellationException) {
                    Log.i(TAG, "Subscription cancelled")
                } else {
                    Log.e(TAG, "Error joining waiting room: $e")
                    sendRoomEvent(RoomEvent.roomJoinError("Error joining waiting room: $e"))
                }
            }
        }
    }

    suspend fun leaveRoom() {
        withContext(defaultDispatcher) {
            try {
                room?.also { r ->
                    r.leave()
                } ?: run {
                    // In case join was unsuccessful, just signal the UI the room was left.
                    joinInProgress = false
                    sendRoomEvent(RoomEvent.roomLeft(null, null))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to leave room: ${e.message ?: e}")
                sendRoomEvent(RoomEvent.error("Failed to leave room: ${e.message ?: e}"))
            }
        }
    }

    fun toggleMute() {
        room?.let {
            try {
                val muted = it.toggleMute()
                Log.i(
                    TAG, "User is now ${
                        if (muted) {
                            "muted"
                        } else {
                            "unmuted"
                        }
                    }"
                )
                sendRoomEvent(RoomEvent.muted(muted))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set mute state: ${e.message ?: e}")
                sendRoomEvent(RoomEvent.error("Failed to set mute state: ${e.message ?: e}"))
            }
        }
    }

    fun toggleVideo() {
        room?.let {
            try {
                val videoEnabled = it.toggleVideo()
                Log.i(TAG, "Video is now ${if (videoEnabled) { "enabled" } else { "disabled" }}")
                sendRoomEvent(RoomEvent.videoEnabled(videoEnabled))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle video: ${e.message ?: e}")
                sendRoomEvent(RoomEvent.error("Failed to toggle video: ${e.message ?: e}"))
            }
        }
    }

    fun toggleSpeakerphone() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = !audioManager.isSpeakerphoneOn
        Log.d(
            TAG, "Speaker set: ${
                if (audioManager.isSpeakerphoneOn) {
                    "ON"
                } else {
                    "OFF"
                }
            }"
        )
        sendRoomEvent(RoomEvent.speakerOn(audioManager.isSpeakerphoneOn))
    }

    fun switchCamera() {
        try {
            room?.switchCamera()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch camera: ${e.message ?: e}")
            sendRoomEvent(RoomEvent.error("Failed to switch camera: ${e.message ?: e}"))
        }
    }

    fun pauseVideoRendering() {
        room?.run {
            localParticipant().detach()
            remoteParticipants().forEach { p ->
                p.detach()
            }
        }
    }

    fun resumeVideoRendering() {
        room?.run {
            localParticipant().attach()
            remoteParticipants().forEach { p ->
                p.attach()
            }
        }
    }

    fun localParticipant(): LocalParticipant? {
        return room?.localParticipant()
    }

    private fun sendRoomEvent(roomEvent: RoomEvent) {
        Log.d(TAG,"sendRoomEvent: $roomEvent")
        eventScope.launch { mutableRoomEvents.emit(roomEvent) }
    }

    inner class RoomListener: RealtimeSdkListener {
        override fun onRoomInitialized(room: Room) {
            this@RoomManager.room = room
            joinInProgress = false
            room.scaleDownVideo(RealtimeSettings.scaleDownVideoResolution(), null, null)
            ActiveCallService.startService(context, room.name() ?: "")
            sendRoomEvent(RoomEvent.roomJoined(
                roomName = room.name() ?: "",
                localParticipant = room.localParticipant(),
                remoteParticipants = room.remoteParticipants().toList(),
                video = room.hasVideo())
            )
         }

        override fun onRoomInitError(error: String) {
            Log.e(TAG, "Error initializing room: $error")
            joinInProgress = false
            ActiveCallService.stopService(context)
            sendRoomEvent(RoomEvent.roomJoinError(error))
        }

        override fun onRoomLeft(room: Room, participant: LocalParticipant) {
            this@RoomManager.room = null
            joinInProgress = false

            // If an error occurs during the join we won't yet have the room
            if (room.roomId() == this@RoomManager.room?.roomId() ?: room.roomId()) {
                Log.d(TAG, "${participant.name()} left the room ${room.name()}")
                sendRoomEvent(RoomEvent.roomLeft(room.name(), participant))
                ActiveCallService.stopService(context)
            } else {
                Log.w(TAG, "Ignoring the Room Left event for roomName = ${room.name()} - user is not part of it")
            }
        }

        override fun onParticipantJoined(room: Room, participant: RemoteParticipant) {
            if (room.roomId() == this@RoomManager.room?.roomId()) {
                Log.d(TAG, "Participant joined the room, participant: ${participant.name()}, roomName: ${room.name()}")
                sendRoomEvent(RoomEvent.participantJoined(participant))
            } else {
                Log.w(TAG, "Participant joined, no matching room")
            }
        }

        override fun onParticipantLeft(room: Room, participant: RemoteParticipant) {
            if (room.roomId() == this@RoomManager.room?.roomId()) {
                Log.d(TAG, "Participant left the room, participant: ${participant.address()}, roomName: ${room.name()}")
                sendRoomEvent(RoomEvent.participantLeft(participant))
            } else {
                Log.w(TAG, "Participant left, no matching room")
            }
        }

        override fun onLocalStreamUpdated(room: Room, participant: LocalParticipant) {
            val videoEnabled = room.hasVideo()
            Log.d(TAG, "Local stream updated, video ${if (videoEnabled) {"enabled"} else {"disabled"}}")
            sendRoomEvent(RoomEvent.videoEnabled(videoEnabled))
        }

        override fun onRemoteStreamUpdated(room: Room, participant: RemoteParticipant) {
            val value = if (participant.hasVideo()) {
                "enabled"
            } else {
                "disabled"
            }
            Log.d(TAG, "RemoteStream video ($value)")
            sendRoomEvent(RoomEvent.remoteStreamUpdated(participant))
        }

        override fun onRegistrationRejected(room: Room, reason: String) {
            Log.w(TAG, "Registration to room (${room.name()}) rejected: $reason")
            joinInProgress = false
            sendRoomEvent(RoomEvent.registrationRejected(room.name() ?: "", reason))
        }

    }

}
