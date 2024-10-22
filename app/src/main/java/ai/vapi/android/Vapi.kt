package ai.vapi.android

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import co.daily.CallClient
import co.daily.CallClientListener
import co.daily.model.*
import co.daily.model.streaming.StreamingSettings
import co.daily.model.streaming.StreamingVideoSettings
import co.daily.settings.CameraInputSettingsUpdate
import co.daily.settings.ClientSettingsUpdate
import co.daily.settings.Height
import co.daily.settings.InputSettingsUpdate
import co.daily.settings.MicrophoneInputSettingsUpdate
import co.daily.settings.StateBoolean
import co.daily.settings.Torch
import co.daily.settings.VideoMediaTrackSettingsUpdate
import co.daily.settings.Width
import co.daily.settings.ZoomRatio
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

public class Vapi(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val configuration: Configuration
) : CallClientListener {

    data class Configuration(
        val publicKey: String,
        val host: String? = "api.vapi.ai",
    ) {
        companion object {
            const val DEFAULT_HOST = "api.vapi.ai"
        }
    }

    sealed class Event {
        object CallDidStart : Event()
        object CallDidEnd : Event()
        data class Transcript(val text: String) : Event()
        data class FunctionCall(val name: String, val parameters: Map<String, Any>) : Event()
        data class SpeechUpdate(val text: String) : Event()
        data class Metadata(val data: Map<String, Any>) : Event()
        data class ConversationUpdate(val messages: List<Map<String, Any>>) : Event()
        object Hang : Event()
        data class Error(val error: String) : Event()
    }

    private val gson = Gson()
    private var call: CallClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val _eventFlow = MutableSharedFlow<Event>()
    val eventFlow = _eventFlow.asSharedFlow()

    var localAudioLevel: Float? = null
        private set

    var remoteAudioLevel: Float? = null
        private set

    private var isMicrophoneMuted: Boolean = false

    suspend fun start(
        assistantId: String,
        metadata: Map<String, Any> = emptyMap(),
        assistantOverrides: Map<String, Any> = emptyMap()
    ): Result<WebCallResponse> = runCatching {
        check(call == null) { "Existing call in progress" }

        val body = mapOf(
            "assistantId" to assistantId,
            "metadata" to metadata,
            "assistantOverrides" to assistantOverrides
        )

        startCall(body)
    }

    suspend fun start(
        assistant: Map<String, Any>,
        metadata: Map<String, Any> = emptyMap(),
        assistantOverrides: Map<String, Any> = emptyMap()
    ): Result<WebCallResponse> = runCatching {
        check(call == null) { "Existing call in progress" }

        val body = mapOf(
            "assistant" to assistant,
            "metadata" to metadata,
            "assistantOverrides" to assistantOverrides
        )

        startCall(body)
    }

    fun stop() {
        coroutineScope.launch {
            runCatching {
                call?.leave()
            }.onFailure { callDidFail(it) }
        }
    }

    fun send(message: VapiMessage): Result<Unit> = runCatching {
        val jsonString = gson.toJson(message)
        call?.sendAppMessage(jsonString, Recipient.All) ?: throw IllegalStateException("No call in progress")
    }

    private fun setMuted(muted: Boolean): Result<Unit> = runCatching {
        val call = call ?: throw IllegalStateException("No call in progress")
        val currentInputs = call.inputs()
        call.setInputsEnabled(currentInputs.camera.isEnabled, !muted)
        isMicrophoneMuted = muted
        Log.d("Vapi", if (muted) "Audio muted" else "Audio unmuted")
    }

    fun toggleMute(): Result<Unit> = setMuted(!isMicrophoneMuted)

    private fun joinCall(url: URL, recordVideo: Boolean) {
        if (!hasPermissions(recordVideo)) {
            requestPermissions(recordVideo)
            throw Throwable("Permissions not granted. Allow and try again.")
        }

        coroutineScope.launch {
            runCatching {
                val call = CallClient(context, lifecycle)
                call.addListener(this@Vapi)
                this@Vapi.call = call

                val cameraSettings = if (recordVideo) {
                    CameraInputSettingsUpdate(
                        StateBoolean.from(true),
                        settings = VideoMediaTrackSettingsUpdate(
                            width = Width(1280),
                            height = Height(720),
                            torch = Torch(false),
                            zoom = ZoomRatio(1.0)
                        )
                    )
                } else {
                    CameraInputSettingsUpdate(
                        StateBoolean.from(false),
                    )
                }

                val inputSettings = InputSettingsUpdate(
                    camera = cameraSettings,
                    microphone = MicrophoneInputSettingsUpdate(
                        StateBoolean.from(value = true)
                    )
                )

                val clientSettings = ClientSettingsUpdate(
                    inputSettings = inputSettings
                )

                call.join(
                    url = url.toString(),
                    meetingToken = null, // You may need to provide a meeting token if required
                    clientSettings = clientSettings,
                    listener = { result ->
                        if(result.isError){
                            callDidFail(Throwable(result.error?.msg))
                        }else{
                            Log.d("Vapi", "Successfully joined call")
                            if (recordVideo) {
                                call.startRecording(streamingSettings = StreamingSettings(
                                    StreamingVideoSettings(
                                        width = 1280,
                                        height = 720,
                                    )
                                ))
                            }
                        }
                    }
                )

            }.onFailure { callDidFail(it) }
        }
    }

    private fun hasPermissions(recordVideo: Boolean): Boolean {
        val microphonePermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        )

        // If recording video, check camera permission as well
        val cameraPermission = if (recordVideo) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            )
        } else {
            PackageManager.PERMISSION_GRANTED // Consider camera permission granted if not recording video
        }

        return microphonePermission == PackageManager.PERMISSION_GRANTED &&
                cameraPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions(recordVideo: Boolean) {
        val permissionsToRequest = mutableListOf<String>(
            android.Manifest.permission.RECORD_AUDIO
        )

        // Add camera permission if recording video
        if (recordVideo) {
            permissionsToRequest.add(android.Manifest.permission.CAMERA)
        }

        ActivityCompat.requestPermissions(
            context as Activity,
            permissionsToRequest.toTypedArray(),
            1001
        )
    }

    private suspend fun startCall(body: Map<String, Any>): WebCallResponse = withContext(Dispatchers.IO) {
        val scheme = if (configuration.host == "localhost") "http" else "https"
        val port = if (configuration.host == "localhost") ":3001" else ""
        val url = URL("$scheme://${configuration.host}$port/call/web")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer ${configuration.publicKey}")
        connection.doOutput = true

        val jsonBody = gson.toJson(body)

        OutputStreamWriter(connection.outputStream).use { it.write(jsonBody) }

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_CREATED) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val webCallResponse = gson.fromJson(response, WebCallResponse::class.java)
            joinCall(webCallResponse.webCallUrl, webCallResponse.artifactPlan?.videoRecordingEnabled ?: false)
            webCallResponse
        } else {
            throw Exception("HTTP error code: $responseCode")
        }
    }

    suspend fun startLocalAudioLevelObserver(): Result<Unit> = runCatching {
        call?.startLocalAudioLevelObserver() ?: throw IllegalStateException("No call in progress")
    }

    suspend fun startRemoteParticipantsAudioLevelObserver(): Result<Unit> = runCatching {
        call?.startRemoteParticipantsAudioLevelObserver() ?: throw IllegalStateException("No call in progress")
    }

    override fun onCallStateUpdated(state: CallState) {
        when (state) {
            CallState.joined -> callDidJoin()
            CallState.left -> callDidLeave()
            else -> {}
        }
    }

    override fun onParticipantUpdated(participant: Participant) {
        val isPlayable = participant.media?.microphone?.state == MediaState.playable
        val isVapiSpeaker = participant.info.userName == "Vapi Speaker"

        if (isPlayable && isVapiSpeaker) {
            coroutineScope.launch {
                runCatching {
                    call?.sendAppMessage("""{"message":"playable"}""", Recipient.All)
                }.onFailure { Log.e("Vapi", "Failed to send playable message", it) }
            }
        }
    }

    private fun cleanUpMessage(message: String): String {
        // Remove leading and trailing quotes if present
        var cleanedMessage = message.trim()

        if (cleanedMessage.startsWith("\"") && cleanedMessage.endsWith("\"")) {
            cleanedMessage = cleanedMessage.substring(1, cleanedMessage.length - 1)
        }

        // Unescape any escape sequences
        cleanedMessage = cleanedMessage.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\t", "\t")

        return cleanedMessage
    }

    override fun onAppMessage(message: String, from: ParticipantId) {
        val cleanedMessage = cleanUpMessage(message)

        coroutineScope.launch {
            runCatching {
                // Clean up the message

                if (cleanedMessage == "listening") {
                    _eventFlow.emit(Event.CallDidStart)
                    return@runCatching
                }

                val type = object : TypeToken<Map<String, Any>>() {}.type
                val jsonObject = gson.fromJson<Map<String, Any>>(cleanedMessage, type)
                val event = when (jsonObject["type"] as? String) {
                    "functionCall" -> {
                        val functionCall = jsonObject["functionCall"] as Map<*, *>
                        Event.FunctionCall(
                            functionCall["name"] as String,
                            functionCall["parameters"] as Map<String, Any>
                        )
                    }
                    "hang" -> Event.Hang
                    "transcript" -> Event.Transcript(jsonObject["transcript"] as String)
                    "speechUpdate" -> Event.SpeechUpdate(jsonObject["text"] as String)
                    "metadata" -> Event.Metadata(jsonObject["data"] as Map<String, Any>)
                    "conversationUpdate" -> Event.ConversationUpdate(jsonObject["messages"] as List<Map<String, Any>>)
                    else -> null
                }
                event?.let { _eventFlow.emit(it) }
            }.onFailure { Log.e("Vapi", "Error parsing app message: $cleanedMessage", it) }
        }
    }

    override fun onError(message: String) {
        callDidFail(Throwable(message))
    }

    override fun onLocalAudioLevel(audioLevel: Float) {
        localAudioLevel = audioLevel
    }

    override fun onRemoteParticipantsAudioLevel(participantsAudioLevel: Map<ParticipantId, Float>) {
        remoteAudioLevel = participantsAudioLevel.values.firstOrNull()
    }

    private fun callDidJoin() {
        Log.d("Vapi", "Successfully joined call.")
    }

    private fun callDidLeave() {
        Log.d("Vapi", "Successfully left call.")
        coroutineScope.launch {
            _eventFlow.emit(Event.CallDidEnd)
        }
        call = null
    }

    private fun callDidFail(error: Throwable) {
        Log.e("Vapi", "Got error while joining/leaving call: $error")
        coroutineScope.launch {
            _eventFlow.emit(Event.Error(error.message ?: "Unknown error"))
        }
        call = null
    }
}

data class VapiMessage(
    val type: String,
    val message: VapiMessageContent
)

data class VapiMessageContent(
    val role: String,
    val content: String
)

data class WebCallResponse(
    val webCallUrl: URL,
    val id: String,
    val artifactPlan: ArtifactPlan?,
)

data class ArtifactPlan(
    val videoRecordingEnabled: Boolean
)