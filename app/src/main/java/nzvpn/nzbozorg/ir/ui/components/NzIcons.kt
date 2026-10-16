package nzvpn.nzbozorg.ir.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nzvpn.nzbozorg.ir.ui.theme.NzTheme

/**
 * مجموعهٔ آیکون‌های خطی NZBOZORG.
 *
 * چرا آیکون دست‌ساز؟ بخش ۷ سند تم می‌گوید: «آیکون‌ها ساده، خطی و هماهنگ باشند»
 * (ضخامت خط ۱.۵ تا ۲dp) و از Emoji به‌عنوان آیکون استفاده نشود.
 * این آیکون‌ها با Canvas کشیده می‌شوند: بدون وابستگی به کتابخانهٔ آیکون،
 * همیشه هم‌راستا با پالت رنگ تم و قابل رنگ‌آمیزی (tint).
 */
enum class NzIconKind {
    POWER, SHIELD, BOLT, REFRESH, PLUS, SETTINGS, CHECK, CLOSE, CHEVRON_RIGHT,
    HEART, LINK_OUT, WARNING, INFO, UPLOAD, DOWNLOAD, COPY, GLOBE, TRASH, DOT, CLOCK,
    STAR, DRAG_HANDLE, PAUSE, PLAY,
    /** سکه/حمایت مالی */
    COIN, COFFEE,
    /** اشتراک‌گذاری (فرستادن برنامه برای دیگران) */
    SHARE,
    /** پوشه (گروه ساب‌ها) */
    FOLDER,
    /** فایل */
    FILE,
    /** کلیپ‌بورد (چسباندن) */
    CLIPBOARD,
}

