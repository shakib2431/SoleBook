package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Provides bottom padding clearance when floating navigation or sticky bars are rendered.
 */
val LocalGlassBottomPadding = compositionLocalOf { 0.dp }

enum class GlassStyle {
    Standard,
    Elevated,
    TintedTerracotta,
    TintedTeal,
    TintedProfit,
    TintedLoss,
    TintedAmber,
    NavDock,
    DarkObsidian
}

/**
 * Clean, restrained surface container replacing excessive glass/blur gradients.
 * Keeps crisp white card panels with 1dp light neutral hairline borders and
 * minimal elevation for high legibility in bright retail environments.
 */
@Composable
fun GlassSurfaceContainer(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    style: GlassStyle = GlassStyle.Standard,
    customContainerColor: Color? = null,
    borderColor: Color? = null,
    elevationOverride: Dp? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = style == GlassStyle.DarkObsidian
    val containerColor = when {
        customContainerColor != null -> customContainerColor
        isDark -> GlassDarkSurface
        else -> SurfaceWhite
    }
    val resolvedContentColor = if (isDark) PureWhite else TextCharcoal
    val resolvedBorder = when {
        borderColor != null -> BorderStroke(1.dp, borderColor)
        isDark -> BorderStroke(1.dp, Color(0xFF28303D))
        else -> BorderStroke(1.dp, DividerGrey)
    }
    val elevation = elevationOverride ?: when (style) {
        GlassStyle.NavDock -> 2.dp
        GlassStyle.Elevated -> 1.dp
        GlassStyle.DarkObsidian -> 2.dp
        else -> 0.dp
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = resolvedContentColor,
            shadowElevation = elevation,
            border = resolvedBorder
        ) {
            CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
                Box(content = content)
            }
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = resolvedContentColor,
            shadowElevation = elevation,
            border = resolvedBorder
        ) {
            CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
                Box(content = content)
            }
        }
    }
}
