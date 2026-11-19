package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.NoticeActionType
import com.example.domain.NoticeCategory
import com.example.domain.PriorityNotice
import com.example.notifications.SoleBookNotificationHelper
import com.example.ui.components.EmptyState
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SkuBoxTag
import com.example.ui.components.SoleBookSpacing
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.Formatters

private enum class NoticeFilter(val label: String) {
    ALL("All"),
    STOCK("Out & Low"),
    DEMAND("Demand"),
    MARGIN("Price & Margin"),
    DEAD("Dead Stock"),
    SOLD("Sold")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onOpenProductDetail: (Long) -> Unit,
    onOpenVendorDetail: (Long) -> Unit = {},
    onSourceFreshLot: (String, String, Long?) -> Unit = { _, _, _ -> },
    onOpenSellTab: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val currency = state.settings.currencySymbol
    val industry = state.industryConfig

    var hasNotifPermission by remember {
        mutableStateOf(SoleBookNotificationHelper.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted || SoleBookNotificationHelper.hasNotificationPermission(context)
        if (granted) {
            viewModel.sendTestPhoneNotification()
        }
    }

    var selectedFilter by remember { mutableStateOf(NoticeFilter.ALL) }
    var showRulesSheet by remember { mutableStateOf(false) }
    var adjustPriceNotice by remember { mutableStateOf<PriorityNotice?>(null) }
    var lotDecisionNotice by remember { mutableStateOf<PriorityNotice?>(null) }

    val allNotices = state.priorityNotices
    val filteredNotices = remember(allNotices, selectedFilter) {
        when (selectedFilter) {
            NoticeFilter.ALL -> allNotices
            NoticeFilter.STOCK -> allNotices.filter {
                it.category == NoticeCategory.OUT_OF_STOCK || it.category == NoticeCategory.LOW_STOCK
            }
            NoticeFilter.DEMAND -> allNotices.filter {
                it.category == NoticeCategory.HIGH_DEMAND || it.category == NoticeCategory.SLOW_DEMAND
            }
            NoticeFilter.MARGIN -> allNotices.filter { it.category == NoticeCategory.PRICE_MARGIN }
            NoticeFilter.DEAD -> allNotices.filter { it.category == NoticeCategory.DEAD_STOCK }
            NoticeFilter.SOLD -> allNotices.filter { it.category == NoticeCategory.SOLD_ALERT }
        }
    }

    val stockAlertCount = allNotices.count {
        it.category == NoticeCategory.OUT_OF_STOCK || it.category == NoticeCategory.LOW_STOCK
    }
    val marginAlertCount = allNotices.count { it.category == NoticeCategory.PRICE_MARGIN }
    val demandDeadCount = allNotices.count {
        it.category == NoticeCategory.HIGH_DEMAND ||
            it.category == NoticeCategory.SLOW_DEMAND ||
            it.category == NoticeCategory.DEAD_STOCK
    }

    Scaffold(
        containerColor = CanvasWarmWhite,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Business Alerts",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextCharcoal
                            )
                            Text(
                                text = "${allNotices.size} active • ${state.unreadNoticeCount} unread",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondaryGrey
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("notices_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = TextCharcoal
                            )
                        }
                    },
                    actions = {
                        if (state.unreadNoticeCount > 0) {
                            TextButton(
                                onClick = { viewModel.markAllNoticesRead() },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("mark_all_read_button")
                            ) {
                                Text("Mark Read", color = EmeraldPrimary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        IconButton(
                            onClick = { showRulesSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("open_alert_rules_button")
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Alert Rules", tint = TextCharcoal)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
                )
                HorizontalDivider(thickness = 1.dp, color = DividerGrey)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = SoleBookSpacing.screenHorizontal, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                item {
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = EmeraldPrimary)
                                Text("Turn on Phone Alerts", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                            }
                            Text(
                                "Allow notifications so SoleBook can send out-of-stock, demand, and margin alerts even when the app is closed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondaryGrey
                            )
                            PrimaryButton(
                                text = "Allow Phone Alerts",
                                icon = Icons.Rounded.CheckCircle,
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "request_notification_permission_button"
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
                    border = BorderStroke(1.dp, DividerGrey),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NoticeSummaryTile(
                                label = "Out / Low",
                                count = stockAlertCount,
                                bg = if (stockAlertCount > 0) SemanticErrorSoft else SurfaceSubtle,
                                fg = if (stockAlertCount > 0) SemanticError else TextSecondaryGrey,
                                modifier = Modifier.weight(1f)
                            )
                            NoticeSummaryTile(
                                label = "Price / Margin",
                                count = marginAlertCount,
                                bg = if (marginAlertCount > 0) EmeraldSoft else SurfaceSubtle,
                                fg = if (marginAlertCount > 0) EmeraldDark else TextSecondaryGrey,
                                modifier = Modifier.weight(1f)
                            )
                            NoticeSummaryTile(
                                label = "Demand / Dead",
                                count = demandDeadCount,
                                bg = if (demandDeadCount > 0) SemanticWarningSoft else SurfaceSubtle,
                                fg = if (demandDeadCount > 0) SemanticWarning else TextSecondaryGrey,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrimaryButton(
                                text = "Send Test Alert",
                                icon = Icons.Rounded.NotificationAdd,
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.sendTestPhoneNotification()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "test_popup_button"
                            )
                            SecondaryButton(
                                text = if (state.settings.alertsEnabled) "Alerts On" else "Paused",
                                icon = Icons.Rounded.Tune,
                                onClick = { showRulesSheet = true },
                                modifier = Modifier.weight(1f),
                                testTag = "alert_rules_button"
                            )
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(NoticeFilter.entries) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label, style = MaterialTheme.typography.labelLarge) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            if (filteredNotices.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Verified,
                        title = "Everything looks healthy",
                        subtitle = "No urgent alerts in this filter right now. SoleBook checks your stock and margins automatically.",
                        primaryLabel = if (selectedFilter != NoticeFilter.ALL) "Show All Alerts" else "Back to Dashboard",
                        onPrimaryClick = {
                            if (selectedFilter != NoticeFilter.ALL) selectedFilter = NoticeFilter.ALL else onBack()
                        }
                    )
                }
            } else {
                items(filteredNotices, key = { it.noticeKey }) { notice ->
                    PriorityNoticeCard(
                        notice = notice,
                        currency = currency,
                        unitSingular = industry.unitSingular,
                        onPrimaryAction = {
                            when (notice.actionType) {
                                NoticeActionType.RESTOCK, NoticeActionType.VIEW_PRODUCT -> {
                                    notice.productId?.let(onOpenProductDetail)
                                }
                                NoticeActionType.SOURCE_SIMILAR_LOT, NoticeActionType.RETIRE_LOT -> {
                                    lotDecisionNotice = notice
                                }
                                NoticeActionType.ADJUST_PRICE -> {
                                    adjustPriceNotice = notice
                                }
                                NoticeActionType.OPEN_SELL -> onOpenSellTab()
                            }
                        },
                        onSnooze = { viewModel.snoozeNotice(notice, 24) },
                        onDismiss = { viewModel.dismissNotice(notice) }
                    )
                }
            }
        }
    }

    adjustPriceNotice?.let { targetNotice ->
        AdjustPriceNoticeDialog(
            notice = targetNotice,
            currency = currency,
            unitSingular = industry.unitSingular,
            onDismiss = { adjustPriceNotice = null },
            onConfirm = { newPrice ->
                val pid = targetNotice.productId
                if (pid != null) {
                    viewModel.applySuggestedPrice(pid, newPrice, targetNotice.noticeKey)
                }
                adjustPriceNotice = null
            }
        )
    }

    lotDecisionNotice?.let { targetNotice ->
        LotDecisionBottomSheet(
            notice = targetNotice,
            state = state,
            viewModel = viewModel,
            onDismiss = { lotDecisionNotice = null },
            onOpenProductDetail = { pid ->
                lotDecisionNotice = null
                onOpenProductDetail(pid)
            },
            onOpenVendorDetail = { vid ->
                lotDecisionNotice = null
                onOpenVendorDetail(vid)
            },
            onLogFreshLot = { cat, seg, vid ->
                lotDecisionNotice = null
                onSourceFreshLot(cat, seg, vid)
            }
        )
    }

    if (showRulesSheet) {
        AlertRulesBottomSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showRulesSheet = false }
        )
    }
}

