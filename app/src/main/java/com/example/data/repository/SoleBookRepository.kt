package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.*
import com.example.util.BackupPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max

data class CartLineRequest(
    val product: ProductEntity,
    val size: String,
    val quantity: Int,
    val unitSellingPrice: Double
)

class SoleBookRepository(
    private val db: SoleBookDatabase
) {
    private val dao = db.soleBookDao()
    private val onboardingDao = db.onboardingDao()
    private val wholesaleDao = db.wholesaleDao()

    val productsFlow: Flow<List<ProductEntity>> = dao.observeProducts()
    val variantsFlow: Flow<List<ProductVariantEntity>> = dao.observeVariants()
    val fingerprintsFlow: Flow<List<ProductFingerprintEntity>> = dao.observeFingerprints()
    val productImagesFlow: Flow<List<ProductImageEntity>> = onboardingDao.observeAllProductImages()
    val locationsFlow: Flow<List<InventoryLocationEntity>> = onboardingDao.observeLocations()
    val movementsFlow: Flow<List<InventoryMovementEntity>> = onboardingDao.observeMovements()
    val importBatchesFlow: Flow<List<ImportBatchEntity>> = onboardingDao.observeImportBatches()
    val stockCountSessionsFlow: Flow<List<StockCountSessionEntity>> = onboardingDao.observeStockCountSessions()
    val purchaseOrdersFlow: Flow<List<PurchaseOrderEntity>> = onboardingDao.observePurchaseOrders()
    val salesFlow: Flow<List<SaleEntity>> = dao.observeSales()
    val saleItemsFlow: Flow<List<SaleItemEntity>> = dao.observeSaleItems()
    val returnsFlow: Flow<List<ReturnEntity>> = dao.observeReturns()
    val purchasesFlow: Flow<List<PurchaseEntity>> = dao.observePurchases()
    val vendorsFlow: Flow<List<VendorEntity>> = dao.observeVendors()
    val vendorPaymentsFlow: Flow<List<VendorPaymentEntity>> = dao.observeVendorPayments()
    val expensesFlow: Flow<List<ExpenseEntity>> = dao.observeExpenses()
    val customersFlow: Flow<List<CustomerEntity>> = dao.observeCustomers()
    val segmentsFlow: Flow<List<SegmentEntity>> = dao.observeSegments()
    val templatesFlow: Flow<List<MessageTemplateEntity>> = dao.observeTemplates()
    val campaignLogsFlow: Flow<List<CampaignLogEntity>> = dao.observeCampaignLogs()
    val noticeStatesFlow: Flow<List<NoticeStateEntity>> = dao.observeNoticeStates()
    val rawMaterialsFlow: Flow<List<RawMaterialEntity>> = wholesaleDao.observeRawMaterials()
    val bomTemplatesFlow: Flow<List<BomTemplateEntity>> = wholesaleDao.observeBomTemplates()
    val bomRulesFlow: Flow<List<BomMaterialRuleEntity>> = wholesaleDao.observeBomMaterialRules()
    val wholesaleQuotesFlow: Flow<List<WholesaleQuoteEntity>> = wholesaleDao.observeWholesaleQuotes()
    val wholesaleQuoteLinesFlow: Flow<List<WholesaleQuoteLineEntity>> = wholesaleDao.observeAllQuoteLines()
    val settingsFlow: Flow<ShopSettingsEntity> = dao.observeSettings().map {
        it ?: ShopSettingsEntity(
            shopName = "",
            businessCategory = "GENERAL",
            hasCompletedOnboarding = false
        )
    }

    suspend fun ensureDefaultsInitialized() {
        if (dao.getSettingsSync() == null) {
            dao.saveSettings(
                ShopSettingsEntity(
                    shopName = "",
                    businessCategory = "GENERAL",
                    hasCompletedOnboarding = false,
                    hasCompletedWalkthrough = false
                )
            )
        }
        if (dao.getAllSegmentsSync().isEmpty()) {
            dao.insertSegments(DemoSeeder.defaultSegments())
        }
        if (dao.getAllTemplatesSync().isEmpty()) {
            dao.insertTemplates(DemoSeeder.defaultTemplates())
        }
        if (onboardingDao.getAllLocationsSync().isEmpty()) {
            onboardingDao.insertLocation(
                InventoryLocationEntity(
                    code = "MAIN",
                    name = "Main Shop Floor",
                    locationType = "STORE",
                    isDefault = true
                )
            )
        }
    }

    suspend fun saveSettings(settings: ShopSettingsEntity) {
        dao.saveSettings(settings)
    }

    suspend fun loadDemoData() {
        db.withTransaction {
            DemoSeeder.seedDemo(dao)
        }
    }

    suspend fun clearAllData() {
        db.withTransaction {
            dao.clearSaleItems()
            dao.clearReturns()
            dao.clearSales()
            dao.clearPurchases()
            dao.clearVendorPayments()
            dao.clearVendors()
            dao.clearExpenses()
            onboardingDao.clearStockCountLines()
            onboardingDao.clearStockCountSessions()
            onboardingDao.clearPurchaseOrders()
            onboardingDao.clearMovements()
            onboardingDao.clearProductImages()
            onboardingDao.clearAllStagedItems()
            onboardingDao.clearAllImportBatches()
            dao.clearVariants()
            dao.clearFingerprints()
            dao.clearProducts()
            dao.clearCustomers()
            dao.clearCampaigns()
            dao.clearNoticeStates()
            wholesaleDao.clearQuoteLines()
            wholesaleDao.clearWholesaleQuotes()
            wholesaleDao.clearBomRules()
            wholesaleDao.clearBomTemplates()
            wholesaleDao.clearRawMaterials()
            val cur = dao.getSettingsSync() ?: ShopSettingsEntity()
            dao.saveSettings(cur.copy(isDemoLoaded = false))
        }
    }

    suspend fun saveProductWithVariants(
        product: ProductEntity,
        sizeStocks: Map<String, Int>
    ): Long {
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val pid = if (product.id == 0L) {
                dao.insertProduct(product.copy(createdAt = now, updatedAt = now))
            } else {
                dao.updateProduct(product.copy(updatedAt = now))
                product.id
            }
            dao.deleteVariantsForProduct(pid)
            val variants = sizeStocks.entries
                .sortedBy { it.key.toIntOrNull() ?: 99 }
                .map { (sz, qty) ->
                    ProductVariantEntity(productId = pid, size = sz, stock = qty.coerceAtLeast(0))
                }
            dao.insertVariants(variants)
            pid
        }
    }

    suspend fun getAllProductsSync(): List<ProductEntity> = dao.getAllProductsSync()

    suspend fun getAllFingerprintsSync(): List<ProductFingerprintEntity> = dao.getAllFingerprintsSync()

    suspend fun indexFingerprintsForProducts(
        context: android.content.Context,
        targetProducts: List<ProductEntity>? = null
    ): Map<Long, ProductFingerprintEntity> {
        val products = targetProducts ?: dao.getAllProductsSync()
        val existingByProductId = dao.getAllFingerprintsSync().associateBy { it.productId }
        val toInsert = mutableListOf<ProductFingerprintEntity>()
        val now = System.currentTimeMillis()
        for (p in products) {
            if (p.photoUri.isBlank()) {
                if (existingByProductId.containsKey(p.id)) {
                    dao.deleteFingerprintForProduct(p.id)
                }
                continue
            }
            val existing = existingByProductId[p.id]
            val needsReindex = targetProducts != null ||
                existing == null ||
                !existing.signatureCsv.startsWith(com.example.domain.OfflineVisualMatcher.SIGNATURE_PREFIX) ||
                p.updatedAt > existing.updatedAt
            if (!needsReindex) continue

            val bmp = com.example.domain.OfflineVisualMatcher.decodeScaledBitmapFromProductPhoto(context, p.photoUri)
                ?: continue
            val (vec, domColor) = com.example.domain.OfflineVisualMatcher.extractSignature(bmp)
            bmp.recycle()
            toInsert += ProductFingerprintEntity(
                productId = p.id,
                signatureCsv = com.example.domain.OfflineVisualMatcher.encodeVector(vec),
                dominantColor = p.colorTag.ifBlank { domColor },
                updatedAt = now
            )
            if (p.colorTag.isBlank()) {
                dao.updateProduct(p.copy(colorTag = domColor))
            }
        }
        if (toInsert.isNotEmpty()) {
            dao.upsertFingerprints(toInsert)
        }
        return dao.getAllFingerprintsSync().associateBy { it.productId }
    }

    suspend fun toggleArchiveProduct(product: ProductEntity) {
        dao.updateProduct(product.copy(isArchived = !product.isArchived, updatedAt = System.currentTimeMillis()))
    }

    suspend fun retireProduct(productId: Long) {
        val prod = dao.getProductById(productId) ?: return
        dao.updateProduct(prod.copy(isArchived = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleRepeatableProduct(productId: Long) {
        val prod = dao.getProductById(productId) ?: return
        dao.updateProduct(prod.copy(isRepeatable = !prod.isRepeatable, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateSellingPrice(productId: Long, newSellingPrice: Double) {
        val prod = dao.getProductById(productId) ?: return
        dao.updateProduct(prod.copy(sellingPrice = newSellingPrice.coerceAtLeast(1.0), updatedAt = System.currentTimeMillis()))
    }

    suspend fun upsertNoticeState(state: NoticeStateEntity) {
        dao.upsertNoticeState(state)
    }

    suspend fun upsertNoticeStates(states: List<NoticeStateEntity>) {
        if (states.isNotEmpty()) dao.upsertNoticeStates(states)
    }

    suspend fun duplicateProduct(source: ProductEntity, newSku: String): Long {
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val copyProd = source.copy(
                id = 0L,
                skuCode = newSku,
                name = "${source.name} (Copy)",
                createdAt = now,
                updatedAt = now
            )
            val newId = dao.insertProduct(copyProd)
            val sourceVariants = dao.getVariantsForProduct(source.id)
            dao.insertVariants(sourceVariants.map { it.copy(id = 0L, productId = newId) })
            newId
        }
    }

    /**
     * Weighted-average cost calculation on restock:
     * newAvgCost = ((existingStock * oldCost) + (addedUnits * newCost)) / (existingStock + addedUnits)
     */
    suspend fun restockProduct(
        productId: Long,
        addedPerSize: Map<String, Int>,
        newUnitCost: Double,
        supplier: String,
        notes: String,
        vendorId: Long? = null,
        paidAmount: Double? = null
    ) {
        val totalAdded = addedPerSize.values.sum()
        if (totalAdded <= 0) return

        db.withTransaction {
            val now = System.currentTimeMillis()
            val product = dao.getProductById(productId) ?: return@withTransaction
            val existingVariants = dao.getVariantsForProduct(productId)
            val existingStock = existingVariants.sumOf { it.stock }

            val weightedCost = if (existingStock + totalAdded > 0) {
                ((existingStock * product.costPrice) + (totalAdded * newUnitCost)) / (existingStock + totalAdded)
            } else {
                newUnitCost
            }

            val resolvedVendorId = vendorId ?: product.vendorId
            val resolvedVendor = resolvedVendorId?.let { dao.getVendorById(it) }
            val resolvedSupplierName = supplier.ifBlank { resolvedVendor?.name ?: product.supplier }

            dao.updateProduct(
                product.copy(
                    costPrice = weightedCost,
                    supplier = resolvedSupplierName,
                    vendorId = resolvedVendorId,
                    updatedAt = now
                )
            )

            val bySize = existingVariants.associateBy { it.size }.toMutableMap()
            addedPerSize.forEach { (sz, addQty) ->
                if (addQty > 0) {
                    val cur = bySize[sz]
                    if (cur != null) {
                        dao.updateVariant(cur.copy(stock = cur.stock + addQty))
                    } else {
                        dao.insertVariant(ProductVariantEntity(productId = productId, size = sz, stock = addQty))
                    }
                }
            }

            val breakdownStr = addedPerSize.entries
                .filter { it.value > 0 }
                .joinToString(", ") { "${it.key}:${it.value}" }
            val totalCost = totalAdded * newUnitCost
            val actualPaid = (paidAmount ?: totalCost).coerceIn(0.0, totalCost)
            val due = (totalCost - actualPaid).coerceAtLeast(0.0)
            val payStatus = when {
                due <= 0.5 -> "PAID"
                actualPaid <= 0.5 -> "CREDIT"
                else -> "PARTIAL"
            }

            dao.insertPurchase(
                PurchaseEntity(
                    productId = productId,
                    totalUnits = totalAdded,
                    unitCostPrice = newUnitCost,
                    totalCost = totalCost,
                    supplier = resolvedSupplierName,
                    vendorId = resolvedVendorId,
                    lotType = if (product.isRepeatable) "REPEAT_RESTOCK" else "FRESH_LOT",
                    paymentStatus = payStatus,
                    paidAmount = actualPaid,
                    dueAmount = due,
                    sizeBreakdown = breakdownStr,
                    timestamp = now,
                    notes = notes
                )
            )

            if (resolvedVendor != null) {
                dao.updateVendor(
                    resolvedVendor.copy(
                        creditBalance = resolvedVendor.creditBalance + due,
                        lastPurchaseAt = now
                    )
                )
            }
        }
    }

    suspend fun saveFreshLotWithVendor(
        product: ProductEntity,
        sizeStocks: Map<String, Int>,
        paidAmount: Double?,
        notes: String
    ): Long {
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val vendor = product.vendorId?.let { dao.getVendorById(it) }
            val resolvedSupplier = product.supplier.ifBlank { vendor?.name ?: "" }
            val pid = dao.insertProduct(
                product.copy(
                    supplier = resolvedSupplier,
                    createdAt = now,
                    updatedAt = now
                )
            )
            val variants = sizeStocks.entries
                .sortedBy { it.key.toIntOrNull() ?: 99 }
                .map { (sz, qty) ->
                    ProductVariantEntity(productId = pid, size = sz, stock = qty.coerceAtLeast(0))
                }
            dao.insertVariants(variants)

            val totalUnits = sizeStocks.values.sum().coerceAtLeast(0)
            if (totalUnits > 0) {
                val totalCost = totalUnits * product.costPrice
                val actualPaid = (paidAmount ?: totalCost).coerceIn(0.0, totalCost)
                val due = (totalCost - actualPaid).coerceAtLeast(0.0)
                val payStatus = when {
                    due <= 0.5 -> "PAID"
                    actualPaid <= 0.5 -> "CREDIT"
                    else -> "PARTIAL"
                }
                val breakdown = sizeStocks.entries
                    .filter { it.value > 0 }
                    .sortedBy { it.key.toIntOrNull() ?: 99 }
                    .joinToString(", ") { "${it.key}:${it.value}" }

                dao.insertPurchase(
                    PurchaseEntity(
                        productId = pid,
                        totalUnits = totalUnits,
                        unitCostPrice = product.costPrice,
                        totalCost = totalCost,
                        supplier = resolvedSupplier,
                        vendorId = product.vendorId,
                        lotType = if (product.isRepeatable) "REPEAT_RESTOCK" else "FRESH_LOT",
                        paymentStatus = payStatus,
                        paidAmount = actualPaid,
                        dueAmount = due,
                        sizeBreakdown = breakdown,
                        timestamp = now,
                        notes = notes.ifBlank { "Initial fresh lot (${product.lotCode.ifBlank { product.skuCode }})" }
                    )
                )
                if (vendor != null) {
                    dao.updateVendor(
                        vendor.copy(
                            creditBalance = vendor.creditBalance + due,
                            lastPurchaseAt = now
                        )
                    )
                }
            }
            pid
        }
    }

    suspend fun saveVendor(vendor: VendorEntity): Long {
        return if (vendor.id == 0L) {
            dao.insertVendor(vendor.copy(createdAt = System.currentTimeMillis()))
        } else {
            dao.updateVendor(vendor)
            vendor.id
        }
    }

    suspend fun deleteVendor(vendorId: Long) {
        dao.deleteVendorById(vendorId)
    }

    suspend fun recordVendorPayment(
        vendorId: Long,
        amount: Double,
        paymentMode: String,
        notes: String
    ) {
        if (amount <= 0.0) return
        db.withTransaction {
            val vendor = dao.getVendorById(vendorId) ?: return@withTransaction
            val now = System.currentTimeMillis()
            dao.insertVendorPayment(
                VendorPaymentEntity(
                    vendorId = vendorId,
                    amount = amount,
                    paymentMode = paymentMode,
                    timestamp = now,
                    notes = notes
                )
            )
            dao.updateVendor(
                vendor.copy(creditBalance = (vendor.creditBalance - amount).coerceAtLeast(0.0))
            )
        }
    }

    suspend fun completeSale(
        lines: List<CartLineRequest>,
        paymentMode: String,
        discountType: String,
        discountInput: Double,
        customerPhone: String,
        customerName: String,
        notes: String = ""
    ): Result<Long> {
        if (lines.isEmpty()) return Result.failure(IllegalArgumentException("Cart is empty"))

        return try {
            val saleId = db.withTransaction {
                // Verify no negative stock
                for (line in lines) {
                    val variant = dao.getVariant(line.product.id, line.size)
                        ?: throw IllegalStateException("Size ${line.size} not found for ${line.product.name}")
                    if (variant.stock < line.quantity) {
                        throw IllegalStateException("Only ${variant.stock} left in size ${line.size} of ${line.product.name}")
                    }
                }

                val now = System.currentTimeMillis()
                val settings = dao.getSettingsSync() ?: ShopSettingsEntity()
                val subtotal = lines.sumOf { it.unitSellingPrice * it.quantity }

                val resolvedDiscount = when (discountType) {
                    "PERCENT" -> (subtotal * (discountInput.coerceIn(0.0, 100.0) / 100.0))
                    else -> discountInput.coerceIn(0.0, subtotal)
                }

                val afterDiscount = max(0.0, subtotal - resolvedDiscount)
                val (revenue, gstAmount, collected) = if (settings.gstEnabled && settings.gstRate > 0) {
                    if (settings.gstInclusive) {
                        val base = afterDiscount / (1.0 + settings.gstRate / 100.0)
                        Triple(base, afterDiscount - base, afterDiscount)
                    } else {
                        val tax = afterDiscount * (settings.gstRate / 100.0)
                        Triple(afterDiscount, tax, afterDiscount + tax)
                    }
                } else {
                    Triple(afterDiscount, 0.0, afterDiscount)
                }

                // Resolve or create customer if phone entered
                val cleanPhone = customerPhone.trim()
                var customerId: Long? = null
                var finalCustName = customerName.trim()
                if (cleanPhone.isNotEmpty()) {
                    val existing = dao.getCustomerByPhone(cleanPhone)
                    val topLine = lines.maxByOrNull { it.quantity }
                    if (existing != null) {
                        finalCustName = finalCustName.ifEmpty { existing.name }
                        val updated = existing.copy(
                            name = finalCustName,
                            lastVisitAt = now,
                            totalSpent = existing.totalSpent + collected,
                            visitCount = existing.visitCount + 1,
                            favouriteCategory = topLine?.product?.category ?: existing.favouriteCategory,
                            favouriteSize = topLine?.size ?: existing.favouriteSize,
                            creditBalance = existing.creditBalance + if (paymentMode == "CREDIT") collected else 0.0
                        )
                        dao.updateCustomer(updated)
                        customerId = existing.id
                    } else {
                        customerId = dao.insertCustomer(
                            CustomerEntity(
                                phone = cleanPhone,
                                name = finalCustName,
                                firstVisitAt = now,
                                lastVisitAt = now,
                                totalSpent = collected,
                                visitCount = 1,
                                favouriteCategory = topLine?.product?.category ?: "",
                                favouriteSize = topLine?.size ?: "",
                                whatsappConsent = true,
                                creditBalance = if (paymentMode == "CREDIT") collected else 0.0
                            )
                        )
                    }
                }

                val existingSalesCount = dao.getAllSalesSync().size
                val billNo = "BILL-${(existingSalesCount + 1).toString().padStart(4, '0')}"

                val newSaleId = dao.insertSale(
                    SaleEntity(
                        billNumber = billNo,
                        timestamp = now,
                        customerId = customerId,
                        customerPhoneSnapshot = cleanPhone,
                        customerNameSnapshot = finalCustName,
                        paymentMode = paymentMode,
                        discountAmount = resolvedDiscount,
                        discountType = discountType,
                        discountInput = discountInput,
                        gstEnabled = settings.gstEnabled,
                        gstRate = settings.gstRate,
                        gstInclusive = settings.gstInclusive,
                        gstAmount = gstAmount,
                        subtotal = subtotal,
                        totalRevenue = revenue,
                        totalCollected = collected,
                        creditSettled = paymentMode != "CREDIT",
                        notes = notes
                    )
                )

                val itemEntities = lines.map { line ->
                    SaleItemEntity(
                        saleId = newSaleId,
                        productId = line.product.id,
                        productNameSnapshot = line.product.name,
                        skuSnapshot = line.product.skuCode,
                        categorySnapshot = line.product.category,
                        size = line.size,
                        quantity = line.quantity,
                        costPriceAtSale = line.product.costPrice,
                        sellingPriceAtSale = line.unitSellingPrice,
                        lineTotal = line.unitSellingPrice * line.quantity
                    )
                }
                dao.insertSaleItems(itemEntities)

                // Decrement stock
                for (line in lines) {
                    val v = dao.getVariant(line.product.id, line.size)!!
                    dao.updateVariant(v.copy(stock = (v.stock - line.quantity).coerceAtLeast(0)))
                }

                newSaleId
            }
            Result.success(saleId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun undoSale(saleId: Long) {
        db.withTransaction {
            val sale = dao.getSaleById(saleId) ?: return@withTransaction
            val items = dao.getSaleItemsForSale(saleId)

            items.forEach { item ->
                val v = dao.getVariant(item.productId, item.size)
                if (v != null) {
                    dao.updateVariant(v.copy(stock = v.stock + item.quantity))
                }
            }

            if (sale.customerId != null) {
                val cust = dao.getCustomerById(sale.customerId)
                if (cust != null) {
                    dao.updateCustomer(
                        cust.copy(
                            totalSpent = (cust.totalSpent - sale.totalCollected).coerceAtLeast(0.0),
                            visitCount = (cust.visitCount - 1).coerceAtLeast(0),
                            creditBalance = if (sale.paymentMode == "CREDIT" && !sale.creditSettled) {
                                (cust.creditBalance - sale.totalCollected).coerceAtLeast(0.0)
                            } else cust.creditBalance
                        )
                    )
                }
            }

            dao.deleteSaleItemsForSale(saleId)
            dao.deleteSaleById(saleId)
        }
    }

    suspend fun processReturnOrExchange(
        saleId: Long,
        saleItem: SaleItemEntity,
        quantity: Int,
        isExchange: Boolean,
        exchangeSize: String?,
        reason: String
    ): Result<Unit> {
        if (quantity <= 0 || quantity > saleItem.quantity) {
            return Result.failure(IllegalArgumentException("Invalid return quantity"))
        }
        return try {
            db.withTransaction {
                val sale = dao.getSaleById(saleId)
                    ?: throw IllegalStateException("Sale not found")

                if (isExchange) {
                    val targetSize = exchangeSize ?: throw IllegalArgumentException("Select exchange size")
                    val exVar = dao.getVariant(saleItem.productId, targetSize)
                        ?: throw IllegalStateException("Size $targetSize unavailable")
                    if (exVar.stock < quantity) {
                        throw IllegalStateException("Only ${exVar.stock} left in size $targetSize")
                    }
                    dao.updateVariant(exVar.copy(stock = exVar.stock - quantity))
                }

                // Restore returned size stock
                val origVar = dao.getVariant(saleItem.productId, saleItem.size)
                if (origVar != null) {
                    dao.updateVariant(origVar.copy(stock = origVar.stock + quantity))
                }

                // Proportional refund if bill had discount
                val discountRatio = if (sale.subtotal > 0) sale.totalRevenue / sale.subtotal else 1.0
                val refundAmt = if (isExchange) 0.0 else (saleItem.sellingPriceAtSale * quantity * discountRatio)
                val costRev = if (isExchange) 0.0 else (saleItem.costPriceAtSale * quantity)

                dao.insertReturn(
                    ReturnEntity(
                        saleId = saleId,
                        saleItemId = saleItem.id,
                        productId = saleItem.productId,
                        productNameSnapshot = saleItem.productNameSnapshot,
                        size = saleItem.size,
                        quantity = quantity,
                        refundAmount = refundAmt,
                        costReversed = costRev,
                        type = if (isExchange) "EXCHANGE" else "RETURN",
                        exchangeSize = if (isExchange) exchangeSize else null,
                        timestamp = System.currentTimeMillis(),
                        reason = reason
                    )
                )

                if (!isExchange && sale.customerId != null) {
                    val cust = dao.getCustomerById(sale.customerId)
                    if (cust != null) {
                        dao.updateCustomer(
                            cust.copy(
                                totalSpent = (cust.totalSpent - refundAmt).coerceAtLeast(0.0),
                                creditBalance = if (sale.paymentMode == "CREDIT" && !sale.creditSettled) {
                                    (cust.creditBalance - refundAmt).coerceAtLeast(0.0)
                                } else cust.creditBalance
                            )
                        )
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addExpense(expense: ExpenseEntity) {
        dao.insertExpense(expense)
    }

    suspend fun deleteExpense(expenseId: Long) {
        dao.deleteExpenseById(expenseId)
    }

    suspend fun saveCustomer(customer: CustomerEntity): Long {
        return if (customer.id == 0L) {
            dao.insertCustomer(customer)
        } else {
            dao.updateCustomer(customer)
            customer.id
        }
    }

    suspend fun importCustomers(
        contacts: List<com.example.domain.ParsedContact>,
        whatsappConsent: Boolean = true
    ): com.example.domain.ContactImportResult {
        if (contacts.isEmpty()) return com.example.domain.ContactImportResult(0, 0, 0)
        var added = 0
        var updated = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existingByPhone = dao.getAllCustomersSync()
                .associateBy { com.example.domain.CampaignBuilder.normalizePhone(it.phone) }
                .toMutableMap()

            for (c in contacts) {
                val normPhone = com.example.domain.CampaignBuilder.normalizePhone(c.phone)
                if (!com.example.domain.CampaignBuilder.isValidPhone(normPhone)) {
                    skipped++
                    continue
                }
                val existing = existingByPhone[normPhone]
                if (existing == null) {
                    val newCustomer = CustomerEntity(
                        phone = normPhone,
                        name = c.name.trim(),
                        email = c.email.trim(),
                        firstVisitAt = now,
                        lastVisitAt = now,
                        whatsappConsent = whatsappConsent
                    )
                    val newId = dao.insertCustomer(newCustomer)
                    existingByPhone[normPhone] = newCustomer.copy(id = newId)
                    added++
                } else {
                    val newName = existing.name.ifBlank { c.name.trim() }
                    val newEmail = existing.email.ifBlank { c.email.trim() }
                    if (newName != existing.name || newEmail != existing.email) {
                        val merged = existing.copy(name = newName, email = newEmail)
                        dao.updateCustomer(merged)
                        existingByPhone[normPhone] = merged
                        updated++
                    } else {
                        skipped++
                    }
                }
            }
        }
        return com.example.domain.ContactImportResult(added, updated, skipped)
    }

    suspend fun settleCustomerCredit(customerId: Long) {
        db.withTransaction {
            val cust = dao.getCustomerById(customerId) ?: return@withTransaction
            val now = System.currentTimeMillis()
            dao.updateCustomer(cust.copy(creditBalance = 0.0))
            val allSales = dao.getAllSalesSync().filter { it.customerId == customerId && it.paymentMode == "CREDIT" && !it.creditSettled }
            allSales.forEach { s ->
                dao.updateSale(s.copy(creditSettled = true, creditSettledAt = now))
            }
        }
    }

    suspend fun logCampaignSend(
        templateId: Long?,
        segmentKey: String,
        customer: CustomerEntity,
        message: String
    ) {
        dao.insertCampaignLog(
            CampaignLogEntity(
                templateId = templateId,
                segmentKey = segmentKey,
                customerId = customer.id,
                customerPhone = customer.phone,
                messageSent = message,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun buildBackupPayload(): BackupPayload {
        return BackupPayload(
            products = dao.getAllProductsSync(),
            variants = dao.getAllVariantsSync(),
            sales = dao.getAllSalesSync(),
            saleItems = dao.getAllSaleItemsSync(),
            returns = dao.getAllReturnsSync(),
            purchases = dao.getAllPurchasesSync(),
            vendors = dao.getAllVendorsSync(),
            vendorPayments = dao.getAllVendorPaymentsSync(),
            expenses = dao.getAllExpensesSync(),
            customers = dao.getAllCustomersSync(),
            segments = dao.getAllSegmentsSync(),
            templates = dao.getAllTemplatesSync(),
            campaigns = dao.getAllCampaignLogsSync(),
            settings = dao.getSettingsSync(),
            productImages = onboardingDao.getAllImagesSync(),
            inventoryLocations = onboardingDao.getAllLocationsSync(),
            inventoryMovements = onboardingDao.getAllMovementsSync(),
            purchaseOrders = onboardingDao.getAllPurchaseOrdersSync(),
            rawMaterials = wholesaleDao.getAllRawMaterialsSync(),
            bomTemplates = wholesaleDao.getAllBomTemplatesSync(),
            bomMaterialRules = wholesaleDao.getAllBomMaterialRulesSync(),
            wholesaleQuotes = wholesaleDao.getAllWholesaleQuotesSync(),
            wholesaleQuoteLines = wholesaleDao.getAllQuoteLinesSync()
        )
    }

    suspend fun restoreFromBackup(payload: BackupPayload) {
        db.withTransaction {
            dao.clearSaleItems()
            dao.clearReturns()
            dao.clearSales()
            dao.clearPurchases()
            dao.clearVendorPayments()
            dao.clearVendors()
            dao.clearExpenses()
            onboardingDao.clearStockCountLines()
            onboardingDao.clearStockCountSessions()
            onboardingDao.clearPurchaseOrders()
            onboardingDao.clearMovements()
            onboardingDao.clearProductImages()
            onboardingDao.clearAllStagedItems()
            onboardingDao.clearAllImportBatches()
            dao.clearVariants()
            dao.clearFingerprints()
            dao.clearProducts()
            dao.clearCustomers()
            dao.clearCampaigns()
            dao.clearNoticeStates()
            wholesaleDao.clearQuoteLines()
            wholesaleDao.clearWholesaleQuotes()
            wholesaleDao.clearBomRules()
            wholesaleDao.clearBomTemplates()
            wholesaleDao.clearRawMaterials()

            if (payload.vendors.isNotEmpty()) dao.insertVendors(payload.vendors)
            if (payload.vendorPayments.isNotEmpty()) dao.insertVendorPayments(payload.vendorPayments)
            if (payload.products.isNotEmpty()) dao.insertProducts(payload.products)
            if (payload.variants.isNotEmpty()) dao.insertVariants(payload.variants)
            if (payload.productImages.isNotEmpty()) onboardingDao.insertProductImages(payload.productImages)
            payload.inventoryLocations.forEach { onboardingDao.insertLocation(it) }
            if (payload.inventoryMovements.isNotEmpty()) onboardingDao.insertMovementsIgnore(payload.inventoryMovements)
            if (payload.purchaseOrders.isNotEmpty()) payload.purchaseOrders.forEach { onboardingDao.insertPurchaseOrder(it) }
            if (payload.customers.isNotEmpty()) dao.insertCustomers(payload.customers)
            if (payload.sales.isNotEmpty()) dao.insertSales(payload.sales)
            if (payload.saleItems.isNotEmpty()) dao.insertSaleItems(payload.saleItems)
            if (payload.returns.isNotEmpty()) dao.insertReturns(payload.returns)
            if (payload.purchases.isNotEmpty()) dao.insertPurchases(payload.purchases)
            if (payload.expenses.isNotEmpty()) dao.insertExpenses(payload.expenses)
            if (payload.rawMaterials.isNotEmpty()) wholesaleDao.insertRawMaterials(payload.rawMaterials)
            if (payload.bomTemplates.isNotEmpty()) wholesaleDao.insertBomTemplates(payload.bomTemplates)
            if (payload.bomMaterialRules.isNotEmpty()) wholesaleDao.insertBomRules(payload.bomMaterialRules)
            if (payload.wholesaleQuotes.isNotEmpty()) wholesaleDao.insertWholesaleQuotes(payload.wholesaleQuotes)
            if (payload.wholesaleQuoteLines.isNotEmpty()) wholesaleDao.insertQuoteLines(payload.wholesaleQuoteLines)
            payload.settings?.let { dao.saveSettings(it) }
        }
    }
}
