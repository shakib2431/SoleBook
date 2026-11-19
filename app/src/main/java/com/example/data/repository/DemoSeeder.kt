package com.example.data.repository

import com.example.data.local.*
import java.util.Calendar
import kotlin.random.Random

object DemoSeeder {

    fun defaultSegments(): List<SegmentEntity> = listOf(
        SegmentEntity(1, "VIP Spenders", "VIP", "Customers who spent ₹5,000+ or top 20%"),
        SegmentEntity(2, "Regulars", "REGULAR", "Visited 3 or more times"),
        SegmentEntity(3, "New Buyers", "NEW", "First visit in the last 30 days"),
        SegmentEntity(4, "Lapsed (60+ days)", "LAPSED", "No visit in over 60 days — bring them back"),
        SegmentEntity(5, "Credit Pending (Udhaar)", "CREDIT_PENDING", "Customers with unsettled balance")
    )

    fun defaultTemplates(): List<MessageTemplateEntity> = listOf(
        MessageTemplateEntity(
            1,
            "VIP Early Access",
            "VIP",
            "Namaste {name}! Fresh stock of {fav_category} just arrived at {shop}. Being our VIP customer, get flat ₹100 off this weekend!"
        ),
        MessageTemplateEntity(
            2,
            "We Miss You (Lapsed)",
            "LAPSED",
            "Hello {name}, it's been a while since you visited {shop}! Show this message for a special 10% loyalty discount on your next pair."
        ),
        MessageTemplateEntity(
            3,
            "Festive Arrival (Regulars)",
            "REGULAR",
            "Hi {name}! New festive collection in Size {fav_size} has landed at {shop}. Drop by today to pick your favourite pair!"
        ),
        MessageTemplateEntity(
            4,
            "Gentle Udhaar Reminder",
            "CREDIT_PENDING",
            "Namaste {name}, friendly reminder from {shop} regarding your pending balance. You can pay via UPI or Cash anytime you visit!"
        )
    )

