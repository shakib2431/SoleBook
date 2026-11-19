package com.example.domain

import com.example.data.local.*
import com.example.util.Formatters
import kotlin.math.max
import kotlin.math.round

data class ComputedQuoteLine(
    val rawMaterialId: Long? = null,
    val itemName: String,
    val category: String,
    val unit: String,
    val qtyPerPiece: Double,
    val totalQty: Double,
    val unitRate: Double,
    val wastagePct: Double,
    val lineTotal: Double,
    val isOverridden: Boolean = false
)

data class QuoteLineOverride(
    val itemName: String? = null,
    val qtyPerPiece: Double? = null,
    val unitRate: Double? = null,
    val wastagePct: Double? = null
)

data class CustomBomLineInput(
    val rawMaterialId: Long? = null,
    val itemName: String,
    val category: String,
    val unit: String,
    val qtyPerPiece: Double,
    val unitRate: Double,
    val wastagePct: Double = 5.0
)

data class QuotationCalculationResult(
    val lines: List<ComputedQuoteLine>,
    val materialCostPerPiece: Double,
    val stitchChargePerPiece: Double,
    val unitManufacturingCost: Double,
    val grossUnitPriceBeforeDiscount: Double,
    val unitQuotedPriceAfterDiscount: Double,
    val totalMaterialCost: Double,
    val totalStitchCost: Double,
    val transportCost: Double,
    val totalManufacturingCost: Double,
    val subtotalBeforeDiscount: Double,
    val bulkDiscountAmount: Double,
    val totalQuoteBeforeGst: Double,
    val gstAmount: Double,
    val grandTotal: Double,
    val estimatedNetProfit: Double,
    val effectiveMarginPct: Double,
    val specSummary: String
)

object BomQuotationEngine {

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
    private fun round3(v: Double): Double = round(v * 1000.0) / 1000.0

