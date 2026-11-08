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
}
