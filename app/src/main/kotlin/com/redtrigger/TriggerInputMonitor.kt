package com.redtrigger

import android.view.KeyEvent
import android.view.MotionEvent

object TriggerInputMonitor {
    private const val LEFT_KEY = KeyEvent.KEYCODE_F7
    private const val RIGHT_KEY = KeyEvent.KEYCODE_F8

    @Volatile var leftHits = 0
        private set

    @Volatile var rightHits = 0
        private set

    @Volatile var lastEvent = "尚未收到肩键输入"
        private set

    @Volatile var touchHits = 0
        private set

    @Volatile var lastTouchEvent = "尚未收到触摸"
        private set

    @Volatile var version = 0L
        private set

    fun record(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) {
            return event.keyCode == LEFT_KEY || event.keyCode == RIGHT_KEY
        }
        return when (event.keyCode) {
            LEFT_KEY -> {
                leftHits++
                lastEvent = "L / F7 / keyCode=${event.keyCode}"
                version++
                DebugLog.log("Input", "Left shoulder key: ${event.keyCode}")
                true
            }
            RIGHT_KEY -> {
                rightHits++
                lastEvent = "R / F8 / keyCode=${event.keyCode}"
                version++
                DebugLog.log("Input", "Right shoulder key: ${event.keyCode}")
                true
            }
            else -> false
        }
    }

    fun recordTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        touchHits++
        lastTouchEvent = "x=${event.x.toInt()}, y=${event.y.toInt()}"
        version++
        DebugLog.log("Input", "Touch down: $lastTouchEvent")
    }

    fun reset() {
        leftHits = 0
        rightHits = 0
        lastEvent = "已清空，等待肩键输入"
        touchHits = 0
        lastTouchEvent = "已清空，等待触摸"
        version++
    }
}
