package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.ExportUtils
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VendorDetailScreen(
    vendorId: Long,
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onOpenProductDetail: (Long) -> Unit,
    onLogFreshLotForVendor: (Long, String?) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig
    val vendor = state.vendors.firstOrNull { it.id == vendorId }

    var showEditSheet by remember { mutableStateOf(false) }
    var showPayDialog by remember { mutableStateOf(false) }
    var showBriefSheet by remember { mutableStateOf(false) }

    if (vendor == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(CanvasWarmWhite).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Supplier not found", style = MaterialTheme.typography.headlineSmall, color = TextCharcoal)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "Go Back", onClick = onBack)
        }
        return
    }

    val vendorPurchases = remember(state.purchases, vendorId) {
        state.purchases.filter { it.vendorId == vendorId }.sortedByDescending { it.timestamp }
    }
    val vendorPayments = remember(state.vendorPayments, vendorId) {
        state.vendorPayments.filter { it.vendorId == vendorId }.sortedByDescending { it.timestamp }
    }
    val productsById = remember(state.products) { state.products.associateBy { it.id } }
    val specialties = remember(vendor.specialtiesCsv) {
        vendor.specialtiesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    val matchingBuckets = remember(state.insightsReport, specialties) {
        state.insightsReport?.buyingBuckets.orEmpty().filter { b ->
            specialties.any { it.equals(b.category, ignoreCase = true) }
        }
    }

    val totalBoughtValue = vendorPurchases.sumOf { it.totalCost }
    val totalUnitsBought = vendorPurchases.sumOf { it.totalUnits }

    Scaffold(
        containerColor = CanvasWarmWhite,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(vendor.name, style = MaterialTheme.typography.titleLarge, color = TextCharcoal) },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextCharcoal)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showEditSheet = true },
                            modifier = Modifier.size(48.dp).testTag("edit_vendor_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Supplier", tint = EmeraldPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
                )
                HorizontalDivider(thickness = 1.dp, color = DividerGrey)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = SoleBookSpacing.screenHorizontal, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "WhatsApp Brief",
                        onClick = { showBriefSheet = true },
                        icon = Icons.Rounded.Chat,
                        modifier = Modifier.weight(1f),
                        testTag = "vendor_whatsapp_brief_btn"
                    )
                    if (vendor.phone.isNotBlank()) {
                        SecondaryButton(
                            text = "Call",
                            onClick = { ExportUtils.dialPhone(context, vendor.phone) },
                            icon = Icons.Rounded.Call,
                            accentColor = TextCharcoal,
                            modifier = Modifier.weight(0.7f)
                        )
                    }
                }
            }

            item {
                SecondaryButton(
                    text = "Log Purchase from Supplier",
                    onClick = { onLogFreshLotForVendor(vendor.id, specialties.firstOrNull()) },
                    icon = Icons.Rounded.AddBox,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "new_lot_from_vendor_btn"
                )
            }

            item {
                val hasDue = vendor.creditBalance > 0.5
                Surface(
                    shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                    color = if (hasDue) SemanticErrorSoft else SemanticSuccessSoft,
                    border = BorderStroke(1.dp, if (hasDue) SemanticError.copy(alpha = 0.35f) else SemanticSuccess.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasDue) "Payable Due to Supplier" else "Supplier Account Settled",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasDue) SemanticError else SemanticSuccess
                            )
                            Text(
                                text = Formatters.formatCurrency(vendor.creditBalance, currency),
                                style = SoleBookTextStyles.FinancialPrimary,
                                color = if (hasDue) SemanticError else SemanticSuccess
                            )
                        }
                        if (hasDue) {
                            PrimaryButton(
                                text = "Pay Due",
                                onClick = { showPayDialog = true },
                                testTag = "pay_vendor_due_button"
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinancialMetric(
                        label = "Total Purchased",
                        value = Formatters.formatCurrency(totalBoughtValue, currency),
                        subtitle = "$totalUnitsBought ${industry.unitPlural} across ${vendorPurchases.size} bills",
                        modifier = Modifier.weight(1f)
                    )
                    FinancialMetric(
                        label = "Payments Logged",
                        value = Formatters.formatCurrency(vendorPayments.sumOf { it.amount }, currency),
                        subtitle = "${vendorPayments.size} settlement(s)",
                        valueColor = SemanticSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vendor.cityMarket.isNotBlank()) {
                            Text("Market: ${vendor.cityMarket}", style = MaterialTheme.typography.titleSmall)
                        }
                        if (vendor.phone.isNotBlank()) {
                            Text("Phone: ${vendor.phone}", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)
                        }
                        if (specialties.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                specialties.forEach { sp ->
                                    Surface(color = SurfaceSubtle, shape = RoundedCornerShape(6.dp)) {
                                        Text(sp, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }
                        if (vendor.notes.isNotBlank()) {
                            Text("Notes: ${vendor.notes}", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)
                        }
                    }
                }
            }

            item {
                Text("Purchase History (${vendorPurchases.size})", style = MaterialTheme.typography.titleLarge, color = TextCharcoal)
            }
            if (vendorPurchases.isEmpty()) {
                item {
                    Text(
                        "No purchases recorded from this supplier yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryGrey
                    )
                }
            } else {
                items(vendorPurchases, key = { "vpurch_${it.id}" }) { pur ->
                    val prod = productsById[pur.productId]
                    Surface(
                        onClick = { if (prod != null) onOpenProductDetail(prod.id) },
                        enabled = prod != null,
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        border = BorderStroke(1.dp, DividerGrey),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        prod?.let { "${it.skuCode} • ${it.name}" }
                                            ?: "${pur.totalUnits} ${industry.unitPlural} @ ${Formatters.formatCurrency(pur.unitCostPrice, currency)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextCharcoal
                                    )
                                    Text(
                                        "${Formatters.formatShortDate(pur.timestamp)} • ${pur.totalUnits} ${industry.unitPlural} @ ${Formatters.formatCurrency(pur.unitCostPrice, currency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondaryGrey
                                    )
                                }
                                Text(
                                    Formatters.formatCurrency(pur.totalCost, currency),
                                    style = SoleBookTextStyles.FinancialCompact,
                                    color = TextCharcoal
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val (fg, bg) = when (pur.paymentStatus) {
                                    "PAID" -> SemanticSuccess to SemanticSuccessSoft
                                    "PARTIAL" -> SemanticWarning to SemanticWarningSoft
                                    else -> SemanticError to SemanticErrorSoft
                                }
                                Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = when (pur.paymentStatus) {
                                            "PAID" -> "Paid"
                                            "PARTIAL" -> "Due ${Formatters.formatCurrency(pur.dueAmount, currency)}"
                                            else -> "On Credit (${Formatters.formatCurrency(pur.dueAmount, currency)})"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fg,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (pur.sizeBreakdown.isNotBlank()) {
                                Text(pur.sizeBreakdown, style = MaterialTheme.typography.labelSmall, color = TextMutedGrey)
                            }
                        }
                    }
                }
            }

            item {
                Text("Payments Made (${vendorPayments.size})", style = MaterialTheme.typography.titleLarge, color = TextCharcoal)
            }
            if (vendorPayments.isEmpty()) {
                item {
                    Text("No supplier payments logged yet.", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)
                }
            } else {
                items(vendorPayments, key = { "vpay_${it.id}" }) { pay ->
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        border = BorderStroke(1.dp, DividerGrey)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${pay.paymentMode} Payment", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                                Text(
                                    "${Formatters.formatShortDate(pay.timestamp)}${if (pay.notes.isNotBlank()) " • ${pay.notes}" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryGrey
                                )
                            }
                            Text(
                                Formatters.formatCurrency(pay.amount, currency),
                                style = SoleBookTextStyles.FinancialCompact,
                                color = SemanticSuccess
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet) {
        AddEditVendorSheet(
            initialVendor = vendor,
            industryConfig = industry,
            onDismiss = { showEditSheet = false },
            onSave = { updated -> viewModel.saveVendor(updated); showEditSheet = false }
        )
    }

    if (showPayDialog) {
        PayVendorDueDialog(
            vendorName = vendor.name,
            currentDue = vendor.creditBalance,
            currency = currency,
            onDismiss = { showPayDialog = false },
            onConfirm = { amt, mode, notes -> viewModel.recordVendorPayment(vendor.id, amt, mode, notes); showPayDialog = false }
        )
    }

    if (showBriefSheet) {
        WhatsAppBuyingBriefSheet(
            state = state,
            viewModel = viewModel,
            initialVendor = vendor,
            preselectedBuckets = matchingBuckets.ifEmpty { state.insightsReport?.buyingBuckets.orEmpty() },
            onDismiss = { showBriefSheet = false }
        )
    }
}

