package nzvpn.nzbozorg.ir.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import nzvpn.nzbozorg.ir.ui.theme.NzTheme
import kotlin.random.Random

/**
 * پس‌زمینهٔ سینمایی NZBOZORG — بخش ۸ سند تم.
 *
 *  - پایهٔ تقریباً مشکی (#090A0C)
 *  - شبکهٔ صنعتی بسیار کم‌رنگ (۳٪ تا ۵٪)
 *  - Film Grain ظریف (۳٪ تا ۶٪)
 *  - نور فیروزه‌ای محو از یک سمت، نور مسی محو از سمت دیگر (۸٪ تا ۱۶٪)
 *  - حرکت بسیار آرام بافت (۱۸ تا ۲۸ ثانیه) — و در حالت Reduced Motion، کاملاً ساکن
 *
 * نکتهٔ کارایی: Grain یک‌بار ساخته و کش می‌شود (bitmap کوچک، TileMode.Repeated)
 * و با GPU تکرار می‌شود؛ هیچ محاسبهٔ سنگینی در هر فریم انجام نمی‌شود.
 */
@Composable
fun NzBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = NzTheme.colors
    val motion = NzTheme.motion
    val density = LocalDensity.current
    val gridStep = with(density) { 34.dp.toPx() }

    // Grain: یک تصویر کوچک نویزی که GPU آن را تکرار می‌کند
    val grain = remember {
        makeNoiseBitmap(size = 96, seed = 20240113)
    }

    val drift = if (motion.reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "ambient")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = motion.ambient, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ambientDrift",
        )
        value
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // ۱) پایه
            drawRect(colors.background)

            // ۲) شبکهٔ صنعتی
            drawGrid(color = colors.gridLine, step = gridStep)

            // ۳) نورها — جای‌شان با حرکت آرام بسیار جزئی جابه‌جا می‌شود
            val shift = drift * h * 0.02f
            drawRadial(
                color = colors.ambientCyan,
                center = Offset(w * 0.86f, h * 0.06f + shift),
                radius = w * 0.9f,
            )
            drawRadial(
                color = colors.ambientCopper,
                center = Offset(w * 0.06f, h * 0.94f - shift),
                radius = w * 0.95f,
            )

            // ۴) گرین فیلم
            drawRect(
                brush = ShaderBrush(
                    ImageShader(grain, TileMode.Repeated, TileMode.Repeated),
                ),
                alpha = 0.05f,
            )
        }
        content()
    }
}

private fun DrawScope.drawGrid(color: Color, step: Float) {
    if (step <= 0f) return
    var x = 0f
    while (x <= size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawRadial(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** نویز خاکستری تصادفی برای Film Grain (یک‌بار ساخته و کش می‌شود) */
private fun makeNoiseBitmap(size: Int, seed: Int): ImageBitmap {
    val random = Random(seed)
    val bitmap = android.graphics.Bitmap.createBitmap(
        size, size, android.graphics.Bitmap.Config.ARGB_8888,
    )
    val pixels = IntArray(size * size)
    for (i in pixels.indices) {
        val v = random.nextInt(90, 220)
        val a = 20 + random.nextInt(0, 40)
        pixels[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}
