package com.redtrigger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings

/**
 * Event-driven per-app TGK guard.
 *
 * Foreground enters a configured app -> apply that app's profile.
 * Foreground switches to another configured app -> apply the new profile.
 * Foreground leaves all configured apps -> releaseTgk() so the old mapping does not leak globally.
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
    }

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var lastObservedForeground = ""
    @Volatile private var shuttingDown = false
    @Volatile private var shutdownCompleted = false

    private val foregroundObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            val fg = Settings.Global
                .getString(contentResolver, "red_magic_forground_pkg")
                .orEmpty()
            if (fg.isNotBlank() && fg != "null" && fg != lastObservedForeground) {
                lastObservedForeground = fg
                evaluateForeground("observer")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        NativeTgkController.init(this)
        runCatching {
            contentResolver.registerContentObserver(
                Settings.Global.CONTENT_URI,
                true,
                foregroundObserver
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("守护已启动，等待已配置应用"))
        isRunning = true

        val action = intent?.action
        if (action == ACTION_SHUTDOWN || !ProfileStore.isMasterEnabled(this)) {
            shutdownAndRelease(if (action == ACTION_SHUTDOWN) "master-off" else "master-disabled")
            return START_NOT_STICKY
        }

        val reason = if (action == ACTION_REFRESH) "profile-refresh" else "start"
        DebugLog.log("Service", "Started/refresh, reason=$reason")
        NativeTgkController.connect {
            evaluateForeground(reason)
        }
        return START_STICKY
    }

    private fun evaluateForeground(reason: String) {
        if (NativeTgkController.state != NativeTgkController.State.CONNECTED) {
            NativeTgkController.connect { evaluateForeground("connected") }
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
        val profileChanged = !nativeActive || activeProfilePackage != foreground
        if (!profileChanged && reason != "profile-refresh") return

        NativeTgkController.enable(profile, logResult = profileChanged)
        updateOverlayMarkers(profile)
        nativeActive = true
        activeProfilePackage = foreground
        DebugLog.log("Service", "Applied profile=${profile.packageName}, reason=$reason")
        updateNotification("已在 ${profile.label} 启用肩键")
    }

    private fun releaseIfActive(foreground: String, reason: String) {
        if (nativeActive) {
            OverlayPickService.hideMarkers(this)
            NativeTgkController.releaseTgk()
            DebugLog.log(
                "Service",
                "Released TGK after leaving $activeProfilePackage, foreground=$foreground, reason=$reason"
            )
        }
        nativeActive = false
        activeProfilePackage = ""
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
        updateNotification("正在释放肩键")
        OverlayPickService.hide(this)
        OverlayPickService.hideMarkers(this)
        DebugLog.log("Service", "Shutdown requested, reason=$reason")

        NativeTgkController.connect {
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
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        runCatching { contentResolver.unregisterContentObserver(foregroundObserver) }
        handler.removeCallbacksAndMessages(null)
        OverlayPickService.hide(this)
        OverlayPickService.hideMarkers(this)
        NativeTgkController.stop(disableNative = !shutdownCompleted)
        nativeActive = false
        activeProfilePackage = ""
        isRunning = false
        DebugLog.log("Service", "Destroyed; shutdownCompleted=$shutdownCompleted")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
