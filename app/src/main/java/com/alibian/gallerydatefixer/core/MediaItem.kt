package com.alibian.gallerydatefixer.core

import kotlin.math.abs

enum class DateSource(val label: String) {
    EXIF("EXIF"),
    VIDEO_METADATA("Video metadata"),
    FILENAME("File name"),
    NONE("No date found"),
}

enum class ItemStatus { NEEDS_FIX, OK, NO_DATE }

data class ScanOptions(
    val includeSubfolders: Boolean = true,
    val includeVideos: Boolean = true,
    /** Use the date in the file name even when the file has its own EXIF / video date. */
    val preferFilename: Boolean = false,
    /** Write EXIF DateTimeOriginal into JPEG / PNG / WebP files that lack it. */
    val writeExif: Boolean = true,
    /** Dates before this year (from any source) are ignored as implausible. 0 = no limit. */
    val minYear: Int = 0,
)

data class MediaItem(
    val path: String,
    val name: String,
    /** Folder relative to the scanned root, "" for the root itself. */
    val relativeFolder: String,
    val isVideo: Boolean,
    val currentModified: Long,
    /** Date stored inside the file (EXIF / video metadata), if any. */
    val embeddedDate: Long?,
    /** Date derived from the file name, if any. */
    val nameDate: Long?,
    /** How the file-name date was read (full date & time, Unix timestamp, or day only). */
    val nameKind: FilenameDate.Kind?,
    val canWriteExif: Boolean,
    val writeExifEnabled: Boolean,
    /** Which of the candidate dates is used. */
    val source: DateSource,
    /** Unticked by the user in the preview: left untouched by "Fix". */
    val selected: Boolean = true,
    /** Candidate dates that were found but rejected (before the minimum year): label to date. */
    val ignoredDates: List<Pair<String, Long>> = emptyList(),
) {
    /** True when the file name only contained a day, so the time of [nameDate] is a guess. */
    val nameDateIsDayOnly: Boolean get() = nameKind == FilenameDate.Kind.DAY_ONLY

    val nameLabel: String
        get() = when (nameKind) {
            FilenameDate.Kind.TIMESTAMP -> "File name (number read as timestamp)"
            FilenameDate.Kind.DAY_ONLY -> "File name (day only, time estimated)"
            else -> "File name"
        }

    /** Human readable description of where the new date comes from. */
    val sourceLabel: String get() = if (source == DateSource.FILENAME) nameLabel else source.label

    val embeddedSource: DateSource get() = if (isVideo) DateSource.VIDEO_METADATA else DateSource.EXIF

    /** The date the file should have, or null when none could be determined. */
    val targetDate: Long?
        get() = when (source) {
            DateSource.EXIF, DateSource.VIDEO_METADATA -> embeddedDate
            DateSource.FILENAME -> nameDate
            DateSource.NONE -> null
        }

    val needsTimestamp: Boolean
        get() = targetDate?.let { abs(currentModified - it) >= TOLERANCE_MILLIS } ?: false

    /** Write EXIF when the photo has no date inside, or (when the user chose the file-name date) a different one. */
    val needsExif: Boolean
        get() {
            val target = targetDate ?: return false
            if (!writeExifEnabled || !canWriteExif) return false
            return embeddedDate == null || abs(embeddedDate - target) >= EXIF_TOLERANCE_MILLIS
        }

    val status: ItemStatus
        get() = when {
            targetDate == null -> ItemStatus.NO_DATE
            needsTimestamp || needsExif -> ItemStatus.NEEDS_FIX
            else -> ItemStatus.OK
        }

    /** The date inside the file and the one in its name are more than an hour apart: worth a look. */
    val datesDisagree: Boolean
        get() = embeddedDate != null && nameDate != null && abs(embeddedDate - nameDate) > 60 * 60 * 1000L

    /** The sources the user can choose between for this file. */
    val availableSources: List<DateSource>
        get() = buildList {
            if (embeddedDate != null) add(embeddedSource)
            if (nameDate != null) add(DateSource.FILENAME)
        }

    companion object {
        /** Differences below this are treated as "already correct" (file systems round times). */
        const val TOLERANCE_MILLIS = 2_000L
        private const val EXIF_TOLERANCE_MILLIS = 60_000L
    }
}

data class FixReport(
    val attempted: Int,
    val fixed: Int,
    val exifWritten: Int,
    val failures: List<Pair<String, String>>,
    val warnings: List<Pair<String, String>> = emptyList(),
)