    /**
     * Parametric calculation engine for custom BOM & Wholesale quotations.
     * Supports:
     * 1. VOLUME_WEIGHT: scales refQtyPerPiece proportionally by (L*W*T)/(refL*refW*refT) * densityFactor, plus wastage%.
     * 2. SURFACE_FABRIC_METERS: computes top+bottom+4-side-border cloth area in sq.in with seam allowance,
     *    converts to linear meters along rollWidthInches (e.g. 60"), plus wastage%.
     * 3. PERIMETER_TRIM_METERS: 2*(L+W) * 2 edges (top/bottom piping) in meters + wastage%.
     * 4. FIXED_PER_PIECE: fixed quantity per piece (polybag, label, zipper, tag) + wastage%.
     */
    fun calculateQuote(
        template: BomTemplateEntity?,
        rules: List<BomMaterialRuleEntity>,
        rawMaterialsById: Map<Long, RawMaterialEntity>,
        lengthIn: Double,
        widthIn: Double,
        thicknessIn: Double,
        densityFactor: Double,
        quantity: Int,
        stitchChargePerPc: Double,
        transportCost: Double,
        marginPercent: Double,
        discountPercent: Double,
        gstPercent: Double,
        overridesByIndex: Map<Int, QuoteLineOverride> = emptyMap(),
        extraCustomLines: List<CustomBomLineInput> = emptyList()
    ): QuotationCalculationResult {
        val safeQty = quantity.coerceAtLeast(1)
        val safeL = lengthIn.coerceAtLeast(1.0)
        val safeW = widthIn.coerceAtLeast(1.0)
        val safeT = thicknessIn.coerceAtLeast(0.5)
        val safeDensity = densityFactor.coerceAtLeast(0.1)

        val refL = (template?.refLengthIn ?: 72.0).coerceAtLeast(1.0)
        val refW = (template?.refWidthIn ?: 36.0).coerceAtLeast(1.0)
        val refT = (template?.refThicknessIn ?: 4.0).coerceAtLeast(0.5)
        val refVol = refL * refW * refT
        val targetVol = safeL * safeW * safeT
        val volRatio = targetVol / refVol

        val computedLines = mutableListOf<ComputedQuoteLine>()

        rules.forEachIndexed { idx, rule ->
            val linkedMat = rule.rawMaterialId?.let { rawMaterialsById[it] }
            val override = overridesByIndex[idx]
            val wastage = (override?.wastagePct ?: rule.wastagePct).coerceIn(0.0, 95.0)
            val wastageMult = 1.0 + (wastage / 100.0)

            val baseQtyPerPc = when (rule.ruleType) {
                "VOLUME_WEIGHT" -> {
                    val densityMult = safeDensity / rule.densityFactor.coerceAtLeast(0.1)
                    rule.refQtyPerPiece * volRatio * densityMult * wastageMult
                }
                "SURFACE_FABRIC_METERS" -> {
                    val rollW = (linkedMat?.rollWidthInches?.takeIf { it > 1.0 }
                        ?: rule.rollWidthInches.takeIf { it > 1.0 }
                        ?: 60.0)
                    val seam = rule.seamAllowanceIn.coerceAtLeast(0.0)
                    // Top + bottom panels (with seam allowance) + 4 side borders
                    val topBottomArea = 2.0 * (safeL + seam) * (safeW + seam)
                    val borderArea = 2.0 * (safeL + safeW) * (safeT + seam)
                    val totalSqIn = topBottomArea + borderArea
                    val linearInches = totalSqIn / rollW
                    val meters = linearInches / 39.3701
                    meters * wastageMult
                }
                "PERIMETER_TRIM_METERS" -> {
                    val perimeterInches = 2.0 * (safeL + safeW) * rule.refQtyPerPiece.coerceAtLeast(1.0)
                    val meters = perimeterInches / 39.3701
                    meters * wastageMult
                }
                else -> {
                    // FIXED_PER_PIECE
                    rule.refQtyPerPiece * wastageMult
                }
            }

            val finalQtyPerPc = round3((override?.qtyPerPiece ?: baseQtyPerPc).coerceAtLeast(0.0))
            val rate = (override?.unitRate ?: rule.unitRateOverride ?: linkedMat?.unitCost ?: 0.0).coerceAtLeast(0.0)
            val totalQty = round3(finalQtyPerPc * safeQty)
            val lineTotal = round2(totalQty * rate)

            computedLines += ComputedQuoteLine(
                rawMaterialId = rule.rawMaterialId,
                itemName = override?.itemName?.takeIf { it.isNotBlank() } ?: linkedMat?.name ?: rule.materialNameSnapshot,
                category = linkedMat?.category ?: rule.category,
                unit = linkedMat?.unit ?: rule.unit,
                qtyPerPiece = finalQtyPerPc,
                totalQty = totalQty,
                unitRate = round2(rate),
                wastagePct = wastage,
                lineTotal = lineTotal,
                isOverridden = override != null
            )
        }

        extraCustomLines.forEach { custom ->
            if (custom.itemName.isBlank()) return@forEach
            val wastage = custom.wastagePct.coerceIn(0.0, 95.0)
            val qtyPerPc = round3(custom.qtyPerPiece.coerceAtLeast(0.0) * (1.0 + wastage / 100.0))
            val totalQty = round3(qtyPerPc * safeQty)
            val rate = round2(custom.unitRate.coerceAtLeast(0.0))
            computedLines += ComputedQuoteLine(
                rawMaterialId = custom.rawMaterialId,
                itemName = custom.itemName.trim(),
                category = custom.category,
                unit = custom.unit.ifBlank { "pcs" },
                qtyPerPiece = qtyPerPc,
                totalQty = totalQty,
                unitRate = rate,
                wastagePct = wastage,
                lineTotal = round2(totalQty * rate),
                isOverridden = true
            )
        }

        val totalMaterialCost = round2(computedLines.sumOf { it.lineTotal })
        val materialCostPerPiece = round2(totalMaterialCost / safeQty.toDouble())
        val safeStitch = round2(stitchChargePerPc.coerceAtLeast(0.0))
        val safeTransport = round2(transportCost.coerceAtLeast(0.0))
        val totalStitchCost = round2(safeStitch * safeQty)
        val totalManufacturingCost = round2(totalMaterialCost + totalStitchCost + safeTransport)
        val unitManufacturingCost = round2(totalManufacturingCost / safeQty.toDouble())

        val safeMargin = marginPercent.coerceIn(0.0, 500.0)
        val grossUnitPrice = round2(unitManufacturingCost * (1.0 + safeMargin / 100.0))
        val subtotalBeforeDiscount = round2(grossUnitPrice * safeQty)

        val safeDiscount = discountPercent.coerceIn(0.0, 90.0)
        val bulkDiscountAmount = round2(subtotalBeforeDiscount * (safeDiscount / 100.0))
        val totalQuoteBeforeGst = round2(max(0.0, subtotalBeforeDiscount - bulkDiscountAmount))
        val unitQuotedAfterDiscount = round2(totalQuoteBeforeGst / safeQty.toDouble())

        val safeGst = gstPercent.coerceIn(0.0, 40.0)
        val gstAmount = round2(totalQuoteBeforeGst * (safeGst / 100.0))
        val grandTotal = round2(totalQuoteBeforeGst + gstAmount)

        val estimatedNetProfit = round2(totalQuoteBeforeGst - totalManufacturingCost)
        val effectiveMarginPct = if (totalQuoteBeforeGst > 0.01) {
            round2((estimatedNetProfit / totalQuoteBeforeGst) * 100.0)
        } else 0.0

        val specSummary = buildString {
            append("${safeL.toInt()}\"×${safeW.toInt()}\"×${if (safeT % 1.0 == 0.0) safeT.toInt().toString() else safeT.toString()}\"")
            append(" • ${safeQty} pcs")
            if (safeDensity != 1.0) append(" • ${safeDensity}x density")
        }

        return QuotationCalculationResult(
            lines = computedLines,
            materialCostPerPiece = materialCostPerPiece,
            stitchChargePerPiece = safeStitch,
            unitManufacturingCost = unitManufacturingCost,
            grossUnitPriceBeforeDiscount = grossUnitPrice,
            unitQuotedPriceAfterDiscount = unitQuotedAfterDiscount,
            totalMaterialCost = totalMaterialCost,
            totalStitchCost = totalStitchCost,
            transportCost = safeTransport,
            totalManufacturingCost = totalManufacturingCost,
            subtotalBeforeDiscount = subtotalBeforeDiscount,
            bulkDiscountAmount = bulkDiscountAmount,
            totalQuoteBeforeGst = totalQuoteBeforeGst,
            gstAmount = gstAmount,
            grandTotal = grandTotal,
            estimatedNetProfit = estimatedNetProfit,
            effectiveMarginPct = effectiveMarginPct,
            specSummary = specSummary
        )
    }

