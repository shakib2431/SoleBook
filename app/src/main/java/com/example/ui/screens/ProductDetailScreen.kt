package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.example.data.local.ProductEntity
import com.example.data.local.VendorEntity
import com.example.domain.AnalyticsEngine
import com.example.domain.IndustryConfig
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.Formatters
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductDetailScreen(
    productId: Long,
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onOpenProductDetail: (Long) -> Unit = {},
    onOpenVendorDetail: (Long) -> Unit = {},
    onSourceSimilarLot: (String, String, Long?) -> Unit = { _, _, _ -> },
    onPromoteOnWhatsApp: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val product = state.products.firstOrNull { it.id == productId }
    val metrics = state.productMetrics[productId]
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig

    var showEditSheet by remember { mutableStateOf(false) }
    var showRestockSheet by remember { mutableStateOf(false) }

    if (product == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(CanvasWarmWhite).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Product not found", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "Go Back", onClick = onBack)
        }
        return
    }

    val unitWord = product.unitLabel.ifBlank { industry.unitPlural }
    val variants = (metrics?.variants ?: state.variants.filter { it.productId == productId })
        .sortedBy { it.size.toIntOrNull() ?: 99 }
    val purchases = state.purchases.filter { it.productId == productId }

    Scaffold(
        containerColor = CanvasWarmWhite,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = product.skuCode,
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
                        IconButton(
                            onClick = { showEditSheet = true },
                            modifier = Modifier.size(48.dp).testTag("edit_product_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = EmeraldPrimary)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                    ProductPhoto(
                        photoUri = product.photoUri,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 16.dp
                    )
                    SkuBoxTag(
                        skuCode = product.skuCode,
                        large = true,
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val subtitleMeta = listOfNotNull(
                        product.brand.takeIf { it.isNotBlank() },
                        product.category.takeIf { it.isNotBlank() },
                        product.gender.takeIf { industry.showAudienceSegmentField && it.isNotBlank() }
                    ).joinToString(" • ")
                    if (subtitleMeta.isNotBlank()) {
                        Text(subtitleMeta, style = MaterialTheme.typography.labelLarge, color = TextSecondaryGrey)
                    }
                    Text(product.name, style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (product.rackLocation.isNotBlank()) {
                            DetailTagChip(label = "Rack ${product.rackLocation}", accent = true)
                        }
                        if (product.colorTag.isNotBlank()) {
                            DetailTagChip(label = "${industry.colorTagLabel}: ${product.colorTag}")
                        }
                        if (product.styleTag.isNotBlank()) {
                            DetailTagChip(label = "${industry.styleTagLabel}: ${product.styleTag}")
                        }
                        if (product.batchOrSerial.isNotBlank()) {
                            DetailTagChip(label = "Batch: ${product.batchOrSerial}")
                        }
                        if (product.expiryOrWarranty.isNotBlank()) {
                            DetailTagChip(label = product.expiryOrWarranty)
                        }
                        metrics?.badges.orEmpty().forEach { ProductBadgeChip(it) }

                        Surface(
                            color = if (product.isRepeatable) EmeraldSoft else SurfaceSubtle,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (product.isRepeatable) EmeraldPrimary.copy(alpha = 0.4f) else DividerGrey),
                            modifier = Modifier.clickable { viewModel.toggleProductRepeatable(product.id) }
                        ) {
                            Text(
                                text = if (product.isRepeatable) "Regular Restock (Tap to switch)" else "One-Time Lot (Tap to switch)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (product.isRepeatable) EmeraldDark else TextSecondaryGrey,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        val linkedVendor = state.vendors.firstOrNull { it.id == product.vendorId }
                        if (linkedVendor != null) {
                            Surface(
                                color = EmeraldSoft,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { onOpenVendorDetail(linkedVendor.id) }
                            ) {
                                Text(
                                    text = "Supplier: ${linkedVendor.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldDark,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Primary Restock / Source Similar Action
            item {
                val totalStock = metrics?.totalStock ?: 0
                if (!product.isRepeatable && totalStock == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            text = "Source Similar Fresh Lot",
                            onClick = { onSourceSimilarLot(product.category, product.gender, product.vendorId) },
                            icon = Icons.Rounded.AddBox,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "source_similar_lot_button"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!product.isArchived) {
                                SecondaryButton(
                                    text = "Retire Lot",
                                    onClick = { viewModel.retireSoldOutLot(product.id) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "retire_lot_button"
                                )
                            }
                            SecondaryButton(
                                text = "Add Stock",
                                onClick = { showRestockSheet = true },
                                modifier = Modifier.weight(1f),
                                testTag = "restock_button"
                            )
                        }
                    }
                } else {
                    PrimaryButton(
                        text = "Restock Product",
                        onClick = { showRestockSheet = true },
                        icon = Icons.Rounded.LocalShipping,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "restock_button"
                    )
                }
            }

            // Financials & Velocity
            item {
                val marginUnit = metrics?.marginPerPair ?: (product.sellingPrice - product.costPrice)
                val marginPct = metrics?.marginPercent ?: 0.0
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinancialMetric(
                        label = "Margin / ${industry.unitSingular}",
                        value = Formatters.formatCurrency(marginUnit, currency),
                        subtitle = "${marginPct.roundToInt()}% • Cost ${Formatters.formatCurrency(product.costPrice, currency)}",
                        valueColor = if (marginUnit >= 0) SemanticSuccess else SemanticError,
                        modifier = Modifier.weight(1f)
                    )
                    FinancialMetric(
                        label = "Profit Earned",
                        value = Formatters.formatCurrency(metrics?.totalProfitEarned ?: 0.0, currency),
                        subtitle = "${metrics?.totalUnitsSold ?: 0} $unitWord sold",
                        valueColor = SemanticSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                val stock = metrics?.totalStock ?: 0
                val coverText = metrics?.daysOfCover?.let { "${it.roundToInt()} days cover" } ?: "No recent sales"
                val lastSoldText = metrics?.lastSoldAt?.let { "Last sold ${Formatters.formatShortDate(it)}" } ?: "Never sold yet"
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinancialMetric(
                        label = "Stock Left",
                        value = "$stock $unitWord",
                        subtitle = coverText,
                        valueColor = if (stock <= product.reorderLevel) SemanticError else TextCharcoal,
                        modifier = Modifier.weight(1f)
                    )
                    FinancialMetric(
                        label = "7 / 30 / 90d Sold",
                        value = "${metrics?.sold7d ?: 0} / ${metrics?.sold30d ?: 0} / ${metrics?.sold90d ?: 0}",
                        subtitle = lastSoldText,
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("14-Day Sales Trend", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                        Spacer(Modifier.height(10.dp))
                        SparklineChart(
                            points = metrics?.sparkline14d ?: listOf(0, 0),
                            lineColor = EmeraldPrimary,
                            modifier = Modifier.fillMaxWidth().height(76.dp)
                        )
                    }
                }
            }

            // Variant Breakdown
            item {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Stock by ${industry.variantLabel}", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            variants.forEach { v ->
                                val bg = when {
                                    v.stock == 0 -> SemanticErrorSoft
                                    v.stock <= 2 -> SemanticWarningSoft
                                    else -> SurfaceSubtle
                                }
                                val fg = if (v.stock == 0) SemanticError else TextCharcoal
                                Surface(color = bg, shape = RoundedCornerShape(10.dp)) {
                                    Text(
                                        text = "${v.size}: ${v.stock}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = fg,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Similar In-Stock Items when low/zero
            val hasZeroSizeOrLowStock = (metrics?.totalStock ?: 0) <= product.reorderLevel || variants.any { it.stock == 0 }
            val similarItems = if (hasZeroSizeOrLowStock) {
                AnalyticsEngine.findSimilarInStock(
                    targetProduct = product,
                    desiredSize = null,
                    desiredCategory = product.category,
                    products = state.products,
                    variants = state.variants,
                    limit = 3
                )
            } else emptyList()

            if (similarItems.isNotEmpty()) {
                item {
                    Surface(
                        color = EmeraldSoft.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Similar In-Stock Items", style = MaterialTheme.typography.titleMedium, color = EmeraldDark)
                            similarItems.forEach { alt ->
                                ProductListItem(
                                    product = alt.product,
                                    totalStock = alt.availableStock,
                                    currencySymbol = currency,
                                    unitPlural = industry.unitPlural,
                                    onClick = { onOpenProductDetail(alt.product.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Secondary Actions: WhatsApp Campaign, Duplicate, Archive
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Promote New Stock on WhatsApp",
                        onClick = { onPromoteOnWhatsApp(product.id) },
                        icon = Icons.Rounded.Campaign,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "promote_whatsapp_button"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            text = "Duplicate",
                            onClick = { viewModel.duplicateProduct(product) },
                            icon = Icons.Rounded.ContentCopy,
                            accentColor = TextCharcoal,
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = if (product.isArchived) "Unarchive" else "Archive",
                            onClick = { viewModel.toggleArchiveProduct(product) },
                            icon = if (product.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                            accentColor = TextCharcoal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Text("Purchase / Restock History", style = MaterialTheme.typography.titleLarge, color = TextCharcoal)
            }
            if (purchases.isEmpty()) {
                item {
                    Text(
                        "No restock bills logged yet. Tap 'Restock Product' when new inventory arrives.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryGrey
                    )
                }
            } else {
                items(purchases, key = { it.id }) { p ->
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
                                Text(
                                    "+${p.totalUnits} $unitWord @ ${Formatters.formatCurrency(p.unitCostPrice, currency)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextCharcoal
                                )
                                Text(
                                    "${Formatters.formatShortDate(p.timestamp)} • ${p.supplier.ifBlank { "Supplier" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryGrey
                                )
                                if (p.sizeBreakdown.isNotBlank()) {
                                    Text(p.sizeBreakdown, style = MaterialTheme.typography.labelSmall, color = TextMutedGrey)
                                }
                            }
                            Text(
                                text = Formatters.formatCurrency(p.totalCost, currency),
                                style = SoleBookTextStyles.FinancialCompact,
                                color = TextCharcoal
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet) {
        AddEditProductSheet(
            initialProduct = product,
            initialSizes = variants.associate { it.size to it.stock },
            autoSku = product.skuCode,
            currency = currency,
            industryConfig = industry,
            vendors = state.vendors,
            initialVendorId = product.vendorId,
            showLotPaymentFields = false,
            onDismiss = { showEditSheet = false },
            onSave = { updated, sizes ->
                viewModel.saveProduct(updated, sizes)
                showEditSheet = false
            }
        )
    }

    if (showRestockSheet) {
        RestockModalSheet(
            product = product,
            industryConfig = industry,
            existingVariants = variants.associate { it.size to it.stock },
            existingTotalStock = metrics?.totalStock ?: 0,
            vendors = state.vendors,
            currency = currency,
            onDismiss = { showRestockSheet = false },
            onConfirm = { addedSizes, newCost, supplier, notes, vendorId, paidAmt ->
                viewModel.restockProduct(
                    productId = product.id,
                    addedPerSize = addedSizes,
                    newUnitCost = newCost,
                    supplier = supplier,
                    notes = notes,
                    vendorId = vendorId,
                    paidAmount = paidAmt
                )
                showRestockSheet = false
            }
        )
    }
}

@Composable
private fun DetailTagChip(label: String, accent: Boolean = false) {
    Surface(
        color = if (accent) EmeraldSoft else SurfaceSubtle,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (accent) EmeraldPrimary.copy(alpha = 0.25f) else DividerGrey)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) EmeraldDark else TextCharcoal,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestockModalSheet(
    product: ProductEntity,
    industryConfig: IndustryConfig,
    existingVariants: Map<String, Int>,
    existingTotalStock: Int,
    vendors: List<VendorEntity>,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Int>, Double, String, String, Long?, Double?) -> Unit
) {
    val addedMap = remember {
        mutableStateMapOf<String, Int>().apply {
            existingVariants.keys.forEach { put(it, 0) }
        }
    }
    var costText by remember { mutableStateOf(product.costPrice.toInt().toString()) }
    var selectedVendorId by remember { mutableStateOf(product.vendorId) }
    var supplier by remember {
        mutableStateOf(
            product.supplier.ifBlank { vendors.firstOrNull { it.id == product.vendorId }?.name.orEmpty() }
        )
    }
    var notes by remember { mutableStateOf("") }
    var paymentModeChoice by remember { mutableStateOf("PAID") }
    var partialPaidText by remember { mutableStateOf("") }

    val unitWord = product.unitLabel.ifBlank { industryConfig.unitPlural }
    val totalAdded = addedMap.values.sum()
    val newUnitCost = costText.toDoubleOrNull() ?: product.costPrice
    val totalBill = totalAdded * newUnitCost
    val weightedPreview = if (existingTotalStock + totalAdded > 0) {
        ((existingTotalStock * product.costPrice) + (totalAdded * newUnitCost)) / (existingTotalStock + totalAdded)
    } else newUnitCost

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceWhite) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Restock • ${product.skuCode}", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Text("Enter quantity received per ${industryConfig.variantLabel.lowercase()}:", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)

            addedMap.keys.sortedBy { it.toIntOrNull() ?: 99 }.forEach { sz ->
                val addQty = addedMap[sz] ?: 0
                val cur = existingVariants[sz] ?: 0
                Surface(
                    color = SurfaceSubtle,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${industryConfig.variantLabel}: $sz", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                            Text("Currently $cur $unitWord", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                        }
                        FilledTonalIconButton(
                            onClick = { addedMap[sz] = (addQty - 1).coerceAtLeast(0) },
                            modifier = Modifier.size(48.dp)
                        ) { Icon(Icons.Rounded.Remove, contentDescription = "Less") }
                        Text("+$addQty", style = SoleBookTextStyles.FinancialCompact, modifier = Modifier.padding(horizontal = 12.dp))
                        FilledTonalIconButton(
                            onClick = { addedMap[sz] = addQty + 1 },
                            modifier = Modifier.size(48.dp)
                        ) { Icon(Icons.Rounded.Add, contentDescription = "More") }
                    }
                }
            }

            FormField(
                value = costText,
                onValueChange = { s -> costText = s.filter { it.isDigit() || it == '.' } },
                label = "New Cost per ${industryConfig.unitSingular} ($currency)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Surface(
                color = EmeraldSoft,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Weighted Avg Cost: ${Formatters.formatCurrency(weightedPreview, currency)} / ${industryConfig.unitSingular} (was ${Formatters.formatCurrency(product.costPrice, currency)})",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldDark,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (vendors.isNotEmpty()) {
                Text("Supplier", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vendors, key = { it.id }) { v ->
                        FilterChip(
                            selected = selectedVendorId == v.id,
                            onClick = {
                                selectedVendorId = v.id
                                supplier = v.name
                            },
                            label = { Text(v.name) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            FormField(value = supplier, onValueChange = { supplier = it }, label = "Supplier Name")

            if (totalBill > 0) {
                Text("Bill Payment (${Formatters.formatCurrency(totalBill, currency)})", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = paymentModeChoice == "PAID",
                        onClick = { paymentModeChoice = "PAID" },
                        label = { Text("Paid Full") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = paymentModeChoice == "PARTIAL",
                        onClick = { paymentModeChoice = "PARTIAL" },
                        label = { Text("Partial") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = paymentModeChoice == "CREDIT",
                        onClick = { paymentModeChoice = "CREDIT" },
                        label = { Text("Credit") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                }
                if (paymentModeChoice == "PARTIAL") {
                    FormField(
                        value = partialPaidText,
                        onValueChange = { s -> partialPaidText = s.filter { it.isDigit() || it == '.' } },
                        label = "Paid Now ($currency)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            FormField(value = notes, onValueChange = { notes = it }, label = "Bill / Notes (optional)")

            PrimaryButton(
                text = "Add +$totalAdded $unitWord to Stock",
                onClick = {
                    val paid = when (paymentModeChoice) {
                        "CREDIT" -> 0.0
                        "PARTIAL" -> (partialPaidText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, totalBill)
                        else -> totalBill
                    }
                    onConfirm(addedMap.toMap(), newUnitCost, supplier, notes, selectedVendorId, paid)
                },
                enabled = totalAdded > 0 && newUnitCost > 0,
                modifier = Modifier.fillMaxWidth(),
                testTag = "confirm_restock_button"
            )
        }
    }
}
