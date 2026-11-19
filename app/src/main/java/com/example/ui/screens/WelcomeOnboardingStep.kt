package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

@Composable
fun WelcomeOnboardingStep(
    onGetStarted: () -> Unit,
    onRestoreBackupClick: (() -> Unit)? = null,
    activePageIndex: Int = 0,
    totalPages: Int = 3
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .testTag("welcome_onboarding_root")
    ) {
        // Subtle layered emerald waves at top-left and bottom-right (edge-to-edge behind insets)
        WelcomeAmbientWaves(modifier = Modifier.fillMaxSize())

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val isVeryCompactHeight = maxHeight < 560.dp
            val isCompactHeight = maxHeight < 690.dp
            val maxHeroHeight = when {
                isVeryCompactHeight -> (maxHeight * 0.36f).coerceIn(154.dp, 196.dp)
                isCompactHeight -> (maxHeight * 0.38f).coerceIn(196.dp, 248.dp)
                else -> 328.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 520.dp)
                    .padding(horizontal = if (maxWidth < 360.dp) 14.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Scrollable upper & center content so narrow, landscape, or large-font screens never clip
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            top = if (isVeryCompactHeight) 6.dp else if (isCompactHeight) 10.dp else 18.dp,
                            bottom = if (isVeryCompactHeight) 4.dp else 10.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    WelcomeBrandIdentity(compact = isVeryCompactHeight)

                    Spacer(Modifier.height(if (isVeryCompactHeight) 4.dp else if (isCompactHeight) 8.dp else 14.dp))

                    WelcomeStorefrontHeroStage(
                        maxStageHeight = maxHeroHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp)
                    )

                    Spacer(Modifier.height(if (isVeryCompactHeight) 4.dp else if (isCompactHeight) 8.dp else 14.dp))

                    WelcomeHeadlineBlock(compact = isVeryCompactHeight)
                }

                // Bottom action zone: pagination dots + emerald CTA + optional restore action
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = if (isVeryCompactHeight) 2.dp else 6.dp,
                            bottom = if (isVeryCompactHeight) 6.dp else 14.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isVeryCompactHeight) 6.dp else 10.dp)
                ) {
                    if (totalPages > 1) {
                        WelcomePaginationDots(
                            activeIndex = activePageIndex,
                            count = totalPages
                        )
                    }

                    WelcomePrimaryCtaButton(
                        text = "Set Up My Business",
                        onClick = onGetStarted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                    )

                    if (onRestoreBackupClick != null) {
                        TextButton(
                            onClick = onRestoreBackupClick,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("onboarding_restore_backup_btn")
                        ) {
                            Text(
                                text = "Restore Existing Backup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = WelcomeHeadlineEmerald
                            )
                        }
                    } else {
                        Text(
                            text = "Takes less than a minute. Works completely offline.",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMutedGrey,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeAmbientWaves(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Top-left soft mist layer
        val topSoftPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.56f, 0f)
            cubicTo(
                w * 0.42f, h * 0.055f,
                w * 0.22f, h * 0.11f,
                0f, h * 0.175f
            )
            close()
        }
        drawPath(
            path = topSoftPath,
            brush = Brush.linearGradient(
                colors = listOf(WelcomeWaveSoft.copy(alpha = 0.85f), PureWhite.copy(alpha = 0f)),
                start = Offset(0f, 0f),
                end = Offset(w * 0.55f, h * 0.18f)
            )
        )

        // Top-left mid emerald ribbon
        val topMidPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.44f, 0f)
            cubicTo(
                w * 0.30f, h * 0.032f,
                w * 0.16f, h * 0.085f,
                0f, h * 0.125f
            )
            close()
        }
        drawPath(
            path = topMidPath,
            brush = Brush.linearGradient(
                colors = listOf(WelcomeWaveMid.copy(alpha = 0.88f), WelcomeWaveDeep.copy(alpha = 0.72f)),
                start = Offset(0f, 0f),
                end = Offset(w * 0.42f, h * 0.12f)
            )
        )

        // Top-left deep forest crest
        val topDeepPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.32f, 0f)
            cubicTo(
                w * 0.20f, h * 0.025f,
                w * 0.11f, h * 0.055f,
                0f, h * 0.088f
            )
            close()
        }
        drawPath(
            path = topDeepPath,
            brush = Brush.linearGradient(
                colors = listOf(WelcomeWaveDeep, WelcomeWaveMid),
                start = Offset(0f, 0f),
                end = Offset(w * 0.32f, h * 0.08f)
            )
        )

        // Bottom-right soft mist layer
        val bottomSoftPath = Path().apply {
            moveTo(w, h)
            lineTo(w * 0.42f, h)
            cubicTo(
                w * 0.58f, h * 0.945f,
                w * 0.82f, h * 0.895f,
                w, h * 0.79f
            )
            close()
        }
        drawPath(
            path = bottomSoftPath,
            brush = Brush.linearGradient(
                colors = listOf(WelcomeWaveSoft.copy(alpha = 0.85f), PureWhite.copy(alpha = 0f)),
                start = Offset(w, h),
                end = Offset(w * 0.42f, h * 0.80f)
            )
        )

        // Bottom-right mid emerald ribbon
        val bottomMidPath = Path().apply {
            moveTo(w, h)
            lineTo(w * 0.50f, h)
            cubicTo(
                w * 0.66f, h * 0.962f,
                w * 0.85f, h * 0.965f,
                w, h * 0.90f
            )
            close()
        }
        drawPath(
            path = bottomMidPath,
            brush = Brush.linearGradient(
                colors = listOf(WelcomeWaveMid.copy(alpha = 0.78f), WelcomeWaveDeep.copy(alpha = 0.85f)),
                start = Offset(w * 0.50f, h),
                end = Offset(w, h * 0.90f)
            )
        )

        // Bottom-right deep crest
        val bottomDeepPath = Path().apply {
            moveTo(w, h)
            lineTo(w * 0.66f, h)
            cubicTo(
                w * 0.80f, h * 0.975f,
                w * 0.91f, h * 0.965f,
                w, h * 0.938f
            )
            close()
        }
        drawPath(
            path = bottomDeepPath,
            brush = Brush.linearGradient(
                colors = listOf(WelcomeWaveDeep, EmeraldDark),
                start = Offset(w * 0.66f, h),
                end = Offset(w, h * 0.94f)
            )
        )
    }
}

