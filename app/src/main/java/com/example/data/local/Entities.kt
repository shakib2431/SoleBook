package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["skuCode"], unique = true),
        Index(value = ["category"]),
        Index(value = ["barcode"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skuCode: String,
    val name: String,
    val category: String,
    val brand: String,
    val gender: String,
    val photoUri: String,
    val costPrice: Double,
    val sellingPrice: Double,
    val mrp: Double? = null,
    val supplier: String = "",
    val reorderLevel: Int = 5,
    val colorTag: String = "",
    val styleTag: String = "",
    val rackLocation: String = "",
    val isRepeatable: Boolean = false,
    val vendorId: Long? = null,
    val lotCode: String = "",
    val unitLabel: String = "",
    val batchOrSerial: String = "",
    val expiryOrWarranty: String = "",
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val barcode: String = "",
    val trackBatchExpiry: Boolean = false,
    val trackSerialNumbers: Boolean = false,
    val secondaryUnitLabel: String = "",
    val unitConversionFactor: Double = 1.0
)

@Entity(tableName = "product_fingerprints")
data class ProductFingerprintEntity(
    @PrimaryKey val productId: Long,
    val signatureCsv: String,
    val dominantColor: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "product_variants",
    indices = [Index(value = ["productId"]), Index(value = ["barcode"])]
)
data class ProductVariantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val size: String,
    val stock: Int,
    val variantType: String = "Variant",
    val variantSku: String = "",
    val barcode: String = "",
    val colorName: String = "",
    val packSizeLabel: String = "",
    val modelName: String = "",
    val costOverride: Double? = null,
    val priceOverride: Double? = null,
    val location: String = "",
    val batchNumber: String = "",
    val expiryDate: String = "",
    val serialNumbersCsv: String = ""
)

@Entity(
    tableName = "sales",
    indices = [Index(value = ["timestamp"]), Index(value = ["customerId"])]
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val customerId: Long? = null,
    val customerPhoneSnapshot: String = "",
    val customerNameSnapshot: String = "",
    val paymentMode: String, // CASH, UPI, CARD, CREDIT
    val discountAmount: Double = 0.0,
    val discountType: String = "AMOUNT", // AMOUNT, PERCENT
    val discountInput: Double = 0.0,
    val gstEnabled: Boolean = false,
    val gstRate: Double = 12.0,
    val gstInclusive: Boolean = true,
    val gstAmount: Double = 0.0,
    val subtotal: Double,
    val totalRevenue: Double, // Net revenue excluding GST when GST enabled, else subtotal - discount
    val totalCollected: Double, // Actual cash/UPI/credit amount billed to customer
    val creditSettled: Boolean = true,
    val creditSettledAt: Long? = null,
    val notes: String = "",
    val saleChannel: String = "RETAIL" // RETAIL, WHOLESALE
)

@Entity(
    tableName = "sale_items",
    indices = [Index(value = ["saleId"]), Index(value = ["productId"])]
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val skuSnapshot: String,
    val categorySnapshot: String,
    val size: String,
    val quantity: Int,
    val costPriceAtSale: Double,
    val sellingPriceAtSale: Double,
    val lineTotal: Double
)

@Entity(
    tableName = "returns",
    indices = [Index(value = ["saleId"]), Index(value = ["productId"]), Index(value = ["timestamp"])]
)
data class ReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val saleItemId: Long? = null,
    val productId: Long,
    val productNameSnapshot: String,
    val size: String,
    val quantity: Int,
    val refundAmount: Double,
    val costReversed: Double,
    val type: String, // RETURN or EXCHANGE
    val exchangeSize: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = ""
)

