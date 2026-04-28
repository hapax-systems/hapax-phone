package dev.hapax.phone.awareness

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AwarenessTop5PickerTest {
    @Test
    fun `pickTop5 renders stance stream health refusals and presence`() {
        val now = Instant.parse("2026-04-28T03:40:00Z")
        val summary = AwarenessSummary(
            timestamp = "2026-04-28T03:39:00Z",
            daimonionVoice = DaimonionVoiceBlock(
                stance = "SEEKING",
                voiceSessionActive = true,
            ),
            stream = StreamBlock(
                live = true,
                chronicleEvents5Min = 12,
                rotationState = "ACTIVE",
            ),
            healthSystem = HealthSystemBlock(
                overallStatus = "healthy",
                failedUnits = 0,
                dockerContainersFailed = 0,
            ),
            studio = StudioBlock(monitorAuxCActive = true),
            musicSoundcloud = MusicBlock(isPlaying = true),
            hardwareFleet = HardwareFleetBlock(
                phoneLastHeartbeatAt = "2026-04-28T03:39:30Z",
                watchLastHeartbeatAt = "2026-04-28T03:39:20Z",
            ),
            refusalsRecent = listOf(
                RefusalEvent(timestamp = "2026-04-28T03:20:00Z", surface = "x", reason = "tos"),
                RefusalEvent(timestamp = "2026-04-28T01:20:00Z", surface = "wiki", reason = "tos"),
            ),
        )

        val payload = AwarenessTop5Picker.pickTop5(summary, now = now)

        assertEquals("Hapax: SEEKING", payload.title)
        assertEquals("live 12 ev/5m - healthy", payload.subtitle)
        assertTrue(payload.expanded.contains("Refusals last hour 1"))
        assertTrue(payload.expanded.contains("Presence 10/10"))
        assertFalse(payload.stale)
    }

    @Test
    fun `missing state returns stale payload`() {
        val payload = AwarenessTop5Picker.pickTop5(summary = null)

        assertTrue(payload.stale)
        assertEquals("Hapax awareness stale", payload.title)
        assertTrue(payload.expanded.contains("Presence 0/10"))
    }
}
