package com.example.ui.viewmodel

import com.example.data.local.*
import com.example.data.repository.WholesaleRepository
import com.example.domain.BomQuotationEngine
import com.example.domain.CustomBomLineInput
import com.example.domain.QuoteLineOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WholesaleController(
    private val wholesaleRepo: WholesaleRepository,
    private val scope: CoroutineScope,
    private val onToast: (String) -> Unit
) {

    fun saveRawMaterial(material: RawMaterialEntity) {
        scope.launch {
            wholesaleRepo.saveRawMaterial(material)
            onToast("${material.name} rate saved")
        }
    }

    fun deleteRawMaterial(id: Long) {
        scope.launch {
            wholesaleRepo.deleteRawMaterial(id)
            onToast("Raw material removed")
        }
    }

    fun saveBomTemplate(template: BomTemplateEntity, rules: List<BomMaterialRuleEntity>) {
        scope.launch {
            wholesaleRepo.saveBomTemplateWithRules(template, rules)
            onToast("BOM template '${template.name}' saved")
        }
    }

    fun deleteBomTemplate(templateId: Long) {
        scope.launch {
            wholesaleRepo.deleteBomTemplate(templateId)
            onToast("BOM template deleted")
        }
    }

    fun loadMattressStarterBom() {
        scope.launch {
            wholesaleRepo.seedMattressStarterBom()
            onToast("Loaded starter raw materials & mattress BOM formulas")
        }
    }

    fun saveQuote(
        existingId: Long = 0L,
        existingQuoteNumber: String = "",
        buyerName: String,
        buyerPhone: String,
        buyerCompany: String,
        status: String,
        template: BomTemplateEntity?,
        rules: List<BomMaterialRuleEntity>,
        rawMaterialsById: Map<Long, RawMaterialEntity>,
        title: String,
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
        validUntil: String,
        notes: String,
        overridesByIndex: Map<Int, QuoteLineOverride>,
        extraCustomLines: List<CustomBomLineInput>,
        onSaved: (Long) -> Unit = {}
    ) {
        scope.launch {
            val calc = BomQuotationEngine.calculateQuote(
                template = template,
                rules = rules,
                rawMaterialsById = rawMaterialsById,
                lengthIn = lengthIn,
                widthIn = widthIn,
                thicknessIn = thicknessIn,
                densityFactor = densityFactor,
                quantity = quantity,
                stitchChargePerPc = stitchChargePerPc,
                transportCost = transportCost,
                marginPercent = marginPercent,
                discountPercent = discountPercent,
                gstPercent = gstPercent,
                overridesByIndex = overridesByIndex,
                extraCustomLines = extraCustomLines
            )
            val id = wholesaleRepo.saveWholesaleQuote(
                existingId = existingId,
                existingQuoteNumber = existingQuoteNumber,
                buyerName = buyerName,
                buyerPhone = buyerPhone,
                buyerCompany = buyerCompany,
                status = status,
                templateId = template?.id,
                title = title,
                lengthIn = lengthIn,
                widthIn = widthIn,
                thicknessIn = thicknessIn,
                densityFactor = densityFactor,
                quantity = quantity,
                stitchChargePerPc = stitchChargePerPc,
                transportCost = transportCost,
                discountPercent = discountPercent,
                gstPercent = gstPercent,
                marginPercent = marginPercent,
                validUntil = validUntil,
                notes = notes,
                calc = calc
            )
            onToast("Quotation saved (${quantity} pcs)")
            onSaved(id)
        }
    }

    fun updateQuoteStatus(quoteId: Long, status: String) {
        scope.launch {
            wholesaleRepo.updateQuoteStatus(quoteId, status)
            onToast("Quote marked $status")
        }
    }

    fun deleteQuote(quoteId: Long) {
        scope.launch {
            wholesaleRepo.deleteQuote(quoteId)
            onToast("Quote deleted")
        }
    }

    fun generateVendorPoFromQuote(
        quoteId: Long,
        vendorId: Long? = null,
        supplierName: String = "",
        onDone: (Long?) -> Unit = {}
    ) {
        scope.launch {
            val res = wholesaleRepo.createPurchaseOrderFromQuote(quoteId, vendorId, supplierName)
            res.onSuccess { poId ->
                onToast("Raw-material Purchase Request created!")
                onDone(poId)
            }.onFailure { err ->
                onToast(err.message ?: "Could not create Purchase Request")
                onDone(null)
            }
        }
    }

    fun convertQuoteToWholesaleSale(
        quoteId: Long,
        paymentMode: String = "CASH",
        onDone: (Long?) -> Unit = {}
    ) {
        scope.launch {
            val res = wholesaleRepo.convertQuoteToWholesaleSale(quoteId, paymentMode)
            res.onSuccess { saleId ->
                onToast("Converted to connected Wholesale Bill ($paymentMode)")
                onDone(saleId)
            }.onFailure { err ->
                onToast(err.message ?: "Could not convert quote to sale")
                onDone(null)
            }
        }
    }
}
