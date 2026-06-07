package com.redtrigger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.redtrigger.AppProfile
import com.redtrigger.BootReceiver
import com.redtrigger.NativeTgkController
import com.redtrigger.OverlayPickService
import com.redtrigger.ProfileStore
import com.redtrigger.ScreenOrientation
import com.redtrigger.TriggerPoint
import com.redtrigger.TriggerService
import kotlinx.coroutines.delay

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

private val AccentRed = Color(0xFFFF1744)
private val AccentBlue = Color(0xFF2B72FF)
private val ShoulderPink = Color(0xFFFFA8C8)
private val ShoulderBlue = Color(0xFF9FD5FF)
private val PanelSoft = Color(0xE51A1C27)
private val StrokeDim = Color(0x33FFFFFF)
private val TextDim = Color(0xFFB9BDCA)
private val StateGray = Color(0xFF9AA0AE)
private val StateAmber = Color(0xFFFFB74D)
private val StateBlue = Color(0xFF42A5F5)
private val StateGreen = Color(0xFF00C853)

@Composable
fun MainScreen() {
    MainContent()
}

@Composable
fun MainContent() {
    val context = LocalContext.current

    var profiles by remember { mutableStateOf(ProfileStore.loadProfiles(context)) }
    var masterEnabled by remember { mutableStateOf(ProfileStore.isMasterEnabled(context)) }
    var shizukuState by remember { mutableStateOf(NativeTgkController.shizukuState()) }
    var foreground by remember { mutableStateOf(TriggerService.lastForeground) }
    var nativeStatus by remember { mutableStateOf(NativeTgkController.lastStatus) }
    var autoBoot by remember { mutableStateOf(BootReceiver.isAutoEnableEnabled(context)) }
    var shizukuDetailsOpen by remember { mutableStateOf(false) }
    var editingPackage by remember { mutableStateOf("") }
    var shellActivePackages by remember { mutableStateOf(emptyList<String>()) }

    // 自测状态（实时底层 probe；不再依赖 app 收键/触摸）
    var testRunning by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf("未开始：点“开始监听”后按肩键，计数实时更新") }
    var probeStatus by remember { mutableStateOf("result=idle\nleft=0\nright=0\ndevices=") }
    var showTouches by remember { mutableStateOf(false) }
    var pointerLocation by remember { mutableStateOf(false) }

    val shizukuUsable = shizukuState in listOf(
        NativeTgkController.ShizukuState.AUTHORIZED,
        NativeTgkController.ShizukuState.CONNECTING,
        NativeTgkController.ShizukuState.CONNECTED
    )

    fun reloadProfiles() {
        profiles = ProfileStore.loadProfiles(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            shizukuState = NativeTgkController.shizukuState()
            masterEnabled = ProfileStore.isMasterEnabled(context)
            profiles = ProfileStore.loadProfiles(context)
            TriggerService.lastForeground.takeIf { it.isNotBlank() }?.let { foreground = it }
            nativeStatus = NativeTgkController.lastStatus
        }
    }

    // 自测运行时实时拉取底层 probe 计数，并同步外部（onPause）停止。
    LaunchedEffect(testRunning) {
        while (testRunning) {
            probeStatus = NativeTgkController.probeCounts()
            if (!NativeTgkController.selfTestRunning) testRunning = false
            delay(250)
        }
    }

    // 初始化/刷新两个调试开关的真实状态。
    LaunchedEffect(shizukuState) {
        if (shizukuUsable) {
            NativeTgkController.debugToggles { toggles ->
                showTouches = statusValue(toggles, "show_touches") == "1"
                pointerLocation = statusValue(toggles, "pointer_location") == "1"
            }
        }
    }

    // 离开界面时确保停止自测并释放。
    DisposableEffect(Unit) {
        onDispose {
            if (NativeTgkController.selfTestRunning) NativeTgkController.stopSelfTest()
        }
    }

    // 主动获取活跃/前台应用（shell getActivePackages），不依赖守护是否运行。
    LaunchedEffect(shizukuState) {
        while (true) {
            if (shizukuUsable) {
                NativeTgkController.refreshActivePackages { packages ->
                    shellActivePackages = packages
                    packages.firstOrNull()?.takeIf { it.isNotBlank() }?.let { foreground = it }
                }
            }
            delay(3500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF070810), Color(0xFF10131D), Color(0xFF0B080D))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderBar(shizukuState = shizukuState, serviceRunning = TriggerService.isRunning)

            ShizukuStatusCard(
                state = shizukuState,
                detailsOpen = shizukuDetailsOpen,
                onOpenShizuku = { openShizuku(context) },
                onRequestPermission = { NativeTgkController.requestPermission() },
                onConnect = { NativeTgkController.connect() },
                onToggleDetails = { shizukuDetailsOpen = !shizukuDetailsOpen }
            )

            AnimatedVisibility(visible = shizukuDetailsOpen) {
                ShizukuFlowPanel()
            }

            MasterSwitchCard(
                masterEnabled = masterEnabled,
                shizukuUsable = shizukuUsable,
                shizukuState = shizukuState,
                profileCount = profiles.count { it.enabled },
                onToggle = { checked ->
                    val ok = if (checked) ProfileStore.enableTriggers(context)
                    else ProfileStore.disableTriggers(context)
                    masterEnabled = ok && checked
                    Toast.makeText(
                        context,
                        if (masterEnabled) "守护已启动" else "守护已停止",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            ProfilesCard(
                context = context,
                profiles = profiles,
                foreground = foreground,
                activePackages = shellActivePackages,
                editingPackage = editingPackage,
                onAdd = { pkg ->
                    if (profiles.none { it.packageName == pkg }) {
                        val (l, r) = defaultPointsFor(ScreenOrientation.PORTRAIT, context)
                        ProfileStore.upsertProfile(
                            context,
                            AppProfile(
                                packageName = pkg,
                                label = ProfileStore.labelFor(context, pkg),
                                enabled = true,
                                orientation = ScreenOrientation.PORTRAIT,
                                left = l,
                                right = r,
                                mode = AppProfile.MODE_SINGLE,
                                rapidFire = FREQ_MID_VALUE,
                                leftEnabled = true,
                                rightEnabled = true
                            )
                        )
                        reloadProfiles()
                    }
                    editingPackage = pkg
                },
                onToggleProfile = { pkg, en ->
                    ProfileStore.setProfileEnabled(context, pkg, en)
                    reloadProfiles()
                },
                onEditToggle = { pkg -> editingPackage = if (editingPackage == pkg) "" else pkg },
                onChangeProfile = { updated ->
                    ProfileStore.upsertProfile(context, updated)
                    reloadProfiles()
                },
                onDelete = { pkg ->
                    ProfileStore.removeProfile(context, pkg)
                    if (editingPackage == pkg) editingPackage = ""
                    reloadProfiles()
                },
                onOpen = { pkg -> openPackage(context, pkg) },
                onPickOverlay = { profile -> startOverlayPicker(context, profile) }
            )

            SelfTestCard(
                shizukuUsable = shizukuUsable,
                running = testRunning,
                probeStatus = probeStatus,
                testStatus = testStatus,
                showTouches = showTouches,
                pointerLocation = pointerLocation,
                onToggleShowTouches = { on ->
                    showTouches = on
                    NativeTgkController.setShowTouches(on)
                },
                onTogglePointer = { on ->
                    pointerLocation = on
                    NativeTgkController.setPointerLocation(on)
                },
                onStart = {
                    if (!shizukuUsable) {
                        testStatus = "Shizuku 未就绪，先完成授权和连接"
                    } else {
                        probeStatus = "result=sampling\nleft=0\nright=0\ndevices="
                        testRunning = true
                        NativeTgkController.startSelfTest(selfTestProfile(context))
                        testStatus = "监听中：按左右肩键，计数实时更新；完成点“停止并恢复”"
                    }
                },
                onStop = {
                    NativeTgkController.stopSelfTest()
                    testRunning = false
                    testStatus = "已停止并释放（releaseTgk），未改动任何应用配置"
                }
            )

            StatusCard(
                shizukuState = shizukuState,
                controllerState = NativeTgkController.state,
                foreground = foreground,
                serviceRunning = TriggerService.isRunning,
                nativeStatus = nativeStatus,
                probeStatus = probeStatus,
                autoBoot = autoBoot,
                onAutoBoot = { BootReceiver.setAutoEnable(context, it); autoBoot = it },
                onRefresh = {
                    if (NativeTgkController.state == NativeTgkController.State.CONNECTED) {
                        nativeStatus = NativeTgkController.refreshStatus()
                    } else {
                        NativeTgkController.connect { nativeStatus = NativeTgkController.refreshStatus() }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// ----------------------------------------------------------------------------
// Master switch
// ----------------------------------------------------------------------------

@Composable
private fun MasterSwitchCard(
    masterEnabled: Boolean,
    shizukuUsable: Boolean,
    shizukuState: NativeTgkController.ShizukuState,
    profileCount: Int,
    onToggle: (Boolean) -> Unit
) {
    PanelSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("肩键守护", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = when {
                        shizukuState == NativeTgkController.ShizukuState.NOT_RUNNING -> "Shizuku 未启动，先启动 Shizuku"
                        shizukuState == NativeTgkController.ShizukuState.UNAUTHORIZED -> "Shizuku 已启动，先授权此应用"
                        masterEnabled -> "运行中：打开已配置应用即自动启用肩键（$profileCount 个已启用）"
                        else -> "开启后，打开任一已配置应用会自动套用其肩键"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = masterEnabled,
                enabled = shizukuUsable,
                colors = redSwitchColors(),
                onCheckedChange = onToggle
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Per-app profiles
// ----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilesCard(
    context: Context,
    profiles: List<AppProfile>,
    foreground: String,
    activePackages: List<String>,
    editingPackage: String,
    onAdd: (String) -> Unit,
    onToggleProfile: (String, Boolean) -> Unit,
    onEditToggle: (String) -> Unit,
    onChangeProfile: (AppProfile) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onPickOverlay: (AppProfile) -> Unit
) {
    PanelSurface {
        Text("已配置应用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "每个应用一份独立肩键配置；前台切到它就自动套用。",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )

        if (profiles.isEmpty()) {
            Text(
                text = "还没有配置。下方选择一个应用开始。",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        profiles.forEach { profile ->
            ProfileRow(
                profile = profile,
                isForeground = profile.packageName == foreground,
                expanded = editingPackage == profile.packageName,
                onToggle = { onToggleProfile(profile.packageName, it) },
                onEditToggle = { onEditToggle(profile.packageName) },
                onChange = onChangeProfile,
                onDelete = { onDelete(profile.packageName) },
                onOpen = { onOpen(profile.packageName) },
                onPickOverlay = { onPickOverlay(profile) }
            )
        }

        Divider(color = StrokeDim)
        AddProfileMenu(
            context = context,
            existing = profiles.map { it.packageName }.toSet(),
            activePackages = activePackages,
            onAdd = onAdd
        )
    }
}

@Composable
private fun ProfileRow(
    profile: AppProfile,
    isForeground: Boolean,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onEditToggle: () -> Unit,
    onChange: (AppProfile) -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onPickOverlay: () -> Unit
) {
    val accent = if (profile.enabled) AccentRed else StateGray
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            profile.label,
                            modifier = Modifier.weight(1f, fill = false),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isForeground) {
                            Text("前台", color = StateGreen, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        profile.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${orientationLabel(profile.orientation)} · ${modeLabel(profile)} · " +
                            "L(${profile.left.x},${profile.left.y}) R(${profile.right.x},${profile.right.y})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(checked = profile.enabled, colors = redSwitchColors(), onCheckedChange = onToggle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onEditToggle) { Text(if (expanded) "收起" else "编辑") }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onOpen) { Text("打开") }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onDelete) { Text("删除") }
            }
            AnimatedVisibility(visible = expanded) {
                ProfileEditor(profile = profile, onChange = onChange, onPickOverlay = onPickOverlay)
            }
        }
    }
}

@Composable
private fun ProfileEditor(profile: AppProfile, onChange: (AppProfile) -> Unit, onPickOverlay: () -> Unit) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Divider(color = StrokeDim)

        EditorSummary(profile)

        EditorSection(
            index = "1",
            title = "基础",
            subtitle = "方向只影响坐标系；多数游戏保持横屏。"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceTile(
                    title = "竖屏",
                    subtitle = "应用/竖屏游戏",
                    selected = profile.orientation == ScreenOrientation.PORTRAIT,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val (l, r) = defaultPointsFor(ScreenOrientation.PORTRAIT, ctx)
                        onChange(profile.copy(orientation = ScreenOrientation.PORTRAIT, left = l, right = r))
                    }
                )
                ChoiceTile(
                    title = "横屏",
                    subtitle = "游戏常用",
                    selected = profile.orientation == ScreenOrientation.LANDSCAPE,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val (l, r) = defaultPointsFor(ScreenOrientation.LANDSCAPE, ctx)
                        onChange(profile.copy(orientation = ScreenOrientation.LANDSCAPE, left = l, right = r))
                    }
                )
            }
        }

        EditorSection(
            index = "2",
            title = "触发",
            subtitle = "单点跟随按压；连发按住重复触发。"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceTile(
                    title = "单点",
                    subtitle = "轻按/长按同步",
                    selected = profile.mode == AppProfile.MODE_SINGLE,
                    modifier = Modifier.weight(1f),
                    onClick = { onChange(profile.copy(mode = AppProfile.MODE_SINGLE)) }
                )
                ChoiceTile(
                    title = "连发",
                    subtitle = "${profile.rapidFire.coerceIn(FREQ_MIN, FREQ_MAX)}/s",
                    selected = profile.mode == AppProfile.MODE_RAPID,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onChange(
                            profile.copy(
                                mode = AppProfile.MODE_RAPID,
                                rapidFire = profile.rapidFire.coerceIn(FREQ_MIN, FREQ_MAX)
                            )
                        )
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShoulderToggle(
                    label = "L",
                    title = "左肩键",
                    enabled = profile.leftEnabled,
                    color = ShoulderPink,
                    modifier = Modifier.weight(1f),
                    onClick = { onChange(profile.copy(leftEnabled = !profile.leftEnabled)) }
                )
                ShoulderToggle(
                    label = "R",
                    title = "右肩键",
                    enabled = profile.rightEnabled,
                    color = ShoulderBlue,
                    modifier = Modifier.weight(1f),
                    onClick = { onChange(profile.copy(rightEnabled = !profile.rightEnabled)) }
                )
            }
            if (profile.mode == AppProfile.MODE_RAPID) {
                FrequencyControl(profile.rapidFire) { onChange(profile.copy(rapidFire = it)) }
            }
        }

        EditorSection(
            index = "3",
            title = "坐标",
            subtitle = "${orientationLabel(profile.orientation)}像素坐标；取点页面后续再做。"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("左 X", profile.left.x, Modifier.weight(1f)) { onChange(profile.copy(left = profile.left.copy(x = it))) }
                NumberField("左 Y", profile.left.y, Modifier.weight(1f)) { onChange(profile.copy(left = profile.left.copy(y = it))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("右 X", profile.right.x, Modifier.weight(1f)) { onChange(profile.copy(right = profile.right.copy(x = it))) }
                NumberField("右 Y", profile.right.y, Modifier.weight(1f)) { onChange(profile.copy(right = profile.right.copy(y = it))) }
            }
            OutlinedButton(onClick = {
                val (l, r) = defaultPointsFor(profile.orientation, ctx)
                onChange(profile.copy(left = l, right = r))
            }) { Text("重置为默认坐标") }
            Button(
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = StateBlue),
                onClick = onPickOverlay
            ) { Text("在目标应用上悬浮取点") }
            ToggleLine("在目标应用中显示 L/R 标记", profile.showOverlayMarkers) { checked ->
                if (checked && !Settings.canDrawOverlays(ctx)) {
                    Toast.makeText(ctx, "先授予悬浮窗权限，才能显示 L/R 标记", Toast.LENGTH_SHORT).show()
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                } else {
                    onChange(profile.copy(showOverlayMarkers = checked))
                }
            }
            Text(
                text = "开启后，进入该应用时会保留半透明 L/R 标记；标记不接收触摸，不影响应用操作。",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim
            )
        }

        if (profile.mode == AppProfile.MODE_RAPID) {
            Text(
                text = "当前连发：按住约 ${profile.rapidFire.coerceIn(FREQ_MIN, FREQ_MAX)}/s，松开停止。",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim
            )
        }
    }
}

@Composable
private fun EditorSummary(profile: AppProfile) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniStatusPill(orientationLabel(profile.orientation), StateBlue)
        MiniStatusPill(modeLabel(profile), if (profile.mode == AppProfile.MODE_RAPID) FreqHigh else StateGreen)
        MiniStatusPill(shoulderStateLabel(profile), if (profile.leftEnabled || profile.rightEnabled) ShoulderPink else StateGray)
    }
}

@Composable
private fun EditorSection(
    index: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(index, AccentRed)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.045f))
                .border(1.dp, StrokeDim, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun ChoiceTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = if (selected) AccentRed else StateGray
    Surface(
        modifier = modifier.height(72.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = if (selected) 0.16f else 0.07f),
        border = BorderStroke(1.dp, color.copy(alpha = if (selected) 0.70f else 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(color)
                )
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                subtitle,
                color = TextDim,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShoulderToggle(
    label: String,
    title: String,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val activeColor = if (enabled) color else StateGray
    Surface(
        modifier = modifier.height(64.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = activeColor.copy(alpha = if (enabled) 0.16f else 0.07f),
        border = BorderStroke(1.dp, activeColor.copy(alpha = if (enabled) 0.70f else 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIcon(label, activeColor)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (enabled) "已启用" else "已禁用", color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProfileMenu(
    context: Context,
    existing: Set<String>,
    activePackages: List<String>,
    onAdd: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val candidates = remember(existing, activePackages, showAll) {
        addCandidates(context, existing, activePackages, showAll)
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            readOnly = true,
            value = "+ 添加应用",
            onValueChange = {},
            label = { Text("为某个应用新建配置") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            candidates.forEach { item ->
                DropdownMenuItem(
                    enabled = !item.isHeader,
                    text = {
                        when {
                            item.isHeader -> Text(
                                item.label,
                                color = TextDim,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                            item.pkg == SHOW_ALL -> Text(item.label, color = StateBlue)
                            else -> Column {
                                Text(item.label)
                                Text(item.pkg, style = MaterialTheme.typography.bodySmall, color = TextDim)
                            }
                        }
                    },
                    onClick = {
                        when {
                            item.isHeader -> {}
                            item.pkg == SHOW_ALL -> showAll = true
                            else -> {
                                onAdd(item.pkg)
                                expanded = false
                            }
                        }
                    }
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Self test (zero-pollution: probe events + verify real touch on THIS app)
// ----------------------------------------------------------------------------

@Composable
private fun SelfTestCard(
    shizukuUsable: Boolean,
    running: Boolean,
    probeStatus: String,
    testStatus: String,
    showTouches: Boolean,
    pointerLocation: Boolean,
    onToggleShowTouches: (Boolean) -> Unit,
    onTogglePointer: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val left = statusValue(probeStatus, "left")?.toIntOrNull() ?: 0
    val right = statusValue(probeStatus, "right")?.toIntOrNull() ?: 0
    PanelSurface {
        Text("自测：验证肩键事件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "按肩键看是否被系统识别（底层 F7/F8，实时计数）。映射在屏幕外，不会误触本应用；配合下方调试叠加可直接看到注入的触摸点。",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TestCounterBox("左肩键 F7", left, ShoulderPink, Modifier.weight(1f))
            TestCounterBox("右肩键 F8", right, ShoulderBlue, Modifier.weight(1f))
        }
        StatusTable(
            rows = listOf(
                "采样状态" to probeSummary(probeStatus),
                "收到键 L / R" to "$left / $right",
                "TGK 输入设备" to statusValue(probeStatus, "devices").orEmpty().ifBlank { "-" }
            )
        )
        Text(testStatus, style = MaterialTheme.typography.bodySmall, color = TextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                enabled = shizukuUsable && !running,
                onClick = onStart
            ) { Text("开始监听") }
            OutlinedButton(enabled = running, onClick = onStop) { Text("停止并恢复") }
        }

        Divider(color = StrokeDim)
        Text("屏幕调试叠加", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        DebugToggleRow(
            kind = DebugIconKind.TOUCH,
            title = "显示点按反馈",
            subtitle = "屏幕上画出每次触摸落点（show_touches）",
            checked = showTouches,
            enabled = shizukuUsable,
            onToggle = onToggleShowTouches
        )
        DebugToggleRow(
            kind = DebugIconKind.POINTER,
            title = "指针位置",
            subtitle = "顶部显示坐标 + 十字准线（pointer_location）",
            checked = pointerLocation,
            enabled = shizukuUsable,
            onToggle = onTogglePointer
        )
        Text(
            text = "实时反馈、无需等待；按“停止并恢复”或切到别的界面会自动停止并 releaseTgk。F7/F8 有计数即说明肩键事件可被识别。",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
    }
}

private enum class DebugIconKind { TOUCH, POINTER }

@Composable
private fun DebugToggleRow(
    kind: DebugIconKind,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val accent = if (checked) StateGreen else StateGray
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DebugIcon(kind, accent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
            Switch(checked = checked, enabled = enabled, colors = redSwitchColors(), onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun DebugIcon(kind: DebugIconKind, color: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            when (kind) {
                DebugIconKind.TOUCH -> {
                    drawCircle(color = color, radius = size.minDimension / 2f, style = Stroke(width = 2.5f))
                    drawCircle(color = color, radius = size.minDimension / 6f)
                }
                DebugIconKind.POINTER -> {
                    val w = size.width
                    val h = size.height
                    drawLine(color, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 2.5f)
                    drawLine(color, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 2.5f)
                }
            }
        }
    }
}

@Composable
private fun TestCounterBox(label: String, hits: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$hits", color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
    }
}

// ----------------------------------------------------------------------------
// Status card (layered: shizuku / userservice / vendor / events / hit)
// ----------------------------------------------------------------------------

@Composable
private fun StatusCard(
    shizukuState: NativeTgkController.ShizukuState,
    controllerState: NativeTgkController.State,
    foreground: String,
    serviceRunning: Boolean,
    nativeStatus: String,
    probeStatus: String,
    autoBoot: Boolean,
    onAutoBoot: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    val left = statusValue(probeStatus, "left")?.toIntOrNull() ?: 0
    val right = statusValue(probeStatus, "right")?.toIntOrNull() ?: 0
    PanelSurface {
        Text("运行状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        StatusText("Shizuku", shizukuPillText(shizukuState))
        StatusText("后端 UserService", controllerStateText(controllerState))
        StatusText("Vendor TGK 开关", tgkSummary(nativeStatus))
        StatusText("肩键事件 F7/F8", if (left + right > 0) "可见 L=$left R=$right" else "未捕获")
        StatusText("守护服务", if (serviceRunning) "运行中" else "未运行")
        StatusText("前台包名", foreground.ifBlank { "-" })
        Divider(color = StrokeDim)
        ToggleLine("开机后自动恢复守护", autoBoot, onAutoBoot)
        OutlinedButton(onClick = onRefresh) { Text("刷新后端状态") }
    }
}

// ----------------------------------------------------------------------------
// Shizuku card + helpers (preserved)
// ----------------------------------------------------------------------------

@Composable
private fun HeaderBar(shizukuState: NativeTgkController.ShizukuState, serviceRunning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("红魔肩键", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("per-app shoulder trigger", style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ShizukuStatusPill(shizukuState)
            StatusPill(if (serviceRunning) "守护中" else "待机", serviceRunning)
        }
    }
}

@Composable
private fun ShizukuStatusCard(
    state: NativeTgkController.ShizukuState,
    detailsOpen: Boolean,
    onOpenShizuku: () -> Unit,
    onRequestPermission: () -> Unit,
    onConnect: () -> Unit,
    onToggleDetails: () -> Unit
) {
    val color = shizukuColor(state)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusIcon(shizukuIcon(state), color)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(shizukuTitle(state), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(shizukuSuggestion(state), style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
                HelpToggleIcon(active = detailsOpen, color = color, onClick = onToggleDetails)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    NativeTgkController.ShizukuState.NOT_RUNNING ->
                        Button(colors = ButtonDefaults.buttonColors(containerColor = color), onClick = onOpenShizuku) { Text("打开 Shizuku") }
                    NativeTgkController.ShizukuState.UNAUTHORIZED -> {
                        Button(colors = ButtonDefaults.buttonColors(containerColor = color), onClick = onRequestPermission) { Text("授权") }
                        OutlinedButton(onClick = onOpenShizuku) { Text("打开 Shizuku") }
                    }
                    NativeTgkController.ShizukuState.AUTHORIZED ->
                        Button(colors = ButtonDefaults.buttonColors(containerColor = color), onClick = onConnect) { Text("连接后端") }
                    NativeTgkController.ShizukuState.CONNECTING ->
                        OutlinedButton(enabled = false, onClick = {}) { Text("连接中") }
                    NativeTgkController.ShizukuState.CONNECTED ->
                        OutlinedButton(onClick = onConnect) { Text("重新检查") }
                }
            }
        }
    }
}

@Composable
private fun HelpToggleIcon(active: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (active) color.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, color.copy(alpha = 0.72f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("?", color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ShizukuFlowPanel() {
    PanelSurface {
        Text("Shizuku 接入流程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowStep("1", "未启动", "Shizuku server 不在线，需从 Shizuku App 或 ADB 启动。", StateGray)
        FlowStep("2", "未授权", "server 已在线，但本 app 还没有 Shizuku 权限。", StateAmber)
        FlowStep("3", "已授权", "权限已通过，可绑定 shell 权限后端服务。", StateBlue)
        FlowStep("4", "已连接", "UserService 已绑定，之后才能读写 TGK 状态。", StateGreen)
    }
}

@Composable
private fun FlowStep(index: String, title: String, body: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        StatusIcon(index, color)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}

@Composable
private fun StatusIcon(text: String, color: Color) {
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)).border(1.dp, color.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ShizukuStatusPill(state: NativeTgkController.ShizukuState) {
    val color = shizukuColor(state)
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(alpha = 0.16f), border = BorderStroke(1.dp, color.copy(alpha = 0.7f))) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
            Text(shizukuPillText(state), color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun shizukuPillText(state: NativeTgkController.ShizukuState): String = when (state) {
    NativeTgkController.ShizukuState.NOT_RUNNING -> "未启动"
    NativeTgkController.ShizukuState.UNAUTHORIZED -> "未授权"
    NativeTgkController.ShizukuState.AUTHORIZED -> "已授权"
    NativeTgkController.ShizukuState.CONNECTING -> "连接中"
    NativeTgkController.ShizukuState.CONNECTED -> "已连接"
}

private fun shizukuColor(state: NativeTgkController.ShizukuState): Color = when (state) {
    NativeTgkController.ShizukuState.NOT_RUNNING -> StateGray
    NativeTgkController.ShizukuState.UNAUTHORIZED -> StateAmber
    NativeTgkController.ShizukuState.AUTHORIZED -> StateBlue
    NativeTgkController.ShizukuState.CONNECTING -> AccentRed
    NativeTgkController.ShizukuState.CONNECTED -> StateGreen
}

private fun shizukuIcon(state: NativeTgkController.ShizukuState): String = when (state) {
    NativeTgkController.ShizukuState.NOT_RUNNING -> "!"
    NativeTgkController.ShizukuState.UNAUTHORIZED -> "?"
    NativeTgkController.ShizukuState.AUTHORIZED -> "i"
    NativeTgkController.ShizukuState.CONNECTING -> "·"
    NativeTgkController.ShizukuState.CONNECTED -> "OK"
}

private fun shizukuTitle(state: NativeTgkController.ShizukuState): String = when (state) {
    NativeTgkController.ShizukuState.NOT_RUNNING -> "Shizuku 未启动"
    NativeTgkController.ShizukuState.UNAUTHORIZED -> "等待授权"
    NativeTgkController.ShizukuState.AUTHORIZED -> "已授权，尚未连接后端"
    NativeTgkController.ShizukuState.CONNECTING -> "正在连接后端"
    NativeTgkController.ShizukuState.CONNECTED -> "后端已连接"
}

private fun shizukuSuggestion(state: NativeTgkController.ShizukuState): String = when (state) {
    NativeTgkController.ShizukuState.NOT_RUNNING -> "先启动 Shizuku server；ADB 启动后此处会自动刷新。"
    NativeTgkController.ShizukuState.UNAUTHORIZED -> "点授权，让本 app 使用 Shizuku API。"
    NativeTgkController.ShizukuState.AUTHORIZED -> "点连接后端，验证 UserService 能否绑定。"
    NativeTgkController.ShizukuState.CONNECTING -> "正在等待 Shizuku 返回连接结果。"
    NativeTgkController.ShizukuState.CONNECTED -> "通道正常，可启动守护或读取 TGK 状态。"
}

// ----------------------------------------------------------------------------
// Small shared composables / helpers
// ----------------------------------------------------------------------------

@Composable
private fun PanelSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = PanelSoft,
        border = BorderStroke(1.dp, StrokeDim)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun StatusTable(rows: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, StrokeDim, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TextDim, style = MaterialTheme.typography.bodySmall)
                Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, ok: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (ok) AccentRed.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (ok) AccentRed.copy(alpha = 0.65f) else StrokeDim)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = if (ok) Color.White else TextDim, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniStatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.56f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Switch(checked = checked, colors = redSwitchColors(), onCheckedChange = onChange)
    }
}

@Composable
private fun StatusText(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(0.42f).padding(end = 12.dp), color = TextDim)
        Text(
            value,
            modifier = Modifier.weight(0.58f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        modifier = modifier,
        value = text,
        onValueChange = {
            text = it.filter { ch -> ch.isDigit() || ch == '-' }
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

private const val FREQ_MIN = 1
private const val FREQ_MAX = 30
private const val FREQ_LOW_VALUE = 6
private const val FREQ_MID_VALUE = 12
private const val FREQ_HIGH_VALUE = 20
private const val FREQ_LOW_POS = 0.25f
private const val FREQ_MID_POS = 0.60f
private const val FREQ_HIGH_POS = 0.84f
private const val FREQ_MAX_POS = 0.96f
private val FreqLow = Color(0xFF2B72FF)
private val FreqHigh = Color(0xFFFF8A00)

/** 频率值 → 颜色：蓝(低)→橙(高)，按 [FREQ_MIN,FREQ_MAX] 过渡。 */
private fun freqColor(value: Int): Color {
    val f = ((value - FREQ_MIN).toFloat() / (FREQ_MAX - FREQ_MIN)).coerceIn(0f, 1f)
    return androidx.compose.ui.graphics.lerp(FreqLow, FreqHigh, f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyControl(value: Int, onChange: (Int) -> Unit) {
    val safeValue = value.coerceIn(FREQ_MIN, FREQ_MAX)
    val color = freqColor(safeValue)
    val isPreset = safeValue in listOf(FREQ_LOW_VALUE, FREQ_MID_VALUE, FREQ_HIGH_VALUE)
    var customOpen by remember { mutableStateOf(!isPreset) }
    LaunchedEffect(isPreset) {
        if (!isPreset) customOpen = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("连发频率", fontWeight = FontWeight.SemiBold)
                Text(
                    "低 / 中 / 高优先，其他数值自定义。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = color.copy(alpha = 0.20f),
                border = BorderStroke(1.dp, color)
            ) {
                Text(
                    "$safeValue/s",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        FrequencyRail(safeValue)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FreqChip("低", FREQ_LOW_VALUE, safeValue, Modifier.weight(1f), onChange)
            FreqChip("中", FREQ_MID_VALUE, safeValue, Modifier.weight(1f), onChange)
            FreqChip("高", FREQ_HIGH_VALUE, safeValue, Modifier.weight(1f), onChange)
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { customOpen = !customOpen }
        ) {
            Text(if (customOpen) "收起自定义" else "自定义")
        }
        AnimatedVisibility(visible = customOpen) {
            NumberField("自定义（$FREQ_MIN-$FREQ_MAX/s）", value, Modifier.fillMaxWidth()) {
                onChange(it.coerceIn(FREQ_MIN, FREQ_MAX))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreqChip(label: String, freq: Int, current: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    FilterChip(
        modifier = modifier,
        selected = current == freq,
        onClick = { onChange(freq) },
        label = { Text(label, maxLines = 1) }
    )
}

@Composable
private fun FrequencyRail(value: Int) {
    val color = freqColor(value)
    val marker = frequencyPosition(value)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
            val y = size.height / 2f
            val start = size.width * 0.08f
            val end = size.width * 0.92f
            fun xAt(pos: Float): Float = start + (end - start) * pos
            drawLine(
                color = Color.White.copy(alpha = 0.16f),
                start = Offset(start, y),
                end = Offset(end, y),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color.copy(alpha = 0.85f),
                start = Offset(start, y),
                end = Offset(xAt(marker), y),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
            listOf(FREQ_LOW_POS, FREQ_MID_POS, FREQ_HIGH_POS).forEach { pos ->
                drawCircle(Color.White.copy(alpha = 0.42f), radius = 5f, center = Offset(xAt(pos), y))
            }
            drawCircle(color, radius = 10f, center = Offset(xAt(marker), y))
            drawCircle(Color.White.copy(alpha = 0.86f), radius = 4f, center = Offset(xAt(marker), y))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("低", style = MaterialTheme.typography.labelSmall, color = TextDim)
            Text("中", style = MaterialTheme.typography.labelSmall, color = TextDim)
            Text("高", style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
    }
}

private fun frequencyPosition(value: Int): Float {
    val safe = value.coerceIn(FREQ_MIN, FREQ_MAX)
    return when {
        safe <= FREQ_LOW_VALUE -> interpolate(
            safe,
            FREQ_MIN,
            FREQ_LOW_VALUE,
            0.08f,
            FREQ_LOW_POS
        )
        safe <= FREQ_MID_VALUE -> interpolate(
            safe,
            FREQ_LOW_VALUE,
            FREQ_MID_VALUE,
            FREQ_LOW_POS,
            FREQ_MID_POS
        )
        safe <= FREQ_HIGH_VALUE -> interpolate(
            safe,
            FREQ_MID_VALUE,
            FREQ_HIGH_VALUE,
            FREQ_MID_POS,
            FREQ_HIGH_POS
        )
        else -> interpolate(
            safe,
            FREQ_HIGH_VALUE,
            FREQ_MAX,
            FREQ_HIGH_POS,
            FREQ_MAX_POS
        )
    }.coerceIn(0.08f, FREQ_MAX_POS)
}

private fun interpolate(value: Int, from: Int, to: Int, start: Float, end: Float): Float {
    if (from == to) return end
    val t = ((value - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    return start + (end - start) * t
}

@Composable
private fun redSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = AccentRed,
    checkedBorderColor = AccentRed,
    uncheckedThumbColor = TextDim,
    uncheckedTrackColor = Color(0xFF2B2E3A),
    uncheckedBorderColor = StrokeDim
)

private fun orientationLabel(o: ScreenOrientation): String =
    if (o == ScreenOrientation.LANDSCAPE) "横屏" else "竖屏"

private fun modeLabel(profile: AppProfile): String = when (profile.mode) {
    AppProfile.MODE_SINGLE -> "单点"
    AppProfile.MODE_RAPID -> "连发 ${profile.rapidFire.coerceIn(FREQ_MIN, FREQ_MAX)}/s"
    else -> "模式 ${profile.mode}"
}

private fun shoulderStateLabel(profile: AppProfile): String = when {
    profile.leftEnabled && profile.rightEnabled -> "L/R 开"
    profile.leftEnabled -> "仅 L"
    profile.rightEnabled -> "仅 R"
    else -> "L/R 关"
}

private fun controllerStateText(state: NativeTgkController.State): String = when (state) {
    NativeTgkController.State.STOPPED -> "未连接"
    NativeTgkController.State.CONNECTING -> "连接中"
    NativeTgkController.State.CONNECTED -> "已连接"
}

private fun tgkSummary(status: String): String {
    if (status.isBlank()) return "未读取"
    val global = statusValue(status, "global")
    val left = statusValue(status, "left")
    val right = statusValue(status, "right")
    return when {
        listOf(global, left, right).any { it?.startsWith("error") == true } -> "读取失败"
        global == "true" && left == "true" && right == "true" -> "已开启"
        global == "false" && left == "false" && right == "false" -> "未开启"
        global == null && left == null && right == null -> "未读取"
        else -> "部分开启"
    }
}

private fun probeSummary(status: String): String {
    val result = statusValue(status, "result") ?: "not_started"
    val left = statusValue(status, "left") ?: "0"
    val right = statusValue(status, "right") ?: "0"
    return when (result) {
        "sampling" -> "采样中"
        "not_started" -> "未采样"
        "no_tgk_input_device" -> "未找到 TGK 输入设备"
        "event_seen" -> "L=$left / R=$right"
        "no_event" -> "未收到事件"
        "raw_seen" -> "有设备输出，未见按下"
        "not_connected" -> "后端未连接"
        "error" -> "采样失败"
        else -> "L=$left / R=$right"
    }
}

private fun shoulderProbeAdvice(status: String): String {
    val result = statusValue(status, "result") ?: "not_started"
    val left = statusValue(status, "left")?.toIntOrNull() ?: 0
    val right = statusValue(status, "right")?.toIntOrNull() ?: 0
    return when {
        left > 0 || right > 0 -> "检测到肩键事件：L=$left / R=$right"
        result == "no_event" -> "采样窗口内没收到肩键事件"
        result == "no_tgk_input_device" -> "未找到 nubia_tgk 输入设备"
        result == "raw_seen" -> "采到设备输出但无按下事件，请再按 L/R"
        result == "not_connected" -> "后端未连接"
        result == "error" -> "采样失败，查看日志"
        else -> "采样完成"
    }
}

private fun statusValue(status: String, key: String): String? =
    status.lineSequence().firstOrNull { it.startsWith("$key=") }?.substringAfter("=")

// ----------------------------------------------------------------------------
// Non-composable helpers
// ----------------------------------------------------------------------------

private const val SHOW_ALL = "__show_all__"

private fun physicalEdges(context: Context): Pair<Int, Int> {
    val dm = context.resources.displayMetrics
    val a = dm.widthPixels
    val b = dm.heightPixels
    return maxOf(a, b) to minOf(a, b)
}

/** 给新 profile 一个一定落在屏内的初始坐标（中心偏右），用户再按需微调。 */
/**
 * 默认坐标（按屏幕方向给合理初始位置，专门取点页面以后做）：
 * 竖屏：左右键水平分布、垂直居中 —— L(W/4,H/2) R(3W/4,H/2)，W=短边 H=长边。
 * 横屏：左右键垂直分布、水平居中 —— L(W/2,H/4) R(W/2,3H/4)，W=长边 H=短边。
 */
private fun defaultPointsFor(orientation: ScreenOrientation, context: Context): Pair<TriggerPoint, TriggerPoint> {
    val (longEdge, shortEdge) = physicalEdges(context)
    return if (orientation == ScreenOrientation.LANDSCAPE) {
        val w = longEdge
        val h = shortEdge
        TriggerPoint(w / 2, h / 4) to TriggerPoint(w / 2, h * 3 / 4)
    } else {
        val w = shortEdge
        val h = longEdge
        TriggerPoint(w / 4, h / 2) to TriggerPoint(w * 3 / 4, h / 2)
    }
}

/** 自测用的临时 profile：本 app（竖屏），左右映射到窗口内不同高度的合法点。 */
private fun selfTestProfile(context: Context): AppProfile {
    val (longEdge, shortEdge) = physicalEdges(context)
    // 映射到屏幕外（底边之外），避免误触本应用 UI；靠 F7/F8 probe + show_touches 确认链路。
    val offScreenY = longEdge + 120
    val cx = shortEdge / 2
    return AppProfile(
        packageName = context.packageName,
        label = "自测",
        enabled = true,
        orientation = ScreenOrientation.PORTRAIT,
        left = TriggerPoint(cx - 100, offScreenY),
        right = TriggerPoint(cx + 100, offScreenY),
        mode = AppProfile.MODE_SINGLE,
        rapidFire = 1,
        leftEnabled = true,
        rightEnabled = true
    )
}

private data class AddItem(val label: String, val pkg: String, val isHeader: Boolean = false)

private fun addCandidates(
    context: Context,
    existing: Set<String>,
    activePackages: List<String>,
    showAll: Boolean
): List<AddItem> {
    val items = mutableListOf<AddItem>()
    val used = HashSet(existing)

    fun section(title: String, pkgs: List<String>) {
        val valid = pkgs.asSequence()
            .filter { it.isNotBlank() && it != "null" && it != context.packageName && it !in used }
            .distinct()
            .toList()
        if (valid.isEmpty()) return
        items.add(AddItem(title, "", isHeader = true))
        valid.forEach { pkg ->
            items.add(AddItem(ProfileStore.labelFor(context, pkg), pkg))
            used.add(pkg)
        }
    }

    section("活跃 / 前台应用", activePackages)
    section("最近应用", ProfileStore.recentTargets(context))
    if (showAll) {
        section("全部应用", loadLaunchableApps(context).map { it.first })
    } else {
        items.add(AddItem("从全部应用中选择…", SHOW_ALL))
    }
    return items
}

/** @return list of (packageName, label) */
private fun loadLaunchableApps(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName to (it.loadLabel(pm)?.toString().orEmpty().ifBlank { it.activityInfo.packageName }) }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
}

private fun openShizuku(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
    if (launch != null) {
        context.startActivity(launch)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
    }
}

private fun startOverlayPicker(context: Context, profile: AppProfile) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "先授予悬浮窗权限，再回到这里取点", Toast.LENGTH_SHORT).show()
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
        return
    }
    Toast.makeText(context, "拖动 L/R 后点悬浮条保存", Toast.LENGTH_SHORT).show()
    openPackage(context, profile.packageName)
    Handler(Looper.getMainLooper()).postDelayed({
        OverlayPickService.show(context.applicationContext, profile.packageName)
    }, 650L)
}

private fun openPackage(context: Context, packageName: String) {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    } else {
        Toast.makeText(context, "无法打开 $packageName", Toast.LENGTH_SHORT).show()
    }
}
