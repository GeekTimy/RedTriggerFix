package com.redtrigger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.redtrigger.AppProfile
import com.redtrigger.BootReceiver
import com.redtrigger.NativeTgkController
import com.redtrigger.ProfileStore
import com.redtrigger.ScreenOrientation
import com.redtrigger.TriggerInputMonitor
import com.redtrigger.TriggerPoint
import com.redtrigger.TriggerService
import kotlinx.coroutines.delay

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

private val AccentRed = Color(0xFFFF1744)
private val AccentBlue = Color(0xFF2B72FF)
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

    // 自测状态
    var testRunning by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf("尚未开始测试") }
    var leftHits by remember { mutableStateOf(0) }
    var rightHits by remember { mutableStateOf(0) }
    var touchHits by remember { mutableStateOf(0) }
    var lastTouch by remember { mutableStateOf(TriggerInputMonitor.lastTouchEvent) }
    var probeStatus by remember { mutableStateOf("result=not_started\nleft=0\nright=0") }

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
            foreground = TriggerService.lastForeground
            nativeStatus = NativeTgkController.lastStatus
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(180)
            leftHits = TriggerInputMonitor.leftHits
            rightHits = TriggerInputMonitor.rightHits
            touchHits = TriggerInputMonitor.touchHits
            lastTouch = TriggerInputMonitor.lastTouchEvent
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
                editingPackage = editingPackage,
                onAdd = { pkg ->
                    if (profiles.none { it.packageName == pkg }) {
                        val (l, r) = defaultPointsFor(ScreenOrientation.LANDSCAPE, context)
                        ProfileStore.upsertProfile(
                            context,
                            AppProfile(
                                packageName = pkg,
                                label = ProfileStore.labelFor(context, pkg),
                                enabled = true,
                                orientation = ScreenOrientation.LANDSCAPE,
                                left = l,
                                right = r,
                                mode = AppProfile.MODE_RAPID,
                                rapidFire = 10
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
                onOpen = { pkg -> openPackage(context, pkg) }
            )

            SelfTestCard(
                shizukuUsable = shizukuUsable,
                running = testRunning,
                leftHits = leftHits,
                rightHits = rightHits,
                touchHits = touchHits,
                lastTouch = lastTouch,
                probeStatus = probeStatus,
                testStatus = testStatus,
                onStart = {
                    if (!shizukuUsable) {
                        testStatus = "Shizuku 未就绪，先完成授权和连接"
                    } else {
                        TriggerInputMonitor.reset()
                        leftHits = 0; rightHits = 0; touchHits = 0
                        probeStatus = "result=sampling\nleft=0\nright=0"
                        testRunning = true
                        val self = selfTestProfile(context)
                        // 临时把肩键映射到本 app 窗口内合法坐标（竖屏中心区），测完会 releaseTgk 恢复。
                        NativeTgkController.enable(self, logResult = true)
                        testStatus = "已临时启用自测映射，6 秒内按左右肩键 L/R"
                        NativeTgkController.probeShoulderKeys(6000) { result ->
                            probeStatus = result
                            testStatus = shoulderProbeAdvice(result) + "；按完点“停止并恢复”"
                        }
                    }
                },
                onStop = {
                    NativeTgkController.releaseTgk()
                    testRunning = false
                    testStatus = "已停止并释放（releaseTgk），未改动任何应用的配置"
                }
            )

            StatusCard(
                shizukuState = shizukuState,
                controllerState = NativeTgkController.state,
                foreground = foreground,
                serviceRunning = TriggerService.isRunning,
                nativeStatus = nativeStatus,
                leftHits = leftHits,
                rightHits = rightHits,
                touchHits = touchHits,
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("肩键守护", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = when {
                        shizukuState == NativeTgkController.ShizukuState.NOT_RUNNING -> "Shizuku 未启动，先启动 Shizuku"
                        shizukuState == NativeTgkController.ShizukuState.UNAUTHORIZED -> "Shizuku 已启动，先授权此应用"
                        masterEnabled -> "运行中：打开已配置应用即自动启用肩键（$profileCount 个已启用）"
                        else -> "开启后，打开任一已配置应用会自动套用其肩键"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
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
    editingPackage: String,
    onAdd: (String) -> Unit,
    onToggleProfile: (String, Boolean) -> Unit,
    onEditToggle: (String) -> Unit,
    onChangeProfile: (AppProfile) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit
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
                onOpen = { onOpen(profile.packageName) }
            )
        }

        Divider(color = StrokeDim)
        AddProfileMenu(
            context = context,
            existing = profiles.map { it.packageName }.toSet(),
            foreground = foreground,
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
    onOpen: () -> Unit
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
                        Text(profile.label, fontWeight = FontWeight.SemiBold)
                        if (isForeground) {
                            Text("前台", color = StateGreen, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(profile.packageName, style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Text(
                        text = "${orientationLabel(profile.orientation)} · ${modeLabel(profile)} · " +
                            "L(${profile.left.x},${profile.left.y}) R(${profile.right.x},${profile.right.y})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
                Switch(checked = profile.enabled, colors = redSwitchColors(), onCheckedChange = onToggle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEditToggle) { Text(if (expanded) "收起" else "编辑") }
                OutlinedButton(onClick = onOpen) { Text("打开") }
                OutlinedButton(onClick = onDelete) { Text("删除") }
            }
            AnimatedVisibility(visible = expanded) {
                ProfileEditor(profile = profile, onChange = onChange)
            }
        }
    }
}

@Composable
private fun ProfileEditor(profile: AppProfile, onChange: (AppProfile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Divider(color = StrokeDim)
        Text("屏幕方向（决定坐标系，游戏多为横屏）", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = profile.orientation == ScreenOrientation.LANDSCAPE,
                onClick = { onChange(profile.copy(orientation = ScreenOrientation.LANDSCAPE)) },
                label = { Text("横屏") }
            )
            FilterChip(
                selected = profile.orientation == ScreenOrientation.PORTRAIT,
                onClick = { onChange(profile.copy(orientation = ScreenOrientation.PORTRAIT)) },
                label = { Text("竖屏") }
            )
        }
        Text("点按模式", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = profile.mode == AppProfile.MODE_SINGLE,
                onClick = { onChange(profile.copy(mode = AppProfile.MODE_SINGLE)) },
                label = { Text("单点") }
            )
            FilterChip(
                selected = profile.mode == AppProfile.MODE_RAPID,
                onClick = { onChange(profile.copy(mode = AppProfile.MODE_RAPID, rapidFire = profile.rapidFire.coerceAtLeast(10))) },
                label = { Text("连点 x${profile.rapidFire}") }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("左 X", profile.left.x, Modifier.weight(1f)) { onChange(profile.copy(left = profile.left.copy(x = it))) }
            NumberField("左 Y", profile.left.y, Modifier.weight(1f)) { onChange(profile.copy(left = profile.left.copy(y = it))) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("右 X", profile.right.x, Modifier.weight(1f)) { onChange(profile.copy(right = profile.right.copy(x = it))) }
            NumberField("右 Y", profile.right.y, Modifier.weight(1f)) { onChange(profile.copy(right = profile.right.copy(y = it))) }
        }
        if (profile.mode == AppProfile.MODE_RAPID) {
            NumberField("连发次数", profile.rapidFire, Modifier.fillMaxWidth()) {
                onChange(profile.copy(rapidFire = it.coerceAtLeast(1)))
            }
        }
        Text(
            text = "提示：坐标是“${orientationLabel(profile.orientation)}下的屏幕像素”。用系统截图找按钮位置，或先用自测确认肩键命中。",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProfileMenu(
    context: Context,
    existing: Set<String>,
    foreground: String,
    onAdd: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val candidates = remember(existing, foreground, showAll) {
        addCandidates(context, existing, foreground, showAll)
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
            candidates.forEach { (label, pkg) ->
                DropdownMenuItem(
                    text = {
                        if (pkg == SHOW_ALL) {
                            Text(label, color = StateBlue)
                        } else {
                            Column {
                                Text(label)
                                Text(pkg, style = MaterialTheme.typography.bodySmall, color = TextDim)
                            }
                        }
                    },
                    onClick = {
                        if (pkg == SHOW_ALL) {
                            showAll = true
                        } else {
                            onAdd(pkg)
                            expanded = false
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
    leftHits: Int,
    rightHits: Int,
    touchHits: Int,
    lastTouch: String,
    probeStatus: String,
    testStatus: String,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    PanelSurface {
        Text("自测：验证肩键效果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "把肩键临时映射到本应用窗口内，按 L/R 看是否真的产生触摸。测完一键释放，不改动任何应用配置。",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TestCounterBox("L 键 (F7)", leftHits, AccentRed, Modifier.weight(1f))
            TestCounterBox("R 键 (F8)", rightHits, AccentBlue, Modifier.weight(1f))
            TestCounterBox("触摸命中", touchHits, StateGreen, Modifier.weight(1f))
        }
        StatusTable(
            rows = listOf(
                "底层事件" to probeSummary(probeStatus),
                "App 收到键" to "L=$leftHits / R=$rightHits",
                "App 触摸命中" to "$touchHits 次",
                "最近触摸" to lastTouch
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
        Text(
            text = "说明：若“底层事件”能收到 F7/F8 但“触摸命中”为 0，可能是系统限制对非游戏应用注入，可改在真实游戏内用对应配置验证。",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
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
    leftHits: Int,
    rightHits: Int,
    touchHits: Int,
    autoBoot: Boolean,
    onAutoBoot: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    PanelSurface {
        Text("运行状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        StatusText("Shizuku", shizukuPillText(shizukuState))
        StatusText("后端 UserService", controllerStateText(controllerState))
        StatusText("Vendor TGK 开关", tgkSummary(nativeStatus))
        StatusText("肩键事件 F7/F8", if (leftHits + rightHits > 0) "可见 L=$leftHits R=$rightHits" else "未捕获")
        StatusText("触摸命中(自测)", if (touchHits > 0) "$touchHits 次" else "-")
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
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, colors = redSwitchColors(), onCheckedChange = onChange)
    }
}

@Composable
private fun StatusText(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextDim)
        Text(value, fontWeight = FontWeight.SemiBold)
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
    AppProfile.MODE_RAPID -> "连点 x${profile.rapidFire}"
    else -> "模式 ${profile.mode}"
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
private fun defaultPointsFor(orientation: ScreenOrientation, context: Context): Pair<TriggerPoint, TriggerPoint> {
    val (longEdge, shortEdge) = physicalEdges(context)
    return if (orientation == ScreenOrientation.LANDSCAPE) {
        TriggerPoint((longEdge * 0.5).toInt(), (shortEdge * 0.45).toInt()) to
            TriggerPoint((longEdge * 0.6).toInt(), (shortEdge * 0.35).toInt())
    } else {
        TriggerPoint((shortEdge * 0.5).toInt(), (longEdge * 0.45).toInt()) to
            TriggerPoint((shortEdge * 0.6).toInt(), (longEdge * 0.35).toInt())
    }
}

/** 自测用的临时 profile：本 app（竖屏），左右映射到窗口内不同高度的合法点。 */
private fun selfTestProfile(context: Context): AppProfile {
    val dm = context.resources.displayMetrics
    val w = dm.widthPixels
    val h = dm.heightPixels
    return AppProfile(
        packageName = context.packageName,
        label = "自测",
        enabled = true,
        orientation = ScreenOrientation.PORTRAIT,
        left = TriggerPoint(w / 2, (h * 0.4).toInt()),
        right = TriggerPoint(w / 2, (h * 0.6).toInt()),
        mode = AppProfile.MODE_SINGLE,
        rapidFire = 1
    )
}

private fun addCandidates(
    context: Context,
    existing: Set<String>,
    foreground: String,
    showAll: Boolean
): List<Pair<String, String>> {
    val result = LinkedHashMap<String, String>()
    fun add(pkg: String) {
        if (pkg.isBlank() || pkg == "null" || pkg in existing || pkg in result) return
        result[pkg] = ProfileStore.labelFor(context, pkg)
    }
    if (foreground.isNotBlank() && foreground != context.packageName) add(foreground)
    ProfileStore.recentTargets(context).forEach(::add)
    if (showAll) {
        loadLaunchableApps(context).forEach { (pkg, _) -> add(pkg) }
    }
    val items = result.entries.map { (pkg, label) -> label to pkg }.toMutableList()
    if (!showAll) items.add("从全部应用中选择…" to SHOW_ALL)
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

private fun openPackage(context: Context, packageName: String) {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    } else {
        Toast.makeText(context, "无法打开 $packageName", Toast.LENGTH_SHORT).show()
    }
}
