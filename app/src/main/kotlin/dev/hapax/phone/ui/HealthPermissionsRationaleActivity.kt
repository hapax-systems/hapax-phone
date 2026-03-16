package dev.hapax.phone.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hapax.phone.ui.theme.HapaxPhoneTheme

/**
 * Required by Health Connect to explain why the app needs health permissions.
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HapaxPhoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Health Data Access",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = "Hapax Phone reads your health data (heart rate, HRV, steps, " +
                                "sleep, SpO2, skin temperature, and exercise) to create a daily " +
                                "health summary. This summary is sent to your personal hapax " +
                                "workstation for analysis. No data is shared with third parties.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
