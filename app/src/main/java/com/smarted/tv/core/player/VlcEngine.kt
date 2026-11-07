package com.smarted.tv.core.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import com.smarted.tv.core.net.Http
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Motor de reprodução baseado em libVLC.
 *
 * Por que VLC: streams públicos brasileiros usam HLS com variantes MPEG-TS,
 * HEVC e áudio AC-3 — combinações que o ExoPlayer recusa. O libVLC traz o
 * mesmo conjunto de codecs do VLC de desktop e reconexão automática, o que
 * reduz muito o "travou e ficou preto" em TV box.
 *
 * A classe esconde a API nativa (que devolve arrays e códigos inteiros) atrás
 * de um conjunto pequeno de operações. Toda chamada nativa é protegida: se a
 * ABI do aparelho não suportar, [available] vira `false` e a tela cai para o
 * ExoPlayer sem quebrar.
 *
 * Compatível com `org.videolan.android:libvlc-all:3.5.x` (VLC 3 para Android).
 */
class VlcEngine(context: Context) {

    enum class Status { IDLE, OPENING, BUFFERING, PLAYING, PAUSED, ENDED, ERROR }

    var onStatus: ((Status) -> Unit)? = null
    var onTime: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onVideoSize: ((width: Int, height: Int) -> Unit)? = null
    var onTracks: ((audio: List<String>, subtitles: List<String>, currentAudio: Int, currentSub: Int) -> Unit)? = null

    /** `true` quando o motor nativo inicializou (ABI suportada + libs extraídas). */
    val available: Boolean get() = libVlc != null && player != null

    private val released = AtomicBoolean(false)
    private val libVlc: LibVLC?
    private val player: MediaPlayer?

    private var attached = false
    private var pendingUrl: String? = null
    private var pendingHeaders: Map<String, String> = emptyMap()

    /** Ids reais das faixas (o índice do array não é o id do VLC). */
    private var audioIds: List<Int> = emptyList()
    private var subtitleIds: List<Int> = emptyList()
    private var audioNames: List<String> = emptyList()
    private var subtitleNames: List<String> = emptyList()

    init {
        libVlc = runCatching { LibVLC(context.applicationContext, buildOptions()) }.getOrNull()
        player = libVlc?.let { vlc -> runCatching { MediaPlayer(vlc) }.getOrNull() }
        player?.let { runCatching { it.setEventListener(::onNativeEvent) } }
    }

    private fun buildOptions(): ArrayList<String> = arrayListOf(
        // Rede: reconexão agressiva é o que salva listas públicas instáveis.
        "--network-caching=1500",
        "--live-caching=1500",
        "--file-caching=500",
        "--http-reconnect",
        "--http-user-agent=" + Http.DEFAULT_UA,
        // Vídeo/áudio
        "--no-drop-late-frames",
        "--no-skip-frames",
        "--audio-time-stretch",
        "--avcodec-hw=any",
        // Compatibilidade
        "--no-osd",
        "--no-sub-autodetect-file",
    )

    private fun onNativeEvent(ev: MediaPlayer.Event) {
        if (released.get()) return
        val p = player ?: return
        // `ev.type` é org.videolan.libvlc.MediaPlayer.Event.type — API do VLC,
        // não a KeyEvent do Compose.
        when (ev.type) {
            MediaPlayer.Event.Opening -> onStatus?.invoke(Status.OPENING)
            MediaPlayer.Event.Buffering -> onStatus?.invoke(Status.BUFFERING)
            MediaPlayer.Event.Playing -> {
                onStatus?.invoke(Status.PLAYING)
                refreshTracks()
                emitVideoSize()
            }
            MediaPlayer.Event.Paused -> onStatus?.invoke(Status.PAUSED)
            MediaPlayer.Event.Stopped -> onStatus?.invoke(Status.ENDED)
            MediaPlayer.Event.EndReached -> onStatus?.invoke(Status.ENDED)
            MediaPlayer.Event.EncounteredError -> {
                onStatus?.invoke(Status.ERROR)
                onError?.invoke("O VLC não conseguiu abrir este stream.")
            }
            MediaPlayer.Event.TimeChanged, MediaPlayer.Event.LengthChanged ->
                runCatching { onTime?.invoke(p.time, p.length.coerceAtLeast(0L)) }

            MediaPlayer.Event.Vout -> {
                emitVideoSize()
                refreshTracks()
            }
        }
    }

    private fun emitVideoSize() {
        val p = player ?: return
        runCatching {
            val track = p.currentVideoTrack ?: return
            if (track.width > 0 && track.height > 0) {
                onVideoSize?.invoke(track.width, track.height)
            }
        }
    }

