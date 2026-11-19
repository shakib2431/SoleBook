package com.example.domain

import com.example.data.local.*
import com.example.util.Formatters
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class NoticeCategory(val label: String, val shortTag: String) {
    OUT_OF_STOCK("Out of Stock", "OUT"),
    LOW_STOCK("Low Stock", "LOW"),
    HIGH_DEMAND("High Demand", "HOT"),
    PRICE_MARGIN("Price & Margin", "MARGIN"),
    DEAD_STOCK("Dead Stock", "DEAD"),
    SLOW_DEMAND("Not Selling", "SLOW"),
    SOLD_ALERT("Sold Update", "SOLD")
}

enum class NoticeActionType {
    RESTOCK,
    SOURCE_SIMILAR_LOT,
    RETIRE_LOT,
    ADJUST_PRICE,
    VIEW_PRODUCT,
    OPEN_SELL
}

data class PriorityNotice(
    val noticeKey: String,
    val category: NoticeCategory,
    val priorityScore: Int,
    val title: String,
    val message: String,
    val actionLabel: String,
    val actionType: NoticeActionType,
    val productId: Long? = null,
    val skuCode: String = "",
    val rackLocation: String = "",
    val costPrice: Double? = null,
    val currentSellingPrice: Double? = null,
    val suggestedSellingPrice: Double? = null,
    val isRepeatableProduct: Boolean = false,
    val recommendedSizesHint: String = "",
    val bucketCategory: String = "",
    val bucketGender: String = "",
    val bucketPriceBand: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val signatureHash: String = "",
    val isRead: Boolean = false
)

