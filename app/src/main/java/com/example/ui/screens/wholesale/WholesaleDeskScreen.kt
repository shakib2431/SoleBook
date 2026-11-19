package com.example.ui.screens.wholesale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.local.WholesaleQuoteEntity
import com.example.domain.BomQuotationEngine
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.ExportUtils
import com.example.util.Formatters

@Composable
fun WholesaleDeskScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onOpenNewQuote: (Long?) -> Unit,
    onOpenBomScreen: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotices: () -> Unit
) {
    val context = LocalContext.current
    val currency = state.settings.currencySymbol
    val shopName = state.settings.shopName.ifBlank { "My Shop" }
    val bottomDockPadding = LocalGlassBottomPadding.current

    var statusFilter by remember { mutableStateOf("ALL") }
    var quoteForSaleConversion by remember { mutableStateOf<WholesaleQuoteEntity?>(null) }
    var quoteForPoCreation by remember { mutableStateOf<WholesaleQuoteEntity?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val filteredQuotes = remember(state.wholesaleQuotes, statusFilter) {
        if (statusFilter == "ALL") state.wholesaleQuotes
        else state.wholesaleQuotes.filter { it.status.equals(statusFilter, ignoreCase = true) }
    }
    val linesByQuoteId = remember(state.wholesaleQuoteLines) {
        state.wholesaleQuoteLines.groupBy { it.quoteId }
    }
    val pipelineTotal = remember(state.wholesaleQuotes) {
        state.wholesaleQuotes
            .filter { it.status in listOf("DRAFT", "SENT", "APPROVED") }
            .sumOf { it.grandTotal }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWarmWhite)
            .testTag("wholesale_desk_screen"),
        contentPadding = PaddingValues(bottom = 24.dp + bottomDockPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SoleBookTopBar(
                title = state.settings.shortHeaderName.ifBlank { shopName },
                subtitle = "Wholesale & Custom BOM Desk",
                logoUri = state.settings.shopLogoUri,
                showLogoSlot = true,
                headerBrandMode = state.settings.headerBrandMode,
                fullShopNameForMonogram = shopName,
                categoryBadge = "Wholesale",
                onHelpClick = { showHelpDialog = true },
                onSettingsClick = onOpenSettings,
                unreadNoticeCount = state.unreadNoticeCount,
                onNoticesClick = onOpenNotices
            )
        }

        // KPI Row
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeadlineMetricCard(
                        title = "Wholesale Sales (${state.channelBreakdown.wholesaleBillCount})",
                        value = Formatters.formatCurrency(state.channelBreakdown.wholesaleRevenue, currency),
                        subtitle = "GP ${Formatters.formatCurrency(state.channelBreakdown.wholesaleGrossProfit, currency)}",
                        valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    HeadlineMetricCard(
                        title = "Open Quotes Pipeline",
                        value = Formatters.formatCurrency(pipelineTotal, currency),
                        subtitle = "${state.wholesaleQuotes.count { it.status != "CONVERTED" }} open quote(s)",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinancialMetric(
                        label = "Customer Receivable",
                        value = Formatters.formatCurrency(state.dashboard.customerOutstanding, currency),
                        subtitle = "Shared retail + B2B dues",
                        valueColor = if (state.dashboard.customerOutstanding > 0.5) SemanticWarning else TextCharcoal,
                        modifier = Modifier.weight(1f)
                    )
                    FinancialMetric(
                        label = "BOM Recipes",
                        value = "${state.bomTemplates.size} formulas",
                        subtitle = "${state.rawMaterials.size} material rates",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Quick Actions
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = "New Spec Quote",
                        icon = Icons.Rounded.Calculate,
                        onClick = { onOpenNewQuote(null) },
                        modifier = Modifier.weight(1f),
                        testTag = "wholesale_new_quote_btn"
                    )
                    SecondaryButton(
                        text = "BOM & Rates",
                        icon = Icons.Rounded.Tune,
                        onClick = onOpenBomScreen,
                        modifier = Modifier.weight(1f),
                        testTag = "wholesale_open_bom_btn"
                    )
                }
                if (state.bomTemplates.isEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.wholesaleController.loadMattressStarterBom() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("load_mattress_starter_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Load Starter BOM (Cotton & Foam Mattress)",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Status Filter Chips
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = SoleBookSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val statuses = listOf("ALL", "DRAFT", "SENT", "APPROVED", "CONVERTED")
                items(statuses) { st ->
                    val selected = statusFilter == st
                    FilterChip(
                        selected = selected,
                        onClick = { statusFilter = st },
                        label = {
                            Text(
                                text = st.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = PureWhite
                        ),
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }

        if (filteredQuotes.isEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                    EmptyState(
                        icon = Icons.Rounded.RequestQuote,
                        title = "No wholesale quotations yet",
                        subtitle = "Calculate custom-size orders (like 200 custom mattresses with cotton, cloth, and stitching) and send a clean PDF or WhatsApp quote in seconds.",
                        primaryLabel = "Create First Spec Quote",
                        onPrimaryClick = { onOpenNewQuote(null) },
                        secondaryLabel = if (state.bomTemplates.isEmpty()) "Load Starter BOM" else null,
                        onSecondaryClick = if (state.bomTemplates.isEmpty()) {
                            { viewModel.wholesaleController.loadMattressStarterBom() }
                        } else null
                    )
                }
            }
        } else {
            items(filteredQuotes, key = { it.id }) { quote ->
                val quoteLines = linesByQuoteId[quote.id].orEmpty()
                WholesaleQuoteCard(
                    quote = quote,
                    currency = currency,
                    onEdit = { onOpenNewQuote(quote.id) },
                    onShareWhatsApp = {
                        val msg = BomQuotationEngine.formatQuoteWhatsAppMessage(
                            shopName = shopName,
                            quote = quote,
                            lines = quoteLines,
                            currency = currency
                        )
                        if (quote.buyerPhone.isNotBlank()) {
                            ExportUtils.openWhatsApp(context, quote.buyerPhone, msg)
                        } else {
                            ExportUtils.shareText(context, "Wholesale Quote ${quote.quoteNumber}", msg)
                        }
                    },
                    onSharePdf = {
                        val pdf = ExportUtils.generateWholesaleQuotePdf(
                            context = context,
                            shopName = shopName,
                            currency = currency,
                            quote = quote,
                            lines = quoteLines
                        )
                        ExportUtils.shareFile(context, pdf, "application/pdf", "Share Quote ${quote.quoteNumber}")
                    },
                    onCreatePo = { quoteForPoCreation = quote },
                    onConvertToBill = { quoteForSaleConversion = quote },
                    onViewLedger = onOpenLedger
                )
            }
        }
    }

    quoteForSaleConversion?.let { quote ->
        ConvertQuoteToSaleDialog(
            quote = quote,
            currency = currency,
            onDismiss = { quoteForSaleConversion = null },
            onConfirm = { paymentMode ->
                viewModel.wholesaleController.convertQuoteToWholesaleSale(quote.id, paymentMode)
                quoteForSaleConversion = null
            }
        )
    }

    quoteForPoCreation?.let { quote ->
        GeneratePoFromQuoteDialog(
            quote = quote,
            vendors = state.vendors,
            onDismiss = { quoteForPoCreation = null },
            onConfirm = { vendorId, supplierName ->
                viewModel.wholesaleController.generateVendorPoFromQuote(quote.id, vendorId, supplierName)
                quoteForPoCreation = null
            }
        )
    }

    if (showHelpDialog) {
        HelpTipDialog(
            title = "Wholesale & Custom BOM",
            tips = listOf(
                "Set up material rates (e.g. Cotton ₹/kg, Damask Cloth ₹/m with roll width) and parametric formulas in BOM & Rates.",
                "Enter any customer dimensions (L × W × Thickness) & quantity (e.g. 200 pcs) to get exact per-piece cost and batch profit.",
                "Convert approved quotes to Wholesale Bills — revenue, COGS, GST, and buyer dues connect directly into your unified ledger."
            ),
            onDismiss = { showHelpDialog = false }
        )
    }
}

@Composable
private fun WholesaleQuoteCard(
    quote: WholesaleQuoteEntity,
    currency: String,
    onEdit: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onSharePdf: () -> Unit,
    onCreatePo: () -> Unit,
    onConvertToBill: () -> Unit,
    onViewLedger: () -> Unit
) {
    val (statusBg, statusFg) = when (quote.status) {
        "CONVERTED" -> SemanticSuccessSoft to SemanticSuccess
        "APPROVED" -> WholesaleBlueSoft to WholesaleBlueDark
        "SENT" -> SemanticWarningSoft to SemanticWarning
        "REJECTED" -> SemanticErrorSoft to SemanticError
        else -> SurfaceSubtle to TextSecondaryGrey
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SoleBookSpacing.screenHorizontal)
            .testTag("quote_card_${quote.quoteNumber}"),
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = quote.quoteNumber,
                            style = SoleBookTextStyles.FinancialInline,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(color = statusBg, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = quote.status,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusFg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = quote.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextCharcoal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${quote.buyerName.ifBlank { "Walk-in B2B Buyer" }}${if (quote.buyerCompany.isNotBlank()) " • ${quote.buyerCompany}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryGrey
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatCurrency(quote.grandTotal, currency),
                        style = SoleBookTextStyles.FinancialPrimary,
                        color = TextCharcoal
                    )
                    Text(
                        text = "${Formatters.formatCurrency(quote.unitQuotedPrice, currency)} / pc",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Surface(color = SurfaceSubtle, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = quote.specSummary.ifBlank { "${quote.quantity} pcs" },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextCharcoal
                    )
                    Text(
                        text = "Cost ${Formatters.formatCurrency(quote.unitManufacturingCost, currency)}/pc",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryGrey
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextCharcoal)
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", color = TextCharcoal, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onShareWhatsApp,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share WhatsApp", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                OutlinedButton(
                    onClick = onSharePdf,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export PDF", modifier = Modifier.size(16.dp), tint = TextCharcoal)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (quote.linkedPoId == null) {
                    OutlinedButton(
                        onClick = onCreatePo,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Material PO", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    OutlinedButton(
                        onClick = onViewLedger,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DividerGrey)
                    ) {
                        Text("PO Created ✓", color = TextSecondaryGrey, style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (quote.linkedSaleId == null) {
                    Button(
                        onClick = onConvertToBill,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("convert_quote_${quote.quoteNumber}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = PureWhite)
                    ) {
                        Text("Bill Order", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        color = SemanticSuccessSoft,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Billed ✓",
                                color = SemanticSuccess,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConvertQuoteToSaleDialog(
    quote: WholesaleQuoteEntity,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (paymentMode: String) -> Unit
) {
    var paymentMode by remember { mutableStateOf("CASH") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = {
            Text("Convert Quote to Wholesale Bill", style = MaterialTheme.typography.headlineSmall, color = TextCharcoal)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${quote.quoteNumber} • ${quote.quantity} pcs • ${Formatters.formatCurrency(quote.grandTotal, currency)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Records a connected Wholesale Sale in your unified P&L and deducts stock only for raw materials where Track Stock is enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryGrey
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH", "UPI", "CARD", "CREDIT").forEach { mode ->
                        FilterChip(
                            selected = paymentMode == mode,
                            onClick = { paymentMode = mode },
                            label = { Text(if (mode == "CREDIT") "Credit" else mode, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = PureWhite
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(paymentMode) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = PureWhite),
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_convert_quote_sale")
            ) {
                Text("Confirm Bill", style = MaterialTheme.typography.labelLarge)
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
fun GeneratePoFromQuoteDialog(
    quote: WholesaleQuoteEntity,
    vendors: List<com.example.data.local.VendorEntity>,
    onDismiss: () -> Unit,
    onConfirm: (vendorId: Long?, supplierName: String) -> Unit
) {
    var selectedVendorId by remember { mutableStateOf(vendors.firstOrNull()?.id) }
    var customSupplier by remember { mutableStateOf(vendors.firstOrNull()?.name ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = {
            Text("Create Vendor Material PO", style = MaterialTheme.typography.headlineSmall, color = TextCharcoal)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Generates a raw-material Purchase Request from ${quote.quoteNumber} (${quote.quantity} pcs). Stock is not added until received.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryGrey
                )
                if (vendors.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(vendors) { v ->
                            FilterChip(
                                selected = selectedVendorId == v.id,
                                onClick = {
                                    selectedVendorId = v.id
                                    customSupplier = v.name
                                },
                                label = { Text(v.name, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = PureWhite
                                ),
                                modifier = Modifier.heightIn(min = 46.dp)
                            )
                        }
                    }
                }
                FormField(
                    value = customSupplier,
                    onValueChange = {
                        customSupplier = it
                        selectedVendorId = vendors.firstOrNull { v -> v.name.equals(it.trim(), ignoreCase = true) }?.id
                    },
                    label = "Supplier Name",
                    placeholder = "e.g. Ganga Cotton Traders"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedVendorId, customSupplier.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = PureWhite),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Create PO", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", color = TextSecondaryGrey)
            }
        }
    )
}
