package org.mohtech.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize Android Application Context globally for offline storage and preferences
        AndroidContextHolder.context = applicationContext

        // Wire AndroidVoiceBridge delegates for Activity context & permission requests
        AndroidVoiceBridge.activityProvider = { this }
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

    override fun onDestroy() {
        super.onDestroy()
        AndroidVoiceBridge.activityProvider = null
        AndroidVoiceBridge.speechIntentLauncher = null
        AndroidVoiceBridge.micPermissionLauncher = null
        onPermissionGrantedCallback = null
        onPermissionDeniedCallback = null
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
