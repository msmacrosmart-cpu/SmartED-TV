package smarted.corecheck

import com.smarted.tv.core.catalog.VodQuery
import com.smarted.tv.core.epg.EpgMatcher
import com.smarted.tv.core.epg.EpgTimeline
import com.smarted.tv.core.model.BrowseQuery
import com.smarted.tv.core.model.EpgChannelRef
import com.smarted.tv.core.model.SortDirection
import com.smarted.tv.core.model.SortMode
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.model.VodEntry
import com.smarted.tv.core.parse.M3uParser
import com.smarted.tv.core.parse.XmltvParser
import com.smarted.tv.core.repo.RepoIndex
import com.smarted.tv.core.repo.RepoSource
import com.smarted.tv.core.repo.RepoDiff
import com.smarted.tv.core.repo.computeRepoDiff
import com.smarted.tv.core.sources.StreamRules
import com.smarted.tv.core.text.TextNormalizer
import com.smarted.tv.core.time.XmlTime

private var failures = 0
private var checks = 0

private fun expect(condition: Boolean, message: String) {
    checks++
    if (!condition) { failures++; println("FAIL: $message") }
}

private fun <T> expectEq(actual: T, expected: T, message: String) {
    checks++
    if (actual != expected) { failures++; println("FAIL: $message — esperado <$expected>, obtido <$actual>") }
}

fun main() {
    testNormalizer()
    testXmlTime()
    testM3u()
    testXmltv()
    testMatcher()
    testTimeline()
    testVodQuery()
    testRepoDiff()
    testStreamRules()
    println(if (failures == 0) "CORE SELFTEST OK ($checks checks)" else "CORE SELFTEST FAILED: $failures/$checks")
    if (failures > 0) kotlin.system.exitProcess(1)
}

private fun testNormalizer() {
    expectEq(TextNormalizer.normalize("Globo SP HD!"), "globo sp hd", "normaliza acentos/pontuação")
    expectEq(TextNormalizer.normalize("CINÉ MAX"), "cine max", "acentos")
    expect(TextNormalizer.searchMatches("viva gente", "Globo Viva a Gente"), "busca tolerante fora de ordem")
    expect(!TextNormalizer.searchMatches("xyz", "Globo"), "busca negativa")
}

private fun testXmlTime() {
    // 2026-11-07 15:30:00 +0000 → epoch (1794065400 s)
    val utc = XmlTime.parseXmltv("20261107153000 +0000")
    expectEq(utc, 1_794_065_400_000L, "epoch UTC xmltv")
    val withOffset = XmlTime.parseXmltv("20261107123000 -0300")
    expectEq(withOffset, utc, "offset -0300 converte p/ UTC")
    expectEq(XmlTime.parseXmltv("lixo"), null, "texto inválido vira null")
    expectEq(XmlTime.parseIso("1794065400"), utc, "epoch em segundos (xtream)")
}

