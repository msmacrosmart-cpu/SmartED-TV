package com.smarted.tv.data.repo

import java.io.File

/**
 * Leitor recursivo da árvore KODI real (backup `files/.kodi/`, pastas de
 * repositório, mídia local): mapeia os diretórios EXATAMENTE como o ecossistema
 * — `addons/`, `userdata/`, `media/`, … — sem pastas estáticas no app: cada
 * diretório do disco vira um nó de navegação e os formatos suportados entram no
 * índice. Nenhum código de add-on é executado; só metadados (`addon.xml`) e
 * estrutura de arquivos (spec §5, docs/KODI_COMPAT.md).
 *
 * O mesmo percorredor atende dois sistemas de arquivos ([Vfs]):
 *  - [FileVfs]  — `file://`, pastas extraídas de ZIP (JVM puro, testável);
 *  - SAF        — árvore `content://` (implementação em data/system).
 */
object KodiScanner {

    // ── Formatos reconhecidos (pedido explícito do produto) ─────────────────
    /** Reproduzíveis diretamente (áudio/vídeo) — folha com URL local. */
    val PLAYABLE: Set<String> = setOf(
        "ts", "mp4", "mkv", "avi", "mov", "m4v", "mp3", "aac", "m4a", "wav",
        "flac", "ogg", "webm", "mpg", "mpeg", "m2ts", "3gp", "divx", "wmv",
    )
    /** Playlists — importadas como fontes (Canais) E listadas na árvore. */
    val PLAYLIST: Set<String> = setOf("m3u", "m3u8", "pls", "xspf", "asx", "wpl")
    /** Tudo que é INDEXADO/visível na árvore (reconhecido, sem ação falsa). */
    val INDEXED: Set<String> = PLAYABLE + PLAYLIST + setOf(
        "json", "xml", "zip", "nfo", "strm", "srt", "ass", "sub", "vtt",
        "txt", "md", "db", "sqlite", "apk", "jpg", "jpeg", "png", "gif",
        "bmp", "pdf",
    )

    const val MAX_ENTRIES = 12_000
    const val MAX_DEPTH = 16

    data class Entry(val name: String, val isDir: Boolean)
    data class PlaylistRef(val name: String, val uri: String)

    /** Resultado da varredura — a hierarquia exata + add-ons instalados. */
    data class Scan(
        val rootName: String,
        /** Diretório "home" Kodi detectado (…/.kodi) — `null` em pasta comum. */
        val homePath: String?,
        val addons: List<RepoPackages.KodiAddon>,
        /** Capacidade estática por add-on id (ex.: "xtream") — leitura, não execução. */
        val capabilities: Map<String, String>,
        val root: OnePlayNodeDto,
        val playlists: List<PlaylistRef>,
        val mediaCount: Int,
        val entryCount: Int,
    )

    /** Sistema de arquivos abstrato (File, ZIP extraído, SAF content://). */
    interface Vfs {
        fun list(path: String): List<Entry>
        fun readText(path: String): String?
        fun uri(path: String): String
    }

    /** `…/addons/<id>/addon.xml` — add-on instalado (não manifesto de repo). */
    private val HOME_ADDON_RE = Regex("(^|/)addons/[^/]+/addon\\.xml$")

    fun isHomeAddonEntry(path: String): Boolean = HOME_ADDON_RE.containsMatchIn(path)

    private fun extensionOf(name: String): String =
        if ('.' in name) name.substringAfterLast('.').lowercase() else ""

