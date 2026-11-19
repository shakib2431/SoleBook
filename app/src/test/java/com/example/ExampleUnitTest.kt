package com.example

import com.example.data.local.*
import com.example.domain.AnalyticsEngine
import com.example.util.Formatters
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `indian currency formatting works accurately`() {
        assertEquals("₹1,25,000", Formatters.formatCurrency(125000.0))
        assertEquals("₹499", Formatters.formatCurrency(499.0))
        assertEquals("-₹12,34,567", Formatters.formatCurrency(-1234567.0))
        assertEquals("₹0", Formatters.formatCurrency(0.0))
    }

    @Test
    fun `financial reconciliation holds with sales returns and expenses`() {
        val now = 1_750_000_000_000L
        val prod = ProductEntity(
            id = 1L,
            skuCode = "SL-001",
            name = "Kolhapuri",
            category = "Chappal",
            brand = "Desi",
            gender = "Men",
            photoUri = "",
            costPrice = 300.0,
            sellingPrice = 500.0,
            createdAt = now - 40 * 86_400_000L
        )
        val variants = listOf(ProductVariantEntity(1L, 1L, "8", 5))
        val sales = listOf(
            SaleEntity(
                id = 1L,
                billNumber = "BILL-0001",
                timestamp = now - 3600_000L,
                paymentMode = "UPI",
                discountAmount = 50.0,
                subtotal = 1000.0,
                totalRevenue = 950.0,
                totalCollected = 950.0
            )
        )
        val saleItems = listOf(
            SaleItemEntity(
                id = 1L,
                saleId = 1L,
                productId = 1L,
                productNameSnapshot = "Kolhapuri",
                skuSnapshot = "SL-001",
                categorySnapshot = "Chappal",
                size = "8",
                quantity = 2,
                costPriceAtSale = 300.0,
                sellingPriceAtSale = 500.0,
                lineTotal = 1000.0
            )
        )
        // Return 1 pair (proportional refund = 475, cost reversed = 300)
        val returns = listOf(
            ReturnEntity(
                id = 1L,
                saleId = 1L,
                productId = 1L,
                productNameSnapshot = "Kolhapuri",
                size = "8",
                quantity = 1,
                refundAmount = 475.0,
                costReversed = 300.0,
                type = "RETURN",
                timestamp = now - 1800_000L
            )
        )
        val expenses = listOf(
            ExpenseEntity(id = 1L, category = "Rent", amount = 100.0, isRecurringMonthly = true, timestamp = now - 7200_000L)
        )

        val metrics = AnalyticsEngine.computeProductMetrics(listOf(prod), variants, sales, saleItems, returns, now)
        val summary = AnalyticsEngine.computeMoneySummary(
            periodLabel = "Today",
            startTime = now - 86_400_000L,
            endTime = now,
            products = listOf(prod),
            variants = variants,
            sales = sales,
            saleItems = saleItems,
            returns = returns,
            expenses = expenses,
            productMetrics = metrics,
            now = now
        )

        // Revenue = 950 - 475 = 475; COGS = 600 - 300 = 300; Gross Profit = 175; Net Profit = 175 - 100 = 75
        assertEquals(475.0, summary.revenue, 0.01)
        assertEquals(300.0, summary.cogs, 0.01)
        assertEquals(summary.revenue - summary.cogs, summary.grossProfit, 0.01)
        assertEquals(175.0, summary.grossProfit, 0.01)
        assertEquals(summary.grossProfit - summary.expenses, summary.netProfit, 0.01)
        assertEquals(75.0, summary.netProfit, 0.01)
    }

    @Test
    fun `analytics engine handles 5000 sales swiftly`() {
        val now = 1_750_000_000_000L
        val prod = ProductEntity(
            id = 1L, skuCode = "SL-001", name = "Test", category = "Chappal",
            brand = "B", gender = "Men", photoUri = "", costPrice = 200.0, sellingPrice = 400.0,
            createdAt = now - 90 * 86_400_000L
        )
        val variants = listOf(ProductVariantEntity(1L, 1L, "8", 10))
        val sales = (1L..5000L).map { idx ->
            SaleEntity(
                id = idx, billNumber = "B-$idx", timestamp = now - (idx % 60) * 86_400_000L,
                paymentMode = "CASH", subtotal = 400.0, totalRevenue = 400.0, totalCollected = 400.0
            )
        }
        val items = (1L..5000L).map { idx ->
            SaleItemEntity(
                id = idx, saleId = idx, productId = 1L, productNameSnapshot = "Test", skuSnapshot = "SL-001",
                categorySnapshot = "Chappal", size = "8", quantity = 1, costPriceAtSale = 200.0,
                sellingPriceAtSale = 400.0, lineTotal = 400.0
            )
        }

        val startMs = System.currentTimeMillis()
        val metrics = AnalyticsEngine.computeProductMetrics(listOf(prod), variants, sales, items, emptyList(), now)
        val insights = AnalyticsEngine.computeInsightsReport(
            listOf(prod), variants, sales, items, emptyList(), ShopSettingsEntity(), metrics, now
        )
        val elapsed = System.currentTimeMillis() - startMs

        assertEquals(5000, metrics[1L]?.totalUnitsSold)
        assertTrue("Expected analytics on 5000 sales under 2000ms, took ${elapsed}ms", elapsed < 2000)
        assertTrue(insights.productForecasts.isNotEmpty())
    }

    @Test
    fun `findSimilarInStock ranks same category color and available size first`() {
        val target = ProductEntity(
            id = 1L, skuCode = "SL-001", name = "Tan Kolhapuri", category = "Chappal",
            brand = "B1", gender = "Men", photoUri = "", costPrice = 300.0, sellingPrice = 550.0,
            colorTag = "Tan", styleTag = "Leather"
        )
        val bestAlt = ProductEntity(
            id = 2L, skuCode = "SL-002", name = "Tan Strap Chappal", category = "Chappal",
            brand = "B2", gender = "Men", photoUri = "", costPrice = 320.0, sellingPrice = 590.0,
            colorTag = "Tan", styleTag = "Leather"
        )
        val otherCat = ProductEntity(
            id = 3L, skuCode = "SL-003", name = "Black Running", category = "Sports Shoe",
            brand = "B3", gender = "Men", photoUri = "", costPrice = 500.0, sellingPrice = 999.0,
            colorTag = "Black", styleTag = "Mesh"
        )
        val outInSize = ProductEntity(
            id = 4L, skuCode = "SL-004", name = "Tan Ortho Chappal", category = "Chappal",
            brand = "B4", gender = "Men", photoUri = "", costPrice = 350.0, sellingPrice = 600.0,
            colorTag = "Tan", styleTag = "Ortho"
        )
        val variants = listOf(
            ProductVariantEntity(1L, 1L, "8", 0),
            ProductVariantEntity(2L, 2L, "8", 3),
            ProductVariantEntity(3L, 3L, "8", 5),
            ProductVariantEntity(4L, 4L, "8", 0),
            ProductVariantEntity(5L, 4L, "9", 4)
        )

        val alts = AnalyticsEngine.findSimilarInStock(
            targetProduct = target,
            desiredSize = "8",
            desiredCategory = target.category,
            products = listOf(target, bestAlt, otherCat, outInSize),
            variants = variants
        )

        assertEquals(2, alts.size)
        assertEquals("SL-002", alts.first().product.skuCode)
        assertEquals(3, alts.first().availableStock)
        assertTrue(alts.none { it.product.skuCode == "SL-004" || it.product.skuCode == "SL-001" })
    }

    @Test
    fun `notice engine generates all priority notice types and enforces anti-noise rules`() {
        val dayMs = 86_400_000L
        val cal = java.util.Calendar.getInstance().apply {
            set(2025, java.util.Calendar.MAY, 15, 14, 0, 0) // 2 PM (outside quiet hours)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis

        val pOut = ProductEntity(
            id = 1L, skuCode = "SL-001", name = "Out Sandal", category = "Sandal",
            brand = "B1", gender = "Men", photoUri = "", costPrice = 300.0, sellingPrice = 500.0,
            createdAt = now - 30 * dayMs
        )
        val pLowMarginHot = ProductEntity(
            id = 2L, skuCode = "SL-002", name = "Fast Slipper", category = "Chappal",
            brand = "B2", gender = "Men", photoUri = "", costPrice = 420.0, sellingPrice = 490.0,
            reorderLevel = 4, createdAt = now - 30 * dayMs
        )
        val pDead = ProductEntity(
            id = 3L, skuCode = "SL-003", name = "Old Boot", category = "Formal",
            brand = "B3", gender = "Men", photoUri = "", costPrice = 900.0, sellingPrice = 1800.0,
            createdAt = now - 90 * dayMs
        )

        val variants = listOf(
            ProductVariantEntity(1L, 1L, "8", 0),
            ProductVariantEntity(2L, 2L, "8", 2),
            ProductVariantEntity(3L, 3L, "8", 6)
        )
        val sales = (1L..6L).map { idx ->
            SaleEntity(
                id = idx, billNumber = "B-$idx",
                timestamp = if (idx == 6L) now - 3_600_000L else now - idx * dayMs,
                paymentMode = "CASH", subtotal = 490.0, totalRevenue = 490.0, totalCollected = 490.0
            )
        }
        val saleItems = (1L..6L).map { idx ->
            SaleItemEntity(
                id = idx, saleId = idx, productId = 2L,
                productNameSnapshot = "Fast Slipper", skuSnapshot = "SL-002",
                categorySnapshot = "Chappal", size = "8", quantity = 1,
                costPriceAtSale = 420.0, sellingPriceAtSale = 490.0, lineTotal = 490.0
            )
        }

        val metrics = AnalyticsEngine.computeProductMetrics(
            listOf(pOut, pLowMarginHot, pDead), variants, sales, saleItems, emptyList(), now
        )
        val settings = ShopSettingsEntity(
            alertsEnabled = true,
            instantSaleAlertsEnabled = true,
            maxAlertsPerDay = 2,
            quietStartHour = 21,
            quietEndHour = 8
        )

        val notices = com.example.domain.NoticeEngine.generateNotices(
            products = listOf(pOut, pLowMarginHot, pDead),
            variants = variants,
            sales = sales,
            saleItems = saleItems,
            productMetrics = metrics,
            noticeStates = emptyMap(),
            settings = settings,
            now = now
        )

        val categories = notices.map { it.category }.toSet()
        assertTrue(com.example.domain.NoticeCategory.OUT_OF_STOCK in categories)
        assertTrue(com.example.domain.NoticeCategory.LOW_STOCK in categories)
        assertTrue(com.example.domain.NoticeCategory.HIGH_DEMAND in categories)
        assertTrue(com.example.domain.NoticeCategory.PRICE_MARGIN in categories)
        assertTrue(com.example.domain.NoticeCategory.DEAD_STOCK in categories)
        assertTrue(com.example.domain.NoticeCategory.SOLD_ALERT in categories)

        // Suggested selling price on low-margin fast mover should be > current 490
        val marginNotice = notices.first { it.noticeKey == "MARGIN_UP:2" }
        assertNotNull(marginNotice.suggestedSellingPrice)
        assertTrue(marginNotice.suggestedSellingPrice!! > 490.0)

        // Pick top candidate when none notified today
        val firstPick = com.example.domain.NoticeEngine.pickNextNotificationCandidate(
            notices = notices,
            noticeStates = emptyMap(),
            settings = settings,
            now = now,
            allowImmediateSale = false
        )
        assertNotNull(firstPick)
        assertEquals(com.example.domain.NoticeCategory.OUT_OF_STOCK, firstPick!!.category)

        // Daily cap enforcement (2 already sent today -> null)
        val statesAtCap = mapOf(
            "OUT:1:ALL" to NoticeStateEntity("OUT:1:ALL", lastNotifiedAt = now - 600_000L, lastSignatureHash = notices.first { it.noticeKey == "OUT:1:ALL" }.signatureHash),
            "LOW:2" to NoticeStateEntity("LOW:2", lastNotifiedAt = now - 300_000L, lastSignatureHash = notices.first { it.noticeKey == "LOW:2" }.signatureHash)
        )
        val cappedPick = com.example.domain.NoticeEngine.pickNextNotificationCandidate(
            notices = notices,
            noticeStates = statesAtCap,
            settings = settings,
            now = now,
            allowImmediateSale = true
        )
        assertNull(cappedPick)

        // Quiet hours enforcement (11 PM -> null)
        val nightCal = java.util.Calendar.getInstance().apply {
            set(2025, java.util.Calendar.MAY, 15, 23, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val quietPick = com.example.domain.NoticeEngine.pickNextNotificationCandidate(
            notices = notices,
            noticeStates = emptyMap(),
            settings = settings,
            now = nightCal.timeInMillis
        )
        assertNull(quietPick)
    }

    @Test
    fun `buying buckets preserve 90d demand after one-time lot retirement and match vendors`() {
        val dayMs = 86_400_000L
        val now = 1_750_000_000_000L
        val vendor = VendorEntity(
            id = 10L,
            name = "Agra Footwear Co",
            phone = "9811122233",
            cityMarket = "Agra",
            specialtiesCsv = "Loafer, Casual Shoe"
        )
        val retiredHotLot = ProductEntity(
            id = 1L, skuCode = "SL-101", name = "Finished Tan Loafer Lot",
            category = "Loafer", brand = "B1", gender = "Men", photoUri = "",
            costPrice = 450.0, sellingPrice = 799.0, isRepeatable = false,
            vendorId = 10L, lotCode = "LOT-JAN", isArchived = true,
            createdAt = now - 40 * dayMs
        )
        val activeLowLot = ProductEntity(
            id = 2L, skuCode = "SL-102", name = "Current Tan Loafer Lot",
            category = "Loafer", brand = "B2", gender = "Men", photoUri = "",
            costPrice = 460.0, sellingPrice = 849.0, isRepeatable = false,
            vendorId = 10L, lotCode = "LOT-FEB", isArchived = false,
            createdAt = now - 15 * dayMs
        )
        val variants = listOf(
            ProductVariantEntity(1L, 1L, "8", 0),
            ProductVariantEntity(2L, 1L, "9", 0),
            ProductVariantEntity(3L, 2L, "8", 1),
            ProductVariantEntity(4L, 2L, "9", 1)
        )
        val sales = (1L..10L).map { idx ->
            SaleEntity(
                id = idx, billNumber = "B-$idx", timestamp = now - idx * 2 * dayMs,
                paymentMode = "CASH", subtotal = 800.0, totalRevenue = 800.0, totalCollected = 800.0
            )
        }
        val items = (1L..10L).map { idx ->
            SaleItemEntity(
                id = idx, saleId = idx,
                productId = if (idx <= 7L) 1L else 2L,
                productNameSnapshot = "Loafer", skuSnapshot = if (idx <= 7L) "SL-101" else "SL-102",
                categorySnapshot = "Loafer", size = if (idx % 2L == 0L) "8" else "9",
                quantity = 1, costPriceAtSale = 450.0, sellingPriceAtSale = 800.0, lineTotal = 800.0
            )
        }

        val metrics = AnalyticsEngine.computeProductMetrics(
            listOf(retiredHotLot, activeLowLot), variants, sales, items, emptyList(), now
        )
        val report = AnalyticsEngine.computeInsightsReport(
            products = listOf(retiredHotLot, activeLowLot),
            variants = variants,
            sales = sales,
            saleItems = items,
            returns = emptyList(),
            settings = ShopSettingsEntity(),
            productMetrics = metrics,
            now = now,
            vendors = listOf(vendor)
        )

        val loaferBucket = report.buyingBuckets.firstOrNull { it.category == "Loafer" && it.gender == "Men" }
        assertNotNull(loaferBucket)
        assertEquals(10, loaferBucket!!.sold30d)
        assertEquals(2, loaferBucket.activeStock)
        assertTrue(loaferBucket.recommendedPairs > 0)
        assertTrue(loaferBucket.sizeSplit.containsKey("8") && loaferBucket.sizeSplit.containsKey("9"))
        assertTrue(loaferBucket.matchingVendors.any { it.id == 10L })
    }

    @Test
    fun `notice engine routes one-time lots to SOURCE_SIMILAR_LOT and repeat staples to RESTOCK`() {
        val dayMs = 86_400_000L
        val now = 1_750_000_000_000L
        val oneTimeSoldOut = ProductEntity(
            id = 1L, skuCode = "SL-201", name = "One-Time Sneaker",
            category = "Sports Shoe", brand = "B1", gender = "Men", photoUri = "",
            costPrice = 500.0, sellingPrice = 999.0, isRepeatable = false, createdAt = now - 20 * dayMs
        )
        val repeatSoldOut = ProductEntity(
            id = 2L, skuCode = "SL-202", name = "Core School Shoe",
            category = "School", brand = "B2", gender = "Kids", photoUri = "",
            costPrice = 300.0, sellingPrice = 599.0, isRepeatable = true, createdAt = now - 20 * dayMs
        )
        val variants = listOf(
            ProductVariantEntity(1L, 1L, "8", 0),
            ProductVariantEntity(2L, 2L, "4", 0)
        )
        val metrics = AnalyticsEngine.computeProductMetrics(
            listOf(oneTimeSoldOut, repeatSoldOut), variants, emptyList(), emptyList(), emptyList(), now
        )
        val fakeBucket = com.example.domain.BuyingBucketSuggestion(
            bucketKey = "Sports Shoe|Men|MID",
            category = "Sports Shoe",
            gender = "Men",
            priceBandLabel = "Mid (₹500–₹999)",
            costRangeLabel = "~₹500",
            sellRangeLabel = "~₹999",
            sold30d = 8,
            sold90d = 12,
            activeStock = 0,
            forecast30d = 10,
            recommendedPairs = 8,
            sizeSplit = mapOf("8" to 4, "9" to 4),
            estimatedBudget = 4000.0,
            expectedProfit = 3992.0,
            matchingVendors = emptyList(),
            reason = "Sold 8 pairs in 30d; 0 left."
        )

        val notices = com.example.domain.NoticeEngine.generateNotices(
            products = listOf(oneTimeSoldOut, repeatSoldOut),
            variants = variants,
            sales = emptyList(),
            saleItems = emptyList(),
            productMetrics = metrics,
            noticeStates = emptyMap(),
            settings = ShopSettingsEntity(),
            now = now,
            buyingBuckets = listOf(fakeBucket)
        )

        val oneTimeNotice = notices.first { it.noticeKey == "OUT:1:ALL" }
        assertEquals(com.example.domain.NoticeActionType.SOURCE_SIMILAR_LOT, oneTimeNotice.actionType)
        assertFalse(oneTimeNotice.isRepeatableProduct)

        val repeatNotice = notices.first { it.noticeKey == "OUT:2:ALL" }
        assertEquals(com.example.domain.NoticeActionType.RESTOCK, repeatNotice.actionType)
        assertTrue(repeatNotice.isRepeatableProduct)

        val bucketNotice = notices.firstOrNull { it.noticeKey.startsWith("BUCKET_LOW:") }
        assertNotNull(bucketNotice)
        assertEquals(com.example.domain.NoticeActionType.SOURCE_SIMILAR_LOT, bucketNotice!!.actionType)
    }

    @Test
    fun `industry profile configures attributes for grocery clothing footwear electronics cosmetics and custom other`() {
        val grocery = com.example.domain.IndustryProfile.resolve("GROCERY")
        assertEquals("Grocery & Kirana", grocery.displayCategoryName)
        assertEquals("pack", grocery.unitSingular)
        assertEquals("Pack Size", grocery.variantLabel)
        assertTrue(grocery.showUnitField)
        assertTrue(grocery.showBatchOrSerialField)
        assertTrue(grocery.showExpiryOrWarrantyField)
        assertFalse(grocery.showColorTag)

        val clothing = com.example.domain.IndustryProfile.resolve("CLOTHING")
        assertEquals("piece", clothing.unitSingular)
        assertEquals("Size", clothing.variantLabel)
        assertTrue(clothing.showColorTag)
        assertTrue(clothing.showAudienceSegmentField)
        assertFalse(clothing.showExpiryOrWarrantyField)

        val footwear = com.example.domain.IndustryProfile.resolve("FOOTWEAR")
        assertEquals("pair", footwear.unitSingular)
        assertEquals("Shoe Size", footwear.variantLabel)
        assertEquals("Shoe Size", footwear.customerPrefLabel)

        val electronics = com.example.domain.IndustryProfile.resolve("ELECTRONICS")
        assertEquals("unit", electronics.unitSingular)
        assertEquals("Model / Spec", electronics.variantLabel)
        assertTrue(electronics.showBatchOrSerialField)
        assertTrue(electronics.showExpiryOrWarrantyField)

        val cosmetics = com.example.domain.IndustryProfile.resolve("COSMETICS")
        assertEquals("Shade / Volume", cosmetics.variantLabel)
        assertTrue(cosmetics.showColorTag)
        assertTrue(cosmetics.showExpiryOrWarrantyField)

        val customOther = com.example.domain.IndustryProfile.resolve("OTHER", "Artisanal Bakery")
        assertEquals("Artisanal Bakery", customOther.displayCategoryName)
        assertEquals("Variant", customOther.variantLabel)
    }
}
