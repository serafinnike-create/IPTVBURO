package com.lucasserafin94.iptvburo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucasserafin94.iptvburo.domain.model.ParentalLock
import com.lucasserafin94.iptvburo.domain.model.ParentalPin
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.catalogueGuardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "catalogue_guard",
)

/**
 * Which categories are hidden, which are locked, and the PIN that opens them.
 *
 * The three belong together because they are read together on every catalogue query, and the same
 * `ParentalLock` from `packages/domain-model` decides all of it — the same one Windows uses, so a
 * category the desktop treats as adult is treated the same here rather than by a second, drifting
 * rule.
 *
 * Hiding and locking are kept apart on purpose. Hiding is the user tidying a provider's several
 * hundred categories down to the ones they watch; locking is keeping a child out of one. A single
 * switch would force the two to mean the same thing.
 *
 * The PIN is never stored in the clear: only its salt and hash, via [ParentalPin]. Four digits is a
 * weak secret whatever is done to it, which is exactly why the stored form should not also be the
 * digits themselves — people reuse PINs.
 */
@Singleton
class CatalogueGuardPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : CatalogueGuard {
        private val dataStore = context.catalogueGuardDataStore

        private val preferences: Flow<Preferences> =
            dataStore.data.catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }

        /** Category ids the user chose not to see at all. */
        override val hiddenCategoryIds: Flow<Set<String>> =
            preferences.map { stored -> stored[HIDDEN_CATEGORY_IDS].orEmpty() }

        /** What needs the PIN. Empty locked set plus adult-locking off means nothing does. */
        override val parentalLock: Flow<ParentalLock> =
            preferences.map { stored ->
                ParentalLock(
                    lockedCategoryIds = stored[LOCKED_CATEGORY_IDS].orEmpty(),
                    // Defaults to on, matching the domain: a provider adds categories over time,
                    // and a lock covering only what existed when it was set quietly stops covering
                    // the new ones.
                    lockAdultCategories = stored[LOCK_ADULT] ?: true,
                )
            }

        /** Whether a PIN exists at all. Locking is only offered once one does. */
        override val hasPin: Flow<Boolean> = preferences.map { stored -> stored[PIN_HASH] != null }

        override suspend fun setCategoryHidden(
            categoryId: String,
            hidden: Boolean,
        ) {
            dataStore.edit { stored ->
                val current = stored[HIDDEN_CATEGORY_IDS].orEmpty()
                stored[HIDDEN_CATEGORY_IDS] =
                    if (hidden) current + categoryId else current - categoryId
            }
        }

        override suspend fun setCategoryLocked(
            categoryId: String,
            locked: Boolean,
        ) {
            dataStore.edit { stored ->
                val current = stored[LOCKED_CATEGORY_IDS].orEmpty()
                stored[LOCKED_CATEGORY_IDS] =
                    if (locked) current + categoryId else current - categoryId
            }
        }

        override suspend fun setLockAdultCategories(locked: Boolean) {
            dataStore.edit { stored -> stored[LOCK_ADULT] = locked }
        }

        /**
         * Sets or changes the PIN, returning false when [currentPin] is wrong.
         *
         * Changing requires the old one. Without that the lock would be a formality — anyone could
         * replace the PIN from the same screen that offers it.
         */
        override suspend fun setPin(
            newPin: String,
            currentPin: String?,
        ): Boolean {
            if (!ParentalPin.isWellFormed(newPin)) return false
            if (!verify(currentPin)) return false
            val salt = newSalt()
            val pin = ParentalPin.of(newPin, salt) ?: return false
            dataStore.edit { stored ->
                stored[PIN_SALT] = pin.salt
                stored[PIN_HASH] = pin.hash
            }
            return true
        }

        /**
         * Removes the PIN, returning false when [currentPin] is wrong.
         *
         * Clearing the locked set with it: a locked category with no PIN can never be opened, so
         * leaving the ids behind would strand them.
         */
        override suspend fun clearPin(currentPin: String): Boolean {
            if (!verify(currentPin)) return false
            dataStore.edit { stored ->
                stored.remove(PIN_SALT)
                stored.remove(PIN_HASH)
                stored.remove(LOCKED_CATEGORY_IDS)
            }
            return true
        }

        /** Whether [candidate] is the stored PIN. False when none is set, so nothing opens by accident. */
        override suspend fun checkPin(candidate: String): Boolean = storedPin()?.matches(candidate) == true

        /** True when there is no PIN to check against, which is what makes first-time setup possible. */
        private suspend fun verify(currentPin: String?): Boolean {
            val stored = storedPin() ?: return true
            return currentPin != null && stored.matches(currentPin)
        }

        private suspend fun storedPin(): ParentalPin? {
            val stored = preferences.first()
            val salt = stored[PIN_SALT] ?: return null
            val hash = stored[PIN_HASH] ?: return null
            return ParentalPin(salt = salt, hash = hash)
        }

        private fun newSalt(): String {
            val bytes = ByteArray(SALT_BYTES)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { byte -> "%02x".format(byte) }
        }

        private companion object {
            const val SALT_BYTES = 16

            val HIDDEN_CATEGORY_IDS = stringSetPreferencesKey("hidden_category_ids")
            val LOCKED_CATEGORY_IDS = stringSetPreferencesKey("locked_category_ids")
            val LOCK_ADULT = booleanPreferencesKey("lock_adult_categories")
            val PIN_SALT = stringPreferencesKey("parental_pin_salt")
            val PIN_HASH = stringPreferencesKey("parental_pin_hash")
        }
    }
