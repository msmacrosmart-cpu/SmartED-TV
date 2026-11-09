package com.smarted.tv.data.repo

import android.content.Context
import com.smarted.tv.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Instalador de pacotes ZIP de repositório (estilo Kodi): baixa o arquivo,
 * permite NAVEGAR o conteúdo (sub-repositórios e ferramentas) e instala os
 * índices contidos nele. Nada aparece na tela de add-ons sem instalação
 * explícita em Configurações — zero dados fictícios.
 */
class RepoPackages(private val context: Context) {

    /** Entrada do Gestor de Arquivos: pasta ou arquivo dentro do pacote. */
    data class Entry(val path: String, val isDirectory: Boolean)

    /**
     * Add-on Kodi detectado via `addon.xml` — SOMENTE metadados (id, nome,
     * versão, tipo, dependências). O runtime Kodi/Python não é executado:
     * ver docs/KODI_COMPAT.md.
     */
    data class KodiAddon(
        val entryPath: String,
        val id: String,
        val name: String,
        val version: String,
        val type: String?,
        val dependencies: List<String>,
    )

    /** Add-on encontrado num índice do ZIP: tipo declarado (null = usuário escolhe). */
    data class AddonInfo(val name: String, val declaredType: String?)

    /** Índice (.json) do ZIP — sub-repositório/add-ons instaláveis. */
    data class Candidate(val entryPath: String, val name: String, val addons: List<AddonInfo>)

    private val packageDir: File
        get() = File(context.filesDir, "repo_packages").apply { mkdirs() }
    private val installDir: File
        get() = File(context.filesDir, "repo_installs").apply { mkdirs() }

