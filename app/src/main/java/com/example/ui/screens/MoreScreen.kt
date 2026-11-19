package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.SoleBookSpacing
import com.example.ui.components.SoleBookTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.SoleBookUiState
import com.example.util.Formatters

@Composable
fun MoreScreen(
    state: SoleBookUiState,
    onOpenCustomers: () -> Unit,
    onOpenSuppliers: () -> Unit,
    onOpenDataHub: () -> Unit = {},
    onOpenInsights: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val currency = state.settings.currencySymbol
    val bottomPadding = LocalGlassBottomPadding.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWarmWhite)
    ) {
        SoleBookTopBar(
            title = "More",
            subtitle = state.settings.shopName.ifBlank { "SoleBook Business Hub" },
            categoryBadge = state.industryConfig.displayCategoryName,
            onSettingsClick = onOpenSettings,
            unreadNoticeCount = state.unreadNoticeCount,
            onNoticesClick = onOpenNotices
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SoleBookSpacing.screenHorizontal,
                end = SoleBookSpacing.screenHorizontal,
                top = 16.dp,
                bottom = bottomPadding + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "OPERATIONS & PEOPLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            item {
                MoreDestinationRow(
                    icon = Icons.Rounded.UploadFile,
                    title = "Data Hub & AI Docs",
                    subtitle = "Excel/CSV import, Barcodes, Stock Counts, Paper Scan & POs",
                    onClick = onOpenDataHub,
                    testTag = "more_data_hub"
                )
            }
            item {
                val custDue = state.customers.sumOf { it.creditBalance }
                MoreDestinationRow(
                    icon = Icons.Rounded.Groups,
                    title = "Customers & Campaigns",
                    subtitle = if (custDue > 0) {
                        "${state.customers.size} customers • ${Formatters.formatCurrency(custDue, currency)} due"
                    } else {
                        "${state.customers.size} saved customers • WhatsApp campaigns"
                    },
                    onClick = onOpenCustomers,
                    testTag = "more_customers"
                )
            }
            item {
                val suppDue = state.vendors.sumOf { it.creditBalance }
                MoreDestinationRow(
                    icon = Icons.Rounded.LocalShipping,
                    title = "Suppliers",
                    subtitle = if (suppDue > 0) {
                        "${state.vendors.size} suppliers • ${Formatters.formatCurrency(suppDue, currency)} payable"
                    } else {
                        "${state.vendors.size} suppliers • Purchases & payables"
                    },
                    onClick = onOpenSuppliers,
                    testTag = "more_suppliers"
                )
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "INTELLIGENCE & RECORDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            item {
                MoreDestinationRow(
                    icon = Icons.Rounded.Insights,
                    title = "Business Insights",
                    subtitle = "Demand forecasts, reorder plan, margin & slow stock",
                    onClick = onOpenInsights,
                    testTag = "more_insights"
                )
            }
            item {
                MoreDestinationRow(
                    icon = Icons.Rounded.Assessment,
                    title = "Reports & Exports",
                    subtitle = "Daily closing report, Sales/Stock/Customers CSV, PDF & Backup",
                    onClick = onOpenReports,
                    testTag = "more_reports"
                )
            }
            item {
                MoreDestinationRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Notices & Alerts",
                    subtitle = if (state.unreadNoticeCount > 0) {
                        "${state.unreadNoticeCount} unread business alerts"
                    } else {
                        "Low stock, price tips & daily alerts"
                    },
                    badgeCount = state.unreadNoticeCount,
                    onClick = onOpenNotices,
                    testTag = "more_notices"
                )
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "CONFIGURATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            item {
                MoreDestinationRow(
                    icon = Icons.Rounded.Settings,
                    title = "Settings",
                    subtitle = "Business profile, logo, GST, currency, alerts & backup",
                    onClick = onOpenSettings,
                    testTag = "more_settings"
                )
            }
        }
    }
}

@Composable
private fun MoreDestinationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badgeCount: Int = 0,
    testTag: String? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it },
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(EmeraldSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextCharcoal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryGrey,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badgeCount > 0) {
                Surface(
                    color = SemanticErrorSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = SemanticError,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMutedGrey
            )
        }
    }
}
