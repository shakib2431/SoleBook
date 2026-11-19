package com.example.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.*
import com.example.domain.IndustryConfig
import com.example.domain.onboarding.BarcodeLookupResult
import com.example.domain.onboarding.StockCountService
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.FormField
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataHubUiState
import com.example.ui.viewmodel.OnboardingHubController
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeOnboardingSheet(
    hubState: DataHubUiState,
    existingSkus: List<String>,
    currency: String,
    industryConfig: IndustryConfig,
    controller: OnboardingHubController,
    onDismiss: () -> Unit,
    onOpenProductDetail: (Long) -> Unit
) {
    var barcodeText by remember { mutableStateOf("") }
    var allowExternalLookup by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            controller.clearBarcodeLookup()
            onDismiss()
        },
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DividerGrey) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Barcode Onboarding", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Text(
                text = "Scan or enter any EAN/UPC/QR code. Matches existing stock for quick restock or confirms new product creation without guessing details.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryGrey
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = barcodeText,
                    onValueChange = { barcodeText = it },
                    label = { Text("Barcode / SKU") },
                    placeholder = { Text("e.g. 8901491101837") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("barcode_onboard_input")
                )
                Button(
                    onClick = { showScanner = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 52.dp).testTag("barcode_onboard_scan_btn")
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan")
                    Spacer(Modifier.width(4.dp))
                    Text("Scan")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = allowExternalLookup,
                    onCheckedChange = { allowExternalLookup = it },
                    modifier = Modifier.testTag("barcode_external_toggle")
                )
                Text(
                    "Allow online Open Food Facts metadata lookup if unknown locally",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondaryGrey
                )
            }

            Button(
                onClick = {
                    val code = barcodeText.trim()
                    if (code.isNotEmpty()) controller.lookupBarcode(code, allowExternalLookup)
                },
                enabled = barcodeText.isNotBlank() && !hubState.isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = PureWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("barcode_lookup_btn")
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Check Catalog")
            }

            if (hubState.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = EmeraldPrimary)
            }

            when (val res = hubState.barcodeLookup) {
                is BarcodeLookupResult.MatchedCatalog -> {
                    MatchedBarcodeCard(
                        res = res,
                        currency = currency,
                        onRestock = { varLabel, qty, cost ->
                            controller.quickRestockByBarcode(res.product.id, varLabel, qty, cost)
                        },
                        onOpenDetail = {
                            onDismiss()
                            onOpenProductDetail(res.product.id)
                        }
                    )
                }
                is BarcodeLookupResult.ExternalCatalogHit -> {
                    CreateFromBarcodeForm(
                        barcode = res.barcode,
                        initialName = res.suggestedName,
                        initialBrand = res.suggestedBrand,
                        initialCategory = res.suggestedCategory.ifBlank { industryConfig.suggestedCategories.firstOrNull() ?: "General" },
                        initialUnit = res.suggestedPackOrUnit.ifBlank { industryConfig.unitSingular },
                        attribution = res.sourceAttribution,
                        existingSkus = existingSkus,
                        currency = currency,
                        defaultVariant = industryConfig.defaultVariants.firstOrNull() ?: "Standard",
                        duplicateConflict = hubState.duplicateConflict?.existingSku,
                        onSave = { prod, variant, qty ->
                            controller.saveBarcodeProduct(prod, variant, qty)
                        }
                    )
                }
                is BarcodeLookupResult.UnknownBarcode -> {
                    Surface(
                        color = SemanticWarningSoft,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SemanticWarning.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Unknown Barcode: ${res.barcode}", style = MaterialTheme.typography.titleSmall, color = SemanticWarning)
                            Text("${res.reason}. Enter real product details & prices below to confirm before saving.", style = MaterialTheme.typography.labelSmall, color = TextCharcoal)
                        }
                    }
                    CreateFromBarcodeForm(
                        barcode = res.barcode,
                        initialName = "",
                        initialBrand = "",
                        initialCategory = industryConfig.suggestedCategories.firstOrNull() ?: "General",
                        initialUnit = industryConfig.unitSingular,
                        attribution = null,
                        existingSkus = existingSkus,
                        currency = currency,
                        defaultVariant = industryConfig.defaultVariants.firstOrNull() ?: "Standard",
                        duplicateConflict = hubState.duplicateConflict?.existingSku,
                        onSave = { prod, variant, qty ->
                            controller.saveBarcodeProduct(prod, variant, qty)
                        }
                    )
                }
                null -> Unit
            }
        }
    }

    if (showScanner) {
        BarcodeScannerDialog(
            title = "Scan Product Barcode",
            onDismiss = { showScanner = false },
            onBarcodeDetected = { detected ->
                showScanner = false
                barcodeText = detected
                controller.lookupBarcode(detected, allowExternalLookup)
            }
        )
    }
}

