package com.redtrigger

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object TriggerManager {
    private const val PREFS = "RedMagicTriggerPrefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PACKAGE = "target_package"
    private const val KEY_LEFT_X = "left_x"
    private const val KEY_LEFT_Y = "left_y"
    private const val KEY_RIGHT_X = "right_x"
    private const val KEY_RIGHT_Y = "right_y"
    private const val KEY_MODE = "mode"
    private const val KEY_FIRE = "rapid_fire"
    private const val KEY_POLL = "poll_ms"
    private const val KEY_RECENT_TARGETS = "recent_targets"

    fun isTriggersEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun enableTriggers(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, true)
                .apply()
            context.startForegroundService(Intent(context, TriggerService::class.java))
            true
        } catch (e: Exception) {
            DebugLog.log("TriggerManager", "Enable failed: ${e.message}")
            false
        }
    }

    fun disableTriggers(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, false)
                .apply()
            NativeTgkController.disable()
            context.stopService(Intent(context, TriggerService::class.java))
            true
        } catch (e: Exception) {
            DebugLog.log("TriggerManager", "Disable failed: ${e.message}")
            false
        }
    }

    fun loadConfig(context: Context): TriggerConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return TriggerConfig(
            targetPackage = prefs.getString(KEY_PACKAGE, "com.tencent.tmgp.sgame") ?: "com.tencent.tmgp.sgame",
            leftX = prefs.getInt(KEY_LEFT_X, 1937),
            leftY = prefs.getInt(KEY_LEFT_Y, 490),
            rightX = prefs.getInt(KEY_RIGHT_X, 2144),
            rightY = prefs.getInt(KEY_RIGHT_Y, 393),
            mode = prefs.getInt(KEY_MODE, 6),
            rapidFire = prefs.getInt(KEY_FIRE, 10),
            pollMs = prefs.getLong(KEY_POLL, 2000L)
        )
    }

    fun saveConfig(context: Context, config: TriggerConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PACKAGE, config.targetPackage)
            .putInt(KEY_LEFT_X, config.leftX)
            .putInt(KEY_LEFT_Y, config.leftY)
            .putInt(KEY_RIGHT_X, config.rightX)
            .putInt(KEY_RIGHT_Y, config.rightY)
            .putInt(KEY_MODE, config.mode)
            .putInt(KEY_FIRE, config.rapidFire)
            .putLong(KEY_POLL, config.pollMs)
            .apply()
    }

    fun loadRecentTargets(context: Context): List<String> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_TARGETS, "")
            .orEmpty()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
    }

    fun recordRecentTarget(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        val recent = (listOf(packageName) + loadRecentTargets(context))
            .distinct()
            .take(10)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT_TARGETS, recent.joinToString("\n"))
            .apply()
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
