package com.redtrigger

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Per-app 肩键配置的持久化层（keymapper 式多配置），取代旧的全局单一 TriggerManager。
 *
 * - profiles 以 JSON 数组存于单一 SharedPreferences（统一用 [PREFS]，
 *   消除历史上 "RedMagicTriggerPrefs" / "RedTriggerPrefs" 两个文件并存的隐患）。
 * - 首次访问时把旧的单 config（RedMagicTriggerPrefs）迁移成一个横屏 profile。
 * - 同时承担守护总开关与 TriggerService 的启停。
 */
object ProfileStore {
    private const val PREFS = "RedTriggerPrefs"
    private const val OLD_PREFS = "RedMagicTriggerPrefs"

    private const val K_PROFILES = "profiles_json"
    private const val K_MASTER = "master_enabled"
    private const val K_POLL = "poll_ms"
    private const val K_RECENT = "recent_targets"
    private const val K_MIGRATED = "migrated_v1"

    const val DEFAULT_POLL_MS = 2000L
    const val MIN_POLL_MS = 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------- profiles ----------------

    fun loadProfiles(context: Context): List<AppProfile> {
        migrateIfNeeded(context)
        return AppProfile.listFromJson(prefs(context).getString(K_PROFILES, null))
    }

    fun saveProfiles(context: Context, list: List<AppProfile>) {
        prefs(context).edit().putString(K_PROFILES, AppProfile.listToJson(list)).apply()
    }

    fun findProfile(context: Context, packageName: String): AppProfile? =
        loadProfiles(context).firstOrNull { it.packageName == packageName }

    fun findEnabledProfile(context: Context, packageName: String): AppProfile? =
        loadProfiles(context).firstOrNull { it.packageName == packageName && it.enabled }

    fun upsertProfile(context: Context, profile: AppProfile) {
        val list = loadProfiles(context).toMutableList()
        val idx = list.indexOfFirst { it.packageName == profile.packageName }
        if (idx >= 0) list[idx] = profile else list.add(0, profile)
        saveProfiles(context, list)
        recordRecent(context, profile.packageName)
        refreshGuard(context)
    }

    fun removeProfile(context: Context, packageName: String) {
        saveProfiles(context, loadProfiles(context).filterNot { it.packageName == packageName })
        refreshGuard(context)
    }

    fun setProfileEnabled(context: Context, packageName: String, enabled: Boolean) {
        saveProfiles(
            context,
            loadProfiles(context).map {
                if (it.packageName == packageName) it.copy(enabled = enabled) else it
            }
        )
        refreshGuard(context)
    }

    // ---------------- master switch + service ----------------

    fun isMasterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_MASTER, false)

    fun pollMs(context: Context): Long =
        prefs(context).getLong(K_POLL, DEFAULT_POLL_MS).coerceAtLeast(MIN_POLL_MS)

    fun setPollMs(context: Context, value: Long) {
        prefs(context).edit().putLong(K_POLL, value.coerceAtLeast(MIN_POLL_MS)).apply()
    }

    fun enableTriggers(context: Context): Boolean {
        return try {
            prefs(context).edit().putBoolean(K_MASTER, true).apply()
            context.startForegroundService(Intent(context, TriggerService::class.java))
            true
        } catch (e: Exception) {
            DebugLog.log("ProfileStore", "Enable failed: ${e.message}")
            false
        }
    }

    fun disableTriggers(context: Context): Boolean {
        return try {
            prefs(context).edit().putBoolean(K_MASTER, false).apply()
            OverlayPickService.hide(context)
            context.startForegroundService(
                Intent(context, TriggerService::class.java).apply {
                    action = TriggerService.ACTION_SHUTDOWN
                }
            )
            true
        } catch (e: Exception) {
            DebugLog.log("ProfileStore", "Disable failed: ${e.message}")
            runCatching { NativeTgkController.disable() }
            runCatching { context.stopService(Intent(context, TriggerService::class.java)) }
            false
        }
    }

    // ---------------- recent targets ----------------

    fun recentTargets(context: Context): List<String> =
        prefs(context).getString(K_RECENT, "").orEmpty()
            .split("\n").map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)

    fun recordRecent(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        val recent = (listOf(packageName) + recentTargets(context)).distinct().take(10)
        prefs(context).edit().putString(K_RECENT, recent.joinToString("\n")).apply()
    }

    private fun refreshGuard(context: Context) {
        if (!isMasterEnabled(context)) return
        runCatching {
            context.startForegroundService(
                Intent(context, TriggerService::class.java).apply {
                    action = TriggerService.ACTION_REFRESH
                }
            )
        }.onFailure {
            DebugLog.log("ProfileStore", "Guard refresh failed: ${it.message}")
        }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun labelFor(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm)?.toString().orEmpty().ifBlank { packageName }
    } catch (_: Exception) {
        packageName
    }

    // ---------------- migration ----------------

    /** 把旧的单一 TriggerConfig（RedMagicTriggerPrefs）迁移成一个横屏 profile，只做一次。 */
    private fun migrateIfNeeded(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(K_MIGRATED, false)) return
        if (p.contains(K_PROFILES)) {
            p.edit().putBoolean(K_MIGRATED, true).apply()
            return
        }
        val old = context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
        val oldPkg = old.getString("target_package", null)
        if (!oldPkg.isNullOrBlank()) {
            val profile = AppProfile(
                packageName = oldPkg,
                label = labelFor(context, oldPkg),
                enabled = true,
                orientation = ScreenOrientation.LANDSCAPE, // 旧坐标是横屏王者坐标
                left = TriggerPoint(old.getInt("left_x", 1937), old.getInt("left_y", 490)),
                right = TriggerPoint(old.getInt("right_x", 2144), old.getInt("right_y", 393)),
                mode = old.getInt("mode", AppProfile.MODE_RAPID),
                rapidFire = old.getInt("rapid_fire", 10),
                leftEnabled = true,
                rightEnabled = true
            )
            p.edit().putString(K_PROFILES, AppProfile.listToJson(listOf(profile))).apply()
        }
        if (old.contains("poll_ms")) {
            p.edit().putLong(K_POLL, old.getLong("poll_ms", DEFAULT_POLL_MS)).apply()
        }
        val oldRecent = old.getString("recent_targets", null)
        if (!oldRecent.isNullOrBlank() && !p.contains(K_RECENT)) {
            p.edit().putString(K_RECENT, oldRecent).apply()
        }
        p.edit().putBoolean(K_MIGRATED, true).apply()
    }
}