    fun formatQuoteWhatsAppMessage(
        shopName: String,
        quote: WholesaleQuoteEntity,
        lines: List<WholesaleQuoteLineEntity>,
        currency: String,
        includeInternalCostBreakdown: Boolean = false
    ): String {
        return buildString {
            appendLine("*$shopName — Wholesale Quotation*")
            appendLine("Quote #: *${quote.quoteNumber}* (${quote.status})")
            if (quote.buyerName.isNotBlank()) {
                val companyPart = if (quote.buyerCompany.isNotBlank()) " (${quote.buyerCompany})" else ""
                appendLine("For: ${quote.buyerName}$companyPart")
            }
            appendLine()
            appendLine("*Item:* ${quote.title}")
            appendLine("*Spec:* ${quote.specSummary}")
            appendLine("*Quantity:* ${quote.quantity} pcs")
            appendLine()
            appendLine("*Unit Rate:* ${Formatters.formatCurrency(quote.unitQuotedPrice, currency)} / pc")
            if (quote.discountPercent > 0) {
                appendLine("Bulk Discount Included: ${quote.discountPercent}%")
            }
            if (quote.gstPercent > 0) {
                appendLine("Subtotal (Excl. GST): ${Formatters.formatCurrency(quote.totalQuoteAmount, currency)}")
                appendLine("GST (${quote.gstPercent}%): ${Formatters.formatCurrency(quote.gstAmount, currency)}")
            }
            appendLine("*Grand Total:* *${Formatters.formatCurrency(quote.grandTotal, currency)}*")
            if (quote.validUntil.isNotBlank()) {
                appendLine("Valid Until: ${quote.validUntil}")
            }
            if (includeInternalCostBreakdown && lines.isNotEmpty()) {
                appendLine()
                appendLine("_Material Requirement Summary:_")
                lines.forEach { l ->
                    appendLine("• ${l.itemName}: ${round2(l.totalQty)} ${l.unit}")
                }
            }
            if (quote.notes.isNotBlank()) {
                appendLine()
                appendLine("Note: ${quote.notes}")
            }
        }.trim()
    }
}

