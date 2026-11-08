package com.smarted.tv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smarted.tv.core.model.UserPreferences
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "smarted_settings_v3")

/**
 * Preferências persistidas chave-a-chave (sem blobs JSON corrompíveis).
 * Valores inválidos caem no padrão em vez de quebrar a inicialização.
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsStore

    suspend fun read(): UserPreferences {
        val prefs = store.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }.first()
        return UserPreferences(
            audioLanguage = prefs[Key.AUDIO] ?: "pt",
            subtitleLanguage = prefs[Key.SUB] ?: "pt",
            subtitlesEnabled = prefs[Key.SUBS_ON] ?: true,
            subtitleSizePercent = (prefs[Key.SUB_SIZE] ?: 100).coerceIn(75, 150),
            resumePlayback = prefs[Key.RESUME] ?: true,
            autoplayNextEpisode = prefs[Key.AUTOPLAY] ?: true,
            seekBackSeconds = (prefs[Key.SEEK_BACK] ?: 10).coerceIn(5, 60),
            seekForwardSeconds = (prefs[Key.SEEK_FWD] ?: 30).coerceIn(5, 120),
            previewPlayerEnabled = prefs[Key.PREVIEW] ?: true,
            epgWindowHours = (prefs[Key.EPG_WINDOW] ?: 24).coerceIn(3, 72),
            elementumEnabled = prefs[Key.ELM_ON] ?: false,
            elementumHost = prefs[Key.ELM_HOST]?.takeIf(String::isNotBlank) ?: "127.0.0.1",
            elementumPort = (prefs[Key.ELM_PORT] ?: 65064).coerceIn(1, 65535),
            autoUpdateMinutes = (prefs[Key.AUTO_UPDATE] ?: 720).let { if (it < 0) 0 else it.coerceIn(15, 10080) },
        )
    }

    suspend fun write(p: UserPreferences) {
        store.edit {
            it[Key.AUDIO] = p.audioLanguage
            it[Key.SUB] = p.subtitleLanguage
            it[Key.SUBS_ON] = p.subtitlesEnabled
            it[Key.SUB_SIZE] = p.subtitleSizePercent
            it[Key.RESUME] = p.resumePlayback
            it[Key.AUTOPLAY] = p.autoplayNextEpisode
            it[Key.SEEK_BACK] = p.seekBackSeconds
            it[Key.SEEK_FWD] = p.seekForwardSeconds
            it[Key.PREVIEW] = p.previewPlayerEnabled
            it[Key.EPG_WINDOW] = p.epgWindowHours
            it[Key.ELM_ON] = p.elementumEnabled
            it[Key.ELM_HOST] = p.elementumHost
            it[Key.ELM_PORT] = p.elementumPort
            it[Key.AUTO_UPDATE] = p.autoUpdateMinutes
        }
    }

    private object Key {
        val AUDIO = stringPreferencesKey("audio_lang")
        val SUB = stringPreferencesKey("subtitle_lang")
        val SUBS_ON = booleanPreferencesKey("subs_on")
        val SUB_SIZE = intPreferencesKey("subtitle_size")
        val RESUME = booleanPreferencesKey("resume")
        val AUTOPLAY = booleanPreferencesKey("autoplay_next")
        val SEEK_BACK = intPreferencesKey("seek_back")
        val SEEK_FWD = intPreferencesKey("seek_forward")
        val PREVIEW = booleanPreferencesKey("preview_player")
        val EPG_WINDOW = intPreferencesKey("epg_window")
        val ELM_ON = booleanPreferencesKey("elementum_enabled")
        val ELM_HOST = stringPreferencesKey("elementum_host")
        val ELM_PORT = intPreferencesKey("elementum_port")
        val AUTO_UPDATE = intPreferencesKey("auto_update_minutes")
    }
}
