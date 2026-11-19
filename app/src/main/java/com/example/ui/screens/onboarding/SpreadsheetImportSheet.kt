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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.onboarding.TargetField
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataHubUiState
import com.example.ui.viewmodel.OnboardingHubController
import com.example.util.ExportUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetImportSheet(
    hubState: DataHubUiState,
    controller: OnboardingHubController,
    defaultCategory: String,
    defaultUnit: String,
    onDismiss: () -> Unit,
    onOpenBatchReview: (Long) -> Unit
) {
    val context = LocalContext.current
    var forceReimport by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { controller.inspectSpreadsheetUri(it) }
    }

    ModalBottomSheet(
        onDismissRequest = {
            controller.clearSpreadsheetInspect()
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
            Text(
                text = "CSV & Excel Bulk Import",
                style = MaterialTheme.typography.headlineMedium,
                color = TextCharcoal
            )
            Text(
                text = "Import 10 to 10,000+ products cleanly with flexible column mapping, duplicate checks, and resumable chunk commits.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryGrey
            )

            // Downloadable Templates
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        controller.generateSpreadsheetTemplate("CSV") { f ->
                            ExportUtils.shareFile(context, f, "text/csv", "Share CSV Template")
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("download_csv_template_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("CSV Template", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        controller.generateSpreadsheetTemplate("XLSX") { f ->
                            ExportUtils.shareFile(
                                context,
                                f,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "Share Excel XLSX Template"
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("download_xlsx_template_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Excel Template", maxLines = 1)
                }
            }

            Button(
                onClick = {
                    filePicker.launch(
                        arrayOf(
                            "text/csv",
                            "text/comma-separated-values",
                            "text/tab-separated-values",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "*/*"
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("pick_spreadsheet_btn")
            ) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Select CSV or XLSX File", fontWeight = FontWeight.SemiBold)
            }

            if (hubState.isBusy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = EmeraldPrimary, strokeWidth = 2.dp)
                    Text(hubState.busyMessage, style = MaterialTheme.typography.bodySmall, color = EmeraldDark)
                }
            }

            val inspect = hubState.spreadsheetInspect
            if (inspect != null) {
                Surface(
                    color = EmeraldSoft,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${inspect.fileName} (${inspect.preview.fileFormat})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDark
                        )
                        Text(
                            text = "${inspect.preview.estimatedTotalRows} data rows • ${inspect.preview.headers.size} columns detected",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextCharcoal
                        )
                    }
                }

                if (inspect.duplicateBatch != null) {
                    Surface(
                        color = SemanticWarningSoft,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SemanticWarning.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "This exact file matches Batch #${inspect.duplicateBatch.id} (${inspect.duplicateBatch.status}).",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SemanticWarning
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = forceReimport,
                                    onCheckedChange = { forceReimport = it }
                                )
                                Text("Force stage as a new import batch anyway", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                Text("Column Mapping", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                Text(
                    text = "Verify auto-detected fields or switch any column before staging.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryGrey
                )

                inspect.preview.headers.forEachIndexed { idx, headerName ->
                    val sampleVal = inspect.preview.sampleRows.firstOrNull()?.getOrNull(idx).orEmpty()
                    val currentTarget = inspect.activeMapping[idx] ?: TargetField.IGNORE
                    var menuOpen by remember(idx) { mutableStateOf(false) }

                    Surface(
                        color = SurfaceSubtle,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DividerGrey),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = headerName.ifBlank { "Column ${idx + 1}" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextCharcoal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (sampleVal.isNotBlank()) {
                                    Text(
                                        text = "e.g. $sampleVal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMutedGrey,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Box {
                                OutlinedButton(
                                    onClick = { menuOpen = true },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("col_map_btn_$idx"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = currentTarget.displayLabel,
                                        maxLines = 1,
                                        color = if (currentTarget == TargetField.IGNORE) TextMutedGrey else EmeraldDark
                                    )
                                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    TargetField.entries.forEach { field ->
                                        DropdownMenuItem(
                                            text = { Text(field.displayLabel) },
                                            onClick = {
                                                controller.updateSpreadsheetMapping(idx, field)
                                                menuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        controller.stageInspectedSpreadsheet(
                            defaultCategory = defaultCategory,
                            defaultUnit = defaultUnit,
                            forceReimport = forceReimport,
                            onStaged = { batchId ->
                                onDismiss()
                                onOpenBatchReview(batchId)
                            }
                        )
                    },
                    enabled = !hubState.isBusy && inspect.activeMapping.values.any { it != TargetField.IGNORE },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("stage_spreadsheet_btn")
                ) {
                    Icon(Icons.Rounded.FactCheck, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stage & Preview Rows", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
