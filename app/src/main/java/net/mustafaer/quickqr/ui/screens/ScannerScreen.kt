package net.mustafaer.quickqr.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.mustafaer.quickqr.R
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fraction of the analysis frame, measured from the centre, in which a barcode is
 * accepted.
 *
 * The viewfinder draws a square at 70% of the view width, and a code the user
 * lines up inside it must always scan — so the accepted region is deliberately
 * wider than the drawn frame rather than matched to it. What it does rule out is
 * a code sitting right at the edge of the frame, in the darkened area, being read
 * and saved without the user ever having aimed at it.
 */
private const val SCAN_REGION_FRACTION = 0.85f

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScannerScreen(
    isActive: Boolean,
    isPaused: Boolean,
    onCodeScanned: (String, Boolean) -> Unit,
    onGalleryScan: (Uri, () -> Unit) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val noQrFoundMsg = stringResource(R.string.no_qr_found)
    val snackbarHostState = remember { SnackbarHostState() }

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
    var isLifecycleResumed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isLifecycleResumed = true
                    // Re-checked here so returning from the settings page picks up
                    // a permission the user just granted.
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    hasCameraPermission = granted
                    if (granted) permissionPermanentlyDenied = false
                }
                Lifecycle.Event.ON_PAUSE -> isLifecycleResumed = false
                else -> {}
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
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA
            )
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val scope = rememberCoroutineScope()
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { picked ->
            // Only the failure path needs feedback: a successful gallery scan
            // opens the result sheet, which is confirmation enough.
            onGalleryScan(picked) {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(noQrFoundMsg)
                }
            }
        }
    }

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
            var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
            var torchEnabled by rememberSaveable { mutableStateOf(false) }
            var cameraInstance by remember { mutableStateOf<Camera?>(null) }
            var hasFlashUnit by remember { mutableStateOf(false) }
            val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
            val barcodeScanner = remember { BarcodeScanning.getClient() }
            val previewViewRef = remember { mutableStateOf<PreviewView?>(null) }

            val isPausedState = rememberUpdatedState(isPaused)
            val onCodeScannedState = rememberUpdatedState(onCodeScanned)

            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewViewRef.value = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cameraInstance) {
                        detectTapGestures { offset ->
                            val camera = cameraInstance ?: return@detectTapGestures
                            val previewView = previewViewRef.value ?: return@detectTapGestures
                            val point = previewView.meteringPointFactory
                                .createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction
                                .Builder(point, FocusMeteringAction.FLAG_AF)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            runCatching { camera.cameraControl.startFocusAndMetering(action) }
                        }
                    }
            )

            // Binding is keyed on `isActive` so the camera releases while the user
            // is on another tab, while this composable — and with it the
            // PreviewView, the executor and the ML Kit client — stays alive.
            LaunchedEffect(lensFacing, previewViewRef.value, isActive, isLifecycleResumed) {
                if (!isActive || !isLifecycleResumed) return@LaunchedEffect
                val previewView = previewViewRef.value ?: return@LaunchedEffect

                var cameraProvider: ProcessCameraProvider? = null
                var imageAnalysis: ImageAnalysis? = null
                try {
                    val provider: ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
                        val future = ProcessCameraProvider.getInstance(context)
                        continuation.invokeOnCancellation { future.cancel(true) }
                        future.addListener({
                            try {
                                if (continuation.isActive) continuation.resume(future.get())
                            } catch (e: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(e)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                    cameraProvider = provider
                    provider.unbindAll()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                analyzeFrame(
                                    imageProxy = imageProxy,
                                    isPaused = isPausedState.value,
                                    scanner = barcodeScanner,
                                    onCode = { code -> onCodeScannedState.value(code, false) }
                                )
                            }
                        }

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    val boundCamera = provider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageAnalysis
                    )
                    cameraInstance = boundCamera
                    hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()
                    if (hasFlashUnit) {
                        runCatching { boundCamera.cameraControl.enableTorch(torchEnabled) }
                    }

                    awaitCancellation()
                } catch (_: Exception) {
                    // A camera that will not open leaves the preview blank; there
                    // is nothing actionable to tell the user beyond that.
                } finally {
                    cameraInstance = null
                    runCatching { imageAnalysis?.clearAnalyzer() }
                    runCatching { cameraProvider?.unbindAll() }
                }
            }

            LaunchedEffect(torchEnabled, cameraInstance) {
                val camera = cameraInstance ?: return@LaunchedEffect
                if (hasFlashUnit) runCatching { camera.cameraControl.enableTorch(torchEnabled) }
            }

            DisposableEffect(Unit) {
                onDispose {
                    cameraExecutor.execute { runCatching { barcodeScanner.close() } }
                    cameraExecutor.shutdown()
                }
            }

            ScannerHudOverlay()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scan_empty_title),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
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
                        onClick = { torchEnabled = !torchEnabled },
                        enabled = hasFlashUnit,
                        size = 56.dp,
                        icon = Icons.Outlined.Lightbulb,
                        iconSize = 24.dp,
                        tint = if (torchEnabled) Color(0xFFFFD54F) else Color.White,
                        contentDescription = stringResource(
                            if (torchEnabled) R.string.header_torch_on else R.string.header_torch_off
                        )
                    )

                    ScannerControl(
                        onClick = { galleryLauncher.launch("image/*") },
                        size = 64.dp,
                        icon = Icons.Outlined.PhotoLibrary,
                        iconSize = 28.dp,
                        contentDescription = stringResource(R.string.scan_gallery)
                    )

                    ScannerControl(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                            torchEnabled = false
                        },
                        size = 56.dp,
                        icon = Icons.Outlined.FlipCameraAndroid,
                        iconSize = 24.dp,
                        contentDescription = stringResource(R.string.header_switch_camera)
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(bottom = 88.dp)
        )
    }
}

