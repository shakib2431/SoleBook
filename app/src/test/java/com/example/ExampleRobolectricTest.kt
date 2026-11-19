package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProductEntity
import com.example.data.local.SoleBookDatabase
import com.example.data.repository.CartLineRequest
import com.example.data.repository.SoleBookRepository
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.local.ProductFingerprintEntity
import com.example.domain.OfflineVisualMatcher
import com.example.ui.components.CameraCaptureDialog
import com.example.ui.theme.SoleBookTheme
import android.app.Application
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.data.local.ShopSettingsEntity
import com.example.domain.IndustryProfile
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.viewmodel.DashboardSnapshot
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.ImageUtils
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: SoleBookDatabase
    private lateinit var repo: SoleBookRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SoleBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SoleBookRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `read string from context returns SoleBook`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SoleBook", appName)
    }

    @Test
    fun `sale undo and weighted average restock work accurately`() = runTest {
        val pid = repo.saveProductWithVariants(
            ProductEntity(
                skuCode = "SL-001", name = "Test Sandal", category = "Sandal",
                brand = "Sparx", gender = "Men", photoUri = "", costPrice = 300.0, sellingPrice = 600.0
            ),
            mapOf("8" to 4)
        )
        val prod = db.soleBookDao().getProductById(pid)!!

        // Negative stock prevention check
        val failRes = repo.completeSale(
            lines = listOf(CartLineRequest(prod, "8", 5, 600.0)),
            paymentMode = "CASH", discountType = "AMOUNT", discountInput = 0.0,
            customerPhone = "", customerName = ""
        )
        assertTrue(failRes.isFailure)

        // Valid sale of 2 pairs
        val okRes = repo.completeSale(
            lines = listOf(CartLineRequest(prod, "8", 2, 580.0)),
            paymentMode = "UPI", discountType = "AMOUNT", discountInput = 0.0,
            customerPhone = "9876543210", customerName = "Raju"
        )
        assertTrue(okRes.isSuccess)
        assertEquals(2, db.soleBookDao().getVariant(pid, "8")!!.stock)

        // Undo sale -> stock back to 4
        repo.undoSale(okRes.getOrThrow())
        assertEquals(4, db.soleBookDao().getVariant(pid, "8")!!.stock)

        // Weighted-average restock: 4 existing @ 300 + 4 added @ 400 = 350 avg cost
        repo.restockProduct(pid, mapOf("8" to 4), 400.0, "Wholesaler", "Batch 2")
        val updated = db.soleBookDao().getProductById(pid)!!
        assertEquals(350.0, updated.costPrice, 0.01)
        assertEquals(8, db.soleBookDao().getVariant(pid, "8")!!.stock)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `offline visual matcher precisely identifies cropped lighting-shifted product and filters out unrelated items`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pidLeather = repo.saveProductWithVariants(
            ProductEntity(
                skuCode = "SL-010", name = "Tan Leather Chappal", category = "Chappal",
                brand = "Paragon", gender = "Men", photoUri = "drawable:img_chappal_leather",
                costPrice = 250.0, sellingPrice = 499.0, colorTag = "Tan", styleTag = "Leather",
                rackLocation = "Rack A-1"
            ),
            mapOf("7" to 0, "8" to 5, "9" to 2)
        )
        val pidSport = repo.saveProductWithVariants(
            ProductEntity(
                skuCode = "SL-011", name = "Navy Runner", category = "Sports Shoe",
                brand = "Campus", gender = "Men", photoUri = "drawable:img_shoe_running",
                costPrice = 600.0, sellingPrice = 1199.0, colorTag = "Navy", styleTag = "Mesh",
                rackLocation = "Rack C-2"
            ),
            mapOf("8" to 4)
        )
        val pidFormal = repo.saveProductWithVariants(
            ProductEntity(
                skuCode = "SL-012", name = "Black Oxford", category = "Formal",
                brand = "Bata", gender = "Men", photoUri = "drawable:img_shoe_formal",
                costPrice = 800.0, sellingPrice = 1499.0, colorTag = "Black", styleTag = "Leather",
                rackLocation = "Rack B-1"
            ),
            mapOf("9" to 3)
        )
        val pidNoPhoto = repo.saveProductWithVariants(
            ProductEntity(
                skuCode = "SL-013", name = "Unphotographed Item", category = "Chappal",
                brand = "Local", gender = "Men", photoUri = "",
                costPrice = 150.0, sellingPrice = 299.0, colorTag = "Tan"
            ),
            mapOf("8" to 6)
        )

        // Seed a stale legacy v1 fingerprint to verify automatic v2 re-indexing
        db.soleBookDao().upsertFingerprint(
            ProductFingerprintEntity(
                productId = pidLeather,
                signatureCsv = "0.1,0.2,0.3",
                dominantColor = "Tan",
                updatedAt = System.currentTimeMillis() + 5000L
            )
        )

        val fingerprints = repo.indexFingerprintsForProducts(context)
        assertEquals(3, fingerprints.size)
        assertFalse(fingerprints.containsKey(pidNoPhoto))
        assertTrue(fingerprints[pidLeather]!!.signatureCsv.startsWith(OfflineVisualMatcher.SIGNATURE_PREFIX))

        // Query using a slightly darker, center-cropped variant of img_shoe_running
        val originalRunner = OfflineVisualMatcher.decodeScaledBitmapFromProductPhoto(context, "drawable:img_shoe_running")!!
        val queryRunner = Bitmap.createBitmap(originalRunner.width, originalRunner.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until originalRunner.height) {
            for (x in 0 until originalRunner.width) {
                val px = originalRunner.getPixel(x, y)
                queryRunner.setPixel(
                    x, y,
                    Color.rgb(
                        (Color.red(px) * 0.84f).toInt().coerceIn(0, 255),
                        (Color.green(px) * 0.84f).toInt().coerceIn(0, 255),
                        (Color.blue(px) * 0.84f).toInt().coerceIn(0, 255)
                    )
                )
            }
        }
        val (runnerViews, runnerDomColor) = OfflineVisualMatcher.extractQuerySignatures(queryRunner)
        originalRunner.recycle()
        queryRunner.recycle()

        val products = listOf(
            db.soleBookDao().getProductById(pidLeather)!!,
            db.soleBookDao().getProductById(pidSport)!!,
            db.soleBookDao().getProductById(pidFormal)!!,
            db.soleBookDao().getProductById(pidNoPhoto)!!
        )
        val outcome = OfflineVisualMatcher.findBestMatches(runnerViews, runnerDomColor, products, fingerprints)
        assertTrue("Expected running shoe match", outcome.matches.isNotEmpty())
        assertEquals("SL-011", outcome.matches.first().product.skuCode)
        assertTrue("Best match flag should be set", outcome.matches.first().isBestMatch)
        assertTrue("Unphotographed product must never match", outcome.matches.none { it.product.id == pidNoPhoto })

        // Completely unrelated green-striped pattern should return ZERO precision matches
        val unrelatedBmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        for (y in 0 until 120) {
            for (x in 0 until 120) {
                unrelatedBmp.setPixel(x, y, if ((y / 12) % 2 == 0) Color.rgb(20, 165, 55) else Color.rgb(180, 35, 190))
            }
        }
        val (unrelatedViews, unrelatedDomColor) = OfflineVisualMatcher.extractQuerySignatures(unrelatedBmp)
        unrelatedBmp.recycle()
        val unrelatedOutcome = OfflineVisualMatcher.findBestMatches(unrelatedViews, unrelatedDomColor, products, fingerprints)
        assertTrue(
            "Unrelated green/purple photo should be filtered out instead of showing wrong products",
            unrelatedOutcome.matches.isEmpty()
        )

        // Verify Tan color detection and v2 encode/decode round-trip
        val tanBmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        tanBmp.eraseColor(Color.rgb(185, 110, 45))
        val (qVec, domColor) = OfflineVisualMatcher.extractSignature(tanBmp)
        tanBmp.recycle()
        assertEquals(OfflineVisualMatcher.VECTOR_LEN, qVec.size)
        assertEquals("Tan", domColor)

        val encoded = OfflineVisualMatcher.encodeVector(qVec)
        assertTrue(encoded.startsWith(OfflineVisualMatcher.SIGNATURE_PREFIX))
        val decoded = OfflineVisualMatcher.decodeVector(encoded)
        assertEquals(OfflineVisualMatcher.VECTOR_LEN, decoded.size)
        assertEquals(1.0f, OfflineVisualMatcher.cosineSimilarity(qVec, decoded), 0.02f)
    }

    @Test
    fun `notice state persistence and inline selling price update work in room`() = runTest {
        val pid = repo.saveProductWithVariants(
            ProductEntity(
                skuCode = "SL-099", name = "Daily Chappal", category = "Chappal",
                brand = "Relaxo", gender = "Men", photoUri = "", costPrice = 280.0, sellingPrice = 340.0
            ),
            mapOf("8" to 6)
        )
        repo.updateSellingPrice(pid, 420.0)
        val repriced = db.soleBookDao().getProductById(pid)!!
        assertEquals(420.0, repriced.sellingPrice, 0.01)

        val now = System.currentTimeMillis()
        repo.upsertNoticeState(
            com.example.data.local.NoticeStateEntity(
                noticeKey = "MARGIN_UP:$pid",
                lastNotifiedAt = now,
                snoozedUntil = now + 86_400_000L,
                dismissedUntil = 0L,
                lastSignatureHash = "repriced:420",
                isRead = true
            )
        )
        val savedStates = db.soleBookDao().getAllNoticeStatesSync()
        assertEquals(1, savedStates.size)
        assertEquals("MARGIN_UP:$pid", savedStates.first().noticeKey)
        assertTrue(savedStates.first().isRead)
    }

    @Test
    fun `vendor fresh lot credit billing payment settlement and lot retirement work end to end`() = runTest {
        val vid = repo.saveVendor(
            com.example.data.local.VendorEntity(
                name = "Hindustan Wholesale",
                phone = "9820011223",
                cityMarket = "Delhi",
                specialtiesCsv = "Sports Shoe, Loafer"
            )
        )

        // Fresh lot of 10 pairs @ ₹400 = ₹4,000 total; paid ₹1,500 -> ₹2,500 credit due (PARTIAL)
        val pid = repo.saveFreshLotWithVendor(
            product = ProductEntity(
                skuCode = "LOT-D1",
                name = "Mesh Runner Lot",
                category = "Sports Shoe",
                brand = "Campus",
                gender = "Men",
                photoUri = "",
                costPrice = 400.0,
                sellingPrice = 899.0,
                isRepeatable = false,
                vendorId = vid,
                lotCode = "DEL-MAY"
            ),
            sizeStocks = mapOf("8" to 5, "9" to 5),
            paidAmount = 1500.0,
            notes = "May Delhi lot"
        )

        val vendorAfterLot = db.soleBookDao().getVendorById(vid)!!
        assertEquals(2500.0, vendorAfterLot.creditBalance, 0.01)

        val purchases = db.soleBookDao().getAllPurchasesSync()
        assertEquals(1, purchases.size)
        assertEquals("FRESH_LOT", purchases.first().lotType)
        assertEquals("PARTIAL", purchases.first().paymentStatus)
        assertEquals(2500.0, purchases.first().dueAmount, 0.01)

        // Settle ₹1,000 via UPI -> ₹1,500 remaining due
        repo.recordVendorPayment(vid, 1000.0, "UPI", "GPay part settlement")
        val vendorAfterPay = db.soleBookDao().getVendorById(vid)!!
        assertEquals(1500.0, vendorAfterPay.creditBalance, 0.01)
        val payments = db.soleBookDao().getAllVendorPaymentsSync()
        assertEquals(1, payments.size)
        assertEquals("UPI", payments.first().paymentMode)

        // Toggle repeatable & retire lot
        repo.toggleRepeatableProduct(pid)
        assertTrue(db.soleBookDao().getProductById(pid)!!.isRepeatable)
        repo.retireProduct(pid)
        assertTrue(db.soleBookDao().getProductById(pid)!!.isArchived)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `camera capture normalization rotates EXIF upright preserves true color and saves crisp 1200px`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawFile = ImageUtils.createTempCameraFile(context)

        // Simulate a 1600x1200 landscape sensor JPEG with EXIF ROTATE_90 and tan leather color
        val bmp = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(185, 110, 45))
        FileOutputStream(rawFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        bmp.recycle()

        val exif = android.media.ExifInterface(rawFile.absolutePath)
        exif.setAttribute(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_ROTATE_90.toString()
        )
        exif.saveAttributes()

        val normalized = ImageUtils.normalizeCapturedFile(context, rawFile, maxDim = 1200)
        assertNotNull(normalized)
        val dims = ImageUtils.readImageDimensions(normalized!!)
        assertNotNull(dims)
        // Rotated 90 deg from 1600x1200 -> 900x1200 portrait upright
        assertEquals(900, dims!!.first)
        assertEquals(1200, dims.second)

        // Verify OfflineVisualMatcher preserves true dominant color ("Tan") after normalization
        val decodedForMatch = OfflineVisualMatcher.decodeScaledBitmapFromUri(context, android.net.Uri.fromFile(normalized))
        assertNotNull(decodedForMatch)
        val (_, dominantColor) = OfflineVisualMatcher.extractSignature(decodedForMatch!!)
        decodedForMatch.recycle()
        assertEquals("Tan", dominantColor)

        // Verify compressAndSaveFile stores crisp JPEG in product_photos
        val savedPath = ImageUtils.compressAndSaveFile(context, normalized, maxDim = 1200)
        assertNotNull(savedPath)
        val savedFile = File(savedPath!!)
        assertTrue(savedFile.exists() && savedFile.length() > 1000L)

        rawFile.delete()
        normalized.delete()
        savedFile.delete()
    }

    @Test
    fun `camera capture dialog displays permission and system camera fallback cleanly`() {
        composeTestRule.setContent {
            SoleBookTheme {
                CameraCaptureDialog(
                    title = "Capture Footwear Photo",
                    subtitle = "4:3 sharp SDR • Tap shoe to focus",
                    allowGalleryFallback = true,
                    onDismiss = {},
                    onPhotoReady = {},
                    onGalleryPicked = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("camera_capture_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("camera_system_fallback_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Camera Permission Needed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use System Camera App").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `shop logo PNG save backup round-trip and branding top bar work cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempLogo = File(context.cacheDir, "test_logo_src.png")
        val bmp = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.argb(200, 37, 99, 235))
        FileOutputStream(tempLogo).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()

        val savedLogoPath = ImageUtils.compressAndSaveLogo(context, android.net.Uri.fromFile(tempLogo), maxDim = 512)
        assertNotNull(savedLogoPath)
        assertTrue(savedLogoPath!!.endsWith(".png"))
        assertTrue(File(savedLogoPath).exists())

        val settings = com.example.data.local.ShopSettingsEntity(
            shopName = "Shree Laxmi Narayan Footwear & Sports",
            shopLogoUri = savedLogoPath,
            shortHeaderName = "Laxmi",
            headerBrandMode = "LOGO_AND_NAME"
        )
        repo.saveSettings(settings)
        val fromDb = db.soleBookDao().getSettingsSync()!!
        assertEquals(savedLogoPath, fromDb.shopLogoUri)
        assertEquals("Laxmi", fromDb.shortHeaderName)
        assertEquals("LOGO_AND_NAME", fromDb.headerBrandMode)

        val payload = repo.buildBackupPayload()
        val json = com.example.util.ExportUtils.encodeFullBackupJson(payload)
        val decoded = com.example.util.ExportUtils.decodeFullBackupJson(json)
        assertEquals(savedLogoPath, decoded.settings?.shopLogoUri)
        assertEquals("Laxmi", decoded.settings?.shortHeaderName)
        assertEquals("LOGO_AND_NAME", decoded.settings?.headerBrandMode)

        composeTestRule.setContent {
            SoleBookTheme {
                com.example.ui.components.SoleBookTopBar(
                    title = "Shree Laxmi Narayan Footwear",
                    subtitle = "Today: ₹3,400 (5 bills)",
                    onHelpClick = {},
                    onSettingsClick = {},
                    unreadNoticeCount = 2,
                    onNoticesClick = {},
                    logoUri = savedLogoPath,
                    showLogoSlot = true,
                    headerBrandMode = "LOGO_AND_NAME",
                    onLogoClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("shop_logo_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shree Laxmi Narayan Footwear").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today: ₹3,400 (5 bills)").assertIsDisplayed()

        tempLogo.delete()
        ImageUtils.deleteLogoFile(savedLogoPath)
    }

    @Test
    fun `contact importer parses csv vcard and pasted lines and merges without overwriting stats`() = runTest {
        // Seed an existing customer with real spend & visits
        repo.saveCustomer(
            com.example.data.local.CustomerEntity(
                phone = "9876543210",
                name = "",
                email = "",
                totalSpent = 2400.0,
                visitCount = 3,
                favouriteCategory = "Sports Shoe",
                favouriteSize = "8"
            )
        )

        val mixedRaw = """
            Name,Phone,Email
            Raju Patel,+91 98765-43210,raju@shop.com
            Anita Verma, 9811223344, anita@gmail.com
            09811223344
            BEGIN:VCARD
            FN:Vikram Singh
            TEL;TYPE=CELL:+919988776655
            EMAIL:vikram@mail.com
            END:VCARD
        """.trimIndent()

        val parsed = com.example.domain.CampaignBuilder.parseContactsText(mixedRaw)
        assertEquals(3, parsed.size)
        assertEquals("9876543210", parsed[0].phone)
        assertEquals("raju@shop.com", parsed[0].email)
        assertEquals("Vikram Singh", parsed[2].name)

        val res = repo.importCustomers(parsed, whatsappConsent = true)
        assertEquals(2, res.addedCount)
        assertEquals(1, res.updatedCount)
        assertEquals(0, res.skippedCount)

        // Verify existing stats were preserved while missing name & email were filled
        val raju = db.soleBookDao().getCustomerByPhone("9876543210")!!
        assertEquals("Raju Patel", raju.name)
        assertEquals("raju@shop.com", raju.email)
        assertEquals(2400.0, raju.totalSpent, 0.01)
        assertEquals(3, raju.visitCount)

        // Second import of identical list skips all 3
        val secondRes = repo.importCustomers(parsed, whatsappConsent = true)
        assertEquals(0, secondRes.addedCount)
        assertEquals(0, secondRes.updatedCount)
        assertEquals(3, secondRes.skippedCount)
    }

    @Test
    fun `campaign builder generates article alert with sizes and smart matches target buyers`() {
        val sportShoe = ProductEntity(
            id = 10L, skuCode = "SP-10", name = "Air Mesh Runner",
            category = "Sports Shoe", brand = "Campus", gender = "Men",
            photoUri = "", costPrice = 500.0, sellingPrice = 999.0
        )
        val variantsMap = mapOf(
            10L to listOf(
                com.example.data.local.ProductVariantEntity(1L, 10L, "7", 0),
                com.example.data.local.ProductVariantEntity(2L, 10L, "8", 4),
                com.example.data.local.ProductVariantEntity(3L, 10L, "9", 3)
            )
        )

        val msg = com.example.domain.CampaignBuilder.buildCampaignTemplate(
            preset = com.example.domain.CampaignPreset.HURRY_LIMITED,
            selectedProducts = listOf(sportShoe),
            variantsByProduct = variantsMap,
            currencySymbol = "₹"
        )
        assertTrue(msg.contains("HURRY — LIMITED PAIRS LEFT!"))
        assertTrue(msg.contains("Campus Air Mesh Runner"))
        assertTrue(msg.contains("SP-10"))
        assertTrue(msg.contains("Sizes: 8, 9"))
        assertFalse(msg.contains("Sizes: 7")) // Out-of-stock size excluded

        val cMatch = com.example.data.local.CustomerEntity(
            id = 1L, phone = "9876543210", name = "Raju",
            favouriteCategory = "Sports Shoe", favouriteSize = "8",
            totalSpent = 2000.0, whatsappConsent = true
        )
        val cOther = com.example.data.local.CustomerEntity(
            id = 2L, phone = "9123456789", name = "Meena",
            favouriteCategory = "Chappal", favouriteSize = "6",
            totalSpent = 5000.0, whatsappConsent = true
        )
        val cOptOut = com.example.data.local.CustomerEntity(
            id = 3L, phone = "9000011111", name = "Silent",
            favouriteCategory = "Sports Shoe", favouriteSize = "8",
            whatsappConsent = false
        )

        val matched = com.example.domain.CampaignBuilder.filterAndRankRecipients(
            customers = listOf(cMatch, cOther, cOptOut),
            customerSegments = emptyMap(),
            audienceFilter = com.example.domain.CampaignAudienceFilter.SMART_MATCH,
            selectedProducts = listOf(sportShoe),
            variantsByProduct = variantsMap
        )
        assertEquals(1, matched.size)
        assertEquals("Raju", matched.first().customer.name)
        assertTrue(matched.first().matchReasons.contains("Buys Sports Shoe"))
        assertTrue(matched.first().matchReasons.contains("Size 8 in stock"))
    }

    @Test
    fun `room migration 6 to 7 adds multi industry columns and marks existing populated shop onboarded`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_v6_v7.db"
        context.deleteDatabase(dbName)

        // Create a schema-v6 database with existing products + settings row
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
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
                            mrp REAL NOT NULL,
                            reorderLevel INTEGER NOT NULL,
                            colorTag TEXT NOT NULL DEFAULT '',
                            styleTag TEXT NOT NULL DEFAULT '',
                            rackLocation TEXT NOT NULL DEFAULT '',
                            isRepeatable INTEGER NOT NULL DEFAULT 0,
                            vendorId INTEGER DEFAULT NULL,
                            lotCode TEXT NOT NULL DEFAULT '',
                            isArchived INTEGER NOT NULL DEFAULT 0,
                            createdAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS settings (
                            id INTEGER NOT NULL PRIMARY KEY,
                            shopName TEXT NOT NULL,
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
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS sales (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            billNumber TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            customerPhone TEXT NOT NULL,
                            customerNameSnapshot TEXT NOT NULL,
                            paymentMode TEXT NOT NULL,
                            discountAmount REAL NOT NULL,
                            gstAmount REAL NOT NULL,
                            subtotal REAL NOT NULL,
                            totalRevenue REAL NOT NULL,
                            totalCollected REAL NOT NULL,
                            creditDue REAL NOT NULL,
                            notes TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO settings (id, shopName, currencySymbol, languageCode, gstEnabled, gstRate, gstInclusive, defaultLeadTimeDays, seasonalityMode, seasonalityUpliftPercent, hasCompletedWalkthrough, isDemoLoaded) VALUES (1, 'Legacy Shop', '₹', 'en', 0, 12.0, 1, 3, 'NONE', 0.0, 0, 0)"
                    )
                    db.execSQL(
                        "INSERT INTO products (id, skuCode, name, category, brand, gender, photoUri, costPrice, sellingPrice, mrp, reorderLevel, isArchived, createdAt) VALUES (10, 'SL-10', 'Existing Product', 'Sandal', 'Bata', 'Men', '', 250.0, 499.0, 599.0, 3, 0, 1000)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val v6Helper = FrameworkSQLiteOpenHelperFactory().create(config)
        v6Helper.writableDatabase.close()
        v6Helper.close()

        // Apply MIGRATION_6_7 directly and verify columns + data preservation + onboarding auto-skip
        val helperV7 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        SoleBookDatabase.MIGRATION_6_7.migrate(db)
                    }
                })
                .build()
        )
        val migratedDb = helperV7.writableDatabase
        migratedDb.query("SELECT shopName, businessCategory, customBusinessType, businessChannel, hasCompletedOnboarding FROM settings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Legacy Shop", c.getString(0))
            assertEquals("FOOTWEAR", c.getString(1))
            assertEquals("", c.getString(2))
            assertEquals("PHYSICAL", c.getString(3))
            assertEquals(1, c.getInt(4))
        }
        migratedDb.query("SELECT name, sellingPrice, unitLabel, batchOrSerial, expiryOrWarranty FROM products WHERE id = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Existing Product", c.getString(0))
            assertEquals(499.0, c.getDouble(1), 0.01)
            assertEquals("", c.getString(2))
            assertEquals("", c.getString(3))
            assertEquals("", c.getString(4))
        }
        migratedDb.close()
        helperV7.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `four screen onboarding flow validates business name selects category and invokes completion callbacks`() {
        var capturedResult: List<String>? = null
        composeTestRule.setContent {
            SoleBookTheme {
                OnboardingScreen(
                    onCompleteGoDashboard = { name, cat, custom, currency, channel ->
                        capturedResult = listOf(name, cat, custom, currency, channel)
                    },
                    onCompleteAddFirstProduct = { _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Screen 1 — Welcome
        composeTestRule.onNodeWithText("SOLEBOOK").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_start_btn").performClick()
        composeTestRule.waitForIdle()

        // Screen 2 — Validation check on empty business name
        composeTestRule.onNodeWithTag("onboarding_step2_continue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Please enter your business name to continue.").assertIsDisplayed()

        // Fill business name & search/select Grocery
        composeTestRule.onNodeWithTag("onboarding_business_name").performTextInput("Sharma Kirana & Co")
        composeTestRule.onNodeWithTag("category_search_input").performScrollTo().performTextInput("Grocery")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("category_option_GROCERY").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("onboarding_step2_continue").performClick()
        composeTestRule.waitForIdle()

        // Screen 3 — Preferences
        composeTestRule.onNodeWithTag("channel_option_BOTH").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("onboarding_step3_finish").performClick()
        composeTestRule.waitForIdle()

        // Screen 4 — Ready
        composeTestRule.onNodeWithText("Your business is ready!").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Sharma Kirana & Co").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_go_dashboard_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("Sharma Kirana & Co", "GROCERY", "", "₹", "BOTH"), capturedResult)
    }

    @Test
    fun `home dashboard renders empty state and real populated KPIs cleanly`() {
        val emptyState = SoleBookUiState(
            isLoading = false,
            settings = ShopSettingsEntity(
                shopName = "Metro Electronics",
                businessCategory = "ELECTRONICS",
                hasCompletedOnboarding = true
            ),
            industryConfig = IndustryProfile.resolve("ELECTRONICS"),
            dashboard = DashboardSnapshot(hasAnyBusinessData = false)
        )

        val showPopulated = androidx.compose.runtime.mutableStateOf(false)
        val populatedState = emptyState.copy(
            dashboard = DashboardSnapshot(
                todaySalesRevenue = 4250.0,
                todayBillCount = 3,
                yesterdaySalesRevenue = 3200.0,
                yesterdayHasSales = true,
                todayVsYesterdayPct = 32.8,
                periodGrossProfit = 1950.0,
                periodNetProfit = 1480.0,
                inventoryCostValue = 32000.0,
                customerOutstanding = 950.0,
                lowStockCount = 2,
                outOfStockCount = 1,
                hasAnyBusinessData = true
            )
        )

        composeTestRule.setContent {
            SoleBookTheme {
                HomeDashboardScreen(
                    state = if (showPopulated.value) populatedState else emptyState,
                    viewModel = null,
                    onNewSale = {},
                    onAddProduct = {},
                    onAddPurchase = {},
                    onOpenStockLow = {},
                    onOpenCustomers = {},
                    onOpenMoney = {},
                    onOpenInsights = {},
                    onOpenNotices = {},
                    onOpenSettings = {},
                    onOpenProductDetail = {},
                    onLoadDemoData = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_dashboard_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your business journey starts here.").assertIsDisplayed()

        composeTestRule.runOnUiThread { showPopulated.value = true }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Today's Sales").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹4,250").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 bills recorded today").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_dashboard_list").performScrollToNode(hasText("Low-Stock Products"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("₹1,480").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹32,000").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹950").assertIsDisplayed()
        composeTestRule.onNodeWithText("Low-Stock Products").assertIsDisplayed()
    }

    @Test
    fun `visual matches sheet displays matched product available sizes and clean no-match state`() {
        val matchedProduct = ProductEntity(
            id = 42L,
            skuCode = "SL-042",
            name = "Campus Air Runner",
            category = "Sports Shoe",
            brand = "Campus",
            gender = "Men",
            photoUri = "drawable:img_shoe_running",
            costPrice = 650.0,
            sellingPrice = 1299.0,
            rackLocation = "Rack C-2"
        )
        val variants = listOf(
            com.example.data.local.ProductVariantEntity(id = 1L, productId = 42L, size = "7", stock = 0),
            com.example.data.local.ProductVariantEntity(id = 2L, productId = 42L, size = "8", stock = 5),
            com.example.data.local.ProductVariantEntity(id = 3L, productId = 42L, size = "9", stock = 2)
        )
        val matchState = androidx.compose.runtime.mutableStateOf(
            com.example.ui.viewmodel.VisualMatchUiState(
                isScanning = false,
                detectedColor = "Navy",
                matches = listOf(
                    com.example.domain.VisualMatchResult(
                        product = matchedProduct,
                        similarityPercent = 88,
                        colorName = "Navy",
                        isBestMatch = true
                    )
                ),
                indexedProductCount = 3
            )
        )
        var pickedSize: String? = null
        var pickedProductId: Long? = null

        composeTestRule.setContent {
            SoleBookTheme {
                com.example.ui.screens.VisualMatchesSheet(
                    matchState = matchState.value,
                    categories = listOf("All", "Sports Shoe"),
                    availableSizes = listOf("7", "8", "9"),
                    initialSizeFilter = null,
                    allProducts = listOf(matchedProduct),
                    allVariants = variants,
                    variantStockMap = mapOf(42L to mapOf("7" to 0, "8" to 5, "9" to 2)),
                    currency = "₹",
                    industryConfig = IndustryProfile.resolve("FOOTWEAR"),
                    onDismiss = {},
                    onRetakePhoto = {},
                    onSelectProduct = { prod, sz ->
                        pickedProductId = prod.id
                        pickedSize = sz
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("BEST MATCH").assertIsDisplayed()
        composeTestRule.onNodeWithText("88% Match").assertIsDisplayed()
        composeTestRule.onNodeWithText("Campus Air Runner").assertIsDisplayed()
        composeTestRule.onNodeWithText("7 (Out)").assertIsDisplayed()
        composeTestRule.onNodeWithText("8 (5)").assertIsDisplayed()
        composeTestRule.onNodeWithText("9 (2)").assertIsDisplayed()

        // Tapping size 8 chip immediately selects product + size 8
        composeTestRule.onNodeWithTag("visual_size_chip_SL-042_8").performClick()
        assertEquals(42L, pickedProductId)
        assertEquals("8", pickedSize)

        // Switching to empty match state displays honest no-match card with Retake button
        composeTestRule.runOnUiThread {
            matchState.value = com.example.ui.viewmodel.VisualMatchUiState(
                isScanning = false,
                detectedColor = "Green",
                matches = emptyList(),
                indexedProductCount = 3
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("visual_match_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("No matching product found").assertIsDisplayed()
        composeTestRule.onNodeWithTag("visual_empty_retake_button").assertIsDisplayed()
    }
}
