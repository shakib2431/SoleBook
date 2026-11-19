package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.DayBar
import com.example.domain.NoticeCategory
import com.example.domain.PriorityNotice
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.Formatters
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeDashboardScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel? = null,
    onNewSale: () -> Unit,
    onAddProduct: () -> Unit,
    onAddPurchase: () -> Unit,
    onOpenStockLow: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenMoney: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProductDetail: (Long) -> Unit,
    onLoadDemoData: () -> Unit,
    onSaveExpense: (category: String, amount: Double, recurring: Boolean, notes: String) -> Unit = { c, a, r, n ->
        viewModel?.addExpense(c, a, r, n)
    }
) {
    val currency = state.settings.currencySymbol
    val dashboard = state.dashboard
    val bottomPadding = LocalGlassBottomPadding.current
    var showQuickExpenseSheet by remember { mutableStateOf(false) }

    val shopTitle = state.settings.shortHeaderName.ifBlank {
        state.settings.shopName.ifBlank { "SoleBook" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWarmWhite)
    ) {
        SoleBookTopBar(
            title = shopTitle,
            categoryBadge = state.industryConfig.displayCategoryName,
            onHelpClick = null,
            onSettingsClick = onOpenSettings,
            unreadNoticeCount = state.unreadNoticeCount,
            onNoticesClick = onOpenNotices,
            logoUri = state.settings.shopLogoUri,
            showLogoSlot = true,
            headerBrandMode = state.settings.headerBrandMode,
            fullShopNameForMonogram = state.settings.shopName.ifBlank { "SoleBook" },
            onLogoClick = onOpenSettings
        )

        if (!dashboard.hasAnyBusinessData) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = SoleBookSpacing.screenHorizontal,
                    end = SoleBookSpacing.screenHorizontal,
                    top = 20.dp,
                    bottom = bottomPadding + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    EmptyDashboardCard(
                        businessName = state.settings.shopName.ifBlank { "Your business" },
                        onAddProduct = onAddProduct,
                        onRecordSale = onNewSale,
                        onAddExpense = { showQuickExpenseSheet = true },
                        onLoadDemoData = onLoadDemoData
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_dashboard_list"),
                contentPadding = PaddingValues(
                    start = SoleBookSpacing.screenHorizontal,
                    end = SoleBookSpacing.screenHorizontal,
                    top = 16.dp,
                    bottom = bottomPadding + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(SoleBookSpacing.sectionGap)
            ) {
                // 1. Primary Metric: Today's Sales
                item {
                    TodaySalesHeroCard(
                        todaySales = dashboard.todaySalesRevenue,
                        todayBills = dashboard.todayBillCount,
                        yesterdayHasSales = dashboard.yesterdayHasSales,
                        todayVsYesterdayPct = dashboard.todayVsYesterdayPct,
                        currencySymbol = currency,
                        onClick = onOpenMoney
                    )
                }

                // 2. Quick Actions
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(title = "Quick actions")
                        QuickActionsGrid(
                            onNewSale = onNewSale,
                            onAddProduct = onAddProduct,
                            onAddPurchase = onAddPurchase,
                            onRecordExpense = { showQuickExpenseSheet = true }
                        )
                    }
                }

                // 3. Four Compact Secondary Metrics
                item {
                    val periodLabel = state.moneySummary?.periodLabel ?: "This Month"
                    val stockItemsCount = state.products.count { !it.isArchived }
                    val lowAndOut = dashboard.lowStockCount + dashboard.outOfStockCount

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = "Business snapshot",
                            subtitle = periodLabel
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FinancialMetric(
                                label = "Net Profit ($periodLabel)",
                                value = Formatters.formatCurrency(dashboard.periodNetProfit, currency),
                                subtitle = "Gross: ${Formatters.formatCurrency(dashboard.periodGrossProfit, currency)}",
                                valueColor = if (dashboard.periodNetProfit >= 0) SemanticSuccess else SemanticError,
                                onClick = onOpenMoney,
                                modifier = Modifier.weight(1f)
                            )
                            FinancialMetric(
                                label = "Inventory Value",
                                value = Formatters.formatCurrency(dashboard.inventoryCostValue, currency),
                                subtitle = "$stockItemsCount active items",
                                onClick = onOpenStockLow,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FinancialMetric(
                                label = "Customer Outstanding",
                                value = Formatters.formatCurrency(dashboard.customerOutstanding, currency),
                                subtitle = if (dashboard.supplierOutstanding > 0) {
                                    "Payables: ${Formatters.formatCurrency(dashboard.supplierOutstanding, currency)}"
                                } else {
                                    "Receivables"
                                },
                                valueColor = if (dashboard.customerOutstanding > 0) SemanticWarning else TextCharcoal,
                                onClick = onOpenCustomers,
                                modifier = Modifier.weight(1f)
                            )
                            FinancialMetric(
                                label = "Low-Stock Products",
                                value = lowAndOut.toString(),
                                subtitle = if (dashboard.outOfStockCount > 0) {
                                    "${dashboard.outOfStockCount} out of stock"
                                } else {
                                    "Need attention"
                                },
                                valueColor = if (lowAndOut > 0) SemanticWarning else SemanticSuccess,
                                onClick = onOpenStockLow,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 4. Compact Business Performance Trend Chart (Real Data Only)
                val dailyBars = state.moneySummary?.dailyBars.orEmpty()
                if (dailyBars.any { it.revenue > 0.0 }) {
                    item {
                        CompactPerformanceCard(
                            bars = dailyBars.takeLast(7),
                            currencySymbol = currency,
                            onOpenInsights = onOpenInsights
                        )
                    }
                }

                // 5. Business Alerts
                val topAlerts = state.priorityNotices.take(4)
                if (topAlerts.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Business alerts",
                            actionLabel = "View all",
                            onActionClick = onOpenNotices
                        )
                    }
                    items(topAlerts, key = { it.noticeKey }) { alert ->
                        DashboardAlertRow(
                            notice = alert,
                            onClick = {
                                val pid = alert.productId
                                if (pid != null) onOpenProductDetail(pid) else onOpenNotices()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showQuickExpenseSheet) {
        QuickExpenseDialog(
            currencySymbol = currency,
            onDismiss = { showQuickExpenseSheet = false },
            onSave = { category, amount, recurring, notes ->
                onSaveExpense(category, amount, recurring, notes)
                showQuickExpenseSheet = false
            }
        )
    }
}

@Composable
private fun EmptyDashboardCard(
    businessName: String,
    onAddProduct: () -> Unit,
    onRecordSale: () -> Unit,
    onAddExpense: () -> Unit,
    onLoadDemoData: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_dashboard_card"),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmeraldSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Your business journey starts here.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextCharcoal
                )
                Text(
                    text = "$businessName is ready. Add products, record your first sale, or log a shop expense — your sales, profit, and alerts will update automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryGrey
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "Add your first product",
                    onClick = onAddProduct,
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "empty_add_first_product"
                )
                SecondaryButton(
                    text = "Record your first sale",
                    onClick = onRecordSale,
                    icon = Icons.Rounded.PointOfSale,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "empty_record_first_sale"
                )
                SecondaryButton(
                    text = "Add an expense",
                    onClick = onAddExpense,
                    icon = Icons.Rounded.ReceiptLong,
                    accentColor = TextCharcoal,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "empty_add_expense"
                )
            }
            HorizontalDivider(color = DividerGrey)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onLoadDemoData)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
                    .testTag("empty_load_demo"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Want to preview with sample store data?",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondaryGrey
                )
                Text(
                    text = "Load demo",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldPrimary
                )
            }
        }
    }
}

