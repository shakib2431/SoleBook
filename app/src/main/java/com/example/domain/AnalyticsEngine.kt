package com.example.domain

import com.example.data.local.*
import com.example.util.Formatters
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ProductBadge(val label: String) {
    FAST_MOVER("Fast Mover"),
    SLOW_MOVER("Slow Mover"),
    DEAD_STOCK("Dead Stock"),
    LOW_STOCK("Low Stock"),
    OUT_OF_STOCK("Out of Stock")
}

enum class ReorderGroup(val title: String) {
    BUY_MORE("BUY MORE"),
    BUY_SAME("BUY SAME"),
    BUY_LESS("BUY LESS"),
    DONT_REORDER("DON'T REORDER")
}

enum class AbcClass(val label: String) {
    A("A — Top 80% Profit (Never Run Out)"),
    B("B — Steady 15% Profit"),
    C("C — Bottom 5% Profit")
}

enum class ConfidenceLevel(val label: String) {
    HIGH("High confidence (8+ wks data)"),
    MEDIUM("Medium confidence (4–7 wks data)"),
    LOW("Low confidence (< 4 wks data)")
}

enum class SeasonalityPreset(val key: String, val label: String, val defaultUpliftPct: Double) {
    NONE("NONE", "Normal Days", 0.0),
    DIWALI("DIWALI", "Diwali Rush", 35.0),
    EID("EID", "Eid Festive", 30.0),
    PONGAL("PONGAL", "Pongal / Sankranti", 25.0),
    WEDDING("WEDDING", "Wedding Season", 20.0),
    MONSOON("MONSOON", "Monsoon Demand", 15.0),
    SCHOOL("SCHOOL", "School Reopening", 25.0)
}

data class ProductMetrics(
    val product: ProductEntity,
    val variants: List<ProductVariantEntity>,
    val totalStock: Int,
    val sold7d: Int,
    val sold30d: Int,
    val sold90d: Int,
    val totalUnitsSold: Int,
    val totalProfitEarned: Double,
    val marginPerPair: Double,
    val marginPercent: Double,
    val daysOfCover: Double?,
    val lastSoldAt: Long?,
    val sparkline14d: List<Int>,
    val badges: List<ProductBadge>,
    val stockCostValue: Double,
    val stockRetailValue: Double
)

data class DayBar(
    val label: String,
    val revenue: Double,
    val profit: Double
)

data class CategoryProfit(
    val category: String,
    val revenue: Double,
    val profit: Double,
    val units: Int
)

data class TopProductProfit(
    val productId: Long,
    val sku: String,
    val name: String,
    val profit: Double,
    val units: Int
)

data class MoneySummary(
    val periodLabel: String,
    val revenue: Double,
    val cogs: Double,
    val grossProfit: Double,
    val grossMarginPct: Double,
    val expenses: Double,
    val netProfit: Double,
    val pairsSold: Int,
    val billCount: Int,
    val avgBillValue: Double,
    val avgProfitPerPair: Double,
    val returnsCount: Int,
    val returnsAmount: Double,
    val revenueChangePct: Double?,
    val profitChangePct: Double?,
    val paymentSplit: Map<String, Double>,
    val dailyBars: List<DayBar>,
    val profitByCategory: List<CategoryProfit>,
    val topProductsByProfit: List<TopProductProfit>,
    val monthlyFixedExpenses: Double,
    val breakEvenPairsPerMonth: Int,
    val pairsSoldThisMonth: Int,
    val totalStockCostValue: Double,
    val totalStockRetailValue: Double,
    val deadSlowCapitalLocked: Double
)

data class SizeGap(
    val productId: Long,
    val sku: String,
    val productName: String,
    val size: String,
    val forecastDemand30d: Int,
    val currentStock: Int,
    val shortage: Int
)

data class ProductForecast(
    val productId: Long,
    val sku: String,
    val name: String,
    val category: String,
    val forecast7d: Int,
    val forecast30d: Int,
    val currentStock: Int,
    val netGap30d: Int,
    val confidence: ConfidenceLevel,
    val whyExplanation: String
)

data class CategoryForecast(
    val category: String,
    val forecast7d: Int,
    val forecast30d: Int,
    val currentStock: Int
)

data class ReorderSuggestion(
    val product: ProductEntity,
    val group: ReorderGroup,
    val avgDailySales: Double,
    val currentStock: Int,
    val reorderPoint: Int,
    val safetyStock: Int,
    val recommendedQty: Int,
    val sizeOrderSplit: Map<String, Int>,
    val estimatedCost: Double,
    val expectedProfit: Double,
    val reason: String
)

data class DeadSlowAction(
    val product: ProductEntity,
    val stockUnits: Int,
    val capitalLocked: Double,
    val daysSinceLastSale: Int?,
    val suggestedAction: String
)

data class PricingSuggestion(
    val product: ProductEntity,
    val currentMarginPct: Double,
    val sold30d: Int,
    val headline: String,
    val reason: String,
    val isIncrease: Boolean
)

data class AbcItem(
    val product: ProductEntity,
    val profitEarned: Double,
    val profitSharePct: Double,
    val abcClass: AbcClass,
    val currentStock: Int
)

data class WeeklyAction(
    val title: String,
    val detail: String,
    val priorityTag: String
)

data class BuyingBucketSuggestion(
    val bucketKey: String,
    val category: String,
    val gender: String,
    val priceBandLabel: String,
    val costRangeLabel: String,
    val sellRangeLabel: String,
    val sold30d: Int,
    val sold90d: Int,
    val activeStock: Int,
    val forecast30d: Int,
    val recommendedPairs: Int,
    val sizeSplit: Map<String, Int>,
    val estimatedBudget: Double,
    val expectedProfit: Double,
    val matchingVendors: List<VendorEntity>,
    val reason: String
)

data class InsightsReport(
    val weeklyActions: List<WeeklyAction>,
    val confidence: ConfidenceLevel,
    val productForecasts: List<ProductForecast>,
    val categoryForecasts: List<CategoryForecast>,
    val criticalSizeGaps: List<SizeGap>,
    val reorderSuggestions: List<ReorderSuggestion>,
    val buyingBuckets: List<BuyingBucketSuggestion> = emptyList(),
    val deadSlowActions: List<DeadSlowAction>,
    val pricingSuggestions: List<PricingSuggestion>,
    val bestSizes: List<Pair<String, Int>>,
    val bestDaysOfWeek: List<Pair<String, Int>>,
    val bestCategoriesByProfit: List<CategoryProfit>,
    val abcItems: List<AbcItem>,
    val sellThroughRatePct: Double,
    val stockTurnoverAnnual: Double,
    val gmroi: Double,
    val daysOfInventory: Double
)