private fun testM3u() {
    val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-id="globo.sp" tvg-logo="https://logo/globo.png" group-title="BRASIL",Globo SP HD
        #EXTVLCOPT:http-user-agent=SmartED/1.0
        https://edge/live/globo.m3u8
        #EXTINF:-1 tvg-id="telecine" group-title="FILMES",Telecane Pipoca
        http://vod/movie.mkv
        #EXTINF:-1 group-title="SÉRIES",La Casa
        http://vod/episode.mp4
        linha-sem-quebra https://ok
        #EXTINF:0,Sem Url
        #KODIPROP:inputstream=inputstream.adaptive
        https://edge/live/simples
    """.trimIndent()
    val result = M3uParser.parse(playlist)
    expectEq(result.channels.size, 4, "linhas #EXTINF pendentes casam com a próxima URL http")
    expectEq(result.channels[3].name, "Sem Url", "pendente atendido pela URL seguinte")
    val first = result.channels.first()
    expectEq(first.name, "Globo SP HD", "nome após vírgula")
    expectEq(first.groupName, "BRASIL", "group-title")
    expectEq(first.tvgId, "globo.sp", "tvg-id")
    expectEq(first.logoUrl, "https://logo/globo.png", "tvg-logo")
    expectEq(first.headers["User-Agent"], "SmartED/1.0", "EXTVLCOPT user agent")
    expectEq(first.epgKey, "globo sp", "epgKey vem do tvg-id normalizado")
    expect(result.channels[1].groupName == "FILMES", "grupo do segundo")
    expect(result.warnings.any { "URL ignorada" in it }, "linha inválida gera aviso")
}

private fun testXmltv() {
    val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="globo.sp">
            <display-name>Globo SP</display-name>
            <display-name lang="pt">GLOBO São Paulo</display-name>
            <icon src="https://logo/globo.png"/>
          </channel>
          <programme start="20261107153000 +0000" stop="20261107170000 +0000" channel="globo.sp">
            <title>Hora Um — Edição &amp; Atualização</title>
            <desc>Notícias &lt;1&gt;</desc>
            <category>Noticias</category>
          </programme>
          <programme start="20261107170000 +0000" stop="20261107180000 +0000" channel="globo.sp">
            <title>Novela das 7</title>
          </programme>
          <programme start="20200101000000 +0000" stop="20200101010000 +0000" channel="globo.sp">
            <title>Fora da janela</title>
          </programme>
        </tv>
    """.trimIndent()
    val start = 1_794_061_800_000L // 2026-11-07T14:30Z
    val end = 1_794_076_200_000L // 2026-11-07T18:30Z
    val result = XmltvParser.parse(java.io.StringReader(xml), start, end)
    expectEq(result.channels.size, 1, "1 canal")
    expectEq(result.channels["globo.sp"]?.displayNames?.size, 2, "2 display names")
    expectEq(result.programmes.size, 2, "janela filtra programas antigos")
    val programme = result.programmes.first()
    expectEq(programme.title, "Hora Um — Edição & Atualização", "entidades decodificadas")
    expectEq(programme.description, "Notícias <1>", "entidades no desc")
    expectEq(programme.stopMs - programme.startMs, 90 * 60_000L, "duração")
    expectEq(result.channels["globo.sp"]?.logoUrl, "https://logo/globo.png", "ícone do canal")
}

private fun testMatcher() {
    val refs = listOf(
        EpgChannelRef("globo.sp", listOf("Globo SP", "TV Globo São Paulo")),
        EpgChannelRef("telecine-pipoca", listOf("Telecine Pipoca HD")),
        EpgChannelRef("band-4k", listOf("Band 4K")),
    )
    val byId = EpgMatcher.plan("globo sp", "Qualquer Nome", refs)
    expectEq(byId?.epgChannel, "globo.sp", "id normalizado casa direto")
    val byName = EpgMatcher.plan("", "TELECINE PIPOCA", refs)
    expectEq(byName?.epgChannel, "telecine-pipoca", "nome exato casa")
    val fuzzy = EpgMatcher.plan("", "Telecine Pipoca HD FHD", refs)
    expectEq(fuzzy?.epgChannel, "telecine-pipoca", "sufixos de qualidade são ignorados")
    val weak = EpgMatcher.plan("", "Canal Aleatório 999", refs)
    expect(weak == null, "sem match não inventa")
    val broken = EpgMatcher.plan("", "Band", refs, previous = com.smarted.tv.core.model.EpgLink("band", "band-4k", 96))
    expectEq(broken?.epgChannel, "band-4k", "auto-cura mantém vínculo existente")
    val stale = EpgMatcher.plan("", "Band", listOf(EpgChannelRef("outro", listOf("Outro"))), previous = com.smarted.tv.core.model.EpgLink("band", "band-4k", 96))
    expect(stale == null, "vínculo órfão é descartado")
}

private fun testTimeline() {
    val now = 1_794_946_200_000L + 10 * 60_000L // 15:40
    val programmes = listOf(
        com.smarted.tv.core.model.Programme("c", now - 10 * 60_000L, now + 50 * 60_000L, "Ao Vivo Agora"),
        com.smarted.tv.core.model.Programme("c", now + 50 * 60_000L, now + 110 * 60_000L, "Depois"),
    )
    val nowNext = EpgTimeline.nowNext(programmes, now)
    expectEq(nowNext.now?.title, "Ao Vivo Agora", "programa atual")
    expectEq(nowNext.next?.title, "Depois", "próximo programa")
    expect(nowNext.progressPercent in 10..25, "progresso coerente (${nowNext.progressPercent})")
    val grid = EpgTimeline.upcoming(programmes, now, limit = 5)
    expectEq(grid.size, 2, "grade inclui atual + próximos")
    expectEq(grid.first().title, "Ao Vivo Agora", "grade começa no atual")
    val empty = EpgTimeline.nowNext(emptyList(), now)
    expect(empty.now == null && empty.next == null, "sem EPG → vazio honesto")
}

