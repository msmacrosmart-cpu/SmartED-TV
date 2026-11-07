package com.smarted.tv.core.addons

import com.smarted.tv.core.data.SettingsStore
import com.smarted.tv.core.data.db.AddonDao
import com.smarted.tv.core.data.db.DbMappers.toDomain
import com.smarted.tv.core.data.db.InstalledAddonEntity
import com.smarted.tv.core.data.db.RepositoryDao
import com.smarted.tv.core.data.db.RepositoryEntity
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.net.Http
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
            val configuredName = settings.settings.activeRepoName.ifBlank { "Brazuca Play" }

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
                            types = inferTypes(info).joinToString(",") { it.name },
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
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private fun indexCandidatesFor(repoUrl: String): List<String> {
        val base = repoUrl.trimEnd('/')
        return listOf(
            "$base/addons.xml",
            "$base/repo/addons.xml",
            "$base/zips/addons.xml",
            "$base/index.html",
            "$base/",
        ).distinct()
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

    suspend fun addRepository(url: String, name: String): Repository = withContext(Dispatchers.IO) {
        val normalized = url.trim().let { if (it.startsWith("http")) it else "https://$it" }.trimEnd('/')
        val existing = repositoryDao.byUrl(normalized)
        if (existing != null) return@withContext existing.toDomain()
        val entity = RepositoryEntity(
            id = repoIdFor(normalized),
            name = name.ifBlank { guessRepoName(normalized) },
            url = normalized,
            addedAt = System.currentTimeMillis(),
        )
        repositoryDao.upsert(entity)
        _repositories.value = repositoryDao.all().map { it.toDomain() }
        entity.toDomain()
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

    suspend fun ensureDefaultRepository(): Repository = withContext(Dispatchers.IO) {
        repositoryDao.byUrl(SettingsStore.DEFAULT_REPO_URL)?.toDomain()
            ?: addRepository(SettingsStore.DEFAULT_REPO_URL, "Brazuca Play")
    }

    private fun guessRepoName(url: String): String {
        val host = url.substringAfter("://").substringBefore('/')
        return host.removePrefix("www.").ifBlank { "Repositório" }
    }

    private fun repoIdFor(url: String): String =
        "repo-" + Integer.toHexString(url.trimEnd('/').hashCode())

    data class SyncResult(val addonCount: Int, val error: String?)
}
