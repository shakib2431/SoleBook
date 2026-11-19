package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.BusinessChannel
import com.example.domain.BusinessOperationMode
import com.example.notifications.SoleBookNotificationHelper
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.ShopBrandingEditorContent
import com.example.ui.components.SoleBookSpacing
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel
import com.example.util.AppLanguage
import com.example.util.ExportUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val settings = state.settings
    val industry = state.industryConfig

    var shopName by remember(settings.shopName) { mutableStateOf(settings.shopName) }
    var shortHeaderName by remember(settings.shortHeaderName) { mutableStateOf(settings.shortHeaderName) }
    var headerBrandMode by remember(settings.headerBrandMode) { mutableStateOf(settings.headerBrandMode) }
    var currencySymbol by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }
    var languageCode by remember(settings.languageCode) { mutableStateOf(settings.languageCode) }
    var gstEnabled by remember(settings.gstEnabled) { mutableStateOf(settings.gstEnabled) }
    var gstInclusive by remember(settings.gstInclusive) { mutableStateOf(settings.gstInclusive) }
    var gstRateText by remember(settings.gstRate) { mutableStateOf(settings.gstRate.toInt().toString()) }
    var leadTimeText by remember(settings.defaultLeadTimeDays) { mutableStateOf(settings.defaultLeadTimeDays.toString()) }

    var alertsEnabled by remember(settings.alertsEnabled) { mutableStateOf(settings.alertsEnabled) }
    var stockAlertsEnabled by remember(settings.stockAlertsEnabled) { mutableStateOf(settings.stockAlertsEnabled) }
    var demandAlertsEnabled by remember(settings.demandAlertsEnabled) { mutableStateOf(settings.demandAlertsEnabled) }
    var marginAlertsEnabled by remember(settings.marginAlertsEnabled) { mutableStateOf(settings.marginAlertsEnabled) }
    var instantSaleAlertsEnabled by remember(settings.instantSaleAlertsEnabled) { mutableStateOf(settings.instantSaleAlertsEnabled) }
    var alertIntervalHours by remember(settings.alertIntervalHours) { mutableStateOf(settings.alertIntervalHours) }
    var maxAlertsPerDay by remember(settings.maxAlertsPerDay) { mutableStateOf(settings.maxAlertsPerDay) }
    var quietHoursEnabled by remember(settings.quietStartHour, settings.quietEndHour) {
        mutableStateOf(settings.quietStartHour != settings.quietEndHour)
    }
    var hasNotifPermission by remember {
        mutableStateOf(SoleBookNotificationHelper.hasNotificationPermission(context))
    }

    var showProfileSheet by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotifPermission = granted || SoleBookNotificationHelper.hasNotificationPermission(context)
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.restoreJsonFromUri(it) }
        }
    )

    Scaffold(
        containerColor = CanvasWarmWhite,
        contentColor = TextCharcoal,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Text(
                            text = state.strings.settingsTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextCharcoal
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextCharcoal)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
                )
                HorizontalDivider(thickness = 1.dp, color = DividerGrey)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = SoleBookSpacing.screenHorizontal, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Business Profile (Multi-industry category, channel, currency)
            item {
                SettingsSectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Business, contentDescription = null, tint = EmeraldPrimary)
                        Text("Business Profile", style = MaterialTheme.typography.titleLarge, color = TextCharcoal)
                    }
                    Surface(
                        color = SurfaceSubtle,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = settings.shopName.ifBlank { "My Shop" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextCharcoal
                            )
                            Text(
                                text = "${industry.displayCategoryName} • ${BusinessChannel.fromKey(settings.businessChannel).title} • ${settings.currencySymbol}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondaryGrey
                            )
                        }
                    }
                    Text("Business Mode", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    Text(
                        text = "Switch Retail & Wholesale anytime from your Dashboard top bar, or lock one mode for inner tabs here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryGrey
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            BusinessOperationMode.RETAIL to "Retail",
                            BusinessOperationMode.WHOLESALE to "Wholesale",
                            BusinessOperationMode.BOTH to "Both"
                        ).forEach { (opMode, label) ->
                            val selected = settings.businessOperationMode == opMode.key
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.updateBusinessProfile(
                                        shopName = settings.shopName,
                                        businessCategory = settings.businessCategory,
                                        customBusinessType = settings.customBusinessType,
                                        businessChannel = settings.businessChannel,
                                        currencySymbol = settings.currencySymbol,
                                        businessOperationMode = opMode.key
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = PureWhite,
                                    containerColor = SurfaceSubtle,
                                    labelColor = TextCharcoal
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_quick_op_mode_${opMode.key}")
                            )
                        }
                    }
                    SecondaryButton(
                        text = "Edit Mode, Category & Currency",
                        icon = Icons.Rounded.Edit,
                        onClick = { showProfileSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "edit_business_profile_button"
                    )
                }
            }

            // Shop Logo & Flexible Naming
            item {
                SettingsSectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Storefront, contentDescription = null, tint = EmeraldPrimary)
                        Text("Business Logo & Header Name", style = MaterialTheme.typography.titleLarge, color = TextCharcoal)
                    }
                    ShopBrandingEditorContent(
                        shopName = shopName,
                        onShopNameChange = { shopName = it },
                        shortHeaderName = shortHeaderName,
                        onShortHeaderNameChange = { shortHeaderName = it },
                        headerBrandMode = headerBrandMode,
                        onHeaderBrandModeChange = { headerBrandMode = it },
                        shopLogoUri = settings.shopLogoUri,
                        onPickLogoUri = { uri -> viewModel.saveShopLogoFromUri(uri) },
                        onRemoveLogo = { viewModel.removeShopLogo() }
                    )
                    PrimaryButton(
                        text = "Apply Logo & Name",
                        icon = Icons.Rounded.Check,
                        onClick = {
                            viewModel.saveShopBranding(shopName, shortHeaderName, headerBrandMode)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "save_branding_settings_button"
                    )
                }
            }

            // Currency & Language
            item {
                SettingsSectionCard {
                    Text("Currency & Language", style = MaterialTheme.typography.titleLarge, color = TextCharcoal)
                    Text("Currency", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("₹", "$", "£", "€").forEach { sym ->
                            FilterChip(
                                selected = currencySymbol == sym,
                                onClick = { currencySymbol = sym },
                                label = { Text(sym, style = MaterialTheme.typography.titleMedium) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                        }
                    }
                    Text("Language", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = languageCode == lang.code,
                                onClick = { languageCode = lang.code },
                                label = { Text(lang.displayName, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                        }
                    }
                }
            }

            // Smart Phone Pop-Up Alerts
            item {
                SettingsSectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = EmeraldPrimary)
                        Text("Smart Phone Alerts", style = MaterialTheme.typography.titleLarge, color = TextCharcoal)
                    }
                    Text(
                        "Sends only high-priority stock, demand, dead-stock, and margin alerts—capped daily so your phone never gets noisy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryGrey
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                        SecondaryButton(
                            text = "Allow Notification Permission",
                            icon = Icons.Rounded.Notifications,
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "settings_allow_notif_button"
                        )
                    }

                    SettingsSwitchRow("Enable Smart Alerts", alertsEnabled) { alertsEnabled = it }
                    SettingsSwitchRow("Out of Stock & Low Stock Alerts", stockAlertsEnabled) { stockAlertsEnabled = it }
                    SettingsSwitchRow("High Demand, Slow & Dead Stock", demandAlertsEnabled) { demandAlertsEnabled = it }
                    SettingsSwitchRow("Cost vs Selling Price & Margin Tips", marginAlertsEnabled) { marginAlertsEnabled = it }
                    SettingsSwitchRow("Instant Alert After Every Sale", instantSaleAlertsEnabled) { instantSaleAlertsEnabled = it }
                    SettingsSwitchRow("Night Quiet Hours (9 PM – 8 AM)", quietHoursEnabled) { quietHoursEnabled = it }

                    Text("Smart Digest Frequency", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 4, 6, 12).forEach { hrs ->
                            FilterChip(
                                selected = alertIntervalHours == hrs,
                                onClick = { alertIntervalHours = hrs },
                                label = { Text("${hrs}h") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                        }
                    }

                    Text("Daily Alert Cap", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 4, 6).forEach { cap ->
                            FilterChip(
                                selected = maxAlertsPerDay == cap,
                                onClick = { maxAlertsPerDay = cap },
                                label = { Text("$cap/day") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                        }
                    }

                    SecondaryButton(
                        text = "Send Test Alert Now",
                        icon = Icons.Rounded.NotificationAdd,
                        accentColor = TextCharcoal,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.sendTestPhoneNotification()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "settings_test_popup_button"
                    )
                }
            }

            // Tax (GST) & Reorder
            item {
                SettingsSectionCard {
                    Text("Tax (GST) & Reorder", style = MaterialTheme.typography.titleLarge, color = TextCharcoal)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable GST Billing", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
                            Text("Optional. Configure whenever your business needs GST.", style = MaterialTheme.typography.labelMedium, color = TextMutedGrey)
                        }
                        Switch(checked = gstEnabled, onCheckedChange = { gstEnabled = it })
                    }
                    if (gstEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = gstInclusive,
                                onClick = { gstInclusive = true },
                                label = { Text("Tax Inclusive") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                            FilterChip(
                                selected = !gstInclusive,
                                onClick = { gstInclusive = false },
                                label = { Text("Tax Exclusive") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            )
                        }
                        OutlinedTextField(
                            value = gstRateText,
                            onValueChange = { s -> gstRateText = s.filter { it.isDigit() || it == '.' } },
                            label = { Text("GST Rate (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = leadTimeText,
                        onValueChange = { s -> leadTimeText = s.filter { it.isDigit() } },
                        label = { Text("Supplier Delivery Lead Time (Days)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    PrimaryButton(
                        text = "Save Preferences",
                        onClick = {
                            viewModel.updateSettings(
                                settings.copy(
                                    shopName = shopName.trim().ifBlank { settings.shopName.ifBlank { "My Shop" } },
                                    shortHeaderName = shortHeaderName.trim(),
                                    headerBrandMode = headerBrandMode,
                                    currencySymbol = currencySymbol,
                                    languageCode = languageCode,
                                    gstEnabled = gstEnabled,
                                    gstInclusive = gstInclusive,
                                    gstRate = gstRateText.toDoubleOrNull() ?: 12.0,
                                    defaultLeadTimeDays = (leadTimeText.toIntOrNull() ?: 3).coerceIn(1, 30),
                                    alertsEnabled = alertsEnabled,
                                    stockAlertsEnabled = stockAlertsEnabled,
                                    demandAlertsEnabled = demandAlertsEnabled,
                                    marginAlertsEnabled = marginAlertsEnabled,
                                    instantSaleAlertsEnabled = instantSaleAlertsEnabled,
                                    alertIntervalHours = alertIntervalHours,
                                    maxAlertsPerDay = maxAlertsPerDay,
                                    quietStartHour = if (quietHoursEnabled) 21 else 0,
                                    quietEndHour = if (quietHoursEnabled) 8 else 0
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "save_settings_button"
                    )
                }
            }

            // Sample Data Controls
            item {
                SettingsSectionCard {
                    Text("Sample Data (Quick Explore)", style = MaterialTheme.typography.titleLarge, color = TextCharcoal)
                    Text(
                        "Seeds 15 sample products, 60 days of sales, 10 customers, returns, and business expenses so you can explore metrics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryGrey
                    )
                    SecondaryButton(
                        text = "Load Sample Data",
                        icon = Icons.Rounded.AutoAwesome,
                        accentColor = TextCharcoal,
                        onClick = { viewModel.loadDemoData() },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "settings_load_demo"
                    )
                    SecondaryButton(
                        text = "Clear All Data",
                        icon = Icons.Rounded.DeleteSweep,
                        accentColor = SemanticError,
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "settings_clear_demo"
                    )
                }
            }

            // Backup, Restore & CSV Exports
            item {
                SettingsSectionCard {
                    Text("Backup, Restore & CSV Export", style = MaterialTheme.typography.titleLarge, color = TextCharcoal)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            text = "Backup JSON",
                            onClick = {
                                viewModel.exportJsonBackup { file ->
                                    ExportUtils.shareFile(context, file, "application/json", "Save SoleBook JSON Backup")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "export_json_button"
                        )
                        SecondaryButton(
                            text = "Restore JSON",
                            accentColor = TextCharcoal,
                            onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f),
                            testTag = "restore_json_button"
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.exportSalesCsv { file ->
                                    ExportUtils.shareFile(context, file, "text/csv", "Export Sales CSV")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DividerGrey),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Sales CSV", color = TextCharcoal) }
                        OutlinedButton(
                            onClick = {
                                viewModel.exportProductsCsv { file ->
                                    ExportUtils.shareFile(context, file, "text/csv", "Export Stock CSV")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DividerGrey),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Stock CSV", color = TextCharcoal) }
                        OutlinedButton(
                            onClick = {
                                viewModel.exportCustomersCsv { file ->
                                    ExportUtils.shareFile(context, file, "text/csv", "Export Customers CSV")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DividerGrey),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Cust CSV", color = TextCharcoal) }
                    }

                    TextButton(
                        onClick = {
                            viewModel.replayWalkthrough()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Replay Quick Product Tour", color = EmeraldPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (showProfileSheet) {
        SettingsBusinessProfileSheet(
            initialName = settings.shopName,
            initialCategoryKey = settings.businessCategory,
            initialCustomType = settings.customBusinessType,
            initialChannelKey = settings.businessChannel,
            initialCurrency = settings.currencySymbol,
            initialOperationModeKey = settings.businessOperationMode,
            onDismiss = { showProfileSheet = false },
            onSave = { newName, newCategory, newCustomType, newChannel, newCurrency, newOperationMode ->
                shopName = newName
                currencySymbol = newCurrency
                viewModel.updateBusinessProfile(newName, newCategory, newCustomType, newChannel, newCurrency, newOperationMode)
                showProfileSheet = false
            }
        )
    }

    if (showClearConfirm) {
        ConfirmationDialog(
            title = "Clear All Business Data?",
            message = "This removes all products, sales, customers, and expenses from this device. Export a JSON backup first if you want to keep a copy.",
            confirmLabel = "Clear Everything",
            onConfirm = {
                viewModel.clearAllData()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
            isDestructive = true
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextCharcoal,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
