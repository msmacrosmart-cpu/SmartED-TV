package com.smarted.tv.core.addons

import com.smarted.tv.core.data.SettingsStore
import com.smarted.tv.core.data.db.AddonDao
import com.smarted.tv.core.data.db.DbMappers.toDomain
import com.smarted.tv.core.data.db.InstalledAddonEntity
import com.smarted.tv.core.data.db.RepositoryDao
import com.smarted.tv.core.data.db.RepositoryEntity
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.net.Http
import com.smarted.tv.core.util.SourceTargeting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gerenciador de addons e repositórios.
 *
 * Fluxo de inicialização (chamado uma vez no [com.smarted.tv.SmartEdApp]):
 *  1. Garante o repositório **Brazuca Play** (`https://vkodi.net/repo`) registrado
 *     e marcado como ativo — lido direto das SharedPreferences.
 *  2. Registra o addon nativo `brazuca.play` (sempre habilitado, não removível).
 *  3. Sincroniza o repositório ativo (online, best-effort) para descobrir addons.
 *  4. Mantém tudo cacheado no Room para o próximo start ser instantâneo.
 *
 * "Trocar repositório" = [setActiveRepository]; persiste em SharedPreferences
 * **e** Room, de modo que o app abre pronto com base no repositório ativo.
 */
