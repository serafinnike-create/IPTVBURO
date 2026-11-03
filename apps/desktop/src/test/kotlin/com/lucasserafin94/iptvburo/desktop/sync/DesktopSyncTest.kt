package com.lucasserafin94.iptvburo.desktop.sync

import com.google.gson.JsonParser
import com.lucasserafin94.iptvburo.desktop.syncFavoriteFor
import com.lucasserafin94.iptvburo.sync.FavoriteSync
import com.lucasserafin94.iptvburo.sync.SyncClient
import com.lucasserafin94.iptvburo.sync.SyncProver
import com.lucasserafin94.iptvburo.sync.SyncResult
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class DesktopSyncTest {
    private object FakeProver : SyncProver {
        override val deviceId: String = "ABCD-EFGH-JKLM"

        override fun proof(nonce: String): String = "signed-$nonce"
    }

    private fun withSync(block: (DesktopSync, MockWebServer) -> Unit) {
        val server = MockWebServer().apply { start() }
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-sync-${UUID.randomUUID()}")
        try {
            val client = SyncClient(OkHttpClient(), server.url("/").toString(), FakeProver)
            block(DesktopSync(client, node, clock = { 1_000L }), server)
        } finally {
            server.shutdown()
            node.removeNode()
        }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `a device that never joined is not a member and so never syncs in the background`() =
        withSync { sync, server ->
            assertFalse(sync.isMember)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `joining makes the device a member, and leaving undoes it`() =
        withSync { sync, server ->
            server.enqueue(json("""{"ok":true,"members":2}"""))
            assertEquals(SyncResult.Success(2), sync.join("abcd efgh"))
            assertTrue(sync.isMember)
            val sent = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            assertEquals("ABCDEFGH", sent.get("code").asString, "the code was not normalised before sending")

            server.enqueue(json("""{"ok":true}"""))
            sync.leave()
            assertFalse(sync.isMember)
        }

    @Test
    fun `an exchange pushes this computer's favourites and brings the household's down`() =
        withSync { sync, server ->
            val fromPhone =
                FavoriteSync.localChanges(
                    listOf(FavoriteSync.Local("Ana", "series:dark", "Dark", null, "series")),
                    FavoriteSync.Ledger(),
                    nowMillis = 500L,
                ).records.single()
            server.enqueue(
                json(
                    """{"ok":true,"revision":4,"hasMore":false,"records":[""" +
                        """{"key":"${fromPhone.key}","updatedAt":500,"deleted":false,"data":${com.google.gson.JsonPrimitive(fromPhone.data)}}]}""",
                ),
            )
            val local = listOfNotNull(syncFavoriteFor("Ana", "movie:a-filha-de-zeus:2024"))

            val result = sync.exchange(local, localProfiles = listOf("Ana"))

            val outcome = assertIs<SyncResult.Success<DesktopSync.Outcome>>(result).value
            assertEquals(listOf("series:dark"), outcome.add.map { it.identityKey })
            val pushed = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            assertEquals(1, pushed.getAsJsonArray("records").size(), "this computer's favourite was not pushed")
            assertEquals(4, sync.loadLedger().revision, "the revision seen was not remembered")
        }

    @Test
    fun `being dropped from the household stops the background sync`() =
        withSync { sync, server ->
            server.enqueue(json("""{"ok":true,"members":2}"""))
            sync.join("ABCD-EFGH")
            server.enqueue(MockResponse().setResponseCode(409).setHeader("Content-Type", "application/json").setBody("""{"error":"not_in_group"}"""))

            sync.exchange(emptyList(), listOf("Ana"))

            assertFalse(sync.isMember)
        }

    @Test
    fun `a stored favourite key becomes a sync entry`() {
        val entry = syncFavoriteFor("Ana", "movie:a-filha-de-zeus:2024")!!
        assertEquals("movie", entry.kind)
        assertEquals(2024, entry.year)
        assertEquals("a filha de zeus", entry.title)
        assertEquals(null, syncFavoriteFor("Ana", "series:dark")?.year)
        assertNull(syncFavoriteFor("Ana", "garbage"))
    }
}