enum class CustomerSegmentType(val key: String, val label: String) {
    ALL("ALL", "All"),
    VIP("VIP", "VIP"),
    REGULAR("REGULAR", "Regulars"),
    NEW("NEW", "New"),
    LAPSED("LAPSED", "Lapsed 60d+"),
    CREDIT_PENDING("CREDIT_PENDING", "Udhaar Pending")
}

data class SimilarProductSuggestion(
    val product: ProductEntity,
    val availableStock: Int,
    val matchScore: Int,
    val reason: String
)

object AnalyticsEngine {

    private const val DAY_MS = 86_400_000L

    fun computeProductMetrics(
        products: List<ProductEntity>,
        variants: List<ProductVariantEntity>,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        returns: List<ReturnEntity>,
        now: Long = System.currentTimeMillis()
    ): Map<Long, ProductMetrics> {
        val saleTimeById = sales.associate { it.id to it.timestamp }
        val variantsByProduct = variants.groupBy { it.productId }
        val itemsByProduct = saleItems.groupBy { it.productId }
        val returnsByProduct = returns.filter { it.type == "RETURN" }.groupBy { it.productId }

        val cut7 = now - 7 * DAY_MS
        val cut30 = now - 30 * DAY_MS
        val cut60 = now - 60 * DAY_MS
        val cut90 = now - 90 * DAY_MS
        val todayStart = Formatters.startOfDay(now)

        return products.associate { p ->
            val pVars = variantsByProduct[p.id].orEmpty()
            val totalStock = pVars.sumOf { it.stock }
            val pItems = itemsByProduct[p.id].orEmpty()
            val pReturns = returnsByProduct[p.id].orEmpty()

            var sold7 = 0
            var sold30 = 0
            var sold90 = 0
            var totalSold = 0
            var grossProfitEarned = 0.0
            var lastSold: Long? = null
            val dailyBuckets = IntArray(14)

            for (item in pItems) {
                val ts = saleTimeById[item.saleId] ?: continue
                totalSold += item.quantity
                grossProfitEarned += (item.sellingPriceAtSale - item.costPriceAtSale) * item.quantity
                if (lastSold == null || ts > lastSold) lastSold = ts
                if (ts >= cut7) sold7 += item.quantity
                if (ts >= cut30) sold30 += item.quantity
                if (ts >= cut90) sold90 += item.quantity

                val dayDiff = ((todayStart - Formatters.startOfDay(ts)) / DAY_MS).toInt()
                if (dayDiff in 0..13) {
                    dailyBuckets[13 - dayDiff] += item.quantity
                }
            }

            // Subtract returned units & reversed profit
            var returned7 = 0
            var returned30 = 0
            var returned90 = 0
            var totalReturned = 0
            for (ret in pReturns) {
                totalReturned += ret.quantity
                grossProfitEarned -= (ret.refundAmount - ret.costReversed)
                if (ret.timestamp >= cut7) returned7 += ret.quantity
                if (ret.timestamp >= cut30) returned30 += ret.quantity
                if (ret.timestamp >= cut90) returned90 += ret.quantity
            }

            val net7 = (sold7 - returned7).coerceAtLeast(0)
            val net30 = (sold30 - returned30).coerceAtLeast(0)
            val net90 = (sold90 - returned90).coerceAtLeast(0)
            val netTotal = (totalSold - totalReturned).coerceAtLeast(0)

            val marginPair = p.sellingPrice - p.costPrice
            val marginPct = if (p.sellingPrice > 0) (marginPair / p.sellingPrice) * 100.0 else 0.0
            val avgDaily30 = net30 / 30.0
            val daysCover = if (avgDaily30 > 0.01) totalStock / avgDaily30 else null

            val badges = mutableListOf<ProductBadge>()
            if (totalStock == 0) {
                badges += ProductBadge.OUT_OF_STOCK
            } else if (totalStock <= p.reorderLevel) {
                badges += ProductBadge.LOW_STOCK
            }

            val ageDays = ((now - p.createdAt) / DAY_MS).toInt()
            if (totalStock > 0 && ((lastSold != null && lastSold < cut60) || (lastSold == null && ageDays >= 60))) {
                badges += ProductBadge.DEAD_STOCK
            } else if (net30 >= 8 || net7 >= 3) {
                badges += ProductBadge.FAST_MOVER
            } else if (ageDays >= 21 && net30 <= 2 && totalStock > 0) {
                badges += ProductBadge.SLOW_MOVER
            }

            p.id to ProductMetrics(
                product = p,
                variants = pVars,
                totalStock = totalStock,
                sold7d = net7,
                sold30d = net30,
                sold90d = net90,
                totalUnitsSold = netTotal,
                totalProfitEarned = grossProfitEarned,
                marginPerPair = marginPair,
                marginPercent = marginPct,
                daysOfCover = daysCover,
                lastSoldAt = lastSold,
                sparkline14d = dailyBuckets.toList(),
                badges = badges,
                stockCostValue = totalStock * p.costPrice,
                stockRetailValue = totalStock * p.sellingPrice
            )
        }
    }

