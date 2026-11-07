package com.smarted.tv.core.addons

import com.smarted.tv.core.util.TextUtils

/**
 * Cliente de repositório estilo Kodi.
 *
 * Um repositório Kodi é um diretório HTTP com:
 *  - `addons.xml` (catálogo de addons + metadados),
 *  - `addons.xml.md5`,
 *  - zips dos addons em `zips/<id>/<id>-<versao>.zip`.
 *
 * Como addons Kodi são escritos em Python (não executável no Android), o
 * SmartED TV interpreta o repositório como **catálogo de fontes**: os metadados
 * são exibidos e o conteúdo é consumido via as fontes públicas que o addon
 * declara (M3U/XTREAM), mantendo a experiência "instalar addon" do Kodi.
 *
 * Todo o parsing aqui é puro e testável.
 */
object KodiRepoParser {

    data class RepoInfo(
        val id: String,
        val name: String,
        val version: String,
        val summary: String,
        val description: String,
        val author: String,
        val icon: String?,
        val fanart: String?,
        val addonId: String?,
        val website: String?,
    )

    /**
     * Faz o parse de `addons.xml`. Tolerante a XML inválido (muitos
     * repositórios escrevem entidades soltas no description).
     */
    fun parseAddonsXml(xml: String, repoUrl: String? = null): List<RepoInfo> {
        val out = ArrayList<RepoInfo>()
        val addonBlocks = Regex("<addon[-_]?[a-z]*\\b[^>]*>", RegexOption.IGNORE_CASE)
        val blocks = addonBlocks.findAll(xml).toList()
        if (blocks.isEmpty()) return out

        for ((index, match) in blocks.withIndex()) {
            val start = match.range.first
            val end = if (index + 1 < blocks.size) blocks[index + 1].range.first else xml.length
            val body = xml.substring(start, end)
            val openTag = match.value
            val id = attr(openTag, "id") ?: continue
            val name = attr(openTag, "name") ?: id
            val version = attr(openTag, "version") ?: "0.0.0"

            val summary = tag(body, "summary")?.let { clean(it) }.orEmpty()
            val description = tag(body, "description")?.let { clean(TextUtils.stripHtml(it)) }.orEmpty()
            val author = attr(openTag, "provider-name")
                ?: tag(body, "author")?.let { clean(it) }
            val icon = iconSrc(body)
            val fanart = fanartSrc(body)
            val website = tag(body, "website")?.let { clean(it) }?.takeIf { it.startsWith("http") }
            val addonId = Regex("<addonid[^>]*>(.*?)</addonid>").find(body)
                ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

            out.add(
                RepoInfo(
                    id = id,
                    name = name,
                    version = version,
                    summary = summary,
                    description = description,
                    author = author ?: "",
                    icon = icon,
                    fanart = fanart,
                    addonId = addonId,
                    website = website,
                ),
            )
        }
        return out
    }

    /** Extrai `<icon>` ou `<assets><icon>`. */
    private fun iconSrc(body: String): String? {
        Regex("<icon[^>]*>(.*?)</icon>", RegexOption.DOT_MATCHES_ALL).find(body)
            ?.groupValues?.get(1)?.trim()?.takeIf { it.startsWith("http") }?.let { return it }
        Regex("<icon[^>]*src=['\"]([^'\"]*)['\"]").find(body)
            ?.groupValues?.get(1)?.takeIf { it.startsWith("http") }?.let { return it }
        return null
    }

    private fun fanartSrc(body: String): String? {
        Regex("<fanart[^>]*>(.*?)</fanart>", RegexOption.DOT_MATCHES_ALL).find(body)
            ?.groupValues?.get(1)?.trim()?.takeIf { it.startsWith("http") }?.let { return it }
        Regex("<fanart[^>]*src=['\"]([^'\"]*)['\"]").find(body)
            ?.groupValues?.get(1)?.takeIf { it.startsWith("http") }?.let { return it }
        return null
    }

