package com.smarted.tv.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import com.smarted.tv.core.model.*
import com.smarted.tv.core.playback.ResolvedPlayback
import com.smarted.tv.player.*
import com.smarted.tv.ui.components.*
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.theme.TvColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class PlayRequest(val item: MediaItem, val video: Video?)

@Composable
fun PlayerScreen(request: PlayRequest, vm: AppViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val data by vm.data.collectAsState()
    var resolved by remember(request) { mutableStateOf<ResolvedPlayback?>(null) }
    var error by remember(request) { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var source by remember(resolved) { mutableIntStateOf(0) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var tracksOpen by remember { mutableStateOf(false) }
    var externalPrompt by remember { mutableStateOf(false) }
    val addons = data.addons
    LaunchedEffect(request, retry, addons) {
        error = ""; resolved = null
        try { resolved = vm.graph.resolver.resolve(request.item, request.video, addons) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Não foi possível obter uma fonte." }
    }
    DisposableEffect(view) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("player")) {
        val stream = resolved?.streams?.getOrNull(source)
        if (stream?.direct == true) {
            val progress = remember(request) { vm.graph.repository.progress(request.item, request.item.playbackId(request.video)) }
            val player = rememberVideoPlayer(stream, false, progress?.takeIf { it.durationMs == 0L || it.positionMs < it.durationMs * .94 }?.positionMs ?: 0)
            val status by playerStatus(player)
            VideoSurface(player, Modifier.matchParentSize())
            LaunchedEffect(player, request) {
                while (true) { delay(5_000); vm.saveProgress(request, player.currentPosition, player.duration.coerceAtLeast(0)) }
            }
            DisposableEffect(player) {
                onDispose {
                    // The graph survives the player composition; only the bounded progress snapshot is retained.
                    val position = player.currentPosition
                    val duration = player.duration.coerceAtLeast(0)
                    vm.saveProgress(request, position, duration)
                }
            }
            if (!status.ready && status.error.isBlank()) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            PlayerControls(request, status, player, { sourcesOpen = !sourcesOpen }, { tracksOpen = !tracksOpen }, onClose)
            if (status.error.isNotBlank()) EmptyState("Fonte indisponível", status.error, Modifier.align(Alignment.Center).widthIn(max = 600.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TvAction("Tentar novamente", { retry++ }); TvAction("Escolher fonte", { sourcesOpen = true }); TvAction("Voltar", onClose) }
            }
            if (tracksOpen) TrackPanel(player) { tracksOpen = false }
        } else Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
            if (resolved == null && error.isBlank()) CircularProgressIndicator(color = Color.White)
            else EmptyState(if (error.isNotBlank()) "Não foi possível reproduzir" else "Preparando transmissão", error.ifBlank { "Buscando fontes HTTP no addon…" }, Modifier.widthIn(max = 650.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvAction("Tentar novamente", { retry++ }, primary = true)
                    if (resolved?.streams?.any { it.externalUrl != null || it.ytId != null } == true) TvAction("Continuar no provedor", { externalPrompt = true })
                    TvAction("Voltar", onClose)
                }
            }
        }
        if (sourcesOpen) Panel(Modifier.align(Alignment.CenterEnd).width(360.dp).padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("Fontes disponíveis", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            resolved?.streams?.forEachIndexed { index, stream ->
                Choice(stream.label + if (!stream.direct) " · externa/indisponível" else "", source == index, { source = index; sourcesOpen = false }, Modifier.fillMaxWidth().padding(bottom = 6.dp))
            }
            TvAction("Fechar fontes", { sourcesOpen = false })
        }
        if (externalPrompt) Panel(Modifier.align(Alignment.Center).widthIn(max = 600.dp)) {
            Text("Abrir aplicativo externo?", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Notice("Você sairá do SmartED. O conteúdo e a sessão serão gerenciados pelo provedor externo.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvAction("Abrir no navegador", {
                    val selected = resolved?.streams?.getOrNull(source)
                    val url = selected?.externalUrl ?: selected?.ytId?.let { "https://www.youtube.com/watch?v=$it" }
                    try { if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else error = "O provedor não informou um link externo seguro." }
                    catch (_: ActivityNotFoundException) { error = "Este aparelho não tem navegador ou aplicativo para abrir o link." }
                    externalPrompt = false
                }, primary = true)
                TvAction("Cancelar", { externalPrompt = false })
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerControls(request: PlayRequest, status: PlayerStatus, player: androidx.media3.exoplayer.ExoPlayer, onSources: () -> Unit, onTracks: () -> Unit, onClose: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(visible) { if (visible) focus.requestFocus() }
    LaunchedEffect(status.playing, visible) { if (visible && status.playing) { delay(8_000); visible = false } }
    Column(Modifier.fillMaxSize().background(if (visible) Color(0x66000000) else Color.Transparent).padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (visible) {
                Column(Modifier.weight(1f)) {
                    Text(request.item.name, color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text(request.video?.title ?: if (request.item.scope == Scope.CHANNELS) "AO VIVO" else "Filme", color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                BackAction(onClose, "Voltar")
            } else TvAction("Controles", { visible = true }, compact = true)
        }
        if (visible) Column(Modifier.fillMaxWidth().smokedGlass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (request.item.scope != Scope.CHANNELS && status.duration > 0) {
                var seeking by remember { mutableStateOf<Float?>(null) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(formatTime(status.position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(value = seeking ?: (status.position.toFloat() / status.duration).coerceIn(0f, 1f), onValueChange = { seeking = it },
                        onValueChangeFinished = { seeking?.let { player.seekTo((it * status.duration).toLong()) }; seeking = null }, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF666666), activeTickColor = Color.White))
                    Text(formatTime(status.duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TvAction(if (status.playing) "Pausar" else "Reproduzir", { if (player.isPlaying) player.pause() else player.play() }, icon = if (status.playing) Icons.Default.Pause else Icons.Default.PlayArrow, primary = true, focusRequester = focus)
                if (request.item.scope != Scope.CHANNELS) {
                    TvAction("−10 s", { player.seekBack() }, compact = true)
                    TvAction("+30 s", { player.seekForward() }, compact = true)
                }
                TvAction("Fontes", onSources, compact = true)
                TvAction("Áudio e legendas", onTracks, compact = true)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun BoxScope.TrackPanel(player: androidx.media3.exoplayer.ExoPlayer, onClose: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    val tracks = remember(revision, player) { player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO || it.type == C.TRACK_TYPE_TEXT } }
    Panel(Modifier.align(Alignment.Center).widthIn(max = 560.dp).padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Áudio e legendas", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Notice("Faixas incorporadas e legendas fornecidas pelo addon.")
        if (tracks.isEmpty()) Notice("Esta fonte não informou faixas alternativas.")
        tracks.forEach { group ->
            for (index in 0 until group.length) {
                val format = group.getTrackFormat(index)
                val label = "${if (group.type == C.TRACK_TYPE_AUDIO) "Áudio" else "Legenda"} · ${format.label ?: format.language ?: "Faixa ${index + 1}"}"
                Choice(label, group.isTrackSelected(index), {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index)).build()
                    revision++
                }, Modifier.fillMaxWidth().padding(bottom = 6.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvAction("Sem legendas", { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT)).build(); revision++ }, compact = true)
            TvAction("Fechar", onClose, compact = true)
        }
    }
}
private fun formatTime(ms: Long): String { val seconds = ms / 1000; return "%02d:%02d".format(seconds / 60, seconds % 60) }
