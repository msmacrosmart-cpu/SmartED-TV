package com.smarted.tv.core.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.TextureView
import com.smarted.tv.core.net.Http
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Motor de reprodução baseado em libVLC.
 *
 * Por que VLC: streams públicos brasileiros usam HLS com variantes MPEG-TS,
 * HEVC e áudio AC-3 — combinações que o ExoPlayer recusa ou trava. O libVLC
 * traz o mesmo conjunto de codecs do VLC de desktop, além de recuperação
 * automática de conexão, o que reduz muito o "travou e ficou preto" em TV box.
 *
 * A classe isola o ciclo de vida nativo (LibVLC → MediaPlayer → Surface) para
 * que a UI continue 100% declarativa e testável. Toda chamada nativa é
 * protegida: se a ABI do aparelho não tiver suporte, [available] vira `false`
 * e a tela cai para o ExoPlayer sem quebrar.
 */
class VlcEngine(context: Context) {

    enum class Status { IDLE, OPENING, BUFFERING, PLAYING, PAUSED, ENDED, ERROR }

    var onStatus: ((Status) -> Unit)? = null
    var onTime: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onVideoSize: ((width: Int, height: Int) -> Unit)? = null
    var onTracks: ((audio: List<String>, subtitles: List<String>, currentAudio: Int, currentSub: Int) -> Unit)? = null

    /** `true` quando o motor nativo inicializou (ABI suportada + libs extraídas). */
    val available: Boolean get() = libVlc != null

    private val released = AtomicBoolean(false)
    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var attachedView: android.view.View? = null
    private var pendingUrl: String? = null
    private var pendingHeaders: Map<String, String> = emptyMap()

    init {
        libVlc = runCatching {
            LibVLC(context.applicationContext, buildOptions())
        }.getOrNull()
        player = libVlc?.let { vlc ->
            runCatching { MediaPlayer(vlc) }.getOrNull()
        }?.apply { installListeners() }
    }

    private fun buildOptions(): ArrayList<String> = arrayListOf(
        // rede: reconexão agressiva é o que salva listas públicas instáveis
        "--network-caching=1500",
        "--live-caching=1500",
        "--file-caching=500",
        "--sout-mux-caching=1500",
        "--http-reconnect",
        "--http-user-agent=" + Http.DEFAULT_UA,
        // vídeo/áudio
        "--no-drop-late-frames",
        "--no-skip-frames",
        "--audio-time-stretch",
        "--avcodec-hw=any",
        // extras de compatibilidade
        "--no-osd",
        "--no-snapshot-preview",
        "--no-sub-autodetect-file",
        "--stats",
    )

    private fun installListeners() {
        val p = player ?: return
        p.setEventListener { ev ->
            if (released.get()) return@setEventListener
            // `ev.type` é org.videolan.libvlc.MediaPlayer.Event.type (API do VLC),
            // não a KeyEvent do Compose — por isso o parâmetro se chama `ev`.
            when (ev.type) {
                MediaPlayer.Event.Opening -> onStatus?.invoke(Status.OPENING)
                MediaPlayer.Event.Buffering -> onStatus?.invoke(Status.BUFFERING)
                MediaPlayer.Event.Playing -> {
                    onStatus?.invoke(Status.PLAYING)
                    emitTracks()
                }
                MediaPlayer.Event.Paused -> onStatus?.invoke(Status.PAUSED)
                MediaPlayer.Event.Stopped -> onStatus?.invoke(Status.ENDED)
                MediaPlayer.Event.EndReached -> onStatus?.invoke(Status.ENDED)
                MediaPlayer.Event.EncounteredError -> {
                    onStatus?.invoke(Status.ERROR)
                    onError?.invoke("O VLC não conseguiu abrir este stream.")
                }
                MediaPlayer.Event.TimeChanged ->
                    onTime?.invoke(p.time, p.length.coerceAtLeast(0L))
                MediaPlayer.Event.LengthChanged ->
                    onTime?.invoke(p.time, p.length.coerceAtLeast(0L))
                MediaPlayer.Event.Vout -> {
                    val size = p.videoSize
                    if (size != null && size.width > 0) onVideoSize?.invoke(size.width, size.height)
                    emitTracks()
                }
            }
        }
    }