@Composable
private fun MatchedBarcodeCard(
    res: BarcodeLookupResult.MatchedCatalog,
    currency: String,
    onRestock: (variant: String, qty: Int, cost: Double) -> Unit,
    onOpenDetail: () -> Unit
) {
    val totalStock = res.allVariants.sumOf { it.stock }
    var selectedVar by remember(res) {
        mutableStateOf(res.matchedVariant?.size ?: res.allVariants.firstOrNull()?.size ?: "Standard")
    }
    var addQtyText by remember { mutableStateOf("5") }
    var costText by remember(res) { mutableStateOf(res.product.costPrice.toInt().toString()) }

    Surface(
        color = EmeraldSoft,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().testTag("barcode_matched_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Catalog Match • ${res.product.skuCode}", style = MaterialTheme.typography.labelMedium, color = EmeraldDark, fontWeight = FontWeight.Bold)
                    Text(res.product.name, style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    Text(
                        "Stock: $totalStock ${res.product.unitLabel.ifBlank { "pcs" }} • Sell ${Formatters.formatCurrency(res.product.sellingPrice, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryGrey
                    )
                }
                TextButton(onClick = onOpenDetail) { Text("View") }
            }

            if (res.allVariants.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    res.allVariants.take(5).forEach { v ->
                        FilterChip(
                            selected = selectedVar == v.size,
                            onClick = { selectedVar = v.size },
                            label = { Text("${v.size} (${v.stock})") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = addQtyText,
                    onValueChange = { addQtyText = it.filter(Char::isDigit) },
                    label = "Add Qty",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                FormField(
                    value = costText,
                    onValueChange = { costText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "Unit Cost ($currency)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    val q = addQtyText.toIntOrNull() ?: 0
                    val c = costText.toDoubleOrNull() ?: res.product.costPrice
                    if (q > 0) onRestock(selectedVar, q, c)
                },
                enabled = (addQtyText.toIntOrNull() ?: 0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("barcode_quick_restock_btn")
            ) {
                Text("Add to Stock")
            }
        }
    }
}

@Composable
private fun CreateFromBarcodeForm(
    barcode: String,
    initialName: String,
    initialBrand: String,
    initialCategory: String,
    initialUnit: String,
    attribution: String?,
    existingSkus: List<String>,
    currency: String,
    defaultVariant: String,
    duplicateConflict: String?,
    onSave: (ProductEntity, String, Int) -> Unit
) {
    val autoSku = remember(existingSkus) { Formatters.generateNextSku(existingSkus, "PRD") }
    var name by remember(barcode, initialName) { mutableStateOf(initialName) }
    var brand by remember(barcode, initialBrand) { mutableStateOf(initialBrand) }
    var category by remember(barcode, initialCategory) { mutableStateOf(initialCategory) }
    var unit by remember(barcode, initialUnit) { mutableStateOf(initialUnit) }
    var variant by remember { mutableStateOf(defaultVariant) }
    var costText by remember { mutableStateOf("") }
    var sellText by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }

    Surface(
        color = SurfaceSubtle,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DividerGrey),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Confirm New Product Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (!attribution.isNullOrBlank()) {
                Text(attribution, style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
            }
            if (!duplicateConflict.isNullOrBlank()) {
                Text(
                    "Warning: Barcode already mapped to SKU $duplicateConflict",
                    style = MaterialTheme.typography.labelSmall,
                    color = SemanticError
                )
            }
            FormField(value = name, onValueChange = { name = it }, label = "Product Name *", testTag = "barcode_create_name")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(value = category, onValueChange = { category = it }, label = "Category", modifier = Modifier.weight(1f))
                FormField(value = unit, onValueChange = { unit = it }, label = "Unit", modifier = Modifier.weight(0.7f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(value = brand, onValueChange = { brand = it }, label = "Brand (opt)", modifier = Modifier.weight(1f))
                FormField(value = variant, onValueChange = { variant = it }, label = "Variant / Size", modifier = Modifier.weight(0.9f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = costText,
                    onValueChange = { costText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "Cost ($currency) *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "barcode_create_cost",
                    modifier = Modifier.weight(1f)
                )
                FormField(
                    value = sellText,
                    onValueChange = { sellText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "Selling ($currency) *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "barcode_create_sell",
                    modifier = Modifier.weight(1f)
                )
                FormField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter(Char::isDigit) },
                    label = "Qty",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "barcode_create_qty",
                    modifier = Modifier.weight(0.7f)
                )
            }
            Button(
                onClick = {
                    val c = costText.toDoubleOrNull() ?: 0.0
                    val s = sellText.toDoubleOrNull() ?: 0.0
                    val q = qtyText.toIntOrNull() ?: 0
                    val prod = ProductEntity(
                        skuCode = autoSku,
                        barcode = barcode,
                        name = name.trim(),
                        category = category.trim().ifBlank { "General" },
                        brand = brand.trim(),
                        gender = "Standard",
                        photoUri = "",
                        costPrice = c,
                        sellingPrice = s,
                        unitLabel = unit.trim().ifBlank { "pcs" }
                    )
                    onSave(prod, variant.trim().ifBlank { "Standard" }, q)
                },
                enabled = name.isNotBlank() && (costText.toDoubleOrNull() ?: 0.0) >= 0 && (sellText.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("barcode_save_new_btn")
            ) {
                Text("Confirm & Save Product", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStockCountSheet(
    locations: List<InventoryLocationEntity>,
    categories: List<String>,
    controller: OnboardingHubController,
    onDismiss: () -> Unit,
    onSessionCreated: (Long) -> Unit
) {
    var title by remember { mutableStateOf("Cycle Count ${Formatters.formatShortDate(System.currentTimeMillis())}") }
    var selectedLocation by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var prefillExpected by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceWhite) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Start Physical Stock Count", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Text(
                text = "Create a barcode-assisted count sheet to audit shelves and reconcile discrepancies with auditable inventory movements.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryGrey
            )

            FormField(value = title, onValueChange = { title = it }, label = "Session Title", testTag = "stock_count_title_input")

            if (locations.isNotEmpty()) {
                Text("Location Filter (Optional)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedLocation.isBlank(), onClick = { selectedLocation = "" }, label = { Text("All Locations") }, modifier = Modifier.heightIn(min = 48.dp))
                    locations.take(4).forEach { loc ->
                        FilterChip(
                            selected = selectedLocation == loc.code,
                            onClick = { selectedLocation = loc.code },
                            label = { Text(loc.name) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            if (categories.isNotEmpty()) {
                Text("Category Filter (Optional)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedCategory.isBlank(), onClick = { selectedCategory = "" }, label = { Text("All") }, modifier = Modifier.heightIn(min = 48.dp))
                    categories.take(4).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = prefillExpected, onCheckedChange = { prefillExpected = it })
                Text("Prefill counted quantities with expected book stock", style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    controller.createStockCountSession(title, selectedLocation, selectedCategory, prefillExpected) { id ->
                        onDismiss()
                        onSessionCreated(id)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("create_stock_count_btn")
            ) {
                Text("Start Count Session", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCountSessionScreen(
    sessionId: Long,
    products: List<ProductEntity>,
    currency: String,
    hubState: DataHubUiState,
    controller: OnboardingHubController,
    onBack: () -> Unit
) {
    val lines by controller.observeStockCountLines(sessionId).collectAsStateWithLifecycle(initialValue = emptyList())
    val session = hubState.stockCountSessions.firstOrNull { it.id == sessionId }
    val productsById = remember(products) { products.associateBy { it.id } }
    var search by remember { mutableStateOf("") }
    var showBarcodeScanner by remember { mutableStateOf(false) }

    val filteredLines = remember(lines, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) lines
        else lines.filter {
            it.productName.lowercase().contains(q) ||
                it.skuCode.lowercase().contains(q) ||
                it.barcode.lowercase().contains(q)
        }
    }

    val varianceCount = remember(lines) { lines.count { it.variance != 0 } }
    val netUnitDelta = remember(lines) { lines.sumOf { it.variance } }
    val netCostDelta = remember(lines, productsById) {
        lines.sumOf { it.variance * (productsById[it.productId]?.costPrice ?: 0.0) }
    }

    Scaffold(
        containerColor = CanvasWarmWhite,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.title ?: "Stock Count #$sessionId", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${session?.status ?: "OPEN"} • ${lines.size} lines",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryGrey
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showBarcodeScanner = true }, modifier = Modifier.size(48.dp).testTag("stock_count_scan_btn")) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan barcode +1", tint = EmeraldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Surface(
                color = SurfaceWhite,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DividerGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Discrepancies", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                        Text("$varianceCount lines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Net Units", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                        Text(
                            if (netUnitDelta > 0) "+$netUnitDelta" else "$netUnitDelta",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (netUnitDelta == 0) SemanticSuccess else SemanticWarning
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Cost Impact", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                        Text(
                            Formatters.formatCurrency(netCostDelta, currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (netCostDelta >= 0) EmeraldDark else SemanticError
                        )
                    }
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Filter SKU, barcode, product...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLines, key = { it.id }) { line ->
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DividerGrey),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = line.approved,
                                onCheckedChange = { controller.toggleStockCountLineApproved(line, it) },
                                enabled = session?.status != "COMMITTED"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(line.productName, style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                                Text(
                                    "${line.skuCode} • ${line.variantLabel} • Expected: ${line.expectedQty}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryGrey
                                )
                                val varColor = when {
                                    line.variance == 0 -> SemanticSuccess
                                    line.variance > 0 -> EmeraldPrimary
                                    else -> SemanticError
                                }
                                Text(
                                    "Variance: ${if (line.variance > 0) "+${line.variance}" else line.variance}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = varColor
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = { controller.updateStockCountLineQty(line, line.countedQty - 1) },
                                    enabled = session?.status != "COMMITTED",
                                    modifier = Modifier.size(48.dp)
                                ) { Icon(Icons.Rounded.Remove, contentDescription = "Decrease") }
                                Text(
                                    text = "${line.countedQty}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                FilledTonalIconButton(
                                    onClick = { controller.updateStockCountLineQty(line, line.countedQty + 1) },
                                    enabled = session?.status != "COMMITTED",
                                    modifier = Modifier.size(48.dp)
                                ) { Icon(Icons.Rounded.Add, contentDescription = "Increase") }
                            }
                        }
                    }
                }
            }

            if (session?.status != "COMMITTED") {
                Button(
                    onClick = { controller.commitStockCountSession(sessionId) { onBack() } },
                    enabled = lines.any { it.approved } && !hubState.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).heightIn(min = 52.dp).testTag("commit_stock_count_btn")
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Approve & Reconcile Inventory", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showBarcodeScanner) {
        BarcodeScannerDialog(
            title = "Scan to Count +1",
            onDismiss = { showBarcodeScanner = false },
            onBarcodeDetected = { code ->
                showBarcodeScanner = false
                controller.scanBarcodeInStockCount(sessionId, code, 1)
            }
        )
    }
}
