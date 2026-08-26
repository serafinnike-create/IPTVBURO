package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a Kids profile is not shown, beyond the explicitly adult.
 *
 * A parent who turns on a Kids profile does not expect it to offer horror. The words here are the
 * ones real provider lists use, in the three languages one list often mixes.
 *
 * The line this holds: horror is blocked for Kids and stays available to everybody else. It is a
 * genre a grown-up may well want, unlike adult content, so merging the two lists would put horror
 * behind the parental PIN for the whole household.
 */
class KidsGenreBlockingTest {
    @Test
    fun `horror and thriller categories are kept from a Kids profile`() {
        listOf("Terror", "TERROR | 4K", "Horror", "Suspense", "Thriller", "Filmes | Suspense")
            .forEach { category ->
                assertFalse(
                    FamilyContentPolicy.isAllowedForKids("Um filme", listOf(category)),
                    "a Kids profile must not be offered: $category",
                )
            }
    }

    @Test
    fun `the plural is caught without listing every form`() {
        // Providers write both, and a list that missed the plural would leave exactly the
        // categories a parent meant to exclude wide open.
        // "Suspenses" and "Thrillers" take a plain -s, which is the rule here. "Terrores"
        // does not — Portuguese pluralises -or as -ores — and teaching this a language's
        // morphology would be a worse trade than the category simply being written "Terror",
        // which is what lists actually do.
        assertFalse(FamilyContentPolicy.isAllowedForKids("x", listOf("Suspenses")))
        assertFalse(FamilyContentPolicy.isAllowedForKids("x", listOf("Thrillers")))
    }

    @Test
    fun `a childrens category is still allowed`() {
        // The check must not take out the catalogue it is meant to leave behind.
        listOf("Infantil", "Desenhos", "Kids", "Animação", "Família").forEach { category ->
            assertTrue(
                FamilyContentPolicy.isAllowedForKids("Um filme", listOf(category)),
                "a Kids profile has to keep: $category",
            )
        }
    }

    @Test
    fun `a word that merely contains the token is not blocked`() {
        // Matching a fragment would take out anything containing "terr" — "Mediterrâneo",
        // "Terra" — and a Kids profile that loses the documentaries is a defect of its own.
        assertTrue(FamilyContentPolicy.isAllowedForKids("Terra Nova", listOf("Documentários")))
        assertTrue(FamilyContentPolicy.isAllowedForKids("Mediterrâneo", listOf("Viagens")))
    }

    @Test
    fun `horror stays available to everybody who is not a Kids profile`() {
        // The reason this list is separate from the adult one. Horror behind the parental PIN
        // would be the app deciding what grown-ups may watch.
        assertFalse(
            FamilyContentPolicy.isExplicitAdultLabel("Terror"),
            "horror is not adult content and must not be locked for the household",
        )
        assertFalse(FamilyContentPolicy.isExplicitAdultLabel("Suspense"))
    }

    @Test
    fun `adult categories remain blocked for Kids as well`() {
        // The new rule must not have displaced the old one.
        listOf("Adulto", "XXX", "+18", "Erótico").forEach { category ->
            assertFalse(
                FamilyContentPolicy.isAllowedForKids("Um filme", listOf(category)),
                "still blocked: $category",
            )
        }
    }
}
