package nzvpn.nzbozorg.ir.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nzvpn.nzbozorg.ir.R
import nzvpn.nzbozorg.ir.brand.BrandConfig
import nzvpn.nzbozorg.ir.data.GroupSource
import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.service.VpnState
import nzvpn.nzbozorg.ir.service.VpnStatus
import nzvpn.nzbozorg.ir.ui.NzUiState
import nzvpn.nzbozorg.ir.ui.ServerGroup
import nzvpn.nzbozorg.ir.ui.components.NzButton
import nzvpn.nzbozorg.ir.ui.components.NzButtonStyle
import nzvpn.nzbozorg.ir.ui.components.NzBackground
import nzvpn.nzbozorg.ir.ui.components.NzCard
import nzvpn.nzbozorg.ir.ui.components.NzChip
import nzvpn.nzbozorg.ir.ui.components.NzCountryFlag
import nzvpn.nzbozorg.ir.ui.components.NzHairLine
import nzvpn.nzbozorg.ir.ui.components.NzIcon
import nzvpn.nzbozorg.ir.ui.components.NzIconButton
import nzvpn.nzbozorg.ir.ui.components.NzIconKind
import nzvpn.nzbozorg.ir.ui.components.NzLatencyBadge
import nzvpn.nzbozorg.ir.ui.components.NzStatusPill
import nzvpn.nzbozorg.ir.ui.components.vpnStatusVisual
import nzvpn.nzbozorg.ir.ui.components.NzThinProgress
import nzvpn.nzbozorg.ir.ui.components.NzTrafficRow
import nzvpn.nzbozorg.ir.ui.theme.NzTheme
import nzvpn.nzbozorg.ir.ui.theme.ltr
import nzvpn.nzbozorg.ir.util.PersianText

/**
 * صفحهٔ اصلی.
 *
 * ساختار (همه فارسی، همه راست‌به‌چپ):
 *   نوار بالا: [NZVPN] … [افزودن] [اشتراک‌گذاری] [تنظیمات] [فنجان قهوه]
 *   کارت اتصال: وضعیت، ترافیک، سرور، پینگ، دکمهٔ اتصال/قطع + اتصال هوشمند
 *   سرورها:     پوشه‌بندی خودکار (هر ساب یک پوشه)، باز/بسته شدن با لمس سر پوشه
 */
@Composable
fun HomeScreen(
    state: NzUiState,
    vpn: VpnStatus,
    isWide: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSmartConnect: () -> Unit,
    onAdd: () -> Unit,
    onDonate: () -> Unit,
    onShare: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshGroup: (String) -> Unit,
    onTestGroup: (String) -> Unit,
    onSelect: (Profile) -> Unit,
    onDeleteServer: (Profile) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onToggleGroup: (String) -> Unit,
) {
    val colors = NzTheme.colors
    val dimens = NzTheme.dimens

    NzBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            TopBar(
                onAdd = onAdd,
                onDonate = onDonate,
                onShare = onShare,
                onSettings = onSettings,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = dimens.screenPadding,
                    end = dimens.screenPadding,
                    top = 8.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.md),
            ) {
                item {
                    ConnectionPanel(
                        state = state,
                        vpn = vpn,
                        onConnect = onConnect,
                        onDisconnect = onDisconnect,
                        onSmartConnect = onSmartConnect,
                    )
                }

                if (state.busy) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = state.busyText.ifBlank { "کمی صبر کنید…" },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            NzThinProgress(progress = state.progress)
                        }
                    }
                }

                if (state.message.isNotBlank()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NzIcon(NzIconKind.INFO, size = 15.dp, tint = colors.copperBright)
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                item { ServersHeader(state = state, onRefresh = onRefresh) }

                for (group in state.groups) {
                    item(key = "group-${group.name}") {
                        GroupHeader(
                            group = group,
                            testing = state.testingGroup == group.name,
                            testedDone = state.testingGroupDone,
                            testedTotal = state.testingGroupTotal,
                            refreshing = state.refreshingGroup == group.name,
                            canTest = state.testingGroup.isEmpty() && !state.testing,
                            canRefresh = state.refreshingGroup.isEmpty() && !state.busy,
                            onToggle = { onToggleGroup(group.name) },
                            onRefresh = { onRefreshGroup(group.name) },
                            onTest = { onTestGroup(group.name) },
                            onDelete = { onDeleteGroup(group.name) },
                        )
                    }
                    if (!group.collapsed) {
                        items(group.profiles, key = { "${group.name}|${it.id}" }) { profile ->
                            ServerRow(
                                profile = profile,
                                selected = profile.id == state.selectedId,
                                onClick = { onSelect(profile) },
                                onDelete = { onDeleteServer(profile) },
                            )
                        }
                    }
                }

                item { DonationCard(onDonate = onDonate) }
                item { FooterNote() }
            }
        }
    }
}

