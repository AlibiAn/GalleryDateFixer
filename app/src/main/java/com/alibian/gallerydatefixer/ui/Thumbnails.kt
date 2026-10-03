package com.alibian.gallerydatefixer.ui

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Loads thumbnails off the main thread; small ones are cached so scrolling back is instant. */
object Thumbnails {
    private const val SMALL_PX = 256

    // Keep at most ~1/8 of the app's heap in thumbnails.
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount / 1024
    }

    // Decoding many 50 MP photos at once would exhaust memory; four at a time is plenty.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(4)

    suspend fun small(path: String, isVideo: Boolean): Bitmap? {
        cache.get(path)?.let { return it }
        return load(path, isVideo, SMALL_PX)?.also { cache.put(path, it) }
    }

    /** A screen-sized preview (not cached). Orientation from EXIF is applied by ThumbnailUtils. */
    suspend fun large(path: String, isVideo: Boolean, maxPx: Int): Bitmap? = load(path, isVideo, maxPx)

    private suspend fun load(path: String, isVideo: Boolean, px: Int): Bitmap? = withContext(decodeDispatcher) {
        try {
            val file = File(path)
            val size = Size(px, px)
            if (isVideo) ThumbnailUtils.createVideoThumbnail(file, size, null)
            else ThumbnailUtils.createImageThumbnail(file, size, null)
        } catch (e: Exception) {
            null // Corrupt / unsupported file: the UI shows a placeholder.
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}

@Composable
fun MediaThumbnail(path: String, isVideo: Boolean, size: Dp, modifier: Modifier = Modifier) {
    // null = still loading; Result with null bitmap = could not be decoded.
    val result by produceState<Result<Bitmap?>?>(initialValue = null, path) {
        value = Result.success(Thumbnails.small(path, isVideo))
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = result?.getOrNull()
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else if (result == null) {
            CircularProgressIndicator(Modifier.size(size / 3), strokeWidth = 2.dp)
        } else if (!isVideo) {
            Icon(Icons.Filled.Warning, contentDescription = "No preview", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .size(size / 2.5f)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)),
            )
        }
    }
}
