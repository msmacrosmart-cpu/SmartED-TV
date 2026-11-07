package com.smarted.tv.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smarted.tv.core.addons.AddonManager
import com.smarted.tv.core.data.Settings
import com.smarted.tv.core.model.CatalogPage
import com.smarted.tv.core.model.EpgProgram
import com.smarted.tv.core.model.ContentMode
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.WatchProgress
import com.smarted.tv.core.util.TextUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Fábrica única de ViewModels (sem dependência de DI por reflexão). */
class SmartEdViewModelFactory(private val graph: AppServices) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(graph) as T
        modelClass.isAssignableFrom(CatalogViewModel::class.java) -> CatalogViewModel(graph) as T
        modelClass.isAssignableFrom(DetailsViewModel::class.java) -> DetailsViewModel(graph) as T
        modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(graph) as T
        modelClass.isAssignableFrom(SearchViewModel::class.java) -> SearchViewModel(graph) as T
        modelClass.isAssignableFrom(GuideViewModel::class.java) -> GuideViewModel(graph) as T
        modelClass.isAssignableFrom(AddonsViewModel::class.java) -> AddonsViewModel(graph) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(graph) as T
        else -> throw IllegalArgumentException("ViewModel desconhecido: ${modelClass.name}")
    }
}

// ------------------------------------------------------------------ HOME

data class HomeUiState(
    val loading: Boolean = true,
    val hero: MediaItem? = null,
    val heroEyebrow: String = "Destaque",
    val heroProgress: Float? = null,
    val heroBackdrops: List<String> = emptyList(),
    val continueWatching: List<WatchProgress> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    /** Top 10 filmes por nota (votos ponderados). */
    val topMovies: List<MediaItem> = emptyList(),
    /** Top 10 séries por nota. */
    val topSeries: List<MediaItem> = emptyList(),
    /** Canais marcados como favorito pelo usuário — única seção de canais da Home. */
    val favoriteChannels: List<MediaItem> = emptyList(),
    /**
     * Todos os canais ao vivo do acervo. Não é exibido na Home (a Home mostra
     * só favoritos); serve para o zap do player (canal anterior/próximo).
     */
    val liveChannels: List<MediaItem> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
    val status: String = "",
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val error: String? = null,
)

