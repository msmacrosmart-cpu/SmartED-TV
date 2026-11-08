package com.smarted.tv.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.smarted.tv.core.data.AppGraph
import com.smarted.tv.core.model.DeviceMode
import com.smarted.tv.core.model.PlaybackRequest
import com.smarted.tv.data.net.Http
import com.smarted.tv.ui.components.GlassButton
import com.smarted.tv.ui.theme.LocalDeviceMode
import com.smarted.tv.ui.theme.LocalDimens
import com.smarted.tv.ui.theme.Smoke
import kotlinx.coroutines.delay

private fun buildPlayer(ctx: android.content.Context, headers: Map<String, String>): ExoPlayer {
    val http = OkHttpDataSource.Factory(Http.client)
    if (headers.isNotEmpty()) http.setDefaultRequestProperties(headers.toMutableMap())
    return ExoPlayer.Builder(ctx)
        .setMediaSourceFactory(DefaultMediaSourceFactory(http))
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(8_000, 40_000, 1_500, 4_000)
                .build(),
        )
        .build()
        .apply { playWhenReady = true }
}

/** Pré-visualização ao vivo (coluna 3 de Canais). Debounce de 500 ms ao navegar pela lista. */
@Composable
fun LivePreviewPlayer(
    url: String?,
    headers: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val d = LocalDimens.current
    val ctx = LocalContext.current
    val exo = remember(headers) { buildPlayer(ctx, headers) }
    DisposableEffect(exo) { onDispose { exo.release() } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, exo) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) exo.pause() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(url) {
        if (url == null) {
            exo.stop(); exo.clearMediaItems()
        } else {
            delay(500)
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.play()
        }
    }
    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    isFocusable = false
                    isFocusableInTouchMode = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = exo
                }
            },
        )
        if (url == null) {
            Text(
                "Sem sinal de pré-visualização", Modifier.align(Alignment.Center),
                fontSize = d.textCaption, color = Smoke.TextTertiary,
            )
        }
    }
}

/**
 * Player em tela cheia (Media3 PlayerView com controles; D-pad e toque).
 * Extras do SmartED: headers da fonte, retomada de progresso (VOD) e
 * autoavanço para o próximo episódio quando [PlaybackRequest.next] existe.
 */
@Composable
fun PlayerScreen(request: PlaybackRequest, onClose: () -> Unit) {
    val d = LocalDimens.current
    val isTv = LocalDeviceMode.current == DeviceMode.TV
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)

    var current by remember { mutableStateOf(request) }
    var hasNext by remember { mutableStateOf(request.next != null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (current.url.isNullOrBlank()) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Nenhum stream para \"${current.title}\".\nAdicione uma lista em Configurações.",
                    fontSize = d.textTitle, color = Smoke.TextPrimary, textAlign = TextAlign.Center,
                )
                GlassButton("Voltar", Icons.Rounded.Close, onClose)
            }
        } else {
            val exo = remember(current.url) { buildPlayer(ctx, current.headers) }
            var resumeAtMs by remember(current) { mutableStateOf<Long?>(null) }
            LaunchedEffect(current) {
                resumeAtMs = AppGraph.system.loadProgress(current.favKey, current.isLive)
            }
            // Autoavanço ao terminar: listener vive enquanto o item atual toca.
            DisposableEffect(exo, current) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            AppGraph.system.saveProgress(current, 0, 0)
                            current.next?.let { nextReq ->
                                hasNext = nextReq.next != null
                                current = nextReq
                            }
                        }
                    }
                }
                exo.addListener(listener)
                onDispose {
                    exo.removeListener(listener)
                    if (!current.isLive) {
                        AppGraph.system.saveProgress(current, exo.currentPosition, exo.duration.coerceAtLeast(0L))
                    }
                    exo.release()
                }
            }
            LaunchedEffect(current.url, resumeAtMs) {
                val url = current.url ?: return@LaunchedEffect
                exo.setMediaItem(MediaItem.fromUri(url))
                exo.prepare()
                exo.play()
                resumeAtMs?.let { if (it > 5_000L) exo.seekTo(it) }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    PlayerView(c).apply {
                        useController = true
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        isFocusable = true
                        isFocusableInTouchMode = true
                        this.player = exo
                        post { requestFocus() }
                    }
                },
            )
            if (!isTv) {
                GlassButton(
                    "Fechar", Icons.Rounded.Close, onClose,
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                )
            }
            if (hasNext && current.next != null) {
                GlassButton(
                    "Próximo episódio", Icons.Rounded.SkipNext,
                    {
                        current.next?.let { n ->
                            hasNext = n.next != null
                            current = n
                        }
                    },
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                )
            }
        }
    }
}
