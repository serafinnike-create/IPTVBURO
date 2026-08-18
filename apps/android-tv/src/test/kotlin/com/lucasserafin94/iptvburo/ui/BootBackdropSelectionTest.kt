package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.data.repository.ProfileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootBackdropSelectionTest {
    @Test
    fun `a remembered adult profile may show its own covers before the profile list arrives`() {
        assertTrue(mayLoadEarlyBootBackdrop(ProfileType.ADULT))
        assertTrue(mayLoadEarlyBootBackdrop(ProfileType.GUEST))
    }

    @Test
    fun `a child's loading screen never starts on the catalogue's covers`() {
        assertFalse(mayLoadEarlyBootBackdrop(ProfileType.KIDS))
    }

    @Test
    fun `no remembered profile means no covers, because they would belong to nobody`() {
        assertFalse(mayLoadEarlyBootBackdrop(null))
    }

    @Test
    fun `the first profile arriving is not a switch, so early covers survive it`() {
        assertTrue(
            keepBootBackdropForArrivingProfile(
                currentActiveProfileId = null,
                arrivingProfileId = "adult",
                arrivingIsKids = false,
            ),
        )
    }

    @Test
    fun `an arriving Kids profile clears covers loaded before it was known`() {
        assertFalse(
            keepBootBackdropForArrivingProfile(
                currentActiveProfileId = null,
                arrivingProfileId = "kid",
                arrivingIsKids = true,
            ),
        )
    }

    @Test
    fun `switching to a different profile still clears the previous one's covers`() {
        assertFalse(
            keepBootBackdropForArrivingProfile(
                currentActiveProfileId = "adult",
                arrivingProfileId = "other",
                arrivingIsKids = false,
            ),
        )
    }

    @Test
    fun `the same profile re-emitting keeps the covers it already had`() {
        assertTrue(
            keepBootBackdropForArrivingProfile(
                currentActiveProfileId = "adult",
                arrivingProfileId = "adult",
                arrivingIsKids = false,
            ),
        )
    }

    @Test
    fun `loading backdrop keeps only distinct usable covers`() {
        val selected =
            selectBootBackdropUrls(
                listOf(null, "", "  https://img.example/a.jpg  ", "https://img.example/a.jpg", "https://img.example/b.jpg"),
            )

        assertEquals(
            listOf("https://img.example/a.jpg", "https://img.example/b.jpg"),
            selected,
        )
    }

    @Test
    fun `loading backdrop has a strict memory and request limit`() {
        val selected = selectBootBackdropUrls((1..40).map { "https://img.example/$it.jpg" })

        assertEquals(20, selected.size)
        assertEquals("https://img.example/20.jpg", selected.last())
    }
}
