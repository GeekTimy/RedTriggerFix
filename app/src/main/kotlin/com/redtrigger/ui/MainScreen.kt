package com.redtrigger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.redtrigger.TriggerConfig
import com.redtrigger.TriggerManager
import com.redtrigger.TriggerService
import kotlinx.coroutines.delay

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

private val AccentRed = Color(0xFFFF1744)
private val AccentRedDeep = Color(0xFF650015)
private val AccentBlue = Color(0xFF2B72FF)
private val AccentBlueDeep = Color(0xFF08245F)
private val Panel = Color(0xD90B0D14)
private val PanelSoft = Color(0xE51A1C27)
private val StrokeDim = Color(0x33FFFFFF)
private val TextDim = Color(0xFFB9BDCA)

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
    var shizukuReady by remember { mutableStateOf(NativeTgkController.hasShizukuPermission()) }
    var foreground by remember { mutableStateOf(TriggerService.lastForeground) }
    var nativeActive by remember { mutableStateOf(TriggerService.nativeActive) }
    var nativeStatus by remember { mutableStateOf(NativeTgkController.lastStatus) }
    var autoBoot by remember { mutableStateOf(BootReceiver.isAutoEnableEnabled(context)) }
    var advancedOpen by remember { mutableStateOf(false) }

    val apps = remember { loadLaunchableApps(context) }
    var appMenuExpanded by remember { mutableStateOf(false) }
    val selectedLabel = apps.firstOrNull { it.packageName == config.targetPackage }?.label ?: config.targetPackage

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            shizukuReady = NativeTgkController.hasShizukuPermission()
            enabled = TriggerManager.isTriggersEnabled(context)
            foreground = TriggerService.lastForeground
            nativeActive = TriggerService.nativeActive
            nativeStatus = NativeTgkController.lastStatus
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
            HeaderBar(shizukuReady = shizukuReady, serviceRunning = TriggerService.isRunning)

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
                                nativeActive -> "目标游戏前台，原生 TGK 已开启"
                                enabled -> "等待目标游戏进入前台"
                                else -> "关闭后不会改动系统肩键状态"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                    }
                    Switch(
                        checked = enabled,
                        enabled = shizukuReady,
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

                if (!shizukuReady) {
                    Divider(color = StrokeDim)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
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
                                        Text(app.label)
                                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = TextDim)
                                    }
                                },
                                onClick = {
                                    config = config.copy(targetPackage = app.packageName)
                                    TriggerManager.saveConfig(context, config)
                                    appMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = config.targetPackage,
                    onValueChange = {
                        config = config.copy(targetPackage = it.trim())
                        TriggerManager.saveConfig(context, config)
                    },
                    label = { Text("包名") },
                    singleLine = true
                )
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
                Text("系统状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ToggleLine("开机后自动恢复", autoBoot) {
                    BootReceiver.setAutoEnable(context, it)
                    autoBoot = it
                }
                StatusText("前台包名", foreground.ifBlank { "-" })
                StatusText("守护服务", if (TriggerService.isRunning) "运行中" else "未运行")
                StatusText("TGK", if (nativeActive) "已开启" else "未开启")
                Text(
                    text = nativeStatus.ifBlank { "启动后会显示 RedMagic TGK 原生状态。" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { nativeStatus = NativeTgkController.refreshStatus() }) {
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
private fun HeaderBar(shizukuReady: Boolean, serviceRunning: Boolean) {
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
            StatusPill(if (shizukuReady) "Shizuku" else "未授权", shizukuReady)
            StatusPill(if (serviceRunning) "守护中" else "待机", serviceRunning)
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

private data class AppChoice(val label: String, val packageName: String)

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
