package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.*
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SoleBookSpacing
import com.example.ui.theme.*
import com.example.util.Formatters

@Composable
internal fun WeeklyActionCard(act: WeeklyAction) {
    Surface(
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(color = EmeraldSoft, shape = RoundedCornerShape(6.dp)) {
                Text(
                    act.priorityTag,
                    color = EmeraldDark,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Text(act.title, style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
            Text(act.detail, style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)
        }
    }
}

@Composable
internal fun VariantGapCard(
    gap: SizeGap,
    variantLabel: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = SemanticErrorSoft,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.widthIn(min = 54.dp).height(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        gap.size,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SemanticError
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("${gap.sku} • ${gap.productName}", style = MaterialTheme.typography.titleSmall, color = TextCharcoal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$variantLabel ${gap.size}: demand ${gap.forecastDemand30d}, stock ${gap.currentStock}, short by ${gap.shortage}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryGrey
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BuyingBucketCard(
    bucket: BuyingBucketSuggestion,
    unitPlural: String,
    variantLabel: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenVendorDetail: (Long) -> Unit,
    onSourceFreshLot: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        color = SurfaceWhite,
        border = BorderStroke(if (isChecked) 1.5.dp else 1.dp, if (isChecked) EmeraldPrimary else DividerGrey),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = isChecked, onCheckedChange = onCheckedChange)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${bucket.category} • ${bucket.priceBandLabel}",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextCharcoal,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "~${bucket.recommendedPairs} $unitPlural",
                            style = SoleBookTextStyles.FinancialInline,
                            color = EmeraldPrimary
                        )
                    }
                    Text(
                        "Target Cost: ${bucket.costRangeLabel} • Sell: ${bucket.sellRangeLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondaryGrey
                    )
                    Text(
                        "$variantLabel Split: " + bucket.sizeSplit.entries.joinToString(", ") { "${it.key}:${it.value}" },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextCharcoal
                    )
                    Text(bucket.reason, style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
                }
            }

            if (bucket.matchingVendors.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bucket.matchingVendors.take(3).forEach { v ->
                        AssistChip(
                            onClick = { onOpenVendorDetail(v.id) },
                            label = { Text(v.name, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = { Icon(Icons.Rounded.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            SecondaryButton(
                text = "Log Fresh ${bucket.category} Lot",
                onClick = onSourceFreshLot,
                icon = Icons.Rounded.AddBox,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun RepeatReorderCard(
    item: ReorderSuggestion,
    variantLabel: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            if (item.recommendedQty > 0) {
                Checkbox(checked = isChecked, onCheckedChange = onCheckedChange)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.product.skuCode} • ${item.product.name}", style = MaterialTheme.typography.titleSmall, color = TextCharcoal, modifier = Modifier.weight(1f))
                    if (item.recommendedQty > 0) {
                        Text("Order ${item.recommendedQty}", style = SoleBookTextStyles.FinancialInline, color = EmeraldPrimary)
                    }
                }
                Text(
                    "Stock: ${item.currentStock} • Reorder Pt: ${item.reorderPoint} • Safety: ${item.safetyStock}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryGrey
                )
                if (item.sizeOrderSplit.isNotEmpty()) {
                    Text(
                        "$variantLabel Split: " + item.sizeOrderSplit.entries.joinToString(", ") { "${it.key}: ${it.value}" },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextCharcoal
                    )
                }
                Text("Why: ${item.reason}", style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
            }
        }
    }
}

@Composable
internal fun AbcProfitRow(
    abc: AbcItem,
    currency: String,
    onClick: () -> Unit
) {
    val (bg, fg) = when (abc.abcClass) {
        AbcClass.A -> SemanticSuccessSoft to SemanticSuccess
        AbcClass.B -> EmeraldSoft to EmeraldDark
        AbcClass.C -> SurfaceSubtle to TextSecondaryGrey
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = bg, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        abc.abcClass.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("${abc.product.skuCode} • ${abc.product.name}", style = MaterialTheme.typography.titleSmall, color = TextCharcoal)
                Text(
                    if (abc.abcClass == AbcClass.A) "Core profit driver • Stock ${abc.currentStock}"
                    else "Stock ${abc.currentStock} • ${abc.profitSharePct.roundToIntSafe()}% of profit",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (abc.abcClass == AbcClass.A && abc.currentStock <= abc.product.reorderLevel) SemanticError else TextSecondaryGrey
                )
            }
            Text(
                Formatters.formatCurrency(abc.profitEarned, currency),
                style = SoleBookTextStyles.FinancialInline,
                color = TextCharcoal
            )
        }
    }
}

private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt()
