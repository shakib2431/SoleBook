package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.domain.WorkspaceMode

private val SoleBookColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = PureWhite,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = EmeraldDark,
    secondary = TextCharcoal,
    onSecondary = PureWhite,
    secondaryContainer = SurfaceSubtle,
    onSecondaryContainer = TextCharcoal,
    tertiary = LeatherTan,
    onTertiary = PureWhite,
    background = CanvasWarmWhite,
    onBackground = TextCharcoal,
    surface = SurfaceWhite,
    onSurface = TextCharcoal,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextSecondaryGrey,
    surfaceContainerLow = SurfaceWhite,
    surfaceContainer = SurfaceWhite,
    surfaceContainerHigh = SurfaceSubtle,
    outline = DividerGrey,
    outlineVariant = DividerGrey,
    error = SemanticError,
    onError = PureWhite,
    errorContainer = SemanticErrorSoft,
    onErrorContainer = SemanticError
)

private val WholesaleColorScheme = lightColorScheme(
    primary = WholesaleBluePrimary,
    onPrimary = PureWhite,
    primaryContainer = WholesaleBlueSoft,
    onPrimaryContainer = WholesaleBlueDark,
    secondary = TextCharcoal,
    onSecondary = PureWhite,
    secondaryContainer = SurfaceSubtle,
    onSecondaryContainer = TextCharcoal,
    tertiary = LeatherTan,
    onTertiary = PureWhite,
    background = CanvasWarmWhite,
    onBackground = TextCharcoal,
    surface = SurfaceWhite,
    onSurface = TextCharcoal,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextSecondaryGrey,
    surfaceContainerLow = SurfaceWhite,
    surfaceContainer = SurfaceWhite,
    surfaceContainerHigh = SurfaceSubtle,
    outline = DividerGrey,
    outlineVariant = DividerGrey,
    error = SemanticError,
    onError = PureWhite,
    errorContainer = SemanticErrorSoft,
    onErrorContainer = SemanticError
)

val SoleBookShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun SoleBookTheme(
    workspaceMode: WorkspaceMode = WorkspaceMode.RETAIL,
    content: @Composable () -> Unit
) {
    val scheme = if (workspaceMode == WorkspaceMode.WHOLESALE) {
        WholesaleColorScheme
    } else {
        SoleBookColorScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = SoleBookTypography,
        shapes = SoleBookShapes,
        content = content
    )
}
