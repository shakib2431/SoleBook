package com.example.ui.screens.wholesale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.data.local.BomMaterialRuleEntity
import com.example.data.local.BomTemplateEntity
import com.example.data.local.RawMaterialEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.Formatters

@Composable
fun BomAndMaterialsScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onUseTemplateInQuote: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotices: () -> Unit
) {
    val currency = state.settings.currencySymbol
    val bottomDockPadding = LocalGlassBottomPadding.current
    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = BOM Formulas, 1 = Raw Materials Rate Card

    var editingMaterial by remember { mutableStateOf<RawMaterialEntity?>(null) }
    var showMaterialDialog by remember { mutableStateOf(false) }

    var editingTemplate by remember { mutableStateOf<BomTemplateEntity?>(null) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    val rulesByTemplate = remember(state.bomMaterialRules) {
        state.bomMaterialRules.groupBy { it.templateId }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWarmWhite)
            .testTag("wholesale_bom_screen"),
        contentPadding = PaddingValues(bottom = 24.dp + bottomDockPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SoleBookTopBar(
                title = "BOM & Material Rates",
                subtitle = "Formulas scale automatically to custom order sizes",
                onSettingsClick = onOpenSettings,
                unreadNoticeCount = state.unreadNoticeCount,
                onNoticesClick = onOpenNotices
            )
        }

        // Sub-tab switcher
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeSubTab == 0,
                    onClick = { activeSubTab = 0 },
                    label = { Text("BOM Formulas (${state.bomTemplates.size})") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("bom_subtab_formulas"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = PureWhite
                    )
                )
                FilterChip(
                    selected = activeSubTab == 1,
                    onClick = { activeSubTab = 1 },
                    label = { Text("Material Rates (${state.rawMaterials.size})") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("bom_subtab_materials"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = PureWhite
                    )
                )
            }
        }

        // Action row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeSubTab == 0) {
                    PrimaryButton(
                        text = "New BOM Formula",
                        icon = Icons.Rounded.Add,
                        onClick = {
                            editingTemplate = null
                            showTemplateDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "add_bom_template_btn"
                    )
                } else {
                    PrimaryButton(
                        text = "Add Raw Material",
                        icon = Icons.Rounded.Add,
                        onClick = {
                            editingMaterial = null
                            showMaterialDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "add_raw_material_btn"
                    )
                }
                if (state.bomTemplates.isEmpty() || state.rawMaterials.isEmpty()) {
                    SecondaryButton(
                        text = "Starter Pack",
                        icon = Icons.Rounded.AutoAwesome,
                        onClick = { viewModel.wholesaleController.loadMattressStarterBom() },
                        testTag = "bom_load_starter_btn"
                    )
                }
            }
        }

        if (activeSubTab == 0) {
            if (state.bomTemplates.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                        EmptyState(
                            icon = Icons.Rounded.Architecture,
                            title = "No BOM formulas defined",
                            subtitle = "Create a product recipe (e.g. Cotton Mattress 72×36×4 baseline) or load the starter pack to calculate exact cotton kg, cloth meters, and stitching for any custom size.",
                            primaryLabel = "Load Starter BOM",
                            onPrimaryClick = { viewModel.wholesaleController.loadMattressStarterBom() },
                            secondaryLabel = "Create Custom Formula",
                            onSecondaryClick = {
                                editingTemplate = null
                                showTemplateDialog = true
                            }
                        )
                    }
                }
            } else {
                items(state.bomTemplates, key = { it.id }) { tpl ->
                    val rules = rulesByTemplate[tpl.id].orEmpty()
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SoleBookSpacing.screenHorizontal),
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerGrey)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tpl.name, style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                                    Text(
                                        "Baseline: ${tpl.refLengthIn.toInt()}\"×${tpl.refWidthIn.toInt()}\"×${tpl.refThicknessIn.toInt()}\" • Stitch ${Formatters.formatCurrency(tpl.defaultStitchCost, currency)}/pc • Margin ${tpl.defaultMarginPct}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondaryGrey
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingTemplate = tpl
                                        showTemplateDialog = true
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit formula", tint = TextSecondaryGrey)
                                }
                            }
                            rules.forEach { r ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "• ${r.materialNameSnapshot} (${r.ruleType.lowercase().replace('_', ' ')})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextCharcoal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${r.refQtyPerPiece} ${r.unit} (+${r.wastagePct}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Button(
                                onClick = { onUseTemplateInQuote(tpl.id) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = PureWhite)
                            ) {
                                Text("Use Formula in New Quote", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        } else {
            if (state.rawMaterials.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                        EmptyState(
                            icon = Icons.Rounded.Layers,
                            title = "No raw materials added",
                            subtitle = "Add your supplier cost rates (e.g. Cotton ₹140/kg, Damask Cloth ₹115/m). Stock tracking is optional per material.",
                            primaryLabel = "Add Raw Material",
                            onPrimaryClick = {
                                editingMaterial = null
                                showMaterialDialog = true
                            },
                            secondaryLabel = "Load Starter Rates",
                            onSecondaryClick = { viewModel.wholesaleController.loadMattressStarterBom() }
                        )
                    }
                }
            } else {
                items(state.rawMaterials, key = { it.id }) { mat ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SoleBookSpacing.screenHorizontal),
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerGrey)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(mat.name, style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                                val rollInfo = if (mat.rollWidthInches > 0) " • ${mat.rollWidthInches.toInt()}\" roll" else ""
                                Text(
                                    "${mat.category}$rollInfo • ${mat.supplierName.ifBlank { "General Vendor" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryGrey
                                )
                                Surface(
                                    color = if (mat.trackStock) MaterialTheme.colorScheme.primaryContainer else SurfaceSubtle,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (mat.trackStock) "Tracked Stock: ${mat.currentStock} ${mat.unit}" else "Rate Card Only (Stock un-tracked)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (mat.trackStock) MaterialTheme.colorScheme.onPrimaryContainer else TextMutedGrey,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${Formatters.formatCurrency(mat.unitCost, currency)}/${mat.unit}",
                                    style = SoleBookTextStyles.FinancialInline,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingMaterial = mat
                                    showMaterialDialog = true
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit material", tint = TextSecondaryGrey)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMaterialDialog) {
        RawMaterialEditorDialog(
            initial = editingMaterial,
            currency = currency,
            onDismiss = { showMaterialDialog = false },
            onSave = { mat ->
                viewModel.wholesaleController.saveRawMaterial(mat)
                showMaterialDialog = false
            },
            onDelete = editingMaterial?.let { existing ->
                {
                    viewModel.wholesaleController.deleteRawMaterial(existing.id)
                    showMaterialDialog = false
                }
            }
        )
    }

    if (showTemplateDialog) {
        val existingRules = editingTemplate?.let { rulesByTemplate[it.id] }.orEmpty()
        BomTemplateEditorDialog(
            initialTemplate = editingTemplate,
            initialRules = existingRules,
            rawMaterials = state.rawMaterials,
            currency = currency,
            onDismiss = { showTemplateDialog = false },
            onSave = { tpl, rules ->
                viewModel.wholesaleController.saveBomTemplate(tpl, rules)
                showTemplateDialog = false
            },
            onDelete = editingTemplate?.let { existing ->
                {
                    viewModel.wholesaleController.deleteBomTemplate(existing.id)
                    showTemplateDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RawMaterialEditorDialog(
    initial: RawMaterialEntity?,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (RawMaterialEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "FILLING") }
    var unit by remember { mutableStateOf(initial?.unit ?: "kg") }
    var costText by remember { mutableStateOf(initial?.unitCost?.takeIf { it > 0 }?.toString() ?: "") }
    var rollWidthText by remember { mutableStateOf(initial?.rollWidthInches?.takeIf { it > 0 }?.toString() ?: "") }
    var trackStock by remember { mutableStateOf(initial?.trackStock ?: false) }
    var currentStockText by remember { mutableStateOf(initial?.currentStock?.toString() ?: "0") }
    var supplierName by remember { mutableStateOf(initial?.supplierName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = { Text(if (initial == null) "Add Raw Material" else "Edit Raw Material", style = MaterialTheme.typography.headlineSmall, color = TextCharcoal) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FormField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Material Name *",
                    placeholder = "e.g. Carded Desi Cotton",
                    testTag = "raw_material_name_input"
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("FILLING", "FABRIC", "TRIM", "PACKING", "OTHER").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                if (cat == "FABRIC") unit = "meter"
                            },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = PureWhite
                            ),
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = "Unit",
                        placeholder = "kg / meter / pcs",
                        modifier = Modifier.weight(1f)
                    )
                    FormField(
                        value = costText,
                        onValueChange = { s -> costText = s.filter { it.isDigit() || it == '.' } },
                        label = "Rate (${currency.trim()})",
                        placeholder = "140",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        testTag = "raw_material_cost_input"
                    )
                }
                if (category == "FABRIC") {
                    FormField(
                        value = rollWidthText,
                        onValueChange = { s -> rollWidthText = s.filter { it.isDigit() || it == '.' } },
                        label = "Roll Width (Inches)",
                        placeholder = "60",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                FormField(
                    value = supplierName,
                    onValueChange = { supplierName = it },
                    label = "Preferred Supplier (Optional)",
                    placeholder = "e.g. Ganga Cotton Traders"
                )
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Track Raw Stock Qty", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                        Text("Optional — keeps un-tracked items frictionless", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                    }
                    Switch(checked = trackStock, onCheckedChange = { trackStock = it })
                }
                if (trackStock) {
                    FormField(
                        value = currentStockText,
                        onValueChange = { s -> currentStockText = s.filter { it.isDigit() || it == '.' } },
                        label = "Current Stock ($unit)",
                        placeholder = "0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Delete Material", color = SemanticError)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        onSave(
                            RawMaterialEntity(
                                id = initial?.id ?: 0L,
                                name = name.trim(),
                                category = category,
                                unit = unit.trim().ifBlank { "kg" },
                                unitCost = costText.toDoubleOrNull() ?: 0.0,
                                rollWidthInches = rollWidthText.toDoubleOrNull() ?: 0.0,
                                trackStock = trackStock,
                                currentStock = currentStockText.toDoubleOrNull() ?: 0.0,
                                supplierName = supplierName.trim()
                            )
                        )
                    }
                },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = PureWhite),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_raw_material_btn")
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", color = TextSecondaryGrey)
            }
        }
    )
}

