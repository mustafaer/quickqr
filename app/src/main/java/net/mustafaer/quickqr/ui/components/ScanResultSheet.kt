package net.mustafaer.quickqr.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.net.toUri
import androidx.core.os.ConfigurationCompat
import kotlinx.coroutines.launch
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.ui.theme.labelRes
import net.mustafaer.quickqr.ui.theme.rememberTypeColors
import net.mustafaer.quickqr.utils.EventData
import net.mustafaer.quickqr.utils.LinkVerdict
import net.mustafaer.quickqr.utils.PaymentRequest
import net.mustafaer.quickqr.utils.ScanContent
import net.mustafaer.quickqr.utils.ScanResultType
import net.mustafaer.quickqr.utils.TypeDetector
import net.mustafaer.quickqr.utils.VCardData
import net.mustafaer.quickqr.utils.WifiData
import net.mustafaer.quickqr.utils.WifiSecurity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hostnames, addresses and payloads read left to right even inside Arabic text. */
private val LtrMonospace = TextStyle(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr)

/** Shown in place of a password, whatever its length — the old mask revealed it up to 12. */
private const val PASSWORD_MASK = "••••••••"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultSheet(content: ScanContent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordRevealed by rememberSaveable(content.raw) { mutableStateOf(false) }

    fun notify(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val copiedMsg = stringResource(R.string.action_copied)
    fun copy(value: String, sensitive: Boolean = false) {
        copyToClipboard(context, value, sensitive)
        // Android 13+ shows its own copy confirmation; a second one would be noise.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) notify(copiedMsg)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // A revealed Wi-Fi password stays out of screenshots and the recents thumbnail.
        properties = ModalBottomSheetProperties(
            securePolicy = if (passwordRevealed) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit
        )
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.scan_result_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                TypeBadge(content.type)
                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (content) {
                            is ScanContent.Link -> LinkDetails(content)
                            is ScanContent.Wifi -> WifiDetails(
                                wifi = content.wifi,
                                revealed = passwordRevealed,
                                onToggleReveal = { passwordRevealed = !passwordRevealed }
                            )
                            is ScanContent.Contact -> ContactDetails(content.card)
                            is ScanContent.Event -> EventDetails(content.event)
                            is ScanContent.Payment -> PaymentDetails(content.payment)
                            is ScanContent.Other -> Text(
                                text = content.raw,
                                style = if (content.type == ScanResultType.TEXT) {
                                    MaterialTheme.typography.bodyLarge
                                } else {
                                    MaterialTheme.typography.bodyLarge.merge(LtrMonospace)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                SheetActions(
                    content = content,
                    onCopy = ::copy,
                    onFailure = ::notify
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// --- Header ------------------------------------------------------------------

@Composable
private fun TypeBadge(type: ScanResultType) {
    val colors = rememberTypeColors(type)
    Surface(color = colors.container, contentColor = colors.onContainer, shape = RoundedCornerShape(12.dp)) {
        Text(
            text = stringResource(type.labelRes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

// --- Details -----------------------------------------------------------------

/**
 * The destination comes first and big — the registrable domain in bold, any
 * subdomain dimmed — because that, not the string as written, is where the
 * link goes. `paypal.com.secure-login.xyz` reads as `secure-login.xyz`.
 */
@Composable
private fun LinkDetails(link: ScanContent.Link) {
    val verdict = link.verdict
    DetailLabel(R.string.link_destination)
    if (verdict != null) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(verdict.subdomainPrefix)
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(verdict.registrableDomain) }
            },
            style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
    Text(
        text = link.url,
        style = MaterialTheme.typography.bodySmall.merge(LtrMonospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis
    )
    verdict?.flags
        ?.sortedByDescending { it.severe }
        ?.forEach { flag -> FlagRow(flag.message(verdict), flag.severe) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.link_checked_offline),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LinkVerdict.Flag.message(verdict: LinkVerdict): String {
    val brand = verdict.imitatedBrand.orEmpty()
    return when (this) {
        LinkVerdict.Flag.USERINFO -> stringResource(R.string.link_flag_userinfo, verdict.registrableDomain)
        LinkVerdict.Flag.MIXED_SCRIPT -> stringResource(R.string.link_flag_mixed_script)
        LinkVerdict.Flag.LOOKALIKE -> stringResource(R.string.link_flag_lookalike, brand)
        LinkVerdict.Flag.BRAND_IN_SUBDOMAIN -> stringResource(R.string.link_flag_brand_in_subdomain, brand)
        LinkVerdict.Flag.PUBLIC_IP -> stringResource(R.string.link_flag_public_ip)
        LinkVerdict.Flag.CONFUSABLE_SCRIPT -> stringResource(R.string.link_flag_confusable_script)
        LinkVerdict.Flag.BRAND_NAME -> stringResource(R.string.link_flag_brand_name, brand)
        LinkVerdict.Flag.LOCAL_IP -> stringResource(R.string.link_flag_local_ip)
        LinkVerdict.Flag.INSECURE -> stringResource(R.string.link_flag_insecure)
        LinkVerdict.Flag.NON_STANDARD_PORT -> stringResource(R.string.link_flag_non_standard_port)
        LinkVerdict.Flag.SHORTENER -> stringResource(R.string.link_flag_shortener)
    }
}

@Composable
private fun WifiDetails(wifi: WifiData, revealed: Boolean, onToggleReveal: () -> Unit) {
    DetailRow(stringResource(R.string.wifi_network), wifi.ssid, valueStyle = LtrMonospace)
    DetailRow(
        stringResource(R.string.wifi_security),
        stringResource(WifiSecurity.fromQrValue(wifi.security).labelRes())
    )
    if (wifi.password.isNotEmpty()) {
        DetailRow(
            label = stringResource(R.string.wifi_password),
            value = if (revealed) wifi.password else PASSWORD_MASK,
            valueStyle = LtrMonospace,
            trailing = {
                IconButton(onClick = onToggleReveal) {
                    Icon(
                        imageVector = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (revealed) R.string.wifi_hide_password else R.string.wifi_show_password
                        ),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
    if (wifi.hidden) {
        Text(
            text = stringResource(R.string.wifi_hidden),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@StringRes
private fun WifiSecurity.labelRes(): Int = when (this) {
    WifiSecurity.WPA -> R.string.wifi_security_wpa
    WifiSecurity.SAE -> R.string.wifi_security_sae
    WifiSecurity.WEP -> R.string.wifi_security_wep
    WifiSecurity.NONE -> R.string.wifi_security_open
}

@Composable
private fun ContactDetails(card: VCardData) {
    Text(
        text = card.name ?: stringResource(R.string.type_vcard),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium
    )
    card.phones.forEach { DetailRow(stringResource(R.string.type_phone), it, valueStyle = LtrMonospace) }
    card.emails.forEach { DetailRow(stringResource(R.string.type_email), it, valueStyle = LtrMonospace) }
    card.organization?.let { DetailRow(stringResource(R.string.vcard_organization), it) }
    card.jobTitle?.let { DetailRow(stringResource(R.string.vcard_title), it) }
    card.website?.let { DetailRow(stringResource(R.string.vcard_website), it, valueStyle = LtrMonospace) }
    card.address?.let { DetailRow(stringResource(R.string.vcard_address), it) }
    card.birthday?.let { DetailRow(stringResource(R.string.vcard_birthday), it) }
    card.note?.let { DetailRow(stringResource(R.string.vcard_note), it) }
}

@Composable
private fun EventDetails(event: EventData) {
    // configChanges="locale" means no recreate on a language change, so the
    // formatters are keyed on the Compose configuration.
    val configuration = LocalConfiguration.current
    val format = remember(configuration, event.isAllDay) {
        val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
        SimpleDateFormat(if (event.isAllDay) "dd MMM yyyy" else "dd MMM yyyy, HH:mm", locale)
    }
    Text(
        text = event.summary ?: stringResource(R.string.event_untitled),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium
    )
    event.start?.let { DetailRow(stringResource(R.string.event_start), format.format(Date(it))) }
    event.end?.let { DetailRow(stringResource(R.string.event_end), format.format(Date(it))) }
    event.location?.let { DetailRow(stringResource(R.string.event_location), it) }
    event.description?.let { DetailRow(stringResource(R.string.event_description), it) }
}

/**
 * Who gets paid and how much — and, always, the fact that defeats the most
 * common UPI fraud: scanning a code can only send money, never receive it.
 */
@Composable
private fun PaymentDetails(payment: PaymentRequest) {
    FlagRow(stringResource(R.string.payment_warning), severe = false, icon = Icons.Outlined.Info)
    payment.payeeName?.let { DetailRow(stringResource(R.string.payment_payee_name), it) }
    payment.payeeAddress?.let {
        DetailRow(stringResource(R.string.payment_payee_address), it, valueStyle = LtrMonospace)
    }
    payment.amount?.let { amount ->
        val currency = payment.currency ?: "INR"
        val shown = if (currency == "INR") "₹$amount" else "$amount $currency"
        DetailRow(stringResource(R.string.payment_amount), shown, valueStyle = LtrMonospace)
    }
    payment.note?.let { DetailRow(stringResource(R.string.payment_note), it) }
    payment.flags.sortedByDescending { it.severe }.forEach { flag ->
        FlagRow(stringResource(flag.messageRes()), flag.severe)
    }
}

@StringRes
private fun PaymentRequest.Flag.messageRes(): Int = when (this) {
    PaymentRequest.Flag.MISSING_PAYEE -> R.string.payment_flag_missing_payee
    PaymentRequest.Flag.MALFORMED_PAYEE -> R.string.payment_flag_malformed_payee
    PaymentRequest.Flag.MALFORMED_AMOUNT -> R.string.payment_flag_malformed_amount
    PaymentRequest.Flag.AMOUNT_PREFILLED -> R.string.payment_flag_amount_prefilled
    PaymentRequest.Flag.RECURRING -> R.string.payment_flag_recurring
}

// --- Actions -----------------------------------------------------------------

@Composable
private fun SheetActions(
    content: ScanContent,
    onCopy: (value: String, sensitive: Boolean) -> Unit,
    onFailure: (String) -> Unit
) {
    val context = LocalContext.current
    val errOpen = stringResource(R.string.error_open_action)
    val errContact = stringResource(R.string.error_add_contact)
    val errEvent = stringResource(R.string.error_add_event)
    val errWifi = stringResource(R.string.error_wifi_connect)
    val wifiManual = stringResource(R.string.wifi_connect_manual)
    val eventFallbackTitle = stringResource(R.string.event_untitled)
    val shareTitle = stringResource(R.string.scan_share_title)

    fun attempt(error: String, action: () -> Unit) {
        runCatching(action).onFailure { onFailure(error) }
    }

    val share = {
        attempt(errOpen) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content.raw)
            }
            context.startActivity(Intent.createChooser(intent, shareTitle))
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (content) {
            is ScanContent.Link -> {
                val open = { attempt(errOpen) { openLink(context, content.url) } }
                if (content.verdict?.isSevere == true) {
                    // A link that is probably lying about where it goes: copying is
                    // the safe default, opening becomes the deliberate choice.
                    PrimaryButton(Icons.Outlined.ContentCopy, stringResource(R.string.link_copy)) {
                        onCopy(content.url, false)
                    }
                    OutlinedButton(
                        onClick = open,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.link_open_anyway), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SecondaryButton(Icons.Outlined.Share, stringResource(R.string.action_share), share)
                } else {
                    PrimaryButton(Icons.Outlined.OpenInBrowser, stringResource(R.string.action_open), open)
                    SecondaryButton(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy)) {
                        onCopy(content.url, false)
                    }
                    SecondaryButton(Icons.Outlined.Share, stringResource(R.string.action_share), share)
                }
            }

            is ScanContent.Wifi -> {
                val password = content.wifi.password
                PrimaryButton(Icons.Outlined.Wifi, stringResource(R.string.action_connect_wifi)) {
                    attempt(errWifi) {
                        connectToWifi(context, content.wifi) {
                            if (password.isNotEmpty()) onCopy(password, true)
                            onFailure(wifiManual)
                        }
                    }
                }
                if (password.isNotEmpty()) {
                    SecondaryButton(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy_password)) {
                        onCopy(password, true)
                    }
                }
                SecondaryButton(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy_all)) {
                    onCopy(content.raw, password.isNotEmpty())
                }
            }

            is ScanContent.Contact -> {
                PrimaryButton(Icons.Outlined.PersonAdd, stringResource(R.string.action_add_contact)) {
                    attempt(errContact) { context.startActivity(addContactIntent(content.card)) }
                }
                SecondaryButton(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy)) {
                    onCopy(content.raw, false)
                }
                SecondaryButton(Icons.Outlined.Share, stringResource(R.string.action_share), share)
            }

            is ScanContent.Event -> {
                PrimaryButton(Icons.Outlined.CalendarToday, stringResource(R.string.action_add_calendar)) {
                    attempt(errEvent) { context.startActivity(addEventIntent(content.event, eventFallbackTitle)) }
                }
                SecondaryButton(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy)) {
                    onCopy(content.raw, false)
                }
                SecondaryButton(Icons.Outlined.Share, stringResource(R.string.action_share), share)
            }

            is ScanContent.Payment -> {
                // No pay button for a code with no valid payee: there is nothing
                // legitimate it could be asking for.
                if (!content.payment.isSevere) {
                    PrimaryButton(Icons.Outlined.CurrencyRupee, stringResource(R.string.action_pay)) {
                        attempt(errOpen) { context.startActivity(Intent(Intent.ACTION_VIEW, content.raw.trim().toUri())) }
                    }
                }
                SecondaryButton(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy)) {
                    onCopy(content.raw, false)
                }
            }

            is ScanContent.Other -> {
                otherActionIcon(content.type)?.let { icon ->
                    PrimaryButton(icon, stringResource(R.string.action_open)) {
                        attempt(errOpen) { context.startActivity(otherActionIntent(content)) }
                    }
                }
                SecondaryButton(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy)) {
                    onCopy(content.raw, false)
                }
                SecondaryButton(Icons.Outlined.Share, stringResource(R.string.action_share), share)
            }
        }
    }
}

private fun otherActionIcon(type: ScanResultType): ImageVector? = when (type) {
    ScanResultType.EMAIL -> Icons.Outlined.Email
    ScanResultType.PHONE -> Icons.Outlined.Call
    ScanResultType.SMS -> Icons.Outlined.Sms
    ScanResultType.GEO -> Icons.Outlined.Map
    else -> null
}

private fun otherActionIntent(content: ScanContent.Other): Intent {
    val actionUrl = TypeDetector.getActionUrl(content.raw, content.type)
    return when (content.type) {
        // DIAL, never CALL: the user still presses the call button, so a code
        // carrying a premium number or a USSD sequence cannot dial by itself.
        ScanResultType.PHONE -> Intent(Intent.ACTION_DIAL, actionUrl.toUri())
        ScanResultType.EMAIL -> {
            val uri = actionUrl.toUri()
            Intent(Intent.ACTION_SENDTO, uri).apply {
                uri.getQueryParameter("subject")?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                uri.getQueryParameter("body")?.let { putExtra(Intent.EXTRA_TEXT, it) }
            }
        }
        ScanResultType.SMS -> {
            val uri = (if (actionUrl.startsWith("sms:", ignoreCase = true)) "smsto:${actionUrl.substring(4)}" else actionUrl)
                .toUri()
            Intent(Intent.ACTION_SENDTO, uri).apply {
                uri.getQueryParameter("body")?.let {
                    putExtra("sms_body", it)
                    putExtra(Intent.EXTRA_TEXT, it)
                }
            }
        }
        else -> Intent(Intent.ACTION_VIEW, actionUrl.toUri())
    }
}

/**
 * Only ever http(s) or ftp(s) reaches here — [TypeDetector] classifies nothing
 * else as a link — so `intent:`, `javascript:` and app schemes cannot be
 * launched from a scanned code.
 */
private fun openLink(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
}

/**
 * Hands the network to the system's "add network" dialog on Android 11+, which
 * needs no extra permission and lets the user confirm before anything is saved.
 * WEP has no suggestion API and older releases have no dialog, so those copy the
 * password and open Wi-Fi settings instead.
 */
private fun connectToWifi(context: Context, wifi: WifiData, onManualFallback: () -> Unit) {
    require(wifi.ssid.isNotEmpty()) { "No network name in the scanned code" }
    val security = WifiSecurity.fromQrValue(wifi.security)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && security != WifiSecurity.WEP) {
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(wifi.ssid)
            .setIsHiddenSsid(wifi.hidden)
            .apply {
                if (wifi.password.isNotEmpty()) {
                    when (security) {
                        WifiSecurity.SAE -> setWpa3Passphrase(wifi.password)
                        WifiSecurity.WPA -> setWpa2Passphrase(wifi.password)
                        WifiSecurity.WEP, WifiSecurity.NONE -> Unit
                    }
                }
            }
            .build()
        context.startActivity(
            Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
        )
    } else {
        onManualFallback()
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}

/**
 * Passes every phone number and address, not just the first two, plus the
 * website and birthday the plain extras have no slot for.
 */
private fun addContactIntent(card: VCardData): Intent {
    val rows = ArrayList<ContentValues>()
    card.phones.forEach { number ->
        rows += ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            put(CommonDataKinds.Phone.NUMBER, number)
            put(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_OTHER)
        }
    }
    card.emails.forEach { address ->
        rows += ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            put(CommonDataKinds.Email.ADDRESS, address)
            put(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_OTHER)
        }
    }
    card.website?.let { url ->
        rows += ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Website.CONTENT_ITEM_TYPE)
            put(CommonDataKinds.Website.URL, url)
            put(CommonDataKinds.Website.TYPE, CommonDataKinds.Website.TYPE_HOMEPAGE)
        }
    }
    card.birthday?.let(::normaliseBirthday)?.let { date ->
        rows += ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE)
            put(CommonDataKinds.Event.START_DATE, date)
            put(CommonDataKinds.Event.TYPE, CommonDataKinds.Event.TYPE_BIRTHDAY)
        }
    }
    return Intent(Intent.ACTION_INSERT).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        card.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
        card.organization?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
        card.jobTitle?.let { putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
        card.address?.let { putExtra(ContactsContract.Intents.Insert.POSTAL, it) }
        card.note?.let { putExtra(ContactsContract.Intents.Insert.NOTES, it) }
        if (rows.isNotEmpty()) putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, rows)
    }
}

/** vCard writes `19900131` or `1990-01-31`; the contacts provider wants the latter. */
private fun normaliseBirthday(value: String): String? {
    val digits = value.trim()
    return when {
        Regex("""\d{4}-\d{2}-\d{2}""").matches(digits) -> digits
        Regex("""\d{8}""").matches(digits) ->
            "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
        else -> null
    }
}

private fun addEventIntent(event: EventData, fallbackTitle: String): Intent =
    Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, event.summary ?: fallbackTitle)
        event.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
        event.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        event.start?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        event.end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
        if (event.isAllDay) putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
    }

/**
 * Marks secrets as sensitive so Android 13+ does not echo them in its clipboard
 * preview overlay — where a copied Wi-Fi password used to appear in plain text.
 */
private fun copyToClipboard(context: Context, value: String, sensitive: Boolean) {
    val clip = ClipData.newPlainText("QuickQR", value)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

// --- Building blocks ---------------------------------------------------------

@Composable
private fun PrimaryButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Stacked rather than side by side: "Copy password" becomes "Parolayı kopyala"
 * and "Passwort kopieren", neither of which fits half a row.
 */
@Composable
private fun SecondaryButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FlagRow(message: String, severe: Boolean, icon: ImageVector? = null) {
    val container = if (severe) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val onContainer = if (severe) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = container, contentColor = onContainer, shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon ?: if (severe) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DetailLabel(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Label above value rather than side by side: a fixed label column clipped
 * "Beschreibung", "Organizasyon" and their Hindi and Arabic equivalents.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueStyle: TextStyle = TextStyle.Default,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.merge(valueStyle),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
    }
}