// ------------------------------------------------------------------ نوار بالا

@Composable
private fun TopBar(
    onAdd: () -> Unit,
    onDonate: () -> Unit,
    onShare: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = NzTheme.colors
    val width = LocalConfiguration.current.screenWidthDp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NzTheme.dimens.screenPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // سمت راست (شروع در RTL): آیکون برنامه + نام برند
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            AppLogo(size = if (width < 400) 30.dp else 36.dp)
            // در گوشی‌های باریک، نشان حفظ می‌شود و نام کوتاه‌تر جا باز می‌کند.
            if (width >= 360) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    if (width < 400) {
                        Text("NZVPN", style = NzTheme.mono.value.copy(fontSize = 17.sp,
                            fontWeight = FontWeight.Bold), color = colors.copperBright)
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = BrandConfig.BRAND_FIRST,
                                style = NzTheme.mono.value.copy(
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                ),
                                color = colors.copperBright,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = BrandConfig.BRAND_SECOND,
                                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                                color = colors.textPrimary,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // سمت چپ: افزودن · اشتراک‌گذاری · تنظیمات · فنجان قهوه
        NzIconButton(
            kind = NzIconKind.PLUS,
            contentDescription = "افزودن سرور یا ساب",
            onClick = onAdd,
        )
        Spacer(Modifier.width(2.dp))
        NzIconButton(
            kind = NzIconKind.SHARE,
            contentDescription = "فرستادن برنامه برای دیگران",
            onClick = onShare,
        )
        Spacer(Modifier.width(2.dp))
        NzIconButton(
            kind = NzIconKind.SETTINGS,
            contentDescription = "تنظیمات و به‌روزرسانی برنامه",
            onClick = onSettings,
        )
        Spacer(Modifier.width(2.dp))
        NzIconButton(
            kind = NzIconKind.COFFEE,
            contentDescription = "حمایت مالی",
            onClick = onDonate,
            tint = colors.copperBright,
        )
    }
}

// --------------------------------------------------------------- کارت اتصال

