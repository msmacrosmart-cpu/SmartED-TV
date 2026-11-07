/*
 * Testes executáveis do núcleo puro do SmartED TV.
 *
 * Roda via `./tools/verify-core.sh` (JVM comum, sem Android) e também é
 * compatível com `./gradlew test` quando convertido em testes JUnit.
 */
import com.smarted.tv.core.addons.AddonBrowser
import com.smarted.tv.core.addons.BrowserEntry
import com.smarted.tv.core.addons.AddonTree
import com.smarted.tv.core.addons.Elementum
import com.smarted.tv.core.addons.KodiRepoParser
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.util.DecodeUtils
import com.smarted.tv.core.util.M3uParser
import com.smarted.tv.core.util.SourceDetector
import com.smarted.tv.core.epg.EpgTimeline
import com.smarted.tv.core.epg.EpgMatcher
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.util.ContentClassifier
import com.smarted.tv.core.util.LanguageFilter
import com.smarted.tv.core.util.SeriesGrouper
import com.smarted.tv.core.util.SourceTargeting
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
    verifyEpgMatcher()
    verifyContentSeparation()
    verifySeriesGrouper()
    verifySourceTargeting()
    verifyAddonBrowser()
    verifyElementum()
    verifyAddonTree()
    println("== TextUtils ==")
    eq("acentos", "cancao", TextUtils.stripAccents("canção"))
    eq("normalize pontuação", "globo hd", TextUtils.normalize("Globo  HD!!!"))
    eq("cleanTitle remove tag", "Globo", TextUtils.cleanTitle("Globo [1080p]"))
    eq("cleanTitle remove parêntese", "SBT", TextUtils.cleanTitle("SBT (720p)"))
    check("sameTitle tolerante", TextUtils.sameTitle("Globo HD", "Globo"))
    check("similarity alta", TextUtils.similarity("A Era do Gelo", "A Era do Gelo 2") > 0.5f)
    eq("initials", "GE", TextUtils.initials("Globo Esporte"))
    // EPG chega em UTC: formatTime puro é UTC, formatLocalTime aplica o fuso.
    // 1700000000000 = 14/11/2023 22:13 UTC
    eq("hora UTC", "22:13", TextUtils.formatTime(1_700_000_000_000L))
    eq("hora local aplica deslocamento", "19:13", TextUtils.formatLocalTime(1_700_000_000_000L, -3 * 60 * 60 * 1000))
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

// ---------------------------------------------------------------------------
// EPG inteligente: cruzamento por nome quando o tvg-id falta ou está errado
// ---------------------------------------------------------------------------

private fun verifyEpgMatcher() {
    println("== EpgMatcher ==")

    // Normalização: some o ruído de qualidade e os separadores da lista.
    eq("normaliza HD", "globo", EpgMatcher.normalize("Globo HD"))
    eq("normaliza pipes e backup", "globo", EpgMatcher.normalize("Globo FHD | Backup"))
    eq("normaliza colchetes", "sbt", EpgMatcher.normalize("SBT [1080p]"))
    eq("normaliza parênteses", "record", EpgMatcher.normalize("Record (720p)"))
    check(
        "remove prefixo de emissora",
        EpgMatcher.normalize("TV Cultura") == EpgMatcher.normalize("Cultura"),
        "tv cultura=${EpgMatcher.normalize("TV Cultura")} cultura=${EpgMatcher.normalize("Cultura")}",
    )
    eq("nome de exibição preserva o canal", "Globo", EpgMatcher.displayName("Globo HD | Backup"))

    val channels = mapOf(
        "globo" to XmltvParser.Channel("globo", "Globo"),
        "globonews" to XmltvParser.Channel("globonews", "Globo News"),
        "band" to XmltvParser.Channel("band", "Band"),
        "bandnews" to XmltvParser.Channel("bandnews", "BandNews"),
        "espn" to XmltvParser.Channel("espn", "ESPN"),
        "espn2" to XmltvParser.Channel("espn2", "ESPN 2"),
        "sbt" to XmltvParser.Channel("sbt", "SBT"),
    )

    // Casos reais de lista pública: nome sujo deve cair no canal certo.
    eq("casa Globo HD → Globo", "globo", EpgMatcher.bestMatch("Globo HD", null, channels)?.id)
    eq("casa com sufixo de espelho", "globo", EpgMatcher.bestMatch("Globo FHD | Backup", null, channels)?.id)
    eq("casa SBT com tag de qualidade", "sbt", EpgMatcher.bestMatch("SBT [1080p]", null, channels)?.id)
    eq("casa pelo tvg-id exato", "band", EpgMatcher.bestMatch("Canal qualquer", "band", channels)?.id)
    eq("casa tvg-id com sufixo de país", "globo", EpgMatcher.bestMatch("x", "globo.br", channels)?.id)

    // O que NÃO pode acontecer: confundir a matriz com o canal qualificador.
    eq("não confunde Band com BandNews", "bandnews", EpgMatcher.bestMatch("Band News", null, channels)?.id)
    eq("não confunde Globo com Globo News", "globonews", EpgMatcher.bestMatch("Globo News HD", null, channels)?.id)

    // Sem candidato confiável → nenhum match (melhor vazio do que grade errada).
    check(
        "canal inexistente não casa",
        EpgMatcher.bestMatch("Sportv3", null, channels) == null,
        "obtido=${EpgMatcher.bestMatch("Sportv3", null, channels)?.id}",
    )

    // Score alto para o caso certo e baixo para o errado.
    check(
        "score do canal certo é alto",
        EpgMatcher.score("Globo HD", "Globo") >= EpgMatcher.STRONG_THRESHOLD,
        "score=${EpgMatcher.score("Globo HD", "Globo")}",
    )
    check(
        "score entre canais diferentes é baixo",
        EpgMatcher.score("Band", "Band News") < EpgMatcher.DEFAULT_THRESHOLD,
        "score=${EpgMatcher.score("Band", "Band News")}",
    )

    // Índice para casamento exato rápido.
    val index = EpgMatcher.buildIndex(channels)
    eq("índice normalizado", "globonews", index["globo news"])
}