private fun vod(name: String, cat: String, added: Long, rating: Double?, year: Int?) = VodEntry(
    id = name.hashCode().toLong(), sourceId = 1, favKey = "vod:1:${name.lowercase()}", kind = Tab.MOVIES, externalKey = name,
    name = name, category = cat, year = year, rating = rating, addedMs = added,
    posterUrl = null, backdropUrl = null, plot = "", streamUrl = "http://x/$name.mp4", runtimeMinutes = null,
)

private fun testVodQuery() {
    val entries = listOf(
        vod("Zebra o Filme", "Lançamentos", 3, 8.0, 2024),
        vod("a Abelha", "Clássicos", 1, 9.0, 1999),
        vod("Meio Termo", "Clássicos", 2, 5.0, 2005),
    )
    val az = VodQuery.order(entries, BrowseQuery(sort = SortMode.NAME, direction = SortDirection.ASC))
    expectEq(az.map { it.name }, listOf("a Abelha", "Meio Termo", "Zebra o Filme"), "A-Z ignorando caixa/acentos")
    val desc = VodQuery.order(entries, BrowseQuery(sort = SortMode.NAME, direction = SortDirection.DESC))
    expectEq(desc.first().name, "Zebra o Filme", "Z-A")
    val added = VodQuery.order(entries, BrowseQuery(sort = SortMode.ADDED, direction = SortDirection.DESC))
    expectEq(added.first().name, "Zebra o Filme", "últimos adicionados")
    val top = VodQuery.order(entries, BrowseQuery(sort = SortMode.RATING, direction = SortDirection.DESC))
    expectEq(top.first().name, "a Abelha", "mais votados por nota")
    val category = VodQuery.order(entries, BrowseQuery(category = "Clássicos"))
    expectEq(category.size, 2, "filtro de categoria")
    val search = VodQuery.order(entries, BrowseQuery(search = "zebra filme"))
    expectEq(search.size, 1, "busca fora de ordem")
    val cats = VodQuery.categories(entries)
    expectEq(cats.first().name, "Clássicos", "contagem por categoria")
}

private fun testRepoDiff() {
    val index = RepoIndex(
        name = "Pacote BR",
        sources = listOf(
            RepoSource("Canais", com.smarted.tv.core.model.SourceKind.M3U, "https://a/lista.m3u", epgUrl = "https://a/epg"),
            RepoSource("VOD", com.smarted.tv.core.model.SourceKind.M3U, "https://a/vod.m3u"),
        ),
    )
    val existing = mapOf(
        RepoDiff.keyOf(index.sources[0]) to Triple("Canais Antigo", "https://a/epg", 720), // nome mudou → updated
        RepoDiff.keyOf(index.sources[1]) to Triple("VOD", "", 720), // igual → nada
        "M3U|https://a/removida" to Triple("X", "", 720), // sumiu do índice → removed
    )
    val diff = computeRepoDiff(index, existing)
    expectEq(diff.added.size, 0, "nada novo")
    expectEq(diff.updated.size, 1, "renomeada detectada")
    expectEq(diff.removed, listOf("M3U|https://a/removida"), "remoção detectada")
    expect(!diff.isEmpty, "diff não vazio")
}

private fun testStreamRules() {
    expect(StreamRules.isProbablyPlayable("https://a/b.m3u8"), "hls ok")
    expect(!StreamRules.isProbablyPlayable("javascript:alert(1)"), "esquema hostil bloqueado")
    expect(StreamRules.looksLikeHls("https://a/b.m3u8?token=x"), "hls com query")
    expectEq(StreamRules.cleanVodTitle("Interestelar (2014) [4K]", Tab.MOVIES), "Interestelar", "limpa título")
}