@Composable
private fun NoticeSummaryTile(
    label: String,
    count: Int,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = SoleBookTextStyles.FinancialPrimary,
                color = fg
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextCharcoal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PriorityNoticeCard(
    notice: PriorityNotice,
    currency: String,
    unitSingular: String,
    onPrimaryAction: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val (badgeBg, badgeFg, icon) = when (notice.category) {
        NoticeCategory.OUT_OF_STOCK -> Triple(SemanticErrorSoft, SemanticError, Icons.Rounded.ErrorOutline)
        NoticeCategory.LOW_STOCK -> Triple(SemanticWarningSoft, SemanticWarning, Icons.Rounded.Inventory2)
        NoticeCategory.HIGH_DEMAND -> Triple(SemanticSuccessSoft, SemanticSuccess, Icons.Rounded.LocalFireDepartment)
        NoticeCategory.PRICE_MARGIN -> Triple(EmeraldSoft, EmeraldDark, Icons.Rounded.PriceChange)
        NoticeCategory.DEAD_STOCK -> Triple(SemanticErrorSoft, SemanticError, Icons.Rounded.HourglassBottom)
        NoticeCategory.SLOW_DEMAND -> Triple(SemanticWarningSoft, SemanticWarning, Icons.Rounded.TrendingDown)
        NoticeCategory.SOLD_ALERT -> Triple(SemanticSuccessSoft, SemanticSuccess, Icons.Rounded.PointOfSale)
    }

    Surface(
        color = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        border = BorderStroke(
            width = if (!notice.isRead && notice.priorityScore >= 80) 1.5.dp else 1.dp,
            color = if (!notice.isRead && notice.priorityScore >= 80) EmeraldPrimary.copy(alpha = 0.55f) else DividerGrey
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = notice.category.label,
                                color = badgeFg,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (notice.skuCode.isNotBlank()) {
                        SkuBoxTag(skuCode = notice.skuCode)
                    }
                    if (notice.rackLocation.isNotBlank()) {
                        Surface(color = SurfaceSubtle, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = notice.rackLocation,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondaryGrey,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                if (!notice.isRead) {
                    Surface(color = EmeraldPrimary, shape = RoundedCornerShape(50)) {
                        Text(
                            text = "NEW",
                            color = PureWhite,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(text = notice.title, style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
            Text(text = notice.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondaryGrey)

            if (notice.costPrice != null && notice.currentSellingPrice != null) {
                Surface(
                    color = SurfaceSubtle,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Cost", style = MaterialTheme.typography.labelSmall, color = TextMutedGrey)
                            Text(
                                Formatters.formatCurrency(notice.costPrice, currency),
                                style = SoleBookTextStyles.FinancialInline,
                                color = TextCharcoal
                            )
                        }
                        Column {
                            Text("Current Sell", style = MaterialTheme.typography.labelSmall, color = TextMutedGrey)
                            Text(
                                Formatters.formatCurrency(notice.currentSellingPrice, currency),
                                style = SoleBookTextStyles.FinancialInline,
                                color = TextCharcoal
                            )
                        }
                        if (notice.suggestedSellingPrice != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Suggested", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                                Text(
                                    Formatters.formatCurrency(notice.suggestedSellingPrice, currency),
                                    style = SoleBookTextStyles.FinancialInline,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            val profit = notice.currentSellingPrice - notice.costPrice
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Profit/$unitSingular", style = MaterialTheme.typography.labelSmall, color = TextMutedGrey)
                                Text(
                                    Formatters.formatCurrency(profit, currency),
                                    style = SoleBookTextStyles.FinancialInline,
                                    color = if (profit > 0) SemanticSuccess else SemanticError,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryButton(
                    text = notice.actionLabel,
                    onClick = onPrimaryAction,
                    modifier = Modifier.weight(1f),
                    testTag = "notice_action_${notice.noticeKey}"
                )
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DividerGrey)
                ) {
                    Icon(Icons.Rounded.Snooze, contentDescription = "Snooze 24 hours", tint = TextSecondaryGrey, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("24h", color = TextCharcoal)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Done, contentDescription = "Mark Done", tint = SemanticSuccess)
                }
            }
        }
    }
}
