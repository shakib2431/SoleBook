package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MoneyPeriod
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.Formatters
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onOpenSettings: () -> Unit,
    onOpenNotices: () -> Unit = {}
) {
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig
    val summary = state.moneySummary
    val bottomDockPadding = LocalGlassBottomPadding.current

    var showExpenseDialog by remember { mutableStateOf(false) }
    var showDailyClosingDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val udhaarCustomers = remember(state.customers) {
        state.customers.filter { it.creditBalance > 0.5 }.sortedByDescending { it.creditBalance }
    }
    val supplierOutstanding = remember(state.vendors) {
        state.vendors.sumOf { it.creditBalance.coerceAtLeast(0.0) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWarmWhite),
        contentPadding = PaddingValues(bottom = 24.dp + bottomDockPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SoleBookTopBar(
                title = "Money & Profit",
                subtitle = summary?.periodLabel ?: "This Month",
                onHelpClick = { showHelpDialog = true },
                onSettingsClick = onOpenSettings,
                unreadNoticeCount = state.unreadNoticeCount,
                onNoticesClick = onOpenNotices,
                extraAction = {
                    SecondaryButton(
                        text = "Close Day",
                        onClick = { showDailyClosingDialog = true },
                        icon = Icons.Rounded.Summarize,
                        testTag = "daily_closing_button"
                    )
                }
            )
        }

        // Period Switcher
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MoneyPeriod.entries.forEach { period ->
                    val selected = state.moneyPeriod == period
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (period == MoneyPeriod.CUSTOM) showDatePicker = true
                            else viewModel.setMoneyPeriod(period)
                        },
                        label = {
                            Text(
                                period.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("period_${period.name}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = PureWhite,
                            containerColor = SurfaceWhite,
                            labelColor = TextCharcoal
                        )
                    )
                }
            }
        }

        if (summary == null || (state.sales.isEmpty() && state.products.isEmpty() && state.expenses.isEmpty())) {
            item {
                Box(modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                    EmptyState(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        title = "No financial records yet",
                        subtitle = "Record your first sale, add products, or log an expense to view real-time Revenue, COGS, Gross Profit, and Net Profit.",
                        primaryLabel = "Record Expense",
                        onPrimaryClick = { showExpenseDialog = true },
                        secondaryLabel = "Load Sample Store Data",
                        onSecondaryClick = { viewModel.loadDemoData() }
                    )
                }
            }
            return@LazyColumn
        }

        // 1. Reconciled Net Profit Summary Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Net Profit (${summary.periodLabel})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondaryGrey
                        )
                        if (state.settings.gstEnabled) {
                            Surface(color = EmeraldSoft, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "GST ${state.settings.gstRate.roundToInt()}% ${if (state.settings.gstInclusive) "Incl." else "Excl."}",
                                    color = EmeraldDark,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = Formatters.formatCurrency(summary.netProfit, currency),
                        style = SoleBookTextStyles.FinancialHero,
                        color = if (summary.netProfit >= 0) SemanticSuccess else SemanticError,
                        modifier = Modifier.testTag("net_profit_value")
                    )

                    HorizontalDivider(color = DividerGrey)

                    // Strict accounting breakdown: Revenue - COGS = Gross Profit; Gross Profit - Expenses = Net Profit
                    LedgerLine("Sales Revenue (${summary.billCount} bills, ${summary.pairsSold} ${industry.unitPlural})", Formatters.formatCurrency(summary.revenue, currency))
                    LedgerLine("Cost of Goods Sold (COGS)", "− ${Formatters.formatCurrency(summary.cogs, currency)}")
                    LedgerLine(
                        "Gross Profit (${summary.grossMarginPct.roundToInt()}% margin)",
                        Formatters.formatCurrency(summary.grossProfit, currency),
                        valueColor = if (summary.grossProfit >= 0) SemanticSuccess else SemanticError,
                        bold = true
                    )
                    LedgerLine("Shop Expenses", "− ${Formatters.formatCurrency(summary.expenses, currency)}", valueColor = SemanticError)
                }
            }
        }

        if (state.channelBreakdown.wholesaleBillCount > 0 || state.settings.businessOperationMode != "RETAIL") {
            item {
                val cb = state.channelBreakdown
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SoleBookSpacing.screenHorizontal)
                        .testTag("connected_channel_breakdown_card"),
                    shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Connected Retail + Wholesale Split",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextCharcoal
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                color = SurfaceSubtle,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Retail (${cb.retailBillCount} bills)", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                                    Text(
                                        Formatters.formatCurrency(cb.retailRevenue, currency),
                                        style = SoleBookTextStyles.FinancialInline,
                                        color = TextCharcoal
                                    )
                                    Text(
                                        "GP: ${Formatters.formatCurrency(cb.retailGrossProfit, currency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SemanticSuccess
                                    )
                                }
                            }
                            Surface(
                                color = EmeraldSoft,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Wholesale (${cb.wholesaleBillCount} orders)", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                                    Text(
                                        Formatters.formatCurrency(cb.wholesaleRevenue, currency),
                                        style = SoleBookTextStyles.FinancialInline,
                                        color = TextCharcoal
                                    )
                                    Text(
                                        "GP: ${Formatters.formatCurrency(cb.wholesaleGrossProfit, currency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Revenue, Gross Profit, COGS & Outstanding Metrics
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeadlineMetricCard(
                        title = "Sales Revenue",
                        value = Formatters.formatCurrency(summary.revenue, currency),
                        deltaPercent = summary.revenueChangePct,
                        subtitle = "Avg bill ${Formatters.formatCurrency(summary.avgBillValue, currency)}",
                        modifier = Modifier.weight(1f)
                    )
                    HeadlineMetricCard(
                        title = "Gross Profit",
                        value = Formatters.formatCurrency(summary.grossProfit, currency),
                        deltaPercent = summary.profitChangePct,
                        subtitle = "${Formatters.formatCurrency(summary.avgProfitPerPair, currency)} / ${industry.unitSingular}",
                        valueColor = if (summary.grossProfit >= 0) SemanticSuccess else SemanticError,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val customerDueTotal = udhaarCustomers.sumOf { it.creditBalance }
                    FinancialMetric(
                        label = "Customer Outstanding",
                        value = Formatters.formatCurrency(customerDueTotal, currency),
                        subtitle = "${udhaarCustomers.size} customer(s) due",
                        valueColor = if (customerDueTotal > 0.5) SemanticWarning else TextCharcoal,
                        modifier = Modifier.weight(1f)
                    )
                    FinancialMetric(
                        label = "Supplier Outstanding",
                        value = Formatters.formatCurrency(supplierOutstanding, currency),
                        subtitle = "Payable to suppliers",
                        valueColor = if (supplierOutstanding > 0.5) SemanticError else SemanticSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (summary.returnsCount > 0) {
            item {
                Surface(
                    color = SemanticWarningSoft,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SemanticWarning.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SoleBookSpacing.screenHorizontal)
                ) {
                    Text(
                        text = "Returns in period: ${summary.returnsCount} ${industry.unitSingular}(s) (${Formatters.formatCurrency(summary.returnsAmount, currency)} deducted from Revenue & COGS reversed).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextCharcoal,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // 3. Monthly Break-Even
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Monthly Break-Even", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                        Text(
                            if (summary.breakEvenPairsPerMonth > 0) {
                                "${summary.pairsSoldThisMonth} / ${summary.breakEvenPairsPerMonth} ${industry.unitPlural}"
                            } else "Add fixed costs",
                            style = SoleBookTextStyles.FinancialInline,
                            color = EmeraldPrimary
                        )
                    }
                    if (summary.breakEvenPairsPerMonth > 0) {
                        val progress = (summary.pairsSoldThisMonth.toFloat() / summary.breakEvenPairsPerMonth.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = if (progress >= 1f) SemanticSuccess else EmeraldPrimary,
                            trackColor = SurfaceSubtle
                        )
                        val rem = (summary.breakEvenPairsPerMonth - summary.pairsSoldThisMonth).coerceAtLeast(0)
                        Text(
                            text = if (rem == 0) {
                                "Break-even reached! Extra sales this month contribute directly to net profit."
                            } else {
                                "Recurring fixed costs ${Formatters.formatCurrency(summary.monthlyFixedExpenses, currency)} require $rem more ${industry.unitPlural} this month."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryGrey
                        )
                    } else {
                        Text(
                            "Mark Rent, Salary, or Electricity as recurring monthly expenses to track break-even.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryGrey
                        )
                    }
                }
            }
        }

        // 4. Stock Valuation
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Stock Valuation", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Inventory at Cost", style = MaterialTheme.typography.labelMedium, color = TextSecondaryGrey)
                            Text(
                                Formatters.formatCurrency(summary.totalStockCostValue, currency),
                                style = SoleBookTextStyles.FinancialPrimary,
                                color = TextCharcoal
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Retail Selling Value", style = MaterialTheme.typography.labelMedium, color = TextSecondaryGrey)
                            Text(
                                Formatters.formatCurrency(summary.totalStockRetailValue, currency),
                                style = SoleBookTextStyles.FinancialPrimary,
                                color = SemanticSuccess
                            )
                        }
                    }
                    if (summary.deadSlowCapitalLocked > 0) {
                        Surface(color = SemanticErrorSoft, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Capital tied in slow/dead stock: ${Formatters.formatCurrency(summary.deadSlowCapitalLocked, currency)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = SemanticError,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Payment Mode Split (Cash vs UPI vs Card vs Credit) & Credit Settle-Up
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cash & Payment Mode Split", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    val totalCollected = summary.paymentSplit.values.sum().coerceAtLeast(1.0)
                    summary.paymentSplit.forEach { (mode, amt) ->
                        val ratio = (amt / totalCollected).toFloat().coerceIn(0f, 1f)
                        val barColor = when (mode) {
                            "CREDIT" -> SemanticWarning
                            "CASH" -> SemanticSuccess
                            "UPI" -> EmeraldPrimary
                            else -> TextSecondaryGrey
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (mode == "CREDIT") "Credit" else mode,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextCharcoal,
                                modifier = Modifier.width(68.dp)
                            )
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = barColor,
                                trackColor = SurfaceSubtle
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                Formatters.formatCurrency(amt, currency),
                                style = SoleBookTextStyles.FinancialInline,
                                color = TextCharcoal
                            )
                        }
                    }

                    if (udhaarCustomers.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = DividerGrey)
                        Spacer(Modifier.height(4.dp))
                        Text("Customer Credit Pending", style = MaterialTheme.typography.titleSmall, color = SemanticError)
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

        // 6. Daily Revenue & Profit Chart
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Daily Revenue & Profit", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(EmeraldPrimary.copy(alpha = 0.38f), RoundedCornerShape(3.dp)))
                                Spacer(Modifier.width(4.dp))
                                Text("Revenue", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(EmeraldPrimary, RoundedCornerShape(3.dp)))
                                Spacer(Modifier.width(4.dp))
                                Text("Profit", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    DailyRevenueProfitChart(
                        bars = summary.dailyBars,
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(summary.dailyBars.firstOrNull()?.label ?: "", style = MaterialTheme.typography.labelSmall, color = TextMutedGrey)
                        Text(summary.dailyBars.lastOrNull()?.label ?: "", style = MaterialTheme.typography.labelSmall, color = TextMutedGrey)
                    }
                }
            }
        }

        // 7. Profit by Category & Top Products
        if (summary.profitByCategory.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SoleBookSpacing.screenHorizontal),
                    shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Profit by Category", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                        val maxCatProfit = summary.profitByCategory.maxOf { it.profit }.coerceAtLeast(1.0)
                        summary.profitByCategory.forEach { cat ->
                            val frac = (cat.profit / maxCatProfit).toFloat().coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${cat.category} (${cat.units} ${industry.unitPlural})", style = MaterialTheme.typography.bodyMedium, color = TextCharcoal)
                                    Text(
                                        Formatters.formatCurrency(cat.profit, currency),
                                        style = SoleBookTextStyles.FinancialInline,
                                        color = if (cat.profit >= 0) SemanticSuccess else SemanticError
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { frac },
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                                    color = if (cat.profit >= 0) EmeraldPrimary else SemanticError,
                                    trackColor = SurfaceSubtle
                                )
                            }
                        }
                    }
                }
            }
        }

        if (summary.topProductsByProfit.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SoleBookSpacing.screenHorizontal),
                    shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Top Products by Profit", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                        summary.topProductsByProfit.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "#${idx + 1}",
                                    style = SoleBookTextStyles.FinancialInline,
                                    color = EmeraldPrimary,
                                    modifier = Modifier.width(34.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall, color = TextCharcoal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${item.sku} • ${item.units} ${industry.unitPlural}", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                                }
                                Text(
                                    Formatters.formatCurrency(item.profit, currency),
                                    style = SoleBookTextStyles.FinancialInline,
                                    color = if (item.profit >= 0) SemanticSuccess else SemanticError
                                )
                            }
                            if (idx < summary.topProductsByProfit.lastIndex) {
                                HorizontalDivider(color = DividerGrey)
                            }
                        }
                    }
                }
            }
        }

        // 8. Shop Expenses
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Business Expenses", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                            Text(
                                "In period: ${Formatters.formatCurrency(summary.expenses, currency)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondaryGrey
                            )
                        }
                        PrimaryButton(
                            text = "Add Expense",
                            onClick = { showExpenseDialog = true },
                            icon = Icons.Rounded.Add,
                            testTag = "add_expense_button"
                        )
                    }

                    val recentExpenses = state.expenses.take(10)
                    if (recentExpenses.isEmpty()) {
                        Text("No expenses recorded yet.", style = MaterialTheme.typography.bodyMedium, color = TextMutedGrey)
                    } else {
                        recentExpenses.forEach { exp ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        exp.category + if (exp.isRecurringMonthly) " (Monthly)" else "",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextCharcoal
                                    )
                                    Text(
                                        "${Formatters.formatShortDate(exp.timestamp)}${if (exp.notes.isNotBlank()) " • ${exp.notes}" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondaryGrey
                                    )
                                }
                                Text(
                                    Formatters.formatCurrency(exp.amount, currency),
                                    style = SoleBookTextStyles.FinancialInline,
                                    color = SemanticError
                                )
                                IconButton(
                                    onClick = { viewModel.deleteExpense(exp.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete expense", tint = TextMutedGrey)
                                }
                            }
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

    if (showDailyClosingDialog) {
        DailyClosingReportDialog(
            state = state,
            onDismiss = { showDailyClosingDialog = false }
        )
    }

    if (showDatePicker) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.customStartMs,
            initialSelectedEndDateMillis = state.customEndMs
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = rangeState.selectedStartDateMillis ?: state.customStartMs
                    val e = rangeState.selectedEndDateMillis ?: s
                    viewModel.setCustomDateRange(s, e)
                    showDatePicker = false
                }) { Text("Apply", color = EmeraldPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextSecondaryGrey) }
            }
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.height(440.dp))
        }
    }

    if (showHelpDialog) {
        HelpTipDialog(
            title = "Money & Profit Tips",
            tips = listOf(
                "Sales Revenue minus Cost of Goods (COGS) equals Gross Profit. Gross Profit minus Shop Expenses equals Net Profit.",
                "Break-even shows how many ${industry.unitPlural} you need to sell per month to cover fixed expenses like rent and salaries.",
                "Use Close Day every evening for a shareable summary of bills, cash, UPI, and low stock."
            ),
            onDismiss = { showHelpDialog = false }
        )
    }
}

@Composable
private fun LedgerLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = TextCharcoal,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (bold) TextCharcoal else TextSecondaryGrey,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            text = value,
            style = SoleBookTextStyles.FinancialInline,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor
        )
    }
}