class HomeViewModel(private val graph: AppServices) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var epgJob: Job? = null

    init {
        observeLibrary()
        load()
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            graph.library.observeRecent(20).collect { recent ->
                _state.value = _state.value.copy(continueWatching = recent.filter { !it.isFinished })
            }
        }
        viewModelScope.launch {
            graph.library.observeFavorites().collect { favs ->
                _state.value = _state.value.copy(
                    favorites = favs,
                    favoriteChannels = favs.filter { it.kind == MediaKind.LIVE },
                )
            }
        }
        viewModelScope.launch {
            graph.catalog.snapshotFlow.collect { snap ->
                if (!snap.isEmpty) applySnapshot(snap)
            }
        }
        viewModelScope.launch {
            graph.catalog.status.collect { status ->
                _state.value = _state.value.copy(status = status)
            }
        }
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.hero == null)
            runCatching { graph.catalog.snapshot(force) }
                .onSuccess { applySnapshot(it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    private fun applySnapshot(snap: CatalogSnapshot) {
        // O banner é SEMPRE de filme (requisito de produto): usa backdrop largo
        // quando existe, senão o pôster. Canal ao vivo nunca vira destaque.
        val hero = pickHero(snap)
        val previousHero = _state.value.hero
        _state.value = _state.value.copy(
            loading = false,
            hero = hero,
            heroEyebrow = heroEyebrowFor(hero),
            continueWatching = _state.value.continueWatching,
            liveChannels = snap.live,
            heroBackdrops = hero?.let { backdropsFor(it, snap) } ?: _state.value.heroBackdrops,
            topMovies = topRated(snap.movies, 10),
            topSeries = topRated(snap.series, 10),
            favoriteChannels = _state.value.favoriteChannels,
            movies = snap.movies.take(30),
            series = snap.series.take(30),
            liveCount = snap.live.size,
            movieCount = snap.movies.size,
            seriesCount = snap.series.size,
            error = if (snap.degraded) snap.sourceErrors.firstOrNull() else null,
        )
        if (hero != null && (previousHero == null || previousHero.id != hero.id)) {
            loadHeroExtras(hero)
        }
        startEpgTicker()
    }

    /**
     * Ranking dos "Top 10": usa a nota quando existe; sem nota, cai para uma
     * pontuação de popularidade estável (ano + presença de arte) para que a
     * lista nunca fique vazia nem mude de ordem a cada atualização.
     */
    private fun topRated(items: List<MediaItem>, limit: Int): List<MediaItem> = items
        .sortedWith(
            compareByDescending<MediaItem> { it.rating ?: 0.0 }
                .thenByDescending { it.details?.votes ?: 0 }
                .thenByDescending { it.year ?: 0 }
                .thenByDescending { (if (it.backdrop != null) 1 else 0) + (if (it.poster != null) 1 else 0) }
                .thenBy { TextUtils.titleKey(it.title) },
        )
        .take(limit)

    /**
     * Destaque da Home: **somente filmes**.
     *
     * O modo SmartED e o Repo podem não trazer capa; nesse caso o filme sem
     * arte ainda é escolhido, mas sem inventar imagens de canal. Se não houver
     * nenhum filme no acervo, a Home simplesmente não mostra banner.
     */
    private fun pickHero(snap: CatalogSnapshot): MediaItem? {
        val movies = snap.movies
        if (movies.isEmpty()) return null
        movies.firstOrNull { it.backdrop != null }?.let { return it }
        // Prioriza quem tem nota/votos: o destaque deve ser um filme relevante.
        return movies.sortedWith(
            compareByDescending<MediaItem> { it.rating ?: 0.0 }
                .thenByDescending { if (it.poster != null) 1 else 0 }
                .thenBy { TextUtils.titleKey(it.title) },
        ).first()
    }

    private fun heroEyebrowFor(hero: MediaItem?): String = when {
        hero == null -> "Destaque"
        hero.year != null -> "Filme em destaque · ${hero.year}"
        else -> "Filme em destaque"
    }

    /** Arte do banner: só filmes, começando pelo próprio destaque. */
    private fun backdropsFor(hero: MediaItem, snap: CatalogSnapshot): List<String> {
        val pool = (listOf(hero) + snap.movies)
            .mapNotNull { it.backdrop ?: it.poster }
            .distinct()
        return pool.take(6)
    }

    private fun loadHeroExtras(hero: MediaItem) {
        viewModelScope.launch {
            val progress = graph.library.progressFor(hero.ref)
            _state.value = _state.value.copy(heroProgress = progress?.fraction)
        }
        viewModelScope.launch {
            val enriched = runCatching { graph.addon.details(hero) }.getOrNull()
            if (enriched != null) {
                _state.value = _state.value.copy(hero = enriched)
            }
        }
    }

    /** Atualiza o "agora" dos canais a cada minuto. */
    private fun startEpgTicker() {
        if (epgJob?.isActive == true) return
        epgJob = viewModelScope.launch {
            while (true) {
                delay(60_000L)
                val now = System.currentTimeMillis()
                _state.value = _state.value.copy(
                    heroProgress = _state.value.hero?.let { graph.library.progressFor(it.ref)?.fraction },
                )
                if (now > 0) Unit
            }
        }
    }

    fun playHero() {
        _state.value.hero?.let { graph.playerRequest = it }
    }
}

// --------------------------------------------------------------- CATALOG

data class CatalogUiState(
    val kind: MediaKind = MediaKind.MOVIE,
    val loading: Boolean = true,
    /** Modo de conteúdo ativo nesta aba: Normal, SmartED ou Repo. */
    val mode: ContentMode = ContentMode.SMARTED,
    val items: List<MediaItem> = emptyList(),
    val categories: List<Pair<String, Int>> = emptyList(),
    val selectedCategory: String? = null,
    val query: String = "",
    val sort: SortMode = SortMode.DEFAULT,
    val hideWatched: Boolean = false,
    val total: Int = 0,
    val status: String = "",
    val epgNow: Map<String, String> = emptyMap(),
    val epgProgress: Map<String, Float> = emptyMap(),
)

enum class SortMode(val label: String) {
    DEFAULT("Padrão"),
    NAME("A–Z"),
    RATING("Melhor nota"),
    YEAR("Mais recente"),
    QUALITY("Qualidade"),
}

class CatalogViewModel(private val graph: AppServices) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var allItems: List<MediaItem> = emptyList()
    private var watchedKeys: Set<String> = emptySet()

    fun setKind(kind: MediaKind) {
        if (_state.value.kind == kind && allItems.isNotEmpty()) return
        _state.value = _state.value.copy(kind = kind, loading = true, selectedCategory = null, query = "")
        load(kind)
    }

    /**
     * Troca o modo de conteúdo da aba atual.
     *
     * O modo é uma preferência (persiste), mas o catálogo é um retrato único
     * das três abas: ao mudar qualquer uma, o snapshot é invalidado e recarregado
     * para que a mistura pública/repositório seja refeita.
     */
    fun setContentMode(mode: ContentMode) {
        val kind = _state.value.kind
        if (_state.value.mode == mode) return
        graph.settings.setContentMode(kind, mode)
        graph.catalog.invalidate()
        _state.value = _state.value.copy(mode = mode, loading = true, items = emptyList())
        load(kind, force = true)
    }

    fun load(kind: MediaKind = _state.value.kind, force: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = _state.value.items.isEmpty(),
                kind = kind,
                mode = modeFor(kind),
            )
            val snap = runCatching { graph.catalog.snapshot(force) }.getOrNull()
            if (snap == null) {
                _state.value = _state.value.copy(loading = false, status = "Falha ao carregar catálogo")
                return@launch
            }
            allItems = if (kind == MediaKind.LIVE) snap.live else if (kind == MediaKind.MOVIE) snap.movies else snap.series
            watchedKeys = runCatching {
                graph.library.recentProgress(300).filter { it.isFinished }.map { it.ref }.toSet()
            }.getOrDefault(emptySet())
            val categories = allItems.groupBy { it.group ?: "Sem categoria" }
                .map { it.key to it.value.size }
                .sortedByDescending { it.second }
            _state.value = _state.value.copy(
                loading = false,
                categories = categories,
                total = allItems.size,
                status = graph.catalog.status.value,
            )
            applyFilters()
            if (kind == MediaKind.LIVE) startEpgPolling()
        }
    }

    /** Modo configurado para a aba (lido das preferências). */
    private fun modeFor(kind: MediaKind): ContentMode = when (kind) {
        MediaKind.LIVE -> graph.settings.settings.modeChannels
        MediaKind.MOVIE -> graph.settings.settings.modeMovies
        MediaKind.SERIES -> graph.settings.settings.modeSeries
        else -> ContentMode.SMARTED
    }

    fun selectCategory(category: String?) {
        _state.value = _state.value.copy(selectedCategory = category)
        applyFilters()
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
        applyFilters()
    }

    fun setSort(sort: SortMode) {
        _state.value = _state.value.copy(sort = sort)
        applyFilters()
    }

    fun toggleHideWatched() {
        _state.value = _state.value.copy(hideWatched = !_state.value.hideWatched)
        applyFilters()
    }

    private fun applyFilters() {
        val s = _state.value
        var list = allItems
        s.selectedCategory?.let { cat ->
            list = list.filter { (it.group ?: "Sem categoria").equals(cat, ignoreCase = true) }
        }
        if (s.query.isNotBlank()) {
            val q = TextUtils.titleKey(s.query)
            list = list.filter { TextUtils.titleKey(it.title).contains(q) }
        }
        if (s.hideWatched) {
            list = list.filter { it.ref.key !in watchedKeys }
        }
        list = when (s.sort) {
            SortMode.DEFAULT -> list
            SortMode.NAME -> list.sortedBy { TextUtils.titleKey(it.title) }
            SortMode.RATING -> list.sortedByDescending { it.rating ?: 0.0 }
            SortMode.YEAR -> list.sortedByDescending { it.year ?: 0 }
            SortMode.QUALITY -> list.sortedByDescending { qualityWeight(it.quality) }
        }
        _state.value = _state.value.copy(items = list, total = list.size)
    }

    private fun qualityWeight(q: String?): Int = when (q) {
        "4K" -> 4
        "1080p" -> 3
        "HD" -> 2
        "720p" -> 1
        else -> 0
    }

    private var epgJob: Job? = null

    private fun startEpgPolling() {
        if (epgJob?.isActive == true) return
        epgJob = viewModelScope.launch {
            refreshEpg()
            while (true) {
                delay(60_000L)
                refreshEpg()
            }
        }
    }

    private suspend fun refreshEpg() {
        val channels = _state.value.items.take(60)
        if (channels.isEmpty()) return
        val now = System.currentTimeMillis()
        val nowMap = HashMap<String, String>()
        val progressMap = HashMap<String, Float>()
        channels.forEach { channel ->
            val (current, _) = runCatching { graph.epg.nowNext(channel, now) }.getOrDefault(null to null)
            if (current != null) {
                nowMap[channel.id] = current.title
                progressMap[channel.id] = current.progressAt(now)
            }
        }
        _state.value = _state.value.copy(epgNow = nowMap, epgProgress = progressMap)
    }

    fun page(): CatalogPage = CatalogPage(
        title = _state.value.kind.name,
        items = _state.value.items,
        total = _state.value.total,
        kind = _state.value.kind,
    )
}