@Composable
private fun WelcomeBrandIdentity(compact: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        modifier = Modifier.testTag("welcome_brand_header")
    ) {
        // Custom SoleBook emerald shopping bag + ascending bar chart vector mark
        Canvas(modifier = Modifier.size(if (compact) 34.dp else 46.dp)) {
            val w = size.width
            val h = size.height
            val strokePx = w * 0.082f
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)

            // Curved handle
            val handlePath = Path().apply {
                moveTo(w * 0.34f, h * 0.31f)
                cubicTo(
                    w * 0.34f, h * 0.08f,
                    w * 0.66f, h * 0.08f,
                    w * 0.66f, h * 0.31f
                )
            }
            drawPath(path = handlePath, color = WelcomeHeadlineEmerald, style = stroke)

            // Tapered rounded shopping bag outline
            val bagPath = Path().apply {
                moveTo(w * 0.22f, h * 0.31f)
                lineTo(w * 0.78f, h * 0.31f)
                quadraticTo(w * 0.84f, h * 0.31f, w * 0.85f, h * 0.38f)
                lineTo(w * 0.90f, h * 0.82f)
                quadraticTo(w * 0.91f, h * 0.92f, w * 0.81f, h * 0.92f)
                lineTo(w * 0.19f, h * 0.92f)
                quadraticTo(w * 0.09f, h * 0.92f, w * 0.10f, h * 0.82f)
                lineTo(w * 0.15f, h * 0.38f)
                quadraticTo(w * 0.16f, h * 0.31f, w * 0.22f, h * 0.31f)
                close()
            }
            drawPath(path = bagPath, color = WelcomeHeadlineEmerald, style = stroke)

            // Three ascending bars inside bag
            val barW = w * 0.105f
            val barBottom = h * 0.78f
            val corner = CornerRadius(w * 0.03f, w * 0.03f)
            drawRoundRect(
                color = WelcomeHeadlineEmerald,
                topLeft = Offset(w * 0.31f, barBottom - h * 0.15f),
                size = Size(barW, h * 0.15f),
                cornerRadius = corner
            )
            drawRoundRect(
                color = WelcomeHeadlineEmerald,
                topLeft = Offset(w * 0.45f, barBottom - h * 0.24f),
                size = Size(barW, h * 0.24f),
                cornerRadius = corner
            )
            drawRoundRect(
                color = WelcomeHeadlineEmerald,
                topLeft = Offset(w * 0.59f, barBottom - h * 0.34f),
                size = Size(barW, h * 0.34f),
                cornerRadius = corner
            )
        }

        Text(
            text = "SoleBook",
            fontFamily = JakartaHeading,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 22.sp else 29.sp,
            lineHeight = if (compact) 26.sp else 34.sp,
            letterSpacing = (-0.6).sp,
            color = WelcomeHeadlineDark
        )
    }
}

