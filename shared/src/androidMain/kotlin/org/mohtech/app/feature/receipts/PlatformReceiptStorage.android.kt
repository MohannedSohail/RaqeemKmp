package org.mohtech.app.feature.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.mohtech.app.data.storage.AndroidContextHolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.roundToInt

actual object PlatformReceiptStorage {

    private val ioLock = Any()

    private fun resolveSandboxDir(ctx: Context): File? = runCatching {
        File(ctx.filesDir, "receipts").apply {
            if (!exists()) mkdirs()
        }
    }.getOrNull()

    actual fun pickAndStoreCompressedReceipt(receiptKey: String, onResult: (String?) -> Unit) {
        val bridge = PlatformReceiptPicker.delegate
        if (bridge != null) {
            bridge.launchPicker(receiptKey, onResult)
        } else {
            onResult(null)
        }
    }

    actual suspend fun pickAndStoreCompressedReceipt(receiptKey: String): String? =
        suspendCancellableCoroutine { continuation ->
            val resumed = AtomicBoolean(false)
            pickAndStoreCompressedReceipt(receiptKey) { storedPath ->
                if (resumed.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(storedPath)
                }
            }
        }

    actual suspend fun compressAndStoreReceiptBytes(receiptKey: String, rawBytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            if (rawBytes.isEmpty()) return@withContext null
            val ctx = AndroidContextHolder.context ?: return@withContext null
            val receiptsDir = resolveSandboxDir(ctx) ?: return@withContext null
            if (!receiptsDir.exists() || !receiptsDir.isDirectory) return@withContext null

            val orientation = runCatching {
                ExifInterface(ByteArrayInputStream(rawBytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return@withContext null
            }

            val sampleSize = calculateInSampleSize(
                width = boundsOpts.outWidth,
                height = boundsOpts.outHeight,
                maxDimensionPx = RECEIPT_MAX_DIMENSION_PX
            )
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            var decoded: Bitmap? = null
            var oriented: Bitmap? = null
            var finalBitmap: Bitmap? = null
            var opaqueWhiteBitmap: Bitmap? = null
            try {
                decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                    ?: return@withContext null

                oriented = applyExifOrientation(decoded, orientation)

                finalBitmap = downscaleIfNeeded(oriented, RECEIPT_MAX_DIMENSION_PX)

                val bitmapToCompress = if (finalBitmap.hasAlpha()) {
                    val whiteBitmap = Bitmap.createBitmap(
                        finalBitmap.width,
                        finalBitmap.height,
                        Bitmap.Config.ARGB_8888
                    )
                    opaqueWhiteBitmap = whiteBitmap
                    val canvas = Canvas(whiteBitmap)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(finalBitmap, 0f, 0f, null)
                    whiteBitmap
                } else {
                    finalBitmap
                }

                val byteOut = ByteArrayOutputStream()
                val compressedOk = bitmapToCompress.compress(
                    Bitmap.CompressFormat.JPEG,
                    RECEIPT_JPEG_QUALITY_PERCENT,
                    byteOut
                )
                val compressedBytes = byteOut.toByteArray()
                if (!compressedOk || compressedBytes.isEmpty()) {
                    return@withContext null
                }

                val safeKey = sanitizeReceiptKey(receiptKey)
                val destFile = File(receiptsDir, "$safeKey.jpg")
                val canonicalDir = receiptsDir.canonicalPath
                val canonicalDest = destFile.canonicalPath
                if (!canonicalDest.startsWith(canonicalDir + File.separator)) {
                    return@withContext null
                }

                val tempFile = File(receiptsDir, "$safeKey.jpg.tmp")
                val backupFile = File(receiptsDir, "$safeKey.jpg.bak")
                synchronized(ioLock) {
                    var movedToBackup = false
                    var replacedSuccessfully = false
                    try {
                        FileOutputStream(tempFile).use { fos ->
                            fos.write(compressedBytes)
                            fos.flush()
                            fos.fd.sync()
                        }
                        if (backupFile.exists()) {
                            backupFile.delete()
                        }
                        if (destFile.exists()) {
                            if (!destFile.renameTo(backupFile)) {
                                return@withContext null
                            }
                            movedToBackup = true
                        }
                        if (!tempFile.renameTo(destFile)) {
                            return@withContext null
                        }
                        replacedSuccessfully = true
                        if (movedToBackup && backupFile.exists()) {
                            backupFile.delete()
                        }
                    } finally {
                        if (!replacedSuccessfully && movedToBackup && !destFile.exists() && backupFile.exists()) {
                            backupFile.renameTo(destFile)
                        }
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                    }
                }
                destFile.absolutePath
            } catch (_: Throwable) {
                null
            } finally {
                if (opaqueWhiteBitmap != null &&
                    opaqueWhiteBitmap !== finalBitmap &&
                    opaqueWhiteBitmap !== oriented &&
                    opaqueWhiteBitmap !== decoded &&
                    !opaqueWhiteBitmap.isRecycled
                ) {
                    opaqueWhiteBitmap.recycle()
                }
                if (finalBitmap != null && !finalBitmap.isRecycled) {
                    finalBitmap.recycle()
                }
                if (oriented != null && oriented !== finalBitmap && !oriented.isRecycled) {
                    oriented.recycle()
                }
                if (decoded != null && decoded !== oriented && decoded !== finalBitmap && !decoded.isRecycled) {
                    decoded.recycle()
                }
            }
        }

    actual suspend fun loadReceiptThumbnail(fileUri: String, maxDimensionPx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (fileUri.isBlank()) return@withContext null
            val targetMax = maxDimensionPx.coerceAtLeast(1)
            val file = File(fileUri)
            if (!file.exists() || !file.isFile || file.length() <= 0L) {
                return@withContext null
            }

            var decoded: Bitmap? = null
            var oriented: Bitmap? = null
            var finalBitmap: Bitmap? = null
            try {
                val orientation = runCatching {
                    ExifInterface(file.absolutePath).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

                val boundsOpts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                    return@withContext null
                }

                val sampleSize = calculateInSampleSize(
                    width = boundsOpts.outWidth,
                    height = boundsOpts.outHeight,
                    maxDimensionPx = targetMax
                )
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                    ?: return@withContext null

                oriented = applyExifOrientation(decoded, orientation)
                finalBitmap = downscaleIfNeeded(oriented, targetMax)

                // Do NOT recycle finalBitmap: AndroidImageBitmap wraps it for Compose rendering.
                finalBitmap.asImageBitmap()
            } catch (_: Throwable) {
                if (finalBitmap != null && !finalBitmap.isRecycled) {
                    finalBitmap.recycle()
                }
                null
            } finally {
                if (oriented != null && oriented !== finalBitmap && !oriented.isRecycled) {
                    oriented.recycle()
                }
                if (decoded != null && decoded !== oriented && decoded !== finalBitmap && !decoded.isRecycled) {
                    decoded.recycle()
                }
            }
        }

    actual fun receiptExists(fileUri: String): Boolean {
        if (fileUri.isBlank()) return false
        return runCatching {
            val file = File(fileUri)
            file.exists() && file.isFile && file.length() > 0L
        }.getOrDefault(false)
    }

    actual fun deleteReceipt(fileUri: String): Boolean {
        if (fileUri.isBlank()) return false
        return runCatching {
            val file = File(fileUri)
            file.exists() && file.isFile && file.delete()
        }.getOrDefault(false)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimensionPx: Int): Int {
        var inSampleSize = 1
        if (height > maxDimensionPx || width > maxDimensionPx) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= maxDimensionPx ||
                halfWidth / inSampleSize >= maxDimensionPx
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
        val maxEdge = maxOf(bitmap.width, bitmap.height)
        if (maxEdge <= maxDimensionPx) return bitmap
        val scale = maxDimensionPx.toDouble() / maxEdge.toDouble()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
