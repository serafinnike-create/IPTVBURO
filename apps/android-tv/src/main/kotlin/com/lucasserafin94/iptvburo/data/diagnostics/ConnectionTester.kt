package com.lucasserafin94.iptvburo.data.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import com.lucasserafin94.iptvburo.data.security.SourceConnectionStore
import com.lucasserafin94.iptvburo.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Finding
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Severity
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import com.lucasserafin94.iptvburo.xtream.XtreamCredentials
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The connection test, from measurement to the sentences somebody reads.
 *
 * Why it exists: a viewer whose picture freezes cannot tell whether the fault is their Wi-Fi, their
 * provider or the app. Without an answer they conclude the app is broken, so every reading here
 * becomes a [Finding] carrying both a value and what to do about it.
 *
 * The thresholds come from the shared domain model, so the phone, the television and Windows all
 * say the same thing about the same connection.
 */
/**
 * The two network measurements, behind a seam.
 *
 * Exists so the tester can be exercised without a real socket: [XtreamClient] is final and the
 * readings here decide what somebody is told about their own connection, which is worth testing
 * directly rather than through an integration that needs a server.
 */
interface ProviderProbe {
    /** Bytes moved and the milliseconds they took, or null when nothing could be read. */
    fun transfer(
        credentials: XtreamCredentials,
        budgetMillis: Long,
    ): Pair<Long, Long>?

    /** One round-trip time per successful attempt; a shorter list is the loss. */
    fun latency(
        credentials: XtreamCredentials,
        attempts: Int,
    ): List<Int>
}

/** The real one, which asks the provider. */
@Singleton
class XtreamProviderProbe
    @Inject
    constructor(
        private val client: XtreamClient,
    ) : ProviderProbe {
        override fun transfer(
            credentials: XtreamCredentials,
            budgetMillis: Long,
        ) = runCatching { client.measureTransfer(credentials, budgetMillis) }.getOrNull()

        override fun latency(
            credentials: XtreamCredentials,
            attempts: Int,
        ) = runCatching { client.measureLatency(credentials, attempts) }.getOrDefault(emptyList())
    }

