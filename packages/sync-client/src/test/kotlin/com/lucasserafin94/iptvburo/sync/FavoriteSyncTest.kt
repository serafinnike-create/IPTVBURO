package com.lucasserafin94.iptvburo.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteSyncTest {
    private val film = FavoriteSync.Local("Ana", "movie:a-filha-de-zeus:2024", "A Filha de Zeus", 2024, "movie")
    private val series = FavoriteSync.Local("Ana", "series:dark", "Dark", null, "series")

    @Test
    fun `a new favourite is pushed once`() {
        val first = FavoriteSync.localChanges(listOf(film), FavoriteSync.Ledger(), nowMillis = 1_000)
        assertEquals(1, first.records.size)
        assertEquals(false, first.records.single().deleted)

        val again = FavoriteSync.localChanges(listOf(film), first.ledger, nowMillis = 2_000)
        assertTrue("an unchanged favourite was pushed again", again.records.isEmpty())
    }

    @Test
    fun `a removed favourite is pushed as a tombstone that still names the title`() {
        val added = FavoriteSync.localChanges(listOf(film), FavoriteSync.Ledger(), nowMillis = 1_000).ledger

        val removed = FavoriteSync.localChanges(emptyList(), added, nowMillis = 2_000)

        val tombstone = removed.records.single()
        assertTrue(tombstone.deleted)
        assertEquals(film.identityKey, (SyncEntryCodec.decode(tombstone.data) as SyncEntry.Favorite).identityKey)
    }

    @Test
    fun `another device's favourite arrives, and its removal goes away again`() {
        val phone = FavoriteSync.localChanges(listOf(series), FavoriteSync.Ledger(), nowMillis = 1_000)

        val tv = FavoriteSync.applyRemote(phone.records, FavoriteSync.Ledger(), revision = 7, localProfiles = listOf("ana"))
        assertEquals(listOf(series.copy(profile = "ana")), tv.add)
        assertEquals(7, tv.ledger.revision)

        val phoneRemoved = FavoriteSync.localChanges(emptyList(), phone.ledger, nowMillis = 2_000)
        val tvAfter = FavoriteSync.applyRemote(phoneRemoved.records, tv.ledger, revision = 8, localProfiles = listOf("ana"))
        assertEquals(1, tvAfter.remove.size)
        assertTrue(tvAfter.add.isEmpty())
    }

    @Test
    fun `this device's own push coming back changes nothing`() {
        val pushed = FavoriteSync.localChanges(listOf(film), FavoriteSync.Ledger(), nowMillis = 1_000)

        val echoed = FavoriteSync.applyRemote(pushed.records, pushed.ledger, revision = 3, localProfiles = listOf("Ana"))

        assertTrue(echoed.add.isEmpty())
        assertTrue(echoed.remove.isEmpty())
    }

    @Test
    fun `an older edit never undoes a newer one`() {
        val removedHere =
            FavoriteSync.localChanges(
                emptyList(),
                FavoriteSync.localChanges(listOf(film), FavoriteSync.Ledger(), nowMillis = 1_000).ledger,
                nowMillis = 5_000,
            )
        val staleAdd = FavoriteSync.localChanges(listOf(film), FavoriteSync.Ledger(), nowMillis = 3_000).records

        val result = FavoriteSync.applyRemote(staleAdd, removedHere.ledger, revision = 9, localProfiles = listOf("Ana"))

        assertTrue("a stale favourite came back", result.add.isEmpty())
    }

    @Test
    fun `favourites of a profile this device does not have are left alone`() {
        val other = FavoriteSync.localChanges(listOf(film.copy(profile = "Bruno")), FavoriteSync.Ledger(), 1_000)

        val result = FavoriteSync.applyRemote(other.records, FavoriteSync.Ledger(), revision = 2, localProfiles = listOf("Ana"))

        assertTrue(result.add.isEmpty())
        assertEquals(2, result.ledger.revision)
    }

    @Test
    fun `profile names match without case or surrounding space`() {
        val pushed = FavoriteSync.localChanges(listOf(film.copy(profile = "  ANA ")), FavoriteSync.Ledger(), 1_000)

        val result = FavoriteSync.applyRemote(pushed.records, FavoriteSync.Ledger(), revision = 1, localProfiles = listOf("Ana"))

        assertEquals("Ana", result.add.single().profile)
    }

    @Test
    fun `a record whose key does not match its content is ignored`() {
        val forged = FavoriteSync.localChanges(listOf(film), FavoriteSync.Ledger(), 1_000).records.single().copy(key = "AAAAAAAAAAAA")

        val result = FavoriteSync.applyRemote(listOf(forged), FavoriteSync.Ledger(), revision = 1, localProfiles = listOf("Ana"))

        assertTrue(result.add.isEmpty())
    }
}
