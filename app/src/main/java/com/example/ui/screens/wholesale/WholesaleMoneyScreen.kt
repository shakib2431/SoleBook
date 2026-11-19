package com.example.ui.screens.wholesale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.screens.AddExpenseDialog
import com.example.ui.screens.CustomerCreditRow
import com.example.ui.theme.*
import com.example.ui.viewmodel.MoneyPeriod
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.Formatters

@Composable
fun WholesaleMoneyScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onOpenSettings: () -> Unit,
    onOpenNotices: () -> Unit
) {
    val currency = state.settings.currencySymbol
    val summary = state.moneySummary
    val cb = state.channelBreakdown
    val bottomDockPadding = LocalGlassBottomPadding.current

    var showExpenseDialog by remember { mutableStateOf(false) }

    val udhaarCustomers = remember(state.customers) {
        state.customers.filter { it.creditBalance > 0.5 }.sortedByDescending { it.creditBalance }
    }
    val wholesaleOrders = remember(state.sales) {
        state.sales.filter { it.saleChannel == "WHOLESALE" }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWarmWhite)
            .testTag("wholesale_ledger_screen"),
        contentPadding = PaddingValues(bottom = 24.dp + bottomDockPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SoleBookTopBar(
                title = "Wholesale Ledger",
                subtitle = "Unified B2B + Retail accounts",
                onSettingsClick = onOpenSettings,
                unreadNoticeCount = state.unreadNoticeCount,
                onNoticesClick = onOpenNotices,
                extraAction = {
                    SecondaryButton(
                        text = "+ Expense",
                        onClick = { showExpenseDialog = true },
                        icon = Icons.Rounded.Add
                    )
                }
            )
        }

        // Period Filter
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(MoneyPeriod.TODAY, MoneyPeriod.WEEK, MoneyPeriod.MONTH).forEach { period ->
                    val selected = state.moneyPeriod == period
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setMoneyPeriod(period) },
                        label = { Text(period.label, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = PureWhite
                        )
                    )
                }
            }
        }

        // Unified Channel P&L Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Combined Shop Profit (${summary?.periodLabel ?: "This Month"})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondaryGrey
                    )
                    Text(
                        text = Formatters.formatCurrency(summary?.netProfit ?: 0.0, currency),
                        style = SoleBookTextStyles.FinancialHero,
                        color = if ((summary?.netProfit ?: 0.0) >= 0) SemanticSuccess else SemanticError
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Wholesale (${cb.wholesaleBillCount} orders)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(Formatters.formatCurrency(cb.wholesaleRevenue, currency), style = SoleBookTextStyles.FinancialInline, color = TextCharcoal)
                                Text("GP ${Formatters.formatCurrency(cb.wholesaleGrossProfit, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Surface(color = SurfaceSubtle, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Retail (${cb.retailBillCount} bills)", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                                Text(Formatters.formatCurrency(cb.retailRevenue, currency), style = SoleBookTextStyles.FinancialInline, color = TextCharcoal)
                                Text("GP ${Formatters.formatCurrency(cb.retailGrossProfit, currency)}", style = MaterialTheme.typography.labelSmall, color = SemanticSuccess)
                            }
                        }
                    }
                    Text(
                        text = "Shared Shop Expenses: ${Formatters.formatCurrency(summary?.expenses ?: 0.0, currency)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SemanticError
                    )
                }
            }
        }

        // B2B Converted Orders
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Billed Wholesale Orders (${wholesaleOrders.size})", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    if (wholesaleOrders.isEmpty()) {
                        Text("Converted wholesale quotes appear here and flow directly into unified revenue.", style = MaterialTheme.typography.bodySmall, color = TextSecondaryGrey)
                    } else {
                        wholesaleOrders.take(10).forEach { s ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${s.billNumber} • ${s.customerNameSnapshot.ifBlank { "B2B Buyer" }}", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                                    Text("${s.paymentMode} • ${Formatters.formatShortDate(s.timestamp)}", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                                }
                                Text(Formatters.formatCurrency(s.totalRevenue, currency), style = SoleBookTextStyles.FinancialInline, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }

        // Material PO Requests
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vendor Material POs (${state.purchaseOrders.size})", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    if (state.purchaseOrders.isEmpty()) {
                        Text("Create a Material PO directly from any quotation to order cotton, cloth, and trims from your vendor.", style = MaterialTheme.typography.bodySmall, color = TextSecondaryGrey)
                    } else {
                        state.purchaseOrders.take(10).forEach { po ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${po.poNumber} • ${po.supplierName}", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                                    Text("${po.status} • Ref ${po.sourceDocumentRef.ifBlank { "Manual" }}", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                                }
                                Text(Formatters.formatCurrency(po.estimatedTotal, currency), style = SoleBookTextStyles.FinancialInline, color = TextCharcoal)
                            }
                        }
                    }
                }
            }
        }

        // Buyer Receivables
        if (udhaarCustomers.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                    shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pending Buyer Credit (${udhaarCustomers.size})", style = MaterialTheme.typography.titleMedium, color = SemanticError)
                        udhaarCustomers.forEach { c ->
                            CustomerCreditRow(
                                customer = c,
                                currency = currency,
                                onSettle = { viewModel.settleCustomerCredit(c.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExpenseDialog) {
        AddExpenseDialog(
            currency = currency,
            onDismiss = { showExpenseDialog = false },
            onSave = { cat, amt, recurring, notes ->
                viewModel.addExpense(cat, amt, recurring, notes)
                showExpenseDialog = false
            }
        )
    }
}