    fun computeMoneySummary(
        periodLabel: String,
        startTime: Long,
        endTime: Long,
        products: List<ProductEntity>,
        variants: List<ProductVariantEntity>,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        returns: List<ReturnEntity>,
        expenses: List<ExpenseEntity>,
        productMetrics: Map<Long, ProductMetrics>,
        now: Long = System.currentTimeMillis()
    ): MoneySummary {
        val duration = max(DAY_MS, endTime - startTime)
        val prevEnd = startTime - 1
        val prevStart = prevEnd - duration

        fun calcSlice(from: Long, to: Long): Triple<Double, Double, Double> {
            val sliceSales = sales.filter { it.timestamp in from..to }
            val sliceSaleIds = sliceSales.map { it.id }.toSet()
            val sliceItems = saleItems.filter { it.saleId in sliceSaleIds }
            val sliceReturns = returns.filter { it.type == "RETURN" && it.timestamp in from..to }

            val grossRev = sliceSales.sumOf { it.totalRevenue } - sliceReturns.sumOf { it.refundAmount }
            val cogs = sliceItems.sumOf { it.costPriceAtSale * it.quantity } - sliceReturns.sumOf { it.costReversed }
            val gp = grossRev - cogs
            return Triple(grossRev, cogs, gp)
        }

        val periodSales = sales.filter { it.timestamp in startTime..endTime }
        val periodSaleIds = periodSales.map { it.id }.toSet()
        val periodItems = saleItems.filter { it.saleId in periodSaleIds }
        val periodReturns = returns.filter { it.type == "RETURN" && it.timestamp in startTime..endTime }
        val periodExpenses = expenses.filter { it.timestamp in startTime..endTime }

        val (revenue, cogs, grossProfit) = calcSlice(startTime, endTime)
        val grossMarginPct = if (revenue > 0.0) (grossProfit / revenue) * 100.0 else 0.0
        val expensesTotal = periodExpenses.sumOf { it.amount }
        val netProfit = grossProfit - expensesTotal

        val pairsSold = (periodItems.sumOf { it.quantity } - periodReturns.sumOf { it.quantity }).coerceAtLeast(0)
        val billCount = periodSales.size
        val avgBillValue = if (billCount > 0) revenue / billCount else 0.0
        val avgProfitPerPair = if (pairsSold > 0) grossProfit / pairsSold else 0.0

        val (prevRevenue, _, prevGrossProfit) = calcSlice(prevStart, prevEnd)
        val revChange = if (prevRevenue > 1.0) ((revenue - prevRevenue) / prevRevenue) * 100.0 else null
        val profChange = if (prevGrossProfit > 1.0) ((grossProfit - prevGrossProfit) / prevGrossProfit) * 100.0 else null

        val paymentSplit = linkedMapOf(
            "CASH" to 0.0,
            "UPI" to 0.0,
            "CARD" to 0.0,
            "CREDIT" to 0.0
        )
        periodSales.forEach { s ->
            paymentSplit[s.paymentMode] = (paymentSplit[s.paymentMode] ?: 0.0) + s.totalCollected
        }

        // Daily bars (up to last 7 or 14 days in range)
        val totalDaysInRange = (((endTime - startTime) / DAY_MS).toInt() + 1).coerceIn(1, 14)
        val barStart = Formatters.startOfDay(endTime) - (totalDaysInRange - 1) * DAY_MS
        val itemsBySale = periodItems.groupBy { it.saleId }
        val dailyBars = (0 until totalDaysInRange).map { idx ->
            val dStart = barStart + idx * DAY_MS
            val dEnd = dStart + DAY_MS - 1
            val daySales = periodSales.filter { it.timestamp in dStart..dEnd }
            val dayReturns = periodReturns.filter { it.timestamp in dStart..dEnd }
            val dRev = daySales.sumOf { it.totalRevenue } - dayReturns.sumOf { it.refundAmount }
            val dCogs = daySales.sumOf { s ->
                itemsBySale[s.id].orEmpty().sumOf { it.costPriceAtSale * it.quantity }
            } - dayReturns.sumOf { it.costReversed }
            DayBar(
                label = Formatters.formatShortDate(dStart).take(6),
                revenue = dRev.coerceAtLeast(0.0),
                profit = dRev - dCogs
            )
        }

        // Profit by category (accounting for bill discount proportionally)
        val saleMap = periodSales.associateBy { it.id }
        val catMap = mutableMapOf<String, Triple<Double, Double, Int>>()
        for (item in periodItems) {
            val s = saleMap[item.saleId] ?: continue
            val ratio = if (s.subtotal > 0) s.totalRevenue / s.subtotal else 1.0
            val rev = item.lineTotal * ratio
            val prof = rev - (item.costPriceAtSale * item.quantity)
            val prev = catMap[item.categorySnapshot] ?: Triple(0.0, 0.0, 0)
            catMap[item.categorySnapshot] = Triple(prev.first + rev, prev.second + prof, prev.third + item.quantity)
        }
        val prodCatById = products.associate { it.id to it.category }
        for (ret in periodReturns) {
            val cat = prodCatById[ret.productId] ?: "Other"
            val prev = catMap[cat] ?: Triple(0.0, 0.0, 0)
            catMap[cat] = Triple(
                (prev.first - ret.refundAmount).coerceAtLeast(0.0),
                prev.second - (ret.refundAmount - ret.costReversed),
                (prev.third - ret.quantity).coerceAtLeast(0)
            )
        }
        val profitByCategory = catMap.entries
            .map { CategoryProfit(it.key, it.value.first, it.value.second, it.value.third) }
            .sortedByDescending { it.profit }

        // Top 10 products by profit
        val prodProfitMap = mutableMapOf<Long, Triple<String, Double, Int>>()
        val prodSkuMap = mutableMapOf<Long, String>()
        for (item in periodItems) {
            val s = saleMap[item.saleId] ?: continue
            val ratio = if (s.subtotal > 0) s.totalRevenue / s.subtotal else 1.0
            val rev = item.lineTotal * ratio
            val prof = rev - (item.costPriceAtSale * item.quantity)
            val prev = prodProfitMap[item.productId]
            prodProfitMap[item.productId] = Triple(
                item.productNameSnapshot,
                (prev?.second ?: 0.0) + prof,
                (prev?.third ?: 0) + item.quantity
            )
            prodSkuMap[item.productId] = item.skuSnapshot
        }
        for (ret in periodReturns) {
            val prev = prodProfitMap[ret.productId] ?: continue
            prodProfitMap[ret.productId] = Triple(
                prev.first,
                prev.second - (ret.refundAmount - ret.costReversed),
                (prev.third - ret.quantity).coerceAtLeast(0)
            )
        }
        val top10Products = prodProfitMap.entries
            .map { TopProductProfit(it.key, prodSkuMap[it.key] ?: "", it.value.first, it.value.second, it.value.third) }
            .sortedByDescending { it.profit }
            .take(10)

        // Break-even this month
        val monthStart = Formatters.startOfMonth(now)
        val monthEnd = Formatters.endOfDay(now)
        val monthRecurringExpenses = expenses
            .filter { it.isRecurringMonthly && it.timestamp >= now - 35 * DAY_MS }
            .groupBy { it.category }
            .values
            .sumOf { list -> list.maxByOrNull { it.timestamp }?.amount ?: 0.0 }
        val monthSales = sales.filter { it.timestamp in monthStart..monthEnd }
        val monthSaleIds = monthSales.map { it.id }.toSet()
        val monthItems = saleItems.filter { it.saleId in monthSaleIds }
        val monthReturns = returns.filter { it.type == "RETURN" && it.timestamp in monthStart..monthEnd }
        val monthPairs = (monthItems.sumOf { it.quantity } - monthReturns.sumOf { it.quantity }).coerceAtLeast(0)
        val monthRev = monthSales.sumOf { it.totalRevenue } - monthReturns.sumOf { it.refundAmount }
        val monthCogs = monthItems.sumOf { it.costPriceAtSale * it.quantity } - monthReturns.sumOf { it.costReversed }
        val monthAvgProfitPerPair = if (monthPairs > 0) (monthRev - monthCogs) / monthPairs else {
            products.filter { !it.isArchived }.map { it.sellingPrice - it.costPrice }.average().takeIf { !it.isNaN() } ?: 200.0
        }
        val breakEvenPairs = if (monthAvgProfitPerPair > 1.0 && monthRecurringExpenses > 0) {
            ceil(monthRecurringExpenses / monthAvgProfitPerPair).toInt()
        } else 0

        // Inventory valuation
        val activeMetrics = productMetrics.values.filter { !it.product.isArchived }
        val stockCost = activeMetrics.sumOf { it.stockCostValue }
        val stockRetail = activeMetrics.sumOf { it.stockRetailValue }
        val deadSlowCapital = activeMetrics
            .filter { ProductBadge.DEAD_STOCK in it.badges || ProductBadge.SLOW_MOVER in it.badges }
            .sumOf { it.stockCostValue }

        return MoneySummary(
            periodLabel = periodLabel,
            revenue = revenue,
            cogs = cogs,
            grossProfit = grossProfit,
            grossMarginPct = grossMarginPct,
            expenses = expensesTotal,
            netProfit = netProfit,
            pairsSold = pairsSold,
            billCount = billCount,
            avgBillValue = avgBillValue,
            avgProfitPerPair = avgProfitPerPair,
            returnsCount = periodReturns.sumOf { it.quantity },
            returnsAmount = periodReturns.sumOf { it.refundAmount },
            revenueChangePct = revChange,
            profitChangePct = profChange,
            paymentSplit = paymentSplit,
            dailyBars = dailyBars,
            profitByCategory = profitByCategory,
            topProductsByProfit = top10Products,
            monthlyFixedExpenses = monthRecurringExpenses,
            breakEvenPairsPerMonth = breakEvenPairs,
            pairsSoldThisMonth = monthPairs,
            totalStockCostValue = stockCost,
            totalStockRetailValue = stockRetail,
            deadSlowCapitalLocked = deadSlowCapital
        )
    }

