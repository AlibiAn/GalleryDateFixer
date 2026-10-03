package com.alibian.gallerydatefixer.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Picks which candidate date a file should get. Pure logic, unit tested. */
class DateChooser(private val zone: ZoneId = ZoneId.systemDefault()) {

    data class Choice(val nameDate: Long?, val source: DateSource)

    fun choose(
        isVideo: Boolean,
        currentModified: Long,
        embedded: Long?,
        fromName: FilenameDate?,
        preferFilename: Boolean,
    ): Choice {
        val nameMillis: Long? = fromName?.let { parsed ->
            when {
                !parsed.dateOnly -> parsed.epochMillis
                // Only the day is known: keep a more precise time when it agrees on the day.
                embedded != null && sameDay(embedded, parsed.epochMillis) -> embedded
                sameDay(currentModified, parsed.epochMillis) -> currentModified
                else -> parsed.epochMillis
            }
        }
        val embeddedSource = if (isVideo) DateSource.VIDEO_METADATA else DateSource.EXIF

        val source = when {
            nameMillis != null && preferFilename -> DateSource.FILENAME
            // Video creation times are stored in UTC, but many phones/apps write local time into them
            // (shifting the date by the time-zone offset), and some record the *end* of the clip.
            // A full camera file name (VID_20230514_181530.mp4) is the local start time, so when both
            // describe the same moment give or take a day, the file name is the more reliable one.
            isVideo && embedded != null && nameMillis != null && fromName?.dateOnly == false &&
                abs(embedded - nameMillis) <= VIDEO_NAME_WINDOW_MILLIS -> DateSource.FILENAME
            embedded != null -> embeddedSource
            nameMillis != null -> DateSource.FILENAME
            else -> DateSource.NONE
        }
        return Choice(nameMillis, source)
    }

    private fun sameDay(a: Long, b: Long): Boolean =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

    private companion object {
        const val VIDEO_NAME_WINDOW_MILLIS = 26L * 60 * 60 * 1000
    }
}
