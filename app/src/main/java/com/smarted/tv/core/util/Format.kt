package com.smarted.tv.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val accents = Regex("\\p{M}+")

/** Minúsculas sem acentos: busca tolerante ("acao" encontra "Ação"). */
fun String.normalized(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(accents, "").lowercase()

private val clockFmt = DateTimeFormatter.ofPattern("HH:mm")
fun Long.asClock(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(clockFmt)

fun Int.asDuration(): String {
    val h = this / 60
    val m = this % 60
    return if (h > 0) String.format("%dh%02dmin", h, m) else "${m}min"
}

fun Double.asRating(): String = String.format(Locale("pt", "BR"), "%.1f", this)

@Composable
fun rememberNowMs(intervalMs: Long = 30_000L): State<Long> =
    produceState(System.currentTimeMillis()) {
        while (true) { delay(intervalMs); value = System.currentTimeMillis() }
    }