    suspend fun seedDemo(dao: SoleBookDao) {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        dao.clearSaleItems()
        dao.clearReturns()
        dao.clearSales()
        dao.clearPurchases()
        dao.clearVendorPayments()
        dao.clearVendors()
        dao.clearExpenses()
        dao.clearVariants()
        dao.clearFingerprints()
        dao.clearProducts()
        dao.clearCustomers()
        dao.clearCampaigns()
        dao.clearNoticeStates()

        if (dao.getAllSegmentsSync().isEmpty()) {
            dao.insertSegments(defaultSegments())
        }
        if (dao.getAllTemplatesSync().isEmpty()) {
            dao.insertTemplates(defaultTemplates())
        }

        // 4 Realistic Wholesale Footwear Vendors
        val vendors = listOf(
            VendorEntity(
                name = "Kolhapur & Agra Craft Hub",
                phone = "9822113344",
                whatsapp = "9822113344",
                cityMarket = "Agra / Kolhapur Mandi",
                specialtiesCsv = "Chappal,Formal,Casual",
                creditBalance = 4200.0,
                notes = "Main leather chappal & formal lot supplier (15-day credit)",
                lastPurchaseAt = now - 14 * dayMs,
                createdAt = now - 90 * dayMs
            ),
            VendorEntity(
                name = "Paragon & Relaxo Wholesale",
                phone = "9810224466",
                whatsapp = "9810224466",
                cityMarket = "Local Wholesale Market",
                specialtiesCsv = "Chappal,Sandal,Ladies",
                creditBalance = 0.0,
                notes = "Steady daily slippers & ladies flats",
                lastPurchaseAt = now - 10 * dayMs,
                createdAt = now - 90 * dayMs
            ),
            VendorEntity(
                name = "Delhi Sneaker & Sport Lot",
                phone = "9811889900",
                whatsapp = "9811889900",
                cityMarket = "Inderlok / Karol Bagh",
                specialtiesCsv = "Sports Shoe,Sandal,Casual",
                creditBalance = 6500.0,
                notes = "Fresh surplus sports shoe lots every 2 weeks",
                lastPurchaseAt = now - 18 * dayMs,
                createdAt = now - 85 * dayMs
            ),
            VendorEntity(
                name = "Bata & Kids Wholesale Depot",
                phone = "9871556677",
                whatsapp = "9871556677",
                cityMarket = "Station Road Depot",
                specialtiesCsv = "Kids,Formal,Sandal",
                creditBalance = 0.0,
                notes = "Kids school shoes & regular staples",
                lastPurchaseAt = now - 22 * dayMs,
                createdAt = now - 85 * dayMs
            )
        )
        val vendorIds = dao.insertVendors(vendors)
        val vCraft = vendorIds[0]
        val vParagon = vendorIds[1]
        val vDelhi = vendorIds[2]
        val vBata = vendorIds[3]

        // 15 Realistic Footwear Products (most are One-Time Lots; SL-002 and SL-012 are Repeatable Staples)
        val products = listOf(
            ProductEntity(
                skuCode = "SL-001", name = "Kolhapuri Heritage Tan", category = "Chappal",
                brand = "DesiCraft", gender = "Men", photoUri = "drawable:img_chappal_leather",
                costPrice = 260.0, sellingPrice = 499.0, mrp = 599.0, supplier = "Kolhapur & Agra Craft Hub",
                reorderLevel = 8, colorTag = "Tan", styleTag = "Leather", rackLocation = "Rack A-1",
                isRepeatable = false, vendorId = vCraft, lotCode = "LOT-K1",
                createdAt = now - 75 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-002", name = "CloudWalk Daily Slipper", category = "Chappal",
                brand = "Relaxo", gender = "Unisex", photoUri = "drawable:img_chappal_leather",
                costPrice = 185.0, sellingPrice = 230.0, mrp = 275.0, supplier = "Paragon & Relaxo Wholesale",
                reorderLevel = 10, colorTag = "Brown", styleTag = "Slip-On", rackLocation = "Rack A-2",
                isRepeatable = true, vendorId = vParagon, lotCode = "STAPLE",
                createdAt = now - 75 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-003", name = "TrailGrip Velcro Floater", category = "Sandal",
                brand = "Sparx", gender = "Men", photoUri = "drawable:img_sandal_sport",
                costPrice = 340.0, sellingPrice = 649.0, mrp = 799.0, supplier = "Delhi Sneaker & Sport Lot",
                reorderLevel = 8, colorTag = "Black", styleTag = "Velcro", rackLocation = "Wall Left-1",
                isRepeatable = false, vendorId = vDelhi, lotCode = "LOT-D3",
                createdAt = now - 70 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-004", name = "AeroMesh Runner Grey", category = "Sports Shoe",
                brand = "Campus", gender = "Men", photoUri = "drawable:img_shoe_running",
                costPrice = 620.0, sellingPrice = 1199.0, mrp = 1499.0, supplier = "Delhi Sneaker & Sport Lot",
                reorderLevel = 6, colorTag = "Grey", styleTag = "Mesh", rackLocation = "Wall Right-2",
                isRepeatable = false, vendorId = vDelhi, lotCode = "LOT-D4",
                createdAt = now - 70 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-005", name = "Regent Cognac Derby", category = "Formal",
                brand = "Red Chief", gender = "Men", photoUri = "drawable:img_shoe_formal",
                costPrice = 950.0, sellingPrice = 1699.0, mrp = 2199.0, supplier = "Kolhapur & Agra Craft Hub",
                reorderLevel = 5, colorTag = "Brown", styleTag = "Lace-Up", rackLocation = "Rack C-1",
                isRepeatable = false, vendorId = vCraft, lotCode = "LOT-A5",
                createdAt = now - 70 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-006", name = "SoftStep Ladies Flat", category = "Ladies",
                brand = "Paragon", gender = "Women", photoUri = "drawable:img_chappal_leather",
                costPrice = 210.0, sellingPrice = 420.0, mrp = 499.0, supplier = "Paragon & Relaxo Wholesale",
                reorderLevel = 8, colorTag = "Tan", styleTag = "Slip-On", rackLocation = "Rack B-1",
                isRepeatable = false, vendorId = vParagon, lotCode = "LOT-P6",
                createdAt = now - 68 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-007", name = "Junior Bounce Sandal", category = "Kids",
                brand = "Action", gender = "Kids", photoUri = "drawable:img_sandal_sport",
                costPrice = 190.0, sellingPrice = 399.0, mrp = 499.0, supplier = "Bata & Kids Wholesale Depot",
                reorderLevel = 6, colorTag = "Navy", styleTag = "Velcro", rackLocation = "Kids Rack-1",
                isRepeatable = false, vendorId = vBata, lotCode = "LOT-B7",
                createdAt = now - 65 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-008", name = "SprintPro Knit Sneaker", category = "Sports Shoe",
                brand = "Asian", gender = "Unisex", photoUri = "drawable:img_shoe_running",
                costPrice = 540.0, sellingPrice = 999.0, mrp = 1299.0, supplier = "Delhi Sneaker & Sport Lot",
                reorderLevel = 6, colorTag = "Grey", styleTag = "Lace-Up", rackLocation = "Wall Right-3",
                isRepeatable = false, vendorId = vDelhi, lotCode = "LOT-D8",
                createdAt = now - 65 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-009", name = "Executive Black Oxford", category = "Formal",
                brand = "Bata", gender = "Men", photoUri = "drawable:img_shoe_formal",
                costPrice = 780.0, sellingPrice = 1499.0, mrp = 1799.0, supplier = "Bata & Kids Wholesale Depot",
                reorderLevel = 4, colorTag = "Black", styleTag = "Lace-Up", rackLocation = "Rack C-2",
                isRepeatable = false, vendorId = vBata, lotCode = "LOT-B9",
                createdAt = now - 75 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-010", name = "Monsoon Guard Sandal", category = "Sandal",
                brand = "Paragon", gender = "Women", photoUri = "drawable:img_sandal_sport",
                costPrice = 220.0, sellingPrice = 449.0, mrp = 549.0, supplier = "Paragon & Relaxo Wholesale",
                reorderLevel = 6, colorTag = "Black", styleTag = "Buckle", rackLocation = "Rack B-3",
                isRepeatable = false, vendorId = vParagon, lotCode = "LOT-P10",
                createdAt = now - 65 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-011", name = "Velvet Wedding Mojari", category = "Formal",
                brand = "JaipurCraft", gender = "Men", photoUri = "drawable:img_shoe_formal",
                costPrice = 480.0, sellingPrice = 950.0, mrp = 1299.0, supplier = "Kolhapur & Agra Craft Hub",
                reorderLevel = 4, colorTag = "Brown", styleTag = "Mojari", rackLocation = "Top Shelf-2",
                isRepeatable = false, vendorId = vCraft, lotCode = "LOT-A11",
                createdAt = now - 85 * dayMs, updatedAt = now - 65 * dayMs
            ), // Dead stock (last sold > 60 days ago!)
            ProductEntity(
                skuCode = "SL-012", name = "SchoolMaster Velcro Black", category = "Kids",
                brand = "Bata", gender = "Kids", photoUri = "drawable:img_shoe_running",
                costPrice = 310.0, sellingPrice = 599.0, mrp = 699.0, supplier = "Bata & Kids Wholesale Depot",
                reorderLevel = 8, colorTag = "Black", styleTag = "Velcro", rackLocation = "Kids Rack-2",
                isRepeatable = true, vendorId = vBata, lotCode = "STAPLE",
                createdAt = now - 70 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-013", name = "Urban Loafer Slip-On", category = "Casual",
                brand = "Woodland", gender = "Men", photoUri = "drawable:img_shoe_formal",
                costPrice = 890.0, sellingPrice = 1599.0, mrp = 1999.0, supplier = "Kolhapur & Agra Craft Hub",
                reorderLevel = 5, colorTag = "Tan", styleTag = "Slip-On", rackLocation = "Rack C-4",
                isRepeatable = false, vendorId = vCraft, lotCode = "LOT-A13",
                createdAt = now - 70 * dayMs, updatedAt = now
            ), // High margin slow mover
            ProductEntity(
                skuCode = "SL-014", name = "Breeze Ortho Chappal", category = "Chappal",
                brand = "DoctorSoft", gender = "Women", photoUri = "drawable:img_chappal_leather",
                costPrice = 240.0, sellingPrice = 499.0, mrp = 599.0, supplier = "Paragon & Relaxo Wholesale",
                reorderLevel = 8, colorTag = "Brown", styleTag = "Ortho", rackLocation = "Rack B-2",
                isRepeatable = false, vendorId = vParagon, lotCode = "LOT-P14",
                createdAt = now - 68 * dayMs, updatedAt = now
            ),
            ProductEntity(
                skuCode = "SL-015", name = "TrekLite Hiking Sandal", category = "Sandal",
                brand = "WildStep", gender = "Men", photoUri = "drawable:img_sandal_sport",
                costPrice = 580.0, sellingPrice = 1099.0, mrp = 1399.0, supplier = "Delhi Sneaker & Sport Lot",
                reorderLevel = 5, colorTag = "Grey", styleTag = "Velcro", rackLocation = "Wall Left-4",
                isRepeatable = false, vendorId = vDelhi, lotCode = "LOT-D15",
                createdAt = now - 75 * dayMs, updatedAt = now
            ) // Out of stock demo
        )

        val productIds = dao.insertProducts(products)

        // Variants per product
        val variantMap = mutableMapOf<Long, List<ProductVariantEntity>>()
        val stockPresets = mapOf(
            "SL-001" to mapOf("6" to 4, "7" to 6, "8" to 2, "9" to 5, "10" to 3), // Size 8 low!
            "SL-002" to mapOf("6" to 8, "7" to 9, "8" to 3, "9" to 6, "10" to 4),
            "SL-003" to mapOf("6" to 3, "7" to 2, "8" to 1, "9" to 4, "10" to 2), // Low stock fast mover!
            "SL-004" to mapOf("7" to 3, "8" to 2, "9" to 4, "10" to 2),
            "SL-005" to mapOf("7" to 4, "8" to 5, "9" to 3, "10" to 2),
            "SL-006" to mapOf("4" to 5, "5" to 6, "6" to 7, "7" to 4, "8" to 2),
            "SL-007" to mapOf("1" to 4, "2" to 5, "3" to 3, "4" to 4),
            "SL-008" to mapOf("6" to 3, "7" to 4, "8" to 5, "9" to 2, "10" to 2),
            "SL-009" to mapOf("7" to 3, "8" to 4, "9" to 3, "10" to 2),
            "SL-010" to mapOf("5" to 4, "6" to 5, "7" to 4, "8" to 3),
            "SL-011" to mapOf("7" to 6, "8" to 8, "9" to 6, "10" to 4), // Lots of capital locked in dead stock
            "SL-012" to mapOf("1" to 5, "2" to 4, "3" to 2, "4" to 3),
            "SL-013" to mapOf("7" to 5, "8" to 6, "9" to 5, "10" to 3),
            "SL-014" to mapOf("5" to 3, "6" to 2, "7" to 3, "8" to 2),
            "SL-015" to mapOf("7" to 0, "8" to 0, "9" to 0, "10" to 0) // Out of stock!
        )

        val allVariants = mutableListOf<ProductVariantEntity>()
        products.forEachIndexed { idx, prod ->
            val pid = productIds[idx]
            val preset = stockPresets[prod.skuCode] ?: mapOf("7" to 4, "8" to 4, "9" to 4)
            val list = preset.map { (sz, qty) -> ProductVariantEntity(productId = pid, size = sz, stock = qty) }
            allVariants += list
            variantMap[pid] = list
        }
        dao.insertVariants(allVariants)

        // 10 Realistic Customers
        val customers = listOf(
            CustomerEntity(phone = "9820112233", name = "Rajesh Sharma", firstVisitAt = now - 58 * dayMs, lastVisitAt = now - 2 * dayMs, totalSpent = 6450.0, visitCount = 6, favouriteCategory = "Formal", favouriteSize = "8", specialDate = "15 Aug", notes = "Prefers wide fit", whatsappConsent = true, creditBalance = 0.0),
            CustomerEntity(phone = "9811445566", name = "Priya Nair", firstVisitAt = now - 55 * dayMs, lastVisitAt = now - 4 * dayMs, totalSpent = 5280.0, visitCount = 5, favouriteCategory = "Ladies", favouriteSize = "6", specialDate = "22 Nov", notes = "Buys for whole family", whatsappConsent = true, creditBalance = 0.0),
            CustomerEntity(phone = "9765223344", name = "Imran Khan", firstVisitAt = now - 48 * dayMs, lastVisitAt = now - 1 * dayMs, totalSpent = 3850.0, visitCount = 4, favouriteCategory = "Sports Shoe", favouriteSize = "9", whatsappConsent = true, creditBalance = 650.0),
            CustomerEntity(phone = "9898776655", name = "Sunita Patel", firstVisitAt = now - 42 * dayMs, lastVisitAt = now - 8 * dayMs, totalSpent = 2490.0, visitCount = 3, favouriteCategory = "Chappal", favouriteSize = "6", whatsappConsent = true, creditBalance = 0.0),
            CustomerEntity(phone = "9900112244", name = "Vikram Verma", firstVisitAt = now - 72 * dayMs, lastVisitAt = now - 64 * dayMs, totalSpent = 1850.0, visitCount = 2, favouriteCategory = "Formal", favouriteSize = "8", notes = "Lapsed VIP prospect", whatsappConsent = true, creditBalance = 0.0),
            CustomerEntity(phone = "9845667788", name = "Deepak Joshi", firstVisitAt = now - 35 * dayMs, lastVisitAt = now - 6 * dayMs, totalSpent = 2990.0, visitCount = 3, favouriteCategory = "Sandal", favouriteSize = "8", whatsappConsent = true, creditBalance = 499.0),
            CustomerEntity(phone = "9876543210", name = "Anita Deshmukh", firstVisitAt = now - 18 * dayMs, lastVisitAt = now - 18 * dayMs, totalSpent = 920.0, visitCount = 1, favouriteCategory = "Kids", favouriteSize = "2", whatsappConsent = true, creditBalance = 0.0),
            CustomerEntity(phone = "9922334455", name = "Suresh Yadav", firstVisitAt = now - 68 * dayMs, lastVisitAt = now - 62 * dayMs, totalSpent = 1499.0, visitCount = 1, favouriteCategory = "Formal", favouriteSize = "9", whatsappConsent = false, creditBalance = 0.0),
            CustomerEntity(phone = "9810998877", name = "Meera Iyer", firstVisitAt = now - 12 * dayMs, lastVisitAt = now - 5 * dayMs, totalSpent = 1798.0, visitCount = 2, favouriteCategory = "Chappal", favouriteSize = "7", whatsappConsent = true, creditBalance = 0.0),
            CustomerEntity(phone = "9712345678", name = "Arjun Malhotra", firstVisitAt = now - 6 * dayMs, lastVisitAt = now - 6 * dayMs, totalSpent = 1199.0, visitCount = 1, favouriteCategory = "Sports Shoe", favouriteSize = "8", whatsappConsent = true, creditBalance = 0.0)
        )
        val customerIds = dao.insertCustomers(customers)

        // Generate 60 days of realistic sales with weekend pattern + recent today sales
        val rng = Random(42)
        val paymentModes = listOf("CASH", "UPI", "UPI", "CASH", "CARD", "UPI")
        val salesToInsert = mutableListOf<SaleEntity>()
        val itemsPlan = mutableListOf<Pair<Int, List<SaleItemEntity>>>() // saleIdx -> items

        // Velocity weights per product index (0..14)
        val fastProductIndices = listOf(0, 1, 2, 3, 5, 7, 11, 13)
        val normalProductIndices = listOf(4, 6, 8, 9)
        val slowProductIndices = listOf(12) // SL-013 slow high margin

        var billCounter = 1
        for (daysAgo in 59 downTo 0) {
            val dayBase = now - daysAgo * dayMs
            val cal = Calendar.getInstance().apply { timeInMillis = dayBase }
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY)
            val billsToday = if (daysAgo == 0) 4 else if (isWeekend) rng.nextInt(4, 7) else rng.nextInt(2, 4)

            for (b in 0 until billsToday) {
                cal.set(Calendar.HOUR_OF_DAY, rng.nextInt(10, 21))
                cal.set(Calendar.MINUTE, rng.nextInt(0, 60))
                val ts = cal.timeInMillis

                val itemCount = if (rng.nextInt(10) < 3) 2 else 1
                val chosenItems = mutableListOf<SaleItemEntity>()
                var subtotal = 0.0

                for (k in 0 until itemCount) {
                    val roll = rng.nextInt(100)
                    val pIdx = when {
                        roll < 72 -> fastProductIndices[rng.nextInt(fastProductIndices.size)]
                        roll < 95 -> normalProductIndices[rng.nextInt(normalProductIndices.size)]
                        else -> slowProductIndices[rng.nextInt(slowProductIndices.size)]
                    }
                    val prod = products[pIdx]
                    val pid = productIds[pIdx]
                    val sizes = variantMap[pid]?.map { it.size } ?: listOf("8")
                    // Bias towards size 7 & 8
                    val chosenSize = if ("8" in sizes && rng.nextBoolean()) "8" else if ("7" in sizes && rng.nextBoolean()) "7" else sizes[rng.nextInt(sizes.size)]
                    val qty = 1
                    val bargainDelta = if (rng.nextInt(5) == 0 && prod.sellingPrice > 400) -20.0 else 0.0
                    val unitSell = prod.sellingPrice + bargainDelta
                    val lineTotal = unitSell * qty
                    subtotal += lineTotal
                    chosenItems += SaleItemEntity(
                        saleId = 0L,
                        productId = pid,
                        productNameSnapshot = prod.name,
                        skuSnapshot = prod.skuCode,
                        categorySnapshot = prod.category,
                        size = chosenSize,
                        quantity = qty,
                        costPriceAtSale = prod.costPrice,
                        sellingPriceAtSale = unitSell,
                        lineTotal = lineTotal
                    )
                }

                val custIdx = if (rng.nextInt(10) < 6) rng.nextInt(customerIds.size) else -1
                val cust = if (custIdx >= 0) customers[custIdx] else null
                val cid = if (custIdx >= 0) customerIds[custIdx] else null
                val mode = if (daysAgo == 1 && b == 0 && cid != null) "CREDIT" else paymentModes[rng.nextInt(paymentModes.size)]

                val sale = SaleEntity(
                    billNumber = "BILL-${billCounter.toString().padStart(4, '0')}",
                    timestamp = ts,
                    customerId = cid,
                    customerPhoneSnapshot = cust?.phone ?: "",
                    customerNameSnapshot = cust?.name ?: "",
                    paymentMode = mode,
                    discountAmount = 0.0,
                    subtotal = subtotal,
                    totalRevenue = subtotal,
                    totalCollected = subtotal,
                    creditSettled = mode != "CREDIT"
                )
                salesToInsert += sale
                itemsPlan += (salesToInsert.lastIndex to chosenItems)
                billCounter++
            }
        }

