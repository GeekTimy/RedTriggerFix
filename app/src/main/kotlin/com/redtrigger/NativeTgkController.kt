package com.redtrigger

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList

object NativeTgkController {
    enum class State { STOPPED, CONNECTING, CONNECTED }

    @Volatile var state: State = State.STOPPED
        private set

    @Volatile var lastStatus: String = ""
        private set

    @Volatile var lastForegroundPackage: String = ""
        private set

    private var inputService: IInputService? = null
    private var appContext: Context? = null
    private val pendingReady = CopyOnWriteArrayList<() -> Unit>()

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.redtrigger", InputService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("tgk")
        .debuggable(true)
        .version(2)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            inputService = IInputService.Stub.asInterface(service)
            state = State.CONNECTED
            DebugLog.log("NativeTGK", "Shizuku UserService connected")
            try {
                inputService?.grantPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
            } catch (e: Exception) {
                DebugLog.log("NativeTGK", "Grant failed: ${e.message}")
            }
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
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission() {
        try {
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
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (e: Exception) {
            state = State.STOPPED
            DebugLog.log("NativeTGK", "Bind failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun enable(config: TriggerConfig, logResult: Boolean = true) {
        connect {
            try {
                inputService?.enableNativeTgk(
                    config.leftX,
                    config.leftY,
                    config.rightX,
                    config.rightY,
                    config.mode,
                    config.rapidFire
                )
                refreshStatus()
                if (logResult) {
                    DebugLog.log("NativeTGK", "Enabled native TGK")
                }
            } catch (e: Exception) {
                DebugLog.log("NativeTGK", "Enable failed: ${e.message}")
            }
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

    fun stop() {
        disable()
        try {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        } catch (_: Exception) {
        }
        inputService = null
        state = State.STOPPED
    }
}

data class TriggerConfig(
    val targetPackage: String = "com.tencent.tmgp.sgame",
    val leftX: Int = 1937,
    val leftY: Int = 490,
    val rightX: Int = 2144,
    val rightY: Int = 393,
    val mode: Int = 6,
    val rapidFire: Int = 10,
    val pollMs: Long = 1000L
)
