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

    @Volatile var connectionGeneration = 0L
        private set

    var onForegroundChanged: (() -> Unit)? = null

    private var inputService: IInputService? = null
    private var appContext: Context? = null
    private val pendingReady = CopyOnWriteArrayList<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val foregroundChanged = Runnable { onForegroundChanged?.invoke() }
    private val foregroundListener = object : IForegroundListener.Stub() {
        override fun onForegroundChanged() {
            mainHandler.removeCallbacks(foregroundChanged)
            mainHandler.post(foregroundChanged)
        }
    }
    @Volatile private var lastOwnerPrepareAt = 0L
    private var connection: ServiceConnection? = null
    private var nextConnectAt = 0L
    private val connectTimeout = Runnable {
        if (state == State.CONNECTING) {
            DebugLog.log("NativeTGK", "UserService connection timed out; will retry")
            disconnect()
        }
    }

    private const val OWNER_PREPARE_MIN_INTERVAL_MS = 15_000L

    private fun userServiceArgs(): Shizuku.UserServiceArgs {
        val packageName = appContext?.packageName ?: "com.redtriggerfix"
        return Shizuku.UserServiceArgs(
            ComponentName(packageName, InputService::class.java.name)
        )
            // The master switch owns the backend lifetime, not the app's Recents card.
            .daemon(true)
            .processNameSuffix("tgk")
            .debuggable(true)
            .version(8)
    }

    private fun newConnection() = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (connection !== this || service?.isBinderAlive != true) return
            mainHandler.removeCallbacks(connectTimeout)
            inputService = IInputService.Stub.asInterface(service)
            state = State.CONNECTED
            connectionGeneration++
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
            runCatching { inputService?.watchForeground(foregroundListener) }
                .onFailure { DebugLog.log("NativeTGK", "Task listener unavailable; using fallback checks: ${it.message}") }
            val callbacks = pendingReady.toList()
            pendingReady.clear()
            callbacks.forEach { it.invoke() }
            mainHandler.post(foregroundChanged)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (connection !== this) return
            inputService = null
            state = State.STOPPED
            lastStatus = ""
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
        if (state == State.CONNECTED && inputService?.asBinder()?.isBinderAlive == true) {
            onReady?.invoke()
            return
        }
        if (state == State.CONNECTING) {
            onReady?.let { pendingReady += it }
            return
        }
        try {
            state = State.STOPPED
            if (!hasShizukuPermission() || SystemClock.elapsedRealtime() < nextConnectAt) return
            disconnect()
            onReady?.let { pendingReady += it }
            state = State.CONNECTING
            nextConnectAt = SystemClock.elapsedRealtime() + 5_000L
            val callback = newConnection()
            connection = callback
            mainHandler.postDelayed(connectTimeout, 8_000L)
            Shizuku.bindUserService(userServiceArgs(), callback)
        } catch (e: Exception) {
            disconnect()
            DebugLog.log("NativeTGK", "Bind failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun enable(profile: AppProfile, landscapeNow: Boolean, logResult: Boolean = true): Boolean {
        val backend = inputService ?: return false
        return try {
            val ctx = appContext
            val cfg = profile.configFor(landscapeNow)
            // 套用前按当前方向校验/回退默认，杜绝越界注入（bug2）。
            val (left, right) = if (ctx != null) Coords.resolve(cfg, landscapeNow, ctx) else (cfg.left to cfg.right)
            backend.enableNativeTgk(
                left.x,
                left.y,
                right.x,
                right.y,
                profile.mode,
                profile.rapidFire,
                profile.leftEnabled,
                profile.rightEnabled
            )
            if (logResult) {
                DebugLog.log(
                    "NativeTGK",
                    "Enabled native TGK for ${profile.packageName} (${if (landscapeNow) "landscape" else "portrait"}) L(${left.x},${left.y}) R(${right.x},${right.y})"
                )
            }
            true
        } catch (e: Exception) {
            DebugLog.log("NativeTGK", "Enable failed: ${e.message}")
            false
        }
    }

    // ponytail: vendor exposes enable flags, not mapped coordinates; use coordinate readback if a future ROM exposes it.
    fun statusMatches(profile: AppProfile): Boolean = lastStatus.lineSequence().toSet().containsAll(
        listOf("global=true", "left=${profile.leftEnabled}", "right=${profile.rightEnabled}", "middle=false", "haptic=true")
    )

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

    fun disable(): Boolean {
        val backend = inputService ?: return false
        return try {
            backend.disableNativeTgk()
            refreshStatus()
            DebugLog.log("NativeTGK", "Disabled native TGK")
            true
        } catch (e: Exception) {
            DebugLog.log("NativeTGK", "Disable failed: ${e.message}")
            false
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
        disconnect(removeService = true)
        nextConnectAt = 0L
    }

    fun disconnect(removeService: Boolean = false) {
        mainHandler.removeCallbacks(connectTimeout)
        mainHandler.removeCallbacks(foregroundChanged)
        pendingReady.clear()
        val oldConnection = connection
        connection = null
        if (oldConnection != null || removeService) runCatching {
            Shizuku.unbindUserService(userServiceArgs(), oldConnection, removeService)
        }
        inputService = null
        state = State.STOPPED
        lastStatus = ""
        lastOwnerPrepareAt = 0L
    }
}
