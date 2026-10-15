package nzvpn.nzbozorg.ir.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import nzvpn.nzbozorg.ir.R

/**
 * تایپوگرافی NZBOZORG
 * مرجع: docs/theme-spec.md (بخش ۳)
 *
 *  - تیترهای بزرگ و سینمایی: Cormorant Garamond (وزن ۵۰۰/۶۰۰)
 *  - متن رابط: Vazirmatn  ← معادل فارسی Inter/Roboto که سند تم پیشنهاد کرده است؛
 *    چون Inter و Roboto هیچ گلیف فارسی ندارند و متن فارسی به فونت سیستمی می‌افتاد.
 *  - اطلاعات فنی (IP، پینگ، پهنای‌باند، وضعیت): JetBrains Mono
 *
 * همهٔ فونت‌ها داخل APK هستند (res/font) و پروانهٔ OFL دارند؛
 * متن پروانه‌ها: assets/licenses/
 */
val NzSerif = FontFamily(
    Font(R.font.cormorant_500, FontWeight.Medium),
    Font(R.font.cormorant_600, FontWeight.SemiBold),
)

val NzSans = FontFamily(
    Font(R.font.vazir_400, FontWeight.Normal),
    Font(R.font.vazir_500, FontWeight.Medium),
    Font(R.font.vazir_600, FontWeight.SemiBold),
)

val NzMono = FontFamily(
    Font(R.font.jbmono_400, FontWeight.Normal),
    Font(R.font.jbmono_500, FontWeight.Medium),
    Font(R.font.jbmono_600, FontWeight.SemiBold),
)

/** استایل‌های مخصوص اعداد و اطلاعات فنی (مونواسپیس) */
@Immutable
data class NzMonoStyles(
    /** برچسب وضعیت، پروتکل، کد سرور */
    val label: TextStyle = TextStyle(
        fontFamily = NzMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
    ),
    /** مقدار فنی معمولی (آدرس، پورت، زمان) */
    val value: TextStyle = TextStyle(
        fontFamily = NzMono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp,
    ),
    /** عدد بزرگ (پینگ/سرعت) */
    val metric: TextStyle = TextStyle(
        fontFamily = NzMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 0.sp,
    ),
)

/**
 * مقیاس تایپوگرافی Material 3 با فونت‌های NZBOZORG.
 * سرصفحه‌ها فشرده و پرکنتراست‌اند (طبق بخش ۳ سند تم).
 */
val NzTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NzSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = NzSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.4).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = NzSerif, fontWeight = FontWeight.Medium,
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = NzSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = NzSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = NzSerif, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = NzSans, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = NzSans, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = NzSans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = NzSans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = NzSans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = NzSans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = NzSans, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = NzSans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = NzSans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp,
    ),
)

/** استایل ویژهٔ برند: «NZ» مسی + «VPN» کرم */
val NzBrandTitleStyle = TextStyle(
    fontFamily = NzSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    lineHeight = 38.sp,
    letterSpacing = 2.sp,
)

/** برچسب‌های انگلیسی افقی (LOADING, SECURE, …) */
val NzOverlineStyle = TextStyle(
    fontFamily = NzMono,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 3.sp,
    fontStyle = FontStyle.Normal,
)

/**
 * متن چپ‌به‌راست (آدرس، میزبان، نسخهٔ هسته، توکن) داخل رابط راست‌به‌چپ.
 * بدون این، بای‌دای در آدرس‌ها آن‌ها را معکوس نشان می‌دهد.
 */
fun TextStyle.ltr(): TextStyle = copy(textDirection = TextDirection.Ltr)
