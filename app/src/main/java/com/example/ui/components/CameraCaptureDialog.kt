package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.ui.theme.*
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Dedicated in-app CameraX studio for crisp 4:3 SDR shoe photography with tap-to-focus,
 * exposure & torch controls, post-capture EXIF/sRGB verification, and graceful System Camera / Gallery fallback.
 */
@Composable
fun CameraCaptureDialog(
    title: String,
    subtitle: String,
    allowGalleryFallback: Boolean = true,
    onDismiss: () -> Unit,
    onPhotoReady: (File) -> Unit,
    onGalleryPicked: ((Uri) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val isEmulator = remember {
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequestedOnce) {
            permissionRequestedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var capturedFile by remember { mutableStateOf<File?>(null) }
    var capturedDimensions by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var useFrontCamera by remember { mutableStateOf(false) }
    var canSwitchLens by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var maxZoomRatio by remember { mutableStateOf(1f) }
    var exposureStepIndex by remember { mutableStateOf(0) } // -2..+2 UI stops
    var exposureSupported by remember { mutableStateOf(false) }

    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    var focusSucceeded by remember { mutableStateOf<Boolean?>(null) }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var boundImageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // System camera fallback (uses TakePicture without needing CAMERA permission)
    var pendingSystemFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    val systemCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingSystemFilePath
        if (success && path != null) {
            val raw = File(path)
            if (raw.exists()) {
                scope.launch {
                    val normalized = withContext(Dispatchers.IO) {
                        ImageUtils.normalizeCapturedFile(context, raw, maxDim = 1200)
                    } ?: raw
                    if (normalized != raw) raw.delete()
                    capturedDimensions = ImageUtils.readImageDimensions(normalized)
                    capturedFile = normalized
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onGalleryPicked?.invoke(uri)
        }
    }
    val filesDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onGalleryPicked?.invoke(uri)
        }
    }

    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(1300)
            focusTapOffset = null
            focusSucceeded = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("camera_capture_dialog"),
            color = CameraScrimBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Close camera",
                                tint = PureWhite
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite.copy(alpha = 0.72f)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val (uri, file) = ImageUtils.createTempCameraUri(context)
                                pendingSystemFilePath = file.absolutePath
                                systemCameraLauncher.launch(uri)
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("camera_system_fallback_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.35f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "System Cam",
                                style = MaterialTheme.typography.labelMedium,
                                color = PureWhite
                            )
                        }
                    }
                }

                // Main Viewfinder or Review or Permission fallback
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val reviewFile = capturedFile
                    when {
                        reviewFile != null -> {
                            // Crisp Post-Capture Review Card
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(3f / 4f)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(PureWhite.copy(alpha = 0.08f))
                                        .border(1.5.dp, PureWhite.copy(alpha = 0.2f), RoundedCornerShape(22.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(reviewFile)
                                            .memoryCachePolicy(CachePolicy.DISABLED)
                                            .diskCachePolicy(CachePolicy.DISABLED)
                                            .build(),
                                        contentDescription = "Captured shoe photo review",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    capturedDimensions?.let { (w, h) ->
                                        Surface(
                                            color = CameraScrimBlack.copy(alpha = 0.72f),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = "$w × $h px • sRGB Upright",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = PureWhite,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        !hasCameraPermission -> {
                            CameraUnavailableCard(
                                heading = "Camera Permission Needed",
                                body = "Allow SoleBook to open the 4:3 sharp shoe viewfinder, or use your phone's System Camera / Gallery instead.",
                                primaryLabel = "Allow Camera",
                                onPrimary = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                onSystemCamera = {
                                    val (uri, file) = ImageUtils.createTempCameraUri(context)
                                    pendingSystemFilePath = file.absolutePath
                                    systemCameraLauncher.launch(uri)
                                },
                                onGallery = if (allowGalleryFallback && onGalleryPicked != null) {
                                    {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                } else null
                            )
                        }

                        cameraError != null -> {
                            CameraUnavailableCard(
                                heading = "In-App Camera Unavailable",
                                body = cameraError ?: "Could not open device camera hardware.",
                                primaryLabel = "Retry Camera",
                                onPrimary = { cameraError = null },
                                onSystemCamera = {
                                    val (uri, file) = ImageUtils.createTempCameraUri(context)
                                    pendingSystemFilePath = file.absolutePath
                                    systemCameraLauncher.launch(uri)
                                },
                                onGallery = if (allowGalleryFallback && onGalleryPicked != null) {
                                    {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                } else null
                            )
                        }

                        else -> {
                            // 4:3 Native Aspect Viewfinder (prevents stretched blur & YUV overlay tinting)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f / 4f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(CameraScrimBlack)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PreviewView(ctx).apply {
                                            // COMPATIBLE (TextureView) avoids hardware overlay YUV color artifacts inside Compose Dialogs
                                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                            scaleType = PreviewView.ScaleType.FIT_CENTER
                                        }.also { previewViewRef = it }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(boundCamera, previewViewRef) {
                                            detectTapGestures { offset ->
                                                val cam = boundCamera ?: return@detectTapGestures
                                                val pv = previewViewRef ?: return@detectTapGestures
                                                focusTapOffset = offset
                                                focusSucceeded = null
                                                val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                                                val action = FocusMeteringAction.Builder(
                                                    point,
                                                    FocusMeteringAction.FLAG_AF or
                                                        FocusMeteringAction.FLAG_AE or
                                                        FocusMeteringAction.FLAG_AWB
                                                )
                                                    .setAutoCancelDuration(4, TimeUnit.SECONDS)
                                                    .build()
                                                val future = cam.cameraControl.startFocusAndMetering(action)
                                                future.addListener({
                                                    try {
                                                        focusSucceeded = future.get().isFocusSuccessful
                                                    } catch (_: Exception) {
                                                        focusSucceeded = null
                                                    }
                                                }, ContextCompat.getMainExecutor(context))
                                            }
                                        }
                                )

                                // Bind CameraX safely whenever lens or permission changes
                                val pv = previewViewRef
                                DisposableEffect(lifecycleOwner, pv, useFrontCamera, hasCameraPermission) {
                                    if (pv == null || !hasCameraPermission) {
                                        return@DisposableEffect onDispose {}
                                    }
                                    var providerRef: ProcessCameraProvider? = null
                                    val providerFuture = ProcessCameraProvider.getInstance(context)
                                    val executor = ContextCompat.getMainExecutor(context)

                                    providerFuture.addListener({
                                        try {
                                            val provider = providerFuture.get()
                                            providerRef = provider

                                            val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                                            val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                                            canSwitchLens = hasBack && hasFront

                                            val selector = when {
                                                useFrontCamera && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                                                hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                                                hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                                                else -> {
                                                    cameraError = "No camera sensor detected on this device."
                                                    return@addListener
                                                }
                                            }

                                            val resolutionSelector = ResolutionSelector.Builder()
                                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                                                .build()

                                            // SDR prevents 10-bit/HLG neon or weird color shifts in TextureView
                                            val preview = Preview.Builder()
                                                .setResolutionSelector(resolutionSelector)
                                                .setDynamicRange(DynamicRange.SDR)
                                                .build()
                                                .also { it.surfaceProvider = pv.surfaceProvider }

                                            val imageCapture = ImageCapture.Builder()
                                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                                .setResolutionSelector(resolutionSelector)
                                                .setJpegQuality(94)
                                                .setFlashMode(flashMode)
                                                .build()

                                            provider.unbindAll()
                                            val camera = provider.bindToLifecycle(
                                                lifecycleOwner,
                                                selector,
                                                preview,
                                                imageCapture
                                            )
                                            boundCamera = camera
                                            boundImageCapture = imageCapture
                                            hasFlash = camera.cameraInfo.hasFlashUnit()
                                            torchEnabled = false
                                            zoomRatio = 1f
                                            maxZoomRatio = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                                            exposureSupported = camera.cameraInfo.exposureState.isExposureCompensationSupported
                                            exposureStepIndex = 0
                                            cameraError = null

                                            // Trigger initial center AF + AE + AWB lock once PreviewView has layout size
                                            pv.post {
                                                if (pv.width > 0 && pv.height > 0) {
                                                    val center = pv.meteringPointFactory.createPoint(
                                                        pv.width / 2f,
                                                        pv.height / 2f
                                                    )
                                                    val action = FocusMeteringAction.Builder(
                                                        center,
                                                        FocusMeteringAction.FLAG_AF or
                                                            FocusMeteringAction.FLAG_AE or
                                                            FocusMeteringAction.FLAG_AWB
                                                    ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                                    camera.cameraControl.startFocusAndMetering(action)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            cameraError = e.localizedMessage ?: "Failed to start camera preview."
                                        }
                                    }, executor)

                                    onDispose {
                                        try {
                                            providerRef?.unbindAll()
                                        } catch (_: Exception) {
                                        }
                                        boundCamera = null
                                        boundImageCapture = null
                                    }
                                }

                                // Subtle shoe framing corners & focus ring overlay
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val marginX = size.width * 0.12f
                                    val marginY = size.height * 0.15f
                                    val boxW = size.width - marginX * 2f
                                    val boxH = size.height - marginY * 2f
                                    val corner = 26.dp.toPx()
                                    val strokeW = 2.5.dp.toPx()
                                    val guideColor = WarmOffWhite.copy(alpha = 0.55f)

                                    // Top-left
                                    drawLine(guideColor, Offset(marginX, marginY), Offset(marginX + corner, marginY), strokeW)
                                    drawLine(guideColor, Offset(marginX, marginY), Offset(marginX, marginY + corner), strokeW)
                                    // Top-right
                                    drawLine(guideColor, Offset(marginX + boxW, marginY), Offset(marginX + boxW - corner, marginY), strokeW)
                                    drawLine(guideColor, Offset(marginX + boxW, marginY), Offset(marginX + boxW, marginY + corner), strokeW)
                                    // Bottom-left
                                    drawLine(guideColor, Offset(marginX, marginY + boxH), Offset(marginX + corner, marginY + boxH), strokeW)
                                    drawLine(guideColor, Offset(marginX, marginY + boxH), Offset(marginX, marginY + boxH - corner), strokeW)
                                    // Bottom-right
                                    drawLine(guideColor, Offset(marginX + boxW, marginY + boxH), Offset(marginX + boxW - corner, marginY + boxH), strokeW)
                                    drawLine(guideColor, Offset(marginX + boxW, marginY + boxH), Offset(marginX + boxW, marginY + boxH - corner), strokeW)
                                }

                                // Focus reticle at tap point
                                focusTapOffset?.let { tap ->
                                    val ringColor = when (focusSucceeded) {
                                        true -> ProfitGreen
                                        false -> AmberWarn
                                        null -> BurntTerracotta
                                    }
                                    Box(
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    (tap.x - 32.dp.toPx()).roundToInt(),
                                                    (tap.y - 32.dp.toPx()).roundToInt()
                                                )
                                            }
                                            .size(64.dp)
                                            .border(2.dp, ringColor, CircleShape)
                                    )
                                }

                                // Top overlay pills: emulator warning or tap-to-focus hint + flash/torch
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = CameraScrimBlack.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = if (isEmulator) {
                                                "Virtual Emulator Cam • Tap to focus"
                                            } else {
                                                "4:3 Sharp SDR • Tap shoe to focus"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PureWhite,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }

                                    if (hasFlash) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                onClick = {
                                                    val next = !torchEnabled
                                                    torchEnabled = next
                                                    boundCamera?.cameraControl?.enableTorch(next)
                                                },
                                                color = if (torchEnabled) AmberWarn else CameraScrimBlack.copy(alpha = 0.65f),
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("camera_torch_button")
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                                        contentDescription = "Toggle torch",
                                                        tint = if (torchEnabled) CameraScrimBlack else PureWhite,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Bottom overlay quick controls: Zoom & Exposure +/- inside viewfinder
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (maxZoomRatio >= 1.8f) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(1f, 2f).forEach { targetZ ->
                                                val active = kotlin.math.abs(zoomRatio - targetZ) < 0.25f
                                                Surface(
                                                    onClick = {
                                                        val clamped = targetZ.coerceAtMost(maxZoomRatio)
                                                        zoomRatio = clamped
                                                        boundCamera?.cameraControl?.setZoomRatio(clamped)
                                                    },
                                                    color = if (active) BurntTerracotta else CameraScrimBlack.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.heightIn(min = 36.dp)
                                                ) {
                                                    Text(
                                                        "${targetZ.toInt()}x",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = PureWhite,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(Modifier.width(1.dp))
                                    }

                                    if (exposureSupported) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            listOf(-1, 0, 1).forEach { stop ->
                                                val selected = exposureStepIndex == stop
                                                val label = when (stop) {
                                                    -1 -> "EV-"
                                                    0 -> "Auto"
                                                    else -> "EV+"
                                                }
                                                Surface(
                                                    onClick = {
                                                        val cam = boundCamera ?: return@Surface
                                                        val range = cam.cameraInfo.exposureState.exposureCompensationRange
                                                        val targetIdx = when (stop) {
                                                            -1 -> (range.lower / 2).coerceAtLeast(range.lower)
                                                            1 -> (range.upper / 2).coerceAtMost(range.upper)
                                                            else -> 0
                                                        }.coerceIn(range.lower, range.upper)
                                                        exposureStepIndex = stop
                                                        cam.cameraControl.setExposureCompensationIndex(targetIdx)
                                                    },
                                                    color = if (selected) BurntTerracotta else CameraScrimBlack.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(
                                                        label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = PureWhite,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isEmulator && capturedFile == null) {
                    Text(
                        text = "Emulator uses Android's synthetic green-house test feed. Real phones show live optics; tap Gallery below to test with shoe photos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                }

                // Bottom Controls Bar (Shutter / Flip / Gallery OR Review Retake / Use Photo)
                val reviewFile = capturedFile
                if (reviewFile != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                reviewFile.delete()
                                capturedFile = null
                                capturedDimensions = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .testTag("camera_retake_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, PureWhite.copy(alpha = 0.55f))
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = PureWhite)
                            Spacer(Modifier.width(8.dp))
                            Text("Retake", color = PureWhite, style = MaterialTheme.typography.titleMedium)
                        }
                        Button(
                            onClick = { onPhotoReady(reviewFile) },
                            modifier = Modifier
                                .weight(1.4f)
                                .heightIn(min = 56.dp)
                                .testTag("camera_use_photo_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen, contentColor = OnMintInk)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = OnMintInk)
                            Spacer(Modifier.width(8.dp))
                            Text("Use Photo", color = OnMintInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Gallery + Files shortcuts
                        if (allowGalleryFallback && onGalleryPicked != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(PureWhite.copy(alpha = 0.14f))
                                ) {
                                    Icon(
                                        Icons.Rounded.PhotoLibrary,
                                        contentDescription = "Pick from gallery",
                                        tint = PureWhite
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        filesDocumentLauncher.launch(arrayOf("image/*"))
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(PureWhite.copy(alpha = 0.14f))
                                ) {
                                    Icon(
                                        Icons.Rounded.FolderOpen,
                                        contentDescription = "Browse image files",
                                        tint = PureWhite
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.size(52.dp))
                        }

                        // Center: Big Shutter Button
                        val canClickShutter = hasCameraPermission && cameraError == null && boundImageCapture != null && !isCapturing
                        Surface(
                            onClick = {
                                val cap = boundImageCapture ?: return@Surface
                                if (isCapturing) return@Surface
                                isCapturing = true
                                val rawOut = ImageUtils.createTempCameraFile(context)
                                val opts = ImageCapture.OutputFileOptions.Builder(rawOut).build()
                                cap.flashMode = if (hasFlash) flashMode else ImageCapture.FLASH_MODE_OFF
                                cap.takePicture(
                                    opts,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            scope.launch {
                                                val normalized = withContext(Dispatchers.IO) {
                                                    ImageUtils.normalizeCapturedFile(context, rawOut, maxDim = 1200)
                                                } ?: rawOut
                                                if (normalized != rawOut) rawOut.delete()
                                                capturedDimensions = ImageUtils.readImageDimensions(normalized)
                                                capturedFile = normalized
                                                isCapturing = false
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isCapturing = false
                                            cameraError = exception.localizedMessage ?: "Capture failed."
                                        }
                                    }
                                )
                            },
                            enabled = canClickShutter,
                            shape = CircleShape,
                            color = PureWhite,
                            border = BorderStroke(4.dp, BurntTerracotta),
                            modifier = Modifier
                                .size(76.dp)
                                .testTag("camera_shutter_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isCapturing) {
                                    CircularProgressIndicator(
                                        color = BurntTerracotta,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(30.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.PhotoCamera,
                                        contentDescription = "Take photo",
                                        tint = CameraScrimBlack,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                        // Right: Flip Camera or Flash mode cycle
                        if (canSwitchLens) {
                            IconButton(
                                onClick = { useFrontCamera = !useFrontCamera },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(PureWhite.copy(alpha = 0.14f))
                                    .testTag("camera_flip_button")
                            ) {
                                Icon(
                                    Icons.Rounded.Cameraswitch,
                                    contentDescription = "Switch front or back camera",
                                    tint = PureWhite
                                )
                            }
                        } else if (hasFlash) {
                            IconButton(
                                onClick = {
                                    flashMode = when (flashMode) {
                                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                        else -> ImageCapture.FLASH_MODE_AUTO
                                    }
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(PureWhite.copy(alpha = 0.14f))
                            ) {
                                Icon(
                                    when (flashMode) {
                                        ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                                        ImageCapture.FLASH_MODE_OFF -> Icons.Rounded.FlashOff
                                        else -> Icons.Rounded.FlashAuto
                                    },
                                    contentDescription = "Cycle flash mode",
                                    tint = PureWhite
                                )
                            }
                        } else {
                            Spacer(Modifier.size(52.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraUnavailableCard(
    heading: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSystemCamera: () -> Unit,
    onGallery: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WarmCard),
        border = BorderStroke(1.dp, WarmBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.PhotoCamera,
                contentDescription = null,
                tint = BurntTerracotta,
                modifier = Modifier.size(36.dp)
            )
            Text(
                heading,
                style = MaterialTheme.typography.headlineSmall,
                color = InkBlack,
                textAlign = TextAlign.Center
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = InkEspresso,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BurntTerracotta)
            ) {
                Text(primaryLabel, style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = onSystemCamera,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, InkBlack)
            ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = InkBlack)
                Spacer(Modifier.width(8.dp))
                Text("Use System Camera App", color = InkBlack)
            }
            if (onGallery != null) {
                TextButton(
                    onClick = onGallery,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = BurntTerracotta)
                    Spacer(Modifier.width(6.dp))
                    Text("Pick Photo from Gallery", color = BurntTerracotta)
                }
            }
        }
    }
}
