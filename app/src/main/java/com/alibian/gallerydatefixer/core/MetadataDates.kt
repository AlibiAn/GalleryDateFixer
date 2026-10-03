package com.alibian.gallerydatefixer.core

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Parsing / formatting of the date strings stored inside photos (EXIF) and videos (MP4/MOV atoms). */
object MetadataDates {

    private const val MIN_PLAUSIBLE_MILLIS = 631_152_000_000L // 1990-01-01
    private val EXIF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    private val EXIF_DATE = Regex("""^\s*(\d{4})[:\-](\d{2})[:\-](\d{2})[ T](\d{2}):(\d{2}):(\d{2})""")
    private val EXIF_OFFSET = Regex("""^\s*([+\-])(\d{2}):(\d{2})\s*$""")
    private val VIDEO_DATE = Regex("""^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(?:\.(\d{1,3}))?(Z)?""")

    /**
     * Parses an EXIF `DateTimeOriginal` value ("2020:01:01 12:34:56").
     * EXIF stores local time; [offset] (`OffsetTimeOriginal`, e.g. "+02:00") is used when present,
     * otherwise the phone's [zone].
     */
    fun parseExif(value: String?, offset: String? = null, subSec: String? = null, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val m = value?.let { EXIF_DATE.find(it) } ?: return null
        val g = m.groupValues
        val ldt = try {
            LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        } catch (e: DateTimeException) {
            return null
        }
        val millis = subSec?.trim()?.takeWhile { it.isDigit() }?.take(3)?.padEnd(3, '0')?.toIntOrNull() ?: 0
        val zoneToUse: ZoneId = offset?.let { parseOffset(it) } ?: zone
        val epoch = ldt.atZone(zoneToUse).toInstant().toEpochMilli() + millis
        return epoch.takeIf { isPlausible(it) }
    }

    /** Parses `MediaMetadataRetriever.METADATA_KEY_DATE`, e.g. "20200101T123456.000Z" (UTC). */
    fun parseVideo(value: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val m = value?.trim()?.let { VIDEO_DATE.find(it) } ?: return null
        val g = m.groupValues
        val ldt = try {
            LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        } catch (e: DateTimeException) {
            return null
        }
        val millis = g[7].padEnd(3, '0').toIntOrNull() ?: 0
        // MP4/MOV creation times are UTC; the retriever appends "Z" in that case.
        val zoneToUse: ZoneId = if (g[8] == "Z") ZoneOffset.UTC else zone
        val epoch = ldt.atZone(zoneToUse).toInstant().toEpochMilli() + millis
        return epoch.takeIf { isPlausible(it) }
    }

    /** Formats [epochMillis] as an EXIF date string plus its offset string, in [zone]. */
    fun formatExif(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> {
        val zdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone)
        val offset = zdt.offset.id.let { if (it == "Z") "+00:00" else it }
        return zdt.format(EXIF_FORMAT) to offset
    }

    private fun parseOffset(value: String): ZoneOffset? {
        val m = EXIF_OFFSET.find(value) ?: return null
        val sign = if (m.groupValues[1] == "-") -1 else 1
        return try {
            ZoneOffset.ofHoursMinutes(sign * m.groupValues[2].toInt(), sign * m.groupValues[3].toInt())
        } catch (e: DateTimeException) {
            null
        }
    }

    private fun isPlausible(epochMillis: Long): Boolean =
        epochMillis >= MIN_PLAUSIBLE_MILLIS && epochMillis <= System.currentTimeMillis() + 24L * 60 * 60 * 1000
}