// ---------------------------------------------------------------------------
// Separação rigorosa: canal ao vivo NUNCA na aba de filmes
// ---------------------------------------------------------------------------

private fun verifyContentSeparation() {
    println("== ContentClassifier ==")

    val liveUrl = "https://exemplo.com/globo/index.m3u8"
    val movieUrl = "https://exemplo.com/movie/123/456.mkv"
    val xtreamVod = "http://painel.tv:8080/movie/user/pass/789.mp4"
    val rtmp = "rtmp://exemplo.com/live/espn"

    check("stream HLS é reconhecido", ContentClassifier.isStream(liveUrl))
    check("arquivo MKV é estático", ContentClassifier.isStaticFile(movieUrl))
    check("extensão ignora query string", ContentClassifier.isStaticFile("https://x/f.mp4?token=abc"))
    check("caminho de VOD detectado", ContentClassifier.hasVodPath(xtreamVod))

    // O defeito original: canal ao vivo aparecendo em Filmes.
    check("canal ao vivo recusado em Filmes", !ContentClassifier.acceptsMovie("Globo HD", liveUrl))
    check("RTMP recusado em Filmes", !ContentClassifier.acceptsMovie("ESPN", rtmp))
    check("MP4 aceito em Filmes", ContentClassifier.acceptsMovie("O Auto da Compadecida", movieUrl))
    check("VOD Xtream aceito", ContentClassifier.acceptsMovie("Filme", xtreamVod))
    check("HLS dentro de /movie/ é VOD", ContentClassifier.acceptsMovie("Filme", "https://x/movie/1/2.m3u8"))
    check("episódio não entra em Filmes", !ContentClassifier.acceptsMovie("The Boys S01E02", movieUrl))

    // Canais: o inverso também vale.
    check("stream entra em Canais", ContentClassifier.acceptsLive(liveUrl))
    check("arquivo de /movie/ não entra em Canais", !ContentClassifier.acceptsLive(movieUrl))

    // Séries: só com estrutura de temporada/episódio.
    check("S01E02 é série", ContentClassifier.acceptsSeries("The Boys S01E02", movieUrl))
    check("1x02 é série", ContentClassifier.acceptsSeries("Round 6 1x03", liveUrl))
    check("filme sem episódio não é série", !ContentClassifier.acceptsSeries("O Auto da Compadecida", movieUrl))

    // classifyFor é o contrato usado pelo catálogo.
    eq("classifyFor LIVE aceita canal", MediaKind.LIVE, ContentClassifier.classifyFor(MediaKind.LIVE, "Globo", liveUrl))
    eq("classifyFor MOVIE recusa canal", null, ContentClassifier.classifyFor(MediaKind.MOVIE, "Globo", liveUrl))
    eq("classifyFor SERIES recusa filme", null, ContentClassifier.classifyFor(MediaKind.SERIES, "Filme", movieUrl))

    // Título de filme limpo e ano extraído.
    eq("limpa prefixo de grupo", "Matrix", ContentClassifier.movieTitle("FILMES | Matrix"))
    eq("extrai ano", 1999, ContentClassifier.yearOf("Matrix (1999) 1080p"))
}

// ---------------------------------------------------------------------------
// Séries: episódios soltos → temporadas organizadas
// ---------------------------------------------------------------------------

