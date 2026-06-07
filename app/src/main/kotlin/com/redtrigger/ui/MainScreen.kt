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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.redtrigger.BootReceiver
import com.redtrigger.NativeTgkController
import com.redtrigger.TriggerInputMonitor
import com.redtrigger.TriggerConfig
import com.redtrigger.TriggerManager
import com.redtrigger.TriggerService
import kotlinx.coroutines.delay

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SHOW_ALL_APPS = "__show_all_apps__"

private val AccentRed = Color(0xFFFF1744)
private val AccentRedDeep = Color(0xFF650015)
private val AccentBlue = Color(0xFF2B72FF)
private val AccentBlueDeep = Color(0xFF08245F)
private val Panel = Color(0xD90B0D14)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent() {
    val context = LocalContext.current
    var config by remember { mutableStateOf(TriggerManager.loadConfig(context)) }
    var enabled by remember { mutableStateOf(TriggerManager.isTriggersEnabled(context)) }
    var shizukuState by remember { mutableStateOf(NativeTgkController.shizukuState()) }
    var foreground by remember { mutableStateOf(TriggerService.lastForeground) }
    var nativeActive by remember { mutableStateOf(TriggerService.nativeActive) }
    var nativeStatus by remember { mutableStateOf(NativeTgkController.lastStatus) }
    var autoBoot by remember { mutableStateOf(BootReceiver.isAutoEnableEnabled(context)) }
    var advancedOpen by remember { mutableStateOf(false) }
    var shizukuDetailsOpen by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf("尚未开始测试") }
    var testLeftHits by remember { mutableStateOf(TriggerInputMonitor.leftHits) }
    var testRightHits by remember { mutableStateOf(TriggerInputMonitor.rightHits) }
    var lastInputEvent by remember { mutableStateOf(TriggerInputMonitor.lastEvent) }
    var testTouchHits by remember { mutableStateOf(TriggerInputMonitor.touchHits) }
    var lastTouchEvent by remember { mutableStateOf(TriggerInputMonitor.lastTouchEvent) }
    var nativeProbeStatus by remember { mutableStateOf("result=not_started\nleft=0\nright=0") }
    var showAllApps by remember { mutableStateOf(false) }
    var shellActivePackages by remember { mutableStateOf(emptyList<String>()) }
    var recentTargetPackages by remember { mutableStateOf(TriggerManager.loadRecentTargets(context)) }

    val allApps = remember { loadLaunchableApps(context) }
    val apps = remember(showAllApps, foreground, config.targetPackage, allApps, shellActivePackages, recentTargetPackages) {
        loadTargetApps(
            context = context,
            allApps = allApps,
            targetPackage = config.targetPackage,
            foreground = foreground,
            shellActivePackages = shellActivePackages,
            recentTargets = recentTargetPackages,
            showAllApps = showAllApps
        )
    }
    var appMenuExpanded by remember { mutableStateOf(false) }
    val selectedLabel = apps.firstOrNull { it.packageName == config.targetPackage }?.label ?: config.targetPackage
    val shizukuUsable = shizukuState in listOf(
        NativeTgkController.ShizukuState.AUTHORIZED,
        NativeTgkController.ShizukuState.CONNECTING,
        NativeTgkController.ShizukuState.CONNECTED
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            shizukuState = NativeTgkController.shizukuState()
            enabled = TriggerManager.isTriggersEnabled(context)
            foreground = TriggerService.lastForeground
            nativeActive = TriggerService.nativeActive
            nativeStatus = NativeTgkController.lastStatus
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(180)
            testLeftHits = TriggerInputMonitor.leftHits
            testRightHits = TriggerInputMonitor.rightHits
            lastInputEvent = TriggerInputMonitor.lastEvent
            testTouchHits = TriggerInputMonitor.touchHits
            lastTouchEvent = TriggerInputMonitor.lastTouchEvent
        }
    }

    LaunchedEffect(shizukuState) {
        while (true) {
            if (shizukuState == NativeTgkController.ShizukuState.CONNECTED) {
                NativeTgkController.refreshActivePackages { packages ->
                    shellActivePackages = packages
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
                    listOf(
                        Color(0xFF070810),
                        Color(0xFF10131D),
                        Color(0xFF0B080D)
                    )
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

            ShoulderPreviewPanel(
                config = config,
                enabled = enabled,
                nativeActive = nativeActive,
                foreground = foreground
            )

            PanelSurface {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "自动恢复肩键",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                nativeActive -> "目标前台，正在持续恢复肩键"
                                enabled -> "守护运行中；离开目标应用不会主动关闭"
                                shizukuState == NativeTgkController.ShizukuState.NOT_RUNNING -> "Shizuku 未启动，先用 ADB 或 Shizuku App 启动"
                                shizukuState == NativeTgkController.ShizukuState.UNAUTHORIZED -> "Shizuku 已启动，先授权此应用"
                                else -> "开启后启动前台守护；关闭守护会关闭 TGK"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                    }
                    Switch(
                        checked = enabled,
                        enabled = shizukuUsable,
                        colors = redSwitchColors(),
                        onCheckedChange = { checked: Boolean ->
                            TriggerManager.saveConfig(context, config)
                            val ok = if (checked) {
                                TriggerManager.enableTriggers(context)
                            } else {
                                TriggerManager.disableTriggers(context)
                            }
                            enabled = ok && checked
                            Toast.makeText(
                                context,
                                if (enabled) "已启动守护" else "已停止",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                if (!shizukuUsable) {
                    Divider(color = StrokeDim)
                    Text(
                        text = shizukuHelpText(shizukuState),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            enabled = shizukuState == NativeTgkController.ShizukuState.UNAUTHORIZED,
                            onClick = { NativeTgkController.requestPermission() }
                        ) {
                            Text("授权 Shizuku")
                        }
                        OutlinedButton(onClick = { openShizuku(context) }) {
                            Text("打开 Shizuku")
                        }
                    }
                }
            }

            TriggerSelfTestPanel(
                config = config,
                shizukuUsable = shizukuUsable,
                shizukuState = shizukuState,
                controllerState = NativeTgkController.state,
                leftHits = testLeftHits,
                rightHits = testRightHits,
                touchHits = testTouchHits,
                lastInputEvent = lastInputEvent,
                lastTouchEvent = lastTouchEvent,
                nativeProbeStatus = nativeProbeStatus,
                testStatus = testStatus,
                onStartTest = {
                    when {
                        !shizukuUsable -> {
                            testStatus = "Shizuku 未就绪，先完成授权和连接"
                        }
                        else -> {
                            TriggerInputMonitor.reset()
                            testLeftHits = 0
                            testRightHits = 0
                            testTouchHits = 0
                            lastInputEvent = TriggerInputMonitor.lastEvent
                            lastTouchEvent = TriggerInputMonitor.lastTouchEvent
                            nativeProbeStatus = "result=sampling\nleft=0\nright=0"
                            val testConfig = config.copy(targetPackage = context.packageName)
                            NativeTgkController.enable(testConfig, logResult = true)
                            testStatus = "已临时启用当前 app：L(${config.leftX},${config.leftY}) / R(${config.rightX},${config.rightY})，5 秒内按 L/R"
                            NativeTgkController.probeShoulderKeys(5000) { result ->
                                nativeProbeStatus = result
                                testStatus = shoulderProbeAdvice(result)
                            }
                        }
                    }
                },
                onRefreshStatus = {
                    if (NativeTgkController.state == NativeTgkController.State.CONNECTED) {
                        nativeStatus = NativeTgkController.refreshStatus()
                    } else {
                        NativeTgkController.connect {
                            nativeStatus = NativeTgkController.refreshStatus()
                        }
                    }
                    testStatus = "已请求刷新后端状态"
                }
            )

            PanelSurface {
                Text("目标应用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ExposedDropdownMenuBox(
                    expanded = appMenuExpanded,
                    onExpandedChange = { appMenuExpanded = !appMenuExpanded }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        readOnly = true,
                        value = selectedLabel,
                        onValueChange = {},
                        label = { Text("前台识别") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appMenuExpanded) },
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = appMenuExpanded,
                        onDismissRequest = { appMenuExpanded = false }
                    ) {
                        apps.forEach { app ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = app.label,
                                            color = if (app.isHeader) TextDim else Color.White,
                                            fontWeight = if (app.isHeader) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (app.packageName != SHOW_ALL_APPS && !app.isHeader) {
                                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = TextDim)
                                        }
                                    }
                                },
                                enabled = !app.isHeader,
                                onClick = {
                                    if (app.isHeader) {
                                        return@DropdownMenuItem
                                    } else if (app.packageName == SHOW_ALL_APPS) {
                                        showAllApps = true
                                    } else {
                                        val nextConfig = config.copy(targetPackage = app.packageName)
                                        config = nextConfig
                                        TriggerManager.saveConfig(context, nextConfig)
                                        TriggerManager.recordRecentTarget(context, app.packageName)
                                        recentTargetPackages = TriggerManager.loadRecentTargets(context)
                                        if (enabled) {
                                            TriggerManager.enableTriggers(context)
                                        }
                                        appMenuExpanded = false
                                    }
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = config.targetPackage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("包名") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            TriggerManager.saveConfig(context, config)
                            TriggerManager.recordRecentTarget(context, config.targetPackage)
                            recentTargetPackages = TriggerManager.loadRecentTargets(context)
                            if (shizukuUsable) {
                                enabled = TriggerManager.enableTriggers(context)
                                Toast.makeText(context, "已切换目标并启动守护", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Shizuku 未就绪，仅打开应用", Toast.LENGTH_SHORT).show()
                            }
                            openPackage(context, config.targetPackage)
                        }
                    ) {
                        Text("打开并启用")
                    }
                    if (showAllApps) {
                        OutlinedButton(onClick = { showAllApps = false }) {
                            Text("收起全部应用")
                        }
                    }
                }
            }

            PanelSurface {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("肩键模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("默认使用我们实测的王者荣耀坐标", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                    OutlinedButton(onClick = { advancedOpen = !advancedOpen }) {
                        Text(if (advancedOpen) "收起" else "展开")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = config.mode == 0,
                        onClick = {
                            config = config.copy(mode = 0, rapidFire = config.rapidFire.coerceAtLeast(1))
                            TriggerManager.saveConfig(context, config)
                        },
                        label = { Text("单点") }
                    )
                    FilterChip(
                        selected = config.mode == 6,
                        onClick = {
                            config = config.copy(mode = 6, rapidFire = config.rapidFire.coerceAtLeast(10))
                            TriggerManager.saveConfig(context, config)
                        },
                        label = { Text("连点 x${config.rapidFire}") }
                    )
                }

                AnimatedVisibility(visible = advancedOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Divider(color = StrokeDim)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("L X", config.leftX, Modifier.weight(1f)) {
                                config = config.copy(leftX = it)
                                TriggerManager.saveConfig(context, config)
                            }
                            NumberField("L Y", config.leftY, Modifier.weight(1f)) {
                                config = config.copy(leftY = it)
                                TriggerManager.saveConfig(context, config)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("R X", config.rightX, Modifier.weight(1f)) {
                                config = config.copy(rightX = it)
                                TriggerManager.saveConfig(context, config)
                            }
                            NumberField("R Y", config.rightY, Modifier.weight(1f)) {
                                config = config.copy(rightY = it)
                                TriggerManager.saveConfig(context, config)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("Mode", config.mode, Modifier.weight(1f)) {
                                config = config.copy(mode = it)
                                TriggerManager.saveConfig(context, config)
                            }
                            NumberField("Rapid", config.rapidFire, Modifier.weight(1f)) {
                                config = config.copy(rapidFire = it.coerceAtLeast(1))
                                TriggerManager.saveConfig(context, config)
                            }
                        }
                        NumberField("轮询间隔 ms", config.pollMs.toInt(), Modifier.fillMaxWidth()) {
                            config = config.copy(pollMs = it.coerceAtLeast(250).toLong())
                            TriggerManager.saveConfig(context, config)
                        }
                    }
                }
            }

            PanelSurface {
                Text("运行状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ToggleLine("开机后自动恢复", autoBoot) {
                    BootReceiver.setAutoEnable(context, it)
                    autoBoot = it
                }
                Text(
                    text = "开机后只会尝试启动守护服务；仍依赖 Shizuku server 已经启动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
                StatusText("前端进程", "UI 活跃")
                StatusText("后端通道", controllerStateText(NativeTgkController.state))
                StatusText("前台包名", foreground.ifBlank { "-" })
                StatusText("守护服务", if (TriggerService.isRunning) "运行中" else "未运行")
                StatusText("TGK 原生", tgkSummary(nativeStatus))
                Text(
                    text = nativeStatus.ifBlank { "启动后会显示 RedMagic TGK 原生状态。" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        if (NativeTgkController.state == NativeTgkController.State.CONNECTED) {
                            nativeStatus = NativeTgkController.refreshStatus()
                        } else {
                            NativeTgkController.connect {
                                nativeStatus = NativeTgkController.refreshStatus()
                            }
                        }
                    }) {
                        Text("刷新")
                    }
                    OutlinedButton(onClick = {
                        NativeTgkController.disable()
                        nativeStatus = NativeTgkController.refreshStatus()
                    }) {
                        Text("强制关闭")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun HeaderBar(shizukuState: NativeTgkController.ShizukuState, serviceRunning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "红魔肩键",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "RedMagic native trigger bridge",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
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
        border = BorderStroke(1.dp, color.copy(alpha = 0.72f)),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusIcon(shizukuIcon(state), color)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = shizukuTitle(state),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = shizukuSuggestion(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HelpToggleIcon(
                        active = detailsOpen,
                        color = color,
                        onClick = onToggleDetails
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    NativeTgkController.ShizukuState.NOT_RUNNING -> {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            onClick = onOpenShizuku
                        ) {
                            Text("打开 Shizuku")
                        }
                    }
                    NativeTgkController.ShizukuState.UNAUTHORIZED -> {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            onClick = onRequestPermission
                        ) {
                            Text("授权")
                        }
                        OutlinedButton(onClick = onOpenShizuku) {
                            Text("打开 Shizuku")
                        }
                    }
                    NativeTgkController.ShizukuState.AUTHORIZED -> {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            onClick = onConnect
                        ) {
                            Text("连接后端")
                        }
                    }
                    NativeTgkController.ShizukuState.CONNECTING -> {
                        OutlinedButton(enabled = false, onClick = {}) {
                            Text("连接中")
                        }
                    }
                    NativeTgkController.ShizukuState.CONNECTED -> {
                        OutlinedButton(onClick = onConnect) {
                            Text("重新检查")
                        }
                    }
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
        Text(
            text = "?",
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun ShizukuFlowPanel() {
    PanelSurface {
        Text("Shizuku 接入流程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowStep("1", "未启动", "Shizuku server 不在线，需要先从 Shizuku App 或 ADB 启动。", StateGray)
        FlowStep("2", "未授权", "server 已在线，但当前 app 还没有 Shizuku API 权限。", StateAmber)
        FlowStep("3", "已授权", "权限已经通过，可以绑定 shell 权限后端服务。", StateBlue)
        FlowStep("4", "已连接", "UserService 已绑定，之后才能读取 TGK 状态和写入肩键配置。", StateGreen)
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
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun ShizukuStatusPill(state: NativeTgkController.ShizukuState) {
    val color = shizukuColor(state)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = shizukuPillText(state),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun shizukuPillText(state: NativeTgkController.ShizukuState): String {
    return when (state) {
        NativeTgkController.ShizukuState.NOT_RUNNING -> "未启动"
        NativeTgkController.ShizukuState.UNAUTHORIZED -> "未授权"
        NativeTgkController.ShizukuState.AUTHORIZED -> "已授权"
        NativeTgkController.ShizukuState.CONNECTING -> "连接中"
        NativeTgkController.ShizukuState.CONNECTED -> "已连接"
    }
}

private fun shizukuHelpText(state: NativeTgkController.ShizukuState): String {
    return when (state) {
        NativeTgkController.ShizukuState.NOT_RUNNING -> "先启动 Shizuku 服务；ADB 启动后回到这里授权。"
        NativeTgkController.ShizukuState.UNAUTHORIZED -> "Shizuku 已启动，点授权后才能开启守护。"
        NativeTgkController.ShizukuState.AUTHORIZED,
        NativeTgkController.ShizukuState.CONNECTING,
        NativeTgkController.ShizukuState.CONNECTED -> ""
    }
}

private fun shizukuColor(state: NativeTgkController.ShizukuState): Color {
    return when (state) {
        NativeTgkController.ShizukuState.NOT_RUNNING -> StateGray
        NativeTgkController.ShizukuState.UNAUTHORIZED -> StateAmber
        NativeTgkController.ShizukuState.AUTHORIZED -> StateBlue
        NativeTgkController.ShizukuState.CONNECTING -> AccentRed
        NativeTgkController.ShizukuState.CONNECTED -> StateGreen
    }
}

private fun shizukuIcon(state: NativeTgkController.ShizukuState): String {
    return when (state) {
        NativeTgkController.ShizukuState.NOT_RUNNING -> "!"
        NativeTgkController.ShizukuState.UNAUTHORIZED -> "?"
        NativeTgkController.ShizukuState.AUTHORIZED -> "i"
        NativeTgkController.ShizukuState.CONNECTING -> "..."
        NativeTgkController.ShizukuState.CONNECTED -> "OK"
    }
}

private fun shizukuTitle(state: NativeTgkController.ShizukuState): String {
    return when (state) {
        NativeTgkController.ShizukuState.NOT_RUNNING -> "Shizuku 未启动"
        NativeTgkController.ShizukuState.UNAUTHORIZED -> "等待授权"
        NativeTgkController.ShizukuState.AUTHORIZED -> "已授权，尚未连接后端"
        NativeTgkController.ShizukuState.CONNECTING -> "正在连接后端"
        NativeTgkController.ShizukuState.CONNECTED -> "后端已连接"
    }
}

private fun shizukuSuggestion(state: NativeTgkController.ShizukuState): String {
    return when (state) {
        NativeTgkController.ShizukuState.NOT_RUNNING -> "先启动 Shizuku server；ADB 启动后此处会自动刷新。"
        NativeTgkController.ShizukuState.UNAUTHORIZED -> "点授权，让 RedTriggerFix 使用 Shizuku API。"
        NativeTgkController.ShizukuState.AUTHORIZED -> "点连接后端，验证 UserService 能否绑定。"
        NativeTgkController.ShizukuState.CONNECTING -> "正在等待 Shizuku 返回 UserService 连接结果。"
        NativeTgkController.ShizukuState.CONNECTED -> "通道正常，可以启动守护或读取 TGK 状态。"
    }
}

@Composable
private fun TriggerSelfTestPanel(
    config: TriggerConfig,
    shizukuUsable: Boolean,
    shizukuState: NativeTgkController.ShizukuState,
    controllerState: NativeTgkController.State,
    leftHits: Int,
    rightHits: Int,
    touchHits: Int,
    lastInputEvent: String,
    lastTouchEvent: String,
    nativeProbeStatus: String,
    testStatus: String,
    onStartTest: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    val accent = shizukuColor(shizukuState)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = PanelSoft,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.68f)),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("RedTriggerFix 自测", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "临时把当前 app 作为测试目标，验证肩键是否落到配置坐标。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TestCounterBox(
                    label = "L",
                    hits = leftHits,
                    color = AccentRed,
                    modifier = Modifier.weight(1f)
                )
                TestCounterBox(
                    label = "R",
                    hits = rightHits,
                    color = AccentBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            StatusTable(
                rows = listOf(
                    "测试目标" to "com.redtriggerfix",
                    "服务目标" to config.targetPackage,
                    "映射坐标" to "L(${config.leftX},${config.leftY}) / R(${config.rightX},${config.rightY})",
                    "后端通道" to controllerStateText(controllerState),
                    "底层输入" to probeSummary(nativeProbeStatus),
                    "App KeyEvent" to "L=$leftHits / R=$rightHits",
                    "App 触摸" to "$touchHits 次",
                    "最近触摸" to lastTouchEvent,
                    "最近输入" to lastInputEvent
                )
            )

            Text(
                text = testStatus,
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    enabled = shizukuUsable,
                    onClick = onStartTest
                ) {
                    Text("开始监听")
                }
                OutlinedButton(onClick = onRefreshStatus) {
                    Text("刷新后端")
                }
            }
        }
    }
}

@Composable
private fun TestCounterBox(
    label: String,
    hits: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("触发 $hits", style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = TextDim, style = MaterialTheme.typography.bodySmall)
                Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ShoulderPreviewPanel(
    config: TriggerConfig,
    enabled: Boolean,
    nativeActive: Boolean,
    foreground: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 26.dp),
        color = Panel,
        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.65f)),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("红魔肩键", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    HelpDot()
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniHardwareSwitch(enabled)
                    Text("X", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Divider(color = StrokeDim)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TriggerSideBlock(
                    label = "L",
                    mode = modeLabel(config),
                    color = AccentRed,
                    darkColor = AccentRedDeep,
                    modifier = Modifier.weight(1f)
                )
                TriggerSideBlock(
                    label = "R",
                    mode = modeLabel(config),
                    color = AccentBlue,
                    darkColor = AccentBlueDeep,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingButtonMarker("L", AccentRed, "${config.leftX}, ${config.leftY}")
                StatusPill(
                    text = when {
                        nativeActive -> "已接管"
                        enabled -> "等待前台"
                        else -> "关闭"
                    },
                    ok = nativeActive
                )
                FloatingButtonMarker("R", AccentRed, "${config.rightX}, ${config.rightY}")
            }

            Text(
                text = "Foreground: ${foreground.ifBlank { "-" }}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PanelSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = PanelSoft,
        border = BorderStroke(1.dp, StrokeDim),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun TriggerSideBlock(
    label: String,
    mode: String,
    color: Color,
    darkColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(82.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(darkColor, color.copy(alpha = 0.28f))))
            .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(mode, fontWeight = FontWeight.SemiBold)
                    Text("▼", color = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }
    }
}

@Composable
private fun FloatingButtonMarker(label: String, color: Color, coordinate: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(3.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        }
        Text(coordinate, color = TextDim, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HelpDot() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .border(2.dp, TextDim, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("?", color = TextDim, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniHardwareSwitch(enabled: Boolean) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF3A3D4E))
            .border(1.dp, StrokeDim, RoundedCornerShape(15.dp))
            .padding(4.dp),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (enabled) AccentRed else Color(0xFF717588))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(7.dp))
        )
    }
}

@Composable
private fun StatusPill(text: String, ok: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (ok) AccentRed.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (ok) AccentRed.copy(alpha = 0.65f) else StrokeDim)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = if (ok) Color.White else TextDim,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, colors = redSwitchColors(), onCheckedChange = onChange)
    }
}

