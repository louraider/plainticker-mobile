package com.plainticker.mobile.ui.you

import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.lint.KotlinScan
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.FakeDigestNotifier
import com.plainticker.mobile.watchlist.InMemoryDigestStore
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The digest screen under You (Today direction A): the digest that left Today's first viewport,
 * its stamp in the reader's own time rather than a mono UTC line, and the notifications setting
 * beside the thing it delivers.
 */
class DigestScreenTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val kyiv = ZoneId.of("Europe/Kyiv")

    private fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

    private val digest = DigestRecord(
        text = "1 stock watched. METAx moved from -0.13% to +0.63% against the NYSE close.",
        producedAtMillis = utc("2026-09-24T06:49:00Z"),
        lastCheckedAtMillis = utc("2026-09-24T06:49:00Z"),
    )

    @Test
    fun `the stamp is the reader's own time, today or a weekday, never UTC`() {
        assertEquals("Today at 09:49", ShippedCopy.render(digestStamp(digest, utc("2026-09-24T19:00:00Z"), kyiv)!!))
        assertEquals("Thursday at 09:49", ShippedCopy.render(digestStamp(digest, utc("2026-09-26T09:00:00Z"), kyiv)!!))
        assertFalse(ShippedCopy.render(digestStamp(digest, utc("2026-09-24T19:00:00Z"), kyiv)!!).contains("UTC"))
    }

    @Test
    fun `before the first digest there is no stamp, and the body says when the first lands`() {
        assertNull(digestStamp(DigestRecord.NONE, utc("2026-09-24T19:00:00Z"), kyiv))
        assertEquals(
            "No digest yet. The first one lands about twelve hours after you watch a stock.",
            ShippedCopy.render(digestBody(DigestRecord.NONE)),
        )
    }

    @Test
    fun `the body is the digest exactly as the notification carried it`() {
        assertEquals(digest.text, ShippedCopy.render(digestBody(digest)))
    }

    @Test
    fun `the screen follows the store and re-reads the notification setting on resume`() = runTest {
        val store = InMemoryDigestStore()
        val notifier = FakeDigestNotifier(on = false)
        var now = utc("2026-09-24T19:00:00Z")
        val vm = DigestViewModel(store, notifier, Clock { now })
        advanceUntilIdle()
        assertFalse(vm.state.value.notificationsOn)

        store.save(digest)
        advanceUntilIdle()
        assertEquals(digest, vm.state.value.record)

        notifier.on = true
        now += 60_000L
        vm.resumed()
        assertTrue("back from the settings, the line says what the device will do now", vm.state.value.notificationsOn)
        assertEquals(now, vm.state.value.nowMillis)
    }

    @Test
    fun `the setting lives here, with Enable only while notifications are off`() {
        val module = listOf(".", "app").map(::File).first { File(it, "src/main/AndroidManifest.xml").isFile }
        val source = KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/you/DigestScreen.kt").readText()).code
        assertTrue("if (!state.notificationsOn) {" in source)
        assertTrue("R.string.action_enable" in source)
        assertTrue("the delivery sentence is the flexible sibling of a short fixed action", "modifier = Modifier.weight(1f)" in source)
    }
}