private fun verifySeriesGrouper() {
    println("== SeriesGrouper ==")

    fun ep(id: String, title: String, url: String = "https://x/$id.mp4") = MediaItem(
        id = id,
        addonId = "a",
        kind = MediaKind.SERIES,
        title = title,
        streamUrl = url,
        poster = "https://art/$id.jpg",
    )

    val items = listOf(
        ep("1", "The Boys S01E01 - A Lei de Homelander"),
        ep("2", "The Boys S01E02 - Cherry"),
        ep("3", "The Boys S02E01 - Prazo"),
        ep("4", "Round 6 1x03"),
        ep("5", "Round 6 1x01"),
    )
    val shows = SeriesGrouper.group(items, "a")

    eq("agrupou em 2 séries", 2, shows.size)
    val boys = shows.firstOrNull { it.title == "The Boys" }
    check("série The Boys existe", boys != null)
    eq("temporadas do The Boys", listOf(1, 2), boys?.details?.seasons?.map { it.number })
    eq("episódios da T1", 2, boys?.details?.seasons?.first()?.episodes?.size)
    eq("total de episódios no contador", 3, boys?.number)
    eq("id estável da série", "show-the-boys", boys?.id)
    check("marca como série na ficha", boys?.details?.isSeries == true)

    val round6 = shows.firstOrNull { it.title?.startsWith("Round 6") == true }
    check("série com formato 1x02 reconhecida", round6 != null, "títulos=${shows.map { it.title }}")
    eq("episódios ordenados de Round 6", listOf(1, 3), round6?.details?.seasons?.first()?.episodes?.map { it.number })

    // Episódio mantém o stream e o vínculo com a série.
    val firstEp = boys?.details?.seasons?.first()?.episodes?.first()
    eq("episódio guarda a URL", "https://x/1.mp4", firstEp?.streamUrl)
    eq("episódio sabe de qual série é", "The Boys", firstEp?.showTitle)

    // Título do show sem o marcador de episódio.
    eq("remove marcador do título", "The Boys", SeriesGrouper.showTitle("The Boys S01E01 - A Lei"))
    eq("remove marcador 1x02", "Round 6", SeriesGrouper.showTitle("Round 6 1x03"))
}

// ---------------------------------------------------------------------------
// Seleção manual de abas (Canais / Filmes / Séries) por fonte
// ---------------------------------------------------------------------------

private fun verifySourceTargeting() {
    println("== SourceTargeting ==")

    eq("lê marcação de duas abas", listOf(MediaKind.LIVE, MediaKind.MOVIE), SourceTargeting.parse("LIVE,MOVIE"))
    eq("aceita nome em português", listOf(MediaKind.MOVIE), SourceTargeting.parse("filmes"))
    eq("aceita separador alternativo", listOf(MediaKind.LIVE, MediaKind.SERIES), SourceTargeting.parse("live;series"))
    eq("ignora token desconhecido", listOf(MediaKind.SERIES), SourceTargeting.parse("SERIES,radioweb"))
    eq("vazio devolve lista vazia", emptyList<MediaKind>(), SourceTargeting.parse(""))
    eq("grava em ordem estável", "LIVE,MOVIE,SERIES", SourceTargeting.encode(
        listOf(MediaKind.SERIES, MediaKind.LIVE, MediaKind.MOVIE),
    ))
    eq("grava sem duplicar", "MOVIE", SourceTargeting.encode(listOf(MediaKind.MOVIE, MediaKind.MOVIE)))
    check("rota para aba marcada", SourceTargeting.routesTo(listOf(MediaKind.MOVIE), MediaKind.MOVIE))
    check("não rota para aba não marcada", !SourceTargeting.routesTo(listOf(MediaKind.MOVIE), MediaKind.LIVE))
    eq("sem marcação usa o tipo legado", listOf(MediaKind.SERIES), SourceTargeting.effective(emptyList(), MediaKind.SERIES))
    eq("com marcação ignora o legado", listOf(MediaKind.LIVE), SourceTargeting.effective(listOf(MediaKind.LIVE), MediaKind.SERIES))
    eq("descrição de duas abas", "Canais + Filmes", SourceTargeting.describe(listOf(MediaKind.LIVE, MediaKind.MOVIE)))
    eq("descrição de todas", "Todas as abas", SourceTargeting.describe(SourceTargeting.ALL))

    // Ida-e-volta: o que a UI grava é o que o catálogo lê.
    val written = SourceTargeting.encode(listOf(MediaKind.LIVE, MediaKind.SERIES))
    eq("ida e volta da marcação", listOf(MediaKind.LIVE, MediaKind.SERIES), SourceTargeting.parse(written))

    // ---- fonte marcada só para Filmes ----
    val moviesOnly = com.smarted.tv.core.model.PublicSource(
        id = "s1",
        name = "Lista de filmes",
        url = "https://exemplo.com/filmes.m3u",
        kind = MediaKind.MOVIE,
        kinds = listOf(MediaKind.MOVIE),
    )
    check("fonte só de filmes alimenta Filmes", moviesOnly.appliesTo(MediaKind.MOVIE))
    check("fonte só de filmes não alimenta Canais", !moviesOnly.appliesTo(MediaKind.LIVE))
    check("fonte só de filmes não é mista", !moviesOnly.isMixed)

    // ---- fonte mista marcada em duas abas ----
    val mixed = moviesOnly.copy(id = "s2", kinds = listOf(MediaKind.LIVE, MediaKind.MOVIE))
    check("fonte mista é mista", mixed.isMixed)
    eq("fonte mista tem 2 destinos", 2, mixed.targets.size)

    // ---- a marcação não sobrepõe a separação de acervo ----
    // Mesmo marcado para FILMES, um canal ao vivo do .m3u8 é recusado.
    check(
        "canal ao vivo de fonte marcada em Filmes é descartado",
        ContentClassifier.classifyFor(MediaKind.MOVIE, "Globo HD", "https://x/globo/index.m3u8") == null,
    )
    check(
        "filme .mkv de fonte mista entra em Filmes",
        ContentClassifier.classifyFor(MediaKind.MOVIE, "Matrix", "https://x/movie/1/2.mkv") == MediaKind.MOVIE,
    )
    check(
        "canal m3u8 da mesma fonte entra em Canais",
        ContentClassifier.classifyFor(MediaKind.LIVE, "Globo HD", "https://x/globo/index.m3u8") == MediaKind.LIVE,
    )
}