@Composable
private fun ConnectionPanel(
    state: NzUiState,
    vpn: VpnStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSmartConnect: () -> Unit,
) {
    val colors = NzTheme.colors
    val connected = vpn.state == VpnState.CONNECTED
    val connecting = vpn.state == VpnState.CONNECTING
    val selected = state.selected

    NzCard(elevated = true, live = connected) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NzStatusPill(
                visual = vpnStatusVisual(vpn.state),
                pulsing = vpn.state == VpnState.CONNECTING,
            )
            if (connected) {
                NzTrafficRow(up = vpn.upBytes, down = vpn.downBytes)
            }
        }

        Spacer(Modifier.height(14.dp))

        val shownProfile = if (connected) {
            state.servers.firstOrNull { it.displayName == vpn.profileName } ?: selected
        } else selected
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (shownProfile?.flagCountryCode != null) {
                NzCountryFlag(shownProfile.flagCountryCode)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = when {
                    vpn.server.isNotBlank() && connected -> vpn.server
                    connected && vpn.profileName.isNotBlank() -> vpn.profileName
                    shownProfile != null -> shownProfile.displayName
                    else -> "سروری انتخاب نشده"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (connected) colors.copperHighlight else colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        val endpoint = if (vpn.server.isNotBlank() && connected) "" else selected?.endpoint.orEmpty()
        if (endpoint.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NzIcon(NzIconKind.GLOBE, size = 14.dp, tint = colors.textMuted)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = endpoint,
                    style = NzTheme.mono.value.ltr(),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val latency = if (vpn.latencyMs > 0) vpn.latencyMs else selected?.latencyMs ?: -1
        if (shownProfile != null || connected) {
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "پینگ",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (latency > 0) colors.cyan else colors.redSoft,
                )
                NzLatencyBadge(latency)
            }
        }

        if (vpn.message.isNotBlank() && !connected) {
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                NzIcon(NzIconKind.WARNING, size = 15.dp, tint = colors.redSoft)
                Text(
                    text = vpn.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.redSoft,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        NzButton(
            text = when {
                connected -> "قطع اتصال"
                connecting -> "در حال اتصال…"
                else -> "اتصال"
            },
            onClick = { if (connected) onDisconnect() else onConnect() },
            leading = if (connected) NzIconKind.CLOSE else NzIconKind.POWER,
            enabled = !connecting && (selected != null || connected),
            style = if (connected) NzButtonStyle.SECONDARY else NzButtonStyle.PRIMARY,
        )

        Spacer(Modifier.height(8.dp))

        NzButton(
            text = "اتصال به سریع‌ترین سرور",
            onClick = onSmartConnect,
            leading = NzIconKind.BOLT,
            enabled = !connecting && state.servers.isNotEmpty(),
            style = NzButtonStyle.SECONDARY,
        )
    }
}

// ------------------------------------------------------------------ سرورها

@Composable
private fun ServersHeader(state: NzUiState, onRefresh: () -> Unit) {
    val colors = NzTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "سرورها",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = if (state.servers.isEmpty()) "خالی"
                else "${PersianText.digits(state.servers.size)} سرور در " +
                    "${PersianText.digits(state.groups.size)} پوشه",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        NzIconButton(
            kind = NzIconKind.REFRESH,
            contentDescription = "به‌روزرسانی از مخزن",
            onClick = onRefresh,
        )
    }
}

