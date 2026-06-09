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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import net.mustafaer.quickqr.R
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    isPaused: Boolean,
    onCodeScanned: (String) -> Unit,
    onGalleryScan: (Uri, onSuccess: (String) -> Unit, onFailure: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
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
                Toast.makeText(context, context.getString(R.string.generator_saved), Toast.LENGTH_SHORT).show()
            }, {
                Toast.makeText(context, context.getString(R.string.no_qr_found), Toast.LENGTH_SHORT).show()
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
            var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
            var torchEnabled by remember { mutableStateOf(false) }
            var cameraInstance by remember { mutableStateOf<Camera?>(null) }
            val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

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
                modifier = Modifier.fillMaxSize()
            )

            // Bind camera whenever lensFacing changes
            LaunchedEffect(lensFacing) {
                val previewView = previewViewRef.value ?: return@LaunchedEffect
                try {
                    val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                    cameraProvider.unbindAll()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                if (!isPausedState.value) {
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val image = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        BarcodeScanning.getClient().process(image)
                                            .addOnSuccessListener { barcodes ->
                                                barcodes.firstOrNull()?.rawValue?.let { code ->
                                                    onCodeScanned(code)
                                                }
                                            }
                                            .addOnCompleteListener {
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

                    cameraInstance = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    cameraInstance?.cameraControl?.enableTorch(torchEnabled)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Watch Torch toggle
            LaunchedEffect(torchEnabled) {
                cameraInstance?.cameraControl?.enableTorch(torchEnabled)
            }

            // Cleanup executor when composable leaves composition
            DisposableEffect(Unit) {
                onDispose {
                    cameraExecutor.shutdown()
                }
            }

            // Scanner HUD overlay
            ScannerHudOverlay()

            // Toolbar Controls overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
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
    val animatedLineY = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Calculate scanning viewport box dimensions
        val boxWidth = size.width * 0.70f
        val boxHeight = boxWidth // Square box
        val left = (width - boxWidth) / 2
        val top = (height - boxHeight) / 2.3f
        val right = left + boxWidth
        val bottom = top + boxHeight

        val rectPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, right, bottom),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                )
            )
        }

        // Draw semi-transparent overlay everywhere except inside the box
        clipPath(rectPath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
            drawRect(
                color = Color.Black.copy(alpha = 0.65f),
                size = Size(width, height)
            )
        }

        // Draw HUD square frame corners
        val borderThickness = 4.dp.toPx()
        val cornerLength = 24.dp.toPx()
        val cornerColor = Color(0xFFB4A5FF)

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
        val lineY = top + (boxHeight * animatedLineY.value)
        drawLine(
            color = Color(0xFFFF5252),
            start = Offset(left + 8.dp.toPx(), lineY),
            end = Offset(right - 8.dp.toPx(), lineY),
            strokeWidth = 2.5.dp.toPx()
        )
    }
}
