package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionsStringsTest {
    private val languages = DesktopLanguage.entries

    @Test
    fun `every language translates the subscriptions screen`() {
        languages.forEach { language ->
            val text = DesktopStrings.of(language)
            listOf(
                text.subscriptions,
                text.subscriptionsDemoNotice,
                text.subscriptionsWhereToWatch,
                text.subscriptionsInYourLibrary,
                text.subscriptionsIncludedInSubscription,
                text.subscriptionsFreeWithAds,
                text.subscriptionsRent,
                text.subscriptionsBuy,
                text.subscriptionsRequiresSubscription,
                text.subscriptionsUnavailable,
                text.subscriptionsOpenProvider,
                text.subscriptionsMyServices,
                text.subscriptionsRegion,
                text.subscriptionsEmptyBody,
                text.subscriptionsBrowseByService,
                text.subscriptionsNoShelves,
                text.subscriptionsBackToServices,
                text.subscriptionsSelectedTitle,
            ).forEach { value ->
                assertTrue(value.isNotBlank(), "blank string for $language")
            }
        }
    }

    /**
     * Catches the copy-paste failure this many parallel blocks invites: a language keeping the
     * English wording because the row was duplicated and never translated.
     */
    @Test
    fun `the non-english languages are actually translated`() {
        val english = DesktopStrings.of(DesktopLanguage.ENGLISH)
        languages.filter { it != DesktopLanguage.ENGLISH }.forEach { language ->
            val text = DesktopStrings.of(language)
            assertTrue(
                text.subscriptionsWhereToWatch != english.subscriptionsWhereToWatch,
                "$language still shows the English 'where to watch'",
            )
            assertTrue(
                text.subscriptionsInYourLibrary != english.subscriptionsInYourLibrary,
                "$language still shows the English 'already in your list'",
            )
            assertTrue(
                text.subscriptionsDemoNotice != english.subscriptionsDemoNotice,
                "$language still shows the English demo notice",
            )
            assertTrue(
                text.subscriptionsBrowseByService != english.subscriptionsBrowseByService,
                "$language still shows the English 'by service'",
            )
            assertTrue(
                text.subscriptionsBackToServices != english.subscriptionsBackToServices,
                "$language still shows the English 'back to services'",
            )
        }
    }

    /** DEMO is the one label that must not be translated — it has to read the same everywhere. */
    @Test
    fun `the demo badge is the same word in every language`() {
        languages.forEach { language ->
            assertEquals("DEMO", DesktopStrings.of(language).subscriptionsDemoBadge)
        }
    }
}
