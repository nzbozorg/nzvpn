package nzvpn.nzbozorg.ir.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nzvpn.nzbozorg.ir.brand.BrandConfig
import nzvpn.nzbozorg.ir.data.GroupSource
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
    subscriptions: List<GroupSource>,
    appVersion: String,
    onDismiss: () -> Unit,
    onOpenRepo: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenProfile: () -> Unit,
    onDonate: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onRemoveSubscription: (String) -> Unit,
    onRestoreRepo: () -> Unit,
) {
    val colors = NzTheme.colors
    var pendingRemoval by remember { mutableStateOf<GroupSource?>(null) }
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

            // این کارت اول می‌آید تا بازکردن اعلان، کاربر را همان ابتدا به آپدیت برساند.
            Box(Modifier.fillMaxWidth()) {
                NzCard(contentPadding = 16.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("به‌روزرسانی برنامه", style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary)
                        if (update.hasPendingUpdate) Text("جدید", style = MaterialTheme.typography.labelSmall,
                            color = colors.redSoft)
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = when (update.phase) {
                            UpdatePhase.IDLE -> "نسخهٔ جدید از انتشارهای رسمی گیت‌هاب بررسی می‌شه."
                            UpdatePhase.CHECKING -> "در حال بررسی انتشارهای گیت‌هاب…"
                            UpdatePhase.CURRENT -> "همین حالا آخرین نسخه رو داری ✨"
                            UpdatePhase.AVAILABLE -> "نسخهٔ ${PersianText.digits(update.release?.version.orEmpty())} آمادهٔ دریافته."
                            UpdatePhase.DOWNLOADING -> "دریافت فایل… ${PersianText.digits(update.percent)}٪"
                            UpdatePhase.PAUSED -> "دریافت متوقف شده؛ ${PersianText.digits(update.percent)}٪ دریافت شده."
                            UpdatePhase.READY -> "فایل دریافت شد؛ امضای بسته با برنامهٔ فعلی یکیه."
                            UpdatePhase.ERROR -> update.message.ifBlank { "بررسی به‌روزرسانی ناموفق بود" }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (update.phase == UpdatePhase.ERROR) colors.redSoft else colors.textSecondary,
                    )
                    if (update.phase == UpdatePhase.CHECKING ||
                        update.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.PAUSED)) {
                        Spacer(Modifier.height(10.dp))
                        NzThinProgress(
                            progress = if (update.phase == UpdatePhase.CHECKING) null
                                else update.percent / 100f,
                            color = colors.cyan,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    when (update.phase) {
                        UpdatePhase.IDLE, UpdatePhase.CURRENT -> NzButton(
                            "بررسی نسخهٔ جدید", onCheck, leading = NzIconKind.REFRESH,
                        )
                        UpdatePhase.AVAILABLE -> NzButton(
                            "دریافت نسخهٔ ${PersianText.digits(update.release?.version.orEmpty())}",
                            onDownload, leading = NzIconKind.DOWNLOAD,
                        )
                        UpdatePhase.DOWNLOADING, UpdatePhase.PAUSED -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NzIconButton(
                                kind = if (update.phase == UpdatePhase.DOWNLOADING)
                                    NzIconKind.PAUSE else NzIconKind.PLAY,
                                contentDescription = if (update.phase == UpdatePhase.DOWNLOADING)
                                    "توقف موقت دانلود" else "ادامهٔ دانلود",
                                onClick = if (update.phase == UpdatePhase.DOWNLOADING)
                                    onPauseDownload else onDownload,
                                tint = colors.copperBright,
                            )
                            NzIconButton(
                                kind = NzIconKind.CLOSE,
                                contentDescription = "لغو دانلود و پاک‌کردن بخش دریافت‌شده",
                                onClick = onCancelDownload,
                                tint = colors.redSoft,
                            )
                            Text("${PersianText.digits(update.percent)}٪",
                                color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        UpdatePhase.READY -> NzButton(
                            "نصب نسخهٔ ${PersianText.digits(update.release?.version.orEmpty())}",
                            onInstall, leading = NzIconKind.DOWNLOAD,
                        )
                        UpdatePhase.ERROR -> if (update.release == null) NzButton(
                            "دوباره بررسی کن", onCheck, style = NzButtonStyle.SECONDARY,
                        ) else NzButton(
                            "ادامه یا تلاش دوبارهٔ دریافت", onDownload,
                            style = NzButtonStyle.SECONDARY, leading = NzIconKind.PLAY,
                        )
                        UpdatePhase.CHECKING -> Unit
                    }
                    if (update.phase == UpdatePhase.ERROR && update.release != null) {
                        NzIconButton(NzIconKind.CLOSE, "لغو و پاک‌کردن دانلود", onCancelDownload,
                            tint = colors.redSoft)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("نصب با تأیید خودت و از طریق نصب‌کنندهٔ گوشی انجام می‌شه.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
                if (update.hasPendingUpdate) Box(
                    Modifier.align(Alignment.TopEnd) // گوشهٔ چپ فیزیکی کارت در RTL
                        .padding(top = 7.dp, end = 7.dp).size(9.dp)
                        .clip(CircleShape).background(colors.redSoft),
                )
            }

            NzCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("مدیریت ساب‌ها", style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary)
                    Text("ساب‌های مخزن و ساب‌های خودت رو اینجا می‌تونی ببینی یا حذف کنی. سرورهای ستاره‌دارِ ذخیره‌شده مستقل می‌مونن.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    if (subscriptions.isEmpty()) Text("فعلاً سابی ثبت نشده.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    subscriptions.forEach { source ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(source.group, style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary)
                                Text(if (source.kind == GroupSource.Kind.REPO) "پیش‌فرض مخزن" else "ساب شخصی",
                                    style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                            NzIconButton(
                                kind = NzIconKind.TRASH,
                                contentDescription = "حذف ساب ${source.group}",
                                onClick = { pendingRemoval = source },
                                tint = colors.redSoft,
                            )
                        }
                    }
                    Text("برای برگردوندن ساب پیش‌فرض: «سرور یا ساب جدید ← خواندن از مخزن گیت‌هاب» رو بزن.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    NzButton("خواندن دوباره از مخزن گیت‌هاب", onRestoreRepo,
                        style = NzButtonStyle.SECONDARY, leading = NzIconKind.REFRESH)
                }
            }

            NzCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("اطلاعات برنامه", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text("نسخهٔ ${PersianText.digits(appVersion)} • NZBOZORG",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Text("ساخته‌شده با ❤️ به دست Amir NoZohour؛ رایگان و متن‌باز.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    NzButton("دیدن سورس در گیت‌هاب", onOpenRepo,
                        style = NzButtonStyle.SECONDARY, leading = NzIconKind.LINK_OUT)
                    NzButton("ستاره دادن در گیت‌هاب", onOpenRepo,
                        style = NzButtonStyle.SECONDARY, leading = NzIconKind.STAR)
                    NzButton("اکانت شخصی در گیت‌هاب", onOpenProfile,
                        style = NzButtonStyle.SECONDARY, leading = NzIconKind.LINK_OUT)
                    NzButton("کانال تلگرام NZBOZORG", onOpenChannel,
                        style = NzButtonStyle.SECONDARY, leading = NzIconKind.LINK_OUT)
                    NzButton("سایت NZBOZORG", onOpenWebsite,
                        style = NzButtonStyle.SECONDARY, leading = NzIconKind.GLOBE)
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
    pendingRemoval?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("حذف ساب «${source.group}»؟") },
            text = { Text("سرورهای این ساب حذف می‌شن، اما کپی‌های ذخیره‌شده می‌مونن. برای برگردوندن ساب پیش‌فرض، بعداً «خواندن از مخزن گیت‌هاب» رو دستی بزن.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveSubscription(source.group)
                    pendingRemoval = null
                }) { Text("حذف", color = colors.redSoft) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("بی‌خیال") }
            },
            containerColor = colors.surfaceElevated,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
        )
    }
}