@Composable
private fun StatusText(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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

private fun modeLabel(config: TriggerConfig): String {
    return when (config.mode) {
        0 -> "单点"
        6 -> "连点 x${config.rapidFire}"
        else -> "模式 ${config.mode}"
    }
}

private fun controllerStateText(state: NativeTgkController.State): String {
    return when (state) {
        NativeTgkController.State.STOPPED -> "未连接"
        NativeTgkController.State.CONNECTING -> "连接中"
        NativeTgkController.State.CONNECTED -> "已连接"
    }
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
        left > 0 || right > 0 -> "检测到肩键输入：L=$left / R=$right"
        result == "no_event" -> "采样窗口内没有按到肩键；点开始监听后按 L/R。"
        result == "no_tgk_input_device" -> "未找到 nubia_tgk 输入设备，检查系统服务或机型接口"
        result == "raw_seen" -> "采到设备输出但没有按下事件；请再按一次 L/R。"
        result == "not_connected" -> "后端未连接，先连接 Shizuku UserService"
        result == "error" -> "采样失败，查看日志"
        else -> "采样完成，未收到肩键按下事件"
    }
}

private fun statusValue(status: String, key: String): String? {
    return status
        .lineSequence()
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")
}

private enum class AppGroup { ACTIVE, RECENT, ALL, ACTION, HEADER }

