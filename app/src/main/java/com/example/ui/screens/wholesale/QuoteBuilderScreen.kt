package com.example.ui.screens.wholesale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.example.data.local.WholesaleQuoteEntity
import com.example.data.local.WholesaleQuoteLineEntity
import com.example.domain.BomQuotationEngine
import com.example.domain.ComputedQuoteLine
import com.example.domain.CustomBomLineInput
import com.example.domain.QuoteLineOverride
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.ExportUtils
import com.example.util.Formatters

@Composable
fun QuoteBuilderScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    editingQuoteId: Long?,
    preselectedTemplateId: Long?,
    onQuoteLoadedOrCreated: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotices: () -> Unit
) {
    val context = LocalContext.current
    val currency = state.settings.currencySymbol
    val shopName = state.settings.shopName.ifBlank { "My Shop" }
    val bottomDockPadding = LocalGlassBottomPadding.current

    val existingQuote = remember(editingQuoteId, state.wholesaleQuotes) {
        editingQuoteId?.let { id -> state.wholesaleQuotes.firstOrNull { it.id == id } }
    }

    var selectedTemplateId by remember(editingQuoteId, preselectedTemplateId, state.bomTemplates) {
        mutableStateOf(
            existingQuote?.templateId
                ?: preselectedTemplateId
                ?: state.bomTemplates.firstOrNull()?.id
        )
    }
    val selectedTemplate = remember(selectedTemplateId, state.bomTemplates) {
        state.bomTemplates.firstOrNull { it.id == selectedTemplateId }
    }
    val templateRules = remember(selectedTemplateId, state.bomMaterialRules) {
        if (selectedTemplateId == null) emptyList()
        else state.bomMaterialRules.filter { it.templateId == selectedTemplateId }
    }
    val rawMaterialsById = remember(state.rawMaterials) {
        state.rawMaterials.associateBy { it.id }
    }

    var title by remember(existingQuote?.id) {
        mutableStateOf(existingQuote?.title ?: selectedTemplate?.name ?: "Custom Wholesale Order")
    }
    var buyerName by remember(existingQuote?.id) { mutableStateOf(existingQuote?.buyerName ?: "") }
    var buyerPhone by remember(existingQuote?.id) { mutableStateOf(existingQuote?.buyerPhone ?: "") }
    var buyerCompany by remember(existingQuote?.id) { mutableStateOf(existingQuote?.buyerCompany ?: "") }
    var status by remember(existingQuote?.id) { mutableStateOf(existingQuote?.status ?: "DRAFT") }

    var lengthText by remember(existingQuote?.id) { mutableStateOf((existingQuote?.lengthIn ?: 72.0).toInt().toString()) }
    var widthText by remember(existingQuote?.id) { mutableStateOf((existingQuote?.widthIn ?: 60.0).toInt().toString()) }
    var thicknessText by remember(existingQuote?.id) { mutableStateOf((existingQuote?.thicknessIn ?: 6.0).toInt().toString()) }
    var densityText by remember(existingQuote?.id) { mutableStateOf((existingQuote?.densityFactor ?: 1.0).toString()) }
    var quantityText by remember(existingQuote?.id) { mutableStateOf((existingQuote?.quantity ?: 200).toString()) }

    var stitchText by remember(existingQuote?.id, selectedTemplate?.id) {
        mutableStateOf((existingQuote?.stitchChargePerPc ?: selectedTemplate?.defaultStitchCost ?: 120.0).toInt().toString())
    }
    var transportText by remember(existingQuote?.id) { mutableStateOf((existingQuote?.transportCost ?: 0.0).toInt().toString()) }
    var marginText by remember(existingQuote?.id, selectedTemplate?.id) {
        mutableStateOf((existingQuote?.marginPercent ?: selectedTemplate?.defaultMarginPct ?: 22.0).toInt().toString())
    }
    var discountText by remember(existingQuote?.id) { mutableStateOf((existingQuote?.discountPercent ?: 0.0).toString()) }
    var gstText by remember(existingQuote?.id) {
        mutableStateOf(
            (existingQuote?.gstPercent ?: if (state.settings.gstEnabled) state.settings.gstRate else 0.0).toString()
        )
    }
    var validUntil by remember(existingQuote?.id) { mutableStateOf(existingQuote?.validUntil ?: "7 days") }
    var notes by remember(existingQuote?.id) { mutableStateOf(existingQuote?.notes ?: "") }

    val overridesByIndex = remember(editingQuoteId, selectedTemplateId) { mutableStateMapOf<Int, QuoteLineOverride>() }
    val customExtraLines = remember(editingQuoteId) { mutableStateListOf<CustomBomLineInput>() }

    var lineIndexToEdit by remember { mutableStateOf<Int?>(null) }
    var showAddCustomLineDialog by remember { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }

    val calc = remember(
        selectedTemplate,
        templateRules,
        rawMaterialsById,
        lengthText,
        widthText,
        thicknessText,
        densityText,
        quantityText,
        stitchText,
        transportText,
        marginText,
        discountText,
        gstText,
        overridesByIndex.toMap(),
        customExtraLines.toList()
    ) {
        BomQuotationEngine.calculateQuote(
            template = selectedTemplate,
            rules = templateRules,
            rawMaterialsById = rawMaterialsById,
            lengthIn = lengthText.toDoubleOrNull() ?: 72.0,
            widthIn = widthText.toDoubleOrNull() ?: 36.0,
            thicknessIn = thicknessText.toDoubleOrNull() ?: 4.0,
            densityFactor = densityText.toDoubleOrNull() ?: 1.0,
            quantity = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            stitchChargePerPc = stitchText.toDoubleOrNull() ?: 0.0,
            transportCost = transportText.toDoubleOrNull() ?: 0.0,
            marginPercent = marginText.toDoubleOrNull() ?: 20.0,
            discountPercent = discountText.toDoubleOrNull() ?: 0.0,
            gstPercent = gstText.toDoubleOrNull() ?: 0.0,
            overridesByIndex = overridesByIndex.toMap(),
            extraCustomLines = customExtraLines.toList()
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWarmWhite)
            .testTag("wholesale_quote_builder_screen"),
        contentPadding = PaddingValues(bottom = 24.dp + bottomDockPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SoleBookTopBar(
                title = existingQuote?.quoteNumber ?: "New Spec Quote",
                subtitle = "Parametric BOM & batch cost calculator",
                onSettingsClick = onOpenSettings,
                unreadNoticeCount = state.unreadNoticeCount,
                onNoticesClick = onOpenNotices
            )
        }

        // Template Picker
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1. Base BOM Formula", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                        if (state.bomTemplates.isEmpty()) {
                            TextButton(
                                onClick = { viewModel.wholesaleController.loadMattressStarterBom() },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Load Starter", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (state.bomTemplates.isEmpty()) {
                        Text(
                            "No saved BOM formulas yet. Tap 'Load Starter' above for instant Cotton & Foam Mattress math, or add custom lines below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryGrey
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.bomTemplates) { tpl ->
                                val selected = tpl.id == selectedTemplateId
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedTemplateId = tpl.id
                                        if (existingQuote == null) title = tpl.name
                                        stitchText = tpl.defaultStitchCost.toInt().toString()
                                        marginText = tpl.defaultMarginPct.toInt().toString()
                                        overridesByIndex.clear()
                                    },
                                    label = { Text(tpl.name) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = PureWhite
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dimensions & Quantity Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("2. Customer Specs & Batch Quantity", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)

                    // Quick Size Presets
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = listOf(
                            "Single 72×36×4" to Triple("72", "36", "4"),
                            "Double 72×48×5" to Triple("72", "48", "5"),
                            "Queen 72×60×6" to Triple("72", "60", "6"),
                            "King 78×72×6" to Triple("78", "72", "6")
                        )
                        items(presets) { (label, dims) ->
                            OutlinedButton(
                                onClick = {
                                    lengthText = dims.first
                                    widthText = dims.second
                                    thicknessText = dims.third
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = TextCharcoal)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(
                            value = lengthText,
                            onValueChange = { lengthText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "Length (in)",
                            placeholder = "72",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            testTag = "quote_length_input"
                        )
                        FormField(
                            value = widthText,
                            onValueChange = { widthText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "Width (in)",
                            placeholder = "60",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            testTag = "quote_width_input"
                        )
                        FormField(
                            value = thicknessText,
                            onValueChange = { thicknessText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "Thick (in)",
                            placeholder = "6",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            testTag = "quote_thickness_input"
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(
                            value = quantityText,
                            onValueChange = { quantityText = it.filter { c -> c.isDigit() } },
                            label = "Order Qty (pcs) *",
                            placeholder = "200",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            testTag = "quote_qty_input"
                        )
                        FormField(
                            value = densityText,
                            onValueChange = { densityText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "Density Multiplier",
                            placeholder = "1.0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(
                            value = stitchText,
                            onValueChange = { stitchText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "Stitch/Make (${currency.trim()}/pc)",
                            placeholder = "120",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            testTag = "quote_stitch_input"
                        )
                        FormField(
                            value = transportText,
                            onValueChange = { transportText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "Freight (${currency.trim()} batch)",
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(
                            value = marginText,
                            onValueChange = { marginText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "Margin %",
                            placeholder = "22",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            testTag = "quote_margin_input"
                        )
                        FormField(
                            value = discountText,
                            onValueChange = { discountText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "Bulk Disc %",
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        FormField(
                            value = gstText,
                            onValueChange = { gstText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "GST %",
                            placeholder = "12",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Calculated BOM Breakdown
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("3. Calculated Raw Materials", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                        TextButton(
                            onClick = { showAddCustomLineDialog = true },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("+ Custom Line", color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (calc.lines.isEmpty()) {
                        Text(
                            "Select a BOM template or add custom material lines to compute exact material requirements.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryGrey
                        )
                    } else {
                        calc.lines.forEachIndexed { idx, line ->
                            Surface(
                                onClick = { if (idx < templateRules.size) lineIndexToEdit = idx },
                                color = if (line.isOverridden) MaterialTheme.colorScheme.primaryContainer else SurfaceSubtle,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(line.itemName, style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                                        Text(
                                            "${line.qtyPerPiece} ${line.unit}/pc × ${quantityText.ifBlank { "1" }} = ${line.totalQty} ${line.unit} @ ${Formatters.formatCurrency(line.unitRate, currency)} (+${line.wastagePct}% waste)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondaryGrey
                                        )
                                    }
                                    Text(
                                        Formatters.formatCurrency(line.lineTotal, currency),
                                        style = SoleBookTextStyles.FinancialInline,
                                        color = TextCharcoal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Precision Summary Output Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal)
                    .testTag("quote_precision_summary_card"),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Precise Estimate — ${calc.specSummary}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    SummaryRow("Materials + Stitching / pc", "${Formatters.formatCurrency(calc.materialCostPerPiece, currency)} + ${Formatters.formatCurrency(calc.stitchChargePerPiece, currency)}")
                    SummaryRow("Unit Manufacturing Cost", Formatters.formatCurrency(calc.unitManufacturingCost, currency))
                    SummaryRow("Batch Mfg Cost (incl. freight)", Formatters.formatCurrency(calc.totalManufacturingCost, currency))
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    SummaryRow("Quoted Rate / Piece", Formatters.formatCurrency(calc.unitQuotedPriceAfterDiscount, currency), bold = true)
                    if (calc.bulkDiscountAmount > 0) {
                        SummaryRow("Bulk Discount", "− ${Formatters.formatCurrency(calc.bulkDiscountAmount, currency)}")
                    }
                    if (calc.gstAmount > 0) {
                        SummaryRow("GST (${gstText}%)", Formatters.formatCurrency(calc.gstAmount, currency))
                    }
                    SummaryRow("Grand Total Quote", Formatters.formatCurrency(calc.grandTotal, currency), bold = true)
                    SummaryRow(
                        "Estimated Net Profit",
                        "${Formatters.formatCurrency(calc.estimatedNetProfit, currency)} (${calc.effectiveMarginPct}%)",
                        bold = true
                    )
                }
            }
        }

        // Buyer Info & Save Actions
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("4. Buyer Details & Actions", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    FormField(value = title, onValueChange = { title = it }, label = "Order Title", placeholder = "e.g. Custom Cotton Mattress Batch")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(value = buyerName, onValueChange = { buyerName = it }, label = "Buyer Name", placeholder = "Ramesh Traders", modifier = Modifier.weight(1f), testTag = "quote_buyer_name_input")
                        FormField(value = buyerPhone, onValueChange = { buyerPhone = it }, label = "Phone", placeholder = "9876543210", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(value = buyerCompany, onValueChange = { buyerCompany = it }, label = "Company / Hostel", placeholder = "Optional", modifier = Modifier.weight(1f))
                        FormField(value = validUntil, onValueChange = { validUntil = it }, label = "Valid Until", placeholder = "7 days", modifier = Modifier.weight(1f))
                    }
                    FormField(value = notes, onValueChange = { notes = it }, label = "Order Notes", placeholder = "Delivery schedule, cloth color, etc.")

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("DRAFT", "SENT", "APPROVED").forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
                                label = { Text(st, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = PureWhite
                                ),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                        }
                    }

                    PrimaryButton(
                        text = if (existingQuote == null) "Save Quotation" else "Update Quotation",
                        icon = Icons.Rounded.Save,
                        onClick = {
                            viewModel.wholesaleController.saveQuote(
                                existingId = existingQuote?.id ?: 0L,
                                existingQuoteNumber = existingQuote?.quoteNumber ?: "",
                                buyerName = buyerName,
                                buyerPhone = buyerPhone,
                                buyerCompany = buyerCompany,
                                status = status,
                                template = selectedTemplate,
                                rules = templateRules,
                                rawMaterialsById = rawMaterialsById,
                                title = title,
                                lengthIn = lengthText.toDoubleOrNull() ?: 72.0,
                                widthIn = widthText.toDoubleOrNull() ?: 36.0,
                                thicknessIn = thicknessText.toDoubleOrNull() ?: 4.0,
                                densityFactor = densityText.toDoubleOrNull() ?: 1.0,
                                quantity = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                stitchChargePerPc = stitchText.toDoubleOrNull() ?: 0.0,
                                transportCost = transportText.toDoubleOrNull() ?: 0.0,
                                marginPercent = marginText.toDoubleOrNull() ?: 20.0,
                                discountPercent = discountText.toDoubleOrNull() ?: 0.0,
                                gstPercent = gstText.toDoubleOrNull() ?: 0.0,
                                validUntil = validUntil,
                                notes = notes,
                                overridesByIndex = overridesByIndex.toMap(),
                                extraCustomLines = customExtraLines.toList(),
                                onSaved = onQuoteLoadedOrCreated
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "save_wholesale_quote_btn"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val previewQuote = (existingQuote ?: WholesaleQuoteEntity(
                            quoteNumber = "DRAFT",
                            buyerName = buyerName,
                            buyerPhone = buyerPhone,
                            buyerCompany = buyerCompany,
                            status = status,
                            title = title,
                            specSummary = calc.specSummary,
                            quantity = quantityText.toIntOrNull() ?: 1,
                            unitManufacturingCost = calc.unitManufacturingCost,
                            unitQuotedPrice = calc.unitQuotedPriceAfterDiscount,
                            totalCost = calc.totalManufacturingCost,
                            totalQuoteAmount = calc.totalQuoteBeforeGst,
                            gstPercent = gstText.toDoubleOrNull() ?: 0.0,
                            gstAmount = calc.gstAmount,
                            grandTotal = calc.grandTotal,
                            validUntil = validUntil,
                            notes = notes
                        ))
                        val previewLines = calc.lines.map {
                            WholesaleQuoteLineEntity(
                                quoteId = previewQuote.id,
                                rawMaterialId = it.rawMaterialId,
                                itemName = it.itemName,
                                category = it.category,
                                unit = it.unit,
                                qtyPerPiece = it.qtyPerPiece,
                                totalQty = it.totalQty,
                                unitRate = it.unitRate,
                                wastagePct = it.wastagePct,
                                lineTotal = it.lineTotal
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val msg = BomQuotationEngine.formatQuoteWhatsAppMessage(shopName, previewQuote, previewLines, currency)
                                if (buyerPhone.isNotBlank()) ExportUtils.openWhatsApp(context, buyerPhone, msg)
                                else ExportUtils.shareText(context, "Wholesale Quote", msg)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("WhatsApp", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        OutlinedButton(
                            onClick = {
                                val pdf = ExportUtils.generateWholesaleQuotePdf(context, shopName, currency, previewQuote, previewLines)
                                ExportUtils.shareFile(context, pdf, "application/pdf", "Share Wholesale Quote PDF")
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextCharcoal)
                            Spacer(Modifier.width(4.dp))
                            Text("PDF", color = TextCharcoal)
                        }
                    }

                    if (existingQuote != null && existingQuote.linkedSaleId == null) {
                        SecondaryButton(
                            text = "Convert Approved Quote to Connected Bill",
                            icon = Icons.Rounded.ReceiptLong,
                            onClick = { showConvertDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "builder_convert_to_sale_btn"
                        )
                    }
                }
            }
        }
    }

    lineIndexToEdit?.let { idx ->
        val line = calc.lines.getOrNull(idx)
        if (line != null) {
            QuoteLineOverrideDialog(
                line = line,
                currency = currency,
                onDismiss = { lineIndexToEdit = null },
                onSave = { newQty, newRate, newWastage ->
                    overridesByIndex[idx] = QuoteLineOverride(
                        qtyPerPiece = newQty,
                        unitRate = newRate,
                        wastagePct = newWastage
                    )
                    lineIndexToEdit = null
                },
                onReset = {
                    overridesByIndex.remove(idx)
                    lineIndexToEdit = null
                }
            )
        }
    }

    if (showAddCustomLineDialog) {
        AddCustomBomLineDialog(
            currency = currency,
            onDismiss = { showAddCustomLineDialog = false },
            onAdd = { custom ->
                customExtraLines.add(custom)
                showAddCustomLineDialog = false
            }
        )
    }

    if (showConvertDialog && existingQuote != null) {
        ConvertQuoteToSaleDialog(
            quote = existingQuote,
            currency = currency,
            onDismiss = { showConvertDialog = false },
            onConfirm = { mode ->
                viewModel.wholesaleController.convertQuoteToWholesaleSale(existingQuote.id, mode)
                showConvertDialog = false
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) MaterialTheme.colorScheme.onPrimaryContainer else TextCharcoal
        )
        Text(
            text = value,
            style = SoleBookTextStyles.FinancialInline,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = if (bold) MaterialTheme.colorScheme.onPrimaryContainer else TextCharcoal
        )
    }
}

@Composable
private fun QuoteLineOverrideDialog(
    line: ComputedQuoteLine,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (qtyPerPc: Double, rate: Double, wastage: Double) -> Unit,
    onReset: () -> Unit
) {
    var qtyText by remember { mutableStateOf(line.qtyPerPiece.toString()) }
    var rateText by remember { mutableStateOf(line.unitRate.toString()) }
    var wastageText by remember { mutableStateOf(line.wastagePct.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = { Text("Override: ${line.itemName}", style = MaterialTheme.typography.titleLarge, color = TextCharcoal) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(value = qtyText, onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } }, label = "Qty per piece (${line.unit})", placeholder = "10", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                FormField(value = rateText, onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' } }, label = "Unit Rate (${currency.trim()}/${line.unit})", placeholder = "140", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                FormField(value = wastageText, onValueChange = { wastageText = it.filter { c -> c.isDigit() || c == '.' } }, label = "Wastage %", placeholder = "5", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (line.isOverridden) {
                    TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Reset to Formula Default", color = SemanticWarning)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        qtyText.toDoubleOrNull() ?: line.qtyPerPiece,
                        rateText.toDoubleOrNull() ?: line.unitRate,
                        wastageText.toDoubleOrNull() ?: line.wastagePct
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = PureWhite),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Apply Override") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", color = TextSecondaryGrey) }
        }
    )
}

@Composable
private fun AddCustomBomLineDialog(
    currency: String,
    onDismiss: () -> Unit,
    onAdd: (CustomBomLineInput) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("pcs") }
    var qtyText by remember { mutableStateOf("1") }
    var rateText by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = { Text("Add Custom BOM Line", style = MaterialTheme.typography.titleLarge, color = TextCharcoal) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(value = name, onValueChange = { name = it }, label = "Material / Job Name *", placeholder = "e.g. Heavy Chain Zipper")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(value = qtyText, onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } }, label = "Qty / pc", placeholder = "1", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    FormField(value = unit, onValueChange = { unit = it }, label = "Unit", placeholder = "pcs", modifier = Modifier.weight(1f))
                }
                FormField(value = rateText, onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' } }, label = "Rate (${currency.trim()})", placeholder = "45", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        onAdd(
                            CustomBomLineInput(
                                itemName = name.trim(),
                                category = "OTHER",
                                unit = unit.ifBlank { "pcs" },
                                qtyPerPiece = qtyText.toDoubleOrNull() ?: 1.0,
                                unitRate = rateText.toDoubleOrNull() ?: 0.0
                            )
                        )
                    }
                },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = PureWhite),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Add Line") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", color = TextSecondaryGrey) }
        }
    )
}
