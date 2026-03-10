package com.droidclaw.ui.theme

import androidx.compose.ui.graphics.Color

// Semantic palette used by Material theme mapping and component tokens.
val BgBase = Color(0xFF080C10)
val BgSurface = Color(0xFF0D1318)
val BgSurfaceRaised = Color(0xFF151E26)
val BgInput = Color(0xFF1A2430)
val BrandAi = Color(0xFF00E5FF)
val BrandAgent = Color(0xFF7B61FF)
val StatusSuccess = Color(0xFF35D07F)
val StatusWarning = Color(0xFFFFA826)
val StatusDanger = Color(0xFFFF5D73)
val TextHigh = Color(0xFFF3F8FB)
val TextLow = Color(0xFF8A9AAA)
val BorderSubtle = Color(0xFF223243)

// Backward-compatible aliases for existing screens/components.
val BackgroundDark = BgBase
val SurfaceDark = BgSurface
val SurfaceElevated = BgSurfaceRaised
val InputSurface = BgInput
val AccentCyan = BrandAi
val WarningAmber = StatusWarning
val DangerRed = StatusDanger
val StatusGreen = StatusSuccess
val TextPrimary = TextHigh
val TextMuted = TextLow
val BorderDark = BorderSubtle

// Chat bubble colors
val UserBubble = BrandAi.copy(alpha = 0.14f)
val AssistantBubble = BgSurfaceRaised
val ToolBubbleBg = Color(0xFF162B35)