@Composable
private fun WelcomeStorefrontHeroStage(
    maxStageHeight: Dp = 328.dp,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val stageW = maxWidth
        val stageH = (stageW * 0.82f).coerceIn(154.dp, maxStageHeight)
        val scaleFactor = (stageH.value / 280f).coerceIn(0.66f, 1.10f)
        val cardW = (stageW * 0.24f).coerceIn(66.dp, 88.dp)
        val cardH = (70.dp * scaleFactor).coerceIn(48.dp, 80.dp)
        val storefrontW = (stageW * 0.56f).coerceIn(132.dp, 232.dp).coerceAtMost(stageH * 0.86f)
        val storefrontH = storefrontW * 0.695f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stageH)
        ) {
            // Layer 1: Soft mint halo + gentle ground silhouettes + semicircular arc connecting the 5 cards
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Soft pale-mint circle behind upper storefront (shows seamlessly through transparent PNG!)
                drawCircle(
                    color = WelcomeWaveSoft.copy(alpha = 0.62f),
                    radius = w * 0.285f,
                    center = Offset(w * 0.50f, h * 0.60f)
                )

                // Soft mint organic ground bush silhouette behind left base of storefront
                val leftHill = Path().apply {
                    moveTo(w * 0.08f, h * 0.96f)
                    cubicTo(
                        w * 0.14f, h * 0.91f,
                        w * 0.18f, h * 0.78f,
                        w * 0.26f, h * 0.80f
                    )
                    cubicTo(
                        w * 0.30f, h * 0.81f,
                        w * 0.32f, h * 0.90f,
                        w * 0.34f, h * 0.96f
                    )
                    close()
                }
                drawPath(
                    path = leftHill,
                    brush = Brush.verticalGradient(
                        colors = listOf(WelcomeArcMint.copy(alpha = 0.55f), WelcomeWaveSoft.copy(alpha = 0.20f)),
                        startY = h * 0.78f,
                        endY = h * 0.96f
                    )
                )

                // Subtle soft-mint arc connecting all 5 floating feature cards
                val arcLeft = w * 0.11f
                val arcRight = w * 0.89f
                val arcTop = h * 0.11f
                val arcBottom = h * 1.15f
                drawArc(
                    color = WelcomeArcMint,
                    startAngle = 192f,
                    sweepAngle = 156f,
                    useCenter = false,
                    topLeft = Offset(arcLeft, arcTop),
                    size = Size(arcRight - arcLeft, arcBottom - arcTop),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Layer 2: Central bundled transparent 3D small-business storefront illustration (100% offline)
            Image(
                painter = painterResource(id = R.drawable.img_welcome_storefront),
                contentDescription = "Small business storefront with inventory shelves and point of sale counter",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(storefrontW)
                    .height(storefrontH)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .testTag("welcome_storefront_illustration")
            )

            // Layer 3: Five floating feature cards around the arc
            // 1. Sell & Bill (top-center)
            WelcomeFeatureCard(
                icon = Icons.Rounded.ShoppingCart,
                label = "Sell & Bill",
                cardWidth = cardW,
                cardHeight = cardH,
                modifier = Modifier
                    .offset(x = (stageW - cardW) / 2f, y = 2.dp)
                    .testTag("welcome_feature_sell_bill")
            )

            // 2. Inventory (upper-left)
            WelcomeFeatureCard(
                icon = Icons.Rounded.Inventory2,
                label = "Inventory",
                cardWidth = cardW,
                cardHeight = cardH,
                modifier = Modifier
                    .offset(x = stageW * 0.065f, y = stageH * 0.16f)
                    .testTag("welcome_feature_inventory")
            )

            // 3. Purchases (upper-right)
            WelcomeFeatureCard(
                icon = Icons.Rounded.Archive,
                label = "Purchases",
                cardWidth = cardW,
                cardHeight = cardH,
                modifier = Modifier
                    .offset(x = stageW - cardW - stageW * 0.065f, y = stageH * 0.16f)
                    .testTag("welcome_feature_purchases")
            )

            // 4. Customers (mid-left)
            WelcomeFeatureCard(
                icon = Icons.Rounded.Groups,
                label = "Customers",
                cardWidth = cardW,
                cardHeight = cardH,
                modifier = Modifier
                    .offset(x = 0.dp, y = stageH * 0.50f)
                    .testTag("welcome_feature_customers")
            )

            // 5. Reports (mid-right)
            WelcomeFeatureCard(
                icon = Icons.Rounded.BarChart,
                label = "Reports",
                cardWidth = cardW,
                cardHeight = cardH,
                modifier = Modifier
                    .offset(x = stageW - cardW, y = stageH * 0.50f)
                    .testTag("welcome_feature_reports")
            )
        }
    }
}