object WholesaleStarterSeeder {
    /**
     * Explicit one-tap sample data for wholesale manufacturing / bedding / custom BOM testing.
     * Only inserted when user triggers "Load Mattress & Wholesale BOM Starter" or inside wholesale demo.
     */
    suspend fun seedMattressStarterIfEmpty(wholesaleDao: WholesaleDao) {
        if (wholesaleDao.getAllRawMaterialsSync().isNotEmpty() || wholesaleDao.getAllBomTemplatesSync().isNotEmpty()) {
            return
        }
        val now = System.currentTimeMillis()
        val cottonId = wholesaleDao.insertRawMaterial(
            RawMaterialEntity(
                name = "Carded Desi Cotton Filling",
                category = "FILLING",
                unit = "kg",
                unitCost = 140.0,
                trackStock = false,
                currentStock = 0.0,
                supplierName = "Ganga Cotton Traders",
                notes = "10 kg standard for 72×36×4 single mattress",
                updatedAt = now
            )
        )
        val clothId = wholesaleDao.insertRawMaterial(
            RawMaterialEntity(
                name = "Quilted Damask Outer Cloth (60\" roll)",
                category = "FABRIC",
                unit = "meter",
                unitCost = 115.0,
                rollWidthInches = 60.0,
                trackStock = false,
                currentStock = 0.0,
                supplierName = "Surat Textile Mills",
                notes = "60-inch roll width; computes top+bottom+4-side border with seam allowance",
                updatedAt = now
            )
        )
        val pipingId = wholesaleDao.insertRawMaterial(
            RawMaterialEntity(
                name = "Border Piping Tape & Heavy Thread",
                category = "TRIM",
                unit = "meter",
                unitCost = 12.0,
                trackStock = false,
                supplierName = "Surat Textile Mills",
                updatedAt = now
            )
        )
        val packId = wholesaleDao.insertRawMaterial(
            RawMaterialEntity(
                name = "HDPE Export Polybag & Tag",
                category = "PACKING",
                unit = "pcs",
                unitCost = 22.0,
                trackStock = true,
                currentStock = 500.0,
                reorderLevel = 100.0,
                supplierName = "Metro Pack Solutions",
                updatedAt = now
            )
        )
        val foamId = wholesaleDao.insertRawMaterial(
            RawMaterialEntity(
                name = "High-Density Bonded Foam Core",
                category = "FILLING",
                unit = "kg",
                unitCost = 195.0,
                trackStock = false,
                supplierName = "Kurlo Polymer Agency",
                updatedAt = now
            )
        )

        val cottonMattressTplId = wholesaleDao.insertBomTemplate(
            BomTemplateEntity(
                name = "Quilted Cotton Mattress",
                category = "Bedding",
                calcMode = "MATTRESS_VOLUME",
                refLengthIn = 72.0,
                refWidthIn = 36.0,
                refThicknessIn = 4.0,
                defaultDensity = 1.0,
                defaultMarginPct = 22.0,
                defaultStitchCost = 120.0,
                wastagePct = 5.0,
                notes = "Baseline 72\"×36\"×4\" single bed = 10 kg cotton. Scales automatically to King/Queen/Custom size.",
                isActive = true
            )
        )
        wholesaleDao.insertBomRules(
            listOf(
                BomMaterialRuleEntity(
                    templateId = cottonMattressTplId,
                    rawMaterialId = cottonId,
                    materialNameSnapshot = "Carded Desi Cotton Filling",
                    category = "FILLING",
                    unit = "kg",
                    ruleType = "VOLUME_WEIGHT",
                    refQtyPerPiece = 10.0,
                    densityFactor = 1.0,
                    wastagePct = 5.0
                ),
                BomMaterialRuleEntity(
                    templateId = cottonMattressTplId,
                    rawMaterialId = clothId,
                    materialNameSnapshot = "Quilted Damask Outer Cloth (60\" roll)",
                    category = "FABRIC",
                    unit = "meter",
                    ruleType = "SURFACE_FABRIC_METERS",
                    refQtyPerPiece = 1.0,
                    seamAllowanceIn = 2.0,
                    rollWidthInches = 60.0,
                    wastagePct = 6.0
                ),
                BomMaterialRuleEntity(
                    templateId = cottonMattressTplId,
                    rawMaterialId = pipingId,
                    materialNameSnapshot = "Border Piping Tape & Heavy Thread",
                    category = "TRIM",
                    unit = "meter",
                    ruleType = "PERIMETER_TRIM_METERS",
                    refQtyPerPiece = 2.0,
                    wastagePct = 4.0
                ),
                BomMaterialRuleEntity(
                    templateId = cottonMattressTplId,
                    rawMaterialId = packId,
                    materialNameSnapshot = "HDPE Export Polybag & Tag",
                    category = "PACKING",
                    unit = "pcs",
                    ruleType = "FIXED_PER_PIECE",
                    refQtyPerPiece = 1.0,
                    wastagePct = 2.0
                )
            )
        )

        val foamMattressTplId = wholesaleDao.insertBomTemplate(
            BomTemplateEntity(
                name = "Ortho Foam + Fabric Mattress",
                category = "Bedding",
                calcMode = "MATTRESS_VOLUME",
                refLengthIn = 72.0,
                refWidthIn = 60.0,
                refThicknessIn = 6.0,
                defaultDensity = 1.0,
                defaultMarginPct = 25.0,
                defaultStitchCost = 160.0,
                wastagePct = 4.0,
                notes = "Baseline 72\"×60\"×6\" Queen mattress = 18 kg bonded foam core.",
                isActive = true
            )
        )
        wholesaleDao.insertBomRules(
            listOf(
                BomMaterialRuleEntity(
                    templateId = foamMattressTplId,
                    rawMaterialId = foamId,
                    materialNameSnapshot = "High-Density Bonded Foam Core",
                    category = "FILLING",
                    unit = "kg",
                    ruleType = "VOLUME_WEIGHT",
                    refQtyPerPiece = 18.0,
                    densityFactor = 1.0,
                    wastagePct = 4.0
                ),
                BomMaterialRuleEntity(
                    templateId = foamMattressTplId,
                    rawMaterialId = clothId,
                    materialNameSnapshot = "Quilted Damask Outer Cloth (60\" roll)",
                    category = "FABRIC",
                    unit = "meter",
                    ruleType = "SURFACE_FABRIC_METERS",
                    refQtyPerPiece = 1.0,
                    seamAllowanceIn = 2.0,
                    rollWidthInches = 60.0,
                    wastagePct = 5.0
                ),
                BomMaterialRuleEntity(
                    templateId = foamMattressTplId,
                    rawMaterialId = packId,
                    materialNameSnapshot = "HDPE Export Polybag & Tag",
                    category = "PACKING",
                    unit = "pcs",
                    ruleType = "FIXED_PER_PIECE",
                    refQtyPerPiece = 1.0,
                    wastagePct = 2.0
                )
            )
        )
    }
}
