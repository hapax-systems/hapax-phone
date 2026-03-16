package dev.hapax.phone.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthSummaryPayloadTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `serialization matches server schema`() {
        val payload = HealthSummaryPayload(
            deviceId = "pixel10",
            date = "2026-03-15",
            restingHr = 62.0,
            hrMin = 55.0,
            hrMax = 165.0,
            hrMean = 72.0,
            hrvMeanMs = 45.0,
            steps = 8432,
            activeMinutes = 42,
            sleepStart = "2026-03-14T23:15:00",
            sleepEnd = "2026-03-15T06:45:00",
            sleepDurationMin = 450,
            deepMin = 90,
            remMin = 105,
            spo2Mean = 97.2,
            skinTempDeviationC = 0.3,
        )

        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<HealthSummaryPayload>(encoded)

        assertEquals(payload, decoded)

        // Verify snake_case field names
        assert(encoded.contains("\"device_id\"")) { "Expected device_id in JSON" }
        assert(encoded.contains("\"resting_hr\"")) { "Expected resting_hr in JSON" }
        assert(encoded.contains("\"hr_min\"")) { "Expected hr_min in JSON" }
        assert(encoded.contains("\"hr_max\"")) { "Expected hr_max in JSON" }
        assert(encoded.contains("\"hr_mean\"")) { "Expected hr_mean in JSON" }
        assert(encoded.contains("\"hrv_mean_ms\"")) { "Expected hrv_mean_ms in JSON" }
        assert(encoded.contains("\"active_minutes\"")) { "Expected active_minutes in JSON" }
        assert(encoded.contains("\"sleep_start\"")) { "Expected sleep_start in JSON" }
        assert(encoded.contains("\"sleep_end\"")) { "Expected sleep_end in JSON" }
        assert(encoded.contains("\"sleep_duration_min\"")) { "Expected sleep_duration_min in JSON" }
        assert(encoded.contains("\"deep_min\"")) { "Expected deep_min in JSON" }
        assert(encoded.contains("\"rem_min\"")) { "Expected rem_min in JSON" }
        assert(encoded.contains("\"spo2_mean\"")) { "Expected spo2_mean in JSON" }
        assert(encoded.contains("\"skin_temp_deviation_c\"")) { "Expected skin_temp_deviation_c in JSON" }
    }

    @Test
    fun `null fields are serialized`() {
        val payload = HealthSummaryPayload(
            deviceId = "pixel10",
            date = "2026-03-15",
        )

        val encoded = json.encodeToString(payload)
        assert(encoded.contains("\"resting_hr\":null")) { "Expected null resting_hr" }
        assert(encoded.contains("\"steps\":null")) { "Expected null steps" }
    }
}
