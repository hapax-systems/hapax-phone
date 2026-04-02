package dev.hapax.phone.network

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.hapax.phone.data.HealthSummaryPayload
import dev.hapax.phone.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Transports health summary data to the hapax workstation.
 *
 * URL resolution order:
 * 1. Manual IP override from DataStore
 * 2. Cached mDNS-discovered URL (valid for 5 minutes)
 * 3. Fresh mDNS discovery (5s timeout)
 * 4. Hardcoded fallback 192.168.68.114:8042
 */
class HapaxTransport(private val context: Context) {

    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { encodeDefaults = true }

    private var backoffMs: Long = INITIAL_BACKOFF_MS
    private val consecutiveFailures = AtomicInteger(0)
    private val cachedUrl = AtomicReference<String?>(null)
    private var cacheTimestamp: Long = 0L

    private val mdnsDiscovery by lazy { MdnsDiscovery(context) }

    enum class Status { CONNECTED, DISCONNECTED }

    @Volatile
    var status: Status = Status.DISCONNECTED
        private set

    @Volatile
    var resolvedUrl: String? = null
        private set

    /**
     * POST the health summary payload to the server.
     * Returns true on success, false on failure.
     */
    fun send(payload: HealthSummaryPayload): Boolean {
        val baseUrl = resolveUrl() ?: run {
            Log.w(TAG, "No server URL available")
            return false
        }

        val body = json.encodeToString(payload)
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$baseUrl/phone/health-summary")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Posted health summary to $baseUrl")
                    backoffMs = INITIAL_BACKOFF_MS
                    consecutiveFailures.set(0)
                    status = Status.CONNECTED
                    resolvedUrl = baseUrl
                    true
                } else {
                    Log.w(TAG, "Server returned ${response.code}")
                    onFailure()
                    false
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Send failed: ${e.message}")
            onFailure()
            false
        }
    }

    private fun resolveUrl(): String? {
        // 1. Manual override
        val manualIp = getManualIp()
        if (!manualIp.isNullOrBlank()) {
            val url = "http://$manualIp"
            resolvedUrl = url
            return url
        }

        // 2. Cached mDNS
        val cached = cachedUrl.get()
        if (cached != null && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
            return cached
        }

        // 3. Fresh mDNS
        val discovered = mdnsDiscovery.discover(timeout = 5000)
        if (discovered != null) {
            val url = "http://$discovered"
            cachedUrl.set(url)
            cacheTimestamp = System.currentTimeMillis()
            resolvedUrl = url
            Log.i(TAG, "mDNS discovered server: $url")
            return url
        }

        // 4. Hardcoded fallback — Tailscale IP (stable across all networks)
        val fallback = "http://100.117.1.83:8051"
        resolvedUrl = fallback
        Log.i(TAG, "Using hardcoded fallback: $fallback")
        return fallback
    }

    private fun getManualIp(): String? {
        return try {
            runBlocking {
                context.dataStore.data.map { prefs ->
                    prefs[stringPreferencesKey("manual_ip")]
                }.first()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read manual_ip: ${e.message}")
            null
        }
    }

    fun invalidateCache() {
        cachedUrl.set(null)
        cacheTimestamp = 0L
    }

    private fun onFailure() {
        val failures = consecutiveFailures.incrementAndGet()
        status = Status.DISCONNECTED
        applyBackoff()
    }

    private fun applyBackoff() {
        try {
            Thread.sleep(backoffMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
    }

    companion object {
        private const val TAG = "HapaxTransport"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val INITIAL_BACKOFF_MS = 5_000L
        private const val MAX_BACKOFF_MS = 120_000L
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
