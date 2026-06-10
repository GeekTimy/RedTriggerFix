package com.redtrigger

import org.json.JSONArray
import org.json.JSONObject

/**
 * 屏幕方向：决定坐标系基准。游戏多为横屏，本 app 自身 UI 为竖屏。
 * 同一个数字坐标在横/竖屏下含义不同，因此坐标必须显式记录方向，
 * 从源头避免横竖屏坐标越界（历史 bug2：竖屏套用横屏坐标 → X 越界 → 注入屏外）。
 */
enum class ScreenOrientation { LANDSCAPE, PORTRAIT }

/** 单个肩键的触摸注入点（屏幕物理坐标，含义随所在 [OrientationConfig] 的方向解释）。 */
data class TriggerPoint(val x: Int, val y: Int) {
    val isSet: Boolean get() = x >= 0 && y >= 0
}

/**
 * 某一屏幕方向（横屏或竖屏）下的肩键配置：
 * - [enabled]：该方向是否使用肩键（= UI 上的横屏/竖屏开关）。关 → 设备处于该方向时干净释放。
 * - [left]/[right]：该方向下左右肩键的触摸点（该方向坐标系内的合法屏幕坐标）。
 */
data class OrientationConfig(
    val enabled: Boolean = false,
    val left: TriggerPoint = TriggerPoint(-1, -1),
    val right: TriggerPoint = TriggerPoint(-1, -1)
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(K_EN, enabled)
        put(K_LX, left.x); put(K_LY, left.y)
        put(K_RX, right.x); put(K_RY, right.y)
    }

    companion object {
        private const val K_EN = "en"
        private const val K_LX = "lx"
        private const val K_LY = "ly"
        private const val K_RX = "rx"
        private const val K_RY = "ry"

        fun fromJson(o: JSONObject): OrientationConfig = OrientationConfig(
            enabled = o.optBoolean(K_EN, false),
            left = TriggerPoint(o.optInt(K_LX, -1), o.optInt(K_LY, -1)),
            right = TriggerPoint(o.optInt(K_RX, -1), o.optInt(K_RY, -1))
        )
    }
}

/**
 * 每个应用一份的肩键配置（keymapper 式 per-app profile）。
 *
 * 每个应用同时持有 [landscape] 与 [portrait] 两套方向配置，进入应用时由守护按
 * 设备当前方向选用对应一套；该方向 [OrientationConfig.enabled] 为 false 则该方向不启用肩键。
 */
data class AppProfile(
    val packageName: String,
    val label: String,
    val enabled: Boolean = true,
    val landscape: OrientationConfig = OrientationConfig(),
    val portrait: OrientationConfig = OrientationConfig(),
    val mode: Int = MODE_SINGLE,
    val rapidFire: Int = 10,
    val leftEnabled: Boolean = true,
    val rightEnabled: Boolean = true,
    val showOverlayMarkers: Boolean = false
) {
    /** 取设备当前方向对应的那套配置。 */
    fun configFor(landscape: Boolean): OrientationConfig = if (landscape) this.landscape else this.portrait

    /** 返回把某一方向替换为 [config] 后的新 profile。 */
    fun withConfig(landscape: Boolean, config: OrientationConfig): AppProfile =
        if (landscape) copy(landscape = config) else copy(portrait = config)

    /** 至少有一个方向启用了肩键。 */
    val anyOrientationEnabled: Boolean get() = landscape.enabled || portrait.enabled

    fun toJson(): JSONObject = JSONObject().apply {
        put(K_PKG, packageName)
        put(K_LABEL, label)
        put(K_ENABLED, enabled)
        put(K_LANDSCAPE, landscape.toJson())
        put(K_PORTRAIT, portrait.toJson())
        put(K_MODE, mode); put(K_FIRE, rapidFire)
        put(K_LEN, leftEnabled); put(K_REN, rightEnabled)
        put(K_SHOW_MARKERS, showOverlayMarkers)
    }

    companion object {
        const val MODE_SINGLE = 0
        const val MODE_RAPID = 6

        private const val K_PKG = "pkg"
        private const val K_LABEL = "label"
        private const val K_ENABLED = "enabled"
        private const val K_LANDSCAPE = "landscape"
        private const val K_PORTRAIT = "portrait"
        private const val K_MODE = "mode"
        private const val K_FIRE = "fire"
        private const val K_LEN = "lEn"
        private const val K_REN = "rEn"
        private const val K_SHOW_MARKERS = "showMarkers"

        // 旧（单方向）格式的键，仅用于一次性兼容读取。
        private const val OLD_K_ORIENT = "orientation"
        private const val OLD_K_LX = "lx"
        private const val OLD_K_LY = "ly"
        private const val OLD_K_RX = "rx"
        private const val OLD_K_RY = "ry"

        fun fromJson(o: JSONObject): AppProfile {
            val landscape: OrientationConfig
            val portrait: OrientationConfig
            if (o.has(K_LANDSCAPE) || o.has(K_PORTRAIT)) {
                landscape = o.optJSONObject(K_LANDSCAPE)?.let { OrientationConfig.fromJson(it) } ?: OrientationConfig()
                portrait = o.optJSONObject(K_PORTRAIT)?.let { OrientationConfig.fromJson(it) } ?: OrientationConfig()
            } else {
                // 旧单方向格式 → 迁移：把坐标放进当时方向那套并启用，另一套留空禁用。
                val wasLandscape = runCatching {
                    ScreenOrientation.valueOf(o.optString(OLD_K_ORIENT, ScreenOrientation.PORTRAIT.name))
                }.getOrDefault(ScreenOrientation.PORTRAIT) == ScreenOrientation.LANDSCAPE
                val cfg = OrientationConfig(
                    enabled = true,
                    left = TriggerPoint(o.optInt(OLD_K_LX, -1), o.optInt(OLD_K_LY, -1)),
                    right = TriggerPoint(o.optInt(OLD_K_RX, -1), o.optInt(OLD_K_RY, -1))
                )
                landscape = if (wasLandscape) cfg else OrientationConfig()
                portrait = if (wasLandscape) OrientationConfig() else cfg
            }
            return AppProfile(
                packageName = o.getString(K_PKG),
                label = o.optString(K_LABEL, o.getString(K_PKG)),
                enabled = o.optBoolean(K_ENABLED, true),
                landscape = landscape,
                portrait = portrait,
                mode = o.optInt(K_MODE, MODE_SINGLE),
                rapidFire = o.optInt(K_FIRE, 10),
                leftEnabled = o.optBoolean(K_LEN, true),
                rightEnabled = o.optBoolean(K_REN, true),
                showOverlayMarkers = o.optBoolean(K_SHOW_MARKERS, false)
            )
        }

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