/**
 * Navegador de repositório (molde Kodi): pastas TV ao Vivo / Filmes / Séries,
 * classificação por metadados e barreira de formato por pasta.
 */
private fun verifyAddonBrowser() {
    println("== Navegador de repositório (pastas estilo Kodi) ==")

    // ---- classificação por metadados do addon ----
    check("addon de filmes cai em Filmes", MediaKind.MOVIE in AddonBrowser.classify(
        "plugin.video.filmeshd", "Filmes HD", "Lançamentos de cinema",
    ))
    check("addon de filmes não cai em Canais", MediaKind.LIVE !in AddonBrowser.classify(
        "plugin.video.filmeshd", "Filmes HD", "Lançamentos de cinema",
    ))
    check("addon de canais cai em TV ao Vivo", MediaKind.LIVE in AddonBrowser.classify(
        "plugin.video.canais", "Canais ao Vivo", "Lista de canais com EPG",
    ))
    check("addon de séries cai em Séries", MediaKind.SERIES in AddonBrowser.classify(
        "plugin.video.series", "Séries e Novelas", "Temporadas completas",
    ))
    eq(
        "addon genérico aparece nas três pastas",
        3,
        AddonBrowser.classify("plugin.video.diversos", "Diversos", "Conteúdo variado").size,
    )
    check("acento não atrapalha a classificação", MediaKind.SERIES in AddonBrowser.classify(
        "x", "SÉRIES", "",
    ))

    // ---- barreira de formato: filmes só aceitam arquivo estático ----
    check("filme .mkv é aceito em Filmes", AddonBrowser.accepts(MediaKind.MOVIE, "https://x/a.mkv"))
    check("filme .mp4 é aceito em Filmes", AddonBrowser.accepts(MediaKind.MOVIE, "https://x/a.mp4"))
    check("canal .m3u8 é recusado em Filmes", !AddonBrowser.accepts(MediaKind.MOVIE, "https://x/live.m3u8"))
    check("canal .m3u8 é aceito em Canais", AddonBrowser.accepts(MediaKind.LIVE, "https://x/live.m3u8"))
    check("filme .mkv é recusado em Canais", !AddonBrowser.accepts(MediaKind.LIVE, "https://x/a.mkv"))
    check("link que não é mídia é recusado", !AddonBrowser.accepts(MediaKind.MOVIE, "https://x/pagina.html"))
    check("sem link não é reproduzível", !AddonBrowser.accepts(MediaKind.MOVIE, null))

    // ---- montagem das pastas ----
    val folderEntries = listOf(
        BrowserEntry(id = "1", title = "Filmes A", summary = "", kind = MediaKind.MOVIE,
            streamUrl = "https://x/a.mp4"),
        BrowserEntry(id = "2", title = "Filmes B", summary = "", kind = MediaKind.MOVIE),
        BrowserEntry(id = "3", title = "Canal C", summary = "", kind = MediaKind.LIVE,
            streamUrl = "https://x/c.m3u8"),
    )
    val folders = AddonBrowser.folders(folderEntries)
    eq("três pastas sempre", 3, folders.size)
    eq("ordem das pastas", listOf(MediaKind.LIVE, MediaKind.MOVIE, MediaKind.SERIES), folders.map { it.kind })
    eq("título da primeira pasta", "TV ao Vivo", folders[0].title)
    eq("Filmes tem 2 itens", 2, folders[1].count)
    eq("Filmes tem 1 reproduzível", 1, folders[1].playableCount)
    eq("Séries vazia", 0, folders[2].count)
    eq("resumo das pastas", "TV ao Vivo 1 · Filmes 2 · Séries 0", AddonBrowser.summary(folders))

    // ---- link de mídia direto ----
    eq(
        "escolhe o primeiro link de mídia",
        "https://x/a.mp4",
        AddonBrowser.mediaUrl(listOf("https://x/pagina.html", "https://x/a.mp4")),
    )
    eq("sem link de mídia devolve null", null, AddonBrowser.mediaUrl(listOf("https://x/pagina.html", null)))

    // ---- metadados do card ----
    val entry = BrowserEntry(id = "z", title = "Filme Z", summary = "", kind = MediaKind.MOVIE,
        author = "Vikings", version = "1.2.3")
    eq("linha de metadados", "Vikings · v1.2.3", entry.metaLine)
    check("entrada sem stream não é reproduzível", !entry.playable)
}