object NoticeEngine {

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    fun generateNotices(
        products: List<ProductEntity>,
        variants: List<ProductVariantEntity>,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        productMetrics: Map<Long, ProductMetrics>,
        noticeStates: Map<String, NoticeStateEntity>,
        settings: ShopSettingsEntity,
        now: Long = System.currentTimeMillis(),
        buyingBuckets: List<BuyingBucketSuggestion> = emptyList()
    ): List<PriorityNotice> {
        val currency = settings.currencySymbol
        val activeProducts = products.filter { !it.isArchived }
        val variantsByProduct = variants.groupBy { it.productId }
        val itemsBySale = saleItems.groupBy { it.saleId }
        val recentCutoff = now - DAY_MS
        val candidates = mutableListOf<PriorityNotice>()

        // Which sizes had recent demand (30d)
        val saleTimeMap = sales.associate { it.id to it.timestamp }
        val sizeSales30d = mutableMapOf<Pair<Long, String>, Int>()
        for (item in saleItems) {
            val ts = saleTimeMap[item.saleId] ?: continue
            if (ts >= now - 30 * DAY_MS) {
                val key = item.productId to item.size
                sizeSales30d[key] = (sizeSales30d[key] ?: 0) + item.quantity
            }
        }

        val bucketByCategoryGender = buyingBuckets.associateBy { "${it.category}|${it.gender}|${it.priceBandLabel}" }

        for (p in activeProducts) {
            val m = productMetrics[p.id] ?: continue
            val pVars = variantsByProduct[p.id].orEmpty()
            val missingSizes = pVars.filter { it.stock == 0 }.map { it.size }
            val popularMissingSizes = pVars
                .filter { it.stock == 0 && (sizeSales30d[p.id to it.size] ?: 0) >= 1 }
                .map { it.size }

            val (_, bandLabel) = AnalyticsEngine.priceBandFor(p.sellingPrice, currency)
            val matchedBucket = bucketByCategoryGender["${p.category}|${p.gender}|$bandLabel"]
                ?: buyingBuckets.firstOrNull { it.category == p.category }
            val sizesHint = matchedBucket?.sizeSplit?.entries
                ?.take(5)
                ?.joinToString(", ") { "Sz ${it.key}:${it.value}" }
                ?: pVars.joinToString(", ") { "Sz ${it.size}" }

            // 1. OUT OF STOCK
            if (m.totalStock == 0) {
                val boost = if (m.sold30d >= 5) 8 else 0
                val key = "OUT:${p.id}:ALL"
                val sig = "0:${m.sold30d}:${p.isRepeatable}"
                candidates += PriorityNotice(
                    noticeKey = key,
                    category = NoticeCategory.OUT_OF_STOCK,
                    priorityScore = (90 + boost).coerceAtMost(99),
                    title = if (p.isRepeatable) {
                        "${p.skuCode} (${p.name}) is OUT OF STOCK"
                    } else {
                        "${p.skuCode} (${p.name}) SOLD OUT • One-Time Lot"
                    },
                    message = if (p.isRepeatable) {
                        "0 pairs left. Sold ${m.sold30d} pairs in last 30d. Reorder soon so you don't lose walk-in sales."
                    } else {
                        "0 pairs left (sold ${m.sold30d} in 30d). One-time lot — choose next step: source a similar ${p.category} batch or retire."
                    },
                    actionLabel = if (p.isRepeatable) "Restock Now" else "Decide Next Step",
                    actionType = if (p.isRepeatable) NoticeActionType.RESTOCK else NoticeActionType.SOURCE_SIMILAR_LOT,
                    productId = p.id,
                    skuCode = p.skuCode,
                    rackLocation = p.rackLocation,
                    costPrice = p.costPrice,
                    currentSellingPrice = p.sellingPrice,
                    isRepeatableProduct = p.isRepeatable,
                    recommendedSizesHint = sizesHint,
                    bucketCategory = p.category,
                    bucketGender = p.gender,
                    bucketPriceBand = bandLabel,
                    timestamp = m.lastSoldAt ?: p.updatedAt,
                    signatureHash = sig
                )
            } else if (popularMissingSizes.isNotEmpty()) {
                val szStr = popularMissingSizes.joinToString(", ")
                val key = "OUT_SZ:${p.id}:$szStr"
                val sig = "${popularMissingSizes.size}:${m.sold7d}:${p.isRepeatable}"
                candidates += PriorityNotice(
                    noticeKey = key,
                    category = NoticeCategory.OUT_OF_STOCK,
                    priorityScore = if (m.sold7d >= 2) 92 else 84,
                    title = "${p.skuCode} out in popular Size $szStr",
                    message = if (p.isRepeatable) {
                        "Size $szStr ran out while ${m.totalStock} pairs remain in other sizes (${m.sold30d} sold/30d)."
                    } else {
                        "Size $szStr finished (${m.totalStock} left in other sizes, ${m.sold30d} sold/30d). One-time lot — plan similar ${p.category} batch."
                    },
                    actionLabel = if (p.isRepeatable) "Restock Size $szStr" else "Sourcing Options",
                    actionType = if (p.isRepeatable) NoticeActionType.RESTOCK else NoticeActionType.SOURCE_SIMILAR_LOT,
                    productId = p.id,
                    skuCode = p.skuCode,
                    rackLocation = p.rackLocation,
                    costPrice = p.costPrice,
                    currentSellingPrice = p.sellingPrice,
                    isRepeatableProduct = p.isRepeatable,
                    recommendedSizesHint = sizesHint,
                    bucketCategory = p.category,
                    bucketGender = p.gender,
                    bucketPriceBand = bandLabel,
                    timestamp = m.lastSoldAt ?: p.updatedAt,
                    signatureHash = sig
                )
            } else if (missingSizes.isNotEmpty() && m.sold30d >= 3) {
                val szStr = missingSizes.joinToString(", ")
                candidates += PriorityNotice(
                    noticeKey = "OUT_SZ:${p.id}:$szStr",
                    category = NoticeCategory.OUT_OF_STOCK,
                    priorityScore = 78,
                    title = "${p.skuCode} missing Size $szStr",
                    message = if (p.isRepeatable) {
                        "Only ${m.totalStock} pairs left; Size $szStr has 0 stock."
                    } else {
                        "Only ${m.totalStock} pairs left; Size $szStr sold out. Add ${p.category} fresh lot sizes."
                    },
                    actionLabel = if (p.isRepeatable) "Restock" else "Plan Fresh Lot",
                    actionType = if (p.isRepeatable) NoticeActionType.RESTOCK else NoticeActionType.SOURCE_SIMILAR_LOT,
                    productId = p.id,
                    skuCode = p.skuCode,
                    rackLocation = p.rackLocation,
                    costPrice = p.costPrice,
                    currentSellingPrice = p.sellingPrice,
                    isRepeatableProduct = p.isRepeatable,
                    recommendedSizesHint = sizesHint,
                    bucketCategory = p.category,
                    bucketGender = p.gender,
                    bucketPriceBand = bandLabel,
                    timestamp = m.lastSoldAt ?: p.updatedAt,
                    signatureHash = "miss:${missingSizes.size}:${m.totalStock}:${p.isRepeatable}"
                )
            }

            // 2. LOW STOCK
            if (m.totalStock > 0 && (m.totalStock <= p.reorderLevel || (m.daysOfCover != null && m.daysOfCover < 5.0))) {
                val coverNote = m.daysOfCover?.let { "~${it.roundToInt()} days cover left" } ?: "below reorder level (${p.reorderLevel})"
                val boost = if (m.sold7d >= 3) 8 else 0
                candidates += PriorityNotice(
                    noticeKey = "LOW:${p.id}",
                    category = NoticeCategory.LOW_STOCK,
                    priorityScore = (76 + boost).coerceAtMost(89),
                    title = "Low stock: ${p.skuCode} (${m.totalStock} pairs left)",
                    message = if (p.isRepeatable) {
                        "${p.name} is $coverNote. Keep ${p.reorderLevel}+ pairs ready."
                    } else {
                        "${p.name} is $coverNote. One-time lot — plan similar ${p.category} batch before it finishes."
                    },
                    actionLabel = if (p.isRepeatable) "Restock" else "Plan Fresh Lot",
                    actionType = if (p.isRepeatable) NoticeActionType.RESTOCK else NoticeActionType.SOURCE_SIMILAR_LOT,
                    productId = p.id,
                    skuCode = p.skuCode,
                    rackLocation = p.rackLocation,
                    costPrice = p.costPrice,
                    currentSellingPrice = p.sellingPrice,
                    isRepeatableProduct = p.isRepeatable,
                    recommendedSizesHint = sizesHint,
                    bucketCategory = p.category,
                    bucketGender = p.gender,
                    bucketPriceBand = bandLabel,
                    timestamp = m.lastSoldAt ?: p.updatedAt,
                    signatureHash = "low:${m.totalStock}:${m.sold7d}:${p.isRepeatable}"
                )
            }

            // 3. HIGH DEMAND
            val weeklyBaseline = (m.sold30d - m.sold7d) / 3.2
            val isSurging = m.sold7d >= 3 && (weeklyBaseline < 0.8 || m.sold7d >= weeklyBaseline * 1.35)
            if (ProductBadge.FAST_MOVER in m.badges || isSurging) {
                val score = if (isSurging) 75 else 68
                candidates += PriorityNotice(
                    noticeKey = "HOT:${p.id}",
                    category = NoticeCategory.HIGH_DEMAND,
                    priorityScore = score,
                    title = "High demand: ${p.skuCode} (${m.sold7d} sold in 7d)",
                    message = "${p.name} is moving fast (${m.sold30d} in 30d). Keep front display stocked & protect margin.",
                    actionLabel = "View Demand",
                    actionType = NoticeActionType.VIEW_PRODUCT,
                    productId = p.id,
                    skuCode = p.skuCode,
                    rackLocation = p.rackLocation,
                    costPrice = p.costPrice,
                    currentSellingPrice = p.sellingPrice,
                    timestamp = m.lastSoldAt ?: p.updatedAt,
                    signatureHash = "hot:${m.sold7d}:${m.sold30d}"
                )
            }

            // 4. COST / SELLING PRICE & MARGIN ADVICE
            if (p.sellingPrice <= p.costPrice) {
                val targetPrice = roundPriceUpTo10(p.costPrice * 1.35, minBump = 50.0, current = p.sellingPrice)
                candidates += PriorityNotice(
                    noticeKey = "MARGIN_LOSS:${p.id}",
                    category = NoticeCategory.PRICE_MARGIN,
                    priorityScore = 95,
                    title = "Increase price on ${p.skuCode} (No profit!)",
                    message = "Cost ${Formatters.formatCurrency(p.costPrice, currency)} vs Sell ${Formatters.formatCurrency(p.sellingPrice, currency)}. Suggested selling price: ${Formatters.formatCurrency(targetPrice, currency)}.",
                    actionLabel = "Adjust Price",
                    actionType = NoticeActionType.ADJUST_PRICE,
                    productId = p.id,
                    skuCode = p.skuCode,
                    rackLocation = p.rackLocation,
                    costPrice = p.costPrice,
                    currentSellingPrice = p.sellingPrice,
                    suggestedSellingPrice = targetPrice,
                    timestamp = p.updatedAt,
                    signatureHash = "loss:${p.costPrice.roundToInt()}:${p.sellingPrice.roundToInt()}"
                )
            } else if (m.marginPercent < 24.0 && (m.sold30d >= 4 || m.sold7d >= 2)) {
                val targetPrice = roundPriceUpTo10(p.costPrice / 0.72, minBump = 30.0, current = p.sellingPrice)
                val gainPerPair = (targetPrice - p.sellingPrice).roundToInt()
                candidates += PriorityNotice(
                    noticeKey = "MARGIN_UP:${p.id}",
                    category = NoticeCategory.PRICE_MARGIN,
                    priorityScore = 74,
                    title = "Increase margin on ${p.skuCode} (+${Formatters.formatCurrency(gainPerPair.toDouble(), currency)}/pair)",
                    message = "Strong demand (${m.sold30d} in 30d) at only ${m.marginPercent.roundToInt()}% margin (Cost ${Formatters.formatCurrency(p.costPrice, currency)} → Sell ${Formatters.formatCurrency(p.sellingPrice, currency)}). Raise to ${Formatters.formatCurrency(targetPrice, currency)}.",
                    actionLabel = "Raise Price",
                    actionType = NoticeActionType.ADJUST_PRICE,
                    productId = p.id,
                    skuCode = p.skuCode,
                    rackLocation = p.rackLocation,
                    costPrice = p.costPrice,
                    currentSellingPrice = p.sellingPrice,
                    suggestedSellingPrice = targetPrice,
                    timestamp = m.lastSoldAt ?: p.updatedAt,
                    signatureHash = "mup:${p.costPrice.roundToInt()}:${p.sellingPrice.roundToInt()}:${m.sold30d}"
                )
            } else if ((ProductBadge.DEAD_STOCK in m.badges || ProductBadge.SLOW_MOVER in m.badges) &&
                m.marginPercent >= 38.0 && m.totalStock >= 4
            ) {
                val disc = if (p.sellingPrice >= 1000.0) 100.0 else 50.0
                val targetPrice = (p.sellingPrice - disc).coerceAtLeast(p.costPrice * 1.18)
                candidates += PriorityNotice(
                    noticeKey = "MARGIN_CUT:${p.id}",
                    category = NoticeCategory.PRICE_MARGIN,
                    priorityScore = 62,
                    title = "High ${m.marginPercent.roundToInt()}% margin, slow sales on ${p.skuCode}",
                    message = "Cost ${Formatters.formatCurrency(p.costPrice, currency)} • Sell ${Formatters.formatCurrency(p.sellingPrice, currency)}. Try ${Formatters.formatCurrency(targetPrice, currency)} to move ${m.totalStock} pairs faster.",
                    actionLabel = "Set Offer Price",
                    actionType = NoticeActionType.ADJUST_PRICE,
                    productId = p.id,
                    skuCode = p.skuCode,
                    rackLocation = p.rackLocation,
                    costPrice = p.costPrice,
                    currentSellingPrice = p.sellingPrice,
                    suggestedSellingPrice = targetPrice,
                    timestamp = p.updatedAt,
                    signatureHash = "mcut:${p.sellingPrice.roundToInt()}:${m.totalStock}"
                )
            }

            // 5. DEAD STOCK (60+ days no sale)
            if (ProductBadge.DEAD_STOCK in m.badges && m.totalStock > 0) {
                val locked = m.stockCostValue
                val daysText = m.lastSoldAt?.let { "${((now - it) / DAY_MS).toInt()}d since last sale" } ?: "Unsold 60+ days"
                val clearancePrice = (p.sellingPrice * 0.80).coerceAtLeast(p.costPrice * 1.05)
                candidates += PriorityNotice(
                    noticeKey = "DEAD:${p.id}",
                    category = NoticeCategory.DEAD_STOCK,
                    priorityScore = if (locked >= 3000.0) 70 else 60,
                    title = "Dead stock: ${p.skuCode} (${Formatters.formatCurrency(locked, currency)} stuck)",
                    message = "${m.totalStock} pairs of ${p.name} • $daysText. Clear at ${Formatters.formatCurrency(clearancePrice, currency)} to free cash.",
                    actionLabel = "Clearance Price",
                    actionType = NoticeActionType.ADJUST_PRICE,
                    productId = p.id,
                    skuCode = p.skuCode,
                    rackLocation = p.rackLocation,
                    costPrice = p.costPrice,
                    currentSellingPrice = p.sellingPrice,
                    suggestedSellingPrice = clearancePrice,
                    timestamp = m.lastSoldAt ?: p.createdAt,
                    signatureHash = "dead:${m.totalStock}:${p.sellingPrice.roundToInt()}"
                )
            } else if (ProductBadge.SLOW_MOVER in m.badges && m.totalStock > 0) {
                // 6. SLOW / NOT SELLING
                candidates += PriorityNotice(
                    noticeKey = "SLOW:${p.id}",
                    category = NoticeCategory.SLOW_DEMAND,
                    priorityScore = 52,
                    title = "Not moving: ${p.skuCode} (${m.sold30d} sold in 30d)",
                    message = "${m.totalStock} pairs sitting on ${p.rackLocation.ifBlank { "shelf" }}. Bring to eye-level display or bundle.",
                    actionLabel = "Inspect Pair",
                    actionType = NoticeActionType.VIEW_PRODUCT,
                    productId = p.id,
                    skuCode = p.skuCode,
                    rackLocation = p.rackLocation,
                    costPrice = p.costPrice,
                    currentSellingPrice = p.sellingPrice,
                    timestamp = m.lastSoldAt ?: p.createdAt,
                    signatureHash = "slow:${m.sold30d}:${m.totalStock}"
                )
            }
        }

        // 7. CATEGORY / PRICE BUCKET FRESH LOT ALERTS
        for (bucket in buyingBuckets.filter { it.recommendedPairs >= 5 }.take(3)) {
            val sizeStr = bucket.sizeSplit.entries.take(5).joinToString(", ") { "Sz ${it.key}:${it.value}" }
            candidates += PriorityNotice(
                noticeKey = "BUCKET_LOW:${bucket.bucketKey}",
                category = if (bucket.activeStock == 0) NoticeCategory.OUT_OF_STOCK else NoticeCategory.HIGH_DEMAND,
                priorityScore = if (bucket.activeStock == 0) 93 else 82,
                title = "Source fresh ${bucket.gender} ${bucket.category} (${bucket.priceBandLabel})",
                message = "Sold ${bucket.sold30d} pairs in 30d; only ${bucket.activeStock} left across active lots. Source ~${bucket.recommendedPairs} pairs ($sizeStr).",
                actionLabel = "Plan Fresh Lot",
                actionType = NoticeActionType.SOURCE_SIMILAR_LOT,
                recommendedSizesHint = sizeStr,
                bucketCategory = bucket.category,
                bucketGender = bucket.gender,
                bucketPriceBand = bucket.priceBandLabel,
                timestamp = now,
                signatureHash = "bkt:${bucket.activeStock}:${bucket.recommendedPairs}"
            )
        }

        // 8. RECENT SOLD HIGHLIGHTS (last 24h, top 3 recent bills)
        val recentSales = sales.filter { it.timestamp >= recentCutoff }.take(3)
        for (sale in recentSales) {
            val items = itemsBySale[sale.id].orEmpty()
            val totalPairs = items.sumOf { it.quantity }.coerceAtLeast(1)
            val firstSku = items.firstOrNull()?.skuSnapshot.orEmpty()
            val summaryNames = items.take(2).joinToString(", ") { "${it.skuSnapshot} (Sz ${it.size})" }
            val extra = if (items.size > 2) " +${items.size - 2} more" else ""
            candidates += PriorityNotice(
                noticeKey = "SOLD:${sale.id}",
                category = NoticeCategory.SOLD_ALERT,
                priorityScore = if (totalPairs >= 2 || sale.totalCollected >= 1500.0) 64 else 48,
                title = "Sold $totalPairs pair(s) • ${Formatters.formatCurrency(sale.totalCollected, currency)}",
                message = "$summaryNames$extra via ${sale.paymentMode} (${sale.billNumber}).",
                actionLabel = "Open Sell",
                actionType = NoticeActionType.OPEN_SELL,
                productId = items.firstOrNull()?.productId,
                skuCode = firstSku,
                timestamp = sale.timestamp,
                signatureHash = "sold:${sale.id}:${sale.totalCollected.roundToInt()}"
            )
        }

        return candidates
            .mapNotNull { notice ->
                val state = noticeStates[notice.noticeKey]
                if (state != null) {
                    if (now < state.snoozedUntil) return@mapNotNull null
                    if (now < state.dismissedUntil && state.lastSignatureHash == notice.signatureHash) {
                        return@mapNotNull null
                    }
                    notice.copy(isRead = state.isRead)
                } else {
                    notice
                }
            }
            .sortedWith(compareByDescending<PriorityNotice> { it.priorityScore }.thenByDescending { it.timestamp })
    }

