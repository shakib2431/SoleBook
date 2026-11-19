package com.example.domain

enum class BusinessCategory(
    val key: String,
    val displayName: String,
    val shortBadge: String,
    val searchKeywords: String
) {
    GROCERY("GROCERY", "Grocery & Kirana", "Grocery", "kirana ration food milk rice grains"),
    CLOTHING("CLOTHING", "Clothing & Fashion", "Clothing", "apparel garments shirts sarees ethnic wear"),
    FOOTWEAR("FOOTWEAR", "Footwear", "Footwear", "shoes sandals slippers chappal sneakers"),
    ELECTRONICS("ELECTRONICS", "Mobile & Electronics", "Electronics", "phones accessories chargers gadgets appliances"),
    COSMETICS("COSMETICS", "Cosmetics & Beauty", "Beauty", "makeup skincare salon perfume personal care"),
    HARDWARE("HARDWARE", "Hardware & Electrical", "Hardware", "tools paint pipes wires switches electrical"),
    STATIONERY("STATIONERY", "Stationery & Books", "Stationery", "books pens notebooks school office paper"),
    GENERAL("GENERAL", "General Retail", "Retail", "shop store convenience variety department"),
    OTHER("OTHER", "Other", "Business", "custom service boutique");

    companion object {
        fun fromKey(key: String?): BusinessCategory {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: GENERAL
        }
    }
}

enum class BusinessChannel(val key: String, val title: String, val subtitle: String) {
    PHYSICAL("PHYSICAL", "Physical store", "Walk-in customers at your shop counter"),
    ONLINE("ONLINE", "Online business", "WhatsApp, Instagram, website, or delivery orders"),
    BOTH("BOTH", "Both online and offline", "Shop counter plus WhatsApp or delivery orders");

    companion object {
        fun fromKey(key: String?): BusinessChannel {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PHYSICAL
        }
    }
}

enum class BusinessOperationMode(val key: String, val title: String, val subtitle: String) {
    RETAIL("RETAIL", "Retail only", "Counter POS, product stock & walk-in billing"),
    WHOLESALE("WHOLESALE", "Wholesale & Custom", "BOM recipes, spec quotes, raw materials & B2B"),
    BOTH("BOTH", "Both (Retail + Wholesale)", "Quick toggle between Retail and Wholesale — unified ledger");

    companion object {
        fun fromKey(key: String?): BusinessOperationMode {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: RETAIL
        }
    }
}

enum class WorkspaceMode(val key: String, val label: String) {
    RETAIL("RETAIL", "Retail"),
    WHOLESALE("WHOLESALE", "Wholesale");

    companion object {
        fun fromKey(key: String?): WorkspaceMode {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: RETAIL
        }
    }
}

enum class CurrencyOption(val symbol: String, val code: String, val label: String) {
    INR("₹", "INR", "INR (₹) — Indian Rupee"),
    USD("$", "USD", "USD ($) — US Dollar"),
    AED("AED ", "AED", "AED — UAE Dirham"),
    GBP("£", "GBP", "GBP (£) — British Pound"),
    EUR("€", "EUR", "EUR (€) — Euro")
}

data class IndustryConfig(
    val category: BusinessCategory,
    val displayCategoryName: String,
    val unitSingular: String,
    val unitPlural: String,
    val variantLabel: String,
    val variantPluralLabel: String,
    val defaultVariants: List<String>,
    val suggestedCategories: List<String>,
    val showAudienceSegmentField: Boolean,
    val audienceSegmentLabel: String,
    val audienceSegmentOptions: List<String>,
    val showColorTag: Boolean,
    val colorTagLabel: String,
    val showStyleTag: Boolean,
    val styleTagLabel: String,
    val showUnitField: Boolean,
    val unitSuggestions: List<String>,
    val showBatchOrSerialField: Boolean,
    val batchOrSerialLabel: String,
    val showExpiryOrWarrantyField: Boolean,
    val expiryOrWarrantyLabel: String,
    val defaultSkuPrefix: String,
    val customerFavouriteLabel: String,
    val customerPrefLabel: String
)

object IndustryProfile {