@Composable
private fun TodaySalesHeroCard(
    todaySales: Double,
    todayBills: Int,
    yesterdayHasSales: Boolean,
    todayVsYesterdayPct: Double?,
    currencySymbol: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_today_sales_card"),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Sales",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondaryGrey
                )
                if (yesterdayHasSales && todayVsYesterdayPct != null) {
                    val isUp = todayVsYesterdayPct >= 0.0
                    Surface(
                        color = if (isUp) SemanticSuccessSoft else SemanticErrorSoft,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${if (isUp) "+" else "-"}${abs(todayVsYesterdayPct).roundToInt()}% vs yesterday",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUp) SemanticSuccess else SemanticError,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Text(
                text = Formatters.formatCurrency(todaySales, currencySymbol),
                style = SoleBookTextStyles.FinancialHero,
                color = TextCharcoal
            )
            Text(
                text = if (todayBills == 0) {
                    "No bills recorded today yet"
                } else {
                    "$todayBills ${if (todayBills == 1) "bill" else "bills"} recorded today"
                },
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondaryGrey
            )
        }
    }
}

@Composable
private fun QuickActionsGrid(
    onNewSale: () -> Unit,
    onAddProduct: () -> Unit,
    onAddPurchase: () -> Unit,
    onRecordExpense: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionButton(
                icon = Icons.Rounded.PointOfSale,
                label = "New Sale",
                primary = true,
                onClick = onNewSale,
                modifier = Modifier.weight(1f).testTag("quick_new_sale")
            )
            QuickActionButton(
                icon = Icons.Rounded.AddBox,
                label = "Add Product",
                primary = false,
                onClick = onAddProduct,
                modifier = Modifier.weight(1f).testTag("quick_add_product")
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionButton(
                icon = Icons.Rounded.LocalShipping,
                label = "Add Purchase",
                primary = false,
                onClick = onAddPurchase,
                modifier = Modifier.weight(1f).testTag("quick_add_purchase")
            )
            QuickActionButton(
                icon = Icons.Rounded.ReceiptLong,
                label = "Record Expense",
                primary = false,
                onClick = onRecordExpense,
                modifier = Modifier.weight(1f).testTag("quick_record_expense")
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (primary) EmeraldPrimary else SurfaceWhite,
        border = if (primary) null else BorderStroke(1.dp, DividerGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (primary) PureWhite else EmeraldPrimary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (primary) PureWhite else TextCharcoal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactPerformanceCard(
    bars: List<DayBar>,
    currencySymbol: String,
    onOpenInsights: () -> Unit
) {
    val maxRev = (bars.maxOfOrNull { it.revenue } ?: 1.0).coerceAtLeast(1.0)
    val totalPeriod = bars.sumOf { it.revenue }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Recent sales trend",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextCharcoal
                    )
                    Text(
                        text = "Last ${bars.size} days: ${Formatters.formatCurrency(totalPeriod, currencySymbol)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryGrey
                    )
                }
                TextButton(
                    onClick = onOpenInsights,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Insights", color = EmeraldPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                bars.forEach { bar ->
                    val frac = (bar.revenue / maxRev).toFloat().coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height((64f * frac).coerceAtLeast(if (bar.revenue > 0) 6f else 2f).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (bar.revenue > 0) EmeraldPrimary else DividerGrey
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = bar.label.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMutedGrey,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardAlertRow(
    notice: PriorityNotice,
    onClick: () -> Unit
) {
    val (bg, fg) = when (notice.category) {
        NoticeCategory.OUT_OF_STOCK, NoticeCategory.DEAD_STOCK -> SemanticErrorSoft to SemanticError
        NoticeCategory.LOW_STOCK, NoticeCategory.SLOW_DEMAND -> SemanticWarningSoft to SemanticWarning
        NoticeCategory.PRICE_MARGIN, NoticeCategory.HIGH_DEMAND, NoticeCategory.SOLD_ALERT -> EmeraldSoft to EmeraldPrimary
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = bg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = notice.category.shortTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextCharcoal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = notice.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryGrey,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMutedGrey
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickExpenseDialog(
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (category: String, amount: Double, recurring: Boolean, notes: String) -> Unit
) {
    val categories = listOf("Rent", "Salary", "Electricity", "Transport", "Packaging", "Other")
    var selectedCategory by remember { mutableStateOf("Rent") }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var recurring by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = {
            Text("Record Expense", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                FormField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it.filter { ch -> ch.isDigit() || ch == '.' }
                        errorMsg = null
                    },
                    label = "Amount (${currencySymbol.trim()}) *",
                    placeholder = "e.g. 500",
                    errorText = errorMsg
                )
                FormField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes (optional)",
                    placeholder = "e.g. Shop packaging bags"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = recurring, onCheckedChange = { recurring = it })
                    Text(
                        text = "Monthly recurring expense",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextCharcoal
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0) {
                        errorMsg = "Enter a valid amount greater than 0."
                    } else {
                        onSave(selectedCategory, amt, recurring, notes.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Save Expense")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", color = TextSecondaryGrey)
            }
        }
    )
}
