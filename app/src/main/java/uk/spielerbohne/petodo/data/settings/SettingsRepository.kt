package uk.spielerbohne.petodo.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.spielerbohne.petodo.domain.nag.QuietHours
import java.io.IOException
import java.time.LocalTime

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Einstellungen in DataStore.
 *
 * Beschädigte oder fehlende Dateien verhindern den Start nicht (Projektplan,
 * Abschnitt 13): Ein Lesefehler fällt auf die Vorgabewerte zurück, statt zu werfen.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val QUIET_START = stringPreferencesKey("quiet_hours_start")
        val QUIET_END = stringPreferencesKey("quiet_hours_end")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    private val preferences: Flow<Preferences> = store.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }

    val quietHours: Flow<QuietHours> = preferences.map { it.toQuietHours() }

    val onboardingCompleted: Flow<Boolean> = preferences.map {
        it[Keys.ONBOARDING_DONE] ?: false
    }

    /** Einmaliges Lesen für Receiver und Worker, die keinen Flow beobachten können. */
    suspend fun currentQuietHours(): QuietHours = preferences.first().toQuietHours()

    suspend fun setQuietHours(start: LocalTime, end: LocalTime, enabled: Boolean) {
        store.edit { preferences ->
            preferences[Keys.QUIET_START] = start.toString()
            preferences[Keys.QUIET_END] = end.toString()
            preferences[Keys.QUIET_ENABLED] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        store.edit { it[Keys.ONBOARDING_DONE] = completed }
    }

    private fun Preferences.toQuietHours(): QuietHours = QuietHours(
        start = parseTime(this[Keys.QUIET_START]) ?: QuietHours.DEFAULT.start,
        end = parseTime(this[Keys.QUIET_END]) ?: QuietHours.DEFAULT.end,
        enabled = this[Keys.QUIET_ENABLED] ?: QuietHours.DEFAULT.enabled,
    )

    /** Ein unlesbarer Wert ist kein Grund abzustürzen — dann gilt die Vorgabe. */
    private fun parseTime(value: String?): LocalTime? =
        value?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
}
