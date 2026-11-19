package com.example.domain.onboarding

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.*
import com.example.data.repository.CartLineRequest
import com.example.data.repository.SoleBookRepository
import com.example.util.Formatters
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class BusinessActionType(val key: String, val label: String) {
    CREATE_PRODUCT("CREATE_PRODUCT", "New Product"),
    UPDATE_PRODUCT("UPDATE_PRODUCT", "Update Product"),
    OPENING_STOCK("OPENING_STOCK", "Opening Stock"),
    SALE_DRAFT("SALE_DRAFT", "Sale / Bill"),
    RETURN_DRAFT("RETURN_DRAFT", "Sales Return"),
    PURCHASE_REQUEST("PURCHASE_REQUEST", "Replenishment Request"),
    PURCHASE_ORDER("PURCHASE_ORDER", "Purchase Order"),
    GOODS_RECEIVED("GOODS_RECEIVED", "Goods Received"),
    STOCK_COUNT("STOCK_COUNT", "Stock Count"),
    STOCK_ADJUSTMENT("STOCK_ADJUSTMENT", "Stock Adjustment"),
    EXPENSE("EXPENSE", "Expense"),
    CUSTOMER_CREDIT("CUSTOMER_CREDIT", "Customer Udhaar"),
    VENDOR_PAYMENT("VENDOR_PAYMENT", "Supplier Payment");

    companion object {
        fun fromKey(k: String): BusinessActionType = entries.firstOrNull { it.key == k } ?: CREATE_PRODUCT
    }
}

data class StagedRowDraft(
    val rowNumber: Int,
    val sourceDocPage: String = "",
    val actionType: BusinessActionType = BusinessActionType.CREATE_PRODUCT,
    val sku: String = "",
    val barcode: String = "",
    val productName: String = "",
    val category: String = "",
    val brand: String = "",
    val unitLabel: String = "",
    val variantLabel: String = "Standard",
    val variantType: String = "Variant",
    val colorName: String = "",
    val quantity: Double = 0.0,
    val costPrice: Double? = null,
    val sellingPrice: Double? = null,
    val mrp: Double? = null,
    val amount: Double = 0.0,
    val reorderLevel: Int? = null,
    val partyName: String = "",
    val partyPhone: String = "",
    val location: String = "",
    val batchNumber: String = "",
    val expiryDate: String = "",
    val serialNumbersCsv: String = "",
    val imageRef: String = "",
    val confidence: Float = 1.0f,
    val rawPayloadJson: String = "",
    val notes: String = ""
)

data class StagingOutcome(
    val batch: ImportBatchEntity,
    val isDuplicateUpload: Boolean,
    val existingBatchId: Long? = null
)

data class BatchCommitResult(
    val batchId: Long,
    val status: String,
    val committedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val createdProductIds: List<Long>
)

/**
 * Common Staging, Matching, Validation & Checkpointed Idempotent Commit Engine
 * shared by all 6 onboarding routes (CSV/XLSX, Images, Barcode, Bulk Manual, Stock Count, AI Docs).
 */
