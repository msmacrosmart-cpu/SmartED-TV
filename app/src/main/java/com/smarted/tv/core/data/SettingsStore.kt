package com.smarted.tv.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Configurações do app.
 *
 * A URL do repositório ativo é gravada em **SharedPreferences** (síncrono, leitura
 * instantânea no start) e espelhada no Room pela camada de dados. Desse jeito o app
 * abre "pronto para uso" na primeira composição, sem esperar I/O.
 */
class SettingsStore(context: Context) : com.smarted.tv.ui.state.SettingsStoreLike {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readAll())
    override val state: StateFlow<Settings> = _state.asStateFlow()

    override val settings: Settings get() = _state.value

    private fun readAll(): Settings = Settings(
        activeRepoUrl = prefs.getString(KEY_REPO_URL, DEFAULT_REPO_URL) ?: DEFAULT_REPO_URL,
        activeRepoName = prefs.getString(KEY_REPO_NAME, "Brazuca Play") ?: "Brazuca Play",
        brazucaEnabled = prefs.getBoolean(KEY_BRAZUCA_ENABLED, true),
        tmdbKey = prefs.getString(KEY_TMDB_KEY, "") ?: "",
        tmdbLanguage = prefs.getString(KEY_TMDB_LANG, "pt-BR") ?: "pt-BR",
        themeDark = prefs.getBoolean(KEY_THEME_DARK, true),
        accentId = prefs.getString(KEY_ACCENT, "fuchsia") ?: "fuchsia",
        fontScale = prefs.getFloat(KEY_FONT_SCALE, 1f),
        overscanPercent = prefs.getFloat(KEY_OVERSCAN, 1.5f),
        epgUrl = prefs.getString(KEY_EPG_URL, DEFAULT_EPG_URL) ?: DEFAULT_EPG_URL,
        epgAutoload = prefs.getBoolean(KEY_EPG_AUTOLOAD, true),
        ytNoAds = prefs.getBoolean(KEY_YT_NO_ADS, true),
        autoTrailer = prefs.getBoolean(KEY_AUTO_TRAILER, false),
        autoplayNext = prefs.getBoolean(KEY_AUTOPLAY_NEXT, true),
        preferredLanguage = prefs.getString(KEY_PREF_LANG, "pt-BR") ?: "pt-BR",
        groupLanguageVariants = prefs.getBoolean(KEY_GROUP_LANG, true),
        hideWatched = prefs.getBoolean(KEY_HIDE_WATCHED, false),
        indexerAutoUpdate = prefs.getBoolean(KEY_INDEXER_AUTO, true),
        indexerIntervalHours = prefs.getInt(KEY_INDEXER_INTERVAL, 6),
        maintenanceEnabled = prefs.getBoolean(KEY_MAINTENANCE, true),
        sourceScanEnabled = prefs.getBoolean(KEY_SOURCE_SCAN, true),
        onboardedAt = prefs.getLong(KEY_ONBOARDED, 0L),
        playerAspectMode = prefs.getString(KEY_ASPECT, "fit") ?: "fit",
        interfaceSounds = prefs.getBoolean(KEY_SOUNDS, true),
        showHero = prefs.getBoolean(KEY_HERO, true),
        reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, false),
    )

    override fun update(block: (Settings) -> Settings) {
        val next = block(_state.value)
        write(next)
        _state.value = next
    }

    override fun setRepository(url: String, name: String) =
        update { it.copy(activeRepoUrl = url.trim(), activeRepoName = name.trim()) }

    private fun write(s: Settings) {
        prefs.edit()
            .putString(KEY_REPO_URL, s.activeRepoUrl)
            .putString(KEY_REPO_NAME, s.activeRepoName)
            .putBoolean(KEY_BRAZUCA_ENABLED, s.brazucaEnabled)
            .putString(KEY_TMDB_KEY, s.tmdbKey)
            .putString(KEY_TMDB_LANG, s.tmdbLanguage)
            .putBoolean(KEY_THEME_DARK, s.themeDark)
            .putString(KEY_ACCENT, s.accentId)
            .putFloat(KEY_FONT_SCALE, s.fontScale)
            .putFloat(KEY_OVERSCAN, s.overscanPercent)
            .putString(KEY_EPG_URL, s.epgUrl)
            .putBoolean(KEY_EPG_AUTOLOAD, s.epgAutoload)
            .putBoolean(KEY_YT_NO_ADS, s.ytNoAds)
            .putBoolean(KEY_AUTO_TRAILER, s.autoTrailer)
            .putBoolean(KEY_AUTOPLAY_NEXT, s.autoplayNext)
            .putString(KEY_PREF_LANG, s.preferredLanguage)
            .putBoolean(KEY_GROUP_LANG, s.groupLanguageVariants)
            .putBoolean(KEY_HIDE_WATCHED, s.hideWatched)
            .putBoolean(KEY_INDEXER_AUTO, s.indexerAutoUpdate)
            .putInt(KEY_INDEXER_INTERVAL, s.indexerIntervalHours)
            .putBoolean(KEY_MAINTENANCE, s.maintenanceEnabled)
            .putBoolean(KEY_SOURCE_SCAN, s.sourceScanEnabled)
            .putLong(KEY_ONBOARDED, s.onboardedAt)
            .putString(KEY_ASPECT, s.playerAspectMode)
            .putBoolean(KEY_SOUNDS, s.interfaceSounds)
            .putBoolean(KEY_HERO, s.showHero)
            .putBoolean(KEY_REDUCE_MOTION, s.reduceMotion)
            .apply()
    }

    /** Mantém o state em sincronia quando outra camada grava direto no prefs. */
    override fun reload() {
        _state.value = readAll()
    }

    companion object {
        private const val PREFS = "smarted_settings"

        const val DEFAULT_REPO_URL = "https://vkodi.net/repo"
        const val DEFAULT_EPG_URL = "https://epg.best/brazil.xml"

        private const val KEY_REPO_URL = "repo.url"
        private const val KEY_REPO_NAME = "repo.name"
        private const val KEY_BRAZUCA_ENABLED = "brazuca.enabled"
        private const val KEY_TMDB_KEY = "tmdb.key"
        private const val KEY_TMDB_LANG = "tmdb.lang"
        private const val KEY_THEME_DARK = "ui.dark"
        private const val KEY_ACCENT = "ui.accent"
        private const val KEY_FONT_SCALE = "ui.fontScale"
        private const val KEY_OVERSCAN = "ui.overscan"
        private const val KEY_EPG_URL = "epg.url"
        private const val KEY_EPG_AUTOLOAD = "epg.autoload"
        private const val KEY_YT_NO_ADS = "yt.noads"
        private const val KEY_AUTO_TRAILER = "trailer.auto"
        private const val KEY_AUTOPLAY_NEXT = "player.autoplayNext"
        private const val KEY_PREF_LANG = "player.prefLang"
        private const val KEY_GROUP_LANG = "ui.groupLang"
        private const val KEY_HIDE_WATCHED = "ui.hideWatched"
        private const val KEY_INDEXER_AUTO = "indexer.auto"
        private const val KEY_INDEXER_INTERVAL = "indexer.hours"
        private const val KEY_MAINTENANCE = "maintenance.enabled"
        private const val KEY_SOURCE_SCAN = "sources.scan"
        private const val KEY_ONBOARDED = "ui.onboardedAt"
        private const val KEY_ASPECT = "player.aspect"
        private const val KEY_SOUNDS = "ui.sounds"
        private const val KEY_HERO = "ui.hero"
        private const val KEY_REDUCE_MOTION = "ui.reduceMotion"
    }
}

