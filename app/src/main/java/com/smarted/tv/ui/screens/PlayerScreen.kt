@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smarted.tv.ui.screens

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.SurfaceView
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.composed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.smarted.tv.R
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.player.VlcEngine
import com.smarted.tv.core.util.TextUtils
import com.smarted.tv.ui.components.EdBadge
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdPill
import com.smarted.tv.ui.components.ThinProgress
import com.smarted.tv.ui.components.edInteraction
import com.smarted.tv.ui.state.PlayerViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * Player de vídeo de tela cheia.
 *
 * Motor principal: **libVLC** (`VlcEngine`) — o mesmo conjunto de codecs do VLC
 * de desktop e reconexão automática, o que resolve a maioria dos streams
 * públicos brasileiros que travavam no ExoPlayer. Se o aparelho não tiver a
 * ABI, cai automaticamente para o ExoPlayer sem erro visível.
 *
 * Os controles mudam conforme o tipo, como nos apps de streaming:
 *
 *  - **Ao vivo**: badge AO VIVO, resolução, zap (canal anterior/próximo),
 *    áudio, legendas e proporção. Sem barra de tempo — não há o que buscar.
 *  - **Filmes/Séries**: linha do tempo navegável (◀ ▶ pulam 10s/30s, segurar
 *    acelera), tempo atual/total, capítulos/faixas, áudio e legendas.
 *
 * Todo o controle é pensado para D-pad: qualquer tecla acorda o HUD, ENTER
 * dá play/pause, VOLTAR fecha.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    item: MediaItem,
    onBack: () -> Unit,
    onPreviousChannel: (() -> Unit)? = null,
    onNextChannel: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = Ed.palette
    val type = Ed.type

    var hudVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var resolution by remember { mutableStateOf<String?>(null) }
    var audioTracks by remember { mutableStateOf<List<String>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<String>>(emptyList()) }
    var audioIndex by remember { mutableStateOf(0) }

    // ---- motor principal: VLC ----
    val vlc = remember { VlcEngine(context) }
    // ExoPlayer só é criado se o VLC não puder subir neste aparelho.
    val exo = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(com.smarted.tv.core.net.Http.DEFAULT_UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
        val dataSource = DefaultDataSource.Factory(context, httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
            .apply { playWhenReady = true }
    }
    val usingVlc = vlc.available

    LaunchedEffect(item.ref.key) { viewModel.prepare(item) }

    DisposableEffect(vlc) {
        vlc.onStatus = { status ->
            playing = status == VlcEngine.Status.PLAYING || status == VlcEngine.Status.BUFFERING
        }
        vlc.onTime = { pos, len ->
            position = pos
            if (len > 0) duration = len
        }
        vlc.onVideoSize = { w, h -> resolution = describeResolution(w, h) }
        vlc.onTracks = { audio, subs, currentAudio, _ ->
            audioTracks = audio
            subtitleTracks = subs
            audioIndex = audio.indexOfFirst { it == audio.getOrNull(audioIndex) }.coerceAtLeast(0)
            if (currentAudio >= 0) audioIndex = currentAudio
        }
        onDispose {
            vlc.detach()
            vlc.onStatus = null
            vlc.onTime = null
            vlc.release()
        }
    }

    LaunchedEffect(state.url, usingVlc) {
        val url = state.url ?: return@LaunchedEffect
        if (usingVlc) {
            vlc.play(url, state.headers)
        } else {
            val media = ExoMediaItem.Builder()
                .setUri(url)
                .apply {
                    if (url.contains(".m3u8", true)) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                    if (url.contains(".mpd", true)) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                }
                .build()
            exo.setMediaItem(media)
            exo.prepare()
            exo.play()
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!usingVlc) playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!usingVlc) duration = exo.duration.coerceAtLeast(0L)
            }
        }
        exo.addListener(listener)
        onDispose {
            viewModel.saveProgress(exo.currentPosition, exo.duration.coerceAtLeast(0L))
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(usingVlc) {
        while (true) {
            if (!usingVlc) {
                position = exo.currentPosition
                duration = exo.duration.coerceAtLeast(0L)
            }
            kotlinx.coroutines.delay(500L)
        }
    }

    // HUD some sozinho depois de 4s sem interação durante a reprodução.
    LaunchedEffect(hudVisible, playing) {
        if (hudVisible && playing) {
            kotlinx.coroutines.delay(4_000L)
            hudVisible = false
        }
    }

    BackHandler { onBack() }

    val isLive = state.isLive || item.kind == MediaKind.LIVE

    fun togglePlay() {
        if (usingVlc) vlc.togglePlayPause() else if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seek(delta: Long) {
        if (usingVlc) vlc.seekBy(delta) else exo.seekTo((exo.currentPosition + delta).coerceAtLeast(0L))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (usingVlc) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        isFocusable = true
                        requestFocus()
                        vlc.attach(this)
                        vlc.replayIfPending()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        player = exo
                        isFocusable = true
                        requestFocus()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Qualquer tecla de navegação reexibe o HUD
        Box(
            Modifier
                .fillMaxSize()
                .wakeOnKey { hudVisible = true }
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            togglePlay(); true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (usingVlc) vlc.play(state.url ?: "", state.headers) else exo.play(); true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            if (usingVlc) vlc.togglePlayPause() else exo.pause(); true
                        }
                        // zap de canais: ◀ ▶ trocam de canal no ao vivo
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isLive) onPreviousChannel?.invoke() else seek(-10_000L)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isLive) onNextChannel?.invoke() else seek(30_000L)
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                            onNextChannel?.invoke(); true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                            onPreviousChannel?.invoke(); true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (hudVisible) onBack() else hudVisible = true
                            true
                        }
                        else -> false
                    }
                },
        )

        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.78f),
                            0.30f to Color.Transparent,
                            0.62f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.90f),
                        ),
                    ),
            ) {
                // ---------- topo: título, badges, motor ----------
                Row(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.title.ifBlank { item.title },
                            style = type.title,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.subtitle?.let {
                            Text(
                                text = it,
                                style = type.caption,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                    }
                    if (isLive) LiveBadge()
                    (resolution ?: item.quality)?.let { EdBadge(it, Color.White) }
                    if (!usingVlc) EdBadge(stringResource(R.string.player_compatibility), Color.White.copy(alpha = 0.7f))
                }

                Box(Modifier.weight(1f))

                // ---------- base: controles por tipo de mídia ----------
                Column(
                    Modifier.fillMaxWidth().padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (isLive) {
                        LiveControls(
                            playing = playing,
                            onTogglePlay = { togglePlay() },
                            sources = state.sources.size,
                            sourceIndex = state.selectedSource,
                            onNextSource = {
                                viewModel.selectSource((state.selectedSource + 1) % state.sources.size.coerceAtLeast(1))
                            },
                            onPreviousChannel = onPreviousChannel,
                            onNextChannel = onNextChannel,
                            audioLabel = audioLabel(audioTracks, audioIndex),
                            onCycleAudio = {
                                if (usingVlc && audioTracks.size > 1) {
                                    val next = (audioIndex + 1) % audioTracks.size
                                    audioIndex = next
                                    vlc.selectAudio(next)
                                }
                            },
                            subtitleLabel = if (subtitleTracks.isEmpty()) "Legendas" else "Legendas (${subtitleTracks.size})",
                            onCycleSubtitle = {
                                if (usingVlc && subtitleTracks.isNotEmpty()) {
                                    val current = vlc.currentSubtitle()
                                    val next = subtitleTracks.indices.firstOrNull { it != current } ?: -1
                                    vlc.selectSubtitle(next)
                                }
                            },
                            onBack = onBack,
                        )
                    } else {
                        CinemaControls(
                            playing = playing,
                            position = position,
                            duration = duration,
                            onTogglePlay = { togglePlay() },
                            onSeek = { seek(it) },
                            onSeekTo = { fraction ->
                                val total = duration.coerceAtLeast(1L)
                                val target = (total * fraction.coerceIn(0f, 1f)).toLong()
                                if (usingVlc) vlc.seekTo(target) else exo.seekTo(target)
                            },
                            audioLabel = audioLabel(audioTracks, audioIndex),
                            onCycleAudio = {
                                if (usingVlc && audioTracks.size > 1) {
                                    val next = (audioIndex + 1) % audioTracks.size
                                    audioIndex = next
                                    vlc.selectAudio(next)
                                }
                            },
                            subtitleLabel = if (subtitleTracks.isEmpty()) "Legendas" else "Legendas (${subtitleTracks.size})",
                            onCycleSubtitle = {
                                if (usingVlc && subtitleTracks.isNotEmpty()) {
                                    val current = vlc.currentSubtitle()
                                    val next = subtitleTracks.indices.firstOrNull { it != current } ?: -1
                                    vlc.selectSubtitle(next)
                                }
                            },
                            onBack = onBack,
                        )
                    }
                }
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(color = palette.accent, strokeWidth = 3.dp)
                    Text(
                        text = stringResource(R.string.player_resolving),
                        style = type.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }

        state.error?.let { message ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.86f)), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(40.dp),
                ) {
                    Icon(EdIcons.warning, contentDescription = null, tint = palette.warn, modifier = Modifier.size(34.dp))
                    Text(message, style = type.body, color = Color.White, maxLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EdPill(label = "Tentar de novo", onClick = { viewModel.prepare(item) }, icon = EdIcons.refresh)
                        EdPill(label = "Voltar", onClick = onBack)
                    }
                }
            }
        }
    }
}

