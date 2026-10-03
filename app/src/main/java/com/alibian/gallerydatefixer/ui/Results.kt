package com.alibian.gallerydatefixer.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alibian.gallerydatefixer.MainViewModel
import com.alibian.gallerydatefixer.ResultFilter
import com.alibian.gallerydatefixer.UiState
import com.alibian.gallerydatefixer.core.DateSource
import com.alibian.gallerydatefixer.core.FilenameDate
import com.alibian.gallerydatefixer.core.ItemStatus
import com.alibian.gallerydatefixer.core.MediaItem
import com.alibian.gallerydatefixer.core.mediaStoreUri

@Composable
fun ResultsScreen(state: UiState, viewModel: MainViewModel) {
    val visible = state.visibleItems
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${state.items.size} files found in ${state.folder?.name.orEmpty()}",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultFilter.entries.forEach { f ->
                    val count = when (f) {
                        ResultFilter.NEEDS_FIX -> state.needsFix
                        ResultFilter.OK -> state.alreadyOk
                        ResultFilter.NO_DATE -> state.noDate
                        ResultFilter.ALL -> state.items.size
                    }
                    FilterChip(selected = state.filter == f, onClick = { viewModel.setFilter(f) }, label = { Text("${f.label} ($count)") })
                }
            }
            if (state.filter == ResultFilter.NEEDS_FIX && state.needsFix > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Tap a file to see it large and check its date. Untick files you don't want changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    val allSelected = state.toFix == state.needsFix
                    TextButton(onClick = { viewModel.selectAll(!allSelected) }) {
                        Text(if (allSelected) "Untick all" else "Tick all")
                    }
                }
            }
        }
        HorizontalDivider()

        if (visible.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
                Text(
                    if (state.filter == ResultFilter.NEEDS_FIX) "Nothing to fix – all dates are already correct."
                    else "No files in this list.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(visible, key = { it.path }) { item ->
                    MediaRow(
                        item = item,
                        sameDateOthers = state.sameDateOthers(item),
                        onClick = { viewModel.showDetail(item.path) },
                        onToggle = { viewModel.toggleSelected(item.path) },
                    )
                    HorizontalDivider()
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = viewModel::fix,
                enabled = state.toFix > 0 && !state.busy,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
            ) { Text(if (state.toFix > 0) "Fix ${state.toFix} files" else "Nothing selected to fix") }
        }
    }

    state.detailItem?.let { item ->
        val index = visible.indexOfFirst { it.path == item.path }
        DetailDialog(
            item = item,
            sameDateOthers = state.sameDateOthers(item),
            position = if (index >= 0) "${index + 1} / ${visible.size}" else "",
            onPrevious = visible.getOrNull(index - 1)?.takeIf { index > 0 }?.let { prev -> { viewModel.showDetail(prev.path) } },
            onNext = visible.getOrNull(index + 1)?.takeIf { index >= 0 }?.let { next -> { viewModel.showDetail(next.path) } },
            onSource = { viewModel.setSource(item.path, it) },
            onToggle = { viewModel.toggleSelected(item.path) },
            onDismiss = { viewModel.showDetail(null) },
        )
    }
}

