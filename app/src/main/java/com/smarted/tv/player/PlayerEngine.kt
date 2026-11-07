package com.smarted.tv.player

import androidx.compose.ui.platform.LocalContext

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.smarted.tv.core.model.PlaybackTarget
import com.smarted.tv.core.model.UserPreferences
import com.smarted.tv.core.sources.StreamRules
import com.smarted.tv.data.net.Http
import kotlinx.coroutines.delay

data class PlayerStatus(
    val ready: Boolean = false,
    val playing: Boolean = false,
    val ended: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val error: String = "",
)

@OptIn(UnstableApi::class)
@Composable
fun rememberVideoPlayer(
    target: PlaybackTarget,
    muted: Boolean,
    startPosition: Long = 0,
    preferences: UserPreferences = UserPreferences(),
): ExoPlayer {
    val context = LocalContext.current
    val player = remember(target.url, muted, preferences) {
        val factory = OkHttpDataSource.Factory(Http.client)
            .setDefaultRequestProperties(target.headers)
            .setUserAgent(target.headers["User-Agent"] ?: Http.USER_AGENT)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // ao vivo: buffer curto para trocar de canal sem engasgo; VOD: padrão.
                    .setBufferDurationsMs(if (target.isLive) 4_000 else 8_000, if (target.isLive) 12_000 else 30_000, 1_000, 2_000)
                    .build()
            )
            .setSeekBackIncrementMs(preferences.seekBackSeconds * 1000L)
            .setSeekForwardIncrementMs(preferences.seekForwardSeconds * 1000L)
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage(preferences.audioLanguage.takeIf(String::isNotBlank))
                    .setPreferredTextLanguage(preferences.subtitleLanguage.takeIf(String::isNotBlank))
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !preferences.subtitlesEnabled)
                    .build()
                volume = if (muted) 0f else 1f
                val builder = ExoMediaItem.Builder().setUri(android.net.Uri.parse(target.url))
                StreamRules.mimeFor(target.url)?.let { builder.setMimeType(it) }
                val subtitles = target.subtitles.map {
                    ExoMediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(it.url))
                        .setMimeType(if (it.url.substringBefore('?').endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage(it.language)
                        .setId(it.label.ifBlank { it.language })
                        .setSelectionFlags(if (it.language.startsWith("pt")) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                }
                builder.setSubtitleConfigurations(subtitles)
                setMediaItem(builder.build())
                if (startPosition > 0) seekTo(startPosition)
                playWhenReady = true
                prepare()
            }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        var resume = player.playWhenReady
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { resume = player.playWhenReady; player.pause() }
                Lifecycle.Event.ON_START -> if (resume) player.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }
    return player
}

@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: ExoPlayer, modifier: Modifier = Modifier, subtitleSizePercent: Int = 100, showController: Boolean = false) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = showController
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player
                subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f * subtitleSizePercent / 100f)
            }
        },
        update = {
            it.player = player
            it.useController = showController
            it.subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f * subtitleSizePercent / 100f)
        },
        onRelease = { it.player = null },
    )
}

@Composable
fun playerStatus(player: ExoPlayer): State<PlayerStatus> {
    val status = remember(player) { mutableStateOf(PlayerStatus()) }
    DisposableEffect(player) {
        fun update() {
            status.value = PlayerStatus(
                ready = player.playbackState == Player.STATE_READY,
                playing = player.isPlaying,
                ended = player.playbackState == Player.STATE_ENDED,
                position = player.currentPosition.coerceAtLeast(0),
                duration = player.duration.coerceAtLeast(0),
            )
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = update()
            override fun onIsPlayingChanged(isPlaying: Boolean) = update()
            override fun onPlayerError(error: PlaybackException) {
                status.value = status.value.copy(
                    ready = false, playing = false,
                    error = "Não foi possível reproduzir esta fonte (${error.errorCodeName}). Tente outro canal/fonte ou verifique a conexão.",
                )
            }
        }
        player.addListener(listener)
        update()
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(500)
            status.value = status.value.copy(
                position = player.currentPosition.coerceAtLeast(0),
                duration = player.duration.coerceAtLeast(0),
                playing = player.isPlaying,
            )
        }
    }
    return status
}