/** Selo AO VIVO com o ponto vermelho pulsante (não usa o acento branco). */
@Composable
private fun LiveBadge() {
    val palette = Ed.palette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(palette.bad.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(Color.White, RoundedCornerShape(percent = 50)))
        Text(
            text = stringResource(R.string.player_live_badge),
            style = Ed.type.eyebrow.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

/** Controles de TV ao vivo: zap, áudio, legendas, servidor. Sem timeline. */
@Composable
private fun LiveControls(
    playing: Boolean,
    onTogglePlay: () -> Unit,
    sources: Int,
    sourceIndex: Int,
    onNextSource: () -> Unit,
    onPreviousChannel: (() -> Unit)?,
    onNextChannel: (() -> Unit)?,
    audioLabel: String,
    onCycleAudio: () -> Unit,
    subtitleLabel: String,
    onCycleSubtitle: () -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).background(Ed.palette.bad, RoundedCornerShape(percent = 50)))
            Text(
                text = stringResource(R.string.player_live_hint),
                style = Ed.type.caption,
                color = Color.White.copy(alpha = 0.82f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            onPreviousChannel?.let {
                EdPill(label = stringResource(R.string.player_channel_previous), onClick = it, icon = EdIcons.rewind)
            }
            PlayPauseButton(playing = playing, onClick = onTogglePlay)
            onNextChannel?.let {
                EdPill(label = stringResource(R.string.player_channel_next), onClick = it, icon = EdIcons.fastForward)
            }
            EdPill(label = audioLabel, onClick = onCycleAudio, icon = EdIcons.volume)
            EdPill(label = subtitleLabel, onClick = onCycleSubtitle, icon = EdIcons.subtitles)
            if (sources > 1) {
                EdPill(
                    label = "Servidor ${sourceIndex + 1}/$sources",
                    onClick = onNextSource,
                    icon = EdIcons.bolt,
                )
            }
            Spacer(Modifier.weight(1f))
            EdPill(label = stringResource(R.string.action_exit), onClick = onBack, icon = EdIcons.close)
        }
    }
}