    /** Ações do controle remoto: anexa a superfície de vídeo. */
    fun attach(view: SurfaceView) {
        attachedView = view
        player?.vlcVout?.setVideoView(view)
    }

    fun attach(view: TextureView) {
        attachedView = view
        player?.vlcVout?.setVideoView(view)
    }

    fun detach() {
        runCatching { player?.vlcVout?.setVideoView(null) }
        attachedView = null
    }

    /**
     * Prepara e inicia um stream.
     *
     * @param url endereço do stream
     * @param headers cabeçalhos HTTP do addon (Referer, User-Agent, Cookie)
     */
    fun play(url: String, headers: Map<String, String> = emptyMap()) {
        val p = player ?: run {
            val reason = "VLC indisponível neste aparelho."
            onError?.invoke(reason)
            return
        }
        pendingUrl = url
        pendingHeaders = headers
        if (attachedView == null) return // replay ao anexar a view

        runCatching {
            p.stop()
            val media = Media(libVlc, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=1500")
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")
            if (headers.isNotEmpty()) {
                val referer = headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value
                val agent = headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
                referer?.let { media.addOption(":http-referrer=$it") }
                agent?.let { media.addOption(":http-user-agent=$it") }
                headers.entries
                    .filterNot { it.key.equals("Referer", true) || it.key.equals("User-Agent", true) }
                    .forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) media.addOption(":http-header=$k: $v") }
            }
            p.media = media
            media.release()
            p.play()
        }.onFailure { onError?.invoke(it.message ?: "Falha ao preparar o stream.") }
    }

    /** Reaplica o último URL — usado quando a superfície é recriada. */
    fun replayIfPending() {
        val url = pendingUrl ?: return
        if (player?.isPlaying == true) return
        play(url, pendingHeaders)
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun seekTo(ms: Long) {
        player?.let { if (it.isSeekable) it.time = ms.coerceAtLeast(0L) }
    }

    fun seekBy(deltaMs: Long) {
        val p = player ?: return
        seekTo((p.time + deltaMs).coerceAtLeast(0L))
    }

    /** Áudio/legendas: lista de faixas exposta para os menus do controle. */
    fun audioTracks(): List<MediaPlayer.TrackDescription> =
        runCatching { player?.audioTracks?.filter { it.id >= 0 } ?: emptyList() }.getOrDefault(emptyList())

    fun subtitleTracks(): List<MediaPlayer.TrackDescription> =
        runCatching { player?.spuTracks?.filter { it.id >= 0 } ?: emptyList() }.getOrDefault(emptyList())

    fun selectAudio(id: Int) {
        runCatching { player?.setAudioTrack(id) }
    }

    fun selectSubtitle(id: Int) {
        runCatching { player?.setSpuTrack(id) }
    }

    fun currentAudio(): Int = runCatching { player?.audioTrack ?: -1 }.getOrDefault(-1)
    fun currentSubtitle(): Int = runCatching { player?.spuTrack ?: -1 }.getOrDefault(-1)

    /** Ajuste de proporção (preencher x manter proporção). */
    fun setAspect(fill: Boolean) {
        runCatching { player?.aspectRatio = if (fill) null else null }
        runCatching { player?.scale = if (fill) 0f else 0f }
    }

    private fun emitTracks() {
        onTracks?.invoke(
            audioTracks().map { it.name ?: "Faixa ${it.id}" },
            subtitleTracks().map { it.name ?: "Legenda ${it.id}" },
            currentAudio(),
            currentSubtitle(),
        )
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        runCatching { player?.stop() }
        runCatching { player?.release() }
        runCatching { libVlc?.release() }
        player = null
        libVlc = null
    }
}
