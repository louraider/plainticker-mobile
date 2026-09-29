package com.plainticker.mobile.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.plainticker.mobile.ui.resolve
import com.plainticker.mobile.ui.shareText
import com.plainticker.mobile.ui.swap.SwapState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The share image's road out of the app: the card is drawn off the main thread, written as a PNG
 * under `cache/share/`, handed out through this app's own [FileProvider] as a `content://` URI with
 * a one-off read grant, and sent through the system chooser together with the same text and link
 * the text-only share already sent ([shareText]).
 *
 * Nothing private rides along: the card carries only what [ShareCard] states (no device code, no
 * wallet key), the provider is not exported and serves that one cache folder alone, and the grant
 * reaches only the app the reader picks. Every failure (no space, a render that throws, a phone
 * whose chooser refuses a stream) falls back to the text alone, so a share is never lost to the
 * picture.
 */
object ShareImage {
    /** The folder under the cache the provider serves (`res/xml/share_paths.xml`). */
    const val DIR = "share"

    /** The provider's authority, `${applicationId}.share` in the manifest. */
    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    const val AUTHORITY_SUFFIX = ".share"

    const val MIME = "image/png"

    /**
     * Draws [card] and writes it, replacing any earlier card in the folder. The file name carries
     * the time, because a receiving app may cache a preview by URI and would otherwise show the
     * previous card.
     */
    fun write(context: Context, card: ShareCard): Uri {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "plainticker-${card.fileStem}-${System.currentTimeMillis()}.png")
        val bitmap = ShareCardRenderer.render(context, card) { it.resolve(context.resources) }
        try {
            file.outputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) }
        } finally {
            bitmap.recycle()
        }
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** `ACTION_SEND` with the image as `EXTRA_STREAM` and the text beside it, the read grant set twice. */
    fun intent(uri: Uri, text: String): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The chooser passes a grant on only for a URI in the ClipData, so the stream is set there too.
        send.clipData = ClipData.newRawUri(null, uri)
        return Intent.createChooser(send, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/** Shares [card] with [text]; the text alone when the picture cannot be made. Never throws. */
suspend fun Context.shareCard(card: ShareCard, text: String) {
    val uri = try {
        withContext(Dispatchers.Default) { ShareImage.write(this@shareCard, card) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        null
    } catch (outOfMemory: OutOfMemoryError) {
        null
    }
    if (uri == null) {
        shareText(text)
        return
    }
    val started = runCatching { startActivity(ShareImage.intent(uri, text)) }.isSuccess
    if (!started) shareText(text)
}

/** [shareCard] bound to the composition's own context and scope, for an `onShare` callback. */
@Composable
fun rememberShareCard(): (ShareCard, String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { { card: ShareCard, text: String -> scope.launch { context.shareCard(card, text) } } }
}

/**
 * The swap receipt's Share ([com.plainticker.mobile.ui.swap.SwapActions.onShare]): the landed
 * swap's card and text, read from [state] at the tap. A swap into a token rides with its card; a
 * swap back to USDC sends the text alone. Anything but a landing does nothing.
 */
@Composable
fun rememberSwapShare(state: SwapState): () -> Unit {
    val context = LocalContext.current
    val share = rememberShareCard()
    val current = androidx.compose.runtime.rememberUpdatedState(state)
    return remember(context, share) {
        {
            (current.value as? SwapState.Landed)?.let { landed ->
                val text = landed.shareText().resolve(context.resources)
                val card = landed.shareCard()
                if (card != null) share(card, text) else context.shareText(text)
            }
        }
    }
}
