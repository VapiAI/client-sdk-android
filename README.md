# Vapi Client SDK for Android

## Minimum SDK Version
The Vapi Client SDK for Android requires a `minSdkVersion` of 21 or greater.

## Installing using Maven
In your top-level `build.gradle` file, add the Maven Central repository:

```
repositories {
    // ...
    mavenCentral()
}
```

In your app’s build.gradle file, add the Vapi Client SDK dependency:

```
dependencies {
    // ...
    implementation 'ai.vapi.android:vapi:1.0.1'
}
```

## App setup
Make sure the following permissions are in your app’s AndroidManifest.xml:
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<application>
  <!--
    A foreground service is needed in the following cases:
    - if you wish to screen share
    - if you wish to keep your call running while the app is in background
  -->
  <service
        android:name=".YourForegroundService"
        android:exported="false"
        android:foregroundServiceType="phoneCall|camera|microphone|mediaProjection" />
</application>

The foregroundServiceType phoneCall, camera and microphone are needed in order to give your app the ability to continue running calls while in the background

The foregroundServiceType mediaProjection is only needed if you wish to do screen sharing.

In order to give your app the ability to continue running calls while in the background, you can start a foreground service that shows an in-call notification while your call is active.

## Usage

First, import the Vapi class from the package:

```kotlin
import ai.vapi.android.Vapi
```

Then, create a new instance of the Vapi class, passing your Public Key and other configuration parameters:

```kotlin
val configuration = Vapi.Configuration(
    publicKey = "your-public-key",
    host = "api.vapi.ai" // Optional, defaults to "api.vapi.ai"
)

val vapi = Vapi(context, lifecycle, configuration)
```

You can start a new call by calling the `start` method and passing an `assistant` object or `assistantId`:

```kotlin
// Using assistantId
vapi.start(
    assistantId = "your-assistant-id",
    metadata = mapOf("key" to "value"), // Optional
    assistantOverrides = mapOf("key" to "value") // Optional
).onSuccess { response ->
    // Handle successful start
}.onFailure { error ->
    // Handle error
}

// Using assistant object
val assistant = mapOf(
    "model" to mapOf(
        "provider" to "openai",
        "model" to "gpt-3.5-turbo",
        "systemPrompt" to "You're an assistant..."
    ),
    "voice" to mapOf(
        "provider" to "11labs",
        "voiceId" to "burt"
    )
)

vapi.start(
    assistant = assistant,
    metadata = mapOf("key" to "value"), // Optional
    assistantOverrides = mapOf("key" to "value") // Optional
).onSuccess { response ->
    // Handle successful start
}.onFailure { error ->
    // Handle error
}
```

You can send text messages to the assistant aside from the audio input using the `send` method:

```kotlin
val message = VapiMessage(
    type = "add-message",
    message = VapiMessageContent(
        role = "system",
        content = "The user has pressed the button, say peanuts"
    )
)

vapi.send(message).onSuccess {
    // Message sent successfully
}.onFailure { error ->
    // Handle error
}
```

Possible values for the role are `system`, `user`, `assistant`, `tool`, or `function`.

You can stop the session by calling the `stop` method:

```kotlin
vapi.stop()
```

This will stop the recording and close the connection.

The `toggleMute()` method can be used to mute and un-mute the user's microphone:

```kotlin
vapi.toggleMute().onSuccess {
    // Mute toggled successfully
}.onFailure { error ->
    // Handle error
}
```

### Events

You can listen to events using Kotlin Flows:

```kotlin
lifecycleScope.launch {
    vapi.eventFlow.collect { event ->
        when (event) {
            is Vapi.Event.CallDidStart -> println("Call started")
            is Vapi.Event.CallDidEnd -> println("Call ended")
            is Vapi.Event.Transcript -> println("Transcript: ${event.text}")
            is Vapi.Event.FunctionCall -> println("Function call: ${event.name}, parameters: ${event.parameters}")
            is Vapi.Event.SpeechUpdate -> println("Speech update: ${event.text}")
            is Vapi.Event.Metadata -> println("Metadata: ${event.data}")
            is Vapi.Event.ConversationUpdate -> println("Conversation update: ${event.messages}")
            is Vapi.Event.Hang -> println("Hang event received")
            is Vapi.Event.Error -> println("Error: ${event.error}")
        }
    }
}
```

These events allow you to react to changes in the state of the call or speech.

## Permissions

The SDK will automatically request the necessary permissions (RECORD_AUDIO and optionally CAMERA) when starting a call. Make sure to handle the permission request result in your activity:

```kotlin
override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 1001) {
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            // Permissions granted, you can now start the call
        } else {
            // Permissions denied, handle accordingly
        }
    }
}
```

## License

```
MIT License

Copyright (c) 2023 Vapi Labs Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```