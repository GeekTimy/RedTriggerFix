package com.redtrigger

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList

object NativeTgkController {
    enum class State { STOPPED, CONNECTING, CONNECTED }
    enum class ShizukuState { NOT_RUNNING, UNAUTHORIZED, AUTHORIZED, CONNECTING, CONNECTED }

    @Volatile var state: State = State.STOPPED
        private set

    @Volatile var lastStatus: String = ""
        private set

    @Volatile var lastForegroundPackage: String = ""
        private set

    @Volatile var selfTestRunning: Boolean = false
        private set

    private var inputService: IInputService? = null
    private var appContext: Context? = null
    private val pendingReady = CopyOnWriteArrayList<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var lastOwnerPrepareAt = 0L

    private const val OWNER_PREPARE_MIN_INTERVAL_MS = 15_000L

    private fun userServiceArgs(): Shizuku.UserServiceArgs {
        val packageName = appContext?.packageName ?: "com.redtriggerfix"
        return Shizuku.UserServiceArgs(
            ComponentName(packageName, InputService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("tgk")
            .debuggable(true)
            .version(5)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            inputService = IInputService.Stub.asInterface(service)
            state = State.CONNECTED
            DebugLog.log("NativeTGK", "Shizuku UserService connected")
            try {
                inputService?.grantPermission(
                    appContext?.packageName ?: "com.redtriggerfix",
                    android.Manifest.permission.WRITE_SECURE_SETTINGS
                )
            } catch (e: Exception) {
                DebugLog.log("NativeTGK", "Grant failed: ${e.message}")
            }
            prepareOwnerIfNeeded(force = true)
            val callbacks = pendingReady.toList()
            pendingReady.clear()
            callbacks.forEach { it.invoke() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            inputService = null
            state = State.STOPPED
            DebugLog.log("NativeTGK", "Shizuku UserService disconnected")
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun hasShizukuPermission(): Boolean {
        return when (shizukuState()) {
            ShizukuState.AUTHORIZED,
            ShizukuState.CONNECTING,
            ShizukuState.CONNECTED -> true
            ShizukuState.NOT_RUNNING,
            ShizukuState.UNAUTHORIZED -> false
        }
    }

    fun shizukuState(): ShizukuState {
        return try {
            when {
                !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
                Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED ->
                    ShizukuState.UNAUTHORIZED
                state == State.CONNECTED -> ShizukuState.CONNECTED
                state == State.CONNECTING -> ShizukuState.CONNECTING
                else -> ShizukuState.AUTHORIZED
            }
        } catch (_: Exception) {
            ShizukuState.NOT_RUNNING
        }
    }

    fun requestPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                DebugLog.log("NativeTGK", "Shizuku is not running")
                return
            }
            Shizuku.requestPermission(1001)
        } catch (e: Exception) {
            DebugLog.log("NativeTGK", "Request Shizuku permission failed: ${e.message}")
        }
    }

    fun connect(onReady: (() -> Unit)? = null) {
        if (state == State.CONNECTED) {
            onReady?.invoke()
            return
        }
        onReady?.let { pendingReady += it }
        if (state == State.CONNECTING) return
        state = State.CONNECTING
        try {
            if (!Shizuku.pingBinder()) {
                state = State.STOPPED
                DebugLog.log("NativeTGK", "Shizuku is not running")
                return
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                state = State.STOPPED
                DebugLog.log("NativeTGK", "Missing Shizuku permission")
                requestPermission()
                return
            }
            Shizuku.bindUserService(userServiceArgs(), connection)
        } catch (e: Exception) {
            state = State.STOPPED
            DebugLog.log("NativeTGK", "Bind failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun enable(profile: AppProfile, landscapeNow: Boolean, logResult: Boolean = true) {
        connect {
            try {
                prepareOwnerIfNeeded()
                val ctx = appContext
                val cfg = profile.configFor(landscapeNow)
                // 套用前按当前方向校验/回退默认，杜绝越界注入（bug2）。
                val (left, right) = if (ctx != null) Coords.resolve(cfg, landscapeNow, ctx) else (cfg.left to cfg.right)
                inputService?.enableNativeTgk(
                    left.x,
                    left.y,
                    right.x,
                    right.y,
                    profile.mode,
                    profile.rapidFire,
                    profile.leftEnabled,
                    profile.rightEnabled
                )
                refreshStatus()
                if (logResult) {
                    DebugLog.log(
                        "NativeTGK",
                        "Enabled native TGK for ${profile.packageName} (${if (landscapeNow) "landscape" else "portrait"}) L(${left.x},${left.y}) R(${right.x},${right.y})"
                    )
                }
            } catch (e: Exception) {
                DebugLog.log("NativeTGK", "Enable failed: ${e.message}")
            }
        }
    }

    private fun prepareOwnerIfNeeded(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastOwnerPrepareAt < OWNER_PREPARE_MIN_INTERVAL_MS) return
        lastOwnerPrepareAt = now
        try {
            val owner = appContext?.packageName ?: "com.redtriggerfix"
            val report = inputService?.prepareNativeOwner(owner).orEmpty()
            DebugLog.log("NativeTGK", "Prepared native owner: ${report.take(500)}")
        } catch (e: Exception) {
            DebugLog.log("NativeTGK", "Prepare owner failed: ${e.message}")
        }
    }

    fun disable() {
        try {
            inputService?.disableNativeTgk()
            refreshStatus()
            DebugLog.log("NativeTGK", "Disabled native TGK")
        } catch (e: Exception) {
            DebugLog.log("NativeTGK", "Disable failed: ${e.message}")
        }
    }

    /**
     * ⚠️ vendor releaseTgk 实测是"全部重新启用"(global/left/right/haptic 全变 true)，不是释放！
     * 干净释放请用 disable()(原生 disable 序列)。此包装仅留作诊断/手动测试，正常流程勿调。
     */
    fun releaseTgk() {
        try {
            inputService?.releaseTgk()
            refreshStatus()
            DebugLog.log("NativeTGK", "Released native TGK")
        } catch (e: Exception) {
            DebugLog.log("NativeTGK", "Release failed: ${e.message}")
        }
    }

    fun refreshStatus(): String {
        lastStatus = try {
            inputService?.getNativeTgkStatus() ?: ""
        } catch (e: Exception) {
            "error: ${e.message}"
        }
        return lastStatus
    }

    fun foregroundPackage(): String {
        lastForegroundPackage = try {
            inputService?.getForegroundPackage() ?: ""
        } catch (_: Exception) {
            ""
        }
        return lastForegroundPackage
    }

    fun refreshActivePackages(onResult: (List<String>) -> Unit) {
        connect {
            Thread {
                val packages = try {
                    inputService?.getActivePackages()
                        ?.lineSequence()
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?.distinct()
                        ?.toList()
                        .orEmpty()
                } catch (e: Exception) {
                    DebugLog.log("NativeTGK", "Active packages failed: ${e.message}")
                    emptyList()
                }
                mainHandler.post { onResult(packages) }
            }.start()
        }
    }

    fun probeShoulderKeys(timeoutMs: Int, onResult: (String) -> Unit) {
        connect {
            Thread {
                val result = try {
                    inputService?.probeShoulderKeys(timeoutMs) ?: "result=not_connected\nleft=0\nright=0"
                } catch (e: Exception) {
                    "result=error\nleft=0\nright=0\nraw=${e.message.orEmpty()}"
                }
                mainHandler.post { onResult(result) }
            }.start()
        }
    }

    /** Self-test: enable TGK to the given (off-screen) profile and start a continuous shoulder probe. */
    fun startSelfTest(profile: AppProfile) {
        selfTestRunning = true
        connect {
            if (!selfTestRunning) return@connect
            try {
                prepareOwnerIfNeeded()
                // 自测专用：直接用 portrait 配置里的（故意映射到屏外的）坐标，不经 Coords 校验/夹取，
                // 否则屏外点会被合法化、破坏「零误触」自测设计。
                val cfg = profile.portrait
                inputService?.enableNativeTgk(
                    cfg.left.x, cfg.left.y,
                    cfg.right.x, cfg.right.y,
                    profile.mode, profile.rapidFire,
                    profile.leftEnabled, profile.rightEnabled
                )
                inputService?.startShoulderProbe()
                refreshStatus()
                DebugLog.log("NativeTGK", "Self-test started")
            } catch (e: Exception) {
                DebugLog.log("NativeTGK", "Self-test start failed: ${e.message}")
            }
        }
    }

    /** Stop the self-test probe and fully release TGK (clean, no lingering config). */
    fun stopSelfTest() {
        selfTestRunning = false
        try {
            inputService?.stopShoulderProbe()
            inputService?.disableNativeTgk()
            refreshStatus()
            DebugLog.log("NativeTGK", "Self-test stopped & disabled")
        } catch (e: Exception) {
            DebugLog.log("NativeTGK", "Self-test stop failed: ${e.message}")
        }
    }

    /** Synchronous probe-count read for the UI poll loop. */
    fun probeCounts(): String = try {
        inputService?.getProbeCounts() ?: "result=not_connected\nleft=0\nright=0"
    } catch (_: Exception) {
        "result=error\nleft=0\nright=0"
    }

    fun setShowTouches(enable: Boolean) {
        connect { try { inputService?.setShowTouches(enable) } catch (_: Exception) {} }
    }

    fun setPointerLocation(enable: Boolean) {
        connect { try { inputService?.setPointerLocation(enable) } catch (_: Exception) {} }
    }

    fun debugToggles(onResult: (String) -> Unit) {
        connect {
            Thread {
                val r = try { inputService?.getDebugToggles() ?: "" } catch (_: Exception) { "" }
                mainHandler.post { onResult(r) }
            }.start()
        }
    }

    fun stop(disableNative: Boolean = true) {
        if (disableNative) {
            disable()
        }
        try {
            Shizuku.unbindUserService(userServiceArgs(), connection, true)
        } catch (_: Exception) {
        }
        inputService = null
        state = State.STOPPED
        lastOwnerPrepareAt = 0L
    }
}