class AddonManager(
    private val repositoryDao: RepositoryDao,
    private val addonDao: AddonDao,
    private val settings: SettingsStore,
    private val http: Http,
    private val builtin: Addon,
) {

    data class RepoView(
        val repository: Repository,
        val addons: List<InstalledAddon>,
        val isActive: Boolean,
    )

    private val mutex = Mutex()

    private val _repositories = MutableStateFlow<List<Repository>>(emptyList())
    val repositories: StateFlow<List<Repository>> = _repositories.asStateFlow()

    private val _addons = MutableStateFlow<List<InstalledAddon>>(emptyList())
    val addons: StateFlow<List<InstalledAddon>> = _addons.asStateFlow()

    private val _active = MutableStateFlow<Repository?>(null)
    val active: StateFlow<Repository?> = _active.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _status = MutableStateFlow("Repositório pronto")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun observeRepositories(): Flow<List<Repository>> =
        repositoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeAddons(): Flow<List<InstalledAddon>> =
        addonDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Addon nativo registrado na inicialização (após [bootstrap]). */
    var builtinAddon: Addon = builtin
        private set

    /**
     * Prepara o banco: repositório padrão + addon nativo.
     * Idempotente — seguro chamar em todo start.
     */
    suspend fun bootstrap() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val configuredUrl = settings.settings.activeRepoUrl.ifBlank { SettingsStore.DEFAULT_REPO_URL }
            val configuredName = settings.settings.activeRepoName.ifBlank { SettingsStore.DEFAULT_REPO_NAME }

            var active = repositoryDao.byUrl(configuredUrl)
            if (active == null) {
                val entity = RepositoryEntity(
                    id = repoIdFor(configuredUrl),
                    name = configuredName,
                    url = configuredUrl,
                    isDefault = configuredUrl == SettingsStore.DEFAULT_REPO_URL,
                    enabled = true,
                    addedAt = System.currentTimeMillis(),
                )
                repositoryDao.upsert(entity)
                active = entity
            }

            // Só um repositório ativo: o persistido.
            repositoryDao.all().forEach { repo ->
                val shouldBeDefault = repo.url == configuredUrl
                if (repo.isDefault != shouldBeDefault) {
                    repositoryDao.upsert(repo.copy(isDefault = shouldBeDefault))
                }
            }

            // Addon nativo (sempre presente, habilitado).
            val existingBuiltin = addonDao.byId(builtin.manifest.id)
            addonDao.upsert(
                InstalledAddonEntity(
                    id = builtin.manifest.id,
                    repositoryId = active.id,
                    name = builtin.manifest.name,
                    version = builtin.manifest.version,
                    summary = builtin.manifest.summary,
                    summaryLong = builtin.manifest.description,
                    author = builtin.manifest.author,
                    icon = builtin.manifest.icon,
                    enabled = true,
                    installedAt = existingBuiltin?.installedAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    kind = AddonKind.BUILTIN.name,
                    types = builtin.manifest.types.joinToString(",") { it.name },
                    sortOrder = 0,
                ),
            )

            _active.value = active.toDomain()
            _repositories.value = repositoryDao.all().map { it.toDomain() }
            _addons.value = addonDao.all().map { it.toDomain() }
            if (_repositories.value.isEmpty()) {
                _status.value = "Nenhum repositório configurado"
            }
        }
    }

    /**
     * Sincroniza o repositório ativo. Se `https://vkodi.net/repo` não expuser
     * `addons.xml`, cai para o parser de índice HTML e, por fim, trata o
     * repositório como contêiner de fontes (o Brazuca Play continua entregando
     * conteúdo pelo indexador).
     */
    suspend fun syncActive(force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val repo = _active.value ?: runCatching { bootstrap() }.let { _active.value }
        ?: return@withContext SyncResult(0, "nenhum repositório ativo")
        syncRepository(repo, force)
    }

    suspend fun syncRepository(repo: Repository, force: Boolean = false): SyncResult =
        withContext(Dispatchers.IO) {
            if (_syncing.value && !force) return@withContext SyncResult(0, "sincronização em andamento")
            _syncing.value = true
            _status.value = "Sincronizando ${repo.name}…"
            _lastError.value = null
            try {
                repositoryDao.markSynced(repo.id, repo.lastSyncAt, "SYNCING", null, repo.addonCount)
                val entries = discoverAddons(repo)
                val now = System.currentTimeMillis()

                if (entries.isNotEmpty()) {
                    val existing = addonDao.byRepository(repo.id).associateBy { it.id }
                    val toWrite = entries.mapIndexed { index, info ->
                        val previous = existing[info.id]
                        InstalledAddonEntity(
                            id = info.id,
                            repositoryId = repo.id,
                            name = info.name,
                            version = info.version,
                            summary = info.summary,
                            summaryLong = info.description,
                            author = info.author,
                            icon = info.icon,
                            enabled = previous?.enabled ?: true,
                            installedAt = previous?.installedAt ?: now,
                            updatedAt = now,
                            kind = AddonKind.REMOTE.name,
                            // Marcação do repositório manda: se o usuário disse
                            // "este repo é de filmes", o addon não aparece nas
                            // outras abas. Sem marcação, infere pelos metadados.
                            types = com.smarted.tv.core.util.SourceTargeting.encode(repo.kinds)
                                .ifBlank { inferTypes(info).joinToString(",") { it.name } },
                            sortOrder = index + 1,
                        )
                    }
                    addonDao.upsertAll(toWrite)
                }

                repositoryDao.markSynced(repo.id, now, "ONLINE", null, entries.size)
                _repositories.value = repositoryDao.all().map { it.toDomain() }
                _addons.value = addonDao.all().map { it.toDomain() }
                _active.value = repositoryDao.byId(repo.id)?.toDomain() ?: _active.value
                _status.value = if (entries.isEmpty()) {
                    "${repo.name} conectado · conteúdo via fontes indexadas"
                } else {
                    "${repo.name} · ${entries.size} addons disponíveis"
                }
                SyncResult(entries.size, null)
            } catch (e: Exception) {
                val message = e.message ?: "falha ao sincronizar"
                repositoryDao.markSynced(repo.id, repo.lastSyncAt, "OFFLINE", message, repo.addonCount)
                _repositories.value = repositoryDao.all().map { it.toDomain() }
                _lastError.value = message
                _status.value = "Falha ao sincronizar ${repo.name}"
                SyncResult(0, message)
            } finally {
                _syncing.value = false
            }
        }

    // -----------------------------------------------------------------
    // Elementum (torrentes)
    // -----------------------------------------------------------------

    /** O addon Elementum já está na lista instalada? */
    fun elementumAddon(): InstalledAddon? =
        _addons.value.firstOrNull { Elementum.isElementum(it.id, it.name) }

    /**
     * Garante o addon **Elementum** na lista, mesmo antes de sincronizar o
     * repositório.
     *
     * Ele é publicado pelo Vikings; se a sincronização ainda não rodou (ou o
     * índice não expôs o addon), o app cria o registro local — assim o usuário
     * vê o Elementum em Addons e pode usar o daemon que já tenha instalado no
     * aparelho. Os streams continuam vindo do daemon local
     * ([com.smarted.tv.core.net.ElementumClient]), nunca de um plugin Python.
     */
    suspend fun ensureElementum(): InstalledAddon = withContext(Dispatchers.IO) {
        elementumAddon()?.let { return@withContext it }
        if (_active.value == null) runCatching { bootstrap() }
        val repositoryId = _active.value?.id.orEmpty()
        val now = System.currentTimeMillis()
        val existing = addonDao.byId(Elementum.ADDON_ID)
        addonDao.upsert(
            InstalledAddonEntity(
                id = Elementum.ADDON_ID,
                repositoryId = repositoryId,
                name = Elementum.ADDON_NAME,
                version = "1.0.0",
                summary = "Filmes e séries por torrentes",
                summaryLong = "Busca torrentes no daemon local do Elementum " +
                    "(127.0.0.1:${Elementum.DEFAULT_PORT}) e reproduz no player do app.",
                author = "Vikings",
                icon = null,
                enabled = true,
                installedAt = existing?.installedAt ?: now,
                updatedAt = now,
                kind = AddonKind.REMOTE.name,
                // Elementum é VOD: nunca aparece em Canais.
                types = "${MediaKind.MOVIE.name},${MediaKind.SERIES.name}",
                sortOrder = 1,
            ),
        )
        _addons.value = addonDao.all().map { it.toDomain() }
        addonDao.byId(Elementum.ADDON_ID)?.toDomain()
            ?: InstalledAddon(
                id = Elementum.ADDON_ID,
                repositoryId = repositoryId,
                name = Elementum.ADDON_NAME,
                version = "1.0.0",
                summary = "Filmes e séries por torrentes",
            )
    }

    /**
     * Descobre addons no repositório:
     *  1. `addons.xml` / `addons.xml.gz` / `repo/addons.xml`
     *  2. listagem HTML (diretório Apache/nginx)
     *  3. fallback: repositório é contêiner de fontes — devolve vazio e o
     *     Brazuca Play segue como provedor de conteúdo.
     */
    private suspend fun discoverAddons(repo: Repository): List<KodiRepoParser.RepoInfo> {
        for (candidate in indexCandidatesFor(repo.url)) {
            val body = try {
                withTimeoutOrNull(25_000L) { http.getText(candidate, cacheable = false, timeoutSeconds = 20) }
            } catch (_: Exception) {
                null
            } ?: continue
            if (!looksLikeMarkup(body)) continue
            val parsed = if (body.contains("<addon", true)) {
                KodiRepoParser.parseAddonsXml(body, repo.url)
            } else {
                KodiRepoParser.parseHtmlIndex(body, repo.url)
            }
            val content = parsed.filter { isContentAddon(it) }
            if (content.isNotEmpty()) return content
        }
        return emptyList()
    }

    /**
     * Ordem de descoberta. Além do layout clássico (`addons.xml` na raiz),
     * cobre repositórios do tipo GitHub Pages que guardam os zips em um
     * subdiretório de versão (ex.: Vikings → `/matrix/`).
     */
    private fun indexCandidatesFor(repoUrl: String): List<String> {
        val base = repoUrl.trimEnd('/')
        val subdirs = listOf("matrix", "leia", "omega", "repo", "zips")
        val candidates = ArrayList<String>()
        candidates += listOf("$base/addons.xml", "$base/repo/addons.xml", "$base/zips/addons.xml")
        subdirs.forEach { dir ->
            candidates += "$base/$dir/addons.xml"
            candidates += "$base/$dir/"
        }
        candidates += "$base/index.html"
        candidates += "$base/"
        return candidates.distinct()
    }

    /**
     * Zips que são infraestrutura do repositório (o próprio repositório,
     * módulos de input e wizards de instalação) não são addons de conteúdo.
     * Sem esse filtro, um repositório como o Vikings devolveria 100+ entradas
     * de manutenção e o catálogo ficaria poluído.
     */
    private fun isContentAddon(info: KodiRepoParser.RepoInfo): Boolean {
        val id = info.id.lowercase()
        val maintenance = listOf(
            ".repo", "repository", "inputstream.", "plugin.program.",
            "script.module.", "script.skin.", "resource.", "skin.",
            "service.", "webinterface.", "context.", "plugin.audio.",
        )
        return maintenance.none { id.contains(it) }
    }

    private fun looksLikeMarkup(body: String): Boolean =
        body.contains("<", false) && body.length > 32

    private fun inferTypes(info: KodiRepoParser.RepoInfo): Set<MediaKind> {
        val text = (info.summary + " " + info.description + " " + info.name).lowercase()
        val types = LinkedHashSet<MediaKind>()
        if (text.contains("epg") || text.contains("guia")) return types.also { it.add(MediaKind.LIVE) }
        if (text.contains("filme") || text.contains("movie") || text.contains("vod")) types.add(MediaKind.MOVIE)
        if (text.contains("série") || text.contains("serie") || text.contains("tv show")) types.add(MediaKind.SERIES)
        if (text.contains("canal") || text.contains("channel") || text.contains("ao vivo") || text.contains("live")) {
            types.add(MediaKind.LIVE)
        }
        if (types.isEmpty()) {
            types.add(MediaKind.LIVE)
            types.add(MediaKind.MOVIE)
        }
        return types
    }

    // ------------------------------------------------------------- operações

    /** Troca o repositório ativo — grava em SharedPreferences + Room. */
    suspend fun setActiveRepository(url: String, name: String): Repository =
        withContext(Dispatchers.IO) {
            val trimmed = url.trim()
            val normalized = (if (trimmed.startsWith("http")) trimmed else "https://$trimmed").trimEnd('/')
            if (normalized.isEmpty()) throw AddonException("URL do repositório vazia")
            settings.setRepository(normalized, name.ifBlank { guessRepoName(normalized) })
            repositoryDao.all().forEach { repo ->
                if (repo.url == normalized && !repo.isDefault) {
                    repositoryDao.upsert(repo.copy(isDefault = true, name = name.ifBlank { repo.name }))
                } else if (repo.isDefault) {
                    repositoryDao.upsert(repo.copy(isDefault = false))
                }
            }
            var entity = repositoryDao.byUrl(normalized)
            if (entity == null) {
                entity = RepositoryEntity(
                    id = repoIdFor(normalized),
                    name = name.ifBlank { guessRepoName(normalized) },
                    url = normalized,
                    isDefault = true,
                    addedAt = System.currentTimeMillis(),
                )
                repositoryDao.upsert(entity)
            }
            builtinAddon.refresh()
            _active.value = entity.toDomain()
            _repositories.value = repositoryDao.all().map { it.toDomain() }
            _status.value = "Repositório ativo: ${entity.name}"
            entity.toDomain()
        }

    /** Troca o repositório já criando o estado local e disparando sync. */
    suspend fun switchRepositoryAndSync(url: String, name: String): SyncResult {
        val repo = setActiveRepository(url, name)
        return syncRepository(repo, force = true)
    }

    /**
     * Adiciona um repositório com as **abas marcadas pelo usuário**.
     *
     * @param kinds abas (Canais/Filmes/Séries) em que os addons deste
     *   repositório podem contribuir; vazio = inferir pelos metadados do addon
     */
    suspend fun addRepository(
        url: String,
        name: String,
        kinds: List<com.smarted.tv.core.model.MediaKind> = emptyList(),
    ): Repository = withContext(Dispatchers.IO) {
        val normalized = url.trim().let { if (it.startsWith("http")) it else "https://$it" }.trimEnd('/')
        val existing = repositoryDao.byUrl(normalized)
        if (existing != null) return@withContext existing.toDomain()
        val entity = RepositoryEntity(
            id = repoIdFor(normalized),
            name = name.ifBlank { guessRepoName(normalized) },
            url = normalized,
            addedAt = System.currentTimeMillis(),
            kinds = com.smarted.tv.core.util.SourceTargeting.encode(kinds),
        )
        repositoryDao.upsert(entity)
        _repositories.value = repositoryDao.all().map { it.toDomain() }
        entity.toDomain()
    }

    /**
     * Troca as abas de um addon instalado.
     *
     * É o mesmo checkbox do painel de configuração, aplicado depois da
     * instalação: quem instalou "tudo" pode restringir o addon a Filmes.
     */
    suspend fun setAddonTypes(
        id: String,
        kinds: List<com.smarted.tv.core.model.MediaKind>,
    ) = withContext(Dispatchers.IO) {
        val encoded = com.smarted.tv.core.util.SourceTargeting.encode(kinds)
        addonDao.setTypes(id, encoded)
        _addons.value = addonDao.all().map { it.toDomain() }
    }

    suspend fun removeRepository(id: String) = withContext(Dispatchers.IO) {
        val repo = repositoryDao.byId(id) ?: return@withContext
        if (repo.isDefault) return@withContext
        addonDao.deleteRemoteByRepository(id)
        repositoryDao.delete(id)
        _repositories.value = repositoryDao.all().map { it.toDomain() }
        _addons.value = addonDao.all().map { it.toDomain() }
    }

    suspend fun setAddonEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val addon = addonDao.byId(id) ?: return@withContext
        if (addon.kind == AddonKind.BUILTIN.name && !enabled) return@withContext
        addonDao.setEnabled(id, enabled)
        _addons.value = addonDao.all().map { it.toDomain() }
    }

    suspend fun deleteAddon(id: String) = withContext(Dispatchers.IO) {
        val addon = addonDao.byId(id) ?: return@withContext
        if (addon.kind == AddonKind.BUILTIN.name) return@withContext
        addonDao.delete(id)
        _addons.value = addonDao.all().map { it.toDomain() }
    }

    /** URL de download do zip do addon (para instalação manual/informação). */
    fun downloadUrl(addon: InstalledAddon): String {
        val repo = _repositories.value.firstOrNull { it.id == addon.repositoryId }
        val base = repo?.url ?: settings.settings.activeRepoUrl
        return KodiRepoParser.zipUrl(base, addon.id, addon.version)
    }

    suspend fun installedFor(repoId: String): List<InstalledAddon> = withContext(Dispatchers.IO) {
        addonDao.byRepository(repoId).map { it.toDomain() }
    }

    // ------------------------------------------- navegador de repositório

    /**
     * Pastas do repositório prontas para a tela de navegação (TV ao Vivo,
     * Filmes, Séries) — o menu que o Kodi mostra ao entrar no repositório.
     *
     * A classificação vem dos metadados do addon (id, nome, resumo); a marcação
     * explícita do usuário, quando existe e é específica, tem prioridade.
     */
    suspend fun browseRepository(repositoryId: String): List<RepositoryFolder> =
        withContext(Dispatchers.IO) {
            val entries = ArrayList<BrowserEntry>()
            for (entity in addonDao.byRepository(repositoryId)) {
                if (entity.kind == AddonKind.BUILTIN.name) continue
                val summary = entity.summary.ifBlank { entity.summaryLong }
                for (kind in typesFor(entity)) {
                    entries.add(
                        BrowserEntry(
                            id = entity.id + ":" + kind.name,
                            title = entity.name,
                            summary = summary,
                            kind = kind,
                            author = entity.author,
                            version = entity.version,
                            poster = entity.icon,
                        ),
                    )
                }
            }
            AddonBrowser.folders(entries)
        }

    /** Abas deste repositório já conectadas pelo usuário. */
    suspend fun boundKinds(repositoryId: String): List<MediaKind> = withContext(Dispatchers.IO) {
        val repo = repositoryDao.byId(repositoryId) ?: return@withContext emptyList()
        SourceTargeting.parse(repo.kinds)
    }

    /**
     * Conecta uma pasta do repositório a uma aba do app.
     *
     * Grava a aba no repositório (`repositories.kinds`) e marca os addons
     * daquela pasta com o destino, de modo que o catálogo passe a alimentá-la.
     * Devolve quantos addons foram vinculados.
     */
    suspend fun bindFolder(repositoryId: String, kind: MediaKind): Int =
        withContext(Dispatchers.IO) {
            val repo = repositoryDao.byId(repositoryId) ?: return@withContext 0
            val bound = SourceTargeting.parse(repo.kinds).toMutableSet()
            bound.add(kind)
            repositoryDao.upsert(repo.copy(kinds = SourceTargeting.encode(bound)))

            var linked = 0
            for (entity in addonDao.byRepository(repositoryId)) {
                if (entity.kind == AddonKind.BUILTIN.name) continue
                if (kind !in typesFor(entity)) continue
                val types = SourceTargeting.parse(entity.types).toMutableSet()
                types.add(kind)
                addonDao.setTypes(entity.id, SourceTargeting.encode(types))
                linked++
            }
            _repositories.value = repositoryDao.all().map { it.toDomain() }
            _addons.value = addonDao.all().map { it.toDomain() }
            _status.value = "Pasta conectada: ${AddonBrowser.titleFor(kind)} → aba do app"
            linked
        }

    /** Desconecta a pasta de uma aba (mantém o repositório). */
    suspend fun unbindFolder(repositoryId: String, kind: MediaKind) =
        withContext(Dispatchers.IO) {
            val repo = repositoryDao.byId(repositoryId) ?: return@withContext
            val remaining = SourceTargeting.parse(repo.kinds).filterNot { it == kind }
            repositoryDao.upsert(repo.copy(kinds = SourceTargeting.encode(remaining)))
            _repositories.value = repositoryDao.all().map { it.toDomain() }
            _status.value = "Pasta desconectada: ${AddonBrowser.titleFor(kind)}"
        }

    /**
     * Abas de um addon instalado.
     *
     * Prefere a classificação por metadados quando ela é específica (uma ou duas
     * abas) e cai para a marcação gravada quando o addon é genérico — assim
     * "Filmes" não recebe 100 addons de manutenção só porque o índice não
     * descreve o conteúdo.
     */
    private fun typesFor(entity: InstalledAddonEntity): Set<MediaKind> {
        val inferred = AddonBrowser.classify(entity.id, entity.name, entity.summary)
        if (inferred.size < AddonBrowser.KINDS.size) return inferred
        val stored = SourceTargeting.parse(entity.types)
        return if (stored.isNotEmpty()) stored.toSet() else inferred
    }

    suspend fun ensureDefaultRepository(): Repository = withContext(Dispatchers.IO) {
        repositoryDao.byUrl(SettingsStore.DEFAULT_REPO_URL)?.toDomain()
            ?: addRepository(SettingsStore.DEFAULT_REPO_URL, SettingsStore.DEFAULT_REPO_NAME)
    }

    private fun guessRepoName(url: String): String {
        val host = url.substringAfter("://").substringBefore('/')
        return host.removePrefix("www.").ifBlank { "Repositório" }
    }

    private fun repoIdFor(url: String): String =
        "repo-" + Integer.toHexString(url.trimEnd('/').hashCode())

    data class SyncResult(val addonCount: Int, val error: String?)
}
