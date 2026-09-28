package com.plainticker.mobile.ui.you

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fresh-device QA of 1.3.23: the promo field no longer rewrites its value as it is typed, it only
 * draws it in capitals. The drawn form must be exactly as long as the typed one, or the identity
 * offset mapping would put the cursor in the wrong place, or throw.
 */
class PromoFieldTest {

    @Test
    fun `the field draws capitals one for one, whatever was typed`() {
        assertEquals("PT-AAAA-BBBB-CCCC", uppercaseCode("pt-aaaa-bbbb-cccc"))
        assertEquals("PT AAAA", uppercaseCode("Pt aaaa"))
        listOf("", "strasse", "stra\u00DFe", "pt\u2013aaaa", "\u0131i").forEach { typed ->
            assertEquals("same length for \"$typed\"", typed.length, uppercaseCode(typed).length)
            val drawn = UppercaseCodeTransformation.filter(AnnotatedString(typed))
            assertEquals(typed.length, drawn.text.length)
            assertEquals(typed.length, drawn.offsetMapping.originalToTransformed(typed.length))
        }
    }
}