// -------------------------------------------------------------- DETAILS

data class DetailsUiState(
    val loading: Boolean = true,
    val item: MediaItem? = null,
    val progress: WatchProgress? = null,
    val isFavorite: Boolean = false,
    val selectedSeason: Int = 1,
    val seasons: List<Int> = emptyList(),
    val episodes: List<MediaItem> = emptyList(),
    val error: String? = null,
    val trailerRequested: Boolean = false,
)

class DetailsViewModel(private val graph: AppServices) : ViewModel() {

    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    fun load(item: MediaItem) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = item.details == null, item = item)
            val enriched = runCatching { graph.addon.details(item) }.getOrDefault(item)
            val progress = graph.library.progressFor(item.ref)
            val favorite = graph.library.isFavorite(item.ref)
            val seasons = enriched.details?.seasons?.map { it.number }?.sorted().orEmpty()
            val selected = progress?.season ?: seasons.firstOrNull() ?: 1
            _state.value = DetailsUiState(
                loading = false,
                item = enriched,
                progress = progress,
                isFavorite = favorite,
                seasons = seasons,
                selectedSeason = selected,
                episodes = episodesFor(enriched, selected),
            )
        }
    }

    private fun episodesFor(item: MediaItem, season: Int): List<MediaItem> {
        val details = item.details ?: return emptyList()
        val s = details.seasons.firstOrNull { it.number == season } ?: return emptyList()
        return s.episodes.map { ep ->
            ep.copy(addonId = item.addonId, showTitle = item.title, showId = item.id).toMediaItem().copy(
                poster = ep.still ?: item.poster,
                backdrop = item.backdrop,
                plot = ep.plot,
                addonId = item.addonId,
                details = null,
            )
        }
    }

    fun selectSeason(season: Int) {
        val item = _state.value.item ?: return
        _state.value = _state.value.copy(
            selectedSeason = season,
            episodes = episodesFor(item, season),
        )
    }

    fun toggleFavorite() {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            val nowFav = graph.library.toggleFavorite(item)
            _state.value = _state.value.copy(isFavorite = nowFav)
        }
    }

    fun playEpisode(episode: MediaItem) {
        val show = _state.value.item ?: return
        graph.playerRequest = episode.copy(
            title = "${show.title} · ${episode.subtitle ?: episode.title}",
            poster = episode.poster ?: show.poster,
            backdrop = show.backdrop,
        )
    }

    fun play() {
        val item = _state.value.item ?: return
        graph.playerRequest = item
    }

    fun playTrailer() {
        val item = _state.value.item ?: return
        // 1) tenta o trailer que já veio no metadado (TMDB/YouTube)
        val known = item.details?.trailerYtId ?: TextUtils.youtubeId(item.details?.trailerUrl ?: item.trailer)
        if (!known.isNullOrBlank()) {
            graph.trailerRequest = known
            return
        }
        // 2) busca o trailer oficial e reproduz no player nativo (sem anúncios)
        viewModelScope.launch {
            _state.value = _state.value.copy(trailerRequested = true)
            val trailer = graph.trailerFinder.find(
                title = item.title,
                year = item.year,
                language = graph.settings.settings.preferredLanguage.take(2),
            )
            graph.trailerRequest = trailer.videoId
            _state.value = _state.value.copy(trailerRequested = false)
        }
    }

    fun requestTrailer() {
        _state.value = _state.value.copy(trailerRequested = true)
    }
}

