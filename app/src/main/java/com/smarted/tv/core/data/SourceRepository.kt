package com.smarted.tv.core.data

import com.smarted.tv.core.data.db.DbMappers.toDomain
import com.smarted.tv.core.data.db.DbMappers.toEntity
import com.smarted.tv.core.data.db.PublicSourceDao
import com.smarted.tv.core.data.db.PublicSourceEntity
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.PublicSource
import com.smarted.tv.core.model.SourceFormat
import com.smarted.tv.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Repositório de **fontes públicas** (channels/filmes/séries + EPG).
 *
 * Três origens, em ordem de precedência:
 *  1. `assets/catalog/sources.json` — catálogo embutido (offline-first).
 *  2. Catálogo remoto em nuvem (URL configurável) — atualização sem publicar app.
 *  3. Fontes declaradas por addons instalados (incluindo os do repositório ativo).
 *
 * A lista é a base do Indexador: ele varre, valida e mantém os links ativos.
 */
class SourceRepository(
    private val dao: PublicSourceDao,
    private val http: Http,
    private val assets: AssetReader,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Garante que o catálogo embutido exista (idempotente). */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val existing = dao.count()
        val embedded = readEmbedded()
        if (existing == 0) {
            dao.upsertAll(embedded.map { it.toEntity(discoveredFrom = "embedded") })
            return@withContext
        }
        // Acrescenta apenas as fontes novas (preserva escolhas do usuário).
        val known = dao.all().map { it.id }.toHashSet()
        val missing = embedded.filter { it.id !in known }
        if (missing.isNotEmpty()) {
            dao.upsertAll(missing.map { it.toEntity(discoveredFrom = "embedded") })
        }
    }

    suspend fun ensureEpgSource(daoEpg: com.smarted.tv.core.data.db.EpgDao, defaultUrl: String) {
        withContext(Dispatchers.IO) {
            val existing = daoEpg.source(defaultUrl)
            if (existing == null) {
                daoEpg.upsertSource(
                    com.smarted.tv.core.data.db.EpgSourceEntity(
                        url = defaultUrl,
                        label = "EPG Brasil (padrão)",
                        enabled = true,
                        priority = 10,
                        status = "UNKNOWN",
                        fromAddonId = "brazuca.play",
                    ),
                )
            }
        }
    }

    suspend fun all(): List<PublicSource> = withContext(Dispatchers.IO) {
        dao.all().map { it.toDomain() }
    }

    suspend fun enabled(): List<PublicSource> = withContext(Dispatchers.IO) {
        dao.enabled().map { it.toDomain() }
    }

    suspend fun upsert(source: PublicSource) = withContext(Dispatchers.IO) {
        dao.upsert(source.toEntity())
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) { dao.delete(id) }

    suspend fun markHealth(id: String, healthy: Boolean, error: String?) = withContext(Dispatchers.IO) {
        dao.markHealth(id, healthy, System.currentTimeMillis(), error)
    }

    /** Adiciona uma fonte avulsa explorada pelo usuário (ex.: URL de lista M3U). */
    suspend fun addCustom(name: String, url: String, kind: MediaKind): PublicSource =
        withContext(Dispatchers.IO) {
            val format = when {
                url.contains("get.php", true) || url.contains("player_api", true) -> SourceFormat.XTREAM
                url.endsWith(".xml", true) || url.endsWith(".xml.gz", true) -> SourceFormat.XMLTV
                else -> SourceFormat.M3U
            }
            val source = PublicSource(
                id = "custom-" + Integer.toHexString((name + url).hashCode()),
                name = name.ifBlank { "Fonte personalizada" },
                url = url.trim(),
                kind = kind,
                format = format,
                priority = 500,
                enabled = true,
            )
            dao.upsert(source.toEntity(discoveredFrom = "user"))
            source
        }

    /**
     * Atualização remota via nuvem: baixa um catálogo JSON (mesmo formato do
     * asset) e faz merge. Devolve quantas fontes novas entraram.
     */
    suspend fun syncFromCloud(url: String): CloudSyncResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext CloudSyncResult(0, 0, "URL vazia")
        val raw = try {
            http.getText(url, cacheable = false, timeoutSeconds = 30)
        } catch (e: Exception) {
            return@withContext CloudSyncResult(0, 0, e.message ?: "falha ao baixar catálogo")
        }
        val parsed = parseCatalog(raw) ?: return@withContext CloudSyncResult(0, 0, "JSON inválido")
        val known = dao.all().associateBy { it.id }
        var added = 0
        var updated = 0
        val toWrite = ArrayList<PublicSourceEntity>()
        for (src in parsed) {
            val current = known[src.id]
            val entity = src.toEntity(discoveredFrom = current?.discoveredFrom ?: "cloud")
            if (current == null) {
                added++
                toWrite.add(entity)
            } else if (current.url != src.url || current.name != src.name) {
                updated++
                toWrite.add(entity.copy(enabled = current.enabled, priority = current.priority))
            }
        }
        if (toWrite.isNotEmpty()) dao.upsertAll(toWrite)
        CloudSyncResult(added, updated, null)
    }

    private fun readEmbedded(): List<PublicSource> {
        val raw = assets.read("catalog/sources.json") ?: return emptyList()
        return parseCatalog(raw) ?: emptyList()
    }

    /** Parser tolerante do catálogo (aceita também lista simples de URLs). */
    fun parseCatalog(raw: String): List<PublicSource>? {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val arr = root["sources"]?.jsonArray ?: return emptyList()
            arr.mapNotNull { el -> toSource(el.jsonObject) }
        } catch (_: Exception) {
            // fallback: arquivo com uma URL por linha
            val lines = raw.lines().map { it.trim() }.filter { it.startsWith("http") }
            if (lines.isEmpty()) null
            else lines.mapIndexed { index, url ->
                PublicSource(
                    id = "line-" + Integer.toHexString(url.hashCode()),
                    name = url.substringAfterLast('/').ifBlank { "Fonte ${index + 1}" },
                    url = url,
                    kind = MediaKind.LIVE,
                    format = SourceFormat.M3U,
                    priority = 300 + index,
                )
            }
        }
    }

    private fun toSource(o: JsonObject): PublicSource? {
        val url = o["url"]?.jsonPrimitive?.content?.takeIf { it.startsWith("http") } ?: return null
        val name = o["name"]?.jsonPrimitive?.content ?: url.substringAfterLast('/')
        val kind = when (o["kind"]?.jsonPrimitive?.content?.uppercase()) {
            "MOVIE", "MOVIES", "VOD" -> MediaKind.MOVIE
            "SERIES", "SERIE" -> MediaKind.SERIES
            else -> MediaKind.LIVE
        }
        val format = when (o["format"]?.jsonPrimitive?.content?.uppercase()) {
            "XTREAM" -> SourceFormat.XTREAM
            "XMLTV" -> SourceFormat.XMLTV
            "PLUGIN" -> SourceFormat.PLUGIN
            else -> SourceFormat.M3U
        }
        return PublicSource(
            id = o["id"]?.jsonPrimitive?.content ?: ("src-" + Integer.toHexString(url.hashCode())),
            name = name,
            url = url,
            kind = kind,
            format = format,
            group = o["group"]?.jsonPrimitive?.content,
            logo = o["logo"]?.jsonPrimitive?.content,
            enabled = o["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
            priority = o["priority"]?.jsonPrimitive?.intOrNull ?: 100,
        )
    }

    data class CloudSyncResult(val added: Int, val updated: Int, val error: String?)
}

/** Leitor de assets com cache em memória. */
class AssetReader(private val androidAssets: android.content.res.AssetManager) {
    private val cache = HashMap<String, String>()

    fun read(path: String): String? {
        cache[path]?.let { return it }
        return try {
            val text = androidAssets.open(path).bufferedReader().use { it.readText() }
            cache[path] = text
            text
        } catch (_: Exception) {
            null
        }
    }

    fun readLines(path: String): List<String> =
        read(path)?.lines()?.filter { it.isNotBlank() } ?: emptyList()
}

/** Utilidades de JSON compartilhadas (evita repetir try/catch em toda a base). */
internal fun JsonArray?.orEmptyArray(): JsonArray = this ?: JsonArray(emptyList())
