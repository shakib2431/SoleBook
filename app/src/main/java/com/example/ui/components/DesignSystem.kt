package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ProductEntity
import com.example.domain.BusinessCategory
import com.example.ui.theme.*
import com.example.util.Formatters

/**
 * 4dp-based layout spacing tokens for SoleBook.
 */
object SoleBookSpacing {
    val screenHorizontal = 18.dp
    val sectionGap = 20.dp
    val componentGap = 12.dp
    val cardPadding = 16.dp
    val compactPadding = 12.dp
    val cardRadius = 14.dp
    val dialogRadius = 20.dp
    val inputHeight = 54.dp
    val buttonHeight = 52.dp
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = SoleBookSpacing.buttonHeight)
            .let { if (testTag != null) it.testTag(testTag) else it },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = PureWhite,
            disabledContainerColor = DividerGrey,
            disabledContentColor = TextMutedGrey
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    testTag: String? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 50.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (enabled) accentColor.copy(alpha = 0.45f) else DividerGrey),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceWhite,
            contentColor = accentColor
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    trailingAction: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = TextSecondaryGrey
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Clear search",
                            tint = TextSecondaryGrey,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                trailingAction?.invoke()
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceWhite,
            unfocusedContainerColor = SurfaceWhite,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = DividerGrey,
            focusedTextColor = TextCharcoal,
            unfocusedTextColor = TextCharcoal
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SoleBookSpacing.inputHeight)
            .let { if (testTag != null) it.testTag(testTag) else it }
    )
}

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    errorText: String? = null,
    helperText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    testTag: String? = null
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            placeholder = if (placeholder.isNotBlank()) {
                { Text(placeholder, color = TextMutedGrey) }
            } else null,
            leadingIcon = leadingIcon,
            isError = !errorText.isNullOrBlank(),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceWhite,
                unfocusedContainerColor = SurfaceWhite,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = DividerGrey,
                errorBorderColor = SemanticError,
                focusedTextColor = TextCharcoal,
                unfocusedTextColor = TextCharcoal
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SoleBookSpacing.inputHeight)
                .let { if (testTag != null) it.testTag(testTag) else it }
        )
        if (!errorText.isNullOrBlank()) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelSmall,
                color = SemanticError,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        } else if (!helperText.isNullOrBlank()) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryGrey,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextCharcoal,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!actionLabel.isNullOrBlank() && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun FinancialMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueColor: Color = TextCharcoal,
    badgeText: String? = null,
    badgePositive: Boolean? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.let {
            if (onClick != null) it.clickable(onClick = onClick) else it
        },
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondaryGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!badgeText.isNullOrBlank()) {
                    val (bg, fg) = when (badgePositive) {
                        true -> SemanticSuccessSoft to SemanticSuccess
                        false -> SemanticErrorSoft to SemanticError
                        null -> SurfaceSubtle to TextSecondaryGrey
                    }
                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = fg,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = value,
                style = SoleBookTextStyles.FinancialCompact,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StockStatusIndicator(
    totalStock: Int,
    reorderLevel: Int,
    modifier: Modifier = Modifier
) {
    val (label, dotColor, bgColor, fgColor) = when {
        totalStock <= 0 -> Quad("Out of stock", SemanticError, SemanticErrorSoft, SemanticError)
        totalStock <= reorderLevel -> Quad("Low stock", SemanticWarning, SemanticWarningSoft, SemanticWarning)
        else -> Quad("In stock", SemanticSuccess, SemanticSuccessSoft, SemanticSuccess)
    }
    Surface(
        modifier = modifier,
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fgColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
fun ProductListItem(
    product: ProductEntity,
    totalStock: Int,
    currencySymbol: String,
    unitPlural: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SoleBookSpacing.cardRadius),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductPhoto(
                photoUri = product.photoUri,
                contentDescription = product.name,
                cornerRadius = 10.dp,
                modifier = Modifier.size(54.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextCharcoal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metaParts = listOfNotNull(
                    product.skuCode.takeIf { it.isNotBlank() },
                    product.category.takeIf { it.isNotBlank() },
                    product.brand.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                Text(
                    text = metaParts,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StockStatusIndicator(
                        totalStock = totalStock,
                        reorderLevel = product.reorderLevel
                    )
                    val unitText = product.unitLabel.ifBlank { unitPlural }
                    Text(
                        text = "$totalStock $unitText",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryGrey,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = Formatters.formatCurrency(product.sellingPrice, currencySymbol),
                    style = SoleBookTextStyles.FinancialInline,
                    color = TextCharcoal,
                    fontWeight = FontWeight.Bold
                )
                trailingAction?.invoke()
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextCharcoal,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryGrey,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            PrimaryButton(
                text = primaryLabel,
                onClick = onPrimaryClick,
                modifier = Modifier.fillMaxWidth(),
                testTag = "empty_primary_action"
            )
            if (!secondaryLabel.isNullOrBlank() && onSecondaryClick != null) {
                SecondaryButton(
                    text = secondaryLabel,
                    onClick = onSecondaryClick,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "empty_secondary_action"
                )
            }
        }
    }
}

@Composable
fun BusinessCategorySelector(
    selectedCategoryKey: String,
    onSelectCategory: (BusinessCategory) -> Unit,
    customType: String,
    onCustomTypeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) BusinessCategory.entries else {
            BusinessCategory.entries.filter {
                it.displayName.lowercase().contains(q) || it.searchKeywords.contains(q)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search business type (e.g. Grocery, Clothing)...",
            testTag = "category_search_input"
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filtered.forEach { cat ->
                val isSelected = cat.key == selectedCategoryKey
                Surface(
                    onClick = { onSelectCategory(cat) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("category_option_${cat.key}"),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else SurfaceWhite,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else DividerGrey
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = categoryIcon(cat),
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else TextSecondaryGrey,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = cat.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else TextCharcoal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        if (selectedCategoryKey == BusinessCategory.OTHER.key) {
            FormField(
                value = customType,
                onValueChange = onCustomTypeChange,
                label = "Specify your business type (optional)",
                placeholder = "e.g. Pharmacy, Pet Shop, Bakery",
                testTag = "custom_business_type_input"
            )
        }
    }
}

private fun categoryIcon(cat: BusinessCategory): ImageVector = when (cat) {
    BusinessCategory.GROCERY -> Icons.Rounded.LocalGroceryStore
    BusinessCategory.CLOTHING -> Icons.Rounded.Checkroom
    BusinessCategory.FOOTWEAR -> Icons.Rounded.IceSkating
    BusinessCategory.ELECTRONICS -> Icons.Rounded.Smartphone
    BusinessCategory.COSMETICS -> Icons.Rounded.FaceRetouchingNatural
    BusinessCategory.HARDWARE -> Icons.Rounded.Build
    BusinessCategory.STATIONERY -> Icons.AutoMirrored.Rounded.MenuBook
    BusinessCategory.GENERAL -> Icons.Rounded.Storefront
    BusinessCategory.OTHER -> Icons.Rounded.Category
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(SoleBookSpacing.dialogRadius),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextCharcoal
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryGrey
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) SemanticError else MaterialTheme.colorScheme.primary,
                    contentColor = PureWhite
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = TextSecondaryGrey, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun LoadingState(
    message: String = "Loading your business...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasWarmWhite)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondaryGrey
        )
    }
}

@Composable
fun ErrorState(
    title: String,
    message: String,
    retryLabel: String = "Try again",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SemanticErrorSoft,
        border = BorderStroke(1.dp, SemanticError.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = SemanticError)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SemanticError
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextCharcoal
            )
            if (onRetry != null) {
                Spacer(Modifier.height(4.dp))
                SecondaryButton(
                    text = retryLabel,
                    onClick = onRetry,
                    accentColor = SemanticError
                )
            }
        }
    }
}
