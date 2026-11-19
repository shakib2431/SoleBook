package com.example.domain.onboarding

import com.example.data.local.ProductEntity
import java.security.MessageDigest

/**
 * Deterministic multilingual (English, Hindi, Bengali) document text parser and
 * business-action classifier for printed OCR transcripts, typed notes, and structured PDF/table text.
 */
object MultilingualDocumentParser {

    private val purchaseRequestKeywords = listOf(
        "not available", "unavailable", "out of stock", "shortage", "to order", "need to buy",
        "need", "reorder", "purchase request", "req:", "wanted", "backorder", "missing stock",
        "मंगवाना", "चाहिए", "कम है", "स्टॉक नहीं", "खत्म", "ऑर्डर करना",
        "স্টক নেই", "লাগবে", "দরকার", "অর্ডার", "পাওয়া যায়নি"
    )

    private val purchaseOrderHeaderKeywords = listOf(
        "purchase order", "p.o.", "po#", "po no", "buying order", "क्रय आदेश", "ক্রয় আদেশ"
    )

    private val goodsReceivedHeaderKeywords = listOf(
        "invoice", "tax invoice", "challan", "delivery note", "grn", "goods received", "supplied by",
        "wholesale bill", "supplier bill", "बिल प्राप्त", "चालान", "माल आया", "চালান", "মহাজন বিল"
    )

    private val saleHeaderKeywords = listOf(
        "cash memo", "sales receipt", "customer bill", "sold to", "rx", "prescription", "bill no",
        "बिक्री", "बेचा", "রসিদ", "ক্যাশ মেমো", "বিক্রি"
    )

    private val returnKeywords = listOf(
        "return", "refund", "credit note", "exchange back", "वापसी", "लौटाया", "ফেরত"
    )

    private val stockCountKeywords = listOf(
        "stock count", "physical count", "cycle count", "audit count", "counted qty", "गिनती", "গণনা"
    )

    private val openingStockKeywords = listOf(
        "opening stock", "stock list", "inventory list", "catalog", "price list", "स्टॉक सूची", "মজুদ তালিকা"
    )

    private val expenseKeywords = listOf(
        "expense", "rent", "electricity", "power bill", "salary", "wages", "tea", "chai",
        "transport", "freight", "courier", "repair", "किराया", "बिजली", "चाय", "खर्च",
        "ভাড়া", "বিদ্যুৎ", "চা", "খরচ", "বেতন"
    )

    private val udhaarKeywords = listOf(
        "udhaar", "udhar", "baaki", "credit due", "balance due", "receivable", "उधार", "बाकी", "বাকি", "ধার"
    )

    private val vendorPaymentKeywords = listOf(
        "paid supplier", "vendor payment", "paid to vendor", "mahajan payment", "सप्लायर भुगतान", "মহাজনকে প্রদান"
    )

    private val suppliedLineMarkers = listOf(
        "supplied", "given", "dispensed", "billed", "sold", "issued", "delivered", "in stock", "दिया", "বিক্রি", "দেওয়া হয়েছে"
    )

    private val ignoreLinePatterns = listOf(
        Regex("^(sno|sl\\.?\\s*no|sr\\.?\\s*no|item|product|description)\\s+.*(qty|rate|price|amt|amount).*$", RegexOption.IGNORE_CASE),
        Regex("^(sub\\s*total|grand\\s*total|total\\s*amount|gst|cgst|sgst|tax|thank\\s*you|signature).*$", RegexOption.IGNORE_CASE),
        Regex("^[-=_*]{4,}$")
    )

