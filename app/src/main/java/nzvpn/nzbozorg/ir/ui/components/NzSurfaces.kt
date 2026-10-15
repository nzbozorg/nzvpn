package nzvpn.nzbozorg.ir.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nzvpn.nzbozorg.ir.ui.theme.NzOverlineStyle
import nzvpn.nzbozorg.ir.ui.theme.NzTheme

/**
 * کارت NZBOZORG — بخش ۵ سند تم:
 *  پس‌زمینه #111317، حاشیهٔ ۱dp (۱۵٪)، گوشهٔ ۱۰dp، سایهٔ صفر (عمق با تفاوت رنگ و حاشیه)،
 *  کارت انتخاب‌شده با حاشیهٔ مسی، کارت زنده/موفق با نشانگر نازک فیروزه‌ای.
 */
@Composable
fun NzCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    live: Boolean = false,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: Dp = NzTheme.dimens.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NzTheme.colors

    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> colors.borderCopper
            live -> colors.borderCyan
            else -> colors.border
        },
        animationSpec = tween(NzTheme.motion.micro),
        label = "cardBorder",
    )
    val background = if (elevated) colors.surfaceElevated else colors.surface
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(BorderStroke(1.dp, borderColor), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        if (live) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(2.dp)
                    .height(30.dp)
                    .background(colors.cyan),
            )
        }
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** سرصفحهٔ بخش: برچسب کوچک مونو + عنوان + خط نازک */
@Composable
fun NzSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = NzTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        if (overline != null) {
            Text(
                text = overline,
                style = NzTheme.mono.label,
                color = colors.copper,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            trailing?.invoke()
        }
        NzHairLine(modifier = Modifier.padding(top = 10.dp).height(1.dp))
    }
}

/** ردیف «برچسب — مقدار»؛ مقدار با فونت مونو (بخش ۳) */
@Composable
fun NzSpecRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = NzTheme.colors.textPrimary,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NzTheme.colors.textMuted)
        Text(text = value, style = NzTheme.mono.value, color = valueColor)
    }
}

/** برچسب کوچک (chip) برای پروتکل/امنیت */
@Composable
fun NzChip(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = NzTheme.colors.textMuted,
    mono: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = if (mono) NzTheme.mono.label else MaterialTheme.typography.labelSmall,
            color = accent,
        )
    }
}

/** حالت خالی: پیام راهنما با حاشیهٔ نازک (بدون تصویر تزئینی) */
@Composable
fun NzEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = NzTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceDeep)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(listOf(colors.border, colors.border.copy(alpha = 0.3f))),
                ),
                RoundedCornerShape(10.dp),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NzIcon(kind = NzIconKind.INFO, size = 18.dp, tint = colors.copper)
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        }
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        action?.invoke()
    }
}

/** «برچسب بالا» — متن کوچک مونو با فاصلهٔ حروف زیاد */
@Composable
fun NzOverline(text: String, modifier: Modifier = Modifier, color: Color = NzTheme.colors.copper) {
    Text(text = text, style = NzOverlineStyle, color = color, modifier = modifier)
}

/** نوار پیشرفت نازک صنعتی. progress = null یعنی حالت نامشخص. */
@Composable
fun NzThinProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = NzTheme.colors.cyan,
) {
    val animated by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(NzTheme.motion.standard),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(NzTheme.colors.border),
    ) {
        if (progress == null) {
            if (NzTheme.motion.reduced) {
                Box(
                    Modifier
                        .fillMaxWidth(0.35f)
                        .height(2.dp)
                        .background(color.copy(alpha = 0.6f)),
                )
            } else {
                val transition = rememberInfiniteTransition(label = "indeterminate")
                val offset by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
                    label = "barOffset",
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val w = maxWidth
                    Box(
                        Modifier
                            .padding(start = w * offset * 0.75f)
                            .width(w * 0.25f)
                            .height(2.dp)
                            .background(color),
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth(animated.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(color),
            )
        }
    }
}
