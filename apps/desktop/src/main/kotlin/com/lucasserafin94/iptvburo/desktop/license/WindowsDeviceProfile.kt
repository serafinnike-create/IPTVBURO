package com.lucasserafin94.iptvburo.desktop.license

import com.google.gson.JsonObject
import com.lucasserafin94.iptvburo.desktop.build.DESKTOP_RELEASE_VERSION
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/**
 * Coarse Windows information shown only in the protected support panel.
 *
 * No hostname, user name, serial number, MachineGuid or MAC address is collected. Manufacturer and
 * product name are enough for the owner to recognise "the Lenovo notebook" without creating a
 * second hardware identity beside the cryptographic installation identity.
 */
internal object WindowsDeviceProfile {
    fun report(): JsonObject =
        JsonObject().apply {
            addProperty("deviceType", "WINDOWS_PC")
            addProperty("platform", "WINDOWS")
            manufacturer()?.let { addProperty("manufacturer", it) }
            model()?.let { addProperty("model", it) }
            addProperty(
                "osVersion",
                listOf(
                    System.getProperty("os.name"),
                    System.getProperty("os.version"),
                    System.getProperty("os.arch")?.let { "($it)" },
                ).filterNotNull().filter(String::isNotBlank).joinToString(" "),
            )
            addProperty("appVersion", DESKTOP_RELEASE_VERSION)
        }

    private fun manufacturer(): String? = biosValue("SystemManufacturer")

    private fun model(): String? = biosValue("SystemProductName")

    private fun biosValue(name: String): String? =
        runCatching {
            if (!Platform.isWindows()) return null
            Advapi32Util.registryGetStringValue(
                WinReg.HKEY_LOCAL_MACHINE,
                BIOS_KEY,
                name,
            ).trim().takeUnless { value ->
                value.isBlank() || PLACEHOLDERS.any { placeholder -> value.equals(placeholder, ignoreCase = true) }
            }
        }.getOrNull()

    private const val BIOS_KEY = "HARDWARE\\DESCRIPTION\\System\\BIOS"
    private val PLACEHOLDERS = setOf("Default string", "System Product Name", "To Be Filled By O.E.M.")
}