@Composable
private fun GroupHeader(
    group: ServerGroup,
    testing: Boolean,
    testedDone: Int,
    testedTotal: Int,
    refreshing: Boolean,
    canTest: Boolean,
    canRefresh: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = NzTheme.colors
    NzCard(
        selected = !group.collapsed,
        onClick = onToggle,
        contentPadding = 14.dp,
    ) {
        // نام پوشه یک ردیف کامل دارد تا افزودن دکمهٔ رفرش باعث بریدن نام نشود.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (group.source?.kind == GroupSource.Kind.REPO) {
                Image(
                    painter = painterResource(R.drawable.nz_logo_mark),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                NzIcon(
                    kind = NzIconKind.FOLDER,
                    size = 18.dp,
                    tint = if (group.collapsed) colors.copper else colors.copperHighlight,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (group.flagCountryCode != null) {
                NzCountryFlag(group.flagCountryCode)
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (group.collapsed) colors.textPrimary else colors.copperHighlight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        refreshing -> "در حال به‌روزرسانی همین پوشه…"
                        testing -> "تست تک‌به‌تک… ${PersianText.digits(testedDone)} از ${PersianText.digits(testedTotal)}"
                        else -> buildString {
                            append(PersianText.digits(group.profiles.size))
                            append(" سرور")
                            if (group.best > 0) {
                                append(" • بهترین پینگ ")
                                append(PersianText.digits(group.best))
                            } else {
                                append(" • بدون پینگ")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        testing || refreshing -> colors.cyan
                        group.best <= 0 -> colors.redSoft
                        else -> colors.cyan
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NzIcon(
                kind = NzIconKind.CHEVRON_RIGHT,
                size = 16.dp,
                tint = if (group.collapsed) colors.textMuted else colors.copperBright,
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (group.collapsed) 180f else 90f
                },
            )
        }
        if (testing || refreshing) {
            Spacer(Modifier.height(8.dp))
            NzThinProgress(
                progress = if (testing && testedTotal > 0) testedDone.toFloat() / testedTotal else null,
                color = colors.cyan,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (group.source != null) {
                NzIconButton(
                    kind = NzIconKind.REFRESH,
                    contentDescription = "به‌روزرسانی فقط پوشهٔ ${group.name}",
                    onClick = onRefresh,
                    tint = colors.copper,
                    active = refreshing,
                    enabled = canRefresh && !testing,
                )
            }
            NzIconButton(
                kind = NzIconKind.BOLT,
                contentDescription = if (testing) "لغو تست پوشهٔ ${group.name}"
                    else "تست تک‌به‌تک سرورهای پوشهٔ ${group.name}",
                onClick = onTest,
                tint = colors.copper,
                active = testing,
                enabled = (canTest || testing) && !refreshing,
            )
            NzIconButton(
                kind = NzIconKind.TRASH,
                contentDescription = "حذف پوشهٔ ${group.name}",
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ServerRow(
    profile: Profile,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = NzTheme.colors
    NzCard(selected = selected, onClick = onClick, contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        NzIcon(NzIconKind.CHECK, size = 15.dp, tint = colors.copperBright)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (profile.flagCountryCode != null) {
                        NzCountryFlag(profile.flagCountryCode)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) colors.copperHighlight else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NzChip(text = profile.protocol.fa, accent = colors.copper)
                    NzChip(text = profile.securityFa, accent = colors.cyan)
                    if (profile.network != "tcp") {
                        NzChip(text = profile.networkFa, accent = colors.textMuted)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = profile.endpoint,
                    style = NzTheme.mono.value.ltr(),
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NzLatencyBadge(profile.latencyMs)
                Spacer(Modifier.height(6.dp))
                NzIconButton(
                    kind = NzIconKind.TRASH,
                    contentDescription = "حذف ${profile.displayName}",
                    onClick = onDelete,
                )
            }
        }
    }
}

// ------------------------------------------------------------------- پایان

@Composable
private fun DonationCard(onDonate: () -> Unit) {
    val colors = NzTheme.colors
    NzCard(contentPadding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // فنجان قهوه در دایرهٔ مسی کم‌رنگ
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.copper.copy(alpha = 0.14f))
                    .border(1.dp, colors.borderCopper, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                NzIcon(NzIconKind.COFFEE, size = 19.dp, tint = colors.copperBright)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = BrandConfig.DONATION_TITLE,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "رایگان و بدون تبلیغ — فقط اگر دوست داشتید",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NzButton(
                text = "یه قهوه؟",
                onClick = onDonate,
                style = NzButtonStyle.SECONDARY,
                fillWidth = false,
                height = 42.dp,
            )
        }
    }
}

@Composable
private fun FooterNote() {
    val colors = NzTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NzHairLine(Modifier.fillMaxWidth())
        Text(
            text = "نسخهٔ برنامه ${PersianText.digits(BrandConfig.VERSION_NAME)} — " +
                "\u2066${BrandConfig.CREDIT_NAME}\u2069",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = NzTheme.mono.label,
            color = colors.textMuted,
        )
    }
}

/** لوگوی تأییدشدهٔ NZVPN برای نوار بالای برنامه */
@Composable
private fun AppLogo(size: androidx.compose.ui.unit.Dp) {
    val colors = NzTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(9.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.borderStrong, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.nz_logo_mark),
            contentDescription = "نشان برنامه",
            modifier = Modifier.size(size),
        )
    }
}