    fun resolve(categoryKey: String?, customBusinessType: String = ""): IndustryConfig {
        val cat = BusinessCategory.fromKey(categoryKey)
        val displayName = if (cat == BusinessCategory.OTHER && customBusinessType.isNotBlank()) {
            customBusinessType.trim()
        } else {
            cat.displayName
        }
        return when (cat) {
            BusinessCategory.GROCERY -> IndustryConfig(
                category = cat,
                displayCategoryName = displayName,
                unitSingular = "pack",
                unitPlural = "packs",
                variantLabel = "Pack Size",
                variantPluralLabel = "Pack Sizes & Qty",
                defaultVariants = listOf("250g", "500g", "1kg", "Standard"),
                suggestedCategories = listOf("Staples & Grains", "Dairy & Bakery", "Beverages", "Snacks", "Personal Care", "Household"),
                showAudienceSegmentField = false,
                audienceSegmentLabel = "Department",
                audienceSegmentOptions = listOf("Packaged", "Loose", "Fresh"),
                showColorTag = false,
                colorTagLabel = "Flavour / Type",
                showStyleTag = true,
                styleTagLabel = "Packaging / Type",
                showUnitField = true,
                unitSuggestions = listOf("kg", "g", "L", "ml", "pcs", "pack"),
                showBatchOrSerialField = true,
                batchOrSerialLabel = "Batch Number",
                showExpiryOrWarrantyField = true,
                expiryOrWarrantyLabel = "Expiry Date (e.g. Oct 2026)",
                defaultSkuPrefix = "GR",
                customerFavouriteLabel = "Fav Category",
                customerPrefLabel = "Preferred Pack"
            )
            BusinessCategory.CLOTHING -> IndustryConfig(
                category = cat,
                displayCategoryName = displayName,
                unitSingular = "piece",
                unitPlural = "pieces",
                variantLabel = "Size",
                variantPluralLabel = "Size & Qty",
                defaultVariants = listOf("S", "M", "L", "XL", "XXL"),
                suggestedCategories = listOf("Shirts & T-Shirts", "Kurtis & Ethnic", "Trousers & Jeans", "Dresses", "Kidswear", "Accessories"),
                showAudienceSegmentField = true,
                audienceSegmentLabel = "Section",
                audienceSegmentOptions = listOf("Men", "Women", "Kids", "Unisex"),
                showColorTag = true,
                colorTagLabel = "Colour",
                showStyleTag = true,
                styleTagLabel = "Fabric / Fit",
                showUnitField = false,
                unitSuggestions = listOf("piece", "set"),
                showBatchOrSerialField = false,
                batchOrSerialLabel = "Lot Code",
                showExpiryOrWarrantyField = false,
                expiryOrWarrantyLabel = "",
                defaultSkuPrefix = "CL",
                customerFavouriteLabel = "Fav Category",
                customerPrefLabel = "Preferred Size"
            )
            BusinessCategory.FOOTWEAR -> IndustryConfig(
                category = cat,
                displayCategoryName = displayName,
                unitSingular = "pair",
                unitPlural = "pairs",
                variantLabel = "Shoe Size",
                variantPluralLabel = "Size Matrix (UK)",
                defaultVariants = listOf("6", "7", "8", "9", "10"),
                suggestedCategories = listOf("Chappal", "Sandal", "Sports Shoe", "Formal", "Heels", "Kids", "Loafer", "School", "Boot"),
                showAudienceSegmentField = true,
                audienceSegmentLabel = "Gender",
                audienceSegmentOptions = listOf("Men", "Women", "Kids", "Unisex"),
                showColorTag = true,
                colorTagLabel = "Colour",
                showStyleTag = true,
                styleTagLabel = "Material / Style",
                showUnitField = false,
                unitSuggestions = listOf("pair"),
                showBatchOrSerialField = false,
                batchOrSerialLabel = "Batch",
                showExpiryOrWarrantyField = false,
                expiryOrWarrantyLabel = "",
                defaultSkuPrefix = "SL",
                customerFavouriteLabel = "Fav Category",
                customerPrefLabel = "Shoe Size"
            )
            BusinessCategory.ELECTRONICS -> IndustryConfig(
                category = cat,
                displayCategoryName = displayName,
                unitSingular = "unit",
                unitPlural = "units",
                variantLabel = "Model / Spec",
                variantPluralLabel = "Model / Spec & Qty",
                defaultVariants = listOf("Standard", "64GB", "128GB", "256GB"),
                suggestedCategories = listOf("Smartphones", "Chargers & Cables", "Audio & Earbuds", "Cases & Guards", "Appliances", "Smartwatches"),
                showAudienceSegmentField = false,
                audienceSegmentLabel = "Type",
                audienceSegmentOptions = listOf("Device", "Accessory", "Spare"),
                showColorTag = true,
                colorTagLabel = "Colour / Finish",
                showStyleTag = true,
                styleTagLabel = "Model / Spec",
                showUnitField = true,
                unitSuggestions = listOf("unit", "pcs", "box"),
                showBatchOrSerialField = true,
                batchOrSerialLabel = "Serial / IMEI Prefix",
                showExpiryOrWarrantyField = true,
                expiryOrWarrantyLabel = "Warranty (e.g. 12 Months)",
                defaultSkuPrefix = "EL",
                customerFavouriteLabel = "Fav Category",
                customerPrefLabel = "Preferred Model"
            )
            BusinessCategory.COSMETICS -> IndustryConfig(
                category = cat,
                displayCategoryName = displayName,
                unitSingular = "unit",
                unitPlural = "units",
                variantLabel = "Shade / Volume",
                variantPluralLabel = "Variants & Qty",
                defaultVariants = listOf("Standard", "50ml", "100ml", "Travel"),
                suggestedCategories = listOf("Skincare", "Makeup", "Haircare", "Fragrance", "Bath & Body", "Grooming"),
                showAudienceSegmentField = false,
                audienceSegmentLabel = "Range",
                audienceSegmentOptions = listOf("Unisex", "Women", "Men"),
                showColorTag = true,
                colorTagLabel = "Shade / Tone",
                showStyleTag = true,
                styleTagLabel = "Finish / Formulation",
                showUnitField = true,
                unitSuggestions = listOf("ml", "g", "pcs", "bottle"),
                showBatchOrSerialField = true,
                batchOrSerialLabel = "Batch Number",
                showExpiryOrWarrantyField = true,
                expiryOrWarrantyLabel = "Expiry Date (e.g. Dec 2027)",
                defaultSkuPrefix = "CS",
                customerFavouriteLabel = "Fav Category",
                customerPrefLabel = "Preferred Shade"
            )
            BusinessCategory.HARDWARE -> IndustryConfig(
                category = cat,
                displayCategoryName = displayName,
                unitSingular = "unit",
                unitPlural = "units",
                variantLabel = "Size / Rating",
                variantPluralLabel = "Specs & Qty",
                defaultVariants = listOf("Standard", "1/2 inch", "1 inch", "Box"),
                suggestedCategories = listOf("Electricals", "Plumbing", "Hand Tools", "Paints & Polish", "Fasteners", "Lighting"),
                showAudienceSegmentField = false,
                audienceSegmentLabel = "Grade",
                audienceSegmentOptions = listOf("Standard", "Heavy Duty"),
                showColorTag = false,
                colorTagLabel = "Finish",
                showStyleTag = true,
                styleTagLabel = "Material / Gauge",
                showUnitField = true,
                unitSuggestions = listOf("pcs", "meter", "box", "kg", "set"),
                showBatchOrSerialField = true,
                batchOrSerialLabel = "Model / Part No.",
                showExpiryOrWarrantyField = true,
                expiryOrWarrantyLabel = "Warranty (optional)",
                defaultSkuPrefix = "HW",
                customerFavouriteLabel = "Fav Category",
                customerPrefLabel = "Preferred Spec"
            )
            BusinessCategory.STATIONERY -> IndustryConfig(
                category = cat,
                displayCategoryName = displayName,
                unitSingular = "item",
                unitPlural = "items",
                variantLabel = "Pack / Ruling",
                variantPluralLabel = "Variants & Qty",
                defaultVariants = listOf("Piece", "Pack of 5", "Pack of 10", "Box"),
                suggestedCategories = listOf("Notebooks", "Pens & Markers", "Art Supplies", "Textbooks", "Office Files", "School Bags"),
                showAudienceSegmentField = false,
                audienceSegmentLabel = "Audience",
                audienceSegmentOptions = listOf("School", "Office", "Art"),
                showColorTag = true,
                colorTagLabel = "Ink / Colour",
                showStyleTag = true,
                styleTagLabel = "Binding / Tip",
                showUnitField = true,
                unitSuggestions = listOf("pcs", "pack", "box", "dozen", "ream"),
                showBatchOrSerialField = false,
                batchOrSerialLabel = "Edition / ISBN",
                showExpiryOrWarrantyField = false,
                expiryOrWarrantyLabel = "",
                defaultSkuPrefix = "ST",
                customerFavouriteLabel = "Fav Category",
                customerPrefLabel = "Preferred Pack"
            )
            BusinessCategory.GENERAL,
            BusinessCategory.OTHER -> IndustryConfig(
                category = cat,
                displayCategoryName = displayName,
                unitSingular = "unit",
                unitPlural = "units",
                variantLabel = "Variant",
                variantPluralLabel = "Variants & Qty",
                defaultVariants = listOf("Standard", "Small", "Medium", "Large"),
                suggestedCategories = listOf("Featured", "Daily Essentials", "Bestsellers", "Accessories", "Combo Packs", "Seasonal"),
                showAudienceSegmentField = false,
                audienceSegmentLabel = "Group",
                audienceSegmentOptions = listOf("Standard", "Premium"),
                showColorTag = true,
                colorTagLabel = "Colour / Type",
                showStyleTag = true,
                styleTagLabel = "Detail / Spec",
                showUnitField = true,
                unitSuggestions = listOf("pcs", "pack", "box", "kg", "unit"),
                showBatchOrSerialField = true,
                batchOrSerialLabel = "Batch / Serial (optional)",
                showExpiryOrWarrantyField = true,
                expiryOrWarrantyLabel = "Expiry / Warranty (optional)",
                defaultSkuPrefix = "SB",
                customerFavouriteLabel = "Fav Category",
                customerPrefLabel = "Preferred Variant"
            )
        }
    }
}
