package net.mustafaer.quickqr.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.FocusMeteringAction
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import net.mustafaer.quickqr.R
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScannerScreen(
    isPaused: Boolean,
    onCodeScanned: (String) -> Unit,
    onGalleryScan: (Uri, onSuccess: (String) -> Unit, onFailure: () -> Unit) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val noQrFoundMsg = stringResource(R.string.no_qr_found)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isLifecycleResumed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isLifecycleResumed = true
                    hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isLifecycleResumed = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onGalleryScan(uri, { _ ->
                // No toast needed here since the result sheet opens or a continuous scan toast shows
            }, {
                Toast.makeText(context, noQrFoundMsg, Toast.LENGTH_SHORT).show()
            })
        }
    }

    if (!hasCameraPermission) {
        // Permission Denied UI
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
                text = stringResource(R.string.permission_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.permission_retry))
            }
        }
    } else {
        // Scanner Camera View
        Box(modifier = Modifier.fillMaxSize()) {
            var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
            var torchEnabled by rememberSaveable { mutableStateOf(false) }
            var cameraInstance by remember { mutableStateOf<Camera?>(null) }
            var cameraProviderInstance by remember { mutableStateOf<ProcessCameraProvider?>(null) }
            var imageAnalysisInstance by remember { mutableStateOf<ImageAnalysis?>(null) }
            val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
            val barcodeScanner = remember { BarcodeScanning.getClient() }

            // Track the PreviewView reference for rebinding
            val previewViewRef = remember { mutableStateOf<PreviewView?>(null) }

            // Stable reference to isPaused for the analyzer callback
            val isPausedState = rememberUpdatedState(isPaused)

            // Camera preview
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
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                            camera.cameraControl.startFocusAndMetering(action)
                        }
                    }
            )

            // Bind camera whenever lensFacing, previewView, context, lifecycleOwner or lifecycle state changes
            LaunchedEffect(lensFacing, previewViewRef.value, context, lifecycleOwner, isLifecycleResumed) {
                if (!isLifecycleResumed) return@LaunchedEffect
                val previewView = previewViewRef.value ?: return@LaunchedEffect
                var cameraProvider: ProcessCameraProvider? = null
                var imageAnalysis: ImageAnalysis? = null
                try {
                    cameraProvider = suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
                        val future = ProcessCameraProvider.getInstance(context)
                        continuation.invokeOnCancellation {
                            future.cancel(true)
                        }
                        future.addListener({
                            try {
                                if (continuation.isActive) {
                                    continuation.resume(future.get())
                                }
                            } catch (e: Exception) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(e)
                                }
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                    cameraProviderInstance = cameraProvider
                    cameraProvider.unbindAll()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                if (!isPausedState.value) {
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        try {
                                            val image = InputImage.fromMediaImage(
                                                mediaImage,
                                                imageProxy.imageInfo.rotationDegrees
                                            )
                                            barcodeScanner.process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    barcodes.firstOrNull()?.rawValue?.let { code ->
                                                        onCodeScanned(code)
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy.close()
                                                }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            imageProxy.close()
                                        }
                                    } else {
                                        imageProxy.close()
                                    }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    cameraInstance = boundCamera
                    imageAnalysisInstance = imageAnalysis
                    boundCamera.cameraControl.enableTorch(torchEnabled)

                    awaitCancellation()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    cameraInstance = null
                    imageAnalysisInstance = null
                    try {
                        imageAnalysis?.clearAnalyzer()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        cameraProvider?.unbindAll()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Watch Torch toggle
            LaunchedEffect(torchEnabled, cameraInstance) {
                cameraInstance?.cameraControl?.enableTorch(torchEnabled)
            }

            // Cleanup executor and unbind camera when composable leaves composition
            DisposableEffect(Unit) {
                onDispose {
                    cameraExecutor.execute {
                        try {
                            barcodeScanner.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    cameraExecutor.shutdown()
                }
            }

            // Scanner HUD overlay
            ScannerHudOverlay()

            // Toolbar Controls overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top controls
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

                // Bottom controls (Flash, Gallery, Switch Camera)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                        .background(Color.Transparent),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flash Toggle
                    IconButton(
                        onClick = { torchEnabled = !torchEnabled },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = stringResource(if (torchEnabled) R.string.header_torch_on else R.string.header_torch_off),
                            tint = if (torchEnabled) Color(0xFFFFD54F) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Gallery scan button
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = stringResource(R.string.scan_gallery),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Camera switcher
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                            torchEnabled = false // Reset torch on switch
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FlipCameraAndroid,
                            contentDescription = stringResource(R.string.header_switch_camera),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
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

                // Calculate scanning viewport box dimensions
                val boxWidth = size.width * 0.70f
                val boxHeight = boxWidth // Square box
                val left = (width - boxWidth) / 2
                val top = (height - boxHeight) / 2.3f
                val right = left + boxWidth
                val bottom = top + boxHeight

                val cornerRadiusPx = 20.dp.toPx()
                val borderThickness = 4.dp.toPx()
                val cornerLength = 24.dp.toPx()
                val cornerColor = Color(0xFFB4A5FF)

                val rectPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(left, top, right, bottom),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                        )
                    )
                }

                onDrawBehind {
                    // Draw semi-transparent overlay everywhere except inside the box
                    clipPath(rectPath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.65f),
                            size = Size(width, height)
                        )
                    }

                    // Draw HUD square frame corners
                    // Top-left
                    drawRect(
                        color = cornerColor,
                        topLeft = Offset(left, top),
                        size = Size(cornerLength, borderThickness)
                    )
                    drawRect(
                        color = cornerColor,
                        topLeft = Offset(left, top),
                        size = Size(borderThickness, cornerLength)
                    )

                    // Top-right
                    drawRect(
                        color = cornerColor,
                        topLeft = Offset(right - cornerLength, top),
                        size = Size(cornerLength, borderThickness)
                    )
                    drawRect(
                        color = cornerColor,
                        topLeft = Offset(right - borderThickness, top),
                        size = Size(borderThickness, cornerLength)
                    )

                    // Bottom-left
                    drawRect(
                        color = cornerColor,
                        topLeft = Offset(left, bottom - borderThickness),
                        size = Size(cornerLength, borderThickness)
                    )
                    drawRect(
                        color = cornerColor,
                        topLeft = Offset(left, bottom - cornerLength),
                        size = Size(borderThickness, cornerLength)
                    )

                    // Bottom-right
                    drawRect(
                        color = cornerColor,
                        topLeft = Offset(right - cornerLength, bottom - borderThickness),
                        size = Size(cornerLength, borderThickness)
                    )
                    drawRect(
                        color = cornerColor,
                        topLeft = Offset(right - borderThickness, bottom - cornerLength),
                        size = Size(borderThickness, cornerLength)
                    )

                    // Animated scan line inside viewport
                    val lineY = top + (boxHeight * animatedLineY)
                    drawLine(
                        color = Color(0xFFFF5252),
                        start = Offset(left + 8.dp.toPx(), lineY),
                        end = Offset(right - 8.dp.toPx(), lineY),
                        strokeWidth = 2.5.dp.toPx()
                    )
                }
            }
    )
}
