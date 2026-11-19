package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.CartLineRequest
import com.example.data.repository.SoleBookRepository
import com.example.data.repository.WholesaleRepository
import com.example.domain.*
import com.example.notifications.SmartNoticeWorker
import com.example.notifications.SoleBookNotificationHelper
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class MoneyPeriod(val label: String) {
    TODAY("Today"),
    WEEK("Week"),
    MONTH("Month"),
    CUSTOM("Custom")
}

data class CartItemUi(
    val id: String,
    val product: ProductEntity,
    val size: String,
    val quantity: Int,
    val unitSellingPrice: Double,
    val maxAvailable: Int
)

data class UndoSaleBanner(
    val saleId: Long,
    val billNumber: String,
    val totalAmount: Double,
    val itemCount: Int
)

data class VisualMatchUiState(
    val isScanning: Boolean = false,
    val queryPhotoUri: String? = null,
    val detectedColor: String = "",
    val matches: List<VisualMatchResult> = emptyList(),
    val fallbackGuesses: List<VisualMatchResult> = emptyList(),
    val indexedProductCount: Int = 0
)

data class DashboardSnapshot(
    val todaySalesRevenue: Double = 0.0,
    val todayBillCount: Int = 0,
    val yesterdaySalesRevenue: Double = 0.0,
    val yesterdayHasSales: Boolean = false,
    val todayVsYesterdayPct: Double? = null,
    val periodGrossProfit: Double = 0.0,
    val periodNetProfit: Double = 0.0,
    val inventoryCostValue: Double = 0.0,
    val inventoryRetailValue: Double = 0.0,
    val customerOutstanding: Double = 0.0,
    val supplierOutstanding: Double = 0.0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val cashInflowSales: Double = 0.0,
    val upiCardInflowSales: Double = 0.0,
    val trackedOutflows: Double = 0.0,
    val hasAnyBusinessData: Boolean = false
)

data class SoleBookUiState(
    val isLoading: Boolean = true,
    val settings: ShopSettingsEntity = ShopSettingsEntity(
        shopName = "",
        businessCategory = "GENERAL",
        hasCompletedOnboarding = false
    ),
    val industryConfig: IndustryConfig = IndustryProfile.resolve("GENERAL"),
    val strings: AppStrings = StringsRegistry.forCode("en"),
    val products: List<ProductEntity> = emptyList(),
    val variants: List<ProductVariantEntity> = emptyList(),
    val fingerprints: Map<Long, ProductFingerprintEntity> = emptyMap(),
    val sales: List<SaleEntity> = emptyList(),
    val saleItems: List<SaleItemEntity> = emptyList(),
    val returns: List<ReturnEntity> = emptyList(),
    val purchases: List<PurchaseEntity> = emptyList(),
    val vendors: List<VendorEntity> = emptyList(),
    val vendorPayments: List<VendorPaymentEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val segments: List<SegmentEntity> = emptyList(),
    val templates: List<MessageTemplateEntity> = emptyList(),
    val campaignLogs: List<CampaignLogEntity> = emptyList(),
    val productMetrics: Map<Long, ProductMetrics> = emptyMap(),
    val moneyPeriod: MoneyPeriod = MoneyPeriod.MONTH,
    val customStartMs: Long = Formatters.startOfMonth(),
    val customEndMs: Long = Formatters.endOfDay(),
    val moneySummary: MoneySummary? = null,
    val dashboard: DashboardSnapshot = DashboardSnapshot(),
    val insightsReport: InsightsReport? = null,
    val customerSegments: Map<Long, Set<CustomerSegmentType>> = emptyMap(),
    val cart: List<CartItemUi> = emptyList(),
    val undoBanner: UndoSaleBanner? = null,
    val visualMatchState: VisualMatchUiState? = null,
    val priorityNotices: List<PriorityNotice> = emptyList(),
    val unreadNoticeCount: Int = 0,
    val toastMessage: String? = null,
    val channelBreakdown: AnalyticsEngine.ChannelFinancialBreakdown = AnalyticsEngine.ChannelFinancialBreakdown(0.0, 0.0, 0, 0.0, 0.0, 0),
    val rawMaterials: List<RawMaterialEntity> = emptyList(),
    val bomTemplates: List<BomTemplateEntity> = emptyList(),
    val bomMaterialRules: List<BomMaterialRuleEntity> = emptyList(),
    val wholesaleQuotes: List<WholesaleQuoteEntity> = emptyList(),
    val wholesaleQuoteLines: List<WholesaleQuoteLineEntity> = emptyList(),
    val purchaseOrders: List<PurchaseOrderEntity> = emptyList()
)

class SoleBookViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SoleBookDatabase.getInstance(application)
    private val repository = SoleBookRepository(db)
    private val wholesaleRepository = WholesaleRepository(db)

    private val _cart = MutableStateFlow<List<CartItemUi>>(emptyList())
    private val _moneyPeriod = MutableStateFlow(MoneyPeriod.MONTH)
    private val _customRange = MutableStateFlow(Formatters.startOfMonth() to Formatters.endOfDay())
    private val _undoBanner = MutableStateFlow<UndoSaleBanner?>(null)
    private val _toastMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _visualMatchState = MutableStateFlow<VisualMatchUiState?>(null)

    val onboardingHub = OnboardingHubController(
        application = application,
        db = db,
        repository = repository,
        scope = viewModelScope,
        onToast = { msg -> _toastMessage.value = msg }
    )
    val wholesaleController = WholesaleController(
        wholesaleRepo = wholesaleRepository,
        scope = viewModelScope,
        onToast = { msg -> _toastMessage.value = msg }
    )
    val dataHubState: StateFlow<DataHubUiState> get() = onboardingHub.state

    private data class CoreSnapshot(
        val products: List<ProductEntity>,
        val variants: List<ProductVariantEntity>,
        val fingerprints: List<ProductFingerprintEntity>,
        val sales: List<SaleEntity>,
        val saleItems: List<SaleItemEntity>,
        val returns: List<ReturnEntity>
    )

    private data class LedgerSnapshot(
        val purchases: List<PurchaseEntity>,
        val expenses: List<ExpenseEntity>,
        val customers: List<CustomerEntity>,
        val segments: List<SegmentEntity>,
        val templates: List<MessageTemplateEntity>,
        val campaigns: List<CampaignLogEntity>,
        val noticeStates: List<NoticeStateEntity>,
        val settings: ShopSettingsEntity
    )

    private val coreFlow = combine(
        repository.productsFlow,
        repository.variantsFlow,
        repository.fingerprintsFlow,
        repository.salesFlow,
        repository.saleItemsFlow,
        repository.returnsFlow
    ) { array: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        CoreSnapshot(
            products = array[0] as List<ProductEntity>,
            variants = array[1] as List<ProductVariantEntity>,
            fingerprints = array[2] as List<ProductFingerprintEntity>,
            sales = array[3] as List<SaleEntity>,
            saleItems = array[4] as List<SaleItemEntity>,
            returns = array[5] as List<ReturnEntity>
        )
    }

    private val ledgerFlow = combine(
        repository.purchasesFlow,
        repository.expensesFlow,
        repository.customersFlow,
        repository.segmentsFlow,
        repository.templatesFlow,
        repository.campaignLogsFlow,
        repository.noticeStatesFlow,
        repository.settingsFlow
    ) { array: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        LedgerSnapshot(
            purchases = array[0] as List<PurchaseEntity>,
            expenses = array[1] as List<ExpenseEntity>,
            customers = array[2] as List<CustomerEntity>,
            segments = array[3] as List<SegmentEntity>,
            templates = array[4] as List<MessageTemplateEntity>,
            campaigns = array[5] as List<CampaignLogEntity>,
            noticeStates = array[6] as List<NoticeStateEntity>,
            settings = array[7] as ShopSettingsEntity
        )
    }

    private val vendorFlow = combine(
        repository.vendorsFlow,
        repository.vendorPaymentsFlow
    ) { vendors, payments -> vendors to payments }

    private val uiControlsFlow = combine(
        _cart,
        _moneyPeriod,
        _customRange,
        _undoBanner,
        _visualMatchState,
        _toastMessage
    ) { array: Array<Any?> ->
        array
    }

    private data class WholesaleSnapshot(
        val rawMaterials: List<RawMaterialEntity>,
        val bomTemplates: List<BomTemplateEntity>,
        val bomRules: List<BomMaterialRuleEntity>,
        val quotes: List<WholesaleQuoteEntity>,
        val quoteLines: List<WholesaleQuoteLineEntity>,
        val purchaseOrders: List<PurchaseOrderEntity>
    )

    private val wholesaleFlow = combine(
        repository.rawMaterialsFlow,
        repository.bomTemplatesFlow,
        repository.bomRulesFlow,
        repository.wholesaleQuotesFlow,
        repository.wholesaleQuoteLinesFlow,
        repository.purchaseOrdersFlow
    ) { array: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        WholesaleSnapshot(
            rawMaterials = array[0] as List<RawMaterialEntity>,
            bomTemplates = array[1] as List<BomTemplateEntity>,
            bomRules = array[2] as List<BomMaterialRuleEntity>,
            quotes = array[3] as List<WholesaleQuoteEntity>,
            quoteLines = array[4] as List<WholesaleQuoteLineEntity>,
            purchaseOrders = array[5] as List<PurchaseOrderEntity>
        )
    }

    private val vendorAndWholesaleFlow = combine(vendorFlow, wholesaleFlow) { v, w -> v to w }

    val uiState: StateFlow<SoleBookUiState> = combine(
        coreFlow,
        ledgerFlow,
        vendorAndWholesaleFlow,
        uiControlsFlow,
        _isLoading
    ) { core, ledger, vendorAndWholesale, controls, loading ->
        @Suppress("UNCHECKED_CAST")
        val cart = controls[0] as List<CartItemUi>
        val period = controls[1] as MoneyPeriod
        @Suppress("UNCHECKED_CAST")
        val range = controls[2] as Pair<Long, Long>
        val undo = controls[3] as UndoSaleBanner?
        val visualMatch = controls[4] as VisualMatchUiState?
        val toast = controls[5] as String?
        val vendors = vendorAndWholesale.first.first
        val vendorPayments = vendorAndWholesale.first.second
        val wholesale = vendorAndWholesale.second

        withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val metrics = AnalyticsEngine.computeProductMetrics(
                products = core.products,
                variants = core.variants,
                sales = core.sales,
                saleItems = core.saleItems,
                returns = core.returns,
                now = now
            )

            val (pStart, pEnd, pLabel) = when (period) {
                MoneyPeriod.TODAY -> Triple(Formatters.startOfDay(now), Formatters.endOfDay(now), "Today")
                MoneyPeriod.WEEK -> Triple(Formatters.startOfWeek(now), Formatters.endOfDay(now), "Last 7 Days")
                MoneyPeriod.MONTH -> Triple(Formatters.startOfMonth(now), Formatters.endOfDay(now), "This Month")
                MoneyPeriod.CUSTOM -> Triple(
                    Formatters.startOfDay(range.first),
                    Formatters.endOfDay(range.second),
                    "${Formatters.formatShortDate(range.first)} – ${Formatters.formatShortDate(range.second)}"
                )
            }

            val moneySummary = AnalyticsEngine.computeMoneySummary(
                periodLabel = pLabel,
                startTime = pStart,
                endTime = pEnd,
                products = core.products,
                variants = core.variants,
                sales = core.sales,
                saleItems = core.saleItems,
                returns = core.returns,
                expenses = ledger.expenses,
                productMetrics = metrics,
                now = now
            )

            val insights = AnalyticsEngine.computeInsightsReport(
                products = core.products,
                variants = core.variants,
                sales = core.sales,
                saleItems = core.saleItems,
                returns = core.returns,
                settings = ledger.settings,
                productMetrics = metrics,
                now = now,
                vendors = vendors
            )

            val custSegments = ledger.customers.associate { c ->
                c.id to AnalyticsEngine.classifyCustomerSegments(c, ledger.customers, now)
            }

            val noticeStateMap = ledger.noticeStates.associateBy { it.noticeKey }
            val notices = NoticeEngine.generateNotices(
                products = core.products,
                variants = core.variants,
                sales = core.sales,
                saleItems = core.saleItems,
                productMetrics = metrics,
                noticeStates = noticeStateMap,
                settings = ledger.settings,
                now = now,
                buyingBuckets = insights.buyingBuckets
            )
            val unreadCount = notices.count { !it.isRead }

            val industryConfig = IndustryProfile.resolve(
                categoryKey = ledger.settings.businessCategory,
                customBusinessType = ledger.settings.customBusinessType
            )

            // Real Home Dashboard calculations (zero fabricated trends)
            val todayStart = Formatters.startOfDay(now)
            val todayEnd = Formatters.endOfDay(now)
            val yestStart = todayStart - 86_400_000L
            val yestEnd = todayStart - 1L

            val todaySales = core.sales.filter { it.timestamp in todayStart..todayEnd }
            val todayReturns = core.returns.filter { it.type == "RETURN" && it.timestamp in todayStart..todayEnd }
            val todaySalesRev = (todaySales.sumOf { it.totalRevenue } - todayReturns.sumOf { it.refundAmount }).coerceAtLeast(0.0)

            val yestSales = core.sales.filter { it.timestamp in yestStart..yestEnd }
            val yestReturns = core.returns.filter { it.type == "RETURN" && it.timestamp in yestStart..yestEnd }
            val yestSalesRev = (yestSales.sumOf { it.totalRevenue } - yestReturns.sumOf { it.refundAmount }).coerceAtLeast(0.0)
            val yesterdayHasData = yestSales.isNotEmpty()
            val todayPct = if (yesterdayHasData && yestSalesRev > 1.0) {
                ((todaySalesRev - yestSalesRev) / yestSalesRev) * 100.0
            } else null

            val activeMetrics = metrics.values.filter { !it.product.isArchived }
            val lowCount = activeMetrics.count { ProductBadge.LOW_STOCK in it.badges }
            val outCount = activeMetrics.count { ProductBadge.OUT_OF_STOCK in it.badges }
            val custOutstanding = ledger.customers.sumOf { it.creditBalance.coerceAtLeast(0.0) }
            val suppOutstanding = vendors.sumOf { it.creditBalance.coerceAtLeast(0.0) }

            val cashInflow = core.sales
                .filter { it.paymentMode == "CASH" }
                .sumOf { it.totalCollected } -
                core.returns.filter { it.type == "RETURN" }.sumOf { it.refundAmount }
            val digitalInflow = core.sales
                .filter { it.paymentMode == "UPI" || it.paymentMode == "CARD" }
                .sumOf { it.totalCollected }
            val totalExpensesAll = ledger.expenses.sumOf { it.amount }
            val totalVendorPaidAll = vendorPayments.sumOf { it.amount }

            val dashboardSnapshot = DashboardSnapshot(
                todaySalesRevenue = todaySalesRev,
                todayBillCount = todaySales.size,
                yesterdaySalesRevenue = yestSalesRev,
                yesterdayHasSales = yesterdayHasData,
                todayVsYesterdayPct = todayPct,
                periodGrossProfit = moneySummary.grossProfit,
                periodNetProfit = moneySummary.netProfit,
                inventoryCostValue = moneySummary.totalStockCostValue,
                inventoryRetailValue = moneySummary.totalStockRetailValue,
                customerOutstanding = custOutstanding,
                supplierOutstanding = suppOutstanding,
                lowStockCount = lowCount,
                outOfStockCount = outCount,
                cashInflowSales = cashInflow,
                upiCardInflowSales = digitalInflow,
                trackedOutflows = totalExpensesAll + totalVendorPaidAll,
                hasAnyBusinessData = core.products.isNotEmpty() || core.sales.isNotEmpty() || ledger.expenses.isNotEmpty() || ledger.purchases.isNotEmpty()
            )

            val channelBreakdown = AnalyticsEngine.computeChannelBreakdown(
                startTime = pStart,
                endTime = pEnd,
                sales = core.sales,
                saleItems = core.saleItems,
                returns = core.returns
            )

            SoleBookUiState(
                isLoading = loading,
                settings = ledger.settings,
                industryConfig = industryConfig,
                strings = StringsRegistry.forCode(ledger.settings.languageCode),
                products = core.products,
                variants = core.variants,
                fingerprints = core.fingerprints.associateBy { it.productId },
                sales = core.sales,
                saleItems = core.saleItems,
                returns = core.returns,
                purchases = ledger.purchases,
                vendors = vendors,
                vendorPayments = vendorPayments,
                expenses = ledger.expenses,
                customers = ledger.customers,
                segments = ledger.segments,
                templates = ledger.templates,
                campaignLogs = ledger.campaigns,
                productMetrics = metrics,
                moneyPeriod = period,
                customStartMs = range.first,
                customEndMs = range.second,
                moneySummary = moneySummary,
                dashboard = dashboardSnapshot,
                insightsReport = insights,
                customerSegments = custSegments,
                cart = cart,
                undoBanner = undo,
                visualMatchState = visualMatch,
                priorityNotices = notices,
                unreadNoticeCount = unreadCount,
                toastMessage = toast,
                channelBreakdown = channelBreakdown,
                rawMaterials = wholesale.rawMaterials,
                bomTemplates = wholesale.bomTemplates,
                bomMaterialRules = wholesale.bomRules,
                wholesaleQuotes = wholesale.quotes,
                wholesaleQuoteLines = wholesale.quoteLines,
                purchaseOrders = wholesale.purchaseOrders
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SoleBookUiState()
    )

    init {
        viewModelScope.launch {
            try {
                repository.ensureDefaultsInitialized()
                val ctx = getApplication<Application>()
                SoleBookNotificationHelper.ensureChannels(ctx)
                withContext(Dispatchers.Default) {
                    repository.indexFingerprintsForProducts(ctx)
                }
                val currentSettings = repository.settingsFlow.first()
                SmartNoticeWorker.schedulePeriodic(ctx, currentSettings.alertIntervalHours, currentSettings.alertsEnabled)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun dismissUndoBanner() {
        _undoBanner.value = null
    }

    fun completeOnboarding(
        shopName: String,
        businessCategory: String,
        customBusinessType: String,
        currencySymbol: String,
        businessChannel: String,
        businessOperationMode: String = "RETAIL"
    ) {
        viewModelScope.launch {
            val current = uiState.value.settings
            val cleanedName = shopName.trim()
            if (cleanedName.isBlank()) return@launch
            val opMode = BusinessOperationMode.fromKey(businessOperationMode).key
            val startWorkspace = if (opMode == BusinessOperationMode.WHOLESALE.key) {
                WorkspaceMode.WHOLESALE.key
            } else {
                WorkspaceMode.RETAIL.key
            }
            repository.saveSettings(
                current.copy(
                    shopName = cleanedName,
                    businessCategory = businessCategory,
                    customBusinessType = if (businessCategory == "OTHER") customBusinessType.trim() else "",
                    currencySymbol = currencySymbol.ifBlank { "₹" },
                    businessChannel = businessChannel.ifBlank { "PHYSICAL" },
                    businessOperationMode = opMode,
                    activeWorkspaceMode = startWorkspace,
                    hasCompletedOnboarding = true,
                    hasCompletedWalkthrough = true
                )
            )
        }
    }

    fun switchWorkspaceMode(targetMode: WorkspaceMode) {
        viewModelScope.launch {
            val current = uiState.value.settings
            val upgradedOpMode = if (current.businessOperationMode != BusinessOperationMode.BOTH.key) {
                BusinessOperationMode.BOTH.key
            } else {
                current.businessOperationMode
            }
            if (current.activeWorkspaceMode == targetMode.key &&
                current.businessOperationMode == upgradedOpMode
            ) {
                return@launch
            }
            repository.saveSettings(
                current.copy(
                    businessOperationMode = upgradedOpMode,
                    activeWorkspaceMode = targetMode.key
                )
            )
        }
    }

    fun updateBusinessProfile(
        shopName: String,
        businessCategory: String,
        customBusinessType: String,
        businessChannel: String,
        currencySymbol: String,
        businessOperationMode: String? = null
    ) {
        viewModelScope.launch {
            val current = uiState.value.settings
            val cleanedName = shopName.trim().ifBlank { current.shopName.ifBlank { "My Shop" } }
            val resolvedOpMode = businessOperationMode?.let { BusinessOperationMode.fromKey(it).key }
                ?: current.businessOperationMode
            val resolvedWorkspace = when (resolvedOpMode) {
                BusinessOperationMode.RETAIL.key -> WorkspaceMode.RETAIL.key
                BusinessOperationMode.WHOLESALE.key -> WorkspaceMode.WHOLESALE.key
                else -> current.activeWorkspaceMode.ifBlank { WorkspaceMode.RETAIL.key }
            }
            repository.saveSettings(
                current.copy(
                    shopName = cleanedName,
                    businessCategory = businessCategory,
                    customBusinessType = if (businessCategory == "OTHER") customBusinessType.trim() else "",
                    businessChannel = businessChannel,
                    currencySymbol = currencySymbol,
                    businessOperationMode = resolvedOpMode,
                    activeWorkspaceMode = resolvedWorkspace
                )
            )
            _toastMessage.value = "Business profile updated"
        }
    }

    fun markWalkthroughCompleted() {
        viewModelScope.launch {
            repository.saveSettings(
                uiState.value.settings.copy(
                    hasCompletedWalkthrough = true,
                    hasCompletedOnboarding = true
                )
            )
        }
    }

    fun replayWalkthrough() {
        viewModelScope.launch {
            repository.saveSettings(uiState.value.settings.copy(hasCompletedWalkthrough = false))
        }
    }

    fun updateSettings(updated: ShopSettingsEntity) {
        viewModelScope.launch {
            repository.saveSettings(updated)
            SmartNoticeWorker.schedulePeriodic(getApplication(), updated.alertIntervalHours, updated.alertsEnabled)
            _toastMessage.value = "Settings saved"
        }
    }

    fun saveShopLogoFromUri(uri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val oldPath = uiState.value.settings.shopLogoUri
            val newPath = withContext(Dispatchers.IO) {
                com.example.util.ImageUtils.compressAndSaveLogo(ctx, uri)
            }
            if (newPath != null) {
                if (oldPath.isNotBlank() && oldPath != newPath) {
                    withContext(Dispatchers.IO) { com.example.util.ImageUtils.deleteLogoFile(oldPath) }
                }
                val current = uiState.value.settings
                val nextMode = if (current.headerBrandMode == "NAME_ONLY") "LOGO_AND_NAME" else current.headerBrandMode
                repository.saveSettings(current.copy(shopLogoUri = newPath, headerBrandMode = nextMode))
                _toastMessage.value = "Business logo updated"
            } else {
                _toastMessage.value = "Could not load selected logo image"
            }
        }
    }

    fun removeShopLogo() {
        viewModelScope.launch {
            val current = uiState.value.settings
            if (current.shopLogoUri.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    com.example.util.ImageUtils.deleteLogoFile(current.shopLogoUri)
                }
            }
            val nextMode = if (current.headerBrandMode == "LOGO_ONLY") "LOGO_AND_NAME" else current.headerBrandMode
            repository.saveSettings(current.copy(shopLogoUri = "", headerBrandMode = nextMode))
            _toastMessage.value = "Business logo removed"
        }
    }

    fun saveShopBranding(
        shopName: String,
        shortHeaderName: String,
        headerBrandMode: String
    ) {
        viewModelScope.launch {
            val current = uiState.value.settings
            val cleanedName = shopName.trim().ifBlank { current.shopName.ifBlank { "My Shop" } }
            val cleanedShort = shortHeaderName.trim()
            repository.saveSettings(
                current.copy(
                    shopName = cleanedName,
                    shortHeaderName = cleanedShort,
                    headerBrandMode = headerBrandMode
                )
            )
            _toastMessage.value = "Business name & logo style saved"
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.loadDemoData()
            withContext(Dispatchers.Default) {
                repository.indexFingerprintsForProducts(getApplication())
            }
            _cart.value = emptyList()
            _isLoading.value = false
            _toastMessage.value = "Demo shop loaded: 15 products, 60 days sales, 10 customers"
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.clearAllData()
            _cart.value = emptyList()
            _undoBanner.value = null
            _isLoading.value = false
            _toastMessage.value = "All shop data cleared"
        }
    }

    // Cart & Sale Actions
    fun addToCart(product: ProductEntity, size: String, quantity: Int, unitSellingPrice: Double, maxAvailable: Int) {
        val current = _cart.value.toMutableList()
        val key = "${product.id}_$size"
        val existingIdx = current.indexOfFirst { it.id == key }
        if (existingIdx >= 0) {
            val old = current[existingIdx]
            val newQty = (old.quantity + quantity).coerceAtMost(maxAvailable)
            current[existingIdx] = old.copy(quantity = newQty, unitSellingPrice = unitSellingPrice)
        } else {
            current += CartItemUi(
                id = key,
                product = product,
                size = size,
                quantity = quantity.coerceIn(1, maxAvailable),
                unitSellingPrice = unitSellingPrice,
                maxAvailable = maxAvailable
            )
        }
        _cart.value = current
        val variantLabel = uiState.value.industryConfig.variantLabel
        _toastMessage.value = "Added ${product.skuCode} ($variantLabel $size) to bill"
    }

    fun updateCartItemQty(id: String, newQty: Int) {
        val current = _cart.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            if (newQty <= 0) {
                current.removeAt(idx)
            } else {
                val item = current[idx]
                current[idx] = item.copy(quantity = newQty.coerceAtMost(item.maxAvailable))
            }
            _cart.value = current
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
    }

    fun completeQuickSingleSale(
        product: ProductEntity,
        size: String,
        quantity: Int,
        unitSellingPrice: Double,
        paymentMode: String,
        discountType: String,
        discountInput: Double,
        customerPhone: String,
        customerName: String
    ) {
        val lines = listOf(CartLineRequest(product, size, quantity, unitSellingPrice))
        executeSale(lines, paymentMode, discountType, discountInput, customerPhone, customerName, clearCartAfter = false)
    }

    fun completeCartCheckout(
        paymentMode: String,
        discountType: String,
        discountInput: Double,
        customerPhone: String,
        customerName: String
    ) {
        val lines = _cart.value.map {
            CartLineRequest(it.product, it.size, it.quantity, it.unitSellingPrice)
        }
        executeSale(lines, paymentMode, discountType, discountInput, customerPhone, customerName, clearCartAfter = true)
    }

    private fun executeSale(
        lines: List<CartLineRequest>,
        paymentMode: String,
        discountType: String,
        discountInput: Double,
        customerPhone: String,
        customerName: String,
        clearCartAfter: Boolean
    ) {
        viewModelScope.launch {
            val result = repository.completeSale(
                lines = lines,
                paymentMode = paymentMode,
                discountType = discountType,
                discountInput = discountInput,
                customerPhone = customerPhone,
                customerName = customerName
            )
            result.onSuccess { saleId ->
                if (clearCartAfter) _cart.value = emptyList()
                val subtotal = lines.sumOf { it.unitSellingPrice * it.quantity }
                val totalQty = lines.sumOf { it.quantity }
                val discountVal = if (discountType == "PERCENT") subtotal * (discountInput / 100.0) else discountInput
                _undoBanner.value = UndoSaleBanner(
                    saleId = saleId,
                    billNumber = "Sale recorded",
                    totalAmount = (subtotal - discountVal).coerceAtLeast(0.0),
                    itemCount = totalQty
                )
                if (uiState.value.settings.alertsEnabled) {
                    SmartNoticeWorker.triggerImmediateCheck(getApplication(), allowImmediateSale = true)
                }
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Could not complete sale"
            }
        }
    }

    fun undoLastSale(saleId: Long) {
        viewModelScope.launch {
            repository.undoSale(saleId)
            _undoBanner.value = null
            _toastMessage.value = "Sale undone — stock & profit restored"
        }
    }

    fun processReturnOrExchange(
        saleId: Long,
        saleItem: SaleItemEntity,
        quantity: Int,
        isExchange: Boolean,
        exchangeSize: String?,
        reason: String
    ) {
        viewModelScope.launch {
            val res = repository.processReturnOrExchange(saleId, saleItem, quantity, isExchange, exchangeSize, reason)
            res.onSuccess {
                _toastMessage.value = if (isExchange) "Size exchanged & stock updated" else "Return processed & stock restored"
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Return failed"
            }
        }
    }

    // Priority Notices & Phone Alerts Actions
    fun dismissNotice(notice: PriorityNotice) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val sevenDaysMs = 7L * 86_400_000L
            repository.upsertNoticeState(
                NoticeStateEntity(
                    noticeKey = notice.noticeKey,
                    lastNotifiedAt = now,
                    snoozedUntil = 0L,
                    dismissedUntil = now + sevenDaysMs,
                    lastSignatureHash = notice.signatureHash,
                    isRead = true
                )
            )
            _toastMessage.value = "Notice marked done"
        }
    }

    fun snoozeNotice(notice: PriorityNotice, hours: Int = 24) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.upsertNoticeState(
                NoticeStateEntity(
                    noticeKey = notice.noticeKey,
                    lastNotifiedAt = now,
                    snoozedUntil = now + hours * 3_600_000L,
                    dismissedUntil = 0L,
                    lastSignatureHash = notice.signatureHash,
                    isRead = true
                )
            )
            _toastMessage.value = "Snoozed for ${hours}h"
        }
    }

    fun markAllNoticesRead() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val unread = uiState.value.priorityNotices.filter { !it.isRead }
            if (unread.isEmpty()) return@launch
            val updates = unread.map { n ->
                NoticeStateEntity(
                    noticeKey = n.noticeKey,
                    lastNotifiedAt = now,
                    snoozedUntil = 0L,
                    dismissedUntil = 0L,
                    lastSignatureHash = n.signatureHash,
                    isRead = true
                )
            }
            repository.upsertNoticeStates(updates)
        }
    }

    fun applySuggestedPrice(productId: Long, newPrice: Double, noticeKey: String?) {
        viewModelScope.launch {
            repository.updateSellingPrice(productId, newPrice)
            if (!noticeKey.isNullOrBlank()) {
                val now = System.currentTimeMillis()
                repository.upsertNoticeState(
                    NoticeStateEntity(
                        noticeKey = noticeKey,
                        lastNotifiedAt = now,
                        snoozedUntil = 0L,
                        dismissedUntil = now + 7L * 86_400_000L,
                        lastSignatureHash = "repriced:${newPrice.toInt()}",
                        isRead = true
                    )
                )
            }
            _toastMessage.value = "Selling price updated to ${Formatters.formatCurrency(newPrice, uiState.value.settings.currencySymbol)}"
        }
    }

    fun sendTestPhoneNotification() {
        val ctx = getApplication<Application>()
        if (!SoleBookNotificationHelper.hasNotificationPermission(ctx)) {
            _toastMessage.value = "Allow phone notification permission first"
            return
        }
        val topNotice = uiState.value.priorityNotices.firstOrNull()
            ?: PriorityNotice(
                noticeKey = "TEST_PREVIEW",
                category = NoticeCategory.LOW_STOCK,
                priorityScore = 90,
                title = "SoleBook Alerts Ready",
                message = "You will get pop-ups here for out-of-stock sizes, high demand, dead stock, and margin tips.",
                actionLabel = "Open Inbox",
                actionType = NoticeActionType.OPEN_SELL
            )
        val posted = SoleBookNotificationHelper.postPriorityNotice(ctx, topNotice)
        _toastMessage.value = if (posted) "Pop-up notification sent to your phone!" else "Could not post notification"
    }

    // Offline Visual Match Actions
    fun findVisualMatchesFromUri(uri: Uri) {
        viewModelScope.launch {
            _visualMatchState.value = VisualMatchUiState(isScanning = true, queryPhotoUri = uri.toString())
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.Default) {
                val freshFingerprints = repository.indexFingerprintsForProducts(context)
                val freshProducts = repository.getAllProductsSync()
                val bmp = OfflineVisualMatcher.decodeQueryBitmapFromUri(context, uri)
                if (bmp == null) {
                    null
                } else {
                    val (queryViews, domColor) = OfflineVisualMatcher.extractQuerySignatures(bmp)
                    bmp.recycle()
                    val outcome = OfflineVisualMatcher.findBestMatches(
                        queryViews = queryViews,
                        queryDominantColor = domColor,
                        products = freshProducts,
                        fingerprints = freshFingerprints
                    )
                    VisualMatchUiState(
                        isScanning = false,
                        queryPhotoUri = uri.toString(),
                        detectedColor = domColor,
                        matches = outcome.matches,
                        fallbackGuesses = outcome.fallbackGuesses,
                        indexedProductCount = outcome.totalIndexedProducts
                    )
                }
            }
            if (result == null) {
                _visualMatchState.value = null
                _toastMessage.value = "Could not read photo for matching"
            } else {
                _visualMatchState.value = result
            }
        }
    }

    fun findVisualMatchesFromFile(file: File) {
        findVisualMatchesFromUri(Uri.fromFile(file))
    }

    fun clearVisualMatches() {
        _visualMatchState.value = null
    }

    // Stock Actions
    fun saveProduct(product: ProductEntity, sizeStocks: Map<String, Int>) {
        viewModelScope.launch {
            val savedId = repository.saveProductWithVariants(product, sizeStocks)
            withContext(Dispatchers.Default) {
                repository.indexFingerprintsForProducts(getApplication(), listOf(product.copy(id = savedId)))
            }
            _toastMessage.value = "${product.skuCode} saved"
        }
    }

    fun toggleArchiveProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.toggleArchiveProduct(product)
            _toastMessage.value = if (product.isArchived) "${product.skuCode} restored to active stock" else "${product.skuCode} archived"
        }
    }

    fun duplicateProduct(product: ProductEntity) {
        viewModelScope.launch {
            val existingSkus = uiState.value.products.map { it.skuCode }
            val nextSku = Formatters.generateNextSku(existingSkus, uiState.value.industryConfig.defaultSkuPrefix)
            val newId = repository.duplicateProduct(product, nextSku)
            withContext(Dispatchers.Default) {
                repository.indexFingerprintsForProducts(getApplication(), listOf(product.copy(id = newId, skuCode = nextSku)))
            }
            _toastMessage.value = "Duplicated as $nextSku"
        }
    }

    fun restockProduct(
        productId: Long,
        addedPerSize: Map<String, Int>,
        newUnitCost: Double,
        supplier: String,
        notes: String,
        vendorId: Long? = null,
        paidAmount: Double? = null
    ) {
        viewModelScope.launch {
            repository.restockProduct(
                productId = productId,
                addedPerSize = addedPerSize,
                newUnitCost = newUnitCost,
                supplier = supplier,
                notes = notes,
                vendorId = vendorId,
                paidAmount = paidAmount
            )
            _toastMessage.value = "Stock updated with weighted-average cost"
        }
    }

    fun saveFreshLotFromVendor(
        product: ProductEntity,
        sizeStocks: Map<String, Int>,
        paidAmount: Double?,
        notes: String
    ) {
        viewModelScope.launch {
            val savedId = repository.saveFreshLotWithVendor(product, sizeStocks, paidAmount, notes)
            withContext(Dispatchers.Default) {
                repository.indexFingerprintsForProducts(getApplication(), listOf(product.copy(id = savedId)))
            }
            _toastMessage.value = "Fresh lot ${product.skuCode} logged & stocked"
        }
    }

    fun retireSoldOutLot(productId: Long, noticeKey: String? = null) {
        viewModelScope.launch {
            repository.retireProduct(productId)
            val now = System.currentTimeMillis()
            val statesToDismiss = mutableListOf<NoticeStateEntity>()
            if (!noticeKey.isNullOrBlank()) {
                statesToDismiss += NoticeStateEntity(
                    noticeKey = noticeKey,
                    lastNotifiedAt = now,
                    dismissedUntil = now + 30L * 86_400_000L,
                    lastSignatureHash = "retired",
                    isRead = true
                )
            }
            if (statesToDismiss.isNotEmpty()) {
                repository.upsertNoticeStates(statesToDismiss)
            }
            _toastMessage.value = "Finished lot retired (90-day demand kept for insights)"
        }
    }

    fun toggleProductRepeatable(productId: Long) {
        viewModelScope.launch {
            val current = uiState.value.products.firstOrNull { it.id == productId } ?: return@launch
            repository.toggleRepeatableProduct(productId)
            _toastMessage.value = if (current.isRepeatable) {
                "${current.skuCode} marked as One-Time Lot"
            } else {
                "${current.skuCode} marked as Repeatable Staple"
            }
        }
    }

    // Vendor & Buying Actions
    fun saveVendor(vendor: VendorEntity) {
        viewModelScope.launch {
            repository.saveVendor(vendor)
            _toastMessage.value = "${vendor.name} saved"
        }
    }

    fun deleteVendor(vendorId: Long) {
        viewModelScope.launch {
            repository.deleteVendor(vendorId)
            _toastMessage.value = "Vendor removed"
        }
    }

    fun recordVendorPayment(vendorId: Long, amount: Double, paymentMode: String, notes: String) {
        viewModelScope.launch {
            repository.recordVendorPayment(vendorId, amount, paymentMode, notes)
            val currency = uiState.value.settings.currencySymbol
            _toastMessage.value = "Paid ${Formatters.formatCurrency(amount, currency)} to vendor ($paymentMode)"
        }
    }

    fun buildWhatsAppBuyingBrief(
        vendor: VendorEntity?,
        buckets: List<BuyingBucketSuggestion>,
        repeatItems: List<ReorderSuggestion> = emptyList()
    ): String {
        val shopName = uiState.value.settings.shopName.ifBlank { "SoleBook Store" }
        val currency = uiState.value.settings.currencySymbol
        val unitPlural = uiState.value.industryConfig.unitPlural
        val unitSingular = uiState.value.industryConfig.unitSingular
        return buildString {
            appendLine("*$shopName — Purchase / Restock Order*")
            if (vendor != null) {
                appendLine("To: ${vendor.name}")
            }
            appendLine("Please share availability & wholesale pricing for:")
            appendLine()
            if (buckets.isNotEmpty()) {
                buckets.forEachIndexed { idx, b ->
                    val sizes = b.sizeSplit.entries.joinToString(", ") { "${it.key}:${it.value}" }
                    appendLine("${idx + 1}. *${b.category}* (${b.priceBandLabel})")
                    appendLine("   Qty: ~${b.recommendedPairs} $unitPlural ($sizes)")
                    appendLine("   Target cost: ${b.costRangeLabel}/$unitSingular")
                }
            }
            if (repeatItems.isNotEmpty()) {
                appendLine()
                appendLine("*Regular Restock Items:*")
                repeatItems.forEach { r ->
                    val sizes = r.sizeOrderSplit.entries.joinToString(", ") { "${it.key}:${it.value}" }
                    appendLine("• ${r.product.name} (${r.product.brand.ifBlank { r.product.skuCode }}) — ${r.recommendedQty} $unitPlural ($sizes)")
                }
            }
            val totalEst = buckets.sumOf { it.estimatedBudget } + repeatItems.sumOf { it.estimatedCost }
            if (totalEst > 0) {
                appendLine()
                appendLine("Approx. budget: ${Formatters.formatCurrency(totalEst, currency)}")
            }
        }.trim()
    }

    // Money & Expense Actions
    fun setMoneyPeriod(period: MoneyPeriod) {
        _moneyPeriod.value = period
    }

    fun setCustomDateRange(startMs: Long, endMs: Long) {
        _customRange.value = startMs to endMs
        _moneyPeriod.value = MoneyPeriod.CUSTOM
    }

    fun addExpense(category: String, amount: Double, isRecurring: Boolean, notes: String) {
        viewModelScope.launch {
            repository.addExpense(
                ExpenseEntity(
                    category = category,
                    amount = amount,
                    isRecurringMonthly = isRecurring,
                    notes = notes
                )
            )
            _toastMessage.value = "Expense added"
        }
    }

    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            _toastMessage.value = "Expense removed"
        }
    }

    // Customer Actions
    fun saveCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.saveCustomer(customer)
            _toastMessage.value = "Customer saved"
        }
    }

    fun importCustomersFromText(
        rawText: String,
        whatsappConsent: Boolean = true,
        onComplete: (ContactImportResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) {
                CampaignBuilder.parseContactsText(rawText)
            }
            if (parsed.isEmpty()) {
                _toastMessage.value = "No valid phone numbers found to import"
                onComplete(ContactImportResult(0, 0, 0))
                return@launch
            }
            val result = repository.importCustomers(parsed, whatsappConsent)
            _toastMessage.value = buildString {
                append("Added ${result.addedCount} new contact(s)")
                if (result.updatedCount > 0) append(", updated ${result.updatedCount}")
                if (result.skippedCount > 0) append(", ${result.skippedCount} duplicate(s) skipped")
            }
            onComplete(result)
        }
    }

    fun importCustomersFromUri(
        uri: Uri,
        whatsappConsent: Boolean = true,
        onComplete: (ContactImportResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: throw IllegalStateException("Could not read file")
                val parsed = withContext(Dispatchers.Default) {
                    CampaignBuilder.parseContactsText(content)
                }
                if (parsed.isEmpty()) {
                    _toastMessage.value = "No valid contacts found in selected file"
                    onComplete(ContactImportResult(0, 0, 0))
                    return@launch
                }
                val result = repository.importCustomers(parsed, whatsappConsent)
                _toastMessage.value = buildString {
                    append("Imported ${result.addedCount} new contact(s)")
                    if (result.updatedCount > 0) append(", updated ${result.updatedCount}")
                    if (result.skippedCount > 0) append(", ${result.skippedCount} already saved")
                }
                onComplete(result)
            } catch (e: Exception) {
                _toastMessage.value = "Import failed: ${e.message ?: "unsupported file"}"
                onComplete(ContactImportResult(0, 0, 0))
            }
        }
    }

    fun settleCustomerCredit(customerId: Long) {
        viewModelScope.launch {
            repository.settleCustomerCredit(customerId)
            _toastMessage.value = "Udhaar settled!"
        }
    }

    fun logCampaignSend(templateId: Long?, segmentKey: String, customer: CustomerEntity, message: String) {
        viewModelScope.launch {
            repository.logCampaignSend(templateId, segmentKey, customer, message)
        }
    }

    // Export / Backup Actions
    fun exportJsonBackup(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val payload = repository.buildBackupPayload()
            val json = withContext(Dispatchers.Default) { ExportUtils.encodeFullBackupJson(payload) }
            val file = withContext(Dispatchers.IO) {
                ExportUtils.writeExportFile(getApplication(), "solebook_backup_${System.currentTimeMillis()}.json", json)
            }
            onReady(file)
        }
    }

    fun exportSalesCsv(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            val csv = withContext(Dispatchers.Default) { ExportUtils.buildSalesCsv(state.sales, state.saleItems) }
            val file = withContext(Dispatchers.IO) {
                ExportUtils.writeExportFile(getApplication(), "solebook_sales.csv", csv)
            }
            onReady(file)
        }
    }

    fun exportProductsCsv(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            val csv = withContext(Dispatchers.Default) { ExportUtils.buildProductsCsv(state.products, state.variants) }
            val file = withContext(Dispatchers.IO) {
                ExportUtils.writeExportFile(getApplication(), "solebook_stock.csv", csv)
            }
            onReady(file)
        }
    }

    fun exportCustomersCsv(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            val csv = withContext(Dispatchers.Default) { ExportUtils.buildCustomersCsv(state.customers) }
            val file = withContext(Dispatchers.IO) {
                ExportUtils.writeExportFile(getApplication(), "solebook_customers.csv", csv)
            }
            onReady(file)
        }
    }

    fun restoreJsonFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: throw IllegalStateException("Could not read file")
                val payload = withContext(Dispatchers.Default) { ExportUtils.decodeFullBackupJson(content) }
                repository.restoreFromBackup(payload)
                withContext(Dispatchers.Default) {
                    repository.indexFingerprintsForProducts(context)
                }
                _toastMessage.value = "Backup restored (${payload.products.size} products, ${payload.sales.size} sales)"
            } catch (e: Exception) {
                _toastMessage.value = "Restore failed: ${e.message}"
            }
        }
    }

    fun findProductByBarcodeOrSku(code: String, onResult: (ProductEntity?, String?) -> Unit) {
        viewModelScope.launch {
            val hit = withContext(Dispatchers.IO) {
                com.example.domain.onboarding.BarcodeOnboardingService.lookupLocalCatalog(
                    barcodeRaw = code,
                    soleBookDao = db.soleBookDao(),
                    onboardingDao = db.onboardingDao()
                )
            }
            if (hit != null) {
                onResult(hit.product, hit.matchedVariant?.size)
            } else {
                _toastMessage.value = "No product found for barcode '$code'"
                onResult(null, null)
            }
        }
    }
}
