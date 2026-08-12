package com.lucasserafin94.iptvburo.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adding a profile photo must not disturb the profiles already on the device.
 *
 * A profile's id is what favourites, playback progress and the encrypted metadata key are all filed
 * under. A migration that recreated the table — the usual way a column gets added carelessly — would
 * orphan every one of them, and the user would find their library apparently empty after an update.
 */
@RunWith(AndroidJUnit4::class)
class ProfilePhotoMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IptvBuroDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration6To7KeepsExistingProfilesAndDefaultsThePhotoToNull() {
        helper.createDatabase(DATABASE_NAME, 6).use { db ->
            db.execSQL(
                "INSERT INTO profiles(id, name, avatar_key, profile_type, language_tag, " +
                    "audio_language_tag, subtitle_language_tag, sort_order, created_at_epoch_millis) " +
                    "VALUES('profile-1', 'Lucas', 'ember', 'ADULT', 'pt-BR', 'pt-BR', 'pt-BR', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO profiles(id, name, avatar_key, profile_type, language_tag, " +
                    "audio_language_tag, subtitle_language_tag, sort_order, created_at_epoch_millis) " +
                    "VALUES('profile-2', 'Kids', 'ocean', 'KIDS', 'pt-BR', 'pt-BR', 'pt-BR', 1, 2)",
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            7,
            true,
            IptvBuroDatabase.MIGRATION_6_7,
        ).use { db ->
            db.query("SELECT COUNT(*) FROM profiles").use { cursor ->
                cursor.moveToFirst()
                assertEquals(
                    "Both profiles must survive: their ids key the user's whole library.",
                    2,
                    cursor.getInt(0),
                )
            }

            // Identity and settings intact, not merely the row count.
            db.query(
                "SELECT name, avatar_key, profile_type, photo_uri FROM profiles WHERE id = 'profile-1'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("Lucas", cursor.getString(0))
                assertEquals("ember", cursor.getString(1))
                assertEquals("ADULT", cursor.getString(2))
                assertTrue(
                    "A profile that never chose a photo must keep its drawn avatar.",
                    cursor.isNull(3),
                )
            }

            // The Kids flag is what hides adult content; losing it would be a safety failure.
            db.query("SELECT profile_type FROM profiles WHERE id = 'profile-2'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("KIDS", cursor.getString(0))
            }
        }
    }

    @Test
    fun migration7To8KeepsProfilesAndDefaultsTheSourceToNoPreference() {
        helper.createDatabase(DATABASE_NAME, 7).use { db ->
            db.execSQL(
                "INSERT INTO profiles(id, name, avatar_key, profile_type, language_tag, " +
                    "audio_language_tag, subtitle_language_tag, sort_order, created_at_epoch_millis, photo_uri) " +
                    "VALUES('profile-1', 'Lucas', 'ember', 'ADULT', 'pt-BR', 'pt-BR', 'pt-BR', 0, 1, " +
                    "'content://media/1')",
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            8,
            true,
            IptvBuroDatabase.MIGRATION_7_8,
        ).use { db ->
            db.query("SELECT name, photo_uri, source_id FROM profiles WHERE id = 'profile-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Lucas", cursor.getString(0))
                assertEquals(
                    "The photo added in the previous migration must survive this one.",
                    "content://media/1",
                    cursor.getString(1),
                )
                assertTrue(
                    "Every existing profile keeps working against whatever source is available; " +
                        "forcing a choice on a household with one playlist would be a setting " +
                        "with only one answer.",
                    cursor.isNull(2),
                )
            }
        }
    }

    @Test
    fun migration6To7AcceptsAPhotoAfterwards() {
        helper.createDatabase(DATABASE_NAME, 6).use { db ->
            db.execSQL(
                "INSERT INTO profiles(id, name, avatar_key, profile_type, language_tag, " +
                    "audio_language_tag, subtitle_language_tag, sort_order, created_at_epoch_millis) " +
                    "VALUES('profile-1', 'Lucas', 'ember', 'ADULT', 'pt-BR', 'pt-BR', 'pt-BR', 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            7,
            true,
            IptvBuroDatabase.MIGRATION_6_7,
        ).use { db ->
            db.execSQL("UPDATE profiles SET photo_uri = 'content://media/1' WHERE id = 'profile-1'")
            db.query("SELECT photo_uri FROM profiles WHERE id = 'profile-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("content://media/1", cursor.getString(0))
            }
        }
    }

    private companion object { const val DATABASE_NAME = "migration-profile-photo" }
}
