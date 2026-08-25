package net.mustafaer.quickqr.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.utils.QrPayloadDraft
import net.mustafaer.quickqr.utils.QrPayloadType
import net.mustafaer.quickqr.utils.QrResult
import net.mustafaer.quickqr.utils.WifiSecurity

@Composable
fun GeneratorScreen(
    draft: QrPayloadDraft,
    result: QrResult,
    onDraftChange: (QrPayloadDraft) -> Unit,
    onGenerate: () -> Unit,
    onClear: () -> Unit,
    onSave: suspend () -> Uri?,
    onShare: suspend () -> Uri?,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val savedMsg = stringResource(R.string.generator_saved)
    val saveFailedMsg = stringResource(R.string.generator_save_failed)
    val shareFailedMsg = stringResource(R.string.generator_share_failed)
    val storagePermissionMsg = stringResource(R.string.error_storage_permission)
    val shareChooserTitle = stringResource(R.string.generator_share_title)

    fun notify(message: String) = scope.launch {
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message)
    }

    fun save() = scope.launch {
        notify(if (onSave() != null) savedMsg else saveFailedMsg)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) save() else notify(storagePermissionMsg)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(contentPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.generator_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
            )

            ContentTypeSelector(
                selected = draft.type,
                onSelect = { type -> onDraftChange(draft.copy(type = type)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PayloadForm(
                draft = draft,
                onDraftChange = onDraftChange,
                onSubmit = {
                    focusManager.clearFocus()
                    onGenerate()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onGenerate()
                    },
                    enabled = draft.isComplete,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.QrCode, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.generator_generate),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        onClear()
                    },
                    enabled = draft.isComplete,
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.generator_clear)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (result) {
                is QrResult.Success -> QrPreviewCard(
                    result = result,
                    onSaveClick = {
                        // Below Android 10 there is no scoped-storage collection to
                        // write into, so the legacy permission is still required.
                        val needsLegacyPermission =
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) != PackageManager.PERMISSION_GRANTED
                        if (needsLegacyPermission) {
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            save()
                        }
                    },
                    onShareClick = {
                        scope.launch {
                            val shareUri = onShare()
                            if (shareUri == null) {
                                notify(shareFailedMsg)
                                return@launch
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                clipData = ClipData.newRawUri("", shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(intent, shareChooserTitle)
                                )
                            }.onFailure { notify(shareFailedMsg) }
                        }
                    }
                )

                is QrResult.TooLong -> GeneratorMessage(
                    isError = true,
                    message = pluralStringResource(
                        R.plurals.generator_error_too_long,
                        result.byteCount,
                        result.byteCount,
                        result.maxBytes
                    )
                )

                QrResult.Failed -> GeneratorMessage(
                    isError = true,
                    message = stringResource(R.string.generator_error_failed)
                )

                QrResult.Empty -> GeneratorMessage(
                    isError = false,
                    message = stringResource(R.string.generator_empty_subtitle)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
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
private fun ContentTypeSelector(
    selected: QrPayloadType,
    onSelect: (QrPayloadType) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QrPayloadType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(stringResource(type.labelRes())) },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

private fun QrPayloadType.labelRes(): Int = when (this) {
    QrPayloadType.TEXT -> R.string.type_text
    QrPayloadType.URL -> R.string.type_url
    QrPayloadType.WIFI -> R.string.type_wifi
    QrPayloadType.EMAIL -> R.string.type_email
    QrPayloadType.PHONE -> R.string.type_phone
    QrPayloadType.SMS -> R.string.type_sms
}

@Composable
private fun PayloadForm(
    draft: QrPayloadDraft,
    onDraftChange: (QrPayloadDraft) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (draft.type) {
            QrPayloadType.TEXT -> GeneratorField(
                value = draft.text,
                onValueChange = { onDraftChange(draft.copy(text = it)) },
                label = stringResource(R.string.generator_field_text),
                singleLine = false,
                maxLines = 5,
                imeAction = ImeAction.Default
            )

            QrPayloadType.URL -> GeneratorField(
                value = draft.url,
                onValueChange = { onDraftChange(draft.copy(url = it)) },
                label = stringResource(R.string.generator_field_url),
                keyboardType = KeyboardType.Uri,
                onSubmit = onSubmit
            )

            QrPayloadType.WIFI -> {
                GeneratorField(
                    value = draft.wifiSsid,
                    onValueChange = { onDraftChange(draft.copy(wifiSsid = it)) },
                    label = stringResource(R.string.generator_field_ssid)
                )
                WifiSecuritySelector(
                    selected = draft.wifiSecurity,
                    onSelect = { onDraftChange(draft.copy(wifiSecurity = it)) }
                )
                if (draft.wifiSecurity != WifiSecurity.NONE) {
                    GeneratorField(
                        value = draft.wifiPassword,
                        onValueChange = { onDraftChange(draft.copy(wifiPassword = it)) },
                        label = stringResource(R.string.generator_field_password),
                        isPassword = true,
                        onSubmit = onSubmit
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.generator_field_hidden),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = draft.wifiHidden,
                        onCheckedChange = { onDraftChange(draft.copy(wifiHidden = it)) }
                    )
                }
            }

            QrPayloadType.EMAIL -> {
                GeneratorField(
                    value = draft.emailAddress,
                    onValueChange = { onDraftChange(draft.copy(emailAddress = it)) },
                    label = stringResource(R.string.generator_field_email),
                    keyboardType = KeyboardType.Email
                )
                GeneratorField(
                    value = draft.emailSubject,
                    onValueChange = { onDraftChange(draft.copy(emailSubject = it)) },
                    label = stringResource(R.string.generator_field_subject)
                )
                GeneratorField(
                    value = draft.emailBody,
                    onValueChange = { onDraftChange(draft.copy(emailBody = it)) },
                    label = stringResource(R.string.generator_field_body),
                    singleLine = false,
                    maxLines = 4,
                    imeAction = ImeAction.Default
                )
            }

            QrPayloadType.PHONE -> GeneratorField(
                value = draft.phoneNumber,
                onValueChange = { onDraftChange(draft.copy(phoneNumber = it)) },
                label = stringResource(R.string.generator_field_phone),
                keyboardType = KeyboardType.Phone,
                onSubmit = onSubmit
            )

            QrPayloadType.SMS -> {
                GeneratorField(
                    value = draft.smsNumber,
                    onValueChange = { onDraftChange(draft.copy(smsNumber = it)) },
                    label = stringResource(R.string.generator_field_sms_number),
                    keyboardType = KeyboardType.Phone
                )
                GeneratorField(
                    value = draft.smsMessage,
                    onValueChange = { onDraftChange(draft.copy(smsMessage = it)) },
                    label = stringResource(R.string.generator_field_message),
                    singleLine = false,
                    maxLines = 4,
                    imeAction = ImeAction.Default
                )
            }
        }
    }
}

