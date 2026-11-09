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
 * Instalador de pacotes de repositório estilo Kodi: baixa o arquivo, permite
 * NAVEGAR o conteúdo e instala:
 *  - índices JSON no nosso formato (repositórios próprios);
 *  - pacotes Kodi de verdade — `addon.xml` (add-on avulso) e `addons.xml`
 *    (manifesto do repositório) — convertidos para o nosso formato por um
 *    ADAPTADOR (spec §5), com metadados reais e extração segura dos arquivos.
 *
 * Nada aparece na tela de add-ons sem instalação explícita em Configurações —
 * zero dados fictícios. O runtime Python do Kodi NÃO é executado.
 */
class RepoPackages(private val context: Context) {

    /** Entrada do Gestor de Arquivos: pasta ou arquivo dentro do pacote. */
    data class Entry(val path: String, val isDirectory: Boolean)

    /**
     * Add-on Kodi detectado via `addon.xml`/`addons.xml` — SOMENTE metadados
     * (id, nome, versão, tipo, autor, dependências, descrição).
     */
    data class KodiAddon(
        val entryPath: String,
        val id: String,
        val name: String,
        val version: String,
        val type: String?,
        val author: String?,
        val description: String?,
        val dependencies: List<String>,
        /** Script principal declarado no addon.xml (`library`) — entrypoint. */
        val library: String? = null,
    )

    /** Pacote Kodi detectado no ZIP: add-on avulso ou manifesto de repositório. */
    data class KodiPackage(val kind: String, val manifestPath: String, val addons: List<KodiAddon>)

    /** Add-on encontrado num índice do ZIP: tipo declarado (null = usuário escolhe). */
    data class AddonInfo(val name: String, val declaredType: String?)

    /** Índice (.json) do ZIP — sub-repositório/add-ons instaláveis. */
    data class Candidate(val entryPath: String, val name: String, val addons: List<AddonInfo>)

    private val packageDir: File
        get() = File(context.filesDir, "repo_packages").apply { mkdirs() }
    private val installDir: File
        get() = File(context.filesDir, "repo_installs").apply { mkdirs() }

