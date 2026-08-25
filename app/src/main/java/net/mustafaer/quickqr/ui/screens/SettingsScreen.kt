package net.mustafaer.quickqr.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import net.mustafaer.quickqr.BuildConfig
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.data.AppLanguage
import net.mustafaer.quickqr.data.ThemeMode

private const val PRIVACY_POLICY_URL = "https://medevstudios.com/quickqr/privacy-policy.html"
private const val STUDIO_URL = "https://medevstudios.com/"

@Composable
fun SettingsScreen(
    hapticEnabled: Boolean,
    continuousMode: Boolean,
    language: AppLanguage,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    onContinuousToggle: (Boolean) -> Unit,
    onLanguageSelect: (AppLanguage) -> Unit,
    onThemeModeSelect: (ThemeMode) -> Unit,
    onDynamicColorToggle: (Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLanguageMenu by rememberSaveable { mutableStateOf(false) }
    var showThemeMenu by rememberSaveable { mutableStateOf(false) }

    val noBrowserMsg = stringResource(R.string.error_no_browser)
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun openLink(url: String) {
        // A device with no browser used to make this tap do nothing at all.
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(noBrowserMsg)
            }
        }
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
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            SettingsGroup {
                SettingSwitchRow(
                    icon = Icons.Outlined.Vibration,
                    iconColor = Color(0xFF1E88E5),
                    title = stringResource(R.string.settings_haptic),
                    description = stringResource(R.string.settings_haptic_desc),
                    checked = hapticEnabled,
                    onCheckedChange = onHapticToggle
                )
                SettingsDivider()
                SettingSwitchRow(
                    icon = Icons.Outlined.Sync,
                    iconColor = Color(0xFF43A047),
                    title = stringResource(R.string.settings_continuous),
                    description = stringResource(R.string.settings_continuous_desc),
                    checked = continuousMode,
                    onCheckedChange = onContinuousToggle
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsGroup {
                // Theme
                SettingMenuRow(
                    icon = Icons.Outlined.DarkMode,
                    iconColor = Color(0xFF5B3FF2),
                    title = stringResource(R.string.settings_theme),
                    valueLabel = stringResource(themeMode.labelRes()),
                    expanded = showThemeMenu,
                    onExpandedChange = { showThemeMenu = it }
                ) {
                    ThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(mode.labelRes())) },
                            trailingIcon = {
                                if (mode == themeMode) {
                                    Icon(Icons.Outlined.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                onThemeModeSelect(mode)
                                showThemeMenu = false
                            }
                        )
                    }
                }

                if (supportsDynamicColor) {
                    SettingsDivider()
                    SettingSwitchRow(
                        icon = Icons.Outlined.ColorLens,
                        iconColor = Color(0xFFE5A93B),
                        title = stringResource(R.string.settings_dynamic_color),
                        description = stringResource(R.string.settings_dynamic_color_desc),
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorToggle
                    )
                }

                SettingsDivider()

                // Language. Every option is labelled in its own language so it is
                // readable to someone who cannot read the language in use.
                SettingMenuRow(
                    icon = Icons.Outlined.Language,
                    iconColor = Color(0xFF8E24AA),
                    title = stringResource(R.string.settings_language),
                    valueLabel = language.displayName,
                    expanded = showLanguageMenu,
                    onExpandedChange = { showLanguageMenu = it }
                ) {
                    AppLanguage.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            trailingIcon = {
                                if (option == language) {
                                    Icon(Icons.Outlined.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                onLanguageSelect(option)
                                showLanguageMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.privacy_policy),
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable { openLink(PRIVACY_POLICY_URL) }
                        .padding(8.dp)
                )

                Text(
                    text = stringResource(
                        R.string.settings_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { openLink(STUDIO_URL) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MEDEV Studios",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.settings_open_website),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
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

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SettingIcon(icon: ImageVector, iconColor: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(iconColor.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconColor)
    }
}

@Composable
fun SettingSwitchRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIcon(icon, iconColor)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
private fun SettingMenuRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    valueLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(true) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIcon(icon, iconColor)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box {
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                content = menuContent
            )
        }
    }
}
