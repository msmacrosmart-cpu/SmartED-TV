package com.smarted.tv.data.system

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.smarted.tv.core.model.PlaybackRequest
import com.smarted.tv.core.model.PlaylistSource
import com.smarted.tv.core.model.SourceType
import com.smarted.tv.core.model.UserPreferences
import com.smarted.tv.data.db.IptvStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Operações de Configurações/sistema sobre o grafo real (fontes, Elementum,
 * progresso, preferências). As telas falam só com esta fachada.
 */
class IptvSystem(private val graph: com.smarted.tv.AppGraph, ctx: Context) {
    private val app = ctx.applicationContext
    private val store: IptvStore get() = graph.store

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e -> report(e.message ?: "Erro interno") },
    )

    private val _sources = MutableStateFlow<List<PlaylistSource>>(emptyList())
    val sources: StateFlow<List<PlaylistSource>> = _sources.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    val preferences: StateFlow<UserPreferences> = graph.preferences
    val elementum = graph.elementum
    /** Fonte em sincronização (id + texto); Busy.isBusy quando != null. */
    val busyFlow = graph.sources.busy

    init {
        reloadSources()
        scope.launch {
            // Qualquer sync (manual ou agendador) atualiza status/erro/contagens da lista.
            graph.sources.revision.collect { reloadSources() }
        }
    }

    fun reloadSources() {
        scope.launch {
            _sources.value = withContext(Dispatchers.IO) {
                runCatching { store.listSources().map { it.toUi() } }.getOrDefault(emptyList())
            }
        }
    }

    // --------------------------------------- pacotes ZIP (instalador estilo Kodi)
    private val repoPackages = com.smarted.tv.data.repo.RepoPackages(app)

    fun zipPackages() = repoPackages.packages()
    fun zipEntries(zipPath: String) = repoPackages.entries(zipPath)
    fun zipCandidates(zipPath: String) = repoPackages.candidates(zipPath)
    suspend fun zipDownload(url: String): String = repoPackages.download(url)

    /** Instala um índice do pacote e o registra como repositório (file://). */
    suspend fun zipInstall(zipPath: String, entryPath: String, types: Map<String, String>): String {
        val (localUrl, name) = repoPackages.install(zipPath, entryPath, types)
        graph.sources.addLocalRepository(name, localUrl)
        reloadSources()
        return name
    }

    // ---------------------------------------------------------------- fontes

    fun add(s: PlaylistSource) = run("Lista adicionada e sincronizada.") {
        when (s.type) {
            SourceType.XTREAM -> graph.sources.addXtream(
                s.name.ifBlank { "Xtream" }, s.url.trim(), s.username.orEmpty().trim(),
                s.password.orEmpty(), s.epgUrl.orEmpty().trim(),
            )
            SourceType.M3U_URL, SourceType.M3U_FILE -> graph.sources.addM3u(
                s.name.ifBlank { "M3U" }, s.url.trim(), s.epgUrl.orEmpty().trim(),
            )
            SourceType.REPOSITORY -> graph.sources.addRepository(
                s.name.ifBlank { "Repositório" }, s.url.trim(),
            )
            SourceType.LOCAL_REPO -> importLocalTree(s)
        }
    }

    /** Pasta local (estilo repositório Kodi): cada .m3u/.m3u8 vira uma fonte M3U. */
    private suspend fun importLocalTree(s: PlaylistSource) = withContext(Dispatchers.IO) {
        val tree = runCatching { Uri.parse(s.url) }.getOrElse { error("URI de pasta inválida.") }
        val root = DocumentFile.fromTreeUri(app, tree) ?: error("Não foi possível abrir a pasta.")
        val files = root.listFiles().filter { f ->
            f.isFile && f.name?.lowercase()?.let { it.endsWith(".m3u") || it.endsWith(".m3u8") } == true
        }.sortedBy { it.name?.lowercase() }
        if (files.isEmpty()) error("Nenhum arquivo .m3u/.m3u8 encontrado na pasta.")
        files.forEach { f ->
            graph.sources.addM3u("${s.name} · ${f.name}", f.uri.toString(), "")
        }
        Unit
    }

    fun remove(id: String) = run("Fonte removida.") {
        val sourceId = id.toLongOrNull() ?: return@run
        withContext(Dispatchers.IO) { graph.sources.remove(sourceId) }
    }

    fun refresh(id: String) = run("Fonte atualizada.") {
        val sourceId = id.toLongOrNull() ?: return@run
        val source = withContext(Dispatchers.IO) { store.sourceById(sourceId) } ?: return@run
        withContext(Dispatchers.IO) { graph.sources.refresh(source) }
        Unit
    }

    fun toggleEnabled(id: String) = run("Fonte pausada/reativada (conteúdo mantido).") {
        val sourceId = id.toLongOrNull() ?: return@run
        val source = withContext(Dispatchers.IO) { store.sourceById(sourceId) } ?: error("Fonte não encontrada.")
        withContext(Dispatchers.IO) { graph.sources.setEnabled(source, !source.enabled) }
        Unit
    }

    fun setInterval(id: String, minutes: Int) = run("Intervalo salvo.") {
        val sourceId = id.toLongOrNull() ?: return@run
        val source = withContext(Dispatchers.IO) { store.sourceById(sourceId) } ?: return@run
        withContext(Dispatchers.IO) { graph.sources.setInterval(source, minutes) }
        Unit
    }

    fun refreshAll() {
        _message.value = "Atualizando todas as fontes…"
        graph.refreshAllNow { msg -> _message.value = msg }
    }

    // ------------------------------------------------------------- Elementum

    fun elementumApply(host: String, portText: String, enabled: Boolean) {
        val port = portText.trim().toIntOrNull()
        if (enabled && (port == null || port !in 1..65535)) {
            report("Porta do Elementum inválida (1-65535).")
            return
        }
        graph.savePreferences {
            it.copy(
                elementumEnabled = enabled,
                elementumHost = host.trim().ifBlank { "127.0.0.1" },
                elementumPort = port ?: it.elementumPort,
            )
        }
        report(if (enabled) "Configurado. Testando conexão…" else "Integração Elementum desativada.")
        graph.checkElementum()
    }

    fun elementumTest() { _message.value = "Verificando Elementum…"; graph.checkElementum() }
    fun elementumUpdate() { _message.value = "Disparando atualização do Elementum…"; graph.triggerElementumUpdate() }

    fun setResumeEnabled(enabled: Boolean) {
        graph.savePreferences { it.copy(resumePlayback = enabled) }
        report(if (enabled) "Retomar reprodução: ligado." else "Retomar reprodução: desligado.")
    }

    // ------------------------------------------------------------- progresso

    /** Posição salva (ms) para retomada, ou null. Respeita a preferência do usuário. */
    suspend fun loadProgress(favKey: String, isLive: Boolean = false): Long? {
        if (favKey.isBlank() || isLive) return null
        if (!graph.preferences.value.resumePlayback) return null
        return withContext(Dispatchers.IO) {
            runCatching { graph.catalog.progressFor(favKey)?.let { it.positionMs } }.getOrNull()
        }
    }

    fun saveProgress(request: PlaybackRequest, positionMs: Long, durationMs: Long) {
        if (request.favKey.isBlank() || request.isLive || positionMs <= 0) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                graph.catalog.saveProgress(
                    request.favKey, request.title, positionMs, durationMs, request.kind,
                    request.url.orEmpty(), false,
                )
            }
        }
    }

    // ------------------------------------------------------------------ util

    private fun run(ok: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                report(ok)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                report(e.message ?: "Falhou (${e.javaClass.simpleName}).")
            } finally {
                reloadSources()
            }
        }
    }

    private fun report(text: String) { _message.value = text }

    private fun com.smarted.tv.core.model.ListSource.toUi() = PlaylistSource(
        id = id.toString(),
        name = name,
        type = when {
            kind == com.smarted.tv.core.model.SourceKind.XTREAM -> SourceType.XTREAM
            kind == com.smarted.tv.core.model.SourceKind.REPOSITORY -> SourceType.REPOSITORY
            url.startsWith("content://") || url.startsWith("file://") -> SourceType.M3U_FILE
            else -> SourceType.M3U_URL
        },
        url = url,
        username = username.ifBlank { null },
        password = password.ifBlank { null },
        epgUrl = epgUrl.ifBlank { null },
        status = status,
        error = error,
        lastRefreshMs = lastRefreshMs,
        enabled = enabled,
        autoUpdateMinutes = autoUpdateMinutes,
    )
}
