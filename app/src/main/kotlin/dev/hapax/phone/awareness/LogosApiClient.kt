package dev.hapax.phone.awareness

import android.util.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class LogosApiClient(private val baseUrl: String = DEFAULT_BASE_URL) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun fetchAwareness(): AwarenessFetchResult {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/awareness")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val stale = response.header("X-Awareness-State-Stale") == "true"
                if (body.isBlank()) {
                    return AwarenessFetchResult(summary = null, stale = true)
                }
                val summary = try {
                    json.decodeFromString<AwarenessSummary>(body)
                } catch (e: SerializationException) {
                    Log.w(TAG, "Awareness payload decode failed: ${e.message}")
                    null
                }
                AwarenessFetchResult(
                    summary = summary,
                    stale = stale || !response.isSuccessful || summary == null,
                )
            }
        } catch (e: IOException) {
            Log.w(TAG, "Awareness fetch failed: ${e.message}")
            AwarenessFetchResult(summary = null, stale = true)
        }
    }

    data class AwarenessFetchResult(
        val summary: AwarenessSummary?,
        val stale: Boolean,
    )

    companion object {
        private const val TAG = "LogosApiClient"
        private const val DEFAULT_BASE_URL = "http://100.117.1.83:8051/api"
    }
}
