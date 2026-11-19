package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupPayload(
    val products: List<ProductEntity>,
    val variants: List<ProductVariantEntity>,
    val sales: List<SaleEntity>,
    val saleItems: List<SaleItemEntity>,
    val returns: List<ReturnEntity>,
    val purchases: List<PurchaseEntity>,
    val vendors: List<VendorEntity> = emptyList(),
    val vendorPayments: List<VendorPaymentEntity> = emptyList(),
    val expenses: List<ExpenseEntity>,
    val customers: List<CustomerEntity>,
    val segments: List<SegmentEntity>,
    val templates: List<MessageTemplateEntity>,
    val campaigns: List<CampaignLogEntity>,
    val settings: ShopSettingsEntity?,
    val productImages: List<ProductImageEntity> = emptyList(),
    val inventoryLocations: List<InventoryLocationEntity> = emptyList(),
    val inventoryMovements: List<InventoryMovementEntity> = emptyList(),
    val purchaseOrders: List<PurchaseOrderEntity> = emptyList(),
    val rawMaterials: List<RawMaterialEntity> = emptyList(),
    val bomTemplates: List<BomTemplateEntity> = emptyList(),
    val bomMaterialRules: List<BomMaterialRuleEntity> = emptyList(),
    val wholesaleQuotes: List<WholesaleQuoteEntity> = emptyList(),
    val wholesaleQuoteLines: List<WholesaleQuoteLineEntity> = emptyList()
)

object ExportUtils {

    fun shareText(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun openWhatsApp(context: Context, phone: String, message: String) {
        val cleaned = phone.filter { it.isDigit() }
        val normalized = if (cleaned.length == 10) "91$cleaned" else cleaned
        val encoded = URLEncoder.encode(message, "UTF-8")
        val uri = Uri.parse("https://wa.me/$normalized?text=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            shareText(context, "WhatsApp Message", message)
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }

    /**
     * Converts selected product photos (both local saved files and bundled drawables) into
     * shareable JPEG files inside cacheDir/exports for FileProvider WhatsApp sharing.
     */
    fun prepareShareableProductImages(context: Context, products: List<ProductEntity>): List<File> {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFiles = mutableListOf<File>()
        products.take(5).forEachIndexed { idx, p ->
            if (p.photoUri.isBlank()) return@forEachIndexed
            val target = File(dir, "campaign_${p.id}_${idx}.jpg")
            val drawableRes = ImageUtils.resolveDrawableRes(p.photoUri)
            if (drawableRes != null) {
                runCatching {
                    val bmp = BitmapFactory.decodeResource(context.resources, drawableRes)
                    if (bmp != null) {
                        FileOutputStream(target).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                        bmp.recycle()
                        outFiles += target
                    }
                }
            } else {
                val src = File(p.photoUri)
                if (src.exists()) {
                    runCatching {
                        src.copyTo(target, overwrite = true)
                        outFiles += target
                    }
                }
            }
        }
        return outFiles
    }

    /**
     * Shares campaign caption + 0..5 product photos via WhatsApp / Android Sharesheet.
     * Also copies the caption to the clipboard because WhatsApp occasionally omits EXTRA_TEXT
     * when attaching multiple photos at once.
     */
    fun shareCampaignWithPhotos(context: Context, imageFiles: List<File>, caption: String) {
        copyToClipboard(context, "WhatsApp Campaign", caption)
        if (imageFiles.isEmpty()) {
            shareText(context, "Share WhatsApp Campaign", caption)
            return
        }
        val uris = ArrayList<Uri>(
            imageFiles.map { file ->
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        )
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Send New Stock Alert on WhatsApp"))
    }

    fun dialPhone(context: Context, phone: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        try {
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    fun writeExportFile(context: Context, fileName: String, content: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    fun generateReorderPdf(
        context: Context,
        shopName: String,
        currency: String,
        lines: List<String>,
        totalBudget: Double,
        expectedProfit: Double
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "solebook_reorder_${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#1A1614")
            textSize = 20f
            isFakeBoldText = true
        }
        val subPaint = Paint().apply {
            color = Color.parseColor("#5B5149")
            textSize = 12f
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#1A1614")
            textSize = 12f
        }
        val accentPaint = Paint().apply {
            color = Color.parseColor("#C84B11")
            textSize = 13f
            isFakeBoldText = true
        }

        var y = 48f
        canvas.drawText("$shopName — Supplier Reorder List", 40f, y, titlePaint)
        y += 22f
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText("Generated on $dateStr via SoleBook", 40f, y, subPaint)
        y += 20f
        canvas.drawText(
            "Est. Order Cost: ${Formatters.formatCurrency(totalBudget, currency)}   |   Expected Profit: ${Formatters.formatCurrency(expectedProfit, currency)}",
            40f,
            y,
            accentPaint
        )
        y += 26f

        for (line in lines) {
            if (y > 790f) {
                doc.finishPage(page)
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }
            canvas.drawText(line, 40f, y, linePaint)
            y += 20f
        }

        doc.finishPage(page)
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
        return file
    }

    fun generateWholesaleQuotePdf(
        context: Context,
        shopName: String,
        currency: String,
        quote: WholesaleQuoteEntity,
        lines: List<WholesaleQuoteLineEntity>
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeNum = quote.quoteNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "solebook_quote_${safeNum}_${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#111827")
            textSize = 20f
            isFakeBoldText = true
        }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F766E")
            textSize = 13f
            isFakeBoldText = true
        }
        val subPaint = Paint().apply {
            color = Color.parseColor("#4B5563")
            textSize = 11f
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#111827")
            textSize = 11.5f
        }
        val boldPaint = Paint().apply {
            color = Color.parseColor("#111827")
            textSize = 13f
            isFakeBoldText = true
        }

        var y = 48f
        canvas.drawText("$shopName — WHOLESALE QUOTATION", 40f, y, titlePaint)
        y += 22f
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(quote.updatedAt))
        canvas.drawText("Quote #: ${quote.quoteNumber}   |   Date: $dateStr   |   Status: ${quote.status}", 40f, y, subPaint)
        y += 20f
        if (quote.buyerName.isNotBlank()) {
            val company = if (quote.buyerCompany.isNotBlank()) " (${quote.buyerCompany})" else ""
            val phone = if (quote.buyerPhone.isNotBlank()) "  |  ${quote.buyerPhone}" else ""
            canvas.drawText("Buyer: ${quote.buyerName}$company$phone", 40f, y, linePaint)
            y += 18f
        }
        canvas.drawText("Item: ${quote.title}", 40f, y, boldPaint)
        y += 18f
        canvas.drawText("Spec: ${quote.specSummary}   |   Qty: ${quote.quantity} pcs", 40f, y, subPaint)
        y += 26f

        canvas.drawText("Bill of Materials & Specification Breakdown", 40f, y, headerPaint)
        y += 18f
        lines.forEachIndexed { idx, l ->
            if (y > 740f) {
                doc.finishPage(page)
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }
            val perPcStr = if (l.qtyPerPiece % 1.0 == 0.0) l.qtyPerPiece.toInt().toString() else String.format(Locale.getDefault(), "%.2f", l.qtyPerPiece)
            val totStr = if (l.totalQty % 1.0 == 0.0) l.totalQty.toInt().toString() else String.format(Locale.getDefault(), "%.2f", l.totalQty)
            val row = "${idx + 1}. ${l.itemName} — $perPcStr ${l.unit}/pc × ${quote.quantity} = $totStr ${l.unit}"
            canvas.drawText(row, 40f, y, linePaint)
            y += 17f
        }

        y += 14f
        canvas.drawText("Quotation Summary", 40f, y, headerPaint)
        y += 18f
        canvas.drawText("Unit Quoted Price: ${Formatters.formatCurrency(quote.unitQuotedPrice, currency)} / pc", 40f, y, boldPaint)
        y += 18f
        if (quote.discountPercent > 0) {
            canvas.drawText("Bulk Discount Included: ${quote.discountPercent}%", 40f, y, linePaint)
            y += 16f
        }
        canvas.drawText("Subtotal (Excl. Tax): ${Formatters.formatCurrency(quote.totalQuoteAmount, currency)}", 40f, y, linePaint)
        y += 16f
        if (quote.gstPercent > 0) {
            canvas.drawText("GST (${quote.gstPercent}%): ${Formatters.formatCurrency(quote.gstAmount, currency)}", 40f, y, linePaint)
            y += 18f
        }
        canvas.drawText("GRAND TOTAL: ${Formatters.formatCurrency(quote.grandTotal, currency)}", 40f, y, titlePaint)
        if (quote.validUntil.isNotBlank()) {
            y += 20f
            canvas.drawText("Valid Until: ${quote.validUntil}", 40f, y, subPaint)
        }
        if (quote.notes.isNotBlank()) {
            y += 18f
            canvas.drawText("Notes: ${quote.notes}", 40f, y, subPaint)
        }

        doc.finishPage(page)
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
        return file
    }