class StagingAndCommitEngine(
    private val db: SoleBookDatabase,
    private val repository: SoleBookRepository
) {
    private val dao = db.soleBookDao()
    private val onboardingDao = db.onboardingDao()

    companion object {
        const val COMMIT_CHUNK_SIZE = 250
        const val STAGE_CHUNK_SIZE = 500
    }

    suspend fun checkExistingFileBatch(fileHash: String): ImportBatchEntity? {
        if (fileHash.isBlank()) return null
        return onboardingDao.getImportBatchByFileHash(fileHash)
    }

    suspend fun stageSpreadsheet(
        file: File,
        fileName: String,
        mapping: Map<Int, TargetField>,
        defaultCategory: String = "General",
        defaultUnit: String = "pcs",
        forceReimport: Boolean = false
    ): StagingOutcome {
        val fmt = SpreadsheetCodec.detectFormatBySignatureOrName(file, fileName)
        val fileHash = SpreadsheetCodec.sha256OfFile(file)
        if (!forceReimport) {
            val prev = onboardingDao.getImportBatchByFileHash(fileHash)
            if (prev != null && prev.status != "CANCELLED") {
                return StagingOutcome(batch = prev, isDuplicateUpload = true, existingBatchId = prev.id)
            }
        }

        val drafts = mutableListOf<StagedRowDraft>()
        SpreadsheetCodec.streamRows(file, fmt) { rowIndex, cells ->
            if (rowIndex == 0) return@streamRows // skip header row
            val mapped = extractRowWithMapping(rowIndex, cells, mapping, defaultCategory, defaultUnit)
            if (mapped != null) {
                drafts += mapped
            }
        }
        val batch = stageDrafts(
            sourceRoute = "SPREADSHEET",
            fileName = fileName,
            fileHash = fileHash,
            mappingJson = JSONObject(mapping.mapKeys { it.key.toString() }.mapValues { it.value.key }).toString(),
            drafts = drafts
        )
        return StagingOutcome(batch = batch, isDuplicateUpload = false)
    }

    suspend fun stageDrafts(
        sourceRoute: String,
        fileName: String,
        fileHash: String = "",
        mappingJson: String = "",
        drafts: List<StagedRowDraft>,
        idempotencyKey: String = "${sourceRoute}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    ): ImportBatchEntity {
        val now = System.currentTimeMillis()
        val existingProducts = dao.getAllProductsSync()
        val existingVariants = dao.getAllVariantsSync()

        val byBarcode = existingProducts.filter { it.barcode.isNotBlank() }.associateBy { it.barcode.trim().lowercase() }
        val bySku = existingProducts.associateBy { it.skuCode.trim().lowercase() }
        val byNormName = existingProducts.associateBy { normalizeName(it.name) }
        val varByBarcode = existingVariants.filter { it.barcode.isNotBlank() }.associateBy { it.barcode.trim().lowercase() }
        val varsByProd = existingVariants.groupBy { it.productId }

        val seenBatchBarcodes = mutableMapOf<String, String>() // barcode -> product identity
        val seenBatchSkuVariants = mutableSetOf<String>()

        var validCount = 0
        var invalidCount = 0
        var duplicateCount = 0

        val batchId = onboardingDao.insertImportBatch(
            ImportBatchEntity(
                idempotencyKey = idempotencyKey,
                sourceRoute = sourceRoute,
                fileName = fileName,
                fileHash = fileHash,
                status = "STAGED",
                totalItems = drafts.size,
                mappingJson = mappingJson,
                createdAt = now,
                updatedAt = now
            )
        )

        val buffer = mutableListOf<StagedImportItemEntity>()
        for (d in drafts) {
            val cleanSku = d.sku.trim()
            val cleanBarcode = d.barcode.trim()
            val cleanName = d.productName.trim()
            val normName = normalizeName(cleanName)
            val normVariant = d.variantLabel.trim().ifBlank { "Standard" }

            val matchedVarByBar = cleanBarcode.takeIf { it.isNotEmpty() }?.let { varByBarcode[it.lowercase()] }
            val matchedProd = matchedVarByBar?.let { v -> existingProducts.firstOrNull { it.id == v.productId } }
                ?: cleanBarcode.takeIf { it.isNotEmpty() }?.let { byBarcode[it.lowercase()] }
                ?: cleanSku.takeIf { it.isNotEmpty() }?.let { bySku[it.lowercase()] }
                ?: normName.takeIf { it.isNotEmpty() }?.let { byNormName[it] }

            val matchedVar = matchedVarByBar ?: matchedProd?.let { mp ->
                varsByProd[mp.id]?.firstOrNull { it.size.equals(normVariant, ignoreCase = true) }
            }

            val resolution = when {
                d.actionType in listOf(
                    BusinessActionType.SALE_DRAFT,
                    BusinessActionType.RETURN_DRAFT,
                    BusinessActionType.PURCHASE_REQUEST,
                    BusinessActionType.PURCHASE_ORDER,
                    BusinessActionType.GOODS_RECEIVED,
                    BusinessActionType.STOCK_COUNT,
                    BusinessActionType.STOCK_ADJUSTMENT,
                    BusinessActionType.EXPENSE,
                    BusinessActionType.CUSTOMER_CREDIT,
                    BusinessActionType.VENDOR_PAYMENT
                ) -> if (matchedProd != null) "MERGE_STOCK" else "CREATE_NEW"
                matchedProd != null && d.actionType == BusinessActionType.OPENING_STOCK -> "MERGE_STOCK"
                matchedProd != null -> "UPDATE_EXISTING"
                else -> "CREATE_NEW"
            }

            val effectiveAction = when {
                d.actionType == BusinessActionType.CREATE_PRODUCT && matchedProd != null -> BusinessActionType.UPDATE_PRODUCT
                else -> d.actionType
            }

            val errors = mutableListOf<String>()
            var isDup = false

            // Duplicate checks within batch
            if (cleanBarcode.isNotEmpty()) {
                val barKey = cleanBarcode.lowercase()
                val identity = cleanSku.ifBlank { normName }.lowercase()
                val prevIdentity = seenBatchBarcodes[barKey]
                if (prevIdentity != null && prevIdentity != identity && identity.isNotEmpty()) {
                    isDup = true
                    errors += "Duplicate barcode '$cleanBarcode' assigned to another product in batch"
                } else {
                    seenBatchBarcodes[barKey] = identity
                }
                // Catalog barcode collision check (barcode exists on DIFFERENT SKU)
                val catBarcodeProd = byBarcode[barKey]
                if (catBarcodeProd != null && matchedProd != null && catBarcodeProd.id != matchedProd.id) {
                    isDup = true
                    errors += "Barcode '$cleanBarcode' already belongs to SKU ${catBarcodeProd.skuCode}"
                }
            }

            if (effectiveAction in listOf(BusinessActionType.CREATE_PRODUCT, BusinessActionType.UPDATE_PRODUCT, BusinessActionType.OPENING_STOCK)) {
                val key = "${cleanSku.ifBlank { normName }.lowercase()}::${normVariant.lowercase()}::${d.batchNumber.lowercase()}"
                if (key != "::::" && !seenBatchSkuVariants.add(key)) {
                    isDup = true
                    if (sourceRoute == "SPREADSHEET") {
                        errors += "Duplicate SKU/variant row in spreadsheet"
                    }
                }
            }

            // Validation rules
            when (effectiveAction) {
                BusinessActionType.CREATE_PRODUCT -> {
                    if (cleanName.isBlank()) errors += "Product name required"
                    if (d.quantity < 0) errors += "Negative quantity not allowed"
                    if ((d.costPrice ?: 0.0) < 0) errors += "Cost price cannot be negative"
                    if ((d.sellingPrice ?: 0.0) < 0) errors += "Selling price cannot be negative"
                    if (d.mrp != null && d.sellingPrice != null && d.mrp < d.sellingPrice && d.sellingPrice > 0) {
                        errors += "MRP (${d.mrp}) is lower than Selling Price (${d.sellingPrice})"
                    }
                }
                BusinessActionType.UPDATE_PRODUCT, BusinessActionType.OPENING_STOCK, BusinessActionType.STOCK_COUNT, BusinessActionType.STOCK_ADJUSTMENT -> {
                    if (matchedProd == null && cleanName.isBlank()) errors += "No matching product found and name is blank"
                    if (d.quantity < 0 && effectiveAction != BusinessActionType.STOCK_ADJUSTMENT) {
                        errors += "Negative quantity not allowed"
                    }
                }
                BusinessActionType.SALE_DRAFT -> {
                    if (d.quantity <= 0) errors += "Sale quantity must be > 0"
                    if (matchedProd == null && cleanName.isBlank()) errors += "Unidentified item in sale draft"
                    if (matchedVar != null && matchedVar.stock < d.quantity.toInt()) {
                        errors += "Insufficient stock (${matchedVar.stock} available, ${d.quantity.toInt()} requested)"
                    }
                }
                BusinessActionType.GOODS_RECEIVED -> {
                    if (d.quantity <= 0) errors += "Received quantity must be > 0"
                }
                BusinessActionType.PURCHASE_REQUEST, BusinessActionType.PURCHASE_ORDER -> {
                    if (d.quantity <= 0) errors += "Requested quantity must be > 0"
                    if (cleanName.isBlank() && matchedProd == null) errors += "Item name required for purchase request"
                }
                BusinessActionType.EXPENSE -> {
                    if (d.amount <= 0) errors += "Expense amount must be > 0"
                }
                BusinessActionType.CUSTOMER_CREDIT, BusinessActionType.VENDOR_PAYMENT -> {
                    if (d.amount <= 0) errors += "Amount must be > 0"
                    if (d.partyName.isBlank() && d.partyPhone.isBlank()) errors += "Customer/Supplier name or phone required"
                }
                BusinessActionType.RETURN_DRAFT -> {
                    if (d.quantity <= 0) errors += "Return quantity must be > 0"
                }
            }

            if (isDup) duplicateCount++
            if (errors.isEmpty()) validCount++ else invalidCount++

            val errStr = errors.joinToString(" | ")
            val initialApproval = if (errors.isEmpty() && d.confidence >= 0.65f && sourceRoute != "AI_DOCUMENT") {
                "PENDING"
            } else {
                "PENDING"
            }

            buffer += StagedImportItemEntity(
                batchId = batchId,
                rowNumber = d.rowNumber,
                sourceDocPage = d.sourceDocPage,
                actionType = effectiveAction.key,
                resolutionMode = resolution,
                matchedProductId = matchedProd?.id,
                matchedVariantId = matchedVar?.id,
                sku = cleanSku,
                barcode = cleanBarcode,
                productName = cleanName,
                category = d.category.trim().ifBlank { matchedProd?.category ?: "General" },
                brand = d.brand.trim().ifBlank { matchedProd?.brand ?: "" },
                unitLabel = UnitCatalog.normalize(d.unitLabel, fallback = matchedProd?.unitLabel?.ifBlank { "pcs" } ?: "pcs"),
                variantLabel = normVariant,
                variantType = d.variantType.trim().ifBlank { "Variant" },
                colorName = d.colorName.trim(),
                quantity = d.quantity,
                costPrice = d.costPrice,
                sellingPrice = d.sellingPrice,
                mrp = d.mrp,
                amount = d.amount,
                reorderLevel = d.reorderLevel,
                partyName = d.partyName.trim(),
                partyPhone = d.partyPhone.trim(),
                location = d.location.trim(),
                batchNumber = d.batchNumber.trim(),
                expiryDate = d.expiryDate.trim(),
                serialNumbersCsv = d.serialNumbersCsv.trim(),
                imageRef = d.imageRef.trim(),
                confidence = d.confidence,
                rawPayloadJson = d.rawPayloadJson,
                validationErrors = errStr,
                isDuplicate = isDup,
                approvalStatus = initialApproval,
                notes = d.notes
            )

            if (buffer.size >= STAGE_CHUNK_SIZE) {
                onboardingDao.insertStagedItems(buffer.toList())
                buffer.clear()
                yield()
            }
        }
        if (buffer.isNotEmpty()) {
            onboardingDao.insertStagedItems(buffer.toList())
            buffer.clear()
        }

        val updatedBatch = onboardingDao.getImportBatchById(batchId)!!.copy(
            validItems = validCount,
            invalidItems = invalidCount,
            duplicateItems = duplicateCount,
            updatedAt = System.currentTimeMillis()
        )
        onboardingDao.updateImportBatch(updatedBatch)
        return updatedBatch
    }

    suspend fun updateStagedItem(item: StagedImportItemEntity) {
        val revalidated = revalidateItem(item)
        onboardingDao.updateStagedItem(revalidated)
        refreshBatchCounts(item.batchId)
    }

    private fun revalidateItem(item: StagedImportItemEntity): StagedImportItemEntity {
        val errs = mutableListOf<String>()
        val action = BusinessActionType.fromKey(item.actionType)
        when (action) {
            BusinessActionType.CREATE_PRODUCT -> {
                if (item.productName.isBlank()) errs += "Product name required"
                if (item.quantity < 0) errs += "Negative quantity not allowed"
                if ((item.costPrice ?: 0.0) < 0) errs += "Cost price cannot be negative"
                if ((item.sellingPrice ?: 0.0) < 0) errs += "Selling price cannot be negative"
            }
            BusinessActionType.EXPENSE -> if (item.amount <= 0) errs += "Expense amount must be > 0"
            BusinessActionType.PURCHASE_REQUEST, BusinessActionType.PURCHASE_ORDER -> {
                if (item.quantity <= 0) errs += "Requested quantity must be > 0"
                if (item.productName.isBlank() && item.matchedProductId == null) errs += "Item name required"
            }
            else -> {
                if (item.quantity < 0 && action != BusinessActionType.STOCK_ADJUSTMENT) errs += "Negative quantity"
            }
        }
        return item.copy(validationErrors = errs.joinToString(" | "))
    }

    private suspend fun refreshBatchCounts(batchId: Long) {
        val items = onboardingDao.getStagedItemsForBatch(batchId)
        val batch = onboardingDao.getImportBatchById(batchId) ?: return
        onboardingDao.updateImportBatch(
            batch.copy(
                totalItems = items.size,
                validItems = items.count { it.validationErrors.isBlank() },
                invalidItems = items.count { it.validationErrors.isNotBlank() },
                duplicateItems = items.count { it.isDuplicate },
                processedItems = items.count { it.approvalStatus == "COMMITTED" },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun approveAllValid(batchId: Long) {
        onboardingDao.approveAllValidInBatch(batchId, "APPROVED")
    }

    suspend fun setItemApproval(item: StagedImportItemEntity, approved: Boolean) {
        if (item.approvalStatus == "COMMITTED") return
        val next = if (approved) "APPROVED" else "REJECTED"
        onboardingDao.updateStagedItem(item.copy(approvalStatus = next))
    }

    /**
     * Commits all APPROVED (or optionally all valid PENDING if autoApproveValid = true) items
     * in resumable 250-item transactional chunks with strict movementKey idempotency.
     * Supports [simulateFailAfterChunks] for verification of interruption & restart resilience.
     */
    suspend fun commitBatchChunked(
        batchId: Long,
        autoApproveValid: Boolean = false,
        simulateFailAfterChunks: Int? = null,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): BatchCommitResult {
        if (autoApproveValid) {
            onboardingDao.approveAllValidInBatch(batchId, "APPROVED")
        }
        val batch = onboardingDao.getImportBatchById(batchId)
            ?: return BatchCommitResult(batchId, "FAILED", 0, 0, 0, emptyList())

        onboardingDao.updateImportBatch(batch.copy(status = "PROCESSING", updatedAt = System.currentTimeMillis()))

        val allItems = onboardingDao.getStagedItemsForBatch(batchId)
        val candidates = allItems.filter {
            it.approvalStatus == "APPROVED" && it.resolutionMode != "SKIP" && it.validationErrors.isBlank()
        }
        val rejectedOrSkipped = allItems.count { it.approvalStatus == "REJECTED" || it.resolutionMode == "SKIP" }
        var committedNow = 0
        var failedNow = 0
        val createdProductIds = mutableListOf<Long>()

        val existingProducts = dao.getAllProductsSync()
        val existingVariants = dao.getAllVariantsSync()
        val cache = CatalogCommitCache(
            existingProducts = existingProducts,
            existingVariants = existingVariants,
            committedMoveKeys = onboardingDao.getMovementKeysForBatch(batchId).toHashSet()
        )

        val nonAggregateItems = candidates.filter {
            it.actionType !in listOf(
                BusinessActionType.SALE_DRAFT.key,
                BusinessActionType.PURCHASE_REQUEST.key,
                BusinessActionType.PURCHASE_ORDER.key
            )
        }
        val saleItemsCandidates = candidates.filter { it.actionType == BusinessActionType.SALE_DRAFT.key }
        val poItemsCandidates = candidates.filter {
            it.actionType == BusinessActionType.PURCHASE_REQUEST.key || it.actionType == BusinessActionType.PURCHASE_ORDER.key
        }

        val chunks = nonAggregateItems.chunked(COMMIT_CHUNK_SIZE)
        for ((chunkIdx, chunk) in chunks.withIndex()) {
            if (simulateFailAfterChunks != null && chunkIdx >= simulateFailAfterChunks) {
                val curBatch = onboardingDao.getImportBatchById(batchId)!!
                onboardingDao.updateImportBatch(
                    curBatch.copy(
                        status = "PARTIAL",
                        processedItems = allItems.count { it.approvalStatus == "COMMITTED" } + committedNow,
                        lastCheckpointIndex = chunkIdx * COMMIT_CHUNK_SIZE,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                return BatchCommitResult(
                    batchId = batchId,
                    status = "PARTIAL",
                    committedCount = committedNow,
                    skippedCount = rejectedOrSkipped,
                    failedCount = failedNow,
                    createdProductIds = createdProductIds
                )
            }

            db.withTransaction {
                val now = System.currentTimeMillis()
                val stagedUpdates = ArrayList<StagedImportItemEntity>(chunk.size)
                val movementsBuffer = ArrayList<InventoryMovementEntity>(chunk.size)

                for (item in chunk) {
                    try {
                        val pid = commitSingleCatalogOrLedgerItem(
                            item = item,
                            sourceRoute = batch.sourceRoute,
                            cache = cache,
                            movementsBuffer = movementsBuffer,
                            now = now
                        )
                        if (pid != null) createdProductIds += pid
                        stagedUpdates += item.copy(approvalStatus = "COMMITTED", committedRef = pid?.toString() ?: "OK")
                        committedNow++
                    } catch (e: Exception) {
                        stagedUpdates += item.copy(
                            approvalStatus = "FAILED",
                            validationErrors = (item.validationErrors + " " + (e.message ?: "Commit error")).trim()
                        )
                        failedNow++
                    }
                }
                if (movementsBuffer.isNotEmpty()) {
                    onboardingDao.insertMovementsIgnore(movementsBuffer)
                }
                if (stagedUpdates.isNotEmpty()) {
                    onboardingDao.updateStagedItems(stagedUpdates)
                }
                val curBatch = onboardingDao.getImportBatchById(batchId)!!
                onboardingDao.updateImportBatch(
                    curBatch.copy(
                        processedItems = committedNow,
                        lastCheckpointIndex = ((chunkIdx + 1) * COMMIT_CHUNK_SIZE).coerceAtMost(nonAggregateItems.size),
                        updatedAt = now
                    )
                )
            }
            onProgress?.invoke(committedNow, candidates.size)
            yield()
        }

        // Group approved SALE_DRAFT rows into a single bill through SoleBookRepository.completeSale
        if (saleItemsCandidates.isNotEmpty()) {
            val saleId = commitSaleDraftGroup(batch, saleItemsCandidates)
            if (saleId != null) {
                committedNow += saleItemsCandidates.size
            } else {
                failedNow += saleItemsCandidates.size
            }
        }

        // Group approved PURCHASE_REQUEST / PURCHASE_ORDER rows into PurchaseOrderEntity WITHOUT touching stock
        if (poItemsCandidates.isNotEmpty()) {
            val poId = commitPurchaseRequestGroup(batch, poItemsCandidates)
            if (poId != null) {
                committedNow += poItemsCandidates.size
            } else {
                failedNow += poItemsCandidates.size
            }
        }

        val finalItems = onboardingDao.getStagedItemsForBatch(batchId)
        val totalCommitted = finalItems.count { it.approvalStatus == "COMMITTED" }
        val totalFailed = finalItems.count { it.approvalStatus == "FAILED" }
        val pendingLeft = finalItems.count { it.approvalStatus == "PENDING" }

        val finalStatus = when {
            totalFailed > 0 && totalCommitted == 0 -> "FAILED"
            totalFailed > 0 || pendingLeft > 0 -> "PARTIAL"
            else -> "COMPLETED"
        }
        val latest = onboardingDao.getImportBatchById(batchId)!!
        onboardingDao.updateImportBatch(
            latest.copy(
                status = finalStatus,
                processedItems = totalCommitted,
                updatedAt = System.currentTimeMillis()
            )
        )
        return BatchCommitResult(
            batchId = batchId,
            status = finalStatus,
            committedCount = totalCommitted,
            skippedCount = rejectedOrSkipped,
            failedCount = totalFailed,
            createdProductIds = createdProductIds
        )
    }

    private inner class CatalogCommitCache(
        existingProducts: List<ProductEntity>,
        existingVariants: List<ProductVariantEntity>,
        val committedMoveKeys: MutableSet<String>
    ) {
        val byId = existingProducts.associateByTo(HashMap(existingProducts.size * 2 + 16)) { it.id }
        val byBarcode = existingProducts.filter { it.barcode.isNotBlank() }
            .associateByTo(HashMap()) { it.barcode.trim().lowercase() }
        val bySku = existingProducts.associateByTo(HashMap(existingProducts.size * 2 + 16)) { it.skuCode.trim().lowercase() }
        val byNormName = existingProducts.associateByTo(HashMap(existingProducts.size * 2 + 16)) { normalizeName(it.name) }
        val varByBarcode = existingVariants.filter { it.barcode.isNotBlank() }
            .associateByTo(HashMap()) { it.barcode.trim().lowercase() }
        val varByProdAndSize = existingVariants.associateByTo(HashMap(existingVariants.size * 2 + 16)) {
            it.productId to it.size.lowercase()
        }
        private var nextSkuNum: Int = (existingProducts.mapNotNull { p ->
            val dash = p.skuCode.indexOf('-')
            if (dash in 0 until p.skuCode.length - 1) p.skuCode.substring(dash + 1).toIntOrNull() else null
        }.maxOrNull() ?: 0) + 1

        fun nextAutoSku(prefix: String = "SB"): String {
            while (true) {
                val candidate = "$prefix-${nextSkuNum.toString().padStart(3, '0')}"
                nextSkuNum++
                if (candidate.lowercase() !in bySku) return candidate
            }
        }

        fun resolve(item: StagedImportItemEntity): ProductEntity? {
            item.matchedProductId?.let { id -> byId[id]?.let { return it } }
            item.barcode.takeIf { it.isNotBlank() }?.let { bc ->
                val lc = bc.trim().lowercase()
                byBarcode[lc]?.let { return it }
                varByBarcode[lc]?.let { v -> byId[v.productId]?.let { return it } }
            }
            item.sku.takeIf { it.isNotBlank() }?.let { sku ->
                bySku[sku.trim().lowercase()]?.let { return it }
            }
            if (item.productName.isNotBlank()) {
                byNormName[normalizeName(item.productName)]?.let { return it }
            }
            return null
        }

        fun registerProduct(p: ProductEntity) {
            byId[p.id] = p
            if (p.barcode.isNotBlank()) byBarcode[p.barcode.trim().lowercase()] = p
            if (p.skuCode.isNotBlank()) bySku[p.skuCode.trim().lowercase()] = p
            if (p.name.isNotBlank()) byNormName[normalizeName(p.name)] = p
        }

        fun registerVariant(v: ProductVariantEntity) {
            varByProdAndSize[v.productId to v.size.lowercase()] = v
            if (v.barcode.isNotBlank()) varByBarcode[v.barcode.trim().lowercase()] = v
        }
    }

    private suspend fun commitSingleCatalogOrLedgerItem(
        item: StagedImportItemEntity,
        sourceRoute: String,
        cache: CatalogCommitCache,
        movementsBuffer: MutableList<InventoryMovementEntity>,
        now: Long
    ): Long? {
        val action = BusinessActionType.fromKey(item.actionType)
        when (action) {
            BusinessActionType.EXPENSE -> {
                val cat = item.category.ifBlank { "Other" }
                dao.insertExpense(
                    ExpenseEntity(
                        category = cat,
                        amount = item.amount,
                        timestamp = now,
                        notes = "${item.notes} [Batch#${item.batchId}]".trim()
                    )
                )
                return null
            }
            BusinessActionType.VENDOR_PAYMENT -> {
                val vendor = dao.getAllVendorsSync().firstOrNull {
                    it.name.equals(item.partyName, ignoreCase = true) ||
                        (item.partyPhone.isNotBlank() && it.phone.endsWith(item.partyPhone.takeLast(10)))
                } ?: return null
                repository.recordVendorPayment(vendor.id, item.amount, "CASH", item.notes)
                return vendor.id
            }
            BusinessActionType.CUSTOMER_CREDIT -> {
                val phone = item.partyPhone.ifBlank { "UDHAAR-${item.partyName.take(10)}" }
                val existing = dao.getCustomerByPhone(phone)
                if (existing != null) {
                    dao.updateCustomer(
                        existing.copy(
                            name = existing.name.ifBlank { item.partyName },
                            creditBalance = existing.creditBalance + item.amount,
                            lastVisitAt = now
                        )
                    )
                    return existing.id
                } else {
                    return dao.insertCustomer(
                        CustomerEntity(
                            phone = phone,
                            name = item.partyName,
                            firstVisitAt = now,
                            lastVisitAt = now,
                            creditBalance = item.amount,
                            notes = item.notes
                        )
                    )
                }
            }
            BusinessActionType.RETURN_DRAFT -> {
                val prod = cache.resolve(item) ?: return null
                val varLabel = item.variantLabel.ifBlank { "Standard" }
                val variant = cache.varByProdAndSize[prod.id to varLabel.lowercase()]
                val qty = item.quantity.toInt().coerceAtLeast(1)
                val mvKey = "batch:${item.batchId}:item:${item.id}:return"
                if (!cache.committedMoveKeys.add(mvKey)) return prod.id
                val before = variant?.stock ?: 0
                val after = before + qty
                val updatedVar = if (variant != null) {
                    variant.copy(stock = after).also { dao.updateVariant(it) }
                } else {
                    val ins = ProductVariantEntity(productId = prod.id, size = varLabel, stock = after)
                    ins.copy(id = dao.insertVariant(ins))
                }
                cache.registerVariant(updatedVar)
                movementsBuffer += InventoryMovementEntity(
                    movementKey = mvKey,
                    productId = prod.id,
                    variantId = updatedVar.id,
                    sizeLabel = varLabel,
                    movementType = "RETURN",
                    qtyDelta = qty,
                    beforeQty = before,
                    afterQty = after,
                    unitCost = prod.costPrice,
                    location = item.location,
                    sourceRoute = sourceRoute,
                    sourceBatchId = item.batchId,
                    sourceRefId = item.sourceDocPage,
                    timestamp = now,
                    notes = item.notes
                )
                return prod.id
            }
            else -> {
                // CREATE_PRODUCT, UPDATE_PRODUCT, OPENING_STOCK, GOODS_RECEIVED, STOCK_COUNT, STOCK_ADJUSTMENT
                val resolved = cache.resolve(item)
                val targetPid: Long
                val isNew = resolved == null
                var currentProd: ProductEntity

                if (resolved == null) {
                    val assignedSku = item.sku.ifBlank { cache.nextAutoSku("SB") }
                    val newProd = ProductEntity(
                        skuCode = assignedSku,
                        name = item.productName.ifBlank { "Item $assignedSku" },
                        category = item.category.ifBlank { "General" },
                        brand = item.brand,
                        gender = "Unisex",
                        photoUri = "",
                        costPrice = (item.costPrice ?: 0.0).coerceAtLeast(0.0),
                        sellingPrice = (item.sellingPrice ?: (item.costPrice ?: 0.0)).coerceAtLeast(0.0),
                        mrp = item.mrp,
                        supplier = item.partyName,
                        reorderLevel = item.reorderLevel ?: 5,
                        colorTag = item.colorName,
                        rackLocation = item.location,
                        unitLabel = item.unitLabel.ifBlank { "pcs" },
                        batchOrSerial = item.batchNumber.ifBlank { item.serialNumbersCsv },
                        expiryOrWarranty = item.expiryDate,
                        barcode = item.barcode,
                        trackBatchExpiry = item.batchNumber.isNotBlank() || item.expiryDate.isNotBlank(),
                        trackSerialNumbers = item.serialNumbersCsv.isNotBlank(),
                        createdAt = now,
                        updatedAt = now
                    )
                    targetPid = dao.insertProduct(newProd)
                    currentProd = newProd.copy(id = targetPid)
                    cache.registerProduct(currentProd)
                } else {
                    targetPid = resolved.id
                    currentProd = if (action == BusinessActionType.UPDATE_PRODUCT || item.resolutionMode == "UPDATE_EXISTING") {
                        val updated = resolved.copy(
                            name = item.productName.ifBlank { resolved.name },
                            category = item.category.ifBlank { resolved.category },
                            brand = item.brand.ifBlank { resolved.brand },
                            costPrice = item.costPrice?.takeIf { it > 0 } ?: resolved.costPrice,
                            sellingPrice = item.sellingPrice?.takeIf { it > 0 } ?: resolved.sellingPrice,
                            mrp = item.mrp ?: resolved.mrp,
                            barcode = item.barcode.ifBlank { resolved.barcode },
                            unitLabel = item.unitLabel.ifBlank { resolved.unitLabel },
                            rackLocation = item.location.ifBlank { resolved.rackLocation },
                            supplier = item.partyName.ifBlank { resolved.supplier },
                            reorderLevel = item.reorderLevel ?: resolved.reorderLevel,
                            updatedAt = now
                        )
                        dao.updateProduct(updated)
                        cache.registerProduct(updated)
                        updated
                    } else {
                        resolved
                    }
                }

                // Handle variant(s) and idempotent inventory movement
                val breakdownPairs = parseBreakdownOrSingle(item)
                for ((varLabel, qtyDouble) in breakdownPairs) {
                    val qtyInt = qtyDouble.toInt()
                    val mvKey = "batch:${item.batchId}:item:${item.id}:var:$varLabel"
                    if (!cache.committedMoveKeys.add(mvKey)) continue // Idempotent!

                    val curVar = cache.varByProdAndSize[targetPid to varLabel.lowercase()]
                    val beforeQty = curVar?.stock ?: 0
                    val afterQty: Int
                    val delta: Int
                    val mvType: String

                    when (action) {
                        BusinessActionType.STOCK_COUNT -> {
                            afterQty = qtyInt.coerceAtLeast(0)
                            delta = afterQty - beforeQty
                            mvType = "COUNT_RECONCILE"
                        }
                        BusinessActionType.STOCK_ADJUSTMENT -> {
                            delta = qtyInt
                            afterQty = (beforeQty + delta).coerceAtLeast(0)
                            mvType = "ADJUSTMENT"
                        }
                        BusinessActionType.GOODS_RECEIVED -> {
                            delta = qtyInt.coerceAtLeast(0)
                            afterQty = beforeQty + delta
                            mvType = "PURCHASE_RECEIPT"
                        }
                        else -> { // CREATE_PRODUCT, UPDATE_PRODUCT, OPENING_STOCK
                            if (isNew || item.resolutionMode == "UPDATE_EXISTING") {
                                afterQty = if (isNew || qtyInt > 0) qtyInt.coerceAtLeast(0) else beforeQty
                                delta = afterQty - beforeQty
                            } else { // MERGE_STOCK
                                delta = qtyInt.coerceAtLeast(0)
                                afterQty = beforeQty + delta
                            }
                            mvType = if (isNew || action == BusinessActionType.OPENING_STOCK) "OPENING_STOCK" else "ADJUSTMENT"
                        }
                    }

                    val updatedVar = if (curVar != null) {
                        curVar.copy(
                            stock = afterQty,
                            variantType = item.variantType.ifBlank { curVar.variantType },
                            barcode = item.barcode.ifBlank { curVar.barcode },
                            colorName = item.colorName.ifBlank { curVar.colorName },
                            location = item.location.ifBlank { curVar.location },
                            batchNumber = item.batchNumber.ifBlank { curVar.batchNumber },
                            expiryDate = item.expiryDate.ifBlank { curVar.expiryDate },
                            serialNumbersCsv = item.serialNumbersCsv.ifBlank { curVar.serialNumbersCsv }
                        ).also { dao.updateVariant(it) }
                    } else {
                        val inserted = ProductVariantEntity(
                            productId = targetPid,
                            size = varLabel,
                            stock = afterQty,
                            variantType = item.variantType.ifBlank { "Variant" },
                            barcode = item.barcode,
                            colorName = item.colorName,
                            location = item.location,
                            batchNumber = item.batchNumber,
                            expiryDate = item.expiryDate,
                            serialNumbersCsv = item.serialNumbersCsv
                        )
                        val vId = dao.insertVariant(inserted)
                        inserted.copy(id = vId)
                    }
                    cache.registerVariant(updatedVar)

                    if (delta != 0 || mvType == "OPENING_STOCK" || mvType == "COUNT_RECONCILE") {
                        movementsBuffer += InventoryMovementEntity(
                            movementKey = mvKey,
                            productId = targetPid,
                            variantId = updatedVar.id,
                            sizeLabel = varLabel,
                            movementType = mvType,
                            qtyDelta = delta,
                            beforeQty = beforeQty,
                            afterQty = afterQty,
                            unitCost = item.costPrice ?: currentProd.costPrice,
                            location = item.location.ifBlank { currentProd.rackLocation },
                            batchNumber = item.batchNumber,
                            serialNumbersCsv = item.serialNumbersCsv,
                            sourceRoute = sourceRoute,
                            sourceBatchId = item.batchId,
                            sourceRefId = item.sourceDocPage,
                            timestamp = now,
                            notes = item.notes
                        )
                    }

                    if (action == BusinessActionType.GOODS_RECEIVED && delta > 0) {
                        val unitCost = item.costPrice ?: currentProd.costPrice
                        val newAvgCost = if (beforeQty + delta > 0) {
                            ((beforeQty * currentProd.costPrice) + (delta * unitCost)) / (beforeQty + delta)
                        } else unitCost
                        val updProd = currentProd.copy(costPrice = newAvgCost, updatedAt = now)
                        dao.updateProduct(updProd)
                        currentProd = updProd
                        cache.registerProduct(updProd)
                        dao.insertPurchase(
                            PurchaseEntity(
                                productId = targetPid,
                                totalUnits = delta,
                                unitCostPrice = unitCost,
                                totalCost = delta * unitCost,
                                supplier = item.partyName.ifBlank { currentProd.supplier },
                                sizeBreakdown = "$varLabel:$delta",
                                timestamp = now,
                                notes = item.notes.ifBlank { "GRN Import Batch #${item.batchId}" }
                            )
                        )
                    }
                }
                return targetPid
            }
        }
    }

    private suspend fun commitSaleDraftGroup(
        batch: ImportBatchEntity,
        items: List<StagedImportItemEntity>
    ): Long? {
        val firstKey = "batch:${batch.id}:sale:${items.first().id}"
        if (onboardingDao.getMovementByKey(firstKey) != null) {
            items.forEach { onboardingDao.updateStagedItem(it.copy(approvalStatus = "COMMITTED")) }
            return 0L
        }
        val lines = mutableListOf<Pair<StagedImportItemEntity, CartLineRequest>>()
        for (item in items) {
            val prod = resolveTargetProduct(item)
            if (prod == null) {
                onboardingDao.updateStagedItem(
                    item.copy(approvalStatus = "FAILED", validationErrors = "Product not found in catalog")
                )
                return null
            }
            val sizeLabel = item.variantLabel.ifBlank {
                dao.getVariantsForProduct(prod.id).firstOrNull()?.size ?: "Standard"
            }
            val qty = item.quantity.toInt().coerceAtLeast(1)
            val sellPrice = item.sellingPrice?.takeIf { it > 0 } ?: prod.sellingPrice
            lines += item to CartLineRequest(prod, sizeLabel, qty, sellPrice)
        }
        val partyPhone = items.firstNotNullOfOrNull { it.partyPhone.takeIf { p -> p.isNotBlank() } } ?: ""
        val partyName = items.firstNotNullOfOrNull { it.partyName.takeIf { n -> n.isNotBlank() } } ?: ""
        val res = repository.completeSale(
            lines = lines.map { it.second },
            paymentMode = if (partyPhone.isNotBlank() && items.any { it.notes.contains("credit", ignoreCase = true) || it.notes.contains("udhaar", ignoreCase = true) }) "CREDIT" else "CASH",
            discountType = "AMOUNT",
            discountInput = 0.0,
            customerPhone = partyPhone,
            customerName = partyName,
            notes = "Document Batch #${batch.id}"
        )
        return res.fold(
            onSuccess = { saleId ->
                val now = System.currentTimeMillis()
                lines.forEach { (item, req) ->
                    onboardingDao.updateStagedItem(
                        item.copy(approvalStatus = "COMMITTED", committedRef = "SALE:$saleId")
                    )
                    onboardingDao.insertMovementIgnore(
                        InventoryMovementEntity(
                            movementKey = "batch:${batch.id}:sale:${item.id}",
                            productId = req.product.id,
                            sizeLabel = req.size,
                            movementType = "SALE",
                            qtyDelta = -req.quantity,
                            beforeQty = 0,
                            afterQty = 0,
                            unitCost = req.product.costPrice,
                            sourceRoute = batch.sourceRoute,
                            sourceBatchId = batch.id,
                            sourceRefId = "SALE:$saleId",
                            timestamp = now,
                            notes = "From staged sale draft"
                        )
                    )
                }
                saleId
            },
            onFailure = { err ->
                items.forEach {
                    onboardingDao.updateStagedItem(
                        it.copy(approvalStatus = "FAILED", validationErrors = err.message ?: "Sale failed")
                    )
                }
                null
            }
        )
    }

    /**
     * Stages Replenishment / Purchase Requests into PurchaseOrderEntity (status = "REQUESTED")
     * WITHOUT modifying stock until Goods Received is confirmed!
     */
    private suspend fun commitPurchaseRequestGroup(
        batch: ImportBatchEntity,
        items: List<StagedImportItemEntity>
    ): Long? {
        val arr = JSONArray()
        var estTotal = 0.0
        items.forEach { item ->
            val estUnit = item.costPrice ?: item.sellingPrice ?: 0.0
            estTotal += estUnit * item.quantity
            arr.put(JSONObject().apply {
                put("productId", item.matchedProductId ?: 0L)
                put("sku", item.sku)
                put("barcode", item.barcode)
                put("name", item.productName)
                put("variant", item.variantLabel)
                put("unit", item.unitLabel)
                put("qty", item.quantity)
                put("estUnitCost", estUnit)
                put("notes", item.notes)
            })
        }
        val poNumber = "PR-${batch.id}-${System.currentTimeMillis().toString().takeLast(4)}"
        val supplierName = items.firstNotNullOfOrNull { it.partyName.takeIf { s -> s.isNotBlank() } } ?: ""
        val poId = onboardingDao.insertPurchaseOrder(
            PurchaseOrderEntity(
                poNumber = poNumber,
                supplierName = supplierName,
                status = if (items.any { it.actionType == BusinessActionType.PURCHASE_ORDER.key }) "ORDERED" else "REQUESTED",
                itemsJson = arr.toString(),
                estimatedTotal = estTotal,
                sourceBatchId = batch.id,
                sourceDocumentRef = batch.fileName,
                notes = "Auto-created replenishment request (stock unchanged until receipt)"
            )
        )
        items.forEach { item ->
            onboardingDao.updateStagedItem(
                item.copy(approvalStatus = "COMMITTED", committedRef = "PO:$poId ($poNumber)")
            )
        }
        return poId
    }

    /**
     * Explicitly confirms Goods Received for a PurchaseOrderEntity — ONLY now does inventory stock increase
     * and weighted-average cost update!
     */
    suspend fun confirmPurchaseOrderReceived(poId: Long, paidAmount: Double? = null): Boolean {
        val po = onboardingDao.getPurchaseOrderById(poId) ?: return false
        if (po.status == "RECEIVED") return true
        val arr = JSONArray(po.itemsJson)
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pid = o.optLong("productId", 0L)
            val qty = o.optDouble("qty", 0.0).toInt().coerceAtLeast(0)
            val unitCost = o.optDouble("estUnitCost", 0.0)
            val variant = o.optString("variant", "Standard").ifBlank { "Standard" }
            if (pid > 0 && qty > 0) {
                repository.restockProduct(
                    productId = pid,
                    addedPerSize = mapOf(variant to qty),
                    newUnitCost = unitCost.coerceAtLeast(1.0),
                    supplier = po.supplierName,
                    notes = "GRN from ${po.poNumber}",
                    vendorId = po.vendorId,
                    paidAmount = paidAmount
                )
                onboardingDao.insertMovementIgnore(
                    InventoryMovementEntity(
                        movementKey = "po:$poId:line:$i:grn",
                        productId = pid,
                        sizeLabel = variant,
                        movementType = "PURCHASE_RECEIPT",
                        qtyDelta = qty,
                        beforeQty = 0,
                        afterQty = qty,
                        unitCost = unitCost,
                        sourceRoute = "PURCHASE_ORDER",
                        sourceRefId = po.poNumber,
                        timestamp = now
                    )
                )
            }
        }
        onboardingDao.updatePurchaseOrder(po.copy(status = "RECEIVED", receivedAt = now))
        return true
    }

    private suspend fun resolveTargetProduct(item: StagedImportItemEntity): ProductEntity? {
        item.matchedProductId?.let { id -> dao.getProductById(id)?.let { return it } }
        item.barcode.takeIf { it.isNotBlank() }?.let { bc ->
            onboardingDao.getProductByBarcode(bc)?.let { return it }
            onboardingDao.getVariantByBarcode(bc)?.let { v -> dao.getProductById(v.productId)?.let { return it } }
        }
        item.sku.takeIf { it.isNotBlank() }?.let { sku ->
            onboardingDao.getProductBySku(sku)?.let { return it }
        }
        if (item.productName.isNotBlank()) {
            val norm = normalizeName(item.productName)
            dao.getAllProductsSync().firstOrNull { normalizeName(it.name) == norm }?.let { return it }
        }
        return null
    }

    private fun parseBreakdownOrSingle(item: StagedImportItemEntity): List<Pair<String, Double>> {
        val raw = runCatching { JSONObject(item.rawPayloadJson).optString("variant_breakdown", "") }.getOrDefault("")
        if (raw.contains(":")) {
            val parsed = raw.split(',', ';', '|').mapNotNull { tok ->
                val parts = tok.trim().split(':')
                if (parts.size == 2) {
                    val sz = parts[0].trim()
                    val q = parts[1].trim().toDoubleOrNull()
                    if (sz.isNotEmpty() && q != null) sz to q else null
                } else null
            }
            if (parsed.isNotEmpty()) return parsed
        }
        return listOf(item.variantLabel.ifBlank { "Standard" } to item.quantity)
    }

    private fun extractRowWithMapping(
        rowNumber: Int,
        cells: List<String>,
        mapping: Map<Int, TargetField>,
        defaultCategory: String,
        defaultUnit: String
    ): StagedRowDraft? {
        if (cells.all { it.isBlank() }) return null
        var sku = ""
        var barcode = ""
        var name = ""
        var category = defaultCategory
        var brand = ""
        var unit = defaultUnit
        var cost: Double? = null
        var sell: Double? = null
        var mrp: Double? = null
        var qty = 0.0
        var reorder: Int? = null
        var variant = "Standard"
        var variantType = "Variant"
        var color = ""
        var model = ""
        var pack = ""
        var breakdown = ""
        var location = ""
        var supplier = ""
        var batchNo = ""
        var expiry = ""
        var serials = ""
        var imageRef = ""

        mapping.forEach { (colIdx, field) ->
            val v = cells.getOrNull(colIdx)?.trim().orEmpty()
            if (v.isEmpty()) return@forEach
            val vAscii = UnitCatalog.normalizeIndicDigits(v)
            when (field) {
                TargetField.SKU -> sku = v
                TargetField.BARCODE -> barcode = vAscii
                TargetField.NAME -> name = v
                TargetField.CATEGORY -> category = v
                TargetField.BRAND -> brand = v
                TargetField.UNIT -> unit = UnitCatalog.normalize(v, defaultUnit)
                TargetField.COST_PRICE -> cost = parseCleanDouble(vAscii)
                TargetField.SELLING_PRICE -> sell = parseCleanDouble(vAscii)
                TargetField.MRP -> mrp = parseCleanDouble(vAscii)
                TargetField.OPENING_STOCK -> qty = parseCleanDouble(vAscii) ?: 0.0
                TargetField.REORDER_LEVEL -> reorder = parseCleanDouble(vAscii)?.toInt()
                TargetField.VARIANT -> variant = v
                TargetField.VARIANT_TYPE -> variantType = v
                TargetField.COLOR -> color = v
                TargetField.MODEL -> model = v
                TargetField.PACK_SIZE -> pack = v
                TargetField.VARIANT_BREAKDOWN -> breakdown = vAscii
                TargetField.LOCATION -> location = v
                TargetField.SUPPLIER -> supplier = v
                TargetField.BATCH_NUMBER -> batchNo = v
                TargetField.EXPIRY_DATE -> expiry = v
                TargetField.SERIAL_NUMBERS -> serials = v
                TargetField.IMAGE_REF -> imageRef = v
                TargetField.IGNORE -> {}
            }
        }

        val resolvedVariant = listOf(variant.takeIf { it != "Standard" }, pack, model, color)
            .filterNotNull()
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" / ")
            .ifBlank { variant }

        val rawJson = JSONObject().apply {
            if (breakdown.isNotBlank()) put("variant_breakdown", breakdown)
            put("cells", JSONArray(cells))
        }.toString()

        return StagedRowDraft(
            rowNumber = rowNumber,
            actionType = BusinessActionType.CREATE_PRODUCT,
            sku = sku,
            barcode = barcode,
            productName = name,
            category = category,
            brand = brand,
            unitLabel = unit,
            variantLabel = resolvedVariant,
            variantType = variantType,
            colorName = color,
            quantity = qty,
            costPrice = cost,
            sellingPrice = sell,
            mrp = mrp,
            reorderLevel = reorder,
            partyName = supplier,
            location = location,
            batchNumber = batchNo,
            expiryDate = expiry,
            serialNumbersCsv = serials,
            imageRef = imageRef,
            rawPayloadJson = rawJson
        )
    }

    private fun parseCleanDouble(raw: String): Double? {
        val cleaned = raw.replace("₹", "").replace("$", "").replace(",", "").trim()
        return cleaned.toDoubleOrNull()
    }

    private fun normalizeName(raw: String): String =
        raw.trim().lowercase().replace(Regex("\\s+"), " ")

    suspend fun exportBatchErrorReport(context: Context, batchId: Long): File {
        val items = onboardingDao.getStagedItemsForBatch(batchId)
        val rows = mutableListOf(
            listOf("Row", "Status", "SKU", "Barcode", "ProductName", "Variant", "Qty", "Errors")
        )
        items.filter { it.validationErrors.isNotBlank() || it.approvalStatus == "FAILED" }.forEach { item ->
            rows += listOf(
                item.rowNumber.toString(),
                item.approvalStatus,
                item.sku,
                item.barcode,
                item.productName,
                item.variantLabel,
                item.quantity.toString(),
                item.validationErrors
            )
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "import_errors_batch_${batchId}.csv")
        file.writeText(SpreadsheetCodec.buildCsvFromRows(rows), Charsets.UTF_8)
        onboardingDao.getImportBatchById(batchId)?.let { b ->
            onboardingDao.updateImportBatch(b.copy(errorReportPath = file.absolutePath))
        }
        return file
    }
}