        // Also add one older sale 66 days ago for SL-011 so it is true Dead Stock (60+ days without sale)
        val deadProd = products[10]
        val deadPid = productIds[10]
        salesToInsert += SaleEntity(
            billNumber = "BILL-0000",
            timestamp = now - 66 * dayMs,
            paymentMode = "CASH",
            subtotal = deadProd.sellingPrice,
            totalRevenue = deadProd.sellingPrice,
            totalCollected = deadProd.sellingPrice
        )
        itemsPlan += (salesToInsert.lastIndex to listOf(
            SaleItemEntity(
                saleId = 0L,
                productId = deadPid,
                productNameSnapshot = deadProd.name,
                skuSnapshot = deadProd.skuCode,
                categorySnapshot = deadProd.category,
                size = "8",
                quantity = 1,
                costPriceAtSale = deadProd.costPrice,
                sellingPriceAtSale = deadProd.sellingPrice,
                lineTotal = deadProd.sellingPrice
            )
        ))

        val insertedSaleIds = dao.insertSales(salesToInsert)
        val finalSaleItems = mutableListOf<SaleItemEntity>()
        itemsPlan.forEach { (saleIdx, list) ->
            val sid = insertedSaleIds[saleIdx]
            list.forEach { item -> finalSaleItems += item.copy(saleId = sid) }
        }
        dao.insertSaleItems(finalSaleItems)

