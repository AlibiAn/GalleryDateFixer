package com.alibian.gallerydatefixer.core

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
)

data class MediaItem(
    val path: String,
    val name: String,
    /** Folder relative to the scanned root, "" for the root itself. */
    val relativeFolder: String,
    val isVideo: Boolean,
    val currentModified: Long,
    /** The date the file should have, or null when none could be determined. */
    val targetDate: Long?,
    val source: DateSource,
    /** Date stored inside the file (EXIF / video metadata), if any. */
    val embeddedDate: Long?,
    val needsTimestamp: Boolean,
    val needsExif: Boolean,
) {
    val status: ItemStatus
        get() = when {
            targetDate == null -> ItemStatus.NO_DATE
            needsTimestamp || needsExif -> ItemStatus.NEEDS_FIX
            else -> ItemStatus.OK
        }
}

data class FixReport(
    val attempted: Int,
    val fixed: Int,
    val exifWritten: Int,
    val failures: List<Pair<String, String>>,
)
