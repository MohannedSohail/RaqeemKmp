@file:OptIn(ExperimentalForeignApi::class)

package org.mohtech.app.feature.receipts

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIPresentationController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.math.roundToInt

actual object PlatformReceiptStorage {

    private val storageScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activePickerDelegate: NSObject? = null

    private fun resolveSandboxDir(): String? {
        val urls = NSFileManager.defaultManager.URLsForDirectory(
            NSDocumentDirectory,
            NSUserDomainMask
        )
        val docsPath = (urls.firstOrNull() as? NSURL)?.path ?: return null
        val receiptsDir = "$docsPath/receipts"
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(receiptsDir)) {
            val created = fileManager.createDirectoryAtPath(
                path = receiptsDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
            if (!created) return null
        }
        return receiptsDir
    }

    private fun findTopViewController(): UIViewController? {
        val scenes = UIApplication.sharedApplication.connectedScenes
        var rootViewController: UIViewController? = null
        for (scene in scenes) {
            val windowScene = scene as? UIWindowScene ?: continue
            val windows = windowScene.windows
            val keyWindow = (windows.firstOrNull { (it as? UIWindow)?.keyWindow == true } as? UIWindow)
                ?: (windows.firstOrNull() as? UIWindow)
            if (keyWindow?.rootViewController != null) {
                rootViewController = keyWindow.rootViewController
                break
            }
        }
        var current = rootViewController ?: return null
        while (current.presentedViewController != null) {
            current = current.presentedViewController!!
        }
        return current
    }

    actual fun pickAndStoreCompressedReceipt(receiptKey: String, onResult: (String?) -> Unit) {
        dispatch_async(dispatch_get_main_queue()) {
            val presenter = findTopViewController()
            if (presenter == null) {
                onResult(null)
                return@dispatch_async
            }

            val config = PHPickerConfiguration().apply {
                filter = PHPickerFilter.imagesFilter()
                selectionLimit = 1L
            }
            val picker = PHPickerViewController(configuration = config)
            val delegate = ReceiptPickerDelegate(
                receiptKey = receiptKey,
                onCompleted = { completedDelegate, storedPath ->
                    if (activePickerDelegate === completedDelegate) {
                        activePickerDelegate = null
                    }
                    onResult(storedPath)
                }
            )
            activePickerDelegate = delegate
            picker.delegate = delegate
            picker.presentationController?.delegate = delegate
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }

    actual suspend fun pickAndStoreCompressedReceipt(receiptKey: String): String? =
        suspendCancellableCoroutine { continuation ->
            var completed = false
            pickAndStoreCompressedReceipt(receiptKey) { storedPath ->
                if (!completed && continuation.isActive) {
                    completed = true
                    continuation.resume(storedPath)
                }
            }
        }

    actual suspend fun compressAndStoreReceiptBytes(receiptKey: String, rawBytes: ByteArray): String? =
        withContext(Dispatchers.Default) {
            if (rawBytes.isEmpty()) return@withContext null

            val sourceData = rawBytes.toNSData() ?: return@withContext null
            val sourceImage = UIImage.imageWithData(sourceData) ?: return@withContext null
            val (width, height) = sourceImage.size.useContents { width to height }
            if (width <= 0.0 || height <= 0.0) return@withContext null

            val maxDim = RECEIPT_MAX_DIMENSION_PX.toDouble()
            val maxEdge = maxOf(width, height)
            val (targetWidth, targetHeight) = if (maxEdge > maxDim) {
                val scale = maxDim / maxEdge
                Pair(
                    (width * scale).roundToInt().coerceAtLeast(1).toDouble(),
                    (height * scale).roundToInt().coerceAtLeast(1).toDouble()
                )
            } else {
                Pair(width, height)
            }

            val renderedImage = renderOpaqueUiImage(sourceImage, targetWidth, targetHeight)
                ?: return@withContext null

            val jpegData = UIImageJPEGRepresentation(
                image = renderedImage,
                compressionQuality = RECEIPT_JPEG_QUALITY_PERCENT.toDouble() / 100.0
            ) ?: return@withContext null
            if (jpegData.length == 0UL) return@withContext null

            val receiptsDir = resolveSandboxDir() ?: return@withContext null
            val safeKey = sanitizeReceiptKey(receiptKey)
            val destPath = "$receiptsDir/$safeKey.jpg"
            val written = jpegData.writeToFile(path = destPath, atomically = true)
            if (written) destPath else null
        }

    actual suspend fun loadReceiptThumbnail(fileUri: String, maxDimensionPx: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            if (fileUri.isBlank() || !receiptExists(fileUri)) return@withContext null
            val targetMax = maxDimensionPx.coerceAtLeast(1).toDouble()

            runCatching {
                val image = UIImage.imageWithContentsOfFile(fileUri) ?: return@runCatching null
                val (width, height) = image.size.useContents { width to height }
                if (width <= 0.0 || height <= 0.0) return@runCatching null

                val maxEdge = maxOf(width, height)
                val encodedData: NSData = if (maxEdge > targetMax) {
                    val scale = targetMax / maxEdge
                    val targetW = (width * scale).roundToInt().coerceAtLeast(1).toDouble()
                    val targetH = (height * scale).roundToInt().coerceAtLeast(1).toDouble()
                    val scaled = renderOpaqueUiImage(image, targetW, targetH)
                        ?: return@runCatching null
                    UIImageJPEGRepresentation(scaled, 0.85) ?: return@runCatching null
                } else {
                    NSData.dataWithContentsOfFile(fileUri) ?: return@runCatching null
                }

                val bytes = encodedData.toByteArray()
                if (bytes.isEmpty()) null else bytes.decodeToImageBitmap()
            }.getOrNull()
        }

    private fun isRegularFile(fileUri: String, requireNonEmpty: Boolean): Boolean {
        if (fileUri.isBlank()) return false
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(fileUri, error = null)
            ?: return false
        val fileType = attrs[NSFileType] as? String ?: return false
        if (fileType != NSFileTypeRegular) return false
        if (!requireNonEmpty) return true
        val fileSize = when (val sizeVal = attrs[NSFileSize]) {
            is NSNumber -> sizeVal.unsignedLongLongValue
            is Number -> sizeVal.toLong().coerceAtLeast(0L).toULong()
            else -> 0UL
        }
        return fileSize > 0UL
    }

    actual fun receiptExists(fileUri: String): Boolean =
        isRegularFile(fileUri, requireNonEmpty = true)

    actual fun deleteReceipt(fileUri: String): Boolean {
        if (!isRegularFile(fileUri, requireNonEmpty = false)) return false
        return NSFileManager.defaultManager.removeItemAtPath(fileUri, error = null)
    }

    private fun renderOpaqueUiImage(
        sourceImage: UIImage,
        targetWidth: Double,
        targetHeight: Double
    ): UIImage? {
        UIGraphicsBeginImageContextWithOptions(
            size = CGSizeMake(targetWidth, targetHeight),
            opaque = true,
            scale = 1.0
        )
        return try {
            val rect = CGRectMake(0.0, 0.0, targetWidth, targetHeight)
            UIGraphicsGetCurrentContext()?.let { ctx ->
                CGContextSetRGBFillColor(ctx, 1.0, 1.0, 1.0, 1.0)
                CGContextFillRect(ctx, rect)
            }
            sourceImage.drawInRect(rect)
            UIGraphicsGetImageFromCurrentImageContext()
        } finally {
            UIGraphicsEndImageContext()
        }
    }

    private fun ByteArray.toNSData(): NSData? {
        if (isEmpty()) return null
        return usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        if (length == 0UL || length > Int.MAX_VALUE.toULong()) return ByteArray(0)
        val sizeInt = length.toInt()
        val ptr = bytes ?: return ByteArray(0)
        val result = ByteArray(sizeInt)
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), ptr, length)
        }
        return result
    }

    private class ReceiptPickerDelegate(
        private val receiptKey: String,
        private val onCompleted: (ReceiptPickerDelegate, String?) -> Unit
    ) : NSObject(),
        PHPickerViewControllerDelegateProtocol,
        UIAdaptivePresentationControllerDelegateProtocol {

        private var finished = false

        private fun finishOnce(path: String?) {
            dispatch_async(dispatch_get_main_queue()) {
                if (!finished) {
                    finished = true
                    onCompleted(this, path)
                }
            }
        }

        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, completion = null)
            val result = didFinishPicking.firstOrNull() as? PHPickerResult
            val provider = result?.itemProvider
            if (provider == null || !provider.hasItemConformingToTypeIdentifier("public.image")) {
                finishOnce(null)
                return
            }

            provider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                if (data == null || error != null) {
                    finishOnce(null)
                    return@loadDataRepresentationForTypeIdentifier
                }
                val rawBytes = data.toByteArray()
                if (rawBytes.isEmpty()) {
                    finishOnce(null)
                    return@loadDataRepresentationForTypeIdentifier
                }
                storageScope.launch {
                    val storedPath = compressAndStoreReceiptBytes(receiptKey, rawBytes)
                    finishOnce(storedPath)
                }
            }
        }

        override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
            finishOnce(null)
        }
    }
}
