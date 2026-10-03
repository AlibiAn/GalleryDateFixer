package com.alibian.gallerydatefixer.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Applies the detected dates:
 *  1. optionally writes EXIF DateTimeOriginal into photos that lack it,
 *  2. sets the file's "last modified" time to the capture date,
 *  3. asks Android's media scanner to re-index the files so the Gallery picks up the new dates.
 */
class DateFixer(private val context: Context, private val zone: ZoneId = ZoneId.systemDefault()) {

    suspend fun apply(
        items: List<MediaItem>,
        onProgress: (done: Int, total: Int, phase: String) -> Unit,
    ): FixReport {
        val todo = items.filter { it.status == ItemStatus.NEEDS_FIX }
        val failures = mutableListOf<Pair<String, String>>()
        val changed = mutableListOf<MediaItem>()
        var exifWritten = 0

        todo.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            val file = File(item.path)
            val target = item.targetDate!!
            try {
                if (item.needsExif) {
                    writeExifDate(file, target)
                    exifWritten++
                }
                // Always (re)set the timestamp: writing EXIF rewrites the file and bumps it to "now".
                if (!file.setLastModified(target) || abs(file.lastModified() - target) >= MediaScanner.TOLERANCE_MILLIS) {
                    failures += item.name to "Android refused to change the modified date"
                } else {
                    changed += item
                }
            } catch (e: Exception) {
                failures += item.name to (e.message ?: e.javaClass.simpleName)
            }
            if (index % 5 == 0 || index == todo.lastIndex) onProgress(index + 1, todo.size, "Updating files")
        }

        rescan(changed, onProgress)

        return FixReport(
            attempted = todo.size,
            fixed = changed.size,
            exifWritten = exifWritten,
            failures = failures,
        )
    }

    private fun writeExifDate(file: File, epochMillis: Long) {
        val (dateTime, offset) = MetadataDates.formatExif(epochMillis, zone)
        val subSec = (epochMillis % 1000).toString().padStart(3, '0')
        val exif = ExifInterface(file)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTime)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
        exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, subSec)
        exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, subSec)
        if (exif.getAttribute(ExifInterface.TAG_DATETIME) == null) {
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
        }
        exif.saveAttributes()
    }

    /** Re-indexes the files in MediaStore so Gallery / Google Photos see the new dates. */
    private suspend fun rescan(items: List<MediaItem>, onProgress: (Int, Int, String) -> Unit) {
        if (items.isEmpty()) return
        val done = AtomicInteger(0)
        items.chunked(SCAN_BATCH).forEach { batch ->
            currentCoroutineContext().ensureActive()
            val paths = batch.map { it.path }.toTypedArray()
            withTimeoutOrNull(60_000L + batch.size * 1_000L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val remaining = AtomicInteger(paths.size)
                    MediaScannerConnection.scanFile(context, paths, null) { _, _ ->
                        val n = done.incrementAndGet()
                        if (n % 10 == 0 || n == items.size) onProgress(n, items.size, "Refreshing gallery index")
                        if (remaining.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
                    }
                }
            }
            batch.forEach { updateDateTaken(it) }
        }
    }

    /**
     * Files without an embedded date (videos without metadata, GIFs, HEIC without EXIF...) get no
     * "date taken" from the scanner. Try to set it to the target date so date-taken sorting works too.
     * This is best effort: on some Android builds MediaStore ignores or rejects it, and the Gallery
     * then falls back to the modified date we've just set.
     */
    private fun updateDateTaken(item: MediaItem) {
        if (item.needsExif || item.embeddedDate != null) return
        val target = item.targetDate ?: return
        try {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val values = ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, target) }
            context.contentResolver.update(
                collection,
                values,
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(item.path),
            )
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val SCAN_BATCH = 100
    }
}
