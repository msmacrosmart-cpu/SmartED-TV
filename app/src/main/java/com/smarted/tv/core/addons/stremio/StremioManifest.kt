package com.smarted.tv.core.addons.stremio

import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder

/**
 * Camada de addons **estilo Stremio** do SmartED TV.
 *
 * O contrato é o mesmo do ecossistema Stremio, só que sem nenhum acoplamento a
 * torrentes:
 *
 * ```
 * GET {base}/manifest.json             -> manifesto do addon
 * GET {base}/catalog/{type}/{id}.json  -> { "metas": [ … ] }
 * ```
 *
 * O manifesto declara seus **catálogos** (`catalogs[]`), cada um com um `type`
 * (`movie`, `series`, `channel`…). Esse `type` é o **escopo** do catálogo: é o
 * que permite ao usuário escolher, catálogo por catálogo, se ele alimenta a aba
 * Filmes, Séries ou Canais — o vínculo é manual e fica salvo em
 * [CatalogBinding].
 *
 * Nada aqui conhece Room, Android ou UI: são modelos puros + parser de JSON +
 * montagem de URL, o que mantém tudo verificável sem emulador.
 */

/** Escopo de um catálogo, traduzido para as três abas do app. */
enum class AddonScope(val kind: MediaKind) {
    LIVE(MediaKind.LIVE),
    MOVIE(MediaKind.MOVIE),
    SERIES(MediaKind.SERIES),
    ;

    /** Rótulo humano usado nos seletores de aba. */
    val title: String
        get() = when (this) {
            LIVE -> "Canais"
            MOVIE -> "Filmes"
            SERIES -> "Séries"
        }

