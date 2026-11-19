package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.ExportUtils
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: Long,
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig
    val customer = state.customers.firstOrNull { it.id == customerId }
    var showEditDialog by remember { mutableStateOf(false) }

    if (customer == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Customer not found", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "Back", onClick = onBack)
        }
        return
    }

    val customerSales = remember(state.sales, customerId) {
        state.sales.filter { it.customerId == customerId }.sortedByDescending { it.timestamp }
    }
    val itemsBySale = remember(state.saleItems) { state.saleItems.groupBy { it.saleId } }
    val shopName = state.settings.shopName.ifBlank { "our store" }

    Scaffold(
        containerColor = CanvasWarmWhite,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            customer.name.ifBlank { customer.phone },
                            style = MaterialTheme.typography.titleLarge,
                            color = TextCharcoal
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextCharcoal)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Customer", tint = TextCharcoal)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
                )
                HorizontalDivider(color = DividerGrey)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(SoleBookSpacing.screenHorizontal, 16.dp, SoleBookSpacing.screenHorizontal, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "WhatsApp",
                        onClick = {
                            ExportUtils.openWhatsApp(
                                context,
                                customer.phone,
                                "Namaste ${customer.name.ifBlank { "Friend" }}, thank you for shopping at $shopName!"
                            )
                        },
                        icon = Icons.Rounded.Chat,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Call",
                        onClick = { ExportUtils.dialPhone(context, customer.phone) },
                        icon = Icons.Rounded.Call,
                        accentColor = TextCharcoal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (customer.creditBalance > 0.5) {
                item {
                    Surface(
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        color = SemanticErrorSoft,
                        border = BorderStroke(1.dp, SemanticError.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Credit Balance Pending", style = MaterialTheme.typography.labelMedium, color = SemanticError)
                                Text(
                                    Formatters.formatCurrency(customer.creditBalance, currency),
                                    style = SoleBookTextStyles.FinancialPrimary,
                                    color = SemanticError
                                )
                            }
                            Button(
                                onClick = { viewModel.settleCustomerCredit(customer.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Settle Up")
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinancialMetric(
                        label = "Total Spent",
                        value = Formatters.formatCurrency(customer.totalSpent, currency),
                        subtitle = "${customer.visitCount} visits",
                        modifier = Modifier.weight(1f)
                    )
                    FinancialMetric(
                        label = industry.customerPrefLabel,
                        value = if (customer.favouriteSize.isNotBlank()) customer.favouriteSize else "—",
                        subtitle = customer.favouriteCategory.ifBlank { "No preference yet" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Phone: ${customer.phone}", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                        if (customer.email.isNotBlank()) {
                            Text("Email: ${customer.email}", style = MaterialTheme.typography.bodyMedium, color = TextCharcoal)
                        }
                        Text(
                            "First visit: ${Formatters.formatShortDate(customer.firstVisitAt)} • Last visit: ${Formatters.formatShortDate(customer.lastVisitAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryGrey
                        )
                        if (customer.specialDate.isNotBlank()) {
                            Text("Birthday / Anniversary: ${customer.specialDate}", style = MaterialTheme.typography.bodyMedium, color = TextCharcoal)
                        }
                        if (customer.notes.isNotBlank()) {
                            Text("Notes: ${customer.notes}", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)
                        }
                        Text(
                            "WhatsApp Offers: ${if (customer.whatsappConsent) "Opted In" else "Opted Out"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (customer.whatsappConsent) SemanticSuccess else TextMutedGrey
                        )
                    }
                }
            }

            item {
                SectionHeader(title = "Purchase History (${customerSales.size})")
            }

            if (customerSales.isEmpty()) {
                item {
                    Text("No bills linked to this customer yet.", style = MaterialTheme.typography.bodyMedium, color = TextMutedGrey)
                }
            } else {
                items(customerSales, key = { it.id }) { sale ->
                    val items = itemsBySale[sale.id].orEmpty()
                    Surface(
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerGrey)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${sale.billNumber} • ${sale.paymentMode}", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                                Text(
                                    Formatters.formatCurrency(sale.totalCollected, currency),
                                    style = SoleBookTextStyles.FinancialInline,
                                    color = EmeraldPrimary
                                )
                            }
                            Text(Formatters.formatDateTime(sale.timestamp), style = MaterialTheme.typography.labelSmall, color = TextMutedGrey)
                            Spacer(Modifier.height(2.dp))
                            items.forEach { it ->
                                Text(
                                    "• ${it.skuSnapshot} ${it.productNameSnapshot} (${industry.variantLabel}: ${it.size}) ×${it.quantity} — ${Formatters.formatCurrency(it.lineTotal, currency)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextCharcoal
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AddEditCustomerDialog(
            initial = customer,
            prefPlaceholder = industry.customerPrefLabel,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                viewModel.saveCustomer(updated)
                showEditDialog = false
            }
        )
    }
}
