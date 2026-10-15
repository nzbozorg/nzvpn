package nzvpn.nzbozorg.ir.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * سیستم فاصله‌گذاری روی پایهٔ 4dp — بخش ۴ سند تم.
 */
@Immutable
data class NzSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 40.dp,
    val huge: Dp = 48.dp,
    val massive: Dp = 64.dp,
)

/** اندازه‌های ساختاری رابط */
@Immutable
data class NzDimens(
    val spacing: NzSpacing = NzSpacing(),
    /** حاشیهٔ صفحه در موبایل: ۱۶ تا ۲۰dp (بخش ۴ و ۱۰) */
    val screenPadding: Dp = 20.dp,
    /** فاصلهٔ داخلی کارت‌ها: ۱۶ تا ۲۰dp (بخش ۵) */
    val cardPadding: Dp = 18.dp,
    /** حداقل ارتفاع هر عنصر لمسی — بخش ۶ و ۱۱ */
    val minTouchTarget: Dp = 48.dp,
    val buttonHeight: Dp = 52.dp,
    /** بیشینهٔ عرض محتوا تا در تبلت/دسکتاپ کشیده نشود (بخش ۱۰) */
    val maxContentWidth: Dp = 720.dp,
    val maxWideContentWidth: Dp = 1080.dp,
)

/** گوشه‌ها: ۸ تا ۱۲dp برای کارت‌ها، ۶ تا ۱۰dp برای دکمه‌ها (بخش ۵ و ۶) */
val NzShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/**
 * زمان‌بندی حرکت — بخش ۹ سند تم.
 * Easing پیشنهادی: Fast Out, Slow In → CubicBezier(0.4, 0, 0.2, 1)
 */
@Immutable
data class NzMotion(
    val microMs: Int = 220,      // ۱۸۰ تا ۲۶۰
    val standardMs: Int = 300,   // ۲۲۰ تا ۳۵۰
    val largeMs: Int = 700,      // ۵۰۰ تا ۹۰۰
    val ambientMs: Int = 22_000, // حرکت آرام بافت پس‌زمینه: ۱۸ تا ۲۸ ثانیه
    /** اگر کاربر انیمیشن را در تنظیمات سیستم خاموش کرده باشد */
    val reduced: Boolean = false,
) {
    /** اگر Reduced Motion فعال باشد همهٔ انیمیشن‌ها صفر می‌شوند */
    val micro: Int get() = if (reduced) 0 else microMs
    val standard: Int get() = if (reduced) 0 else standardMs
    val large: Int get() = if (reduced) 0 else largeMs
    val ambient: Int get() = if (reduced) 1 else ambientMs
}
