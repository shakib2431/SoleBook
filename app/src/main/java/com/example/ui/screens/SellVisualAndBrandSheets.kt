package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ProductEntity
import com.example.data.local.ProductVariantEntity
import com.example.data.local.ShopSettingsEntity
import com.example.domain.AnalyticsEngine
import com.example.domain.IndustryConfig
import com.example.domain.VisualMatchResult
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.VisualMatchUiState
import com.example.util.Formatters

@Composable
fun VisualMatchSourceDialog(
    onDismiss: () -> Unit,
    onSnapCamera: () -> Unit,
    onPickGallery: () -> Unit,
    onBrowseFiles: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = EmeraldPrimary)
                Spacer(Modifier.width(10.dp))
                Text("Find Product by Photo", style = MaterialTheme.typography.headlineSmall, color = TextCharcoal)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Works 100% offline. Snap the item or pick a photo to match against your inventory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryGrey
                )
                PrimaryButton(
                    text = "Snap Item Photo",
                    onClick = onSnapCamera,
                    icon = Icons.Rounded.PhotoCamera,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "visual_match_camera_option"
                )
                SecondaryButton(
                    text = "Pick Photo / Screenshot",
                    onClick = onPickGallery,
                    icon = Icons.Rounded.PhotoLibrary,
                    accentColor = TextCharcoal,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "visual_match_gallery_option"
                )
                SecondaryButton(
                    text = "Browse Files / Downloads",
                    onClick = onBrowseFiles,
                    icon = Icons.Rounded.FolderOpen,
                    accentColor = TextSecondaryGrey,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "visual_match_files_option"
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", color = TextSecondaryGrey)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopBrandingModalSheet(
    settings: ShopSettingsEntity,
    onDismiss: () -> Unit,
    onPickLogoUri: (Uri) -> Unit,
    onRemoveLogo: () -> Unit,
    onSaveBranding: (fullName: String, shortName: String, mode: String) -> Unit
) {
    var draftShopName by remember(settings.shopName) { mutableStateOf(settings.shopName) }
    var draftShortName by remember(settings.shortHeaderName) { mutableStateOf(settings.shortHeaderName) }
    var draftBrandMode by remember(settings.headerBrandMode) { mutableStateOf(settings.headerBrandMode) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        contentColor = TextCharcoal
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Storefront, contentDescription = null, tint = EmeraldPrimary)
                    Text("Business Logo & Name", style = MaterialTheme.typography.headlineSmall, color = TextCharcoal)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextSecondaryGrey)
                }
            }

            ShopBrandingEditorContent(
                shopName = draftShopName,
                onShopNameChange = { draftShopName = it },
                shortHeaderName = draftShortName,
                onShortHeaderNameChange = { draftShortName = it },
                headerBrandMode = draftBrandMode,
                onHeaderBrandModeChange = { draftBrandMode = it },
                shopLogoUri = settings.shopLogoUri,
                onPickLogoUri = onPickLogoUri,
                onRemoveLogo = onRemoveLogo
            )

            PrimaryButton(
                text = "Save Branding",
                onClick = { onSaveBranding(draftShopName, draftShortName, draftBrandMode) },
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth(),
                testTag = "save_branding_sheet_button"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VisualMatchesSheet(
    matchState: VisualMatchUiState,
    categories: List<String>,
    availableSizes: List<String>,
    initialSizeFilter: String?,
    allProducts: List<ProductEntity>,
    allVariants: List<ProductVariantEntity>,
    variantStockMap: Map<Long, Map<String, Int>>,
    currency: String,
    industryConfig: IndustryConfig,
    onDismiss: () -> Unit,
    onRetakePhoto: () -> Unit = {},
    onSelectProduct: (ProductEntity, String?) -> Unit
) {
    var sheetCategory by remember { mutableStateOf("All") }
    var sheetSize by remember(initialSizeFilter) { mutableStateOf(initialSizeFilter) }
    var showFallbackGuesses by remember(matchState.queryPhotoUri) { mutableStateOf(false) }

    val variantsByProduct = remember(allVariants) {
        allVariants
            .groupBy { it.productId }
            .mapValues { (_, list) ->
                list.sortedWith(compareBy({ it.size.toIntOrNull() ?: Int.MAX_VALUE }, { it.size }))
            }
    }

    val filteredMatches = remember(matchState.matches, sheetCategory) {
        matchState.matches.filter { sheetCategory == "All" || it.product.category == sheetCategory }
    }

    val matchedCategories = remember(matchState.matches, categories) {
        val catsInMatches = matchState.matches.map { it.product.category }.toSet()
        listOf("All") + categories.filter { it != "All" && it in catsInMatches }
    }

    val matchedAvailableSizes = remember(filteredMatches, variantsByProduct, availableSizes) {
        val sizesInMatches = filteredMatches
            .flatMap { m -> variantsByProduct[m.product.id].orEmpty().map { it.size } }
            .toSet()
        if (sizesInMatches.isNotEmpty()) {
            availableSizes.filter { it in sizesInMatches }
        } else {
            availableSizes
        }
    }

    val topMatch = filteredMatches.firstOrNull()
    val sizeOutAlternatives = remember(topMatch, sheetSize, sheetCategory, allProducts, allVariants, variantStockMap) {
        val sz = sheetSize
        if (sz != null && filteredMatches.isNotEmpty()) {
            val topHaveStock = filteredMatches.any { (variantStockMap[it.product.id]?.get(sz) ?: 0) > 0 }
            if (!topHaveStock) {
                AnalyticsEngine.findSimilarInStock(
                    targetProduct = topMatch?.product,
                    desiredSize = sz,
                    desiredCategory = topMatch?.product?.category ?: if (sheetCategory == "All") null else sheetCategory,
                    products = allProducts,
                    variants = allVariants,
                    limit = 4
                )
            } else emptyList()
        } else emptyList()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DividerGrey) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!matchState.queryPhotoUri.isNullOrBlank()) {
                    ProductPhoto(
                        photoUri = matchState.queryPhotoUri,
                        contentDescription = "Query photo",
                        modifier = Modifier.size(60.dp),
                        cornerRadius = 12.dp
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Visual Match", style = MaterialTheme.typography.headlineSmall, color = TextCharcoal)
                    val summaryText = when {
                        matchState.isScanning -> "Analyzing color, shape & pattern..."
                        matchState.matches.isNotEmpty() -> {
                            val count = matchState.matches.size
                            "$count matching ${if (count == 1) "product" else "products"} • ${matchState.detectedColor}"
                        }
                        matchState.indexedProductCount == 0 -> "No catalog photos indexed yet"
                        else -> "No matching product • ${matchState.detectedColor}"
                    }
                    Surface(
                        color = if (matchState.matches.isNotEmpty()) EmeraldSoft else CanvasWarmWhite,
                        shape = RoundedCornerShape(8.dp),
                        border = if (matchState.matches.isEmpty()) BorderStroke(1.dp, DividerGrey) else null
                    ) {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (matchState.matches.isNotEmpty()) EmeraldDark else TextSecondaryGrey,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (!matchState.isScanning) {
                    TextButton(
                        onClick = onRetakePhoto,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("visual_match_retake_button")
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close visual matches")
                }
            }

            if (matchState.isScanning) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = EmeraldPrimary)
                    Spacer(Modifier.height(14.dp))
                    Text("Matching precisely against your shop catalog...", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)
                }
            } else {
                if (matchedCategories.size > 2) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(matchedCategories) { cat ->
                            FilterChip(
                                selected = cat == sheetCategory,
                                onClick = { sheetCategory = cat },
                                label = { Text(cat) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = PureWhite,
                                    containerColor = CanvasWarmWhite,
                                    labelColor = TextCharcoal
                                )
                            )
                        }
                    }
                }

                if (filteredMatches.isNotEmpty() && matchedAvailableSizes.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = sheetSize == null,
                                onClick = { sheetSize = null },
                                label = { Text("All ${industryConfig.variantLabel}s") },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldSoft,
                                    selectedLabelColor = EmeraldDark,
                                    containerColor = CanvasWarmWhite,
                                    labelColor = TextCharcoal
                                )
                            )
                        }
                        items(matchedAvailableSizes) { sz ->
                            val sel = sheetSize == sz
                            FilterChip(
                                selected = sel,
                                onClick = { sheetSize = if (sel) null else sz },
                                label = { Text(sz, fontWeight = FontWeight.Medium) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldSoft,
                                    selectedLabelColor = EmeraldDark,
                                    containerColor = CanvasWarmWhite,
                                    labelColor = TextCharcoal
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false).testTag("visual_matches_list")
                ) {
                    if (filteredMatches.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                                color = CanvasWarmWhite,
                                border = BorderStroke(1.dp, DividerGrey),
                                modifier = Modifier.fillMaxWidth().testTag("visual_match_empty_state")
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Rounded.ImageSearch, contentDescription = null, tint = TextSecondaryGrey)
                                        Text(
                                            text = when {
                                                matchState.matches.isNotEmpty() -> "No matches in $sheetCategory"
                                                matchState.indexedProductCount == 0 -> "Add product photos first"
                                                else -> "No matching product found"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextCharcoal,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = when {
                                            matchState.matches.isNotEmpty() ->
                                                "Your matched product is in another category. Switch back to All to see it."
                                            matchState.indexedProductCount == 0 ->
                                                "None of your active products have saved photos yet. Add photos when creating products in Stock to enable visual matching."
                                            else ->
                                                "This photo did not match any of your ${matchState.indexedProductCount} photographed products closely enough. Center the item in good lighting and try again."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondaryGrey
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (matchState.matches.isNotEmpty()) {
                                            PrimaryButton(
                                                text = "Show All Matches",
                                                onClick = { sheetCategory = "All" },
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            PrimaryButton(
                                                text = "Retake Photo",
                                                onClick = onRetakePhoto,
                                                icon = Icons.Rounded.PhotoCamera,
                                                modifier = Modifier.weight(1f),
                                                testTag = "visual_empty_retake_button"
                                            )
                                            SecondaryButton(
                                                text = "Browse Catalog",
                                                onClick = onDismiss,
                                                accentColor = TextCharcoal,
                                                modifier = Modifier.weight(1f),
                                                testTag = "visual_empty_browse_button"
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (matchState.matches.isEmpty() && matchState.fallbackGuesses.isNotEmpty()) {
                            item {
                                TextButton(
                                    onClick = { showFallbackGuesses = !showFallbackGuesses },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = if (showFallbackGuesses) {
                                            "Hide low-confidence guesses"
                                        } else {
                                            "Show ${matchState.fallbackGuesses.size} closest low-confidence guess(es)"
                                        },
                                        color = TextSecondaryGrey
                                    )
                                }
                            }
                            if (showFallbackGuesses) {
                                items(matchState.fallbackGuesses, key = { "guess_${it.product.id}" }) { guess ->
                                    VisualMatchResultCard(
                                        match = guess,
                                        variants = variantsByProduct[guess.product.id].orEmpty(),
                                        selectedSize = sheetSize,
                                        currency = currency,
                                        industryConfig = industryConfig,
                                        isLowConfidenceGuess = true,
                                        onSelectSize = { sz -> onSelectProduct(guess.product, sz) },
                                        onActionClick = { onSelectProduct(guess.product, sheetSize) }
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredMatches, key = { it.product.id }) { match ->
                            VisualMatchResultCard(
                                match = match,
                                variants = variantsByProduct[match.product.id].orEmpty(),
                                selectedSize = sheetSize,
                                currency = currency,
                                industryConfig = industryConfig,
                                onSelectSize = { sz -> onSelectProduct(match.product, sz) },
                                onActionClick = { onSelectProduct(match.product, sheetSize) }
                            )
                        }
                    }

                    if (sizeOutAlternatives.isNotEmpty() && sheetSize != null) {
                        item {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Similar available in ${industryConfig.variantLabel.lowercase()} $sheetSize",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldDark
                            )
                        }
                        items(sizeOutAlternatives, key = { "alt_${it.product.id}" }) { alt ->
                            ProductListItem(
                                product = alt.product,
                                totalStock = alt.availableStock,
                                currencySymbol = currency,
                                unitPlural = industryConfig.unitPlural,
                                onClick = { onSelectProduct(alt.product, sheetSize) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisualMatchResultCard(
    match: VisualMatchResult,
    variants: List<ProductVariantEntity>,
    selectedSize: String?,
    currency: String,
    industryConfig: IndustryConfig,
    isLowConfidenceGuess: Boolean = false,
    onSelectSize: (String) -> Unit,
    onActionClick: () -> Unit
) {
    val p = match.product
    val totalStock = variants.sumOf { it.stock }
    val selectedSizeStock = selectedSize?.let { sz -> variants.firstOrNull { it.size == sz }?.stock ?: 0 }
    val hasStockForChoice = if (selectedSize != null) (selectedSizeStock ?: 0) > 0 else totalStock > 0
    val inStockVariants = variants.filter { it.stock > 0 }
    val cardBorderColor = when {
        match.isBestMatch -> EmeraldPrimary.copy(alpha = 0.55f)
        else -> DividerGrey
    }

    Surface(
        onClick = onActionClick,
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        color = SurfaceWhite,
        border = BorderStroke(if (match.isBestMatch) 1.5.dp else 1.dp, cardBorderColor),
        modifier = Modifier.fillMaxWidth().testTag("visual_match_card_${p.skuCode}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductPhoto(photoUri = p.photoUri, contentDescription = p.name, modifier = Modifier.size(68.dp), cornerRadius = 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (match.isBestMatch) {
                            Surface(color = EmeraldPrimary, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "BEST MATCH",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Surface(
                            color = when {
                                isLowConfidenceGuess -> SemanticWarningSoft
                                match.similarityPercent >= 74 -> SemanticSuccessSoft
                                else -> EmeraldSoft
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isLowConfidenceGuess) {
                                    "Low (${match.similarityPercent}%)"
                                } else {
                                    "${match.similarityPercent}% Match"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isLowConfidenceGuess -> SemanticWarning
                                    match.similarityPercent >= 74 -> SemanticSuccess
                                    else -> EmeraldDark
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        SkuBoxTag(skuCode = p.skuCode)
                    }

                    Text(
                        text = p.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextCharcoal,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val metaLine = buildString {
                        if (p.brand.isNotBlank()) append("${p.brand} • ")
                        append(Formatters.formatCurrency(p.sellingPrice, currency))
                        if (p.rackLocation.isNotBlank()) append(" • ${p.rackLocation}")
                    }
                    Text(metaLine, style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    Text(
                        text = when {
                            selectedSize != null && (selectedSizeStock ?: 0) > 0 ->
                                "${industryConfig.variantLabel} $selectedSize: $selectedSizeStock ${industryConfig.unitPlural} in stock"
                            selectedSize != null ->
                                "${industryConfig.variantLabel} $selectedSize out of stock ($totalStock total left)"
                            inStockVariants.isNotEmpty() ->
                                "$totalStock ${industryConfig.unitPlural} available across ${inStockVariants.size} ${industryConfig.variantLabel.lowercase()}${if (inStockVariants.size == 1) "" else "s"}"
                            else -> "Out of stock"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasStockForChoice) SemanticSuccess else SemanticError
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (hasStockForChoice) {
                    PrimaryButton(
                        text = "Sell",
                        onClick = onActionClick,
                        testTag = "visual_match_sell_${p.skuCode}"
                    )
                } else {
                    SecondaryButton(
                        text = "View",
                        onClick = onActionClick,
                        accentColor = TextCharcoal
                    )
                }
            }

            if (variants.isNotEmpty()) {
                HorizontalDivider(color = DividerGrey.copy(alpha = 0.7f))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Available ${industryConfig.variantLabel}s (tap to sell):",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryGrey,
                        fontWeight = FontWeight.Medium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        variants.forEach { variant ->
                            val isSelected = selectedSize == variant.size
                            val inStock = variant.stock > 0
                            if (inStock) {
                                Surface(
                                    onClick = { onSelectSize(variant.size) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) EmeraldPrimary else EmeraldSoft,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) EmeraldPrimary else EmeraldPrimary.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("visual_size_chip_${p.skuCode}_${variant.size}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "${variant.size} (${variant.stock})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) PureWhite else EmeraldDark
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CanvasWarmWhite,
                                    border = BorderStroke(1.dp, DividerGrey),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "${variant.size} (Out)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextMutedGrey
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
}

