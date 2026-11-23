package org.mohtech.app.feature.receipts

/**
 * ReceiptPickerBridge:
 * Clean, 100% offline platform abstraction for picking receipt photo into filesDir/receipts/{expenseId}.jpg.
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
            onResult(null)
        }
    }
}