    /** Baixa/copia o ZIP (http(s), file://, content:// ou caminho absoluto) e valida. */
    suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        val file: File = when {
            trimmed.startsWith("file://") -> File(trimmed.removePrefix("file://"))
            trimmed.startsWith("content://") -> copyUriToCache(trimmed)
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                val bytes = Http.getBytes(trimmed)
                val name = trimmed.substringAfterLast('/').substringBefore('?')
                    .ifBlank { "repositorio.zip" }
                    .let { if (it.endsWith(".zip", true)) it else "$it.zip" }
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                File(packageDir, name).apply { writeBytes(bytes) }
            }
            trimmed.startsWith("/") && File(trimmed).isFile -> File(trimmed)
            else -> error("Informe a URL http(s) do pacote .zip ou um arquivo local.")
        }
        require(file.isFile) { "Arquivo .zip não encontrado: ${file.name}" }
        val hasJson = candidates(file.absolutePath).isNotEmpty()
        val hasKodi = kodiPackage(file.absolutePath) != null
        require(hasJson || hasKodi) {
            "O ZIP não contém índice .json nem addon.xml/addons.xml instalável."
        }
        file.absolutePath
    }

    /** Copia um content:// (SAF) para o cache privado — usado pelo instalador. */
    private fun copyUriToCache(uri: String): File {
        val target = File(packageDir, "local_${uri.hashCode().toUInt()}.zip")
        val input = context.contentResolver.openInputStream(android.net.Uri.parse(uri))
            ?: error("Não foi possível abrir o arquivo selecionado.")
        input.use { src -> target.outputStream().use { dst -> src.copyTo(dst) } }
        return target
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
     * Detecta um pacote Kodi no ZIP: `addons.xml` (manifesto de repositório —
     * todos os add-ons listados) ou `addon.xml` (add-on avulso, podendo estar
     * em subpasta — layout padrão `<id>/addon.xml`).
     */
    fun kodiPackage(zipPath: String): KodiPackage? {
        return runCatching {
            val entries = ZipInputStream(File(zipPath).inputStream()).use { zip ->
                generateSequence { zip.nextEntry }
                    .filter { !it.isDirectory }
                    .map { it.name }
                    .toList()
            }
            // BACKUP/estrutura Kodi (files/.kodi/addons/<id>/addon.xml): o zip
            // inteiro é um home com VÁRIOS add-ons instalados — hierarquia real.
            val homeEntries = entries.filter { KodiScanner.isHomeAddonEntry(it) }.sorted()
            if (homeEntries.isNotEmpty()) {
                val addons = homeEntries.mapNotNull { entry ->
                    readZipText(zipPath, entry)?.let { parseAddonXml(entry, it) }
                }
                return@runCatching if (addons.isEmpty()) {
                    null
                } else {
                    KodiPackage("home", homeEntries.first().substringBeforeLast('/'), addons)
                }
            }
            val manifest = entries.firstOrNull { it.substringAfterLast('/') == "addons.xml" }
                ?: entries.firstOrNull { it.substringAfterLast('/') == "addon.xml" }
                ?: return@runCatching null
            val text = readZipText(zipPath, manifest) ?: return@runCatching null
            val kind = if (manifest.substringAfterLast('/') == "addons.xml") "repository" else "addon"
            val addons = if (kind == "repository") parseAddonsManifest(manifest, text)
            else listOfNotNull(parseAddonXml(manifest, text))
            if (addons.isEmpty()) null else KodiPackage(kind, manifest, addons)
        }.getOrNull()
    }

    /** Lê UM texto do ZIP (segunda passada — ZipInputStream é sequencial). */
    private fun readZipText(zipPath: String, entryPath: String): String? = runCatching {
        ZipInputStream(File(zipPath).inputStream()).use { z ->
            generateSequence { z.nextEntry }
                .firstOrNull { it.name == entryPath }
                ?.let { z.readBytes().decodeToString() }
        }
    }.getOrNull()

    /**
     * Instala um pacote KODI: extrai os arquivos (Zip Slip bloqueado) e gera um
     * índice no nosso formato a partir do addon.xml/addons.xml — adaptador de
     * compatibilidade (spec §5). Devolve (url local, nome).
     */
    suspend fun installKodi(zipPath: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val pkg = kodiPackage(zipPath) ?: error("O ZIP não contém addon.xml/addons.xml.")
        val stamp = zipPath.hashCode().toUInt()
        val extractRoot = File(installDir, "kodi_$stamp").apply { deleteRecursively(); mkdirs() }
        extractZipSafely(zipPath, extractRoot)
        if (pkg.kind == "home") {
            // Backup/estrutura KODI: varre os arquivos extraídos e monta o índice
            // com a hierarquia EXATA + add-ons instalados (metadados reais).
            val scan = KodiScanner.scan(KodiScanner.FileVfs, extractRoot.absolutePath, "Kodi")
            val homeName = scan.homePath?.substringAfterLast('/')
                ?: pkg.addons.firstOrNull()?.name
                ?: "Backup Kodi"
            val index = KodiScanner.buildIndex(scan, "Backup $homeName")
            val target = File(installDir, "kodi_index_$stamp.json")
            target.writeText(Http.json.encodeToString(OnePlayIndexDto.serializer(), index))
            return@withContext "file://${target.absolutePath}" to (index.name ?: "Backup Kodi")
        }
        // Leitura ESTÁTICA do entrypoint para classificar a capacidade real
        // (sem executar nada); a árvore de arquivos vai para `files` — o clique
        // do add-on NÃO abre explorador de código.
        val addonDirFor = { id: String ->
            File(extractRoot, id).takeIf { it.isDirectory } ?: extractRoot
        }
        val index = OnePlayIndexDto(
            name = pkg.addons.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: "Pacote Kodi",
            addons = pkg.addons.map { a ->
                val deps = if (a.dependencies.isEmpty()) null
                else "Depende de: ${a.dependencies.joinToString()}"
                val addonDir = addonDirFor(a.id)
                val names = addonDir.listFiles()?.mapNotNull { it.name } ?: emptyList()
                val capability = if (a.description?.let { d -> isXtreamHint(d) } == true) {
                    "xtream"
                } else {
                    detectCapability(
                        { n -> File(addonDir, n).takeIf { f -> f.length() in 1..200_000L }?.readText() },
                        a.library,
                        names,
                    )
                }
                OnePlayAddonDto(
                    name = a.name,
                    type = a.type,
                    author = a.author,
                    version = a.version,
                    description = listOfNotNull(a.description, deps)
                        .joinToString(" · ").ifBlank { null },
                    tree = null,
                    capability = capability,
                    entrypoint = a.library,
                )
            },
            files = fileTreeFor(extractRoot, extractRoot.name),
        )
        val target = File(installDir, "kodi_index_$stamp.json")
        target.writeText(Http.json.encodeToString(OnePlayIndexDto.serializer(), index))
        "file://${target.absolutePath}" to index.name!!
    }

    /** Grava um índice gerado em runtime e devolve a URL local `file://`. */
    fun writeIndex(index: OnePlayIndexDto, baseName: String): String {
        val base = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48).ifBlank { "local" }
        val target = File(installDir, "local_${base}_${index.hashCode().toUInt()}.json")
        target.writeText(Http.json.encodeToString(OnePlayIndexDto.serializer(), index))
        return "file://${target.absolutePath}"
    }

    /** Extração segura de TODO o pacote (Zip Slip/Path Traversal bloqueados). */
    private fun extractZipSafely(zipPath: String, root: File) {
        ZipInputStream(File(zipPath).inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!isSafeEntryPath(entry.name)) continue
                val out = File(root, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    /**
     * Árvore real dos arquivos extraídos do add-on (navegável) — diretórios e
     * arquivos como pastas (nenhuma ação falsa: sem execução de código).
     */
    private fun fileTreeFor(root: File, addonId: String): OnePlayNodeDto? {
        val base = File(root, addonId).takeIf { it.isDirectory } ?: root
        fun node(f: File): OnePlayNodeDto = OnePlayNodeDto(
            name = f.name,
            kind = "folder",
            children = f.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { node(it) }
                .orEmpty(),
        )
        val kids = base.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { node(it) }
            .orEmpty()
        if (kids.isEmpty()) return null
        return OnePlayNodeDto(name = addonId, kind = "folder", children = kids)
    }

    /**
     * Instala um índice JSON do pacote: extrai para arquivo próprio, grava os
     * tipos escolhidos pelo usuário e devolve a URL local (`file://`).
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
     * Detecta `addon.xml`/`addons.xml` no pacote e extrai os metadados
     * (spec §5). Nenhum código do add-on é carregado ou executado.
     */
    fun kodiAddons(zipPath: String): List<KodiAddon> =
        kodiPackage(zipPath)?.addons.orEmpty()

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

        /** Tags de markup Kodi ([COLOR]..[/B]) nunca chegam à UI (sem tags cruas). */
        fun sanitizeKodiName(name: String): String =
            name.replace(Regex("\\[[^\\]]*\\]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

        /**
         * Metadados de um `addon.xml` por leitura de texto (regex) — puro JVM,
         * sem execução de código. Lido do arquivo REAL do ecossistema:
         * provider-name (autor), `<provides>` (tipo real do plugin), summary e
         * description (descrição), `<requires>` (dependências).
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
            val name = sanitizeKodiName(attr("name"))
            val version = attr("version")
            val author = attr("provider-name").ifBlank { null }
            if (id.isBlank() && name.isBlank()) return null
            val requires = Regex(
                "<requires>(.*?)</requires>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(clean)?.groupValues?.get(1).orEmpty()
            val dependencies = Regex(
                "<import\\s[^>]*addon\\s*=\\s*\"([^\"]*)\"",
                RegexOption.IGNORE_CASE,
            ).findAll(requires).map { it.groupValues[1] }.toList()
            // <provides>video</provides> é o tipo REAL de um pluginsource;
            // extension point resolve o resto (metadata, repository, ...).
            val provides = Regex(
                "<provides>(.*?)</provides>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(clean)?.groupValues?.get(1)?.trim()?.lowercase()
            val type = provides?.let { providesType(it) }
                ?: Regex(
                    "<extension\\s[^>]*point\\s*=\\s*\"([^\"]*)\"",
                    RegexOption.IGNORE_CASE,
                ).findAll(clean)
                    .mapNotNull { typeFromExtensionPoint(it.groupValues[1]) }
                    .firstOrNull()
            val description = firstTagText(clean, "summary")
                ?: firstTagText(clean, "description")
            // Entrypoint: `library` da extensão pluginsource (fallback: 1ª ext).
            val pluginExt = Regex(
                "<extension\\s[^>]*point\\s*=\\s*\"[^\"]*pluginsource[^\"]*\"[^>]*>",
                RegexOption.IGNORE_CASE,
            ).find(clean)?.value
                ?: Regex("<extension\\s[^>]*>", RegexOption.IGNORE_CASE).find(clean)?.value
            val library = pluginExt?.let {
                Regex("library\\s*=\\s*\"([^\"]*)\"").find(it)?.groupValues?.get(1)
            }?.ifBlank { null }
            return KodiAddon(
                entryPath = entryPath,
                id = id.ifBlank { name },
                name = name.ifBlank { id },
                version = version,
                type = type,
                author = author,
                description = description?.let { sanitizeKodiName(it) }?.ifBlank { null },
                dependencies = dependencies,
                library = library,
            )
        }

        /** Manifesto `addons.xml` do repositório Kodi: um add-on por bloco. */
        fun parseAddonsManifest(entryPath: String, xml: String): List<KodiAddon> =
            Regex(
                "<addon\\s[^>]*>.*?</addon>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).findAll(xml)
                .mapNotNull { parseAddonXml(entryPath, it.value) }
                .toList()

        /** Conteúdo de `<summary>`/`<description>` (primeiro não vazio). */
        private fun firstTagText(xml: String, tag: String): String? =
            Regex(
                "<$tag\\s[^>]*>(.*?)</$tag>|<$tag>(.*?)</$tag>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).findAll(xml)
                .map { m -> (m.groupValues[1] + m.groupValues[2]).replace(Regex("<[^>]*>"), " ").trim() }
                .firstOrNull { it.isNotBlank() }

        /** `<provides>` Kodi → tipo oficial do agrupamento. */
        fun providesType(provides: String): String? = when {
            provides.contains("video") -> "video"
            provides.contains("music") -> "music"
            provides.contains("executable") -> "programs"
            provides.contains("image") -> "pictures"
            else -> null
        }

        /**
         * Capacidade REAL do add-on detectada ESTATICAMENTE (leitura de texto
         * apenas — NUNCA executa código do add-on, spec §7). "xtream" = o
         * plugin fala com painéis Xtream Codes (player_api/get.php/…).
         */
        private val XTREAM_HINT_RE = Regex(
            "(?i)player_api\\.php|get\\.php\\?|xtream\\s*codes|action=get_(live|vod|series)|type=m3u_plus",
        )

        fun isXtreamHint(text: String): Boolean = XTREAM_HINT_RE.containsMatchIn(text)

        /**
         * Lê entrypoint + fontes .py do topo (top-level, limitadas) procurando
         * marcadores de capacidade. [read] devolve o texto ou null.
         */
        fun detectCapability(
            read: (String) -> String?,
            entrypoint: String?,
            topLevelNames: List<String>,
        ): String? {
            val hint = listOfNotNull(entrypoint) +
                topLevelNames.filter { it.endsWith(".py", true) }.take(12)
            for (name in hint) {
                val text = read(name) ?: continue
                if (isXtreamHint(text)) return "xtream"
            }
            return null
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

        // ── Migração de índices ANTIGOS (árvore de arquivos no add-on) ──────

        /** Contém um nó chamado "addon.xml" (assinatura de árvore de ARQUIVOS). */
        fun looksLikeFileTree(node: OnePlayNodeDto, depth: Int = 0): Boolean {
            if (depth > 5) return false
            if (node.name?.substringAfterLast('/') == "addon.xml") return true
            return node.children.orEmpty().any { looksLikeFileTree(it, depth + 1) }
        }

        /**
         * Índices instalados por versões anteriores guardavam a árvore de
         * arquivos em `addons[i].tree` — causava explorador de código no clique
         * do add-on. Migra: move a árvore para `files` (Gestor de Arquivos),
         * zera `tree` e enriquece capacidade/entrypoint lendo a extração
         * irmã `kodi_<stamp>` (quando existir) — leitura estática, sem execução.
         */
        fun migrateLegacyIndex(index: OnePlayIndexDto, indexPath: String): OnePlayIndexDto {
            val addons = index.addons.orEmpty()
            if (addons.isEmpty()) return index
            val hasFileTrees = addons.any { it.tree != null && looksLikeFileTree(it.tree) }
            val needsCapability = addons.any {
                it.capability == null && (it.description?.let { d -> isXtreamHint(d) } == true)
            }
            if (!hasFileTrees && !needsCapability) {
                // Ainda assim: dica de metadado SEMPRE vale (resumo "Xtream Codes…").
                var changed = false
                val withHint = addons.map { a ->
                    if (a.capability == null && a.description?.let { d -> isXtreamHint(d) } == true) {
                        changed = true
                        a.copy(capability = "xtream")
                    } else {
                        a
                    }
                }
                return if (changed) index.copy(addons = withHint) else index
            }
            // Extração irmã: …/repo_installs/kodi_index_<stamp>.json → …/kodi_<stamp>/
            val extractDir = runCatching {
                if (!indexPath.startsWith("file://")) null
                else {
                    val f = java.io.File(indexPath.removePrefix("file://"))
                    val stamp = f.name.removePrefix("kodi_index_").removeSuffix(".json")
                    if (stamp == f.name) null else java.io.File(f.parentFile, "kodi_$stamp")
                }
            }.getOrNull()?.takeIf { it.isDirectory }

            val migrated = addons.map { a ->
                var addon = a
                if (addon.tree != null && looksLikeFileTree(addon.tree)) {
                    addon = addon.copy(tree = null)
                }
                if (addon.capability == null) {
                    val meta = addon.description?.let { d -> isXtreamHint(d) } == true
                    val fromFiles = extractDir?.let { dir ->
                        // DTO legado não tem id: o topo da extração é o
                        // próprio addon (ou <plugin.id>/ — descido abaixo).
                        val addonDir = dir
                        val top = addonDir.listFiles() ?: emptyArray()
                        // Topo + 1 nível (extração legada agrupa em <plugin.id>/).
                        val names = buildList {
                            top.filter { it.isFile }.mapTo(this) { it.name }
                            top.filter { it.isDirectory }.take(8).forEach { sub ->
                                sub.listFiles()?.filter { it.isFile }?.take(24)
                                    ?.mapTo(this) { "${sub.name}/${it.name}" }
                            }
                        }
                        detectCapability(
                            { n -> java.io.File(addonDir, n).takeIf { f -> f.length() in 1..200_000L }?.readText() },
                            null,
                            names,
                        )
                    }
                    val cap = when {
                        meta -> "xtream"
                        fromFiles != null -> fromFiles
                        else -> null
                    }
                    if (cap != null) addon = addon.copy(capability = cap)
                }
                addon
            }
            val files = index.files
                ?: addons.firstNotNullOfOrNull { a -> a.tree?.takeIf { looksLikeFileTree(it) } }
            return index.copy(addons = migrated, files = files)
        }
    }
}
