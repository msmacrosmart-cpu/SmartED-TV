@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smarted.tv.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import android.view.Window
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
import androidx.compose.ui.composed
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.smarted.tv.ui.components.GlassPanel
import com.smarted.tv.ui.components.GlassPill
import com.smarted.tv.ui.components.ThinProgress
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
    var audioLabelText by remember { mutableStateOf("Áudio") }
    var subtitleLabelText by remember { mutableStateOf("Legendas") }

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
        // O engine já devolve os rótulos prontos em português; aqui só
        // sincronizamos a UI quando as faixas mudam de stream.
        vlc.onTracks = { _, _, _, _ ->
            audioLabelText = vlc.audioLabel()
            subtitleLabelText = vlc.subtitleLabel()
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

    // Reforço de tela cheia absoluta. O MainActivity já esconde as barras,
    // mas vários aparelhos (Fire TV, boxes chineses) as trazem de volta assim
    // que a janela ganha foco. Barra visível encolhe a janela, o vout do VLC
    // recebe dimensão menor que a tela e o vídeo "diminui". Reaplicamos o modo
    // imersivo a cada abertura do player.
    val hostView = LocalView.current
    LaunchedEffect(hostView) {
        val window = hostView.context.activityWindow()
        val controller = window?.let { WindowCompat.getInsetsController(it, hostView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
    }

    val isLive = state.isLive || item.kind == MediaKind.LIVE

    fun togglePlay() {
        if (usingVlc) vlc.togglePlayPause() else if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seek(delta: Long) {
        if (usingVlc) vlc.seekBy(delta) else exo.seekTo((exo.currentPosition + delta).coerceAtLeast(0L))
    }

    // Tela cheia absoluta: o Box raiz ocupa todo o espaço disponível e o
    // vídeo é esticado até as bordas (match parent / fill bounds). Nada de
    // letterbox por padrão — quem quiser a proporção original usa o ajuste de
    // proporção nos controles.
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (usingVlc) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        isFocusable = true
                        // MATCH_PARENT real: a superfície é filha do Box raiz
                        // que já está em fillMaxSize, então recebe a tela toda.
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        // O vout do libVLC desenha num viewport próprio e não
                        // herda o tamanho da SurfaceView. Sem setWindowSize ele
                        // usa um padrão minúsculo e o vídeo aparece pequeno no
                        // canto inferior esquerdo — mesmo com tudo em match
                        // parent. attachSurface já informa o tamanho do display;
                        // aqui reaplicamos o tamanho real em pixels assim que o
                        // layout entrega as dimensões (e a cada mudança delas).
                        addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                            if (right - left <= 0 || bottom - top <= 0) return@addOnLayoutChangeListener
                            vlc.updateWindowSize(this)
                        }
                        requestFocus()
                        vlc.attachSurface(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        // FILL: o vídeo ocupa 100% da tela (fill bounds),
                        // mesmo que a proporção da fonte seja diferente.
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
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
            Box(Modifier.fillMaxSize()) {
                // Véu leve: apenas o suficiente para os painéis de vidro terem
                // contraste. Nada de barras pretas de tela cheia.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.55f),
                                0.30f to Color.Transparent,
                                0.70f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.60f),
                            ),
                        ),
                )

                // ---------- topo: título, badges, motor ----------
                GlassPanel(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    padding = 16.dp,
                    spacing = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = state.title.ifBlank { item.title },
                                style = type.title,
                                color = palette.onArt,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            state.subtitle?.let {
                                Text(
                                    text = it,
                                    style = type.caption,
                                    color = palette.onArt.copy(alpha = 0.7f),
                                    maxLines = 1,
                                )
                            }
                        }
                        if (isLive) LiveBadge()
                        (resolution ?: item.quality)?.let { EdBadge(it, palette.onArt) }
                        if (!usingVlc) {
                            EdBadge(stringResource(R.string.player_compatibility), palette.onArt.copy(alpha = 0.7f))
                        }
                    }
                }

                // ---------- base: controles flutuantes por tipo de mídia ----------
                GlassPanel(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    padding = 18.dp,
                    spacing = 12.dp,
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
                            audioLabel = audioLabelText,
                            onCycleAudio = { if (usingVlc) audioLabelText = vlc.cycleAudio() },
                            subtitleLabel = subtitleLabelText,
                            onCycleSubtitle = { if (usingVlc) subtitleLabelText = vlc.cycleSubtitle() },
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
                            audioLabel = audioLabelText,
                            onCycleAudio = { if (usingVlc) audioLabelText = vlc.cycleAudio() },
                            subtitleLabel = subtitleLabelText,
                            onCycleSubtitle = { if (usingVlc) subtitleLabelText = vlc.cycleSubtitle() },
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
                        GlassPill(
                            label = stringResource(R.string.action_retry),
                            onClick = { viewModel.prepare(item) },
                            icon = EdIcons.refresh,
                        )
                        GlassPill(label = stringResource(R.string.action_back), onClick = onBack)
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
                GlassPill(label = stringResource(R.string.player_channel_previous), onClick = it, icon = EdIcons.rewind)
            }
            PlayPauseButton(playing = playing, onClick = onTogglePlay)
            onNextChannel?.let {
                GlassPill(label = stringResource(R.string.player_channel_next), onClick = it, icon = EdIcons.fastForward)
            }
            GlassPill(label = audioLabel, onClick = onCycleAudio, icon = EdIcons.volume)
            GlassPill(label = subtitleLabel, onClick = onCycleSubtitle, icon = EdIcons.subtitles)
            if (sources > 1) {
                GlassPill(
                    label = "Servidor ${sourceIndex + 1}/$sources",
                    onClick = onNextSource,
                    icon = EdIcons.bolt,
                )
            }
            Spacer(Modifier.weight(1f))
            GlassPill(label = stringResource(R.string.action_exit), onClick = onBack, icon = EdIcons.close)
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
            GlassPill(label = stringResource(R.string.player_back_10), onClick = { onSeek(-10_000L) }, icon = EdIcons.rewind)
            PlayPauseButton(playing = playing, onClick = onTogglePlay)
            GlassPill(label = stringResource(R.string.player_forward_30), onClick = { onSeek(30_000L) }, icon = EdIcons.fastForward)
            GlassPill(label = stringResource(R.string.player_start), onClick = { onSeekTo(0f) })
            GlassPill(label = "Cap. 25%", onClick = { onSeekTo(0.25f) })
            GlassPill(label = "Cap. 50%", onClick = { onSeekTo(0.5f) })
            GlassPill(label = "Cap. 75%", onClick = { onSeekTo(0.75f) })
            GlassPill(label = audioLabel, onClick = onCycleAudio, icon = EdIcons.volume)
            GlassPill(label = subtitleLabel, onClick = onCycleSubtitle, icon = EdIcons.subtitles)
            Spacer(Modifier.weight(1f))
            GlassPill(label = stringResource(R.string.action_exit), onClick = onBack, icon = EdIcons.close)
        }
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    GlassPill(
        label = if (playing) {
            stringResource(R.string.action_pause)
        } else {
            stringResource(R.string.action_resume_playback)
        },
        icon = if (playing) EdIcons.pause else EdIcons.play,
        primary = true,
        onClick = onClick,
    )
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
            GlassPill(label = stringResource(R.string.action_close), onClick = onClose, icon = EdIcons.close)
        }
    }
}

/** Sobe a árvore de contexto até a janela da Activity (null fora de uma tela real). */
private fun Context.activityWindow(): Window? {
    var current: Context? = this
    while (current != null) {
        if (current is Activity) return current.window
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}