    /**
     * Varre [rootPath] recursivamente (limites de profundidade/nós por
     * segurança), detecta o home Kodi (dir com `addons/<id>/addon.xml`) e
     * coleciona playlists mídia reconhecida — sem pressupor a estrutura.
     */
    fun scan(vfs: Vfs, rootPath: String, rootName: String): Scan {
        var entries = 0
        var media = 0
        val playlists = mutableListOf<PlaylistRef>()

        fun leaf(path: String, name: String): OnePlayNodeDto {
            entries++
            return when (val ext = extensionOf(name)) {
                in PLAYLIST -> {
                    playlists += PlaylistRef(name, vfs.uri(path))
                    OnePlayNodeDto(name = name, kind = "leaf")
                }
                in PLAYABLE -> {
                    media++
                    // Mídia reconhecida → URL local de verdade (file:// ou content://).
                    OnePlayNodeDto(name = name, kind = "leaf", url = vfs.uri(path))
                }
                else -> {
                    // xml/json/zip/… INDEXADOS (visíveis na árvore) — sem ação
                    // falsa: sem executar nem "instalar" nada sozinho.
                    OnePlayNodeDto(name = name, kind = "leaf")
                }
            }
        }

        fun walk(path: String, name: String, depth: Int): OnePlayNodeDto {
            if (depth > MAX_DEPTH || entries > MAX_ENTRIES) {
                return OnePlayNodeDto(name = name, kind = "folder", children = emptyList())
            }
            val sorted = vfs.list(path).sortedWith(
                compareBy({ !it.isDir }, { it.name.lowercase() }),
            )
            val children = sorted.map { e ->
                val childPath = "$path/${e.name}"
                if (e.isDir) walk(childPath, e.name, depth + 1) else leaf(childPath, e.name)
            }
            return OnePlayNodeDto(name = name, kind = "folder", children = children)
        }

        // ── Home Kodi: BFS raso, prefere ".kodi" (hierarquia real do backup) ──
        fun isHome(dir: String): Boolean =
            vfs.list("$dir/addons").any { it.isDir && vfs.readText("$dir/addons/${it.name}/addon.xml") != null }

        var home: String? = null
        run {
            val queue = ArrayDeque<Pair<String, Int>>()
            queue += rootPath to 0
            var visited = 0
            while (queue.isNotEmpty() && visited < 400) {
                val (cur, depth) = queue.removeFirst()
                visited++
                if (depth > 3) continue
                if (isHome(cur)) {
                    if (cur.substringAfterLast('/') == ".kodi") {
                        home = cur
                        break
                    }
                    if (home == null) home = cur
                }
                if (depth < 3) {
                    vfs.list(cur).filter { it.isDir }.forEach { queue += "$cur/${it.name}" to depth + 1 }
                }
            }
        }

        // ── Add-ons instalados (metadados reais do addon.xml de cada pasta) ──
        val addons = mutableListOf<RepoPackages.KodiAddon>()
        val capabilities = mutableMapOf<String, String>()
        val homeDir = home
        if (homeDir != null) {
            vfs.list("$homeDir/addons")
                .filter { it.isDir }
                .sortedBy { it.name.lowercase() }
                .forEach { child ->
                    val addonPath = "$homeDir/addons/${child.name}"
                    val text = vfs.readText("$addonPath/addon.xml") ?: return@forEach
                    val info = RepoPackages.parseAddonXml("$addonPath/addon.xml", text) ?: return@forEach
                    addons += info
                    // Capacidade estática: metadado OU entrypoint/.py de topo.
                    val meta = info.description?.let { d -> RepoPackages.isXtreamHint(d) } == true
                    val cap = if (meta) {
                        "xtream"
                    } else {
                        val topNames = vfs.list(addonPath).filter { !it.isDir }.map { it.name }
                        RepoPackages.detectCapability(
                            { n -> vfs.readText("$addonPath/$n") },
                            info.library,
                            topNames,
                        )
                    }
                    if (cap != null) capabilities[info.id] = cap
                }
        }

        val root = walk(rootPath, rootName, 0)
        return Scan(
            rootName = rootName,
            homePath = home,
            addons = addons,
            capabilities = capabilities,
            root = root,
            playlists = playlists,
            mediaCount = media,
            entryCount = entries,
        )
    }

    /**
     * Índice do nosso formato a partir da varredura:
     *  - `addons` = add-ons INSTALADOS com tipo/autor/versão reais (grupos
     *    oficiais Vídeo/Programas/PVR/… saem do `<provides>`/extension point);
     *  - `files`  = a hierarquia EXATA da árvore (Gestor de Arquivos);
     *  - sem estrutura Kodi: o próprio conteúdo da pasta vira um único add-on
     *    real (tipo livre — nada inventado).
     */
    fun buildIndex(scan: Scan, name: String? = null): OnePlayIndexDto {
        val declared = scan.addons.map { info ->
            val deps = if (info.dependencies.isEmpty()) null
            else "Depende de: ${info.dependencies.joinToString()}"
            OnePlayAddonDto(
                name = info.name,
                // script.module.* = bibliotecas — sem grupo oficial próprio;
                // não rotular como Programas (tipo real: nenhum).
                type = if (info.id.startsWith("script.module.")) null else info.type,
                author = info.author,
                version = info.version,
                description = listOfNotNull(info.description, deps).joinToString(" · ").ifBlank { null },
                // Clique do add-on = EXECUÇÃO (capacidade) — nunca explorador
                // de arquivos; a hierarquia fica em `files` (Gestor de Arquivos).
                tree = null,
                capability = scan.capabilities[info.id],
                entrypoint = info.library,
            )
        }
        return OnePlayIndexDto(
            name = name?.takeIf { it.isNotBlank() } ?: scan.rootName,
            addons = declared.ifEmpty {
                listOf(
                    OnePlayAddonDto(
                        name = scan.rootName,
                        type = null,
                        tree = scan.root,
                    ),
                )
            },
            files = if (declared.isNotEmpty()) scan.root else null,
            tree = null,
        )
    }

    /** Filesystem JVM (file://, pastas extraídas de ZIP). */
    object FileVfs : Vfs {
        override fun list(path: String): List<Entry> {
            val files = File(path).listFiles() ?: return emptyList()
            return files.mapNotNull { f -> f.name?.let { Entry(it, f.isDirectory()) } }
        }

        override fun readText(path: String): String? {
            val f = File(path)
            if (!f.isFile || f.length() > 600_000L) return null
            return runCatching { f.readText() }.getOrNull()
        }

        override fun uri(path: String): String = "file://$path"
    }
}