    fun parsePages(
        pages: List<Pair<String, String>>,
        catalogProducts: List<ProductEntity>,
        engineId: String,
        isCloudAiBacked: Boolean,
        extraWarnings: List<String> = emptyList()
    ): DocumentExtractionResult {
        val combinedText = pages.joinToString("\n") { it.second }
        val docHash = sha256(combinedText)
        val detectedLangs = detectLanguages(combinedText)

        val productsBySku = catalogProducts.associateBy { it.skuCode.trim().lowercase() }
        val productsByBarcode = catalogProducts.filter { it.barcode.isNotBlank() }.associateBy { it.barcode.trim().lowercase() }

        val extractedLines = mutableListOf<ExtractedDocumentLine>()
        val warnings = extraWarnings.toMutableList()
        var rowCounter = 1

        var detectedHeaderAction = BusinessActionType.OPENING_STOCK
        var headerActionLabel = "Mixed / General Stock Note"

        for ((pageLabel, rawPageText) in pages) {
            val asciiText = UnitCatalog.normalizeIndicDigits(rawPageText)
            val rawLines = asciiText.lines().map { it.trim() }.filter { it.isNotEmpty() }

            val pageTextLower = asciiText.lowercase()
            val pageDefaultAction = classifyDocumentBlock(pageTextLower)
            detectedHeaderAction = pageDefaultAction
            headerActionLabel = pageDefaultAction.label

            var currentPartyName = extractPartyName(rawLines)
            var currentPartyPhone = extractPhone(asciiText)

            for (rawLine in rawLines) {
                if (ignoreLinePatterns.any { it.matches(rawLine) }) continue
                val lineLower = rawLine.lowercase()

                // Update running party header if line starts with Supplier/Customer/Party/Name
                extractPartyFromLine(rawLine)?.let { (pName, pPhone) ->
                    if (pName.isNotBlank()) currentPartyName = pName
                    if (pPhone.isNotBlank()) currentPartyPhone = pPhone
                    continue
                }

                // Skip pure metadata header lines like "Date: 12/11/2026" or "Invoice #104"
                if (isHeaderMetadataLine(lineLower)) continue

                val parsed = parseStructuredLine(
                    rowNumber = rowCounter,
                    sourceDocPage = pageLabel,
                    rawLine = rawLine,
                    lineLower = lineLower,
                    pageDefaultAction = pageDefaultAction,
                    partyName = currentPartyName,
                    partyPhone = currentPartyPhone,
                    productsBySku = productsBySku,
                    productsByBarcode = productsByBarcode,
                    catalogProducts = catalogProducts
                )
                if (parsed != null) {
                    extractedLines += parsed
                    rowCounter++
                }
            }
        }

        if (extractedLines.isEmpty()) {
            warnings += "No structured item or financial rows could be extracted cleanly from this document."
        }
        val avgConf = if (extractedLines.isNotEmpty()) {
            extractedLines.map { it.confidence }.average().toFloat()
        } else 0.0f

        val docTitle = if (extractedLines.map { it.actionType }.distinct().size > 1) {
            "Mixed Operations (${extractedLines.map { it.actionType.label }.distinct().joinToString(" + ")})"
        } else {
            headerActionLabel
        }

        return DocumentExtractionResult(
            engineId = engineId,
            isCloudAiBacked = isCloudAiBacked,
            documentTypeLabel = docTitle,
            detectedLanguages = detectedLangs,
            overallConfidence = avgConf,
            extractedTextByPage = pages,
            lines = extractedLines,
            warnings = warnings.distinct(),
            docHash = docHash
        )
    }

    private fun classifyDocumentBlock(textLower: String): BusinessActionType {
        return when {
            purchaseOrderHeaderKeywords.any { textLower.contains(it) } -> BusinessActionType.PURCHASE_ORDER
            goodsReceivedHeaderKeywords.any { textLower.contains(it) } -> BusinessActionType.GOODS_RECEIVED
            stockCountKeywords.any { textLower.contains(it) } -> BusinessActionType.STOCK_COUNT
            returnKeywords.any { textLower.contains(it) } -> BusinessActionType.RETURN_DRAFT
            vendorPaymentKeywords.any { textLower.contains(it) } -> BusinessActionType.VENDOR_PAYMENT
            udhaarKeywords.any { textLower.contains(it) } -> BusinessActionType.CUSTOMER_CREDIT
            saleHeaderKeywords.any { textLower.contains(it) } -> BusinessActionType.SALE_DRAFT
            openingStockKeywords.any { textLower.contains(it) } -> BusinessActionType.OPENING_STOCK
            expenseKeywords.any { textLower.contains(it) } && !textLower.contains("qty") -> BusinessActionType.EXPENSE
            purchaseRequestKeywords.any { textLower.contains(it) } -> BusinessActionType.PURCHASE_REQUEST
            else -> BusinessActionType.CREATE_PRODUCT
        }
    }