// ---------------------------------------------------------------- PLAYER

data class PlayerUiState(
    val item: MediaItem? = null,
    val url: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val poster: String? = null,
    val isLive: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
    val sources: List<String> = emptyList(),
    val selectedSource: Int = 0,
)

class PlayerViewModel(private val graph: AppServices) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun prepare(item: MediaItem) {
        viewModelScope.launch {
            _state.value = PlayerUiState(item = item, title = item.title, poster = item.poster, isLive = item.kind == MediaKind.LIVE)
            val info = runCatching { graph.addon.resolve(item) }.getOrNull()
            if (info == null || info.url.isBlank()) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Não foi possível resolver o link deste item. Tente outra fonte ou atualize o catálogo.",
                )
                return@launch
            }
            _state.value = _state.value.copy(
                loading = false,
                url = info.url,
                title = info.title.ifBlank { item.title },
                subtitle = info.subtitle,
                isLive = info.isLive,
                headers = info.headers,
                sources = info.sources.map { it.label },
                selectedSource = info.selectedSourceIndex,
            )
        }
    }

    fun selectSource(index: Int) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            val info = runCatching { graph.addon.resolve(item) }.getOrNull() ?: return@launch
            val source = info.sources.getOrNull(index) ?: return@launch
            _state.value = _state.value.copy(
                url = source.url,
                headers = source.headers,
                selectedSource = index,
                error = null,
            )
        }
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            graph.library.saveProgress(item, positionMs, durationMs, item.number, item.number, item.id)
        }
    }
}

