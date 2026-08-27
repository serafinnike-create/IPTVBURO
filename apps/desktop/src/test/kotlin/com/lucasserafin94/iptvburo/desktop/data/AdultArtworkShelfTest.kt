package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.metadata.AdultArtworkClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * Covers fetched for a grid, rather than for the one title on a details page.
 *
 * The grid is what makes this hard. It draws a hundred cards, rebuilds them on every scroll, and
 * asks again for the same title each time it comes back into view — so the thing worth pinning is
 * not that a lookup works, but that it happens once.
 */
class AdultArtworkShelfTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val requests = AtomicInteger()

    @BeforeTest
    fun setUp() {
        requests.set(0)
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests.incrementAndGet()
                    val wanted = request.requestUrl?.queryParameter("parse").orEmpty()
                    return if (wanted.contains("desconhecido")) {
                        json("""{"data":[]}""")
                    } else {
                        json("""{"data":[{"poster":"http://covers.invalid/${wanted.take(8)}.jpg"}]}""")
                    }
                }
            }
        server.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun shelf(
        maximumInFlight: Int = 4,
        onFound: () -> Unit = {},
    ) = AdultArtworkShelf(
        client = AdultArtworkClient(apiKey = "chave-sintetica", baseUrl = server.url("/").toString().toHttpUrl()),
        scope = scope,
        maximumInFlight = maximumInFlight,
        onFound = onFound,
    )

    /** Nothing is known on the first ask, so the card draws its readable fallback meanwhile. */
    @Test
    fun `the first ask returns nothing and starts a lookup`() {
        val shelf = shelf()

        assertNull(shelf.posterFor("Filme Um"))
        waitFor { shelf.lookupCount() == 1 }
        assertEquals("http://covers.invalid/Filme Um.jpg", shelf.posterFor("Filme Um"))
    }

    /**
     * The whole reason this class exists. A grid asks for the same title on every scroll, and
     * without the cache that is a request per card per scroll against the viewer's own key.
     */
    @Test
    fun `a title already looked up is never asked about twice`() {
        val shelf = shelf()
        shelf.posterFor("Filme Um")
        waitFor { shelf.lookupCount() == 1 }

        repeat(50) { shelf.posterFor("Filme Um") }
        Thread.sleep(80)

        assertEquals(1, requests.get(), "a grelha pediu de novo o que ja sabia")
    }

    /**
     * A miss is remembered too. A provider's own naming resolves rarely, so asking again on every
     * scroll would spend the whole budget on titles that never will.
     */
    @Test
    fun `a title the source does not know is not asked about again`() {
        val shelf = shelf()
        shelf.posterFor("titulo desconhecido")
        waitFor { shelf.lookupCount() == 1 }

        repeat(20) { assertNull(shelf.posterFor("titulo desconhecido")) }
        Thread.sleep(80)

        assertEquals(1, requests.get(), "um titulo sem capa foi perguntado outra vez")
    }

    /** Several cards carry the same title at different qualities and all draw at once. */
    @Test
    fun `cards asking at the same instant produce one lookup`() {
        val shelf = shelf()

        repeat(20) { shelf.posterFor("Filme Um") }
        waitFor { shelf.lookupCount() == 1 }
        Thread.sleep(80)

        assertEquals(1, requests.get(), "cartoes simultaneos dispararam pedidos repetidos")
    }

    /**
     * A hundred simultaneous requests at a service that rate-limits gets the page refused rather
     * than answered slowly, so the shelf holds a small number in flight.
     */
    @Test
    fun `no more than the permitted number of lookups run at once`() {
        val peak = AtomicInteger()
        val live = AtomicInteger()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val now = live.incrementAndGet()
                    peak.updateAndGet { seen -> maxOf(seen, now) }
                    Thread.sleep(40)
                    live.decrementAndGet()
                    return json("""{"data":[{"poster":"http://covers.invalid/x.jpg"}]}""")
                }
            }
        val shelf = shelf(maximumInFlight = 3)

        (1..24).forEach { index -> shelf.posterFor("Filme $index") }
        waitFor(timeoutMs = 15_000) { shelf.lookupCount() == 24 }

        assertTrue(peak.get() <= 3, "chegou a ${peak.get()} pedidos ao mesmo tempo, o limite era 3")
    }

    /** Without the signal a card drawn before its answer would keep the fallback until a scroll. */
    @Test
    fun `an arriving cover asks the screen to redraw`() {
        val redraws = AtomicInteger()
        val shelf = shelf(onFound = { redraws.incrementAndGet() })

        shelf.posterFor("Filme Um")
        waitFor { shelf.lookupCount() == 1 }

        assertEquals(1, redraws.get())
    }

    /** A blank title is not a question worth asking. */
    @Test
    fun `a blank title never reaches the network`() {
        val shelf = shelf()

        assertNull(shelf.posterFor("   "))
        Thread.sleep(60)
        assertEquals(0, requests.get())
    }

    private fun json(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun waitFor(
        timeoutMs: Long = 5_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("a condicao nunca aconteceu em ${timeoutMs}ms")
    }
}
