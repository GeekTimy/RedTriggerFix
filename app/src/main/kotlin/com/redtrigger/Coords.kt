package com.redtrigger

import android.content.Context

/**
 * 坐标工具：方向相关的屏幕尺寸、默认点、以及套用前的「按方向校验/回退默认」（对齐官方 updateValidRect）。
 *
 * UI 与守护共用同一套规则，确保「编辑时看到的默认」与「套用时的合法化」一致。
 */
object Coords {

    /** 物理长边、短边（与设备当前旋转无关）。 */
    fun longShort(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        return maxOf(dm.widthPixels, dm.heightPixels) to minOf(dm.widthPixels, dm.heightPixels)
    }

    /** 指定方向下的屏幕 (宽, 高)。横屏：宽=长边；竖屏：宽=短边。 */
    fun screenSize(landscape: Boolean, context: Context): Pair<Int, Int> {
        val (long, short) = longShort(context)
        return if (landscape) long to short else short to long
    }

    /** 指定方向下、一定落在屏内的默认 L/R 点。 */
    fun defaultPoints(landscape: Boolean, context: Context): Pair<TriggerPoint, TriggerPoint> {
        val (long, short) = longShort(context)
        return if (landscape) {
            // 横屏固定实测默认：左下 / 右下偏中。
            TriggerPoint(800, 600) to TriggerPoint(1700, 600)
        } else {
            // 竖屏：左右水平分布、垂直居中。宽=短边、高=长边。
            TriggerPoint(short / 4, long / 2) to TriggerPoint(short * 3 / 4, long / 2)
        }
    }

    private fun inBounds(p: TriggerPoint, w: Int, h: Int): Boolean =
        p.isSet && p.x in 0 until w && p.y in 0 until h

    /**
     * 套用前合法化：点未设置或越出该方向屏幕范围 → 回退到该方向默认点（官方 updateValidRect 同款）。
     * @return 合法化后的 (left, right)，可直接下发 vendor。
     */
    fun resolve(config: OrientationConfig, landscape: Boolean, context: Context): Pair<TriggerPoint, TriggerPoint> {
        val (w, h) = screenSize(landscape, context)
        val (dl, dr) = defaultPoints(landscape, context)
        val left = if (inBounds(config.left, w, h)) config.left else dl
        val right = if (inBounds(config.right, w, h)) config.right else dr
        return left to right
    }
}
