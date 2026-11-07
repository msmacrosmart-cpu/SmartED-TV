package com.smarted.tv.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.smarted.tv.core.model.BrowseQuery
import com.smarted.tv.core.model.Episode
import com.smarted.tv.core.model.LiveChannel
import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.Programme
import com.smarted.tv.core.model.SortDirection
import com.smarted.tv.core.model.SortMode
import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.model.VodEntry
import com.smarted.tv.core.text.TextNormalizer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repositório local (estilo Kodi/Elementum): SQLite puro, SQL escrito à mão — sem codegen,
 * para o build ser determinístico. Toda chamada é síncrona; o chamador decide o dispatcher.
 * Chaves de favoritos/progresso são DERIVADAS (fonte+nome), não rowids: sobrevivem a re-imports.
 */
class IptvStore(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "smarted_tv.db"
        const val DB_VERSION = 1
        const val META_EPG_REFS = "epg_refs"
        private val SORT_COLUMNS = mapOf(
            SortMode.NAME to "norm_name",
            SortMode.ADDED to "added_ms",
            SortMode.RATING to "rating",
            SortMode.YEAR to "year",
        )
        private const val CHANNEL_SELECT = "id, source_id, fav_key, num, name, group_name, logo, url, headers, epg_key, epg_channel"
        private const val VOD_SELECT = "id, source_id, fav_key, kind, ext_key, name, category, year, rating, added_ms, poster, backdrop, plot, url, runtime, cast, genre, country, language, imdb"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(false) // re-import apaga por fonte; integridade é por chave derivada.
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE sources(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL, kind TEXT NOT NULL, url TEXT NOT NULL,
                username TEXT NOT NULL DEFAULT '', password TEXT NOT NULL DEFAULT '',
                epg_url TEXT NOT NULL DEFAULT '', enabled INTEGER NOT NULL DEFAULT 1,
                auto_update_minutes INTEGER NOT NULL DEFAULT 720,
                last_refresh_ms INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT '', error TEXT NOT NULL DEFAULT '',
                repo_parent TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL(
            """CREATE TABLE channels(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_id INTEGER NOT NULL,
                fav_key TEXT NOT NULL UNIQUE,
                num INTEGER, name TEXT NOT NULL, norm_name TEXT NOT NULL,
                group_name TEXT NOT NULL DEFAULT '', norm_group TEXT NOT NULL DEFAULT '',
                logo TEXT, url TEXT NOT NULL, headers TEXT NOT NULL DEFAULT '',
                epg_key TEXT NOT NULL, epg_channel TEXT, epg_conf INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX ch_src ON channels(source_id)")
        db.execSQL("CREATE INDEX ch_group ON channels(source_id, norm_group)")
        db.execSQL("CREATE INDEX ch_norm ON channels(norm_name)")
        db.execSQL(
            """CREATE TABLE programmes(
                epg_channel TEXT NOT NULL, start_ms INTEGER NOT NULL, stop_ms INTEGER NOT NULL,
                title TEXT NOT NULL, descr TEXT NOT NULL DEFAULT '', cat TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(epg_channel, start_ms)) WITHOUT ROWID"""
        )
        db.execSQL(
            """CREATE TABLE vod(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_id INTEGER NOT NULL,
                fav_key TEXT NOT NULL UNIQUE,
                kind TEXT NOT NULL, ext_key TEXT NOT NULL,
                name TEXT NOT NULL, norm_name TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT '', norm_category TEXT NOT NULL DEFAULT '',
                year INTEGER, rating REAL, added_ms INTEGER NOT NULL DEFAULT 0,
                poster TEXT, backdrop TEXT, plot TEXT NOT NULL DEFAULT '',
                url TEXT NOT NULL DEFAULT '', runtime INTEGER, cast TEXT NOT NULL DEFAULT '',
                genre TEXT NOT NULL DEFAULT '', country TEXT NOT NULL DEFAULT '',
                language TEXT NOT NULL DEFAULT '', imdb TEXT NOT NULL DEFAULT '',
                seasons INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX vod_cat ON vod(kind, norm_category)")
        db.execSQL("CREATE INDEX vod_added ON vod(kind, added_ms)")
        db.execSQL("CREATE INDEX vod_norm ON vod(norm_name)")
        db.execSQL(
            """CREATE TABLE episodes(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                vod_id INTEGER NOT NULL, season INTEGER NOT NULL, episode_num INTEGER NOT NULL,
                title TEXT NOT NULL DEFAULT '', plot TEXT NOT NULL DEFAULT '', thumb TEXT,
                url TEXT NOT NULL, duration INTEGER, ext TEXT NOT NULL DEFAULT 'mp4')"""
        )
        db.execSQL("CREATE INDEX eps_vod ON episodes(vod_id, season, episode_num)")
        db.execSQL(
            """CREATE TABLE favorites(kind TEXT NOT NULL, fav_key TEXT NOT NULL, title TEXT NOT NULL DEFAULT '',
                added_ms INTEGER NOT NULL, PRIMARY KEY(kind, fav_key)) WITHOUT ROWID"""
        )
        db.execSQL(
            """CREATE TABLE progress(fav_key TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL DEFAULT '',
                position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL,
                kind TEXT NOT NULL DEFAULT '', url TEXT NOT NULL DEFAULT '', is_live INTEGER NOT NULL DEFAULT 0) WITHOUT ROWID"""
        )
        db.execSQL("CREATE TABLE meta(key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL DEFAULT '') WITHOUT ROWID")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Sem migração de dados ainda: recriar é aceitável (fontes re-sincronizam na abertura).
        for (table in listOf("sources", "channels", "programmes", "vod", "episodes", "favorites", "progress", "meta")) {
            db.execSQL("DROP TABLE IF EXISTS $table")
        }
        onCreate(db)
    }

    // ---------------------------------------------------------------- fontes

    fun insertSource(source: ListSource): Long =
        writableDatabase.insert("sources", null, contentValuesForSource(source))

    fun updateSource(source: ListSource) {
        writableDatabase.update("sources", contentValuesForSource(source), "id=?", arrayOf(source.id.toString()))
    }

    private fun contentValuesForSource(source: ListSource) = ContentValues().apply {
        put("name", source.name)
        put("kind", source.kind.name)
        put("url", source.url)
        put("username", source.username)
        put("password", source.password)
        put("epg_url", source.epgUrl)
        put("enabled", if (source.enabled) 1 else 0)
        put("auto_update_minutes", source.autoUpdateMinutes)
        put("last_refresh_ms", source.lastRefreshMs)
        put("status", source.status)
        put("error", source.error)
    }

    fun listSources(): List<ListSource> = readableDatabase.rawQuery(SOURCE_SELECT + " ORDER BY id", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.toSource()) }
    }

    fun sourceById(id: Long): ListSource? =
        readableDatabase.rawQuery("$SOURCE_SELECT WHERE id=?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToNext()) cursor.toSource() else null
        }

    fun touchSource(id: Long, status: String, error: String, refreshMs: Long) {
        writableDatabase.execSQL("UPDATE sources SET status=?, error=?, last_refresh_ms=? WHERE id=?", arrayOf(status, error, refreshMs, id))
    }

    fun deleteSource(id: Long) = withTransaction(writableDatabase) {
        writableDatabase.delete("channels", "source_id=?", arrayOf(id.toString()))
        writableDatabase.delete("vod", "source_id=?", arrayOf(id.toString()))
        writableDatabase.delete("sources", "id=?", arrayOf(id.toString()))
    }

    fun repoChildren(parentId: Long): List<ListSource> = readableDatabase.rawQuery(
        SOURCE_SELECT + " WHERE repo_parent=? ORDER BY id", arrayOf(parentId.toString()),
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toSource()) } }

    /** Remove a fonte e, se for repositório, todas as fontes filhas (listas + índices de conteúdo). */
    fun deleteSourceCascade(id: Long) = withTransaction(writableDatabase) {
        val ids = ArrayList<String>()
        readableDatabase.rawQuery("SELECT id FROM sources WHERE id=? OR repo_parent=?", arrayOf(id.toString(), id.toString())).use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0).toString()
        }
        for (sourceId in ids) {
            writableDatabase.delete("channels", "source_id=?", arrayOf(sourceId))
            writableDatabase.delete("vod", "source_id=?", arrayOf(sourceId))
            writableDatabase.delete("sources", "id=?", arrayOf(sourceId))
        }
        // programas órfãos: mantidos; a janela de import limpa na próxima sincronização de EPG.
    }

    fun channelCount(sourceId: Long): Int = scalar("SELECT COUNT(*) FROM channels WHERE source_id=?", arrayOf(sourceId.toString()))
    fun vodCount(sourceId: Long): Int = scalar("SELECT COUNT(*) FROM vod WHERE source_id=?", arrayOf(sourceId.toString()))
    fun programmeCount(): Int = scalar("SELECT COUNT(*) FROM programmes", null)

    fun replaceRepoChildren(repoKey: String, children: List<ListSource>) = withTransaction(writableDatabase) {
        val db = writableDatabase
        val ids = ArrayList<Long>()
        db.rawQuery("SELECT id FROM sources WHERE repo_parent=?", arrayOf(repoKey)).use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        for (id in ids) {
            db.delete("channels", "source_id=?", arrayOf(id.toString()))
            db.delete("vod", "source_id=?", arrayOf(id.toString()))
            db.delete("sources", "id=?", arrayOf(id.toString()))
        }
        for (child in children) {
            val values = contentValuesForSource(child).apply { put("repo_parent", repoKey) }
            db.insert("sources", null, values)
        }
    }

    private fun Cursor.toSource() = ListSource(
        id = getLong(0), name = getString(1), kind = runCatching { SourceKind.valueOf(getString(2)) }.getOrDefault(SourceKind.M3U),
        url = getString(3), username = getString(4) ?: "", password = getString(5) ?: "", epgUrl = getString(6) ?: "",
        enabled = getInt(7) != 0, autoUpdateMinutes = getInt(8), lastRefreshMs = getLong(9),
        status = getString(10) ?: "", error = getString(11) ?: "",
    )

    // ------------------------------------------------------------- canais

    data class ImportedChannel(
        val favKey: String,
        val number: Int?,
        val name: String,
        val group: String,
        val logo: String?,
        val url: String,
        val headers: Map<String, String>,
        val epgKey: String,
    )

    fun replaceChannels(sourceId: Long, channels: List<ImportedChannel>) = withTransaction(writableDatabase) {
        val db = writableDatabase
        db.delete("channels", "source_id=?", arrayOf(sourceId.toString()))
        for (channel in channels) {
            db.insert(
                "channels", null, ContentValues().apply {
                    put("source_id", sourceId)
                    put("fav_key", channel.favKey)
                    if (channel.number != null) put("num", channel.number) else putNull("num")
                    put("name", channel.name)
                    put("norm_name", TextNormalizer.normalize(channel.name))
                    put("group_name", channel.group)
                    put("norm_group", TextNormalizer.categoryKey(channel.group))
                    put("logo", channel.logo)
                    put("url", channel.url)
                    put("headers", headersToJson(channel.headers))
                    put("epg_key", channel.epgKey)
                }
            )
        }
        // favoritos desta fonte que sumiram da lista são limpos (órfãos), preservando os vivos
        db.execSQL(
            """DELETE FROM favorites WHERE kind='channel' AND fav_key LIKE ?
               AND fav_key NOT IN (SELECT fav_key FROM channels WHERE source_id=?)""",
            arrayOf("ch:$sourceId:%", sourceId.toString()),
        )
    }

    fun channelGroups(sourceId: Long?): List<Pair<String, Int>> = stringRows(
        if (sourceId == null) {
            "SELECT group_name, COUNT(*) FROM channels WHERE group_name<>'' GROUP BY norm_group ORDER BY COUNT(*) DESC, MIN(norm_name)"
        } else {
            "SELECT group_name, COUNT(*) FROM channels WHERE source_id=? AND group_name<>'' GROUP BY norm_group ORDER BY COUNT(*) DESC, MIN(norm_name)"
        },
        sourceId?.let { arrayOf(it.toString()) },
    ).mapNotNull { row -> (row[0] as? String)?.let { it to (row[1]?.toIntOrNull() ?: 0) } }

    fun channels(sourceId: Long?, group: String?, search: String?, limit: Int, offset: Int, favoritesOnly: Boolean): List<LiveChannel> {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        if (sourceId != null) { where += "source_id=?"; args += sourceId.toString() }
        if (!group.isNullOrBlank() && group != BrowseQuery.ALL && group != BrowseQuery.FAVORITES) {
            where += "norm_group=?"; args += TextNormalizer.categoryKey(group)
        }
        if (favoritesOnly) where += "EXISTS(SELECT 1 FROM favorites f WHERE f.kind='channel' AND f.fav_key=channels.fav_key)"
        if (!search.isNullOrBlank()) {
            for (token in TextNormalizer.normalize(search).split(' ').filter(String::isNotEmpty).take(6)) {
                where += "norm_name LIKE ?"; args += "%$token%"
            }
        }
        val sql = """
            SELECT $CHANNEL_SELECT FROM channels ${if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")}
            ORDER BY CASE WHEN num IS NULL THEN 1 ELSE 0 END, num, norm_name
            LIMIT ? OFFSET ?
        """.trimIndent()
        return readableDatabase.rawQuery(sql, (args + listOf(limit.toString(), offset.toString())).toTypedArray()).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toChannel()) }
        }
    }

    fun countChannels(sourceId: Long?, group: String?, search: String?, favoritesOnly: Boolean): Int {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        if (sourceId != null) { where += "source_id=?"; args += sourceId.toString() }
        if (!group.isNullOrBlank() && group != BrowseQuery.ALL && group != BrowseQuery.FAVORITES) {
            where += "norm_group=?"; args += TextNormalizer.categoryKey(group)
        }
        if (favoritesOnly) where += "EXISTS(SELECT 1 FROM favorites f WHERE f.kind='channel' AND f.fav_key=channels.fav_key)"
        if (!search.isNullOrBlank()) {
            for (token in TextNormalizer.normalize(search).split(' ').filter(String::isNotEmpty).take(6)) {
                where += "norm_name LIKE ?"; args += "%$token%"
            }
        }
        return scalar("SELECT COUNT(*) FROM channels ${if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")}", args.toTypedArray())
    }

    fun channelById(id: Long): LiveChannel? = readableDatabase.rawQuery(
        "SELECT $CHANNEL_SELECT FROM channels WHERE id=?", arrayOf(id.toString())
    ).use { cursor -> if (cursor.moveToNext()) cursor.toChannel() else null }

    fun channelsByIds(ids: List<Long>): List<LiveChannel> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        return readableDatabase.rawQuery(
            "SELECT $CHANNEL_SELECT FROM channels WHERE id IN ($placeholders) ORDER BY CASE WHEN num IS NULL THEN 1 ELSE 0 END, num, norm_name",
            ids.map { it.toString() }.toTypedArray(),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toChannel()) } }
    }

    private fun Cursor.toChannel() = LiveChannel(
        id = getLong(0), sourceId = getLong(1), favKey = getString(2) ?: "",
        number = if (isNull(3)) null else getInt(3),
        name = getString(4) ?: "", groupName = getString(5) ?: "",
        logoUrl = getString(6)?.takeIf(String::isNotBlank),
        streamUrl = getString(7) ?: "", tvgId = getString(9)?.takeIf(String::isNotBlank),
        headers = parseHeaders(getString(8)), epgKey = getString(9) ?: "",
    )

    /** (favKey, epgKey, name) para o pareador de EPG rodar sobre a lista inteira. */
    fun epgTargets(): List<Triple<String, String, String>> =
        stringRows("SELECT fav_key, epg_key, name FROM channels", null)
            .map { Triple(it[0] as String, it[1] as String, it[2] as String) }

    /** Vínculos atuais (favKey → EpgLink) para a auto-cura do pareamento. */
    fun currentEpgLinks(): Map<String, com.smarted.tv.core.model.EpgLink> = readableDatabase.rawQuery(
        "SELECT fav_key, epg_channel, epg_conf FROM channels WHERE epg_channel IS NOT NULL", null,
    ).use { cursor ->
        buildMap {
            while (cursor.moveToNext()) {
                put(cursor.getString(0), com.smarted.tv.core.model.EpgLink(cursor.getString(0), cursor.getString(1), cursor.getInt(2)))
            }
        }
    }

    data class EpgLinkByFavKey(val favKey: String, val epgChannel: String, val confidence: Int)

    fun applyEpgLinks(links: List<EpgLinkByFavKey>) = withTransaction(writableDatabase) {
        val db = writableDatabase
        db.delete("channels", "epg_channel IS NOT NULL", null)
        for (link in links) {
            db.execSQL("UPDATE channels SET epg_channel=?, epg_conf=? WHERE fav_key=?", arrayOf(link.epgChannel, link.confidence, link.favKey))
        }
    }

    /** canal id → epg_channel dos canais da página visível. */
    fun epgKeysFor(channelIds: List<Long>): Map<Long, String> {
        if (channelIds.isEmpty()) return emptyMap()
        val placeholders = channelIds.joinToString(",") { "?" }
        return readableDatabase.rawQuery(
            "SELECT id, epg_channel FROM channels WHERE id IN ($placeholders) AND epg_channel IS NOT NULL",
            channelIds.map { it.toString() }.toTypedArray(),
        ).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getLong(0), cursor.getString(1)) }
        }
    }

    // ---------------------------------------------------------------- EPG

    fun replaceProgrammes(programmes: List<Programme>, refs: Map<String, List<String>>) = withTransaction(writableDatabase) {
        val db = writableDatabase
        db.delete("programmes", null, null)
        for (programme in programmes) {
            db.insertWithOnConflict(
                "programmes", null,
                ContentValues().apply {
                    put("epg_channel", programme.epgChannel)
                    put("start_ms", programme.startMs)
                    put("stop_ms", programme.stopMs)
                    put("title", programme.title)
                    put("descr", programme.description)
                    put("cat", programme.category)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        setMeta(META_EPG_REFS, encodeEpgRefs(refs))
    }

    fun loadEpgRefs(): Map<String, List<String>> = getMeta(META_EPG_REFS)?.let(::decodeEpgRefs).orEmpty()

    fun timelineFor(epgChannels: Collection<String>, nowMs: Long, windowEndMs: Long): Map<String, List<Programme>> {
        if (epgChannels.isEmpty()) return emptyMap()
        val placeholders = epgChannels.joinToString(",") { "?" }
        val args = (epgChannels.toList() + listOf(nowMs.toString(), windowEndMs.toString())).toTypedArray()
        return readableDatabase.rawQuery(
            """SELECT epg_channel, start_ms, stop_ms, title, descr, cat FROM programmes
               WHERE epg_channel IN ($placeholders) AND stop_ms >= ? AND start_ms <= ?
               ORDER BY epg_channel, start_ms""",
            args,
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val channel = cursor.getString(0)
                    val programme = Programme(channel, cursor.getLong(1), cursor.getLong(2), cursor.getString(3), cursor.getString(4) ?: "", cursor.getString(5) ?: "")
                    getOrPut(channel) { ArrayList() }.add(programme)
                }
            }
        }
    }

    fun programmeCountFor(epgChannel: String): Int =
        scalar("SELECT COUNT(*) FROM programmes WHERE epg_channel=?", arrayOf(epgChannel))

    fun trimProgrammesBefore(cutoffMs: Long) {
        writableDatabase.delete("programmes", "stop_ms < ?", arrayOf(cutoffMs.toString()))
    }

    // ----------------------------------------------------------------- VOD

    data class ImportedVod(
        val favKey: String,
        val extKey: String,
        val name: String,
        val category: String,
        val year: Int?,
        val rating: Double?,
        val addedMs: Long,
        val poster: String?,
        val backdrop: String?,
        val plot: String,
        val url: String,
        val runtimeMinutes: Int?,
        val cast: List<String>,
        val genre: String,
        val country: String,
        val language: String,
        val imdbId: String,
        val seasons: Int,
    )

    fun replaceVod(sourceId: Long, kind: Tab, entries: List<ImportedVod>) = withTransaction(writableDatabase) {
        val db = writableDatabase
        db.delete("vod", "source_id=? AND kind=?", arrayOf(sourceId.toString(), kind.name))
        for (entry in entries) {
            db.insertWithOnConflict(
                "vod", null,
                ContentValues().apply {
                    put("source_id", sourceId)
                    put("fav_key", entry.favKey)
                    put("kind", kind.name)
                    put("ext_key", entry.extKey)
                    put("name", entry.name)
                    put("norm_name", TextNormalizer.normalize(entry.name))
                    put("category", entry.category)
                    put("norm_category", TextNormalizer.categoryKey(entry.category))
                    if (entry.year != null) put("year", entry.year) else putNull("year")
                    if (entry.rating != null) put("rating", entry.rating) else putNull("rating")
                    put("added_ms", entry.addedMs)
                    put("poster", entry.poster)
                    put("backdrop", entry.backdrop)
                    put("plot", entry.plot)
                    put("url", entry.url)
                    if (entry.runtimeMinutes != null) put("runtime", entry.runtimeMinutes) else putNull("runtime")
                    put("cast", entry.cast.joinToString("|"))
                    put("genre", entry.genre)
                    put("country", entry.country)
                    put("language", entry.language)
                    put("imdb", entry.imdbId)
                    put("seasons", entry.seasons)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
        db.delete("episodes", "vod_id NOT IN (SELECT id FROM vod)", null)
        db.execSQL(
            "DELETE FROM favorites WHERE kind IN ('movie','series') AND fav_key LIKE ? AND fav_key NOT IN (SELECT fav_key FROM vod)",
            arrayOf("vod:$sourceId:%"),
        )
    }

    fun vodCategories(kind: Tab, search: String?): List<Pair<String, Int>> = stringRows(
        "SELECT category, COUNT(*) FROM vod WHERE kind=? AND category<>'' GROUP BY norm_category ORDER BY COUNT(*) DESC, MIN(norm_name)",
        arrayOf(kind.name),
    ).filter { row -> search.isNullOrBlank() || TextNormalizer.searchMatches(search, row[0] as String) }
        .map { (it[0] as String) to (it[1]?.toIntOrNull() ?: 0) }

    fun vodPage(
        kind: Tab,
        category: String?,
        search: String?,
        sort: SortMode,
        direction: SortDirection,
        limit: Int,
        offset: Int,
        favoritesOnly: Boolean,
    ): List<VodEntry> {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        where += "kind=?"; args += kind.name
        if (!category.isNullOrBlank() && category != BrowseQuery.ALL && category != BrowseQuery.FAVORITES) {
            where += "norm_category=?"; args += TextNormalizer.categoryKey(category)
        }
        if (favoritesOnly) where += "EXISTS(SELECT 1 FROM favorites f WHERE f.kind=vod.kind AND f.fav_key=vod.fav_key)"
        if (!search.isNullOrBlank()) {
            for (token in TextNormalizer.normalize(search).split(' ').filter(String::isNotEmpty).take(6)) {
                where += "norm_name LIKE ?"; args += "%$token%"
            }
        }
        val sortColumn = SORT_COLUMNS.getValue(sort)
        val order = if (direction == SortDirection.DESC) "DESC" else "ASC"
        val sql = """
            SELECT $VOD_SELECT FROM vod ${if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")}
            ORDER BY $sortColumn $order, norm_name ASC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return readableDatabase.rawQuery(sql, (args + listOf(limit.toString(), offset.toString())).toTypedArray()).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toVod()) }
        }
    }

    fun countVod(kind: Tab, category: String?, search: String?, favoritesOnly: Boolean): Int {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        where += "kind=?"; args += kind.name
        if (!category.isNullOrBlank() && category != BrowseQuery.ALL && category != BrowseQuery.FAVORITES) {
            where += "norm_category=?"; args += TextNormalizer.categoryKey(category)
        }
        if (favoritesOnly) where += "EXISTS(SELECT 1 FROM favorites f WHERE f.kind=vod.kind AND f.fav_key=vod.fav_key)"
        if (!search.isNullOrBlank()) {
            for (token in TextNormalizer.normalize(search).split(' ').filter(String::isNotEmpty).take(6)) {
                where += "norm_name LIKE ?"; args += "%$token%"
            }
        }
        return scalar("SELECT COUNT(*) FROM vod ${if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")}", args.toTypedArray())
    }

    fun vodById(id: Long): VodEntry? = readableDatabase.rawQuery(
        "SELECT $VOD_SELECT FROM vod WHERE id=?", arrayOf(id.toString()),
    ).use { cursor -> if (cursor.moveToNext()) cursor.toVod() else null }

    fun vodByFavKey(favKey: String): VodEntry? = readableDatabase.rawQuery(
        "SELECT $VOD_SELECT FROM vod WHERE fav_key=?", arrayOf(favKey),
    ).use { cursor -> if (cursor.moveToNext()) cursor.toVod() else null }

    private fun Cursor.toVod() = VodEntry(
        id = getLong(0), sourceId = getLong(1), favKey = getString(2) ?: "",
        kind = runCatching { Tab.valueOf(getString(3)) }.getOrDefault(Tab.MOVIES),
        externalKey = getString(4) ?: "", name = getString(5) ?: "", category = getString(6) ?: "",
        year = if (isNull(7)) null else getInt(7), rating = if (isNull(8)) null else getDouble(8),
        addedMs = getLong(9), posterUrl = getString(10)?.takeIf(String::isNotBlank),
        backdropUrl = getString(11)?.takeIf(String::isNotBlank), plot = getString(12) ?: "",
        streamUrl = getString(13) ?: "", runtimeMinutes = if (isNull(14)) null else getInt(14),
        cast = getString(15).orEmpty().split('|').filter(String::isNotBlank),
        genre = getString(16) ?: "", countryCode = getString(17) ?: "", language = getString(18) ?: "",
        imdbId = getString(19) ?: "",
    )

    data class ImportedEpisode(
        val season: Int, val episode: Int, val title: String, val plot: String,
        val thumb: String?, val url: String, val durationMinutes: Int?, val ext: String,
    )

    fun replaceEpisodes(vodId: Long, episodes: List<ImportedEpisode>) = withTransaction(writableDatabase) {
        val db = writableDatabase
        db.delete("episodes", "vod_id=?", arrayOf(vodId.toString()))
        for (episode in episodes) {
            db.insert(
                "episodes", null, ContentValues().apply {
                    put("vod_id", vodId)
                    put("season", episode.season)
                    put("episode_num", episode.episode)
                    put("title", episode.title)
                    put("plot", episode.plot)
                    put("thumb", episode.thumb)
                    put("url", episode.url)
                    if (episode.durationMinutes != null) put("duration", episode.durationMinutes) else putNull("duration")
                    put("ext", episode.ext)
                }
            )
        }
    }

    fun episodesFor(vodId: Long): List<Episode> = readableDatabase.rawQuery(
        """SELECT id, vod_id, season, episode_num, title, plot, thumb, duration, url, ext
           FROM episodes WHERE vod_id=? ORDER BY season, episode_num""",
        arrayOf(vodId.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    Episode(
                        id = cursor.getLong(0), vodId = cursor.getLong(1),
                        season = cursor.getInt(2), episode = cursor.getInt(3),
                        title = cursor.getString(4) ?: "", plot = cursor.getString(5) ?: "",
                        thumbUrl = cursor.getString(6)?.takeIf(String::isNotBlank),
                        runtimeMinutes = if (cursor.isNull(7)) null else cursor.getInt(7),
                        streamUrl = cursor.getString(8) ?: "", containerExtension = cursor.getString(9) ?: "mp4",
                    )
                )
            }
        }
    }

    // ----------------------------------------------------------- favoritos

    fun favoriteKeys(kind: String): Set<String> = readableDatabase.rawQuery(
        "SELECT fav_key FROM favorites WHERE kind=?", arrayOf(kind),
    ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    /** @return true se virou favorito. */
    fun toggleFavorite(kind: String, favKey: String, title: String): Boolean {
        val db = writableDatabase
        val removed = db.delete("favorites", "kind=? AND fav_key=?", arrayOf(kind, favKey))
        if (removed > 0) return false
        db.insert("favorites", null, ContentValues().apply {
            put("kind", kind); put("fav_key", favKey); put("title", title); put("added_ms", System.currentTimeMillis())
        })
        return true
    }

    fun favoriteChannels(): List<LiveChannel> = readableDatabase.rawQuery(
        """SELECT c.id, c.source_id, c.num, c.name, c.group_name, c.logo, c.url, c.headers, c.epg_key, c.epg_channel
           FROM channels c
           JOIN favorites f ON f.kind='channel' AND f.fav_key=c.fav_key
           ORDER BY CASE WHEN c.num IS NULL THEN 1 ELSE 0 END, c.num, c.norm_name""",
        null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toChannel()) } }

    // ----------------------------------------------------------- progresso

    data class ProgressRecord(
        val favKey: String, val title: String, val positionMs: Long, val durationMs: Long,
        val updatedMs: Long, val kind: String, val url: String, val isLive: Boolean,
    )

    fun saveProgress(record: ProgressRecord) {
        writableDatabase.insertWithOnConflict(
            "progress", null,
            ContentValues().apply {
                put("fav_key", record.favKey); put("title", record.title)
                put("position_ms", record.positionMs); put("duration_ms", record.durationMs)
                put("updated_ms", record.updatedMs); put("kind", record.kind)
                put("url", record.url); put("is_live", if (record.isLive) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun progressFor(favKey: String): ProgressRecord? = readableDatabase.rawQuery(PROGRESS_SELECT + " WHERE fav_key=?", arrayOf(favKey)).use { cursor ->
        if (cursor.moveToNext()) cursor.toProgress() else null
    }

    fun recentProgress(limit: Int = 12): List<ProgressRecord> = readableDatabase.rawQuery(
        "$PROGRESS_SELECT WHERE is_live=0 ORDER BY updated_ms DESC LIMIT ?", arrayOf(limit.toString()),
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toProgress()) } }

    fun clearProgress() { writableDatabase.delete("progress", null, null) }

    // ---------------------------------------------------------------- meta

    fun setMeta(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "meta", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getMeta(key: String): String? =
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(key)).use { cursor ->
            if (cursor.moveToNext()) cursor.getString(0) else null
        }

    /** Reindexa fav_keys/epg após re-import: mantém estrutura, só zera stats. */
    fun recomputeAllEpgLinks(matcher: (List<Triple<String, String, String>>, Map<String, List<String>>) -> List<EpgLinkByFavKey>) {
        val targets = epgTargets()
        val refs = loadEpgRefs()
        applyEpgLinks(matcher(targets, refs))
    }

    fun wipeCatalogs() = withTransaction(writableDatabase) {
        val db = writableDatabase
        db.delete("channels", null, null)
        db.delete("programmes", null, null)
        db.delete("vod", null, null)
        db.delete("episodes", null, null)
        setMeta(META_EPG_REFS, "")
    }

    // ------------------------------------------------------------- helpers

    private fun scalar(sql: String, args: Array<String>?): Int =
        readableDatabase.rawQuery(sql, args).use { cursor -> if (cursor.moveToNext()) cursor.getInt(0) else 0 }

    private fun stringRows(sql: String, args: Array<String>?): List<Array<String?>> =
        readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(Array(cursor.columnCount) { i -> if (cursor.isNull(i)) null else cursor.getString(i) })
                }
            }
        }

    private fun headersToJson(headers: Map<String, String>): String {
        if (headers.isEmpty()) return ""
        val obj = JSONObject()
        headers.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun parseHeaders(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap { obj.keys().forEach { put(it, obj.getString(it)) } }
        }.getOrDefault(emptyMap())
    }

    private fun encodeEpgRefs(refs: Map<String, List<String>>): String {
        val array = JSONArray()
        refs.forEach { (id, names) ->
            val obj = JSONObject()
            obj.put("id", id)
            obj.put("names", JSONArray().apply { names.forEach { put(it) } })
            array.put(obj)
        }
        return array.toString()
    }

    private fun decodeEpgRefs(raw: String): Map<String, List<String>> = runCatching {
        val array = JSONArray(raw)
        buildMap {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val names = obj.optJSONArray("names")
                put(obj.getString("id"), buildList { if (names != null) for (j in 0 until names.length()) add(names.getString(j)) })
            }
        }
    }.getOrDefault(emptyMap())

    private fun withTransaction(db: SQLiteDatabase, block: () -> Unit) {
        db.beginTransaction()
        try {
            block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

private const val SOURCE_SELECT = "SELECT id,name,kind,url,username,password,epg_url,enabled,auto_update_minutes,last_refresh_ms,status,error FROM sources"
private const val PROGRESS_SELECT = "SELECT fav_key, title, position_ms, duration_ms, updated_ms, kind, url, is_live FROM progress"

private fun Cursor.toProgress() = IptvStore.ProgressRecord(
    getString(0), getString(1) ?: "", getLong(2), getLong(3), getLong(4), getString(5) ?: "", getString(6) ?: "", getInt(7) != 0,
)
