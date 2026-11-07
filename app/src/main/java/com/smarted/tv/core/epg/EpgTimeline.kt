package com.smarted.tv.core.epg

import com.smarted.tv.core.model.EpgProgram

/**
 * Regras de montagem da linha do tempo do guia.
 *
 * Fica separado do [EpgService] porque não depende de Android, rede ou banco:
 * é só aritmética de intervalos — e por isso é testável no `verify-core`.
 *
 * Requisito de produto: a grade sempre cobre **no mínimo 12 horas contínuas**.
 * As fontes públicas de XMLTV costumam ter buracos (fim de um programa e
 * início do outro com minutos de diferença, ou blocos inteiros ausentes). Sem
 * esse preenchimento, a régua de horários do guia parece "quebrada" e o
 * usuário não consegue navegar pela faixa.
 */
object EpgTimeline {

    /** Duração da janela mínima exigida pelo produto. */
    const val MIN_HOURS = 12

    /** Blocos menores que isso são absorvidos pelo vizinho (evita tarjas ínfimas). */
    private const val MIN_GAP_MS = 60_000L

    const val GAP_TITLE = "Sem informação"

    /**
     * Preenche os vãos de [programs] entre [fromMs] e [toMs] com blocos
     * sintéticos, preservando a ordem cronológica.
     */
    fun pad(
        programs: List<EpgProgram>,
        fromMs: Long,
        toMs: Long,
        channelId: String,
    ): List<EpgProgram> {
        if (toMs <= fromMs) return emptyList()
        val sorted = programs
            .filter { it.endMs > fromMs && it.startMs < toMs }
            .sortedBy { it.startMs }

        val out = ArrayList<EpgProgram>(sorted.size + 4)
        var cursor = fromMs

        fun gap(start: Long, end: Long) {
            if (end - start < MIN_GAP_MS) return
            out.add(
                EpgProgram(
                    channelId = channelId,
                    title = GAP_TITLE,
                    startMs = start,
                    endMs = end,
                    description = null,
                    category = null,
                    icon = null,
                ),
            )
        }

        sorted.forEach { program ->
            val start = program.startMs.coerceAtLeast(fromMs)
            if (start > cursor) gap(cursor, start)
            out.add(program)
            cursor = maxOf(cursor, program.endMs.coerceAtMost(toMs))
        }
        if (cursor < toMs) gap(cursor, toMs)
        return out
    }

    /**
     * Cobertura efetiva da grade, em milissegundos, descontando sobreposições.
     * Útil para diagnóstico: se for menor que [MIN_HOURS], o guia está incompleto.
     */
    fun coverageMs(programs: List<EpgProgram>): Long {
        if (programs.isEmpty()) return 0L
        val sorted = programs.sortedBy { it.startMs }
        var total = 0L
        var start = sorted.first().startMs
        var end = sorted.first().endMs
        for (program in sorted.drop(1)) {
            if (program.startMs > end) {
                total += end - start
                start = program.startMs
                end = program.endMs
            } else if (program.endMs > end) {
                end = program.endMs
            }
        }
        return total + (end - start)
    }

    /** `true` quando a faixa cobre a janela mínima de 12 horas. */
    fun hasMinimumWindow(programs: List<EpgProgram>, fromMs: Long, hours: Int = MIN_HOURS): Boolean {
        val target = fromMs + hours * 60L * 60L * 1000L
        val end = programs.maxOfOrNull { it.endMs } ?: return false
        return end >= target
    }
}
