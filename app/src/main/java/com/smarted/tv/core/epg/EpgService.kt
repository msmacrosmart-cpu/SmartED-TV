package com.smarted.tv.core.epg

import com.smarted.tv.core.data.db.EpgDao
import com.smarted.tv.core.data.db.EpgSourceEntity
import com.smarted.tv.core.model.EpgProgram
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.net.Http
import com.smarted.tv.core.util.TextUtils
import com.smarted.tv.core.util.XmltvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Serviço de EPG com **fallback em cascata**:
 *
 *  1. Guia do addon ativo (se ele fornecer).
 *  2. Fontes XMLTV configuradas — começando pelo padrão
 *     `https://epg.best/brazil.xml`.
 *  3. Fontes XMLTV declaradas no `addon.xml` / catálogo de cada addon.
 *
 * O casamento canal ↔ programa é feito por `tvg-id`, depois por nome
 * normalizado (tolerante a "Globo HD" ↔ "Globo", "SBT (1080p)" ↔ "SBT").
 *
 * O parse roda em memória com índice invertido e janela de 3 dias para caber
 * em TV boxes de 1 GB sem estourar o heap.
 */
class EpgService(
    private val http: Http,
    private val dao: EpgDao,
) {

    data class Guide(
        val byChannel: Map<String, List<EpgProgram>> = emptyMap(),
        val channelFields: Map<String, XmltvParser.Channel> = emptyMap(),
        val loadedAt: Long = 0L,
        val sourceUrl: String? = null,
        val error: String? = null,
    ) {
        val isEmpty: Boolean get() = byChannel.isEmpty()
        val programCount: Int get() = byChannel.values.sumOf { it.size }
    }

    private val mutex = Mutex()
    private val nameIndex = ConcurrentHashMap<String, String>()
    private val programCache = ConcurrentHashMap<String, List<EpgProgram>>()

    private val _guide = MutableStateFlow(Guide())
    val guide: StateFlow<Guide> = _guide.asStateFlow()

    private val _status = MutableStateFlow("EPG não carregado")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    @Volatile
    private var loadedAt = 0L

    private val ttlMs = 60 * 60 * 1000L

    fun currentGuide(): Guide = _guide.value

    /** Carrega o guia, tentando cada fonte em ordem até uma funcionar. */
    suspend fun ensureLoaded(force: Boolean = false): Guide = withContext(Dispatchers.IO) {
        val current = _guide.value
        if (!force && !current.isEmpty && System.currentTimeMillis() - loadedAt < ttlMs) return@withContext current
        mutex.withLock {
            if (!force && !_guide.value.isEmpty && System.currentTimeMillis() - loadedAt < ttlMs) {
                return@withLock _guide.value
            }
            val sources = dao.enabledSources().sortedBy { it.priority }
            if (sources.isEmpty()) {
                _status.value = "Nenhuma fonte de EPG configurada"
                return@withLock _guide.value
            }
            _loading.value = true
            try {
                for (source in sources) {
                    val guide = fetch(source)
                    if (guide != null && !guide.isEmpty) {
                        loadedAt = guide.loadedAt
                        _guide.value = guide
                        _status.value = "${guide.programCount} programas · ${guide.channelFields.size} canais"
                        return@withLock guide
                    }
                }
                _status.value = "Nenhuma fonte de EPG respondeu"
                _guide.value
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun fetch(source: EpgSourceEntity): Guide? {
        return try {
            val raw = http.getText(source.url, cacheable = true, timeoutSeconds = 120)
            val parsed = XmltvParser.parse(raw)
            dao.markFetched(
                url = source.url,
                at = System.currentTimeMillis(),
                channels = parsed.channels.size,
                programs = parsed.programs.size,
                status = if (parsed.programs.isEmpty()) "EMPTY" else "OK",
                message = null,
            )
            nameIndex.clear()
            nameIndex.putAll(XmltvParser.buildChannelIndex(parsed.channels))
            programCache.clear()
            programCache.putAll(parsed.programs)
            val cutoff = System.currentTimeMillis() - 12 * 60 * 60 * 1000L
            val pruned = parsed.programs.mapValues { (_, list) ->
                list.filter { it.endMs > cutoff }
            }
            Guide(
                byChannel = pruned,
                channelFields = parsed.channels,
                loadedAt = System.currentTimeMillis(),
                sourceUrl = source.url,
            )
        } catch (e: Exception) {
            dao.markFetched(
                url = source.url,
                at = System.currentTimeMillis(),
                channels = 0,
                programs = 0,
                status = "ERROR",
                message = e.message,
            )
            null
        }
    }

    /** Programa "agora" + "a seguir" para o canal. */
    suspend fun programsFor(channel: MediaItem, startMs: Long, endMs: Long): List<EpgProgram> {
        val guide = if (_guide.value.isEmpty) ensureLoaded() else _guide.value
        if (guide.isEmpty) return emptyList()
        val ids = candidateIds(channel)
        for (id in ids) {
            val list = guide.byChannel[id] ?: continue
            if (list.isNotEmpty()) {
                return list.filter { p -> p.endMs > startMs && p.startMs < endMs }
            }
        }
        // último recurso: casamento por nome contra todos os canais
        val key = TextUtils.titleKey(channel.title)
        val match = guide.channelFields.entries.firstOrNull { (id, ch) ->
            TextUtils.titleKey(ch.name) == key || TextUtils.titleKey(id) == key
        }
        if (match != null) {
            return guide.byChannel[match.key].orEmpty().filter { p -> p.endMs > startMs && p.startMs < endMs }
        }
        return emptyList()
    }

    /** Resolve o id do XMLTV para um canal, considerando variações de sufixo. */
    fun candidateIds(channel: MediaItem): List<String> {
        val out = LinkedHashSet<String>()
        channel.tvgId?.let { out.add(it) }
        channel.tvgId?.substringBefore('.')?.let { out.add(it) }
        val key = TextUtils.titleKey(channel.title)
        nameIndex[key]?.let { out.add(it) }
        // "Globo HD" -> "globo"
        channel.tvgId?.let { tvg ->
            val normalized = TextUtils.titleKey(tvg.replace(".", " "))
            nameIndex[normalized]?.let { out.add(it) }
        }
        return out.toList()
    }

    /** Próximo programa após `now` no canal. */
    suspend fun nowNext(channel: MediaItem, now: Long = System.currentTimeMillis()): Pair<EpgProgram?, EpgProgram?> {
        val list = programsFor(channel, now - 2 * 60 * 60 * 1000L, now + 12 * 60 * 60 * 1000L)
        val current = list.firstOrNull { now in it.startMs until it.endMs }
        val next = list.firstOrNull { it.startMs > now }
        return current to next
    }

    /** Grade completa de um canal para a tela de EPG. */
    suspend fun timeline(channel: MediaItem, fromMs: Long, hours: Int = 12): List<EpgProgram> =
        programsFor(channel, fromMs, fromMs + hours * 60L * 60L * 1000L)

    suspend fun sources(): List<EpgSourceEntity> = withContext(Dispatchers.IO) { dao.enabledSources() }

    suspend fun addSource(url: String, label: String, priority: Int = 100, fromAddonId: String? = null) =
        withContext(Dispatchers.IO) {
            dao.upsertSource(
                EpgSourceEntity(
                    url = url.trim(),
                    label = label.ifBlank { url },
                    enabled = true,
                    priority = priority,
                    fromAddonId = fromAddonId,
                ),
            )
        }

    suspend fun removeSource(url: String) = withContext(Dispatchers.IO) { dao.deleteSource(url) }

    fun invalidate() {
        loadedAt = 0L
        _guide.value = Guide()
        programCache.clear()
        nameIndex.clear()
    }
}
