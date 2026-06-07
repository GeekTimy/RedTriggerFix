package com.redtrigger

import org.json.JSONArray
import org.json.JSONObject

/**
 * 屏幕方向：决定坐标系基准。游戏多为横屏，本 app 自身 UI 为竖屏。
 * 同一个数字坐标在横/竖屏下含义不同，因此每个配置必须显式记录方向，
 * 从源头避免横竖屏坐标越界（即历史 bug2：竖屏套用横屏坐标 → X 越界 → 注入屏外）。
 */
enum class ScreenOrientation { LANDSCAPE, PORTRAIT }

/** 单个肩键的触摸注入点（屏幕物理坐标，含义随 [AppProfile.orientation] 解释）。 */
data class TriggerPoint(val x: Int, val y: Int) {
    val isSet: Boolean get() = x >= 0 && y >= 0
}

/**
 * 每个应用一份的肩键配置（keymapper 式 per-app profile），取代旧的全局单一 TriggerConfig。
 *
 * 打开某个应用时，前台守护会找到它对应的 enabled profile，按该 profile 的
 * 坐标 / 模式 / 方向启用肩键。配置以 JSON 数组形式分条持久化（见 [ProfileStore]）。
 */
data class AppProfile(
    val packageName: String,
    val label: String,
    val enabled: Boolean = true,
    val orientation: ScreenOrientation = ScreenOrientation.LANDSCAPE,
    val left: TriggerPoint,
    val right: TriggerPoint,
    val mode: Int = MODE_RAPID,
    val rapidFire: Int = 10
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(K_PKG, packageName)
        put(K_LABEL, label)
        put(K_ENABLED, enabled)
        put(K_ORIENT, orientation.name)
        put(K_LX, left.x); put(K_LY, left.y)
        put(K_RX, right.x); put(K_RY, right.y)
        put(K_MODE, mode); put(K_FIRE, rapidFire)
    }

    companion object {
        const val MODE_SINGLE = 0
        const val MODE_RAPID = 6

        private const val K_PKG = "pkg"
        private const val K_LABEL = "label"
        private const val K_ENABLED = "enabled"
        private const val K_ORIENT = "orientation"
        private const val K_LX = "lx"
        private const val K_LY = "ly"
        private const val K_RX = "rx"
        private const val K_RY = "ry"
        private const val K_MODE = "mode"
        private const val K_FIRE = "fire"

        fun fromJson(o: JSONObject): AppProfile = AppProfile(
            packageName = o.getString(K_PKG),
            label = o.optString(K_LABEL, o.getString(K_PKG)),
            enabled = o.optBoolean(K_ENABLED, true),
            orientation = runCatching {
                ScreenOrientation.valueOf(o.optString(K_ORIENT, ScreenOrientation.LANDSCAPE.name))
            }.getOrDefault(ScreenOrientation.LANDSCAPE),
            left = TriggerPoint(o.optInt(K_LX, -1), o.optInt(K_LY, -1)),
            right = TriggerPoint(o.optInt(K_RX, -1), o.optInt(K_RY, -1)),
            mode = o.optInt(K_MODE, MODE_RAPID),
            rapidFire = o.optInt(K_FIRE, 10)
        )

        fun listToJson(profiles: List<AppProfile>): String {
            val arr = JSONArray()
            profiles.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String?): List<AppProfile> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            }.getOrDefault(emptyList())
        }
    }
}
