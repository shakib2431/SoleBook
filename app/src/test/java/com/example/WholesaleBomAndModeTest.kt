package com.example

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.repository.SoleBookRepository
import com.example.data.repository.WholesaleRepository
import com.example.domain.*
import com.example.ui.components.WorkspaceModeSwitcher
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.SoleBookTheme
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.BackupPayload
import com.example.util.ExportUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WholesaleBomAndModeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: SoleBookDatabase
    private lateinit var wholesaleRepo: WholesaleRepository
    private lateinit var retailRepo: SoleBookRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SoleBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        wholesaleRepo = WholesaleRepository(db)
        retailRepo = SoleBookRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `200 piece mattress BOM quote scales cotton filling cloth roll stitching margin discount and GST accurately`() = runBlocking {
        wholesaleRepo.seedMattressStarterBom()
        val materials = db.wholesaleDao().getAllRawMaterialsSync().associateBy { it.id }
        val templates = db.wholesaleDao().getAllBomTemplatesSync()
        val cottonTpl = templates.first { it.name.contains("Cotton", ignoreCase = true) }
        val rules = db.wholesaleDao().getRulesForTemplate(cottonTpl.id)

        // Baseline 72x36x4 = 10 kg cotton. Target 72x60x6 is 2.5x volume -> 25 kg * 1.05 wastage = 26.25 kg/pc -> 5250 kg for 200 pcs.
        val calc = BomQuotationEngine.calculateQuote(
            template = cottonTpl,
            rules = rules,
            rawMaterialsById = materials,
            lengthIn = 72.0,
            widthIn = 60.0,
            thicknessIn = 6.0,
            densityFactor = 1.0,
            quantity = 200,
            stitchChargePerPc = 150.0,
            transportCost = 4000.0,
            marginPercent = 20.0,
            discountPercent = 5.0,
            gstPercent = 12.0
        )

        val cottonLine = calc.lines.first { it.itemName.contains("Cotton") }
        assertEquals(26.25, cottonLine.qtyPerPiece, 0.01)
        assertEquals(5250.0, cottonLine.totalQty, 0.1)
        assertEquals(5250.0 * 140.0, cottonLine.lineTotal, 1.0)

        val clothLine = calc.lines.first { it.itemName.contains("Cloth") }
        // Top+Bottom = 2*(74*62) = 9176 sq.in; Border = 2*(72+60)*(6+2) = 2112 sq.in -> 11288 / 60 / 39.3701 * 1.06 = 5.065m/pc
        assertTrue(clothLine.qtyPerPiece in 4.9..5.2)
        assertEquals(calc.lines.sumOf { it.lineTotal }, calc.totalMaterialCost, 0.01)
        assertEquals(30000.0, calc.totalStitchCost, 0.01)
        assertEquals(4000.0, calc.transportCost, 0.01)
        assertTrue(calc.grandTotal > calc.totalQuoteBeforeGst)
        assertTrue(calc.estimatedNetProfit > 0.0)

        // Verify manual override on cotton qty works cleanly
        val overridden = BomQuotationEngine.calculateQuote(
            template = cottonTpl,
            rules = rules,
            rawMaterialsById = materials,
            lengthIn = 72.0,
            widthIn = 60.0,
            thicknessIn = 6.0,
            densityFactor = 1.0,
            quantity = 200,
            stitchChargePerPc = 150.0,
            transportCost = 0.0,
            marginPercent = 20.0,
            discountPercent = 0.0,
            gstPercent = 0.0,
            overridesByIndex = mapOf(0 to QuoteLineOverride(qtyPerPiece = 24.0, unitRate = 135.0))
        )
        assertEquals(24.0, overridden.lines[0].qtyPerPiece, 0.001)
        assertEquals(4800.0, overridden.lines[0].totalQty, 0.01)
        assertEquals(4800.0 * 135.0, overridden.lines[0].lineTotal, 0.1)
        assertTrue(overridden.lines[0].isOverridden)
    }

    @Test
    fun `quote converts to connected wholesale sale and PO with optional tracked material deduction`() = runBlocking {
        wholesaleRepo.seedMattressStarterBom()
        val materials = db.wholesaleDao().getAllRawMaterialsSync().associateBy { it.id }
        val packMat = materials.values.first { it.trackStock }
        val cottonMat = materials.values.first { !it.trackStock && it.name.contains("Cotton") }
        assertEquals(500.0, packMat.currentStock, 0.01)
        assertEquals(0.0, cottonMat.currentStock, 0.01)

        val cottonTpl = db.wholesaleDao().getAllBomTemplatesSync().first { it.name.contains("Cotton") }
        val rules = db.wholesaleDao().getRulesForTemplate(cottonTpl.id)
        val calc = BomQuotationEngine.calculateQuote(
            template = cottonTpl,
            rules = rules,
            rawMaterialsById = materials,
            lengthIn = 72.0,
            widthIn = 60.0,
            thicknessIn = 6.0,
            densityFactor = 1.0,
            quantity = 200,
            stitchChargePerPc = 150.0,
            transportCost = 2000.0,
            marginPercent = 20.0,
            discountPercent = 0.0,
            gstPercent = 12.0
        )

        val quoteId = wholesaleRepo.saveWholesaleQuote(
            buyerName = "Hotel Paradise",
            buyerPhone = "9876543210",
            buyerCompany = "Paradise Hospitality",
            status = "APPROVED",
            templateId = cottonTpl.id,
            title = "200 Queen Cotton Mattresses",
            lengthIn = 72.0,
            widthIn = 60.0,
            thicknessIn = 6.0,
            densityFactor = 1.0,
            quantity = 200,
            stitchChargePerPc = 150.0,
            transportCost = 2000.0,
            discountPercent = 0.0,
            gstPercent = 12.0,
            marginPercent = 20.0,
            validUntil = "10 days",
            notes = "",
            calc = calc
        )

        // Generate Material PO
        val poId = wholesaleRepo.createPurchaseOrderFromQuote(quoteId, supplierName = "Ganga Cotton").getOrThrow()
        val pos = db.onboardingDao().getAllPurchaseOrdersSync()
        assertEquals(1, pos.size)
        assertEquals(poId, pos[0].id)
        assertTrue(pos[0].poNumber.startsWith("PO-WQ-"))

        // Convert to connected B2B CREDIT sale (creates SaleEntity(saleChannel='WHOLESALE') + customer credit)
        val saleId = wholesaleRepo.convertQuoteToWholesaleSale(quoteId, paymentMode = "CREDIT").getOrThrow()
        val sales = db.soleBookDao().getAllSalesSync()
        assertEquals(1, sales.size)
        assertEquals(saleId, sales[0].id)
        assertEquals("WHOLESALE", sales[0].saleChannel)
        assertEquals(calc.totalQuoteBeforeGst, sales[0].totalRevenue, 0.01)
        assertEquals(calc.grandTotal, sales[0].totalCollected, 0.01)

        // Verify buyer credit increased
        val buyer = db.soleBookDao().getCustomerByPhone("9876543210")
        assertNotNull(buyer)
        assertEquals(calc.grandTotal, buyer!!.creditBalance, 0.01)

        // Verify ONLY tracked raw material (polybags) deducted stock; untracked cotton remains 0 & did NOT block sale
        val updatedMaterials = db.wholesaleDao().getAllRawMaterialsSync().associateBy { it.id }
        val expectedPackUsed = calc.lines.first { it.rawMaterialId == packMat.id }.totalQty
        assertEquals((500.0 - expectedPackUsed).coerceAtLeast(0.0), updatedMaterials[packMat.id]!!.currentStock, 0.1)
        assertEquals(0.0, updatedMaterials[cottonMat.id]!!.currentStock, 0.01)

        // Verify unified financial analytics splits Retail vs Wholesale and combines Net Profit
        val channelBreakdown = AnalyticsEngine.computeChannelBreakdown(
            startTime = 0L,
            endTime = System.currentTimeMillis() + 60_000L,
            sales = sales,
            saleItems = db.soleBookDao().getAllSaleItemsSync(),
            returns = emptyList()
        )
        assertEquals(1, channelBreakdown.wholesaleBillCount)
        assertEquals(0, channelBreakdown.retailBillCount)
        assertEquals(calc.totalQuoteBeforeGst, channelBreakdown.wholesaleRevenue, 0.01)
        assertEquals(calc.totalQuoteBeforeGst - calc.totalManufacturingCost, channelBreakdown.wholesaleGrossProfit, 0.5)
    }

    @Test
    fun `migration 8 to 9 preserves existing retail records and creates wholesale tables cleanly`() {
        val dbName = "solebook_migration_8_9_test.db"
        context.deleteDatabase(dbName)

        val helperV8 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(sqldb: SupportSQLiteDatabase) {
                        // Create minimal v8 schema tables before MIGRATION_8_9
                        sqldb.execSQL("""
                            CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `skuCode` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT NOT NULL, `category` TEXT NOT NULL, `gender` TEXT NOT NULL, `materialStyle` TEXT NOT NULL, `color` TEXT NOT NULL, `costPrice` REAL NOT NULL, `mrp` REAL NOT NULL, `sellingPrice` REAL NOT NULL, `lowStockThreshold` INTEGER NOT NULL, `leadTimeDays` INTEGER NOT NULL, `reorderFrequencyDays` INTEGER NOT NULL, `isRepeatable` INTEGER NOT NULL, `photoUri` TEXT NOT NULL, `barcode` TEXT NOT NULL, `unitLabel` TEXT NOT NULL, `altUnitLabel` TEXT NOT NULL, `unitConversionFactor` REAL NOT NULL, `batchOrSerial` TEXT NOT NULL, `expiryOrWarranty` TEXT NOT NULL, `location` TEXT NOT NULL, `trackSerial` INTEGER NOT NULL, `trackBatchExpiry` INTEGER NOT NULL, `defaultVendorId` INTEGER, `vendorName` TEXT NOT NULL, `isArchived` INTEGER NOT NULL, `retiredAt` INTEGER, `createdAt` INTEGER NOT NULL)
                        """.trimIndent())
                        sqldb.execSQL("""
                            CREATE TABLE IF NOT EXISTS `sales` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `billNumber` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `paymentMode` TEXT NOT NULL, `customerId` INTEGER, `customerName` TEXT NOT NULL, `customerPhone` TEXT NOT NULL, `subtotal` REAL NOT NULL, `discountType` TEXT NOT NULL, `discountValue` REAL NOT NULL, `discountAmount` REAL NOT NULL, `gstPercent` REAL NOT NULL, `gstAmount` REAL NOT NULL, `totalCollected` REAL NOT NULL, `totalRevenue` REAL NOT NULL, `isCreditSettled` INTEGER NOT NULL, `creditSettledAt` INTEGER)
                        """.trimIndent())
                        sqldb.execSQL("""
                            CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `shopName` TEXT NOT NULL, `businessCategory` TEXT NOT NULL, `customBusinessType` TEXT NOT NULL, `businessChannel` TEXT NOT NULL, `hasCompletedOnboarding` INTEGER NOT NULL, `shopLogoUri` TEXT NOT NULL, `shortHeaderName` TEXT NOT NULL, `headerBrandMode` TEXT NOT NULL, `currencySymbol` TEXT NOT NULL, `languageCode` TEXT NOT NULL, `gstEnabled` INTEGER NOT NULL, `gstRate` REAL NOT NULL, `gstInclusive` INTEGER NOT NULL, `defaultLeadTimeDays` INTEGER NOT NULL, `seasonalityMode` TEXT NOT NULL, `seasonalityUpliftPercent` REAL NOT NULL, `hasCompletedWalkthrough` INTEGER NOT NULL, `isDemoLoaded` INTEGER NOT NULL, `alertsEnabled` INTEGER NOT NULL, `instantSaleAlertsEnabled` INTEGER NOT NULL, `stockAlertsEnabled` INTEGER NOT NULL, `demandAlertsEnabled` INTEGER NOT NULL, `marginAlertsEnabled` INTEGER NOT NULL, `alertIntervalHours` INTEGER NOT NULL, `maxAlertsPerDay` INTEGER NOT NULL, `quietStartHour` INTEGER NOT NULL, `quietEndHour` INTEGER NOT NULL, PRIMARY KEY(`id`))
                        """.trimIndent())
                        // Populate existing v8 rows
                        sqldb.execSQL("INSERT INTO settings VALUES (1, 'Metro Bedding', 'GENERAL', '', 'PHYSICAL', 1, '', '', 'LOGO_AND_NAME', '₹', 'en', 0, 12.0, 1, 3, 'NONE', 0.0, 1, 0, 1, 0, 1, 1, 1, 4, 4, 21, 8)")
                        sqldb.execSQL("INSERT INTO sales VALUES (1, 'INV-001', 1700000000000, 'CASH', NULL, 'Walk-in', '', 500.0, 'NONE', 0.0, 0.0, 0.0, 0.0, 500.0, 500.0, 1, NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val sqlDb = helperV8.writableDatabase
        SoleBookDatabase.MIGRATION_8_9.migrate(sqlDb)

        sqlDb.query("SELECT shopName, businessOperationMode, activeWorkspaceMode FROM settings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Metro Bedding", c.getString(0))
            assertEquals("RETAIL", c.getString(1))
            assertEquals("RETAIL", c.getString(2))
        }
        sqlDb.query("SELECT billNumber, saleChannel FROM sales WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("INV-001", c.getString(0))
            assertEquals("RETAIL", c.getString(1))
        }
        sqlDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('raw_materials', 'bom_templates', 'bom_material_rules', 'wholesale_quotes', 'wholesale_quote_lines')").use { c ->
            assertEquals(5, c.count)
        }
        sqlDb.close()
        helperV8.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `json backup round trip preserves wholesale materials BOM templates quotes and operation modes`() = runBlocking {
        wholesaleRepo.seedMattressStarterBom()
        val materials = db.wholesaleDao().getAllRawMaterialsSync()
        val templates = db.wholesaleDao().getAllBomTemplatesSync()
        val rules = db.wholesaleDao().getAllBomMaterialRulesSync()
        val payload = BackupPayload(
            products = emptyList(),
            variants = emptyList(),
            sales = listOf(
                SaleEntity(id = 10L, billNumber = "WQ-0001", paymentMode = "CASH", subtotal = 15000.0, totalCollected = 15000.0, totalRevenue = 15000.0, saleChannel = "WHOLESALE")
            ),
            saleItems = emptyList(),
            returns = emptyList(),
            purchases = emptyList(),
            expenses = emptyList(),
            customers = emptyList(),
            segments = emptyList(),
            templates = emptyList(),
            campaigns = emptyList(),
            settings = ShopSettingsEntity(
                shopName = "National Mattresses",
                businessOperationMode = "BOTH",
                activeWorkspaceMode = "WHOLESALE"
            ),
            rawMaterials = materials,
            bomTemplates = templates,
            bomMaterialRules = rules
        )

        val encoded = ExportUtils.encodeFullBackupJson(payload)
        val decoded = ExportUtils.decodeFullBackupJson(encoded)

        assertEquals("BOTH", decoded.settings?.businessOperationMode)
        assertEquals("WHOLESALE", decoded.settings?.activeWorkspaceMode)
        assertEquals("WHOLESALE", decoded.sales.first().saleChannel)
        assertEquals(materials.size, decoded.rawMaterials.size)
        assertEquals(templates.size, decoded.bomTemplates.size)
        assertEquals(rules.size, decoded.bomMaterialRules.size)
    }

    @Test
    fun `onboarding selects wholesale or both mode and invokes callback cleanly`() {
        var selectedMode = ""
        composeTestRule.setContent {
            SoleBookTheme {
                OnboardingScreen(
                    onCompleteGoDashboard = { _, _, _, _, _ -> },
                    onCompleteAddFirstProduct = { _, _, _, _, _ -> },
                    onSelectOperationMode = { selectedMode = it }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_start_btn").performClick()
        composeTestRule.onNodeWithTag("onboarding_business_name").performTextInput("Sunrise Mattresses")
        composeTestRule.onNodeWithTag("onboarding_step2_continue").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("operation_mode_BOTH").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("onboarding_step3_finish").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("onboarding_go_dashboard_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals("BOTH", selectedMode)
    }

    @Test
    fun `workspace mode switcher toggles between Retail and Wholesale`() {
        val currentMode = androidx.compose.runtime.mutableStateOf(WorkspaceMode.RETAIL)
        composeTestRule.setContent {
            SoleBookTheme {
                WorkspaceModeSwitcher(
                    activeMode = currentMode.value,
                    onSelectMode = { currentMode.value = it }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workspace_switch_WHOLESALE").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(WorkspaceMode.WHOLESALE, currentMode.value)
        composeTestRule.onNodeWithTag("workspace_switch_RETAIL").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(WorkspaceMode.RETAIL, currentMode.value)
    }

    @Test
    fun `dashboard displays Retail and Wholesale toggle even in RETAIL mode and switches workspaces cleanly`() {
        val app = context.applicationContext as android.app.Application
        val singletonDb = SoleBookDatabase.getInstance(app)
        runBlocking<Unit> {
            singletonDb.soleBookDao().saveSettings(
                ShopSettingsEntity(
                    shopName = "Test Business",
                    businessCategory = "CLOTHING",
                    hasCompletedOnboarding = true,
                    hasCompletedWalkthrough = true,
                    businessOperationMode = BusinessOperationMode.RETAIL.key,
                    activeWorkspaceMode = WorkspaceMode.RETAIL.key
                )
            )
        }
        val vm = SoleBookViewModel(app)
        composeTestRule.setContent {
            SoleBookTheme {
                SoleBookRootApp(viewModel = vm)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("workspace_switch_WHOLESALE").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("workspace_switch_RETAIL").assertIsDisplayed()
        composeTestRule.onNodeWithTag("workspace_switch_WHOLESALE").assertIsDisplayed().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("wholesale_desk_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("wholesale_desk_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_w_bom").assertIsDisplayed()
        composeTestRule.onNodeWithTag("workspace_switch_RETAIL").assertIsDisplayed().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("tab_stock").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("tab_stock").assertIsDisplayed()
        assertEquals(BusinessOperationMode.BOTH.key, vm.uiState.value.settings.businessOperationMode)
        assertEquals(WorkspaceMode.RETAIL.key, vm.uiState.value.settings.activeWorkspaceMode)
    }

    @Test
    fun `soleBookTheme keeps Retail emerald green and shifts Wholesale to cobalt blue`() {
        var retailPrimary: androidx.compose.ui.graphics.Color? = null
        var wholesalePrimary: androidx.compose.ui.graphics.Color? = null
        var wholesaleContainer: androidx.compose.ui.graphics.Color? = null

        composeTestRule.setContent {
            SoleBookTheme(workspaceMode = WorkspaceMode.RETAIL) {
                retailPrimary = androidx.compose.material3.MaterialTheme.colorScheme.primary
            }
            SoleBookTheme(workspaceMode = WorkspaceMode.WHOLESALE) {
                wholesalePrimary = androidx.compose.material3.MaterialTheme.colorScheme.primary
                wholesaleContainer = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.theme.EmeraldPrimary, retailPrimary)
        assertEquals(com.example.ui.theme.WholesaleBluePrimary, wholesalePrimary)
        assertEquals(com.example.ui.theme.WholesaleBlueSoft, wholesaleContainer)
    }
}