@Singleton
class ConnectionTester
    @Inject
    constructor(
        /**
         * Only ever read for the local network facts.
         *
         * Nullable so the measurements can be tested without an Android runtime: those are the
         * part that decides what somebody is told about their own connection, and they deserve to
         * be exercised directly. A null context degrades to "unknown", exactly as a device whose
         * connectivity service is unavailable already does.
         */
        @param:ApplicationContext private val context: Context?,
        private val probe: ProviderProbe,
        private val sourceConnectionStore: SourceConnectionStore,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** Everything the test learned, in the order it should be read. */
        data class Report(
            val findings: List<Finding>,
            val qualityCeiling: String,
            val address: String? = null,
            val netmask: String? = null,
            val gateway: String? = null,
        ) {
            val overall: Severity
                get() = ConnectionDiagnostics.overall(findings)
        }

        /**
         * Runs every check.
         *
         * Suspends on the IO dispatcher: it deliberately spends several seconds on the network,
         * because a shorter measurement is a less honest one.
         *
         * @param sourceId the subscription to measure against, or null when none is configured.
         * @param loadedItems how many titles the catalogue holds, so an empty list is reported.
         */
        suspend fun run(
            sourceId: String?,
            loadedItems: Int,
        ): Report =
            withContext(ioDispatcher) {
                val findings = mutableListOf<Finding>()
                val credentials = sourceId?.let { id -> sourceConnectionStore.readXtream(id) }

                val mbps =
                    credentials?.let { secret ->
                        probe
                            .transfer(secret, BUDGET_MILLIS)
                            ?.let { (bytes, millis) ->
                                ConnectionDiagnostics.megabitsPerSecond(bytes, millis)
                            }
                    }
                findings +=
                    Finding(
                        id = "download",
                        severity = ConnectionDiagnostics.downloadVerdict(mbps),
                        detail = mbps?.let { formatMbps(it) } ?: EM_DASH,
                        advice = ConnectionDiagnostics.qualityCeiling(mbps),
                    )

                val samples =
                    credentials?.let { secret ->
                        probe.latency(secret, PING_ATTEMPTS)
                    } ?: emptyList()
                val ping = samples.sorted().takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
                findings +=
                    Finding(
                        id = "ping",
                        severity = ConnectionDiagnostics.pingVerdict(ping),
                        detail = ping?.let { "$it ms" } ?: EM_DASH,
                    )

                // Counted rather than thrown: a connection losing one request in ten is exactly
                // what the viewer needs told, and an exception would replace that with "failed".
                val loss =
                    credentials?.let {
                        (PING_ATTEMPTS - samples.size) * 100.0 / PING_ATTEMPTS
                    }
                findings +=
                    Finding(
                        id = "loss",
                        severity = ConnectionDiagnostics.packetLossVerdict(loss),
                        detail = loss?.let { "${formatPercent(it)} de $PING_ATTEMPTS" } ?: EM_DASH,
                    )

                findings += catalogueFinding(sourceId, loadedItems)
                findings += linkFinding()
                findings += memoryFinding()

                val addresses = addresses()
                Report(
                    findings = findings,
                    qualityCeiling = ConnectionDiagnostics.qualityCeiling(mbps),
                    address = addresses.first,
                    netmask = addresses.second,
                    gateway = addresses.third,
                )
            }

        /**
         * Whether the list itself loaded.
         *
         * A connection can measure perfectly while the catalogue is empty — an expired
         * subscription, a provider that moved. Without this the screen would say everything is
         * fine while the app shows nothing at all, which is the worst thing it could say.
         */
        private fun catalogueFinding(
            sourceId: String?,
            loadedItems: Int,
        ): Finding =
            when {
                sourceId == null -> Finding("catalogue", Severity.WARNING, EM_DASH, advice = "signed-out")
                loadedItems <= 0 -> Finding("catalogue", Severity.PROBLEM, "0", advice = "empty")
                else -> Finding("catalogue", Severity.GOOD, loadedItems.toString())
            }

        /**
         * Wired or wireless.
         *
         * Not a fault, but worth naming: wireless is the single most common explanation for a
         * connection that measures well and still stutters.
         */
        private fun linkFinding(): Finding {
            val kind = linkKind()
            return Finding(
                id = "link",
                severity =
                    when (kind) {
                        "none" -> Severity.PROBLEM
                        "wireless" -> Severity.WARNING
                        else -> Severity.GOOD
                    },
                detail = EM_DASH,
                advice = kind,
            )
        }

        private fun linkKind(): String {
            val manager =
                runCatching {
                    context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                }.getOrNull() ?: return "unknown"
            val capabilities =
                runCatching {
                    manager.getNetworkCapabilities(manager.activeNetwork)
                }.getOrNull() ?: return "none"
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "wired"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wireless"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "wireless"
                else -> "unknown"
            }
        }

        /**
         * The address the device holds on its local network, plus mask and gateway.
         *
         * These are what a support call asks for. Read locally, so they work with no Internet at
         * all — which is exactly when somebody opens this screen.
         */
        private fun addresses(): Triple<String?, String?, String?> {
            val manager =
                runCatching {
                    context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                }.getOrNull() ?: return Triple(null, null, null)
            val link: LinkProperties =
                runCatching {
                    manager.getLinkProperties(manager.activeNetwork)
                }.getOrNull() ?: return Triple(null, null, null)

            val binding =
                link.linkAddresses.firstOrNull { entry -> entry.address is Inet4Address }
            val gateway =
                link.routes
                    .firstOrNull { route -> route.isDefaultRoute && route.gateway is Inet4Address }
                    ?.gateway
                    ?.hostAddress
            return Triple(
                binding?.address?.hostAddress,
                binding?.prefixLength?.let(::netmaskFor),
                gateway,
            )
        }

        /** A prefix length as the dotted mask people recognise from their router's page. */
        private fun netmaskFor(prefixLength: Int): String {
            val mask = if (prefixLength <= 0) 0 else (-1 shl (32 - prefixLength))
            return listOf(24, 16, 8, 0).joinToString(".") { shift ->
                ((mask ushr shift) and 0xFF).toString()
            }
        }

        /**
         * Memory this process can still take.
         *
         * The runtime's headroom rather than the device's, because that is what actually stops
         * playback: a phone with free memory and an exhausted heap still stutters, and the viewer
         * needs to be told to restart the app rather than to buy a new phone.
         */
        private fun memoryFinding(): Finding {
            val runtime = Runtime.getRuntime()
            val used = (runtime.totalMemory() - runtime.freeMemory()) / MEGABYTE
            val free = (runtime.maxMemory() / MEGABYTE) - used
            return Finding(
                id = "memory",
                severity =
                    if (free < ConnectionDiagnostics.LOW_MEMORY_MEGABYTES) {
                        Severity.WARNING
                    } else {
                        Severity.GOOD
                    },
                detail = "$used MB / ${used + free} MB",
            )
        }

        /** One decimal, because a second says nothing a viewer can act on. */
        private fun formatMbps(value: Double): String = "${(value * 10).toLong() / 10.0} Mbit/s"

        private fun formatPercent(value: Double): String = "${(value * 10).toLong() / 10.0}%"

        private companion object {
            /**
             * How long a diagnostic transfer may run.
             *
             * Long enough to outlast the local buffer and measure the network, short enough that
             * somebody waiting on the screen does not think it has hung.
             */
            const val BUDGET_MILLIS = 6_000L

            /** Enough round trips to see loss without making the screen wait. */
            const val PING_ATTEMPTS = 8

            const val MEGABYTE = 1024L * 1024L

            /** Shown where a reading is missing — never a zero, which would read as a measurement. */
            const val EM_DASH = "—"
        }
    }
