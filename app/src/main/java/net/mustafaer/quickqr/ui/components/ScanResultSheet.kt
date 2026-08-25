package net.mustafaer.quickqr.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.os.ConfigurationCompat
import kotlinx.coroutines.launch
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.utils.ScanResultType
import net.mustafaer.quickqr.utils.TypeDetector
import net.mustafaer.quickqr.utils.WifiSecurity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultSheet(
    text: String,
    type: ScanResultType,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val actionCopiedMsg = stringResource(R.string.action_copied)
    val errOpenActionMsg = stringResource(R.string.error_open_action)
    val errAddContactMsg = stringResource(R.string.error_add_contact)
    val errAddEventMsg = stringResource(R.string.error_add_event)
    val errWifiMsg = stringResource(R.string.error_wifi_connect)
    val wifiManualMsg = stringResource(R.string.wifi_connect_manual)
    val eventFallbackTitle = stringResource(R.string.event_untitled)
    val shareChooserTitle = stringResource(R.string.scan_share_title)

    var wifiShowPassword by rememberSaveable { mutableStateOf(false) }

    val wifiData = remember(text, type) {
        if (type == ScanResultType.WIFI) TypeDetector.parseWifi(text) else null
    }
    val vCard = remember(text, type) {
        if (type == ScanResultType.VCARD) TypeDetector.parseVCard(text) else null
    }
    val event = remember(text, type) {
        if (type == ScanResultType.CALENDAR) TypeDetector.parseEvent(text) else null
    }

    val configuration = LocalConfiguration.current
    val dateTimeFormat = remember(configuration) {
        val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
        SimpleDateFormat("dd MMM yyyy, HH:mm", locale)
    }
    val dateOnlyFormat = remember(configuration) {
        val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
        SimpleDateFormat("dd MMM yyyy", locale)
    }

    fun formatEventTime(millis: Long): String =
        if (event?.isAllDay == true) {
            dateOnlyFormat.format(Date(millis))
        } else {
            dateTimeFormat.format(Date(millis))
        }

    val snackbarHostState = remember { SnackbarHostState() }
    fun notify(message: String) = scope.launch {
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message)
    }

    fun copyToClipboard(value: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        // Android 13+ shows its own copy confirmation; a second one would be noise.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) notify(actionCopiedMsg)
    }

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

    val typeLabel = stringResource(
        when (type) {
            ScanResultType.URL -> R.string.type_url
            ScanResultType.WIFI -> R.string.type_wifi
            ScanResultType.EMAIL -> R.string.type_email
            ScanResultType.PHONE -> R.string.type_phone
            ScanResultType.GEO -> R.string.type_geo
            ScanResultType.SMS -> R.string.type_sms
            ScanResultType.VCARD -> R.string.type_vcard
            ScanResultType.CALENDAR -> R.string.type_calendar
            ScanResultType.TEXT -> R.string.type_text
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box {
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

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        when {
                            type == ScanResultType.WIFI && wifiData != null -> {
                                DetailRow(
                                    label = stringResource(R.string.wifi_network),
                                    value = wifiData.ssid
                                )
                                DetailRow(
                                    label = stringResource(R.string.wifi_security),
                                    value = stringResource(
                                        when (WifiSecurity.fromQrValue(wifiData.security)) {
                                            WifiSecurity.WPA -> R.string.wifi_security_wpa
                                            WifiSecurity.WEP -> R.string.wifi_security_wep
                                            WifiSecurity.NONE -> R.string.wifi_security_open
                                        }
                                    )
                                )
                                if (wifiData.password.isNotEmpty()) {
                                    DetailRow(
                                        label = stringResource(R.string.wifi_password),
                                        value = if (wifiShowPassword) {
                                            wifiData.password
                                        } else {
                                            "•".repeat(wifiData.password.length.coerceAtMost(12))
                                        },
                                        trailing = {
                                            IconButton(
                                                onClick = { wifiShowPassword = !wifiShowPassword },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (wifiShowPassword) {
                                                        Icons.Outlined.VisibilityOff
                                                    } else {
                                                        Icons.Outlined.Visibility
                                                    },
                                                    contentDescription = stringResource(
                                                        if (wifiShowPassword) R.string.wifi_hide_password
                                                        else R.string.wifi_show_password
                                                    ),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                }
                                if (wifiData.hidden) {
                                    Text(
                                        text = stringResource(R.string.wifi_hidden),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }

                            type == ScanResultType.VCARD && vCard != null -> {
                                Text(
                                    text = vCard.name ?: stringResource(R.string.type_vcard),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                vCard.phones.forEach {
                                    DetailRow(stringResource(R.string.type_phone), it)
                                }
                                vCard.emails.forEach {
                                    DetailRow(stringResource(R.string.type_email), it)
                                }
                                vCard.organization?.let {
                                    DetailRow(stringResource(R.string.vcard_organization), it)
                                }
                                vCard.jobTitle?.let {
                                    DetailRow(stringResource(R.string.vcard_title), it)
                                }
                                vCard.website?.let {
                                    DetailRow(stringResource(R.string.vcard_website), it)
                                }
                                vCard.address?.let {
                                    DetailRow(stringResource(R.string.vcard_address), it)
                                }
                                vCard.birthday?.let {
                                    DetailRow(stringResource(R.string.vcard_birthday), it)
                                }
                                vCard.note?.let {
                                    DetailRow(stringResource(R.string.vcard_note), it)
                                }
                            }

                            type == ScanResultType.CALENDAR && event != null -> {
                                Text(
                                    text = event.summary ?: eventFallbackTitle,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                event.start?.let {
                                    DetailRow(
                                        stringResource(R.string.event_start),
                                        formatEventTime(it)
                                    )
                                }
                                event.end?.let {
                                    DetailRow(
                                        stringResource(R.string.event_end),
                                        formatEventTime(it)
                                    )
                                }
                                event.location?.let {
                                    DetailRow(stringResource(R.string.event_location), it)
                                }
                                event.description?.let {
                                    DetailRow(stringResource(R.string.event_description), it)
                                }
                            }

                            else -> Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // --- Primary action -------------------------------------------
                val primaryAction = primaryActionFor(type)
                if (primaryAction != null) {
                    PrimaryActionButton(
                        icon = primaryAction.icon,
                        label = stringResource(primaryAction.labelRes),
                        onClick = {
                            val failure = when (type) {
                                ScanResultType.WIFI -> connectToWifi(
                                    context = context,
                                    wifiData = wifiData,
                                    onManualFallback = {
                                        wifiData?.password?.takeIf { it.isNotEmpty() }?.let {
                                            copyToClipboard(it, "QuickQR Wi-Fi")
                                        }
                                        notify(wifiManualMsg)
                                    }
                                )
                                ScanResultType.VCARD -> addContact(context, vCard)
                                ScanResultType.CALENDAR -> addEvent(context, event, eventFallbackTitle)
                                else -> openAction(context, text, type)
                            }
                            if (failure != null) {
                                notify(
                                    when (type) {
                                        ScanResultType.WIFI -> errWifiMsg
                                        ScanResultType.VCARD -> errAddContactMsg
                                        ScanResultType.CALENDAR -> errAddEventMsg
                                        else -> errOpenActionMsg
                                    }
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // --- Secondary actions ----------------------------------------
                // Stacked rather than side by side: "Copy password" becomes
                // "Parolayı kopyala" and "Passwort kopieren", neither of which
                // fits half a row at any sensible font size.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val wifiPassword = wifiData?.password.orEmpty()
                    SecondaryActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.ContentCopy,
                        label = if (wifiPassword.isNotEmpty()) {
                            stringResource(R.string.action_copy_password)
                        } else {
                            stringResource(R.string.action_copy)
                        },
                        onClick = {
                            val value = wifiPassword.ifEmpty { text }
                            copyToClipboard(value, "QuickQR")
                        }
                    )

                    if (wifiPassword.isNotEmpty()) {
                        SecondaryActionButton(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.ContentCopy,
                            label = stringResource(R.string.action_copy_all),
                            onClick = { copyToClipboard(text, "QuickQR") }
                        )
                    } else {
                        SecondaryActionButton(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.Share,
                            label = stringResource(R.string.action_share),
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    // Qualified: the composable's `type` parameter
                                    // would otherwise shadow Intent.type here.
                                    this.type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(intent, shareChooserTitle)
                                    )
                                }.onFailure { notify(errOpenActionMsg) }
                            }
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// --- Actions -----------------------------------------------------------------

private class PrimaryAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelRes: Int
)

private fun primaryActionFor(type: ScanResultType): PrimaryAction? = when (type) {
    ScanResultType.URL -> PrimaryAction(Icons.Outlined.OpenInBrowser, R.string.action_open)
    ScanResultType.EMAIL -> PrimaryAction(Icons.Outlined.Email, R.string.action_open)
    ScanResultType.PHONE -> PrimaryAction(Icons.Outlined.Call, R.string.action_open)
    ScanResultType.SMS -> PrimaryAction(Icons.Outlined.Sms, R.string.action_open)
    ScanResultType.GEO -> PrimaryAction(Icons.Outlined.Map, R.string.action_open)
    ScanResultType.WIFI -> PrimaryAction(Icons.Outlined.Wifi, R.string.action_connect_wifi)
    ScanResultType.VCARD -> PrimaryAction(Icons.Outlined.PersonAdd, R.string.action_add_contact)
    ScanResultType.CALENDAR ->
        PrimaryAction(Icons.Outlined.CalendarToday, R.string.action_add_calendar)
    ScanResultType.TEXT -> null
}

/** Returns null on success, or the throwable that stopped the action. */
private fun openAction(context: Context, text: String, type: ScanResultType): Throwable? =
    runCatching {
        val actionUrl = TypeDetector.getActionUrl(text, type)
        val intent = when (type) {
            ScanResultType.PHONE -> Intent(Intent.ACTION_DIAL, actionUrl.toUri())

            ScanResultType.EMAIL -> {
                val emailUri = actionUrl.toUri()
                Intent(Intent.ACTION_SENDTO, emailUri).apply {
                    emailUri.getQueryParameter("subject")?.let {
                        putExtra(Intent.EXTRA_SUBJECT, it)
                    }
                    emailUri.getQueryParameter("body")?.let { putExtra(Intent.EXTRA_TEXT, it) }
                }
            }

            ScanResultType.SMS -> {
                val smsUri = if (actionUrl.startsWith("sms:", ignoreCase = true)) {
                    "smsto:${actionUrl.substring(4)}".toUri()
                } else {
                    actionUrl.toUri()
                }
                Intent(Intent.ACTION_SENDTO, smsUri).apply {
                    smsUri.getQueryParameter("body")?.let {
                        putExtra("sms_body", it)
                        putExtra(Intent.EXTRA_TEXT, it)
                    }
                }
            }

            // A scanned link is untrusted input, so it is handed to a browser
            // rather than to whichever installed app happens to claim the URL.
            else -> Intent(Intent.ACTION_VIEW, actionUrl.toUri()).apply {
                if (type == ScanResultType.URL) addCategory(Intent.CATEGORY_BROWSABLE)
            }
        }
        context.startActivity(intent)
    }.exceptionOrNull()

/**
 * Hands the network to the system's "add network" dialog on Android 11+, which
 * needs no extra permission and lets the user confirm before anything is saved.
 * Older releases have no equivalent, so the password is copied and Wi-Fi settings
 * are opened instead.
 */
private fun connectToWifi(
    context: Context,
    wifiData: net.mustafaer.quickqr.utils.WifiData?,
    onManualFallback: () -> Unit
): Throwable? {
    if (wifiData == null || wifiData.ssid.isEmpty()) {
        return IllegalArgumentException("No network name in the scanned code")
    }
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(wifiData.ssid)
                .apply {
                    setIsHiddenSsid(wifiData.hidden)
                    when (WifiSecurity.fromQrValue(wifiData.security)) {
                        WifiSecurity.WPA -> if (wifiData.password.isNotEmpty()) {
                            setWpa2Passphrase(wifiData.password)
                        }
                        WifiSecurity.WEP -> if (wifiData.password.isNotEmpty()) {
                            // WEP has no builder support; fall through to settings.
                            throw UnsupportedOperationException("WEP is not supported here")
                        }
                        WifiSecurity.NONE -> Unit
                    }
                }
                .build()

            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                putExtra(
                    Settings.EXTRA_WIFI_NETWORK_LIST,
                    arrayListOf(suggestion)
                )
            }
            context.startActivity(intent)
        } else {
            onManualFallback()
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }.exceptionOrNull()
}

private fun addContact(
    context: Context,
    vCard: net.mustafaer.quickqr.utils.VCardData?
): Throwable? = runCatching {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        vCard?.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
        vCard?.phones?.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        vCard?.phones?.getOrNull(1)?.let {
            putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it)
        }
        vCard?.emails?.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        vCard?.emails?.getOrNull(1)?.let {
            putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL, it)
        }
        vCard?.organization?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
        vCard?.jobTitle?.let { putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
        vCard?.address?.let { putExtra(ContactsContract.Intents.Insert.POSTAL, it) }
        vCard?.note?.let { putExtra(ContactsContract.Intents.Insert.NOTES, it) }
    }
    context.startActivity(intent)
}.exceptionOrNull()

private fun addEvent(
    context: Context,
    event: net.mustafaer.quickqr.utils.EventData?,
    fallbackTitle: String
): Throwable? = runCatching {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, event?.summary ?: fallbackTitle)
        event?.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
        event?.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        event?.start?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        event?.end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
        if (event?.isAllDay == true) putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
    }
    context.startActivity(intent)
}.exceptionOrNull()

// --- Building blocks ---------------------------------------------------------

@Composable
private fun PrimaryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SecondaryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Label above value rather than side by side.
 *
 * A fixed-width label column fit "Website" but clipped "Beschreibung",
 * "Organizasyon" and their Hindi and Arabic equivalents. Stacking removes the
 * constraint entirely, in every language and at every font scale.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 5.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
    }
}
