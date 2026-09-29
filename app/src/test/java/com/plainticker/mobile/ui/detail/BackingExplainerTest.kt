package com.plainticker.mobile.ui.detail

import com.plainticker.mobile.R
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.ui.words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "What backing and controls mean" (founder feedback 2026-09-29): the sheet explains every row the
 * grid can draw, by the row's own label, each with what it is and why it matters, and links the
 * same page on the web.
 */
class BackingExplainerTest {

    private fun render(id: Int) = ShippedCopy.render(words(id))

    @Test
    fun `every row the grid draws is explained under its own label`() {
        val titles = BackingTopics.map { render(it.title) }
        listOf(
            R.string.detail_fact_por, R.string.detail_fact_delegate, R.string.detail_fact_pausable,
            R.string.detail_fact_hook, R.string.detail_fact_split,
        ).forEach { label -> assertTrue("${render(label)} is not explained", render(label) in titles) }
        assertTrue("minted on chain is explained beside the circulating count", titles.any { render(R.string.detail_fact_supply) in it })
        assertEquals(7, BackingTopics.size)
    }

    @Test
    fun `each topic says what it is and why it matters, in sentences`() {
        BackingTopics.forEach { topic ->
            listOf(topic.what, topic.why).map(::render).forEach { text ->
                assertTrue("\"$text\" is not a sentence", text.length > 40 && text.endsWith("."))
            }
        }
        assertEquals("What it is", render(R.string.backing_explain_what))
        assertEquals("Why it matters to you", render(R.string.backing_explain_why))
    }

    @Test
    fun `the permanent delegate says who can do what, and why tokenized stocks carry it`() {
        val what = render(R.string.backing_explain_delegate_what)
        listOf("move or burn tokens in any wallet", "without the holder signing", "legal duties").forEach {
            assertTrue("the delegate topic does not say \"$it\"", it in what)
        }
    }

    @Test
    fun `read more opens the web page of the same words`() {
        assertEquals("Read more on plainticker.com", render(R.string.backing_explain_read_more))
        assertEquals("https://www.plainticker.com/en/learn/backing-and-controls", render(R.string.backing_explain_url))
    }
}