/** Controles de cinema: linha do tempo estilo streaming + faixas. */
@Composable
private fun CinemaControls(
    playing: Boolean,
    position: Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekTo: (Float) -> Unit,
    audioLabel: String,
    onCycleAudio: () -> Unit,
    subtitleLabel: String,
    onCycleSubtitle: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Linha do tempo: trilha fina + cabeça de reprodução + marcações de capítulo
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.22f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(palette.accent),
            )
            // marcadores de capítulo a cada 10% (navegação rápida no D-pad)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(9) {
                    Box(Modifier.width(2.dp).height(8.dp).background(Color.White.copy(alpha = 0.28f)))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(TextUtils.formatClock(position), style = type.caption, color = Color.White.copy(alpha = 0.85f))
            Text(
                text = if (duration > 0) TextUtils.formatClock(duration) else "--:--",
                style = type.caption,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            EdPill(label = stringResource(R.string.player_back_10), onClick = { onSeek(-10_000L) }, icon = EdIcons.rewind)
            PlayPauseButton(playing = playing, onClick = onTogglePlay)
            EdPill(label = stringResource(R.string.player_forward_30), onClick = { onSeek(30_000L) }, icon = EdIcons.fastForward)
            EdPill(label = stringResource(R.string.player_start), onClick = { onSeekTo(0f) })
            EdPill(label = "Cap. 25%", onClick = { onSeekTo(0.25f) })
            EdPill(label = "Cap. 50%", onClick = { onSeekTo(0.5f) })
            EdPill(label = "Cap. 75%", onClick = { onSeekTo(0.75f) })
            EdPill(label = audioLabel, onClick = onCycleAudio, icon = EdIcons.volume)
            EdPill(label = subtitleLabel, onClick = onCycleSubtitle, icon = EdIcons.subtitles)
            Spacer(Modifier.weight(1f))
            EdPill(label = stringResource(R.string.action_exit), onClick = onBack, icon = EdIcons.close)
        }
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    val palette = Ed.palette
    val interaction = edInteraction(onClick = onClick)
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(shape)
            .background(if (interaction.isFocused) palette.accent else Color.White, shape)
            .then(
                if (interaction.isFocused) {
                    Modifier.border(2.dp, Color.White, shape)
                } else {
                    Modifier
                },
            )
            .then(interaction.modifier)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (playing) EdIcons.pause else EdIcons.play,
            contentDescription = null,
            tint = if (interaction.isFocused) palette.onAccent else Color.Black,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = if (playing) "Pausar" else "Reproduzir",
            style = Ed.type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (interaction.isFocused) palette.onAccent else Color.Black,
        )
    }
}

