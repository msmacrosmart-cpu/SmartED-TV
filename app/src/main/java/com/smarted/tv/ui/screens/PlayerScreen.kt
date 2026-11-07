package com.smarted.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.PlaybackTarget
import kotlinx.coroutines.delay
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.player.VideoSurface
import com.smarted.tv.player.playerStatus
import com.smarted.tv.player.rememberVideoPlayer
import com.smarted.tv.ui.components.TvAction
import com.smarted.tv.ui.state.AppViewModel

/** Item encadeável no player (próximo episódio de série). */
data class QueuedItem(val title: String, val target: PlaybackTarget, val favKey: String)

data class PlayRequest(
    val target: PlaybackTarget,
    val title: String,
    val favKey: String = "",
    val kind: String = "vod",
    val queue: List<QueuedItem> = emptyList(),
    val queueIndex: Int = -1,
) {
    val next: QueuedItem? get() = queue.getOrNull(queueIndex + 1)
}

/**
 * Player em tela cheia: barra/legendas/controle por D-pad (esq/dir ±saltos, OK pausa).
 * Progresso salvo em nomeadas curtas e ao sair. Sem autoplay silencioso: o próximo
 * episódio é uma ação confirmada (estilo Elementum).
 */
@Composable
fun PlayerScreen(
    request: PlayRequest,
    app: AppViewModel,
    onDismiss: () -> Unit,
    onQueue: (PlayRequest) -> Unit,
) {
    val prefs by app.preferences.collectAsState()
    val rootFocus = remember { FocusRequester() }
    var current by remember { mutableStateOf(request) }
    val player = rememberVideoPlayer(current.target, muted = false, preferences = prefs, startPosition = remember(current.favKey) { 0L })
    val status by playerStatus(player)
    var resumeChecked by remember(current.favKey) { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(current.favKey, prefs.resumePlayback) {
        if (resumeChecked) return@LaunchedEffect
        resumeChecked = true
        if (prefs.resumePlayback && !current.target.isLive && current.favKey.isNotBlank()) {
            val progress = runCatching { app.loadProgress(current.favKey) }.getOrNull()
            if (progress != null && progress.durationMs > 5_000 && progress.positionMs in 10_000 until progress.durationMs - 15_000) {
                player.seekTo(progress.positionMs)
            }
        }
    }
    // persiste posição periódica p/ retomar depois
    LaunchedEffect(player) {
        while (true) {
            delay(10_000)
            if (player.isPlaying) app.saveProgress(current.favKey, current.title, player.currentPosition, player.duration, current.kind, current.target.url, current.target.isLive)
        }
    }
    DisposableEffect(player, current) {
        onDispose {
            if (!current.target.isLive) app.saveProgress(current.favKey, current.title, player.currentPosition, player.duration, current.kind, current.target.url, current.target.isLive)
        }
    }
    BackHandler(onBack = onDismiss)

    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag("player-${current.kind}")
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (player.isPlaying) player.pause() else player.play(); true
                    }
                    else -> false
                }
            },
    ) {
        VideoSurface(player, Modifier.fillMaxSize().testTag("player-surface"), subtitleSizePercent = prefs.subtitleSizePercent, showController = false)

        if (!status.ready && status.error.isBlank()) {
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Preparando ${current.title}…", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(if (current.target.isLive) "Sintonizando canal ao vivo" else "Abrindo fonte do repositório", color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (status.error.isNotBlank()) {
            Box(Modifier.fillMaxSize().background(Color(0xEE0A0B0D)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.testTag("player-error")) {
                    Text("Falha na reprodução", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text(status.error, color = Color(0xFFFFC9C9), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvAction("Tentar novamente", { resumeChecked = false; player.prepare() })
                        TvAction("Voltar", onDismiss)
                    }
                }
            }
        }

        val seekHint = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { player.seekTo((player.currentPosition - prefs.seekBackSeconds * 1000L).coerceAtLeast(0)); true }
                Key.DirectionRight -> {
                    val destination = if (player.duration > 0) (player.currentPosition + prefs.seekForwardSeconds * 1000L).coerceAtMost(player.duration) else player.currentPosition + prefs.seekForwardSeconds * 1000L
                    player.seekTo(destination); true
                }
                else -> false
            }
        }
        Column(
            (Modifier.fillMaxWidth().align(Alignment.BottomCenter).then(seekHint))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6050608))))
                .padding(horizontal = 42.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(current.title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (current.target.isLive) {
                    Text("AO VIVO", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier
                        .background(Color(0x33FF3B3B), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                } else {
                    Text(
                        "%01d:%02d / %01d:%02d".format(
                            status.position / 3_600_000, (status.position / 60_000 % 60), status.duration / 3_600_000, (status.duration / 60_000 % 60)
                        ),
                        color = Color.White, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (!current.target.isLive && status.duration > 0) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x40FFFFFF), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth((status.position.toFloat() / status.duration.toFloat()).coerceIn(0f, 1f)).height(4.dp).background(Color.White, RoundedCornerShape(2.dp)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TvAction(if (status.playing) "Pausar" else "Retomar", { if (player.isPlaying) player.pause() else player.play() }, compact = true, modifier = Modifier.testTag("player-toggle").focusRequester(rootFocus))
                if (prefs.subtitlesEnabled) {
                    TvAction("Legendas: on", { app.preferences { it.copy(subtitlesEnabled = false) } }, compact = true)
                } else {
                    TvAction("Legendas: off", { app.preferences { it.copy(subtitlesEnabled = true) } }, compact = true)
                }
                current.next?.let { next ->
                    TvAction("Próximo episódio", {
                        onQueue(current.copy(target = next.target, title = next.title, favKey = next.favKey, queueIndex = current.queueIndex + 1))
                    }, compact = true, modifier = Modifier.testTag("player-next"))
                }
                Spacer(Modifier.weight(1f))
                Text("◀ Voltar fecha · OK alterna play/pause · ←/→ avançam", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

