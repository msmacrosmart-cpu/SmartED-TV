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