/** "1080p · 50 fps" a partir do tamanho de vídeo entregue pelo VLC. */
private fun describeResolution(width: Int, height: Int): String {
    val label = when {
        height >= 2000 -> "4K"
        height >= 1000 -> "1080p"
        height >= 700 -> "720p"
        height >= 500 -> "576p"
        height >= 400 -> "480p"
        else -> "${width}x$height"
    }
    return label
}

private fun audioLabel(tracks: List<String>, index: Int): String = when {
    tracks.isEmpty() -> "Áudio"
    tracks.size == 1 -> "Áudio: ${tracks.first().take(14)}"
    else -> "Áudio ${index + 1}/${tracks.size}"
}

/** Mantém o HUD visível ao primeiro toque de tecla (como os players de TV). */
private fun Modifier.wakeOnKey(show: () -> Unit): Modifier = composed {
    this.then(
        onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) show()
            false
        },
    )
}

/**
 * Player do YouTube (trailer) — WebView isolada, sem anúncios quando possível.
 * Mantido separado do player principal: trailer não é conteúdo do catálogo.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    videoId: String,
    onClose: () -> Unit,
    noAds: Boolean = true,
) {
    val context = LocalContext.current
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    val origin = if (noAds) "https://www.youtube-nocookie.com" else "https://www.youtube.com"
                    loadUrl("$origin/embed/$videoId?autoplay=1&rel=0&modestbranding=1&playsinline=1")
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            EdPill(label = stringResource(R.string.action_close), onClick = onClose, icon = EdIcons.close)
        }
    }
}
