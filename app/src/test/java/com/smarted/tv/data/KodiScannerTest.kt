package com.smarted.tv.data

import com.smarted.tv.data.repo.KodiScanner
import com.smarted.tv.data.repo.RepoPackages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Varredura da hierarquia KODI real — fixtures = addon.xmls REAIS do backup
 * files.zip (IPTV-Native v1.0.0), com a MESMA estrutura
 * files/.kodi/{addons,userdata,media,system} capturada do ecossistema.
 */
class KodiScannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/kodihome/$name")) { "recurso $name" }
            .bufferedReader().readText()

    /** Monta o backup real em disco (estrutura idêntica ao files.zip). */
    private fun buildKodiBackup(): File {
        val root = tmp.newFolder("backup")
        fun put(rel: String, text: String) {
            File(root, rel).apply { parentFile.mkdirs(); writeText(text) }
        }
        val home = "files/.kodi"
        for (id in listOf(
            "plugin.video.OnePlay.Matrix",
            "plugin.video.BrazucaPlay.Matrix",
            "repository.elementumorg",
            "script.module.requests",
        )) {
            put("$home/addons/$id/addon.xml", resource("addons_$id.addon.xml"))
        }
        // Marcadores de estrutura (contagens/tipos de arquivo — sem conteúdo).
        File(root, "$home/addons/packages").mkdirs()
        File(root, "$home/addons/packages/plugin.video.OnePlay.Matrix-3.4.8.zip").writeBytes(ByteArray(0))
        File(root, "$home/media").mkdirs()
        File(root, "$home/media/filme.mp4").writeBytes(ByteArray(0))
        File(root, "$home/system").mkdirs()
        File(root, "$home/system/advancedsettings.xml").writeText("<advancedsettings/>")
        put("$home/userdata/Database/MyVideos.db", "")
        put("$home/userdata/playlists/video/filmes.m3u", "#EXTM3U\n")
        return root
    }

    @Test
    fun scansExactBackupHierarchyWithRealAddons() {
        val root = buildKodiBackup()
        val scan = KodiScanner.scan(KodiScanner.FileVfs, root.absolutePath, root.name)

        // Home detectado pela presença de addons/<id>/addon.xml (sem lista fixa).
        assertNotNull(scan.homePath)
        assertTrue(scan.homePath!!.endsWith("/.kodi"))
        assertEquals(4, scan.addons.size)
        val ids = scan.addons.map { it.id }.toSet()
        assertTrue("plugin.video.OnePlay.Matrix" in ids)
        assertTrue("repository.elementumorg" in ids)
        assertEquals("video", scan.addons.first { it.id == "plugin.video.OnePlay.Matrix" }.type)
        assertEquals("repository", scan.addons.first { it.id == "repository.elementumorg" }.type)

        // Hierarquia EXATA: raiz → files → .kodi → pastas oficiais (dirs 1º, alfa).
        val files = scan.root.children.orEmpty()
        assertEquals(listOf("files"), files.map { it.name })
        val kodi = files.first().children.orEmpty()
        assertEquals(listOf(".kodi"), kodi.map { it.name })
        assertEquals(
            listOf("addons", "media", "system", "userdata"),
            kodi.first().children.orEmpty().map { it.name },
        )
        // addons/ contém as subpastas de add-on reais + packages/ (zips).
        val addonsDir = kodi.first().children.orEmpty().first { it.name == "addons" }
        val addonNames = addonsDir.children.orEmpty().map { it.name }.toSet()
        assertTrue("plugin.video.OnePlay.Matrix" in addonNames)
        assertTrue("packages" in addonNames)
    }

    @Test
    fun indexesSupportedFormatsWithoutM3uRestriction() {
        val root = buildKodiBackup()
        val scan = KodiScanner.scan(KodiScanner.FileVfs, root.absolutePath, root.name)

        // Mídia reconhecida → URL local reproduzível.
        val media = find(scan.root, "filme.mp4")
        assertNotNull(media)
        assertTrue(media!!.url!!.startsWith("file://"))
        assertTrue(media.url!!.endsWith("filme.mp4"))
        // .zip/.xml/.db INDEXADOS (visíveis) sem ação falsa (sem URL de player).
        assertNotNull(find(scan.root, "plugin.video.OnePlay.Matrix-3.4.8.zip"))
        assertNull(find(scan.root, "plugin.video.OnePlay.Matrix-3.4.8.zip")!!.url)
        assertNotNull(find(scan.root, "MyVideos.db"))
        assertNotNull(find(scan.root, "advancedsettings.xml"))
        // Playlist coletada para importação como fonte M3U (e visível na árvore).
        assertEquals(1, scan.playlists.size)
        assertEquals("filmes.m3u", scan.playlists.first().name)
        assertTrue(scan.playlists.first().uri.startsWith("file://"))
        assertNotNull(find(scan.root, "filmes.m3u"))
        assertTrue(scan.mediaCount >= 1)
    }

    @Test
    fun coversEveryRequestedFormat() {
        val requested = listOf(
            "m3u", "m3u8", "ts", "mp4", "mkv", "avi", "mov", "m4v",
            "mp3", "aac", "json", "xml", "zip",
        )
        for (ext in requested) {
            assertTrue("$ext deve ser reconhecida", ext in KodiScanner.INDEXED)
        }
        assertTrue("m3u" in KodiScanner.PLAYLIST)
        assertTrue("mp4" in KodiScanner.PLAYABLE)
    }

    @Test
    fun buildIndexUsesOfficialTypesAndRealFilesTree() {
        val root = buildKodiBackup()
        val scan = KodiScanner.scan(KodiScanner.FileVfs, root.absolutePath, root.name)
        val index = KodiScanner.buildIndex(scan, "Backup .kodi")

        assertEquals("Backup .kodi", index.name)
        assertEquals(4, index.addons.orEmpty().size)
        val oneplay = index.addons.orEmpty().first { it.name.contains("ONEPLAY", true) || it.name.contains("OnePlay", true) }
        assertEquals("video", oneplay.type)
        assertEquals("Equipe OnePlay", oneplay.author)
        assertEquals("3.4.8", oneplay.version)
        assertTrue(oneplay.description.orEmpty().contains("OnePlay"))
        // script.module.* rotulado honestamente (sem grupo inventado).
        val requests = index.addons.orEmpty().first { it.name == "requests" }
        assertNull(requests.type)
        // `files` = hierarquia EXATA (pastas de topo) — Gestor de Arquivos.
        assertNotNull(index.files)
        assertEquals(
            listOf("files"),
            index.files!!.children.orEmpty().map { it.name },
        )
        // Clique do add-on NÃO é explorador de arquivos: sem menu no índice.
        assertNull(oneplay.tree)
        assertNull(index.tree)
        assertEquals("default.py", oneplay.entrypoint)
    }

    @Test
    fun detectsXtreamCapabilityFromRealAddonSourceWithoutExecuting() {
        // Código REAL do plugin.video.oneplayvip (zoreu/kodirepo): o entrypoint
        // app.py não cita a API, mas tsdownloader.py tem marcador XtreamCodes
        // — leitura estática de texto, NUNCA execução.
        val files = mapOf(
            "app.py" to resource("oneplayvip_app.py"),
            "tsdownloader.py" to resource("oneplayvip_tsdownloader.py"),
            "variables.py" to "// sem marcadores aqui\n",
        )
        val cap = RepoPackages.detectCapability(
            read = { files[it] },
            entrypoint = "app.py",
            topLevelNames = files.keys.toList(),
        )
        assertEquals("xtream", cap)
        // Só o entrypoint (sem marcadores) → nenhuma capacidade inventada.
        assertNull(
            RepoPackages.detectCapability(
                read = { if (it == "app.py") files["app.py"] else null },
                entrypoint = "app.py",
                topLevelNames = listOf("app.py"),
            ),
        )
        // Metadado do add-on v0.3.0 (resumo real exibido na instalação).
        assertTrue(
            RepoPackages.isXtreamHint(
                "Xtream Codes client with Live TV, VOD, series, background EPG and global search.",
            ),
        )
        // addon.xml real declara o entrypoint app.py.
        val info = RepoPackages.parseAddonXml(
            "plugin.video.oneplayvip/addon.xml",
            resource("oneplayvip_addon.xml"),
        )
        requireNotNull(info)
        assertEquals("app.py", info.library)
        assertEquals("video", info.type)
    }

    @Test
    fun migratesLegacyFileTreeIndexToExecutionFlow() {
        // Índice das versões antigas: arquivos DO ADDON em addons[0].tree.
        val fileTree = com.smarted.tv.data.repo.OnePlayNodeDto(
            name = "plugin.video.oneplayvip",
            kind = "folder",
            children = listOf(
                com.smarted.tv.data.repo.OnePlayNodeDto(name = "addon.xml", kind = "folder"),
                com.smarted.tv.data.repo.OnePlayNodeDto(name = "fanart.jpg", kind = "folder"),
            ),
        )
        val legacy = com.smarted.tv.data.repo.OnePlayIndexDto(
            name = "ONEPLAY VIP",
            addons = listOf(
                com.smarted.tv.data.repo.OnePlayAddonDto(
                    name = "ONEPLAY VIP",
                    type = "video",
                    tree = fileTree,
                ),
            ),
        )
        // Migração SEM extração irmã: sem invenção de capacidade.
        val plain = RepoPackages.migrateLegacyIndex(legacy, "file:///tmp/sem-extracao/kodi_index_1.json")
        assertNull(plain.addons.orEmpty().first().tree)
        assertNotNull(plain.files)
        assertNull(plain.addons.orEmpty().first().capability)

        // Com extração irmã kodi_<stamp> contendo fonte com marcador REAL.
        val dir = tmp.newFolder("repo_installs")
        val ext = File(dir, "kodi_777")
        ext.mkdirs()
        File(ext, "tsdownloader.py").writeText(resource("oneplayvip_tsdownloader.py"))
        val idxFile = File(dir, "kodi_index_777.json").apply { writeText("{}") }
        val viaFiles = RepoPackages.migrateLegacyIndex(legacy, "file://${idxFile.absolutePath}")
        val migrated = viaFiles.addons.orEmpty().first()
        assertNull(migrated.tree)
        assertNotNull(viaFiles.files)
        assertEquals("xtream", migrated.capability)

        // Resumo "Xtream Codes…" (v0.3.0) → capacidade sem depender dos arquivos.
        val withSummary = legacy.copy(
            addons = listOf(
                legacy.addons.orEmpty().first().copy(
                    description = "Xtream Codes client with Live TV, VOD, series.",
                ),
            ),
        )
        val viaSummary = RepoPackages.migrateLegacyIndex(withSummary, "file:///outra/kodi_index_9.json")
        assertEquals("xtream", viaSummary.addons.orEmpty().first().capability)
    }

    @Test
    fun plainMediaFolderIndexsWithoutKodiStructure() {
        val folder = tmp.newFolder("filmes")
        File(folder, "acao.mp4").writeBytes(ByteArray(0))
        File(folder, "radio.mp3").writeBytes(ByteArray(0))
        File(folder, "lista.m3u").writeText("#EXTM3U\n")

        val scan = KodiScanner.scan(KodiScanner.FileVfs, folder.absolutePath, folder.name)
        assertNull(scan.homePath)
        assertTrue(scan.addons.isEmpty())
        assertEquals(1, scan.playlists.size)
        assertEquals(2, scan.mediaCount)

        val index = KodiScanner.buildIndex(scan)
        // Sem estrutura Kodi: o CONTEÚDO DA PASTA vira um único add-on real.
        assertEquals(1, index.addons.orEmpty().size)
        assertNull(index.addons.orEmpty().first().type)
        assertNotNull(index.addons.orEmpty().first().tree)
        assertNull(index.files)
    }

    @Test
    fun homeEntryDetectionMatchesInstalledAddonsOnly() {
        assertTrue(KodiScanner.isHomeAddonEntry("files/.kodi/addons/plugin.video.OnePlay.Matrix/addon.xml"))
        assertTrue(KodiScanner.isHomeAddonEntry("addons/plugin.video.x/addon.xml"))
        // Add-on avulso (zip padrão) e manifesto de repositório NÃO são home.
        assertFalse(KodiScanner.isHomeAddonEntry("plugin.video.oneplayvip/addon.xml"))
        assertFalse(KodiScanner.isHomeAddonEntry("addons.xml"))
        assertFalse(KodiScanner.isHomeAddonEntry("myaddons/addon.xml.txt"))
    }

    private fun find(node: com.smarted.tv.data.repo.OnePlayNodeDto, name: String): com.smarted.tv.data.repo.OnePlayNodeDto? {
        if (node.name == name) return node
        for (child in node.children.orEmpty()) {
            find(child, name)?.let { return it }
        }
        return null
    }
}