// ---------------------------------------------------------------- SEARCH

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<MediaItem> = emptyList(),
    val history: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
)

class SearchViewModel(private val graph: AppServices) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            graph.library.observeSearchHistory().collect { history ->
                _state.value = _state.value.copy(history = history)
            }
        }
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
        job?.cancel()
        if (q.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), loading = false)
            return
        }
        job = viewModelScope.launch {
            delay(220)
            _state.value = _state.value.copy(loading = true)
            val results = runCatching { graph.addon.search(q, page = 1) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(loading = false, results = results)
        }
    }

    fun submit(q: String = _state.value.query) {
        if (q.isBlank()) return
        viewModelScope.launch { graph.library.recordSearch(q) }
    }

    fun clearHistory() {
        viewModelScope.launch { graph.library.clearSearchHistory() }
    }
}

// ----------------------------------------------------------------- GUIDE

data class GuideUiState(
    val loading: Boolean = true,
    val channels: List<MediaItem> = emptyList(),
    val selectedChannel: MediaItem? = null,
    val programs: List<EpgProgram> = emptyList(),
    val status: String = "",
    val error: String? = null,
    val nowMs: Long = System.currentTimeMillis(),
)

class GuideViewModel(private val graph: AppServices) : ViewModel() {

    private val _state = MutableStateFlow(GuideUiState())
    val state: StateFlow<GuideUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val snap = runCatching { graph.catalog.snapshot() }.getOrNull()
            val channels = snap?.live.orEmpty().filter { !it.tvgId.isNullOrBlank() }
                .ifEmpty { snap?.live.orEmpty() }
                .take(200)
            _state.value = _state.value.copy(
                loading = false,
                channels = channels,
                status = graph.epg.status.value,
            )
            // Carrega o guia ANTES de montar a grade do primeiro canal: antes a
            // ordem era invertida e o canal inicial abria sem programação.
            runCatching { graph.epg.ensureLoaded() }
            _state.value = _state.value.copy(status = graph.epg.status.value)
            channels.firstOrNull()?.let { selectChannel(it) }
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                _state.value = _state.value.copy(nowMs = System.currentTimeMillis())
            }
        }
    }

    fun selectChannel(channel: MediaItem) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // 12h contínuas a partir de agora (lacunas preenchidas pelo serviço).
            val programs = runCatching {
                graph.epg.timeline(channel, now, hours = 12)
            }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                selectedChannel = channel,
                programs = programs,
                nowMs = now,
                status = graph.epg.status.value,
            )
        }
    }

    fun forceReloadEpg() {
        viewModelScope.launch {
            graph.epg.invalidate()
            _state.value = _state.value.copy(loading = true)
            graph.epg.ensureLoaded(force = true)
            _state.value = _state.value.copy(loading = false, status = graph.epg.status.value)
            _state.value.selectedChannel?.let { selectChannel(it) }
        }
    }
}

