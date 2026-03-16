package dev.hapax.phone.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.hapax.phone.data.HealthSummaryPayload
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthConnectReader(context: Context) {

    private val client = HealthConnectClient.getOrCreate(context)

    suspend fun readDailySummary(date: LocalDate): HealthSummaryPayload {
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        val timeRange = TimeRangeFilter.between(startOfDay, endOfDay)

        val hr = readHeartRate(timeRange)
        val hrv = readHrv(timeRange)
        val steps = readSteps(timeRange)
        val activeMinutes = readActiveMinutes(timeRange)
        val sleep = readSleep(startOfDay, endOfDay)
        val spo2 = readSpo2(timeRange)
        val skinTemp = readSkinTemp(timeRange)

        return HealthSummaryPayload(
            deviceId = "pixel10",
            date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            restingHr = hr?.restingHr,
            hrMin = hr?.min,
            hrMax = hr?.max,
            hrMean = hr?.mean,
            hrvMeanMs = hrv,
            steps = steps,
            activeMinutes = activeMinutes,
            sleepStart = sleep?.start,
            sleepEnd = sleep?.end,
            sleepDurationMin = sleep?.durationMin,
            deepMin = sleep?.deepMin,
            remMin = sleep?.remMin,
            spo2Mean = spo2,
            skinTempDeviationC = skinTemp,
        )
    }

    private data class HrResult(
        val restingHr: Double,
        val min: Double,
        val max: Double,
        val mean: Double,
    )

    private suspend fun readHeartRate(timeRange: TimeRangeFilter): HrResult? = try {
        val records = client.readRecords(
            ReadRecordsRequest(HeartRateRecord::class, timeRange)
        ).records
        val allSamples = records.flatMap { it.samples }
        if (allSamples.isEmpty()) null
        else {
            val bpms = allSamples.map { it.beatsPerMinute.toDouble() }
            HrResult(
                restingHr = bpms.min(),
                min = bpms.min(),
                max = bpms.max(),
                mean = bpms.average(),
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read heart rate: ${e.message}")
        null
    }

    private suspend fun readHrv(timeRange: TimeRangeFilter): Double? = try {
        val records = client.readRecords(
            ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, timeRange)
        ).records
        if (records.isEmpty()) null
        else records.map { it.heartRateVariabilityMillis }.average()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read HRV: ${e.message}")
        null
    }

    private suspend fun readSteps(timeRange: TimeRangeFilter): Int? = try {
        val records = client.readRecords(
            ReadRecordsRequest(StepsRecord::class, timeRange)
        ).records
        if (records.isEmpty()) null
        else records.sumOf { it.count }.toInt()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read steps: ${e.message}")
        null
    }

    private suspend fun readActiveMinutes(timeRange: TimeRangeFilter): Int? = try {
        val records = client.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, timeRange)
        ).records
        if (records.isEmpty()) null
        else {
            records.sumOf { record ->
                java.time.Duration.between(record.startTime, record.endTime).toMinutes()
            }.toInt()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read active minutes: ${e.message}")
        null
    }

    private data class SleepResult(
        val start: String,
        val end: String,
        val durationMin: Int,
        val deepMin: Int,
        val remMin: Int,
    )

    private suspend fun readSleep(startOfDay: Instant, endOfDay: Instant): SleepResult? = try {
        // Look back to previous day evening for sleep that started the night before
        val sleepWindowStart = startOfDay.minus(java.time.Duration.ofHours(12))
        val timeRange = TimeRangeFilter.between(sleepWindowStart, endOfDay)
        val records = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, timeRange)
        ).records
        if (records.isEmpty()) null
        else {
            val session = records.maxByOrNull {
                java.time.Duration.between(it.startTime, it.endTime)
            }!!
            val durationMin = java.time.Duration.between(session.startTime, session.endTime).toMinutes().toInt()

            var deepMin = 0
            var remMin = 0
            for (stage in session.stages) {
                val stageMin = java.time.Duration.between(stage.startTime, stage.endTime).toMinutes().toInt()
                when (stage.stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> deepMin += stageMin
                    SleepSessionRecord.STAGE_TYPE_REM -> remMin += stageMin
                }
            }

            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            SleepResult(
                start = session.startTime.atZone(ZoneId.systemDefault()).format(formatter),
                end = session.endTime.atZone(ZoneId.systemDefault()).format(formatter),
                durationMin = durationMin,
                deepMin = deepMin,
                remMin = remMin,
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read sleep: ${e.message}")
        null
    }

    private suspend fun readSpo2(timeRange: TimeRangeFilter): Double? = try {
        val records = client.readRecords(
            ReadRecordsRequest(OxygenSaturationRecord::class, timeRange)
        ).records
        if (records.isEmpty()) null
        else records.map { it.percentage.value }.average()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read SpO2: ${e.message}")
        null
    }

    private suspend fun readSkinTemp(timeRange: TimeRangeFilter): Double? = try {
        val records = client.readRecords(
            ReadRecordsRequest(SkinTemperatureRecord::class, timeRange)
        ).records
        if (records.isEmpty()) null
        else {
            val deltas = records.flatMap { record ->
                record.deltas.map { it.delta.inCelsius }
            }
            if (deltas.isEmpty()) null else deltas.average()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read skin temperature: ${e.message}")
        null
    }

    companion object {
        private const val TAG = "HealthConnectReader"
    }
}