    /**
     * Alguns repositórios servem apenas listagem de diretório HTML. Este parser
     * extrai links de `.zip`/`.m3u`/pasta e deriva nomes legíveis.
     */
    fun parseHtmlIndex(html: String, baseUrl: String): List<RepoInfo> {
        val out = LinkedHashMap<String, RepoInfo>()
        val hrefRegex = Regex("<a\\s+[^>]*href=['\"]([^'\"]+)['\"][^>]*>(.*?)</a>", RegexOption.IGNORE_CASE)
        for (m in hrefRegex.findAll(html)) {
            val href = m.groupValues[1].trim()
            val label = TextUtils.stripHtml(m.groupValues[2]).trim().ifBlank { href }
            if (href.startsWith("?") || href.startsWith("#") || href.contains("..")) continue
            val isZip = href.endsWith(".zip", true)
            val isDir = href.endsWith("/")
            if (!isZip && !isDir) continue
            val id = href.trimEnd('/').substringAfterLast('/').substringBeforeLast(".zip")
            if (id.isBlank() || id.startsWith("http")) continue
            val lower = id.lowercase()
            if (lower in setOf("images", "icons", "fanart", "resources", "media")) continue
            val version = Regex("(\\d+(?:\\.\\d+){1,3})").find(label)?.groupValues?.get(1)
                ?: Regex("(\\d+(?:\\.\\d+){1,3})").find(id)?.groupValues?.get(1)
                ?: "—"
            out[id] = RepoInfo(
                id = id,
                name = clean(label.substringBefore(version).trim('-', ' ', 'v') ?: id),
                version = version,
                summary = if (isZip) "Addon disponível para instalação" else "Diretório do repositório",
                description = "",
                author = "",
                icon = null,
                fanart = null,
                addonId = null,
                website = null,
            )
        }
        return out.values.toList()
    }

    /** Deriva a URL de download do zip de um addon Kodi. */
    fun zipUrl(repoUrl: String, addonId: String, version: String): String =
        "${repoUrl.trimEnd('/')}/zips/$addonId/$addonId-$version.zip"

    private fun attr(tag: String, name: String): String? =
        Regex("$name=['\"]([^'\"]*)['\"]").find(tag)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private fun tag(scope: String, name: String): String? =
        Regex("<$name[^>]*>(.*?)</$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(scope)?.groupValues?.get(1)?.let { TextUtils.stripHtml(it) }

    private fun clean(s: String?): String = TextUtils.stripHtml(s).replace(Regex("\\s+"), " ").trim()
}

/**
 * Definição de um repositório gerenciado pelo usuário.
 * Persistido no Room; carregado na inicialização para montar os addons.
 */
data class Repository(
    val id: String,
    val name: String,
    val url: String,
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
    val addedAt: Long = 0L,
    val lastSyncAt: Long = 0L,
    val addonCount: Int = 0,
    val status: RepoStatus = RepoStatus.UNKNOWN,
    val message: String? = null,
    val icon: String? = null,
)

enum class RepoStatus { UNKNOWN, SYNCING, ONLINE, OFFLINE, EMPTY }

/** Um addon instalado, ligado a um repositório. */
data class InstalledAddon(
    val id: String,
    val repositoryId: String,
    val name: String,
    val version: String,
    val summary: String,
    val summaryLong: String = "",
    val author: String = "",
    val icon: String? = null,
    val enabled: Boolean = true,
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val kind: AddonKind = AddonKind.REMOTE,
    val types: Set<com.smarted.tv.core.model.MediaKind> = setOf(
        com.smarted.tv.core.model.MediaKind.LIVE,
        com.smarted.tv.core.model.MediaKind.MOVIE,
        com.smarted.tv.core.model.MediaKind.SERIES,
    ),
)

enum class AddonKind { BUILTIN, REMOTE, SOURCE }
