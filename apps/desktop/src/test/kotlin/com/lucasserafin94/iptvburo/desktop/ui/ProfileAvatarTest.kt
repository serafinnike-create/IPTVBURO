package com.lucasserafin94.iptvburo.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileAvatarTest {
    @Test
    fun `every avatar is distinct`() {
        assertEquals(
            BURO_AVATARS.size,
            BURO_AVATARS.map(BuroAvatar::id).toSet().size,
            "ids must be unique",
        )
        assertEquals(
            BURO_AVATARS.size,
            BURO_AVATARS.map(BuroAvatar::motif).toSet().size,
            "two avatars sharing a motif would be told apart only by colour",
        )
    }

    /**
     * Profiles store an index, so the order is saved data. Appending is safe; reordering would
     * silently change the face of every existing profile.
     */
    @Test
    fun `the first eight positions keep the faces the emoji set had`() {
        assertEquals(
            listOf("clapper", "popcorn", "rocket", "fox", "moon", "ball", "guitar", "cat"),
            BURO_AVATARS.take(8).map(BuroAvatar::id),
        )
    }

    @Test
    fun `an index beyond the set still resolves to an avatar`() {
        // A stored index is no longer clamped on load, so drawing has to cope with any value.
        assertEquals(BURO_AVATARS[0], avatarAt(BURO_AVATARS.size))
        assertEquals(BURO_AVATARS[1], avatarAt(BURO_AVATARS.size + 1))
    }

    @Test
    fun `a negative index still resolves to an avatar`() {
        assertEquals(BURO_AVATARS.last(), avatarAt(-1))
        assertEquals(BURO_AVATARS[0], avatarAt(-BURO_AVATARS.size))
    }

    @Test
    fun `the set is large enough to be worth picking from`() {
        assertTrue(BURO_AVATARS.size >= 12, "was ${BURO_AVATARS.size}")
    }

    /** The shade is the unlit side of the sphere; if it were not darker there would be no volume. */
    @Test
    fun `each shade is darker than its base`() {
        BURO_AVATARS.forEach { avatar ->
            val base = avatar.base.red + avatar.base.green + avatar.base.blue
            val shade = avatar.shade.red + avatar.shade.green + avatar.shade.blue
            assertTrue(shade < base, "${avatar.id}: shade must be darker than base")
        }
    }
}
