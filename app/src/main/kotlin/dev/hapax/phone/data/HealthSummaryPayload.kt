package dev.hapax.phone.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthSummaryPayload(
    @SerialName("device_id") val deviceId: String,
    val date: String,
    @SerialName("resting_hr") val restingHr: Double? = null,
    @SerialName("hr_min") val hrMin: Double? = null,
    @SerialName("hr_max") val hrMax: Double? = null,
    @SerialName("hr_mean") val hrMean: Double? = null,
    @SerialName("hrv_mean_ms") val hrvMeanMs: Double? = null,
    val steps: Int? = null,
    @SerialName("active_minutes") val activeMinutes: Int? = null,
    @SerialName("sleep_start") val sleepStart: String? = null,
    @SerialName("sleep_end") val sleepEnd: String? = null,
    @SerialName("sleep_duration_min") val sleepDurationMin: Int? = null,
    @SerialName("deep_min") val deepMin: Int? = null,
    @SerialName("rem_min") val remMin: Int? = null,
    @SerialName("spo2_mean") val spo2Mean: Double? = null,
    @SerialName("skin_temp_deviation_c") val skinTempDeviationC: Double? = null,
    @SerialName("eda_events") val edaEvents: Int? = null,
)