@Entity(
    tableName = "purchases",
    indices = [Index(value = ["productId"]), Index(value = ["timestamp"]), Index(value = ["vendorId"])]
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val totalUnits: Int,
    val unitCostPrice: Double,
    val totalCost: Double,
    val supplier: String = "",
    val vendorId: Long? = null,
    val lotType: String = "FRESH_LOT", // FRESH_LOT or REPEAT_RESTOCK
    val paymentStatus: String = "PAID", // PAID, PARTIAL, CREDIT
    val paidAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val sizeBreakdown: String = "", // e.g. "7:4, 8:6, 9:4"
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "vendors",
    indices = [Index(value = ["name"])]
)
data class VendorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val whatsapp: String = "",
    val cityMarket: String = "",
    val specialtiesCsv: String = "", // e.g. "Chappal,Sandal"
    val creditBalance: Double = 0.0, // Payable owed by shop to vendor
    val notes: String = "",
    val lastPurchaseAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vendor_payments",
    indices = [Index(value = ["vendorId"]), Index(value = ["timestamp"])]
)
data class VendorPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendorId: Long,
    val amount: Double,
    val paymentMode: String = "CASH", // CASH, UPI, BANK
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["timestamp"])]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // Rent, Salary, Electricity, Transport, Packaging, Other
    val amount: Double,
    val isRecurringMonthly: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "customers",
    indices = [Index(value = ["phone"], unique = true)]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phone: String,
    val name: String = "",
    val email: String = "",
    val firstVisitAt: Long = System.currentTimeMillis(),
    val lastVisitAt: Long = System.currentTimeMillis(),
    val totalSpent: Double = 0.0,
    val visitCount: Int = 0,
    val favouriteCategory: String = "",
    val favouriteSize: String = "",
    val specialDate: String = "", // Birthday/Anniversary
    val notes: String = "",
    val whatsappConsent: Boolean = true,
    val creditBalance: Double = 0.0
)

@Entity(tableName = "segments")
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ruleKey: String, // VIP, REGULAR, NEW, LAPSED, CREDIT_PENDING
    val description: String
)

@Entity(tableName = "message_templates")
data class MessageTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val segmentRuleKey: String,
    val bodyTemplate: String
)

@Entity(
    tableName = "campaigns",
    indices = [Index(value = ["timestamp"])]
)
data class CampaignLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long? = null,
    val segmentKey: String,
    val customerId: Long? = null,
    val customerPhone: String,
    val messageSent: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notice_states")
data class NoticeStateEntity(
    @PrimaryKey val noticeKey: String,
    val lastNotifiedAt: Long = 0L,
    val snoozedUntil: Long = 0L,
    val dismissedUntil: Long = 0L,
    val lastSignatureHash: String = "",
    val isRead: Boolean = false
)

@Entity(tableName = "settings")
data class ShopSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "Laxmi Footwear",
    val businessCategory: String = "FOOTWEAR",
    val customBusinessType: String = "",
    val businessChannel: String = "PHYSICAL", // PHYSICAL, ONLINE, BOTH
    val hasCompletedOnboarding: Boolean = false,
    val shopLogoUri: String = "",
    val shortHeaderName: String = "",
    val headerBrandMode: String = "LOGO_AND_NAME", // LOGO_AND_NAME, LOGO_ONLY, NAME_ONLY
    val currencySymbol: String = "₹",
    val languageCode: String = "en",
    val gstEnabled: Boolean = false,
    val gstRate: Double = 12.0,
    val gstInclusive: Boolean = true,
    val defaultLeadTimeDays: Int = 3,
    val seasonalityMode: String = "NONE", // NONE, DIWALI, EID, PONGAL, WEDDING, MONSOON, SCHOOL
    val seasonalityUpliftPercent: Double = 0.0,
    val hasCompletedWalkthrough: Boolean = false,
    val isDemoLoaded: Boolean = false,
    val alertsEnabled: Boolean = true,
    val instantSaleAlertsEnabled: Boolean = false,
    val stockAlertsEnabled: Boolean = true,
    val demandAlertsEnabled: Boolean = true,
    val marginAlertsEnabled: Boolean = true,
    val alertIntervalHours: Int = 4,
    val maxAlertsPerDay: Int = 4,
    val quietStartHour: Int = 21,
    val quietEndHour: Int = 8,
    val businessOperationMode: String = "RETAIL", // RETAIL, WHOLESALE, BOTH
    val activeWorkspaceMode: String = "RETAIL" // RETAIL, WHOLESALE
)

