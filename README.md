# RedTriggerFix

RedTriggerFix 是一个面向红魔 / Nubia RMOS 设备的肩键桥接工具。它通过 Shizuku 获取 shell 侧能力，调用系统 TGK / native input 路径，把红魔肩键映射到目标应用里的触摸坐标。

这个项目不是通用 Android keymapper。它依赖红魔系统中的 vendor 扩展接口，只适用于支持相关 TGK 方法的设备。

## 🎆 功能（v1.0）

🎉 v1.0 是第一个稳定大版本：每应用横竖双配置、按设备方向自动切换、坐标越界保护与干净释放，在反复真机测试下已相当鲁棒。

- 🎮 为不同应用保存独立肩键配置，进入目标应用时自动启用、离开时自动释放。
- 🔄 **每应用横屏 / 竖屏各一套坐标**，每个方向独立「使用肩键」开关（可都开）。
- 📱 **旋转跟随**：设备旋转时自动套用当前方向的坐标；该方向未启用则干净释放，绝不残留到另一方向。
- 🛡️ **坐标越界保护**：输入即按当前方向屏幕范围校验，超界提示并保留原值；套用前再次合法化，杜绝注入到屏外。
- 🧹 **干净释放**：用原生 disable 序列让出 TGK，不会把肩键泄漏到全局 / 桌面。
- 🎯 在目标应用上拖动 L/R 悬浮取点，按当前手机方向保存到对应方向配置。
- 👆 支持 L/R 单独启用、单点 / 连发模式，连发 1–30 次/秒。
- 🪧 可选显示运行时 L/R 标记，方便确认当前映射位置。
- 🔍 提供 Shizuku、前台 / 活跃应用、TGK 状态与肩键输入采样信息（自测零污染）。

默认新建配置为竖屏、单点模式，并启用 L/R 两侧肩键；需要横屏时在配置里打开「横屏使用肩键」开关即可。

## 使用

1. 安装并启动 Shizuku。
2. 给 RedTriggerFix 授权 Shizuku。
3. 在 RedTriggerFix 中添加目标应用。
4. 根据目标应用打开「横屏 / 竖屏使用肩键」开关（可都开），并设置各方向坐标。
5. 打开悬浮取点，在目标应用上拖动 L/R 到需要触发的位置并保存。
6. 打开肩键守护。
7. 进入目标应用后，RedTriggerFix 会自动应用该应用的肩键配置。
8. 离开目标应用后，守护服务会释放当前 TGK 映射。

如果需要确认运行时位置，可以在应用配置中开启 L/R 标记显示。

## 权限

- Shizuku 授权：启动 shell 侧 UserService，并调用系统 / vendor 接口。
- 悬浮窗权限：用于悬浮取点和可选的运行时 L/R 标记。
- 前台服务权限：用于维持肩键守护服务。
- 开机广播权限：用于后续恢复守护状态。

## 架构

```text
Compose UI
  |
  | 读写配置、启动守护、启动悬浮取点
  v
ProfileStore
  |
  | profiles_json / master switch / recent targets
  v
TriggerService  <---- foreground package query
  |
  | connect / enable / disable / release
  v
NativeTgkController
  |
  | Shizuku bindUserService
  v
InputService (shell process)
  |
  | reflection call android.hardware.input.IInputManager vendor methods
  v
RedMagic TGK native input path
```

悬浮取点使用独立的 overlay 服务：

```text
Compose UI -> OverlayPickService -> WindowManager TYPE_APPLICATION_OVERLAY
```

`OverlayPickService` 只负责显示 L/R 标记和保存坐标。真正的肩键转触摸仍由红魔 TGK / native input 层完成。

## 代码结构

```text
app/src/main/kotlin/com/redtrigger/
  AppProfile.kt          每应用肩键配置模型
  ProfileStore.kt        配置持久化、总开关、近期应用
  TriggerService.kt      前台守护与 TGK 生命周期管理
  NativeTgkController.kt App 侧 TGK 控制入口
  InputService.kt        Shizuku UserService，shell 侧调用 vendor API
  OverlayPickService.kt  悬浮取点与运行时 L/R 标记
  BootReceiver.kt        开机恢复入口
  MainActivity.kt        Compose 入口
  ui/MainScreen.kt       主界面、配置编辑、自测卡片
```

## 本地构建

需要 JDK 17 和 Android SDK。

Debug 包：

```powershell
.\gradlew.bat assembleDebug
```

Release 包：

```powershell
.\gradlew.bat assembleRelease
```

本地构建时，`local.properties` 可以指向 Android SDK：

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

`local.properties` 只用于本机环境配置，仓库已忽略该文件。

## 下载

发布版 APK 会放在 GitHub Releases：

```text
https://github.com/GeekTimy/RedTriggerFix/releases
```

## License

MIT