@Composable
private fun WelcomeFeatureCard(
    icon: ImageVector,
    label: String,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(width = cardWidth, height = cardHeight),
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        shadowElevation = 6.dp,
        border = BorderStroke(0.75.dp, DividerGrey.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = WelcomeHeadlineEmerald,
                modifier = Modifier.size(if (cardHeight < 58.dp) 18.dp else 24.dp)
            )
            Spacer(Modifier.height(if (cardHeight < 58.dp) 2.dp else 4.dp))
            Text(
                text = label,
                fontFamily = DmSansBody,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (cardHeight < 58.dp) 9.5.sp else 11.sp,
                lineHeight = if (cardHeight < 58.dp) 11.sp else 13.sp,
                color = TextCharcoal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WelcomeHeadlineBlock(compact: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = "Your Business,",
            fontFamily = JakartaHeading,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 21.sp else 27.sp,
            lineHeight = if (compact) 25.sp else 33.sp,
            letterSpacing = (-0.5).sp,
            color = WelcomeHeadlineDark,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Simpler than Ever.",
            fontFamily = JakartaHeading,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 21.sp else 27.sp,
            lineHeight = if (compact) 25.sp else 33.sp,
            letterSpacing = (-0.5).sp,
            color = WelcomeHeadlineEmerald,
            textAlign = TextAlign.Center
        )

        // Subtle curved emerald swash underline matching the visual reference
        Canvas(
            modifier = Modifier
                .padding(top = 2.dp, bottom = if (compact) 4.dp else 10.dp)
                .width(if (compact) 108.dp else 134.dp)
                .height(if (compact) 8.dp else 10.dp)
        ) {
            val w = size.width
            val h = size.height
            val upperArc = Path().apply {
                moveTo(w * 0.04f, h * 0.65f)
                quadraticTo(w * 0.48f, h * 0.05f, w * 0.96f, h * 0.45f)
            }
            val lowerArc = Path().apply {
                moveTo(w * 0.22f, h * 0.88f)
                quadraticTo(w * 0.56f, h * 0.48f, w * 0.86f, h * 0.85f)
            }
            drawPath(
                path = upperArc,
                color = WelcomeHeadlineEmerald,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = lowerArc,
                color = WelcomeWaveMid.copy(alpha = 0.85f),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Text(
            text = "Manage inventory, sales, purchases, customers and money — all in one place.",
            fontFamily = DmSansBody,
            fontWeight = FontWeight.Normal,
            fontSize = if (compact) 12.5.sp else 15.sp,
            lineHeight = if (compact) 17.sp else 22.sp,
            color = TextSecondaryGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 336.dp)
        )
    }
}

@Composable
private fun WelcomePaginationDots(
    activeIndex: Int,
    count: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("welcome_pagination_dots")
    ) {
        repeat(count) { idx ->
            val isActive = idx == activeIndex
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (isActive) WelcomeHeadlineEmerald else WelcomeDotInactive)
            )
        }
    }
}

@Composable
private fun WelcomePrimaryCtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("onboarding_start_btn"),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 6.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(WelcomeCtaStart, EmeraldPrimary, WelcomeCtaEnd)
                    )
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = text,
                    fontFamily = JakartaHeading,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.5.sp,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
