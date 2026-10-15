package nzvpn.nzbozorg.ir.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.compose.material3.Typography

/**
 * تم NZBOZORG (Dark Copper).
 *
 * همهٔ توکن‌های طراحی از این‌جا در دسترس‌اند:
 *      NzTheme.colors   → پالت رنگ
 *      NzTheme.dimens   → فاصله‌ها و اندازه‌ها
 *      NzTheme.mono     → استایل‌های مونواسپیس (IP/پینگ/سرعت)
 *      NzTheme.motion   → زمان‌بندی انیمیشن (با احترام به Reduced Motion)
 */
@Immutable
class NzDesign(
    val colors: NzColors,
    val dimens: NzDimens,
    val mono: NzMonoStyles,
    val motion: NzMotion,
)

private val LocalNzDesign = staticCompositionLocalOf {
    NzDesign(
        colors = NzDefaultColors,
        dimens = NzDimens(),
        mono = NzMonoStyles(),
        motion = NzMotion(),
    )
}

/** برای پیش‌نمایش‌ها و تست‌ها */
val LocalNzTypographyOverride = compositionLocalOf<Typography?> { null }

object NzTheme {
    val colors: NzColors
        @Composable @ReadOnlyComposable get() = LocalNzDesign.current.colors

    val dimens: NzDimens
        @Composable @ReadOnlyComposable get() = LocalNzDesign.current.dimens

    val mono: NzMonoStyles
        @Composable @ReadOnlyComposable get() = LocalNzDesign.current.mono

    val motion: NzMotion
        @Composable @ReadOnlyComposable get() = LocalNzDesign.current.motion
}

/** نگاشت پالت NZBOZORG روی اسکیم رنگی Material 3 */
private fun nzColorScheme(colors: NzColors) = darkColorScheme(
    primary = colors.copper,
    onPrimary = colors.onCopper,
    primaryContainer = colors.copperDark,
    onPrimaryContainer = colors.copperHighlight,
    secondary = colors.textSecondary,
    onSecondary = colors.background,
    tertiary = colors.cyan,
    onTertiary = colors.background,
    tertiaryContainer = colors.cyanDeep,
    onTertiaryContainer = colors.textPrimary,
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceElevated,
    onSurfaceVariant = colors.textSecondary,
    surfaceContainer = colors.surface,
    surfaceContainerHigh = colors.surfaceElevated,
    surfaceContainerHighest = colors.surfaceSecondary,
    surfaceContainerLow = colors.surfaceDeep,
    error = colors.errorRed,
    onError = colors.textPrimary,
    errorContainer = colors.red,
    onErrorContainer = colors.onCopper,
    outline = colors.border,
    outlineVariant = colors.borderStrong,
    scrim = colors.background,
)

/** تشخیص خاموش‌بودن انیمیشن‌ها در تنظیمات سیستم (بخش ۹: رعایت Reduced Motion) */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        scale == 0f
    }
}

@Composable
fun NzTheme(
    colors: NzColors = NzDefaultColors,
    dimens: NzDimens = NzDimens(),
    mono: NzMonoStyles = NzMonoStyles(),
    motion: NzMotion = NzMotion(reduced = rememberReducedMotion()),
    typography: Typography = NzTypography,
    content: @Composable () -> Unit,
) {
    val design = remember(colors, dimens, mono, motion) {
        NzDesign(colors = colors, dimens = dimens, mono = mono, motion = motion)
    }
    val scheme = remember(colors) { nzColorScheme(colors) }

    // نوارهای سیستم شفاف و آیکون‌های روشن (لبه‌تالبه — edge-to-edge)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    // طراحی ما همیشه تیره است؛ isSystemInDarkTheme فقط برای ثبت نیت استفاده می‌شود
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()

    CompositionLocalProvider(
        LocalNzDesign provides design,
        LocalNzTypographyOverride provides typography,
        // برنامه فقط راست‌به‌چپ است (حتی اگر زبان گوشی انگلیسی باشد)
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = NzShapes,
            content = content,
        )
    }
}