@Composable
private fun PayVendorDueDialog(
    vendorName: String,
    currentDue: Double,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String) -> Unit
) {
    var amountText by remember { mutableStateOf(currentDue.toInt().toString()) }
    var paymentMode by remember { mutableStateOf("UPI") }
    var notes by remember { mutableStateOf("") }
    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = { Text("Pay Supplier Due", style = MaterialTheme.typography.headlineSmall, color = TextCharcoal) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "$vendorName • Pending Due: ${Formatters.formatCurrency(currentDue, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryGrey
                )
                FormField(
                    value = amountText,
                    onValueChange = { s -> amountText = s.filter { it.isDigit() || it == '.' } },
                    label = "Amount Paid ($currency)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "vendor_payment_amount_input"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CASH", "UPI", "BANK").forEach { mode ->
                        FilterChip(
                            selected = paymentMode == mode,
                            onClick = { paymentMode = mode },
                            label = { Text(mode) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        )
                    }
                }
                FormField(value = notes, onValueChange = { notes = it }, label = "Reference / Notes (optional)")
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Record Payment",
                onClick = { if (parsedAmount > 0.0) onConfirm(parsedAmount, paymentMode, notes) },
                enabled = parsedAmount > 0.0,
                testTag = "confirm_vendor_payment_button"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", color = TextSecondaryGrey)
            }
        }
    )
}
