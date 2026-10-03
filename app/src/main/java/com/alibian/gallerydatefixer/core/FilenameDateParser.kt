package com.alibian.gallerydatefixer.core

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A date recovered from a file name.
 *
 * [Kind.DAY_ONLY] means the name only contained a day (e.g. WhatsApp's `IMG-20200101-WA0001.jpg`),
 * so [localDateTime] has an estimated time of day.
 */
data class FilenameDate(
    val localDateTime: LocalDateTime,
    val kind: Kind,
    val epochMillis: Long,
) {
    enum class Kind { DATE_TIME, TIMESTAMP, DAY_ONLY }

    val dateOnly: Boolean get() = kind == Kind.DAY_ONLY
}

/**
 * Extracts capture dates from common camera / messenger / screenshot file names, e.g.
 *
 * - `IMG_20200101_123456.jpg`, `VID_20200101_123456.mp4`, `20200101_123456.jpg` (Samsung, most Android cameras)
 * - `PXL_20200101_123456789.jpg` (Pixel, with milliseconds)
 * - `Screenshot_20200101-123456_Chrome.jpg`, `Screenshot_2020-01-01-12-34-56.png`
 * - `Screenshot 2020-01-01 at 12.34.56.png` (macOS), `PHOTO-2020-01-01-12-34-56.jpg` (WhatsApp export)
 * - `signal-2020-01-01-123456.jpg`
 * - `IMG-20200101-WA0001.jpg` (WhatsApp, date only – the WA sequence number keeps the order inside a day)
 * - `FB_IMG_1577836800000.jpg`, `mmexport1577836800000.jpg`, `1577836800000.jpg` (Unix timestamps,
 *   only with prefixes known to use them – other long numbers in names are usually random IDs)
 *
 * File-name times are interpreted in [zone] (the phone's time zone), which is what cameras use.
 */
class FilenameDateParser(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    fun parse(fileName: String): FilenameDate? {
        // Hashes and UUIDs (common in downloaded / cached files) contain digit runs that can look like dates.
        val name = HASH_LIKE.replace(fileName.substringBeforeLast('.'), "_")
        return parseDateTime(name) ?: parseTimestamp(name) ?: parseDateOnly(name)
    }

    private fun parseDateTime(name: String): FilenameDate? {
        for (regex in DATE_TIME_PATTERNS) {
            for (m in regex.findAll(name)) {
                val g = m.groupValues
                val ldt = localDateTime(g[1], g[2], g[3], g[4], g[5], g[6]) ?: continue
                val millis = g.getOrNull(7).orEmpty().take(3).padEnd(3, '0').ifEmpty { "000" }.toInt()
                val withMillis = ldt.plusNanos(millis * 1_000_000L)
                val epoch = withMillis.atZone(zone).toInstant().toEpochMilli()
                if (isPlausible(epoch)) return FilenameDate(withMillis, FilenameDate.Kind.DATE_TIME, epoch)
            }
        }
        return null
    }

    private fun parseTimestamp(name: String): FilenameDate? {
        // Most long numbers in file names are random IDs (Messenger_creation_…, Snapchat-…, picker
        // copies like 1000012345.jpg), which as timestamps land on bogus dates such as 2006 or 2009.
        // Only trust numbers in a position where apps are known to put a timestamp.
        val m = TIMESTAMP.matchEntire(name) ?: return null
        val digits = m.groupValues[2]
        val prefix = m.groupValues[1]
        val epoch = when {
            digits.length == 13 && (prefix.isEmpty() || MS_TIMESTAMP_PREFIX.matches(prefix)) -> digits.toLong()
            digits.length == 10 && S_TIMESTAMP_PREFIX.matches(prefix) -> digits.toLong() * 1000
            else -> return null
        }
        if (epoch < MIN_TIMESTAMP_MILLIS || !isPlausible(epoch)) return null
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), zone)
        return FilenameDate(ldt, FilenameDate.Kind.TIMESTAMP, epoch)
    }

    private fun parseDateOnly(name: String): FilenameDate? {
        for (regex in DATE_ONLY_PATTERNS) {
            for (m in regex.findAll(name)) {
                val g = m.groupValues
                val date = try {
                    LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
                } catch (e: DateTimeException) {
                    continue
                }
                // Noon is the safest guess when only the day is known (no time-zone day flips).
                // The WhatsApp sequence number (WA0001, WA0002...) is added as seconds so files
                // from the same day keep the order in which they were received.
                val sequence = WA_SEQUENCE.find(name)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val ldt = date.atTime(12, 0).plusSeconds(sequence.coerceAtMost(MAX_SEQUENCE_SECONDS))
                val epoch = ldt.atZone(zone).toInstant().toEpochMilli()
                if (isPlausible(epoch)) return FilenameDate(ldt, FilenameDate.Kind.DAY_ONLY, epoch)
            }
        }
        return null
    }

    private fun localDateTime(y: String, mo: String, d: String, h: String, mi: String, s: String): LocalDateTime? =
        try {
            LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt())
        } catch (e: DateTimeException) {
            null
        }

    private fun isPlausible(epochMillis: Long): Boolean =
        epochMillis >= MIN_PLAUSIBLE_MILLIS && epochMillis <= nowMillis() + ONE_DAY_MILLIS

    companion object {
        private const val YEAR = """((?:19|20)\d{2})"""
        private const val MONTH = """(0[1-9]|1[0-2])"""
        private const val DAY = """(0[1-9]|[12]\d|3[01])"""
        private const val HOUR = """([01]\d|2[0-3])"""
        private const val MIN = """([0-5]\d)"""
        private const val SEC = """([0-5]\d)"""

        private val DATE_TIME_PATTERNS = listOf(
            // 20200101_123456, 20200101-123456, 20200101123456, 20200101_123456789
            Regex("""(?<!\d)$YEAR$MONTH$DAY[ _\-.T]?$HOUR$MIN$SEC(\d{1,3})?(?!\d)"""),
            // 2020-01-01-12-34-56, 2020-01-01 12.34.56, 2020-01-01 at 12.34.56, 2020_01_01_12_34_56
            Regex("""(?<!\d)$YEAR[\-_.]$MONTH[\-_.]$DAY(?:[ _\-T]|\s+at\s+)$HOUR[\-_.:]$MIN[\-_.:]$SEC(?:[.,](\d{1,3}))?(?!\d)"""),
            // signal-2020-01-01-123456, 2020-01-01_123456
            Regex("""(?<!\d)$YEAR-$MONTH-$DAY[ _\-T]$HOUR$MIN$SEC(\d{1,3})?(?!\d)"""),
        )

        // A bare day must stand on its own (IMG-20230514-WA0007, 2023-05-14 trip), not be glued to
        // letters or digits, where 8 digits are far more likely to be part of an ID.
        private val DATE_ONLY_PATTERNS = listOf(
            Regex("""(?<![A-Za-z0-9])$YEAR$MONTH$DAY(?![A-Za-z0-9])"""),
            Regex("""(?<![A-Za-z0-9])$YEAR-$MONTH-$DAY(?![A-Za-z0-9])"""),
        )

        /** Whole name = optional known prefix + number + optional "(1)" / "_1" / "-edited" style suffix. */
        private val TIMESTAMP = Regex("""(?i)^([a-z_\-]*?)(\d{13}|\d{10})(?:\s*\(\d{1,3}\)|[_\-][a-z0-9]{1,8})?$""")
        private val MS_TIMESTAMP_PREFIX = Regex("""(?i)(fb_img_|received_|mmexport|wp_|screenshot_|img_|image_|photo_|video_|vid_)""")
        private val S_TIMESTAMP_PREFIX = Regex("""(?i)(fb_img_|received_|mmexport|screenshot_)""")
        private val HASH_LIKE = Regex(
            """(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|(?=[0-9a-f]{16,})(?=[0-9a-f]*?[a-f])(?=[0-9a-f]*?\d)[0-9a-f]{16,}""",
        )
        private val WA_SEQUENCE = Regex("""WA(\d{1,5})""", RegexOption.IGNORE_CASE)

        private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val MAX_SEQUENCE_SECONDS = 11L * 60 * 60 // stay within the same day
        private const val MIN_PLAUSIBLE_MILLIS = 631_152_000_000L // 1990-01-01
        private const val MIN_TIMESTAMP_MILLIS = 1_262_304_000_000L // 2010-01-01
    }
}
