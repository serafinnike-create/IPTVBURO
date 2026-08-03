package com.lucasserafin94.iptvburo.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.W32APIOptions
import java.awt.Window

/**
 * Applies the app's dark chrome to the native window frame.
 *
 * Compose paints inside the window; the title bar belongs to the OS and stayed light, so a
 * near-black application sat under a white bar. Windows exposes this through DWM rather than any
 * AWT API, hence the native call.
 */
object WindowChrome {
    /**
     * Requests the dark title bar for [window].
     *
     * Silently does nothing when unavailable: this is cosmetic, and older Windows builds, non-Windows
     * platforms and a missing peer must not take the app down over a title bar colour.
     */
    fun applyDarkTitleBar(window: Window?) {
        if (window == null) return
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return

        runCatching {
            val handle = Native.getComponentPointer(window) ?: return
            val hwnd = WinDef.HWND(handle)
            val enabled = WinDef.BOOLByReference(WinDef.BOOL(true))

            // Attribute 20 on Windows 11 and later builds of 10; 19 on the earlier ones. Trying the
            // modern value first and falling back keeps a single code path for both.
            val applied = Dwm.INSTANCE.DwmSetWindowAttribute(hwnd, ATTRIBUTE_MODERN, enabled, 4)
            if (applied != 0) {
                Dwm.INSTANCE.DwmSetWindowAttribute(hwnd, ATTRIBUTE_LEGACY, enabled, 4)
            }
        }
    }

    private const val ATTRIBUTE_MODERN = 20
    private const val ATTRIBUTE_LEGACY = 19

    private interface Dwm : com.sun.jna.win32.StdCallLibrary {
        fun DwmSetWindowAttribute(
            hwnd: WinDef.HWND,
            attribute: Int,
            value: Pointer?,
            size: Int,
        ): Int

        fun DwmSetWindowAttribute(
            hwnd: WinDef.HWND,
            attribute: Int,
            value: WinDef.BOOLByReference,
            size: Int,
        ): Int

        companion object {
            val INSTANCE: Dwm =
                Native.load("dwmapi", Dwm::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}
