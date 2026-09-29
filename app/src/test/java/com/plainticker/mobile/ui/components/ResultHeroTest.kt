package com.plainticker.mobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared result hero's pure parts (2026-09-29): how the mark's entrance is split into its
 * ring, fill and check, and what TalkBack reads. The composition itself is pinned from source in
 * `SwapSheetTest`, the way the rest of the swap sheet is.
 */
class ResultHeroTest {

    @Test
    fun `a settled landing is a closed ring, a full disc and the whole check`() {
        assertEquals(MarkPhases(ring = 1f, fill = 1f, check = 1f), markPhases(ResultMarkTone.Landed, 1f))
    }

    @Test
    fun `the landing's entrance starts empty and draws the ring before the check`() {
        assertEquals(MarkPhases(0f, 0f, 0f), markPhases(ResultMarkTone.Landed, 0f))
        val early = markPhases(ResultMarkTone.Landed, 0.3f)
        assertTrue("the ring leads", early.ring > 0f)
        assertEquals("no check before the ring has closed", 0f, early.check, 0f)
        val ringClosed = markPhases(ResultMarkTone.Landed, RING_END)
        assertEquals(1f, ringClosed.ring, 0f)
        assertEquals("the check starts as the ring closes", 0f, ringClosed.check, 0f)
        val late = markPhases(ResultMarkTone.Landed, 0.8f)
        assertEquals("the disc is full before the check is", 1f, late.fill, 0f)
        assertTrue(late.check in 0.01f..0.99f)
    }

    @Test
    fun `every part only ever grows as the mark settles`() {
        ResultMarkTone.entries.forEach { tone ->
            val frames = (0..100).map { markPhases(tone, it / 100f) }
            frames.zipWithNext().forEach { (a, b) ->
                assertTrue("$tone ring shrinks", b.ring >= a.ring)
                assertTrue("$tone fill shrinks", b.fill >= a.fill)
                assertTrue("$tone check shrinks", b.check >= a.check)
            }
        }
    }

    @Test
    fun `only a landing fills or draws a check`() {
        listOf(ResultMarkTone.Failed, ResultMarkTone.Pending).forEach { tone ->
            assertEquals(MarkPhases(ring = 1f, fill = 0f, check = 0f), markPhases(tone, 1f))
        }
    }

    @Test
    fun `a spring's overshoot never draws past the settled mark`() {
        // The settle spring is no-bounce, but a progress past 1 must still read as settled.
        assertEquals(markPhases(ResultMarkTone.Landed, 1f), markPhases(ResultMarkTone.Landed, 1.2f))
        assertEquals(markPhases(ResultMarkTone.Landed, 0f), markPhases(ResultMarkTone.Landed, -0.1f))
    }

    @Test
    fun `talkback hears the headline and then each sentence, with one full stop between`() {
        assertEquals(
            "Your vote is on chain. Counted within about 20 minutes. You will see it under Your votes.",
            resultAnnouncement(
                "Your vote is on chain",
                listOf("Counted within about 20 minutes. You will see it under Your votes."),
            ),
        )
        assertEquals(
            "Swap landed. Received 0.013609 TSLAx. TSLAx is in your wallet now, and Portfolio shows it.",
            resultAnnouncement(
                "Swap landed",
                listOf("Received 0.013609 TSLAx", "TSLAx is in your wallet now, and Portfolio shows it."),
            ),
        )
        assertEquals("Vote not sent", resultAnnouncement("Vote not sent", listOf(" ")))
    }

    @Test
    fun `the mark is large enough to read as the moment, not a bullet`() {
        // The 40dp ring it replaces read as a list marker beside a 22sp line (2026-09-29).
        assertEquals(72f, ResultMarkSize.value, 0f)
        assertTrue(ResultMarkStroke.value >= 4f)
    }
}