@Entity(
    tableName = "product_images",
    indices = [Index(value = ["productId"]), Index(value = ["sha256Hash"])]
)
data class ProductImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val variantKey: String = "",
    val colorTag: String = "",
    val localPath: String,
    val thumbPath: String = "",
    val sourceUrl: String = "",
    val sha256Hash: String = "",
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "inventory_locations",
    indices = [Index(value = ["code"], unique = true)]
)
data class InventoryLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val locationType: String = "STORE", // STORE, WAREHOUSE, RACK, BIN
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "inventory_movements",
    indices = [
        Index(value = ["movementKey"], unique = true),
        Index(value = ["productId"]),
        Index(value = ["timestamp"]),
        Index(value = ["sourceBatchId"])
    ]
)
data class InventoryMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val movementKey: String,
    val productId: Long,
    val variantId: Long? = null,
    val sizeLabel: String = "Standard",
    val movementType: String, // OPENING_STOCK, ADJUSTMENT, PURCHASE_RECEIPT, SALE, RETURN, EXCHANGE, COUNT_RECONCILE
    val qtyDelta: Int,
    val beforeQty: Int,
    val afterQty: Int,
    val unitCost: Double = 0.0,
    val location: String = "",
    val batchNumber: String = "",
    val serialNumbersCsv: String = "",
    val sourceRoute: String = "MANUAL",
    val sourceBatchId: Long? = null,
    val sourceRefId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "import_batches",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["fileHash"]),
        Index(value = ["createdAt"])
    ]
)
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val idempotencyKey: String,
    val sourceRoute: String, // SPREADSHEET, IMAGE_ZIP, BARCODE, BULK_MANUAL, STOCK_COUNT, AI_DOCUMENT
    val fileName: String = "",
    val fileHash: String = "",
    val status: String = "STAGED", // STAGED, PROCESSING, PARTIAL, COMPLETED, FAILED, CANCELLED
    val totalItems: Int = 0,
    val validItems: Int = 0,
    val invalidItems: Int = 0,
    val duplicateItems: Int = 0,
    val processedItems: Int = 0,
    val lastCheckpointIndex: Int = 0,
    val mappingJson: String = "",
    val summaryJson: String = "",
    val errorReportPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "staged_import_items",
    indices = [
        Index(value = ["batchId"]),
        Index(value = ["approvalStatus"]),
        Index(value = ["barcode"]),
        Index(value = ["sku"])
    ]
)
data class StagedImportItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val rowNumber: Int = 0,
    val sourceDocPage: String = "",
    val actionType: String = "CREATE_PRODUCT", // CREATE_PRODUCT, UPDATE_PRODUCT, OPENING_STOCK, SALE_DRAFT, RETURN_DRAFT, PURCHASE_REQUEST, PURCHASE_ORDER, GOODS_RECEIVED, STOCK_COUNT, STOCK_ADJUSTMENT, EXPENSE, CUSTOMER_CREDIT, VENDOR_PAYMENT, ATTACH_IMAGE
    val resolutionMode: String = "CREATE_NEW", // CREATE_NEW, UPDATE_EXISTING, MERGE_STOCK, SKIP
    val matchedProductId: Long? = null,
    val matchedVariantId: Long? = null,
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
    val validationErrors: String = "",
    val isDuplicate: Boolean = false,
    val approvalStatus: String = "PENDING", // PENDING, APPROVED, REJECTED, COMMITTED, FAILED
    val committedRef: String = "",
    val notes: String = ""
)

@Entity(
    tableName = "stock_count_sessions",
    indices = [Index(value = ["status"]), Index(value = ["createdAt"])]
)
data class StockCountSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val location: String = "",
    val categoryFilter: String = "",
    val status: String = "OPEN", // OPEN, REVIEW, COMMITTED, CANCELLED
    val importBatchId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val committedAt: Long? = null
)

@Entity(
    tableName = "stock_count_lines",
    indices = [Index(value = ["sessionId"]), Index(value = ["productId"]), Index(value = ["barcode"])]
)
data class StockCountLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val productId: Long,
    val variantId: Long? = null,
    val skuCode: String,
    val barcode: String = "",
    val productName: String,
    val variantLabel: String = "Standard",
    val expectedQty: Int,
    val countedQty: Int,
    val variance: Int,
    val approved: Boolean = true,
    val notes: String = ""
)

