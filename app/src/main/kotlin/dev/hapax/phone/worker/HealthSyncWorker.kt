package dev.hapax.phone.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.hapax.phone.health.HealthConnectReader
import dev.hapax.phone.network.HapaxTransport
import java.time.LocalDate
import java.time.LocalTime

class HealthSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting health sync")

        return try {
            val reader = HealthConnectReader(applicationContext)
            val transport = HapaxTransport(applicationContext)

            // If before 7 AM, report yesterday's data
            val date = if (LocalTime.now().isBefore(LocalTime.of(7, 0))) {
                LocalDate.now().minusDays(1)
            } else {
                LocalDate.now()
            }

            val payload = reader.readDailySummary(date)
            val success = transport.send(payload)

            if (success) {
                Log.i(TAG, "Health sync completed for $date")
                Result.success()
            } else {
                Log.w(TAG, "Health sync failed, will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Health sync error: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "HealthSyncWorker"
    }
}