@Composable
private fun BomTemplateEditorDialog(
    initialTemplate: BomTemplateEntity?,
    initialRules: List<BomMaterialRuleEntity>,
    rawMaterials: List<RawMaterialEntity>,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (BomTemplateEntity, List<BomMaterialRuleEntity>) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialTemplate?.name ?: "") }
    var refL by remember { mutableStateOf((initialTemplate?.refLengthIn ?: 72.0).toString()) }
    var refW by remember { mutableStateOf((initialTemplate?.refWidthIn ?: 36.0).toString()) }
    var refT by remember { mutableStateOf((initialTemplate?.refThicknessIn ?: 4.0).toString()) }
    var stitchCost by remember { mutableStateOf((initialTemplate?.defaultStitchCost ?: 120.0).toString()) }
    var marginPct by remember { mutableStateOf((initialTemplate?.defaultMarginPct ?: 20.0).toString()) }

    val rules = remember { mutableStateListOf<BomMaterialRuleEntity>().apply { addAll(initialRules) } }
    var newRuleMatIdx by remember { mutableIntStateOf(0) }
    var newRuleType by remember { mutableStateOf("VOLUME_WEIGHT") }
    var newRuleQty by remember { mutableStateOf("10.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = { Text(if (initialTemplate == null) "New BOM Formula" else "Edit BOM Formula", style = MaterialTheme.typography.headlineSmall, color = TextCharcoal) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FormField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Product Formula Name *",
                    placeholder = "e.g. Custom Cotton Mattress",
                    testTag = "bom_template_name_input"
                )
                Text("Reference Size (Baseline for Scaling)", style = MaterialTheme.typography.labelMedium, color = TextSecondaryGrey)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormField(value = refL, onValueChange = { refL = it.filter { c -> c.isDigit() || c == '.' } }, label = "L (in)", placeholder = "72", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    FormField(value = refW, onValueChange = { refW = it.filter { c -> c.isDigit() || c == '.' } }, label = "W (in)", placeholder = "36", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    FormField(value = refT, onValueChange = { refT = it.filter { c -> c.isDigit() || c == '.' } }, label = "T (in)", placeholder = "4", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(value = stitchCost, onValueChange = { stitchCost = it.filter { c -> c.isDigit() || c == '.' } }, label = "Stitch (${currency.trim()}/pc)", placeholder = "120", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    FormField(value = marginPct, onValueChange = { marginPct = it.filter { c -> c.isDigit() || c == '.' } }, label = "Margin %", placeholder = "20", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }

                HorizontalDivider(color = DividerGrey)
                Text("Material Rules (${rules.size})", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                rules.forEachIndexed { idx, r ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${r.materialNameSnapshot} • ${r.ruleType} (${r.refQtyPerPiece} ${r.unit})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { rules.removeAt(idx) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove rule", tint = SemanticError)
                        }
                    }
                }

                if (rawMaterials.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(rawMaterials.indices.toList()) { i ->
                            FilterChip(
                                selected = newRuleMatIdx == i,
                                onClick = {
                                    newRuleMatIdx = i
                                    val cat = rawMaterials[i].category
                                    newRuleType = when (cat) {
                                        "FABRIC" -> "SURFACE_FABRIC_METERS"
                                        "TRIM" -> "PERIMETER_TRIM_METERS"
                                        "PACKING" -> "FIXED_PER_PIECE"
                                        else -> "VOLUME_WEIGHT"
                                    }
                                    newRuleQty = if (cat == "FABRIC" || cat == "PACKING") "1.0" else "10.0"
                                },
                                label = { Text(rawMaterials[i].name, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = PureWhite
                                ),
                                modifier = Modifier.heightIn(min = 44.dp)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FormField(
                            value = newRuleQty,
                            onValueChange = { newRuleQty = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "Ref Qty/pc",
                            placeholder = "10",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val mat = rawMaterials.getOrNull(newRuleMatIdx) ?: return@OutlinedButton
                                rules.add(
                                    BomMaterialRuleEntity(
                                        templateId = initialTemplate?.id ?: 0L,
                                        rawMaterialId = mat.id,
                                        materialNameSnapshot = mat.name,
                                        category = mat.category,
                                        unit = mat.unit,
                                        ruleType = newRuleType,
                                        refQtyPerPiece = newRuleQty.toDoubleOrNull() ?: 1.0,
                                        rollWidthInches = mat.rollWidthInches.takeIf { it > 0 } ?: 60.0
                                    )
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("+ Add Rule", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Delete BOM Formula", color = SemanticError)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        onSave(
                            BomTemplateEntity(
                                id = initialTemplate?.id ?: 0L,
                                name = name.trim(),
                                refLengthIn = refL.toDoubleOrNull() ?: 72.0,
                                refWidthIn = refW.toDoubleOrNull() ?: 36.0,
                                refThicknessIn = refT.toDoubleOrNull() ?: 4.0,
                                defaultStitchCost = stitchCost.toDoubleOrNull() ?: 120.0,
                                defaultMarginPct = marginPct.toDoubleOrNull() ?: 20.0
                            ),
                            rules.toList()
                        )
                    }
                },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = PureWhite),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_bom_template_btn")
            ) {
                Text("Save Formula", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", color = TextSecondaryGrey)
            }
        }
    )
}
