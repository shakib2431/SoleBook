package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.ExportUtils
import com.example.util.Formatters
import kotlin.math.roundToInt

private enum class InsightsSection(val label: String) {
    OVERVIEW("Plan & Gaps"),
    REORDER("Buying Plan"),
    FORECAST("Forecasts"),
    STOCK_HEALTH("Dead & Pricing"),
    INTELLIGENCE("ABC & Metrics")
}

@Composable
fun InsightsScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onOpenProductDetail: (Long) -> Unit,
    onOpenVendorDetail: (Long) -> Unit = {},
    onSourceFreshLot: (String, String, Long?) -> Unit = { _, _, _ -> },
    onOpenSettings: () -> Unit,
    onOpenNotices: () -> Unit = {}
) {
    val context = LocalContext.current
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig
    val report = state.insightsReport
    val bottomDockPadding = LocalGlassBottomPadding.current
    var activeSection by remember { mutableStateOf(InsightsSection.OVERVIEW) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showBriefSheet by remember { mutableStateOf(false) }

    val checkedBucketKeys = remember { mutableStateMapOf<String, Boolean>() }
    val checkedReorderIds = remember { mutableStateMapOf<Long, Boolean>() }
    LaunchedEffect(report?.buyingBuckets, report?.reorderSuggestions) {
        report?.buyingBuckets?.forEach { b ->
            if (b.bucketKey !in checkedBucketKeys) {
                checkedBucketKeys[b.bucketKey] = b.recommendedPairs > 0
            }
        }
        report?.reorderSuggestions?.filter { it.product.isRepeatable }?.forEach { sug ->
            if (sug.product.id !in checkedReorderIds) {
                checkedReorderIds[sug.product.id] = (sug.group == ReorderGroup.BUY_MORE || sug.group == ReorderGroup.BUY_SAME) && sug.recommendedQty > 0
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CanvasWarmWhite),
        contentPadding = PaddingValues(bottom = 24.dp + bottomDockPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SoleBookTopBar(
                title = "Shop Insights",
                subtitle = report?.confidence?.label ?: "Demand & stock intelligence",
                onHelpClick = { showHelpDialog = true },
                onSettingsClick = onOpenSettings,
                unreadNoticeCount = state.unreadNoticeCount,
                onNoticesClick = onOpenNotices
            )
        }

        if (report == null || state.products.isEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                    EmptyState(
                        icon = Icons.Rounded.Insights,
                        title = "Not enough data for insights yet",
                        subtitle = "Add products and record sales to see demand forecasting, ${industry.variantLabel.lowercase()} gaps, and ABC profit analysis.",
                        primaryLabel = "Load Sample Store Data",
                        onPrimaryClick = { viewModel.loadDemoData() }
                    )
                }
            }
            return@LazyColumn
        }

        // Confidence + Festival Uplift Switcher
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Season / Festival Uplift", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                        val (bg, fg) = when (report.confidence) {
                            ConfidenceLevel.HIGH -> SemanticSuccessSoft to SemanticSuccess
                            ConfidenceLevel.MEDIUM -> SemanticWarningSoft to SemanticWarning
                            ConfidenceLevel.LOW -> SemanticErrorSoft to SemanticError
                        }
                        Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = report.confidence.name + " CONF.",
                                color = fg,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SeasonalityPreset.entries) { preset ->
                            val selected = state.settings.seasonalityMode == preset.key
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.updateSettings(
                                        state.settings.copy(
                                            seasonalityMode = preset.key,
                                            seasonalityUpliftPercent = preset.defaultUpliftPct
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        if (preset.defaultUpliftPct > 0) "${preset.label} (+${preset.defaultUpliftPct.roundToInt()}%)"
                                        else preset.label
                                    )
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldSoft,
                                    selectedLabelColor = EmeraldDark,
                                    containerColor = SurfaceWhite
                                )
                            )
                        }
                    }
                }
            }
        }

        // Section Switcher Pills
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = SoleBookSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(InsightsSection.entries) { sec ->
                    val sel = activeSection == sec
                    FilterChip(
                        selected = sel,
                        onClick = { activeSection = sec },
                        label = { Text(sec.label, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("insights_tab_${sec.name}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = PureWhite,
                            containerColor = SurfaceWhite,
                            labelColor = TextCharcoal
                        )
                    )
                }
            }
        }

        when (activeSection) {
            InsightsSection.OVERVIEW -> {
                item {
                    SectionHeader(
                        title = "Recommended actions this week",
                        modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                    )
                }
                if (report.weeklyActions.isEmpty()) {
                    item {
                        Text(
                            "Stock and margins look balanced right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SemanticSuccess,
                            modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                        )
                    }
                } else {
                    items(report.weeklyActions) { act ->
                        Box(modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                            WeeklyActionCard(act = act)
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Supply-Demand Gap (${industry.variantLabel})",
                        subtitle = "Next 30 days",
                        modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                    )
                }
                if (report.criticalSizeGaps.isEmpty()) {
                    item {
                        Text(
                            "No ${industry.variantLabel.lowercase()} shortages detected for the next 30 days.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SemanticSuccess,
                            modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                        )
                    }
                } else {
                    items(report.criticalSizeGaps) { gap ->
                        Box(modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                            VariantGapCard(
                                gap = gap,
                                variantLabel = industry.variantLabel,
                                onClick = { onOpenProductDetail(gap.productId) }
                            )
                        }
                    }
                }
            }

            InsightsSection.REORDER -> {
                val repeatSuggestions = report.reorderSuggestions.filter { it.product.isRepeatable }
                val selectedBuckets = report.buyingBuckets.filter { checkedBucketKeys[it.bucketKey] == true && it.recommendedPairs > 0 }
                val selectedRepeats = repeatSuggestions.filter { checkedReorderIds[it.product.id] == true && it.recommendedQty > 0 }
                val totalBudget = selectedBuckets.sumOf { it.estimatedBudget } + selectedRepeats.sumOf { it.estimatedCost }
                val expectedProfit = selectedBuckets.sumOf { it.expectedProfit } + selectedRepeats.sumOf { it.expectedProfit }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerGrey)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Selected Buying Plan Summary", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Est. Buying Budget", style = MaterialTheme.typography.labelMedium, color = TextSecondaryGrey)
                                    Text(Formatters.formatCurrency(totalBudget, currency), style = SoleBookTextStyles.FinancialPrimary, color = TextCharcoal)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Expected Profit", style = MaterialTheme.typography.labelMedium, color = TextSecondaryGrey)
                                    Text(Formatters.formatCurrency(expectedProfit, currency), style = SoleBookTextStyles.FinancialPrimary, color = SemanticSuccess)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton(
                                    text = "WhatsApp",
                                    onClick = { showBriefSheet = true },
                                    enabled = selectedBuckets.isNotEmpty() || selectedRepeats.isNotEmpty(),
                                    icon = Icons.Rounded.Chat,
                                    modifier = Modifier.weight(1f)
                                )
                                SecondaryButton(
                                    text = "Share",
                                    onClick = {
                                        val txt = viewModel.buildWhatsAppBuyingBrief(null, selectedBuckets, selectedRepeats)
                                        ExportUtils.shareText(context, "Purchase Buying Plan", txt)
                                    },
                                    enabled = selectedBuckets.isNotEmpty() || selectedRepeats.isNotEmpty(),
                                    icon = Icons.Rounded.Share,
                                    modifier = Modifier.weight(0.9f)
                                )
                                SecondaryButton(
                                    text = "PDF",
                                    onClick = {
                                        val lines = selectedBuckets.map { b ->
                                            val sizes = b.sizeSplit.entries.joinToString(" ") { "${it.key}:${it.value}" }
                                            "LOT | ${b.category} (${b.priceBandLabel}) | ~${b.recommendedPairs} ($sizes) | Cost ${Formatters.formatCurrency(b.estimatedBudget, currency)}"
                                        } + selectedRepeats.map { s ->
                                            val sizes = s.sizeOrderSplit.entries.joinToString(" ") { "${it.key}:${it.value}" }
                                            "STAPLE | ${s.product.skuCode} ${s.product.name} | Order ${s.recommendedQty} ($sizes) | Cost ${Formatters.formatCurrency(s.estimatedCost, currency)}"
                                        }
                                        val pdf = ExportUtils.generateReorderPdf(
                                            context = context,
                                            shopName = state.settings.shopName.ifBlank { "My Shop" },
                                            currency = currency,
                                            lines = lines,
                                            totalBudget = totalBudget,
                                            expectedProfit = expectedProfit
                                        )
                                        ExportUtils.shareFile(context, pdf, "application/pdf", "Share Buying Plan PDF")
                                    },
                                    enabled = selectedBuckets.isNotEmpty() || selectedRepeats.isNotEmpty(),
                                    icon = Icons.Rounded.PictureAsPdf,
                                    accentColor = TextCharcoal,
                                    modifier = Modifier.weight(0.8f)
                                )
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Fresh Lots to Source (${report.buyingBuckets.size})",
                        subtitle = "Grouped by category + price band + ${industry.variantLabel.lowercase()} split",
                        modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                    )
                }
                if (report.buyingBuckets.isEmpty()) {
                    item {
                        Text(
                            "All category and price buckets are covered for the next 2+ weeks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SemanticSuccess,
                            modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                        )
                    }
                } else {
                    items(report.buyingBuckets, key = { "bkt_${it.bucketKey}" }) { bucket ->
                        Box(modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                            BuyingBucketCard(
                                bucket = bucket,
                                unitPlural = industry.unitPlural,
                                variantLabel = industry.variantLabel,
                                isChecked = checkedBucketKeys[bucket.bucketKey] == true,
                                onCheckedChange = { checkedBucketKeys[bucket.bucketKey] = it },
                                onOpenVendorDetail = onOpenVendorDetail,
                                onSourceFreshLot = {
                                    onSourceFreshLot(bucket.category, bucket.gender, bucket.matchingVendors.firstOrNull()?.id)
                                }
                            )
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Repeat Staples (${repeatSuggestions.size})",
                        subtitle = "Exact SKUs marked as repeatable",
                        modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                    )
                }
                ReorderGroup.entries.forEach { group ->
                    val groupItems = repeatSuggestions.filter { it.group == group }
                    if (groupItems.isNotEmpty()) {
                        item {
                            Text(
                                "${group.title} (${groupItems.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = when (group) {
                                    ReorderGroup.BUY_MORE -> EmeraldPrimary
                                    ReorderGroup.BUY_SAME -> TextCharcoal
                                    ReorderGroup.BUY_LESS -> SemanticWarning
                                    ReorderGroup.DONT_REORDER -> TextMutedGrey
                                },
                                modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                            )
                        }
                        items(groupItems, key = { "ro_${it.product.id}" }) { item ->
                            Box(modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                                RepeatReorderCard(
                                    item = item,
                                    variantLabel = industry.variantLabel,
                                    isChecked = checkedReorderIds[item.product.id] ?: false,
                                    onCheckedChange = { checkedReorderIds[item.product.id] = it },
                                    onClick = { onOpenProductDetail(item.product.id) }
                                )
                            }
                        }
                    }
                }
            }

            InsightsSection.FORECAST -> {
                item {
                    SectionHeader(
                        title = "Category Demand (Next 7 / 30 Days)",
                        modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                    )
                }
                items(report.categoryForecasts) { cf ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerGrey)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(cf.category, style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                                Text("Current stock: ${cf.currentStock} ${industry.unitPlural}", style = MaterialTheme.typography.labelSmall, color = TextSecondaryGrey)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("7d: ${cf.forecast7d} • 30d: ${cf.forecast30d}", style = SoleBookTextStyles.FinancialInline, color = TextCharcoal)
                                val gap = cf.forecast30d - cf.currentStock
                                Text(
                                    if (gap > 0) "Short by $gap ${industry.unitPlural}" else "Covered",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (gap > 0) SemanticError else SemanticSuccess
                                )
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Product Forecasts & Reasoning",
                        modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                    )
                }
                items(report.productForecasts, key = { "pf_${it.productId}" }) { pf ->
                    Surface(
                        onClick = { onOpenProductDetail(pf.productId) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerGrey)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${pf.sku} • ${pf.name}", style = MaterialTheme.typography.titleSmall, color = TextCharcoal, modifier = Modifier.weight(1f))
                                Text("7d: ${pf.forecast7d} | 30d: ${pf.forecast30d}", style = SoleBookTextStyles.FinancialInline, color = EmeraldPrimary)
                            }
                            Text(
                                "Stock: ${pf.currentStock} • 30d Gap: ${if (pf.netGap30d > 0) "Short ${pf.netGap30d}" else "OK"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (pf.netGap30d > 0) SemanticError else SemanticSuccess
                            )
                            Text("Why: ${pf.whyExplanation}", style = MaterialTheme.typography.bodySmall, color = TextSecondaryGrey)
                        }
                    }
                }
            }

            InsightsSection.STOCK_HEALTH -> {
                item {
                    SectionHeader(
                        title = "Pricing Suggestions",
                        modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                    )
                }
                if (report.pricingSuggestions.isEmpty()) {
                    item {
                        Text(
                            "All current product margins align well with demand.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryGrey,
                            modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                        )
                    }
                } else {
                    items(report.pricingSuggestions) { ps ->
                        Surface(
                            onClick = { onOpenProductDetail(ps.product.id) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                            shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                            color = if (ps.isIncrease) SemanticSuccessSoft else SemanticWarningSoft,
                            border = BorderStroke(1.dp, DividerGrey)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(ps.headline, style = MaterialTheme.typography.titleSmall, color = if (ps.isIncrease) SemanticSuccess else SemanticWarning)
                                Text("Why: ${ps.reason}", style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Dead & Slow Stock (Cash Locked)",
                        modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                    )
                }
                if (report.deadSlowActions.isEmpty()) {
                    item {
                        Text(
                            "No dead or slow-moving stock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SemanticSuccess,
                            modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                        )
                    }
                } else {
                    items(report.deadSlowActions) { ds ->
                        Surface(
                            onClick = { onOpenProductDetail(ds.product.id) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                            shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, DividerGrey)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${ds.product.skuCode} • ${ds.product.name}", style = MaterialTheme.typography.titleSmall, color = TextCharcoal, modifier = Modifier.weight(1f))
                                    Text(Formatters.formatCurrency(ds.capitalLocked, currency), style = SoleBookTextStyles.FinancialInline, color = SemanticError)
                                }
                                Text(
                                    "${ds.stockUnits} ${industry.unitPlural} locked • ${ds.daysSinceLastSale?.let { "$it days since last sale" } ?: "Never sold"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryGrey
                                )
                                Text("Action: ${ds.suggestedAction}", style = MaterialTheme.typography.bodySmall, color = TextCharcoal)
                            }
                        }
                    }
                }
            }

            InsightsSection.INTELLIGENCE -> {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeader(title = "Inventory Health")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FinancialMetric(
                                label = "Sell-Through (60d)",
                                value = "${report.sellThroughRatePct.roundToInt()}%",
                                subtitle = "Higher = faster turnover",
                                valueColor = EmeraldPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            FinancialMetric(
                                label = "Stock Turnover",
                                value = "${String.format("%.1f", report.stockTurnoverAnnual)}x / yr",
                                subtitle = "${report.daysOfInventory.roundToInt()} days of stock",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        FinancialMetric(
                            label = "GMROI (Gross Margin Return on Inventory)",
                            value = "${String.format("%.2f", report.gmroi)}x",
                            subtitle = "Gross profit return per ${currency.trim()}100 invested in stock",
                            valueColor = if (report.gmroi >= 1.5) SemanticSuccess else EmeraldPrimary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SoleBookSpacing.screenHorizontal),
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerGrey)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${industry.variantLabel} & Day Intelligence (60d)", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                            Text(
                                "Top ${industry.variantLabel}s: " + report.bestSizes.take(5).joinToString(" • ") { "${it.first} (${it.second})" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextCharcoal
                            )
                            Text(
                                "Busiest Days: " + report.bestDaysOfWeek.take(4).joinToString(" • ") { "${it.first} (${it.second})" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondaryGrey
                            )
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "ABC Profit Analysis (80 / 15 / 5 Rule)",
                        modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)
                    )
                }
                items(report.abcItems, key = { "abc_${it.product.id}" }) { abc ->
                    Box(modifier = Modifier.padding(horizontal = SoleBookSpacing.screenHorizontal)) {
                        AbcProfitRow(abc = abc, currency = currency, onClick = { onOpenProductDetail(abc.product.id) })
                    }
                }
            }
        }
    }

    if (showBriefSheet && report != null) {
        val selectedBuckets = report.buyingBuckets.filter { checkedBucketKeys[it.bucketKey] == true && it.recommendedPairs > 0 }
        WhatsAppBuyingBriefSheet(
            state = state,
            viewModel = viewModel,
            initialVendor = state.vendors.firstOrNull(),
            preselectedBuckets = selectedBuckets.ifEmpty { report.buyingBuckets },
            onDismiss = { showBriefSheet = false }
        )
    }

    if (showHelpDialog) {
        HelpTipDialog(
            title = "Shop Insights Tips",
            tips = listOf(
                "Forecasts use your recent 4-week sales weighted toward the latest week, weekend rush, and selected season uplift.",
                "Buying Plan groups non-repeating items by category, price band, and ${industry.variantLabel.lowercase()} split.",
                "Use WhatsApp or Export PDF to send exact restock quantities to your wholesalers."
            ),
            onDismiss = { showHelpDialog = false }
        )
    }
}
