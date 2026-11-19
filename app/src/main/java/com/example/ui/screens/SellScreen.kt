package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ProductEntity
import com.example.domain.AnalyticsEngine
import com.example.domain.ProductBadge
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onOpenSettings: () -> Unit,
    onOpenAddProduct: () -> Unit,
    onOpenNotices: () -> Unit = {}
) {
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig
    val strings = state.strings
    val bottomDockPadding = LocalGlassBottomPadding.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedSizeFilter by remember { mutableStateOf<String?>(null) }
    var selectedProductForSale by remember { mutableStateOf<ProductEntity?>(null) }
    var preselectedSizeForModal by remember { mutableStateOf<String?>(null) }
    var showCartSheet by remember { mutableStateOf(false) }
    var showReturnsSheet by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showVisualSourcePicker by remember { mutableStateOf(false) }
    var showVisualCameraDialog by remember { mutableStateOf(false) }
    var showBarcodeScannerDialog by remember { mutableStateOf(false) }
    var showBrandingSheet by remember { mutableStateOf(false) }

    val visualGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.findVisualMatchesFromUri(it) }
    }
    val visualFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.findVisualMatchesFromUri(it) }
    }

    val categories = remember(state.products) {
        val cats = state.products.filter { !it.isArchived }.map { it.category }.distinct().sorted()
        listOf("All") + cats
    }

    val availableSizes = remember(state.variants, state.products) {
        val activeIds = state.products.filter { !it.isArchived }.map { it.id }.toSet()
        state.variants
            .filter { it.productId in activeIds }
            .map { it.size }
            .distinct()
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
    }

    val variantStockMap = remember(state.variants) {
        state.variants.groupBy { it.productId }.mapValues { (_, list) -> list.associate { it.size to it.stock } }
    }

    val sortedProducts = remember(state.products, state.productMetrics, variantStockMap, searchQuery, selectedCategory, selectedSizeFilter) {
        val q = searchQuery.trim().lowercase()
        val qDigits = q.filter { it.isDigit() }
        val isNumericOnlyQuery = q.isNotEmpty() && q.all { it.isDigit() }

        state.products
            .filter { !it.isArchived }
            .filter { p ->
                val matchesCat = selectedCategory == "All" || p.category == selectedCategory
                val skuDigits = p.skuCode.filter { it.isDigit() }
                val matchesSearch = q.isEmpty() ||
                    p.name.lowercase().contains(q) ||
                    p.skuCode.lowercase().contains(q) ||
                    p.barcode.lowercase().contains(q) ||
                    (isNumericOnlyQuery && skuDigits.toIntOrNull() == qDigits.toIntOrNull()) ||
                    p.brand.lowercase().contains(q) ||
                    p.category.lowercase().contains(q) ||
                    p.colorTag.lowercase().contains(q) ||
                    p.styleTag.lowercase().contains(q) ||
                    p.rackLocation.lowercase().contains(q)
                val matchesSize = selectedSizeFilter == null || (variantStockMap[p.id]?.get(selectedSizeFilter) ?: 0) > 0
                matchesCat && matchesSearch && matchesSize
            }
            .sortedWith(
                compareByDescending<ProductEntity> {
                    val stock = if (selectedSizeFilter != null) {
                        variantStockMap[it.id]?.get(selectedSizeFilter) ?: 0
                    } else {
                        state.productMetrics[it.id]?.totalStock ?: 0
                    }
                    if (stock > 0) 1 else 0
                }.thenByDescending {
                    state.productMetrics[it.id]?.totalUnitsSold ?: 0
                }
            )
    }

    val fallbackSizeAlternatives = remember(sortedProducts, selectedSizeFilter, selectedCategory, state.products, state.variants) {
        val sz = selectedSizeFilter
        if (sortedProducts.isEmpty() && sz != null) {
            val catTarget = if (selectedCategory == "All") null else selectedCategory
            AnalyticsEngine.findSimilarInStock(
                targetProduct = null,
                desiredSize = sz,
                desiredCategory = catTarget,
                products = state.products,
                variants = state.variants,
                limit = 6
            ).ifEmpty {
                AnalyticsEngine.findSimilarInStock(null, sz, null, state.products, state.variants, limit = 6)
            }
        } else emptyList()
    }

    val todayStart = remember { Formatters.startOfDay() }
    val todaySales = remember(state.sales, todayStart) { state.sales.filter { it.timestamp >= todayStart } }
    val todayRevenue = remember(todaySales) { todaySales.sumOf { it.totalRevenue } }

    Box(modifier = Modifier.fillMaxSize().background(CanvasWarmWhite)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SoleBookTopBar(
                title = state.settings.shortHeaderName.ifBlank { state.settings.shopName.ifBlank { "SoleBook" } },
                subtitle = "Today: ${Formatters.formatCurrency(todayRevenue, currency)} (${todaySales.size} bills)",
                onHelpClick = { showHelpDialog = true },
                onSettingsClick = onOpenSettings,
                unreadNoticeCount = state.unreadNoticeCount,
                onNoticesClick = onOpenNotices,
                logoUri = state.settings.shopLogoUri,
                showLogoSlot = true,
                headerBrandMode = state.settings.headerBrandMode,
                fullShopNameForMonogram = state.settings.shopName,
                categoryBadge = industry.displayCategoryName,
                onLogoClick = { showBrandingSheet = true },
                extraAction = {
                    SecondaryButton(
                        text = "Returns",
                        onClick = { showReturnsSheet = true },
                        icon = Icons.Rounded.SwapHoriz,
                        accentColor = TextCharcoal,
                        testTag = "returns_history_button"
                    )
                }
            )

            Spacer(Modifier.height(10.dp))

            // Search bar + Visual Match Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search product, SKU, barcode, rack...",
                    testTag = "sell_search_input",
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    onClick = { showBarcodeScannerDialog = true },
                    color = SurfaceWhite,
                    contentColor = EmeraldDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DividerGrey),
                    modifier = Modifier.size(SoleBookSpacing.inputHeight).testTag("sell_barcode_scan_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = "Scan Barcode to Sell",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    onClick = { showVisualSourcePicker = true },
                    color = EmeraldSoft,
                    contentColor = EmeraldDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.size(SoleBookSpacing.inputHeight).testTag("visual_match_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = "Find Product by Photo",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(start = SoleBookSpacing.screenHorizontal, end = SoleBookSpacing.screenHorizontal, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val selected = cat == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = PureWhite,
                            containerColor = SurfaceWhite,
                            labelColor = TextCharcoal
                        )
                    )
                }
            }

            if (availableSizes.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(start = SoleBookSpacing.screenHorizontal, end = SoleBookSpacing.screenHorizontal, top = 2.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val isAny = selectedSizeFilter == null
                        FilterChip(
                            selected = isAny,
                            onClick = { selectedSizeFilter = null },
                            label = { Text("All ${industry.variantLabel}s") },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("size_filter_all"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldSoft,
                                selectedLabelColor = EmeraldDark,
                                containerColor = SurfaceWhite,
                                labelColor = TextCharcoal
                            )
                        )
                    }
                    items(availableSizes) { sz ->
                        val selected = selectedSizeFilter == sz
                        FilterChip(
                            selected = selected,
                            onClick = { selectedSizeFilter = if (selected) null else sz },
                            label = { Text(sz, fontWeight = FontWeight.Medium) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("size_filter_$sz"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldSoft,
                                selectedLabelColor = EmeraldDark,
                                containerColor = SurfaceWhite,
                                labelColor = TextCharcoal
                            )
                        )
                    }
                }
            }

            if (sortedProducts.isEmpty()) {
                if (selectedSizeFilter != null && state.products.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = SoleBookSpacing.screenHorizontal, end = SoleBookSpacing.screenHorizontal, top = 4.dp, bottom = 110.dp + bottomDockPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Surface(
                                color = SurfaceWhite,
                                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                                border = BorderStroke(1.dp, DividerGrey)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("No matching items in $selectedSizeFilter", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                                    Text(
                                        text = if (fallbackSizeAlternatives.isNotEmpty()) {
                                            "Similar in-stock items available in $selectedSizeFilter:"
                                        } else {
                                            "No items currently have $selectedSizeFilter in stock."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondaryGrey
                                    )
                                    SecondaryButton(
                                        text = "Reset Filters",
                                        onClick = { searchQuery = ""; selectedCategory = "All"; selectedSizeFilter = null },
                                        accentColor = TextCharcoal
                                    )
                                }
                            }
                        }
                        items(fallbackSizeAlternatives, key = { it.product.id }) { alt ->
                            ProductListItem(
                                product = alt.product,
                                totalStock = alt.availableStock,
                                currencySymbol = currency,
                                unitPlural = industry.unitPlural,
                                onClick = {
                                    preselectedSizeForModal = selectedSizeFilter
                                    selectedProductForSale = alt.product
                                }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = SoleBookSpacing.screenHorizontal, vertical = 12.dp)) {
                        EmptyState(
                            icon = Icons.Rounded.PointOfSale,
                            title = if (state.products.isEmpty()) "Ready to start selling" else "No matching products",
                            subtitle = if (state.products.isEmpty()) {
                                "Add your first product to start creating bills, or load sample data to explore."
                            } else {
                                "Try clearing your search or switching category."
                            },
                            primaryLabel = if (state.products.isEmpty()) "Add First Product" else "Show All Products",
                            onPrimaryClick = {
                                if (state.products.isEmpty()) onOpenAddProduct()
                                else {
                                    searchQuery = ""
                                    selectedCategory = "All"
                                    selectedSizeFilter = null
                                }
                            },
                            secondaryLabel = if (state.products.isEmpty()) "Load Sample Store Data" else null,
                            onSecondaryClick = if (state.products.isEmpty()) {
                                { viewModel.loadDemoData() }
                            } else null
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 162.dp),
                    contentPadding = PaddingValues(
                        start = SoleBookSpacing.screenHorizontal,
                        end = SoleBookSpacing.screenHorizontal,
                        top = 4.dp,
                        bottom = (if (state.cart.isNotEmpty() || state.undoBanner != null) 140.dp else 20.dp) + bottomDockPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedProducts, key = { it.id }) { product ->
                        val metrics = state.productMetrics[product.id]
                        val stock = metrics?.totalStock ?: 0
                        val sizeFilteredStock = selectedSizeFilter?.let { variantStockMap[product.id]?.get(it) ?: 0 }
                        val isOut = stock <= 0

                        Surface(
                            onClick = {
                                if (!isOut) {
                                    preselectedSizeForModal = selectedSizeFilter
                                    selectedProductForSale = product
                                }
                            },
                            enabled = !isOut,
                            shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, DividerGrey),
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isOut) 0.55f else 1f)
                                .testTag("sell_product_${product.skuCode}")
                        ) {
                            Column {
                                Box(modifier = Modifier.fillMaxWidth().height(116.dp)) {
                                    ProductPhoto(
                                        photoUri = product.photoUri,
                                        contentDescription = product.name,
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 12.dp
                                    )
                                    SkuBoxTag(
                                        skuCode = product.skuCode,
                                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                    )
                                    if (isOut) {
                                        Surface(
                                            color = SemanticError,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                        ) {
                                            Text(
                                                "OUT",
                                                color = PureWhite,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (metrics?.badges?.contains(ProductBadge.FAST_MOVER) == true) {
                                        Surface(
                                            color = SemanticSuccessSoft,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                        ) {
                                            Text(
                                                "Popular",
                                                color = SemanticSuccess,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = product.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextCharcoal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        minLines = 2
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Text(
                                            text = Formatters.formatCurrency(product.sellingPrice, currency),
                                            style = SoleBookTextStyles.FinancialCompact,
                                            color = EmeraldPrimary
                                        )
                                        if (sizeFilteredStock != null) {
                                            Text(
                                                text = "$selectedSizeFilter: $sizeFilteredStock",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (sizeFilteredStock <= 1) SemanticWarning else SemanticSuccess
                                            )
                                        } else {
                                            Text(
                                                text = "$stock left",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (stock <= product.reorderLevel) SemanticError else TextSecondaryGrey
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom overlays: Undo Banner + Cart Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = SoleBookSpacing.screenHorizontal, end = SoleBookSpacing.screenHorizontal, top = 10.dp, bottom = 10.dp + bottomDockPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = state.undoBanner != null) {
                state.undoBanner?.let { banner ->
                    Surface(
                        color = GlassDarkSurface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ProfitGreenMint)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Sold ${banner.itemCount} ${industry.unitPlural} • ${Formatters.formatCurrency(banner.totalAmount, currency)}",
                                color = PureWhite,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { viewModel.undoLastSale(banner.saleId) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("undo_sale_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, tint = ProfitGreenMint)
                                Spacer(Modifier.width(4.dp))
                                Text("UNDO", color = ProfitGreenMint, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { viewModel.dismissUndoBanner() }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = PureWhite.copy(alpha = 0.78f))
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = state.cart.isNotEmpty()) {
                val totalUnits = state.cart.sumOf { it.quantity }
                val totalAmt = state.cart.sumOf { it.unitSellingPrice * it.quantity }
                Button(
                    onClick = { showCartSheet = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open_cart_bill_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite)
                ) {
                    Icon(Icons.Rounded.ReceiptLong, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Bill ($totalUnits ${industry.unitPlural} • ${Formatters.formatCurrency(totalAmt, currency)})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Checkout →", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    selectedProductForSale?.let { prod ->
        val prodVariants = state.variants.filter { it.productId == prod.id }.sortedBy { it.size.toIntOrNull() ?: 99 }
        QuickSellModalSheet(
            product = prod,
            variants = prodVariants,
            customers = state.customers,
            allProducts = state.products,
            allVariants = state.variants,
            initialSize = preselectedSizeForModal ?: selectedSizeFilter,
            currency = currency,
            industryConfig = industry,
            strings = strings,
            onDismiss = { selectedProductForSale = null; preselectedSizeForModal = null },
            onSwitchProduct = { altProduct, preferredSz -> preselectedSizeForModal = preferredSz; selectedProductForSale = altProduct },
            onAddToBill = { size, qty, price ->
                val maxAv = prodVariants.firstOrNull { it.size == size }?.stock ?: 0
                viewModel.addToCart(prod, size, qty, price, maxAv)
                selectedProductForSale = null
                preselectedSizeForModal = null
            },
            onSoldNow = { size, qty, price, mode, discType, discVal, phone, name ->
                viewModel.completeQuickSingleSale(prod, size, qty, price, mode, discType, discVal, phone, name)
                selectedProductForSale = null
                preselectedSizeForModal = null
            }
        )
    }

    if (showVisualSourcePicker) {
        VisualMatchSourceDialog(
            onDismiss = { showVisualSourcePicker = false },
            onSnapCamera = { showVisualSourcePicker = false; showVisualCameraDialog = true },
            onPickGallery = {
                showVisualSourcePicker = false
                visualGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onBrowseFiles = { showVisualSourcePicker = false; visualFilesLauncher.launch(arrayOf("image/*")) }
        )
    }

    if (showVisualCameraDialog) {
        CameraCaptureDialog(
            title = "Match Product by Photo",
            subtitle = "4:3 sharp SDR • Tap item to focus",
            allowGalleryFallback = true,
            onDismiss = { showVisualCameraDialog = false },
            onPhotoReady = { file -> showVisualCameraDialog = false; viewModel.findVisualMatchesFromFile(file) },
            onGalleryPicked = { uri -> showVisualCameraDialog = false; viewModel.findVisualMatchesFromUri(uri) }
        )
    }

    if (showBarcodeScannerDialog) {
        BarcodeScannerDialog(
            title = "Scan Barcode to Sell",
            onDismiss = { showBarcodeScannerDialog = false },
            onBarcodeDetected = { code ->
                showBarcodeScannerDialog = false
                viewModel.findProductByBarcodeOrSku(code) { prod, matchedVar ->
                    if (prod != null) {
                        preselectedSizeForModal = matchedVar
                        selectedProductForSale = prod
                    }
                }
            }
        )
    }

    state.visualMatchState?.let { matchState ->
        VisualMatchesSheet(
            matchState = matchState,
            categories = categories,
            availableSizes = availableSizes,
            initialSizeFilter = selectedSizeFilter,
            allProducts = state.products,
            allVariants = state.variants,
            variantStockMap = variantStockMap,
            currency = currency,
            industryConfig = industry,
            onDismiss = { viewModel.clearVisualMatches() },
            onRetakePhoto = {
                viewModel.clearVisualMatches()
                showVisualSourcePicker = true
            },
            onSelectProduct = { prod, preferredSz ->
                viewModel.clearVisualMatches()
                preselectedSizeForModal = preferredSz
                selectedProductForSale = prod
            }
        )
    }

    if (showCartSheet) {
        CartCheckoutSheet(state = state, viewModel = viewModel, onDismiss = { showCartSheet = false })
    }

    if (showReturnsSheet) {
        RecentSalesAndReturnsSheet(state = state, viewModel = viewModel, onDismiss = { showReturnsSheet = false })
    }

    if (showHelpDialog) {
        HelpTipDialog(
            title = "Sell & Counter Billing",
            tips = listOf(
                "Tap any product card, pick ${industry.variantLabel.lowercase()} and quantity, and tap Complete Sale.",
                "Use Add to Bill to combine multiple items into one bill with optional discount and customer details.",
                "Tap the camera button next to search to visually match products offline, or tap Returns for refunds & exchanges."
            ),
            onDismiss = { showHelpDialog = false }
        )
    }

    if (showBrandingSheet) {
        ShopBrandingModalSheet(
            settings = state.settings,
            onDismiss = { showBrandingSheet = false },
            onPickLogoUri = { uri -> viewModel.saveShopLogoFromUri(uri) },
            onRemoveLogo = { viewModel.removeShopLogo() },
            onSaveBranding = { fullName, shortName, mode ->
                viewModel.saveShopBranding(fullName, shortName, mode)
                showBrandingSheet = false
            }
        )
    }
}
