package com.smarted.tv.core.time

/**
 * Conversor de datas XMLTV ("20261107153000 +0000") e ISO curto para epoch millis.
 * Aritmética própria de calendário (proleptícica gregoriana) — determinística em JVM e Android.
 */
object XmlTime {

    /** Converte "YYYYMMDDHHMMSS ±HHMM". Retorna null se o formato não for reconhecido. */
    fun parseXmltv(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.length < 14) return null
        val digits = trimmed.substring(0, 14)
        if (!digits.all(Char::isDigit)) return null
        val year = digits.substring(0, 4).toIntOrNull() ?: return null
        val month = digits.substring(4, 6).toIntOrNull() ?: return null
        val day = digits.substring(6, 8).toIntOrNull() ?: return null
        val hour = digits.substring(8, 10).toIntOrNull() ?: return null
        val minute = digits.substring(10, 12).toIntOrNull() ?: return null
        val second = digits.substring(12, 14).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null
        val offsetMinutes = parseOffset(trimmed.substring(14)) ?: 0
        val utcSeconds = daysFromCivil(year, month, day) * 86_400L + hour * 3_600L + minute * 60L + second - offsetMinutes * 60L
        return utcSeconds * 1_000L
    }

    /** Aceita também "2026-11-07T15:30:00Z" e variantes sem segundos (formato de API Xtream). */
    fun parseIso(raw: String): Long? {
        val trimmed = raw.trim()
        val compact = trimmed.filter { it.isDigit() || it == '+' || it == '-' || it == 'Z' || it == 'T' }
        if (compact.length >= 8 && 'T' in compact) return parseIsoCompact(compact)
        if (trimmed.all(Char::isDigit) && trimmed.length in 9..10) {
            // unix epoch em segundos (Xtream usa isso no added)
            val secs = trimmed.toLongOrNull() ?: return null
            return secs * 1_000L
        }
        return null
    }

    private fun parseIsoCompact(compact: String): Long? {
        val tIndex = compact.indexOf('T')
        val datePart = compact.substring(0, tIndex)
        if (datePart.length != 8 || !datePart.all(Char::isDigit)) return null
        val year = datePart.substring(0, 4).toInt()
        val month = datePart.substring(4, 6).toInt()
        val day = datePart.substring(6, 8).toInt()
        var hour = 0; var minute = 0; var second = 0
        var rest = compact.substring(tIndex + 1)
        if (rest.endsWith("Z")) rest = rest.dropLast(1)
        val signIndex = rest.indexOfFirst { it == '+' || it == '-' }
        if (signIndex >= 0) {
            val offsetText = rest.substring(signIndex)
            rest = rest.substring(0, signIndex)
            val offset = parseOffset(offsetText) ?: 0
            return baseSeconds(year, month, day, rest) - offset * 60L
        }
        return baseSeconds(year, month, day, rest)
    }

    private fun baseSeconds(year: Int, month: Int, day: Int, time: String): Long {
        val hh = time.substringOrNull(0, 2)?.toIntOrNull() ?: 0
        val mm = time.substringOrNull(2, 4)?.toIntOrNull() ?: 0
        val ss = time.substringOrNull(4, 6)?.toIntOrNull() ?: 0
        return daysFromCivil(year, month, day) * 86_400L + hh * 3_600L + mm * 60L + ss
    }

    private fun String.substringOrNull(start: Int, end: Int): String? = if (length >= end) substring(start, end) else null

    private fun parseOffset(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        val sign = when (trimmed.first()) { '+' -> 1; '-' -> -1; else -> return null }
        val digits = trimmed.drop(1).filter(Char::isDigit)
        if (digits.length < 2) return null
        val hours = digits.substring(0, 2).toIntOrNull() ?: return null
        val minutes = if (digits.length >= 4) digits.substring(2, 4).toIntOrNull() ?: 0 else 0
        return sign * (hours * 60 + minutes)
    }

    /** Algoritmo de Howard Hinnant: dias de 1970-01-01 até a data civil. */
    fun daysFromCivil(yearIn: Int, month: Int, day: Int): Long {
        var year = yearIn.toLong()
        year -= if (month <= 2) 1 else 0
        val era = (if (year >= 0) year else year - 399) / 400
        val yoe = year - era * 400
        val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    /** Converte epoch millis para "HH:mm" fixo (chamador aplica timezone). */
    fun hourMinuteOfDayUtc(millis: Long): Pair<Int, Int> {
        val secondsFloor = floorDiv(millis, 1_000L)
        val secondsOfDay = floorMod(secondsFloor, 86_400L)
        return Pair((secondsOfDay / 3_600).toInt(), ((secondsOfDay % 3_600) / 60).toInt())
    }

    fun floorDiv(a: Long, b: Long): Long = Math.floorDiv(a, b)
    fun floorMod(a: Long, b: Long): Long = Math.floorMod(a, b)
}
