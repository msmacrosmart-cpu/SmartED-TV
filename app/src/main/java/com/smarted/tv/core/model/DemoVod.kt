package com.smarted.tv.core.model

/**
 * Catálogo de demonstração de **filmes de domínio público**.
 *
 * Por que existe: não há lista M3U pública brasileira de filmes que seja
 * legítima e estável — as listas públicas de IPTV são de canais ao vivo. Sem
 * nada aqui, a aba Filmes abriria vazia na primeira execução, e o usuário não
 * teria como avaliar o player, a ficha do filme e a linha do tempo.
 *
 * Todo o conteúdo abaixo é de domínio público ou licença Creative Commons
 * (Blender Foundation e acervo público do Internet Archive), com URLs diretas
 * de arquivo estático (`.mp4`) — o mesmo formato que qualquer lista de VOD do
 * usuário entrega.
 *
 * Regras:
 *  - só entra aqui o que é **arquivo estático** (nunca transmissão ao vivo);
 *  - o grupo "Demonstração" deixa claro que não é o acervo do usuário;
 *  - assim que o usuário aponta a lista dele, estes itens saem do caminho.
 */
object DemoVod {

    /** Identificador do addon virtual do catálogo de demonstração. */
    const val ADDON_ID = "demo-vod"

    /** Grupo exibido nas abas/linhas da Home. */
    const val GROUP = "Demonstração · domínio público"

    /**
     * Bucket público de amostras em H.264 mantido pelo Google — estável há
     * anos e usado para demonstrar reprodução de arquivo estático.
     */
    private const val SAMPLE_BUCKET = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample"

    private fun sample(file: String) = "$SAMPLE_BUCKET/$file"

    val movies: List<MediaItem> = listOf(
        MediaItem(
            id = "demo-big-buck-bunny",
            addonId = ADDON_ID,
            kind = MediaKind.MOVIE,
            title = "O Coelho Big Buck",
            subtitle = "Big Buck Bunny",
            plot = "Um coelho gigante e gentil decide se vingar de três roedores que " +
                "maltrataram os animais da floresta. Primeiro filme aberto da Blender Foundation.",
            year = 2008,
            rating = 7.8,
            durationSec = 596,
            group = GROUP,
            streamUrl = sample("BigBuckBunny.mp4"),
            ext = "mp4",
            quality = "1080p",
            language = "pt",
            country = "BR",
        ),
        MediaItem(
            id = "demo-elephants-dream",
            addonId = ADDON_ID,
            kind = MediaKind.MOVIE,
            title = "O Sonho dos Elefantes",
            subtitle = "Elephants Dream",
            plot = "Dois homens exploram uma máquina infinita e descobrem que não " +
                "concordam sobre o que é real. Primeiro filme aberto da história.",
            year = 2006,
            rating = 7.1,
            durationSec = 654,
            group = GROUP,
            streamUrl = sample("ElephantsDream.mp4"),
            ext = "mp4",
            quality = "1080p",
            language = "pt",
            country = "BR",
        ),
        MediaItem(
            id = "demo-sintel",
            addonId = ADDON_ID,
            kind = MediaKind.MOVIE,
            title = "Sintel",
            subtitle = "Sintel",
            plot = "Uma jovem cruza um mundo hostil à procura do dragão que criou. " +
                "Curta-metragem aberto da Blender Foundation.",
            year = 2010,
            rating = 7.7,
            durationSec = 888,
            group = GROUP,
            streamUrl = sample("Sintel.mp4"),
            ext = "mp4",
            quality = "1080p",
            language = "pt",
            country = "BR",
        ),
        MediaItem(
            id = "demo-tears-of-steel",
            addonId = ADDON_ID,
            kind = MediaKind.MOVIE,
            title = "Lágrimas de Aço",
            subtitle = "Tears of Steel",
            plot = "Num futuro dominado por robôs, um grupo tenta desfazer o erro que " +
                "causou a tragédia. Filme aberto com atores reais e efeitos visuais.",
            year = 2012,
            rating = 6.9,
            durationSec = 734,
            group = GROUP,
            streamUrl = sample("TearsOfSteel.mp4"),
            ext = "mp4",
            quality = "1080p",
            language = "pt",
            country = "BR",
        ),
        MediaItem(
            id = "demo-nosferatu",
            addonId = ADDON_ID,
            kind = MediaKind.MOVIE,
            title = "Nosferatu",
            subtitle = "Nosferatu, eine Symphonie des Grauens",
            plot = "Clássico expressionista de 1922, a primeira adaptação de Drácula " +
                "a chegar ao cinema. Acervo público do Internet Archive.",
            year = 1922,
            rating = 7.9,
            durationSec = 5658,
            group = GROUP,
            streamUrl = "https://archive.org/download/nosferatu_1922/nosferatu_1922.mp4",
            ext = "mp4",
            quality = "720p",
            language = "pt",
            country = "BR",
        ),
    )

    /** Catálogo de séries de demonstração (vazio: exige estrutura real). */
    val series: List<MediaItem> = emptyList()

    fun isDemo(item: MediaItem): Boolean = item.addonId == ADDON_ID
}
