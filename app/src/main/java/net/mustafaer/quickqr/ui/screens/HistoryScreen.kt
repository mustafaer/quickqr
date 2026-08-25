package net.mustafaer.quickqr.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.core.os.ConfigurationCompat
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.data.ScanEntity
import net.mustafaer.quickqr.utils.ScanResultType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyList: List<ScanEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onItemSelect: (ScanEntity) -> Unit,
    onItemDelete: (ScanEntity) -> Unit,
    onClearAll: () -> Unit,
    onExport: suspend (format: String) -> Uri?,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showExportMenu by rememberSaveable { mutableStateOf(false) }

    val exportFailedMsg = stringResource(R.string.error_share_history)
    val exportEmptyMsg = stringResource(R.string.history_export_empty)
    val shareChooserTitle = stringResource(R.string.history_share_title)

    // The activity declares configChanges="locale", so it is never recreated when
    // the language changes — the formatter has to be keyed on the Compose
    // configuration or dates would keep rendering in the previous language.
    val configuration = LocalConfiguration.current
    val dateFormat = remember(configuration) {
        val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
        SimpleDateFormat("dd MMM yyyy, HH:mm", locale)
    }

    fun export(format: String, mimeType: String) {
        showExportMenu = false
        scope.launch {
            val uri = onExport(format)
            val message = when {
                // Null with an empty list means "nothing to export"; null with
                // items in the list means the write itself failed. Either way the
                // user now hears about it instead of the menu just closing.
                uri == null && historyList.isEmpty() -> exportEmptyMsg
                uri == null -> exportFailedMsg
                else -> shareFile(context, uri, mimeType, shareChooserTitle)?.let { exportFailedMsg }
            }
            if (message != null) {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (historyList.isNotEmpty() || searchQuery.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = stringResource(R.string.history_export)
                                )
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_export_json)) },
                                    onClick = { export("json", "application/json") }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_export_csv)) },
                                    onClick = { export("csv", "text/csv") }
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
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.history_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.history_search_clear)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (historyList.isEmpty()) {
                Box(
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                stringResource(R.string.history_empty)
                            } else {
                                stringResource(R.string.history_no_items)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items = historyList, key = { it.id }) { scan ->
                        val deleteLabel = stringResource(R.string.history_delete_item)
                        val dismissState = rememberSwipeToDismissBoxState()

                        // Reacting to the settled value rather than vetoing the
                        // change in confirmValueChange, which is deprecated. The
                        // row leaves the list on delete, so this composable is
                        // disposed and the state never needs resetting.
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                onItemDelete(scan)
                            }
                        }

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val isDeleting =
                                    dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                val color by animateColorAsState(
                                    targetValue = if (isDeleting) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        Color.Transparent
                                    },
                                    label = "swipeBackground"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(color)
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
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            HistoryItem(
                                scan = scan,
                                formattedDate = dateFormat.format(Date(scan.timestamp)),
                                onClick = { onItemSelect(scan) }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
        )
    }
}

@Composable
fun HistoryItem(
    scan: ScanEntity,
    formattedDate: String,
    onClick: () -> Unit
) {
    val type = remember(scan.type) { ScanResultType.fromString(scan.type) }
    val (icon, color) = typeVisuals(type)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scan.text.trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Icon and accent colour per scan type. */
private fun typeVisuals(type: ScanResultType) = when (type) {
    ScanResultType.URL -> Icons.Outlined.Link to Color(0xFF5B3FF2)
    ScanResultType.WIFI -> Icons.Outlined.Wifi to Color(0xFFE5A93B)
    ScanResultType.EMAIL -> Icons.Outlined.Email to Color(0xFF43A047)
    ScanResultType.PHONE -> Icons.Outlined.Call to Color(0xFF1E88E5)
    ScanResultType.GEO -> Icons.Outlined.Map to Color(0xFFE53935)
    ScanResultType.SMS -> Icons.Outlined.Sms to Color(0xFF00ACC1)
    ScanResultType.VCARD -> Icons.Outlined.Person to Color(0xFF8E24AA)
    ScanResultType.CALENDAR -> Icons.Outlined.Event to Color(0xFFF4511E)
    ScanResultType.TEXT -> Icons.Outlined.TextFormat to Color(0xFF757575)
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
