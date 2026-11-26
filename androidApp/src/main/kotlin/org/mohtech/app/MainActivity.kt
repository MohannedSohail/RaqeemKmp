package org.mohtech.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mohtech.app.ai.audio.AndroidVoiceBridge
import org.mohtech.app.data.storage.AndroidContextHolder
import org.mohtech.app.feature.receipts.PlatformReceiptPicker
import org.mohtech.app.feature.receipts.PlatformReceiptStorage
import org.mohtech.app.feature.receipts.ReceiptPickerBridge
import org.mohtech.app.feature.reports.AndroidReportBridge
import java.io.File

class MainActivity : ComponentActivity() {

    private var onPermissionGrantedCallback: (() -> Unit)? = null
    private var onPermissionDeniedCallback: (() -> Unit)? = null
    private var pendingFileToSave: String? = null
    private var onReceiptPickedCallback: ((String?) -> Unit)? = null
    private var targetReceiptExpenseId: String = ""

    private val pickReceiptLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val key = targetReceiptExpenseId
        val callback = onReceiptPickedCallback
        onReceiptPickedCallback = null
        if (uri == null || callback == null) {
            callback?.invoke(null)
            return@registerForActivityResult
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val storedPath = try {
                val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (rawBytes != null) {
                    PlatformReceiptStorage.compressAndStoreReceiptBytes(key, rawBytes)
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }
            withContext(Dispatchers.Main) {
                callback.invoke(storedPath)
            }
        }
    }

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

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val sourcePath = pendingFileToSave
        if (uri != null && sourcePath != null) {
            try {
                val sourceFile = File(sourcePath)
                if (sourceFile.exists()) {
                    contentResolver.openOutputStream(uri)?.use { outStream ->
                        sourceFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Ignore SAF write failure
            }
        }
        pendingFileToSave = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize Android Application Context globally for offline storage and preferences
        AndroidContextHolder.context = applicationContext

        // Wire AndroidVoiceBridge delegates for Activity context & permission requests
        AndroidVoiceBridge.activityProvider = { this }
        AndroidVoiceBridge.micPermissionLauncher = { onGranted, onDenied ->
            requestMicPermission(onGranted, onDenied)
        }

        // Wire AndroidReportBridge delegates for FileProvider and SAF
        AndroidReportBridge.shareFileDelegate = { filePath, mimeType ->
            shareReportFile(filePath, mimeType)
        }
        AndroidReportBridge.saveDocumentLauncher = { sourcePath, defaultFileName, _ ->
            pendingFileToSave = sourcePath
            createDocumentLauncher.launch(defaultFileName)
        }

        // Wire PlatformReceiptPicker delegate for local receipt photo storage
        PlatformReceiptPicker.delegate = object : ReceiptPickerBridge {
            override fun launchPicker(expenseId: String, onImagePicked: (String?) -> Unit) {
                targetReceiptExpenseId = expenseId
                onReceiptPickedCallback = onImagePicked
                pickReceiptLauncher.launch("image/*")
            }
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

    private fun shareReportFile(filePath: String, mimeType: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة التقرير"))
        } catch (_: Throwable) {
            // Ignore share intent failures
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AndroidVoiceBridge.activityProvider = null
        AndroidVoiceBridge.speechIntentLauncher = null
        AndroidVoiceBridge.micPermissionLauncher = null
        AndroidReportBridge.shareFileDelegate = null
        AndroidReportBridge.saveDocumentLauncher = null
        PlatformReceiptPicker.delegate = null
        onReceiptPickedCallback = null
        onPermissionGrantedCallback = null
        onPermissionDeniedCallback = null
        pendingFileToSave = null
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
