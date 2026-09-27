package com.plainticker.mobile.ui

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The one way this app shares anything: plain text through the system chooser (`ACTION_SEND`,
 * `text/plain`). No file, no image, no App Link: the receiving app gets a sentence and a public
 * URL, and nothing on this device (no device code, no wallet key) ever rides along.
 *
 * Never throws: a device with nothing that accepts text (rare, but real on a stripped image)
 * costs the share and nothing else.
 */
fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(chooser) }
}

/** [shareText] bound to the composition's own context, for an `onShare` callback. */
@Composable
fun rememberShareText(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text: String -> context.shareText(text) } }
}

/**
 * A [Copy] resolved outside composition, for text assembled in a click handler (a share line is
 * built at the tap, from the state of that moment). Same resources [text] reads.
 */
internal fun Copy.resolve(resources: Resources): String = when (this) {
    is Copy.Words -> resources.getString(id, *args.toTypedArray())
    is Copy.Counted -> resources.getQuantityString(id, quantity, *args.toTypedArray())
    is Copy.Raw -> text
}
