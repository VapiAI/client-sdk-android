package com.vapi.vapitest

import ai.vapi.android.Vapi
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private var VAPI_PUBLIC_KEY = "XXXXX"
private var VAPI_ASSISTANT_ID= "XXXXX"

class MainActivity : AppCompatActivity() {

    private lateinit var vapi: Vapi
    private lateinit var startCallButton: Button
    private lateinit var endCallButton: Button
    private lateinit var toggleMuteButton: Button
    private lateinit var statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.vapi.vapitest.R.layout.activity_vapi_test)

        startCallButton = findViewById(com.vapi.vapitest.R.id.startCallButton)
        endCallButton = findViewById(com.vapi.vapitest.R.id.endCallButton)
        toggleMuteButton = findViewById(com.vapi.vapitest.R.id.toggleMuteButton)
        statusTextView = findViewById(com.vapi.vapitest.R.id.statusTextView)

        val config = Vapi.Configuration(VAPI_PUBLIC_KEY)
        vapi = Vapi(this, lifecycle, config)

        setupButtons()
        observeVapiEvents()
    }

    private fun setupButtons() {
        startCallButton.setOnClickListener {
            lifecycleScope.launch {
                vapi.start(VAPI_ASSISTANT_ID)
                    .onSuccess { updateStatus("Call started successfully") }
                    .onFailure { updateStatus("Failed to start call: ${it.message}") }
            }
        }

        endCallButton.setOnClickListener {
            vapi.stop()
            updateStatus("Call ended")
        }

        toggleMuteButton.setOnClickListener {
            lifecycleScope.launch {
                vapi.toggleMute()
                    .onSuccess { updateStatus("Audio toggled successfully") }
                    .onFailure { updateStatus("Failed to toggle mute: ${it.message}") }
            }
        }
    }

    private fun observeVapiEvents() {
        vapi.eventFlow.onEach { event ->
            when (event) {
                is Vapi.Event.CallDidStart -> updateStatus("Call started")
                is Vapi.Event.CallDidEnd -> updateStatus("Call ended")
                is Vapi.Event.Transcript -> updateStatus("Transcript: ${event.text}")
                is Vapi.Event.FunctionCall -> updateStatus("Function call: ${event.name}")
                is Vapi.Event.SpeechUpdate -> updateStatus("Speech update: ${event.text}")
                is Vapi.Event.Metadata -> updateStatus("Metadata received")
                is Vapi.Event.ConversationUpdate -> updateStatus("Conversation updated")
                is Vapi.Event.Hang -> updateStatus("Call hung up")
                is Vapi.Event.Error -> updateStatus("Error: ${event.error}")
                else -> {}
            }
        }.launchIn(lifecycleScope)
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusTextView.text = message
        }
    }
}