package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.BusinessCategory
import com.example.domain.BusinessChannel
import com.example.domain.BusinessOperationMode
import com.example.domain.CurrencyOption
import com.example.ui.components.BusinessCategorySelector
import com.example.ui.components.FormField
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SoleBookSpacing
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsBusinessProfileSheet(
    initialName: String,
    initialCategoryKey: String,
    initialCustomType: String,
    initialChannelKey: String,
    initialCurrency: String,
    initialOperationModeKey: String = BusinessOperationMode.RETAIL.key,
    onDismiss: () -> Unit,
    onSave: (name: String, categoryKey: String, customType: String, channelKey: String, currency: String, operationModeKey: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var categoryKey by remember { mutableStateOf(initialCategoryKey) }
    var customType by remember { mutableStateOf(initialCustomType) }
    var channelKey by remember { mutableStateOf(initialChannelKey) }
    var currency by remember { mutableStateOf(initialCurrency) }
    var operationModeKey by remember { mutableStateOf(initialOperationModeKey) }
    var showNameError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(topStart = SoleBookSpacing.dialogRadius, topEnd = SoleBookSpacing.dialogRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SoleBookSpacing.screenHorizontal)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Edit Business Profile",
                style = MaterialTheme.typography.headlineMedium,
                color = TextCharcoal
            )
            Text(
                text = "Your business category adjusts product fields, unit labels, and variants across the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryGrey
            )

            FormField(
                value = name,
                onValueChange = {
                    name = it
                    if (showNameError && it.trim().isNotEmpty()) showNameError = false
                },
                label = "Business Name",
                placeholder = "e.g. Sharma Kirana & General",
                errorText = if (showNameError) "Business name cannot be empty." else null,
                testTag = "profile_business_name_input"
            )

            Text("Currency", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CurrencyOption.entries.forEach { opt ->
                    FilterChip(
                        selected = currency == opt.symbol,
                        onClick = { currency = opt.symbol },
                        label = {
                            Text(
                                text = opt.code,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("profile_currency_${opt.code}")
                    )
                }
            }

            Text("How do you sell?", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BusinessChannel.entries.forEach { ch ->
                    val selected = channelKey == ch.key
                    Surface(
                        onClick = { channelKey = ch.key },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) EmeraldSoft else SurfaceWhite,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) EmeraldPrimary else DividerGrey
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("profile_channel_${ch.key}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ch.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) EmeraldDark else TextCharcoal
                                )
                                Text(
                                    text = ch.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryGrey
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text("Business Mode (Retail / Wholesale / Both)", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BusinessOperationMode.entries.forEach { opMode ->
                    val selected = operationModeKey == opMode.key
                    Surface(
                        onClick = { operationModeKey = opMode.key },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) EmeraldSoft else SurfaceWhite,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) EmeraldPrimary else DividerGrey
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("profile_op_mode_${opMode.key}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = opMode.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) EmeraldDark else TextCharcoal
                                )
                                Text(
                                    text = opMode.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryGrey
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text("Business Category", style = MaterialTheme.typography.titleMedium, color = TextCharcoal)
            BusinessCategorySelector(
                selectedCategoryKey = categoryKey,
                onSelectCategory = { cat ->
                    categoryKey = cat.key
                    if (cat != BusinessCategory.OTHER) customType = ""
                },
                customType = customType,
                onCustomTypeChange = { customType = it }
            )

            PrimaryButton(
                text = "Save Business Profile",
                onClick = {
                    if (name.trim().isEmpty()) {
                        showNameError = true
                    } else {
                        onSave(name.trim(), categoryKey, customType.trim(), channelKey, currency, operationModeKey)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "confirm_save_business_profile"
            )
        }
    }
}

@Composable
internal fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
