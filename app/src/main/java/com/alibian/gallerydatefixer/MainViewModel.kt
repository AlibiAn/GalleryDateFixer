package com.alibian.gallerydatefixer

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alibian.gallerydatefixer.core.DateFixer
import com.alibian.gallerydatefixer.core.DateSource
import com.alibian.gallerydatefixer.core.FixReport
import com.alibian.gallerydatefixer.core.ItemStatus
import com.alibian.gallerydatefixer.core.MediaItem
import com.alibian.gallerydatefixer.core.MediaScanner
import com.alibian.gallerydatefixer.core.ScanOptions
import com.alibian.gallerydatefixer.core.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { HOME, RESULTS, DONE }

enum class ResultFilter(val label: String) { NEEDS_FIX("To fix"), OK("Correct"), NO_DATE("No date"), ALL("All") }

data class Progress(val label: String, val done: Int, val total: Int)

data class UiState(
    val hasPermission: Boolean = false,
    val folder: File? = null,
    val options: ScanOptions = ScanOptions(),
    val screen: Screen = Screen.HOME,
    val items: List<MediaItem> = emptyList(),
    val filter: ResultFilter = ResultFilter.NEEDS_FIX,
    val progress: Progress? = null,
    val report: FixReport? = null,
    val error: String? = null,
    /** How many files share each target date (in whole seconds) – used to flag suspicious duplicates. */
    val sameDateCounts: Map<Long, Int> = emptyMap(),
    /** File opened in the large preview, if any. */
    val detailPath: String? = null,
) {
    val busy get() = progress != null
    val needsFix get() = items.count { it.status == ItemStatus.NEEDS_FIX }
    val toFix get() = items.count { it.selected && it.status == ItemStatus.NEEDS_FIX }
    val detailItem: MediaItem? get() = detailPath?.let { p -> items.firstOrNull { it.path == p } }

    /** Number of *other* files with exactly the same new date. */
    fun sameDateOthers(item: MediaItem): Int =
        item.targetDate?.let { (sameDateCounts[it / 1000] ?: 1) - 1 } ?: 0
    val alreadyOk get() = items.count { it.status == ItemStatus.OK }
    val noDate get() = items.count { it.status == ItemStatus.NO_DATE }
    val visibleItems: List<MediaItem>
        get() = when (filter) {
            ResultFilter.ALL -> items
            ResultFilter.NEEDS_FIX -> items.filter { it.status == ItemStatus.NEEDS_FIX }
            ResultFilter.OK -> items.filter { it.status == ItemStatus.OK }
            ResultFilter.NO_DATE -> items.filter { it.status == ItemStatus.NO_DATE }
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val scanner = MediaScanner()
    private val fixer = DateFixer(app)
    private var job: Job? = null

    private val _state = MutableStateFlow(
        UiState(
            hasPermission = Storage.hasAllFilesAccess(),
            folder = prefs.getString(KEY_FOLDER, null)?.let(::File)?.takeIf { it.isDirectory },
            options = ScanOptions(
                includeSubfolders = prefs.getBoolean(KEY_SUBFOLDERS, true),
                includeVideos = prefs.getBoolean(KEY_VIDEOS, true),
                preferFilename = prefs.getBoolean(KEY_PREFER_NAME, false),
                writeExif = prefs.getBoolean(KEY_WRITE_EXIF, true),
            ),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refreshPermission() = _state.update { it.copy(hasPermission = Storage.hasAllFilesAccess()) }

    fun selectFolder(folder: File) {
        prefs.edit().putString(KEY_FOLDER, folder.absolutePath).apply()
        _state.update { it.copy(folder = folder) }
    }

    fun setOptions(options: ScanOptions) {
        prefs.edit()
            .putBoolean(KEY_SUBFOLDERS, options.includeSubfolders)
            .putBoolean(KEY_VIDEOS, options.includeVideos)
            .putBoolean(KEY_PREFER_NAME, options.preferFilename)
            .putBoolean(KEY_WRITE_EXIF, options.writeExif)
            .apply()
        _state.update { it.copy(options = options) }
    }

    fun setFilter(filter: ResultFilter) = _state.update { it.copy(filter = filter) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun scan() {
        val folder = _state.value.folder ?: return
        val options = _state.value.options
        job?.cancel()
        _state.update { it.copy(progress = Progress("Reading files", 0, 0), report = null) }
        job = viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    scanner.scan(folder, options) { done, total ->
                        _state.update { it.copy(progress = Progress("Reading dates", done, total)) }
                    }
                }
                val filter = if (items.any { it.status == ItemStatus.NEEDS_FIX }) ResultFilter.NEEDS_FIX else ResultFilter.ALL
                _state.update {
                    it.copy(
                        items = items,
                        sameDateCounts = sameDateCounts(items),
                        filter = filter,
                        screen = Screen.RESULTS,
                        progress = null,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(progress = null) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(progress = null, error = e.message ?: e.toString()) }
            }
        }
    }

    fun fix() {
        val items = _state.value.items
        job?.cancel()
        _state.update { it.copy(progress = Progress("Updating files", 0, it.toFix), detailPath = null) }
        job = viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    fixer.apply(items) { done, total, label ->
                        _state.update { it.copy(progress = Progress(label, done, total)) }
                    }
                }
                _state.update { it.copy(report = report, screen = Screen.DONE, progress = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(progress = null) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(progress = null, error = e.message ?: e.toString()) }
            }
        }
    }

    fun toggleSelected(path: String) = updateItem(path) { it.copy(selected = !it.selected) }

    /** Tick / untick every file in the "To fix" list. */
    fun selectAll(selected: Boolean) = _state.update { st ->
        st.copy(items = st.items.map { if (it.status == ItemStatus.NEEDS_FIX) it.copy(selected = selected) else it })
    }

    /** Lets the user pick the EXIF/video date or the file-name date for one file. */
    fun setSource(path: String, source: DateSource) = updateItem(path) {
        if (source in it.availableSources) it.copy(source = source, selected = true) else it
    }

    fun showDetail(path: String?) = _state.update { it.copy(detailPath = path) }

    private fun updateItem(path: String, change: (MediaItem) -> MediaItem) = _state.update { st ->
        val items = st.items.map { if (it.path == path) change(it) else it }
        st.copy(items = items, sameDateCounts = sameDateCounts(items))
    }

    private fun sameDateCounts(items: List<MediaItem>): Map<Long, Int> =
        items.mapNotNull { it.targetDate?.div(1000) }.groupingBy { it }.eachCount()

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(progress = null) }
    }

    fun back() {
        if (_state.value.busy) return
        _state.update {
            when (it.screen) {
                Screen.DONE -> it.copy(screen = Screen.HOME, items = emptyList(), sameDateCounts = emptyMap(), report = null)
                Screen.RESULTS -> if (it.detailPath != null) it.copy(detailPath = null)
                else it.copy(screen = Screen.HOME, items = emptyList(), sameDateCounts = emptyMap())
                Screen.HOME -> it
            }
        }
    }

    private companion object {
        const val KEY_FOLDER = "folder"
        const val KEY_SUBFOLDERS = "subfolders"
        const val KEY_VIDEOS = "videos"
        const val KEY_PREFER_NAME = "prefer_name"
        const val KEY_WRITE_EXIF = "write_exif"
    }
}