    companion object {
        /** `type` do manifesto -> escopo do app. Desconhecido devolve `null`. */
        fun fromType(type: String?): AddonScope? = when (type?.trim()?.lowercase()) {
            "movie", "movies", "film", "filme", "filmes", "vod" -> MOVIE
            "series", "serie", "série", "séries", "tvshow", "tvshows", "anime" -> SERIES
            "channel", "channels", "live", "tv", "aovivo", "linear" -> LIVE
            else -> null
        }

        fun fromKind(kind: MediaKind): AddonScope? = entries.firstOrNull { it.kind == kind }

        fun fromName(name: String?): AddonScope? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/** Um catálogo publicado pelo addon (uma "prateleira" remota). */
data class StremioCatalog(
    val type: String = "",
    val id: String = "",
    val name: String? = null,
    val extra: List<StremioExtra> = emptyList(),
) {
    /** Escopo declarado. `null` = catálogo que o app ainda não sabe exibir. */
    val scope: AddonScope? get() = AddonScope.fromType(type)

    val displayName: String get() = name?.trim()?.takeIf { it.isNotEmpty() } ?: id

    /** Catálogo que só responde a busca (não abre lista vazia). */
    val searchOnly: Boolean get() = extra.any { it.name == "search" && it.isRequired }

    /** Aceita paginação por `skip`. */
    val paged: Boolean get() = extra.any { it.name == "skip" }

    /** Gêneros declarados pelo addon (filtros rápidos das trilhas). */
    val genres: List<String>
        get() = extra.firstOrNull { it.name == "genre" }?.options.orEmpty()
}

data class StremioExtra(
    val name: String = "",
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
)

/** Manifesto do addon (subconjunto estável do `manifest.json` do Stremio). */
data class StremioManifest(
    val id: String = "",
    val version: String = "",
    val name: String = "",
    val description: String? = null,
    val logo: String? = null,
    val background: String? = null,
    val types: List<String> = emptyList(),
    val catalogs: List<StremioCatalog> = emptyList(),
) {
    /** Catálogos que o app sabe exibir (com escopo reconhecido). */
    val usableCatalogs: List<StremioCatalog> get() = catalogs.filter { it.scope != null }

    /** Escopos declarados pelo addon, na ordem das abas. */
    val scopes: List<AddonScope>
        get() = AddonScope.entries.filter { scope -> usableCatalogs.any { it.scope == scope } }
}

/**
 * Addon instalado: URL base + manifesto resolvido + o JSON original.
 *
 * O texto original do manifesto é guardado para que o app abra já sabendo os
 * catálogos, sem uma requisição de rede no start.
 */
data class StremioAddonSource(
    val url: String,
    val manifest: StremioManifest,
    val installedAt: Long = 0L,
    val rawManifest: String = "",
) {
    val addonId: String get() = manifest.id.ifBlank { url }
    val name: String get() = manifest.name.ifBlank { url }
    val version: String get() = manifest.version
    val catalogs: List<StremioCatalog> get() = manifest.usableCatalogs
}

/**
 * Vínculo manual entre um catálogo remoto e uma aba do app.
 *
 * É a unidade que o usuário controla na tela de addons Stremio: o mesmo addon
 * pode ter um catálogo vinculado a **Filmes** e outro a **Séries**, e o usuário
 * liga/desliga cada um sem desinstalar nada.
 */
data class CatalogBinding(
    val addonUrl: String,
    val catalogId: String,
    val type: String,
    val scope: AddonScope,
    val addonName: String = "",
    val catalogName: String = "",
) {
    val key: String get() = "$addonUrl|$catalogId"

    /** Nome mostrado no cabeçalho da trilha. */
    val displayName: String get() = catalogName.ifBlank { addonName }.ifBlank { catalogId }
}

/** Item devolvido por `catalog/…` (o "meta preview" do Stremio). */
data class StremioPreview(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
) {
    /** Converte o item remoto no modelo único do app. */
    fun toMediaItem(addonId: String): MediaItem {
        val kind = when (AddonScope.fromType(type)) {
            AddonScope.LIVE -> MediaKind.LIVE
            AddonScope.SERIES -> MediaKind.SERIES
            AddonScope.MOVIE -> MediaKind.MOVIE
            null -> MediaKind.UNKNOWN
        }
        return MediaItem(
            id = id,
            addonId = addonId,
            kind = kind,
            title = name,
            plot = description,
            poster = poster ?: background,
            backdrop = background ?: poster,
            logo = logo,
            year = parseYear(releaseInfo),
            rating = imdbRating?.replace(',', '.')?.toDoubleOrNull(),
            genres = genres,
            durationSec = parseRuntime(runtime),
        )
    }

    companion object {
        /** `2013`, `2013–2019`, `2019-` -> 2013/2019. */
        fun parseYear(releaseInfo: String?): Int? =
            Regex("(19|20)\\d{2}").find(releaseInfo.orEmpty())?.value?.toIntOrNull()

        /** `128 min`, `2h 08min`, `02:12:00` -> segundos. */
        fun parseRuntime(runtime: String?): Long? {
            val raw = runtime?.trim().orEmpty()
            if (raw.isEmpty()) return null
            Regex("(\\d{1,2})[:h](\\d{2})").find(raw)?.let { m ->
                val hours = m.groupValues[1].toLongOrNull() ?: 0L
                val minutes = m.groupValues[2].toLongOrNull() ?: 0L
                if (hours in 0L..12L) return hours * 3600L + minutes * 60L
            }
            Regex("(\\d{2,3})\\s*min", RegexOption.IGNORE_CASE).find(raw)?.let { m ->
                return (m.groupValues[1].toLongOrNull() ?: 0L) * 60L
            }
            return null
        }
    }
}

/** Parser do protocolo: manifesto e página de catálogo. */
object StremioParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Lê o manifesto. Lança [StremioException] com mensagem pronta para a UI. */
    fun manifest(raw: String): StremioManifest {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }
            .getOrNull()
            ?: throw StremioException("O manifesto não é um objeto JSON válido")
        return StremioManifest(
            id = root.text("id").orEmpty(),
            version = root.text("version").orEmpty(),
            name = root.text("name").orEmpty(),
            description = root.text("description"),
            logo = root.text("logo"),
            background = root.text("background"),
            types = root.strings("types"),
            catalogs = root.objects("catalogs").map(::catalog),
        )
    }

