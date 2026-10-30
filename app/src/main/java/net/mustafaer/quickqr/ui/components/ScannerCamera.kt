package net.mustafaer.quickqr.ui.components

import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors

/**
 * The camera, as the scanner screen sees it: one [LifecycleCameraController]
 * plus the bits of its state the controls need.
 *
 * Results come back in PreviewView coordinates (`COORDINATE_SYSTEM_VIEW_REFERENCED`),
 * the same pixel space the viewfinder overlay draws in. That is what makes the
 * "is it inside the frame" check and the lock-on animation correct: the previous
 * check compared ML Kit's upright boxes against the sensor's landscape width and
 * height, so on a portrait phone the accepted region sat on swapped axes.
 */
@Stable
class ScannerCameraState internal constructor(val controller: LifecycleCameraController) {
    var hasFlashUnit by mutableStateOf(false)
        internal set
    var torchOn by mutableStateOf(false)
        internal set
    var usingFrontCamera by mutableStateOf(false)
        private set
    var canSwitchCamera by mutableStateOf(false)
        internal set

    internal var maxZoomRatio = 1f

    fun toggleTorch() {
        if (hasFlashUnit) controller.enableTorch(!torchOn)
    }

    fun switchCamera() {
        val target = if (usingFrontCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        if (runCatching { controller.hasCamera(target) }.getOrDefault(false)) {
            controller.cameraSelector = target
            usingFrontCamera = !usingFrontCamera
        }
    }
}

/**
 * @param active binds the camera while true and releases it otherwise, so it is
 *   free whenever the scanner tab is not on screen.
 * @param analyzing false while a result is showing. Frames are dropped without
 *   reaching ML Kit rather than the analyzer being removed: removing it makes the
 *   controller rebind its use cases, which flashes the preview.
 * @param onBarcodes every analysed frame's codes, in PreviewView pixels, on the
 *   main thread.
 */
@Composable
fun rememberScannerCameraState(
    active: Boolean,
    analyzing: Boolean,
    onBarcodes: (List<Barcode>) -> Unit
): ScannerCameraState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val onBarcodesState = rememberUpdatedState(onBarcodes)

    val state = remember {
        val controller = LifecycleCameraController(context).apply {
            // Preview and analysis only. Image capture would hold a second
            // full-resolution stream for nothing.
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            // The 640×480 default leaves dense codes — a vCard at version 15 and
            // up — with too few pixels per module to decode reliably.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
        }
        ScannerCameraState(controller)
    }
    val analyzer = remember { PausableAnalyzer() }
    SideEffect { analyzer.paused = !analyzing }

    DisposableEffect(state) {
        val controller = state.controller
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                // Every format QuickQR can act on. ML Kit is measurably faster
                // when it is not also hunting for formats nobody asked for.
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39
                )
                // ML Kit asks for more zoom when a code is detected but too small
                // to decode; the callback runs off the main thread.
                .setZoomSuggestionOptions(
                    ZoomSuggestionOptions.Builder { ratio ->
                        mainExecutor.execute {
                            controller.setZoomRatio(ratio.coerceAtMost(state.maxZoomRatio))
                        }
                        true
                    }
                        .setMaxSupportedZoomRatio(MAX_SUGGESTED_ZOOM)
                        .build()
                )
                .build()
        )
        val analysisExecutor = Executors.newSingleThreadExecutor()
        analyzer.delegate = MlKitAnalyzer(
            listOf(scanner),
            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
            mainExecutor
        ) { result ->
            onBarcodesState.value(result.getValue(scanner).orEmpty())
        }
        controller.setImageAnalysisAnalyzer(analysisExecutor, analyzer)

        val zoomObserver = Observer<ZoomState> { zoom ->
            // The zoom state only arrives once a camera is actually bound, which
            // is also the first moment its flash and lens facts are known.
            state.maxZoomRatio = zoom.maxZoomRatio
            state.hasFlashUnit = controller.cameraInfo?.hasFlashUnit() == true
            state.canSwitchCamera = runCatching {
                controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) &&
                    controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            }.getOrDefault(false)
        }
        val torchObserver = Observer<Int> { state.torchOn = it == TorchState.ON }
        controller.zoomState.observe(lifecycleOwner, zoomObserver)
        controller.torchState.observe(lifecycleOwner, torchObserver)

        onDispose {
            controller.zoomState.removeObserver(zoomObserver)
            controller.torchState.removeObserver(torchObserver)
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analyzer.delegate = null
            analysisExecutor.execute { scanner.close() }
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(state, active) {
        if (active) state.controller.bindToLifecycle(lifecycleOwner) else state.controller.unbind()
    }

    // A zoom ML Kit suggested for one far-away code is wrong for the next one.
    LaunchedEffect(state, analyzing) {
        if (analyzing) state.controller.setZoomRatio(1f)
    }

    return state
}

@Composable
fun ScannerPreview(state: ScannerCameraState, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // Tap-to-focus and pinch-to-zoom come with the controller.
                controller = state.controller
            }
        },
        modifier = modifier
    )
}

private const val MAX_SUGGESTED_ZOOM = 5f

/**
 * Passes frames to [delegate] unless [paused], and forwards the coordinate
 * system and transform so the controller still treats it as view-referenced.
 */
private class PausableAnalyzer : ImageAnalysis.Analyzer {
    @Volatile
    var paused = false

    @Volatile
    var delegate: MlKitAnalyzer? = null

    override fun analyze(image: ImageProxy) {
        val target = delegate
        if (paused || target == null) image.close() else target.analyze(image)
    }

    override fun getDefaultTargetResolution(): Size? = delegate?.defaultTargetResolution

    override fun getTargetCoordinateSystem(): Int = ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED

    override fun updateTransform(matrix: Matrix?) {
        delegate?.updateTransform(matrix)
    }
}
