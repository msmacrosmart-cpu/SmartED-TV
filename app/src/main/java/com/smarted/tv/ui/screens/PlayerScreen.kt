@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smarted.tv.ui.screens

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.border
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.util.TextUtils
import com.smarted.tv.ui.components.EdBadge
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdPill
import com.smarted.tv.ui.components.ThinProgress
import com.smarted.tv.ui.components.edInteraction
import com.smarted.tv.ui.state.PlayerViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * Player de vídeo com `ExoPlayer` (HLS/DASH/MP4) e controles próprios na tela,
 * desenhados para D-pad: qualquer tecla de direção acorda o HUD, ENTER
 * dá play/pause, voltar fecha.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    item: MediaItem,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = Ed.palette
    val type = Ed.type

    var hudVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }

    val player = remember {
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

    LaunchedEffect(item.ref.key) { viewModel.prepare(item) }

    LaunchedEffect(state.url) {
        val url = state.url ?: return@LaunchedEffect
        val media = ExoMediaItem.Builder()
            .setUri(url)
            .apply {
                if (url.contains(".m3u8", true)) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                if (url.contains(".mpd", true)) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
            }
            .build()
        player.setMediaItem(media)
        player.prepare()
        player.play()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose {
            viewModel.saveProgress(player.currentPosition, player.duration.coerceAtLeast(0L))
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            buffered = player.bufferedPosition
            kotlinx.coroutines.delay(500L)
        }
    }

    BackHandler { onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                val view = PlayerView(ctx)
                view.useController = false
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                view.setShutterBackgroundColor(android.graphics.Color.BLACK)
                view.player = player
                view.isFocusable = true
                view.requestFocus()
                view
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Qualquer tecla de navegação reexibe o HUD
        Box(
            Modifier
                .fillMaxSize()
                .wakeOnKey { hudVisible = true }
                .androidxKeyHandler { key ->
                    when (key) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (player.isPlaying) player.pause() else player.play()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L)); true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            player.seekTo(player.currentPosition + 30_000); true
                        }
                        KeyEvent.KEYCODE_BACK -> { onBack(); true }
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
                            0f to Color.Black.copy(alpha = 0.75f),
                            0.35f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
            ) {
                // Topo: título + qualidade + AO VIVO
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
                    if (state.isLive) EdBadge("AO VIVO", palette.bad, filled = true)
                    item.quality?.let { EdBadge(it, Color.White) }
                }

                Box(Modifier.weight(1f))

                // Barra inferior de progresso e ações
                Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (state.isLive) {
                        LiveProgressHint()
                    } else {
                        ThinProgress(
                            fraction = if (duration > 0) position.toFloat() / duration else 0f,
                            height = 4.dp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(TextUtils.formatClock(position), style = type.caption, color = Color.White.copy(alpha = 0.8f))
                            Text(TextUtils.formatClock(duration), style = type.caption, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayPauseButton(playing = playing) {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                        EdPill(label = "−10s", onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L)) })
                        EdPill(label = "+30s", onClick = { player.seekTo(player.currentPosition + 30_000) })
                        if (state.sources.size > 1) {
                            EdPill(
                                label = "Servidor ${state.selectedSource + 1}/${state.sources.size}",
                                onClick = { viewModel.selectSource((state.selectedSource + 1) % state.sources.size) },
                                icon = EdIcons.bolt,
                            )
                        }
                        Box(Modifier.weight(1f))
                        EdPill(label = "Sair", onClick = onBack, icon = EdIcons.close)
                    }
                }
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(color = palette.accent, strokeWidth = 3.dp)
                    Text(
                        text = "Resolvendo link…",
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
                    Modifier.border(3.dp, Color.White, shape)
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

@Composable
private fun LiveProgressHint() {
    val palette = Ed.palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(7.dp).background(palette.bad, RoundedCornerShape(percent = 50)))
        Text(
            text = "Transmissão ao vivo",
            style = Ed.type.caption,
            color = Color.White.copy(alpha = 0.82f),
        )
    }
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

private fun Modifier.androidxKeyHandler(handler: (Int) -> Boolean): Modifier = composed {
    this.then(
        onKeyEvent { event ->
            val native = event.nativeKeyEvent
            if (native.action == KeyEvent.ACTION_DOWN) handler(native.keyCode) else false
        },
    )
}

/**
 * Player do YouTube **sem anúncios**: WebView com a embed oficial em modo
 * `rel=0&modestbranding=1` e bloqueio das requisições de anúncio via
 * `shouldInterceptRequest`. O conteúdo é renderizado sem o wrapper de anúncios
 * do app do YouTube, o que é o máximo que a plataforma permite sem SDK oficial.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    videoId: String,
    onClose: () -> Unit,
    noAds: Boolean = true,
) {
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = WebSettings.getDefaultUserAgent(ctx)
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                        ): android.webkit.WebResourceResponse? {
                            if (!noAds) return null
                            val url = request?.url?.toString() ?: return null
                            val blocked = AD_HOSTS.any { url.contains(it) }
                            return if (blocked) {
                                android.webkit.WebResourceResponse(
                                    "text/plain",
                                    "utf-8",
                                    java.io.ByteArrayInputStream(ByteArray(0)),
                                )
                            } else {
                                null
                            }
                        }
                    }
                    val embed = "https://www.youtube-nocookie.com/embed/$videoId" +
                        "?autoplay=1&rel=0&modestbranding=1&playsinline=1&iv_load_policy=3&fs=1"
                    loadUrl(embed)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            EdPill(label = "Fechar", onClick = onClose, icon = EdIcons.close)
        }
    }
}

private val AD_HOSTS = listOf(
    "doubleclick.net",
    "googleadservices.com",
    "googlesyndication.com",
    "google-analytics.com",
    "ads.youtube.com",
    "static.doubleclick.net",
    "youtube.com/ptracking",
    "youtube.com/api/stats/ads",
    "youtube.com/pagead/",
)
