package com.lucasserafin94.iptvburo.desktop.user

import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryPreferenceIdentityTest {
    @Test
    fun `the same provider id is independent in each section`() {
        val movieOnly = setOf(CategoryPreferenceIdentity.scoped(XtreamContentType.MOVIE, "1"))

        assertTrue(CategoryPreferenceIdentity.matches(movieOnly, XtreamContentType.MOVIE, "1"))
        assertFalse(CategoryPreferenceIdentity.matches(movieOnly, XtreamContentType.LIVE, "1"))
        assertFalse(CategoryPreferenceIdentity.matches(movieOnly, XtreamContentType.SERIES, "1"))
    }

    @Test
    fun `legacy ids keep their old protection during migration`() {
        val migrated = CategoryPreferenceIdentity.migrateLegacy(setOf("1"))

        XtreamContentType.entries.forEach { type ->
            assertTrue(CategoryPreferenceIdentity.matches(migrated, type, "1"))
        }
    }

    @Test
    fun `one migrated section can be changed without changing the others`() {
        val migrated = CategoryPreferenceIdentity.migrateLegacy(setOf("1"))
        val movieRestored = migrated - CategoryPreferenceIdentity.scoped(XtreamContentType.MOVIE, "1")

        assertFalse(CategoryPreferenceIdentity.matches(movieRestored, XtreamContentType.MOVIE, "1"))
        assertTrue(CategoryPreferenceIdentity.matches(movieRestored, XtreamContentType.LIVE, "1"))
        assertTrue(CategoryPreferenceIdentity.matches(movieRestored, XtreamContentType.SERIES, "1"))
    }

    @Test
    fun `provider punctuation cannot collide with the scope format`() {
        val id = "a:b,c|d/é"
        val stored = setOf(CategoryPreferenceIdentity.scoped(XtreamContentType.SERIES, id))

        assertTrue(CategoryPreferenceIdentity.matches(stored, XtreamContentType.SERIES, id))
        assertFalse(CategoryPreferenceIdentity.matches(stored, XtreamContentType.MOVIE, id))
    }
}
