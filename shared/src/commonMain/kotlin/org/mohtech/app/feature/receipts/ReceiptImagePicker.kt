package org.mohtech.app.feature.receipts

/**
 * ReceiptPickerBridge:
 * Clean, 100% offline platform abstraction for picking a compressed receipt photo into sandbox storage.
 */
interface ReceiptPickerBridge {
    fun launchPicker(expenseId: String, onImagePicked: (String?) -> Unit)
}

object PlatformReceiptPicker {
    var delegate: ReceiptPickerBridge? = null

    fun pickReceipt(expenseId: String, onResult: (String?) -> Unit) {
        val bridge = delegate
        if (bridge != null) {
            bridge.launchPicker(expenseId, onResult)
        } else {
            PlatformReceiptStorage.pickAndStoreCompressedReceipt(expenseId, onResult)
        }
    }
}