    fun priceBandFor(sellingPrice: Double, currency: String = "₹"): Pair<String, String> = when {
        sellingPrice < 500.0 -> "BUDGET" to "Budget (<${currency}500)"
        sellingPrice < 1000.0 -> "MID" to "Mid (${currency}500–${currency}999)"
        else -> "PREMIUM" to "Premium (${currency}1,000+)"
    }

    fun computeInsightsReport(
        products: List<ProductEntity>,
        variants: List<ProductVariantEntity>,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        returns: List<ReturnEntity>,
        settings: ShopSettingsEntity,
        productMetrics: Map<Long, ProductMetrics>,
        now: Long = System.currentTimeMillis(),
        vendors: List<VendorEntity> = emptyList()
    ): InsightsReport {
        val activeProducts = products.filter { !it.isArchived }
        val saleMap = sales.associateBy { it.id }
        val currency = settings.currencySymbol

        // History span for confidence indicator
        val oldestSale = sales.minOfOrNull { it.timestamp }
        val historyDays = if (oldestSale != null) ((now - oldestSale) / DAY_MS).toInt() else 0
        val globalConfidence = when {
            historyDays >= 56 && sales.size >= 40 -> ConfidenceLevel.HIGH
            historyDays >= 28 && sales.size >= 15 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

        // Weekday pattern factor
        val dowCounts = IntArray(8) // 1..7
        val dowNames = mapOf(
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat",
            Calendar.SUNDAY to "Sun"
        )
        for (item in saleItems) {
            val ts = saleMap[item.saleId]?.timestamp ?: continue
            if (ts >= now - 60 * DAY_MS) {
                val dow = Formatters.dayOfWeekIndex(ts)
                if (dow in 1..7) dowCounts[dow] += item.quantity
            }
        }
        val weekendAvg = (dowCounts[Calendar.SATURDAY] + dowCounts[Calendar.SUNDAY]) / 2.0
        val weekdayAvg = (dowCounts[Calendar.MONDAY] + dowCounts[Calendar.TUESDAY] +
            dowCounts[Calendar.WEDNESDAY] + dowCounts[Calendar.THURSDAY] + dowCounts[Calendar.FRIDAY]) / 5.0
        val weekendRatio = if (weekdayAvg > 0.5) (weekendAvg / weekdayAvg).coerceIn(1.0, 1.8) else 1.25

        val festivalMultiplier = 1.0 + (settings.seasonalityUpliftPercent / 100.0)
        val leadTime = settings.defaultLeadTimeDays.coerceIn(1, 30)

        // Product Forecasts & Size Gaps & Smart Reorder
        val itemsByProduct = saleItems.groupBy { it.productId }
        val variantsByProduct = variants.groupBy { it.productId }

        val productForecasts = mutableListOf<ProductForecast>()
        val allSizeGaps = mutableListOf<SizeGap>()
        val reorderSuggestions = mutableListOf<ReorderSuggestion>()
        val deadSlowActions = mutableListOf<DeadSlowAction>()
        val pricingSuggestions = mutableListOf<PricingSuggestion>()

        for (p in activeProducts) {
            val pMetrics = productMetrics[p.id]
            val stock = pMetrics?.totalStock ?: 0
            val pVars = variantsByProduct[p.id].orEmpty()
            val pItems = itemsByProduct[p.id].orEmpty()

            // 4 weekly buckets (W1 = last 7d, W2 = 8..14d, W3 = 15..21d, W4 = 22..28d)
            val w = IntArray(4)
            val sizeCounts = mutableMapOf<String, Int>()
            for (item in pItems) {
                val ts = saleMap[item.saleId]?.timestamp ?: continue
                val daysAgo = ((now - ts) / DAY_MS).toInt()
                if (daysAgo in 0..6) w[0] += item.quantity
                else if (daysAgo in 7..13) w[1] += item.quantity
                else if (daysAgo in 14..20) w[2] += item.quantity
                else if (daysAgo in 21..27) w[3] += item.quantity

                if (daysAgo in 0..60) {
                    sizeCounts[item.size] = (sizeCounts[item.size] ?: 0) + item.quantity
                }
            }

            // Weighted Moving Average per week
            val wmaWeekly = (w[0] * 4.0 + w[1] * 3.0 + w[2] * 2.0 + w[3] * 1.0) / 10.0
            // 7-day forecast has 5 weekdays + 2 weekend days normalized
            val weekPatternBoost = ((5.0 + 2.0 * weekendRatio) / 7.0)
            val f7Raw = wmaWeekly * weekPatternBoost * festivalMultiplier
            val f7 = ceil(f7Raw).toInt()
            val f30 = ceil((f7Raw / 7.0) * 30.0).toInt()

            val prodAgeDays = ((now - p.createdAt) / DAY_MS).toInt()
            val prodConfidence = when {
                prodAgeDays < 28 || pItems.size < 4 -> ConfidenceLevel.LOW
                prodAgeDays < 56 -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.HIGH
            }

            val festLabel = if (settings.seasonalityUpliftPercent > 0) " + ${settings.seasonalityUpliftPercent.roundToInt()}% ${settings.seasonalityMode.lowercase()} uplift" else ""
            val why = "Recent 4-wk avg ${String.format("%.1f", wmaWeekly)}/wk, ${String.format("%.1f", weekendRatio)}x weekend boost$festLabel."

            productForecasts += ProductForecast(
                productId = p.id,
                sku = p.skuCode,
                name = p.name,
                category = p.category,
                forecast7d = f7,
                forecast30d = f30,
                currentStock = stock,
                netGap30d = (f30 - stock).coerceAtLeast(0),
                confidence = prodConfidence,
                whyExplanation = why
            )

            // Size distribution for supply-demand gap & reorder split
            val totalSizeSales = sizeCounts.values.sum()
            val targetSizes = pVars.map { it.size }.ifEmpty { listOf("7", "8", "9") }
            val sizeShares = targetSizes.associateWith { sz ->
                if (totalSizeSales > 0) {
                    (sizeCounts[sz] ?: 0).toDouble() / totalSizeSales.toDouble()
                } else {
                    1.0 / targetSizes.size.toDouble()
                }
            }

            for (v in pVars) {
                val share = sizeShares[v.size] ?: (1.0 / targetSizes.size)
                val szDemand30 = ceil(f30 * share).toInt()
                val short = (szDemand30 - v.stock).coerceAtLeast(0)
                if (short > 0) {
                    allSizeGaps += SizeGap(
                        productId = p.id,
                        sku = p.skuCode,
                        productName = p.name,
                        size = v.size,
                        forecastDemand30d = szDemand30,
                        currentStock = v.stock,
                        shortage = short
                    )
                }
            }

            // Smart Reorder Calculation
            val avgDaily = (f30 / 30.0)
            val safetyStock = if (avgDaily > 0.02) ceil(avgDaily * 1.5 * sqrt(leadTime.toDouble())).toInt().coerceAtLeast(1) else 0
            val reorderPoint = ceil(avgDaily * leadTime + safetyStock).toInt()
            val target14dStock = ceil(avgDaily * (14 + leadTime) + safetyStock).toInt()
            val rawNeed = (target14dStock - stock).coerceAtLeast(0)

            val isDead = pMetrics?.badges?.contains(ProductBadge.DEAD_STOCK) == true
            val isSlow = pMetrics?.badges?.contains(ProductBadge.SLOW_MOVER) == true

            val group = when {
                isDead || (avgDaily <= 0.03 && stock >= p.reorderLevel) -> ReorderGroup.DONT_REORDER
                isSlow && stock > 3 -> ReorderGroup.BUY_LESS
                stock <= reorderPoint && rawNeed > 0 && (pMetrics?.sold30d ?: 0) >= 6 -> ReorderGroup.BUY_MORE
                rawNeed > 0 -> ReorderGroup.BUY_SAME
                else -> ReorderGroup.DONT_REORDER
            }

            val recQty = when (group) {
                ReorderGroup.DONT_REORDER -> 0
                ReorderGroup.BUY_LESS -> (rawNeed / 2).coerceAtLeast(0)
                else -> rawNeed.coerceAtLeast(if (stock == 0 && !isDead) p.reorderLevel else 0)
            }

            val split = linkedMapOf<String, Int>()
            if (recQty > 0) {
                var remaining = recQty
                val sortedSizes = targetSizes.sortedByDescending { sizeShares[it] ?: 0.0 }
                for ((idx, sz) in sortedSizes.withIndex()) {
                    val alloc = if (idx == sortedSizes.lastIndex) remaining else {
                        ((recQty * (sizeShares[sz] ?: 0.0)).roundToInt()).coerceAtMost(remaining)
                    }
                    if (alloc > 0) split[sz] = alloc
                    remaining -= alloc
                }
            }

            val marginPair = (p.sellingPrice - p.costPrice).coerceAtLeast(0.0)
            val reason = when (group) {
                ReorderGroup.BUY_MORE -> "Sells ${String.format("%.1f", avgDaily)}/day. Stock ($stock) is below reorder point ($reorderPoint for ${leadTime}d lead + $safetyStock safety)."
                ReorderGroup.BUY_SAME -> "Steady demand (${pMetrics?.sold30d ?: 0} in 30d). Top up to cover next 14 days."
                ReorderGroup.BUY_LESS -> "Moving slowly (${pMetrics?.sold30d ?: 0} in 30d). Order half quantity only for missing sizes."
                ReorderGroup.DONT_REORDER -> if (isDead) "Dead stock (60+ days no sale). Clear existing $stock pairs first." else "Current stock ($stock) covers demand well."
            }

            reorderSuggestions += ReorderSuggestion(
                product = p,
                group = group,
                avgDailySales = avgDaily,
                currentStock = stock,
                reorderPoint = reorderPoint,
                safetyStock = safetyStock,
                recommendedQty = recQty,
                sizeOrderSplit = split,
                estimatedCost = recQty * p.costPrice,
                expectedProfit = recQty * marginPair,
                reason = reason
            )

            // Dead & Slow stock actions
            if ((isDead || isSlow) && stock > 0) {
                val daysSince = pMetrics?.lastSoldAt?.let { ((now - it) / DAY_MS).toInt() }
                val locked = stock * p.costPrice
                val action = if (isDead) {
                    "Offer 25% clearance discount (down to ${Formatters.formatCurrency(p.sellingPrice * 0.75)}) or display at front counter to unlock ${Formatters.formatCurrency(locked)}."
                } else {
                    "Bundle with a fast-moving chappal/sandal or place on eye-level shelf near billing."
                }
                deadSlowActions += DeadSlowAction(
                    product = p,
                    stockUnits = stock,
                    capitalLocked = locked,
                    daysSinceLastSale = daysSince,
                    suggestedAction = action
                )
            }

            // Pricing suggestions
            val marginPct = pMetrics?.marginPercent ?: 0.0
            val sold30 = pMetrics?.sold30d ?: 0
            if (sold30 >= 8 && marginPct < 24.0) {
                val bump = if (p.sellingPrice >= 800) 50 else 30
                pricingSuggestions += PricingSuggestion(
                    product = p,
                    currentMarginPct = marginPct,
                    sold30d = sold30,
                    headline = "Raise price of ${p.skuCode} by ₹$bump",
                    reason = "Sold $sold30 pairs in 30d, but margin is only ${marginPct.roundToInt()}%. Strong demand lets you earn ₹$bump more per pair.",
                    isIncrease = true
                )
            } else if ((isDead || isSlow) && marginPct >= 38.0 && stock >= 4) {
                val disc = if (p.sellingPrice >= 1000) 100 else 50
                pricingSuggestions += PricingSuggestion(
                    product = p,
                    currentMarginPct = marginPct,
                    sold30d = sold30,
                    headline = "Try ₹$disc off on ${p.skuCode}",
                    reason = "High ${marginPct.roundToInt()}% margin, but only $sold30 sold in 30d. A small discount still keeps healthy profit while freeing cash.",
                    isIncrease = false
                )
            }
        }

        // Category forecasts
        val categoryForecasts = productForecasts.groupBy { it.category }.map { (cat, list) ->
            CategoryForecast(
                category = cat,
                forecast7d = list.sumOf { it.forecast7d },
                forecast30d = list.sumOf { it.forecast30d },
                currentStock = list.sumOf { it.currentStock }
            )
        }.sortedByDescending { it.forecast30d }

        // Size & Day Intelligence
        val allSizeSales = mutableMapOf<String, Int>()
        for (item in saleItems) {
            val ts = saleMap[item.saleId]?.timestamp ?: continue
            if (ts >= now - 60 * DAY_MS) {
                allSizeSales[item.size] = (allSizeSales[item.size] ?: 0) + item.quantity
            }
        }
        val bestSizes = allSizeSales.entries.sortedByDescending { it.value }.map { it.key to it.value }
        val bestDaysOfWeek = dowNames.entries
            .map { (dow, name) -> name to dowCounts[dow] }
            .sortedByDescending { it.second }

        // Best categories by profit (last 60d)
        val catProfits = mutableMapOf<String, Triple<Double, Double, Int>>()
        for (item in saleItems) {
            val s = saleMap[item.saleId] ?: continue
            if (s.timestamp >= now - 60 * DAY_MS) {
                val ratio = if (s.subtotal > 0) s.totalRevenue / s.subtotal else 1.0
                val rev = item.lineTotal * ratio
                val prof = rev - (item.costPriceAtSale * item.quantity)
                val prev = catProfits[item.categorySnapshot] ?: Triple(0.0, 0.0, 0)
                catProfits[item.categorySnapshot] = Triple(prev.first + rev, prev.second + prof, prev.third + item.quantity)
            }
        }
        val bestCategoriesByProfit = catProfits.entries
            .map { CategoryProfit(it.key, it.value.first, it.value.second, it.value.third) }
            .sortedByDescending { it.profit }

        // ABC Analysis (A = top 80% cumulative profit, B = next 15%, C = bottom 5%)
        val totalPositiveProfit = activeProducts.sumOf { (productMetrics[it.id]?.totalProfitEarned ?: 0.0).coerceAtLeast(0.0) }
        var cumProfit = 0.0
        val abcItems = activeProducts
            .sortedByDescending { productMetrics[it.id]?.totalProfitEarned ?: 0.0 }
            .map { p ->
                val prof = (productMetrics[p.id]?.totalProfitEarned ?: 0.0).coerceAtLeast(0.0)
                val share = if (totalPositiveProfit > 0) (prof / totalPositiveProfit) * 100.0 else 0.0
                val prevCumPct = if (totalPositiveProfit > 0) (cumProfit / totalPositiveProfit) * 100.0 else 100.0
                cumProfit += prof
                val cls = when {
                    totalPositiveProfit <= 0 -> AbcClass.C
                    prevCumPct < 80.0 -> AbcClass.A
                    prevCumPct < 95.0 -> AbcClass.B
                    else -> AbcClass.C
                }
                AbcItem(
                    product = p,
                    profitEarned = prof,
                    profitSharePct = share,
                    abcClass = cls,
                    currentStock = productMetrics[p.id]?.totalStock ?: 0
                )
            }

        // Inventory Health Ratios (60-day annualized)
        val sold60dItems = saleItems.filter { (saleMap[it.saleId]?.timestamp ?: 0L) >= now - 60 * DAY_MS }
        val unitsSold60d = sold60dItems.sumOf { it.quantity }
        val cogs60d = sold60dItems.sumOf { it.costPriceAtSale * it.quantity }
        val rev60d = sales.filter { it.timestamp >= now - 60 * DAY_MS }.sumOf { it.totalRevenue }
        val gp60d = rev60d - cogs60d
        val totalCurrentUnits = activeProducts.sumOf { productMetrics[it.id]?.totalStock ?: 0 }
        val totalInventoryCost = activeProducts.sumOf { productMetrics[it.id]?.stockCostValue ?: 0.0 }

        val sellThroughPct = if (unitsSold60d + totalCurrentUnits > 0) {
            (unitsSold60d.toDouble() / (unitsSold60d + totalCurrentUnits).toDouble()) * 100.0
        } else 0.0
        val annualizedCogs = cogs60d * (365.0 / 60.0)
        val stockTurnover = if (totalInventoryCost > 1.0) annualizedCogs / totalInventoryCost else 0.0
        val annualizedGp = gp60d * (365.0 / 60.0)
        val gmroi = if (totalInventoryCost > 1.0) annualizedGp / totalInventoryCost else 0.0
        val daysOfInventory = if (stockTurnover > 0.05) 365.0 / stockTurnover else 0.0

        // Buying Buckets (Category + Gender + PriceBand) — uses ALL products (including archived/retired) for demand memory!
        data class BucketKey(val category: String, val gender: String, val bandKey: String, val bandLabel: String)
        val bucketGroups = products.groupBy { p ->
            val (bKey, bLabel) = priceBandFor(p.sellingPrice, currency)
            BucketKey(p.category, p.gender, bKey, bLabel)
        }
        val buyingBuckets = mutableListOf<BuyingBucketSuggestion>()
        for ((bKey, bProducts) in bucketGroups) {
            val wBucket = IntArray(4)
            val sizeCounts90d = mutableMapOf<String, Int>()
            var sold30dBucket = 0
            var sold90dBucket = 0

            for (bp in bProducts) {
                val m = productMetrics[bp.id]
                sold30dBucket += m?.sold30d ?: 0
                sold90dBucket += m?.sold90d ?: 0
                for (item in itemsByProduct[bp.id].orEmpty()) {
                    val ts = saleMap[item.saleId]?.timestamp ?: continue
                    val daysAgo = ((now - ts) / DAY_MS).toInt()
                    if (daysAgo in 0..6) wBucket[0] += item.quantity
                    else if (daysAgo in 7..13) wBucket[1] += item.quantity
                    else if (daysAgo in 14..20) wBucket[2] += item.quantity
                    else if (daysAgo in 21..27) wBucket[3] += item.quantity

                    if (daysAgo in 0..90) {
                        sizeCounts90d[item.size] = (sizeCounts90d[item.size] ?: 0) + item.quantity
                    }
                }
            }

            val activeInBucket = bProducts.filter { !it.isArchived }
            val activeStockBucket = activeInBucket.sumOf { productMetrics[it.id]?.totalStock ?: 0 }
            val wmaWeeklyBucket = (wBucket[0] * 4.0 + wBucket[1] * 3.0 + wBucket[2] * 2.0 + wBucket[3] * 1.0) / 10.0
            val weekPatternBoost = ((5.0 + 2.0 * weekendRatio) / 7.0)
            val f30Wma = ceil(((wmaWeeklyBucket * weekPatternBoost * festivalMultiplier) / 7.0) * 30.0).toInt()
            val f30Bucket = maxOf(f30Wma, sold30dBucket)

            val avgDailyBucket = f30Bucket / 30.0
            val safetyBucket = if (avgDailyBucket > 0.02) {
                ceil(avgDailyBucket * 1.5 * sqrt(leadTime.toDouble())).toInt().coerceAtLeast(1)
            } else 0
            val targetBucketStock = ceil(avgDailyBucket * (14 + leadTime) + safetyBucket).toInt()
            val recommendedPairs = (targetBucketStock - activeStockBucket).coerceAtLeast(0)

            if (recommendedPairs > 0 && (sold30dBucket >= 2 || sold90dBucket >= 4)) {
                val targetSizes = sizeCounts90d.keys.ifEmpty {
                    bProducts.flatMap { variantsByProduct[it.id].orEmpty().map { v -> v.size } }.distinct()
                }.ifEmpty { listOf("7", "8", "9") }
                val totalSizeSold = sizeCounts90d.values.sum()
                val sizeShares = targetSizes.associateWith { sz ->
                    if (totalSizeSold > 0) (sizeCounts90d[sz] ?: 0).toDouble() / totalSizeSold.toDouble()
                    else 1.0 / targetSizes.size.toDouble()
                }
                val sizeSplit = linkedMapOf<String, Int>()
                var rem = recommendedPairs
                val sortedSizes = targetSizes.sortedByDescending { sizeShares[it] ?: 0.0 }
                for ((idx, sz) in sortedSizes.withIndex()) {
                    val alloc = if (idx == sortedSizes.lastIndex) rem else {
                        ((recommendedPairs * (sizeShares[sz] ?: 0.0)).roundToInt()).coerceAtMost(rem)
                    }
                    if (alloc > 0) sizeSplit[sz] = alloc
                    rem -= alloc
                }

                val minCost = bProducts.minOf { it.costPrice }
                val maxCost = bProducts.maxOf { it.costPrice }
                val avgCost = bProducts.map { it.costPrice }.average()
                val minSell = bProducts.minOf { it.sellingPrice }
                val maxSell = bProducts.maxOf { it.sellingPrice }
                val avgMargin = bProducts.map { (it.sellingPrice - it.costPrice).coerceAtLeast(0.0) }.average()

                val costRangeLabel = if ((maxCost - minCost) < 25.0) {
                    "~${Formatters.formatCurrency(avgCost, currency)}"
                } else {
                    "${Formatters.formatCurrency(minCost, currency)}–${Formatters.formatCurrency(maxCost, currency)}"
                }
                val sellRangeLabel = if ((maxSell - minSell) < 25.0) {
                    "~${Formatters.formatCurrency(minSell, currency)}"
                } else {
                    "${Formatters.formatCurrency(minSell, currency)}–${Formatters.formatCurrency(maxSell, currency)}"
                }

                val pastVendorIds = bProducts.mapNotNull { it.vendorId }.toSet()
                val matchedVendors = vendors.filter { v ->
                    v.id in pastVendorIds ||
                        v.specialtiesCsv.split(",").any { it.trim().equals(bKey.category, ignoreCase = true) }
                }.sortedByDescending { it.id in pastVendorIds }

                val retiredCount = bProducts.count { it.isArchived }
                val historyNote = if (retiredCount > 0) " (includes $retiredCount finished lot)" else ""
                val reason = "Sold $sold30dBucket pairs in 30d$historyNote. Only $activeStockBucket left across ${activeInBucket.size} active lot(s) vs ${targetBucketStock}-pair target."

                buyingBuckets += BuyingBucketSuggestion(
                    bucketKey = "${bKey.category}|${bKey.gender}|${bKey.bandKey}",
                    category = bKey.category,
                    gender = bKey.gender,
                    priceBandLabel = bKey.bandLabel,
                    costRangeLabel = costRangeLabel,
                    sellRangeLabel = sellRangeLabel,
                    sold30d = sold30dBucket,
                    sold90d = sold90dBucket,
                    activeStock = activeStockBucket,
                    forecast30d = f30Bucket,
                    recommendedPairs = recommendedPairs,
                    sizeSplit = sizeSplit,
                    estimatedBudget = recommendedPairs * avgCost,
                    expectedProfit = recommendedPairs * avgMargin,
                    matchingVendors = matchedVendors,
                    reason = reason
                )
            }
        }
        val sortedBuyingBuckets = buyingBuckets.sortedByDescending { it.recommendedPairs }

        // "What should I do this week?" top 3-5 actions
        val weeklyActions = mutableListOf<WeeklyAction>()
        val urgentAItems = abcItems.filter { it.abcClass == AbcClass.A && it.currentStock <= it.product.reorderLevel }
        if (urgentAItems.isNotEmpty()) {
            val topA = urgentAItems.first()
            if (topA.product.isRepeatable) {
                weeklyActions += WeeklyAction(
                    title = "Restock ${topA.product.skuCode} (${topA.product.name}) immediately",
                    detail = "Top A-grade staple only has ${topA.currentStock} pairs left. Never run out of this item!",
                    priorityTag = "URGENT"
                )
            } else {
                weeklyActions += WeeklyAction(
                    title = "Source fresh lot like ${topA.product.skuCode} (${topA.product.name})",
                    detail = "Top A-grade one-time lot is down to ${topA.currentStock} pairs. Plan a similar ${topA.product.category} batch!",
                    priorityTag = "FRESH LOT"
                )
            }
        }
        val topBucket = sortedBuyingBuckets.firstOrNull()
        if (topBucket != null) {
            weeklyActions += WeeklyAction(
                title = "Buy ${topBucket.recommendedPairs} pairs of ${topBucket.gender} ${topBucket.category} (${topBucket.priceBandLabel})",
                detail = topBucket.reason,
                priorityTag = "BUYING PLAN"
            )
        }
        val topGap = allSizeGaps.maxByOrNull { it.shortage }
        if (topGap != null) {
            weeklyActions += WeeklyAction(
                title = "Low on Size ${topGap.size} in ${topGap.sku} (${topGap.productName})",
                detail = "Demand next 30d is ${topGap.forecastDemand30d} pairs, stock is ${topGap.currentStock} (short by ${topGap.shortage}).",
                priorityTag = "SIZE GAP"
            )
        }
        val topDead = deadSlowActions.maxByOrNull { it.capitalLocked }
        if (topDead != null) {
            weeklyActions += WeeklyAction(
                title = "Clear ${topDead.product.skuCode} to free ${Formatters.formatCurrency(topDead.capitalLocked)}",
                detail = topDead.suggestedAction,
                priorityTag = "FREE CASH"
            )
        }
        val topPrice = pricingSuggestions.firstOrNull()
        if (topPrice != null) {
            weeklyActions += WeeklyAction(
                title = topPrice.headline,
                detail = topPrice.reason,
                priorityTag = "MARGIN"
            )
        }
        val repeatOrderCount = reorderSuggestions.count { it.product.isRepeatable && (it.group == ReorderGroup.BUY_MORE || it.group == ReorderGroup.BUY_SAME) && it.recommendedQty > 0 }
        if (repeatOrderCount > 0) {
            val budget = reorderSuggestions
                .filter { it.product.isRepeatable && (it.group == ReorderGroup.BUY_MORE || it.group == ReorderGroup.BUY_SAME) }
                .sumOf { it.estimatedCost }
            weeklyActions += WeeklyAction(
                title = "Restock $repeatOrderCount repeatable staple(s)",
                detail = "Estimated staple restock cost ${Formatters.formatCurrency(budget)}. Check Buying Plan tab.",
                priorityTag = "STAPLE"
            )
        }

        return InsightsReport(
            weeklyActions = weeklyActions.take(5),
            confidence = globalConfidence,
            productForecasts = productForecasts.sortedByDescending { it.forecast30d },
            categoryForecasts = categoryForecasts,
            criticalSizeGaps = allSizeGaps.sortedByDescending { it.shortage }.take(15),
            reorderSuggestions = reorderSuggestions.sortedWith(compareBy({ it.group.ordinal }, { -it.recommendedQty })),
            buyingBuckets = sortedBuyingBuckets,
            deadSlowActions = deadSlowActions.sortedByDescending { it.capitalLocked },
            pricingSuggestions = pricingSuggestions,
            bestSizes = bestSizes,
            bestDaysOfWeek = bestDaysOfWeek,
            bestCategoriesByProfit = bestCategoriesByProfit,
            abcItems = abcItems,
            sellThroughRatePct = sellThroughPct,
            stockTurnoverAnnual = stockTurnover,
            gmroi = gmroi,
            daysOfInventory = daysOfInventory
        )
    }

    fun classifyCustomerSegments(
        customer: CustomerEntity,
        allCustomers: List<CustomerEntity>,
        now: Long = System.currentTimeMillis()
    ): Set<CustomerSegmentType> {
        val result = mutableSetOf<CustomerSegmentType>()
        val sortedSpends = allCustomers.map { it.totalSpent }.sortedDescending()
        val top20Threshold = if (sortedSpends.isNotEmpty()) {
            sortedSpends[(sortedSpends.size * 0.2).toInt().coerceAtMost(sortedSpends.lastIndex)]
        } else 5000.0

        if (customer.totalSpent >= 5000.0 || (customer.totalSpent > 0 && customer.totalSpent >= top20Threshold)) {
            result += CustomerSegmentType.VIP
        }
        if (customer.visitCount >= 3) {
            result += CustomerSegmentType.REGULAR
        }
        if (customer.visitCount <= 1 && (now - customer.firstVisitAt) <= 30 * DAY_MS) {
            result += CustomerSegmentType.NEW
        }
        if ((now - customer.lastVisitAt) > 60 * DAY_MS) {
            result += CustomerSegmentType.LAPSED
        }
        if (customer.creditBalance > 0.5) {
            result += CustomerSegmentType.CREDIT_PENDING
        }
        return result
    }

    fun findSimilarInStock(
        targetProduct: ProductEntity?,
        desiredSize: String?,
        desiredCategory: String?,
        products: List<ProductEntity>,
        variants: List<ProductVariantEntity>,
        limit: Int = 4
    ): List<SimilarProductSuggestion> {
        val byProduct = variants.groupBy { it.productId }
        val refCat = targetProduct?.category ?: desiredCategory
        val refColor = targetProduct?.colorTag.orEmpty()
        val refStyle = targetProduct?.styleTag.orEmpty()
        val refGender = targetProduct?.gender.orEmpty()
        val refPrice = targetProduct?.sellingPrice ?: 0.0

        return products
            .asSequence()
            .filter { !it.isArchived && it.id != targetProduct?.id }
            .mapNotNull { candidate ->
                val pVars = byProduct[candidate.id].orEmpty()
                val stockInSize = if (!desiredSize.isNullOrBlank()) {
                    pVars.firstOrNull { it.size == desiredSize }?.stock ?: 0
                } else {
                    pVars.sumOf { it.stock }
                }
                if (stockInSize <= 0) return@mapNotNull null

                var score = 10
                val reasons = mutableListOf<String>()
                if (!refCat.isNullOrBlank() && candidate.category == refCat) {
                    score += 45
                    reasons += candidate.category
                }
                if (refColor.isNotBlank() && candidate.colorTag.equals(refColor, ignoreCase = true)) {
                    score += 25
                    reasons += candidate.colorTag
                }
                if (refStyle.isNotBlank() && candidate.styleTag.equals(refStyle, ignoreCase = true)) {
                    score += 15
                    reasons += candidate.styleTag
                }
                if (refGender.isNotBlank() && (candidate.gender == refGender || candidate.gender == "Unisex")) {
                    score += 10
                }
                if (refPrice > 0.0) {
                    val diffPct = kotlin.math.abs(candidate.sellingPrice - refPrice) / refPrice
                    if (diffPct <= 0.30) score += 15
                }

                val sizeText = if (!desiredSize.isNullOrBlank()) "$stockInSize in Sz $desiredSize" else "$stockInSize left"
                reasons += sizeText
                SimilarProductSuggestion(
                    product = candidate,
                    availableStock = stockInSize,
                    matchScore = score,
                    reason = reasons.joinToString(" • ")
                )
            }
            .sortedByDescending { it.matchScore }
            .take(limit)
            .toList()
    }

    data class ChannelFinancialBreakdown(
        val retailRevenue: Double,
        val retailGrossProfit: Double,
        val retailBillCount: Int,
        val wholesaleRevenue: Double,
        val wholesaleGrossProfit: Double,
        val wholesaleBillCount: Int
    )

    fun computeChannelBreakdown(
        startTime: Long,
        endTime: Long,
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        returns: List<ReturnEntity>
    ): ChannelFinancialBreakdown {
        val periodSales = sales.filter { it.timestamp in startTime..endTime }
        val itemsBySale = saleItems.groupBy { it.saleId }
        val saleChannelById = sales.associate { it.id to it.saleChannel }
        val periodReturns = returns.filter { it.type == "RETURN" && it.timestamp in startTime..endTime }

        fun calcFor(channel: String): Triple<Double, Double, Int> {
            val chSales = periodSales.filter { it.saleChannel.equals(channel, ignoreCase = true) }
            val chReturns = periodReturns.filter { (saleChannelById[it.saleId] ?: "RETAIL").equals(channel, ignoreCase = true) }
            val rev = chSales.sumOf { it.totalRevenue } - chReturns.sumOf { it.refundAmount }
            val cogs = chSales.sumOf { s ->
                itemsBySale[s.id].orEmpty().sumOf { it.costPriceAtSale * it.quantity }
            } - chReturns.sumOf { it.costReversed }
            return Triple(rev, rev - cogs, chSales.size)
        }

        val (rRev, rGp, rCount) = calcFor("RETAIL")
        val (wRev, wGp, wCount) = calcFor("WHOLESALE")
        return ChannelFinancialBreakdown(
            retailRevenue = rRev,
            retailGrossProfit = rGp,
            retailBillCount = rCount,
            wholesaleRevenue = wRev,
            wholesaleGrossProfit = wGp,
            wholesaleBillCount = wCount
        )
    }
}
