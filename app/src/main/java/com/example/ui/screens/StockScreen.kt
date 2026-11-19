package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.domain.ProductBadge
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.Formatters

data class FreshLotPrefill(
    val category: String? = null,
    val gender: String? = null,
    val vendorId: Long? = null
)

@Composable
fun StockScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    openAddImmediately: Boolean = false,
    openLowStockImmediately: Boolean = false,
    freshLotPrefill: FreshLotPrefill? = null,
    onConsumedOpenAdd: () -> Unit = {},
    onOpenProductDetail: (Long) -> Unit,
    onOpenVendorDetail: (Long) -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenNotices: () -> Unit = {},
    onOpenDataHub: (String?) -> Unit = {}
) {
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig
    val bottomDockPadding = LocalGlassBottomPadding.current
    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Inventory, 1 = Suppliers
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showAddEditSheet by remember { mutableStateOf(false) }
    var activePrefill by remember { mutableStateOf<FreshLotPrefill?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(openAddImmediately, openLowStockImmediately, freshLotPrefill) {
        if (openLowStockImmediately) {
            activeSubTab = 0
            selectedFilter = "Low Stock"
            onConsumedOpenAdd()
        } else if (openAddImmediately || freshLotPrefill != null) {
            activePrefill = freshLotPrefill
            activeSubTab = 0
            showAddEditSheet = true
            onConsumedOpenAdd()
        }
    }

    val filterOptions = listOf(
        "All", "In Stock", "Low Stock", "Out of Stock", "Fast Mover", "Dead Stock", "Archived"
    )

    val filteredProducts = remember(state.products, state.productMetrics, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase()
        val qDigits = q.filter { it.isDigit() }
        val isNumericOnlyQuery = q.isNotEmpty() && q.all { it.isDigit() }
        state.products
            .filter { p ->
                val metrics = state.productMetrics[p.id]
                val badges = metrics?.badges.orEmpty()
                val stock = metrics?.totalStock ?: 0
                val matchesFilter = when (selectedFilter) {
                    "Archived" -> p.isArchived
                    "In Stock" -> !p.isArchived && stock > 0
                    "Low Stock" -> !p.isArchived && ProductBadge.LOW_STOCK in badges
                    "Out of Stock" -> !p.isArchived && ProductBadge.OUT_OF_STOCK in badges
                    "Fast Mover" -> !p.isArchived && ProductBadge.FAST_MOVER in badges
                    "Dead Stock" -> !p.isArchived && ProductBadge.DEAD_STOCK in badges
                    else -> !p.isArchived
                }
                val skuDigits = p.skuCode.filter { it.isDigit() }
                val matchesSearch = q.isEmpty() || p.name.lowercase().contains(q) ||
                    p.skuCode.lowercase().contains(q) ||
                    p.barcode.lowercase().contains(q) ||
                    (isNumericOnlyQuery && skuDigits.toIntOrNull() == qDigits.toIntOrNull()) ||
                    p.brand.lowercase().contains(q) ||
                    p.category.lowercase().contains(q) ||
                    p.colorTag.lowercase().contains(q) ||
                    p.styleTag.lowercase().contains(q) ||
                    p.supplier.lowercase().contains(q) ||
                    p.rackLocation.lowercase().contains(q)
                matchesFilter && matchesSearch
            }
    }

    val activeMetrics = remember(state.productMetrics) {
        state.productMetrics.values.filter { !it.product.isArchived }
    }
    val totalUnits = activeMetrics.sumOf { it.totalStock }
    val totalCostValue = activeMetrics.sumOf { it.stockCostValue }
    val lowOrOutCount = activeMetrics.count {
        ProductBadge.LOW_STOCK in it.badges || ProductBadge.OUT_OF_STOCK in it.badges
    }

    Scaffold(
        containerColor = CanvasWarmWhite,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (activeSubTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        activePrefill = null
                        showAddEditSheet = true
                    },
                    containerColor = EmeraldPrimary,
                    contentColor = PureWhite,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(bottom = bottomDockPadding)
                        .heightIn(min = 54.dp)
                        .testTag("add_product_fab")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Product", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            SoleBookTopBar(
                title = "Inventory",
                subtitle = "$totalUnits ${industry.unitPlural} • Cost ${Formatters.formatCurrency(totalCostValue, currency)}",
                onHelpClick = { showHelpDialog = true },
                onSettingsClick = onOpenSettings,
                unreadNoticeCount = state.unreadNoticeCount,
                onNoticesClick = onOpenNotices
            )

            // Clean 2-Segment Toggle: Products | Suppliers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeSubTab == 0,
                    onClick = { activeSubTab = 0 },
                    leadingIcon = {
                        Icon(Icons.Rounded.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    label = { Text("Products (${activeMetrics.size})", maxLines = 1) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("stock_subtab_boxes"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldSoft,
                        selectedLabelColor = EmeraldDark,
                        selectedLeadingIconColor = EmeraldPrimary,
                        containerColor = SurfaceWhite
                    )
                )
                FilterChip(
                    selected = activeSubTab == 1,
                    onClick = { activeSubTab = 1 },
                    leadingIcon = {
                        Icon(Icons.Rounded.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    label = { Text("Suppliers (${state.vendors.size})", maxLines = 1) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("stock_subtab_vendors"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldSoft,
                        selectedLabelColor = EmeraldDark,
                        selectedLeadingIconColor = EmeraldPrimary,
                        containerColor = SurfaceWhite
                    )
                )
            }

            if (activeSubTab == 1) {
                VendorsSectionContent(
                    state = state,
                    viewModel = viewModel,
                    onOpenVendorDetail = onOpenVendorDetail,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SoleBookSpacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FinancialMetric(
                        label = "Stock Quantity",
                        value = "${Formatters.formatIndianNumber(totalUnits.toLong())} ${industry.unitPlural}",
                        modifier = Modifier.weight(1f)
                    )
                    FinancialMetric(
                        label = "Low / Out Alerts",
                        value = "$lowOrOutCount items",
                        valueColor = if (lowOrOutCount > 0) SemanticWarning else SemanticSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))

                SearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search product, SKU, barcode, rack...",
                    testTag = "stock_search_input",
                    modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = SoleBookSpacing.screenHorizontal, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        AssistChip(
                            onClick = { onOpenDataHub("SPREADSHEET") },
                            label = { Text("Excel / CSV") },
                            leadingIcon = { Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldPrimary) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("stock_quick_import_btn")
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onOpenDataHub("BARCODE") },
                            label = { Text("Barcode Add") },
                            leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldPrimary) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("stock_quick_barcode_btn")
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onOpenDataHub("DOCS") },
                            label = { Text("Scan Bill / Slip") },
                            leadingIcon = { Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldPrimary) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("stock_quick_docs_btn")
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onOpenDataHub("STOCK_COUNT") },
                            label = { Text("Stock Count") },
                            leadingIcon = { Icon(Icons.Rounded.Inventory, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldPrimary) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("stock_quick_count_btn")
                        )
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = SoleBookSpacing.screenHorizontal, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterOptions) { opt ->
                        val sel = opt == selectedFilter
                        FilterChip(
                            selected = sel,
                            onClick = { selectedFilter = opt },
                            label = { Text(opt, style = MaterialTheme.typography.labelLarge) },
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

                if (filteredProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                        EmptyState(
                            icon = Icons.Rounded.Inventory2,
                            title = if (state.products.isEmpty()) "No products in stock yet" else "No items in '$selectedFilter'",
                            subtitle = if (state.products.isEmpty()) {
                                "Add your first product with cost, selling price, and opening stock to begin tracking."
                            } else {
                                "Switch filter back to 'All' or clear your search query."
                            },
                            primaryLabel = if (state.products.isEmpty()) "Add First Product" else "Reset Filter",
                            onPrimaryClick = {
                                if (state.products.isEmpty()) showAddEditSheet = true
                                else {
                                    selectedFilter = "All"
                                    searchQuery = ""
                                }
                            },
                            secondaryLabel = if (state.products.isEmpty()) "Load Sample Store Data" else null,
                            onSecondaryClick = if (state.products.isEmpty()) {
                                { viewModel.loadDemoData() }
                            } else null
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = SoleBookSpacing.screenHorizontal,
                            end = SoleBookSpacing.screenHorizontal,
                            bottom = 92.dp + bottomDockPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { prod ->
                            val metrics = state.productMetrics[prod.id]
                            val stock = metrics?.totalStock ?: 0
                            val margin = prod.sellingPrice - prod.costPrice

                            ProductListItem(
                                product = prod,
                                totalStock = stock,
                                currencySymbol = currency,
                                unitPlural = industry.unitPlural,
                                onClick = { onOpenProductDetail(prod.id) },
                                modifier = Modifier.testTag("stock_item_${prod.skuCode}"),
                                trailingAction = {
                                    Text(
                                        text = "Margin ${Formatters.formatCurrency(margin, currency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (margin >= 0) SemanticSuccess else SemanticError,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddEditSheet) {
        val nextSku = remember(state.products, industry.defaultSkuPrefix) {
            Formatters.generateNextSku(state.products.map { it.skuCode }, industry.defaultSkuPrefix)
        }
        val starterVariants = remember(industry) {
            mapOf((industry.defaultVariants.firstOrNull() ?: "Standard") to 10)
        }
        AddEditProductSheet(
            initialProduct = null,
            initialSizes = starterVariants,
            autoSku = nextSku,
            currency = currency,
            industryConfig = industry,
            vendors = state.vendors,
            initialVendorId = activePrefill?.vendorId,
            initialCategory = activePrefill?.category,
            initialGender = activePrefill?.gender,
            showLotPaymentFields = true,
            onDismiss = {
                showAddEditSheet = false
                activePrefill = null
            },
            onSave = { prod, sizes ->
                viewModel.saveProduct(prod, sizes)
                showAddEditSheet = false
                activePrefill = null
            },
            onSaveWithPayment = { prod, sizes, paidAmt, lotNotes ->
                viewModel.saveFreshLotFromVendor(prod, sizes, paidAmt, lotNotes)
                showAddEditSheet = false
                activePrefill = null
            }
        )
    }

    if (showHelpDialog) {
        HelpTipDialog(
            title = "Inventory & Suppliers",
            tips = listOf(
                "Add products with cost price, selling price, and current stock to see exact profit and inventory value.",
                "Tap any product to restock with weighted-average cost, edit prices, view sales history, or promote on WhatsApp.",
                "Use the Suppliers tab to track purchase bills, dues payable, and share WhatsApp reorder lists."
            ),
            onDismiss = { showHelpDialog = false }
        )
    }
}