// ---------------------------------------------------------------- ADDONS

data class AddonsUiState(
    val repositoryName: String = com.smarted.tv.core.data.SettingsStore.DEFAULT_REPO_NAME,
    val repositoryUrl: String = "",
    val repositories: List<com.smarted.tv.core.addons.Repository> = emptyList(),
    val addons: List<com.smarted.tv.core.addons.InstalledAddon> = emptyList(),
    val syncStatus: String = "",
    val syncing: Boolean = false,
    val error: String? = null,
)

class AddonsViewModel(private val graph: AppServices) : ViewModel() {

    private val _state = MutableStateFlow(AddonsUiState())
    val state: StateFlow<AddonsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            graph.addons.observeRepositories().collect { repos ->
                val active = repos.firstOrNull { it.isDefault }
                _state.value = _state.value.copy(
                    repositories = repos,
                    repositoryName = active?.name ?: graph.settings.settings.activeRepoName,
                    repositoryUrl = active?.url ?: graph.settings.settings.activeRepoUrl,
                )
            }
        }
        viewModelScope.launch {
            graph.addons.observeAddons().collect { list ->
                _state.value = _state.value.copy(addons = list)
            }
        }
        viewModelScope.launch {
            graph.addons.status.collect { _state.value = _state.value.copy(syncStatus = it) }
        }
        viewModelScope.launch {
            graph.addons.syncing.collect { _state.value = _state.value.copy(syncing = it) }
        }
        viewModelScope.launch {
            graph.addons.lastError.collect { _state.value = _state.value.copy(error = it) }
        }
    }

    fun sync() {
        viewModelScope.launch { graph.addons.syncActive(force = true) }
    }

    fun toggleAddon(id: String, enabled: Boolean) {
        viewModelScope.launch { graph.addons.setAddonEnabled(id, enabled) }
    }

    fun removeAddon(id: String) {
        viewModelScope.launch { graph.addons.deleteAddon(id) }
    }

    fun addRepository(url: String, name: String, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { graph.addons.addRepository(url, name) }
                .onSuccess { onDone(null) }
                .onFailure { onDone(it.message) }
        }
    }

    fun switchRepository(url: String, name: String) {
        viewModelScope.launch {
            runCatching { graph.addons.switchRepositoryAndSync(url, name) }
        }
    }

    fun removeRepository(id: String) {
        viewModelScope.launch { graph.addons.removeRepository(id) }
    }
}

