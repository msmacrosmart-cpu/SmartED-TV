package com.smarted.tv.data

import com.smarted.tv.data.repo.RepoPackages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras de segurança (Zip Slip/Path Traversal) e reconhecimento de
 * `addon.xml` (spec §5 e §7 do documento de arquitetura).
 */
class RepoPackagesTest {

    @Test
    fun zipSlipAndPathTraversalAreBlocked() {
        assertTrue(RepoPackages.isSafeEntryPath("repo/addon/addon.xml"))
        assertTrue(RepoPackages.isSafeEntryPath("index.json"))
        assertFalse(RepoPackages.isSafeEntryPath("../../etc/passwd"))
        assertFalse(RepoPackages.isSafeEntryPath("a/../../b.json"))
        assertFalse(RepoPackages.isSafeEntryPath("/abs/path.json"))
        assertFalse(RepoPackages.isSafeEntryPath("a\\b.json"))
        assertFalse(RepoPackages.isSafeEntryPath(""))
        assertFalse(RepoPackages.isSafeEntryPath("   "))
        assertFalse(RepoPackages.isSafeEntryPath("./relative.json"))
    }

    @Test
    fun parsesAddonXmlMetadata() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <addon id="plugin.video.demo" name="Demo Addon" version="1.2.3" provider-name="Someone">
              <requires>
                <import addon="xbmc.python" version="3.0.0"/>
                <import addon="plugin.something"/>
              </requires>
              <extension point="xbmc.python.script"/>
            </addon>
        """.trimIndent()
        val info = RepoPackages.parseAddonXml("p/addon.xml", xml)
        assertNotNull(info)
        assertEquals("plugin.video.demo", info!!.id)
        assertEquals("Demo Addon", info.name)
        assertEquals("1.2.3", info.version)
        assertEquals("programs", info.type)
        assertEquals(listOf("xbmc.python", "plugin.something"), info.dependencies)
    }

    @Test
    fun addonWithoutExtensionPointHasNoType() {
        val info = RepoPackages.parseAddonXml(
            "a/addon.xml",
            """<addon id="x.y.z" name="Z" version="9"/>""",
        )
        assertNotNull(info)
        assertNull(info!!.type)
        assertTrue(info.dependencies.isEmpty())
        assertEquals("x.y.z", info.id)
    }

    @Test
    fun parsesRealStyleAddonXmlWithProvidesAndMarkup() {
        // Estrutura REAL do ecossistema (plugin.video.oneplayvip): tags de cor
        // Kodi, provider-name, <provides>video</provides> e summary em pt.
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <addon id="plugin.video.oneplayvip" version="0.0.3"
                name="[COLOR aquamarine][B]ONE[/COLOR][COLOR white]PLAY [COLOR aquamarine]VIP[/B][/COLOR]"
                provider-name="Equipe OnePlay">
              <requires>
                <import addon="script.module.simplejson" />
                <import addon="script.module.six" />
                <import addon="script.module.kodi-six" />
                <import addon="script.module.requests"/>
                <import addon="script.module.pyxbmct" />
              </requires>
              <extension point="xbmc.python.pluginsource" library="app.py">
                <provides>video</provides>
              </extension>
              <extension point="xbmc.addon.metadata">
                <summary lang="pt">OnePlay VIP é um plugin baseado no aplicativo OnePlay Alpha.</summary>
              </extension>
            </addon>
        """.trimIndent()
        val info = RepoPackages.parseAddonXml("plugin.video.oneplayvip/addon.xml", xml)
        requireNotNull(info)
        assertEquals("plugin.video.oneplayvip", info.id)
        assertEquals("ONEPLAY VIP", info.name) // markup Kodi removido, sem tags cruas
        assertEquals("video", info.type) // <provides> vence extension point metadata
        assertEquals("Equipe OnePlay", info.author)
        assertEquals("0.0.3", info.version)
        assertEquals(5, info.dependencies.size)
        assertTrue(info.description.orEmpty().contains("OnePlay VIP"))
    }

    @Test
    fun parsesRepositoryManifestWithMultipleAddons() {
        val manifest = """
            <?xml version="1.0" encoding="UTF-8"?>
            <addons>
              <addon id="plugin.video.a" name="Addon A" version="1.0" provider-name="Autor A">
                <extension point="xbmc.python.pluginsource" library="app.py">
                  <provides>video</provides>
                </extension>
              </addon>
              <addon id="plugin.video.b" name="Addon B" version="2.0">
                <extension point="xbmc.addon.repository"/>
              </addon>
            </addons>
        """.trimIndent()
        val list = RepoPackages.parseAddonsManifest("addons.xml", manifest)
        assertEquals(2, list.size)
        assertEquals("video", list[0].type)
        assertEquals("Autor A", list[0].author)
        assertEquals("repository", list[1].type)
    }

    @Test
    fun zipSlipBlockedOnExtractionPath() {
        // Caminhos usados pela extração segura do pacote Kodi.
        assertFalse(RepoPackages.isSafeEntryPath("plugin.video.x/../../evil.py"))
        assertFalse(RepoPackages.isSafeEntryPath("/etc/passwd"))
        assertTrue(RepoPackages.isSafeEntryPath("plugin.video.x/app.py"))
    }

    @Test
    fun extensionPointMapsToOfficialCategories() {
        assertEquals("video", RepoPackages.typeFromExtensionPoint("kodi.addon.video"))
        assertEquals("pvr", RepoPackages.typeFromExtensionPoint("kodi.pvr.client"))
        assertEquals("music", RepoPackages.typeFromExtensionPoint("kodi.addon.music"))
        assertEquals("subtitles", RepoPackages.typeFromExtensionPoint("xbmc.subtitle.module"))
        assertEquals("metadata", RepoPackages.typeFromExtensionPoint("kodi.metadata.scraper.video"))
        assertEquals("services", RepoPackages.typeFromExtensionPoint("kodi.service"))
        assertEquals("skins", RepoPackages.typeFromExtensionPoint("kodi.skin"))
        assertEquals("repository", RepoPackages.typeFromExtensionPoint("kodi.addon.repository"))
        assertNull(RepoPackages.typeFromExtensionPoint("kodi.context.item"))
    }
}
