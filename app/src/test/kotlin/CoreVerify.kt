/*
 * Testes executáveis do núcleo puro do SmartED TV.
 *
 * Roda via `./tools/verify-core.sh` (JVM comum, sem Android) e também é
 * compatível com `./gradlew test` quando convertido em testes JUnit.
 */
import com.smarted.tv.core.addons.KodiRepoParser
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.util.DecodeUtils
import com.smarted.tv.core.util.M3uParser
import com.smarted.tv.core.util.SourceDetector
import com.smarted.tv.core.epg.EpgTimeline
import com.smarted.tv.core.util.LanguageFilter
import com.smarted.tv.core.util.TextUtils
import com.smarted.tv.core.util.XmltvParser

private var passed = 0
private var failed = 0

private fun check(name: String, condition: Boolean, detail: String = "") {
    if (condition) {
        passed++
        println("  ✓ $name")
    } else {
        failed++
        println("  ✗ $name ${if (detail.isNotBlank()) "→ $detail" else ""}")
    }
}

private fun eq(name: String, expected: Any?, actual: Any?) =
    check(name, expected == actual, "esperado=<$expected> obtido=<$actual>")

fun main() {
    verifyLanguageFilter()
    verifyEpgTimeline()
    println("== TextUtils ==")
    eq("acentos", "cancao", TextUtils.stripAccents("canção"))
    eq("normalize pontuação", "globo hd", TextUtils.normalize("Globo  HD!!!"))
    eq("cleanTitle remove tag", "Globo", TextUtils.cleanTitle("Globo [1080p]"))
    eq("cleanTitle remove parêntese", "SBT", TextUtils.cleanTitle("SBT (720p)"))
    check("sameTitle tolerante", TextUtils.sameTitle("Globo HD", "Globo"))
    check("similarity alta", TextUtils.similarity("A Era do Gelo", "A Era do Gelo 2") > 0.5f)
    eq("initials", "GE", TextUtils.initials("Globo Esporte"))
    eq("youtube watch", "dQw4w9WgXcQ", TextUtils.youtubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    eq("youtube short", "dQw4w9WgXcQ", TextUtils.youtubeId("https://youtu.be/dQw4w9WgXcQ?t=10"))
    eq("youtube embed", "dQw4w9WgXcQ", TextUtils.youtubeId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
    eq("youtube id puro", "dQw4w9WgXcQ", TextUtils.youtubeId("dQw4w9WgXcQ"))
    eq("duração", "1h 42min", TextUtils.formatDuration(6120))
    eq("duração curta", "5min", TextUtils.formatDuration(300))
    eq("clock", "1:02:03", TextUtils.formatClock(3_723_000))
    eq("clock curto", "02:03", TextUtils.formatClock(123_000))
    eq("stripHtml", "Primeira\n\nSegunda", TextUtils.stripHtml("<p>Primeira</p><p>Segunda</p>"))
    eq("entidades", "A & B", TextUtils.stripHtml("A &amp; B"))
    eq("ellipsize", "palavra longa…", TextUtils.ellipsize("palavra longa texto extra", 16))
    eq("ageRating", "12", TextUtils.ageRating("12 anos"))
    eq("formatTime 00:00", "00:00", TextUtils.formatTime(0))
    eq("formatTime 13:30", "13:30", TextUtils.formatTime(13 * 3600_000L + 30 * 60_000L))

    println("== M3uParser ==")
    val playlist = """
        #EXTM3U url-tvg="https://epg.best/brazil.xml"
        #EXTINF:-1 tvg-id="globo.br" tvg-logo="https://logo/globo.png" group-title="Canais | Abertos",Globo HD
        #EXTVLCOPT:http-user-agent=SmartED
        http://stream.example/globo.m3u8
        #EXTINF:-1 tvg-id="sbt.br" group-title="Canais | Abertos",SBT
        http://stream.example/sbt.m3u8
        #EXTINF:-1 tvg-id="" group-title="Filmes | Ação",Filme Teste (2019)
        http://stream.example/filme.mp4
        #EXTINF:-1 group-title="Séries",Minha Serie S01E05
        http://stream.example/serie.mkv
    """.trimIndent()

    val parsed = M3uParser.parse(playlist, addonId = "brazuca.play")
    eq("total itens", 4, parsed.items.size)
    eq("epg do header", "https://epg.best/brazil.xml", parsed.epgUrl)
    eq("tvg-id", "globo.br", parsed.items[0].tvgId)
    eq("group", "Canais | Abertos", parsed.items[0].group)
    eq("logo", "https://logo/globo.png", parsed.items[0].poster)
    eq("qualidade HD", "HD", parsed.items[0].quality)
    eq("kind canal", MediaKind.LIVE, parsed.items[0].kind)
    eq("kind filme", MediaKind.MOVIE, parsed.items[2].kind)
    eq("kind serie", MediaKind.SERIES, parsed.items[3].kind)
    eq("temporada/episódio", "T1E5", parsed.items[3].subtitle)
    eq("headers VLC", "SmartED", M3uParser.ItemHeaderStore.get("http://stream.example/globo.m3u8")["User-Agent"])
    eq("title limpo", "Globo", parsed.items[0].title)

    val qual = listOf(
        "Canal 4K" to "4K",
        "Canal 1080p" to "1080p",
        "Canal 720p" to "720p",
        "Canal SD" to null,
        "Canal sem nada" to null,
    )
    for ((input, expected) in qual) eq("qualidade '$input'", expected, M3uParser.inferQuality(input))

    eq("attrs", "a", M3uParser.parseAttributes("""#EXTINF:-1 tvg-id="a" group-title="b"""")["tvg-id"])

    println("== XmltvParser ==")
    val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv date="2026-11-07T00:00:00Z">
          <channel id="globo.br">
            <display-name>Globo</display-name>
            <icon src="https://logo/globo.png"/>
          </channel>
          <programme start="20261107120000 -0300" stop="20261107130000 -0300" channel="globo.br">
            <title>Jornal Hoje</title>
            <desc>Notícias do meio-dia &amp; mais.</desc>
            <category>Jornalismo</category>
          </programme>
          <programme start="20261107130000 -0300" stop="20261107140000 -0300" channel="globo.br">
            <title>Sessão da Tarde</title>
          </programme>
        </tv>
    """.trimIndent()

    val epgResult = XmltvParser.parse(xml)
    eq("canais", 1, epgResult.channels.size)
    eq("nome canal", "Globo", epgResult.channels["globo.br"]?.name)
    eq("programas", 2, epgResult.programs["globo.br"]?.size)
    eq("título programa", "Jornal Hoje", epgResult.programs["globo.br"]?.first()?.title)
    eq("desc unescaped", "Notícias do meio-dia & mais.", epgResult.programs["globo.br"]?.first()?.description)

    // 2026-11-07T12:00:00-03:00 == 15:00 UTC
    val expectedMs = XmltvParser.epochFromUtc(2026, 11, 7, 15, 0, 0)
    eq("conversão de horário", expectedMs, XmltvParser.parseXmltvTime("20261107120000 -0300"))
    eq("sem offset", XmltvParser.epochFromUtc(2026, 11, 7, 12, 0, 0), XmltvParser.parseXmltvTime("20261107120000"))

    val index = XmltvParser.buildChannelIndex(epgResult.channels)
    eq("índice por nome", "globo.br", index[TextUtils.titleKey("Globo")])

    val program = epgResult.programs["globo.br"]!!.first()
    eq("durationMs", 3_600_000L, program.durationMs)
    check("progresso 0 no início", program.progressAt(program.startMs) == 0f)
    check("progresso 1 no fim", program.progressAt(program.endMs) == 1f)
    check("progresso 0.5 no meio", kotlin.math.abs(program.progressAt(program.startMs + 1_800_000L) - 0.5f) < 0.01f)

    println("== KodiRepoParser ==")
    val addonsXml = """
        <addons>
          <addon id="plugin.video.brazuca" name="Brazuca Play" version="2.4.0" provider-name="SmartED">
            <requires><import addon="xbmc.python" version="3.0.0"/></requires>
            <extension point="xbmc.addon.metadata">
              <summary lang="pt_BR">Canais, filmes e séries</summary>
              <description lang="pt_BR">Addon com catálogo brasileiro.</description>
              <platform>all</platform>
              <assets>
                <icon>https://cdn/icon.png</icon>
                <fanart>https://cdn/fanart.jpg</fanart>
              </assets>
            </extension>
          </addon>
          <addon id="plugin.video.gsource" name="GSource" version="1.2.3">
            <extension point="xbmc.addon.metadata">
              <summary>Guia EPG e VOD</summary>
              <description>Grade de programação completa.</description>
            </extension>
          </addon>
        </addons>
    """.trimIndent()

    val repos = KodiRepoParser.parseAddonsXml(addonsXml, "https://vkodi.net/repo")
    eq("addons encontrados", 2, repos.size)
    eq("id addon", "plugin.video.brazuca", repos[0].id)
    eq("nome addon", "Brazuca Play", repos[0].name)
    eq("versão", "2.4.0", repos[0].version)
    eq("icon", "https://cdn/icon.png", repos[0].icon)
    eq("summary", "Canais, filmes e séries", repos[0].summary)
    eq("zip url", "https://vkodi.net/repo/zips/plugin.video.brazuca/plugin.video.brazuca-2.4.0.zip",
        KodiRepoParser.zipUrl("https://vkodi.net/repo", "plugin.video.brazuca", "2.4.0"))

    val html = """
        <html><body><pre>
        <a href="zips/">zips/</a>
        <a href="plugin.video.brazuca-2.4.0.zip">plugin.video.brazuca-2.4.0.zip</a>
        <a href="repository.brazuca-1.0.0.zip">repository.brazuca-1.0.0.zip</a>
        </pre></body></html>
    """.trimIndent()
    val htmlRepos = KodiRepoParser.parseHtmlIndex(html, "https://vkodi.net/repo/")
    check("html index encontrou zips", htmlRepos.size >= 2, "obtido=${htmlRepos.size}")

    println("== SourceDetector ==")
    eq("xtream", com.smarted.tv.core.model.SourceFormat.XTREAM,
        SourceDetector.formatFor("http://host/get.php?username=x&password=y"))
    eq("m3u", com.smarted.tv.core.model.SourceFormat.M3U, SourceDetector.formatFor("https://a/b.m3u"))
    eq("xmltv", com.smarted.tv.core.model.SourceFormat.XMLTV, SourceDetector.formatFor("https://a/b.xml"))
    eq("normaliza repo", "https://vkodi.net/repo", SourceDetector.normalizeRepoUrl("vkodi.net/repo"))
    check("candidatos de índice", SourceDetector.indexCandidates("https://vkodi.net/repo").contains("https://vkodi.net/repo/addons.xml"))

    println("== DecodeUtils ==")
    eq("resolve absoluto", "https://x/y.m3u8", DecodeUtils.resolve("https://base/dir/lista.m3u", "https://x/y.m3u8"))
    eq("resolve raiz", "https://base/outro.m3u", DecodeUtils.resolve("https://base/dir/lista.m3u", "/outro.m3u"))
    eq("urlDecode", "A B", DecodeUtils.urlDecode("A%20B"))

    println()
    println("──────────────────────────────────────────────")
    println("$passed testes OK · $failed falha(s)")
    if (failed > 0) {
        throw AssertionError("$failed teste(s) falharam")
    }
}

// ---------------------------------------------------------------------------
// Filtro de idioma (separação PT-BR dos repositórios públicos)
// ---------------------------------------------------------------------------

private fun verifyLanguageFilter() {
    // Aceita: metadados explícitos
    check(
        "pt por tvg-language",
        LanguageFilter.isPortuguese("Some Channel", language = "pt"),
    )
    check(
        "pt por tvg-country BR",
        LanguageFilter.isPortuguese("Some Channel", country = "BR"),
    )
    // Aceita: marcadores no título
    check(
        "pt por DUBLADO no título",
        LanguageFilter.isPortuguese("Missão Impossível (Dublado)"),
    )
    check(
        "pt por grupo Brasil",
        LanguageFilter.isPortuguese("Globo SP", group = "Brasil"),
    )
    // Rejeita: idioma explícito estrangeiro
    check(
        "rejeita tvg-language en",
        !LanguageFilter.isPortuguese("The Office", language = "en"),
    )
    check(
        "rejeita tvg-country US",
        !LanguageFilter.isPortuguese("NBC News", country = "US"),
    )
    check(
        "rejeita marcador [EN]",
        !LanguageFilter.isPortuguese("Breaking News [EN]"),
    )
    // Rejeita: título claramente espanhol
    check(
        "rejeita título em espanhol",
        !LanguageFilter.isPortuguese("El Señor de los Cielos"),
    )
    // Aceita: título em português sem metadados
    check(
        "pt por palavras funcionais",
        LanguageFilter.isPortuguese("A Guerra dos Mundos"),
        "verdict=${LanguageFilter.verdict("A Guerra dos Mundos")}",
    )
    check(
        "rejeita título sem evidência",
        !LanguageFilter.isPortuguese("XYZ"),
    )
}

// ---------------------------------------------------------------------------
// Grade do EPG: mínimo de 12 horas contínuas (requisito de produto)
// ---------------------------------------------------------------------------

private fun verifyEpgTimeline() {
    val hour = 60L * 60L * 1000L
    val t0 = 1_700_000_000_000L
    fun program(title: String, startHour: Int, durationHours: Int) = com.smarted.tv.core.model.EpgProgram(
        channelId = "globo",
        title = title,
        startMs = t0 + startHour * hour,
        endMs = t0 + (startHour + durationHours) * hour,
    )

    // Grade com buraco no meio: 0-2h e 6-8h preenchidas.
    val sparse = listOf(
        program("Manhã 1", 0, 2),
        program("Manhã 2", 6, 2),
    )
    val padded = EpgTimeline.pad(sparse, t0, t0 + 12 * hour, "globo")

    check("timeline cobre 12h mesmo com lacunas", EpgTimeline.hasMinimumWindow(padded, t0))
    check(
        "timeline começa exatamente na janela",
        padded.first().startMs == t0,
        "obtido=${padded.first().startMs}",
    )
    check(
        "timeline termina na janela",
        padded.last().endMs == t0 + 12 * hour,
        "obtido=${padded.last().endMs}",
    )
    check(
        "lacunas viraram blocos 'Sem informação'",
        padded.count { it.title == EpgTimeline.GAP_TITLE } >= 2,
        "blocos=${padded.count { it.title == EpgTimeline.GAP_TITLE }}",
    )
    check(
        "grade preenchida não tem sobreposição",
        padded.zipWithNext().all { (a, b) -> a.endMs <= b.startMs },
    )
    check(
        "sem duplicar programas reais",
        padded.count { it.title == "Manhã 1" } == 1,
    )

    // Grade vazia (canal sem EPG): a régua ainda precisa existir.
    val empty = EpgTimeline.pad(emptyList(), t0, t0 + 12 * hour, "sbt")
    check("canal sem grade recebe 12h contínuas", empty.size == 1 && EpgTimeline.hasMinimumWindow(empty, t0))

    // Cobertura efetiva
    check("cobertura de grade cheia", EpgTimeline.coverageMs(padded) >= 12 * hour - 1)
    check("cobertura ignora sobreposição", EpgTimeline.coverageMs(listOf(program("A", 0, 3), program("B", 2, 3))) == 5 * hour)

    // Janela mínima de 12h nunca é reduzida
    check("MIN_HOURS é 12", EpgTimeline.MIN_HOURS == 12)
}
