package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Structural guards that need reflection, which only the JVM has.
 *
 * These assert that a persisted type has no field capable of holding a resolved URL or a token —
 * GDD 8 section 16, "a fila nunca guarda token ou URL resolvida". The point is that adding such a
 * field later fails here rather than shipping credentials into the saved queue.
 *
 * They lived in commonTest until the module started compiling for iOS, where `::class.java` does
 * not exist. Moved rather than deleted or softened: a rule worth stating on one platform is worth
 * keeping, and these types are shared, so a violation caught here is caught for every target.
 */
class StructuralCredentialGuardsJvmTest {
    @Test
    fun `an episode has no stream uri field`() {
        assertFalse(Episode::class.java.declaredFields.any { it.name == "streamUri" })
    }

    @Test
    fun `a queue entry has no field named like a credential`() {
        val fields = QueueEntry::class.java.declaredFields.map { it.name.lowercase() }
        val forbidden = listOf("uri", "url", "token", "password", "header", "credential", "stream")
        forbidden.forEach { needle ->
            assertFalse(
                fields.any { it.contains(needle) },
                "QueueEntry must not carry a field named like '$needle'.",
            )
        }
    }
}
