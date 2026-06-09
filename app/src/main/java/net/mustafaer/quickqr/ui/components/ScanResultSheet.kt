package net.mustafaer.quickqr.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.utils.ScanResultType
import net.mustafaer.quickqr.utils.TypeDetector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultSheet(
    text: String,
    type: ScanResultType,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Wifi specific
    var wifiShowPassword by remember { mutableStateOf(false) }
    val wifiData = remember(text, type) {
        if (type == ScanResultType.WIFI) TypeDetector.parseWifi(text) else null
    }

    // Contact specific
    val vcardName = remember(text, type) {
        if (type == ScanResultType.VCARD) TypeDetector.parseVCardName(text) else null
    }

    // Event specific
    val eventSummary = remember(text, type) {
        if (type == ScanResultType.CALENDAR) TypeDetector.parseEventSummary(text) else null
    }

    // Badge styling mapping
    val badgeColor = when (type) {
        ScanResultType.URL -> Color(0xFF5B3FF2)
        ScanResultType.WIFI -> Color(0xFFE5A93B)
        ScanResultType.EMAIL -> Color(0xFF43A047)
        ScanResultType.PHONE -> Color(0xFF1E88E5)
        ScanResultType.GEO -> Color(0xFFE53935)
        ScanResultType.SMS -> Color(0xFF00ACC1)
        ScanResultType.VCARD -> Color(0xFF8E24AA)
        ScanResultType.CALENDAR -> Color(0xFFF4511E)
        ScanResultType.TEXT -> Color(0xFF757575)
    }

    val typeLabel = when (type) {
        ScanResultType.URL -> stringResource(R.string.type_url)
        ScanResultType.WIFI -> stringResource(R.string.type_wifi)
        ScanResultType.EMAIL -> stringResource(R.string.type_email)
        ScanResultType.PHONE -> stringResource(R.string.type_phone)
        ScanResultType.GEO -> stringResource(R.string.type_geo)
        ScanResultType.SMS -> stringResource(R.string.type_sms)
        ScanResultType.VCARD -> stringResource(R.string.type_vcard)
        ScanResultType.CALENDAR -> stringResource(R.string.type_calendar)
        ScanResultType.TEXT -> stringResource(R.string.type_text)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.scan_result_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Type Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeColor)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = typeLabel,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Custom Display Card based on QR content type
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    when (type) {
                        ScanResultType.WIFI -> {
                            if (wifiData != null) {
                                Row(Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        "${stringResource(R.string.wifi_network)}: ",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(wifiData.ssid, style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        "${stringResource(R.string.wifi_security)}: ",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(wifiData.security, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (wifiData.password.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${stringResource(R.string.wifi_password)}: ",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = if (wifiShowPassword) wifiData.password else "••••••••",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { wifiShowPassword = !wifiShowPassword },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (wifiShowPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                contentDescription = if (wifiShowPassword) stringResource(R.string.wifi_hide_password) else stringResource(R.string.wifi_show_password),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                if (wifiData.hidden) {
                                    Text(
                                        stringResource(R.string.wifi_hidden),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        ScanResultType.VCARD -> {
                            Text(
                                text = vcardName ?: stringResource(R.string.type_vcard),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ScanResultType.CALENDAR -> {
                            Text(
                                text = eventSummary ?: stringResource(R.string.type_calendar),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Quick Actions Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Copy Action Button
                Button(
                    onClick = {
                        val textToCopy = if (type == ScanResultType.WIFI && wifiData != null) {
                            // Copying password specifically or entire wifi raw text?
                            // Let's copy the raw QR text, or if wifi has password copy password.
                            // The original app copied password if wifi, copy text if others. Let's offer both copy pass and copy all!
                            if (wifiData.password.isNotEmpty()) wifiData.password else text
                        } else {
                            text
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("QuickQR Result", textToCopy)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.action_copied), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (type == ScanResultType.WIFI && wifiData?.password?.isNotEmpty() == true) {
                            stringResource(R.string.action_copy_password)
                        } else {
                            stringResource(R.string.action_copy)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // If WIFI type and password exists, copy raw text (Copy All)
                if (type == ScanResultType.WIFI && wifiData?.password?.isNotEmpty() == true) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("QuickQR Raw", text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.action_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy_all),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_copy_all),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Primary Action Button (Open URL, connect mail, dial etc.)
            if (isActionable(type)) {
                val actionUrl = remember(text, type) { TypeDetector.getActionUrl(text, type) }
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open action", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val icon = when (type) {
                        ScanResultType.URL -> Icons.Outlined.OpenInBrowser
                        ScanResultType.EMAIL -> Icons.Outlined.Email
                        ScanResultType.PHONE -> Icons.Outlined.Call
                        ScanResultType.SMS -> Icons.Outlined.Sms
                        ScanResultType.GEO -> Icons.Outlined.Map
                        else -> Icons.Outlined.OpenInBrowser
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(R.string.action_open),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_open), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun isActionable(type: ScanResultType): Boolean {
    return when (type) {
        ScanResultType.URL,
        ScanResultType.EMAIL,
        ScanResultType.PHONE,
        ScanResultType.SMS,
        ScanResultType.GEO -> true
        else -> false
    }
}
