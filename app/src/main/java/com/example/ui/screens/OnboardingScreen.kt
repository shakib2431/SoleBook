package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.BusinessCategory
import com.example.domain.BusinessChannel
import com.example.domain.BusinessOperationMode
import com.example.domain.CurrencyOption
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun OnboardingScreen(
    onCompleteGoDashboard: (
        shopName: String,
        businessCategory: String,
        customBusinessType: String,
        currencySymbol: String,
        businessChannel: String
    ) -> Unit,
    onCompleteAddFirstProduct: (
        shopName: String,
        businessCategory: String,
        customBusinessType: String,
        currencySymbol: String,
        businessChannel: String
    ) -> Unit,
    onSelectOperationMode: (String) -> Unit = {},
    onRestoreBackupClick: (() -> Unit)? = null
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var businessName by rememberSaveable { mutableStateOf("") }
    var businessNameError by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryKey by rememberSaveable { mutableStateOf(BusinessCategory.GENERAL.key) }
    var customBusinessType by rememberSaveable { mutableStateOf("") }
    var currencySymbol by rememberSaveable { mutableStateOf(CurrencyOption.INR.symbol) }
    var channelKey by rememberSaveable { mutableStateOf(BusinessChannel.PHYSICAL.key) }
    var operationModeKey by rememberSaveable { mutableStateOf(BusinessOperationMode.RETAIL.key) }

    BackHandler(enabled = step > 0) {
        step = (step - 1).coerceAtLeast(0)
    }

    if (step == 0) {
        WelcomeOnboardingStep(
            onGetStarted = { step = 1 },
            onRestoreBackupClick = onRestoreBackupClick,
            activePageIndex = 0,
            totalPages = 3
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CanvasWarmWhite
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
            ) {
                if (step in 1..2) {
                    OnboardingProgressHeader(
                        currentStep = step,
                        totalSteps = 2,
                        onBack = { step = (step - 1).coerceAtLeast(0) }
                    )
                }

                when (step) {
                    1 -> BusinessInfoStep(
                        businessName = businessName,
                        businessNameError = businessNameError,
                        onBusinessNameChange = {
                            businessName = it
                            if (businessNameError != null && it.isNotBlank()) {
                                businessNameError = null
                            }
                        },
                        categoryKey = categoryKey,
                        onSelectCategory = { categoryKey = it.key },
                        customBusinessType = customBusinessType,
                        onCustomBusinessTypeChange = { customBusinessType = it },
                        currencySymbol = currencySymbol,
                        onSelectCurrency = { currencySymbol = it.symbol },
                        onContinue = {
                            if (businessName.trim().isBlank()) {
                                businessNameError = "Please enter your business name to continue."
                            } else {
                                businessNameError = null
                                step = 2
                            }
                        }
                    )
                    2 -> BusinessPreferencesStep(
                        selectedOperationModeKey = operationModeKey,
                        onSelectOperationMode = { operationModeKey = it.key },
                        selectedChannelKey = channelKey,
                        onSelectChannel = { channelKey = it.key },
                        onContinue = { step = 3 },
                        onSkip = { step = 3 }
                    )
                    else -> SetupCompleteStep(
                        businessName = businessName.trim(),
                        categoryName = if (categoryKey == BusinessCategory.OTHER.key && customBusinessType.isNotBlank()) {
                            customBusinessType.trim()
                        } else {
                            BusinessCategory.fromKey(categoryKey).displayName
                        },
                        currencySymbol = currencySymbol,
                        operationModeTitle = BusinessOperationMode.fromKey(operationModeKey).title,
                        channelTitle = BusinessChannel.fromKey(channelKey).title,
                        onGoDashboard = {
                            onSelectOperationMode(operationModeKey)
                            onCompleteGoDashboard(
                                businessName.trim(),
                                categoryKey,
                                customBusinessType.trim(),
                                currencySymbol,
                                channelKey
                            )
                        },
                        onAddFirstProduct = {
                            onSelectOperationMode(operationModeKey)
                            onCompleteAddFirstProduct(
                                businessName.trim(),
                                categoryKey,
                                customBusinessType.trim(),
                                currencySymbol,
                                channelKey
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingProgressHeader(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SoleBookSpacing.screenHorizontal, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("onboarding_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = TextCharcoal
                )
            }
            Text(
                text = "Step $currentStep of $totalSteps",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondaryGrey
            )
            Spacer(Modifier.size(48.dp))
        }
        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = EmeraldPrimary,
            trackColor = DividerGrey
        )
    }
}

@Composable
private fun BusinessInfoStep(
    businessName: String,
    businessNameError: String?,
    onBusinessNameChange: (String) -> Unit,
    categoryKey: String,
    onSelectCategory: (BusinessCategory) -> Unit,
    customBusinessType: String,
    onCustomBusinessTypeChange: (String) -> Unit,
    currencySymbol: String,
    onSelectCurrency: (CurrencyOption) -> Unit,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SoleBookSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(SoleBookSpacing.sectionGap)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Tell us about your business",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextCharcoal
                )
                Text(
                    text = "We tailor stock fields and labels to fit what you sell.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryGrey
                )
            }

            FormField(
                value = businessName,
                onValueChange = onBusinessNameChange,
                label = "Business name *",
                placeholder = "e.g. Sharma Kirana, Trendz Apparel, Metro Footwear",
                errorText = businessNameError,
                testTag = "onboarding_business_name"
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Currency",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextCharcoal
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(CurrencyOption.INR, CurrencyOption.USD, CurrencyOption.AED).forEach { cur ->
                        val selected = currencySymbol == cur.symbol
                        Surface(
                            onClick = { onSelectCurrency(cur) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("currency_option_${cur.code}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) EmeraldSoft else SurfaceWhite,
                            border = BorderStroke(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) EmeraldPrimary else DividerGrey
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "${cur.code} (${cur.symbol.trim()})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) EmeraldDark else TextCharcoal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Business category",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextCharcoal
                )
                BusinessCategorySelector(
                    selectedCategoryKey = categoryKey,
                    onSelectCategory = onSelectCategory,
                    customType = customBusinessType,
                    onCustomTypeChange = onCustomBusinessTypeChange
                )
            }

            Spacer(Modifier.height(12.dp))
        }

        Surface(
            color = SurfaceWhite,
            border = BorderStroke(1.dp, DividerGrey),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoleBookSpacing.screenHorizontal, vertical = 12.dp)
            ) {
                PrimaryButton(
                    text = "Continue",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "onboarding_step2_continue"
                )
            }
        }
    }
}

