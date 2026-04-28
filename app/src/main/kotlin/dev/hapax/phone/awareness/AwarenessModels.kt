package dev.hapax.phone.awareness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AwarenessSummary(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val timestamp: String = "",
    @SerialName("ttl_seconds") val ttlSeconds: Int = 90,
    @SerialName("daimonion_voice") val daimonionVoice: DaimonionVoiceBlock = DaimonionVoiceBlock(),
    val stream: StreamBlock = StreamBlock(),
    @SerialName("health_system") val healthSystem: HealthSystemBlock = HealthSystemBlock(),
    val studio: StudioBlock = StudioBlock(),
    @SerialName("music_soundcloud") val musicSoundcloud: MusicBlock = MusicBlock(),
    @SerialName("hardware_fleet") val hardwareFleet: HardwareFleetBlock = HardwareFleetBlock(),
    @SerialName("refusals_recent") val refusalsRecent: List<RefusalEvent> = emptyList(),
)

@Serializable
data class DaimonionVoiceBlock(
    val stance: String = "unknown",
    @SerialName("voice_session_active") val voiceSessionActive: Boolean = false,
)

@Serializable
data class StreamBlock(
    val live: Boolean = false,
    @SerialName("chronicle_events_5min") val chronicleEvents5Min: Int = 0,
    @SerialName("rotation_state") val rotationState: String = "",
)

@Serializable
data class HealthSystemBlock(
    @SerialName("overall_status") val overallStatus: String = "unknown",
    @SerialName("failed_units") val failedUnits: Int = 0,
    @SerialName("docker_containers_failed") val dockerContainersFailed: Int = 0,
    @SerialName("disk_pct_used") val diskPctUsed: Double = 0.0,
    @SerialName("gpu_vram_pct_used") val gpuVramPctUsed: Double = 0.0,
)

@Serializable
data class StudioBlock(
    @SerialName("monitor_aux_c_active") val monitorAuxCActive: Boolean = false,
)

@Serializable
data class MusicBlock(
    @SerialName("current_track") val currentTrack: String = "",
    val source: String = "",
    @SerialName("is_playing") val isPlaying: Boolean = false,
)

@Serializable
data class HardwareFleetBlock(
    @SerialName("pi_count_online") val piCountOnline: Int = 0,
    @SerialName("pi_count_total") val piCountTotal: Int = 0,
    @SerialName("watch_last_heartbeat_at") val watchLastHeartbeatAt: String? = null,
    @SerialName("phone_last_heartbeat_at") val phoneLastHeartbeatAt: String? = null,
)

@Serializable
data class RefusalEvent(
    val timestamp: String,
    val surface: String = "",
    val reason: String = "",
    @SerialName("refused_artifact_slug") val refusedArtifactSlug: String? = null,
)

data class Top5Payload(
    val title: String,
    val subtitle: String,
    val expanded: String,
    val stale: Boolean = false,
)
