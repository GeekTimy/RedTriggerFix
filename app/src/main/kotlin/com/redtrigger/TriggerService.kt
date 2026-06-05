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
    }

    private val handler = Handler(Looper.getMainLooper())
    private var config = TriggerConfig()

    private val tick = object : Runnable {
        override fun run() {
            pollOnce()
            handler.postDelayed(this, config.pollMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        NativeTgkController.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        config = TriggerManager.loadConfig(this)
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring ${config.targetPackage}"))
        isRunning = true
        DebugLog.log("Service", "Started with $config")
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
        val shouldEnable = foreground == config.targetPackage
        when {
            shouldEnable -> {
                NativeTgkController.enable(config, logResult = !nativeActive)
                if (!nativeActive) {
                    nativeActive = true
                    updateNotification("Active in ${config.targetPackage}")
                }
            }
            nativeActive -> {
                NativeTgkController.disable()
                nativeActive = false
                updateNotification("Waiting for ${config.targetPackage}")
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
            .setContentTitle("RedMagic Trigger active")
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
        handler.removeCallbacks(tick)
        NativeTgkController.disable()
        nativeActive = false
        isRunning = false
        DebugLog.log("Service", "Destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
