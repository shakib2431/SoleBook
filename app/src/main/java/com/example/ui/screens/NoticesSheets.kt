package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.PriorityNotice
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SoleBookSpacing
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.ExportUtils
import com.example.util.Formatters
import kotlin.math.roundToInt

@Composable
internal fun AdjustPriceNoticeDialog(
    notice: PriorityNotice,
    currency: String,
    unitSingular: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val initialPrice = (notice.suggestedSellingPrice ?: notice.currentSellingPrice ?: 0.0).roundToInt()
    var priceText by remember(notice.noticeKey) { mutableStateOf(initialPrice.toString()) }
    val parsed = priceText.toDoubleOrNull() ?: 0.0
    val cost = notice.costPrice ?: 0.0
    val newProfit = parsed - cost
    val newMargin = if (parsed > 0.0) (newProfit / parsed) * 100.0 else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = {
            Text(
                text = "Update Price • ${notice.skuCode}",
                style = MaterialTheme.typography.headlineMedium,
                color = TextCharcoal
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Cost: ${Formatters.formatCurrency(cost, currency)} • Current sell: ${Formatters.formatCurrency(notice.currentSellingPrice ?: 0.0, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryGrey
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { s -> priceText = s.filter { it.isDigit() || it == '.' } },
                    label = { Text("New Selling Price ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = DividerGrey
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SoleBookSpacing.inputHeight)
                        .testTag("notice_new_price_input")
                )
                Surface(
                    color = if (newProfit > 0) SemanticSuccessSoft else SemanticErrorSoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "New profit: ${Formatters.formatCurrency(newProfit, currency)}/$unitSingular (${newMargin.roundToInt()}% margin)",
                        color = if (newProfit > 0) SemanticSuccess else SemanticError,
                        style = SoleBookTextStyles.FinancialInline,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (parsed > 0.0) onConfirm(parsed) },
                enabled = parsed > 0.0,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_adjust_price_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = PureWhite
                )
            ) {
                Text("Update Price")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", color = TextSecondaryGrey)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlertRulesBottomSheet(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onDismiss: () -> Unit
) {
    val s = state.settings
    var alertsEnabled by remember(s.alertsEnabled) { mutableStateOf(s.alertsEnabled) }
    var stockAlerts by remember(s.stockAlertsEnabled) { mutableStateOf(s.stockAlertsEnabled) }
    var demandAlerts by remember(s.demandAlertsEnabled) { mutableStateOf(s.demandAlertsEnabled) }
    var marginAlerts by remember(s.marginAlertsEnabled) { mutableStateOf(s.marginAlertsEnabled) }
    var instantSales by remember(s.instantSaleAlertsEnabled) { mutableStateOf(s.instantSaleAlertsEnabled) }
    var intervalHours by remember(s.alertIntervalHours) { mutableStateOf(s.alertIntervalHours) }
    var maxPerDay by remember(s.maxAlertsPerDay) { mutableStateOf(s.maxAlertsPerDay) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(topStart = SoleBookSpacing.dialogRadius, topEnd = SoleBookSpacing.dialogRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SoleBookSpacing.screenHorizontal)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Smart Alert Rules", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Text(
                "SoleBook respects night quiet hours (9 PM – 8 AM) and caps daily notifications so alerts stay focused.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryGrey
            )

            RuleSwitchRow("Enable Smart Phone Alerts", alertsEnabled) { alertsEnabled = it }
            RuleSwitchRow("Out of Stock & Low Stock", stockAlerts) { stockAlerts = it }
            RuleSwitchRow("High Demand, Slow & Dead Stock", demandAlerts) { demandAlerts = it }
            RuleSwitchRow("Cost vs Selling Price & Margin Tips", marginAlerts) { marginAlerts = it }
            RuleSwitchRow("Instant Notification After Every Sale", instantSales) { instantSales = it }

            Text("Check Frequency", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 4, 6, 12).forEach { hrs ->
                    FilterChip(
                        selected = intervalHours == hrs,
                        onClick = { intervalHours = hrs },
                        label = { Text("${hrs}h") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                }
            }

            Text("Max Notifications Per Day", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 4, 6).forEach { cap ->
                    FilterChip(
                        selected = maxPerDay == cap,
                        onClick = { maxPerDay = cap },
                        label = { Text("$cap/day") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                }
            }

            PrimaryButton(
                text = "Save Alert Rules",
                onClick = {
                    viewModel.updateSettings(
                        s.copy(
                            alertsEnabled = alertsEnabled,
                            stockAlertsEnabled = stockAlerts,
                            demandAlertsEnabled = demandAlerts,
                            marginAlertsEnabled = marginAlerts,
                            instantSaleAlertsEnabled = instantSales,
                            alertIntervalHours = intervalHours,
                            maxAlertsPerDay = maxPerDay
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "save_alert_rules_button"
            )
        }
    }
}

@Composable
private fun RuleSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextCharcoal,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LotDecisionBottomSheet(
    notice: PriorityNotice,
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onDismiss: () -> Unit,
    onOpenProductDetail: (Long) -> Unit,
    onOpenVendorDetail: (Long) -> Unit,
    onLogFreshLot: (String, String, Long?) -> Unit
) {
    val context = LocalContext.current
    val industry = state.industryConfig
    val linkedProduct = notice.productId?.let { pid -> state.products.firstOrNull { it.id == pid } }
    val defaultCat = industry.suggestedCategories.firstOrNull() ?: "General"
    val defaultSegment = industry.audienceSegmentOptions.firstOrNull() ?: "Standard"
    val category = notice.bucketCategory.ifBlank { linkedProduct?.category ?: defaultCat }
    val segment = notice.bucketGender.ifBlank { linkedProduct?.gender ?: defaultSegment }

    val matchingBucket = state.insightsReport?.buyingBuckets?.firstOrNull {
        it.category.equals(category, ignoreCase = true) && it.gender.equals(segment, ignoreCase = true)
    } ?: state.insightsReport?.buyingBuckets?.firstOrNull {
        it.category.equals(category, ignoreCase = true)
    }

    val matchingVendors = remember(state.vendors, linkedProduct, category) {
        state.vendors.filter { v ->
            v.id == linkedProduct?.vendorId ||
                v.specialtiesCsv.split(",").any { it.trim().equals(category, ignoreCase = true) }
        }.ifEmpty { state.vendors }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(topStart = SoleBookSpacing.dialogRadius, topEnd = SoleBookSpacing.dialogRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SoleBookSpacing.screenHorizontal)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Management Next Step", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Text(notice.title, style = MaterialTheme.typography.titleMedium, color = EmeraldDark)
            Text(notice.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)

            Surface(
                color = SurfaceSubtle,
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                border = BorderStroke(1.dp, DividerGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Recommended Fresh Batch Profile", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    val profileText = if (industry.showAudienceSegmentField && segment.isNotBlank()) {
                        "$segment • $category • ${notice.bucketPriceBand.ifBlank { matchingBucket?.priceBandLabel ?: "Same price range" }}"
                    } else {
                        "$category • ${notice.bucketPriceBand.ifBlank { matchingBucket?.priceBandLabel ?: "Same price range" }}"
                    }
                    Text(
                        text = profileText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryGrey
                    )
                    if (notice.recommendedSizesHint.isNotBlank()) {
                        Text(
                            text = "${industry.variantLabel} to buy: ${notice.recommendedSizesHint}",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldDark
                        )
                    }
                }
            }

            if (matchingVendors.isNotEmpty()) {
                Text("Ask Matching Suppliers for Fresh Stock", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                matchingVendors.take(3).forEach { vendor ->
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DividerGrey),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(vendor.name, style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                                if (vendor.cityMarket.isNotBlank()) {
                                    Text(vendor.cityMarket, style = MaterialTheme.typography.labelSmall, color = TextMutedGrey)
                                }
                            }
                            TextButton(
                                onClick = { onOpenVendorDetail(vendor.id) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("History", color = TextSecondaryGrey)
                            }
                            FilledTonalButton(
                                onClick = {
                                    val brief = viewModel.buildWhatsAppBuyingBrief(
                                        vendor = vendor,
                                        buckets = listOfNotNull(matchingBucket),
                                        repeatItems = emptyList()
                                    )
                                    val targetPhone = vendor.whatsapp.ifBlank { vendor.phone }
                                    if (targetPhone.isNotBlank()) {
                                        ExportUtils.openWhatsApp(context, targetPhone, brief)
                                    } else {
                                        ExportUtils.shareText(context, "Buying Brief", brief)
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = SemanticSuccessSoft,
                                    contentColor = SemanticSuccess
                                )
                            ) {
                                Icon(Icons.Rounded.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Brief")
                            }
                        }
                    }
                }
            }

            PrimaryButton(
                text = "Log New Fresh Stock",
                onClick = { onLogFreshLot(category, segment, matchingVendors.firstOrNull()?.id) },
                icon = Icons.Rounded.AddBox,
                modifier = Modifier.fillMaxWidth(),
                testTag = "lot_decision_log_fresh_btn"
            )

            if (linkedProduct != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!linkedProduct.isArchived) {
                        SecondaryButton(
                            text = "Retire Finished Batch",
                            onClick = {
                                viewModel.retireSoldOutLot(linkedProduct.id, notice.noticeKey)
                                onDismiss()
                            },
                            accentColor = TextCharcoal,
                            modifier = Modifier.weight(1f),
                            testTag = "lot_decision_retire_btn"
                        )
                    }
                    SecondaryButton(
                        text = "Inspect Item",
                        onClick = { onOpenProductDetail(linkedProduct.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
