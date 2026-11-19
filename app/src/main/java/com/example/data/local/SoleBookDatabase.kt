package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductEntity::class,
        ProductVariantEntity::class,
        ProductFingerprintEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        ReturnEntity::class,
        PurchaseEntity::class,
        VendorEntity::class,
        VendorPaymentEntity::class,
        ExpenseEntity::class,
        CustomerEntity::class,
        SegmentEntity::class,
        MessageTemplateEntity::class,
        CampaignLogEntity::class,
        NoticeStateEntity::class,
        ShopSettingsEntity::class,
        ProductImageEntity::class,
        InventoryLocationEntity::class,
        InventoryMovementEntity::class,
        ImportBatchEntity::class,
        StagedImportItemEntity::class,
        StockCountSessionEntity::class,
        StockCountLineEntity::class,
        PurchaseOrderEntity::class,
        RawMaterialEntity::class,
        BomTemplateEntity::class,
        BomMaterialRuleEntity::class,
        WholesaleQuoteEntity::class,
        WholesaleQuoteLineEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class SoleBookDatabase : RoomDatabase() {
    abstract fun soleBookDao(): SoleBookDao
    abstract fun onboardingDao(): OnboardingDao
    abstract fun wholesaleDao(): WholesaleDao

    companion object {
        @Volatile
        private var INSTANCE: SoleBookDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN colorTag TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN styleTag TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN rackLocation TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS product_fingerprints (
                        productId INTEGER NOT NULL PRIMARY KEY,
                        signatureCsv TEXT NOT NULL,
                        dominantColor TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notice_states (
                        noticeKey TEXT NOT NULL PRIMARY KEY,
                        lastNotifiedAt INTEGER NOT NULL,
                        snoozedUntil INTEGER NOT NULL,
                        dismissedUntil INTEGER NOT NULL,
                        lastSignatureHash TEXT NOT NULL,
                        isRead INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE settings ADD COLUMN alertsEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN instantSaleAlertsEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN stockAlertsEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN demandAlertsEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN marginAlertsEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN alertIntervalHours INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE settings ADD COLUMN maxAlertsPerDay INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE settings ADD COLUMN quietStartHour INTEGER NOT NULL DEFAULT 21")
                db.execSQL("ALTER TABLE settings ADD COLUMN quietEndHour INTEGER NOT NULL DEFAULT 8")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN isRepeatable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN vendorId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN lotCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE purchases ADD COLUMN vendorId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE purchases ADD COLUMN lotType TEXT NOT NULL DEFAULT 'FRESH_LOT'")
                db.execSQL("ALTER TABLE purchases ADD COLUMN paymentStatus TEXT NOT NULL DEFAULT 'PAID'")
                db.execSQL("ALTER TABLE purchases ADD COLUMN paidAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE purchases ADD COLUMN dueAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vendors (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        whatsapp TEXT NOT NULL,
                        cityMarket TEXT NOT NULL,
                        specialtiesCsv TEXT NOT NULL,
                        creditBalance REAL NOT NULL,
                        notes TEXT NOT NULL,
                        lastPurchaseAt INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vendors_name ON vendors(name)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vendor_payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vendorId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        paymentMode TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        notes TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_payments_vendorId ON vendor_payments(vendorId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_payments_timestamp ON vendor_payments(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_vendorId ON purchases(vendorId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN shopLogoUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN shortHeaderName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN headerBrandMode TEXT NOT NULL DEFAULT 'LOGO_AND_NAME'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN email TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN unitLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN batchOrSerial TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN expiryOrWarranty TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN businessCategory TEXT NOT NULL DEFAULT 'FOOTWEAR'")
                db.execSQL("ALTER TABLE settings ADD COLUMN customBusinessType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN businessChannel TEXT NOT NULL DEFAULT 'PHYSICAL'")
                db.execSQL("ALTER TABLE settings ADD COLUMN hasCompletedOnboarding INTEGER NOT NULL DEFAULT 0")
                // Existing shops upgrading from v6 skip onboarding cleanly
                db.execSQL(
                    """
                    UPDATE settings SET hasCompletedOnboarding = 1
                    WHERE hasCompletedWalkthrough = 1
                       OR isDemoLoaded = 1
                       OR EXISTS (SELECT 1 FROM products)
                       OR EXISTS (SELECT 1 FROM sales)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE products SET updatedAt = createdAt WHERE updatedAt = 0")
                db.execSQL("ALTER TABLE products ADD COLUMN barcode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN trackBatchExpiry INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN trackSerialNumbers INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN secondaryUnitLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN unitConversionFactor REAL NOT NULL DEFAULT 1.0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_barcode ON products(barcode)")

                db.execSQL("ALTER TABLE product_variants ADD COLUMN variantType TEXT NOT NULL DEFAULT 'Variant'")
                db.execSQL("ALTER TABLE product_variants ADD COLUMN variantSku TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE product_variants ADD COLUMN barcode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE product_variants ADD COLUMN colorName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE product_variants ADD COLUMN packSizeLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE product_variants ADD COLUMN modelName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE product_variants ADD COLUMN costOverride REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE product_variants ADD COLUMN priceOverride REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE product_variants ADD COLUMN location TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE product_variants ADD COLUMN batchNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE product_variants ADD COLUMN expiryDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE product_variants ADD COLUMN serialNumbersCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_variants_barcode ON product_variants(barcode)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS product_images (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        productId INTEGER NOT NULL,
                        variantKey TEXT NOT NULL,
                        colorTag TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        thumbPath TEXT NOT NULL,
                        sourceUrl TEXT NOT NULL,
                        sha256Hash TEXT NOT NULL,
                        isPrimary INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_images_productId ON product_images(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_images_sha256Hash ON product_images(sha256Hash)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_locations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        code TEXT NOT NULL,
                        name TEXT NOT NULL,
                        locationType TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_locations_code ON inventory_locations(code)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_movements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        movementKey TEXT NOT NULL,
                        productId INTEGER NOT NULL,
                        variantId INTEGER,
                        sizeLabel TEXT NOT NULL,
                        movementType TEXT NOT NULL,
                        qtyDelta INTEGER NOT NULL,
                        beforeQty INTEGER NOT NULL,
                        afterQty INTEGER NOT NULL,
                        unitCost REAL NOT NULL,
                        location TEXT NOT NULL,
                        batchNumber TEXT NOT NULL,
                        serialNumbersCsv TEXT NOT NULL,
                        sourceRoute TEXT NOT NULL,
                        sourceBatchId INTEGER,
                        sourceRefId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        notes TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_movements_movementKey ON inventory_movements(movementKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_movements_productId ON inventory_movements(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_movements_timestamp ON inventory_movements(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_movements_sourceBatchId ON inventory_movements(sourceBatchId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS import_batches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        idempotencyKey TEXT NOT NULL,
                        sourceRoute TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        fileHash TEXT NOT NULL,
                        status TEXT NOT NULL,
                        totalItems INTEGER NOT NULL,
                        validItems INTEGER NOT NULL,
                        invalidItems INTEGER NOT NULL,
                        duplicateItems INTEGER NOT NULL,
                        processedItems INTEGER NOT NULL,
                        lastCheckpointIndex INTEGER NOT NULL,
                        mappingJson TEXT NOT NULL,
                        summaryJson TEXT NOT NULL,
                        errorReportPath TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_import_batches_idempotencyKey ON import_batches(idempotencyKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_import_batches_fileHash ON import_batches(fileHash)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_import_batches_createdAt ON import_batches(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS staged_import_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        batchId INTEGER NOT NULL,
                        rowNumber INTEGER NOT NULL,
                        sourceDocPage TEXT NOT NULL,
                        actionType TEXT NOT NULL,
                        resolutionMode TEXT NOT NULL,
                        matchedProductId INTEGER,
                        matchedVariantId INTEGER,
                        sku TEXT NOT NULL,
                        barcode TEXT NOT NULL,
                        productName TEXT NOT NULL,
                        category TEXT NOT NULL,
                        brand TEXT NOT NULL,
                        unitLabel TEXT NOT NULL,
                        variantLabel TEXT NOT NULL,
                        variantType TEXT NOT NULL,
                        colorName TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        costPrice REAL,
                        sellingPrice REAL,
                        mrp REAL,
                        amount REAL NOT NULL,
                        reorderLevel INTEGER,
                        partyName TEXT NOT NULL,
                        partyPhone TEXT NOT NULL,
                        location TEXT NOT NULL,
                        batchNumber TEXT NOT NULL,
                        expiryDate TEXT NOT NULL,
                        serialNumbersCsv TEXT NOT NULL,
                        imageRef TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        rawPayloadJson TEXT NOT NULL,
                        validationErrors TEXT NOT NULL,
                        isDuplicate INTEGER NOT NULL,
                        approvalStatus TEXT NOT NULL,
                        committedRef TEXT NOT NULL,
                        notes TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_staged_import_items_batchId ON staged_import_items(batchId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_staged_import_items_approvalStatus ON staged_import_items(approvalStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_staged_import_items_barcode ON staged_import_items(barcode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_staged_import_items_sku ON staged_import_items(sku)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stock_count_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        location TEXT NOT NULL,
                        categoryFilter TEXT NOT NULL,
                        status TEXT NOT NULL,
                        importBatchId INTEGER,
                        createdAt INTEGER NOT NULL,
                        committedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_count_sessions_status ON stock_count_sessions(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_count_sessions_createdAt ON stock_count_sessions(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stock_count_lines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        productId INTEGER NOT NULL,
                        variantId INTEGER,
                        skuCode TEXT NOT NULL,
                        barcode TEXT NOT NULL,
                        productName TEXT NOT NULL,
                        variantLabel TEXT NOT NULL,
                        expectedQty INTEGER NOT NULL,
                        countedQty INTEGER NOT NULL,
                        variance INTEGER NOT NULL,
                        approved INTEGER NOT NULL,
                        notes TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_count_lines_sessionId ON stock_count_lines(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_count_lines_productId ON stock_count_lines(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_count_lines_barcode ON stock_count_lines(barcode)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS purchase_orders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        poNumber TEXT NOT NULL,
                        vendorId INTEGER,
                        supplierName TEXT NOT NULL,
                        status TEXT NOT NULL,
                        itemsJson TEXT NOT NULL,
                        estimatedTotal REAL NOT NULL,
                        sourceBatchId INTEGER,
                        sourceDocumentRef TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        receivedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_orders_poNumber ON purchase_orders(poNumber)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_vendorId ON purchase_orders(vendorId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_status ON purchase_orders(status)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sales ADD COLUMN saleChannel TEXT NOT NULL DEFAULT 'RETAIL'")
                db.execSQL("ALTER TABLE settings ADD COLUMN businessOperationMode TEXT NOT NULL DEFAULT 'RETAIL'")
                db.execSQL("ALTER TABLE settings ADD COLUMN activeWorkspaceMode TEXT NOT NULL DEFAULT 'RETAIL'")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS raw_materials (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        unitCost REAL NOT NULL,
                        rollWidthInches REAL NOT NULL,
                        trackStock INTEGER NOT NULL,
                        currentStock REAL NOT NULL,
                        reorderLevel REAL NOT NULL,
                        supplierName TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_raw_materials_category ON raw_materials(category)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bom_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        calcMode TEXT NOT NULL,
                        refLengthIn REAL NOT NULL,
                        refWidthIn REAL NOT NULL,
                        refThicknessIn REAL NOT NULL,
                        defaultDensity REAL NOT NULL,
                        defaultMarginPct REAL NOT NULL,
                        defaultStitchCost REAL NOT NULL,
                        wastagePct REAL NOT NULL,
                        notes TEXT NOT NULL,
                        isActive INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bom_material_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        templateId INTEGER NOT NULL,
                        rawMaterialId INTEGER,
                        materialNameSnapshot TEXT NOT NULL,
                        category TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        ruleType TEXT NOT NULL,
                        refQtyPerPiece REAL NOT NULL,
                        densityFactor REAL NOT NULL,
                        seamAllowanceIn REAL NOT NULL,
                        rollWidthInches REAL NOT NULL,
                        wastagePct REAL NOT NULL,
                        unitRateOverride REAL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bom_material_rules_templateId ON bom_material_rules(templateId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS wholesale_quotes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        quoteNumber TEXT NOT NULL,
                        buyerName TEXT NOT NULL,
                        buyerPhone TEXT NOT NULL,
                        buyerCompany TEXT NOT NULL,
                        status TEXT NOT NULL,
                        templateId INTEGER,
                        title TEXT NOT NULL,
                        specSummary TEXT NOT NULL,
                        lengthIn REAL NOT NULL,
                        widthIn REAL NOT NULL,
                        thicknessIn REAL NOT NULL,
                        densityFactor REAL NOT NULL,
                        quantity INTEGER NOT NULL,
                        stitchChargePerPc REAL NOT NULL,
                        transportCost REAL NOT NULL,
                        discountPercent REAL NOT NULL,
                        gstPercent REAL NOT NULL,
                        marginPercent REAL NOT NULL,
                        unitManufacturingCost REAL NOT NULL,
                        unitQuotedPrice REAL NOT NULL,
                        totalCost REAL NOT NULL,
                        totalQuoteAmount REAL NOT NULL,
                        gstAmount REAL NOT NULL,
                        grandTotal REAL NOT NULL,
                        validUntil TEXT NOT NULL,
                        linkedPoId INTEGER,
                        linkedSaleId INTEGER,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_wholesale_quotes_quoteNumber ON wholesale_quotes(quoteNumber)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_wholesale_quotes_status ON wholesale_quotes(status)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS wholesale_quote_lines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        quoteId INTEGER NOT NULL,
                        rawMaterialId INTEGER,
                        itemName TEXT NOT NULL,
                        category TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        qtyPerPiece REAL NOT NULL,
                        totalQty REAL NOT NULL,
                        unitRate REAL NOT NULL,
                        wastagePct REAL NOT NULL,
                        lineTotal REAL NOT NULL,
                        isOverridden INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_wholesale_quote_lines_quoteId ON wholesale_quote_lines(quoteId)")
            }
        }

        fun getInstance(context: Context): SoleBookDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SoleBookDatabase::class.java,
                    "solebook.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
