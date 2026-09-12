package com.myapp.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * One piece of text on a screen. Either copy this app wrote, which lives in strings.xml and is
 * named by its id, or text the app did not write: a numeral [Fmt] produced, or a sentence the
 * server sent. Keeping the two apart is what lets a test assert the wording without a device, and
 * what stops a translator's string from being spelled in Kotlin.
 *
 * It began in the Detail model and now serves the swap sheet as well, which is why it lives one
 * package up: a screen's model decides which sentence is true, a composable only renders it.
 */
sealed interface Copy {
    /** Copy from strings.xml, with its arguments already formatted by [Fmt]. */
    data class Words(@StringRes val id: Int, val args: List<String> = emptyList()) : Copy

    /** A numeral, a ticker or a sentence from the payload. Never translated, never invented. */
    data class Raw(val text: String) : Copy
}

internal fun words(@StringRes id: Int, vararg args: String): Copy.Words = Copy.Words(id, args.toList())

internal fun raw(text: String): Copy.Raw = Copy.Raw(text)

/** The one place a [Copy] becomes a string: words come from strings.xml, raw text from the source. */
@Composable
internal fun Copy.text(): String = when (this) {
    is Copy.Words -> stringResource(id, *args.toTypedArray())
    is Copy.Raw -> text
}
