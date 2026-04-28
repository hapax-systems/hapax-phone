package dev.hapax.phone.awareness

import java.time.Instant
import java.time.format.DateTimeParseException

object AwarenessTop5Picker {
    fun pickTop5(
        summary: AwarenessSummary?,
        stale: Boolean = false,
        now: Instant = Instant.now(),
    ): Top5Payload {
        if (summary == null) {
            return Top5Payload(
                title = "Hapax awareness stale",
                subtitle = "No current awareness state",
                expanded = "Stance unknown\nStream unknown\nHealth unknown\nRefusals last hour unknown\nPresence 0/10",
                stale = true,
            )
        }

        val stance = summary.daimonionVoice.stance.ifBlank { "unknown" }
        val stream = if (summary.stream.live) {
            "live ${summary.stream.chronicleEvents5Min} ev/5m"
        } else {
            "offline"
        }
        val health = formatHealth(summary.healthSystem)
        val refusals1h = countRefusalsLastHour(summary, now)
        val presence = presenceDecile(summary)
        val stalePrefix = if (stale) "stale - " else ""

        return Top5Payload(
            title = "${stalePrefix}Hapax: $stance",
            subtitle = "$stream - $health",
            expanded = listOf(
                "Stance $stance",
                "Stream $stream",
                "Health $health",
                "Refusals last hour $refusals1h",
                "Presence $presence/10",
            ).joinToString("\n"),
            stale = stale,
        )
    }

    private fun formatHealth(health: HealthSystemBlock): String {
        val status = health.overallStatus.ifBlank { "unknown" }
        val failures = health.failedUnits + health.dockerContainersFailed
        return if (failures > 0) {
            "$status $failures fail"
        } else {
            status
        }
    }

    private fun countRefusalsLastHour(summary: AwarenessSummary, now: Instant): Int {
        val cutoff = now.minusSeconds(3600)
        return summary.refusalsRecent.count { event ->
            val ts = parseInstant(event.timestamp) ?: return@count false
            !ts.isBefore(cutoff) && !ts.isAfter(now)
        }
    }

    private fun presenceDecile(summary: AwarenessSummary): Int {
        var score = 0
        if (summary.stream.live) score += 3
        if (summary.daimonionVoice.voiceSessionActive) score += 2
        if (summary.studio.monitorAuxCActive) score += 1
        if (summary.musicSoundcloud.isPlaying) score += 1
        if (summary.hardwareFleet.phoneLastHeartbeatAt != null) score += 1
        if (summary.hardwareFleet.watchLastHeartbeatAt != null) score += 1
        if (summary.healthSystem.overallStatus == "healthy") score += 1
        return score.coerceIn(0, 10)
    }

    private fun parseInstant(raw: String): Instant? {
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(raw.replace("+00:00", "Z"))
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
