package com.smarted.tv.core.epg

import com.smarted.tv.core.model.ChannelRow
import com.smarted.tv.core.model.LiveChannel
import com.smarted.tv.core.model.NowNext
import com.smarted.tv.core.model.Programme

/**
 * Linha do tempo de EPG a partir de programas ordenados por canal.
 * Nunca inventa programação: sem match, `now`/`next` ficam nulos e a UI mostra o motivo.
 */
object EpgTimeline {

    /** Índice por canal → lista ordenada (start asc). Chamador garante lista limpa da janela. */
    fun indexByChannel(programmes: List<Programme>): Map<String, List<Programme>> =
        programmes.groupBy { it.epgChannel }.mapValues { (_, list) -> list.sortedBy { it.startMs } }

    fun nowNext(channelProgrammes: List<Programme>, nowMs: Long): NowNext {
        if (channelProgrammes.isEmpty()) return NowNext(null, null, 0)
        val now = channelProgrammes.lastOrNull { it.startMs <= nowMs && it.stopMs > nowMs }
        val next = if (now != null) {
            channelProgrammes.firstOrNull { it.startMs >= now.stopMs }
                ?: channelProgrammes.firstOrNull { it.startMs > nowMs && it != now }
        } else {
            channelProgrammes.firstOrNull { it.stopMs > nowMs }
        }
        val progress = if (now != null && now.stopMs > now.startMs) {
            (((nowMs - now.startMs).toDouble() / (now.stopMs - now.startMs).toDouble()) * 100)
                .toInt().coerceIn(0, 100)
        } else {
            0
        }
        return NowNext(now, next, progress)
    }

    /** Programas exibidos na grade abaixo do player: a grade começa no programa atual. */
    fun upcoming(channelProgrammes: List<Programme>, nowMs: Long, limit: Int, includeCurrent: Boolean = true): List<Programme> {
        val start = if (includeCurrent) {
            channelProgrammes.indexOfLast { it.startMs <= nowMs }.let { if (it >= 0) it else 0 }
        } else {
            channelProgrammes.indexOfFirst { it.startMs > nowMs }.let { if (it >= 0) it else channelProgrammes.size }
        }
        return channelProgrammes.drop(start).take(limit.coerceAtLeast(0))
    }

    fun rows(
        channels: List<LiveChannel>,
        epgKeys: Map<Long, String>,
        timeline: Map<String, List<Programme>>,
        favoriteKeys: Set<String>,
        nowMs: Long,
    ): List<ChannelRow> = channels.map { channel ->
        val key = epgKeys[channel.id]
        val list = key?.let { timeline[it] }.orEmpty()
        val now = nowNext(list, nowMs)
        ChannelRow(channel, now.now, now.next, now.progressPercent, channel.favKey in favoriteKeys)
    }
}
