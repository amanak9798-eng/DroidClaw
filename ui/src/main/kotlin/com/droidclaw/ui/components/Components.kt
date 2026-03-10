package com.droidclaw.ui.components

import androidx.compose.animation.core.*
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidclaw.ui.theme.*

@Composable
fun PulsingDot(
    modifier: Modifier = Modifier,
    color: Color = AccentCyan,
    delayMillis: Int = 0
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_dot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delayMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delayMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun DroidClawProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = AccentCyan
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = color,
        trackColor = SurfaceDark,
    )
}

@Composable
fun DroidClawToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = BackgroundDark,
            checkedTrackColor = AccentCyan,
            uncheckedThumbColor = TextMuted,
            uncheckedTrackColor = SurfaceDark,
            uncheckedBorderColor = BorderDark
        )
    )
}

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 180, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "typing_dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentCyan.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun ScreenLoadingState(
    modifier: Modifier = Modifier,
    color: Color = AccentCyan
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = color)
    }
}

@Composable
fun ScreenMessageState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    titleColor: Color = TextPrimary,
    messageColor: Color = TextMuted,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                fontWeight = FontWeight.Bold
            )
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = messageColor
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel, color = AccentCyan)
                }
            }
        }
    }
}

@Composable
fun ScreenEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    glyph: String = "◧"
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.displayLarge,
                color = TextMuted.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun InlineErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        color = DangerRed.copy(alpha = 0.12f),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                color = DangerRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text("Retry", color = AccentCyan)
                }
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = TextMuted)
                }
            }
        }
    }
}

// ─── Markdown Text ──────────────────────────────────────────────────

/**
 * Renders a subset of Markdown inline in Compose without any external library.
 * Supported: **bold**, *italic*, `code`.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    val annotated = remember(text) { parseMarkdown(text) }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color
    )
}

/** Parses simple Markdown into an [AnnotatedString] for Compose rendering. */
fun parseMarkdown(input: String): AnnotatedString = buildAnnotatedString {
    val codeSpanRegex = Regex("`(.+?)`")
    val boldRegex     = Regex("\\*\\*(.+?)\\*\\*")
    val italicRegex   = Regex("\\*(.+?)\\*|_(.+?)_")

    var cursor = 0
    while (cursor < input.length) {
        val codeMatch   = codeSpanRegex.find(input, cursor)
        val boldMatch   = boldRegex.find(input, cursor)
        val italicMatch = italicRegex.find(input, cursor)

        val next = listOfNotNull(codeMatch, boldMatch, italicMatch)
            .minByOrNull { it.range.first }

        if (next == null) {
            append(input.substring(cursor))
            break
        }

        if (next.range.first > cursor) {
            append(input.substring(cursor, next.range.first))
        }

        when (next) {
            codeMatch -> withStyle(SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = Color(0xFF1E1E2E),
                color = Color(0xFF80CBC4)
            )) { append(next.groupValues[1]) }

            boldMatch -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(next.groupValues[1])
            }

            italicMatch -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(next.groupValues[1].ifEmpty { next.groupValues[2] })
            }
        }

        cursor = next.range.last + 1
    }
}