    fun buildCustomersCsv(customers: List<CustomerEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("ID,Phone,Name,Email,Visits,TotalSpent,CreditOwed,FavCategory,FavSize,WhatsAppConsent,LastVisit")
        customers.forEach { c ->
            sb.appendLine(
                "${c.id},\"${c.phone}\",\"${c.name.replace("\"", "'")}\",\"${c.email.replace("\"", "'")}\",${c.visitCount},${c.totalSpent},${c.creditBalance},\"${c.favouriteCategory}\",\"${c.favouriteSize}\",${c.whatsappConsent},\"${Formatters.formatShortDate(c.lastVisitAt)}\""
            )
        }
        return sb.toString()
    }

    fun buildSalesCsv(sales: List<SaleEntity>, items: List<SaleItemEntity>): String {
        val itemsBySale = items.groupBy { it.saleId }
        val sb = StringBuilder()
        sb.appendLine("BillNo,DateTime,PaymentMode,Subtotal,Discount,Revenue,COGS,GrossProfit,CustomerPhone,Items")
        sales.forEach { s ->
            val sItems = itemsBySale[s.id].orEmpty()
            val cogs = sItems.sumOf { it.costPriceAtSale * it.quantity }
            val gp = s.totalRevenue - cogs
            val desc = sItems.joinToString("; ") { "${it.skuSnapshot} (${it.size}) x${it.quantity}" }
            sb.appendLine(
                "\"${s.billNumber}\",\"${Formatters.formatDateTime(s.timestamp)}\",${s.paymentMode},${s.subtotal},${s.discountAmount},${s.totalRevenue},$cogs,$gp,\"${s.customerPhoneSnapshot}\",\"$desc\""
            )
        }
        return sb.toString()
    }

    fun buildProductsCsv(products: List<ProductEntity>, variants: List<ProductVariantEntity>): String {
        val byProduct = variants.groupBy { it.productId }
        val sb = StringBuilder()
        sb.appendLine("SKU,Name,Category,Brand,Gender,CostPrice,SellingPrice,MRP,TotalStock,SizeBreakdown,Archived")
        products.forEach { p ->
            val vars = byProduct[p.id].orEmpty()
            val total = vars.sumOf { it.stock }
            val sizes = vars.joinToString(" ") { "${it.size}:${it.stock}" }
            sb.appendLine(
                "\"${p.skuCode}\",\"${p.name.replace("\"", "'")}\",\"${p.category}\",\"${p.brand}\",\"${p.gender}\",${p.costPrice},${p.sellingPrice},${p.mrp ?: ""},$total,\"$sizes\",${p.isArchived}"
            )
        }
        return sb.toString()
    }

    fun encodeFullBackupJson(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("products", JSONArray().apply {
            payload.products.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("skuCode", p.skuCode)
                    put("name", p.name)
                    put("category", p.category)
                    put("brand", p.brand)
                    put("gender", p.gender)
                    put("photoUri", p.photoUri)
                    put("costPrice", p.costPrice)
                    put("sellingPrice", p.sellingPrice)
                    if (p.mrp != null) put("mrp", p.mrp) else put("mrp", JSONObject.NULL)
                    put("supplier", p.supplier)
                    put("reorderLevel", p.reorderLevel)
                    put("colorTag", p.colorTag)
                    put("styleTag", p.styleTag)
                    put("rackLocation", p.rackLocation)
                    put("isRepeatable", p.isRepeatable)
                    if (p.vendorId != null) put("vendorId", p.vendorId) else put("vendorId", JSONObject.NULL)
                    put("lotCode", p.lotCode)
                    put("unitLabel", p.unitLabel)
                    put("batchOrSerial", p.batchOrSerial)
                    put("expiryOrWarranty", p.expiryOrWarranty)
                    put("isArchived", p.isArchived)
                    put("createdAt", p.createdAt)
                    put("updatedAt", p.updatedAt)
                    put("barcode", p.barcode)
                    put("trackBatchExpiry", p.trackBatchExpiry)
                    put("trackSerialNumbers", p.trackSerialNumbers)
                    put("secondaryUnitLabel", p.secondaryUnitLabel)
                    put("unitConversionFactor", p.unitConversionFactor)
                })
            }
        })

        root.put("variants", JSONArray().apply {
            payload.variants.forEach { v ->
                put(JSONObject().apply {
                    put("id", v.id)
                    put("productId", v.productId)
                    put("size", v.size)
                    put("stock", v.stock)
                    put("variantType", v.variantType)
                    put("variantSku", v.variantSku)
                    put("barcode", v.barcode)
                    put("colorName", v.colorName)
                    put("packSizeLabel", v.packSizeLabel)
                    put("modelName", v.modelName)
                    if (v.costOverride != null) put("costOverride", v.costOverride) else put("costOverride", JSONObject.NULL)
                    if (v.priceOverride != null) put("priceOverride", v.priceOverride) else put("priceOverride", JSONObject.NULL)
                    put("location", v.location)
                    put("batchNumber", v.batchNumber)
                    put("expiryDate", v.expiryDate)
                    put("serialNumbersCsv", v.serialNumbersCsv)
                })
            }
        })

        root.put("sales", JSONArray().apply {
            payload.sales.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("billNumber", s.billNumber)
                    put("timestamp", s.timestamp)
                    if (s.customerId != null) put("customerId", s.customerId) else put("customerId", JSONObject.NULL)
                    put("customerPhoneSnapshot", s.customerPhoneSnapshot)
                    put("customerNameSnapshot", s.customerNameSnapshot)
                    put("paymentMode", s.paymentMode)
                    put("discountAmount", s.discountAmount)
                    put("discountType", s.discountType)
                    put("discountInput", s.discountInput)
                    put("gstEnabled", s.gstEnabled)
                    put("gstRate", s.gstRate)
                    put("gstInclusive", s.gstInclusive)
                    put("gstAmount", s.gstAmount)
                    put("subtotal", s.subtotal)
                    put("totalRevenue", s.totalRevenue)
                    put("totalCollected", s.totalCollected)
                    put("creditSettled", s.creditSettled)
                    if (s.creditSettledAt != null) put("creditSettledAt", s.creditSettledAt) else put("creditSettledAt", JSONObject.NULL)
                    put("notes", s.notes)
                    put("saleChannel", s.saleChannel)
                })
            }
        })

        root.put("saleItems", JSONArray().apply {
            payload.saleItems.forEach { i ->
                put(JSONObject().apply {
                    put("id", i.id)
                    put("saleId", i.saleId)
                    put("productId", i.productId)
                    put("productNameSnapshot", i.productNameSnapshot)
                    put("skuSnapshot", i.skuSnapshot)
                    put("categorySnapshot", i.categorySnapshot)
                    put("size", i.size)
                    put("quantity", i.quantity)
                    put("costPriceAtSale", i.costPriceAtSale)
                    put("sellingPriceAtSale", i.sellingPriceAtSale)
                    put("lineTotal", i.lineTotal)
                })
            }
        })

        root.put("returns", JSONArray().apply {
            payload.returns.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("saleId", r.saleId)
                    if (r.saleItemId != null) put("saleItemId", r.saleItemId) else put("saleItemId", JSONObject.NULL)
                    put("productId", r.productId)
                    put("productNameSnapshot", r.productNameSnapshot)
                    put("size", r.size)
                    put("quantity", r.quantity)
                    put("refundAmount", r.refundAmount)
                    put("costReversed", r.costReversed)
                    put("type", r.type)
                    if (r.exchangeSize != null) put("exchangeSize", r.exchangeSize) else put("exchangeSize", JSONObject.NULL)
                    put("timestamp", r.timestamp)
                    put("reason", r.reason)
                })
            }
        })

        root.put("purchases", JSONArray().apply {
            payload.purchases.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("productId", p.productId)
                    put("totalUnits", p.totalUnits)
                    put("unitCostPrice", p.unitCostPrice)
                    put("totalCost", p.totalCost)
                    put("supplier", p.supplier)
                    if (p.vendorId != null) put("vendorId", p.vendorId) else put("vendorId", JSONObject.NULL)
                    put("lotType", p.lotType)
                    put("paymentStatus", p.paymentStatus)
                    put("paidAmount", p.paidAmount)
                    put("dueAmount", p.dueAmount)
                    put("sizeBreakdown", p.sizeBreakdown)
                    put("timestamp", p.timestamp)
                    put("notes", p.notes)
                })
            }
        })

        root.put("vendors", JSONArray().apply {
            payload.vendors.forEach { v ->
                put(JSONObject().apply {
                    put("id", v.id)
                    put("name", v.name)
                    put("phone", v.phone)
                    put("whatsapp", v.whatsapp)
                    put("cityMarket", v.cityMarket)
                    put("specialtiesCsv", v.specialtiesCsv)
                    put("creditBalance", v.creditBalance)
                    put("notes", v.notes)
                    if (v.lastPurchaseAt != null) put("lastPurchaseAt", v.lastPurchaseAt) else put("lastPurchaseAt", JSONObject.NULL)
                    put("createdAt", v.createdAt)
                })
            }
        })

        root.put("vendorPayments", JSONArray().apply {
            payload.vendorPayments.forEach { vp ->
                put(JSONObject().apply {
                    put("id", vp.id)
                    put("vendorId", vp.vendorId)
                    put("amount", vp.amount)
                    put("paymentMode", vp.paymentMode)
                    put("timestamp", vp.timestamp)
                    put("notes", vp.notes)
                })
            }
        })

        root.put("expenses", JSONArray().apply {
            payload.expenses.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("category", e.category)
                    put("amount", e.amount)
                    put("isRecurringMonthly", e.isRecurringMonthly)
                    put("timestamp", e.timestamp)
                    put("notes", e.notes)
                })
            }
        })

        root.put("customers", JSONArray().apply {
            payload.customers.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("phone", c.phone)
                    put("name", c.name)
                    put("email", c.email)
                    put("firstVisitAt", c.firstVisitAt)
                    put("lastVisitAt", c.lastVisitAt)
                    put("totalSpent", c.totalSpent)
                    put("visitCount", c.visitCount)
                    put("favouriteCategory", c.favouriteCategory)
                    put("favouriteSize", c.favouriteSize)
                    put("specialDate", c.specialDate)
                    put("notes", c.notes)
                    put("whatsappConsent", c.whatsappConsent)
                    put("creditBalance", c.creditBalance)
                })
            }
        })

        payload.settings?.let { st ->
            root.put("settings", JSONObject().apply {
                put("shopName", st.shopName)
                put("businessCategory", st.businessCategory)
                put("customBusinessType", st.customBusinessType)
                put("businessChannel", st.businessChannel)
                put("hasCompletedOnboarding", st.hasCompletedOnboarding)
                put("shopLogoUri", st.shopLogoUri)
                put("shortHeaderName", st.shortHeaderName)
                put("headerBrandMode", st.headerBrandMode)
                put("currencySymbol", st.currencySymbol)
                put("languageCode", st.languageCode)
                put("gstEnabled", st.gstEnabled)
                put("gstRate", st.gstRate)
                put("gstInclusive", st.gstInclusive)
                put("defaultLeadTimeDays", st.defaultLeadTimeDays)
                put("seasonalityMode", st.seasonalityMode)
                put("seasonalityUpliftPercent", st.seasonalityUpliftPercent)
                put("hasCompletedWalkthrough", st.hasCompletedWalkthrough)
                put("isDemoLoaded", st.isDemoLoaded)
                put("alertsEnabled", st.alertsEnabled)
                put("instantSaleAlertsEnabled", st.instantSaleAlertsEnabled)
                put("stockAlertsEnabled", st.stockAlertsEnabled)
                put("demandAlertsEnabled", st.demandAlertsEnabled)
                put("marginAlertsEnabled", st.marginAlertsEnabled)
                put("alertIntervalHours", st.alertIntervalHours)
                put("maxAlertsPerDay", st.maxAlertsPerDay)
                put("quietStartHour", st.quietStartHour)
                put("quietEndHour", st.quietEndHour)
                put("businessOperationMode", st.businessOperationMode)
                put("activeWorkspaceMode", st.activeWorkspaceMode)
            })
        }

        root.put("productImages", JSONArray().apply {
            payload.productImages.forEach { img ->
                put(JSONObject().apply {
                    put("id", img.id)
                    put("productId", img.productId)
                    put("variantKey", img.variantKey)
                    put("colorTag", img.colorTag)
                    put("localPath", img.localPath)
                    put("thumbPath", img.thumbPath)
                    put("sourceUrl", img.sourceUrl)
                    put("sha256Hash", img.sha256Hash)
                    put("isPrimary", img.isPrimary)
                    put("createdAt", img.createdAt)
                })
            }
        })

        root.put("inventoryLocations", JSONArray().apply {
            payload.inventoryLocations.forEach { loc ->
                put(JSONObject().apply {
                    put("id", loc.id)
                    put("code", loc.code)
                    put("name", loc.name)
                    put("locationType", loc.locationType)
                    put("isDefault", loc.isDefault)
                    put("createdAt", loc.createdAt)
                })
            }
        })

        root.put("inventoryMovements", JSONArray().apply {
            payload.inventoryMovements.forEach { mv ->
                put(JSONObject().apply {
                    put("id", mv.id)
                    put("movementKey", mv.movementKey)
                    put("productId", mv.productId)
                    if (mv.variantId != null) put("variantId", mv.variantId) else put("variantId", JSONObject.NULL)
                    put("sizeLabel", mv.sizeLabel)
                    put("movementType", mv.movementType)
                    put("qtyDelta", mv.qtyDelta)
                    put("beforeQty", mv.beforeQty)
                    put("afterQty", mv.afterQty)
                    put("unitCost", mv.unitCost)
                    put("location", mv.location)
                    put("batchNumber", mv.batchNumber)
                    put("serialNumbersCsv", mv.serialNumbersCsv)
                    put("sourceRoute", mv.sourceRoute)
                    if (mv.sourceBatchId != null) put("sourceBatchId", mv.sourceBatchId) else put("sourceBatchId", JSONObject.NULL)
                    put("sourceRefId", mv.sourceRefId)
                    put("timestamp", mv.timestamp)
                    put("notes", mv.notes)
                })
            }
        })

        root.put("purchaseOrders", JSONArray().apply {
            payload.purchaseOrders.forEach { po ->
                put(JSONObject().apply {
                    put("id", po.id)
                    put("poNumber", po.poNumber)
                    if (po.vendorId != null) put("vendorId", po.vendorId) else put("vendorId", JSONObject.NULL)
                    put("supplierName", po.supplierName)
                    put("status", po.status)
                    put("itemsJson", po.itemsJson)
                    put("estimatedTotal", po.estimatedTotal)
                    if (po.sourceBatchId != null) put("sourceBatchId", po.sourceBatchId) else put("sourceBatchId", JSONObject.NULL)
                    put("sourceDocumentRef", po.sourceDocumentRef)
                    put("notes", po.notes)
                    put("createdAt", po.createdAt)
                    if (po.receivedAt != null) put("receivedAt", po.receivedAt) else put("receivedAt", JSONObject.NULL)
                })
            }
        })

        root.put("rawMaterials", JSONArray().apply {
            payload.rawMaterials.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("name", m.name)
                    put("category", m.category)
                    put("unit", m.unit)
                    put("unitCost", m.unitCost)
                    put("rollWidthInches", m.rollWidthInches)
                    put("trackStock", m.trackStock)
                    put("currentStock", m.currentStock)
                    put("reorderLevel", m.reorderLevel)
                    put("supplierName", m.supplierName)
                    put("notes", m.notes)
                    put("updatedAt", m.updatedAt)
                })
            }
        })

        root.put("bomTemplates", JSONArray().apply {
            payload.bomTemplates.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("category", t.category)
                    put("calcMode", t.calcMode)
                    put("refLengthIn", t.refLengthIn)
                    put("refWidthIn", t.refWidthIn)
                    put("refThicknessIn", t.refThicknessIn)
                    put("defaultDensity", t.defaultDensity)
                    put("defaultMarginPct", t.defaultMarginPct)
                    put("defaultStitchCost", t.defaultStitchCost)
                    put("wastagePct", t.wastagePct)
                    put("notes", t.notes)
                    put("isActive", t.isActive)
                })
            }
        })

        root.put("bomMaterialRules", JSONArray().apply {
            payload.bomMaterialRules.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("templateId", r.templateId)
                    if (r.rawMaterialId != null) put("rawMaterialId", r.rawMaterialId) else put("rawMaterialId", JSONObject.NULL)
                    put("materialNameSnapshot", r.materialNameSnapshot)
                    put("category", r.category)
                    put("unit", r.unit)
                    put("ruleType", r.ruleType)
                    put("refQtyPerPiece", r.refQtyPerPiece)
                    put("densityFactor", r.densityFactor)
                    put("seamAllowanceIn", r.seamAllowanceIn)
                    put("rollWidthInches", r.rollWidthInches)
                    put("wastagePct", r.wastagePct)
                    if (r.unitRateOverride != null) put("unitRateOverride", r.unitRateOverride) else put("unitRateOverride", JSONObject.NULL)
                })
            }
        })

        root.put("wholesaleQuotes", JSONArray().apply {
            payload.wholesaleQuotes.forEach { q ->
                put(JSONObject().apply {
                    put("id", q.id)
                    put("quoteNumber", q.quoteNumber)
                    put("buyerName", q.buyerName)
                    put("buyerPhone", q.buyerPhone)
                    put("buyerCompany", q.buyerCompany)
                    put("status", q.status)
                    if (q.templateId != null) put("templateId", q.templateId) else put("templateId", JSONObject.NULL)
                    put("title", q.title)
                    put("specSummary", q.specSummary)
                    put("lengthIn", q.lengthIn)
                    put("widthIn", q.widthIn)
                    put("thicknessIn", q.thicknessIn)
                    put("densityFactor", q.densityFactor)
                    put("quantity", q.quantity)
                    put("stitchChargePerPc", q.stitchChargePerPc)
                    put("transportCost", q.transportCost)
                    put("discountPercent", q.discountPercent)
                    put("gstPercent", q.gstPercent)
                    put("marginPercent", q.marginPercent)
                    put("unitManufacturingCost", q.unitManufacturingCost)
                    put("unitQuotedPrice", q.unitQuotedPrice)
                    put("totalCost", q.totalCost)
                    put("totalQuoteAmount", q.totalQuoteAmount)
                    put("gstAmount", q.gstAmount)
                    put("grandTotal", q.grandTotal)
                    put("validUntil", q.validUntil)
                    if (q.linkedPoId != null) put("linkedPoId", q.linkedPoId) else put("linkedPoId", JSONObject.NULL)
                    if (q.linkedSaleId != null) put("linkedSaleId", q.linkedSaleId) else put("linkedSaleId", JSONObject.NULL)
                    put("notes", q.notes)
                    put("createdAt", q.createdAt)
                    put("updatedAt", q.updatedAt)
                })
            }
        })

        root.put("wholesaleQuoteLines", JSONArray().apply {
            payload.wholesaleQuoteLines.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id)
                    put("quoteId", l.quoteId)
                    if (l.rawMaterialId != null) put("rawMaterialId", l.rawMaterialId) else put("rawMaterialId", JSONObject.NULL)
                    put("itemName", l.itemName)
                    put("category", l.category)
                    put("unit", l.unit)
                    put("qtyPerPiece", l.qtyPerPiece)
                    put("totalQty", l.totalQty)
                    put("unitRate", l.unitRate)
                    put("wastagePct", l.wastagePct)
                    put("lineTotal", l.lineTotal)
                    put("isOverridden", l.isOverridden)
                })
            }
        })

        return root.toString(2)
    }

    fun decodeFullBackupJson(jsonString: String): BackupPayload {
        val root = JSONObject(jsonString)

        val products = mutableListOf<ProductEntity>()
        root.optJSONArray("products")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                products += ProductEntity(
                    id = o.getLong("id"),
                    skuCode = o.getString("skuCode"),
                    name = o.getString("name"),
                    category = o.getString("category"),
                    brand = o.optString("brand", ""),
                    gender = o.optString("gender", "Unisex"),
                    photoUri = o.optString("photoUri", ""),
                    costPrice = o.getDouble("costPrice"),
                    sellingPrice = o.getDouble("sellingPrice"),
                    mrp = if (o.isNull("mrp")) null else o.getDouble("mrp"),
                    supplier = o.optString("supplier", ""),
                    reorderLevel = o.optInt("reorderLevel", 5),
                    colorTag = o.optString("colorTag", ""),
                    styleTag = o.optString("styleTag", ""),
                    rackLocation = o.optString("rackLocation", ""),
                    isRepeatable = o.optBoolean("isRepeatable", false),
                    vendorId = if (o.isNull("vendorId")) null else o.optLong("vendorId").takeIf { it > 0L },
                    lotCode = o.optString("lotCode", ""),
                    unitLabel = o.optString("unitLabel", ""),
                    batchOrSerial = o.optString("batchOrSerial", ""),
                    expiryOrWarranty = o.optString("expiryOrWarranty", ""),
                    isArchived = o.optBoolean("isArchived", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    barcode = o.optString("barcode", ""),
                    trackBatchExpiry = o.optBoolean("trackBatchExpiry", false),
                    trackSerialNumbers = o.optBoolean("trackSerialNumbers", false),
                    secondaryUnitLabel = o.optString("secondaryUnitLabel", ""),
                    unitConversionFactor = o.optDouble("unitConversionFactor", 1.0)
                )
            }
        }

        val variants = mutableListOf<ProductVariantEntity>()
        root.optJSONArray("variants")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                variants += ProductVariantEntity(
                    id = o.getLong("id"),
                    productId = o.getLong("productId"),
                    size = o.getString("size"),
                    stock = o.getInt("stock"),
                    variantType = o.optString("variantType", "Variant"),
                    variantSku = o.optString("variantSku", ""),
                    barcode = o.optString("barcode", ""),
                    colorName = o.optString("colorName", ""),
                    packSizeLabel = o.optString("packSizeLabel", ""),
                    modelName = o.optString("modelName", ""),
                    costOverride = if (o.isNull("costOverride")) null else o.optDouble("costOverride"),
                    priceOverride = if (o.isNull("priceOverride")) null else o.optDouble("priceOverride"),
                    location = o.optString("location", ""),
                    batchNumber = o.optString("batchNumber", ""),
                    expiryDate = o.optString("expiryDate", ""),
                    serialNumbersCsv = o.optString("serialNumbersCsv", "")
                )
            }
        }

        val sales = mutableListOf<SaleEntity>()
        root.optJSONArray("sales")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                sales += SaleEntity(
                    id = o.getLong("id"),
                    billNumber = o.getString("billNumber"),
                    timestamp = o.getLong("timestamp"),
                    customerId = if (o.isNull("customerId")) null else o.getLong("customerId"),
                    customerPhoneSnapshot = o.optString("customerPhoneSnapshot", ""),
                    customerNameSnapshot = o.optString("customerNameSnapshot", ""),
                    paymentMode = o.getString("paymentMode"),
                    discountAmount = o.optDouble("discountAmount", 0.0),
                    discountType = o.optString("discountType", "AMOUNT"),
                    discountInput = o.optDouble("discountInput", 0.0),
                    gstEnabled = o.optBoolean("gstEnabled", false),
                    gstRate = o.optDouble("gstRate", 12.0),
                    gstInclusive = o.optBoolean("gstInclusive", true),
                    gstAmount = o.optDouble("gstAmount", 0.0),
                    subtotal = o.getDouble("subtotal"),
                    totalRevenue = o.getDouble("totalRevenue"),
                    totalCollected = o.optDouble("totalCollected", o.getDouble("totalRevenue")),
                    creditSettled = o.optBoolean("creditSettled", true),
                    creditSettledAt = if (o.isNull("creditSettledAt")) null else o.getLong("creditSettledAt"),
                    notes = o.optString("notes", ""),
                    saleChannel = o.optString("saleChannel", "RETAIL")
                )
            }
        }

        val saleItems = mutableListOf<SaleItemEntity>()
        root.optJSONArray("saleItems")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                saleItems += SaleItemEntity(
                    id = o.getLong("id"),
                    saleId = o.getLong("saleId"),
                    productId = o.getLong("productId"),
                    productNameSnapshot = o.getString("productNameSnapshot"),
                    skuSnapshot = o.getString("skuSnapshot"),
                    categorySnapshot = o.getString("categorySnapshot"),
                    size = o.getString("size"),
                    quantity = o.getInt("quantity"),
                    costPriceAtSale = o.getDouble("costPriceAtSale"),
                    sellingPriceAtSale = o.getDouble("sellingPriceAtSale"),
                    lineTotal = o.getDouble("lineTotal")
                )
            }
        }

        val returns = mutableListOf<ReturnEntity>()
        root.optJSONArray("returns")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                returns += ReturnEntity(
                    id = o.getLong("id"),
                    saleId = o.getLong("saleId"),
                    saleItemId = if (o.isNull("saleItemId")) null else o.getLong("saleItemId"),
                    productId = o.getLong("productId"),
                    productNameSnapshot = o.getString("productNameSnapshot"),
                    size = o.getString("size"),
                    quantity = o.getInt("quantity"),
                    refundAmount = o.getDouble("refundAmount"),
                    costReversed = o.getDouble("costReversed"),
                    type = o.getString("type"),
                    exchangeSize = if (o.isNull("exchangeSize")) null else o.getString("exchangeSize"),
                    timestamp = o.getLong("timestamp"),
                    reason = o.optString("reason", "")
                )
            }
        }

        val purchases = mutableListOf<PurchaseEntity>()
        root.optJSONArray("purchases")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                purchases += PurchaseEntity(
                    id = o.getLong("id"),
                    productId = o.getLong("productId"),
                    totalUnits = o.getInt("totalUnits"),
                    unitCostPrice = o.getDouble("unitCostPrice"),
                    totalCost = o.getDouble("totalCost"),
                    supplier = o.optString("supplier", ""),
                    vendorId = if (o.isNull("vendorId")) null else o.optLong("vendorId").takeIf { it > 0L },
                    lotType = o.optString("lotType", "FRESH_LOT"),
                    paymentStatus = o.optString("paymentStatus", "PAID"),
                    paidAmount = o.optDouble("paidAmount", o.optDouble("totalCost", 0.0)),
                    dueAmount = o.optDouble("dueAmount", 0.0),
                    sizeBreakdown = o.optString("sizeBreakdown", ""),
                    timestamp = o.getLong("timestamp"),
                    notes = o.optString("notes", "")
                )
            }
        }

        val vendors = mutableListOf<VendorEntity>()
        root.optJSONArray("vendors")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                vendors += VendorEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    phone = o.optString("phone", ""),
                    whatsapp = o.optString("whatsapp", ""),
                    cityMarket = o.optString("cityMarket", ""),
                    specialtiesCsv = o.optString("specialtiesCsv", ""),
                    creditBalance = o.optDouble("creditBalance", 0.0),
                    notes = o.optString("notes", ""),
                    lastPurchaseAt = if (o.isNull("lastPurchaseAt")) null else o.optLong("lastPurchaseAt"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            }
        }

        val vendorPayments = mutableListOf<VendorPaymentEntity>()
        root.optJSONArray("vendorPayments")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                vendorPayments += VendorPaymentEntity(
                    id = o.getLong("id"),
                    vendorId = o.getLong("vendorId"),
                    amount = o.getDouble("amount"),
                    paymentMode = o.optString("paymentMode", "CASH"),
                    timestamp = o.getLong("timestamp"),
                    notes = o.optString("notes", "")
                )
            }
        }

        val expenses = mutableListOf<ExpenseEntity>()
        root.optJSONArray("expenses")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                expenses += ExpenseEntity(
                    id = o.getLong("id"),
                    category = o.getString("category"),
                    amount = o.getDouble("amount"),
                    isRecurringMonthly = o.optBoolean("isRecurringMonthly", false),
                    timestamp = o.getLong("timestamp"),
                    notes = o.optString("notes", "")
                )
            }
        }

        val customers = mutableListOf<CustomerEntity>()
        root.optJSONArray("customers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                customers += CustomerEntity(
                    id = o.getLong("id"),
                    phone = o.getString("phone"),
                    name = o.optString("name", ""),
                    email = o.optString("email", ""),
                    firstVisitAt = o.getLong("firstVisitAt"),
                    lastVisitAt = o.getLong("lastVisitAt"),
                    totalSpent = o.getDouble("totalSpent"),
                    visitCount = o.getInt("visitCount"),
                    favouriteCategory = o.optString("favouriteCategory", ""),
                    favouriteSize = o.optString("favouriteSize", ""),
                    specialDate = o.optString("specialDate", ""),
                    notes = o.optString("notes", ""),
                    whatsappConsent = o.optBoolean("whatsappConsent", true),
                    creditBalance = o.optDouble("creditBalance", 0.0)
                )
            }
        }

        val settings = root.optJSONObject("settings")?.let { s ->
            ShopSettingsEntity(
                id = 1,
                shopName = s.optString("shopName", "My Shop"),
                businessCategory = s.optString("businessCategory", "FOOTWEAR"),
                customBusinessType = s.optString("customBusinessType", ""),
                businessChannel = s.optString("businessChannel", "PHYSICAL"),
                hasCompletedOnboarding = s.optBoolean("hasCompletedOnboarding", true),
                shopLogoUri = s.optString("shopLogoUri", ""),
                shortHeaderName = s.optString("shortHeaderName", ""),
                headerBrandMode = s.optString("headerBrandMode", "LOGO_AND_NAME"),
                currencySymbol = s.optString("currencySymbol", "₹"),
                languageCode = s.optString("languageCode", "en"),
                gstEnabled = s.optBoolean("gstEnabled", false),
                gstRate = s.optDouble("gstRate", 12.0),
                gstInclusive = s.optBoolean("gstInclusive", true),
                defaultLeadTimeDays = s.optInt("defaultLeadTimeDays", 3),
                seasonalityMode = s.optString("seasonalityMode", "NONE"),
                seasonalityUpliftPercent = s.optDouble("seasonalityUpliftPercent", 0.0),
                hasCompletedWalkthrough = s.optBoolean("hasCompletedWalkthrough", true),
                isDemoLoaded = s.optBoolean("isDemoLoaded", false),
                alertsEnabled = s.optBoolean("alertsEnabled", true),
                instantSaleAlertsEnabled = s.optBoolean("instantSaleAlertsEnabled", false),
                stockAlertsEnabled = s.optBoolean("stockAlertsEnabled", true),
                demandAlertsEnabled = s.optBoolean("demandAlertsEnabled", true),
                marginAlertsEnabled = s.optBoolean("marginAlertsEnabled", true),
                alertIntervalHours = s.optInt("alertIntervalHours", 4),
                maxAlertsPerDay = s.optInt("maxAlertsPerDay", 4),
                quietStartHour = s.optInt("quietStartHour", 21),
                quietEndHour = s.optInt("quietEndHour", 8),
                businessOperationMode = s.optString("businessOperationMode", "RETAIL"),
                activeWorkspaceMode = s.optString("activeWorkspaceMode", "RETAIL")
            )
        }

        val productImages = mutableListOf<ProductImageEntity>()
        root.optJSONArray("productImages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                productImages += ProductImageEntity(
                    id = o.getLong("id"),
                    productId = o.getLong("productId"),
                    variantKey = o.optString("variantKey", ""),
                    colorTag = o.optString("colorTag", ""),
                    localPath = o.getString("localPath"),
                    thumbPath = o.optString("thumbPath", ""),
                    sourceUrl = o.optString("sourceUrl", ""),
                    sha256Hash = o.optString("sha256Hash", ""),
                    isPrimary = o.optBoolean("isPrimary", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            }
        }

        val inventoryLocations = mutableListOf<InventoryLocationEntity>()
        root.optJSONArray("inventoryLocations")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                inventoryLocations += InventoryLocationEntity(
                    id = o.getLong("id"),
                    code = o.getString("code"),
                    name = o.getString("name"),
                    locationType = o.optString("locationType", "STORE"),
                    isDefault = o.optBoolean("isDefault", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            }
        }

        val inventoryMovements = mutableListOf<InventoryMovementEntity>()
        root.optJSONArray("inventoryMovements")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                inventoryMovements += InventoryMovementEntity(
                    id = o.getLong("id"),
                    movementKey = o.getString("movementKey"),
                    productId = o.getLong("productId"),
                    variantId = if (o.isNull("variantId")) null else o.optLong("variantId"),
                    sizeLabel = o.optString("sizeLabel", "Standard"),
                    movementType = o.getString("movementType"),
                    qtyDelta = o.getInt("qtyDelta"),
                    beforeQty = o.getInt("beforeQty"),
                    afterQty = o.getInt("afterQty"),
                    unitCost = o.optDouble("unitCost", 0.0),
                    location = o.optString("location", ""),
                    batchNumber = o.optString("batchNumber", ""),
                    serialNumbersCsv = o.optString("serialNumbersCsv", ""),
                    sourceRoute = o.optString("sourceRoute", "MANUAL"),
                    sourceBatchId = if (o.isNull("sourceBatchId")) null else o.optLong("sourceBatchId"),
                    sourceRefId = o.optString("sourceRefId", ""),
                    timestamp = o.getLong("timestamp"),
                    notes = o.optString("notes", "")
                )
            }
        }

        val purchaseOrders = mutableListOf<PurchaseOrderEntity>()
        root.optJSONArray("purchaseOrders")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                purchaseOrders += PurchaseOrderEntity(
                    id = o.getLong("id"),
                    poNumber = o.getString("poNumber"),
                    vendorId = if (o.isNull("vendorId")) null else o.optLong("vendorId"),
                    supplierName = o.optString("supplierName", ""),
                    status = o.optString("status", "REQUESTED"),
                    itemsJson = o.getString("itemsJson"),
                    estimatedTotal = o.optDouble("estimatedTotal", 0.0),
                    sourceBatchId = if (o.isNull("sourceBatchId")) null else o.optLong("sourceBatchId"),
                    sourceDocumentRef = o.optString("sourceDocumentRef", ""),
                    notes = o.optString("notes", ""),
                    createdAt = o.getLong("createdAt"),
                    receivedAt = if (o.isNull("receivedAt")) null else o.optLong("receivedAt")
                )
            }
        }

        val rawMaterials = mutableListOf<RawMaterialEntity>()
        root.optJSONArray("rawMaterials")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                rawMaterials += RawMaterialEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    category = o.optString("category", "FILLING"),
                    unit = o.optString("unit", "kg"),
                    unitCost = o.optDouble("unitCost", 0.0),
                    rollWidthInches = o.optDouble("rollWidthInches", 0.0),
                    trackStock = o.optBoolean("trackStock", false),
                    currentStock = o.optDouble("currentStock", 0.0),
                    reorderLevel = o.optDouble("reorderLevel", 0.0),
                    supplierName = o.optString("supplierName", ""),
                    notes = o.optString("notes", ""),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            }
        }

        val bomTemplates = mutableListOf<BomTemplateEntity>()
        root.optJSONArray("bomTemplates")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                bomTemplates += BomTemplateEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    category = o.optString("category", "Bedding"),
                    calcMode = o.optString("calcMode", "MATTRESS_VOLUME"),
                    refLengthIn = o.optDouble("refLengthIn", 72.0),
                    refWidthIn = o.optDouble("refWidthIn", 36.0),
                    refThicknessIn = o.optDouble("refThicknessIn", 4.0),
                    defaultDensity = o.optDouble("defaultDensity", 1.0),
                    defaultMarginPct = o.optDouble("defaultMarginPct", 20.0),
                    defaultStitchCost = o.optDouble("defaultStitchCost", 120.0),
                    wastagePct = o.optDouble("wastagePct", 5.0),
                    notes = o.optString("notes", ""),
                    isActive = o.optBoolean("isActive", true)
                )
            }
        }

        val bomMaterialRules = mutableListOf<BomMaterialRuleEntity>()
        root.optJSONArray("bomMaterialRules")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                bomMaterialRules += BomMaterialRuleEntity(
                    id = o.getLong("id"),
                    templateId = o.getLong("templateId"),
                    rawMaterialId = if (o.isNull("rawMaterialId")) null else o.optLong("rawMaterialId"),
                    materialNameSnapshot = o.getString("materialNameSnapshot"),
                    category = o.optString("category", "FILLING"),
                    unit = o.optString("unit", "kg"),
                    ruleType = o.optString("ruleType", "FIXED_PER_PIECE"),
                    refQtyPerPiece = o.optDouble("refQtyPerPiece", 1.0),
                    densityFactor = o.optDouble("densityFactor", 1.0),
                    seamAllowanceIn = o.optDouble("seamAllowanceIn", 2.0),
                    rollWidthInches = o.optDouble("rollWidthInches", 60.0),
                    wastagePct = o.optDouble("wastagePct", 5.0),
                    unitRateOverride = if (o.isNull("unitRateOverride")) null else o.optDouble("unitRateOverride")
                )
            }
        }

        val wholesaleQuotes = mutableListOf<WholesaleQuoteEntity>()
        root.optJSONArray("wholesaleQuotes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                wholesaleQuotes += WholesaleQuoteEntity(
                    id = o.getLong("id"),
                    quoteNumber = o.getString("quoteNumber"),
                    buyerName = o.optString("buyerName", ""),
                    buyerPhone = o.optString("buyerPhone", ""),
                    buyerCompany = o.optString("buyerCompany", ""),
                    status = o.optString("status", "DRAFT"),
                    templateId = if (o.isNull("templateId")) null else o.optLong("templateId"),
                    title = o.getString("title"),
                    specSummary = o.optString("specSummary", ""),
                    lengthIn = o.optDouble("lengthIn", 72.0),
                    widthIn = o.optDouble("widthIn", 36.0),
                    thicknessIn = o.optDouble("thicknessIn", 4.0),
                    densityFactor = o.optDouble("densityFactor", 1.0),
                    quantity = o.optInt("quantity", 1),
                    stitchChargePerPc = o.optDouble("stitchChargePerPc", 0.0),
                    transportCost = o.optDouble("transportCost", 0.0),
                    discountPercent = o.optDouble("discountPercent", 0.0),
                    gstPercent = o.optDouble("gstPercent", 0.0),
                    marginPercent = o.optDouble("marginPercent", 20.0),
                    unitManufacturingCost = o.optDouble("unitManufacturingCost", 0.0),
                    unitQuotedPrice = o.optDouble("unitQuotedPrice", 0.0),
                    totalCost = o.optDouble("totalCost", 0.0),
                    totalQuoteAmount = o.optDouble("totalQuoteAmount", 0.0),
                    gstAmount = o.optDouble("gstAmount", 0.0),
                    grandTotal = o.optDouble("grandTotal", 0.0),
                    validUntil = o.optString("validUntil", ""),
                    linkedPoId = if (o.isNull("linkedPoId")) null else o.optLong("linkedPoId"),
                    linkedSaleId = if (o.isNull("linkedSaleId")) null else o.optLong("linkedSaleId"),
                    notes = o.optString("notes", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            }
        }

        val wholesaleQuoteLines = mutableListOf<WholesaleQuoteLineEntity>()
        root.optJSONArray("wholesaleQuoteLines")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                wholesaleQuoteLines += WholesaleQuoteLineEntity(
                    id = o.getLong("id"),
                    quoteId = o.getLong("quoteId"),
                    rawMaterialId = if (o.isNull("rawMaterialId")) null else o.optLong("rawMaterialId"),
                    itemName = o.getString("itemName"),
                    category = o.optString("category", "FILLING"),
                    unit = o.optString("unit", "kg"),
                    qtyPerPiece = o.optDouble("qtyPerPiece", 0.0),
                    totalQty = o.optDouble("totalQty", 0.0),
                    unitRate = o.optDouble("unitRate", 0.0),
                    wastagePct = o.optDouble("wastagePct", 0.0),
                    lineTotal = o.optDouble("lineTotal", 0.0),
                    isOverridden = o.optBoolean("isOverridden", false)
                )
            }
        }

        return BackupPayload(
            products = products,
            variants = variants,
            sales = sales,
            saleItems = saleItems,
            returns = returns,
            purchases = purchases,
            vendors = vendors,
            vendorPayments = vendorPayments,
            expenses = expenses,
            customers = customers,
            segments = emptyList(),
            templates = emptyList(),
            campaigns = emptyList(),
            settings = settings,
            productImages = productImages,
            inventoryLocations = inventoryLocations,
            inventoryMovements = inventoryMovements,
            purchaseOrders = purchaseOrders,
            rawMaterials = rawMaterials,
            bomTemplates = bomTemplates,
            bomMaterialRules = bomMaterialRules,
            wholesaleQuotes = wholesaleQuotes,
            wholesaleQuoteLines = wholesaleQuoteLines
        )
    }
}