/**
 * Elementum: o addon de torrentes do repositório Vikings fala com o daemon
 * local (`127.0.0.1:65220`). Aqui ficam a detecção, a leitura do JSON e o link
 * de reprodução — sem Android e sem rede.
 */
private fun verifyElementum() {
    println("== Elementum (addon de torrentes do Vikings) ==")

    // ---- detecção do addon no repositório ----
    check("reconhece pelo id", Elementum.isElementum("plugin.video.elementum"))
    check("reconhece pelo nome", Elementum.isElementum("outro.id", "Elementum"))
    check("reconhece o script do daemon", Elementum.isElementum("script.elementum.burst"))
    check("não confunde com outro addon", !Elementum.isElementum("plugin.video.filmeshd", "Filmes HD"))

    // ---- endpoints do daemon (contrato real do elementumd) ----
    eq("base do daemon", "http://127.0.0.1:65220", Elementum.baseUrl())
    eq("endpoint de status", "http://127.0.0.1:65220/status", Elementum.statusUrl())
    eq("porta inválida cai no padrão", "http://127.0.0.1:65220", Elementum.baseUrl(0))
    eq("porta alternativa", "http://127.0.0.1:6809", Elementum.baseUrl(6809))
    eq(
        "busca pede lista e escapa o termo",
        "http://127.0.0.1:65220/search?list=1&q=a%20volta%20do%20grande%20trem",
        Elementum.searchUrl("a volta do grande trem"),
    )
    eq("menu inicial pede lista", "http://127.0.0.1:65220/?list=1", Elementum.browseUrl("/"))
    eq("filmes populares", "http://127.0.0.1:65220/movies/popular?list=1", Elementum.browseUrl(Elementum.PATH_MOVIES))
    eq("caminho sem barra inicial", "http://127.0.0.1:65220/shows/popular?list=1", Elementum.browseUrl("shows/popular"))

    // ---- status ----
    val ligado = Elementum.parseStatus("{\"status\":\"ok\",\"version\":\"2.0.11\"}")
    check("status online", ligado.online)
    eq("versão lida", "2.0.11", ligado.version)
    eq("rótulo ligado", "Elementum ligado", ligado.label)
    check("status vazio é offline", !Elementum.parseStatus(null).online)
    eq("rótulo desligado", "Elementum desligado", Elementum.parseStatus("").label)
    check("resposta sem corpo ainda é tolerada", Elementum.parseStatus("{}").online)

    // ---- busca: view do Kodi, como o daemon devolve com list=1 ----
    val view = """
        {"content_type":"movies","items":[
          {"label":"O Auto da Compadecida 1080p","label2":"RuTor","is_playable":true,
           "path":"/play?uri=magnet%3A%3Fxt%3Durn%3Abtih%3AAAA%26dn%3DAuto",
           "info":{"title":"O Auto da Compadecida 1080p","tagline":"320 / 12 - 1080p - 1.4 GB","code":"AAA"}},
          {"label":"Cidade de Deus 720p","label2":"Comando","is_playable":true,
           "path":"/play?uri=magnet%3A%3Fxt%3Durn%3Abtih%3ABBB",
           "info":{"tagline":"88 / 4 - 720p - 700.5 MB"}},
          {"label":"Breaking Bad S01E01","is_playable":true,
           "path":"/play?uri=magnet%3A%3Fxt%3Durn%3Abtih%3ACCC","info":{"tagline":"10 / 1 - 480p - 500 MB"}},
          {"label":"Buscar","path":"/search","is_playable":false}
        ]}
    """.trimIndent()
    val nos = Elementum.parseView(view)
    eq("view com quatro nós", 4, nos.size)
    eq("rótulo do primeiro", "O Auto da Compadecida 1080p", nos[0].label)
    eq("provedor no label2", "RuTor", nos[0].label2)
    check("primeiro é reproduzível", nos[0].playable)
    check("menu não é reproduzível", !nos[3].playable)

    val itens = Elementum.parseItems(view)
    eq("só torrentes viram resultado", 3, itens.size)
    eq("título do primeiro", "O Auto da Compadecida 1080p", itens[0].title)
    eq("magnet decodificado", "magnet:?xt=urn:btih:AAA&dn=Auto", itens[0].torrent)
    eq("tamanho vindo do rótulo", 1503238553L, itens[0].sizeBytes)
    eq("seeds do rótulo", 320, itens[0].seeds)
    eq("peers do rótulo", 12, itens[0].peers)
    eq("qualidade do rótulo", "1080p", itens[0].quality)
    eq("tamanho legível", "1,4 GB", Elementum.humanSize(itens[0].sizeBytes))
    eq("segundo com 700,5 MB", 734527488L, itens[1].sizeBytes)
    eq("qualidade do segundo", "720p", itens[1].quality)
    eq("série reconhecida", MediaKind.SERIES, itens[2].kind)
    eq("filme reconhecido", MediaKind.MOVIE, itens[0].kind)
    eq("resposta vazia devolve nada", 0, Elementum.parseItems("").size)
    eq("resposta inválida devolve nada", 0, Elementum.parseItems("nao e json").size)

    // ---- array cru (versões antigas) e path com magnet solto ----
    val cru = """[{"label":"Matrix","is_playable":true,"path":"magnet:?xt=urn:btih:DDD"}]"""
    val cruItens = Elementum.parseItems(cru)
    eq("array cru lido", 1, cruItens.size)
    eq("magnet do path solto", "magnet:?xt=urn:btih:DDD", cruItens[0].torrent)
    eq("rótulos com cor do Kodi ficam limpos", "Filme", Elementum.stripKodiTags("[B][COLOR FFFFDE00]Filme[/COLOR][/B]"))
    eq(
        "tagline só com tamanho",
        0,
        Elementum.parseTagline("").seeds,
    )
    eq("tamanho em GB", 1073741824L, Elementum.parseSize("1 GB"))
    eq("tamanho desconhecido", 0L, Elementum.parseSize("sem tamanho"))

    // ---- link de reprodução: /play?uri= (302 para /files/…) ----
    val play = Elementum.playUrl(itens[0])
    check("play no daemon", play.startsWith("http://127.0.0.1:65220/play?uri="), play)
    check("play carrega o magnet", play.contains("uri=magnet%3A%3Fxt"), play)
    eq("sem magnet não há link", "", Elementum.playUrl(Elementum.Item(title = "solto")))
    eq("play acha o magnet", "magnet:?xt=urn:btih:AAA&dn=Auto", Elementum.playTarget("/play?uri=magnet%3A%3Fxt%3Durn%3Abtih%3AAAA%26dn%3DAuto"))
    eq("parâmetro inexistente", "", Elementum.parameter("/play?uri=x", "index"))
    eq("decodifica vírgula e acento", "ação, ok", Elementum.decodeQuery("a%C3%A7%C3%A3o%2C%20ok"))

    // ---- itens de pasta: VOD, nunca canal ----
    val entries = Elementum.toEntries(itens)
    eq("todos viram entrada", 3, entries.size)
    check("nenhum item é canal ao vivo", entries.none { it.kind == MediaKind.LIVE })
    check("todos reproduzíveis", entries.all { it.playable })
    check("link do daemon local é aceito em Filmes", AddonBrowser.accepts(MediaKind.MOVIE, play))
    check("link do daemon local é aceito em Séries", AddonBrowser.accepts(MediaKind.SERIES, play))
    check("magnet cru é recusado em Filmes", !AddonBrowser.accepts(MediaKind.MOVIE, "magnet:?xt=urn:btih:AAA"))
    check("link do daemon não vira canal", !AddonBrowser.accepts(MediaKind.LIVE, play))
    eq("pasta da série", MediaKind.SERIES, entries[2].kind)
    check("resumo traz tamanho e seeds", entries[0].summary.contains("1,4 GB") && entries[0].summary.contains("320 seeds"))
    check("resumo traz o provedor", entries[0].summary.contains("RuTor"), entries[0].summary)
    check("autor é o addon", entries[0].author == "Elementum")
    check("item sem magnet não é reproduzível", Elementum.toEntries(listOf(Elementum.Item(title = "x"))).first().playable.not())

    // ---- magnet a partir do info hash ----
    val magnet = Elementum.magnet("ABC123", "Filme")
    check("magnet montado", magnet.startsWith("magnet:?xt=urn:btih:ABC123&dn=Filme"))
    check("magnet traz trackers", magnet.contains("tr=udp%3A%2F%2F"))
    eq("hash vazio não gera magnet", "", Elementum.magnet(""))
}

