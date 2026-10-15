package nzvpn.nzbozorg.ir.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nzvpn.nzbozorg.ir.R
import nzvpn.nzbozorg.ir.brand.BrandConfig
import nzvpn.nzbozorg.ir.ui.BootStage
import nzvpn.nzbozorg.ir.ui.components.NzBackground
import nzvpn.nzbozorg.ir.ui.components.NzHairLine
import nzvpn.nzbozorg.ir.ui.components.NzIcon
import nzvpn.nzbozorg.ir.ui.components.NzIconKind
import nzvpn.nzbozorg.ir.ui.components.NzOverline
import nzvpn.nzbozorg.ir.ui.components.NzThinProgress
import nzvpn.nzbozorg.ir.ui.theme.NzBrandTitleStyle
import nzvpn.nzbozorg.ir.ui.theme.NzTheme
import nzvpn.nzbozorg.ir.ui.theme.ltr
import nzvpn.nzbozorg.ir.util.PersianText

/**
 * صفحهٔ لودینگ (Splash).
 *
 * طراحی بر اساس سند تم: تیره، سینمایی، مسی/فیروزه‌ای، ساکت و جدی.
 *  - «NZ» با رنگ مسی و «VPN» با رنگ کرم (بخش ۳: یک کلمهٔ مهم با رنگ مسی)
 *  - نوار پیشرفت نازک فیروزه‌ای (دادهٔ زنده)
 *  - خطوط وضعیت با فونت مونو (بخش ۳)
 *  - ورود نرم (Fade + جابه‌جایی ۱۲dp) و احترام به Reduced Motion (بخش ۹)
 *
 * این صفحه واقعاً کار می‌کند: همان لحظه، هستهٔ Xray مقدار‌دهی اولیه می‌شود و
 * سرورها از حافظه خوانده می‌شوند؛ پس از آماده‌شدن (حداقل ~۱ ثانیه برای حس نرم)
 * به صفحهٔ اصلی می‌رود.
 */
@Composable
fun SplashScreen(
    stage: BootStage,
    bootMessage: String,
    coreVersion: String,
    appVersion: String,
    onFinished: () -> Unit,
) {
    val colors = NzTheme.colors
    val motion = NzTheme.motion

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // وقتی کارهای واقعی تمام شد، کمی صبر می‌کنیم تا گذار نرم باشد
    LaunchedEffect(stage) {
        if (stage == BootStage.READY) {
            val minShow = if (motion.reduced) 250L else 900L
            delay(minShow)
            onFinished()
        }
    }

    val progress: Float = when (stage) {
        BootStage.IDLE -> 0.06f
        BootStage.CORE -> 0.40f
        BootStage.STORAGE -> 0.68f
        BootStage.REPO -> 0.88f
        BootStage.READY -> 1f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(if (motion.reduced) 0 else 520),
        label = "bootProgress",
    )

    NzBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(if (motion.reduced) 0 else 600)) +
                    slideInVertically(tween(if (motion.reduced) 0 else 600)) { it / 12 },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Image(
                        painter = painterResource(R.drawable.nz_logo_mark),
                        contentDescription = "نشان برنامهٔ NZVPN",
                        modifier = Modifier.size(108.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    NzOverline(BrandConfig.FULL_NAME)

                    Spacer(Modifier.height(14.dp))

                    // نام برند: NZ (مسی) + VPN (کرم)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.copperBright, fontWeight = FontWeight.SemiBold)) {
                                append(BrandConfig.BRAND_FIRST)
                            }
                            withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.Medium)) {
                                append(BrandConfig.BRAND_SECOND)
                            }
                        },
                        style = NzBrandTitleStyle.copy(fontSize = 56.sp),
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = BrandConfig.TAGLINE,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )

                    Spacer(Modifier.height(34.dp))

                    NzThinProgress(progress = animatedProgress, color = colors.cyan)

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NzIcon(
                                kind = when (stage) {
                                    BootStage.READY -> NzIconKind.CHECK
                                    else -> NzIconKind.CLOCK
                                },
                                size = 14.dp,
                                tint = if (stage == BootStage.READY) colors.cyan else colors.copper,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = bootMessage,
                                style = NzTheme.mono.value,
                                color = colors.textMuted,
                            )
                        }
                        Text(
                            text = "نگارش ${PersianText.digits(appVersion)}",
                            style = NzTheme.mono.label,
                            color = colors.textDisabled,
                        )
                    }

                    if (coreVersion.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        NzHairLine(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = coreVersionFa(coreVersion),
                            style = NzTheme.mono.label,
                            color = colors.textDisabled,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1.15f))

            Text(
                text = "© ${BrandConfig.FULL_NAME}",
                style = NzTheme.mono.label,
                color = colors.textDisabled,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 320.dp)
                    .padding(bottom = 18.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090A0A)
@Composable
private fun SplashPreview() {
    NzTheme {
        SplashScreen(
            stage = BootStage.CORE,
            bootMessage = "آماده‌سازی هستهٔ اتصال…",
            coreVersion = "Lib v40 · Xray-core v26.10.10",
            appVersion = "1.0.0",
            onFinished = {},
        )
    }
}

/**
 * «Lib v40, Xray-core v26.10.10»  →  «کتابخانهٔ هسته ۴۰ • ایکسری ۲۶.۱۰.۱۰»
 * (هسته متن انگلیسی برمی‌گرداند؛ ما فارسی‌اش می‌کنیم)
 */
internal fun coreVersionFa(raw: String): String {
    val lib = Regex("Lib v([0-9.]+)").find(raw)?.groupValues?.get(1)
    val core = Regex("Xray-core v([0-9.]+)").find(raw)?.groupValues?.get(1)
        ?: Regex("v([0-9.]+)").find(raw)?.groupValues?.get(1)
    val parts = buildList {
        if (lib != null) add("کتابخانهٔ هسته ${PersianText.digits(lib)}")
        if (core != null) add("ایکس‌ری ${PersianText.digits(core)}")
    }
    return if (parts.isEmpty()) "هستهٔ اتصال آماده است" else parts.joinToString(" • ")
}
