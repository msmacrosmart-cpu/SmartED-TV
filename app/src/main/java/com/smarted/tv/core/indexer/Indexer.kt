package com.smarted.tv.core.indexer

import com.smarted.tv.core.data.SourceRepository
import com.smarted.tv.core.data.db.PublicSourceDao
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.PublicSource
import com.smarted.tv.core.net.Http
import com.smarted.tv.core.util.M3uParser
import com.smarted.tv.core.util.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * **Indexador de fontes públicas** + rotinas de manutenção de links.
 *
 * Duas responsabilidades:
 *
 *  1. **Varredura (scan)**: descobre/valida fontes do catálogo, mede latência,
 *     conta itens indexáveis e grava a saúde de cada fonte.
 *  2. **Manutenção (maintenance)**: amostra links de reprodução, classifica
 *     mortos e mantém uma lista de bloqueio, de modo que o addon só entregue
 *     links que respondem.
 *
 * Rodar em background (WorkManager no app real / coroutine na sessão) com
 * intervalo configurável em Configurações → Indexador.
 */
class Indexer(
    private val sources: SourceRepository,
    private val publicSourceDao: PublicSourceDao,
    private val http: Http,
) {

    data class ScanReport(
        val startedAt: Long = System.currentTimeMillis(),
        val finishedAt: Long = 0L,
        val checked: Int = 0,
        val healthy: Int = 0,
        val dead: Int = 0,
        val itemsIndexed: Int = 0,
        val details: List<SourceStatus> = emptyList(),
        val error: String? = null,
    )

    data class SourceStatus(
        val id: String,
        val name: String,
        val ok: Boolean,
        val latencyMs: Long,
        val items: Int,
        val message: String? = null,
    )

    data class MaintenanceReport(
        val startedAt: Long = System.currentTimeMillis(),
        val finishedAt: Long = 0L,
        val sampled: Int = 0,
        val alive: Int = 0,
        val dead: Int = 0,
        val deadUrls: List<String> = emptyList(),
        val cancelled: Boolean = false,
    )

    private val _scanReport = MutableStateFlow(ScanReport())
    val scanReport: StateFlow<ScanReport> = _scanReport.asStateFlow()

    private val _maintenanceReport = MutableStateFlow(MaintenanceReport())
    val maintenanceReport: StateFlow<MaintenanceReport> = _maintenanceReport.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _status = MutableStateFlow("Indexador ocioso")
    val status: StateFlow<String> = _status.asStateFlow()

    /** URLs consideradas mortas na última manutenção (bloqueio de entrega). */
    private val deadLinks: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

    fun isDead(url: String?): Boolean = url != null && deadLinks.contains(url)

    fun deadLinkCount(): Int = deadLinks.size

    /**
     * Varre todas as fontes habilitadas em paralelo (máx. 4 simultâneas),
     * valida e atualiza a saúde no banco.
     */
    suspend fun scan(limit: Int = 0): ScanReport = withContext(Dispatchers.IO) {
        _running.value = true
        _status.value = "Varrendo fontes públicas…"
        try {
            val all = sources.all().filter { it.enabled }
            val targets = if (limit > 0) all.take(limit) else all
            val statuses = coroutineScope {
                targets.chunked(4).flatMap { chunk ->
                    chunk.map { source -> async { checkSource(source) } }.awaitAll()
                }
            }
            val report = ScanReport(
                finishedAt = System.currentTimeMillis(),
                checked = statuses.size,
                healthy = statuses.count { it.ok },
                dead = statuses.count { !it.ok },
                itemsIndexed = statuses.sumOf { it.items },
                details = statuses.sortedByDescending { it.items },
            )
            _scanReport.value = report
            _status.value = "Varredura concluída · ${report.healthy}/${report.checked} fontes OK · ${report.itemsIndexed} itens"
            report
        } catch (e: Exception) {
            _scanReport.value = ScanReport(finishedAt = System.currentTimeMillis(), error = e.message)
            _status.value = "Falha na varredura: ${e.message}"
            _scanReport.value
        } finally {
            _running.value = false
        }
    }

    private suspend fun checkSource(source: PublicSource): SourceStatus {
        val started = System.currentTimeMillis()
        if (source.format == com.smarted.tv.core.model.SourceFormat.XMLTV) {
            return try {
                val probe = http.probe(source.url)
                publicSourceDao.markHealth(source.id, probe.ok, started, probe.error)
                SourceStatus(source.id, source.name, probe.ok, probe.latencyMs, 0, probe.error)
            } catch (e: Exception) {
                publicSourceDao.markHealth(source.id, false, started, e.message)
                SourceStatus(source.id, source.name, false, 0, 0, e.message)
            }
        }
        return try {
            val body = withTimeoutOrNull(45_000L) {
                http.getText(source.url, cacheable = false, timeoutSeconds = 30)
            } ?: throw IllegalStateException("timeout")
            val parsed = M3uParser.parse(body, addonId = "indexer", defaultKind = source.kind, limit = 20_000)
            val latency = System.currentTimeMillis() - started
            val ok = parsed.items.isNotEmpty()
            publicSourceDao.markHealth(source.id, ok, started, if (ok) null else "lista vazia")
            SourceStatus(
                id = source.id,
                name = source.name,
                ok = ok,
                latencyMs = latency,
                items = parsed.items.size,
                message = if (ok) null else "lista vazia",
            )
        } catch (e: Exception) {
            publicSourceDao.markHealth(source.id, false, started, e.message)
            SourceStatus(source.id, source.name, false, 0, 0, e.message)
        }
    }

    /**
     * Manutenção de links: amostra N itens do catálogo já carregado, faz probe
     * em paralelo e marca os mortos. Itens mortos deixam de ser entregues.
     */
    suspend fun maintain(items: List<MediaItem>, sampleSize: Int = 40, concurrency: Int = 6): MaintenanceReport =
        withContext(Dispatchers.IO) {
            _running.value = true
            _status.value = "Verificando integridade dos links…"
            val start = System.currentTimeMillis()
            val sample = if (items.size <= sampleSize) items else items.shuffled().take(sampleSize)
            var alive = 0
            var dead = 0
            val deadUrls = java.util.Collections.synchronizedList(ArrayList<String>())
            try {
                coroutineScope {
                    sample.chunked(concurrency).forEach { chunk ->
                        chunk.map { item ->
                            async {
                                val url = item.streamUrl ?: return@async
                                val probe = http.probe(url, M3uParser.ItemHeaderStore.get(url))
                                if (probe.ok) {
                                    alive++
                                    deadLinks.remove(url)
                                } else {
                                    dead++
                                    deadUrls.add(url)
                                    deadLinks.add(url)
                                }
                            }
                        }.awaitAll()
                    }
                }
                val report = MaintenanceReport(
                    startedAt = start,
                    finishedAt = System.currentTimeMillis(),
                    sampled = sample.size,
                    alive = alive,
                    dead = dead,
                    deadUrls = deadUrls.toList(),
                )
                _maintenanceReport.value = report
                _status.value = "Manutenção: $alive ativos, $dead mortos de ${sample.size} amostrados"
                report
            } catch (e: Exception) {
                MaintenanceReport(
                    startedAt = start,
                    finishedAt = System.currentTimeMillis(),
                    sampled = sample.size,
                    alive = alive,
                    dead = dead,
                    deadUrls = deadUrls.toList(),
                    cancelled = true,
                )
            } finally {
                _running.value = false
            }
        }

    /**
     * Rotina combinada usada na inicialização (quando habilitada nas configs):
     * hidrata cache → varre fontes → manutenção leve.
     */
    suspend fun runRoutine(trigger: String = "startup"): Pair<ScanReport, MaintenanceReport?> {
        val scan = scan()
        return scan to null
    }

    /** Filtra itens mortos de uma lista (usado pelo addon antes de entregar). */
    fun filterAlive(items: List<MediaItem>): List<MediaItem> =
        if (deadLinks.isEmpty()) items else items.filter { !isDead(it.streamUrl) }

    /** Estatística textual para a tela de Configurações. */
    fun summarize(): String {
        val report = _scanReport.value
        return if (report.checked == 0) {
            "Nunca executado"
        } else {
            val age = (System.currentTimeMillis() - report.finishedAt) / 60_000
            "${report.healthy}/${report.checked} fontes · ${report.itemsIndexed} itens · há ${age}min"
        }
    }

    companion object {
        /** Fontes públicas conhecidas que o indexador pode descobrir automaticamente. */
        val DISCOVERY_CANDIDATES = listOf(
            "https://iptv-org.github.io/iptv/countries/br.m3u",
            "https://iptv-org.github.io/iptv/languages/por.m3u",
            "https://iptv-org.github.io/iptv/categories/news.m3u",
            "https://iptv-org.github.io/iptv/categories/sports.m3u",
            "https://iptv-org.github.io/iptv/categories/movies.m3u",
            "https://iptv-org.github.io/iptv/categories/documentary.m3u",
            "https://iptv-org.github.io/iptv/categories/music.m3u",
            "https://iptv-org.github.io/iptv/categories/religious.m3u",
        )

        fun describe(url: String): String {
            val file = url.substringAfterLast('/').substringBeforeLast('.')
            if (file.isBlank()) return TextUtils.ellipsize(url, 48)
            return when {
                url.contains("/countries/br") -> "Brasil · canais"
                url.contains("/languages/por") -> "Português · canais"
                url.contains("categories/") -> file.replaceFirstChar { it.uppercase() } + " · canais"
                else -> file
            }
        }
    }
}