        // Add 2 sample returns so Returns tracking is live
        if (insertedSaleIds.size > 10) {
            val sampleSaleId = insertedSaleIds[5]
            val p0 = products[0]
            dao.insertReturn(
                ReturnEntity(
                    saleId = sampleSaleId,
                    productId = productIds[0],
                    productNameSnapshot = p0.name,
                    size = "7",
                    quantity = 1,
                    refundAmount = p0.sellingPrice,
                    costReversed = p0.costPrice,
                    type = "RETURN",
                    timestamp = now - 14 * dayMs,
                    reason = "Size too tight"
                )
            )
        }

        // Add Purchase History (Lots & Restocks linked to Vendors)
        dao.insertPurchases(
            listOf(
                PurchaseEntity(
                    productId = productIds[0], totalUnits = 24, unitCostPrice = 260.0, totalCost = 6240.0,
                    supplier = "Kolhapur & Agra Craft Hub", vendorId = vCraft, lotType = "FRESH_LOT",
                    paymentStatus = "PARTIAL", paidAmount = 2040.0, dueAmount = 4200.0,
                    sizeBreakdown = "6:4, 7:6, 8:6, 9:5, 10:3",
                    timestamp = now - 14 * dayMs, notes = "Lot LOT-K1 fresh Kolhapuri batch"
                ),
                PurchaseEntity(
                    productId = productIds[3], totalUnits = 16, unitCostPrice = 620.0, totalCost = 9920.0,
                    supplier = "Delhi Sneaker & Sport Lot", vendorId = vDelhi, lotType = "FRESH_LOT",
                    paymentStatus = "PARTIAL", paidAmount = 3420.0, dueAmount = 6500.0,
                    sizeBreakdown = "7:4, 8:5, 9:4, 10:3",
                    timestamp = now - 18 * dayMs, notes = "Runner surplus lot LOT-D4"
                ),
                PurchaseEntity(
                    productId = productIds[2], totalUnits = 18, unitCostPrice = 340.0, totalCost = 6120.0,
                    supplier = "Delhi Sneaker & Sport Lot", vendorId = vDelhi, lotType = "FRESH_LOT",
                    paymentStatus = "PAID", paidAmount = 6120.0, dueAmount = 0.0,
                    sizeBreakdown = "6:3, 7:5, 8:5, 9:3, 10:2",
                    timestamp = now - 20 * dayMs, notes = "Floater lot LOT-D3"
                ),
                PurchaseEntity(
                    productId = productIds[1], totalUnits = 30, unitCostPrice = 185.0, totalCost = 5550.0,
                    supplier = "Paragon & Relaxo Wholesale", vendorId = vParagon, lotType = "REPEAT_RESTOCK",
                    paymentStatus = "PAID", paidAmount = 5550.0, dueAmount = 0.0,
                    sizeBreakdown = "6:6, 7:8, 8:7, 9:5, 10:4",
                    timestamp = now - 10 * dayMs, notes = "Regular staple restock"
                )
            )
        )
        dao.insertVendorPayments(
            listOf(
                VendorPaymentEntity(
                    vendorId = vCraft,
                    amount = 2040.0,
                    paymentMode = "UPI",
                    timestamp = now - 14 * dayMs,
                    notes = "Advance paid on Lot LOT-K1"
                ),
                VendorPaymentEntity(
                    vendorId = vParagon,
                    amount = 5550.0,
                    paymentMode = "CASH",
                    timestamp = now - 10 * dayMs,
                    notes = "Full payment for Relaxo staple batch"
                )
            )
        )

