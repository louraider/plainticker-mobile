package com.plainticker.mobile.ui.you

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import com.plainticker.mobile.ui.components.TopScrim
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.R
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.Panel
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.TopBar
import com.plainticker.mobile.ui.components.defaultAmberColors
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.watchlist.digestFooter
import com.plainticker.mobile.ui.words
import com.plainticker.mobile.watchlist.DigestNotifier
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.DigestStore
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The daily digest, under You (Today direction A, 2026-09-24): what used to sit on Today as a
 * bordered paragraph, a mono UTC stamp and two footer lines now lives here, one tap from Today's
 * "Read it" and from You's own link. The digest store keeps the last digest, so this is that
 * digest, when it landed in the reader's own time, when the check last ran, and whether the next
 * one will notify, with Enable when it will not. That line is where the notification setting
 * lives now: next to the thing it delivers, not on the home screen before anything was watched.
 */
data class DigestUiState(
    val record: DigestRecord = DigestRecord.NONE,
    val notificationsOn: Boolean = false,
    val nowMillis: Long = 0L,
)

class DigestViewModel(
    private val digests: DigestStore,
    private val notifier: DigestNotifier,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DigestUiState(record = digests.record.value, notificationsOn = notifier.enabled(), nowMillis = clock.nowMillis()),
    )
    val state: StateFlow<DigestUiState> = _state.asStateFlow()

    init {
        // The worker writes the digest from another coroutine in this process; the screen follows it.
        viewModelScope.launch { digests.record.collect { record -> _state.update { it.copy(record = record) } } }
    }

    /** Back from the system settings, or back at all: re-read the setting and move the clock. */
    fun resumed() {
        _state.update { it.copy(notificationsOn = notifier.enabled(), nowMillis = clock.nowMillis()) }
    }
}

/** When the digest landed, in the reader's own time: "Today at 09:49", "Monday at 09:49". Null before the first. */
fun digestStamp(record: DigestRecord, nowMillis: Long, zone: ZoneId): Copy? {
    val at = record.producedAtMillis ?: return null
    if (record.text == null) return null
    val time = Fmt.clock(at, zone)
    return if (Fmt.daysAhead(at, nowMillis, zone) == 0L) {
        words(R.string.digest_produced_today, time)
    } else {
        words(R.string.digest_produced_day, Fmt.weekday(at, zone), time)
    }
}

/** The digest itself, exactly as the notification carried it, or the sentence that says when the first lands. */
fun digestBody(record: DigestRecord): Copy = record.text?.let { raw(it) } ?: words(R.string.watchlist_digest_none)

@Composable
fun DigestScreen(viewModel: DigestViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val colors = defaultAmberColors()

    LifecycleResumeEffect(viewModel) {
        viewModel.resumed()
        onPauseOrDispose { }
    }

    val footer = digestFooter(record = state.record, notificationsOn = state.notificationsOn, nowMillis = state.nowMillis)
    // The band the clock sits in stays the page's own ground while the digest scrolls under it,
    // the same scrim the five home destinations and Detail draw (device QA of 1.3.17).
    Box(modifier.fillMaxSize().background(colors.surfaceGround)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = WindowInsets.systemBars.asPaddingValues(),
        ) {
            item(key = "header") { TopBar(insets = WindowInsets(0), colors = colors) }
            item(key = "heading") {
                AmberSectionHead(
                    title = stringResource(R.string.watchlist_heading_digest),
                    lede = stringResource(R.string.digest_screen_lede),
                    colors = colors,
                )
            }
            item(key = "panel") {
                // The page's own 16dp gutter, once: the panel's inset is the gutter, and the heading
                // above sits on it (device QA of 1.3.17: 16dp here plus the panel's own 20dp drew the
                // card 36dp in, well inside the heading's edge).
                Panel(modifier = Modifier.padding(vertical = 8.dp), inset = 16.dp, colors = colors) {
                    digestStamp(state.record, state.nowMillis, zone)?.let {
                        Text(text = it.text(), style = AmberType.meta, color = colors.textTertiary(AmberSurface.RAISED))
                    }
                    Text(text = digestBody(state.record).text(), style = AmberType.body, color = colors.textPrimary)
                }
            }
            item(key = "delivery") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Weighted and wrapping; Enable is short and fixed, so it can never starve it.
                        Text(
                            text = footer.delivery.text(),
                            style = AmberType.context,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        if (!state.notificationsOn) {
                            TextAction(
                                label = stringResource(R.string.action_enable),
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                    )
                                },
                                color = colors.actionText,
                            )
                        }
                    }
                    footer.checked?.let {
                        Text(text = it.text(), style = AmberType.context, color = colors.textTertiary(AmberSurface.GROUND))
                    }
                }
            }
        }
        TopScrim(Modifier.align(Alignment.TopCenter), groundColor = colors.surfaceGround)
    }
}
