package com.example.domain.onboarding

enum class TargetField(
    val key: String,
    val displayLabel: String,
    val requiredForNewProduct: Boolean = false
) {
    IGNORE("IGNORE", "Skip / Ignore"),
    SKU("SKU", "SKU / Item Code"),
    BARCODE("BARCODE", "Barcode / EAN / UPC"),
    NAME("NAME", "Product Name", requiredForNewProduct = true),
    CATEGORY("CATEGORY", "Category"),
    BRAND("BRAND", "Brand / Make"),
    UNIT("UNIT", "Unit (pcs, kg, L...)"),
    COST_PRICE("COST_PRICE", "Cost / Purchase Price"),
    SELLING_PRICE("SELLING_PRICE", "Selling Price"),
    MRP("MRP", "MRP / List Price"),
    OPENING_STOCK("OPENING_STOCK", "Opening Stock / Qty"),
    REORDER_LEVEL("REORDER_LEVEL", "Reorder Alert Level"),
    VARIANT("VARIANT", "Variant / Size / Pack"),
    VARIANT_TYPE("VARIANT_TYPE", "Variant Type (Size/Colour/Model)"),
    COLOR("COLOR", "Colour / Shade"),
    MODEL("MODEL", "Model / Spec"),
    PACK_SIZE("PACK_SIZE", "Pack Size"),
    VARIANT_BREAKDOWN("VARIANT_BREAKDOWN", "Size Breakdown (e.g. 7:4,8:6)"),
    LOCATION("LOCATION", "Rack / Location / Bin"),
    SUPPLIER("SUPPLIER", "Supplier / Vendor"),
    BATCH_NUMBER("BATCH_NUMBER", "Batch / Lot Number"),
    EXPIRY_DATE("EXPIRY_DATE", "Expiry / Warranty"),
    SERIAL_NUMBERS("SERIAL_NUMBERS", "Serial / IMEI Numbers"),
    IMAGE_REF("IMAGE_REF", "Image Filename or URL")
}

object UnitCatalog {
    val standardUnits = listOf(
        "pcs", "kg", "g", "L", "ml", "box", "pack", "pair", "dozen", "meter", "strip", "bottle", "set", "ream"
    )

    private val aliases = mapOf(
        "pc" to "pcs", "pcs" to "pcs", "piece" to "pcs", "pieces" to "pcs", "nos" to "pcs",
        "no" to "pcs", "unit" to "pcs", "units" to "pcs", "ea" to "pcs", "each" to "pcs",
        "नग" to "pcs", "पीस" to "pcs", "পিস" to "pcs", "টা" to "pcs", "টি" to "pcs",
        "kg" to "kg", "kgs" to "kg", "kilogram" to "kg", "kilograms" to "kg", "kilo" to "kg",
        "किलो" to "kg", "किग्रा" to "kg", "কেজি" to "kg",
        "g" to "g", "gm" to "g", "gms" to "g", "gram" to "g", "grams" to "g",
        "ग्राम" to "g", "গ্রাম" to "g",
        "l" to "L", "ltr" to "L", "ltrs" to "L", "liter" to "L", "liters" to "L", "litre" to "L", "litres" to "L",
        "लीटर" to "L", "লিটার" to "L",
        "ml" to "ml", "milliliter" to "ml", "millilitre" to "ml", "मिली" to "ml",
        "box" to "box", "boxes" to "box", "bx" to "box", "carton" to "box", "ctn" to "box",
        "बॉक्स" to "box", "পেটি" to "box", "বাক্স" to "box",
        "pack" to "pack", "packs" to "pack", "pkt" to "pack", "pkts" to "pack", "packet" to "pack", "pouch" to "pack",
        "पैक" to "pack", "प्याकेट" to "pack", "প্যাক" to "pack", "প্যাকেট" to "pack",
        "pair" to "pair", "pairs" to "pair", "pr" to "pair", "prs" to "pair",
        "जोड़ी" to "pair", "जोड़ा" to "pair", "জোড়া" to "pair",
        "dozen" to "dozen", "dozens" to "dozen", "doz" to "dozen", "dz" to "dozen",
        "दर्जन" to "dozen", "ডজন" to "dozen",
        "meter" to "meter", "meters" to "meter", "metre" to "meter", "mtr" to "meter", "m" to "meter",
        "मीटर" to "meter", "মিটার" to "meter",
        "strip" to "strip", "strips" to "strip", "पत्ता" to "strip", "स्ट्रिप" to "strip", "স্ট্রিপ" to "strip",
        "bottle" to "bottle", "bottles" to "bottle", "btl" to "bottle", "बोतल" to "bottle", "বোতল" to "bottle",
        "set" to "set", "sets" to "set", "सेट" to "set", "সেট" to "set",
        "ream" to "ream", "reams" to "ream"
    )

