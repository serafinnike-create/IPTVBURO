package com.lucasserafin94.iptvburo.desktop.playback

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/**
 * Ties every VLC this app starts to the life of the app itself.
 *
 * ## Why a shutdown hook is not enough
 *
 * [VlcDesktopPlayer] disposes its engine on close and registers a JVM shutdown hook as a backstop.
 * Both are ordinary code, and both need the JVM to still be running to execute. A customer reported
 * the app dying after hours of playback with an unhandled-exception dialog, and pressing "Encerrar"
 * ends the process outright — no dispose, no hook. A VLC holding eighty megabytes and a loopback
 * port was still running afterwards, with nothing left that could ever reap it.
 *
 * ## What this does instead
 *
 * A Windows Job Object with `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`. Every VLC is assigned to the job,
 * and the job's last handle closes when this process ends — however it ends. The kernel then
 * terminates everything in it. Task Manager, a crash, a power-off of the app: all the same.
 *
 * This is the mechanism browsers use to make sure a killed browser does not leave renderers behind.
 *
 * ## What it deliberately does not do
 *
 * Nothing on a non-Windows host, and nothing if the job cannot be created — the app is expected to
 * work either way, and the existing dispose and shutdown hook remain the ordinary path. This is a
 * floor under them, not a replacement.
 */
internal object ChildProcessJob {
    /**
     * The job handle, or null where one could not be made.
     *
     * Held for the life of the process on purpose: the kill happens when the last handle to the job
     * closes, so letting this be collected would defeat the whole mechanism.
     */
    private val handle: WinNT.HANDLE? by lazy { createJob() }

    /**
     * Puts [process] under the job, so it cannot outlive this app.
     *
     * Failures are swallowed. A VLC that could not be assigned still plays; it merely loses this
     * particular guarantee, which is a far better outcome than refusing to start the channel.
     */
    fun adopt(process: Process) {
        val job = handle ?: return
        runCatching {
            val child = Kernel32.INSTANCE.OpenProcess(PROCESS_SET_QUOTA or PROCESS_TERMINATE, false, process.pid().toInt())
                ?: return@runCatching
            try {
                Kernel32Job.INSTANCE.AssignProcessToJobObject(job, child)
            } finally {
                Kernel32.INSTANCE.CloseHandle(child)
            }
        }
    }

    private fun createJob(): WinNT.HANDLE? {
        if (!Platform.isWindows()) return null
        return runCatching {
            val job = Kernel32Job.INSTANCE.CreateJobObject(null, null) ?: return@runCatching null

            // The whole point: when the last handle to this job closes — which happens when this
            // process dies, whatever the reason — the kernel terminates every process in it.
            val limits = JobObjectExtendedLimitInformation()
            limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            limits.write()

            val applied =
                Kernel32Job.INSTANCE.SetInformationJobObject(
                    job,
                    JOB_OBJECT_EXTENDED_LIMIT_INFORMATION,
                    limits.pointer,
                    limits.size(),
                )

            // A job without the kill flag is worse than none: it would look like protection and
            // provide none, so it is closed rather than kept.
            if (!applied) {
                Kernel32.INSTANCE.CloseHandle(job)
                null
            } else {
                job
            }
        }.getOrNull()
    }

    /** Job Object APIs and structures are not exposed by JNA's stock Kernel32 interface. */
    private interface Kernel32Job : StdCallLibrary {
        fun CreateJobObject(
            attributes: WinBase.SECURITY_ATTRIBUTES?,
            name: WString?,
        ): WinNT.HANDLE?

        fun SetInformationJobObject(
            job: WinNT.HANDLE,
            informationClass: Int,
            information: Pointer,
            informationLength: Int,
        ): Boolean

        fun AssignProcessToJobObject(
            job: WinNT.HANDLE,
            process: WinNT.HANDLE,
        ): Boolean

        companion object {
            val INSTANCE: Kernel32Job =
                Native.load("kernel32", Kernel32Job::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    @Structure.FieldOrder(
        "PerProcessUserTimeLimit",
        "PerJobUserTimeLimit",
        "LimitFlags",
        "MinimumWorkingSetSize",
        "MaximumWorkingSetSize",
        "ActiveProcessLimit",
        "Affinity",
        "PriorityClass",
        "SchedulingClass",
    )
    private open class JobObjectBasicLimitInformation : Structure() {
        @JvmField var PerProcessUserTimeLimit = WinNT.LARGE_INTEGER()
        @JvmField var PerJobUserTimeLimit = WinNT.LARGE_INTEGER()
        @JvmField var LimitFlags: Int = 0
        @JvmField var MinimumWorkingSetSize = BaseTSD.SIZE_T()
        @JvmField var MaximumWorkingSetSize = BaseTSD.SIZE_T()
        @JvmField var ActiveProcessLimit: Int = 0
        @JvmField var Affinity = BaseTSD.ULONG_PTR()
        @JvmField var PriorityClass: Int = 0
        @JvmField var SchedulingClass: Int = 0
    }

    @Structure.FieldOrder(
        "BasicLimitInformation",
        "IoInfo",
        "ProcessMemoryLimit",
        "JobMemoryLimit",
        "PeakProcessMemoryUsed",
        "PeakJobMemoryUsed",
    )
    private class JobObjectExtendedLimitInformation : Structure() {
        @JvmField var BasicLimitInformation = JobObjectBasicLimitInformation()
        @JvmField var IoInfo = WinNT.IO_COUNTERS()
        @JvmField var ProcessMemoryLimit = BaseTSD.SIZE_T()
        @JvmField var JobMemoryLimit = BaseTSD.SIZE_T()
        @JvmField var PeakProcessMemoryUsed = BaseTSD.SIZE_T()
        @JvmField var PeakJobMemoryUsed = BaseTSD.SIZE_T()
    }

    private const val PROCESS_SET_QUOTA = 0x0100
    private const val PROCESS_TERMINATE = 0x0001
    private const val JOB_OBJECT_EXTENDED_LIMIT_INFORMATION = 9
    private const val JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000
}
