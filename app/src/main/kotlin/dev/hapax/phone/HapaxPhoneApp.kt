package dev.hapax.phone

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.ActivityRecognition
import dev.hapax.phone.awareness.AwarenessLiveUpdateService
import dev.hapax.phone.service.ActivityRecognitionReceiver
import dev.hapax.phone.service.ContextService
import dev.hapax.phone.worker.HealthSyncWorker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class HapaxPhoneApp : Application() {

    override fun onCreate() {
        super.onCreate()
        schedulePeriodicSync()
        startContextStreaming()
        startAwarenessLiveUpdate()
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Calculate initial delay to next 7:00 AM
        val now = LocalDateTime.now()
        val nextRun = if (now.toLocalTime().isBefore(LocalTime.of(7, 0))) {
            now.toLocalDate().atTime(7, 0)
        } else {
            now.toLocalDate().plusDays(1).atTime(7, 0)
        }
        val delayMinutes = Duration.between(now, nextRun).toMinutes()

        val syncRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .addTag(SYNC_WORK_TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )

        Log.i(TAG, "Scheduled periodic health sync (initial delay: ${delayMinutes}min)")
    }

    private fun startContextStreaming() {
        // Start the context streaming foreground service
        val serviceIntent = Intent(this, ContextService::class.java)
        startForegroundService(serviceIntent)
        Log.i(TAG, "Started ContextService")

        // Register for activity recognition updates (every 60s)
        try {
            val pendingIntent = ActivityRecognitionReceiver.getPendingIntent(this)
            ActivityRecognition.getClient(this)
                .requestActivityUpdates(ACTIVITY_DETECTION_INTERVAL_MS, pendingIntent)
                .addOnSuccessListener {
                    Log.i(TAG, "Activity recognition registered")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Activity recognition failed: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "ACTIVITY_RECOGNITION permission not granted: ${e.message}")
        }
    }

    private fun startAwarenessLiveUpdate() {
        val serviceIntent = Intent(this, AwarenessLiveUpdateService::class.java)
        startForegroundService(serviceIntent)
        Log.i(TAG, "Started AwarenessLiveUpdateService")
    }

    companion object {
        private const val TAG = "HapaxPhoneApp"
        const val SYNC_WORK_NAME = "health_sync"
        const val SYNC_WORK_TAG = "health_sync"
        private const val ACTIVITY_DETECTION_INTERVAL_MS = 60_000L
    }
}
