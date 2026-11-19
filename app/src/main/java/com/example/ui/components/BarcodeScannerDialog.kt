package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live CameraX + ML Kit barcode/QR scanner dialog with manual/USB-HID barcode entry
 * and gallery image barcode decoding fallback (zero permission required for gallery).
 */
@Composable
fun BarcodeScannerDialog(
    title: String = "Scan Barcode / QR",
    subtitle: String = "Align barcode inside frame or type below",
    onDismiss: () -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequestedOnce) {
            permissionRequestedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var manualCode by remember { mutableStateOf("") }
    var torchOn by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val detectedOnce = remember { AtomicBoolean(false) }

    val galleryBarcodeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val scanner = BarcodeScanning.getClient()
            runCatching {
                val input = InputImage.fromFilePath(context, uri)
                scanner.process(input)
                    .addOnSuccessListener { list ->
                        val raw = list.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.trim()
                        if (!raw.isNullOrBlank()) {
                            onBarcodeDetected(raw)
                        } else {
                            Toast.makeText(context, "No barcode found in image", Toast.LENGTH_SHORT).show()
                        }
                        scanner.close()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Could not scan image", Toast.LENGTH_SHORT).show()
                        scanner.close()
                    }
            }.onFailure {
                scanner.close()
                Toast.makeText(context, "Unable to read selected image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("barcode_scanner_dialog"),
            color = CameraScrimBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close scanner", tint = PureWhite)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, color = PureWhite, fontWeight = FontWeight.Bold)
                            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = PureWhite.copy(alpha = 0.72f))
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            galleryBarcodeLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("barcode_gallery_scan_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.35f))
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("From Photo", color = PureWhite, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!hasCameraPermission) {
                        Surface(
                            color = GlassDarkSurface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.16f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(44.dp))
                                Text("Camera Permission Needed", style = MaterialTheme.typography.titleMedium, color = PureWhite, fontWeight = FontWeight.Bold)
                                Text(
                                    "Allow camera access for live barcode scanning, or enter the barcode manually below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PureWhite.copy(alpha = 0.75f)
                                )
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Allow Camera")
                                }
                            }
                        }
                    } else if (cameraError != null) {
                        Surface(
                            color = GlassDarkSurface,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = AmberWarn, modifier = Modifier.size(40.dp))
                                Text("Live Camera Unavailable", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                Text(cameraError ?: "", style = MaterialTheme.typography.bodySmall, color = PureWhite.copy(alpha = 0.7f))
                                Text("Type barcode or scan from gallery image.", style = MaterialTheme.typography.labelSmall, color = PureWhite.copy(alpha = 0.75f))
                            }
                        }
                    } else {
                        val barcodeScanner = remember { BarcodeScanning.getClient() }
                        val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
                        val controller = remember {
                            LifecycleCameraController(context).apply {
                                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                            }
                        }

                        DisposableEffect(lifecycleOwner) {
                            try {
                                controller.setImageAnalysisAnalyzer(
                                    analysisExecutor,
                                    MlKitAnalyzer(
                                        listOf(barcodeScanner),
                                        CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
                                        ContextCompat.getMainExecutor(context)
                                    ) { result ->
                                        val codes = result?.getValue(barcodeScanner)
                                        val first = codes?.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.trim()
                                        if (!first.isNullOrBlank() && detectedOnce.compareAndSet(false, true)) {
                                            onBarcodeDetected(first)
                                        }
                                    }
                                )
                                controller.bindToLifecycle(lifecycleOwner)
                                cameraError = null
                            } catch (e: Exception) {
                                cameraError = e.localizedMessage ?: "Camera preview unavailable"
                            }

                            onDispose {
                                runCatching { controller.clearImageAnalysisAnalyzer() }
                                runCatching { controller.unbind() }
                                runCatching { barcodeScanner.close() }
                                runCatching { analysisExecutor.shutdown() }
                            }
                        }

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
                                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        scaleType = PreviewView.ScaleType.FIT_CENTER
                                        this.controller = controller
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Crisp laser-reticle overlay
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val mx = size.width * 0.12f
                                val my = size.height * 0.28f
                                val bw = size.width - mx * 2f
                                val bh = size.height - my * 2f
                                val c = 24.dp.toPx()
                                val sw = 3.dp.toPx()
                                val col = ProfitGreenMint
                                drawLine(col, Offset(mx, my), Offset(mx + c, my), sw)
                                drawLine(col, Offset(mx, my), Offset(mx, my + c), sw)
                                drawLine(col, Offset(mx + bw, my), Offset(mx + bw - c, my), sw)
                                drawLine(col, Offset(mx + bw, my), Offset(mx + bw, my + c), sw)
                                drawLine(col, Offset(mx, my + bh), Offset(mx + c, my + bh), sw)
                                drawLine(col, Offset(mx, my + bh), Offset(mx, my + bh - c), sw)
                                drawLine(col, Offset(mx + bw, my + bh), Offset(mx + bw - c, my + bh), sw)
                                drawLine(col, Offset(mx + bw, my + bh), Offset(mx + bw, my + bh - c), sw)
                            }

                            IconButton(
                                onClick = {
                                    torchOn = !torchOn
                                    runCatching { controller.enableTorch(torchOn) }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (torchOn) AmberWarn else CameraScrimBlack.copy(alpha = 0.65f))
                            ) {
                                Icon(
                                    if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                    contentDescription = "Toggle flash",
                                    tint = if (torchOn) CameraScrimBlack else PureWhite
                                )
                            }
                        }
                    }
                }

                // Manual / USB-HID Scanner Input Card
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier.fillMaxWidth().imePadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = manualCode,
                            onValueChange = { manualCode = it },
                            label = { Text("Enter or scan barcode / SKU") },
                            placeholder = { Text("e.g. 8901491101837") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    val clean = manualCode.trim()
                                    if (clean.isNotEmpty()) onBarcodeDetected(clean)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("barcode_manual_input")
                        )
                        Button(
                            onClick = {
                                val clean = manualCode.trim()
                                if (clean.isNotEmpty()) onBarcodeDetected(clean)
                            },
                            enabled = manualCode.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 52.dp).testTag("barcode_manual_submit")
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Find")
                        }
                    }
                }
            }
        }
    }
}
