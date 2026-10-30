package net.mustafaer.quickqr.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.ui.components.ScannerPreview
import net.mustafaer.quickqr.ui.components.Viewfinder
import net.mustafaer.quickqr.ui.components.acceptRegion
import net.mustafaer.quickqr.ui.components.rememberScannerCameraState
import net.mustafaer.quickqr.ui.components.viewfinderRect

/** Colour of the torch glyph while the torch is on. Drawn over the camera feed, so not themed. */
private val TorchOnTint = Color(0xFFFFD54F)

/**
 * @param isActive the scanner tab is on screen; the camera is bound only then.
 * @param isPaused a result is showing, so frames are not analysed.
 * @param lockedCode the code the viewfinder should spring onto, if any.
 * @param onFrame every analysed frame's chosen code, or null for an empty frame.
 */
@Composable
fun ScannerScreen(
    isActive: Boolean,
    isPaused: Boolean,
    lockedCode: String?,
    showPrivacyNotice: Boolean,
    onDismissPrivacyNotice: () -> Unit,
    onFrame: (String?) -> Unit,
    onImagePicked: (Uri) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // Set once the system dialog has been shown and dismissed without a grant and
    // the OS will no longer show it — the point at which "Try again" is useless
    // and the only way forward is the app's settings page.
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Re-checked here so returning from the settings page picks up a
                // permission the user just granted.
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                hasCameraPermission = granted
                if (granted) permissionPermanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionPermanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // The system photo picker: no storage permission, and on Android 11+ the
    // user picks one image without granting access to the rest of the gallery.
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onImagePicked) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!hasCameraPermission) {
            CameraPermissionPrompt(
                permanentlyDenied = permissionPermanentlyDenied,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                "package:${context.packageName}".toUri()
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
        } else {
            CameraScanner(
                isActive = isActive,
                isPaused = isPaused,
                lockedCode = lockedCode,
                showPrivacyNotice = showPrivacyNotice,
                onDismissPrivacyNotice = onDismissPrivacyNotice,
                onFrame = onFrame,
                onPickImage = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                contentPadding = contentPadding
            )
        }
    }
}

@Composable
private fun CameraScanner(
    isActive: Boolean,
    isPaused: Boolean,
    lockedCode: String?,
    showPrivacyNotice: Boolean,
    onDismissPrivacyNotice: () -> Unit,
    onFrame: (String?) -> Unit,
    onPickImage: () -> Unit,
    contentPadding: PaddingValues
) {
    Box(modifier = Modifier.fillMaxSize()) {
        var overlaySize by remember { mutableStateOf(Size.Zero) }
        // Plain map, not state: written for every analysed frame, read only when
        // a lock begins — so frames never trigger recomposition.
        val lastBoxes = remember { HashMap<String, Rect>() }

        val camera = rememberScannerCameraState(
            active = isActive,
            analyzing = isActive && !isPaused
        ) { barcodes ->
            lastBoxes.clear()
            val frame = if (overlaySize == Size.Zero) null else viewfinderRect(overlaySize)
            val accepted = frame?.acceptRegion()
            val chosen = barcodes
                .mapNotNull { barcode ->
                    val raw = barcode.rawValue?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    raw to barcode.boundingBox?.toComposeRect()
                }
                .filter { (_, box) -> accepted == null || box == null || accepted.contains(box.center) }
                // Of several codes in the frame, the one the user aimed at.
                .minByOrNull { (_, box) ->
                    if (box == null || frame == null) 0f else (box.center - frame.center).getDistanceSquared()
                }
            if (chosen != null) chosen.second?.let { lastBoxes[chosen.first] = it }
            onFrame(chosen?.first)
        }

        val lockedBox = remember(lockedCode) { lockedCode?.let { lastBoxes[it] } }

        ScannerPreview(camera, Modifier.fillMaxSize())

        Viewfinder(
            lockedBox = lockedBox,
            idle = isActive && !isPaused,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { overlaySize = it.toSize() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.scan_empty_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showPrivacyNotice,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 }
                ) {
                    PrivacyNotice(onDismiss = onDismissPrivacyNotice)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScannerControl(
                    onClick = camera::toggleTorch,
                    enabled = camera.hasFlashUnit,
                    size = 56.dp,
                    // On/off is told by the glyph itself, not by colour alone.
                    icon = if (camera.torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                    tint = if (camera.torchOn) TorchOnTint else Color.White,
                    contentDescription = stringResource(
                        if (camera.torchOn) R.string.header_torch_on else R.string.header_torch_off
                    )
                )

                ScannerControl(
                    onClick = onPickImage,
                    size = 64.dp,
                    icon = Icons.Outlined.PhotoLibrary,
                    contentDescription = stringResource(R.string.scan_gallery)
                )

                ScannerControl(
                    onClick = camera::switchCamera,
                    enabled = camera.canSwitchCamera,
                    size = 56.dp,
                    icon = Icons.Outlined.Cameraswitch,
                    contentDescription = stringResource(R.string.header_switch_camera)
                )
            }
        }
    }
}

@Composable
private fun PrivacyNotice(onDismiss: () -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp)
        ) {
            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.privacy_notice),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.privacy_notice_dismiss),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ScannerControl(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    enabled: Boolean = true,
    tint: Color = Color.White
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.6f),
            contentColor = tint,
            disabledContainerColor = Color.Black.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.43f)
        )
    }
}

@Composable
private fun CameraPermissionPrompt(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.NoPhotography,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (permanentlyDenied) R.string.permission_message_denied else R.string.permission_message
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onRequest,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                stringResource(
                    if (permanentlyDenied) R.string.permission_open_settings else R.string.permission_retry
                )
            )
        }
    }
}
