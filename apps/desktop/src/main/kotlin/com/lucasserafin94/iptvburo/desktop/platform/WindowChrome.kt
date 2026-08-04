package com.lucasserafin94.iptvburo.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
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

    /**
     * Puts [window] into true full screen, or restores it.
     *
     * Compose's Fullscreen placement leaves the window decorated and merely maximised, so a border
     * and the taskbar stayed visible over the video. Real full screen means dropping the frame
     * style and covering the monitor's whole bounds, which is what every video player does.
     */
    fun setBorderlessFullScreen(
        window: Window?,
        fullScreen: Boolean,
    ) {
        if (window == null) return
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return

        runCatching {
            val handle = Native.getComponentPointer(window) ?: return
            val hwnd = WinDef.HWND(handle)
            val user32 = User32Ex.INSTANCE
            val style = user32.GetWindowLong(hwnd, GWL_STYLE)

            if (fullScreen) {
                user32.SetWindowLong(hwnd, GWL_STYLE, style and WS_OVERLAPPEDWINDOW.inv())
                // MONITOR_DEFAULTTONEAREST: the screen the window is actually on, not the primary,
                // so full screen lands on the right monitor in a multi-display setup.
                val monitor = user32.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST)
                val info = WinUser.MONITORINFO()
                if (user32.GetMonitorInfo(monitor, info)) {
                    val bounds = info.rcMonitor
                    user32.SetWindowPos(
                        hwnd,
                        null,
                        bounds.left,
                        bounds.top,
                        bounds.right - bounds.left,
                        bounds.bottom - bounds.top,
                        SWP_FRAMECHANGED or SWP_NOOWNERZORDER,
                    )
                }
            } else {
                user32.SetWindowLong(hwnd, GWL_STYLE, style or WS_OVERLAPPEDWINDOW)
                // Restored to the work area, not merely re-decorated. Putting the frame back while
                // the window still covered the whole monitor left it larger than the desktop, with
                // the picture spilling over the taskbar and no way to grab an edge.
                val monitor = user32.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST)
                val info = WinUser.MONITORINFO()
                if (user32.GetMonitorInfo(monitor, info)) {
                    val work = info.rcWork
                    user32.SetWindowPos(
                        hwnd,
                        null,
                        work.left + RESTORE_INSET,
                        work.top + RESTORE_INSET,
                        (work.right - work.left) - RESTORE_INSET * 2,
                        (work.bottom - work.top) - RESTORE_INSET * 2,
                        SWP_FRAMECHANGED or SWP_NOZORDER or SWP_NOOWNERZORDER,
                    )
                } else {
                    user32.SetWindowPos(
                        hwnd,
                        null,
                        0,
                        0,
                        0,
                        0,
                        SWP_FRAMECHANGED or SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOOWNERZORDER,
                    )
                }
            }
        }
    }

    private const val ATTRIBUTE_MODERN = 20
    private const val ATTRIBUTE_LEGACY = 19

    private const val GWL_STYLE = -16
    private const val WS_OVERLAPPEDWINDOW = 0x00CF0000
    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOOWNERZORDER = 0x0200

    /** A small margin so the restored window is visibly inside the desktop, not flush to it. */
    private const val RESTORE_INSET = 24

    private interface User32Ex : com.sun.jna.win32.StdCallLibrary {
        fun GetWindowLong(hwnd: WinDef.HWND, index: Int): Int

        fun SetWindowLong(hwnd: WinDef.HWND, index: Int, value: Int): Int

        fun MonitorFromWindow(hwnd: WinDef.HWND, flags: Int): WinUser.HMONITOR

        fun GetMonitorInfo(monitor: WinUser.HMONITOR, info: WinUser.MONITORINFO): Boolean

        fun SetWindowPos(
            hwnd: WinDef.HWND,
            insertAfter: WinDef.HWND?,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            flags: Int,
        ): Boolean

        companion object {
            val INSTANCE: User32Ex =
                Native.load("user32", User32Ex::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

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