// -------------------------------------------------------------- SETTINGS

data class SettingsUiState(
    val settings: Settings = Settings(),
    val repoName: String = com.smarted.tv.core.data.SettingsStore.DEFAULT_REPO_NAME,
    val repoUrl: String = "",
    val tmdbConfigured: Boolean = false,
    val indexerSummary: String = "",
    val epgStatus: String = "",
    val cacheLabel: String = "",
    val catalogCounts: Triple<Int, Int, Int> = Triple(0, 0, 0),
    val message: String? = null,
    val sources: List<com.smarted.tv.core.model.PublicSource> = emptyList(),
    val epgSources: List<com.smarted.tv.core.data.db.EpgSourceEntity> = emptyList(),
    val busy: Boolean = false,
    val addonCount: Int = 0,
)

class SettingsViewModel(private val graph: AppServices) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            graph.settings.state.collect { s ->
                _state.value = _state.value.copy(
                    settings = s,
                    repoName = s.activeRepoName,
                    repoUrl = s.activeRepoUrl,
                    tmdbConfigured = graph.tmdb.isConfigured,
                )
            }
        }
        viewModelScope.launch {
            graph.catalog.snapshotFlow.collect { snap ->
                _state.value = _state.value.copy(
                    catalogCounts = Triple(snap.live.size, snap.movies.size, snap.series.size),
                )
            }
        }
        viewModelScope.launch {
            graph.indexer.status.collect { _state.value = _state.value.copy(indexerSummary = it) }
        }
        viewModelScope.launch {
            graph.addons.observeAddons().collect { list ->
                _state.value = _state.value.copy(addonCount = list.count { it.enabled })
            }
        }
        viewModelScope.launch {
            graph.epg.status.collect { _state.value = _state.value.copy(epgStatus = it) }
        }
        refreshSources()
        refreshCacheLabel()
    }

    fun refreshSources() {
        viewModelScope.launch {
            val sources = runCatching { graph.sources.all() }.getOrDefault(emptyList())
            val epg = runCatching { graph.db.epgDao().enabledSources() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(sources = sources, epgSources = epg)
        }
    }

    fun refreshCacheLabel() {
        viewModelScope.launch {
            val bytes = runCatching { graph.library.cacheSizeBytes() }.getOrDefault(0L)
            val catalog = runCatching { graph.db.cacheDao().sizeBytes() }.getOrNull() ?: 0L
            _state.value = _state.value.copy(cacheLabel = formatBytes(bytes + catalog))
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> "vazio"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

    fun update(block: (Settings) -> Settings) {
        graph.settings.update(block)
    }

    /** Troca um dos três repositórios de conteúdo e força a reindexação. */
    fun setContentRepo(kind: MediaKind, url: String) {
        graph.settings.setContentRepository(kind, url)
        _state.value = _state.value.copy(message = "Repositório atualizado. Reindexando…")
        viewModelScope.launch {
            graph.catalog.invalidate()
            runCatching { graph.catalog.snapshot(force = true) }
            _state.value = _state.value.copy(message = null)
        }
    }

    /** Grava a lista pública de um tipo (modos Normal e SmartED). */
    fun setPublicSource(kind: MediaKind, url: String) {
        graph.settings.setPublicSource(kind, url)
        _state.value = _state.value.copy(message = "Lista pública atualizada. Reindexando…")
        viewModelScope.launch {
            graph.catalog.invalidate()
            runCatching { graph.catalog.snapshot(force = true) }
            _state.value = _state.value.copy(message = null)
        }
    }

    /** Define o modo de conteúdo de uma aba em Ajustes. */
    fun setContentMode(kind: MediaKind, mode: ContentMode) {
        graph.settings.setContentMode(kind, mode)
        viewModelScope.launch {
            graph.catalog.invalidate()
            runCatching { graph.catalog.snapshot(force = true) }
        }
    }

    fun togglePortugueseOnly(enabled: Boolean) {
        graph.settings.update { it.copy(portugueseOnly = enabled) }
        viewModelScope.launch {
            graph.catalog.invalidate()
            runCatching { graph.catalog.snapshot(force = true) }
        }
    }

    fun setRepo(url: String, name: String) {
        viewModelScope.launch {
            runCatching { graph.addons.setActiveRepository(url, name) }
                .onSuccess { _state.value = _state.value.copy(message = "Repositório ativo: ${it.name}") }
                .onFailure { _state.value = _state.value.copy(message = it.message) }
        }
    }

    fun setTmdbKey(key: String) {
        graph.applyTmdbKey(key)
        _state.value = _state.value.copy(message = if (key.isBlank()) "TMDB desativado" else "Chave TMDB salva")
    }

    fun addEpgSource(url: String, label: String) {
        viewModelScope.launch {
            runCatching { graph.epg.addSource(url, label, priority = 50) }
                .onSuccess {
                    _state.value = _state.value.copy(message = "Fonte de EPG adicionada")
                    refreshSources()
                }
                .onFailure { _state.value = _state.value.copy(message = it.message) }
        }
    }

    fun removeEpgSource(url: String) {
        viewModelScope.launch {
            runCatching { graph.epg.removeSource(url); refreshSources() }
        }
    }

    fun addPublicSource(name: String, url: String, kind: MediaKind) {
        viewModelScope.launch {
            runCatching { graph.sources.addCustom(name, url, kind) }
                .onSuccess {
                    _state.value = _state.value.copy(message = "Fonte adicionada")
                    refreshSources()
                    graph.catalog.invalidate()
                }
                .onFailure { _state.value = _state.value.copy(message = it.message) }
        }
    }

    fun toggleSource(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val source = runCatching { graph.sources.all() }.getOrDefault(emptyList()).firstOrNull { it.id == id }
                ?: return@launch
            graph.sources.upsert(source.copy(enabled = enabled))
            refreshSources()
            graph.catalog.invalidate()
        }
    }

    fun removeSource(id: String) {
        viewModelScope.launch {
            graph.sources.remove(id)
            refreshSources()
            graph.catalog.invalidate()
        }
    }

    fun runIndexerScan() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { graph.indexer.scan() }
            _state.value = _state.value.copy(busy = false, message = graph.indexer.summarize())
        }
    }

    fun runMaintenance() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val snap = graph.catalog.snapshotFlow.value
            val report = runCatching { graph.indexer.maintain(snap.live + snap.movies, sampleSize = 30) }.getOrNull()
            _state.value = _state.value.copy(
                busy = false,
                message = if (report != null) {
                    "Manutenção: ${report.alive} ativos / ${report.dead} mortos"
                } else {
                    "Falha na manutenção"
                },
            )
        }
    }

    fun reloadCatalog() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { graph.catalog.snapshot(force = true) }
            _state.value = _state.value.copy(busy = false, message = graph.catalog.status.value)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            runCatching { graph.library.clearCache() }
            runCatching { graph.catalog.clearCache() }
            refreshCacheLabel()
            _state.value = _state.value.copy(message = "Cache limpo")
        }
    }

    fun reloadEpg() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            graph.epg.invalidate()
            runCatching { graph.epg.ensureLoaded(force = true) }
            _state.value = _state.value.copy(busy = false, message = graph.epg.status.value)
        }
    }

    fun syncCloudCatalog(url: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val result = runCatching { graph.sources.syncFromCloud(url) }.getOrNull()
            _state.value = _state.value.copy(
                busy = false,
                message = when {
                    result == null -> "Falha ao sincronizar catálogo"
                    result.error != null -> result.error
                    else -> "Catálogo atualizado: +${result.added} novas, ${result.updated} atualizadas"
                },
            )
            refreshSources()
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
