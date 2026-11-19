package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Core Brand & Semantic Tokens (Deep Forest Green + Warm Off-White + Deep Charcoal)
val EmeraldPrimary = Color(0xFF0F6B4E)        // Sophisticated deep forest emerald (Retail)
val EmeraldDark = Color(0xFF094734)           // Deep emerald ink / pressed state
val EmeraldLight = Color(0xFF15805D)          // Subtle emerald highlight
val EmeraldSoft = Color(0xFFE7F3EE)           // Muted sage-emerald container pill

// Wholesale B2B Brand Tokens (Authoritative Royal Cobalt Blue)
val WholesaleBluePrimary = Color(0xFF1D4ED8)  // Crisp royal cobalt blue (Wholesale)
val WholesaleBlueDark = Color(0xFF1E3A8A)     // Deep navy-cobalt ink / pressed state
val WholesaleBlueLight = Color(0xFF2563EB)    // Subtle cobalt blue highlight
val WholesaleBlueSoft = Color(0xFFEFF6FF)     // Clean ice-blue container pill

val CanvasWarmWhite = Color(0xFFFAF9F6)       // Warm off-white primary canvas
val SurfaceWhite = Color(0xFFFFFFFF)          // Crisp clean white card surface
val SurfaceSubtle = Color(0xFFF3F1EC)         // Restrained warm stone surface variant
val DividerGrey = Color(0xFFE8EAED)           // Very light neutral divider & hairline border

val TextCharcoal = Color(0xFF14181F)          // Deep charcoal main text
val TextSecondaryGrey = Color(0xFF5C6470)     // Muted slate grey secondary text
val TextMutedGrey = Color(0xFF7B8390)         // Caption / helper grey

val SemanticSuccess = Color(0xFF15803D)       // Restrained financial green
val SemanticSuccessSoft = Color(0xFFECFDF3)   // Clean muted green pill fill
val SemanticWarning = Color(0xFFB45309)       // Warm amber warning
val SemanticWarningSoft = Color(0xFFFEF7EC)   // Soft amber pill fill
val SemanticError = Color(0xFFC62828)         // Muted crimson error
val SemanticErrorSoft = Color(0xFFFEF2F2)     // Soft red pill fill

// Fixed Constants
val PureWhite = Color(0xFFFFFFFF)
val CameraScrimBlack = Color(0xFF0A0D10)
val OnMintInk = PureWhite

// Clean Aliases Mapped to Unified Design System (Preserving backwards compatibility cleanly)
val WarmOffWhite = CanvasWarmWhite
val WarmPaper = SurfaceSubtle
val WarmCard = SurfaceWhite
val WarmCardSolid = SurfaceWhite
val WarmCardMuted = SurfaceSubtle
val WarmBorder = DividerGrey

val GlassSurface = SurfaceWhite
val GlassSurfaceStrong = SurfaceWhite
val GlassSurfaceElevated = SurfaceWhite
val GlassSheetSurface = SurfaceWhite
val GlassSpecularTop = DividerGrey
val GlassSpecularMid = DividerGrey
val GlassSpecularBottom = DividerGrey

val GlassTerracottaTint = SurfaceWhite
val GlassTealTint = SurfaceWhite
val GlassProfitTint = SurfaceWhite
val GlassLossTint = SurfaceWhite
val GlassAmberTint = SurfaceWhite

val GlassDarkSurface = Color(0xFF14181F)
val GlassDarkSurfaceEnd = Color(0xFF1E242E)
val GlassDarkAccentTint = Color(0x140F6B4E)
val GlassDarkTealTint = Color(0x140F6B4E)
val GlassDarkBorder = Color(0x33FFFFFF)

val GlassOrbWarm = Color.Transparent
val GlassOrbTeal = Color.Transparent
val GlassOrbGold = Color.Transparent
val GlassOrbCobalt = Color.Transparent

val InkBlack = TextCharcoal
val InkEspresso = TextSecondaryGrey
val InkMuted = TextMutedGrey

val BurntTerracotta = EmeraldPrimary
val CoralHighlight = EmeraldLight
val BurntTerracottaDark = EmeraldDark
val TerracottaSoft = EmeraldSoft
val LeatherTan = Color(0xFF374151)            // Restrained cool charcoal-slate secondary
val LeatherTanSoft = Color(0xFFF3F4F6)
val TealAccent = Color(0xFF0F6B4E)
val TealSoft = EmeraldSoft

val ProfitGreen = SemanticSuccess
val ProfitGreenSoft = SemanticSuccessSoft
val ProfitGreenMint = Color(0xFF6EE7B7)
val LossRed = SemanticError
val LossRedSoft = SemanticErrorSoft
val LossCoralSoft = Color(0xFFFCA5A5)
val AmberWarn = SemanticWarning
val AmberWarnSoft = SemanticWarningSoft