@Composable
private fun GeneratorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onSubmit: (() -> Unit)? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = singleLine,
        maxLines = maxLines,
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = {
            when {
                isPassword -> IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.wifi_hide_password
                            else R.string.wifi_show_password
                        )
                    )
                }
                value.isNotEmpty() -> IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.generator_clear_field)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (onSubmit != null) ImeAction.Done else imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                onSubmit?.invoke()
            },
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        )
    )
}

@Composable
private fun WifiSecuritySelector(
    selected: WifiSecurity,
    onSelect: (WifiSecurity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.wifi_security),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WifiSecurity.entries.forEach { security ->
                FilterChip(
                    selected = security == selected,
                    onClick = { onSelect(security) },
                    label = { Text(stringResource(security.labelRes())) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    }
}

private fun WifiSecurity.labelRes(): Int = when (this) {
    WifiSecurity.WPA -> R.string.wifi_security_wpa
    WifiSecurity.WEP -> R.string.wifi_security_wep
    WifiSecurity.NONE -> R.string.wifi_security_open
}

@Composable
private fun QrPreviewCard(
    result: QrResult.Success,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit
) {
    // Converting the bitmap once per bitmap rather than once per recomposition.
    val imageBitmap = remember(result.bitmap) { result.bitmap.asImageBitmap() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    // Always a white quiet zone: a QR on a themed background is a
                    // QR some scanners refuse to read.
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = stringResource(R.string.generator_qr_image),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Default button content padding is 24dp a side, which left too
                // little room for a label plus icon once the text was German or
                // Turkish rather than English.
                val tightPadding = PaddingValues(horizontal = 12.dp)

                Button(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = tightPadding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        Icons.Outlined.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.generator_save),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onShareClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = tightPadding
                ) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.generator_share),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratorMessage(isError: Boolean, message: String) {
    val tint = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isError) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.QrCode,
                contentDescription = null,
                modifier = Modifier.size(if (isError) 48.dp else 64.dp),
                tint = if (isError) tint else tint.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = tint,
                textAlign = TextAlign.Center
            )
        }
    }
}
