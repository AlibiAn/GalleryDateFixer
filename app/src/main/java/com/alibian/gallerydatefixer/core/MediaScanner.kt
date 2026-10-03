package com.alibian.gallerydatefixer.core

import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.time.ZoneId

/** Walks a folder, and works out for every photo / video which date it should carry. */
class MediaScanner(private val zone: ZoneId = ZoneId.systemDefault()) {

    private val filenameParser = FilenameDateParser(zone)
    private val chooser = DateChooser(zone)

    suspend fun scan(
        root: File,
        options: ScanOptions,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<MediaItem> {
        val files = listMediaFiles(root, options)
        val result = ArrayList<MediaItem>(files.size)
        files.forEachIndexed { index, file ->
            currentCoroutineContext().ensureActive()
            result += analyze(root, file, options)
            if (index % 10 == 0 || index == files.lastIndex) onProgress(index + 1, files.size)
        }
        return sortByTarget(result)
    }

    private fun listMediaFiles(root: File, options: ScanOptions): List<File> {
        val out = ArrayList<File>()
        val stack = ArrayDeque<File>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                // Skip hidden files and Android's trash (".trashed-…") / ".thumbnails" folders.
                if (child.name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (options.includeSubfolders) stack.add(child)
                } else if (MediaTypes.isImage(child) || (options.includeVideos && MediaTypes.isVideo(child))) {
                    out += child
                }
            }
        }
        return out
    }

    private fun analyze(root: File, file: File, options: ScanOptions): MediaItem {
        val isVideo = MediaTypes.isVideo(file)
        val current = file.lastModified()
        val embedded = if (isVideo) readVideoDate(file) else readExifDate(file)
        val fromName = filenameParser.parse(file.name)
        val choice = chooser.choose(isVideo, current, embedded, fromName, options.preferFilename)

        return MediaItem(
            path = file.absolutePath,
            name = file.name,
            relativeFolder = file.parentFile?.relativeToOrNull(root)?.path.orEmpty(),
            isVideo = isVideo,
            currentModified = current,
            embeddedDate = embedded,
            nameDate = choice.nameDate,
            nameDateIsDayOnly = fromName?.dateOnly == true,
            canWriteExif = MediaTypes.canWriteExif(file),
            writeExifEnabled = options.writeExif,
            source = choice.source,
        )
    }

    private fun readExifDate(file: File): Long? = try {
        val exif = ExifInterface(file)
        MetadataDates.parseExif(
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
            exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
            exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL),
            zone,
        ) ?: MetadataDates.parseExif(
            exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED),
            exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED),
            exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED),
            zone,
        )
    } catch (e: Exception) {
        null // Unsupported format (GIF, BMP) or corrupt file.
    }

    private fun readVideoDate(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            MetadataDates.parseVideo(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE), zone)
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        /** Oldest first: the order the gallery should show them in. */
        fun sortByTarget(items: List<MediaItem>): List<MediaItem> =
            items.sortedWith(compareBy<MediaItem> { it.targetDate ?: Long.MAX_VALUE }.thenBy { it.name })
    }
}

object MediaTypes {
    private val IMAGE = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "dng", "avif")
    private val VIDEO = setOf("mp4", "mov", "3gp", "3gpp", "mkv", "webm", "m4v", "avi")
    private val EXIF_WRITABLE = setOf("jpg", "jpeg", "png", "webp")

    fun isImage(file: File) = file.extension.lowercase() in IMAGE
    fun isVideo(file: File) = file.extension.lowercase() in VIDEO
    fun canWriteExif(file: File) = file.extension.lowercase() in EXIF_WRITABLE
}
