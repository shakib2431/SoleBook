package com.example.domain.onboarding

import androidx.room.withTransaction
import com.example.data.local.*
import com.example.data.repository.SoleBookRepository

data class StockCountDiscrepancySummary(
    val totalLines: Int,
    val countedLines: Int,
    val matchedLines: Int,
    val varianceLines: Int,
    val netUnitDelta: Int,
    val netCostImpact: Double
)

class StockCountService(
    private val db: SoleBookDatabase,
    private val repository: SoleBookRepository
) {
    private val dao = db.soleBookDao()
    private val onboardingDao = db.onboardingDao()

    suspend fun createSession(
        title: String,
        location: String = "",
        categoryFilter: String = "",
        prefillExpectedCount: Boolean = false
    ): Long {
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val sessionId = onboardingDao.insertStockCountSession(
                StockCountSessionEntity(
                    title = title.ifBlank { "Physical Stock Count" },
                    location = location,
                    categoryFilter = categoryFilter,
                    status = "OPEN",
                    createdAt = now
                )
            )
            val products = dao.getAllProductsSync().filter { p ->
                !p.isArchived &&
                    (categoryFilter.isBlank() || p.category.equals(categoryFilter, ignoreCase = true)) &&
                    (location.isBlank() || p.rackLocation.equals(location, ignoreCase = true))
            }
            val variantsByProd = dao.getAllVariantsSync().groupBy { it.productId }
            val lines = mutableListOf<StockCountLineEntity>()
            products.forEach { p ->
                val vars = variantsByProd[p.id].orEmpty()
                if (vars.isEmpty()) {
                    lines += StockCountLineEntity(
                        sessionId = sessionId,
                        productId = p.id,
                        variantId = null,
                        skuCode = p.skuCode,
                        barcode = p.barcode,
                        productName = p.name,
                        variantLabel = "Standard",
                        expectedQty = 0,
                        countedQty = 0,
                        variance = 0
                    )
                } else {
                    vars.forEach { v ->
                        if (location.isBlank() || v.location.isBlank() || v.location.equals(location, ignoreCase = true)) {
                            val initCount = if (prefillExpectedCount) v.stock else 0
                            lines += StockCountLineEntity(
                                sessionId = sessionId,
                                productId = p.id,
                                variantId = v.id,
                                skuCode = p.skuCode,
                                barcode = v.barcode.ifBlank { p.barcode },
                                productName = p.name,
                                variantLabel = v.size,
                                expectedQty = v.stock,
                                countedQty = initCount,
                                variance = initCount - v.stock
                            )
                        }
                    }
                }
            }
            if (lines.isNotEmpty()) {
                onboardingDao.insertStockCountLines(lines)
            }
            sessionId
        }
    }

    suspend fun updateLineCount(line: StockCountLineEntity, newCount: Int) {
        val cleanCount = newCount.coerceAtLeast(0)
        onboardingDao.updateStockCountLine(
            line.copy(
                countedQty = cleanCount,
                variance = cleanCount - line.expectedQty
            )
        )
    }

    suspend fun toggleLineApproved(line: StockCountLineEntity, approved: Boolean) {
        onboardingDao.updateStockCountLine(line.copy(approved = approved))
    }

    suspend fun incrementCountByBarcode(sessionId: Long, barcodeRaw: String, delta: Int = 1): StockCountLineEntity? {
        val code = barcodeRaw.trim()
        if (code.isEmpty()) return null
        val lines = onboardingDao.getStockCountLines(sessionId)
        val matched = lines.firstOrNull {
            it.barcode.equals(code, ignoreCase = true) || it.skuCode.equals(code, ignoreCase = true)
        } ?: run {
            val lookup = BarcodeOnboardingService.lookupLocalCatalog(code, dao, onboardingDao)
            if (lookup != null) {
                val targetVar = lookup.matchedVariant
                lines.firstOrNull {
                    it.productId == lookup.product.id && (targetVar == null || it.variantLabel == targetVar.size)
                }
            } else null
        } ?: return null

        val nextQty = (matched.countedQty + delta).coerceAtLeast(0)
        val updated = matched.copy(countedQty = nextQty, variance = nextQty - matched.expectedQty)
        onboardingDao.updateStockCountLine(updated)
        return updated
    }

    fun summarizeLines(
        lines: List<StockCountLineEntity>,
        productsById: Map<Long, ProductEntity>
    ): StockCountDiscrepancySummary {
        val varianceLines = lines.filter { it.variance != 0 }
        val netUnits = lines.sumOf { it.variance }
        val netCost = lines.sumOf { line ->
            val cost = productsById[line.productId]?.costPrice ?: 0.0
            line.variance * cost
        }
        return StockCountDiscrepancySummary(
            totalLines = lines.size,
            countedLines = lines.count { it.countedQty > 0 },
            matchedLines = lines.count { it.variance == 0 },
            varianceLines = varianceLines.size,
            netUnitDelta = netUnits,
            netCostImpact = netCost
        )
    }

    /**
     * Commits all approved stock count lines via auditable COUNT_RECONCILE inventory movements
     * with unique idempotency keys.
     */
    suspend fun commitSession(sessionId: Long): Int {
        val session = onboardingDao.getStockCountSession(sessionId) ?: return 0
        if (session.status == "COMMITTED") return 0

        val lines = onboardingDao.getStockCountLines(sessionId).filter { it.approved }
        var reconciledCount = 0
        val now = System.currentTimeMillis()

        db.withTransaction {
            val batchId = onboardingDao.insertImportBatch(
                ImportBatchEntity(
                    idempotencyKey = "STOCK_COUNT_$sessionId",
                    sourceRoute = "STOCK_COUNT",
                    fileName = session.title,
                    status = "COMPLETED",
                    totalItems = lines.size,
                    validItems = lines.size,
                    processedItems = lines.size,
                    createdAt = now,
                    updatedAt = now
                )
            )

            for (line in lines) {
                val mvKey = "stockcount:$sessionId:line:${line.id}"
                if (onboardingDao.getMovementByKey(mvKey) != null) continue

                val prod = dao.getProductById(line.productId) ?: continue
                val variant = dao.getVariant(line.productId, line.variantLabel)
                val before = variant?.stock ?: line.expectedQty
                val after = line.countedQty.coerceAtLeast(0)
                val delta = after - before

                if (variant != null) {
                    dao.updateVariant(variant.copy(stock = after))
                } else {
                    dao.insertVariant(
                        ProductVariantEntity(
                            productId = line.productId,
                            size = line.variantLabel,
                            stock = after,
                            barcode = line.barcode
                        )
                    )
                }

                onboardingDao.insertMovementIgnore(
                    InventoryMovementEntity(
                        movementKey = mvKey,
                        productId = line.productId,
                        variantId = variant?.id,
                        sizeLabel = line.variantLabel,
                        movementType = "COUNT_RECONCILE",
                        qtyDelta = delta,
                        beforeQty = before,
                        afterQty = after,
                        unitCost = prod.costPrice,
                        location = session.location.ifBlank { prod.rackLocation },
                        sourceRoute = "STOCK_COUNT",
                        sourceBatchId = batchId,
                        sourceRefId = "SC-$sessionId",
                        timestamp = now,
                        notes = "Stock count reconciliation: ${session.title}"
                    )
                )
                reconciledCount++
            }

            onboardingDao.updateStockCountSession(
                session.copy(status = "COMMITTED", committedAt = now)
            )
        }
        return reconciledCount
    }
}
