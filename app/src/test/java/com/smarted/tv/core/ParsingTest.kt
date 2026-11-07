package com.smarted.tv.core

import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.core.parse.M3uParser
import com.smarted.tv.core.parse.XmltvParser
import com.smarted.tv.core.repo.RepoDiff
import com.smarted.tv.core.repo.RepoIndex
import com.smarted.tv.core.repo.RepoSource
import com.smarted.tv.core.repo.computeRepoDiff
import com.smarted.tv.core.sources.FavKeys
import com.smarted.tv.core.sources.StreamRules
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.text.TextNormalizer
import com.smarted.tv.core.time.XmlTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {
    @Test fun `lowercases strips accents and punctuation`() {
        assertEquals("globo sp hd", TextNormalizer.normalize("Globo SP HD!"))
        assertEquals("cine max", TextNormalizer.normalize("CINÉ MAX"))
        assertEquals("sao paulo", TextNormalizer.normalize("São Paulo"))
    }

    @Test fun `out-of-order token search`() {
        assertTrue(TextNormalizer.searchMatches("viva gente", "Globo Viva a Gente"))
        assertFalse(TextNormalizer.searchMatches("xyz", "Globo"))
        assertTrue(TextNormalizer.searchMatches("", "qualquer"))
    }

    @Test fun `category key is stable`() {
        assertEquals(TextNormalizer.categoryKey("Filmes 4K"), TextNormalizer.categoryKey("filmes 4k"))
    }
}

class XmlTimeTest {
    @Test fun `xmltv timestamps convert to utc epoch`() {
        assertEquals(1_794_065_400_000L, XmlTime.parseXmltv("20261107153000 +0000"))
        assertEquals(1_794_065_400_000L, XmlTime.parseXmltv("20261107123000 -0300"))
        assertNull(XmlTime.parseXmltv("lixo"))
    }

    @Test fun `xtream epoch seconds`() {
        assertEquals(1_794_065_400_000L, XmlTime.parseIso("1794065400"))
    }
}

class M3uParserTest {
    @Test fun `parses attributes groups and headers`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="globo.sp" tvg-logo="https://logo/globo.png" group-title="BRASIL",Globo SP HD
            #EXTVLCOPT:http-user-agent=SmartED/1.0
            https://edge/live/globo.m3u8
        """.trimIndent()
        val result = M3uParser.parse(playlist)
        assertEquals(1, result.channels.size)
        val channel = result.channels.first()
        assertEquals("Globo SP HD", channel.name)
        assertEquals("BRASIL", channel.groupName)
        assertEquals("globo.sp", channel.tvgId)
        assertEquals("https://logo/globo.png", channel.logoUrl)
        assertEquals("SmartED/1.0", channel.headers["User-Agent"])
        assertEquals("globo sp", channel.epgKey)
    }

    @Test fun `handles name after last comma and bare urls`() {
        val result = M3uParser.parse("#EXTINF:-1 tvg-name=\"x\",Canal, com Sufixo\nhttp://a/b.ts")
        assertEquals("Canal, com Sufixo", result.channels.single().name)
    }

    @Test fun `ignores unknown schemes with warning`() {
        val result = M3uParser.parse("#EXTINF:-1,Ruim\nfile:///etc/passwd")
        assertTrue(result.channels.isEmpty())
        assertTrue(result.warnings.any { "URL ignorada" in it })
    }

    @Test fun `extgrp fallback and kodi props tolerated`() {
        val result = M3uParser.parse("#EXTGRP:ESPORTES\n#EXTINF:0,Canal A\n#KODIPROP:inputstream=adaptive\nhttps://x/a.m3u8")
        assertEquals("ESPORTES", result.channels.single().groupName)
    }
}

class XmltvParserTest {
    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="globo.sp">
            <display-name>Globo SP</display-name>
            <display-name lang='pt'>GLOBO São Paulo</display-name>
            <icon src="https://logo/globo.png"/>
          </channel>
          <programme start="20261107153000 +0000" stop="20261107170000 +0000" channel="globo.sp">
            <title>Hora Um — Edição &amp; Atualização</title>
            <desc>Notícias &lt;1&gt;</desc>
            <category>Noticias</category>
          </programme>
          <programme start="20261107170000 +0000" stop="20261107180000 +0000" channel="globo.sp">
            <title><![CDATA[Novela das 7]]></title>
          </programme>
          <programme start="20200101000000 +0000" stop="20200101010000 +0000" channel="globo.sp">
            <title>Fora da janela</title>
          </programme>
        </tv>
    """.trimIndent()

    @Test fun `channels programmes entities and window`() {
        val start = 1_794_061_800_000L
        val end = 1_794_076_200_000L
        val result = XmltvParser.parse(java.io.StringReader(xml), start, end)
        assertEquals(1, result.channels.size)
        assertEquals(2, result.programmes.size)
        val first = result.programmes.first()
        assertEquals("Hora Um — Edição & Atualização", first.title)
        assertEquals("Notícias <1>", first.description)
        assertEquals("Noticias", first.category)
        assertEquals("https://logo/globo.png", result.channels["globo.sp"]?.logoUrl)
        assertEquals(2, result.channels["globo.sp"]?.displayNames?.size)
        assertEquals(5400_000L, first.stopMs - first.startMs)
        assertTrue(result.programmes[1].title == "Novela das 7")
    }

    @Test fun `empty document reports warning without crash`() {
        val result = XmltvParser.parse(java.io.StringReader("<tv></tv>"))
        assertTrue(result.programmes.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }
}

class RepoDiffTest {
    @Test fun `diff detects add update remove`() {
        val index = RepoIndex(
            "Pacote",
            listOf(
                RepoSource("Canais", SourceKind.M3U, "https://a/lista.m3u", epgUrl = "e1"),
                RepoSource("VOD", SourceKind.M3U, "https://a/vod.m3u"),
            ),
        )
        val existing = mapOf(
            RepoDiff.keyOf(index.sources[0]) to Triple("Antigo", "e1", 720),
            RepoDiff.keyOf(index.sources[1]) to Triple("VOD", "", 720),
            "M3U|https://a/removida" to Triple("X", "", 720),
        )
        val diff = computeRepoDiff(index, existing)
        assertTrue(diff.added.isEmpty())
        assertEquals(1, diff.updated.size)
        assertEquals(listOf("M3U|https://a/removida"), diff.removed)
    }
}

class FavKeysTest {
    @Test fun `collision gets suffix and survives reorder`() {
        val used = hashSetOf<String>()
        val first = FavKeys.unique(3, "ch", "globo", used)
        val second = FavKeys.unique(3, "ch", "globo", used)
        assertEquals("ch:3:globo", first)
        assertEquals("ch:3:globo#2", second)
        assertNotNull(FavKeys.unique(3, "vod", "globo", used))
    }

    @Test fun `stream rules classify vod extensions`() {
        assertTrue(StreamRules.isProbablyPlayable("https://a/b.m3u8"))
        assertFalse(StreamRules.isProbablyPlayable("javascript:alert(1)"))
        assertTrue(StreamRules.looksLikeHls("https://a/b.m3u8?t=1"))
        assertEquals("Interestelar", StreamRules.cleanVodTitle("Interestelar (2014) [4K]", Tab.MOVIES))
    }
}
