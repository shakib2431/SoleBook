package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.*
import com.example.domain.ComputedQuoteLine
import com.example.domain.QuotationCalculationResult
import com.example.domain.WholesaleStarterSeeder
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class WholesaleRepository(
    private val db: SoleBookDatabase
) {
    private val wholesaleDao = db.wholesaleDao()
    private val soleBookDao = db.soleBookDao()
    private val onboardingDao = db.onboardingDao()

    val rawMaterialsFlow: Flow<List<RawMaterialEntity>> = wholesaleDao.observeRawMaterials()
    val bomTemplatesFlow: Flow<List<BomTemplateEntity>> = wholesaleDao.observeBomTemplates()
    val bomRulesFlow: Flow<List<BomMaterialRuleEntity>> = wholesaleDao.observeBomMaterialRules()
    val wholesaleQuotesFlow: Flow<List<WholesaleQuoteEntity>> = wholesaleDao.observeWholesaleQuotes()
    val wholesaleQuoteLinesFlow: Flow<List<WholesaleQuoteLineEntity>> = wholesaleDao.observeAllQuoteLines()

    suspend fun saveRawMaterial(material: RawMaterialEntity): Long {
        val updated = material.copy(updatedAt = System.currentTimeMillis())
        return if (updated.id == 0L) {
            wholesaleDao.insertRawMaterial(updated)
        } else {
            wholesaleDao.updateRawMaterial(updated)
            updated.id
        }
    }

    suspend fun deleteRawMaterial(id: Long) {
        wholesaleDao.deleteRawMaterialById(id)
    }

    suspend fun saveBomTemplateWithRules(
        template: BomTemplateEntity,
        rules: List<BomMaterialRuleEntity>
    ): Long {
        return db.withTransaction {
            val tid = if (template.id == 0L) {
                wholesaleDao.insertBomTemplate(template)
            } else {
                wholesaleDao.updateBomTemplate(template)
                template.id
            }
            wholesaleDao.deleteRulesForTemplate(tid)
            if (rules.isNotEmpty()) {
                wholesaleDao.insertBomRules(rules.map { it.copy(id = 0L, templateId = tid) })
            }
            tid
        }
    }

    suspend fun deleteBomTemplate(templateId: Long) {
        db.withTransaction {
            wholesaleDao.deleteRulesForTemplate(templateId)
            wholesaleDao.deleteBomTemplateById(templateId)
        }
    }

    suspend fun seedMattressStarterBom() {
        db.withTransaction {
            WholesaleStarterSeeder.seedMattressStarterIfEmpty(wholesaleDao)
        }
    }

    suspend fun saveWholesaleQuote(
        existingId: Long = 0L,
        existingQuoteNumber: String = "",
        buyerName: String,
        buyerPhone: String,
        buyerCompany: String,
        status: String,
        templateId: Long?,
        title: String,
        lengthIn: Double,
        widthIn: Double,
        thicknessIn: Double,
        densityFactor: Double,
        quantity: Int,
        stitchChargePerPc: Double,
        transportCost: Double,
        discountPercent: Double,
        gstPercent: Double,
        marginPercent: Double,
        validUntil: String,
        notes: String,
        calc: QuotationCalculationResult
    ): Long {
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val existing = if (existingId > 0L) wholesaleDao.getWholesaleQuoteById(existingId) else null
            val quoteNo = existingQuoteNumber.ifBlank {
                existing?.quoteNumber ?: run {
                    val count = wholesaleDao.getAllWholesaleQuotesSync().size + 1
                    "WQ-${count.toString().padStart(4, '0')}"
                }
            }
            val quoteEntity = WholesaleQuoteEntity(
                id = existingId,
                quoteNumber = quoteNo,
                buyerName = buyerName.trim(),
                buyerPhone = buyerPhone.trim(),
                buyerCompany = buyerCompany.trim(),
                status = status,
                templateId = templateId,
                title = title.trim().ifBlank { "Custom Wholesale Order" },
                specSummary = calc.specSummary,
                lengthIn = lengthIn,
                widthIn = widthIn,
                thicknessIn = thicknessIn,
                densityFactor = densityFactor,
                quantity = quantity.coerceAtLeast(1),
                stitchChargePerPc = stitchChargePerPc,
                transportCost = transportCost,
                discountPercent = discountPercent,
                gstPercent = gstPercent,
                marginPercent = marginPercent,
                unitManufacturingCost = calc.unitManufacturingCost,
                unitQuotedPrice = calc.unitQuotedPriceAfterDiscount,
                totalCost = calc.totalManufacturingCost,
                totalQuoteAmount = calc.totalQuoteBeforeGst,
                gstAmount = calc.gstAmount,
                grandTotal = calc.grandTotal,
                validUntil = validUntil.trim(),
                linkedPoId = existing?.linkedPoId,
                linkedSaleId = existing?.linkedSaleId,
                notes = notes.trim(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            val savedId = if (existingId == 0L) {
                wholesaleDao.insertWholesaleQuote(quoteEntity)
            } else {
                wholesaleDao.updateWholesaleQuote(quoteEntity)
                existingId
            }
            wholesaleDao.deleteLinesForQuote(savedId)
            val lineEntities = calc.lines.map { c: ComputedQuoteLine ->
                WholesaleQuoteLineEntity(
                    quoteId = savedId,
                    rawMaterialId = c.rawMaterialId,
                    itemName = c.itemName,
                    category = c.category,
                    unit = c.unit,
                    qtyPerPiece = c.qtyPerPiece,
                    totalQty = c.totalQty,
                    unitRate = c.unitRate,
                    wastagePct = c.wastagePct,
                    lineTotal = c.lineTotal,
                    isOverridden = c.isOverridden
                )
            }
            if (lineEntities.isNotEmpty()) {
                wholesaleDao.insertQuoteLines(lineEntities)
            }
            savedId
        }
    }

    suspend fun updateQuoteStatus(quoteId: Long, newStatus: String) {
        val quote = wholesaleDao.getWholesaleQuoteById(quoteId) ?: return
        wholesaleDao.updateWholesaleQuote(
            quote.copy(status = newStatus, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteQuote(quoteId: Long) {
        db.withTransaction {
            wholesaleDao.deleteLinesForQuote(quoteId)
            wholesaleDao.deleteWholesaleQuoteById(quoteId)
        }
    }

    /**
     * Generates a supplier Purchase Order Request (`PurchaseOrderEntity`) from a saved quote's BOM lines.
     * Raw material stock is NOT inflated until the user marks goods received.
     */
    suspend fun createPurchaseOrderFromQuote(
        quoteId: Long,
        vendorId: Long? = null,
        supplierName: String = ""
    ): Result<Long> {
        return try {
            val poId = db.withTransaction {
                val quote = wholesaleDao.getWholesaleQuoteById(quoteId)
                    ?: throw IllegalStateException("Quote not found")
                val lines = wholesaleDao.getLinesForQuote(quoteId)
                if (lines.isEmpty()) throw IllegalStateException("Quote has no raw material lines")

                val vendor = vendorId?.let { soleBookDao.getVendorById(it) }
                val resolvedSupplier = supplierName.ifBlank { vendor?.name ?: "Raw Material Supplier" }
                val existingPos = onboardingDao.getAllPurchaseOrdersSync().size + 1
                val poNumber = "PO-WQ-${existingPos.toString().padStart(4, '0')}"

                val jsonArray = JSONArray().apply {
                    lines.forEach { l ->
                        put(JSONObject().apply {
                            put("rawMaterialId", l.rawMaterialId ?: 0L)
                            put("name", l.itemName)
                            put("category", l.category)
                            put("unit", l.unit)
                            put("qty", l.totalQty)
                            put("unitRate", l.unitRate)
                            put("lineTotal", l.lineTotal)
                        })
                    }
                }
                val estimatedMaterialTotal = lines.sumOf { it.lineTotal }
                val newPoId = onboardingDao.insertPurchaseOrder(
                    PurchaseOrderEntity(
                        poNumber = poNumber,
                        vendorId = vendorId,
                        supplierName = resolvedSupplier,
                        status = "REQUESTED",
                        itemsJson = jsonArray.toString(),
                        estimatedTotal = estimatedMaterialTotal,
                        sourceDocumentRef = quote.quoteNumber,
                        notes = "BOM material requirement for ${quote.title} (${quote.quantity} pcs)",
                        createdAt = System.currentTimeMillis()
                    )
                )
                wholesaleDao.updateWholesaleQuote(
                    quote.copy(linkedPoId = newPoId, updatedAt = System.currentTimeMillis())
                )
                newPoId
            }
            Result.success(poId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converts a Wholesale Quote directly into a connected B2B `SaleEntity(saleChannel = "WHOLESALE")`
     * so revenue, COGS, GST, customer udhaar/credit, and net profit update across the unified ledger.
     * Optionally deducts tracked raw materials (`trackStock == true`) without blocking un-tracked materials.
     */
    suspend fun convertQuoteToWholesaleSale(
        quoteId: Long,
        paymentMode: String = "CASH"
    ): Result<Long> {
        return try {
            val saleId = db.withTransaction {
                val quote = wholesaleDao.getWholesaleQuoteById(quoteId)
                    ?: throw IllegalStateException("Quote not found")
                if (quote.linkedSaleId != null) {
                    throw IllegalStateException("Quote ${quote.quoteNumber} is already billed.")
                }
                val lines = wholesaleDao.getLinesForQuote(quoteId)
                val now = System.currentTimeMillis()

                // Link or create customer record if phone present
                val cleanPhone = quote.buyerPhone.trim()
                var customerId: Long? = null
                val finalBuyerName = quote.buyerName.ifBlank { quote.buyerCompany }.trim()
                if (cleanPhone.isNotEmpty()) {
                    val existingCust = soleBookDao.getCustomerByPhone(cleanPhone)
                    if (existingCust != null) {
                        soleBookDao.updateCustomer(
                            existingCust.copy(
                                name = existingCust.name.ifBlank { finalBuyerName },
                                lastVisitAt = now,
                                totalSpent = existingCust.totalSpent + quote.grandTotal,
                                visitCount = existingCust.visitCount + 1,
                                favouriteCategory = "Wholesale BOM",
                                creditBalance = existingCust.creditBalance + if (paymentMode == "CREDIT") quote.grandTotal else 0.0
                            )
                        )
                        customerId = existingCust.id
                    } else {
                        customerId = soleBookDao.insertCustomer(
                            CustomerEntity(
                                phone = cleanPhone,
                                name = finalBuyerName,
                                firstVisitAt = now,
                                lastVisitAt = now,
                                totalSpent = quote.grandTotal,
                                visitCount = 1,
                                favouriteCategory = "Wholesale BOM",
                                favouriteSize = quote.specSummary,
                                whatsappConsent = true,
                                creditBalance = if (paymentMode == "CREDIT") quote.grandTotal else 0.0
                            )
                        )
                    }
                }

                val existingSalesCount = soleBookDao.getAllSalesSync().size
                val billNumber = "WS-${(existingSalesCount + 1).toString().padStart(4, '0')}"
                val newSaleId = soleBookDao.insertSale(
                    SaleEntity(
                        billNumber = billNumber,
                        timestamp = now,
                        customerId = customerId,
                        customerPhoneSnapshot = cleanPhone,
                        customerNameSnapshot = finalBuyerName,
                        paymentMode = paymentMode,
                        discountAmount = 0.0,
                        discountType = "PERCENT",
                        discountInput = quote.discountPercent,
                        gstEnabled = quote.gstPercent > 0.0,
                        gstRate = quote.gstPercent,
                        gstInclusive = false,
                        gstAmount = quote.gstAmount,
                        subtotal = quote.totalQuoteAmount,
                        totalRevenue = quote.totalQuoteAmount,
                        totalCollected = quote.grandTotal,
                        creditSettled = paymentMode != "CREDIT",
                        notes = "Wholesale Quote ${quote.quoteNumber} • ${quote.specSummary}",
                        saleChannel = "WHOLESALE"
                    )
                )

                soleBookDao.insertSaleItems(
                    listOf(
                        SaleItemEntity(
                            saleId = newSaleId,
                            productId = 0L,
                            productNameSnapshot = quote.title,
                            skuSnapshot = quote.quoteNumber,
                            categorySnapshot = "Wholesale BOM",
                            size = "${quote.lengthIn.toInt()}×${quote.widthIn.toInt()}×${quote.thicknessIn.toInt()}\"",
                            quantity = quote.quantity.coerceAtLeast(1),
                            costPriceAtSale = quote.unitManufacturingCost,
                            sellingPriceAtSale = quote.unitQuotedPrice,
                            lineTotal = quote.totalQuoteAmount
                        )
                    )
                )

                // Optional raw-material stock deduction ONLY when trackStock == true
                for (qLine in lines) {
                    val matId = qLine.rawMaterialId ?: continue
                    val mat = wholesaleDao.getRawMaterialById(matId) ?: continue
                    if (mat.trackStock) {
                        wholesaleDao.updateRawMaterial(
                            mat.copy(
                                currentStock = (mat.currentStock - qLine.totalQty).coerceAtLeast(0.0),
                                updatedAt = now
                            )
                        )
                    }
                }

                wholesaleDao.updateWholesaleQuote(
                    quote.copy(
                        status = "CONVERTED",
                        linkedSaleId = newSaleId,
                        updatedAt = now
                    )
                )
                newSaleId
            }
            Result.success(saleId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
