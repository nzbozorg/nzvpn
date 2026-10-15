package nzvpn.nzbozorg.ir

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nzvpn.nzbozorg.ir.brand.BrandConfig
import nzvpn.nzbozorg.ir.net.TrustedApk
import nzvpn.nzbozorg.ir.parser.ConfigImporter
import nzvpn.nzbozorg.ir.service.NzVpnService
import nzvpn.nzbozorg.ir.ui.NzViewModel
import nzvpn.nzbozorg.ir.ui.components.NzButton
import nzvpn.nzbozorg.ir.ui.components.NzButtonStyle
import nzvpn.nzbozorg.ir.ui.components.NzCard
import nzvpn.nzbozorg.ir.ui.components.NzHairLine
import nzvpn.nzbozorg.ir.ui.components.NzIcon
import nzvpn.nzbozorg.ir.ui.components.NzIconButton
import nzvpn.nzbozorg.ir.ui.components.NzIconKind
import nzvpn.nzbozorg.ir.ui.components.NzOverline
import nzvpn.nzbozorg.ir.ui.components.NzTextField
import nzvpn.nzbozorg.ir.ui.screens.HomeScreen
import nzvpn.nzbozorg.ir.ui.screens.SettingsSheet
import nzvpn.nzbozorg.ir.ui.screens.SplashScreen
import nzvpn.nzbozorg.ir.ui.theme.NzTheme
import nzvpn.nzbozorg.ir.util.PersianText
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // ⚠️ مهم: همهٔ صفحه‌ها باید داخل NzTheme باشند تا راست‌به‌چپ اجباری،
            // فونت وزیرمتن و رنگ‌های برند اعمال شود.
            NzTheme { NzRoot() }
        }
    }
}

