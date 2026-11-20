package com.cem.rock

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Build
import android.view.HapticFeedbackConstants
import java.security.MessageDigest

/** Reads any NFC tag while the activity is resumed and hands back a hashed tag UID. */
class TagReader(private val activity: Activity, private val onTag: (String) -> Unit) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val available get() = adapter != null
    val enabled get() = adapter?.isEnabled == true

    fun start() {
        adapter?.enableReaderMode(
            activity,
            { tag ->
                val id = hash(tag.id)
                activity.runOnUiThread {
                    haptic(activity)
                    onTag(id)
                }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null,
        )
    }

    fun stop() {
        runCatching { adapter?.disableReaderMode(activity) }
    }

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

fun haptic(activity: Activity) {
    val view = activity.window.decorView
    val c = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
    view.performHapticFeedback(c)
}
