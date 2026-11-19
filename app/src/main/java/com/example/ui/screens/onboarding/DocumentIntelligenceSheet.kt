package com.example.ui.screens.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ProductEntity
import com.example.domain.onboarding.BusinessActionType
import com.example.domain.onboarding.DocumentSourceInput
import com.example.ui.components.CameraCaptureDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataHubUiState
import com.example.ui.viewmodel.OnboardingHubController
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentIntelligenceSheet(
    hubState: DataHubUiState,
    catalogProducts: List<ProductEntity>,
    currency: String,
    controller: OnboardingHubController,
    onDismiss: () -> Unit,
    onOpenBatchReview: (Long) -> Unit
) {
    var noteInput by remember { mutableStateOf("") }
    var showCameraDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            controller.extractDocuments(
                listOf(DocumentSourceInput.ContentUri(it, "image/*", "Selected Photo")),
                catalogProducts
            )
        }
    }

    val pdfOrDocPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val inputs = uris.mapIndexed { idx, u ->
                DocumentSourceInput.ContentUri(u, "application/pdf", "Document Page Set ${idx + 1}")
            }
            controller.extractDocuments(inputs, catalogProducts)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            controller.clearDocExtraction()
            onDismiss()
        },
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DividerGrey) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Paper-to-Action AI Document Scan", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)

            // Honest transparent engine capability banner
            Surface(
                color = EmeraldSoft,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "On-Device Multilingual OCR & Retail Classifier",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDark
                    )
                    Text(
                        text = "Works offline for printed English & Hindi invoices, stock registers, slips & multi-page PDFs, plus English/Hindi/Bengali text notes. Handwritten notes & Bengali images are flagged for manual review before approval.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextCharcoal
                    )
                }
            }

            // Capture / Pick Source Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { showCameraDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("doc_camera_btn")
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Camera", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("doc_gallery_btn")
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Photo", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        pdfOrDocPicker.launch(arrayOf("application/pdf", "image/*"))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("doc_pdf_btn")
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDFs", maxLines = 1)
                }
            }

            // Direct slip / multilingual note extraction
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("Or paste/type handwritten slip, WhatsApp order, EN/HI/BN note") },
                placeholder = { Text("e.g.\nDelivered 2 dozen Milk @ 50\nShortage / Reorder: Sugar 50kg @ 38") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("doc_note_input")
            )
            OutlinedButton(
                onClick = {
                    controller.extractDocuments(
                        listOf(DocumentSourceInput.PlainTextNote(noteInput)),
                        catalogProducts
                    )
                },
                enabled = noteInput.isNotBlank() && !hubState.isBusy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doc_extract_note_btn")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Analyze Note & Classify Actions")
            }

            if (hubState.isBusy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = EmeraldPrimary, strokeWidth = 2.dp)
                    Text(hubState.busyMessage, style = MaterialTheme.typography.bodySmall, color = EmeraldDark)
                }
            }

            val doc = hubState.docExtraction
            if (doc != null) {
                Surface(
                    color = SurfaceSubtle,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DividerGrey),
                    modifier = Modifier.fillMaxWidth().testTag("doc_extraction_preview_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(doc.documentTypeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            val pct = (doc.overallConfidence * 100).roundToInt()
                            Surface(
                                color = if (pct >= 75) SemanticSuccessSoft else SemanticWarningSoft,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "$pct% Conf • ${doc.detectedLanguages.joinToString("+")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (pct >= 75) SemanticSuccess else SemanticWarning,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (doc.warnings.isNotEmpty()) {
                            doc.warnings.forEach { w ->
                                Text("• $w", style = MaterialTheme.typography.labelSmall, color = SemanticWarning)
                            }
                        }

                        val actionCounts = doc.lines.groupingBy { it.actionType }.eachCount()
                        if (actionCounts.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                actionCounts.forEach { (action, count) ->
                                    Surface(
                                        color = if (action == BusinessActionType.PURCHASE_REQUEST || action == BusinessActionType.PURCHASE_ORDER) {
                                            SemanticWarningSoft
                                        } else EmeraldSoft,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "${action.label}: $count",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextCharcoal,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        doc.lines.take(8).forEach { line ->
                            Surface(
                                color = SurfaceWhite,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, DividerGrey),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "${line.actionType.label}: ${line.productName.ifBlank { line.partyName.ifBlank { line.rawLineText } }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val priceStr = line.sellingPrice ?: line.costPrice ?: line.amount.takeIf { it > 0 }
                                    Text(
                                        "Qty ${line.quantity} ${line.unitLabel}" +
                                            (priceStr?.let { " • $currency${it.roundToInt()}" } ?: "") +
                                            " (${line.sourceDocPage})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondaryGrey
                                    )
                                }
                            }
                        }
                        if (doc.lines.size > 8) {
                            Text("+ ${doc.lines.size - 8} more lines in full staging review...", style = MaterialTheme.typography.labelSmall, color = TextMutedGrey)
                        }

                        Button(
                            onClick = {
                                controller.stageDocExtraction { batchId ->
                                    onDismiss()
                                    onOpenBatchReview(batchId)
                                }
                            },
                            enabled = doc.lines.isNotEmpty() && !hubState.isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("stage_doc_extraction_btn")
                        ) {
                            Icon(Icons.Rounded.FactCheck, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Review & Approve in Staging", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showCameraDialog) {
        CameraCaptureDialog(
            title = "Scan Invoice / Register / Slip",
            subtitle = "Hold flat in bright light • Multi-page slips supported",
            allowGalleryFallback = true,
            onDismiss = { showCameraDialog = false },
            onPhotoReady = { file ->
                showCameraDialog = false
                controller.extractDocuments(
                    listOf(DocumentSourceInput.ImageFile(file, "Camera Document")),
                    catalogProducts
                )
            },
            onGalleryPicked = { uri ->
                showCameraDialog = false
                controller.extractDocuments(
                    listOf(DocumentSourceInput.ContentUri(uri, "image/*", "Picked Photo")),
                    catalogProducts
                )
            }
        )
    }
}
