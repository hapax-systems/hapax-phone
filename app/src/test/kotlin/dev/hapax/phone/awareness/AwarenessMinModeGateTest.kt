package dev.hapax.phone.awareness

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AwarenessMinModeGateTest {
    @Test
    fun `gate is disabled until feature flag and platform API are available`() {
        assertFalse(AwarenessMinModeGate.isAvailable(sdkInt = 36, featureEnabled = true))
        assertFalse(AwarenessMinModeGate.isAvailable(sdkInt = 37, featureEnabled = false))
        assertTrue(AwarenessMinModeGate.isAvailable(sdkInt = 37, featureEnabled = true))
    }
}
