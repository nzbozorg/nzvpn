package nzvpn.nzbozorg.ir.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * پالت رنگ NZBOZORG — Dark Copper
 * مرجع: docs/theme-spec.md (بخش ۲ — پالت رنگ)
 *
 * قانون استفاده:
 *  - مشکی/خاکستری تیره پایهٔ رابط است.
 *  - مسی رنگ برند، دکمه‌های اصلی و نقاط تأکید.
 *  - فیروزه‌ای فقط برای «فعال/موفق/دادهٔ زنده».
 *  - قرمز فقط برای خطا و هشدار.
 *  - درخشش (Glow) محدود، نه روی همهٔ اجزا.
 */
@Immutable
data class NzColors(
    // پس‌زمینه‌ها
    val background: Color = Color(0xFF090A0C),
    val surface: Color = Color(0xFF111317),
    val surfaceElevated: Color = Color(0xFF171A1F),
    val surfaceSecondary: Color = Color(0xFF20242A),
    val surfaceDeep: Color = Color(0xFF0D0F12),

    // متن‌ها
    val textPrimary: Color = Color(0xFFE9E4D9),
    val textSecondary: Color = Color(0xFFB7B4AD),
    val textMuted: Color = Color(0xFF9D9A92),
    val textDisabled: Color = Color(0xFF666761),
    val onCopper: Color = Color(0xFF090A0C),

    // مسی
    val copper: Color = Color(0xFFC27D4D),
    val copperBright: Color = Color(0xFFD88752),
    val copperBurnished: Color = Color(0xFF814824),
    val copperHighlight: Color = Color(0xFFE09A62),
    val copperDark: Color = Color(0xFF6B3C24),

    // مکمل
    val cyan: Color = Color(0xFF5DD6F4),
    val cyanDeep: Color = Color(0xFF286678),
    val red: Color = Color(0xFFFF3227),
    val redSoft: Color = Color(0xFFFF5A4E),
    val errorRed: Color = Color(0xFFDC2626),

    // خطوط و حاشیه‌ها
    val border: Color = Color(0x26E9E4D9),          // rgba(233,228,217,0.15)
    val borderStrong: Color = Color(0x47E9E4D9),    // rgba(233,228,217,0.28)
    val borderCopper: Color = Color(0x8CD88752),    // rgba(216,135,82,0.55)
    val borderCyan: Color = Color(0x735DD6F4),      // rgba(93,214,244,0.45)

    // لایه‌های کمکی برای پس‌زمینه (بخش ۸ — بافت)
    val gridLine: Color = Color(0x0DE9E4D9),        // شبکه ۳٪ تا ۵٪
    val ambientCyan: Color = Color(0x145DD6F4),     // نور محو فیروزه‌ای
    val ambientCopper: Color = Color(0x14D88752),   // نور محو مسی
)

/** پالت تیرهٔ ثابت برنامه (تم روشن نداریم؛ طراحی سینمایی تیره است) */
val NzDefaultColors = NzColors()
