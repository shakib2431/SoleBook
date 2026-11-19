package com.example.ui.screens.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ProductEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataHubUiState
import com.example.ui.viewmodel.OnboardingHubController
import com.example.util.ExportUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImageImportSheet(
    hubState: DataHubUiState,
    products: List<ProductEntity>,
    controller: OnboardingHubController,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mappingCsvUri by remember { mutableStateOf<Uri?>(null) }
    var imageUrlText by remember { mutableStateOf("") }
    var selectedProductForUrl by remember { mutableStateOf(products.firstOrNull()) }
    var productMenuOpen by remember { mutableStateOf(false) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { controller.importImagesFromZipUri(it, mappingCsvUri) }
    }
    val mapCsvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        mappingCsvUri = uri
    }

    ModalBottomSheet(
        onDismissRequest = {
            controller.clearImageImportReport()
            onDismiss()
        },
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DividerGrey) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Product Images Bulk Upload", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Text(
                text = "Upload a ZIP archive named by SKU or barcode (e.g. SL-001_Black.jpg), include an optional CSV mapping sheet, or download from URLs. SHA-256 duplicates are skipped automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryGrey
            )

            OutlinedButton(
                onClick = { mapCsvPicker.launch(arrayOf("text/csv", "*/*")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pick_image_mapping_csv_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (mappingCsvUri != null) "Mapping CSV Selected" else "Optional: Select Mapping CSV")
            }

            Button(
                onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("pick_image_zip_btn")
            ) {
                Icon(Icons.Rounded.FolderZip, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Select Images ZIP Archive", fontWeight = FontWeight.SemiBold)
            }

            if (hubState.isBusy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = EmeraldPrimary, strokeWidth = 2.dp)
                    Text(hubState.busyMessage, style = MaterialTheme.typography.bodySmall, color = EmeraldDark)
                }
            }

            hubState.imageImportReport?.let { rep ->
                Surface(
                    color = SurfaceSubtle,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DividerGrey),
                    modifier = Modifier.fillMaxWidth().testTag("image_import_summary_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Import Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Matched & compressed: ${rep.matchedCount} / ${rep.totalProcessed}", color = SemanticSuccess, style = MaterialTheme.typography.bodyMedium)
                        Text("Duplicates skipped (SHA-256): ${rep.duplicateSkippedCount}", style = MaterialTheme.typography.bodySmall, color = TextSecondaryGrey)
                        Text("Unmatched image files: ${rep.unmatchedFiles.size}", style = MaterialTheme.typography.bodySmall, color = if (rep.unmatchedFiles.isNotEmpty()) SemanticWarning else TextSecondaryGrey)
                        Text("Catalog items without photos: ${rep.productsMissingImagesCount}", style = MaterialTheme.typography.bodySmall, color = TextSecondaryGrey)

                        if (rep.reportCsvPath.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    ExportUtils.shareFile(context, File(rep.reportCsvPath), "text/csv", "Image Import Report")
                                },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("share_image_report_btn")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share Missing & Unmatched CSV")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = DividerGrey)
            Text("Attach Image by URL (Optional)", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)

            if (products.isNotEmpty()) {
                Box {
                    OutlinedButton(
                        onClick = { productMenuOpen = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = selectedProductForUrl?.let { "${it.skuCode} • ${it.name}" } ?: "Select Product",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = productMenuOpen, onDismissRequest = { productMenuOpen = false }) {
                        products.take(40).forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.skuCode} • ${p.name}") },
                                onClick = {
                                    selectedProductForUrl = p
                                    productMenuOpen = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = imageUrlText,
                    onValueChange = { imageUrlText = it },
                    label = { Text("Image HTTPS URL") },
                    placeholder = { Text("https://example.com/product.jpg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("image_url_input")
                )

                Button(
                    onClick = {
                        val target = selectedProductForUrl
                        if (target != null && imageUrlText.isNotBlank()) {
                            controller.attachImageUrlToProduct(target.id, imageUrlText.trim())
                            imageUrlText = ""
                        }
                    },
                    enabled = selectedProductForUrl != null && imageUrlText.startsWith("http", ignoreCase = true) && !hubState.isBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("attach_image_url_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite)
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Download & Save Locally")
                }
            } else {
                Text("Add or import products first before linking URLs.", style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
            }
        }
    }
}
