package com.lucasserafin94.iptvburo.desktop.security

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecureTextBufferTest {
    @Test
    fun `blank state invalidates its compose observer when text changes`() {
        val buffer = SecureTextBuffer()
        val observer = SnapshotStateObserver { command -> command() }
        val scope = Any()
        var invalidations = 0

        observer.start()
        try {
            observer.observeReads(
                scope = scope,
                onValueChangedForScope = { invalidations += 1 },
            ) {
                assertTrue(buffer.isBlank)
            }

            buffer.replace("ready")
            Snapshot.sendApplyNotifications()

            assertEquals(1, invalidations)
            assertFalse(buffer.isBlank)
        } finally {
            buffer.clear()
            observer.stop()
            observer.clear()
        }
    }
}
