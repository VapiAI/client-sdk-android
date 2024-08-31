# Vapi Client SDK for Android

## Minimum SDK Version
The Vapi Client SDK for Android requires a `minSdkVersion` of 21 or greater.

## Installing using Maven
In your top-level `build.gradle` file, add the Maven Central repository:
repositories {
    // ...
    mavenCentral()
}

In your app’s build.gradle file, add the Vapi Client SDK dependency:

dependencies {
    // ...
    implementation 'ai.vapi:client:0.0.1'
}

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

Continuing calls in the background
In order to give your app the ability to continue running calls while in the background, you can start a foreground service that shows an in-call notification while your call is active.

You can read more about foreground services in the Android documentation.

For an example of a foreground service that customizes and shows a notification, see the one that is bundled with react-native-daily-js.
