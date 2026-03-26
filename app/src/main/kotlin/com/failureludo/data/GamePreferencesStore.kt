package com.failureludo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class FeedbackSettings(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val masterVolume: Float = 0.8f
)

private val Context.feedbackPreferencesDataStore by preferencesDataStore(name = "feedback_preferences")

class GamePreferencesStore(private val context: Context) {

    private object Keys {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
    }

    val feedbackSettings: Flow<FeedbackSettings> = context.feedbackPreferencesDataStore.data
        .map { prefs -> prefs.toFeedbackSettings() }

    suspend fun updateFeedbackSettings(settings: FeedbackSettings) {
        context.feedbackPreferencesDataStore.edit { prefs ->
            prefs[Keys.SOUND_ENABLED] = settings.soundEnabled
            prefs[Keys.MUSIC_ENABLED] = settings.musicEnabled
            prefs[Keys.HAPTICS_ENABLED] = settings.hapticsEnabled
            prefs[Keys.MASTER_VOLUME] = settings.masterVolume.coerceIn(0f, 1f)
        }
    }

    private fun Preferences.toFeedbackSettings(): FeedbackSettings {
        return FeedbackSettings(
            soundEnabled = this[Keys.SOUND_ENABLED] ?: true,
            musicEnabled = this[Keys.MUSIC_ENABLED] ?: false,
            hapticsEnabled = this[Keys.HAPTICS_ENABLED] ?: true,
            masterVolume = (this[Keys.MASTER_VOLUME] ?: 0.8f).coerceIn(0f, 1f)
        )
    }
}