/**
 * Hands one camera frame to ML Kit and closes it exactly once.
 *
 * `addOnCompleteListener` fires for success *and* failure, so it is the only
 * place the proxy is released — adding a failure listener that also closed it
 * would double-close every failed frame.
 */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyzeFrame(
    imageProxy: ImageProxy,
    isPaused: Boolean,
    scanner: BarcodeScanner,
    onCode: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (isPaused || mediaImage == null) {
        imageProxy.close()
        return
    }
    try {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { it.isWithinScanRegion(imageProxy.width, imageProxy.height) }
                    ?.rawValue
                    ?.let(onCode)
            }
            .addOnCompleteListener { imageProxy.close() }
    } catch (_: Exception) {
        imageProxy.close()
    }
}

/** True when the barcode's centre sits inside the accepted central region. */
private fun Barcode.isWithinScanRegion(imageWidth: Int, imageHeight: Int): Boolean {
    val box = boundingBox ?: return true
    val marginX = imageWidth * (1f - SCAN_REGION_FRACTION) / 2f
    val marginY = imageHeight * (1f - SCAN_REGION_FRACTION) / 2f
    val centerX = box.exactCenterX()
    val centerY = box.exactCenterY()
    return centerX in marginX..(imageWidth - marginX) &&
        centerY in marginY..(imageHeight - marginY)
}

@Composable
private fun ScannerControl(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    tint: Color = Color.White
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize)
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
        Icon(
            imageVector = Icons.Outlined.FlipCameraAndroid,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
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
                if (permanentlyDenied) R.string.permission_message_denied
                else R.string.permission_message
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onRequest,
            modifier = Modifier.height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                stringResource(
                    if (permanentlyDenied) R.string.permission_open_settings
                    else R.string.permission_retry
                )
            )
        }
    }
}

@Composable
fun ScannerHudOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanHud")
    val animatedLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val width = size.width
                val height = size.height

                val boxWidth = width * 0.70f
                val boxHeight = boxWidth
                val left = (width - boxWidth) / 2
                val top = (height - boxHeight) / 2.3f
                val right = left + boxWidth
                val bottom = top + boxHeight

                val cornerRadiusPx = 20.dp.toPx()
                val borderThickness = 4.dp.toPx()
                val cornerLength = 24.dp.toPx()
                val cornerColor = Color(0xFFB4A5FF)
                val lineInset = 8.dp.toPx()

                val rectPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(left, top, right, bottom),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                        )
                    )
                }

                onDrawBehind {
                    clipPath(rectPath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.65f),
                            size = Size(width, height)
                        )
                    }

                    // Four L-shaped corner brackets.
                    listOf(
                        Offset(left, top) to Size(cornerLength, borderThickness),
                        Offset(left, top) to Size(borderThickness, cornerLength),
                        Offset(right - cornerLength, top) to Size(cornerLength, borderThickness),
                        Offset(right - borderThickness, top) to Size(borderThickness, cornerLength),
                        Offset(left, bottom - borderThickness) to Size(cornerLength, borderThickness),
                        Offset(left, bottom - cornerLength) to Size(borderThickness, cornerLength),
                        Offset(right - cornerLength, bottom - borderThickness) to
                            Size(cornerLength, borderThickness),
                        Offset(right - borderThickness, bottom - cornerLength) to
                            Size(borderThickness, cornerLength)
                    ).forEach { (topLeft, rectSize) ->
                        drawRect(color = cornerColor, topLeft = topLeft, size = rectSize)
                    }

                    val lineY = top + (boxHeight * animatedLineY)
                    drawLine(
                        color = Color(0xFFFF5252),
                        start = Offset(left + lineInset, lineY),
                        end = Offset(right - lineInset, lineY),
                        strokeWidth = 2.5.dp.toPx()
                    )
                }
            }
    )
}
