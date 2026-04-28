package dev.hapax.phone.awareness

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AwarenessNotificationInvariantTest {
    @Test
    fun `live update notification has no pending intent or action buttons`() {
        val sourcePath = listOf(
            "app/src/main/kotlin/dev/hapax/phone/awareness/AwarenessLiveUpdateService.kt",
            "src/main/kotlin/dev/hapax/phone/awareness/AwarenessLiveUpdateService.kt",
        ).map(::File).first(File::isFile)
        val source = sourcePath.readText()

        assertFalse(source.contains("PendingIntent"))
        assertFalse(source.contains("setContentIntent"))
        assertFalse(source.contains("addAction"))
        assertTrue(source.contains("setOngoing(true)"))
    }
}
