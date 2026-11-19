package com.example.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.StagedImportItemEntity
import com.example.domain.onboarding.BusinessActionType
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataHubUiState
import com.example.ui.viewmodel.OnboardingHubController
import com.example.util.ExportUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StagingReviewScreen(
    batchId: Long,
    currency: String,
    hubState: DataHubUiState,
    controller: OnboardingHubController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val batch by controller.observeBatch(batchId).collectAsStateWithLifecycle(initialValue = null)
    val items by controller.observeBatchItems(batchId).collectAsStateWithLifecycle(initialValue = emptyList())

    var filterMode by remember { mutableStateOf("All") }
    var editingItem by remember { mutableStateOf<StagedImportItemEntity?>(null) }
    val filters = listOf("All", "Valid", "Errors", "Duplicates", "Low Conf", "Committed")

    val filteredItems = remember(items, filterMode) {
        when (filterMode) {
            "Valid" -> items.filter { it.validationErrors.isBlank() && it.approvalStatus != "COMMITTED" }
            "Errors" -> items.filter { it.validationErrors.isNotBlank() || it.approvalStatus == "FAILED" }
            "Duplicates" -> items.filter { it.isDuplicate }
            "Low Conf" -> items.filter { it.confidence < 0.72f }
            "Committed" -> items.filter { it.approvalStatus == "COMMITTED" }
            else -> items
        }
    }

    val approvedReadyCount = remember(items) {
        items.count { it.approvalStatus == "APPROVED" && it.resolutionMode != "SKIP" && it.validationErrors.isBlank() }
    }
    val validPendingCount = remember(items) {
        items.count { it.approvalStatus == "PENDING" && it.validationErrors.isBlank() }
    }
    val errorCount = remember(items) {
        items.count { it.validationErrors.isNotBlank() || it.approvalStatus == "FAILED" }
    }

    Scaffold(
        containerColor = CanvasWarmWhite,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(batch?.fileName ?: "Staging Batch #$batchId", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            "${batch?.sourceRoute ?: ""} • Status: ${batch?.status ?: "STAGED"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryGrey
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (errorCount > 0) {
                        IconButton(
                            onClick = {
                                controller.exportBatchErrorCsv(batchId) { file ->
                                    ExportUtils.shareFile(context, file, "text/csv", "Import Error Report")
                                }
                            },
                            modifier = Modifier.size(48.dp).testTag("export_batch_errors_btn")
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = "Download error CSV", tint = SemanticError)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Batch Status & KPI Card
            Surface(
                color = SurfaceWhite,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DividerGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total: ${items.size}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("Ready: $approvedReadyCount", style = MaterialTheme.typography.labelLarge, color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                        Text("Errors: $errorCount", style = MaterialTheme.typography.labelLarge, color = if (errorCount > 0) SemanticError else TextSecondaryGrey)
                    }
                    if (hubState.isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = EmeraldPrimary)
                        Text(hubState.busyMessage, style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (validPendingCount > 0) {
                            OutlinedButton(
                                onClick = { controller.approveAllValidInBatch(batchId) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("approve_all_valid_btn")
                            ) {
                                Text("Approve Valid ($validPendingCount)", maxLines = 1)
                            }
                        }
                        Button(
                            onClick = { controller.commitBatch(batchId, autoApproveValid = (approvedReadyCount == 0 && validPendingCount > 0)) },
                            enabled = !hubState.isBusy && (approvedReadyCount > 0 || validPendingCount > 0),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                            modifier = Modifier.weight(1.2f).heightIn(min = 48.dp).testTag("commit_batch_btn")
                        ) {
                            val label = when {
                                batch?.status == "PARTIAL" -> "Resume Commit"
                                approvedReadyCount > 0 -> "Commit ($approvedReadyCount)"
                                else -> "Approve & Commit"
                            }
                            Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { f ->
                    FilterChip(
                        selected = filterMode == f,
                        onClick = { filterMode = f },
                        label = { Text(f) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    StagedRowCard(
                        item = item,
                        currency = currency,
                        onToggleApprove = { approve ->
                            val nextStatus = if (approve) "APPROVED" else "REJECTED"
                            controller.updateStagedItem(item.copy(approvalStatus = nextStatus))
                        },
                        onEdit = { editingItem = item }
                    )
                }
            }
        }
    }

    editingItem?.let { current ->
        EditStagedItemDialog(
            item = current,
            onDismiss = { editingItem = null },
            onSave = { updated ->
                controller.updateStagedItem(updated)
                editingItem = null
            }
        )
    }
}

@Composable
private fun StagedRowCard(
    item: StagedImportItemEntity,
    currency: String,
    onToggleApprove: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    val action = BusinessActionType.fromKey(item.actionType)
    val isCommitted = item.approvalStatus == "COMMITTED"
    val hasErr = item.validationErrors.isNotBlank()
    val confPct = (item.confidence * 100).roundToInt()

    Surface(
        color = SurfaceWhite,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            when {
                hasErr -> SemanticError.copy(alpha = 0.4f)
                isCommitted -> SemanticSuccess.copy(alpha = 0.4f)
                else -> DividerGrey
            }
        ),
        modifier = Modifier.fillMaxWidth().testTag("staged_row_${item.rowNumber}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = if (action == BusinessActionType.PURCHASE_REQUEST || action == BusinessActionType.PURCHASE_ORDER) {
                            SemanticWarningSoft
                        } else EmeraldSoft,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextCharcoal,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(color = SurfaceSubtle, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = item.resolutionMode,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryGrey,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (confPct < 90) {
                        Text("$confPct%", style = MaterialTheme.typography.labelSmall, color = if (confPct < 70) SemanticWarning else TextMutedGrey)
                    }
                }

                if (!isCommitted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit staged item", modifier = Modifier.size(18.dp))
                        }
                        Checkbox(
                            checked = item.approvalStatus == "APPROVED",
                            onCheckedChange = onToggleApprove,
                            enabled = !hasErr
                        )
                    }
                } else {
                    Text("COMMITTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SemanticSuccess)
                }
            }

            Text(
                text = "#${item.rowNumber} ${item.productName.ifBlank { item.partyName.ifBlank { "(Unnamed)" } }}",
                style = MaterialTheme.typography.titleSmall,
                color = TextCharcoal
            )
            Text(
                text = buildString {
                    if (item.sku.isNotBlank()) append("SKU: ${item.sku} • ")
                    if (item.barcode.isNotBlank()) append("Barcode: ${item.barcode} • ")
                    append("Qty ${item.quantity} ${item.unitLabel} (${item.variantLabel})")
                    item.sellingPrice?.let { append(" • Sell $currency${it.roundToInt()}") }
                    item.costPrice?.let { append(" • Cost $currency${it.roundToInt()}") }
                    if (item.amount > 0 && item.sellingPrice == null) append(" • Amt $currency${item.amount.roundToInt()}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryGrey
            )
            if (hasErr) {
                Text(item.validationErrors, style = MaterialTheme.typography.labelSmall, color = SemanticError)
            }
            if (item.isDuplicate) {
                Text("Duplicate flag detected — review resolution mode.", style = MaterialTheme.typography.labelSmall, color = SemanticWarning)
            }
        }
    }
}

@Composable
private fun EditStagedItemDialog(
    item: StagedImportItemEntity,
    onDismiss: () -> Unit,
    onSave: (StagedImportItemEntity) -> Unit
) {
    var actionKey by remember { mutableStateOf(item.actionType) }
    var resolution by remember { mutableStateOf(item.resolutionMode) }
    var name by remember { mutableStateOf(item.productName) }
    var sku by remember { mutableStateOf(item.sku) }
    var barcode by remember { mutableStateOf(item.barcode) }
    var variant by remember { mutableStateOf(item.variantLabel) }
    var unit by remember { mutableStateOf(item.unitLabel) }
    var qty by remember { mutableStateOf(item.quantity.toString()) }
    var cost by remember { mutableStateOf(item.costPrice?.toString() ?: "") }
    var sell by remember { mutableStateOf(item.sellingPrice?.toString() ?: "") }
    var amount by remember { mutableStateOf(if (item.amount > 0) item.amount.toString() else "") }
    var party by remember { mutableStateOf(item.partyName) }
    var actionDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Edit Row #${item.rowNumber}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    OutlinedButton(onClick = { actionDropdown = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Action: ${BusinessActionType.fromKey(actionKey).label}", modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = actionDropdown, onDismissRequest = { actionDropdown = false }) {
                        BusinessActionType.entries.forEach { act ->
                            DropdownMenuItem(
                                text = { Text(act.label) },
                                onClick = {
                                    actionKey = act.key
                                    actionDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CREATE_NEW", "UPDATE_EXISTING", "MERGE_STOCK", "SKIP").forEach { mode ->
                        FilterChip(
                            selected = resolution == mode,
                            onClick = { resolution = mode },
                            label = { Text(mode.substringBefore('_'), maxLines = 1) }
                        )
                    }
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(0.8f))
                    OutlinedTextField(value = variant, onValueChange = { variant = it }, label = { Text("Variant") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Cost") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sell, onValueChange = { sell = it }, label = { Text("Sell") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amt") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = party, onValueChange = { party = it }, label = { Text("Customer / Supplier") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        item.copy(
                            actionType = actionKey,
                            resolutionMode = resolution,
                            productName = name.trim(),
                            sku = sku.trim(),
                            barcode = barcode.trim(),
                            variantLabel = variant.trim().ifBlank { "Standard" },
                            unitLabel = unit.trim().ifBlank { "pcs" },
                            quantity = qty.toDoubleOrNull() ?: item.quantity,
                            costPrice = cost.toDoubleOrNull(),
                            sellingPrice = sell.toDoubleOrNull(),
                            amount = amount.toDoubleOrNull() ?: item.amount,
                            partyName = party.trim(),
                            approvalStatus = "APPROVED"
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite)
            ) { Text("Save & Approve") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
