package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomerEntity
import com.example.domain.DayBar
import com.example.domain.ProductBadge
import com.example.ui.components.FormField
import com.example.ui.components.SoleBookSpacing
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.util.ExportUtils
import com.example.util.Formatters
import kotlin.math.max

@Composable
internal fun DailyRevenueProfitChart(
    bars: List<DayBar>,
    modifier: Modifier = Modifier
) {
    val maxVal = max(1.0, bars.maxOfOrNull { max(it.revenue, it.profit) } ?: 1.0)
    Canvas(modifier = modifier) {
        val count = bars.size.coerceAtLeast(1)
        val groupW = size.width / count
        val barW = (groupW * 0.34f).coerceAtMost(24f)
        val gap = (barW * 0.25f)

        bars.forEachIndexed { idx, bar ->
            val centerX = idx * groupW + groupW / 2f
            val revH = ((bar.revenue / maxVal) * size.height * 0.90f).toFloat().coerceAtLeast(4f)
            val profH = ((bar.profit.coerceAtLeast(0.0) / maxVal) * size.height * 0.90f).toFloat().coerceAtLeast(4f)

            drawRoundRect(
                color = EmeraldPrimary.copy(alpha = 0.38f),
                topLeft = Offset(centerX - barW - gap / 2f, size.height - revH),
                size = Size(barW, revH),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = EmeraldPrimary,
                topLeft = Offset(centerX + gap / 2f, size.height - profH),
                size = Size(barW, profH),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
    }
}

@Composable
internal fun DailyClosingReportDialog(
    state: SoleBookUiState,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currency = state.settings.currencySymbol
    val unitPlural = state.industryConfig.unitPlural
    val shopLabel = state.settings.shopName.ifBlank { "My Shop" }

    val todayStart = Formatters.startOfDay()
    val todayEnd = Formatters.endOfDay()
    val todaySales = state.sales.filter { it.timestamp in todayStart..todayEnd }
    val todayIds = todaySales.map { it.id }.toSet()
    val todayItems = state.saleItems.filter { it.saleId in todayIds }
    val todayReturns = state.returns.filter { it.type == "RETURN" && it.timestamp in todayStart..todayEnd }
    val todayRev = todaySales.sumOf { it.totalRevenue } - todayReturns.sumOf { it.refundAmount }
    val todayCogs = todayItems.sumOf { it.costPriceAtSale * it.quantity } - todayReturns.sumOf { it.costReversed }
    val todayProfit = todayRev - todayCogs
    val cashAmt = todaySales.filter { it.paymentMode == "CASH" }.sumOf { it.totalCollected }
    val upiAmt = todaySales.filter { it.paymentMode == "UPI" }.sumOf { it.totalCollected }
    val cardAmt = todaySales.filter { it.paymentMode == "CARD" }.sumOf { it.totalCollected }
    val creditAmt = todaySales.filter { it.paymentMode == "CREDIT" }.sumOf { it.totalCollected }
    val lowStockItems = state.productMetrics.values.filter {
        !it.product.isArchived && (ProductBadge.LOW_STOCK in it.badges || ProductBadge.OUT_OF_STOCK in it.badges)
    }.take(6)

    val whatsappText = buildString {
        appendLine("*$shopLabel — Daily Closing*")
        appendLine("Date: ${Formatters.formatDayHeader(System.currentTimeMillis())}")
        appendLine("-------------------------")
        appendLine("Bills: ${todaySales.size} | ${unitPlural.replaceFirstChar { it.uppercase() }}: ${todayItems.sumOf { it.quantity }}")
        appendLine("Sales: ${Formatters.formatCurrency(todayRev, currency)}")
        appendLine("Gross Profit: ${Formatters.formatCurrency(todayProfit, currency)}")
        appendLine("Cash: ${Formatters.formatCurrency(cashAmt, currency)} | UPI: ${Formatters.formatCurrency(upiAmt, currency)}")
        if (cardAmt > 0 || creditAmt > 0) {
            appendLine("Card: ${Formatters.formatCurrency(cardAmt, currency)} | Credit: ${Formatters.formatCurrency(creditAmt, currency)}")
        }
        if (lowStockItems.isNotEmpty()) {
            appendLine("Low Stock: " + lowStockItems.joinToString(", ") { "${it.product.skuCode} (${it.totalStock})" })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = {
            Text("Today's Closing Report", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    Formatters.formatDayHeader(System.currentTimeMillis()),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondaryGrey
                )
                HorizontalDivider(color = DividerGrey)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Sales (${todaySales.size} bills)", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)
                    Text(Formatters.formatCurrency(todayRev, currency), style = SoleBookTextStyles.FinancialInline, color = TextCharcoal)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cost of Goods (COGS)", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)
                    Text(Formatters.formatCurrency(todayCogs, currency), style = SoleBookTextStyles.FinancialInline, color = TextCharcoal)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Gross Profit", style = MaterialTheme.typography.bodyMedium, color = TextCharcoal, fontWeight = FontWeight.SemiBold)
                    Text(
                        Formatters.formatCurrency(todayProfit, currency),
                        style = SoleBookTextStyles.FinancialInline,
                        fontWeight = FontWeight.Bold,
                        color = if (todayProfit >= 0) SemanticSuccess else SemanticError
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cash / UPI", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)
                    Text(
                        "${Formatters.formatCurrency(cashAmt, currency)} / ${Formatters.formatCurrency(upiAmt, currency)}",
                        style = SoleBookTextStyles.FinancialInline,
                        color = TextCharcoal
                    )
                }
                if (lowStockItems.isNotEmpty()) {
                    Surface(color = SemanticWarningSoft, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Low Stock (${lowStockItems.size}): " + lowStockItems.joinToString(", ") { "${it.product.skuCode}(${it.totalStock})" },
                            style = MaterialTheme.typography.labelMedium,
                            color = SemanticWarning,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { ExportUtils.shareText(context, "Daily Closing Report", whatsappText) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share Summary", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Close", color = TextSecondaryGrey)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExpenseDialog(
    currency: String,
    onDismiss: () -> Unit,
    onSave: (category: String, amount: Double, recurring: Boolean, notes: String) -> Unit
) {
    val categories = listOf("Rent", "Salary", "Electricity", "Transport", "Packaging", "Other")
    var category by remember { mutableStateOf("Rent") }
    var amountText by remember { mutableStateOf("") }
    var recurring by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = {
            Text("Add Business Expense", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                recurring = cat in listOf("Rent", "Salary", "Electricity")
                            },
                            label = { Text(cat, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.heightIn(min = 46.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldSoft,
                                selectedLabelColor = EmeraldDark,
                                containerColor = SurfaceSubtle
                            )
                        )
                    }
                }
                FormField(
                    value = amountText,
                    onValueChange = { s -> amountText = s.filter { it.isDigit() || it == '.' } },
                    label = "Amount (${currency.trim()}) *",
                    placeholder = "e.g. 2500",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "expense_amount_input"
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = recurring, onCheckedChange = { recurring = it })
                    Text("Monthly recurring fixed expense", style = MaterialTheme.typography.bodyMedium, color = TextCharcoal)
                }
                FormField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes (optional)",
                    placeholder = "e.g. Shop rent or utility bill"
                )
            }
        },
        confirmButton = {
            val amt = amountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = { onSave(category, amt, recurring, notes.trim()) },
                enabled = amt > 0,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_expense_button")
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
fun CustomerCreditRow(
    customer: CustomerEntity,
    currency: String,
    onSettle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                customer.name.ifBlank { customer.phone },
                style = MaterialTheme.typography.titleSmall,
                color = TextCharcoal
            )
            Text(customer.phone, style = MaterialTheme.typography.labelSmall, color = TextMutedGrey)
        }
        Text(
            Formatters.formatCurrency(customer.creditBalance, currency),
            style = SoleBookTextStyles.FinancialInline,
            color = SemanticError,
            fontWeight = FontWeight.Bold
        )
        OutlinedButton(
            onClick = onSettle,
            modifier = Modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, EmeraldPrimary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary)
        ) {
            Text("Settle", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
