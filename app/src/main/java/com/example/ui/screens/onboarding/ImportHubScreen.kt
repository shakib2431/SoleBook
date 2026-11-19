package com.example.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataHubUiState
import com.example.ui.viewmodel.SoleBookUiState
import com.example.util.ExportUtils
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportHubScreen(
    state: SoleBookUiState,
    hubState: DataHubUiState,
    controller: com.example.ui.viewmodel.OnboardingHubController,
    initialRouteSheet: String? = null,
    onBack: () -> Unit,
    onOpenBatchReview: (Long) -> Unit,
    onOpenStockCountSession: (Long) -> Unit,
    onOpenProductDetail: (Long) -> Unit
) {
    val context = LocalContext.current
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig

    var activeSheet by remember(initialRouteSheet) { mutableStateOf(initialRouteSheet) } // SPREADSHEET, IMAGES, BARCODE, MANUAL, STOCK_COUNT, DOCS
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Routes & History, 1 = Purchase Requests, 2 = Locations & Ledger
    var showAddLocationDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = CanvasWarmWhite,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Data Hub & AI Docs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${state.products.size} catalog items • ${hubState.locations.size} locations",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Segmented Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("Import Hub", maxLines = 1) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("hub_tab_import")
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = {
                        val openPr = hubState.purchaseOrders.count { it.status != "RECEIVED" }
                        Text(if (openPr > 0) "Requests ($openPr)" else "Requests", maxLines = 1)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("hub_tab_po")
                )
                FilterChip(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    label = { Text("Counts & Loc", maxLines = 1) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("hub_tab_counts")
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("data_hub_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeTab) {
                    0 -> {
                        item {
                            Text(
                                "ONBOARDING & CAPTURE ROUTES",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMutedGrey,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HubActionTile(
                                        icon = Icons.Rounded.TableChart,
                                        title = "Excel & CSV",
                                        subtitle = "10 to 10,000+ rows, templates & mapping",
                                        onClick = { activeSheet = "SPREADSHEET" },
                                        testTag = "hub_route_spreadsheet",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HubActionTile(
                                        icon = Icons.Rounded.DocumentScanner,
                                        title = "Paper / PDF Scan",
                                        subtitle = "Bills, slips, registers (EN/HI/BN)",
                                        onClick = { activeSheet = "DOCS" },
                                        testTag = "hub_route_docs",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HubActionTile(
                                        icon = Icons.Rounded.QrCodeScanner,
                                        title = "Barcode Scan",
                                        subtitle = "Instant match, restock or create",
                                        onClick = { activeSheet = "BARCODE" },
                                        testTag = "hub_route_barcode",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HubActionTile(
                                        icon = Icons.Rounded.FolderZip,
                                        title = "Bulk Images ZIP",
                                        subtitle = "Match by SKU/CSV, compress & dedup",
                                        onClick = { activeSheet = "IMAGES" },
                                        testTag = "hub_route_images",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HubActionTile(
                                        icon = Icons.Rounded.GridOn,
                                        title = "Bulk Manual",
                                        subtitle = "Fast multi-row entry & paste",
                                        onClick = { activeSheet = "MANUAL" },
                                        testTag = "hub_route_manual",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HubActionTile(
                                        icon = Icons.Rounded.Inventory,
                                        title = "Stock Count",
                                        subtitle = "Barcode shelf audit & reconcile",
                                        onClick = { activeSheet = "STOCK_COUNT" },
                                        testTag = "hub_route_stock_count",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "IMPORT & STAGING HISTORY",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMutedGrey,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (hubState.importBatches.isEmpty()) {
                            item {
                                Surface(
                                    color = SurfaceWhite,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, DividerGrey),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No staged batches yet. Choose any route above to stage items safely for review before committing.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondaryGrey,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(hubState.importBatches, key = { it.id }) { batch ->
                                Surface(
                                    color = SurfaceWhite,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, DividerGrey),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                text = "Batch #${batch.id} • ${batch.fileName}",
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                color = when (batch.status) {
                                                    "COMPLETED" -> SemanticSuccessSoft
                                                    "PARTIAL", "STAGED" -> SemanticWarningSoft
                                                    else -> SemanticErrorSoft
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = batch.status,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${batch.sourceRoute} • ${batch.processedItems}/${batch.totalItems} committed • ${batch.invalidItems} errors",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondaryGrey
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { onOpenBatchReview(batch.id) },
                                                modifier = Modifier.heightIn(min = 48.dp)
                                            ) {
                                                Text("Review")
                                            }
                                            if (batch.status == "PARTIAL" || batch.status == "FAILED") {
                                                Button(
                                                    onClick = { controller.commitBatch(batch.id, autoApproveValid = true) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                                                    modifier = Modifier.heightIn(min = 48.dp)
                                                ) {
                                                    Text("Resume")
                                                }
                                            }
                                            if (batch.invalidItems > 0) {
                                                TextButton(
                                                    onClick = {
                                                        controller.exportBatchErrorCsv(batch.id) { f ->
                                                            ExportUtils.shareFile(context, f, "text/csv", "Batch Errors")
                                                        }
                                                    },
                                                    modifier = Modifier.heightIn(min = 48.dp)
                                                ) {
                                                    Text("Error CSV", color = SemanticError)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        item {
                            Surface(
                                color = SemanticWarningSoft,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SemanticWarning.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Replenishment Requests & Purchase Orders do NOT increase live stock until you explicitly confirm Goods Received (GRN).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SemanticWarning,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        if (hubState.purchaseOrders.isEmpty()) {
                            item {
                                Surface(
                                    color = SurfaceWhite,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, DividerGrey),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No purchase requests or orders staged yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondaryGrey,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(hubState.purchaseOrders, key = { it.id }) { po ->
                                Surface(
                                    color = SurfaceWhite,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, DividerGrey),
                                    modifier = Modifier.fillMaxWidth().testTag("po_card_${po.id}")
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${po.poNumber} • ${po.supplierName.ifBlank { "General Supplier" }}", style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                po.status,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (po.status == "RECEIVED") SemanticSuccess else SemanticWarning
                                            )
                                        }
                                        Text(
                                            "Est. Total: ${Formatters.formatCurrency(po.estimatedTotal, currency)} • ${po.notes}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondaryGrey
                                        )
                                        if (po.status != "RECEIVED") {
                                            Button(
                                                onClick = { controller.confirmPurchaseOrderReceived(po.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite),
                                                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_grn_btn_${po.id}")
                                            ) {
                                                Icon(Icons.Rounded.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Confirm Goods Received")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("PHYSICAL STOCK COUNTS", style = MaterialTheme.typography.labelSmall, color = TextMutedGrey, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { activeSheet = "STOCK_COUNT" }) { Text("+ New Count") }
                            }
                        }
                        items(hubState.stockCountSessions, key = { it.id }) { sess ->
                            Surface(
                                onClick = { onOpenStockCountSession(sess.id) },
                                color = SurfaceWhite,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, DividerGrey),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(sess.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${sess.location.ifBlank { "All Locations" }} • ${sess.status}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondaryGrey
                                        )
                                    }
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("INVENTORY LOCATIONS", style = MaterialTheme.typography.labelSmall, color = TextMutedGrey, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { showAddLocationDialog = true }) { Text("+ Add Location") }
                            }
                        }
                        items(hubState.locations, key = { it.id }) { loc ->
                            Surface(
                                color = SurfaceWhite,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, DividerGrey),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${loc.name} (${loc.code})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(loc.locationType, style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(6.dp))
                            Text("RECENT AUDIT MOVEMENTS (${hubState.recentMovements.size})", style = MaterialTheme.typography.labelSmall, color = TextMutedGrey, fontWeight = FontWeight.SemiBold)
                        }
                        items(hubState.recentMovements.take(25), key = { it.id }) { mv ->
                            Surface(
                                color = SurfaceWhite,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, DividerGrey),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${mv.movementType} (${mv.sizeLabel}) • ${mv.sourceRoute}", style = MaterialTheme.typography.labelLarge)
                                        Text(mv.notes.ifBlank { mv.movementKey }, style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                                    }
                                    Text(
                                        text = if (mv.qtyDelta >= 0) "+${mv.qtyDelta}" else "${mv.qtyDelta}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (mv.qtyDelta >= 0) SemanticSuccess else SemanticError
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (activeSheet) {
        "SPREADSHEET" -> SpreadsheetImportSheet(
            hubState = hubState,
            controller = controller,
            defaultCategory = industry.suggestedCategories.firstOrNull() ?: "General",
            defaultUnit = industry.unitSingular,
            onDismiss = { activeSheet = null },
            onOpenBatchReview = onOpenBatchReview
        )
        "IMAGES" -> BulkImageImportSheet(
            hubState = hubState,
            products = state.products,
            controller = controller,
            onDismiss = { activeSheet = null }
        )
        "BARCODE" -> BarcodeOnboardingSheet(
            hubState = hubState,
            existingSkus = state.products.map { it.skuCode },
            currency = currency,
            industryConfig = industry,
            controller = controller,
            onDismiss = { activeSheet = null },
            onOpenProductDetail = onOpenProductDetail
        )
        "MANUAL" -> BulkManualEntrySheet(
            defaultCategory = industry.suggestedCategories.firstOrNull() ?: "General",
            defaultUnit = industry.unitSingular,
            currency = currency,
            controller = controller,
            onDismiss = { activeSheet = null },
            onOpenBatchReview = onOpenBatchReview
        )
        "STOCK_COUNT" -> CreateStockCountSheet(
            locations = hubState.locations,
            categories = state.products.map { it.category }.distinct().sorted(),
            controller = controller,
            onDismiss = { activeSheet = null },
            onSessionCreated = onOpenStockCountSession
        )
        "DOCS" -> DocumentIntelligenceSheet(
            hubState = hubState,
            catalogProducts = state.products,
            currency = currency,
            controller = controller,
            onDismiss = { activeSheet = null },
            onOpenBatchReview = onOpenBatchReview
        )
    }

    if (showAddLocationDialog) {
        var code by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("WAREHOUSE") }
        AlertDialog(
            onDismissRequest = { showAddLocationDialog = false },
            containerColor = SurfaceWhite,
            title = { Text("Add Inventory Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code (e.g. GODOWN-1)") }, singleLine = true)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("STORE", "WAREHOUSE", "RACK").forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            controller.addInventoryLocation(code, name, type)
                            showAddLocationDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = PureWhite)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddLocationDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HubActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DividerGrey),
        modifier = modifier.heightIn(min = 108.dp).testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(EmeraldSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextCharcoal)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey, maxLines = 2)
        }
    }
}