    /** Lê `{ "metas": [...] }`. */
    fun catalogPage(raw: String): List<StremioPreview> {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }
            .getOrNull()
            ?: throw StremioException("O catálogo não é um objeto JSON válido")
        return root.objects("metas").map { meta ->
            StremioPreview(
                id = meta.text("id").orEmpty(),
                type = meta.text("type") ?: meta.text("__type").orEmpty(),
                name = meta.text("name").orEmpty(),
                poster = meta.text("poster"),
                background = meta.text("background"),
                logo = meta.text("logo"),
                description = meta.text("description"),
                releaseInfo = meta.text("releaseInfo"),
                imdbRating = meta.text("imdbRating"),
                runtime = meta.text("runtime"),
                genres = meta.strings("genres"),
            )
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
    }

    private fun catalog(node: JsonObject): StremioCatalog = StremioCatalog(
        type = node.text("type").orEmpty(),
        id = node.text("id").orEmpty(),
        name = node.text("name"),
        extra = node.objects("extra").map { extra ->
            StremioExtra(
                name = extra.text("name").orEmpty(),
                isRequired = extra.text("isRequired")?.toBooleanStrictOrNull() ?: false,
                options = extra.strings("options"),
            )
        },
    )

    // ------------------------------------------------------- utilidades JSON

    private fun JsonObject.text(key: String): String? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        val value = primitive.content
        return value.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JsonObject.strings(key: String): List<String> {
        val array = this[key] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val primitive = element as? JsonPrimitive ?: return@mapNotNull null
            primitive.content.takeIf { it.isNotBlank() && it != "null" }
        }
    }

    private fun JsonObject.objects(key: String): List<JsonObject> {
        val array = this[key] as? JsonArray ?: return emptyList()
        return array.mapNotNull { it as? JsonObject }
    }
}

/** Montagem/normalização das URLs do protocolo. */
object StremioUrls {

    /**
     * Normaliza o que o usuário digita:
     *  - aceita `http(s)://host/addon`, `…/manifest.json`, com ou sem barra final;
     *  - devolve a **base** (sem `/manifest.json`) ou `null` se não for HTTP.
     */
    fun base(input: String?): String? {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val lowered = raw.lowercase()
        if (!lowered.startsWith("http://") && !lowered.startsWith("https://")) return null
        var base = raw
        if (base.endsWith("/manifest.json", ignoreCase = true)) {
            base = base.dropLast("/manifest.json".length)
        }
        return base.trimEnd('/')
    }

    fun manifestUrl(base: String): String = "$base/manifest.json"

    /** `{base}/catalog/{type}/{id}.json` (+ `/skip=…/search=….json`). */
    fun catalogUrl(
        base: String,
        catalog: StremioCatalog,
        skip: Int = 0,
        search: String? = null,
    ): String {
        val segments = ArrayList<String>(2)
        if (skip > 0) segments.add("skip=$skip")
        if (!search.isNullOrBlank()) segments.add("search=" + encode(search))
        val extra = if (segments.isEmpty()) "" else "/" + segments.joinToString("&")
        return "$base/catalog/" + encode(catalog.type) + "/" + encode(catalog.id) + extra + ".json"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/**
 * Regras de escopo: decide se um catálogo pode alimentar uma aba.
 *
 * O usuário sempre tem a palavra final ([CatalogBinding]); esta camada só evita
 * vínculos impossíveis (catálogo de filmes nunca entra na aba Canais).
 */
object StremioScopePolicy {

    fun canBind(catalog: StremioCatalog, target: MediaKind): Boolean =
        catalog.scope?.kind == target

    fun bindable(catalogs: List<StremioCatalog>, target: MediaKind): List<StremioCatalog> =
        catalogs.filter { canBind(it, target) }

    /**
     * Catálogos que alimentam uma aba, na ordem dos vínculos salvos.
     * Vínculos órfãos (addon removido/catálogo renomeado) são descartados.
     */
    fun resolve(
        bindings: List<CatalogBinding>,
        addons: List<StremioAddonSource>,
        target: MediaKind,
    ): List<Pair<StremioAddonSource, StremioCatalog>> {
        val byUrl = addons.associateBy { it.url }
        return bindings
            .filter { it.scope.kind == target }
            .mapNotNull { binding ->
                val addon = byUrl[binding.addonUrl] ?: return@mapNotNull null
                val catalog = addon.catalogs.firstOrNull { it.id == binding.catalogId }
                    ?: return@mapNotNull null
                addon to catalog
            }
    }
}
