package com.redtrigger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.redtrigger.ui.MainScreen
import com.redtrigger.ui.theme.RedTriggerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DebugLog.log("Activity", "MainActivity created")

        val triggersEnabled = ProfileStore.isMasterEnabled(this)
        val shizukuOk = try {
            rikka.shizuku.Shizuku.pingBinder() &&
                rikka.shizuku.Shizuku.checkSelfPermission() ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        if (shizukuOk && triggersEnabled && !TriggerService.isRunning) {
            startForegroundService(android.content.Intent(this, TriggerService::class.java))
        }

        setContent {
            RedTriggerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 切到别的界面/应用时自动停止自测并释放，避免临时映射残留。
        if (NativeTgkController.selfTestRunning) {
            NativeTgkController.stopSelfTest()
        }
    }

    override fun onStop() {
        super.onStop()
        // 双保险：完全退到后台也确保自测已停止并释放。
        if (NativeTgkController.selfTestRunning) {
            NativeTgkController.stopSelfTest()
        }
    }
}
