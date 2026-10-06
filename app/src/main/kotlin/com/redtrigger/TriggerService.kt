package com.redtrigger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.KeyguardManager
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import java.io.FileDescriptor
import java.io.PrintWriter

/**
 * Foreground events plus a low-frequency native-state check.
 *
 * Foreground enters a configured app -> apply that app's profile.
 * Foreground switches to another configured app -> apply the new profile.
 * Foreground leaves all configured apps -> disable() (native clean-release sequence) so the old mapping does not leak globally.
 */
class TriggerService : Service() {
    companion object {
        private const val CHANNEL_ID = "redmagic_tgk_service"
        private const val NOTIFICATION_ID = 1

        const val ACTION_REFRESH = "com.redtriggerfix.action.REFRESH_ACTIVE_PROFILE"
        const val ACTION_SHUTDOWN = "com.redtriggerfix.action.SHUTDOWN_AND_RELEASE"

        @Volatile var isRunning = false
            private set

        @Volatile var nativeActive = false
            private set

        @Volatile var lastForeground = ""
            private set

        @Volatile var activeProfilePackage = ""
            private set

        @Volatile var lastDecision = "未启动"
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var shuttingDown = false
    @Volatile private var shutdownCompleted = false
    @Volatile private var lastAppliedLandscape = false
    private var appliedConnection = -1L
    private var lastAppliedAt = 0L
    private var lastCheckedAt = 0L
    private var lastNotification = ""
    private val verify = Runnable { evaluateForeground("verify") }
    private val eventRecheck = Runnable { evaluateForeground("event-settle") }
    private val healthCheck = object : Runnable {
        override fun run() {
            if (!isRunning || shuttingDown) return
            evaluateForeground("health-check")
            handler.postDelayed(this, ProfileStore.pollMs(this@TriggerService))
        }
    }

    private val foregroundObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            // This vendor signal can remain stale after cancelling Recents.
            // Treat it as a hint; always query ActivityTaskManager via the backend.
            scheduleForegroundChecks("observer")
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        NativeTgkController.init(this)
        NativeTgkController.onForegroundChanged = { scheduleForegroundChecks("task-event") }
        runCatching {
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor("red_magic_forground_pkg"),
                false,
                foregroundObserver
            )
        }
        // RMOS can recreate a sticky service after Recents cleanup without delivering
        // onStartCommand until the activity is opened. Restore from the saved switch here.
        if (ProfileStore.isMasterEnabled(this)) startGuard("create")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_SHUTDOWN || !ProfileStore.isMasterEnabled(this)) {
            startForeground(NOTIFICATION_ID, buildNotification("正在释放肩键"))
            isRunning = true
            shutdownAndRelease(if (action == ACTION_SHUTDOWN) "master-off" else "master-disabled")
            return START_NOT_STICKY
        }

        if (shuttingDown) {
            handler.removeCallbacksAndMessages(null)
            shuttingDown = false
            shutdownCompleted = false
        }

        val reason = if (action == ACTION_REFRESH) "profile-refresh" else "start"
        startGuard(reason)
        return START_STICKY
    }

    private fun startGuard(reason: String) {
        startForeground(NOTIFICATION_ID, buildNotification("守护已启动，等待已配置应用"))
        lastNotification = ""
        isRunning = true
        DebugLog.log("Service", "Started/refresh, reason=$reason")
        evaluateForeground(reason)
        handler.removeCallbacks(healthCheck)
        handler.postDelayed(healthCheck, ProfileStore.pollMs(this))
    }

    private fun scheduleForegroundChecks(reason: String) {
        if (!isRunning || shuttingDown) return
        handler.removeCallbacks(eventRecheck)
        evaluateForeground(reason)
        // Task/vendor events can precede the new focus or the vendor's final TGK write.
        handler.postDelayed(eventRecheck, 150L)
        handler.postDelayed(eventRecheck, 450L)
        handler.postDelayed(eventRecheck, 900L)
    }

    private fun evaluateForeground(reason: String) {
        if (!isRunning || shuttingDown || !ProfileStore.isMasterEnabled(this)) return
        if (NativeTgkController.selfTestRunning) return
        lastCheckedAt = SystemClock.elapsedRealtime()
        NativeTgkController.connect()
        if (NativeTgkController.state != NativeTgkController.State.CONNECTED) {
            if (nativeActive) OverlayPickService.hideMarkers(this)
            nativeActive = false
            lastDecision = "等待 Shizuku / 后端连接"
            return
        }

        if (!getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            releaseIfActive("screen-off/locked", reason)
            return
        }

        val foreground = NativeTgkController.foregroundPackage()
        lastForeground = foreground

        val profile = ProfileStore.findEnabledProfile(this, foreground)
        if (profile != null) {
            applyProfile(profile, foreground, reason)
        } else {
            releaseIfActive(foreground, reason)
        }
    }

    private fun applyProfile(profile: AppProfile, foreground: String, reason: String) {
        val landscapeNow = isLandscapeNow()
        val cfg = profile.configFor(landscapeNow)
        if (!cfg.enabled) {
            // 设备当前方向未启用肩键 -> 干净释放，不残留另一方向的映射。
            releaseIfActive(foreground, "$reason/orientation-off")
            return
        }
        val sameMapping = activeProfilePackage == foreground && lastAppliedLandscape == landscapeNow &&
            appliedConnection == NativeTgkController.connectionGeneration
        NativeTgkController.refreshStatus()
        val confirmed = sameMapping && NativeTgkController.statusMatches(profile)
        if (confirmed && reason != "profile-refresh") {
            if (!nativeActive) {
                nativeActive = true
                updateOverlayMarkers(profile)
                DebugLog.log("Service", "Verified native TGK for $foreground")
            }
            lastDecision = "原生开关已核验"
            updateNotification("已在 ${profile.label} 启用肩键（${if (landscapeNow) "横屏" else "竖屏"}）")
            return
        }

        if (nativeActive) OverlayPickService.hideMarkers(this)
        nativeActive = false
        lastDecision = "原生状态不符，等待恢复"
        // Vendor setters settle asynchronously. Never turn a failed readback into a tight write loop.
        val retryDelay = if (reason == "event-settle") 250L else ProfileStore.pollMs(this)
        if (sameMapping && reason != "profile-refresh" &&
            (reason == "verify" || SystemClock.elapsedRealtime() - lastAppliedAt < retryDelay)) return

        if (!NativeTgkController.enable(profile, landscapeNow)) {
            // Retain ownership after a partial write so leaving the app still releases it.
            activeProfilePackage = foreground
            appliedConnection = -1L
            lastAppliedAt = SystemClock.elapsedRealtime()
            lastDecision = "启用失败，等待重试"
            updateNotification(lastDecision)
            return
        }
        activeProfilePackage = foreground
        lastAppliedLandscape = landscapeNow
        appliedConnection = NativeTgkController.connectionGeneration
        lastAppliedAt = SystemClock.elapsedRealtime()
        lastDecision = "已写入，等待原生开关核验"
        handler.removeCallbacks(verify)
        handler.postDelayed(verify, 250L)
        DebugLog.log(
            "Service",
            "Applied profile=${profile.packageName} (${if (landscapeNow) "landscape" else "portrait"}), reason=$reason"
        )
        updateNotification(lastDecision)
    }

    private fun isLandscapeNow(): Boolean =
        resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    private fun releaseIfActive(foreground: String, reason: String) {
        handler.removeCallbacks(verify)
        if (nativeActive || activeProfilePackage.isNotBlank()) {
            OverlayPickService.hideMarkers(this)
            // 干净释放：用原生 disable 序列，绝不用 releaseTgk —— 实测 vendor releaseTgk 是"全开"
            // (global/left/right 全 true)，离开应用时调它正是"肩键残留"的根因。
            nativeActive = false
            if (!NativeTgkController.disable()) {
                lastDecision = "释放失败，等待重试"
                return
            }
            DebugLog.log(
                "Service",
                "Disabled TGK (clean release) after leaving $activeProfilePackage, foreground=$foreground, reason=$reason"
            )
        }
        nativeActive = false
        activeProfilePackage = ""
        appliedConnection = -1L
        lastDecision = "等待已配置应用"
        updateNotification("等待已配置应用")
    }

    private fun updateOverlayMarkers(profile: AppProfile) {
        if (profile.showOverlayMarkers && Settings.canDrawOverlays(this)) {
            OverlayPickService.showMarkers(this, profile.packageName)
        } else {
            OverlayPickService.hideMarkers(this)
        }
    }

    private fun shutdownAndRelease(reason: String) {
        if (shuttingDown) return
        shuttingDown = true
        handler.removeCallbacks(healthCheck)
        handler.removeCallbacks(verify)
        handler.removeCallbacks(eventRecheck)
        updateNotification("正在释放肩键")
        OverlayPickService.hide(this)
        OverlayPickService.hideMarkers(this)
        DebugLog.log("Service", "Shutdown requested, reason=$reason")

        NativeTgkController.connect {
            if (!isRunning || !shuttingDown || shutdownCompleted) return@connect
            NativeTgkController.disable()
            NativeTgkController.stop(disableNative = false)
            nativeActive = false
            activeProfilePackage = ""
            shutdownCompleted = true
            DebugLog.log("Service", "Shutdown completed, reason=$reason")
            stopSelf()
        }

        handler.postDelayed({
            if (!shutdownCompleted) {
                NativeTgkController.stop(disableNative = true)
                nativeActive = false
                activeProfilePackage = ""
                shutdownCompleted = true
                DebugLog.log("Service", "Shutdown fallback stop, reason=$reason")
                stopSelf()
            }
        }, 2_500L)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RedMagic TGK",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Restores RedMagic shoulder triggers for selected games"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("红魔肩键守护中")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        if (lastNotification == text) return
        lastNotification = text
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 设备旋转 -> 重新评估前台：按新方向选用对应坐标，或在该方向未启用肩键时干净释放。
        val landscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        DebugLog.log("Service", "Config changed, orientation=${if (landscape) "landscape" else "portrait"}")
        if (isRunning && !shuttingDown) {
            scheduleForegroundChecks("rotation")
        }
    }

    override fun onDestroy() {
        NativeTgkController.onForegroundChanged = null
        runCatching { contentResolver.unregisterContentObserver(foregroundObserver) }
        handler.removeCallbacksAndMessages(null)
        OverlayPickService.hide(this)
        OverlayPickService.hideMarkers(this)
        if (ProfileStore.isMasterEnabled(this) && !shuttingDown) {
            NativeTgkController.disconnect()
        } else {
            NativeTgkController.stop(disableNative = !shutdownCompleted)
        }
        nativeActive = false
        activeProfilePackage = ""
        isRunning = false
        lastDecision = "已停止"
        DebugLog.log("Service", "Destroyed; shutdownCompleted=$shutdownCompleted")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        writer.println("masterEnabled=${ProfileStore.isMasterEnabled(this)} running=$isRunning shuttingDown=$shuttingDown")
        writer.println("backend=${NativeTgkController.state} generation=${NativeTgkController.connectionGeneration}")
        writer.println("foreground=$lastForeground profile=$activeProfilePackage landscape=$lastAppliedLandscape nativeActive=$nativeActive")
        writer.println("decision=$lastDecision checkedAt=$lastCheckedAt appliedAt=$lastAppliedAt pollMs=${ProfileStore.pollMs(this)}")
        writer.println("nativeStatus=${NativeTgkController.lastStatus.replace('\n', ' ')}")
        DebugLog.getEntries().takeLast(20).forEach(writer::println)
    }
}
