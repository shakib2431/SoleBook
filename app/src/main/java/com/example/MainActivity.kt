package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.BusinessOperationMode
import com.example.domain.WorkspaceMode
import com.example.notifications.SoleBookNotificationHelper
import com.example.ui.components.LoadingState
import com.example.ui.components.LocalWorkspaceSwitchBar
import com.example.ui.components.WorkspaceModeSwitcher
import com.example.ui.screens.*
import com.example.ui.screens.onboarding.ImportHubScreen
import com.example.ui.screens.onboarding.StagingReviewScreen
import com.example.ui.screens.onboarding.StockCountSessionScreen
import com.example.ui.screens.wholesale.BomAndMaterialsScreen
import com.example.ui.screens.wholesale.QuoteBuilderScreen
import com.example.ui.screens.wholesale.WholesaleDeskScreen
import com.example.ui.screens.wholesale.WholesaleMoneyScreen
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookViewModel

object SoleBookRoutes {
    const val HOME = "home"
    const val STOCK = "stock"
    const val SELL = "sell"
    const val MONEY = "money"
    const val MORE = "more"
    const val WHOLESALE_DESK = "wholesale_desk"
    const val WHOLESALE_BOM = "wholesale_bom"
    const val WHOLESALE_QUOTE = "wholesale_quote"
    const val WHOLESALE_LEDGER = "wholesale_ledger"
    const val CUSTOMERS = "customers"
    const val SUPPLIERS = "suppliers"
    const val INSIGHTS = "insights"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val NOTICES = "notices"
    const val DATA_HUB = "data_hub?sheet={sheet}"
    const val STAGING_REVIEW = "staging_review/{batchId}"
    const val STOCK_COUNT_SESSION = "stock_count/{sessionId}"
    const val PRODUCT_DETAIL = "product_detail/{productId}"
    const val CUSTOMER_DETAIL = "customer_detail/{customerId}"
    const val VENDOR_DETAIL = "vendor_detail/{vendorId}"

    fun dataHub(initialSheet: String? = null) =
        if (initialSheet.isNullOrBlank()) "data_hub" else "data_hub?sheet=$initialSheet"
    fun stagingReview(batchId: Long) = "staging_review/$batchId"
    fun stockCountSession(sessionId: Long) = "stock_count/$sessionId"
    fun productDetail(id: Long) = "product_detail/$id"
    fun customerDetail(id: Long) = "customer_detail/$id"
    fun vendorDetail(id: Long) = "vendor_detail/$id"
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    private var openNoticesSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(SoleBookNotificationHelper.EXTRA_OPEN_NOTICES, false) == true) {
            openNoticesSignal++
        }
        setContent {
            SoleBookTheme {
                SoleBookRootApp(openNoticesSignal = openNoticesSignal)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(SoleBookNotificationHelper.EXTRA_OPEN_NOTICES, false)) {
            openNoticesSignal++
        }
    }
}

