package nzvpn.nzbozorg.ir.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nzvpn.nzbozorg.ir.service.VpnState
import nzvpn.nzbozorg.ir.ui.theme.NzTheme
import nzvpn.nzbozorg.ir.util.PersianText

/**
 * رنگ و متن هر وضعیت.
 * بخش ۱۱ سند تم: «وضعیت‌ها فقط با رنگ نمایش داده نشوند؛ متن و آیکون هم داشته باشند.»
 * پس هر وضعیت = (رنگ + آیکون + متن).
 */
data class NzStatusVisual(
    val label: String,
    val color: Color,
    val icon: NzIconKind,
)

@Composable
fun vpnStatusVisual(state: VpnState): NzStatusVisual = when (state) {
    VpnState.DISCONNECTED -> NzStatusVisual("قطع", NzTheme.colors.textMuted, NzIconKind.POWER)
    VpnState.CONNECTING -> NzStatusVisual("در حال اتصال", NzTheme.colors.copperBright, NzIconKind.CLOCK)
    VpnState.CONNECTED -> NzStatusVisual("متصل", NzTheme.colors.cyan, NzIconKind.SHIELD)
    VpnState.STOPPING -> NzStatusVisual("در حال قطع", NzTheme.colors.copper, NzIconKind.POWER)
}

/** نشان وضعیت: نقطه (با نبض محدود) + متن + آیکون */
@Composable
fun NzStatusPill(
    visual: NzStatusVisual,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val motion = NzTheme.motion
    val pulse: Float = if (pulsing && !motion.reduced) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "pulseValue",
        )
        value
    } else {
        0f
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(visual.color.copy(alpha = 0.10f))
            .border(1.dp, visual.color.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NzStatusDot(color = visual.color, size = 8.dp, pulsing = pulsing, pulseFraction = pulse)
        NzIcon(kind = visual.icon, size = 15.dp, tint = visual.color)
        Text(
            text = visual.label,
            style = NzTheme.mono.label,
            color = visual.color,
        )
    }
}

/** پینگِ پاسخ‌گو همیشه فیروزه‌ای؛ بدون پاسخ همیشه قرمز، بدون مرزبندی عددی. */
@Composable
fun NzLatencyBadge(latencyMs: Long, modifier: Modifier = Modifier) {
    val colors = NzTheme.colors
    val color = if (latencyMs > 0) colors.cyan else colors.redSoft
    val label = if (latencyMs > 0) PersianText.digits(latencyMs) else "—"
    Box(
        modifier = modifier
            .semantics {
                contentDescription = if (latencyMs > 0) "پینگ $label" else "بدون پینگ"
            }
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = label, style = NzTheme.mono.value, color = color)
        }
    }
}

/** آمار ترافیک در سطری جدا از نشان وضعیت؛ هر مقدار عرض مستقل دارد و نمی‌شکند. */
@Composable
fun NzTrafficRow(
    up: Long,
    down: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrafficValue("آپلود", NzIconKind.UPLOAD, up, NzTheme.colors.copperBright, Modifier.weight(1f))
        TrafficValue("دانلود", NzIconKind.DOWNLOAD, down, NzTheme.colors.cyan, Modifier.weight(1f))
    }
}

@Composable
private fun TrafficValue(label: String, icon: NzIconKind, bytes: Long, accent: Color, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        NzIcon(kind = icon, size = 16.dp, tint = accent)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = NzTheme.colors.textMuted,
                maxLines = 1)
            Text(formatBytes(bytes), style = NzTheme.mono.value, color = NzTheme.colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** تبدیل بایت به متن خوانا با ارقام فارسی («۱٫۲ مگابایت») */
fun formatBytes(bytes: Long): String = PersianText.bytes(bytes)

/** متن برچسب‌دار برای اعداد مهم (مثلاً: «۳ سرور») */
@Composable
fun NzCountLabel(count: Int, singular: String, plural: String, modifier: Modifier = Modifier) {
    Text(
        text = if (count == 1) "۱ $singular" else "${toPersianDigits(count)} $plural",
        style = MaterialTheme.typography.bodySmall,
        color = NzTheme.colors.textMuted,
        modifier = modifier,
    )
}

/** متن شمارشی با ارقام فارسی: «۳ سرور» */
fun nzCount(count: Int, singular: String, plural: String): String =
    if (count == 1) "۱ $singular" else "${toPersianDigits(count)} $plural"

/** تبدیل ارقام لاتین به فارسی برای متن‌های رابط */
fun toPersianDigits(value: Number): String = PersianText.digits(value)