private data class AppChoice(
    val label: String,
    val packageName: String,
    val group: AppGroup = AppGroup.ACTIVE,
    val isHeader: Boolean = false
)

private fun loadTargetApps(
    context: Context,
    allApps: List<AppChoice>,
    targetPackage: String,
    foreground: String,
    shellActivePackages: List<String>,
    recentTargets: List<String>,
    showAllApps: Boolean
): List<AppChoice> {
    if (showAllApps) {
        return listOf(AppChoice("全部应用", "", AppGroup.HEADER, isHeader = true)) +
            allApps.map { it.copy(group = AppGroup.ALL) }
    }
    val byPackage = allApps.associateBy { it.packageName }
    val activePackages = mutableListOf<String>()
    fun addActive(packageName: String) {
        if (packageName.isNotBlank() && packageName != "null" && packageName !in activePackages) {
            activePackages += packageName
        }
    }
    addActive(foreground)
    addActive(targetPackage)
    shellActivePackages.forEach(::addActive)

    val activeChoices = activePackages
        .map { packageName ->
            byPackage[packageName]?.copy(group = AppGroup.ACTIVE)
                ?: AppChoice(loadPackageLabel(context, packageName), packageName, AppGroup.ACTIVE)
        }
        .filter { it.label.isNotBlank() || it.packageName.isNotBlank() }
        .distinctBy { it.packageName }

    val recentChoices = recentTargets
        .filter { recent -> recent !in activeChoices.map { it.packageName } }
        .map { packageName ->
            byPackage[packageName]?.copy(group = AppGroup.RECENT)
                ?: AppChoice(loadPackageLabel(context, packageName), packageName, AppGroup.RECENT)
        }
        .distinctBy { it.packageName }
        .take(10)

    val result = mutableListOf<AppChoice>()
    result += AppChoice("活跃应用", "", AppGroup.HEADER, isHeader = true)
    result += activeChoices
    if (recentChoices.isNotEmpty()) {
        result += AppChoice("常用应用", "", AppGroup.HEADER, isHeader = true)
        result += recentChoices
    }
    result += AppChoice("不存在? 尝试在全部应用中选取", SHOW_ALL_APPS, AppGroup.ACTION)
    return result
}

private fun loadPackageLabel(context: Context, packageName: String): String {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        info.loadLabel(pm)?.toString().orEmpty().ifBlank { packageName }
    } catch (_: Exception) {
        packageName
    }
}

private fun loadLaunchableApps(context: Context): List<AppChoice> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = pm.queryIntentActivities(intent, 0)
        .map {
            AppChoice(
                label = it.loadLabel(pm)?.toString().orEmpty().ifBlank { it.activityInfo.packageName },
                packageName = it.activityInfo.packageName
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .toMutableList()
    if (apps.none { it.packageName == "com.tencent.tmgp.sgame" }) {
        apps.add(0, AppChoice("王者荣耀", "com.tencent.tmgp.sgame"))
    }
    return apps
}

private fun openShizuku(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
    if (launch != null) {
        context.startActivity(launch)
    } else {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
        )
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
