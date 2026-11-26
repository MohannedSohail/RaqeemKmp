package org.mohtech.app.feature.receipts

import androidx.compose.ui.graphics.ImageBitmap

internal const val RECEIPT_MAX_DIMENSION_PX: Int = 1280
internal const val RECEIPT_JPEG_QUALITY_PERCENT: Int = 75
private val unsafeReceiptKeyRegex = Regex("[^A-Za-z0-9_-]")
private val windowsReservedDeviceNames = setOf(
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
)

private fun fnv1a32Hex(input: String): String {
    var hash = 0x811c9dc5u
    val bytes = input.encodeToByteArray()
    for (b in bytes) {
        hash = hash xor (b.toUInt() and 0xFFu)
        hash *= 0x01000193u
    }
    return hash.toString(16).padStart(8, '0')
}

/**
 * Sanitizes a caller-supplied receipt key into a safe, deterministic single-segment filename stem
 * (strips path traversal sequences, separators, and unsafe characters, preserves uniqueness for
 * non-ASCII/Arabic keys via a deterministic FNV-1a suffix, neutralizes Windows reserved device
 * names, and caps stems at 80 chars).
 */
internal fun sanitizeReceiptKey(receiptKey: String): String {
    val trimmed = receiptKey.trim()
    val hasNonAscii = trimmed.any { it.code > 127 }
    val base = trimmed
        .replace(unsafeReceiptKeyRegex, "_")
        .trim('_', '-')

    val stem = if (hasNonAscii) {
        val hashHex = fnv1a32Hex(trimmed)
        val prefix = base.take(70).trim('_', '-')
        if (prefix.isEmpty() || prefix.none { it.isLetterOrDigit() }) {
            "receipt_$hashHex"
        } else {
            "${prefix}_$hashHex"
        }
    } else {
        val ascii = base.take(80).trim('_', '-')
        if (ascii.isEmpty() || ascii.none { it.isLetterOrDigit() }) {
            "receipt"
        } else {
            ascii
        }
    }

    return if (stem.uppercase() in windowsReservedDeviceNames) {
        "r_$stem"
    } else {
        stem
    }
}

/**
 * 100% offline, platform-abstracted compressed receipt storage and thumbnail loader.
 */
expect object PlatformReceiptStorage {
    fun pickAndStoreCompressedReceipt(receiptKey: String, onResult: (String?) -> Unit)
    suspend fun pickAndStoreCompressedReceipt(receiptKey: String): String?
    suspend fun compressAndStoreReceiptBytes(receiptKey: String, rawBytes: ByteArray): String?
    suspend fun loadReceiptThumbnail(fileUri: String, maxDimensionPx: Int = 512): ImageBitmap?
    fun receiptExists(fileUri: String): Boolean
    fun deleteReceipt(fileUri: String): Boolean
}
