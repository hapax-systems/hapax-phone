package dev.hapax.phone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import dev.hapax.phone.R
import dev.hapax.phone.network.HapaxTransport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request

/**
 * Lightweight foreground service streaming coarse phone context to the
 * workstation every 60 seconds.
 *
 * Payload: activity_type, activity_confidence, screen_on, ringer_mode,
 * battery_pct, charging, network_type.
 *
 * No GPS, no audio, no camera. Activity recognition uses Google Play
 * Services on-device ML (accelerometer only, ~1% battery/day).
 */
class ContextService : Service() {

    private lateinit var transport: HapaxTransport
    private lateinit var handler: Handler
    private val json = Json { encodeDefaults = true }

    // Activity state (updated by ActivityRecognitionReceiver)
    @Volatile
    var currentActivityType: String = "still"
        internal set

    @Volatile
    var currentActivityConfidence: Float = 0f
        internal set

    private val contextRunnable = object : Runnable {
        override fun run() {
            Thread {
                sendContext()
            }.start()
            handler.postDelayed(this, CONTEXT_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        transport = HapaxTransport(this)
        handler = Handler(Looper.getMainLooper())
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification("Context streaming active")
        startForeground(NOTIFICATION_ID, notification)

        handler.postDelayed(contextRunnable, CONTEXT_INTERVAL_MS)
        Log.i(TAG, "ContextService started — streaming every ${CONTEXT_INTERVAL_MS / 1000}s")

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(contextRunnable)
        instance = null
        Log.i(TAG, "ContextService stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendContext() {
        val baseUrl = transport.resolvedUrl ?: run {
            // Try to resolve once
            transport.send(dev.hapax.phone.data.HealthSummaryPayload(
                deviceId = "pixel10", date = ""
            ))
            transport.resolvedUrl
        } ?: return

        val payload = PhoneContextPayload(
            deviceId = "pixel10",
            ts = System.currentTimeMillis(),
            activityType = currentActivityType,
            activityConfidence = currentActivityConfidence,
            screenOn = isScreenOn(),
            ringerMode = getRingerMode(),
            batteryPct = getBatteryPercentage(),
            charging = isCharging(),
            networkType = getNetworkType(),
        )

        val body = json.encodeToString(payload)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/phone/context")
            .post(body)
            .build()

        try {
            transport.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Context sent: activity=${payload.activityType}")
                } else {
                    Log.w(TAG, "Context POST returned ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Context send failed: ${e.message}")
        }
    }

    private fun isScreenOn(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private fun getRingerMode(): String {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "normal"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            AudioManager.RINGER_MODE_SILENT -> "silent"
            else -> "normal"
        }
    }

    private fun getBatteryPercentage(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isCharging(): Boolean {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }

    private fun getNetworkType(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "none"
        return when {
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "none"
        }
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Hapax Context")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hapax Context Streaming",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ContextService"
        private const val CHANNEL_ID = "hapax_context_service"
        private const val NOTIFICATION_ID = 2
        private const val CONTEXT_INTERVAL_MS = 60_000L

        // Singleton for ActivityRecognitionReceiver to update
        @Volatile
        var instance: ContextService? = null
            private set
    }
}

@Serializable
data class PhoneContextPayload(
    @SerialName("device_id") val deviceId: String,
    val ts: Long,
    @SerialName("activity_type") val activityType: String = "still",
    @SerialName("activity_confidence") val activityConfidence: Float = 0f,
    @SerialName("screen_on") val screenOn: Boolean = false,
    @SerialName("ringer_mode") val ringerMode: String = "normal",
    @SerialName("battery_pct") val batteryPct: Int = 100,
    val charging: Boolean = false,
    @SerialName("network_type") val networkType: String = "none",
)