@Composable
private fun BusinessPreferencesStep(
    selectedOperationModeKey: String,
    onSelectOperationMode: (BusinessOperationMode) -> Unit,
    selectedChannelKey: String,
    onSelectChannel: (BusinessChannel) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SoleBookSpacing.screenHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SoleBookSpacing.sectionGap)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Business Mode",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextCharcoal
                )
                Text(
                    text = "Choose Retail, Wholesale (BOM & Quotes), or Both with an instant switcher.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryGrey
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BusinessOperationMode.entries.forEach { opMode ->
                    val isSelected = opMode.key == selectedOperationModeKey
                    val opIcon: ImageVector = when (opMode) {
                        BusinessOperationMode.RETAIL -> Icons.Rounded.PointOfSale
                        BusinessOperationMode.WHOLESALE -> Icons.Rounded.PrecisionManufacturing
                        BusinessOperationMode.BOTH -> Icons.Rounded.SwapHoriz
                    }
                    Surface(
                        onClick = { onSelectOperationMode(opMode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("operation_mode_${opMode.key}"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) EmeraldSoft else SurfaceWhite,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) EmeraldPrimary else DividerGrey
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) SurfaceWhite else SurfaceSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = opIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) EmeraldPrimary else TextSecondaryGrey
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = opMode.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) EmeraldDark else TextCharcoal
                                )
                                Text(
                                    text = opMode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondaryGrey
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = EmeraldPrimary
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "How do you sell?",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextCharcoal
                )
                Text(
                    text = "Optional — helps organize billing & store preferences.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryGrey
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BusinessChannel.entries.forEach { ch ->
                    val isSelected = ch.key == selectedChannelKey
                    val icon: ImageVector = when (ch) {
                        BusinessChannel.PHYSICAL -> Icons.Rounded.Storefront
                        BusinessChannel.ONLINE -> Icons.Rounded.LocalShipping
                        BusinessChannel.BOTH -> Icons.Rounded.SyncAlt
                    }
                    Surface(
                        onClick = { onSelectChannel(ch) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("channel_option_${ch.key}"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) EmeraldSoft else SurfaceWhite,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) EmeraldPrimary else DividerGrey
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) SurfaceWhite else SurfaceSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) EmeraldPrimary else TextSecondaryGrey
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = ch.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) EmeraldDark else TextCharcoal
                                )
                                Text(
                                    text = ch.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondaryGrey
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = EmeraldPrimary
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                color = SurfaceWhite,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "GST billing, shop logo, and alerts can be configured anytime in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryGrey
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryButton(
                text = "Finish setup",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                testTag = "onboarding_step3_finish"
            )
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("onboarding_step3_skip")
            ) {
                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondaryGrey
                )
            }
        }
    }
}

@Composable
private fun SetupCompleteStep(
    businessName: String,
    categoryName: String,
    currencySymbol: String,
    operationModeTitle: String,
    channelTitle: String,
    onGoDashboard: () -> Unit,
    onAddFirstProduct: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(EmeraldSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Setup complete",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Your business is ready!",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextCharcoal,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "You're all set to manage stock, record sales, and track profit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryGrey,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = businessName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextCharcoal
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(color = EmeraldSoft, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldDark,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Surface(color = SurfaceSubtle, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "Currency: ${currencySymbol.trim()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondaryGrey,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = DividerGrey)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = operationModeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextCharcoal
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storefront,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = channelTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryGrey
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(
                text = "Go to my dashboard",
                onClick = onGoDashboard,
                modifier = Modifier.fillMaxWidth(),
                testTag = "onboarding_go_dashboard_btn"
            )
            SecondaryButton(
                text = "Add my first product",
                onClick = onAddFirstProduct,
                icon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth(),
                testTag = "onboarding_add_product_btn"
            )
        }
    }
}
