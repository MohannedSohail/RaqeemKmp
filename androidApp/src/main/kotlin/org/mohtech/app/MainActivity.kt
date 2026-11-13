package org.mohtech.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import org.mohtech.app.ai.audio.AndroidVoiceBridge
import org.mohtech.app.data.storage.AndroidContextHolder

class MainActivity : ComponentActivity() {

    private var onPermissionGrantedCallback: (() -> Unit)? = null
    private var onPermissionDeniedCallback: (() -> Unit)? = null

    private var onSpeechResultCallback: ((String) -> Unit)? = null
    private var onSpeechErrorCallback: ((String) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            onPermissionGrantedCallback?.invoke()
        } else {
            onPermissionDeniedCallback?.invoke()
        }
        onPermissionGrantedCallback = null
        onPermissionDeniedCallback = null
    }

    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognizedText = matches?.firstOrNull()?.trim().orEmpty()
            if (recognizedText.isNotBlank()) {
                onSpeechResultCallback?.invoke(recognizedText)
            } else {
                onSpeechErrorCallback?.invoke("NO_MATCH")
            }
        } else {
            onSpeechErrorCallback?.invoke("SPEECH_CANCELLED")
        }
        onSpeechResultCallback = null
        onSpeechErrorCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize Android Application Context globally for offline storage and preferences
        AndroidContextHolder.context = applicationContext

        // Wire AndroidVoiceBridge delegates for Branch B fallback & permission requests
        AndroidVoiceBridge.speechIntentLauncher = { onResult, onError ->
            launchSpeechInput(onResult, onError)
        }
        AndroidVoiceBridge.micPermissionLauncher = { onGranted, onDenied ->
            requestMicPermission(onGranted, onDenied)
        }

        // Request Record Audio permission if not already granted on startup
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            App(
                onRequestMicPermission = {
                    requestMicPermission(onGranted = null, onDenied = null)
                }
            )
        }
    }

    private fun requestMicPermission(
        onGranted: (() -> Unit)? = null,
        onDenied: (() -> Unit)? = null
    ) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted?.invoke()
        } else {
            onPermissionGrantedCallback = onGranted
            onPermissionDeniedCallback = onDenied
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchSpeechInput(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ar-SA", "ar-EG", "ar-PS", "ar-JO"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث لتسجيل المصروف باللغة العربية")
        }

        onSpeechResultCallback = onResult
        onSpeechErrorCallback = onError

        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            onSpeechResultCallback = null
            onSpeechErrorCallback = null
            onError("FAILED_TO_LAUNCH_INTENT: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AndroidVoiceBridge.speechIntentLauncher = null
        AndroidVoiceBridge.micPermissionLauncher = null
        onPermissionGrantedCallback = null
        onPermissionDeniedCallback = null
        onSpeechResultCallback = null
        onSpeechErrorCallback = null
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
