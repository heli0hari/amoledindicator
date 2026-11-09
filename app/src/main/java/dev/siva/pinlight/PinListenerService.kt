package dev.siva.pinlight

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

// Watches pending notifications and pushes the newest apps (up to the user's
// maximum) to System UI.
class PinListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val sendRunnable = Runnable { sendUpdate() }
    private var connected = false

    private val requestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // System UI (re)started or settings changed: resend everything.
            PinSettings.push(this@PinListenerService)
            scheduleSend()
        }
    }

    override fun onListenerConnected() {
        connected = true
        registerReceiver(requestReceiver, IntentFilter(Protocol.ACTION_REQUEST), Context.RECEIVER_EXPORTED)
        PinSettings.push(this)
        scheduleSend()
    }

    override fun onListenerDisconnected() {
        connected = false
        try {
            unregisterReceiver(requestReceiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered.
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: NotificationListenerService.RankingMap) = scheduleSend()

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: NotificationListenerService.RankingMap) = scheduleSend()

    override fun onNotificationRankingUpdate(rankingMap: NotificationListenerService.RankingMap) = scheduleSend()

    // Bursts of notifications (group + summary) arrive together; send once.
    private fun scheduleSend() {
        handler.removeCallbacks(sendRunnable)
        handler.postDelayed(sendRunnable, 250)
    }

    private fun sendUpdate() {
        if (!connected) return
        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            null
        } ?: return

        val ranking = currentRanking
        val newestPerApp = LinkedHashMap<String, StatusBarNotification>()
        for (sbn in active.sortedByDescending { it.postTime }) {
            if (!isPending(sbn, ranking)) continue
            if (!newestPerApp.containsKey(sbn.packageName)) newestPerApp[sbn.packageName] = sbn
        }

        val maxPins = PinSettings.load(this).maxPins.coerceIn(1, Protocol.MAX_PINS)
        val packages = ArrayList<String>()
        val colors = ArrayList<Int>()
        val icons = ArrayList<Bitmap>()
        for (sbn in newestPerApp.values) {
            if (packages.size >= maxPins) break
            val mask = IconPacker.mask(this, sbn) ?: continue
            packages.add(sbn.packageName)
            colors.add(IconPacker.color(this, sbn))
            icons.add(mask)
        }

        val intent = Intent(Protocol.ACTION_UPDATE)
            .setPackage(Protocol.SYSTEMUI)
            .putExtra(Protocol.EXTRA_PACKAGES, packages.toTypedArray())
            .putExtra(Protocol.EXTRA_COLORS, colors.toIntArray())
            .putParcelableArrayListExtra(Protocol.EXTRA_ICONS, icons)
        sendBroadcast(intent)
    }

    // LED rule: only alerting, dismissible notifications count.
    private fun isPending(sbn: StatusBarNotification, ranking: NotificationListenerService.RankingMap): Boolean {
        if (sbn.packageName == packageName) return false
        if (sbn.isOngoing || !sbn.isClearable) return false
        if (sbn.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false

        val r = NotificationListenerService.Ranking()
        if (ranking.getRanking(sbn.key, r)) {
            if (r.importance < NotificationManager.IMPORTANCE_DEFAULT) return false
            if (r.isSuspended) return false
        }
        return true
    }
}
