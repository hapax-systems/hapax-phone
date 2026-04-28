package dev.hapax.phone.awareness

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class AwarenessMinModeService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Min Mode awareness placeholder started before platform API is available")
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AwarenessMinMode"
    }
}