@Composable
private fun NzRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: NzViewModel = viewModel()
    val state by viewModel.ui.collectAsState()
    val update by viewModel.update.collectAsState()
    val vpnStatus by viewModel.vpnStatus.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    var showSplash by remember { mutableStateOf(true) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDonate by remember { mutableStateOf(false) }
    var pendingInstall by remember { mutableStateOf<File?>(null) }
    var pendingDelete by remember { mutableStateOf<nzvpn.nzbozorg.ir.model.Profile?>(null) }
    var pendingGroup by remember { mutableStateOf<String?>(null) }

    val isWide = LocalConfiguration.current.screenWidthDp >= 600

    // ---------------------------------------------------------- راه‌اندازی
    LaunchedEffect(Unit) { viewModel.boot() }

    // ------------------------------------------------------- مجوزها و اتصال
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            connect(context, viewModel)
        } else {
            scope.launch { snackbarHost.showSnackbar("اجازهٔ VPN داده نشد") }
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val apk = pendingInstall
        pendingInstall = null
        if (apk != null && context.packageManager.canRequestPackageInstalls()) {
            launchVerifiedInstaller(context, apk, snackbarHost, scope)
        } else {
            scope.launch { snackbarHost.showSnackbar("برای نصب، اجازهٔ «نصب از این منبع» رو فعال کن") }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ------------------------------------------------- انتخاب فایل کانفیگ
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val canRefresh = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            viewModel.importUri(uri, canRefresh)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showSplash) {
                SplashScreen(
                    stage = state.bootStage,
                    bootMessage = state.bootMessage,
                    coreVersion = state.coreVersion,
                    appVersion = BrandConfig.VERSION_NAME,
                    onFinished = { showSplash = false },
                )
            } else {
                HomeScreen(
                    state = state,
                    vpn = vpnStatus,
                    isWide = isWide,
                    onConnect = {
                        val prepared = VpnService.prepare(context)
                        if (prepared != null) vpnPermissionLauncher.launch(prepared) else connect(context, viewModel)
                    },
                    onDisconnect = { NzVpnService.stop(context) },
                    onSmartConnect = {
                        viewModel.smartConnect { best ->
                            if (best != null) {
                                val prepared = VpnService.prepare(context)
                                if (prepared != null) vpnPermissionLauncher.launch(prepared)
                                else connect(context, viewModel)
                            } else {
                                scope.launch { snackbarHost.showSnackbar("سرور سالمی پیدا نشد") }
                            }
                        }
                    },
                    onAdd = { showAddSheet = true },
                    onDonate = { showDonate = true },
                    onShare = { shareApp(context, snackbarHost, scope) },
                    onSettings = { showSettings = true },
                    onRefresh = { viewModel.refreshFromRepo() },
                    onRefreshGroup = { viewModel.refreshGroup(it) },
                    onTestGroup = { viewModel.testGroup(it) },
                    onSelect = { viewModel.select(it) },
                    onDeleteServer = { pendingDelete = it },
                    onDeleteGroup = { pendingGroup = it },
                    onToggleGroup = { viewModel.toggleGroup(it) },
                )
            }
        }
    }

    // ------------------------------------------------------- پاپ‌آپ اتصال هوشمند
    if (state.testing) {
        // فقط عنوان و حلقهٔ در حال چرخش؛ بازگشت/لمس بیرون برای لغو آزمون طولانی.
        Dialog(onDismissRequest = { viewModel.cancelTest() }) {
            NzCard(elevated = true, contentPadding = 24.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "در حال تست سرورها",
                        style = MaterialTheme.typography.titleMedium,
                        color = NzTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(24.dp))
                    if (NzTheme.motion.reduced) {
                        CircularProgressIndicator(
                            progress = {
                                if (state.testingTotal > 0) {
                                    state.testingDone.toFloat() / state.testingTotal
                                } else 0f
                            },
                            modifier = Modifier.size(56.dp),
                            color = NzTheme.colors.cyan,
                            strokeWidth = 4.dp,
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = NzTheme.colors.cyan,
                            strokeWidth = 4.dp,
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ شیت تنظیمات
    if (showSettings) {
        SettingsSheet(
            update = update,
            appVersion = BuildConfig.VERSION_NAME,
            onDismiss = { showSettings = false },
            onOpenRepo = { viewModel.openUrl(BrandConfig.DEFAULT_REPO) },
            onDonate = { showSettings = false; showDonate = true },
            onShare = {
                showSettings = false
                shareApp(context, snackbarHost, scope)
            },
            onCheck = { viewModel.checkForUpdates() },
            onDownload = { viewModel.downloadUpdate() },
            onInstall = {
                val apk = viewModel.readyUpdateFile()
                if (apk == null) {
                    scope.launch { snackbarHost.showSnackbar("فایل به‌روزرسانی پیدا نشد؛ دوباره دریافت کن") }
                } else if (!context.packageManager.canRequestPackageInstalls()) {
                    pendingInstall = apk
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"))
                    runCatching { unknownSourcesLauncher.launch(intent) }.onFailure {
                        pendingInstall = null
                        scope.launch { snackbarHost.showSnackbar("صفحهٔ اجازهٔ نصب روی این گوشی باز نشد") }
                    }
                } else {
                    launchVerifiedInstaller(context, apk, snackbarHost, scope)
                }
            },
        )
    }

    // ------------------------------------------------------------ شیت افزودن
    if (showAddSheet) {
        AddSheet(
            onDismiss = { showAddSheet = false },
            onPasteClipboard = {
                val text = clipboardText(context)
                showAddSheet = false
                if (text.isNullOrBlank()) {
                    scope.launch { snackbarHost.showSnackbar("کلیپ‌بورد خالی است") }
                } else {
                    viewModel.importText(text)
                }
            },
            onPickFile = {
                showAddSheet = false
                filePicker.launch(arrayOf("*/*"))
            },
            onManualLink = { link ->
                showAddSheet = false
                viewModel.importText(link)
            },
            onFetchRepo = {
                showAddSheet = false
                viewModel.refreshFromRepo()
            },
        )
    }

    // --------------------------------------------------------- دیالوگ حمایت
    if (showDonate) {
        AlertDialog(
            onDismissRequest = { showDonate = false },
            containerColor = NzTheme.colors.surfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NzIcon(NzIconKind.COFFEE, size = 21.dp, tint = NzTheme.colors.copperBright)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = BrandConfig.DONATION_TITLE,
                        style = MaterialTheme.typography.titleMedium,
                        color = NzTheme.colors.textPrimary,
                    )
                }
            },
            text = {
                Text(
                    text = BrandConfig.DONATION_BODY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NzTheme.colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDonate = false
                        viewModel.openUrl(BrandConfig.DONATION_URL)
                    },
                ) {
                    Text(text = BrandConfig.DONATION_BUTTON, color = NzTheme.colors.copperBright)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDonate = false }) {
                    Text(text = BrandConfig.DONATION_LATER, color = NzTheme.colors.textMuted)
                }
            },
        )
    }

    // ------------------------------------------------- حذف سرور / حذف پوشه
    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = NzTheme.colors.surfaceElevated,
            title = { Text("حذف سرور", color = NzTheme.colors.textPrimary) },
            text = {
                Text(
                    text = "«${profile.displayName}» حذف شود؟",
                    color = NzTheme.colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(profile)
                    pendingDelete = null
                }) { Text("حذف", color = NzTheme.colors.redSoft) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("بی‌خیال", color = NzTheme.colors.textMuted)
                }
            },
        )
    }

    pendingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { pendingGroup = null },
            containerColor = NzTheme.colors.surfaceElevated,
            title = { Text("حذف پوشه", color = NzTheme.colors.textPrimary) },
            text = {
                Text(
                    text = "همهٔ سرورهای پوشهٔ «$group» حذف شوند؟",
                    color = NzTheme.colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeGroup(group)
                    pendingGroup = null
                }) { Text("حذف همه", color = NzTheme.colors.redSoft) }
            },
            dismissButton = {
                TextButton(onClick = { pendingGroup = null }) {
                    Text("بی‌خیال", color = NzTheme.colors.textMuted)
                }
            },
        )
    }

    // ------------------------------------------------------------- پیام‌ها
    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            snackbarHost.showSnackbar(state.message)
            viewModel.clearMessage()
        }
    }
}

