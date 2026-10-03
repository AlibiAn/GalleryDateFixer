package com.alibian.gallerydatefixer.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibian.gallerydatefixer.MainViewModel
import com.alibian.gallerydatefixer.Screen
import com.alibian.gallerydatefixer.UiState
import com.alibian.gallerydatefixer.core.FixReport
import com.alibian.gallerydatefixer.core.ScanOptions
import com.alibian.gallerydatefixer.core.Storage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss")
internal fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_FORMAT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.screen != Screen.HOME && !state.busy) { viewModel.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.screen) {
                            Screen.HOME -> "Gallery Date Fixer"
                            Screen.RESULTS -> "Preview"
                            Screen.DONE -> "Finished"
                        },
                    )
                },
                navigationIcon = {
                    if (state.screen != Screen.HOME) {
                        IconButton(onClick = viewModel::back, enabled = !state.busy) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.hasPermission -> PermissionScreen()
                state.screen == Screen.HOME -> HomeScreen(
                    state = state,
                    onSelectFolder = viewModel::selectFolder,
                    onOptions = viewModel::setOptions,
                    onScan = viewModel::scan,
                )
                state.screen == Screen.RESULTS -> ResultsScreen(state, viewModel)
                state.screen == Screen.DONE -> DoneScreen(state.report, onDone = viewModel::back)
            }
        }
    }

    state.progress?.let { progress ->
        ProgressDialog(
            label = progress.label,
            done = progress.done,
            total = progress.total,
            // Cancelling half-way through writing would leave the gallery index stale, so only scans can be cancelled.
            onCancel = if (state.screen == Screen.HOME) viewModel::cancel else null,
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
            title = { Text("Something went wrong") },
            text = { Text(error) },
        )
    }
}

@Composable
private fun PermissionScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Permission needed", style = MaterialTheme.typography.headlineSmall)
        Text(
            "To change the dates of your photos and videos this app needs \"All files access\". " +
                "Android only allows changing a file's modified date with this permission.\n\n" +
                "Tap the button below, then enable \"Allow access to manage all files\" and come back.",
        )
        Button(
            onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    "package:${context.packageName}".toUri(),
                )
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Grant all files access") }
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    onSelectFolder: (File) -> Unit,
    onOptions: (ScanOptions) -> Unit,
    onScan: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val options = state.options

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Puts your photos and videos back in the right order in the Gallery. Each file's date is read " +
                "from its EXIF / video metadata or its file name (e.g. IMG_20230514_181530.jpg, IMG-20230514-WA0007.jpg), " +
                "and the file's modified date is set to that date.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Folder", style = MaterialTheme.typography.titleMedium)
                Text(
                    state.folder?.absolutePath ?: "No folder selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { showPicker = true }) { Text("Choose folder") }
            }
        }

        val shortcuts = remember { Storage.shortcuts() }
        if (shortcuts.isNotEmpty()) {
            Text("Quick picks", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts.forEach { s ->
                    FilterChip(
                        selected = state.folder == s.dir,
                        onClick = { onSelectFolder(s.dir) },
                        label = { Text(s.label) },
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                OptionRow("Include subfolders", null, options.includeSubfolders) {
                    onOptions(options.copy(includeSubfolders = it))
                }
                OptionRow("Include videos", null, options.includeVideos) {
                    onOptions(options.copy(includeVideos = it))
                }
                OptionRow(
                    "Write missing EXIF date",
                    "Also stores the date inside JPEG/PNG/WebP photos that have none (e.g. WhatsApp images), " +
                        "so apps that sort by \"date taken\" get it right too.",
                    options.writeExif,
                ) { onOptions(options.copy(writeExif = it)) }
                OptionRow(
                    "Prefer date from file name",
                    "Use the file-name date even when the file contains its own date.",
                    options.preferFilename,
                ) { onOptions(options.copy(preferFilename = it)) }
            }
        }

        Button(
            onClick = onScan,
            enabled = state.folder != null && !state.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Scan folder") }

        Text(
            "Nothing is changed until you review the preview and tap \"Fix\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showPicker) {
        FolderPickerDialog(
            initial = state.folder,
            onDismiss = { showPicker = false },
            onPick = {
                onSelectFolder(it)
                showPicker = false
            },
        )
    }
}

@Composable
private fun OptionRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { text -> @Composable { Text(text) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = CardDefaults.cardColors().containerColor),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerDialog(initial: File?, onDismiss: () -> Unit, onPick: (File) -> Unit) {
    val context = LocalContext.current
    val roots = remember { Storage.roots(context) }
    var current by remember { mutableStateOf(initial?.takeIf { it.isDirectory }) }
    val isRoot = current != null && roots.any { it.dir == current }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(current?.name ?: "Choose storage", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = {
                            when {
                                current == null -> onDismiss()
                                isRoot -> current = null
                                else -> current = current?.parentFile
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up") }
                    },
                )
            },
            bottomBar = {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = { current?.let(onPick) },
                        enabled = current != null,
                        modifier = Modifier.weight(1f),
                    ) { Text("Use this folder") }
                }
            },
        ) { padding ->
            val dir = current
            val entries: List<Pair<String, File>> = remember(dir) {
                if (dir == null) roots.map { it.label to it.dir } else Storage.subfolders(dir).map { it.name to it }
            }
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (dir != null) {
                    Text(
                        dir.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (entries.isEmpty()) {
                    Text("No subfolders", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.second.absolutePath }) { (label, file) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                            modifier = Modifier.clickable { current = file },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneScreen(report: FixReport?, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (report == null) return@Column
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text("${report.fixed} of ${report.attempted} files fixed", style = MaterialTheme.typography.headlineSmall)
        }
        if (report.exifWritten > 0) Text("EXIF date written into ${report.exifWritten} photos.")
        Text(
            "The Gallery index has been refreshed. If Samsung Gallery still shows the old order, " +
                "close it from Recents and open it again – it can take a minute to pick up the changes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (report.warnings.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${report.warnings.size} warnings (modified date was still fixed)", style = MaterialTheme.typography.titleSmall)
                    report.warnings.take(50).forEach { (name, reason) ->
                        Text("• $name: $reason", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (report.failures.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${report.failures.size} files could not be changed", style = MaterialTheme.typography.titleSmall)
                    report.failures.take(50).forEach { (name, reason) ->
                        Text("• $name: $reason", style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.failures.size > 50) Text("… and ${report.failures.size - 50} more", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun ProgressDialog(label: String, done: Int, total: Int, onCancel: (() -> Unit)?) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (total > 0) {
                    LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    Text("$done / $total")
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = onCancel?.let { cancel -> @Composable { TextButton(onClick = cancel) { Text("Cancel") } } },
    )
}