@Composable
fun SoleBookRootApp(
    viewModel: SoleBookViewModel = viewModel(),
    openNoticesSignal: Int = 0
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hubState by viewModel.dataHubState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val snackbarHostState = remember { SnackbarHostState() }
    var openAddProductOnStock by remember { mutableStateOf(false) }
    var openLowFilterOnStock by remember { mutableStateOf(false) }
    var freshLotPrefill by remember { mutableStateOf<FreshLotPrefill?>(null) }
    var preselectedCampaignProductId by remember { mutableStateOf<Long?>(null) }
    var pendingOperationMode by remember { mutableStateOf(BusinessOperationMode.RETAIL.key) }
    var activeEditingQuoteId by remember { mutableStateOf<Long?>(null) }
    var preselectedBomTemplateId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(openNoticesSignal) {
        if (openNoticesSignal > 0 && state.settings.hasCompletedOnboarding) {
            navController.navigate(SoleBookRoutes.NOTICES) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearToast()
        }
    }

    when {
        state.isLoading -> {
            LoadingState()
        }
        !state.settings.hasCompletedOnboarding -> {
            OnboardingScreen(
                onCompleteGoDashboard = { name, cat, customType, curr, ch ->
                    viewModel.completeOnboarding(name, cat, customType, curr, ch, pendingOperationMode)
                },
                onCompleteAddFirstProduct = { name, cat, customType, curr, ch ->
                    openAddProductOnStock = (pendingOperationMode != BusinessOperationMode.WHOLESALE.key)
                    viewModel.completeOnboarding(name, cat, customType, curr, ch, pendingOperationMode)
                },
                onSelectOperationMode = { opMode ->
                    pendingOperationMode = opMode
                }
            )
        }
        else -> {
            val operationMode = BusinessOperationMode.fromKey(state.settings.businessOperationMode)
            val activeWorkspace = when (operationMode) {
                BusinessOperationMode.RETAIL -> WorkspaceMode.RETAIL
                BusinessOperationMode.WHOLESALE -> WorkspaceMode.WHOLESALE
                BusinessOperationMode.BOTH -> WorkspaceMode.fromKey(state.settings.activeWorkspaceMode)
            }
            SoleBookTheme(workspaceMode = activeWorkspace) {
            val initialStartDestination = remember {
                if (activeWorkspace == WorkspaceMode.WHOLESALE) SoleBookRoutes.WHOLESALE_DESK
                else SoleBookRoutes.HOME
            }

            // If onboarding chose "Add my first product" in retail mode, jump cleanly once main NavHost mounts
            LaunchedEffect(state.settings.hasCompletedOnboarding, openAddProductOnStock) {
                if (state.settings.hasCompletedOnboarding && openAddProductOnStock && currentRoute == SoleBookRoutes.HOME) {
                    navController.navigate(SoleBookRoutes.STOCK) {
                        popUpTo(SoleBookRoutes.HOME) { saveState = true }
                        launchSingleTop = true
                    }
                }
            }

            val strings = state.strings
            val retailTabs = listOf(
                BottomNavTab(SoleBookRoutes.HOME, strings.tabHome, Icons.Rounded.Home, Icons.Outlined.Home, "tab_home"),
                BottomNavTab(SoleBookRoutes.STOCK, strings.tabStock, Icons.Rounded.Inventory2, Icons.Outlined.Inventory2, "tab_stock"),
                BottomNavTab(SoleBookRoutes.SELL, strings.tabSell, Icons.Rounded.PointOfSale, Icons.Outlined.PointOfSale, "tab_sell"),
                BottomNavTab(SoleBookRoutes.MONEY, strings.tabMoney, Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "tab_money"),
                BottomNavTab(SoleBookRoutes.MORE, strings.tabMore, Icons.Rounded.GridView, Icons.Outlined.GridView, "tab_more")
            )
            val wholesaleTabs = listOf(
                BottomNavTab(SoleBookRoutes.WHOLESALE_DESK, "Desk", Icons.Rounded.PrecisionManufacturing, Icons.Outlined.PrecisionManufacturing, "tab_w_desk"),
                BottomNavTab(SoleBookRoutes.WHOLESALE_BOM, "BOM", Icons.Rounded.Architecture, Icons.Outlined.Architecture, "tab_w_bom"),
                BottomNavTab(SoleBookRoutes.WHOLESALE_QUOTE, "Quote", Icons.Rounded.RequestQuote, Icons.Outlined.RequestQuote, "tab_w_quote"),
                BottomNavTab(SoleBookRoutes.WHOLESALE_LEDGER, "Ledger", Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "tab_w_ledger"),
                BottomNavTab(SoleBookRoutes.MORE, strings.tabMore, Icons.Rounded.GridView, Icons.Outlined.GridView, "tab_more")
            )
            val tabs = if (activeWorkspace == WorkspaceMode.WHOLESALE) wholesaleTabs else retailTabs
            val retailRouteSet = remember { setOf(SoleBookRoutes.HOME, SoleBookRoutes.STOCK, SoleBookRoutes.SELL, SoleBookRoutes.MONEY) }
            val wholesaleRouteSet = remember { setOf(SoleBookRoutes.WHOLESALE_DESK, SoleBookRoutes.WHOLESALE_BOM, SoleBookRoutes.WHOLESALE_QUOTE, SoleBookRoutes.WHOLESALE_LEDGER) }
            val mainRoutes = retailRouteSet + wholesaleRouteSet + SoleBookRoutes.MORE
            val showBottomOrRailNav = currentRoute in mainRoutes

            LaunchedEffect(activeWorkspace, currentRoute) {
                if (activeWorkspace == WorkspaceMode.WHOLESALE && currentRoute in retailRouteSet) {
                    navController.navigate(SoleBookRoutes.WHOLESALE_DESK) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                } else if (activeWorkspace == WorkspaceMode.RETAIL && currentRoute in wholesaleRouteSet) {
                    navController.navigate(SoleBookRoutes.HOME) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }

            fun navigateTopTab(route: String) {
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }

    fun openNoticesInbox() {
        navController.navigate(SoleBookRoutes.NOTICES) {
            launchSingleTop = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWarmWhite)
    ) {
        val isWideScreen = maxWidth >= 720.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen && showBottomOrRailNav) {
                Surface(
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, DividerGrey),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    NavigationRail(
                        containerColor = SurfaceWhite,
                        contentColor = TextCharcoal,
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
                        )
                    ) {
                        Spacer(Modifier.height(16.dp))
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = TextSecondaryGrey,
                                    unselectedTextColor = TextSecondaryGrey
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }

            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                containerColor = CanvasWarmWhite,
                contentColor = TextCharcoal,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Surface(
                            color = GlassDarkSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Snackbar(
                                snackbarData = data,
                                containerColor = GlassDarkSurface,
                                contentColor = PureWhite,
                                actionColor = ProfitGreenMint,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!isWideScreen && showBottomOrRailNav) {
                        Surface(
                            color = SurfaceWhite,
                            contentColor = TextCharcoal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(thickness = 1.dp, color = DividerGrey)
                                NavigationBar(
                                    containerColor = SurfaceWhite,
                                    tonalElevation = 0.dp,
                                    windowInsets = WindowInsets.navigationBars,
                                    modifier = Modifier.heightIn(min = 68.dp)
                                ) {
                                    tabs.forEach { tab ->
                                        val selected = currentRoute == tab.route
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTopTab(tab.route) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label,
                                                    modifier = Modifier.size(23.dp)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = tab.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                                unselectedIconColor = TextSecondaryGrey,
                                                unselectedTextColor = TextSecondaryGrey
                                            ),
                                            modifier = Modifier
                                                .heightIn(min = 52.dp)
                                                .testTag(tab.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                val dockClearance = innerPadding.calculateBottomPadding()
                val isDashboardRoute = currentRoute == SoleBookRoutes.HOME ||
                    currentRoute == SoleBookRoutes.WHOLESALE_DESK ||
                    currentRoute == null
                val showWorkspaceSwitchBar =
                    (operationMode == BusinessOperationMode.BOTH && showBottomOrRailNav) || isDashboardRoute
                val switchBarProvider: (@Composable () -> Unit)? =
                    if (showWorkspaceSwitchBar) {
                        {
                            WorkspaceModeSwitcher(
                                activeMode = activeWorkspace,
                                onSelectMode = { target -> viewModel.switchWorkspaceMode(target) }
                            )
                        }
                    } else null

                CompositionLocalProvider(
                    LocalGlassBottomPadding provides dockClearance,
                    LocalWorkspaceSwitchBar provides switchBarProvider
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = initialStartDestination,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding())
                    ) {
                        composable(SoleBookRoutes.HOME) {
                            HomeDashboardScreen(
                                state = state,
                                viewModel = viewModel,
                                onNewSale = { navigateTopTab(SoleBookRoutes.SELL) },
                                onAddProduct = {
                                    openAddProductOnStock = true
                                    navigateTopTab(SoleBookRoutes.STOCK)
                                },
                                onAddPurchase = {
                                    freshLotPrefill = FreshLotPrefill()
                                    navigateTopTab(SoleBookRoutes.STOCK)
                                },
                                onOpenStockLow = {
                                    openLowFilterOnStock = true
                                    navigateTopTab(SoleBookRoutes.STOCK)
                                },
                                onOpenCustomers = {
                                    navController.navigate(SoleBookRoutes.CUSTOMERS) { launchSingleTop = true }
                                },
                                onOpenMoney = { navigateTopTab(SoleBookRoutes.MONEY) },
                                onOpenInsights = {
                                    navController.navigate(SoleBookRoutes.INSIGHTS) { launchSingleTop = true }
                                },
                                onOpenNotices = ::openNoticesInbox,
                                onOpenSettings = {
                                    navController.navigate(SoleBookRoutes.SETTINGS) { launchSingleTop = true }
                                },
                                onOpenProductDetail = { pid ->
                                    navController.navigate(SoleBookRoutes.productDetail(pid))
                                },
                                onLoadDemoData = { viewModel.loadDemoData() }
                            )
                        }

                        composable(SoleBookRoutes.SELL) {
                            SellScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenSettings = { navController.navigate(SoleBookRoutes.SETTINGS) },
                                onOpenAddProduct = {
                                    openAddProductOnStock = true
                                    navigateTopTab(SoleBookRoutes.STOCK)
                                },
                                onOpenNotices = ::openNoticesInbox
                            )
                        }

                        composable(SoleBookRoutes.STOCK) {
                            StockScreen(
                                state = state,
                                viewModel = viewModel,
                                openAddImmediately = openAddProductOnStock,
                                openLowStockImmediately = openLowFilterOnStock,
                                freshLotPrefill = freshLotPrefill,
                                onConsumedOpenAdd = {
                                    openAddProductOnStock = false
                                    openLowFilterOnStock = false
                                    freshLotPrefill = null
                                },
                                onOpenProductDetail = { pid ->
                                    navController.navigate(SoleBookRoutes.productDetail(pid))
                                },
                                onOpenVendorDetail = { vid ->
                                    navController.navigate(SoleBookRoutes.vendorDetail(vid))
                                },
                                onOpenSettings = { navController.navigate(SoleBookRoutes.SETTINGS) },
                                onOpenNotices = ::openNoticesInbox,
                                onOpenDataHub = { sheet ->
                                    navController.navigate(SoleBookRoutes.dataHub(sheet)) { launchSingleTop = true }
                                }
                            )
                        }

                        composable(SoleBookRoutes.MONEY) {
                            MoneyScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenSettings = { navController.navigate(SoleBookRoutes.SETTINGS) },
                                onOpenNotices = ::openNoticesInbox
                            )
                        }

                        composable(SoleBookRoutes.MORE) {
                            MoreScreen(
                                state = state,
                                onOpenCustomers = { navController.navigate(SoleBookRoutes.CUSTOMERS) },
                                onOpenSuppliers = { navController.navigate(SoleBookRoutes.SUPPLIERS) },
                                onOpenDataHub = {
                                    navController.navigate(SoleBookRoutes.dataHub()) { launchSingleTop = true }
                                },
                                onOpenInsights = { navController.navigate(SoleBookRoutes.INSIGHTS) },
                                onOpenReports = { navController.navigate(SoleBookRoutes.REPORTS) },
                                onOpenNotices = ::openNoticesInbox,
                                onOpenSettings = { navController.navigate(SoleBookRoutes.SETTINGS) }
                            )
                        }

                        composable(SoleBookRoutes.WHOLESALE_DESK) {
                            WholesaleDeskScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenNewQuote = { quoteId ->
                                    activeEditingQuoteId = quoteId
                                    preselectedBomTemplateId = null
                                    navigateTopTab(SoleBookRoutes.WHOLESALE_QUOTE)
                                },
                                onOpenBomScreen = { navigateTopTab(SoleBookRoutes.WHOLESALE_BOM) },
                                onOpenLedger = { navigateTopTab(SoleBookRoutes.WHOLESALE_LEDGER) },
                                onOpenSettings = { navController.navigate(SoleBookRoutes.SETTINGS) },
                                onOpenNotices = ::openNoticesInbox
                            )
                        }

                        composable(SoleBookRoutes.WHOLESALE_BOM) {
                            BomAndMaterialsScreen(
                                state = state,
                                viewModel = viewModel,
                                onUseTemplateInQuote = { tplId ->
                                    activeEditingQuoteId = null
                                    preselectedBomTemplateId = tplId
                                    navigateTopTab(SoleBookRoutes.WHOLESALE_QUOTE)
                                },
                                onOpenSettings = { navController.navigate(SoleBookRoutes.SETTINGS) },
                                onOpenNotices = ::openNoticesInbox
                            )
                        }

                        composable(SoleBookRoutes.WHOLESALE_QUOTE) {
                            QuoteBuilderScreen(
                                state = state,
                                viewModel = viewModel,
                                editingQuoteId = activeEditingQuoteId,
                                preselectedTemplateId = preselectedBomTemplateId,
                                onQuoteLoadedOrCreated = { savedId ->
                                    activeEditingQuoteId = savedId
                                },
                                onOpenSettings = { navController.navigate(SoleBookRoutes.SETTINGS) },
                                onOpenNotices = ::openNoticesInbox
                            )
                        }

                        composable(SoleBookRoutes.WHOLESALE_LEDGER) {
                            WholesaleMoneyScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenSettings = { navController.navigate(SoleBookRoutes.SETTINGS) },
                                onOpenNotices = ::openNoticesInbox
                            )
                        }

                        composable(SoleBookRoutes.CUSTOMERS) {
                            CustomersScreen(
                                state = state,
                                viewModel = viewModel,
                                preselectedCampaignProductId = preselectedCampaignProductId,
                                onConsumedPreselectedProduct = { preselectedCampaignProductId = null },
                                onOpenCustomerDetail = { cid ->
                                    navController.navigate(SoleBookRoutes.customerDetail(cid))
                                },
                                onOpenSettings = { navController.navigate(SoleBookRoutes.SETTINGS) },
                                onOpenNotices = ::openNoticesInbox
                            )
                        }

                        composable(SoleBookRoutes.SUPPLIERS) {
                            SuppliersScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenVendorDetail = { vid ->
                                    navController.navigate(SoleBookRoutes.vendorDetail(vid))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(SoleBookRoutes.INSIGHTS) {
                            InsightsScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenProductDetail = { pid ->
                                    navController.navigate(SoleBookRoutes.productDetail(pid))
                                },
                                onOpenVendorDetail = { vid ->
                                    navController.navigate(SoleBookRoutes.vendorDetail(vid))
                                },
                                onSourceFreshLot = { cat, gen, vid ->
                                    freshLotPrefill = FreshLotPrefill(category = cat, gender = gen, vendorId = vid)
                                    navigateTopTab(SoleBookRoutes.STOCK)
                                },
                                onOpenSettings = { navController.navigate(SoleBookRoutes.SETTINGS) },
                                onOpenNotices = ::openNoticesInbox
                            )
                        }

                        composable(SoleBookRoutes.REPORTS) {
                            ReportsScreen(
                                state = state,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(SoleBookRoutes.NOTICES) {
                            NoticesScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenProductDetail = { pid ->
                                    navController.navigate(SoleBookRoutes.productDetail(pid))
                                },
                                onOpenVendorDetail = { vid ->
                                    navController.navigate(SoleBookRoutes.vendorDetail(vid))
                                },
                                onSourceFreshLot = { cat, gen, vid ->
                                    freshLotPrefill = FreshLotPrefill(category = cat, gender = gen, vendorId = vid)
                                    navController.popBackStack(SoleBookRoutes.HOME, inclusive = false)
                                    navigateTopTab(SoleBookRoutes.STOCK)
                                },
                                onOpenSellTab = {
                                    navController.popBackStack(SoleBookRoutes.HOME, inclusive = false)
                                    navigateTopTab(SoleBookRoutes.SELL)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = SoleBookRoutes.PRODUCT_DETAIL,
                            arguments = listOf(navArgument("productId") { type = NavType.LongType })
                        ) { entry ->
                            val pid = entry.arguments?.getLong("productId") ?: 0L
                            ProductDetailScreen(
                                productId = pid,
                                state = state,
                                viewModel = viewModel,
                                onOpenProductDetail = { nextPid ->
                                    navController.navigate(SoleBookRoutes.productDetail(nextPid))
                                },
                                onOpenVendorDetail = { vid ->
                                    navController.navigate(SoleBookRoutes.vendorDetail(vid))
                                },
                                onSourceSimilarLot = { cat, gen, vid ->
                                    freshLotPrefill = FreshLotPrefill(category = cat, gender = gen, vendorId = vid)
                                    navController.popBackStack(SoleBookRoutes.HOME, inclusive = false)
                                    navigateTopTab(SoleBookRoutes.STOCK)
                                },
                                onPromoteOnWhatsApp = { promotePid ->
                                    preselectedCampaignProductId = promotePid
                                    navController.navigate(SoleBookRoutes.CUSTOMERS)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = SoleBookRoutes.VENDOR_DETAIL,
                            arguments = listOf(navArgument("vendorId") { type = NavType.LongType })
                        ) { entry ->
                            val vid = entry.arguments?.getLong("vendorId") ?: 0L
                            VendorDetailScreen(
                                vendorId = vid,
                                state = state,
                                viewModel = viewModel,
                                onOpenProductDetail = { pid ->
                                    navController.navigate(SoleBookRoutes.productDetail(pid))
                                },
                                onLogFreshLotForVendor = { vId, cat ->
                                    freshLotPrefill = FreshLotPrefill(category = cat, vendorId = vId)
                                    navController.popBackStack(SoleBookRoutes.HOME, inclusive = false)
                                    navigateTopTab(SoleBookRoutes.STOCK)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = SoleBookRoutes.CUSTOMER_DETAIL,
                            arguments = listOf(navArgument("customerId") { type = NavType.LongType })
                        ) { entry ->
                            val cid = entry.arguments?.getLong("customerId") ?: 0L
                            CustomerDetailScreen(
                                customerId = cid,
                                state = state,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(SoleBookRoutes.SETTINGS) {
                            SettingsScreen(
                                state = state,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = SoleBookRoutes.DATA_HUB,
                            arguments = listOf(navArgument("sheet") { nullable = true; defaultValue = null })
                        ) { entry ->
                            val initialSheet = entry.arguments?.getString("sheet")
                            ImportHubScreen(
                                state = state,
                                hubState = hubState,
                                controller = viewModel.onboardingHub,
                                initialRouteSheet = initialSheet,
                                onBack = { navController.popBackStack() },
                                onOpenBatchReview = { batchId ->
                                    navController.navigate(SoleBookRoutes.stagingReview(batchId))
                                },
                                onOpenStockCountSession = { sessionId ->
                                    navController.navigate(SoleBookRoutes.stockCountSession(sessionId))
                                },
                                onOpenProductDetail = { pid ->
                                    navController.navigate(SoleBookRoutes.productDetail(pid))
                                }
                            )
                        }

                        composable(
                            route = SoleBookRoutes.STAGING_REVIEW,
                            arguments = listOf(navArgument("batchId") { type = NavType.LongType })
                        ) { entry ->
                            val batchId = entry.arguments?.getLong("batchId") ?: 0L
                            StagingReviewScreen(
                                batchId = batchId,
                                currency = state.settings.currencySymbol,
                                hubState = hubState,
                                controller = viewModel.onboardingHub,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = SoleBookRoutes.STOCK_COUNT_SESSION,
                            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                        ) { entry ->
                            val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
                            StockCountSessionScreen(
                                sessionId = sessionId,
                                products = state.products,
                                currency = state.settings.currencySymbol,
                                hubState = hubState,
                                controller = viewModel.onboardingHub,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
        }
    }
    }
    }
}
