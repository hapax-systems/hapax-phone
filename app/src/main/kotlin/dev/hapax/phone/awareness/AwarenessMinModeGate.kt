package dev.hapax.phone.awareness

import android.os.Build

object AwarenessMinModeGate {
    const val MIN_MODE_API_LEVEL = 37
    const val FEATURE_ENABLED = false

    fun isAvailable(
        sdkInt: Int = Build.VERSION.SDK_INT,
        featureEnabled: Boolean = FEATURE_ENABLED,
    ): Boolean = featureEnabled && sdkInt >= MIN_MODE_API_LEVEL
}
