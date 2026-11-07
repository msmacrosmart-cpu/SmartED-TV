package com.smarted.tv

import android.content.Context
import com.smarted.tv.addons.brazuca.BrazucaCatalog
import com.smarted.tv.addons.brazuca.BrazucaPlayAddon
import com.smarted.tv.core.addons.AddonManager
import com.smarted.tv.core.data.AssetReader
import com.smarted.tv.core.data.SettingsStore
import com.smarted.tv.core.data.SourceRepository
import com.smarted.tv.core.data.db.SmartEdDatabase
import com.smarted.tv.core.epg.EpgService
import com.smarted.tv.core.indexer.Indexer
import com.smarted.tv.core.net.Http
import com.smarted.tv.core.tmdb.TmdbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Grafo de dependências do app (service locator simples, sem reflexão).
 *
 * Tudo é criado uma única vez e compartilhado pelas telas via
 * [LocalAppGraph] / [SmartEdApp.graph].
 */
class AppGraph(context: Context) : com.smarted.tv.ui.state.AppServices {

    override val appContext: Context = context.applicationContext
    override val settings = SettingsStore(appContext)
    val assets = AssetReader(appContext.assets)
    val http = Http(cacheDir = File(appContext.cacheDir, "smarted"), userAgent = Http.DEFAULT_UA)
    override val db: SmartEdDatabase = SmartEdDatabase.get(appContext)

    override val tmdb = TmdbClient(
        http = http,
        apiKey = settings.settings.tmdbKey.ifBlank { BuildConfig.TMDB_API_KEY },
        language = settings.settings.tmdbLanguage.ifBlank { "pt-BR" },
    )

    override val sources = SourceRepository(
        dao = db.publicSourceDao(),
        http = http,
        assets = assets,
    )

    override val catalog = BrazucaCatalog(
        sources = sources,
        http = http,
        cache = db.cacheDao(),
    )

    override val trailerFinder = com.smarted.tv.core.trailer.TrailerFinder(http)

    override val epg = EpgService(http = http, dao = db.epgDao())

    override val indexer = Indexer(sources = sources, publicSourceDao = db.publicSourceDao(), http = http)

    val brazuca = BrazucaPlayAddon(catalog = catalog, tmdb = tmdb, epg = epg)

    override val addon: com.smarted.tv.ui.state.AddonGateway = brazuca

    override val addons = AddonManager(
        repositoryDao = db.repositoryDao(),
        addonDao = db.addonDao(),
        settings = settings,
        http = http,
        builtin = brazuca,
    )

    override val library = com.smarted.tv.ui.state.LibraryRepository(
        favorites = db.favoriteDao(),
        progress = db.progressDao(),
        categories = db.categoryDao(),
        search = db.searchDao(),
        cache = db.cacheDao(),
    )

    /** Pedido de reprodução pendente (a navegação consome e limpa). */
    @Volatile
    override var playerRequest: com.smarted.tv.core.model.MediaItem? = null

    /** Trailer do YouTube pendente (id do vídeo). */
    @Volatile
    override var trailerRequest: String? = null

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Estado global de prontidão usado pelo splash. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _bootMessage = MutableStateFlow("Iniciando SmartED TV…")
    val bootMessage: StateFlow<String> = _bootMessage.asStateFlow()

    /**
     * Sequência de boot — precisa terminar rápido:
     *  1. bootstrap do repositório (SharedPreferences já tem o valor → sem I/O de rede)
     *  2. seed do catálogo de fontes (asset)
     *  3. hidratação do snapshot em cache (sem rede)
     *  4. rede: sync do repositório + catálogo + EPG (em background, não bloqueia)
     */
    fun boot() {
        scope.launch {
            _bootMessage.value = "Carregando repositório ativo…"
            runCatching { addons.bootstrap() }
                .onFailure { _bootMessage.value = "Falha no repositório: ${it.message}" }

            _bootMessage.value = "Indexando fontes públicas…"
            runCatching { sources.ensureSeeded() }
            runCatching { sources.ensureEpgSource(db.epgDao(), settings.settings.epgUrl) }

            // cache local → tela pronta
            runCatching { catalog.hydrate() }
            _ready.value = true

            // rede em background
            launch {
                runCatching { addons.syncActive() }
            }
            launch {
                runCatching { catalog.snapshot() }
                if (settings.settings.maintenanceEnabled) {
                    val snap = catalog.snapshotFlow.value
                    runCatching {
                        indexer.maintain(snap.live.take(12) + snap.movies.take(6))
                    }
                }
            }
            launch {
                if (settings.settings.epgAutoload) runCatching { epg.ensureLoaded() }
            }
            launch {
                if (settings.settings.indexerAutoUpdate) runCatching { indexer.scan() }
            }
        }
    }

    override fun applyTmdbKey(key: String) {
        settings.update { it.copy(tmdbKey = key) }
        tmdb.updateKey(key.ifBlank { BuildConfig.TMDB_API_KEY })
    }

    fun shutdown() {
        runCatching { http.shutdown() }
    }
}

