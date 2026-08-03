package com.lucasserafin94.iptvburo.desktop.update

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Probe, not an assertion about this machine.
 *
 * Whether IPTV BURO is installed depends on the developer's box, so this only checks that the
 * lookup returns either nothing or something that is genuinely a ProductCode — never a fragment of
 * registry output that would be pasted into a shell command.
 */
class InstalledProductProbe {
    @Test
    fun `the lookup returns a product code or nothing`() {
        val code = installedProductCode()
        println("installedProductCode() = ${code ?: "<none>"}")

        assertTrue(
            code == null || PRODUCT_CODE.matches(code),
            "a non-null result must be a ProductCode, was: $code",
        )
    }
}