    fun normalize(raw: String, fallback: String = "pcs"): String {
        val cleaned = raw.trim().lowercase()
        if (cleaned.isEmpty()) return fallback
        return aliases[cleaned] ?: raw.trim()
    }

    /**
     * Converts Devanagari (०-९) and Bengali (০-৯) digits into ASCII 0-9.
     */
    fun normalizeIndicDigits(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '\u0966'..'\u096F' -> sb.append(('0'.code + (ch.code - 0x0966)).toChar())
                in '\u09E6'..'\u09EF' -> sb.append(('0'.code + (ch.code - 0x09E6)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

object ColumnMapper {

    private val aliasRules: List<Pair<TargetField, List<String>>> = listOf(
        TargetField.SKU to listOf("sku", "sku_code", "skucode", "item_code", "itemcode", "article_no", "article_code", "product_code", "code"),
        TargetField.BARCODE to listOf("barcode", "bar_code", "barcode_number", "ean", "ean13", "ean_number", "ean_code", "upc", "gtin", "isbn", "scan_code", "upc_code"),
        TargetField.NAME to listOf("product_name", "item_name", "name", "title", "article", "description", "product", "item", "medicine", "goods"),
        TargetField.CATEGORY to listOf("category", "cat", "dept", "department", "section", "group", "product_type"),
        TargetField.BRAND to listOf("brand", "company", "manufacturer", "mfg", "make"),
        TargetField.UNIT to listOf("unit", "uom", "unit_label", "unit_of_measure", "measure"),
        TargetField.COST_PRICE to listOf("cost_price", "cost", "purchase_price", "buy_price", "cp", "landing_cost", "wholesale_price", "wholesale_rate", "wholesale_cost", "purchase_rate", "buying_rate"),
        TargetField.SELLING_PRICE to listOf("selling_price", "sell_price", "sale_price", "sp", "rate", "price", "retail_price", "offer_price"),
        TargetField.MRP to listOf("mrp", "mrp_price", "list_price", "max_retail_price", "printed_price"),
        TargetField.OPENING_STOCK to listOf("opening_stock", "opening_qty", "stock", "qty", "quantity", "available_stock", "closing_stock", "closing_balance", "closing_qty", "balance", "count", "on_hand"),
        TargetField.REORDER_LEVEL to listOf("reorder_level", "reorder", "min_stock", "min_qty", "low_stock_alert", "alert_level"),
        TargetField.VARIANT_BREAKDOWN to listOf("size_breakdown", "variant_breakdown", "stock_breakdown", "sizes"),
        TargetField.VARIANT to listOf("variant", "size", "shade", "flavour", "flavor", "spec"),
        TargetField.VARIANT_TYPE to listOf("variant_type", "attribute_type"),
        TargetField.COLOR to listOf("color", "colour", "color_tag"),
        TargetField.MODEL to listOf("model", "style", "style_tag", "model_no"),
        TargetField.PACK_SIZE to listOf("pack_size", "pack", "weight", "volume", "capacity"),
        TargetField.LOCATION to listOf("location", "rack", "shelf", "bin", "godown", "warehouse", "store"),
        TargetField.SUPPLIER to listOf("supplier", "vendor", "distributor", "party", "wholesaler"),
        TargetField.BATCH_NUMBER to listOf("batch", "batch_no", "batch_number", "lot", "lot_code"),
        TargetField.EXPIRY_DATE to listOf("expiry", "exp_date", "expiry_date", "best_before", "warranty"),
        TargetField.SERIAL_NUMBERS to listOf("serial", "serial_no", "serial_numbers", "imei"),
        TargetField.IMAGE_REF to listOf("image", "image_url", "image_file", "photo", "photo_uri", "photo_file")
    )

    fun canonicalizeHeader(header: String): String {
        return header.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    fun autoDetectMappings(headers: List<String>): Map<Int, TargetField> {
        val used = mutableSetOf<TargetField>()
        val result = linkedMapOf<Int, TargetField>()

        // Pass 1: exact normalized header match
        headers.forEachIndexed { idx, raw ->
            val norm = canonicalizeHeader(raw)
            val exact = aliasRules.firstOrNull { (field, keys) ->
                field !in used && keys.any { it == norm }
            }?.first
            if (exact != null) {
                result[idx] = exact
                used += exact
            }
        }

        // Pass 2: substring token fallback for messy spreadsheet columns like "Retail Selling Price (INR)"
        headers.forEachIndexed { idx, raw ->
            if (idx in result) return@forEachIndexed
            val norm = canonicalizeHeader(raw)
            if (norm.isBlank()) {
                result[idx] = TargetField.IGNORE
                return@forEachIndexed
            }
            val partial = aliasRules.firstOrNull { (field, keys) ->
                field !in used && keys.any { k -> k.length >= 3 && (norm.contains(k) || k.contains(norm)) }
            }?.first ?: TargetField.IGNORE
            result[idx] = partial
            if (partial != TargetField.IGNORE) used += partial
        }

        return result
    }
}