/** نصب فقط با رضایت کاربر و فقط پس از بازبینی بسته و گواهی امضای همان برنامه. */
private fun launchVerifiedInstaller(
    context: Context,
    apk: File,
    snackbar: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    scope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) { TrustedApk.verify(context, apk) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }
        result.onFailure {
            snackbar.showSnackbar("نصب نسخهٔ جدید باز نشد؛ بسته یا امضاش رو دوباره بررسی کن")
        }
    }
}

/** اتصال به سرور انتخاب‌شده */
private fun connect(context: Context, viewModel: NzViewModel) {
    val profile = viewModel.selectedProfile() ?: return
    val config = viewModel.configFor(profile)
    NzVpnService.start(context, config, profile.displayName, profile.latencyMs)
}

/** متن کلیپ‌بورد */
private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

/**
 * شیت «افزودن»:
 *  ۱) چسباندن از کلیپ‌بورد (خودش تشخیص می‌دهد ساب است یا سرور تک)
 *  ۲) انتخاب فایل (ساب/کانفیگ)
 *  ۳) چسباندن دستی لینک
 *  ۴) خواندن از مخزن
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(
    onDismiss: () -> Unit,
    onPasteClipboard: () -> Unit,
    onPickFile: () -> Unit,
    onManualLink: (String) -> Unit,
    onFetchRepo: () -> Unit,
) {
    val colors = NzTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var manual by remember { mutableStateOf("") }
    val context = LocalContext.current

    // اگر کلیپ‌بورد چیز قابل‌افزودنی داشته باشد، همان اول نشان می‌دهیم
    val clipboardPreview = remember {
        clipboardText(context)?.takeIf { ConfigImporter.looksImportable(it) }?.trim()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NzTheme.dimens.screenPadding)
                .padding(bottom = 26.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    NzOverline("افزودن")
                    Text(
                        text = "سرور یا ساب جدید",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                }
                NzIconButton(
                    kind = NzIconKind.CLOSE,
                    contentDescription = "بستن",
                    onClick = onDismiss,
                )
            }

            NzHairLine(Modifier.fillMaxWidth())

            if (clipboardPreview != null) {
                NzCard(live = true) {
                    Text(
                        text = "کلیپ‌بورد شما این را دارد:",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (clipboardPreview.length > 120) clipboardPreview.take(120) + "…" else clipboardPreview,
                        style = NzTheme.mono.label,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    NzButton(
                        text = "همین را اضافه کن",
                        onClick = onPasteClipboard,
                        leading = NzIconKind.CLIPBOARD,
                    )
                }
            } else {
                NzButton(
                    text = "چسباندن از کلیپ‌بورد",
                    onClick = onPasteClipboard,
                    leading = NzIconKind.CLIPBOARD,
                )
            }

            NzButton(
                text = "انتخاب فایل کانفیگ یا ساب",
                onClick = onPickFile,
                leading = NzIconKind.FILE,
                style = NzButtonStyle.SECONDARY,
            )

            NzButton(
                text = "خواندن از مخزن گیت‌هاب",
                onClick = onFetchRepo,
                leading = NzIconKind.REFRESH,
                style = NzButtonStyle.SECONDARY,
            )

            NzHairLine(Modifier.fillMaxWidth())

            NzTextField(
                value = manual,
                onValueChange = { manual = it },
                label = "یا لینک را اینجا بچسبانید",
                supporting = "لینک سرور (vless://، vmess://، trojan://، ss://) یا لینک ساب یا کانفیگ کامل",
                minLines = 3,
                ltr = true,
            )
            NzButton(
                text = "افزودن",
                onClick = { if (manual.isNotBlank()) onManualLink(manual.trim()) },
                enabled = manual.isNotBlank(),
                leading = NzIconKind.PLUS,
            )

            Text(
                text = "هر ساب یک پوشهٔ جدا می‌شود؛ خودمان تشخیص می‌دهیم کدام ساب است و کدام سرور تنها.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDisabled,
            )
        }
    }
}

/**
 * فرستادن برنامه برای دیگران.
 *
 * اول تلاش می‌کنیم **خود فایل نصب** را بفرستیم (از طریق FileProvider) تا طرف
 * مقابل بدون اینترنت هم نصب کند؛ اگر نشد، لینک دانلود + متن معرفی فرستاده می‌شود.
 */
private fun shareApp(
    context: Context,
    snackbarHost: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val text = BrandConfig.SHARE_TEXT
    val apkUri = runCatching {
        val source = java.io.File(context.applicationInfo.sourceDir)
        if (!source.exists()) return@runCatching null
        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
        val target = java.io.File(dir, "NZVPN-${BrandConfig.VERSION_NAME}.apk")
        if (!target.exists() || target.length() != source.length()) {
            source.copyTo(target, overwrite = true)
        }
        androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            target,
        )
    }.getOrNull()

    val send = if (apkUri != null) {
        Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    runCatching {
        context.startActivity(Intent.createChooser(send, "فرستادن برنامه برای دیگران"))
    }.onFailure {
        scope.launch { snackbarHost.showSnackbar("برنامه‌ای برای اشتراک‌گذاری پیدا نشد") }
    }
}