private fun verifyAddonTree() {
    println("== Árvore de navegação (estilo Kodi) e minha lista ==")

    // ---- caminhos: plugin://, absolutos e magnet ----
    eq(
        "plugin:// vira rota",
        "/movies/genre/acao",
        AddonTree.routeOf("plugin://plugin.video.elementum/movies/genre/acao"),
    )
    eq(
        "plugin:// com query mantém a query",
        "/movies/popular?page=2",
        AddonTree.routeOf("plugin://plugin.video.elementum/movies/popular?page=2"),
    )
    eq("plugin:// sem caminho é a raiz", "/", AddonTree.routeOf("plugin://plugin.video.elementum"))
    eq("URL absoluta é mantida", "http://127.0.0.1:65220/play?uri=x", AddonTree.routeOf("http://127.0.0.1:65220/play?uri=x"))
    eq("magnet não é navegável", "", AddonTree.routeOf("magnet:?xt=urn:btih:AAA"))
    eq("rota relativa ganha barra", "/shows/popular", AddonTree.routeOf("shows/popular"))
    check("rota de play reconhecida", AddonTree.isPlayRoute("http://127.0.0.1:65220/play?uri=x"))
    check("rota de pasta não é play", !AddonTree.isPlayRoute("/movies/popular"))

    // ---- subir/descer na árvore ----
    eq("pai de gênero", "/movies/genre", AddonTree.parentOf("/movies/genre/acao"))
    eq("pai de pasta", "/movies", AddonTree.parentOf("/movies/genre"))
    eq("pai da raiz é nulo", null, AddonTree.parentOf("/movies"))
    eq("raiz não tem pai", null, AddonTree.parentOf("/"))

    // ---- view do daemon virada em nós ----
    val view = """
        {"content_type":"seasons","items":[
          {"label":"[B]Lançamentos[/B]","path":"plugin://plugin.video.elementum/movies/recent","is_playable":false},
          {"label":"Ação","path":"plugin://plugin.video.elementum/movies/genre/acao","is_playable":false},
          {"label":"Aventura","path":"plugin://plugin.video.elementum/movies/genre/aventura","is_playable":false},
          {"label":"O Auto da Compadecida","label2":"RuTor","is_playable":true,
           "path":"plugin://plugin.video.elementum/play?uri=magnet%3A%3Fxt%3Durn%3Abtih%3AAAA",
           "info":{"tagline":"320 / 12 - 1080p - 1.4 GB"}},
          {"label":"Matrix 1999","is_playable":true,
           "path":"plugin://plugin.video.elementum/play?uri=magnet%3A%3Fxt%3Durn%3Abtih%3ABBB",
           "info":{"tagline":"88 / 4 - 720p - 700 MB"}}
        ]}
    """.trimIndent()
    val nodes = Elementum.parseTree(view)
    eq("cinco nós", 5, nodes.size)
    eq("rótulo sem marcação do Kodi", "Lançamentos", nodes[0].title)
    eq("rota do nó plugin://", "/movies/recent", nodes[0].route)
    check("primeiro é pasta", nodes[0].isFolder)
    check("terceiro é item", nodes[3].isItem)
    eq("ícone de pasta de gênero", AddonTree.FolderStyle.GENRE, AddonTree.styleOf(nodes[1]))

    // ---- nó com play: destino do player e do salvamento ----
    val itemNode = nodes[3]
    eq("magnet decodificado do play", "magnet:?xt=urn:btih:AAA", Elementum.playTarget(itemNode.path))
    check("item pode ser salvo", AddonTree.canSave(itemNode))
    check("pasta não pode ser salva", !AddonTree.canSave(nodes[0]))
    eq("tipo do filme", MediaKind.MOVIE, AddonTree.kindOf(itemNode))

    // ---- sugestão de aba (o usuário pode trocar) ----
    eq("filme sugere Filmes", AddonTree.Shelf.MOVIES, AddonTree.Shelf.suggest(itemNode))
    val serieNode = AddonTree.Node(
        label = "Breaking Bad S01E01",
        path = "plugin://plugin.video.elementum/play?uri=magnet%3A%3Fxt%3Durn%3Abtih%3ACCC",
        playable = true,
    )
    eq("série sugere Séries", AddonTree.Shelf.SERIES, AddonTree.Shelf.suggest(serieNode))

    // ---- salvar: o destino escolhido manda ----
    eq(
        "player recebe a URL absoluta do daemon",
        "http://127.0.0.1:65220/play?uri=magnet%3A%3Fxt%3Durn%3Abtih%3AAAA",
        AddonTree.playUrl(itemNode),
    )
    eq("pasta não tem URL de player", "", AddonTree.playUrl(nodes[0]))
    val emFilmes = AddonTree.toSaved(itemNode, playUrl = AddonTree.playUrl(itemNode), folder = "Lançamentos", folderRoute = "/movies/recent", shelf = AddonTree.Shelf.MOVIES)
    eq("aba escolhida: Filmes", AddonTree.Shelf.MOVIES, emFilmes.shelf)
    eq("pasta de origem", "Lançamentos", emFilmes.folder)
    check("link guardado é o play do daemon", emFilmes.playUrl.startsWith("http://127.0.0.1:65220/play?uri="), emFilmes.playUrl)
    check("resumo traz qualidade e tamanho", emFilmes.meta.contains("1080p") && emFilmes.meta.contains("1,4 GB"), emFilmes.meta)

    // Mesmo item salvo em Canais: a escolha do usuário é a autoridade.
    val emCanais = AddonTree.toSaved(itemNode, playUrl = AddonTree.playUrl(itemNode), folder = "Lançamentos", shelf = AddonTree.Shelf.CHANNELS)
    eq("mesmo item pode ir para Canais", AddonTree.Shelf.CHANNELS, emCanais.shelf)
    eq("tipo continua sendo filme", MediaKind.MOVIE, emCanais.kind)
    check("itens distintos por aba", emFilmes.id == emCanais.id)

    // ---- agrupamento por aba ----
    val lista = listOf(
        emFilmes,
        AddonTree.toSaved(serieNode, playUrl = AddonTree.playUrl(serieNode), folder = "Séries", shelf = AddonTree.Shelf.SERIES),
        AddonTree.toSaved(itemNode, playUrl = "http://127.0.0.1:65220/play?uri=BBB", folder = "Ação", shelf = AddonTree.Shelf.MOVIES),
        emCanais,
    )
    val grupos = AddonTree.groupByShelf(lista)
    eq("três abas", 3, grupos.size)
    eq("ordem: Canais, Filmes, Séries", listOf("Canais", "Filmes", "Séries"), grupos.map { it.shelf.label })
    eq("Filmes com dois itens", 2, grupos.first { it.shelf == AddonTree.Shelf.MOVIES }.count)
    eq("Canais com um item", 1, grupos.first { it.shelf == AddonTree.Shelf.CHANNELS }.count)
    eq("Séries com um item", 1, grupos.first { it.shelf == AddonTree.Shelf.SERIES }.count)
    eq(
        "pastas de origem em Filmes",
        listOf("Lançamentos", "Ação"),
        grupos.first { it.shelf == AddonTree.Shelf.MOVIES }.folders.map { it.first },
    )
    eq("resumo com as três abas", "4 itens · Canais 1 · Filmes 2 · Séries 1", AddonTree.savedSummary(lista))
    eq("lista vazia", "Sua lista está vazia", AddonTree.savedSummary(emptyList()))

    // ---- leitura da aba gravada ----
    eq("aba LIVE", AddonTree.Shelf.CHANNELS, AddonTree.Shelf.parse("LIVE"))
    eq("aba SERIES", AddonTree.Shelf.SERIES, AddonTree.Shelf.parse("SERIES"))
    eq("aba vazia cai em Filmes", AddonTree.Shelf.MOVIES, AddonTree.Shelf.parse(""))
    eq("aba desconhecida cai em Filmes", AddonTree.Shelf.MOVIES, AddonTree.Shelf.parse("xyz"))
    eq("rótulo da aba", "Séries", AddonTree.Shelf.SERIES.label)

    // ---- pasta única (Kodi "abre direto") ----
    val soPasta = listOf(AddonTree.Node(label = "Filmes", path = "plugin://plugin.video.elementum/movies"))
    eq("pasta única detectada", "/movies", AddonTree.onlyFolder(soPasta)?.route)
    eq("com item não é pasta única", null, AddonTree.onlyFolder(nodes))
}
