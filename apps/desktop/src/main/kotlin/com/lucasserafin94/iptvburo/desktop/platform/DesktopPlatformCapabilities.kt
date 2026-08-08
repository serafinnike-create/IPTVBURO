package com.lucasserafin94.iptvburo.desktop.platform

import com.google.gson.JsonParser

/**
 * Runtime view of the canonical Windows preview capability manifest.
 *
 * Optional and malformed fields fail closed. A packaging mistake may hide a preview feature, but it
 * must never advertise downloads, audio or multiview that the release contract says are unavailable.
 */
internal data class DesktopPlatformCapabilities(
    val offlineSupported: Boolean = false,
    val multiviewSupported: Boolean = false,
    val audioSupported: Boolean = false,
) {
    companion object {
        val current: DesktopPlatformCapabilities by lazy {
            val document =
                DesktopPlatformCapabilities::class.java
                    .getResourceAsStream(CAPABILITY_RESOURCE)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
            document?.let(::parse) ?: DesktopPlatformCapabilities()
        }

        internal fun parse(document: String): DesktopPlatformCapabilities =
            runCatching {
                val root = JsonParser.parseString(document).asJsonObject
                DesktopPlatformCapabilities(
                    offlineSupported =
                        root.getAsJsonObject("offline")
                            ?.get("supported")
                            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                            ?.asBoolean == true,
                    multiviewSupported =
                        root.getAsJsonObject("playback")
                            ?.get("multiview")
                            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                            ?.asBoolean == true,
                    audioSupported =
                        root.getAsJsonObject("audio")
                            ?.get("supported")
                            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                            ?.asBoolean == true,
                )
            }.getOrDefault(DesktopPlatformCapabilities())

        private const val CAPABILITY_RESOURCE = "/capabilities/windows-preview.json"
    }
}
