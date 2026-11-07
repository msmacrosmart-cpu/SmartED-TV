package com.smarted.tv.core

import com.smarted.tv.core.protocol.AddonException
import com.smarted.tv.core.protocol.AddonUrls
import org.junit.Assert.*
import org.junit.Test

class UrlTest {
    @Test fun normalizesHttpAndTransportUrls() {
        assertEquals("https://example.com/addon/manifest.json", AddonUrls.normalize(" https://example.com/addon/ "))
        assertEquals("https://example.com/manifest.json", AddonUrls.normalize("stremio://example.com/manifest.json"))
        assertEquals("http://192.168.1.5:7000/manifest.json", AddonUrls.normalize("http://192.168.1.5:7000"))
        assertEquals("https://example.com/manifest.json", AddonUrls.normalize("https://example.com/manifest.json#fragment"))
    }
    @Test fun rejectsNonHttpAndEmbeddedCredentials() {
        listOf("", "file:///tmp/manifest.json", "javascript:alert(1)", "https://", "https://user:pass@example.com/manifest.json").forEach {
            assertThrows(AddonException::class.java) { AddonUrls.normalize(it) }
        }
    }
    @Test fun resourceIdsAreSingleEncodedSegments() {
        val url = AddonUrls.resource("https://example.com/addon/manifest.json", "meta", "series", "tt123:2:3")
        assertEquals("https://example.com/addon/meta/series/tt123:2:3.json", url)
        assertTrue(AddonUrls.resource("https://example.com/manifest.json", "meta", "movie", "a/b?c").contains("a%2Fb%3Fc.json"))
    }
    @Test fun extrasPreserveQuerySeparatorsAndEncodeValues() {
        val url = AddonUrls.resource("https://example.com/manifest.json", "catalog", "movie", "top", mapOf("skip" to "100", "search" to "ação & aventura/"))
        assertEquals("https://example.com/catalog/movie/top/search=a%C3%A7%C3%A3o%20%26%20aventura%2F&skip=100.json", url)
    }
    @Test fun resourceUrlNeverExecutesAddonCode() {
        assertTrue(AddonUrls.resource("https://example.com/manifest.json", "catalog", "tv", "../../secret").startsWith("https://example.com/catalog/tv/"))
    }
}