@Composable
private fun MediaRow(item: MediaItem, sameDateOthers: Int, onClick: () -> Unit, onToggle: () -> Unit) {
    val fix = item.status == ItemStatus.NEEDS_FIX
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediaThumbnail(item.path, item.isVideo, size = 72.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.relativeFolder.isNotEmpty()) {
                Text(
                    item.relativeFolder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("Now: ${formatDate(item.currentModified)}", style = MaterialTheme.typography.bodySmall)
            val target = item.targetDate
            if (target == null) {
                Text("No date found in metadata or file name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Text(
                    "New: ${formatDate(target)} · ${item.sourceLabel}" + if (item.needsExif) " · writes EXIF" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (fix) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (fix && item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Warnings(item, sameDateOthers)
        }
        when (item.status) {
            ItemStatus.NEEDS_FIX -> Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
            ItemStatus.OK -> Icon(Icons.Filled.CheckCircle, contentDescription = "Correct", tint = MaterialTheme.colorScheme.primary)
            ItemStatus.NO_DATE -> Icon(Icons.Filled.Warning, contentDescription = "No date", tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** Hints that help spot a wrong date before applying it. */
@Composable
private fun Warnings(item: MediaItem, sameDateOthers: Int) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.tertiary
    if (item.datesDisagree) Text("⚠ Date in file and file name differ – tap to compare", style = style, color = color)
    if (item.source == DateSource.FILENAME && item.nameDateIsDayOnly && item.targetDate != item.currentModified &&
        item.targetDate != item.embeddedDate
    ) {
        Text("ⓘ Only the day is known; time is estimated", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item.ignoredDates.forEach { (label, date) ->
        Text(
            "ⓘ Ignored $label date ${formatDate(date)} (before your minimum year)",
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (item.source == DateSource.FILENAME && item.nameKind == FilenameDate.Kind.TIMESTAMP) {
        Text("ⓘ Date comes from a number in the file name – check it looks right", style = style, color = color)
    }
    if (sameDateOthers > 0) {
        Text("⚠ Same new date as $sameDateOthers other file${if (sameDateOthers == 1) "" else "s"}", style = style, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailDialog(
    item: MediaItem,
    sameDateOthers: Int,
    position: String,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onSource: (DateSource) -> Unit,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close") }
                    },
                )
            },
            bottomBar = {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                    }
                    Text(position, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LargePreview(item)

                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.relativeFolder.isNotEmpty()) {
                        Text(item.relativeFolder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    InfoLine("Current modified date", formatDate(item.currentModified))
                    Warnings(item, sameDateOthers)
                }

                HorizontalDivider()
                Text("Use this date", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                SourceOption(
                    title = item.embeddedSource.label + if (item.isVideo) "" else " (date taken)",
                    value = item.embeddedDate?.let(::formatDate) ?: "Not present in this file",
                    selected = item.source == item.embeddedSource,
                    enabled = item.embeddedDate != null,
                    onClick = { onSource(item.embeddedSource) },
                )
                SourceOption(
                    title = item.nameLabel,
                    value = item.nameDate?.let(::formatDate) ?: "No date in the file name",
                    selected = item.source == DateSource.FILENAME,
                    enabled = item.nameDate != null,
                    onClick = { onSource(DateSource.FILENAME) },
                )

                HorizontalDivider()
                when (item.status) {
                    ItemStatus.NEEDS_FIX -> Row(
                        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Fix this file", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "→ ${formatDate(item.targetDate!!)}" + if (item.needsExif) " (also writes EXIF)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Switch(checked = item.selected, onCheckedChange = { onToggle() })
                    }
                    ItemStatus.OK -> Text(
                        "✓ The modified date already matches – nothing to change.",
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    ItemStatus.NO_DATE -> Text(
                        "No date could be found, this file will be left untouched.",
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                OutlinedButton(
                    onClick = {
                        val uri = mediaStoreUri(context, item.path, item.isVideo) ?: return@OutlinedButton
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, if (item.isVideo) "video/*" else "image/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) { Text(if (item.isVideo) "Play video" else "Open in Gallery") }
                Box(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LargePreview(item: MediaItem) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 460.dp).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val maxPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceIn(480, 1600)
        // null = loading; Result(null) = could not decode.
        val result by produceState<Result<Bitmap?>?>(initialValue = null, item.path) {
            value = Result.success(Thumbnails.large(item.path, item.isVideo, maxPx))
        }
        val bmp = result?.getOrNull()
        when {
            bmp != null -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
            )
            result == null -> CircularProgressIndicator()
            else -> Text("No preview available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SourceOption(title: String, value: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