        // Add Shop Expenses (last 2 months)
        dao.insertExpenses(
            listOf(
                ExpenseEntity(category = "Rent", amount = 14000.0, isRecurringMonthly = true, timestamp = now - 12 * dayMs, notes = "Shop monthly rent"),
                ExpenseEntity(category = "Salary", amount = 9500.0, isRecurringMonthly = true, timestamp = now - 10 * dayMs, notes = "Helper Ramesh salary"),
                ExpenseEntity(category = "Electricity", amount = 1850.0, isRecurringMonthly = true, timestamp = now - 7 * dayMs, notes = "Shop EB bill"),
                ExpenseEntity(category = "Transport", amount = 650.0, isRecurringMonthly = false, timestamp = now - 4 * dayMs, notes = "Wholesale parcel rickshaw"),
                ExpenseEntity(category = "Packaging", amount = 420.0, isRecurringMonthly = false, timestamp = now - 2 * dayMs, notes = "Carry bags & boxes"),
                ExpenseEntity(category = "Rent", amount = 14000.0, isRecurringMonthly = true, timestamp = now - 42 * dayMs, notes = "Previous month rent"),
                ExpenseEntity(category = "Salary", amount = 9500.0, isRecurringMonthly = true, timestamp = now - 40 * dayMs, notes = "Previous month salary")
            )
        )

        val currentSettings = dao.getSettingsSync() ?: ShopSettingsEntity()
        dao.saveSettings(
            currentSettings.copy(
                shopName = currentSettings.shopName.ifBlank { "Laxmi Footwear" },
                businessCategory = "FOOTWEAR",
                hasCompletedOnboarding = true,
                hasCompletedWalkthrough = true,
                isDemoLoaded = true
            )
        )
    }
}
