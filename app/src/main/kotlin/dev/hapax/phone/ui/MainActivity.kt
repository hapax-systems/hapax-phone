package dev.hapax.phone.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.hapax.phone.data.dataStore
import dev.hapax.phone.ui.theme.HapaxPhoneTheme
import dev.hapax.phone.worker.HealthSyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    private val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(SkinTemperatureRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HapaxPhoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(permissions)
                }
            }
        }
    }
}

@Composable
private fun MainScreen(permissions: Set<String>) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(false) }
    var syncState by remember { mutableStateOf("Idle") }
    var manualIp by remember { mutableStateOf("") }
    var lastSyncTime by remember { mutableStateOf("Never") }
    val scope = rememberCoroutineScope()

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        permissionsGranted = granted.containsAll(permissions)
    }

    LaunchedEffect(Unit) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            permissionsGranted = granted.containsAll(permissions)
        } catch (_: Exception) {
            permissionsGranted = false
        }

        // Load saved manual IP
        try {
            manualIp = context.dataStore.data.map { prefs ->
                prefs[stringPreferencesKey("manual_ip")] ?: ""
            }.first()
        } catch (_: Exception) { }

        // Load last sync time
        try {
            lastSyncTime = context.dataStore.data.map { prefs ->
                prefs[stringPreferencesKey("last_sync")] ?: "Never"
            }.first()
        } catch (_: Exception) { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Hapax Phone",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // Connection status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (permissionsGranted) Color.Green else Color.Red),
            )
            Text(
                text = if (permissionsGranted) "Health Connect: Authorized" else "Health Connect: Not Authorized",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (!permissionsGranted) {
            Button(onClick = { permissionLauncher.launch(permissions) }) {
                Text("Grant Health Permissions")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Last sync: $lastSyncTime",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "Sync status: $syncState",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Manual IP field
        OutlinedTextField(
            value = manualIp,
            onValueChange = { manualIp = it },
            label = { Text("Server IP:Port (optional)") },
            placeholder = { Text("100.117.1.83:8051") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                scope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[stringPreferencesKey("manual_ip")] = manualIp
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save IP")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                syncState = "Syncing..."
                val workRequest = OneTimeWorkRequestBuilder<HealthSyncWorker>()
                    .addTag("manual_sync")
                    .build()
                val workManager = WorkManager.getInstance(context)
                workManager.enqueueUniqueWork(
                    "manual_health_sync",
                    ExistingWorkPolicy.REPLACE,
                    workRequest,
                )
                // Observe work state
                workManager.getWorkInfoByIdLiveData(workRequest.id)
                    .observeForever { workInfo ->
                        syncState = when (workInfo?.state) {
                            WorkInfo.State.SUCCEEDED -> "Sync completed"
                            WorkInfo.State.FAILED -> "Sync failed"
                            WorkInfo.State.RUNNING -> "Syncing..."
                            WorkInfo.State.ENQUEUED -> "Queued"
                            else -> "Idle"
                        }
                    }
            },
            enabled = permissionsGranted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sync Now")
        }
    }
}
