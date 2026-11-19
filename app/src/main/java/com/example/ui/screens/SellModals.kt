package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.domain.AnalyticsEngine
import com.example.domain.IndustryConfig
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.AppStrings
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickSellModalSheet(
    product: ProductEntity,
    variants: List<ProductVariantEntity>,
    customers: List<CustomerEntity>,
    allProducts: List<ProductEntity>,
    allVariants: List<ProductVariantEntity>,
    initialSize: String? = null,
    currency: String,
    industryConfig: IndustryConfig,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSwitchProduct: (ProductEntity, String?) -> Unit,
    onAddToBill: (size: String, qty: Int, price: Double) -> Unit,
    onSoldNow: (
        size: String,
        qty: Int,
        price: Double,
        mode: String,
        discType: String,
        discVal: Double,
        phone: String,
        name: String
    ) -> Unit
) {
    val inStockVariants = variants.filter { it.stock > 0 }
    val preferredAvailable = initialSize?.takeIf { sz -> variants.any { it.size == sz && it.stock > 0 } }
    var selectedSize by remember(product.id, initialSize) {
        mutableStateOf(preferredAvailable ?: inStockVariants.firstOrNull()?.size ?: "")
    }
    var outOfStockCheckedSize by remember(product.id, initialSize) {
        mutableStateOf(if (initialSize != null && preferredAvailable == null) initialSize else null)
    }
    var quantity by remember(product.id) { mutableIntStateOf(1) }
    var priceInput by remember(product.id) { mutableStateOf(product.sellingPrice.toInt().toString()) }
    var paymentMode by remember { mutableStateOf("CASH") }
    var showExtras by remember { mutableStateOf(false) }
    var discountType by remember { mutableStateOf("AMOUNT") }
    var discountInput by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerSkipped by remember { mutableStateOf(false) }

    val selectedVariantStock = variants.firstOrNull { it.size == selectedSize }?.stock ?: 0
    val parsedPrice = priceInput.toDoubleOrNull() ?: product.sellingPrice
    val isSellingAtLoss = parsedPrice < product.costPrice

    val inlineAlternatives = remember(product.id, outOfStockCheckedSize, allProducts, allVariants) {
        val sz = outOfStockCheckedSize
        if (sz != null) {
            AnalyticsEngine.findSimilarInStock(
                targetProduct = product,
                desiredSize = sz,
                desiredCategory = product.category,
                products = allProducts,
                variants = allVariants,
                limit = 4
            )
        } else emptyList()
    }

    LaunchedEffect(customerPhone) {
        val clean = customerPhone.trim()
        if (clean.length >= 8) {
            val match = customers.firstOrNull { it.phone.endsWith(clean) || it.phone == clean }
            if (match != null && customerName.isBlank()) {
                customerName = match.name
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DividerGrey) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductPhoto(
                    photoUri = product.photoUri,
                    contentDescription = product.name,
                    modifier = Modifier.size(72.dp),
                    cornerRadius = 12.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SkuBoxTag(skuCode = product.skuCode)
                        if (product.rackLocation.isNotBlank()) {
                            Surface(color = EmeraldSoft, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = product.rackLocation,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(product.category, style = MaterialTheme.typography.labelMedium, color = TextSecondaryGrey)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(product.name, style = MaterialTheme.typography.titleLarge, color = TextCharcoal)
                    Text(
                        "Cost ${Formatters.formatCurrency(product.costPrice, currency)} • MRP ${Formatters.formatCurrency(product.mrp ?: product.sellingPrice, currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedGrey
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("1. Select ${industryConfig.variantLabel}", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                variants.forEach { v ->
                    val inStock = v.stock > 0
                    val isSelected = inStock && v.size == selectedSize
                    val isOutChecked = !inStock && v.size == outOfStockCheckedSize
                    FilterChip(
                        selected = isSelected || isOutChecked,
                        onClick = {
                            if (inStock) {
                                selectedSize = v.size
                                outOfStockCheckedSize = null
                                quantity = quantity.coerceAtMost(v.stock).coerceAtLeast(1)
                            } else {
                                outOfStockCheckedSize = v.size
                            }
                        },
                        label = { Text("${v.size} (${v.stock})", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("size_chip_${v.size}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isOutChecked) SemanticError else EmeraldPrimary,
                            selectedLabelColor = PureWhite,
                            containerColor = if (inStock) SurfaceSubtle else SemanticErrorSoft,
                            labelColor = if (inStock) TextCharcoal else SemanticError
                        )
                    )
                }
            }

            outOfStockCheckedSize?.let { missingSz ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = SurfaceSubtle,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("inline_similar_alternatives")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${industryConfig.variantLabel} $missingSz out of stock — Similar in stock:",
                            style = MaterialTheme.typography.titleSmall,
                            color = EmeraldDark
                        )
                        if (inlineAlternatives.isEmpty()) {
                            Text(
                                text = "No similar ${product.category} items have $missingSz in stock.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryGrey
                            )
                        } else {
                            inlineAlternatives.forEach { alt ->
                                ProductListItem(
                                    product = alt.product,
                                    totalStock = alt.availableStock,
                                    currencySymbol = currency,
                                    unitPlural = industryConfig.unitPlural,
                                    onClick = { onSwitchProduct(alt.product, missingSz) },
                                    trailingAction = {
                                        Text(
                                            text = "Switch",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = EmeraldPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FormField(
                    value = priceInput,
                    onValueChange = { s -> priceInput = s.filter { it.isDigit() || it == '.' } },
                    label = "Price ($currency)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "quick_sell_price_input",
                    modifier = Modifier.weight(1f)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(industryConfig.unitPlural.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            modifier = Modifier.size(48.dp)
                        ) { Icon(Icons.Rounded.Remove, contentDescription = "Decrease") }
                        Text("$quantity", style = SoleBookTextStyles.FinancialCompact, modifier = Modifier.padding(horizontal = 10.dp))
                        FilledTonalIconButton(
                            onClick = { if (quantity < selectedVariantStock) quantity++ },
                            modifier = Modifier.size(48.dp)
                        ) { Icon(Icons.Rounded.Add, contentDescription = "Increase") }
                    }
                }
            }

            if (isSellingAtLoss) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = SemanticErrorSoft,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SemanticError.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = SemanticError, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${strings.sellingAtLossWarning} (Cost ${Formatters.formatCurrency(product.costPrice, currency)})",
                            color = SemanticError,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("2. Payment Mode", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("CASH" to "Cash", "UPI" to "UPI", "CARD" to "Card", "CREDIT" to "Credit").forEach { (key, label) ->
                    FilterChip(
                        selected = paymentMode == key,
                        onClick = { paymentMode = key },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pay_mode_$key"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = PureWhite,
                            containerColor = SurfaceSubtle
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showExtras = !showExtras }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(
                    if (showExtras) Icons.Rounded.ExpandLess else Icons.Rounded.PersonAdd,
                    contentDescription = null,
                    tint = EmeraldPrimary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (showExtras) "Hide discount & customer" else "Add discount or customer (optional)",
                    color = EmeraldPrimary
                )
            }

            AnimatedVisibility(visible = showExtras) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormField(
                            value = discountInput,
                            onValueChange = { s -> discountInput = s.filter { it.isDigit() || it == '.' } },
                            label = "Discount",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(selected = discountType == "AMOUNT", onClick = { discountType = "AMOUNT" }, label = { Text(currency) }, modifier = Modifier.heightIn(min = 48.dp))
                        FilterChip(selected = discountType == "PERCENT", onClick = { discountType = "PERCENT" }, label = { Text("%") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                    if (!customerSkipped) {
                        FormField(
                            value = customerPhone,
                            onValueChange = { customerPhone = it.filter { ch -> ch.isDigit() }.take(12) },
                            label = "Customer Phone",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        FormField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            label = "Customer Name (optional)"
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            val discVal = discountInput.toDoubleOrNull() ?: 0.0
            val subtotal = parsedPrice * quantity
            val resolvedDisc = if (discountType == "PERCENT") subtotal * (discVal / 100.0) else discVal
            val finalTotal = (subtotal - resolvedDisc).coerceAtLeast(0.0)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = strings.addToBill,
                    onClick = { if (selectedSize.isNotEmpty()) onAddToBill(selectedSize, quantity, parsedPrice) },
                    enabled = selectedSize.isNotEmpty() && selectedVariantStock >= quantity,
                    modifier = Modifier.weight(1f),
                    testTag = "add_to_bill_button"
                )
                PrimaryButton(
                    text = "${strings.soldButton} • ${Formatters.formatCurrency(finalTotal, currency)}",
                    onClick = {
                        if (selectedSize.isNotEmpty()) {
                            onSoldNow(selectedSize, quantity, parsedPrice, paymentMode, discountType, discVal, customerPhone, customerName)
                        }
                    },
                    enabled = selectedSize.isNotEmpty() && selectedVariantStock >= quantity,
                    modifier = Modifier.weight(1.25f),
                    testTag = "sold_now_button"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartCheckoutSheet(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onDismiss: () -> Unit
) {
    val currency = state.settings.currencySymbol
    var paymentMode by remember { mutableStateOf("CASH") }
    var discountType by remember { mutableStateOf("AMOUNT") }
    var discountInput by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }

    LaunchedEffect(customerPhone) {
        val clean = customerPhone.trim()
        if (clean.length >= 8) {
            val match = state.customers.firstOrNull { it.phone.endsWith(clean) || it.phone == clean }
            if (match != null && customerName.isBlank()) customerName = match.name
        }
    }

    val subtotal = state.cart.sumOf { it.unitSellingPrice * it.quantity }
    val discVal = discountInput.toDoubleOrNull() ?: 0.0
    val discAmount = if (discountType == "PERCENT") subtotal * (discVal.coerceIn(0.0, 100.0) / 100.0) else discVal.coerceIn(0.0, subtotal)
    val finalTotal = (subtotal - discAmount).coerceAtLeast(0.0)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceWhite) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Current Bill", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
                TextButton(
                    onClick = { viewModel.clearCart(); onDismiss() },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Clear Bill", color = SemanticError)
                }
            }

            state.cart.forEach { item ->
                Surface(
                    color = SurfaceSubtle,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductPhoto(photoUri = item.product.photoUri, contentDescription = item.product.name, modifier = Modifier.size(48.dp), cornerRadius = 8.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${item.product.skuCode} • ${item.size} • ${Formatters.formatCurrency(item.unitSellingPrice, currency)}", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                        }
                        IconButton(onClick = { viewModel.updateCartItemQty(item.id, item.quantity - 1) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Less")
                        }
                        Text("${item.quantity}", style = SoleBookTextStyles.FinancialInline)
                        IconButton(onClick = { viewModel.updateCartItemQty(item.id, item.quantity + 1) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.AddCircleOutline, contentDescription = "More")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FormField(
                    value = discountInput,
                    onValueChange = { s -> discountInput = s.filter { it.isDigit() || it == '.' } },
                    label = "Bill Discount",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(selected = discountType == "AMOUNT", onClick = { discountType = "AMOUNT" }, label = { Text(currency) }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = discountType == "PERCENT", onClick = { discountType = "PERCENT" }, label = { Text("%") }, modifier = Modifier.heightIn(min = 48.dp))
            }

            Text("Payment Mode", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("CASH" to "Cash", "UPI" to "UPI", "CARD" to "Card", "CREDIT" to "Credit").forEach { (k, label) ->
                    FilterChip(
                        selected = paymentMode == k,
                        onClick = { paymentMode = k },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = PureWhite,
                            containerColor = SurfaceSubtle
                        )
                    )
                }
            }

            FormField(
                value = customerPhone,
                onValueChange = { customerPhone = it.filter { ch -> ch.isDigit() }.take(12) },
                label = "Customer Phone (optional)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            FormField(value = customerName, onValueChange = { customerName = it }, label = "Customer Name (optional)")

            Spacer(Modifier.height(6.dp))
            PrimaryButton(
                text = "Complete Sale • ${Formatters.formatCurrency(finalTotal, currency)}",
                onClick = {
                    viewModel.completeCartCheckout(paymentMode, discountType, discVal, customerPhone, customerName)
                    onDismiss()
                },
                enabled = state.cart.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                testTag = "cart_done_button"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecentSalesAndReturnsSheet(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onDismiss: () -> Unit
) {
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig
    val recentSales = remember(state.sales) { state.sales.take(25) }
    val itemsBySale = remember(state.saleItems) { state.saleItems.groupBy { it.saleId } }

    var selectedReturnItem by remember { mutableStateOf<Pair<SaleEntity, SaleItemEntity>?>(null) }
    var isExchange by remember { mutableStateOf(false) }
    var exchangeSize by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Customer exchange") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceWhite) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Recent Bills & Returns", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Text(
                "Tap Return or Swap on any item to refund or exchange ${industry.variantLabel.lowercase()}.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryGrey
            )
            Spacer(Modifier.height(12.dp))

            if (recentSales.isEmpty()) {
                Text("No sales recorded yet.", style = MaterialTheme.typography.bodyLarge, color = TextMutedGrey)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentSales, key = { it.id }) { sale ->
                        val items = itemsBySale[sale.id].orEmpty()
                        Surface(
                            color = SurfaceSubtle,
                            shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                            border = BorderStroke(1.dp, DividerGrey)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${sale.billNumber} • ${sale.paymentMode}", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                                    Text(
                                        Formatters.formatCurrency(sale.totalCollected, currency),
                                        style = SoleBookTextStyles.FinancialCompact,
                                        color = EmeraldPrimary
                                    )
                                }
                                Text(
                                    Formatters.formatDateTime(sale.timestamp) +
                                        if (sale.customerNameSnapshot.isNotBlank()) " • ${sale.customerNameSnapshot}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMutedGrey
                                )
                                Spacer(Modifier.height(8.dp))
                                items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${item.skuSnapshot} ${item.productNameSnapshot} (${item.size}) x${item.quantity}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextCharcoal,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                selectedReturnItem = sale to item
                                                isExchange = false
                                                exchangeSize = ""
                                            },
                                            modifier = Modifier.heightIn(min = 44.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Return / Swap", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedReturnItem?.let { (sale, item) ->
        val prodVariants = state.variants.filter { it.productId == item.productId && it.stock > 0 && it.size != item.size }
        AlertDialog(
            onDismissRequest = { selectedReturnItem = null },
            containerColor = SurfaceWhite,
            title = { Text("${item.skuSnapshot} • ${item.size}", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isExchange, onClick = { isExchange = false }, label = { Text("Full Return") }, modifier = Modifier.heightIn(min = 48.dp))
                        FilterChip(selected = isExchange, onClick = { isExchange = true }, label = { Text("Exchange") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                    if (isExchange) {
                        Text("Pick replacement ${industry.variantLabel.lowercase()} in stock:", style = MaterialTheme.typography.labelMedium)
                        if (prodVariants.isEmpty()) {
                            Text("No other variant currently in stock.", color = SemanticError)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                prodVariants.forEach { v ->
                                    FilterChip(
                                        selected = exchangeSize == v.size,
                                        onClick = { exchangeSize = v.size },
                                        label = { Text("${v.size} (${v.stock})") },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            "Restores 1 ${industry.unitSingular} back into stock and refunds ${Formatters.formatCurrency(item.sellingPriceAtSale, currency)}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryGrey
                        )
                    }
                    FormField(value = reason, onValueChange = { reason = it }, label = "Reason")
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = if (isExchange) "Confirm Exchange" else "Confirm Return",
                    onClick = {
                        viewModel.processReturnOrExchange(sale.id, item, 1, isExchange, exchangeSize.ifBlank { null }, reason)
                        selectedReturnItem = null
                    },
                    enabled = !isExchange || exchangeSize.isNotBlank()
                )
            },
            dismissButton = {
                TextButton(onClick = { selectedReturnItem = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel", color = TextSecondaryGrey)
                }
            }
        )
    }
}
