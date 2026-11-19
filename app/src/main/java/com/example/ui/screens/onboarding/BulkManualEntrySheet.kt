package com.example.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.onboarding.BusinessActionType
import com.example.domain.onboarding.StagedRowDraft
import com.example.domain.onboarding.UnitCatalog
import com.example.ui.theme.*
import com.example.ui.viewmodel.OnboardingHubController

data class ManualGridRowUi(
    val id: Int,
    var name: String = "",
    var sku: String = "",
    var barcode: String = "",
    var category: String = "",
    var unit: String = "pcs",
    var variant: String = "Standard",
    var cost: String = "",
    var sell: String = "",
    var qty: String = "1"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkManualEntrySheet(
    defaultCategory: String,
    defaultUnit: String,
    currency: String,
    controller: OnboardingHubController,
    onDismiss: () -> Unit,
    onOpenBatchReview: (Long) -> Unit
) {
    var nextId by remember { mutableIntStateOf(4) }
    val rows = remember {
        mutableStateListOf(
            ManualGridRowUi(1, category = defaultCategory, unit = defaultUnit),
            ManualGridRowUi(2, category = defaultCategory, unit = defaultUnit),
            ManualGridRowUi(3, category = defaultCategory, unit = defaultUnit)
        )
    }
    var showPasteBox by remember { mutableStateOf(false) }
    var pastedGridText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Text("Fast Multi-Row Manual Entry", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Text(
                text = "Enter multiple products rapidly or paste comma/tab-separated lines from Notes, WhatsApp, or Excel.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryGrey
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        repeat(5) {
                            rows += ManualGridRowUi(nextId++, category = defaultCategory, unit = defaultUnit)
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("manual_add_5_rows_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+5 Rows")
                }
                OutlinedButton(
                    onClick = {
                        val last = rows.lastOrNull()
                        if (last != null) {
                            rows += last.copy(id = nextId++, sku = "", barcode = "")
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dup Last")
                }
                OutlinedButton(
                    onClick = { showPasteBox = !showPasteBox },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("manual_paste_toggle_btn")
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Paste")
                }
            }

            if (showPasteBox) {
                Surface(
                    color = SurfaceSubtle,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DividerGrey),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Paste lines: Name, Qty, Cost, Sell [, Category, Variant, Unit, Barcode]",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryGrey
                        )
                        OutlinedTextField(
                            value = pastedGridText,
                            onValueChange = { pastedGridText = it },
                            minLines = 3,
                            placeholder = { Text("Sugar 1kg, 25, 38, 45\nBasmati Rice 5kg, 10, 420, 490") },
                            modifier = Modifier.fillMaxWidth().testTag("manual_paste_input")
                        )
                        Button(
                            onClick = {
                                val lines = pastedGridText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                lines.forEach { line ->
                                    val parts = if (line.contains('\t')) line.split('\t') else line.split(',')
                                    rows += ManualGridRowUi(
                                        id = nextId++,
                                        name = parts.getOrNull(0)?.trim().orEmpty(),
                                        qty = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "1" },
                                        cost = parts.getOrNull(2)?.trim().orEmpty(),
                                        sell = parts.getOrNull(3)?.trim().orEmpty(),
                                        category = parts.getOrNull(4)?.trim().orEmpty().ifBlank { defaultCategory },
                                        variant = parts.getOrNull(5)?.trim().orEmpty().ifBlank { "Standard" },
                                        unit = UnitCatalog.normalize(parts.getOrNull(6)?.trim().orEmpty(), defaultUnit),
                                        barcode = parts.getOrNull(7)?.trim().orEmpty()
                                    )
                                }
                                pastedGridText = ""
                                showPasteBox = false
                            },
                            enabled = pastedGridText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite)
                        ) {
                            Text("Append Pasted Rows")
                        }
                    }
                }
            }

            rows.forEachIndexed { idx, row ->
                Surface(
                    color = SurfaceSubtle,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DividerGrey),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Row ${idx + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EmeraldDark)
                            if (rows.size > 1) {
                                IconButton(
                                    onClick = { rows.removeAt(idx) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove row", tint = TextMutedGrey)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { rows[idx] = row.copy(name = it) },
                            label = { Text("Product Name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("manual_row_name_$idx")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = row.cost,
                                onValueChange = { s -> rows[idx] = row.copy(cost = s.filter { it.isDigit() || it == '.' }) },
                                label = { Text("Cost ($currency)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = row.sell,
                                onValueChange = { s -> rows[idx] = row.copy(sell = s.filter { it.isDigit() || it == '.' }) },
                                label = { Text("Sell ($currency) *") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("manual_row_sell_$idx")
                            )
                            OutlinedTextField(
                                value = row.qty,
                                onValueChange = { s -> rows[idx] = row.copy(qty = s.filter { it.isDigit() || it == '.' }) },
                                label = { Text("Qty") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.75f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = row.variant,
                                onValueChange = { rows[idx] = row.copy(variant = it) },
                                label = { Text("Variant") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = row.unit,
                                onValueChange = { rows[idx] = row.copy(unit = it) },
                                label = { Text("Unit") },
                                singleLine = true,
                                modifier = Modifier.weight(0.7f)
                            )
                            OutlinedTextField(
                                value = row.barcode,
                                onValueChange = { rows[idx] = row.copy(barcode = it) },
                                label = { Text("Barcode/SKU") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            val populated = rows.filter { it.name.isNotBlank() || it.barcode.isNotBlank() }
            Button(
                onClick = {
                    val drafts = populated.mapIndexed { idx, r ->
                        StagedRowDraft(
                            rowNumber = idx + 1,
                            actionType = BusinessActionType.CREATE_PRODUCT,
                            sku = r.sku.trim(),
                            barcode = r.barcode.trim(),
                            productName = r.name.trim(),
                            category = r.category.trim().ifBlank { defaultCategory },
                            unitLabel = UnitCatalog.normalize(r.unit, defaultUnit),
                            variantLabel = r.variant.trim().ifBlank { "Standard" },
                            quantity = r.qty.toDoubleOrNull() ?: 0.0,
                            costPrice = r.cost.toDoubleOrNull(),
                            sellingPrice = r.sell.toDoubleOrNull()
                        )
                    }
                    controller.stageBulkManualDrafts(drafts, "Bulk Manual (${drafts.size} rows)") { batchId ->
                        onDismiss()
                        onOpenBatchReview(batchId)
                    }
                },
                enabled = populated.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("stage_manual_rows_btn")
            ) {
                Icon(Icons.Rounded.FactCheck, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Stage ${populated.size} Row(s) for Review", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
