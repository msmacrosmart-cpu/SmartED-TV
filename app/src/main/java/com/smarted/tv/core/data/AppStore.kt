package com.smarted.tv.core.data

import com.smarted.tv.core.model.AppData
import com.smarted.tv.core.model.InstalledAddon
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.PlaybackProgress
import com.smarted.tv.core.model.Scope
import com.smarted.tv.core.protocol.AddonException
import com.smarted.tv.core.protocol.AddonUrls
import com.smarted.tv.core.protocol.ManifestValidator
import com.smarted.tv.core.protocol.StremioApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PersistedState {
    suspend fun read(): AppData
    suspend fun write(data: AppData)
}

class InMemoryState(initial: AppData = AppData()) : PersistedState {
    var value = initial
        private set
    override suspend fun read() = value
    override suspend fun write(data: AppData) { value = data }
}

/** All mutations are serialized. Network validation happens before an installation is committed. */
class AppRepository(val persistence: PersistedState, private val api: StremioApi, private val clock: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()
    val initialized: Boolean get() = loaded
    private var loaded = false

    suspend fun initialize() = mutex.withLock {
        if (!loaded) {
            _data.value = persistence.read()
            loaded = true
        }
    }

    suspend fun install(input: String, scope: Scope): InstalledAddon = mutex.withLock {
        ensureLoaded()
        val url = AddonUrls.normalize(input)
        val current = _data.value
        if (current.addons.any { it.url == url }) throw AddonException("Este manifesto já está instalado. Edite o addon existente para alterar o escopo.")
        if (current.addons.size >= 3) throw AddonException("Limite atingido: remova um dos 3 addons antes de adicionar outro.")
        val manifest = api.manifest(url)
        ManifestValidator.validate(manifest, scope)
        val addon = InstalledAddon(url, scope, manifest, clock())
        commit(current.copy(addons = current.addons + addon))
        addon
    }

    suspend fun changeScope(url: String, scope: Scope) = mutex.withLock {
        ensureLoaded()
        val current = _data.value
        val addon = current.addons.firstOrNull { it.url == url } ?: throw AddonException("Addon não encontrado.")
        ManifestValidator.validate(addon.manifest, scope)
        commit(current.copy(addons = current.addons.map {
            if (it.url == url) it.copy(scope = scope) else it
        }, favorites = current.favorites.filterNot { it.addonUrl == url && it.scope != scope }))
    }

    suspend fun refresh(url: String) = mutex.withLock {
        ensureLoaded()
        val current = _data.value
        val addon = current.addons.firstOrNull { it.url == url } ?: throw AddonException("Addon não encontrado.")
        val manifest = api.manifest(url)
        ManifestValidator.validate(manifest, addon.scope)
        commit(current.copy(addons = current.addons.map { if (it.url == url) it.copy(manifest = manifest) else it }))
    }

    suspend fun remove(url: String) = mutex.withLock {
        ensureLoaded()
        val current = _data.value
        commit(current.copy(addons = current.addons.filterNot { it.url == url },
            favorites = current.favorites.filterNot { it.addonUrl == url },
            progress = current.progress.filterKeys { !it.startsWith("$url|") }))
    }

    suspend fun favorite(item: MediaItem) = mutex.withLock {
        ensureLoaded()
        val current = _data.value
        val exists = current.favorites.any { it.key == item.key }
        commit(current.copy(favorites = if (exists) current.favorites.filterNot { it.key == item.key } else current.favorites + item))
    }

    suspend fun saveProgress(item: MediaItem, videoId: String, position: Long, duration: Long) = mutex.withLock {
        ensureLoaded()
        if (item.scope == Scope.CHANNELS || position < 0 || duration < 0) return@withLock
        val current = _data.value
        val key = "${item.key}|$videoId"
        val entries = current.progress.filterKeys { it != key }.entries.sortedByDescending { it.value.updatedAt }.take(199)
        commit(current.copy(progress = entries.associate { it.toPair() } + (key to PlaybackProgress(position, duration, clock()))))
    }

    fun progress(item: MediaItem, videoId: String): PlaybackProgress? = _data.value.progress["${item.key}|$videoId"]

    suspend fun updatePreferences(transform: (com.smarted.tv.core.model.UserPreferences) -> com.smarted.tv.core.model.UserPreferences) = mutex.withLock {
        ensureLoaded()
        val current = _data.value
        commit(current.copy(preferences = transform(current.preferences)))
    }

    suspend fun clearProgress() = mutex.withLock {
        ensureLoaded()
        commit(_data.value.copy(progress = emptyMap()))
    }

    private suspend fun commit(next: AppData) {
        persistence.write(next)
        _data.value = next
    }
    private fun ensureLoaded() { check(loaded) { "Armazenamento ainda não inicializado." } }
}