    private fun parseStructuredLine(
        rowNumber: Int,
        sourceDocPage: String,
        rawLine: String,
        lineLower: String,
        pageDefaultAction: BusinessActionType,
        partyName: String,
        partyPhone: String,
        productsBySku: Map<String, ProductEntity>,
        productsByBarcode: Map<String, ProductEntity>,
        catalogProducts: List<ProductEntity>
    ): ExtractedDocumentLine? {
        // Line-level override classification (critical for mixed slips!)
        val lineAction = when {
            purchaseRequestKeywords.any { lineLower.contains(it) } -> BusinessActionType.PURCHASE_REQUEST
            returnKeywords.any { lineLower.contains(it) } -> BusinessActionType.RETURN_DRAFT
            suppliedLineMarkers.any { lineLower.contains(it) } -> BusinessActionType.SALE_DRAFT
            vendorPaymentKeywords.any { lineLower.contains(it) } -> BusinessActionType.VENDOR_PAYMENT
            udhaarKeywords.any { lineLower.contains(it) } -> BusinessActionType.CUSTOMER_CREDIT
            expenseKeywords.any { lineLower.contains(it) } -> BusinessActionType.EXPENSE
            else -> pageDefaultAction
        }

        val batchMatch = Regex("(?:batch|lot|b\\.no)\\s*[:#-]?\\s*([A-Za-z0-9-]+)", RegexOption.IGNORE_CASE).find(rawLine)
        val batchNo = batchMatch?.groupValues?.getOrNull(1).orEmpty()
        val expMatch = Regex("(?:exp|expiry|valid)\\s*[:#-]?\\s*([0-9]{1,2}[/-][0-9]{2,4}|[A-Za-z]{3}\\s*20[0-9]{2})", RegexOption.IGNORE_CASE).find(rawLine)
        val expiry = expMatch?.groupValues?.getOrNull(1).orEmpty()

        val cleanedTextForParsing = rawLine
            .replace(batchMatch?.value ?: "", "")
            .replace(expMatch?.value ?: "", "")
            .trim()

        // Expense / Udhaar / Vendor payment single-amount line
        if (lineAction in listOf(BusinessActionType.EXPENSE, BusinessActionType.CUSTOMER_CREDIT, BusinessActionType.VENDOR_PAYMENT)) {
            val amt = extractTrailingOrMarkedAmount(cleanedTextForParsing) ?: return null
            val catOrNotes = cleanedTextForParsing
                .replace(Regex("(?:rs\\.?|inr|₹)?\\s*[0-9]+(?:\\.[0-9]+)?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("[-|:]"), " ")
                .trim()
                .ifBlank { lineAction.label }
            return ExtractedDocumentLine(
                rowNumber = rowNumber,
                sourceDocPage = sourceDocPage,
                actionType = lineAction,
                category = if (lineAction == BusinessActionType.EXPENSE) classifyExpenseCategory(lineLower) else "",
                amount = amt,
                partyName = if (lineAction != BusinessActionType.EXPENSE) partyName.ifBlank { catOrNotes } else partyName,
                partyPhone = partyPhone,
                confidence = 0.80f,
                rawLineText = rawLine,
                notes = catOrNotes
            )
        }

        // Tabular split (pipe | or tab \t or 3+ spaces)
        val cols = when {
            cleanedTextForParsing.contains('|') -> cleanedTextForParsing.split('|').map { it.trim() }.filter { it.isNotEmpty() }
            cleanedTextForParsing.contains('\t') -> cleanedTextForParsing.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }

        var qty = 1.0
        var unit = "pcs"
        var price: Double? = null
        var namePart = ""
        var skuGuess = ""
        var barcodeGuess = ""
        var conf = 0.72f

        if (cols.size >= 2) {
            val nums = cols.mapNotNull { c ->
                val clean = c.replace("₹", "").replace(",", "").trim()
                clean.toDoubleOrNull()?.let { c to it }
            }
            val barcodeCol = cols.firstOrNull { it.length in 8..14 && it.all { ch -> ch.isDigit() } }
            val skuCol = cols.firstOrNull { it.matches(Regex("[A-Za-z]{1,4}-[0-9]{1,6}")) }
            barcodeGuess = barcodeCol.orEmpty()
            skuGuess = skuCol.orEmpty()

            // First non-numeric non-code column is usually product name
            namePart = cols.firstOrNull { c ->
                c != barcodeCol && c != skuCol && c.replace("₹", "").replace(",", "").trim().toDoubleOrNull() == null && c.any { it.isLetter() }
            } ?: cols.first()

            val qtyCol = cols.firstOrNull { c ->
                c != namePart && extractQtyAndUnit(c) != null
            }
            if (qtyCol != null) {
                val (q, u) = extractQtyAndUnit(qtyCol)!!
                qty = q
                unit = u
            } else if (nums.isNotEmpty()) {
                qty = nums.first().second
            }
            if (nums.size >= 2) {
                price = nums.last().second
            } else if (nums.size == 1 && qtyCol != null) {
                price = nums.first().second
            }
            conf = 0.86f
        } else {
            // Freeform counter note / slip line: e.g. "2x Paracetamol 500mg @ 30 - Supplied"
            // or "10 kg Basmati Rice ₹560" or "Azithromycin 500mg - 1 strip - Not available"
            var work = cleanedTextForParsing
            // Strip status annotation tail after dash for cleaner name extraction
            val statusDash = Regex("\\s[-–—]\\s*(supplied|given|sold|not available|unavailable|out of stock|shortage|to order|pending|stock nahi).*$", RegexOption.IGNORE_CASE)
            work = work.replace(statusDash, "").trim()

            // Detect barcode or SKU inline
            Regex("\\b([0-9]{8,14})\\b").find(work)?.let { m ->
                barcodeGuess = m.groupValues[1]
                work = work.replace(m.value, "").trim()
            }
            Regex("\\b([A-Z]{1,4}-[0-9]{2,6})\\b").find(work)?.let { m ->
                skuGuess = m.groupValues[1]
                work = work.replace(m.value, "").trim()
            }

            // Extract @ price or ₹ price or trailing price
            val atPrice = Regex("(?:@|₹|rs\\.?|inr)\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE).find(work)
            if (atPrice != null) {
                price = atPrice.groupValues[1].toDoubleOrNull()
                work = work.replace(atPrice.value, "").trim()
            }

            // Leading qty e.g. "2x", "5 pcs", "10 kg"
            val leadQty = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*(x|×|pcs|pc|kg|g|gm|l|ltr|ml|box|pack|pkt|pair|dozen|strip|bottle|नग|पीस|किलो|पैक|पत्ता|পিস|কেজি|প্যাক|টা|টি|জোড়া)?(?=\\s|$|[-,|/])", RegexOption.IGNORE_CASE).find(work)
            if (leadQty != null) {
                qty = leadQty.groupValues[1].toDoubleOrNull() ?: 1.0
                val rawUnit = leadQty.groupValues[2]
                if (rawUnit.isNotBlank() && rawUnit.lowercase() !in listOf("x", "×")) {
                    unit = UnitCatalog.normalize(rawUnit)
                }
                work = work.removeRange(leadQty.range).trim()
            } else {
                // Take the LAST inline/trailing qty+unit so pack sizes inside product names (e.g. "Rice 1kg 2 kg") aren't consumed as order qty
                val trailQty = Regex("(?:^|\\s)([0-9]+(?:\\.[0-9]+)?)\\s*(pcs|pc|kg|g|gm|l|ltr|ml|box|pack|pkt|pair|dozen|strip|bottle|नग|पीस|किलो|पैक|पत्ता|পিস|কেজি|প্যাক|টা|টি|জোড়া)(?=\\s|$|[-,|/])", RegexOption.IGNORE_CASE)
                    .findAll(work)
                    .lastOrNull()
                if (trailQty != null) {
                    qty = trailQty.groupValues[1].toDoubleOrNull() ?: 1.0
                    unit = UnitCatalog.normalize(trailQty.groupValues[2])
                    work = work.removeRange(trailQty.range).trim()
                }
            }

            if (price == null) {
                val trailNum = Regex("\\s+([0-9]+(?:\\.[0-9]+)?)$").find(work)
                if (trailNum != null) {
                    price = trailNum.groupValues[1].toDoubleOrNull()
                    work = work.removeRange(trailNum.range).trim()
                }
            }

            // Clean leading/trailing status markers (e.g. "বিল Sold", "বিক্রি Supplied", "Out of stock / मंगवाना है")
            val markerNoise = Regex(
                "\\b(supplied|given|dispensed|billed|sold|issued|delivered|in stock|out of stock|not available|unavailable|shortage|to order|reorder)\\b|बिल|बिक्री|मंगवाना है|मंगवाना|चाहिए|স্টক নেই|লাগবে|দরকার|বিক্রি",
                RegexOption.IGNORE_CASE
            )
            val cleanedName = work.replace(markerNoise, " ")
                .replace(Regex("\\s*[/|•]+\\s*"), " ")
                .replace(Regex("\\s+"), " ")
                .trim(' ', '-', ':', ',', '|', '•', '.', '/')

            namePart = cleanedName.ifBlank { work.trim(' ', '-', ':', ',', '|', '•', '.') }
            if (namePart.isBlank() && barcodeGuess.isBlank() && skuGuess.isBlank()) return null
            if (namePart.length < 2 && barcodeGuess.isBlank() && skuGuess.isBlank()) return null
        }

        // Catalog reconciliation hint
        val matchedProd = barcodeGuess.takeIf { it.isNotBlank() }?.let { productsByBarcode[it.lowercase()] }
            ?: skuGuess.takeIf { it.isNotBlank() }?.let { productsBySku[it.lowercase()] }
            ?: findBestNameMatch(namePart, catalogProducts)

        val resolvedCost = when (lineAction) {
            BusinessActionType.GOODS_RECEIVED, BusinessActionType.PURCHASE_ORDER, BusinessActionType.PURCHASE_REQUEST, BusinessActionType.CREATE_PRODUCT, BusinessActionType.OPENING_STOCK ->
                price ?: matchedProd?.costPrice
            else -> matchedProd?.costPrice
        }
        val resolvedSell = when (lineAction) {
            BusinessActionType.SALE_DRAFT, BusinessActionType.RETURN_DRAFT -> price ?: matchedProd?.sellingPrice
            BusinessActionType.CREATE_PRODUCT -> price ?: matchedProd?.sellingPrice
            else -> matchedProd?.sellingPrice
        }

        val finalConf = when {
            matchedProd != null -> (conf + 0.10f).coerceAtMost(0.95f)
            price != null -> conf
            else -> (conf - 0.12f).coerceAtLeast(0.45f)
        }

        return ExtractedDocumentLine(
            rowNumber = rowNumber,
            sourceDocPage = sourceDocPage,
            actionType = lineAction,
            productName = matchedProd?.name ?: namePart,
            sku = matchedProd?.skuCode ?: skuGuess,
            barcode = matchedProd?.barcode?.ifBlank { barcodeGuess } ?: barcodeGuess,
            category = matchedProd?.category ?: "",
            quantity = qty.coerceAtLeast(1.0),
            unitLabel = unit,
            costPrice = resolvedCost,
            sellingPrice = resolvedSell,
            amount = (price ?: resolvedSell ?: resolvedCost ?: 0.0) * qty,
            partyName = partyName,
            partyPhone = partyPhone,
            batchNumber = batchNo,
            expiryDate = expiry,
            confidence = finalConf,
            rawLineText = rawLine,
            notes = if (lineAction == BusinessActionType.PURCHASE_REQUEST) "Marked unavailable / replenishment needed" else ""
        )
    }

    private fun extractQtyAndUnit(cell: String): Pair<Double, String>? {
        val m = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*([A-Za-z\\u0900-\\u097F\\u0980-\\u09FF]*)$").find(cell.trim()) ?: return null
        val q = m.groupValues[1].toDoubleOrNull() ?: return null
        val u = UnitCatalog.normalize(m.groupValues[2], "pcs")
        return q to u
    }

    private fun extractTrailingOrMarkedAmount(text: String): Double? {
        val m = Regex("(?:₹|rs\\.?|inr)?\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE).findAll(text).lastOrNull()
        return m?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun classifyExpenseCategory(lower: String): String = when {
        lower.contains("rent") || lower.contains("किराया") || lower.contains("ভাড়া") -> "Rent"
        lower.contains("electric") || lower.contains("बिजली") || lower.contains("বিদ্যুৎ") -> "Electricity"
        lower.contains("salary") || lower.contains("wage") || lower.contains("বেতন") -> "Salary"
        lower.contains("transport") || lower.contains("freight") || lower.contains("courier") -> "Transport"
        else -> "Other"
    }

    private fun extractPartyName(lines: List<String>): String {
        for (l in lines.take(6)) {
            extractPartyFromLine(l)?.first?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun extractPartyFromLine(line: String): Pair<String, String>? {
        val m = Regex("^(?:supplier|vendor|party|customer|patient|from|to|बिल नाम|नाम|নাম)\\s*[:\\-]\\s*(.+)$", RegexOption.IGNORE_CASE).find(line)
            ?: return null
        val rest = m.groupValues[1].trim()
        val phone = extractPhone(rest)
        val name = rest.replace(phone, "").trim(' ', ',', '-', '(', ')')
        return name to phone
    }

    private fun extractPhone(text: String): String {
        return Regex("(?:\\+91|0)?([6-9][0-9]{9})").find(text)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun isHeaderMetadataLine(lower: String): Boolean {
        return lower.startsWith("date:") || lower.startsWith("bill no") ||
            lower.startsWith("invoice no") || lower.startsWith("gstin") ||
            lower.startsWith("page ")
    }

    private fun findBestNameMatch(query: String, catalog: List<ProductEntity>): ProductEntity? {
        val q = query.trim().lowercase().replace(Regex("[^a-z0-9\\s]+"), " ").replace(Regex("\\s+"), " ")
        if (q.length < 3) return null
        catalog.firstOrNull { it.name.trim().lowercase() == q }?.let { return it }
        val qTokens = q.split(' ').filter { it.length >= 3 }.toSet()
        if (qTokens.isEmpty()) return null
        var best: ProductEntity? = null
        var bestOverlap = 0.0
        for (p in catalog) {
            val pTokens = p.name.lowercase().replace(Regex("[^a-z0-9\\s]+"), " ").split(' ').filter { it.length >= 3 }.toSet()
            if (pTokens.isEmpty()) continue
            val inter = qTokens.intersect(pTokens).size.toDouble()
            val score = inter / maxOf(qTokens.size, pTokens.size).toDouble()
            if (score > bestOverlap && score >= 0.55) {
                bestOverlap = score
                best = p
            }
        }
        return best
    }

    fun detectLanguages(text: String): List<String> {
        val langs = mutableListOf<String>()
        if (text.any { it in 'a'..'z' || it in 'A'..'Z' }) langs += "EN"
        if (text.any { it in '\u0900'..'\u097F' }) langs += "HI"
        if (text.any { it in '\u0980'..'\u09FF' }) langs += "BN"
        return langs.ifEmpty { listOf("EN") }
    }

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
