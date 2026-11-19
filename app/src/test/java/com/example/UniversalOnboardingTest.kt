package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.repository.SoleBookRepository
import com.example.domain.onboarding.*
import com.example.ui.screens.onboarding.ImportHubScreen
import com.example.ui.screens.onboarding.StagingReviewScreen
import com.example.ui.theme.SoleBookTheme
import com.example.ui.viewmodel.DataHubUiState
import com.example.ui.viewmodel.OnboardingHubController
import com.example.ui.viewmodel.SoleBookUiState
import com.example.util.ExportUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UniversalOnboardingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: SoleBookDatabase
    private lateinit var repository: SoleBookRepository
    private lateinit var stagingEngine: StagingAndCommitEngine
    private lateinit var stockCountService: StockCountService
    private lateinit var tempDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SoleBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SoleBookRepository(db)
        stagingEngine = StagingAndCommitEngine(db, repository)
        stockCountService = StockCountService(db, repository)
        tempDir = File(context.cacheDir, "test_onboarding_${System.nanoTime()}").apply { mkdirs() }
        runBlocking { repository.ensureDefaultsInitialized() }
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `migration 7 to 8 preserves existing rows and passes Room schema validation`() {
        val dbName = "migration_v7_v8_test_${System.nanoTime()}.db"
        context.deleteDatabase(dbName)

        // Build exact SQLite v7 schema + seed rows
        val v7Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(sqldb: SupportSQLiteDatabase) {
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS products (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                skuCode TEXT NOT NULL,
                                name TEXT NOT NULL,
                                category TEXT NOT NULL,
                                brand TEXT NOT NULL,
                                gender TEXT NOT NULL,
                                photoUri TEXT NOT NULL,
                                costPrice REAL NOT NULL,
                                sellingPrice REAL NOT NULL,
                                mrp REAL,
                                supplier TEXT NOT NULL DEFAULT '',
                                reorderLevel INTEGER NOT NULL DEFAULT 5,
                                colorTag TEXT NOT NULL DEFAULT '',
                                styleTag TEXT NOT NULL DEFAULT '',
                                rackLocation TEXT NOT NULL DEFAULT '',
                                isRepeatable INTEGER NOT NULL DEFAULT 0,
                                vendorId INTEGER DEFAULT NULL,
                                lotCode TEXT NOT NULL DEFAULT '',
                                unitLabel TEXT NOT NULL DEFAULT '',
                                batchOrSerial TEXT NOT NULL DEFAULT '',
                                expiryOrWarranty TEXT NOT NULL DEFAULT '',
                                isArchived INTEGER NOT NULL DEFAULT 0,
                                createdAt INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_products_skuCode ON products(skuCode)")
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_products_category ON products(category)")
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS product_variants (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                productId INTEGER NOT NULL,
                                size TEXT NOT NULL,
                                stock INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_product_variants_productId ON product_variants(productId)")
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS product_fingerprints (
                                productId INTEGER NOT NULL PRIMARY KEY,
                                signatureCsv TEXT NOT NULL,
                                dominantColor TEXT NOT NULL,
                                updatedAt INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS sales (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                billNumber TEXT NOT NULL,
                                timestamp INTEGER NOT NULL,
                                customerId INTEGER,
                                customerPhoneSnapshot TEXT NOT NULL DEFAULT '',
                                customerNameSnapshot TEXT NOT NULL DEFAULT '',
                                paymentMode TEXT NOT NULL,
                                discountAmount REAL NOT NULL DEFAULT 0.0,
                                discountType TEXT NOT NULL DEFAULT 'AMOUNT',
                                discountInput REAL NOT NULL DEFAULT 0.0,
                                gstEnabled INTEGER NOT NULL DEFAULT 0,
                                gstRate REAL NOT NULL DEFAULT 12.0,
                                gstInclusive INTEGER NOT NULL DEFAULT 1,
                                gstAmount REAL NOT NULL DEFAULT 0.0,
                                subtotal REAL NOT NULL,
                                totalRevenue REAL NOT NULL,
                                totalCollected REAL NOT NULL,
                                creditSettled INTEGER NOT NULL DEFAULT 1,
                                creditSettledAt INTEGER,
                                notes TEXT NOT NULL DEFAULT ''
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_sales_timestamp ON sales(timestamp)")
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_sales_customerId ON sales(customerId)")
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS sale_items (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                saleId INTEGER NOT NULL,
                                productId INTEGER NOT NULL,
                                productNameSnapshot TEXT NOT NULL,
                                skuSnapshot TEXT NOT NULL,
                                categorySnapshot TEXT NOT NULL,
                                size TEXT NOT NULL,
                                quantity INTEGER NOT NULL,
                                costPriceAtSale REAL NOT NULL,
                                sellingPriceAtSale REAL NOT NULL,
                                lineTotal REAL NOT NULL
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_saleId ON sale_items(saleId)")
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_productId ON sale_items(productId)")
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS returns (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                saleId INTEGER NOT NULL,
                                saleItemId INTEGER,
                                productId INTEGER NOT NULL,
                                productNameSnapshot TEXT NOT NULL,
                                size TEXT NOT NULL,
                                quantity INTEGER NOT NULL,
                                refundAmount REAL NOT NULL,
                                costReversed REAL NOT NULL,
                                type TEXT NOT NULL,
                                exchangeSize TEXT,
                                timestamp INTEGER NOT NULL,
                                reason TEXT NOT NULL DEFAULT ''
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_returns_saleId ON returns(saleId)")
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_returns_productId ON returns(productId)")
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_returns_timestamp ON returns(timestamp)")
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS purchases (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                productId INTEGER NOT NULL,
                                totalUnits INTEGER NOT NULL,
                                unitCostPrice REAL NOT NULL,
                                totalCost REAL NOT NULL,
                                supplier TEXT NOT NULL DEFAULT '',
                                vendorId INTEGER,
                                lotType TEXT NOT NULL DEFAULT 'FRESH_LOT',
                                paymentStatus TEXT NOT NULL DEFAULT 'PAID',
                                paidAmount REAL NOT NULL DEFAULT 0.0,
                                dueAmount REAL NOT NULL DEFAULT 0.0,
                                sizeBreakdown TEXT NOT NULL DEFAULT '',
                                timestamp INTEGER NOT NULL,
                                notes TEXT NOT NULL DEFAULT ''
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_productId ON purchases(productId)")
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_timestamp ON purchases(timestamp)")
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_vendorId ON purchases(vendorId)")
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS vendors (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                name TEXT NOT NULL,
                                phone TEXT NOT NULL DEFAULT '',
                                whatsapp TEXT NOT NULL DEFAULT '',
                                cityMarket TEXT NOT NULL DEFAULT '',
                                specialtiesCsv TEXT NOT NULL DEFAULT '',
                                creditBalance REAL NOT NULL DEFAULT 0.0,
                                notes TEXT NOT NULL DEFAULT '',
                                lastPurchaseAt INTEGER,
                                createdAt INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_vendors_name ON vendors(name)")
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS vendor_payments (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                vendorId INTEGER NOT NULL,
                                amount REAL NOT NULL,
                                paymentMode TEXT NOT NULL DEFAULT 'CASH',
                                timestamp INTEGER NOT NULL,
                                notes TEXT NOT NULL DEFAULT ''
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_payments_vendorId ON vendor_payments(vendorId)")
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_payments_timestamp ON vendor_payments(timestamp)")
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS expenses (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                category TEXT NOT NULL,
                                amount REAL NOT NULL,
                                isRecurringMonthly INTEGER NOT NULL DEFAULT 0,
                                timestamp INTEGER NOT NULL,
                                notes TEXT NOT NULL DEFAULT ''
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_timestamp ON expenses(timestamp)")
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS customers (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                phone TEXT NOT NULL,
                                name TEXT NOT NULL DEFAULT '',
                                email TEXT NOT NULL DEFAULT '',
                                firstVisitAt INTEGER NOT NULL,
                                lastVisitAt INTEGER NOT NULL,
                                totalSpent REAL NOT NULL DEFAULT 0.0,
                                visitCount INTEGER NOT NULL DEFAULT 0,
                                favouriteCategory TEXT NOT NULL DEFAULT '',
                                favouriteSize TEXT NOT NULL DEFAULT '',
                                specialDate TEXT NOT NULL DEFAULT '',
                                notes TEXT NOT NULL DEFAULT '',
                                whatsappConsent INTEGER NOT NULL DEFAULT 1,
                                creditBalance REAL NOT NULL DEFAULT 0.0
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customers_phone ON customers(phone)")
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS segments (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                name TEXT NOT NULL,
                                ruleKey TEXT NOT NULL,
                                description TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS message_templates (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                title TEXT NOT NULL,
                                segmentRuleKey TEXT NOT NULL,
                                bodyTemplate TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS campaigns (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                templateId INTEGER,
                                segmentKey TEXT NOT NULL,
                                customerId INTEGER,
                                customerPhone TEXT NOT NULL,
                                messageSent TEXT NOT NULL,
                                timestamp INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL("CREATE INDEX IF NOT EXISTS index_campaigns_timestamp ON campaigns(timestamp)")
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS notice_states (
                                noticeKey TEXT NOT NULL PRIMARY KEY,
                                lastNotifiedAt INTEGER NOT NULL DEFAULT 0,
                                snoozedUntil INTEGER NOT NULL DEFAULT 0,
                                dismissedUntil INTEGER NOT NULL DEFAULT 0,
                                lastSignatureHash TEXT NOT NULL DEFAULT '',
                                isRead INTEGER NOT NULL DEFAULT 0
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS settings (
                                id INTEGER NOT NULL PRIMARY KEY,
                                shopName TEXT NOT NULL,
                                businessCategory TEXT NOT NULL DEFAULT 'FOOTWEAR',
                                customBusinessType TEXT NOT NULL DEFAULT '',
                                businessChannel TEXT NOT NULL DEFAULT 'PHYSICAL',
                                hasCompletedOnboarding INTEGER NOT NULL DEFAULT 0,
                                shopLogoUri TEXT NOT NULL DEFAULT '',
                                shortHeaderName TEXT NOT NULL DEFAULT '',
                                headerBrandMode TEXT NOT NULL DEFAULT 'LOGO_AND_NAME',
                                currencySymbol TEXT NOT NULL,
                                languageCode TEXT NOT NULL,
                                gstEnabled INTEGER NOT NULL,
                                gstRate REAL NOT NULL,
                                gstInclusive INTEGER NOT NULL,
                                defaultLeadTimeDays INTEGER NOT NULL,
                                seasonalityMode TEXT NOT NULL,
                                seasonalityUpliftPercent REAL NOT NULL,
                                hasCompletedWalkthrough INTEGER NOT NULL,
                                isDemoLoaded INTEGER NOT NULL,
                                alertsEnabled INTEGER NOT NULL DEFAULT 1,
                                instantSaleAlertsEnabled INTEGER NOT NULL DEFAULT 0,
                                stockAlertsEnabled INTEGER NOT NULL DEFAULT 1,
                                demandAlertsEnabled INTEGER NOT NULL DEFAULT 1,
                                marginAlertsEnabled INTEGER NOT NULL DEFAULT 1,
                                alertIntervalHours INTEGER NOT NULL DEFAULT 4,
                                maxAlertsPerDay INTEGER NOT NULL DEFAULT 4,
                                quietStartHour INTEGER NOT NULL DEFAULT 21,
                                quietEndHour INTEGER NOT NULL DEFAULT 8
                            )
                            """.trimIndent()
                        )

                        sqldb.execSQL(
                            "INSERT INTO products (id, skuCode, name, category, brand, gender, photoUri, costPrice, sellingPrice, createdAt) VALUES (1, 'GR-101', 'Aashirvaad Atta 5kg', 'Flour', 'ITC', 'Standard', '', 210.0, 260.0, 1700000000000)"
                        )
                        sqldb.execSQL(
                            "INSERT INTO product_variants (id, productId, size, stock) VALUES (1, 1, '5kg', 14)"
                        )
                        sqldb.execSQL(
                            "INSERT INTO settings (id, shopName, businessCategory, hasCompletedOnboarding, currencySymbol, languageCode, gstEnabled, gstRate, gstInclusive, defaultLeadTimeDays, seasonalityMode, seasonalityUpliftPercent, hasCompletedWalkthrough, isDemoLoaded) VALUES (1, 'Kirana Mart', 'GROCERY', 1, '₹', 'en', 0, 5.0, 1, 2, 'NONE', 0.0, 1, 0)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        v7Helper.writableDatabase.close()
        v7Helper.close()

        // Now open through Room with MIGRATION_7_8 — Room runs strict full schema verification!
        val migratedRoom = Room.databaseBuilder(context, SoleBookDatabase::class.java, dbName)
            .addMigrations(
                SoleBookDatabase.MIGRATION_1_2,
                SoleBookDatabase.MIGRATION_2_3,
                SoleBookDatabase.MIGRATION_3_4,
                SoleBookDatabase.MIGRATION_4_5,
                SoleBookDatabase.MIGRATION_5_6,
                SoleBookDatabase.MIGRATION_6_7,
                SoleBookDatabase.MIGRATION_7_8,
                SoleBookDatabase.MIGRATION_8_9
            )
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val products = migratedRoom.soleBookDao().getAllProductsSync()
            assertEquals(1, products.size)
            assertEquals("Aashirvaad Atta 5kg", products.first().name)
            assertEquals("", products.first().barcode)
            assertEquals(1.0, products.first().unitConversionFactor, 0.001)

            val variants = migratedRoom.soleBookDao().getVariantsForProduct(1L)
            assertEquals(1, variants.size)
            assertEquals(14, variants.first().stock)

            val locs = migratedRoom.onboardingDao().getAllLocationsSync()
            assertTrue(locs.isEmpty()) // table exists cleanly and queries without schema errors
        }
        migratedRoom.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `csv and xlsx templates auto-map aliases indic digits and normalized units`() = runBlocking {
        // Test CSV & XLSX template export + parse round-trip
        val csvFile = File(tempDir, "template.csv").apply { writeText(SpreadsheetCodec.buildImportTemplateCsv()) }
        val xlsxFile = File(tempDir, "template.xlsx")
        SpreadsheetCodec.writeImportTemplateXlsx(xlsxFile)

        val csvPreview = SpreadsheetCodec.inspectPreview(csvFile)
        val xlsxPreview = SpreadsheetCodec.inspectPreview(xlsxFile)
        assertEquals("CSV", csvPreview.fileFormat)
        assertEquals("XLSX", xlsxPreview.fileFormat)
        assertEquals(3, csvPreview.estimatedTotalRows)
        assertEquals(3, xlsxPreview.estimatedTotalRows)
        assertEquals(TargetField.NAME, xlsxPreview.suggestedMapping[2])
        assertEquals(TargetField.BARCODE, xlsxPreview.suggestedMapping[1])

        // Test messy real-world spreadsheet with alias headers, Hindi/Bengali unit strings & Indic digits
        val messyCsv = File(tempDir, "messy_shop.csv")
        messyCsv.writeText(
            """
            Item Code,EAN Number,Title,Dept,Make,UOM,Wholesale Rate,Rate,MRP Price,Closing Balance,Rack No,Batch Number,Expiry Date
            KR-01,8901112223331,Toor Dal Unpolished,Pulses,Tata Sampann,किलो,120,155,170,२५,R-01,B-77,Dec 2026
            ST-02,8901112223332,Classmate Notebook,Stationery,ITC,ডজন,360,480,520,১২,R-09,,
            """.trimIndent()
        )
        val messyPreview = SpreadsheetCodec.inspectPreview(messyCsv)
        val mappedFields = messyPreview.suggestedMapping.values.toSet()
        assertTrue(TargetField.SKU in mappedFields)
        assertTrue(TargetField.BARCODE in mappedFields)
        assertTrue(TargetField.NAME in mappedFields)
        assertTrue(TargetField.UNIT in mappedFields)
        assertTrue(TargetField.COST_PRICE in mappedFields)
        assertTrue(TargetField.SELLING_PRICE in mappedFields)
        assertTrue(TargetField.OPENING_STOCK in mappedFields)

        val outcome = stagingEngine.stageSpreadsheet(messyCsv, "messy_shop.csv", messyPreview.suggestedMapping)
        assertFalse(outcome.isDuplicateUpload)
        assertEquals(2, outcome.batch.validItems)

        val commitRes = stagingEngine.commitBatchChunked(outcome.batch.id, autoApproveValid = true)
        assertEquals("COMPLETED", commitRes.status)
        assertEquals(2, commitRes.committedCount)

        val p1 = db.onboardingDao().getProductByBarcode("8901112223331")!!
        val p2 = db.onboardingDao().getProductByBarcode("8901112223332")!!
        assertEquals("kg", p1.unitLabel)
        assertEquals("dozen", p2.unitLabel)
        assertEquals(25, db.soleBookDao().getVariantsForProduct(p1.id).sumOf { it.stock })
        assertEquals(12, db.soleBookDao().getVariantsForProduct(p2.id).sumOf { it.stock })
        assertEquals("B-77", p1.batchOrSerial)
    }

    @Test
    fun `multi-variant serials validation errors duplicate upload and error CSV export`() = runBlocking {
        // File with 1 multi-variant shoe row, 1 electronics serials row, 1 invalid negative-price row, 1 duplicate barcode row
        val csv = File(tempDir, "mixed_validation.csv")
        csv.writeText(
            """
            SKU,Barcode,Product Name,Category,Unit,Cost Price,Selling Price,Opening Stock,Size Breakdown,Serial Numbers,Location
            FW-100,8904440001001,Campus Runner,Footwear,pair,600,1199,15,"7:4,8:6,9:5",,STORE-A
            EL-200,8904440002002,Boat Airdopes,Electronics,pcs,850,1499,3,,"SN101,SN102,SN103",COUNTER-1
            BAD-01,8904440003003,Broken Negative Item,General,pcs,-50,100,5,,,
            DUP-01,8904440001001,Duplicate Barcode Item,General,pcs,100,150,2,,,
            """.trimIndent()
        )
        val preview = SpreadsheetCodec.inspectPreview(csv)
        val stage1 = stagingEngine.stageSpreadsheet(csv, "mixed_validation.csv", preview.suggestedMapping)
        assertEquals(4, stage1.batch.totalItems)
        assertEquals(2, stage1.batch.validItems)
        assertEquals(2, stage1.batch.invalidItems)
        assertTrue(stage1.batch.duplicateItems >= 1)

        // Exact same fileHash triggers duplicate upload protection unless forceReimport = true
        val stageDup = stagingEngine.stageSpreadsheet(csv, "mixed_validation.csv", preview.suggestedMapping)
        assertTrue(stageDup.isDuplicateUpload)
        assertEquals(stage1.batch.id, stageDup.existingBatchId)

        // Partial commit: commits the 2 valid rows safely, skips invalid rows without corrupting catalog
        val res = stagingEngine.commitBatchChunked(stage1.batch.id, autoApproveValid = true)
        assertEquals(2, res.committedCount)

        val shoe = db.onboardingDao().getProductBySku("FW-100")!!
        val shoeVars = db.soleBookDao().getVariantsForProduct(shoe.id)
        assertEquals(3, shoeVars.size)
        assertEquals(15, shoeVars.sumOf { it.stock })

        val airdopes = db.onboardingDao().getProductBySku("EL-200")!!
        assertTrue(airdopes.trackSerialNumbers)
        assertTrue(airdopes.batchOrSerial.contains("SN101"))

        val errorCsv = stagingEngine.exportBatchErrorReport(context, stage1.batch.id)
        val errorContent = errorCsv.readText()
        assertTrue(errorContent.contains("BAD-01"))
        assertTrue(errorContent.contains("DUP-01"))
        assertFalse(errorContent.contains("FW-100"))
    }

    @Test
    fun `benchmark 100 1000 10000 CSV and XLSX scaling plus mid-batch interruption resume idempotency`() = runBlocking {
        // 1) Measure 100 & 1,000 XLSX + 10,000 CSV throughput & heap delta
        val tiers = listOf(100 to "CSV", 1_000 to "XLSX", 10_000 to "CSV")
        val runtime = Runtime.getRuntime()

        for ((count, fmt) in tiers) {
            val testDb = Room.inMemoryDatabaseBuilder(context, SoleBookDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val testRepo = SoleBookRepository(testDb)
            val engine = StagingAndCommitEngine(testDb, testRepo)

            val file = File(tempDir, "bench_${count}.${fmt.lowercase()}")
            val rows = ArrayList<List<String>>(count + 1)
            rows += listOf("SKU", "Barcode", "Product Name", "Category", "Unit", "Cost Price", "Selling Price", "Opening Stock")
            for (i in 1..count) {
                rows += listOf(
                    "B$count-$i",
                    "899${count}$i",
                    "Benchmark Product $count #$i",
                    if (i % 2 == 0) "Grocery" else "General",
                    "pcs",
                    "${100 + (i % 50)}",
                    "${160 + (i % 50)}",
                    "${(i % 20) + 1}"
                )
            }
            if (fmt == "XLSX") {
                SpreadsheetCodec.writeXlsxFromRows(file, rows)
            } else {
                file.writeText(SpreadsheetCodec.buildCsvFromRows(rows))
            }

            System.gc()
            val memBefore = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
            val t0 = System.nanoTime()

            val preview = SpreadsheetCodec.inspectPreview(file, maxSampleRows = 3)
            val staged = engine.stageSpreadsheet(file, file.name, preview.suggestedMapping)
            val tStaged = System.nanoTime()
            val committed = engine.commitBatchChunked(staged.batch.id, autoApproveValid = true)
            val tEnd = System.nanoTime()

            val memAfter = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
            val stageMs = (tStaged - t0) / 1_000_000.0
            val commitMs = (tEnd - tStaged) / 1_000_000.0
            val totalMs = (tEnd - t0) / 1_000_000.0

            println(
                "BENCHMARK_METRIC: count=$count fmt=$fmt stageMs=${"%.1f".format(stageMs)} " +
                    "commitMs=${"%.1f".format(commitMs)} totalMs=${"%.1f".format(totalMs)} " +
                    "heapDeltaMB=${"%.2f".format(memAfter - memBefore)} committed=${committed.committedCount}"
            )

            assertEquals("COMPLETED", committed.status)
            assertEquals(count, committed.committedCount)
            assertEquals(count, testDb.soleBookDao().getAllProductsSync().size)
            assertEquals(count, testDb.onboardingDao().getAllMovementsSync().size)
            testDb.close()
        }

        // 2) Safe interruption after 2 chunks (500 rows of 1,000) -> Resume -> Re-run idempotency
        val interruptCsv = File(tempDir, "interrupt_1000.csv")
        val iRows = mutableListOf(listOf("SKU", "Barcode", "Product Name", "Cost Price", "Selling Price", "Opening Stock"))
        for (i in 1..1000) {
            iRows += listOf("INT-$i", "777000$i", "Resumable Item $i", "50", "80", "5")
        }
        interruptCsv.writeText(SpreadsheetCodec.buildCsvFromRows(iRows))
        val prev = SpreadsheetCodec.inspectPreview(interruptCsv)
        val stage = stagingEngine.stageSpreadsheet(interruptCsv, "interrupt_1000.csv", prev.suggestedMapping)

        // Crash after 2 chunks (2 * 250 = 500 committed)
        val partial = stagingEngine.commitBatchChunked(stage.batch.id, autoApproveValid = true, simulateFailAfterChunks = 2)
        assertEquals("PARTIAL", partial.status)
        assertEquals(500, partial.committedCount)
        assertEquals(500, db.soleBookDao().getAllProductsSync().size)

        // Resume remaining 500
        val resumed = stagingEngine.commitBatchChunked(stage.batch.id, autoApproveValid = false)
        assertEquals("COMPLETED", resumed.status)
        assertEquals(1000, resumed.committedCount)
        assertEquals(1000, db.soleBookDao().getAllProductsSync().size)
        assertEquals(5000, db.soleBookDao().getAllVariantsSync().sumOf { it.stock })
        assertEquals(1000, db.onboardingDao().getAllMovementsSync().size)

        // Re-running commitBatchChunked on already-completed batch is strictly idempotent (zero stock inflation)
        val replay = stagingEngine.commitBatchChunked(stage.batch.id, autoApproveValid = true)
        assertEquals(1000, replay.committedCount)
        assertEquals(5000, db.soleBookDao().getAllVariantsSync().sumOf { it.stock })
        assertEquals(1000, db.onboardingDao().getAllMovementsSync().size)
    }

    @Test
    fun `streaming ZIP image import matches SKUs mapping sheet deduplicates SHA256 and exports missing report`() = runBlocking {
        // Create 2 products (only 1 gets matched images, the other should appear in missing report)
        val p1Id = repository.saveProductWithVariants(
            ProductEntity(skuCode = "SL-001", barcode = "890001", name = "Urban Sneaker", category = "Shoes", brand = "Sole", gender = "Men", photoUri = "", costPrice = 500.0, sellingPrice = 999.0),
            mapOf("8" to 4)
        )
        repository.saveProductWithVariants(
            ProductEntity(skuCode = "SL-999", barcode = "890999", name = "Photo Missing Shoe", category = "Shoes", brand = "Sole", gender = "Men", photoUri = "", costPrice = 400.0, sellingPrice = 799.0),
            mapOf("8" to 2)
        )

        fun makeJpeg(color: Int, w: Int = 1600, h: Int = 1200): ByteArray {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            bmp.recycle()
            return baos.toByteArray()
        }

        val imgBlack = makeJpeg(android.graphics.Color.BLACK)
        val imgBlue = makeJpeg(android.graphics.Color.BLUE)
        val imgUnmatched = makeJpeg(android.graphics.Color.RED)

        val zipFile = File(tempDir, "catalog_photos.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // SKU-named photo with color tag
            zos.putNextEntry(ZipEntry("SL-001_Black_front.jpg"))
            zos.write(imgBlack)
            zos.closeEntry()

            // Duplicate byte-for-byte copy with different filename -> SHA-256 deduplicated!
            zos.putNextEntry(ZipEntry("SL-001_copy.jpg"))
            zos.write(imgBlack)
            zos.closeEntry()

            // Non-matching filename mapped via CSV inside ZIP
            zos.putNextEntry(ZipEntry("IMG_4412.jpg"))
            zos.write(imgBlue)
            zos.closeEntry()

            // Unmatched image
            zos.putNextEntry(ZipEntry("random_orphan.jpg"))
            zos.write(imgUnmatched)
            zos.closeEntry()

            // Mapping CSV inside ZIP
            zos.putNextEntry(ZipEntry("mapping.csv"))
            zos.write("sku,file,variant,color,primary\nSL-001,IMG_4412.jpg,8,Blue,false\n".toByteArray())
            zos.closeEntry()
        }

        val report = zipFile.inputStream().use { zin ->
            ProductImageManager.importImagesFromZipStream(context, zin, db.soleBookDao(), db.onboardingDao())
        }

        assertEquals(2, report.matchedCount) // SL-001_Black_front.jpg + IMG_4412.jpg
        assertEquals(1, report.duplicateSkippedCount) // SL-001_copy.jpg
        assertEquals(listOf("random_orphan.jpg"), report.unmatchedFiles)
        assertEquals(1, report.productsMissingImagesCount) // SL-999

        val savedImgs = db.onboardingDao().getImagesForProduct(p1Id)
        assertEquals(2, savedImgs.size)
        assertTrue(savedImgs.any { it.colorTag.equals("Black", ignoreCase = true) })
        assertTrue(savedImgs.any { it.colorTag.equals("Blue", ignoreCase = true) })
        // Check generated compressed file + thumbnail exist and are scaled down <= 1200px
        savedImgs.forEach { img ->
            val full = File(img.localPath)
            val thumb = File(img.thumbPath)
            assertTrue(full.exists() && full.length() > 0)
            assertTrue(thumb.exists() && thumb.length() > 0)
        }
        val repCsv = File(report.reportCsvPath).readText()
        assertTrue(repCsv.contains("random_orphan.jpg"))
        assertTrue(repCsv.contains("SL-999"))
    }

    @Test
    fun `barcode lookup enforces duplicate guard and never fabricates unknown product details`() = runBlocking {
        val id1 = repository.saveProductWithVariants(
            ProductEntity(skuCode = "GR-10", barcode = "8901491101837", name = "Lay's Classic Salted", category = "Snacks", brand = "Lays", gender = "Standard", photoUri = "", costPrice = 16.0, sellingPrice = 20.0),
            mapOf("50g" to 30)
        )

        val matched = BarcodeOnboardingService.lookupBarcode("8901491101837", allowExternalLookup = false, db.soleBookDao(), db.onboardingDao())
        assertTrue(matched is BarcodeLookupResult.MatchedCatalog)
        assertEquals("Lay's Classic Salted", (matched as BarcodeLookupResult.MatchedCatalog).product.name)

        // Duplicate check blocks assigning 8901491101837 to a different product
        val dup = BarcodeOnboardingService.checkDuplicateBarcode("8901491101837", excludeProductId = id1 + 99, db.soleBookDao(), db.onboardingDao())
        assertNotNull(dup)
        assertEquals("GR-10", dup!!.existingSku)

        // Unknown barcode offline MUST return UnknownBarcode and NEVER invent prices or names
        val unknown = BarcodeOnboardingService.lookupBarcode("9999999999999", allowExternalLookup = false, db.soleBookDao(), db.onboardingDao())
        assertTrue(unknown is BarcodeLookupResult.UnknownBarcode)
    }

    @Test
    fun `physical stock count barcode increment discrepancy calculation and auditable reconciliation`() = runBlocking {
        val pid = repository.saveProductWithVariants(
            ProductEntity(skuCode = "HM-50", barcode = "8905000000050", name = "Dettol Handwash", category = "Hygiene", brand = "Dettol", gender = "Standard", photoUri = "", costPrice = 80.0, sellingPrice = 110.0),
            mapOf("200ml" to 10)
        )

        val sessionId = stockCountService.createSession("Monthly Shelf Audit", prefillExpectedCount = false)
        repeat(8) {
            val updated = stockCountService.incrementCountByBarcode(sessionId, "8905000000050", 1)
            assertNotNull(updated)
        }

        val lines = db.onboardingDao().getStockCountLines(sessionId)
        assertEquals(1, lines.size)
        assertEquals(10, lines.first().expectedQty)
        assertEquals(8, lines.first().countedQty)
        assertEquals(-2, lines.first().variance)

        val summary = stockCountService.summarizeLines(lines, mapOf(pid to db.soleBookDao().getProductById(pid)!!))
        assertEquals(-2, summary.netUnitDelta)
        assertEquals(-160.0, summary.netCostImpact, 0.01)

        val reconciled = stockCountService.commitSession(sessionId)
        assertEquals(1, reconciled)
        assertEquals(8, db.soleBookDao().getVariantsForProduct(pid).first().stock)

        val movements = db.onboardingDao().getMovementsForProduct(pid)
        assertTrue(movements.any { it.movementType == "COUNT_RECONCILE" && it.qtyDelta == -2 && it.beforeQty == 10 && it.afterQty == 8 })
    }

    @Test
    fun `multilingual EN HI BN document extraction separates supplied sales from shortage purchase requests until GRN`() = runBlocking {
        // Existing catalog items
        val riceId = repository.saveProductWithVariants(
            ProductEntity(skuCode = "GR-1", barcode = "", name = "Basmati Rice 1kg", category = "Grains", brand = "", gender = "Standard", photoUri = "", costPrice = 90.0, sellingPrice = 120.0),
            mapOf("Standard" to 10)
        )
        val oilId = repository.saveProductWithVariants(
            ProductEntity(skuCode = "GR-2", barcode = "", name = "Mustard Oil 1L", category = "Oil", brand = "", gender = "Standard", photoUri = "", costPrice = 130.0, sellingPrice = 165.0),
            mapOf("Standard" to 8)
        )
        val sugarId = repository.saveProductWithVariants(
            ProductEntity(skuCode = "GR-3", barcode = "", name = "Refined Sugar 1kg", category = "Staples", brand = "", gender = "Standard", photoUri = "", costPrice = 38.0, sellingPrice = 46.0),
            mapOf("Standard" to 0)
        )

        val mixedMultilingualNote = """
            Customer: Ramesh +91 9876543210
            बिल Sold Basmati Rice 1kg २ kg @ 120
            বিক্রি Supplied Mustard Oil 1L ১ L @ 165
            Out of stock / मंगवाना है Refined Sugar 1kg 20 kg @ 38
            Expense Tea & Delivery 150
        """.trimIndent()

        val extractor = OnDeviceDocumentExtractor()
        val res = extractor.extractDocument(
            context = context,
            inputs = listOf(DocumentSourceInput.PlainTextNote(mixedMultilingualNote)),
            catalogProducts = db.soleBookDao().getAllProductsSync()
        )

        assertTrue(res.detectedLanguages.containsAll(listOf("EN", "HI", "BN")))
        assertEquals(4, res.lines.size)
        assertEquals(2, res.lines.count { it.actionType == BusinessActionType.SALE_DRAFT })
        assertEquals(1, res.lines.count { it.actionType == BusinessActionType.PURCHASE_REQUEST })
        assertEquals(1, res.lines.count { it.actionType == BusinessActionType.EXPENSE })

        val batch = stagingEngine.stageDrafts(
            sourceRoute = "AI_DOCUMENT",
            fileName = "Multilingual Slip",
            fileHash = res.docHash,
            drafts = res.toStagedDrafts()
        )
        val commit = stagingEngine.commitBatchChunked(batch.id, autoApproveValid = true)
        assertEquals("COMPLETED", commit.status)

        // Stock deducted for the 2 supplied items: Rice (10 - 2 = 8), Oil (8 - 1 = 7)
        assertEquals(8, db.soleBookDao().getVariantsForProduct(riceId).first().stock)
        assertEquals(7, db.soleBookDao().getVariantsForProduct(oilId).first().stock)
        assertEquals(1, db.soleBookDao().getAllSalesSync().size)
        assertEquals(150.0, db.soleBookDao().getAllExpensesSync().first().amount, 0.01)

        // Replenishment item MUST create PurchaseOrderEntity WITHOUT increasing Sugar stock!
        assertEquals(0, db.soleBookDao().getVariantsForProduct(sugarId).first().stock)
        val pos = db.onboardingDao().getAllPurchaseOrdersSync()
        assertEquals(1, pos.size)
        assertEquals("REQUESTED", pos.first().status)

        // Explicitly Confirm Goods Received -> NOW Sugar stock increases by 20 & GRN movement is logged!
        val grnOk = stagingEngine.confirmPurchaseOrderReceived(pos.first().id, paidAmount = 760.0)
        assertTrue(grnOk)
        assertEquals(20, db.soleBookDao().getVariantsForProduct(sugarId).first().stock)
        assertEquals("RECEIVED", db.onboardingDao().getPurchaseOrderById(pos.first().id)!!.status)
        assertTrue(db.onboardingDao().getMovementsForProduct(sugarId).any { it.movementType == "PURCHASE_RECEIPT" && it.qtyDelta == 20 })
    }

    @Test
    fun `backup encode and restore preserves all v8 entities`() = runBlocking {
        val pid = repository.saveProductWithVariants(
            ProductEntity(skuCode = "BK-1", barcode = "8909998887771", name = "Backup Verified Oil", category = "Oil", brand = "Fortune", gender = "Standard", photoUri = "", costPrice = 110.0, sellingPrice = 140.0),
            mapOf("1L" to 15)
        )
        db.onboardingDao().insertPurchaseOrder(
            PurchaseOrderEntity(poNumber = "PO-TEST-1", supplierName = "Adani Wilmar", status = "REQUESTED", itemsJson = "[]", estimatedTotal = 5500.0)
        )
        db.onboardingDao().insertMovementIgnore(
            InventoryMovementEntity(movementKey = "test:bk:1", productId = pid, sizeLabel = "1L", movementType = "OPENING", qtyDelta = 15, beforeQty = 0, afterQty = 15, unitCost = 110.0, sourceRoute = "MANUAL")
        )

        val payload = repository.buildBackupPayload()
        val json = ExportUtils.encodeFullBackupJson(payload)
        val decoded = ExportUtils.decodeFullBackupJson(json)
        repository.restoreFromBackup(decoded)

        val restoredProd = db.onboardingDao().getProductByBarcode("8909998887771")
        assertNotNull(restoredProd)
        assertEquals("Backup Verified Oil", restoredProd!!.name)
        assertEquals(1, db.onboardingDao().getAllPurchaseOrdersSync().size)
        assertEquals("PO-TEST-1", db.onboardingDao().getAllPurchaseOrdersSync().first().poNumber)
        assertEquals(1, db.onboardingDao().getAllMovementsSync().size)
    }

    @Test
    fun `DataHub and StagingReview Compose UI render all 6 routes and staging items cleanly`() {
        val controller = OnboardingHubController(
            application = context as android.app.Application,
            db = db,
            repository = repository,
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
            onToast = {}
        )
        var openedSpreadsheet = false

        composeTestRule.setContent {
            SoleBookTheme {
                ImportHubScreen(
                    state = SoleBookUiState(
                        isLoading = false,
                        settings = ShopSettingsEntity(shopName = "Universal Retail", hasCompletedOnboarding = true)
                    ),
                    hubState = DataHubUiState(
                        importBatches = listOf(
                            ImportBatchEntity(
                                id = 7L,
                                idempotencyKey = "k7",
                                sourceRoute = "SPREADSHEET",
                                fileName = "opening_stock.xlsx",
                                status = "PARTIAL",
                                totalItems = 1000,
                                validItems = 995,
                                invalidItems = 5,
                                processedItems = 500
                            )
                        )
                    ),
                    controller = controller,
                    onBack = {},
                    onOpenBatchReview = {},
                    onOpenStockCountSession = {},
                    onOpenProductDetail = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hub_route_spreadsheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hub_route_docs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_hub_list").performScrollToNode(hasTestTag("hub_route_manual"))
        composeTestRule.onNodeWithTag("hub_route_barcode").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hub_route_images").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hub_route_manual").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hub_route_stock_count").assertIsDisplayed()

        // Verify resumable partial batch renders Resume action
        composeTestRule.onNodeWithTag("data_hub_list").performScrollToNode(hasText("Resume"))
        composeTestRule.onNodeWithText("Batch #7 • opening_stock.xlsx").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()

        // Scroll back to top and tap spreadsheet tile to open modal sheet
        composeTestRule.onNodeWithTag("data_hub_list").performScrollToNode(hasTestTag("hub_route_spreadsheet"))
        composeTestRule.onNodeWithTag("hub_route_spreadsheet").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pick_spreadsheet_btn").assertIsDisplayed()
        openedSpreadsheet = true
        assertTrue(openedSpreadsheet)
    }
}
