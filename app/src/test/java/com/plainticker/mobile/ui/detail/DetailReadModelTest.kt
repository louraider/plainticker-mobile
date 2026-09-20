package com.plainticker.mobile.ui.detail

import com.plainticker.mobile.data.plainticker.NarrativeRead
import com.plainticker.mobile.data.plainticker.NextStepsRead
import com.plainticker.mobile.data.plainticker.TickerReadResponse
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.ShippedCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "The read" and "What to check next" (task A6): the honest short form for every reader, the full
 * text once entitled, and neither block for the four states that are not [ReadState.Ready].
 *
 * Three of those four are calm and silent: a server not yet answered, one that does not cover
 * this ticker, and one that has not turned the route on are none of them a fault, so
 * [DetailUiState.readNotice] stays null right alongside [DetailUiState.readNarrative] and
 * [DetailUiState.nextStepsBlock]. [ReadState.Failed] is the one that is a fault, and it is the
 * only one of the four where [DetailUiState.readNotice] is not null: this app's original bug was
 * that this line did not exist and a failed read looked exactly like the three calm states beside
 * it, from the screen alone.
 */
class DetailReadModelTest {

    private fun state(read: ReadState) = DetailUiState(ticker = "AAPL", read = read)

    // ---- The three calm states that draw nothing, not even a notice ---------------------------

    @Test
    fun `still loading draws neither block and no notice, never a skeleton either`() {
        val s = state(ReadState.Loading)
        assertNull(s.readNarrative)
        assertNull(s.nextStepsBlock)
        assertNull(s.readNotice)
    }

    @Test
    fun `not served draws neither block and no notice`() {
        val s = state(ReadState.NotServed)
        assertNull(s.readNarrative)
        assertNull(s.nextStepsBlock)
        assertNull(s.readNotice)
    }

    @Test
    fun `disabled reads as an honest unavailable state, drawing neither block and no notice`() {
        val s = state(ReadState.Disabled)
        assertNull(s.readNarrative)
        assertNull(s.nextStepsBlock)
        assertNull(s.readNotice)
    }

    // ---- The one state that is a fault, and says so ---------------------------------------------

    @Test
    fun `a call that failed draws neither block, but does draw a notice`() {
        val s = state(ReadState.Failed)
        assertNull(s.readNarrative)
        assertNull(s.nextStepsBlock)
        assertEquals("The read is unavailable", ShippedCopy.render(s.readNotice!!))
    }

    // ---- The peek: the server's own honest short form, never a bare door ---------------------

    @Test
    fun `an uncoded reader sees the excerpt, never the full text`() {
        val payload = TickerReadResponse(
            ticker = "AAPL",
            pro = false,
            narrative = NarrativeRead(excerptEn = "First sentence.", fullEn = null),
            nextSteps = NextStepsRead(titlesEn = listOf("Check margins", "Review filing"), stepsEn = null),
        )
        val s = state(ReadState.Ready(payload))

        val narrative = s.readNarrative!!
        assertTrue("the peek is never a locked door", !narrative.full)
        assertEquals("First sentence.", (narrative.text as Copy.Raw).text)

        val steps = s.nextStepsBlock!!
        assertTrue(!steps.full)
        assertEquals(listOf("Check margins", "Review filing"), steps.items.map { it.title })
        assertTrue("no step detail without entitlement", steps.items.all { it.detail == null })
    }

    /** `pro: false` from the server settles it even if a stray `fullEn` were ever present. */
    @Test
    fun `pro false never draws the full text, whatever the payload otherwise carries`() {
        val payload = TickerReadResponse(
            ticker = "AAPL",
            pro = false,
            narrative = NarrativeRead(excerptEn = "First sentence.", fullEn = "Should never be drawn."),
        )
        val narrative = state(ReadState.Ready(payload)).readNarrative!!
        assertTrue(!narrative.full)
        assertEquals("First sentence.", (narrative.text as Copy.Raw).text)
    }

    // ---- The full content, for an entitled wallet ---------------------------------------------

    @Test
    fun `an entitled wallet sees the full narrative and the full steps`() {
        val payload = TickerReadResponse(
            ticker = "AAPL",
            pro = true,
            narrative = NarrativeRead(excerptEn = "First sentence.", fullEn = "First sentence. Second one."),
            nextSteps = NextStepsRead(
                titlesEn = listOf("Check margins", "Review filing"),
                stepsEn = listOf("Margins rose again.", "The filing added detail."),
            ),
        )
        val s = state(ReadState.Ready(payload))

        val narrative = s.readNarrative!!
        assertTrue(narrative.full)
        assertEquals("First sentence. Second one.", (narrative.text as Copy.Raw).text)

        val steps = s.nextStepsBlock!!
        assertTrue(steps.full)
        assertEquals(listOf("Margins rose again.", "The filing added detail."), steps.items.map { it.detail })
    }

    /** `pro: true` but no full field sent yet: the excerpt still stands rather than a blank block. */
    @Test
    fun `pro true with no full field yet falls back to the excerpt, not to nothing`() {
        val payload = TickerReadResponse(
            ticker = "AAPL",
            pro = true,
            narrative = NarrativeRead(excerptEn = "First sentence.", fullEn = null),
        )
        val narrative = state(ReadState.Ready(payload)).readNarrative!!
        assertTrue("no full text landed yet, so this is still the peek", !narrative.full)
        assertEquals("First sentence.", (narrative.text as Copy.Raw).text)
    }

    @Test
    fun `a step past the titles the server sent carries no detail, never an index error`() {
        val payload = TickerReadResponse(
            ticker = "AAPL",
            pro = true,
            nextSteps = NextStepsRead(
                titlesEn = listOf("Check margins", "Review filing", "Watch guidance"),
                stepsEn = listOf("Margins rose again."),
            ),
        )
        val steps = state(ReadState.Ready(payload)).nextStepsBlock!!
        assertEquals(listOf("Margins rose again.", null, null), steps.items.map { it.detail })
    }

    @Test
    fun `no narrative or next steps on the payload draws neither block, and still no notice`() {
        val payload = TickerReadResponse(ticker = "AAPL", pro = false, narrative = null, nextSteps = null)
        val s = state(ReadState.Ready(payload))
        assertNull(s.readNarrative)
        assertNull(s.nextStepsBlock)
        // A 200 the server sent on purpose with nothing in it is not the same fault as a 200 this
        // app could not read: only ReadState.Failed draws a notice, never a Ready payload that is
        // simply empty.
        assertNull(s.readNotice)
    }

    @Test
    fun `empty titles draws nothing, rather than an empty heading`() {
        val payload = TickerReadResponse(ticker = "AAPL", nextSteps = NextStepsRead(titlesEn = emptyList()))
        assertNull(state(ReadState.Ready(payload)).nextStepsBlock)
    }
}
