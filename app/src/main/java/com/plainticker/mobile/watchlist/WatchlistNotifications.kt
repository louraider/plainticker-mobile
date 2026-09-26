package com.plainticker.mobile.watchlist

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.plainticker.mobile.MainActivity
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.home.HomeTab

/**
 * The one notification this app sends: the daily digest, on one channel, with the monochrome brand
 * glyph, opening the Watchlist.
 *
 * Three things it deliberately does not do. It does not ask for anything: the permission is
 * requested once, at the moment the first ticker is watched, and this class only ever reads the
 * answer, the app-wide switch and this channel. It does not create its channel until it has something to post, so a build that is
 * installed and never used leaves no channel behind in the system settings. And it never throws:
 * a refused permission, a blocked channel or a system that declines the post all mean the digest
 * stays on the screen, which it does anyway, and the worker finishes normally.
 *
 * The text this class ever receives is [com.plainticker.mobile.watchlist.Digest.headline]'s own
 * shorter reading, one to two short sentences (task digest-stickiness), in a big-text style so a
 * two-line reading still shows whole in the shade; the screen under You draws the fuller paragraph
 * instead, every clause the notification kept to one. The title is the heading the screen draws
 * above the Panel, so a person who taps through recognizes where they have landed.
 */
class WatchlistNotifications(context: Context) : DigestNotifier {

    private val app: Context = context.applicationContext

    /**
     * What this device will actually do with the next digest, which is three questions and not
     * one: the runtime permission, the app-wide switch, and this channel. A reader who long
     * presses the digest and turns off "Watchlist" leaves the first two untouched, and a screen
     * that only asked those two would go on promising a notification that can never arrive and
     * would hide the Enable action that is the way back. The channel does not exist until the
     * first post, so its absence is not a refusal.
     */
    override fun enabled(): Boolean {
        if (!granted()) return false
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannelCompat(CHANNEL_ID) ?: return true
        return channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    // Checked by [enabled] just above, which lint cannot see through.
    @SuppressLint("MissingPermission")
    override fun post(text: String) {
        if (!enabled()) return
        val manager = NotificationManagerCompat.from(app)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(app.getString(R.string.notification_channel_watchlist))
                .setDescription(app.getString(R.string.notification_channel_watchlist_description))
                .build(),
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_plainticker)
            .setContentTitle(app.getString(R.string.notification_digest_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openWatchlist())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        // The post itself can still be refused by the system; that costs the notification and
        // nothing else, least of all the worker that asked for it.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    /**
     * Opens the screen the notification is about, which is the whole of plan section 13 Pass 3's
     * rule for the return: a notification deep-links to exactly what it named.
     */
    private fun openWatchlist(): PendingIntent {
        val intent = Intent(app, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_TAB, HomeTab.WATCHLIST.ordinal)
        return PendingIntent.getActivity(
            app,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Below API 33 the permission does not exist and the channel alone decides. */
    private fun granted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "watchlist-digest"

        /** One id: a second digest replaces the first rather than stacking yesterday under it. */
        const val NOTIFICATION_ID = 1
    }
}