@Composable
fun NzIcon(
    kind: NzIconKind,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = NzTheme.colors.textMuted,
    strokeWidth: Dp = 1.6.dp,
    contentDescription: String? = null,
) {
    val semantics = if (contentDescription != null) {
        Modifier.nzContentDescription(contentDescription)
    } else {
        Modifier
    }
    Canvas(modifier = modifier.then(semantics).size(size)) {
        val s = this.size.minDimension
        val w = strokeWidth.toPx().coerceAtMost(s / 6f)
        val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val c = Offset(s / 2f, s / 2f)
        val r = s * 0.34f

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), w, StrokeCap.Round)

        when (kind) {
            NzIconKind.POWER -> {
                drawArc(
                    color = tint, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                    topLeft = Offset(s * 0.5f - r, s * 0.5f - r), size = Size(r * 2, r * 2), style = stroke,
                )
                line(0.5f, 0.10f, 0.5f, 0.48f)
            }

            NzIconKind.SHIELD -> {
                val p = Path().apply {
                    moveTo(s * 0.5f, s * 0.10f)
                    lineTo(s * 0.84f, s * 0.24f)
                    lineTo(s * 0.84f, s * 0.52f)
                    cubicTo(s * 0.84f, s * 0.74f, s * 0.68f, s * 0.86f, s * 0.5f, s * 0.92f)
                    cubicTo(s * 0.32f, s * 0.86f, s * 0.16f, s * 0.74f, s * 0.16f, s * 0.52f)
                    lineTo(s * 0.16f, s * 0.24f)
                    close()
                }
                drawPath(p, tint, style = stroke)
            }

            NzIconKind.BOLT -> {
                val p = Path().apply {
                    moveTo(s * 0.56f, s * 0.08f)
                    lineTo(s * 0.26f, s * 0.54f)
                    lineTo(s * 0.46f, s * 0.54f)
                    lineTo(s * 0.42f, s * 0.92f)
                    lineTo(s * 0.74f, s * 0.44f)
                    lineTo(s * 0.54f, s * 0.44f)
                    close()
                }
                drawPath(p, tint, style = stroke)
            }

            NzIconKind.REFRESH -> {
                drawArc(
                    color = tint, startAngle = -40f, sweepAngle = 290f, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = stroke,
                )
                // سر پیکان
                val tip = Offset(c.x + r * 0.86f, c.y - r * 0.6f)
                drawLine(tint, tip, Offset(tip.x - s * 0.16f, tip.y - s * 0.02f), w, StrokeCap.Round)
                drawLine(tint, tip, Offset(tip.x - s * 0.02f, tip.y + s * 0.14f), w, StrokeCap.Round)
            }

            NzIconKind.PLUS -> {
                line(0.5f, 0.2f, 0.5f, 0.8f)
                line(0.2f, 0.5f, 0.8f, 0.5f)
            }

            NzIconKind.SETTINGS -> {
                drawCircle(tint, r * 0.42f, c, style = stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45).toDouble())
                    val dx = kotlin.math.cos(a).toFloat()
                    val dy = kotlin.math.sin(a).toFloat()
                    line(
                        0.5f + dx * 0.26f, 0.5f + dy * 0.26f,
                        0.5f + dx * 0.40f, 0.5f + dy * 0.40f,
                    )
                }
            }

            NzIconKind.CHECK -> {
                line(0.2f, 0.54f, 0.42f, 0.74f)
                line(0.42f, 0.74f, 0.82f, 0.28f)
            }

            NzIconKind.CLOSE -> {
                line(0.24f, 0.24f, 0.76f, 0.76f)
                line(0.76f, 0.24f, 0.24f, 0.76f)
            }

            NzIconKind.CHEVRON_RIGHT -> {
                line(0.40f, 0.22f, 0.64f, 0.5f)
                line(0.64f, 0.5f, 0.40f, 0.78f)
            }

            NzIconKind.HEART -> {
                val p = Path().apply {
                    moveTo(s * 0.5f, s * 0.84f)
                    cubicTo(s * 0.08f, s * 0.56f, s * 0.16f, s * 0.20f, s * 0.5f, s * 0.36f)
                    cubicTo(s * 0.84f, s * 0.20f, s * 0.92f, s * 0.56f, s * 0.5f, s * 0.84f)
                    close()
                }
                drawPath(p, tint, style = stroke)
            }

            NzIconKind.SHARE -> {
                // آیکون استاندارد اشتراک‌گذاری: سه نقطهٔ متصل
                val r = s * 0.105f
                val top = Offset(s * 0.66f, s * 0.22f)
                val left = Offset(s * 0.30f, s * 0.50f)
                val bottom = Offset(s * 0.66f, s * 0.78f)
                drawLine(tint, top, left, w * 0.9f, StrokeCap.Round)
                drawLine(tint, left, bottom, w * 0.9f, StrokeCap.Round)
                drawCircle(tint, radius = r, center = top, style = stroke)
                drawCircle(tint, radius = r, center = left, style = stroke)
                drawCircle(tint, radius = r, center = bottom, style = stroke)
            }

            NzIconKind.COFFEE -> {
                // فنجان قهوه: بدنه + دسته + نعلبکی + بخار (خطی، هم‌سبک بقیه آیکون‌ها)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(s * 0.20f, s * 0.38f),
                    size = Size(s * 0.44f, s * 0.34f),
                    cornerRadius = CornerRadius(s * 0.07f, s * 0.07f),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = -75f,
                    sweepAngle = 190f,
                    useCenter = false,
                    topLeft = Offset(s * 0.56f, s * 0.44f),
                    size = Size(s * 0.28f, s * 0.20f),
                    style = stroke,
                )
                line(0.14f, 0.80f, 0.86f, 0.80f)
                line(0.36f, 0.30f, 0.32f, 0.15f)
                line(0.50f, 0.30f, 0.46f, 0.13f)
            }

            NzIconKind.COIN -> {
                // سکه با علامت ریال داخلش
                drawCircle(tint, radius = r * 1.05f, center = c, style = stroke)
                drawCircle(tint, radius = r * 0.72f, center = c, style = stroke)
                line(0.40f, 0.42f, 0.60f, 0.42f)
                line(0.44f, 0.50f, 0.56f, 0.50f)
                line(0.48f, 0.42f, 0.45f, 0.60f)
                line(0.55f, 0.42f, 0.52f, 0.60f)
            }

            NzIconKind.FOLDER -> {
                val p = Path().apply {
                    moveTo(s * 0.14f, s * 0.32f)
                    lineTo(s * 0.42f, s * 0.32f)
                    lineTo(s * 0.48f, s * 0.24f)
                    lineTo(s * 0.86f, s * 0.24f)
                    lineTo(s * 0.86f, s * 0.76f)
                    lineTo(s * 0.14f, s * 0.76f)
                    close()
                }
                drawPath(p, tint, style = stroke)
                line(0.14f, 0.40f, 0.86f, 0.40f)
            }

            NzIconKind.FILE -> {
                val p = Path().apply {
                    moveTo(s * 0.26f, s * 0.18f)
                    lineTo(s * 0.60f, s * 0.18f)
                    lineTo(s * 0.76f, s * 0.36f)
                    lineTo(s * 0.76f, s * 0.82f)
                    lineTo(s * 0.26f, s * 0.82f)
                    close()
                }
                drawPath(p, tint, style = stroke)
                line(0.58f, 0.18f, 0.58f, 0.36f)
                line(0.58f, 0.36f, 0.76f, 0.36f)
            }

            NzIconKind.CLIPBOARD -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(s * 0.22f, s * 0.24f),
                    size = androidx.compose.ui.geometry.Size(s * 0.56f, s * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.07f),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(s * 0.38f, s * 0.14f),
                    size = androidx.compose.ui.geometry.Size(s * 0.24f, s * 0.14f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.05f),
                    style = stroke,
                )
                line(0.34f, 0.46f, 0.66f, 0.46f)
                line(0.34f, 0.58f, 0.66f, 0.58f)
                line(0.34f, 0.70f, 0.54f, 0.70f)
            }

            NzIconKind.LINK_OUT -> {
                line(0.20f, 0.80f, 0.62f, 0.38f)
                line(0.44f, 0.28f, 0.74f, 0.28f)
                line(0.74f, 0.28f, 0.74f, 0.58f)
                line(0.40f, 0.80f, 0.20f, 0.80f)
                line(0.20f, 0.80f, 0.20f, 0.60f)
            }

            NzIconKind.WARNING -> {
                val p = Path().apply {
                    moveTo(s * 0.5f, s * 0.12f)
                    lineTo(s * 0.92f, s * 0.84f)
                    lineTo(s * 0.08f, s * 0.84f)
                    close()
                }
                drawPath(p, tint, style = stroke)
                line(0.5f, 0.38f, 0.5f, 0.60f)
                drawCircle(tint, w * 0.6f, Offset(s * 0.5f, s * 0.72f))
            }

            NzIconKind.INFO -> {
                drawCircle(tint, r, c, style = stroke)
                line(0.5f, 0.62f, 0.5f, 0.44f)
                drawCircle(tint, w * 0.6f, Offset(s * 0.5f, s * 0.32f))
            }

            NzIconKind.UPLOAD -> {
                line(0.5f, 0.74f, 0.5f, 0.22f)
                line(0.5f, 0.22f, 0.30f, 0.42f)
                line(0.5f, 0.22f, 0.70f, 0.42f)
                line(0.24f, 0.80f, 0.76f, 0.80f)
            }

            NzIconKind.DOWNLOAD -> {
                line(0.5f, 0.22f, 0.5f, 0.74f)
                line(0.5f, 0.74f, 0.30f, 0.54f)
                line(0.5f, 0.74f, 0.70f, 0.54f)
                line(0.24f, 0.84f, 0.76f, 0.84f)
            }

            NzIconKind.COPY -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(s * 0.18f, s * 0.30f),
                    size = Size(s * 0.50f, s * 0.52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(s * 0.36f, s * 0.16f),
                    size = Size(s * 0.46f, s * 0.48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w),
                    style = stroke,
                )
            }

            NzIconKind.GLOBE -> {
                drawCircle(tint, r, c, style = stroke)
                drawOval(
                    color = tint,
                    topLeft = Offset(c.x - r * 0.45f, c.y - r),
                    size = Size(r * 0.9f, r * 2),
                    style = stroke,
                )
                line(0.16f, 0.5f, 0.84f, 0.5f)
            }

            NzIconKind.TRASH -> {
                line(0.22f, 0.28f, 0.78f, 0.28f)
                line(0.40f, 0.28f, 0.44f, 0.16f)
                line(0.60f, 0.28f, 0.56f, 0.16f)
                line(0.44f, 0.16f, 0.56f, 0.16f)
                line(0.28f, 0.28f, 0.32f, 0.86f)
                line(0.72f, 0.28f, 0.68f, 0.86f)
                line(0.32f, 0.86f, 0.68f, 0.86f)
            }

            NzIconKind.STAR -> {
                val p = Path()
                for (i in 0 until 10) {
                    val angle = -Math.PI / 2 + Math.PI * i / 5
                    val radius = if (i % 2 == 0) s * 0.42f else s * 0.18f
                    val point = Offset(c.x + kotlin.math.cos(angle).toFloat() * radius,
                        c.y + kotlin.math.sin(angle).toFloat() * radius)
                    if (i == 0) p.moveTo(point.x, point.y) else p.lineTo(point.x, point.y)
                }
                p.close()
                drawPath(p, tint, style = stroke)
            }

            NzIconKind.DRAG_HANDLE -> {
                line(0.22f, 0.30f, 0.78f, 0.30f)
                line(0.22f, 0.50f, 0.78f, 0.50f)
                line(0.22f, 0.70f, 0.78f, 0.70f)
            }

            NzIconKind.PAUSE -> {
                drawLine(tint, Offset(s * 0.36f, s * 0.22f), Offset(s * 0.36f, s * 0.78f),
                    strokeWidth = w * 2f, cap = StrokeCap.Round)
                drawLine(tint, Offset(s * 0.64f, s * 0.22f), Offset(s * 0.64f, s * 0.78f),
                    strokeWidth = w * 2f, cap = StrokeCap.Round)
            }

            NzIconKind.PLAY -> {
                val p = Path().apply {
                    moveTo(s * 0.32f, s * 0.20f)
                    lineTo(s * 0.80f, s * 0.50f)
                    lineTo(s * 0.32f, s * 0.80f)
                    close()
                }
                drawPath(p, tint, style = stroke)
            }

            NzIconKind.DOT -> drawCircle(tint, w * 1.4f, c)

            NzIconKind.CLOCK -> {
                drawCircle(tint, r, c, style = stroke)
                line(0.5f, 0.5f, 0.5f, 0.30f)
                line(0.5f, 0.5f, 0.66f, 0.58f)
            }
        }
    }
}

private fun Modifier.nzContentDescription(text: String): Modifier =
    this.semantics { contentDescription = text }

/** نقطهٔ وضعیت (نبض محدود؛ فقط برای وضعیت مهم) — بخش ۹ */
@Composable
fun NzStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    pulsing: Boolean = false,
    pulseFraction: Float = 0f,
) {
    Canvas(modifier = modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        if (pulsing) {
            drawCircle(
                color = color.copy(alpha = 0.25f * (1f - pulseFraction)),
                radius = (this.size.minDimension / 2f) * (1f + pulseFraction * 0.9f),
                center = c,
            )
        }
        drawCircle(color = color, radius = this.size.minDimension / 2f, center = c)
    }
}

/** خط جداکنندهٔ نازک صنعتی */
@Composable
fun NzHairLine(modifier: Modifier = Modifier, color: Color = NzTheme.colors.border, dashed: Boolean = false) {
    Canvas(modifier = modifier) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null,
        )
    }
}
