package org.mohtech.app.feature.receipts

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.abs
import kotlin.math.roundToInt

actual object PlatformReceiptStorage {

    private val ioLock = Any()
    private val pickerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val supportedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private const val MAX_FALLBACK_FILE_BYTES: Long = 16L * 1024L * 1024L
    private const val MAX_FALLBACK_PIXELS: Long = 25_000_000L

    /**
     * Allows unit tests to isolate receipt persistence in a temporary directory without touching ~/.raqeem.
     */
    internal var testSandboxDirOverride: File? = null

    private fun resolveSandboxDir(): File? = runCatching {
        val dir = testSandboxDirOverride ?: File(System.getProperty("user.home"), ".raqeem/receipts")
        dir.apply {
            if (!exists()) mkdirs()
        }
    }.getOrNull()

    actual fun pickAndStoreCompressedReceipt(receiptKey: String, onResult: (String?) -> Unit) {
        pickerScope.launch {
            val storedPath = pickReceiptInternal(receiptKey)
            dispatchResultOnMain(storedPath, onResult)
        }
    }

    actual suspend fun pickAndStoreCompressedReceipt(receiptKey: String): String? =
        pickReceiptInternal(receiptKey)

    private suspend fun pickReceiptInternal(receiptKey: String): String? =
        withContext(Dispatchers.IO) {
            val selectedFile: File? = runCatching {
                val dialog = FileDialog(null as Frame?, "اختر صورة الإيصال", FileDialog.LOAD)
                try {
                    dialog.filenameFilter = FilenameFilter { _, name ->
                        val ext = name.substringAfterLast('.', "").lowercase()
                        ext in supportedExtensions
                    }
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val fileName = dialog.file
                    if (dir != null && fileName != null) File(dir, fileName) else null
                } finally {
                    dialog.dispose()
                }
            }.getOrNull()

            if (selectedFile == null || !selectedFile.exists() || !selectedFile.isFile) {
                return@withContext null
            }

            val rawBytes = runCatching { selectedFile.readBytes() }.getOrNull()
                ?: return@withContext null
            compressAndStoreReceiptBytes(receiptKey, rawBytes)
        }

    private suspend fun dispatchResultOnMain(result: String?, onResult: (String?) -> Unit) {
        val hasMain = runCatching {
            withContext(Dispatchers.Main) { Unit }
            true
        }.getOrDefault(false)
        if (hasMain) {
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        } else {
            onResult(result)
        }
    }

    actual suspend fun compressAndStoreReceiptBytes(receiptKey: String, rawBytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            if (rawBytes.isEmpty()) return@withContext null
            val srcImage = runCatching {
                ImageIO.read(ByteArrayInputStream(rawBytes))
            }.getOrNull() ?: return@withContext null

            if (srcImage.width <= 0 || srcImage.height <= 0) {
                return@withContext null
            }

            val maxEdge = maxOf(srcImage.width, srcImage.height)
            val (targetWidth, targetHeight) = if (maxEdge > RECEIPT_MAX_DIMENSION_PX) {
                val scale = RECEIPT_MAX_DIMENSION_PX.toDouble() / maxEdge.toDouble()
                Pair(
                    (srcImage.width * scale).roundToInt().coerceAtLeast(1),
                    (srcImage.height * scale).roundToInt().coerceAtLeast(1)
                )
            } else {
                Pair(srcImage.width, srcImage.height)
            }

            val rgbImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
            val g2d = rgbImage.createGraphics()
            try {
                g2d.color = Color.WHITE
                g2d.fillRect(0, 0, targetWidth, targetHeight)
                g2d.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
                )
                g2d.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
                )
                g2d.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )
                g2d.drawImage(srcImage, 0, 0, targetWidth, targetHeight, null)
            } finally {
                g2d.dispose()
            }

            val compressedBytes = runCatching {
                val byteOut = ByteArrayOutputStream()
                val writers = ImageIO.getImageWritersByFormatName("jpg")
                if (!writers.hasNext()) return@runCatching null
                val writer = writers.next()
                val imageOut = ImageIO.createImageOutputStream(byteOut)
                try {
                    writer.output = imageOut
                    val writeParam = writer.defaultWriteParam.apply {
                        if (canWriteCompressed()) {
                            compressionMode = ImageWriteParam.MODE_EXPLICIT
                            compressionQuality = RECEIPT_JPEG_QUALITY_PERCENT / 100f
                        }
                    }
                    writer.write(null, IIOImage(rgbImage, null, null), writeParam)
                    imageOut.flush()
                } finally {
                    runCatching { imageOut.close() }
                    writer.dispose()
                }
                byteOut.toByteArray()
            }.getOrNull() ?: return@withContext null

            if (compressedBytes.isEmpty()) return@withContext null

            val sandboxDir = resolveSandboxDir() ?: return@withContext null
            val safeKey = sanitizeReceiptKey(receiptKey)
            val destFile = File(sandboxDir, "$safeKey.jpg")
            if (destFile.canonicalFile.parentFile != sandboxDir.canonicalFile) {
                return@withContext null
            }

            val tempFile = File(sandboxDir, "$safeKey.jpg.tmp")
            synchronized(ioLock) {
                try {
                    FileOutputStream(tempFile).use { fos ->
                        fos.write(compressedBytes)
                        fos.flush()
                        fos.fd.sync()
                    }
                    try {
                        Files.move(
                            tempFile.toPath(),
                            destFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE
                        )
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(
                            tempFile.toPath(),
                            destFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                } catch (_: Throwable) {
                    return@withContext null
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            }

            destFile.absolutePath
        }

    private fun downscaleBufferedImage(srcImage: BufferedImage, targetW: Int, targetH: Int): BufferedImage {
        val scaled = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB)
        val g2d = scaled.createGraphics()
        try {
            g2d.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            )
            g2d.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
            )
            g2d.drawImage(srcImage, 0, 0, targetW, targetH, null)
        } finally {
            g2d.dispose()
        }
        return scaled
    }

    actual suspend fun loadReceiptThumbnail(fileUri: String, maxDimensionPx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (fileUri.isBlank()) return@withContext null
            val targetMax = maxDimensionPx.coerceAtLeast(1)
            val file = File(fileUri)
            if (!file.exists() || !file.isFile || file.length() <= 0L) {
                return@withContext null
            }

            runCatching {
                val srcImage = ImageIO.read(file)
                if (srcImage != null && srcImage.width > 0 && srcImage.height > 0) {
                    val maxEdge = maxOf(srcImage.width, srcImage.height)
                    val outputImage = if (maxEdge > targetMax) {
                        val scale = targetMax.toDouble() / maxEdge.toDouble()
                        val targetW = (srcImage.width * scale).roundToInt().coerceAtLeast(1)
                        val targetH = (srcImage.height * scale).roundToInt().coerceAtLeast(1)
                        downscaleBufferedImage(srcImage, targetW, targetH)
                    } else {
                        srcImage
                    }
                    try {
                        outputImage.toComposeImageBitmap()
                    } catch (_: LinkageError) {
                        AwtBufferedImageBitmap(outputImage)
                    }
                } else {
                    if (file.length() > MAX_FALLBACK_FILE_BYTES) {
                        return@runCatching null
                    }
                    val decoded = file.readBytes().decodeToImageBitmap()
                    if (decoded.width <= 0 || decoded.height <= 0) {
                        return@runCatching null
                    }
                    val maxEdge = maxOf(decoded.width, decoded.height)
                    if (maxEdge <= targetMax) {
                        decoded
                    } else {
                        val totalPixels = decoded.width.toLong() * decoded.height.toLong()
                        if (totalPixels > MAX_FALLBACK_PIXELS) {
                            return@runCatching null
                        }
                        val scale = targetMax.toDouble() / maxEdge.toDouble()
                        val targetW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
                        val targetH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
                        val srcPixels = IntArray(totalPixels.toInt())
                        decoded.readPixels(srcPixels)
                        val srcBuffered = BufferedImage(decoded.width, decoded.height, BufferedImage.TYPE_INT_ARGB)
                        srcBuffered.setRGB(0, 0, decoded.width, decoded.height, srcPixels, 0, decoded.width)
                        val scaled = downscaleBufferedImage(srcBuffered, targetW, targetH)
                        try {
                            scaled.toComposeImageBitmap()
                        } catch (_: LinkageError) {
                            AwtBufferedImageBitmap(scaled)
                        }
                    }
                }
            }.getOrNull()
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

    private class AwtBufferedImageBitmap(
        private val image: BufferedImage
    ) : ImageBitmap {
        override val width: Int get() = image.width
        override val height: Int get() = image.height
        override val colorSpace: ColorSpace get() = ColorSpaces.Srgb
        override val hasAlpha: Boolean get() = image.colorModel.hasAlpha()
        override val config: ImageBitmapConfig get() = ImageBitmapConfig.Argb8888

        override fun prepareToDraw() = Unit

        override fun readPixels(
            buffer: IntArray,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int,
            bufferOffset: Int,
            stride: Int
        ) {
            val lastScanline = bufferOffset + (height - 1) * stride
            require(startX >= 0 && startY >= 0)
            require(width > 0 && startX + width <= this.width)
            require(height > 0 && startY + height <= this.height)
            require(abs(stride) >= width)
            require(bufferOffset >= 0 && bufferOffset + width <= buffer.size)
            require(lastScanline >= 0 && lastScanline + width <= buffer.size)
            image.getRGB(startX, startY, width, height, buffer, bufferOffset, stride)
        }
    }
}
