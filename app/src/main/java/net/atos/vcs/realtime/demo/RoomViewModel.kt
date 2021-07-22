package net.atos.vcs.realtime.demo

import android.util.Log
import androidx.lifecycle.*
import net.atos.vcs.realtime.sdk.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RoomViewModel(
        private val roomManager: RoomManager
    ) : ViewModel()  {

    private val TAG = "${this.javaClass.kotlin.simpleName}"
    private var roomManagerJob: Job? = null

    init {
        Log.d(TAG, "Initialize RoomViewModel")
        subscribeToRoomEvents()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared")
        roomManagerJob?.cancel()
    }

    private val _roomName = MutableLiveData<String>()
    val roomName: LiveData<String> = _roomName

    private val _localParticipant = MutableLiveData<LocalParticipant>()
    val localParticipant: LiveData<LocalParticipant> = _localParticipant

    private val _remoteParticipants = MutableLiveData<MutableList<RemoteParticipant>>()
    val remoteParticipants: LiveData<MutableList<RemoteParticipant>> = _remoteParticipants

    private val _participantJoined = MutableLiveData<RemoteParticipant>()
    val participantJoined: LiveData<RemoteParticipant> = _participantJoined

    private val _participantLeft = MutableLiveData<RemoteParticipant>()
    val participantLeft: LiveData<RemoteParticipant> = _participantLeft

    private val _muted = MutableLiveData<Boolean>(false)
    val muted: LiveData<Boolean> = _muted

    private val _speakerOn = MutableLiveData<Boolean>(false)
    val speakerOn: LiveData<Boolean> = _speakerOn

    private val _videoEnabled = MutableLiveData<Boolean>(false)
    val videoEnabled: LiveData<Boolean> = _videoEnabled

    private val _alert = MutableLiveData<String>()
    val alert: LiveData<String> = _alert

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _leftRoom = MutableLiveData<Boolean>(false)
    val leftRoom: LiveData<Boolean> = _leftRoom

    fun leaveRoom() {
        roomManager.leaveRoom()
    }

    fun toggleMute() {
        roomManager.toggleMute()
    }

    fun toggleVideo() {
        roomManager.toggleVideo()
    }

    fun toggleSpeakerphone() {
        roomManager.toggleSpeakerphone()
    }

    fun switchCamera() {
        roomManager.switchCamera()
    }

    fun pauseVideoRendering() {
        roomManager.pauseVideoRendering()
    }

    fun resumeVideoRendering() {
        roomManager.resumeVideoRendering()
    }

    fun joinRoom(token: String, options: SessionOptions) {
        viewModelScope.launch {
            roomManager.joinRoom(token, options)
        }
    }

    private fun subscribeToRoomEvents() {
        roomManager.roomEvents.let { sharedFlow ->
            roomManagerJob = viewModelScope.launch {
                Log.d(TAG, "Listening for RoomEvents...")
                sharedFlow.collect { observeRoomEvents(it) }
            }
        }
    }

    private fun observeRoomEvents(roomEvent: RoomEvent) {
        Log.d(TAG, "Room event received: $roomEvent")
        when (roomEvent) {
            is RoomEvent.roomJoined -> {
                _roomName.value = roomEvent.roomName
                _localParticipant.value = roomEvent.localParticipant
                _videoEnabled.value = roomEvent.video
                _remoteParticipants.value = roomEvent.remoteParticipants.toMutableList()
            }
            is RoomEvent.roomJoinError -> {
                _alert.value = roomEvent.error
                _leftRoom.value = true
            }
            is RoomEvent.roomLeft -> {
                _leftRoom.value = true
            }
            is RoomEvent.participantJoined -> {
                _remoteParticipants.value?.add(roomEvent.remoteParticipant)
                _participantJoined.value = roomEvent.remoteParticipant
            }
            is RoomEvent.participantLeft -> {
                _remoteParticipants.value?.removeAll { p -> p.address() == roomEvent.remoteParticipant.address() }
                _participantLeft.value = roomEvent.remoteParticipant
            }
            is RoomEvent.remoteStreamUpdated -> {
                _remoteParticipants.value?.let { list ->
                    for (i in list.indices) {
                        if (list[i].address() == roomEvent.remoteParticipant.address()) {
                            list[i] = roomEvent.remoteParticipant
                        }
                    }
                }
            }
            is RoomEvent.registrationRejected -> {
                _alert.value = "Registration to room (${roomEvent.roomName}) rejected: ${roomEvent.reason}"
            }
            is RoomEvent.muted -> {
                _muted.value = roomEvent.muted
            }
            is RoomEvent.videoEnabled -> {
                _videoEnabled.value = roomEvent.video
            }
            is RoomEvent.speakerOn -> {
                _speakerOn.value = roomEvent.speaker
            }
            is RoomEvent.error -> {
                _alert.value = roomEvent.error
            }
        }
    }
}