    /** Lê as faixas de áudio/legenda disponíveis no stream atual. */
    private fun refreshTracks() {
        val p = player ?: return
        runCatching {
            val audio = p.audioTracks?.filter { it.id >= 0 } ?: emptyList()
            val subs = p.spuTracks?.filter { it.id >= 0 } ?: emptyList()
            audioIds = audio.map { it.id }
            audioNames = audio.map { it.name ?: "Faixa ${it.id}" }
            subtitleIds = subs.map { it.id }
            subtitleNames = subs.map { it.name ?: "Legenda ${it.id}" }
            onTracks?.invoke(
                audioNames,
                subtitleNames,
                audioIds.indexOf(p.audioTrack),
                subtitleIds.indexOf(p.spuTrack),
            )
        }
    }

    /** Anexa a superfície de vídeo. Precisa vir antes de [play]. */
    fun attachSurface(view: SurfaceView) {
        val p = player ?: return
        runCatching {
            if (attached) detach()
            p.vlcVout.setVideoView(view)
            p.vlcVout.attachViews()
            attached = true
        }
        replayIfPending()
    }

    fun detach() {
        if (!attached) return
        runCatching { player?.vlcVout?.detachViews() }
        attached = false
    }

    /**
     * Prepara e inicia um stream.
     *
     * @param url endereço do stream
     * @param headers cabeçalhos HTTP do addon (Referer, User-Agent, Cookie)
     */
    fun play(url: String, headers: Map<String, String> = emptyMap()) {
        val p = player ?: run {
            onError?.invoke("VLC indisponível neste aparelho.")
            return
        }
        pendingUrl = url
        pendingHeaders = headers
        // Sem superfície anexada o VLC abriria um vout próprio; guardamos o
        // pedido e repetimos em attachSurface().
        if (!attached) return

        runCatching {
            p.stop()
            val media = Media(libVlc, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=1500")
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")
            applyHeaders(media, headers)
            p.media = media
            media.release()
            p.play()
        }.onFailure { onError?.invoke(it.message ?: "Falha ao preparar o stream.") }
    }

    private fun applyHeaders(media: Media, headers: Map<String, String>) {
        if (headers.isEmpty()) return
        headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value
            ?.let { media.addOption(":http-referrer=$it") }
        headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
            ?.let { media.addOption(":http-user-agent=$it") }
        headers.entries
            .filterNot { it.key.equals("Referer", true) || it.key.equals("User-Agent", true) }
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .forEach { (key, value) -> media.addOption(":http-header=$key: $value") }
    }

    /** Reaplica o último URL — usado quando a superfície é recriada. */
    fun replayIfPending() {
        val url = pendingUrl ?: return
        if (!attached) return
        if (player?.isPlaying == true) return
        play(url, pendingHeaders)
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun seekTo(ms: Long) {
        val p = player ?: return
        runCatching { if (p.isSeekable) p.time = ms.coerceAtLeast(0L) }
    }

    fun seekBy(deltaMs: Long) {
        val p = player ?: return
        runCatching { seekTo((p.time + deltaMs).coerceAtLeast(0L)) }
    }

    // ---------------------------------------------------------------- faixas

    fun audioNames(): List<String> = audioNames

    fun subtitleNames(): List<String> = subtitleNames

    /** Rótulo do botão de áudio, sempre em português. */
    fun audioLabel(): String = when {
        audioNames.isEmpty() -> "Áudio"
        audioNames.size == 1 -> "Áudio: ${audioNames.first().take(14)}"
        else -> "Áudio ${currentAudioIndex() + 1}/${audioNames.size}"
    }

    fun subtitleLabel(): String =
        if (subtitleNames.isEmpty()) "Legendas" else "Legendas (${subtitleNames.size})"

    private fun currentAudioIndex(): Int {
        val current = runCatching { player?.audioTrack ?: -1 }.getOrDefault(-1)
        return audioIds.indexOf(current).coerceAtLeast(0)
    }

    /** Avança para a próxima faixa de áudio (volta ao início no fim). */
    fun cycleAudio(): String {
        if (audioIds.size < 2) return audioLabel()
        val next = (currentAudioIndex() + 1) % audioIds.size
        runCatching { player?.setAudioTrack(audioIds[next]) }
        return "Áudio: ${audioNames.getOrNull(next)?.take(14) ?: (next + 1)}"
    }

    /** Liga/desliga/alterna legendas: cada toque passa para a próxima opção. */
    fun cycleSubtitle(): String {
        val p = player ?: return subtitleLabel()
        if (subtitleIds.isEmpty()) return subtitleLabel()
        val current = runCatching { p.spuTrack }.getOrDefault(-1)
        val index = subtitleIds.indexOf(current)
        // último passo desliga a legenda (comportamento de player de TV)
        val next = if (index >= subtitleIds.size - 1) -1 else index + 1
        runCatching { p.setSpuTrack(if (next < 0) -1 else subtitleIds[next]) }
        return if (next < 0) "Legendas: desligadas" else "Legendas: ${subtitleNames.getOrNull(next)?.take(12) ?: "ligadas"}"
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        runCatching { detach() }
        runCatching { player?.stop() }
        runCatching { player?.release() }
        runCatching { libVlc?.release() }
        onStatus = null
        onTime = null
        onError = null
        onVideoSize = null
        onTracks = null
    }
}
