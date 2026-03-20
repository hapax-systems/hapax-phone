package dev.hapax.phone.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity

/**
 * Receives activity recognition updates from Google Play Services.
 *
 * Updates the ContextService singleton with the current activity type
 * and confidence. Uses the Transition API for battery efficiency (~1%/day).
 *
 * Activity types mapped: still, walking, running, in_vehicle, on_bicycle.
 */
class ActivityRecognitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return

        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val mostProbable = result.mostProbableActivity

        val activityType = mapActivityType(mostProbable.type)
        val confidence = mostProbable.confidence / 100f

        Log.d(TAG, "Activity: $activityType (confidence=${confidence})")

        // Update the running ContextService
        ContextService.instance?.let { service ->
            service.currentActivityType = activityType
            service.currentActivityConfidence = confidence
        }
    }

    companion object {
        private const val TAG = "ActivityRecognition"
        private const val REQUEST_CODE = 100

        fun mapActivityType(type: Int): String = when (type) {
            DetectedActivity.STILL -> "still"
            DetectedActivity.WALKING -> "walking"
            DetectedActivity.RUNNING -> "running"
            DetectedActivity.IN_VEHICLE -> "in_vehicle"
            DetectedActivity.ON_BICYCLE -> "on_bicycle"
            DetectedActivity.ON_FOOT -> "walking"
            DetectedActivity.TILTING -> "still"
            else -> "still"
        }

        fun getPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ActivityRecognitionReceiver::class.java)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        }
    }
}
