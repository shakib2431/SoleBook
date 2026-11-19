package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.domain.ProductBadge
import com.example.domain.WorkspaceMode
import com.example.ui.theme.*
import com.example.util.ImageUtils
import java.io.File
import kotlin.math.roundToInt

val GlassBorderBrush: Brush
    get() = SolidColor(DividerGrey)

val LocalWorkspaceSwitchBar = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
fun WorkspaceModeSwitcher(
    activeMode: WorkspaceMode,
    onSelectMode: (WorkspaceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("workspace_mode_switcher"),
        color = SurfaceSubtle,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WorkspaceMode.entries.forEach { mode ->
                val selected = activeMode == mode
                val icon = if (mode == WorkspaceMode.RETAIL) {
                    Icons.Rounded.PointOfSale
                } else {
                    Icons.Rounded.PrecisionManufacturing
                }
                val activePillColor = if (mode == WorkspaceMode.WHOLESALE) {
                    WholesaleBluePrimary
                } else {
                    EmeraldPrimary
                }
                Surface(
                    onClick = { if (!selected) onSelectMode(mode) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("workspace_switch_${mode.key}"),
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) activePillColor else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) PureWhite else TextSecondaryGrey,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) PureWhite else TextSecondaryGrey,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AtmosphericBackdrop(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasWarmWhite)
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    containerColor: Color = GlassSurface,
    borderColor: Color? = null,
    elevation: Dp = 6.dp,
    glassStyle: GlassStyle = GlassStyle.Standard,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedStyle = when {
        containerColor == GlassSurfaceStrong || containerColor == GlassSurfaceElevated -> GlassStyle.Elevated
        containerColor == TerracottaSoft || containerColor == GlassTerracottaTint -> GlassStyle.TintedTerracotta
        containerColor == TealSoft || containerColor == GlassTealTint -> GlassStyle.TintedTeal
        containerColor == ProfitGreenSoft || containerColor == GlassProfitTint -> GlassStyle.TintedProfit
        containerColor == LossRedSoft || containerColor == GlassLossTint -> GlassStyle.TintedLoss
        containerColor == AmberWarnSoft || containerColor == GlassAmberTint -> GlassStyle.TintedAmber
        else -> glassStyle
    }
    GlassSurfaceContainer(
        modifier = modifier,
        shape = shape,
        style = resolvedStyle,
        customContainerColor = if (containerColor == GlassSurface) null else containerColor,
        borderColor = borderColor,
        elevationOverride = elevation,
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun DarkGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    elevation: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurfaceContainer(
        modifier = modifier,
        shape = shape,
        style = GlassStyle.DarkObsidian,
        elevationOverride = elevation,
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun ProductPhoto(
    photoUri: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp
) {
    val drawableRes = ImageUtils.resolveDrawableRes(photoUri)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(WarmPaper.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            drawableRes != null -> {
                Image(
                    painter = painterResource(id = drawableRes),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            photoUri.startsWith("content://") || photoUri.startsWith("file://") -> {
                AsyncImage(
                    model = android.net.Uri.parse(photoUri),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            photoUri.isNotBlank() && File(photoUri).exists() -> {
                AsyncImage(
                    model = File(photoUri),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.ShoppingBag,
                    contentDescription = contentDescription,
                    tint = LeatherTan,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun SkuBoxTag(
    skuCode: String,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = GlassDarkSurface,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, GlassDarkBorder),
        shape = RoundedCornerShape(if (large) 12.dp else 9.dp)
    ) {
        Text(
            text = skuCode,
            color = PureWhite,
            fontFamily = SoraDisplay,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (large) 20.sp else 12.5.sp,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(
                horizontal = if (large) 14.dp else 9.dp,
                vertical = if (large) 6.dp else 3.5.dp
            )
        )
    }
}

@Composable
fun ProductBadgeChip(badge: ProductBadge, modifier: Modifier = Modifier) {
    val (bg, fg) = when (badge) {
        ProductBadge.FAST_MOVER -> ProfitGreenSoft to ProfitGreen
        ProductBadge.SLOW_MOVER -> AmberWarnSoft to AmberWarn
        ProductBadge.DEAD_STOCK -> LossRedSoft to LossRed
        ProductBadge.LOW_STOCK -> TerracottaSoft to BurntTerracottaDark
        ProductBadge.OUT_OF_STOCK -> LossRed to PureWhite
    }
    Surface(
        modifier = modifier,
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.32f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = badge.label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun HeadlineMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    deltaPercent: Double? = null,
    valueColor: Color = TextCharcoal,
    accentContainer: Color = SurfaceWhite,
    accentColor: Color = valueColor.takeIf { it != TextCharcoal } ?: MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (deltaPercent != null) {
                    val isUp = deltaPercent >= 0
                    val pillBg = if (isUp) SemanticSuccessSoft else SemanticErrorSoft
                    val pillFg = if (isUp) SemanticSuccess else SemanticError
                    val arrow = if (isUp) "▲" else "▼"
                    Surface(
                        color = pillBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$arrow ${kotlin.math.abs(deltaPercent).roundToInt()}%",
                            color = pillFg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = SoleBookTextStyles.FinancialPrimary,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey
                )
            }
        }
    }
}

@Composable
fun SparklineChart(
    points: List<Int>,
    modifier: Modifier = Modifier,
    lineColor: Color = BurntTerracotta
) {
    val safePoints = if (points.size < 2) listOf(0, 0) else points
    val maxVal = (safePoints.maxOrNull() ?: 1).coerceAtLeast(1)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stepX = w / (safePoints.size - 1).coerceAtLeast(1)
        val path = Path()
        val fillPath = Path()

        safePoints.forEachIndexed { idx, v ->
            val x = idx * stepX
            val y = h - ((v.toFloat() / maxVal.toFloat()) * (h * 0.82f)) - (h * 0.08f)
            if (idx == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(w, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.26f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )

        // Draw last point glow + dot
        val lastY = h - ((safePoints.last().toFloat() / maxVal.toFloat()) * (h * 0.82f)) - (h * 0.08f)
        drawCircle(
            color = lineColor.copy(alpha = 0.24f),
            radius = 14f,
            center = Offset(w, lastY)
        )
        drawCircle(
            color = lineColor,
            radius = 7.5f,
            center = Offset(w, lastY)
        )
    }
}

internal fun hasValidLogoFile(logoUri: String): Boolean {
    if (logoUri.isBlank()) return false
    return if (logoUri.startsWith("/")) File(logoUri).exists() else true
}

internal fun shopMonogram(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "SB"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
    }
}

@Composable
fun ShopLogoBadge(
    logoUri: String,
    shopName: String,
    wideWordmark: Boolean = false,
    showUploadHintBadge: Boolean = true,
    sizeDp: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hasLogo = hasValidLogoFile(logoUri)
    val badgeShape = RoundedCornerShape(14.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(badgeShape)
                        .clickable(onClick = onClick)
                        .testTag("shop_logo_button")
                } else {
                    Modifier
                }
            )
    ) {
        if (hasLogo) {
            Surface(
                color = PureWhite,
                shape = badgeShape,
                border = BorderStroke(1.dp, GlassSpecularTop),
                shadowElevation = 2.dp,
                modifier = if (wideWordmark) {
                    Modifier
                        .widthIn(min = 56.dp, max = 148.dp)
                        .height(sizeDp)
                } else {
                    Modifier.size(sizeDp)
                }
            ) {
                AsyncImage(
                    model = if (logoUri.startsWith("/")) File(logoUri) else logoUri,
                    contentDescription = "$shopName logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(sizeDp)
                    .clip(badgeShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = shopMonogram(shopName),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = JakartaHeading,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                    maxLines = 1
                )
            }
        }

        if (showUploadHintBadge && onClick != null && !hasLogo) {
            Surface(
                color = PureWhite,
                shape = CircleShape,
                border = BorderStroke(1.dp, DividerGrey),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = "Upload business logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SoleBookTopBar(
    title: String,
    subtitle: String? = null,
    onHelpClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    unreadNoticeCount: Int = 0,
    onNoticesClick: (() -> Unit)? = null,
    logoUri: String = "",
    showLogoSlot: Boolean = false,
    headerBrandMode: String = "LOGO_AND_NAME",
    fullShopNameForMonogram: String = title,
    categoryBadge: String? = null,
    onLogoClick: (() -> Unit)? = null,
    extraAction: @Composable (() -> Unit)? = null
) {
    val hasLogo = hasValidLogoFile(logoUri)
    val showBadge = showLogoSlot && headerBrandMode != "NAME_ONLY"
    val showTitleText = !showLogoSlot || headerBrandMode != "LOGO_ONLY" || !hasLogo

    val adaptiveTitleSize = when {
        title.length <= 16 -> 20.sp
        title.length <= 26 -> 17.sp
        else -> 15.sp
    }
    val adaptiveLineHeight = when {
        title.length <= 16 -> 24.sp
        title.length <= 26 -> 21.sp
        else -> 19.sp
    }

    Surface(
        color = SurfaceWhite,
        contentColor = TextCharcoal,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 18.dp,
                        end = 14.dp,
                        top = 12.dp,
                        bottom = if (!subtitle.isNullOrBlank() || extraAction != null) 6.dp else 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (onLogoClick != null && showLogoSlot) {
                                Modifier.clickable(onClick = onLogoClick)
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showBadge) {
                        ShopLogoBadge(
                            logoUri = logoUri,
                            shopName = fullShopNameForMonogram.ifBlank { title },
                            wideWordmark = (headerBrandMode == "LOGO_ONLY" && hasLogo),
                            showUploadHintBadge = true,
                            sizeDp = 42.dp,
                            onClick = onLogoClick
                        )
                    }
                    if (showTitleText) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontFamily = JakartaHeading,
                                fontWeight = FontWeight.Bold,
                                fontSize = adaptiveTitleSize,
                                lineHeight = adaptiveLineHeight,
                                color = TextCharcoal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!categoryBadge.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = categoryBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (onNoticesClick != null) {
                    IconButton(
                        onClick = onNoticesClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("notices_bell_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNoticeCount > 0) {
                                    Badge(
                                        containerColor = SemanticError,
                                        contentColor = PureWhite
                                    ) {
                                        Text(
                                            text = if (unreadNoticeCount > 9) "9+" else unreadNoticeCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (unreadNoticeCount > 0) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                                contentDescription = "Business alerts",
                                tint = if (unreadNoticeCount > 0) MaterialTheme.colorScheme.primary else TextCharcoal,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                }
                if (onHelpClick != null) {
                    IconButton(
                        onClick = onHelpClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("help_tip_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                            contentDescription = "Help Tip",
                            tint = TextSecondaryGrey,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (onSettingsClick != null) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = TextCharcoal,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (!subtitle.isNullOrBlank() || extraAction != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondaryGrey,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    extraAction?.invoke()
                }
            }

            LocalWorkspaceSwitchBar.current?.let { switchBar ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 10.dp)
                ) {
                    switchBar()
                }
            }

            HorizontalDivider(thickness = 1.dp, color = DividerGrey)
        }
    }
}

@Composable
fun ShopBrandingEditorContent(
    shopName: String,
    onShopNameChange: (String) -> Unit,
    shortHeaderName: String,
    onShortHeaderNameChange: (String) -> Unit,
    headerBrandMode: String,
    onHeaderBrandModeChange: (String) -> Unit,
    shopLogoUri: String,
    onPickLogoUri: (Uri) -> Unit,
    onRemoveLogo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let(onPickLogoUri)
    }

    val hasLogo = hasValidLogoFile(shopLogoUri)
    val previewFullName = shopName.trim().ifBlank { "Laxmi Footwear" }
    val previewHeaderTitle = shortHeaderName.trim().ifBlank { previewFullName }
    val previewTitleSize = when {
        previewHeaderTitle.length <= 14 -> 20.sp
        previewHeaderTitle.length <= 24 -> 17.sp
        else -> 14.5.sp
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live Header Preview
        Surface(
            color = GlassSurfaceStrong,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GlassSpecularBottom),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "LIVE HEADER PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TealAccent
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (headerBrandMode != "NAME_ONLY") {
                        ShopLogoBadge(
                            logoUri = shopLogoUri,
                            shopName = previewFullName,
                            wideWordmark = (headerBrandMode == "LOGO_ONLY" && hasLogo),
                            showUploadHintBadge = false,
                            sizeDp = 42.dp
                        )
                    }
                    if (headerBrandMode != "LOGO_ONLY" || !hasLogo) {
                        Text(
                            text = previewHeaderTitle,
                            fontFamily = SoraDisplay,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = previewTitleSize,
                            lineHeight = (previewTitleSize.value + 4f).sp,
                            color = InkBlack,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Logo Upload / Remove Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShopLogoBadge(
                logoUri = shopLogoUri,
                shopName = previewFullName,
                wideWordmark = false,
                showUploadHintBadge = false,
                sizeDp = 56.dp,
                onClick = {
                    logoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            logoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("upload_logo_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealAccent,
                            contentColor = PureWhite
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (hasLogo) "Change Logo" else "Upload Logo",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (hasLogo) {
                        OutlinedButton(
                            onClick = onRemoveLogo,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("remove_logo_button"),
                            border = BorderStroke(1.dp, LossRed.copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Remove Logo",
                                tint = LossRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "Pick any PNG or JPG logo from gallery. Transparent logos stay crisp.",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkEspresso
                )
            }
        }

        // Full Shop Name
        OutlinedTextField(
            value = shopName,
            onValueChange = onShopNameChange,
            label = { Text("Shop Name (Bills, WhatsApp & Headers)") },
            placeholder = { Text("e.g. Laxmi Footwear & Sports") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("shop_name_input")
        )

        // Optional Short Header Name for very long shop names
        OutlinedTextField(
            value = shortHeaderName,
            onValueChange = onShortHeaderNameChange,
            label = { Text("Short Header Name (Optional)") },
            placeholder = { Text("e.g. Laxmi (if full name is very long)") },
            supportingText = {
                Text(
                    "Leave blank to show full Shop Name in the header; bills always keep your full name.",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkEspresso
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("short_header_name_input")
        )

        // Header Display Mode
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Header Style", style = MaterialTheme.typography.titleMedium, color = InkBlack)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "LOGO_AND_NAME" to "Logo + Name",
                    "LOGO_ONLY" to "Logo Only",
                    "NAME_ONLY" to "Name Only"
                ).forEach { (mode, label) ->
                    val enabled = mode != "LOGO_ONLY" || hasLogo
                    FilterChip(
                        selected = headerBrandMode == mode,
                        onClick = { if (enabled) onHeaderBrandModeChange(mode) },
                        enabled = enabled,
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("brand_mode_$mode")
                    )
                }
            }
            if (!hasLogo) {
                Text(
                    text = "Upload a logo to enable Logo Only mode.",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
        }
    }
}

@Composable
fun EmptyShopCard(
    title: String,
    description: String,
    primaryButtonLabel: String,
    onPrimaryClick: () -> Unit,
    onLoadDemoClick: (() -> Unit)? = null
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = GlassSurfaceStrong,
        elevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(TerracottaSoft, TealSoft.copy(alpha = 0.75f))
                        )
                    )
                    .border(1.5.dp, GlassSpecularTop, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = BurntTerracotta,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = InkBlack,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = InkEspresso,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onPrimaryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("empty_primary_action"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BurntTerracotta)
            ) {
                Text(
                    text = primaryButtonLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (onLoadDemoClick != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onLoadDemoClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("load_demo_button"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, BurntTerracotta)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = BurntTerracotta)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Load Demo Shop (15 Pairs & 60d Sales)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = BurntTerracotta
                    )
                }
            }
        }
    }
}

@Composable
fun HelpTipDialog(
    title: String,
    tips: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassSheetSurface,
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = TerracottaSoft,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = BurntTerracotta)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkBlack
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tips.forEach { tip ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("• ", color = BurntTerracotta, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(tip, style = MaterialTheme.typography.bodyMedium, color = InkEspresso)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BurntTerracotta)
            ) {
                Text("Got it", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun WalkthroughDialog(
    onFinish: () -> Unit,
    onLoadDemoAndFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        Triple(
            "Bill in Seconds",
            "Pick an item, select size or variant if applicable, and tap Complete Sale. You can record customer details or credit in one step.",
            Icons.Rounded.Bolt
        ),
        Triple(
            "Every Rupee Accounted For",
            "SoleBook tracks sales revenue, cost of goods, expenses, returns, and customer or supplier balances automatically.",
            Icons.Rounded.AccountBalanceWallet
        ),
        Triple(
            "Actionable Business Insights",
            "Spot low stock, fast-moving items, slow capital, and margin leaks right from Home and Insights without guesswork.",
            Icons.Rounded.TrendingUp
        )
    )

    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassSurfaceContainer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(28.dp),
            style = GlassStyle.Elevated,
            customContainerColor = GlassSheetSurface,
            elevationOverride = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = TerracottaSoft,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Step ${step + 1} of 3",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = BurntTerracottaDark,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Skip", color = InkEspresso, style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(TerracottaSoft, TealSoft.copy(alpha = 0.7f))
                            )
                        )
                        .border(1.5.dp, GlassSpecularTop, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = steps[step].third,
                        contentDescription = null,
                        tint = BurntTerracotta,
                        modifier = Modifier.size(46.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = steps[step].first,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkBlack,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = steps[step].second,
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkEspresso,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))

                // Dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0..2) {
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (i == step) 28.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (i == step) BurntTerracotta else WarmBorder)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                if (step < 2) {
                    Button(
                        onClick = { step++ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("walkthrough_next"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BurntTerracotta)
                    ) {
                        Text("Next", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onLoadDemoAndFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("walkthrough_load_demo"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BurntTerracotta)
                    ) {
                        Text("Load Demo Shop & Start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("walkthrough_start_empty"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Start with Blank Shop", color = InkBlack, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