/** Snapshot imutável das configurações. */
data class Settings(
    val activeRepoUrl: String = SettingsStore.DEFAULT_REPO_URL,
    val activeRepoName: String = "Brazuca Play",
    val brazucaEnabled: Boolean = true,
    val tmdbKey: String = "",
    val tmdbLanguage: String = "pt-BR",
    val themeDark: Boolean = true,
    val accentId: String = "fuchsia",
    val fontScale: Float = 1f,
    val overscanPercent: Float = 1.5f,
    val epgUrl: String = SettingsStore.DEFAULT_EPG_URL,
    val epgAutoload: Boolean = true,
    val ytNoAds: Boolean = true,
    val autoTrailer: Boolean = false,
    val autoplayNext: Boolean = true,
    val preferredLanguage: String = "pt-BR",
    val groupLanguageVariants: Boolean = true,
    val hideWatched: Boolean = false,
    val indexerAutoUpdate: Boolean = true,
    val indexerIntervalHours: Int = 6,
    val maintenanceEnabled: Boolean = true,
    val sourceScanEnabled: Boolean = true,
    val onboardedAt: Long = 0L,
    val playerAspectMode: String = "fit",
    val interfaceSounds: Boolean = true,
    val showHero: Boolean = true,
    val reduceMotion: Boolean = false,
)
