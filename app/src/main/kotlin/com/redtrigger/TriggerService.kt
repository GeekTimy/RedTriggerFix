package com.redtrigger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * 前台守护：轮询当前前台包名，找到它对应的 enabled [AppProfile] 后按该 profile 启用肩键。
 * 这就是 keymapper 式 per-app 行为——打开哪个已配置应用，就自动套那个应用的肩键。
 *
 * 守"单 writer"纪律：离开已配置应用时不主动 disable，避免在服务存活期反复与系统状态机抢全局 TGK。
 */
class TriggerService : Service() {
    companion object {
        private const val CHANNEL_ID = "redmagic_tgk_service"
        private const val NOTIFICATION_ID = 1

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
    private var pollMs = ProfileStore.DEFAULT_POLL_MS

    private val tick = object : Runnable {
        override fun run() {
            pollOnce()
            handler.postDelayed(this, pollMs)
        }
    }

    @Volatile private var lastObservedForeground = ""

    // 事件驱动：监听 Settings.Global（红魔把当前前台写进 red_magic_forground_pkg）。
    // 监听 root URI + notifyForDescendants，确保一定收到该 key 的变化；前台真变了才立即评估切换。
    private val foregroundObserver = object : android.database.ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            val fg = android.provider.Settings.Global
                .getString(contentResolver, "red_magic_forground_pkg").orEmpty()
            if (fg.isNotBlank() && fg != "null" && fg != lastObservedForeground) {
                lastObservedForeground = fg
                pollOnce()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        NativeTgkController.init(this)
        runCatching {
            contentResolver.registerContentObserver(
                android.provider.Settings.Global.CONTENT_URI,
                true,
                foregroundObserver
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pollMs = ProfileStore.pollMs(this)
        startForeground(NOTIFICATION_ID, buildNotification("守护已启动，等待已配置应用"))
        isRunning = true
        DebugLog.log("Service", "Started, pollMs=$pollMs")
        NativeTgkController.connect {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
        return START_STICKY
    }

    private fun pollOnce() {
        if (NativeTgkController.state != NativeTgkController.State.CONNECTED) {
            NativeTgkController.connect()
            return
        }
        val foreground = NativeTgkController.foregroundPackage()
        lastForeground = foreground
        val profile = ProfileStore.findEnabledProfile(this, foreground)
        when {
            profile != null -> {
                val firstApply = !nativeActive || activeProfilePackage != foreground
                NativeTgkController.enable(profile, logResult = firstApply)
                if (firstApply) {
                    nativeActive = true
                    activeProfilePackage = foreground
                    updateNotification("已在 ${profile.label} 启用肩键")
                }
            }
            nativeActive -> {
                // 离开已配置应用：守单 writer 纪律，不主动 disable。
                nativeActive = false
                activeProfilePackage = ""
                DebugLog.log("Service", "Left configured app; keeping native TGK state unchanged")
                updateNotification("等待已配置应用")
            }
        }
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
        handler.removeCallbacks(tick)
        NativeTgkController.stop(disableNative = false)
        nativeActive = false
        activeProfilePackage = ""
        isRunning = false
        DebugLog.log("Service", "Destroyed; native TGK state left unchanged")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
