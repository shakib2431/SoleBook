package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.FinancialMetric
import com.example.ui.components.SectionHeader
import com.example.ui.components.SoleBookSpacing
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.ExportUtils
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currency = state.settings.currencySymbol
    val shopName = state.settings.shopName.ifBlank { "SoleBook Store" }
    val summary = state.moneySummary

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.restoreJsonFromUri(it) }
    }

    Scaffold(
        containerColor = CanvasWarmWhite,
        topBar = {
            TopAppBar(
                title = {
                    Text("Reports & Exports", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite,
                    titleContentColor = TextCharcoal
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = SoleBookSpacing.screenHorizontal,
                vertical = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (summary != null) {
                item {
                    SectionHeader(
                        title = "Summary (${summary.periodLabel})",
                        subtitle = "Live figures from recorded bills, returns, and expenses"
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FinancialMetric(
                            label = "Sales Revenue",
                            value = Formatters.formatCurrency(summary.revenue, currency),
                            subtitle = "${summary.billCount} bills",
                            modifier = Modifier.weight(1f)
                        )
                        FinancialMetric(
                            label = "Net Profit",
                            value = Formatters.formatCurrency(summary.netProfit, currency),
                            subtitle = "After expenses",
                            valueColor = if (summary.netProfit >= 0) SemanticSuccess else SemanticError,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionHeader(title = "Share & Export")
            }

            item {
                ReportActionCard(
                    icon = Icons.Rounded.Summarize,
                    title = "Daily Closing Summary",
                    subtitle = "Share today's sales, expenses, and net profit via WhatsApp or text",
                    onClick = {
                        val dash = state.dashboard
                        val reportText = buildString {
                            appendLine("*$shopName — Daily Closing Report*")
                            appendLine("Today's Sales: ${Formatters.formatCurrency(dash.todaySalesRevenue, currency)} (${dash.todayBillCount} bills)")
                            appendLine("Inventory Value: ${Formatters.formatCurrency(dash.inventoryCostValue, currency)}")
                            appendLine("Customer Due: ${Formatters.formatCurrency(dash.customerOutstanding, currency)}")
                            appendLine("Supplier Due: ${Formatters.formatCurrency(dash.supplierOutstanding, currency)}")
                        }
                        ExportUtils.shareText(context, "$shopName Daily Closing", reportText)
                    },
                    testTag = "report_daily_closing"
                )
            }

            item {
                ReportActionCard(
                    icon = Icons.Rounded.TableView,
                    title = "Export Sales CSV",
                    subtitle = "${state.sales.size} recorded bills with line-level cost & profit",
                    onClick = {
                        viewModel.exportSalesCsv { file ->
                            ExportUtils.shareFile(context, file, "text/csv", "Sales Report CSV")
                        }
                    },
                    testTag = "report_export_sales"
                )
            }

            item {
                ReportActionCard(
                    icon = Icons.Rounded.Inventory2,
                    title = "Export Stock CSV",
                    subtitle = "${state.products.size} products with variant breakdown",
                    onClick = {
                        viewModel.exportProductsCsv { file ->
                            ExportUtils.shareFile(context, file, "text/csv", "Stock Inventory CSV")
                        }
                    },
                    testTag = "report_export_stock"
                )
            }

            item {
                ReportActionCard(
                    icon = Icons.Rounded.Groups,
                    title = "Export Customers CSV",
                    subtitle = "${state.customers.size} saved customers with balances & preferences",
                    onClick = {
                        viewModel.exportCustomersCsv { file ->
                            ExportUtils.shareFile(context, file, "text/csv", "Customers CSV")
                        }
                    },
                    testTag = "report_export_customers"
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionHeader(title = "Backup & Restore")
            }

            item {
                ReportActionCard(
                    icon = Icons.Rounded.CloudDownload,
                    title = "Export Full JSON Backup",
                    subtitle = "Save complete offline backup of products, sales, expenses, and customers",
                    onClick = {
                        viewModel.exportJsonBackup { file ->
                            ExportUtils.shareFile(context, file, "application/json", "SoleBook Backup")
                        }
                    },
                    testTag = "report_export_backup"
                )
            }

            item {
                ReportActionCard(
                    icon = Icons.Rounded.Restore,
                    title = "Restore from JSON Backup",
                    subtitle = "Import a previously saved SoleBook backup file",
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    testTag = "report_restore_backup"
                )
            }
        }
    }
}

@Composable
private fun ReportActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(EmeraldSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(22.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
            }
            Icon(Icons.Rounded.Share, contentDescription = "Run action", tint = TextSecondaryGrey, modifier = Modifier.size(18.dp))
        }
    }
}