    fun pickNextNotificationCandidate(
        notices: List<PriorityNotice>,
        noticeStates: Map<String, NoticeStateEntity>,
        settings: ShopSettingsEntity,
        now: Long = System.currentTimeMillis(),
        allowImmediateSale: Boolean = false
    ): PriorityNotice? {
        if (!settings.alertsEnabled) return null
        if (isQuietHours(now, settings.quietStartHour, settings.quietEndHour)) return null

        val dayStart = Formatters.startOfDay(now)
        val sentTodayCount = noticeStates.values.count { it.lastNotifiedAt >= dayStart }
        if (sentTodayCount >= settings.maxAlertsPerDay.coerceAtLeast(1)) return null

        for (notice in notices) {
            if (notice.isRead) continue
            if (!isCategoryEnabled(notice.category, settings, allowImmediateSale)) continue

            val state = noticeStates[notice.noticeKey]
            val cooldownMs = when (notice.category) {
                NoticeCategory.OUT_OF_STOCK,
                NoticeCategory.LOW_STOCK,
                NoticeCategory.SOLD_ALERT -> 24 * HOUR_MS
                else -> 48 * HOUR_MS
            }

            if (state != null && (now - state.lastNotifiedAt) < cooldownMs && state.lastSignatureHash == notice.signatureHash) {
                continue
            }
            return notice
        }
        return null
    }

    fun isCategoryEnabled(
        category: NoticeCategory,
        settings: ShopSettingsEntity,
        allowImmediateSale: Boolean
    ): Boolean {
        return when (category) {
            NoticeCategory.OUT_OF_STOCK, NoticeCategory.LOW_STOCK -> settings.stockAlertsEnabled
            NoticeCategory.HIGH_DEMAND, NoticeCategory.SLOW_DEMAND, NoticeCategory.DEAD_STOCK -> settings.demandAlertsEnabled
            NoticeCategory.PRICE_MARGIN -> settings.marginAlertsEnabled
            NoticeCategory.SOLD_ALERT -> settings.instantSaleAlertsEnabled && allowImmediateSale
        }
    }

    fun isQuietHours(now: Long, quietStartHour: Int, quietEndHour: Int): Boolean {
        if (quietStartHour == quietEndHour) return false
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return if (quietStartHour > quietEndHour) {
            hour >= quietStartHour || hour < quietEndHour
        } else {
            hour in quietStartHour until quietEndHour
        }
    }

    private fun roundPriceUpTo10(rawTarget: Double, minBump: Double, current: Double): Double {
        val bumped = maxOf(rawTarget, current + minBump)
        return ceil(bumped / 10.0) * 10.0
    }
}
