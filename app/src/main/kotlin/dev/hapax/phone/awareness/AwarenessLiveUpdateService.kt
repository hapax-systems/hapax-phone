package dev.hapax.phone.awareness

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class AwarenessLiveUpdateService : Service() {
    private lateinit var handler: Handler
    private lateinit var client: LogosApiClient

    private val refreshRunnable = object : Runnable {
        override fun run() {
            Thread {
                refreshNotification()
            }.start()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        client = LogosApiClient()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(AwarenessTop5Picker.pickTop5(summary = null, stale = true)),
        )
        Thread {
            refreshNotification()
        }.start()
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
        Log.i(TAG, "AwarenessLiveUpdateService started")
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshNotification() {
        val result = client.fetchAwareness()
        val payload = AwarenessTop5Picker.pickTop5(result.summary, stale = result.stale)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(payload))
    }

    private fun buildNotification(payload: Top5Payload): Notification {
        val icon = if (payload.stale) {
            android.R.drawable.presence_invisible
        } else {
            android.R.drawable.presence_online
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(payload.title)
            .setContentText(payload.subtitle)
            .setStyle(Notification.BigTextStyle().bigText(payload.expanded))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setColorized(false)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hapax Awareness Live Update",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Read-only Hapax awareness summary for glance surfaces"
            setSound(null, AudioAttributes.Builder().build())
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "AwarenessLiveUpdate"
        private const val CHANNEL_ID = "hapax_awareness_live_update"
        private const val NOTIFICATION_ID = 3
        private const val REFRESH_INTERVAL_MS = 60_000L
    }
}
