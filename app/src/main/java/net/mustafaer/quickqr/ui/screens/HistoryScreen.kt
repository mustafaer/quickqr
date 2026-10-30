package net.mustafaer.quickqr.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.ui.components.RelativeTimeFormatter
import net.mustafaer.quickqr.ui.components.rememberRelativeTimeFormatter
import net.mustafaer.quickqr.ui.theme.icon
import net.mustafaer.quickqr.ui.theme.labelRes
import net.mustafaer.quickqr.ui.theme.rememberTypeColors
import net.mustafaer.quickqr.ui.viewmodel.HistoryRow
import net.mustafaer.quickqr.utils.ExportFormat

/**
 * @param rows null until the first database read, so the empty state never
 *   flashes up for a user who has history.
 */
@Composable
fun HistoryScreen(
    rows: List<HistoryRow>?,
    query: String,
    onQueryChange: (String) -> Unit,
    onItemSelect: (HistoryRow) -> Unit,
    onDelete: (HistoryRow) -> Unit,
    onTogglePin: (HistoryRow) -> Unit,
    onClearAll: () -> Unit,
    onExport: suspend (ExportFormat) -> Uri?,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showExportMenu by rememberSaveable { mutableStateOf(false) }
    val relativeTime = rememberRelativeTimeFormatter()
    // Ticks once a minute while the list is on screen so "2 min ago" stays true.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }

    val exportFailedMsg = stringResource(R.string.error_share_history)
    val exportEmptyMsg = stringResource(R.string.history_export_empty)
    val shareChooserTitle = stringResource(R.string.history_share_title)

    fun export(format: ExportFormat) {
        showExportMenu = false
        scope.launch {
            val uri = onExport(format)
            val message = when {
                // Null with an empty list means "nothing to export"; null with
                // items in the list means the write itself failed.
                uri == null && rows.isNullOrEmpty() -> exportEmptyMsg
                uri == null -> exportFailedMsg
                else -> shareFile(context, uri, format.mimeType, shareChooserTitle)?.let { exportFailedMsg }
            }
            if (message != null) scope.showMessage(snackbarHostState, message)
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text(
                        stringResource(R.string.history_clear_confirm_confirm),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.history_clear_confirm_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (!rows.isNullOrEmpty() || query.isNotEmpty()) {
                Box {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.history_export))
                    }
                    DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_export_json)) },
                            onClick = { export(ExportFormat.JSON) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_export_csv)) },
                            onClick = { export(ExportFormat.CSV) }
                        )
                    }
                }
                IconButton(onClick = { showDeleteConfirmDialog = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.history_clear),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.history_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.history_search_clear))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        when {
            rows == null -> Spacer(modifier = Modifier.weight(1f))

            rows.isEmpty() -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            if (query.isNotEmpty()) R.string.history_empty else R.string.history_no_items
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items = rows, key = { it.id }, contentType = { "scan" }) { row ->
                    SwipeToDelete(onDelete = { onDelete(row) }) {
                        HistoryItem(
                            row = row,
                            relativeTime = relativeTime,
                            now = now,
                            onClick = { onItemSelect(row) },
                            onTogglePin = { onTogglePin(row) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Deletes on an end-to-start swipe. The row leaves the list at once and the
 * screen offers Undo; the database delete happens only once that window closes.
 */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    val deleteLabel = stringResource(R.string.history_delete_item)

    // The swipe state is saveable, so a row brought back by Undo is restored
    // still swiped away. Without this flag it would delete itself again the
    // moment it reappeared; with it, it slides back into place instead.
    var restoredDismissed by remember {
        mutableStateOf(dismissState.currentValue == SwipeToDismissBoxValue.EndToStart)
    }

    // Reacting to the settled value rather than vetoing the change in
    // confirmValueChange, which is deprecated.
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.EndToStart) return@LaunchedEffect
        if (restoredDismissed) {
            restoredDismissed = false
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        } else {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val isDeleting = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val color by animateColorAsState(
                targetValue = if (isDeleting) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                label = "swipeBackground"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isDeleting) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = deleteLabel,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        content()
    }
}

@Composable
private fun HistoryItem(
    row: HistoryRow,
    relativeTime: RelativeTimeFormatter,
    now: Long,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val colors = rememberTypeColors(row.type)
    val typeLabel = stringResource(row.type.labelRes)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.container, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = row.type.icon,
                    contentDescription = null,
                    tint = colors.onContainer,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // A title derived from the content — SSID, contact name, site —
                    // never the raw payload, which for Wi-Fi includes the password.
                    text = row.title ?: typeLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$typeLabel · ${relativeTime.format(row.entity.timestamp, now)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (row.entity.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = stringResource(
                        if (row.entity.pinned) R.string.history_unpin else R.string.history_pin
                    ),
                    tint = if (row.entity.pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/** Returns null on success, or the throwable when no app could take the file. */
private fun shareFile(
    context: Context,
    uri: Uri,
    mimeType: String,
    chooserTitle: String
): Throwable? = runCatching {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}.exceptionOrNull()