    /** Baixa e persiste o ZIP; valida que há ao menos um índice dentro. */
    suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "Informe a URL http(s) do pacote .zip."
        }
        val bytes = Http.getBytes(trimmed)
        val name = trimmed.substringAfterLast('/').substringBefore('?')
            .ifBlank { "repositorio.zip" }
            .let { if (it.endsWith(".zip", true)) it else "$it.zip" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(packageDir, name)
        file.writeBytes(bytes)
        require(candidates(file.absolutePath).isNotEmpty()) {
            "O ZIP não contém nenhum índice .json instalável."
        }
        file.absolutePath
    }

    /** Pacotes já baixados — lista do navegador em Configurações. */
    fun packages(): List<File> =
        packageDir.listFiles { f -> f.isFile && f.name.endsWith(".zip", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Árvore de entradas (pastas/arquivos) do pacote — Gestor de Arquivos. */
    fun entries(zipPath: String): List<Entry> = runCatching {
        ZipInputStream(File(zipPath).inputStream()).use { zip ->
            generateSequence { zip.nextEntry }
                .map { Entry(it.name, it.isDirectory) }
                .toList()
        }
    }.getOrNull().orEmpty()

    /** Índices .json do pacote (cada um = um repositório/add-ons instalável). */
    fun candidates(zipPath: String): List<Candidate> = runCatching {
        ZipInputStream(File(zipPath).inputStream()).use { zip ->
            generateSequence { zip.nextEntry }
                .filter { !it.isDirectory && it.name.endsWith(".json", true) }
                .mapNotNull { entry ->
                    runCatching {
                        val dto = Http.json.decodeFromString<OnePlayIndexDto>(
                            zip.readBytes().decodeToString(),
                        )
                        val addons = dto.addons.orEmpty().map {
                            AddonInfo(it.name?.takeIf { n -> n.isNotBlank() } ?: "Add-on", it.type)
                        }
                        Candidate(
                            entryPath = entry.name,
                            name = dto.name?.takeIf { n -> n.isNotBlank() }
                                ?: entry.name.substringAfterLast('/'),
                            addons = addons,
                        )
                    }.getOrNull()
                }
                .toList()
        }
    }.getOrNull().orEmpty()

    /**
     * Instala um índice do pacote: extrai para arquivo próprio, grava os tipos
     * escolhidos pelo usuário e devolve a URL local (`file://`) do índice.
     */
    suspend fun install(
        zipPath: String,
        entryPath: String,
        chosenTypes: Map<String, String>,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        require(isSafeEntryPath(entryPath)) { "Caminho inválido no pacote (Zip Slip bloqueado)." }
        val dto = readEntry(zipPath, entryPath) ?: error("Índice não encontrado no pacote.")
        val fixed = dto.copy(
            addons = dto.addons?.map { addon ->
                val chosen = chosenTypes[addon.name]
                if (addon.type.isNullOrBlank() && !chosen.isNullOrBlank()) {
                    addon.copy(type = chosen)
                } else {
                    addon
                }
            },
        )
        val base = (fixed.name ?: entryPath.substringAfterLast('/')).substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(installDir, "${base}_${entryPath.hashCode().toUInt()}.json")
        target.writeText(Http.json.encodeToString(OnePlayIndexDto.serializer(), fixed))
        "file://${target.absolutePath}" to (fixed.name ?: base)
    }

    /**
     * Detecta `addon.xml` no pacote e extrai os metadados (spec §5). Nenhum
     * código do add-on é carregado ou executado — apenas leitura de XML.
     */
    fun kodiAddons(zipPath: String): List<KodiAddon> = runCatching {
        ZipInputStream(File(zipPath).inputStream()).use { zip ->
            generateSequence { zip.nextEntry }
                .filter { !it.isDirectory && it.name.substringAfterLast('/') == "addon.xml" }
                .take(50)
                .mapNotNull { entry ->
                    runCatching { parseAddonXml(entry.name, zip.readBytes().decodeToString()) }
                        .getOrNull()
                }
                .toList()
        }
    }.getOrNull().orEmpty()

    private fun readEntry(zipPath: String, entryPath: String): OnePlayIndexDto? = runCatching {
        ZipInputStream(File(zipPath).inputStream()).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { !it.isDirectory && it.name == entryPath }
                ?.let { entry ->
                    Http.json.decodeFromString<OnePlayIndexDto>(
                        zip.readBytes().decodeToString(),
                    )
                }
        }
    }.getOrNull()

    companion object {
        /**
         * Guarda Zip Slip / Path Traversal (spec §7): caminho relativo sem
         * segmentos `..`/`.`, sem início absoluto e sem barra invertida.
         */
        fun isSafeEntryPath(path: String): Boolean {
            if (path.isBlank() || path.contains('\u0000')) return false
            if (path.startsWith("/") || path.contains('\\')) return false
            return path.split('/').none { it == ".." || it == "." }
        }

        /**
         * Metadados de um `addon.xml` por leitura de texto (regex) — puro JVM,
         * sem XmlPullParser (stub em unit tests) e sem execução de código.
         */
        fun parseAddonXml(entryPath: String, xml: String): KodiAddon? {
            val clean = xml.replace(Regex("<\\?xml[^>]*\\?>"), "")
            val addonTag = Regex("<addon\\s([^>]*)>", RegexOption.IGNORE_CASE)
                .find(clean)?.groupValues?.get(1) ?: return null
            fun attr(key: String): String =
                Regex("$key\\s*=\\s*\"([^\"]*)\"").find(addonTag)?.groupValues?.get(1)
                    ?: Regex("$key\\s*=\\s*'([^']*)'").find(addonTag)?.groupValues?.get(1)
                    ?: ""
            val id = attr("id")
            val name = attr("name")
            val version = attr("version")
            if (id.isBlank() && name.isBlank()) return null
            val requires = Regex(
                "<requires>(.*?)</requires>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(clean)?.groupValues?.get(1).orEmpty()
            val dependencies = Regex(
                "<import\\s[^>]*addon\\s*=\\s*\"([^\"]*)\"",
                RegexOption.IGNORE_CASE,
            ).findAll(requires).map { it.groupValues[1] }.toList()
            val type = Regex(
                "<extension\\s[^>]*point\\s*=\\s*\"([^\"]*)\"",
                RegexOption.IGNORE_CASE,
            ).findAll(clean)
                .mapNotNull { typeFromExtensionPoint(it.groupValues[1]) }
                .firstOrNull()
            return KodiAddon(
                entryPath = entryPath,
                id = id.ifBlank { name },
                name = name.ifBlank { id },
                version = version,
                type = type,
                dependencies = dependencies,
            )
        }

        /** Ponto de extensão Kodi → tipo oficial do agrupamento (spec §5). */
        fun typeFromExtensionPoint(point: String): String? {
            val p = point.lowercase()
            return when {
                // metadata/legendas antes de video: pontos como
                // kodi.metadata.scraper.video são Metadados, não Vídeo.
                p.contains("metadata") -> "metadata"
                p.contains("subtitle") -> "subtitles"
                p.contains("video") -> "video"
                p.contains("program") || p.contains("script") -> "programs"
                p.contains("music") || p.contains("audio") -> "music"
                p.contains("pvr") -> "pvr"
                p.contains("service") -> "services"
                p.contains("skin") || p.contains("interface") -> "skins"
                p.contains("repository") -> "repository"
                p.contains("picture") -> "pictures"
                else -> null
            }
        }
    }
}
