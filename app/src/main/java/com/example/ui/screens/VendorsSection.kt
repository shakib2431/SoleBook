package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.VendorEntity
import com.example.domain.BuyingBucketSuggestion
import com.example.domain.IndustryConfig
import com.example.domain.IndustryProfile
import com.example.domain.ReorderGroup
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.ExportUtils
import com.example.util.Formatters

@Composable
fun VendorsSectionContent(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onOpenVendorDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig
    val bottomDockPadding = LocalGlassBottomPadding.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddVendorSheet by remember { mutableStateOf(false) }
    var briefTargetVendor by remember { mutableStateOf<VendorEntity?>(null) }
    var showBriefSheet by remember { mutableStateOf(false) }

    val filteredVendors = remember(state.vendors, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) state.vendors else {
            state.vendors.filter { v ->
                v.name.lowercase().contains(q) ||
                    v.cityMarket.lowercase().contains(q) ||
                    v.specialtiesCsv.lowercase().contains(q) ||
                    v.phone.contains(q)
            }
        }
    }

    val totalPayableDue = remember(state.vendors) { state.vendors.sumOf { it.creditBalance } }
    val purchasesByVendor = remember(state.purchases) { state.purchases.groupBy { it.vendorId } }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SoleBookSpacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FinancialMetric(
                label = "Total Suppliers",
                value = state.vendors.size.toString(),
                modifier = Modifier.weight(1f)
            )
            FinancialMetric(
                label = "Supplier Outstanding",
                value = Formatters.formatCurrency(totalPayableDue, currency),
                valueColor = if (totalPayableDue > 0.5) SemanticError else SemanticSuccess,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        SearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search supplier, market, or category...",
            testTag = "vendor_search_input",
            modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SoleBookSpacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryButton(
                text = "Add Supplier",
                onClick = { showAddVendorSheet = true },
                icon = Icons.Rounded.PersonAdd,
                accentColor = TextCharcoal,
                modifier = Modifier.weight(1f),
                testTag = "add_vendor_button"
            )
            PrimaryButton(
                text = "Order Brief",
                onClick = {
                    briefTargetVendor = state.vendors.firstOrNull()
                    showBriefSheet = true
                },
                icon = Icons.Rounded.Chat,
                modifier = Modifier.weight(1f),
                testTag = "open_whatsapp_brief_button"
            )
        }

        Spacer(Modifier.height(10.dp))

        if (filteredVendors.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                EmptyState(
                    icon = Icons.Rounded.LocalShipping,
                    title = if (state.vendors.isEmpty()) "No suppliers saved yet" else "No matching suppliers",
                    subtitle = if (state.vendors.isEmpty()) {
                        "Save your wholesalers and distributors to track purchases, balances, and WhatsApp order briefs."
                    } else {
                        "Try clearing your search or adding a new supplier."
                    },
                    primaryLabel = if (state.vendors.isEmpty()) "Add First Supplier" else "Reset Search",
                    onPrimaryClick = {
                        if (state.vendors.isEmpty()) showAddVendorSheet = true else searchQuery = ""
                    },
                    secondaryLabel = if (state.vendors.isEmpty()) "Load Sample Store Data" else null,
                    onSecondaryClick = if (state.vendors.isEmpty()) { { viewModel.loadDemoData() } } else null
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = SoleBookSpacing.screenHorizontal, end = SoleBookSpacing.screenHorizontal, bottom = 24.dp + bottomDockPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredVendors, key = { it.id }) { vendor ->
                    val lotsCount = purchasesByVendor[vendor.id]?.size ?: 0
                    VendorListCard(
                        vendor = vendor,
                        lotsCount = lotsCount,
                        currency = currency,
                        onClick = { onOpenVendorDetail(vendor.id) },
                        onCallClick = {
                            if (vendor.phone.isNotBlank()) ExportUtils.dialPhone(context, vendor.phone)
                        },
                        onWhatsAppClick = {
                            briefTargetVendor = vendor
                            showBriefSheet = true
                        }
                    )
                }
            }
        }
    }

    if (showAddVendorSheet) {
        AddEditVendorSheet(
            initialVendor = null,
            industryConfig = industry,
            onDismiss = { showAddVendorSheet = false },
            onSave = { newVendor ->
                viewModel.saveVendor(newVendor)
                showAddVendorSheet = false
            }
        )
    }

    if (showBriefSheet) {
        WhatsAppBuyingBriefSheet(
            state = state,
            viewModel = viewModel,
            initialVendor = briefTargetVendor,
            preselectedBuckets = state.insightsReport?.buyingBuckets.orEmpty(),
            onDismiss = { showBriefSheet = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VendorListCard(
    vendor: VendorEntity,
    lotsCount: Int,
    currency: String,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    onWhatsAppClick: () -> Unit
) {
    val specialties = vendor.specialtiesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey),
        modifier = Modifier.fillMaxWidth().testTag("vendor_card_${vendor.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(vendor.name, style = MaterialTheme.typography.titleMedium, color = TextCharcoal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (vendor.cityMarket.isNotBlank()) {
                        Text(vendor.cityMarket, style = MaterialTheme.typography.labelMedium, color = TextSecondaryGrey)
                    }
                }
                Surface(
                    color = if (vendor.creditBalance > 0.5) SemanticErrorSoft else SemanticSuccessSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (vendor.creditBalance > 0.5) {
                            "Due ${Formatters.formatCurrency(vendor.creditBalance, currency)}"
                        } else "Settled",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (vendor.creditBalance > 0.5) SemanticError else SemanticSuccess,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (specialties.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    specialties.take(4).forEach { sp ->
                        Surface(color = SurfaceSubtle, shape = RoundedCornerShape(6.dp)) {
                            Text(sp, style = MaterialTheme.typography.labelSmall, color = TextCharcoal, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastStr = vendor.lastPurchaseAt?.let { "Last bill ${Formatters.formatShortDate(it)}" } ?: "No purchases yet"
                Text("$lotsCount bill(s) • $lastStr", style = MaterialTheme.typography.labelSmall, color = TextMutedGrey, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (vendor.phone.isNotBlank()) {
                        FilledTonalIconButton(
                            onClick = onCallClick,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SurfaceSubtle)
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Call ${vendor.name}", tint = TextCharcoal)
                        }
                    }
                    FilledTonalIconButton(
                        onClick = onWhatsAppClick,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = EmeraldSoft)
                    ) {
                        Icon(Icons.Rounded.Chat, contentDescription = "WhatsApp brief to ${vendor.name}", tint = EmeraldPrimary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditVendorSheet(
    initialVendor: VendorEntity?,
    industryConfig: IndustryConfig = IndustryProfile.resolve("GENERAL"),
    onDismiss: () -> Unit,
    onSave: (VendorEntity) -> Unit
) {
    val allCategories = industryConfig.suggestedCategories
    var name by remember { mutableStateOf(initialVendor?.name ?: "") }
    var phone by remember { mutableStateOf(initialVendor?.phone ?: "") }
    var whatsapp by remember { mutableStateOf(initialVendor?.whatsapp ?: "") }
    var cityMarket by remember { mutableStateOf(initialVendor?.cityMarket ?: "") }
    var notes by remember { mutableStateOf(initialVendor?.notes ?: "") }
    val selectedSpecialties = remember {
        mutableStateListOf<String>().apply {
            initialVendor?.specialtiesCsv
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.forEach { add(it) }
        }
    }

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
            Text(
                if (initialVendor == null) "Add Supplier" else "Edit Supplier",
                style = MaterialTheme.typography.headlineMedium,
                color = TextCharcoal
            )
            FormField(value = name, onValueChange = { name = it }, label = "Supplier / Distributor Name *", testTag = "vendor_name_input")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(13) },
                    label = "Phone",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    testTag = "vendor_phone_input",
                    modifier = Modifier.weight(1f)
                )
                FormField(
                    value = whatsapp,
                    onValueChange = { whatsapp = it.filter { ch -> ch.isDigit() || ch == '+' }.take(13) },
                    label = "WhatsApp (if diff)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f)
                )
            }
            FormField(value = cityMarket, onValueChange = { cityMarket = it }, label = "City / Market (optional)")
            Text("Categories Supplied", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allCategories.forEach { cat ->
                    val sel = selectedSpecialties.any { it.equals(cat, ignoreCase = true) }
                    FilterChip(
                        selected = sel,
                        onClick = {
                            if (sel) selectedSpecialties.removeAll { it.equals(cat, ignoreCase = true) }
                            else selectedSpecialties.add(cat)
                        },
                        label = { Text(cat) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            FormField(value = notes, onValueChange = { notes = it }, label = "Payment terms / notes (optional)")
            PrimaryButton(
                text = "Save Supplier",
                onClick = {
                    onSave(
                        VendorEntity(
                            id = initialVendor?.id ?: 0L,
                            name = name.trim(),
                            phone = phone.trim(),
                            whatsapp = whatsapp.trim().ifBlank { phone.trim() },
                            cityMarket = cityMarket.trim(),
                            specialtiesCsv = selectedSpecialties.joinToString(","),
                            creditBalance = initialVendor?.creditBalance ?: 0.0,
                            notes = notes.trim(),
                            lastPurchaseAt = initialVendor?.lastPurchaseAt,
                            createdAt = initialVendor?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                testTag = "save_vendor_button"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WhatsAppBuyingBriefSheet(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    initialVendor: VendorEntity?,
    preselectedBuckets: List<BuyingBucketSuggestion>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val unitPlural = state.industryConfig.unitPlural
    var selectedVendor by remember(initialVendor) { mutableStateOf(initialVendor) }
    val allBuckets = state.insightsReport?.buyingBuckets.orEmpty()
    val repeatItems = state.insightsReport?.reorderSuggestions
        .orEmpty()
        .filter { it.product.isRepeatable && (it.group == ReorderGroup.BUY_MORE || it.group == ReorderGroup.BUY_SAME) && it.recommendedQty > 0 }

    val selectedBucketKeys = remember {
        mutableStateMapOf<String, Boolean>().apply {
            val preKeys = preselectedBuckets.map { it.bucketKey }.toSet()
            allBuckets.forEach { b ->
                val matchesVendor = selectedVendor?.let { v ->
                    v.specialtiesCsv.split(",").any { it.trim().equals(b.category, ignoreCase = true) }
                } ?: true
                put(b.bucketKey, if (preKeys.isNotEmpty()) b.bucketKey in preKeys && matchesVendor else matchesVendor)
            }
        }
    }
    var includeRepeatStaples by remember { mutableStateOf(repeatItems.isNotEmpty()) }

    val activeBuckets = allBuckets.filter { selectedBucketKeys[it.bucketKey] == true }
    val activeRepeats = if (includeRepeatStaples) repeatItems else emptyList()
    val briefText = remember(selectedVendor, activeBuckets, activeRepeats) {
        viewModel.buildWhatsAppBuyingBrief(selectedVendor, activeBuckets, activeRepeats)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceWhite) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("WhatsApp Order Brief", style = MaterialTheme.typography.headlineMedium, color = TextCharcoal)
            Text(
                "Send your supplier a clean purchase inquiry matching what is selling well.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryGrey
            )

            if (state.vendors.isNotEmpty()) {
                Text("Select Supplier", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedVendor == null,
                        onClick = { selectedVendor = null },
                        label = { Text("Any / Share") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    state.vendors.forEach { v ->
                        FilterChip(
                            selected = selectedVendor?.id == v.id,
                            onClick = {
                                selectedVendor = v
                                allBuckets.forEach { b ->
                                    selectedBucketKeys[b.bucketKey] = v.specialtiesCsv.split(",").any { it.trim().equals(b.category, ignoreCase = true) }
                                }
                            },
                            label = { Text(v.name) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            if (allBuckets.isNotEmpty()) {
                Text("Categories Needed", style = MaterialTheme.typography.titleSmall)
                allBuckets.forEach { bucket ->
                    val checked = selectedBucketKeys[bucket.bucketKey] == true
                    Surface(
                        color = SurfaceSubtle,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (checked) EmeraldPrimary else DividerGrey),
                        modifier = Modifier.fillMaxWidth().clickable { selectedBucketKeys[bucket.bucketKey] = !checked }
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { selectedBucketKeys[bucket.bucketKey] = it })
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${bucket.category} • ${bucket.priceBandLabel}", style = MaterialTheme.typography.titleSmall)
                                Text("~${bucket.recommendedPairs} $unitPlural • Cost ${bucket.costRangeLabel}", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                            }
                        }
                    }
                }
            }

            if (repeatItems.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeRepeatStaples, onCheckedChange = { includeRepeatStaples = it })
                    Text(
                        "Include ${repeatItems.size} regular restock item(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { includeRepeatStaples = !includeRepeatStaples }
                    )
                }
            }

            Surface(
                color = SurfaceSubtle,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DividerGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(briefText, style = MaterialTheme.typography.bodySmall, color = TextCharcoal, modifier = Modifier.padding(14.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val targetPhone = selectedVendor?.whatsapp?.ifBlank { selectedVendor?.phone.orEmpty() }.orEmpty()
                if (targetPhone.isNotBlank()) {
                    PrimaryButton(
                        text = "Send WhatsApp",
                        onClick = { ExportUtils.openWhatsApp(context, targetPhone, briefText); onDismiss() },
                        icon = Icons.Rounded.Chat,
                        modifier = Modifier.weight(1f)
                    )
                }
                SecondaryButton(
                    text = "Share Text",
                    onClick = { ExportUtils.shareText(context, "Purchase Brief", briefText); onDismiss() },
                    icon = Icons.Rounded.Share,
                    accentColor = TextCharcoal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
