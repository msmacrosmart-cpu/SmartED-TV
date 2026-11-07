package com.smarted.tv.core.model

import kotlinx.serialization.Serializable

/** The UI destination is chosen by the user, never inferred from a mixed playlist. */
@Serializable
enum class Scope(val wireTypes: Set<String>) {
    CHANNELS(setOf("tv", "channel", "channels", "live")),
    MOVIES(setOf("movie", "movies")),
    SERIES(setOf("series"));

    companion object {
        fun fromWire(type: String): Scope? = entries.firstOrNull { type.lowercase() in it.wireTypes }
    }
}

@Serializable
data class ExtraSpec(
    val name: String,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val optionsLimit: Int? = null,
)

@Serializable
data class ResourceSpec(
    val name: String,
    val types: List<String> = emptyList(),
    val idPrefixes: List<String>? = null,
) {
    fun supports(type: String, id: String = ""): Boolean =
        (types.isEmpty() || type in types) && (idPrefixes == null || idPrefixes.any { id.startsWith(it) })
}

@Serializable
data class CatalogSpec(
    val type: String,
    val id: String,
    val name: String = "",
    val extra: List<ExtraSpec> = emptyList(),
    val genres: List<String> = emptyList(),
    val pageSize: Int = 100,
) {
    val displayName: String get() = name.ifBlank { id }
    fun extra(name: String): ExtraSpec? = extra.firstOrNull { it.name == name }
    val paged: Boolean get() = extra("skip") != null
    val searchable: Boolean get() = extra("search") != null
    val searchOnly: Boolean get() = extra("search")?.required == true
}

@Serializable
data class Manifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val logo: String? = null,
    val background: String? = null,
    val types: List<String> = emptyList(),
    val resources: List<ResourceSpec> = emptyList(),
    val catalogs: List<CatalogSpec> = emptyList(),
    val configurationRequired: Boolean = false,
    val configurationUrl: String? = null,
    val epgProvider: Boolean = false,
) {
    fun catalogsFor(scope: Scope): List<CatalogSpec> = catalogs.filter { it.type in scope.wireTypes }
    fun resource(name: String, type: String, id: String = ""): Boolean =
        resources.any { it.name == name && it.supports(type, id) }
}

@Serializable
data class Video(
    val id: String,
    val title: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val released: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val thumbnail: String? = null,
    val description: String = "",
    val streams: List<MediaStream> = emptyList(),
)

@Serializable
data class MediaItem(
    val addonUrl: String,
    val id: String,
    val type: String,
    val scope: Scope,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String = "",
    val releaseInfo: String = "",
    val released: String? = null,
    val rating: Double? = null,
    val runtime: String = "",
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val language: String = "",
    val country: String = "",
    val videos: List<Video> = emptyList(),
    val streams: List<MediaStream> = emptyList(),
    val defaultVideoId: String? = null,
    val scheduledVideos: Boolean = false,
    val catalogName: String = "",
    val catalogPosition: Long = 0,
    val addedAt: Long? = null,
) {
    val key: String get() = "$addonUrl|$type|$id"
    val imageUrl: String? get() = if (scope == Scope.CHANNELS) logo ?: poster else poster ?: background
    val seasons: List<Int> get() = videos.mapNotNull { it.season }.distinct().sorted()
    val year: Int? get() = Regex("\\d{4}").find(released ?: releaseInfo)?.value?.toIntOrNull()
    fun playbackId(video: Video?): String = when {
        scope == Scope.CHANNELS -> id // Native EPG: a programme is NOT a playback identity.
        video != null -> video.id
        scope == Scope.SERIES -> defaultVideoId ?: videos.firstOrNull()?.id ?: id
        else -> defaultVideoId ?: id
    }
}

@Serializable
data class Subtitle(val id: String, val url: String, val language: String = "", val mime: String = "")

@Serializable
data class MediaStream(
    val url: String? = null,
    val externalUrl: String? = null,
    val infoHash: String? = null,
    val ytId: String? = null,
    val name: String = "",
    val description: String = "",
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Subtitle> = emptyList(),
) {
    val label: String get() = listOf(name, description).filter(String::isNotBlank).joinToString(" · ").ifBlank { "Fonte disponível" }
    val direct: Boolean get() = url?.lowercase()?.let { it.startsWith("https://") || it.startsWith("http://") } == true
}

@Serializable
data class InstalledAddon(val url: String, val scope: Scope, val manifest: Manifest, val installedAt: Long)

@Serializable
data class PlaybackProgress(val positionMs: Long, val durationMs: Long, val updatedAt: Long)

/** Preferências locais no modelo de organização do Stremio. Só expomos controles que o player nativo realmente aplica. */
@Serializable
data class UserPreferences(
    val audioLanguage: String = "pt",
    val subtitleLanguage: String = "pt",
    val subtitlesEnabled: Boolean = true,
    val subtitleSizePercent: Int = 100,
    val resumePlayback: Boolean = true,
    val autoplayNextEpisode: Boolean = true,
    val seekBackSeconds: Int = 10,
    val seekForwardSeconds: Int = 30,
) {
    init {
        require(subtitleSizePercent in 75..150) { "Tamanho de legenda inválido." }
        require(seekBackSeconds in 5..60 && seekForwardSeconds in 5..120) { "Intervalo de salto inválido." }
    }
}

@Serializable
data class AppData(
    val addons: List<InstalledAddon> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val progress: Map<String, PlaybackProgress> = emptyMap(),
    val preferences: UserPreferences = UserPreferences(),
) {
    init { require(addons.size <= 3) { "Limite de 3 addons." } }
}

data class CatalogPage(val items: List<MediaItem>, val nextCursor: com.smarted.tv.core.catalog.PageCursor?, val warnings: List<String> = emptyList())

enum class Sort { AZ, ADDED, RATING, YEAR }
enum class Direction { ASC, DESC }
data class CatalogQuery(
    val search: String = "",
    val category: String = "Todos",
    val sort: Sort = Sort.ADDED,
    val direction: Direction = Direction.DESC,
)