@Entity(
    tableName = "purchase_orders",
    indices = [Index(value = ["poNumber"], unique = true), Index(value = ["vendorId"]), Index(value = ["status"])]
)
data class PurchaseOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val poNumber: String,
    val vendorId: Long? = null,
    val supplierName: String = "",
    val status: String = "REQUESTED", // REQUESTED, ORDERED, RECEIVED, CANCELLED
    val itemsJson: String,
    val estimatedTotal: Double = 0.0,
    val sourceBatchId: Long? = null,
    val sourceDocumentRef: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val receivedAt: Long? = null
)

// ==================== Wholesale & Custom BOM Entities (v9) ====================

@Entity(
    tableName = "raw_materials",
    indices = [Index(value = ["category"])]
)
data class RawMaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "FILLING", // FILLING, FABRIC, STITCHING, TRIM, PACKING, OTHER
    val unit: String = "kg", // kg, meter, pcs, roll, sheet
    val unitCost: Double = 0.0,
    val rollWidthInches: Double = 0.0,
    val trackStock: Boolean = false,
    val currentStock: Double = 0.0,
    val reorderLevel: Double = 0.0,
    val supplierName: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bom_templates")
data class BomTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "Bedding",
    val calcMode: String = "MATTRESS_VOLUME", // MATTRESS_VOLUME, AREA_COVER, FIXED_UNIT
    val refLengthIn: Double = 72.0,
    val refWidthIn: Double = 36.0,
    val refThicknessIn: Double = 4.0,
    val defaultDensity: Double = 1.0,
    val defaultMarginPct: Double = 20.0,
    val defaultStitchCost: Double = 120.0,
    val wastagePct: Double = 5.0,
    val notes: String = "",
    val isActive: Boolean = true
)

@Entity(
    tableName = "bom_material_rules",
    indices = [Index(value = ["templateId"])]
)
data class BomMaterialRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val rawMaterialId: Long? = null,
    val materialNameSnapshot: String,
    val category: String = "FILLING",
    val unit: String = "kg",
    val ruleType: String = "VOLUME_WEIGHT", // VOLUME_WEIGHT, SURFACE_FABRIC_METERS, PERIMETER_TRIM_METERS, FIXED_PER_PIECE
    val refQtyPerPiece: Double = 1.0,
    val densityFactor: Double = 1.0,
    val seamAllowanceIn: Double = 2.0,
    val rollWidthInches: Double = 60.0,
    val wastagePct: Double = 5.0,
    val unitRateOverride: Double? = null
)

@Entity(
    tableName = "wholesale_quotes",
    indices = [Index(value = ["quoteNumber"], unique = true), Index(value = ["status"])]
)
data class WholesaleQuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quoteNumber: String,
    val buyerName: String,
    val buyerPhone: String = "",
    val buyerCompany: String = "",
    val status: String = "DRAFT", // DRAFT, SENT, APPROVED, CONVERTED, REJECTED
    val templateId: Long? = null,
    val title: String,
    val specSummary: String = "",
    val lengthIn: Double = 72.0,
    val widthIn: Double = 36.0,
    val thicknessIn: Double = 4.0,
    val densityFactor: Double = 1.0,
    val quantity: Int = 1,
    val stitchChargePerPc: Double = 0.0,
    val transportCost: Double = 0.0,
    val discountPercent: Double = 0.0,
    val gstPercent: Double = 0.0,
    val marginPercent: Double = 20.0,
    val unitManufacturingCost: Double = 0.0,
    val unitQuotedPrice: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalQuoteAmount: Double = 0.0,
    val gstAmount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val validUntil: String = "",
    val linkedPoId: Long? = null,
    val linkedSaleId: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "wholesale_quote_lines",
    indices = [Index(value = ["quoteId"])]
)
data class WholesaleQuoteLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quoteId: Long,
    val rawMaterialId: Long? = null,
    val itemName: String,
    val category: String = "FILLING",
    val unit: String = "kg",
    val qtyPerPiece: Double = 0.0,
    val totalQty: Double = 0.0,
    val unitRate: Double = 0.0,
    val wastagePct: Double = 0.0,
    val lineTotal: Double = 0.0,
    val isOverridden: Boolean = false
)
