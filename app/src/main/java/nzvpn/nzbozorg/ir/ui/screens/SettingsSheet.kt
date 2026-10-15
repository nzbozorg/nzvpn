package nzvpn.nzbozorg.ir.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nzvpn.nzbozorg.ir.brand.BrandConfig
import nzvpn.nzbozorg.ir.ui.UpdatePhase
import nzvpn.nzbozorg.ir.ui.UpdateUiState
import nzvpn.nzbozorg.ir.ui.components.NzButton
import nzvpn.nzbozorg.ir.ui.components.NzButtonStyle
import nzvpn.nzbozorg.ir.ui.components.NzCard
import nzvpn.nzbozorg.ir.ui.components.NzIconButton
import nzvpn.nzbozorg.ir.ui.components.NzIconKind
import nzvpn.nzbozorg.ir.ui.components.NzThinProgress
import nzvpn.nzbozorg.ir.ui.theme.NzTheme
import nzvpn.nzbozorg.ir.util.PersianText

/** اطلاعات و راه‌های اختیاری تعامل؛ هیچ گزینه‌ای برای تغییر تنظیمات VPN ندارد. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    update: UpdateUiState,
    appVersion: String,
    onDismiss: () -> Unit,
    onOpenRepo: () -> Unit,
    onDonate: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = NzTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = NzTheme.dimens.screenPadding)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("تنظیمات", style = MaterialTheme.typography.titleLarge, color = colors.copperBright)
                NzIconButton(NzIconKind.CLOSE, "بستن تنظیمات", onDismiss)
            }

            NzCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("اطلاعات برنامه", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text("نسخهٔ ${PersianText.digits(appVersion)} • NZBOZORG",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Text("ساخته‌شده با ❤️ به دست Amir NoZohour؛ رایگان و متن‌باز.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    NzButton("دیدن سورس در گیت‌هاب", onOpenRepo, style = NzButtonStyle.SECONDARY)
                }
            }

            NzCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("یه قهوه؟ ☕", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text("هیچ اجباری نیست؛ همین که اینجایی کلی دلگرمیه. اگه دلت خواست، یه فنجون قهوه هم خوشحالم می‌کنه ☕", 
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    NzButton(BrandConfig.DONATION_BUTTON, onDonate, style = NzButtonStyle.SECONDARY)
                }
            }

            NzCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("به‌روزرسانی برنامه", style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary)
                    Text(
                        text = when (update.phase) {
                            UpdatePhase.IDLE -> "نسخهٔ جدید از انتشارهای رسمی گیت‌هاب بررسی می‌شه."
                            UpdatePhase.CHECKING -> "در حال بررسی انتشارهای گیت‌هاب…"
                            UpdatePhase.CURRENT -> "همین حالا آخرین نسخه رو داری ✨"
                            UpdatePhase.AVAILABLE -> "نسخهٔ ${PersianText.digits(update.release?.version.orEmpty())} آمادهٔ دریافته."
                            UpdatePhase.DOWNLOADING -> "دریافت فایل… ${PersianText.digits(update.percent)}٪"
                            UpdatePhase.READY -> "فایل دریافت شد؛ امضای بسته با برنامهٔ فعلی یکیه."
                            UpdatePhase.ERROR -> update.message.ifBlank { "بررسی به‌روزرسانی ناموفق بود" }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (update.phase == UpdatePhase.ERROR) colors.redSoft else colors.textSecondary,
                    )
                    when (update.phase) {
                        UpdatePhase.CHECKING -> NzThinProgress(progress = null)
                        UpdatePhase.DOWNLOADING -> NzThinProgress(progress = update.percent / 100f,
                            color = colors.cyan)
                        else -> Unit
                    }
                    when (update.phase) {
                        UpdatePhase.IDLE, UpdatePhase.CURRENT -> NzButton(
                            "بررسی نسخهٔ جدید", onCheck, leading = NzIconKind.REFRESH,
                        )
                        UpdatePhase.AVAILABLE -> NzButton(
                            "دریافت نسخهٔ ${PersianText.digits(update.release?.version.orEmpty())}",
                            onDownload, leading = NzIconKind.DOWNLOAD,
                        )
                        UpdatePhase.READY -> NzButton(
                            "نصب نسخهٔ ${PersianText.digits(update.release?.version.orEmpty())}",
                            onInstall, leading = NzIconKind.DOWNLOAD,
                        )
                        UpdatePhase.ERROR -> NzButton(
                            "دوباره بررسی کن", onCheck, style = NzButtonStyle.SECONDARY,
                        )
                        UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING -> Unit
                    }
                    Text("نصب با تأیید خودت و از طریق نصب‌کنندهٔ گوشی انجام می‌شه.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }

            NzCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("اشتراک‌گذاری برنامه", style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary)
                    Text("اگر دوست داشتی، فایل خود برنامه رو برای دوستات بفرست.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    NzButton("فرستادن برنامه", onShare, leading = NzIconKind.SHARE,
                        style = NzButtonStyle.SECONDARY)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("NZVPN • Amir NoZohour", modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center, color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
