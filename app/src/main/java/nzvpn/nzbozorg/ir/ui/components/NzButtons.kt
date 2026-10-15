package nzvpn.nzbozorg.ir.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nzvpn.nzbozorg.ir.ui.theme.NzTheme
import nzvpn.nzbozorg.ir.ui.theme.NzShapes

/** انواع دکمه بر اساس بخش ۶ سند تم */
enum class NzButtonStyle { PRIMARY, SECONDARY, DANGER, GHOST }

/**
 * دکمهٔ NZBOZORG
 *  - حداقل ارتفاع ۴۸dp (اینجا ۵۲dp) — بخش ۶ و ۱۱
 *  - حالت Press با تغییر رنگ/حاشیه، بدون bounce — بخش ۹
 *  - متن کوتاه و پرکنتراست
 */
@Composable
fun NzButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NzButtonStyle = NzButtonStyle.PRIMARY,
    enabled: Boolean = true,
    leading: NzIconKind? = null,
    trailing: NzIconKind? = null,
    fillWidth: Boolean = true,
    height: Dp = NzTheme.dimens.buttonHeight,
) {
    val colors = NzTheme.colors
    val motion = NzTheme.motion

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val targetBg: Color = when (style) {
        NzButtonStyle.PRIMARY -> if (pressed) colors.copperHighlight else colors.copper
        NzButtonStyle.SECONDARY -> if (pressed) colors.surfaceSecondary else Color.Transparent
        NzButtonStyle.DANGER -> if (pressed) colors.red.copy(alpha = 0.16f) else Color.Transparent
        NzButtonStyle.GHOST -> if (pressed) colors.surfaceElevated else Color.Transparent
    }
    val bg by animateColorAsState(
        targetValue = if (enabled) targetBg else targetBg.copy(alpha = 0.35f),
        animationSpec = tween(if (motion.reduced) 0 else 180),
        label = "btnBg",
    )

    val contentColor: Color = when (style) {
        NzButtonStyle.PRIMARY -> colors.onCopper
        NzButtonStyle.SECONDARY, NzButtonStyle.GHOST -> colors.textPrimary
        NzButtonStyle.DANGER -> colors.redSoft
    }
    val borderColor = when (style) {
        NzButtonStyle.PRIMARY -> Color.Transparent
        NzButtonStyle.SECONDARY -> if (pressed) colors.borderStrong else colors.border
        NzButtonStyle.DANGER -> if (pressed) colors.red else colors.red.copy(alpha = 0.55f)
        NzButtonStyle.GHOST -> Color.Transparent
    }

    val shape = RoundedCornerShape(8.dp)
    val base = modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = NzTheme.dimens.minTouchTarget)
        .defaultMinSize(minHeight = height)
        .clip(shape)
        .background(bg, shape)
        .border(BorderStroke(1.dp, borderColor), shape)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
        .padding(horizontal = 18.dp)

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(modifier = base, contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    NzIcon(kind = leading, size = 18.dp, tint = contentColor)
                }
                Text(
                    text = text,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (trailing != null) {
                    NzIcon(kind = trailing, size = 18.dp, tint = contentColor)
                }
            }
        }
    }
}

/** دکمهٔ آیکونی مربعی (۴۸dp لمسی) */
@Composable
fun NzIconButton(
    kind: NzIconKind,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NzTheme.colors.textMuted,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = NzTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color = if (active) colors.copperBright else tint
    val bg = when {
        !enabled -> Color.Transparent
        pressed -> colors.surfaceSecondary
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        NzIcon(kind = kind, size = 22.dp, tint = color.copy(alpha = if (enabled) 1f else 0.4f), contentDescription = contentDescription)
    }
}

/** ردیف قابل‌لمس (مثل آیتم تنظیمات) */
@Composable
fun NzClickableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (pressed) NzTheme.colors.surfaceSecondary else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

/** شکل مشترک کارت‌ها (برای استفاده در سطوح دیگر) */
val NzCardShape = RoundedCornerShape(10.dp)

internal val nzCardShapeRef = NzShapes.large
