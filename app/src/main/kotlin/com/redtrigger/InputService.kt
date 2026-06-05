package com.redtrigger

import android.content.ComponentName
import android.os.IBinder
import android.util.Log
import java.lang.reflect.Method

/**
 * Shizuku UserService running as shell.
 *
 * This service calls RedMagic's vendor-extended IInputManager methods directly.
 * It intentionally does not inject Android input events and does not create a
 * virtual gamepad. The system's TGK/native layer remains responsible for
 * converting shoulder-key presses into touch events.
 */
class InputService : IInputService.Stub() {
    companion object {
        private const val TAG = "InputService"
        private const val LEFT_TGK = 137
        private const val RIGHT_TGK = 138
        private const val MIDDLE_TGK = 136
    }

    private val inputManager: Any by lazy {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager
            .getMethod("getService", String::class.java)
            .invoke(null, "input") as IBinder
        val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
        stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
    }

    override fun grantPermission(permission: String) {
        try {
            Runtime.getRuntime()
                .exec(arrayOf("pm", "grant", "com.redtrigger", permission))
                .waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "grantPermission failed: ${e.message}")
        }
    }

    override fun enableNativeTgk(
        leftX: Int,
        leftY: Int,
        rightX: Int,
        rightY: Int,
        mode: Int,
        rapidFireCount: Int
    ) {
        call("setGameLeftKeyLinkFunction", arrayOf(Int::class.javaPrimitiveType!!), 0)
        call("setGameRightKeyLinkFunction", arrayOf(Int::class.javaPrimitiveType!!), 0)
        call("setGameMiddleKeyLinkFunction", arrayOf(Int::class.javaPrimitiveType!!), 0)
        call("setGlobalKeyEnable", arrayOf(Boolean::class.javaPrimitiveType!!), true)
        call("enableTgkDrive", arrayOf(Boolean::class.javaPrimitiveType!!), true)
        call("enableLeftTgkDrive", arrayOf(Boolean::class.javaPrimitiveType!!), true)
        call("enableRightTgkDrive", arrayOf(Boolean::class.javaPrimitiveType!!), true)

        call("setTgkPoint", arrayOf(IntArray::class.java, IntArray::class.java, Int::class.javaPrimitiveType!!), intArrayOf(leftX, leftY), intArrayOf(-1, -1), LEFT_TGK)
        call("setTgkPoint", arrayOf(IntArray::class.java, IntArray::class.java, Int::class.javaPrimitiveType!!), intArrayOf(rightX, rightY), intArrayOf(-1, -1), RIGHT_TGK)
        call("setTgkPoint", arrayOf(IntArray::class.java, IntArray::class.java, Int::class.javaPrimitiveType!!), intArrayOf(-1, -1), intArrayOf(-1, -1), MIDDLE_TGK)

        if (mode == 6) {
            call("setTgkRapidFireCount", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!), rapidFireCount, LEFT_TGK)
            call("setTgkRapidFireCount", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!), rapidFireCount, RIGHT_TGK)
        }
        call("setTgkMode", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!), mode, LEFT_TGK)
        call("setTgkMode", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!), mode, RIGHT_TGK)
        call("setTgkSensitivity", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!), 2, LEFT_TGK)
        call("setTgkSensitivity", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!), 2, RIGHT_TGK)
        call("setLeftGameKeyEnable", arrayOf(Boolean::class.javaPrimitiveType!!), true)
        call("setRightGameKeyEnable", arrayOf(Boolean::class.javaPrimitiveType!!), true)
        call("setMiddleGameKeyEnable", arrayOf(Boolean::class.javaPrimitiveType!!), false)
        call("setTouchHapticFeedbackEnable", arrayOf(Boolean::class.javaPrimitiveType!!), true)
        call("setTgkTopEffectEnable", arrayOf(Boolean::class.javaPrimitiveType!!), false)
        call("setTgkCenterEffectEnable", arrayOf(Boolean::class.javaPrimitiveType!!), false)
        call("setTgkTransparency", arrayOf(Int::class.javaPrimitiveType!!), 0)
    }

    override fun disableNativeTgk() {
        call("setTouchHapticFeedbackEnable", arrayOf(Boolean::class.javaPrimitiveType!!), false)
        call("setTgkTopEffectEnable", arrayOf(Boolean::class.javaPrimitiveType!!), false)
        call("setTgkCenterEffectEnable", arrayOf(Boolean::class.javaPrimitiveType!!), false)
        call("setLeftGameKeyEnable", arrayOf(Boolean::class.javaPrimitiveType!!), false)
        call("setRightGameKeyEnable", arrayOf(Boolean::class.javaPrimitiveType!!), false)
        call("setMiddleGameKeyEnable", arrayOf(Boolean::class.javaPrimitiveType!!), false)
        call("setGameLeftKeyLinkFunction", arrayOf(Int::class.javaPrimitiveType!!), 0)
        call("setGameRightKeyLinkFunction", arrayOf(Int::class.javaPrimitiveType!!), 0)
        call("setGameMiddleKeyLinkFunction", arrayOf(Int::class.javaPrimitiveType!!), 0)
        call("setGlobalKeyEnable", arrayOf(Boolean::class.javaPrimitiveType!!), false)
    }

    override fun getNativeTgkStatus(): String {
        return listOf(
            "global=${ret("isGlobalKeyEnable")}",
            "left=${ret("isLeftGameKeyEnable")}",
            "right=${ret("isRightGameKeyEnable")}",
            "middle=${ret("isMiddleGameKeyEnable")}",
            "haptic=${ret("isTouchHapticFeedbackEnable")}"
        ).joinToString("\n")
    }

    override fun getForegroundPackage(): String {
        val byActivity = getForegroundPackageByActivityTaskManager()
        return byActivity.ifBlank { getForegroundPackageBySettings() }
    }

    private fun getForegroundPackageByActivityTaskManager(): String {
        return try {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val service = atmClass.getMethod("getService").invoke(null) ?: return ""
            val taskInfo = service.javaClass.getMethod("getFocusedRootTaskInfo").invoke(service)
                ?: return ""
            val topActivity = taskInfo.javaClass.getField("topActivity").get(taskInfo)
            if (topActivity is ComponentName) topActivity.packageName else ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun getForegroundPackageBySettings(): String {
        return try {
            val proc = ProcessBuilder("settings", "get", "global", "red_magic_forground_pkg")
                .redirectErrorStream(true)
                .start()
            val value = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (value == "null") "" else value
        } catch (_: Exception) {
            ""
        }
    }

    override fun destroy() {
        disableNativeTgk()
    }

    private fun call(name: String, types: Array<Class<*>>, vararg args: Any) {
        try {
            val method: Method = inputManager.javaClass.getMethod(name, *types)
            method.invoke(inputManager, *args)
            Log.i(TAG, "OK $name")
        } catch (e: Exception) {
            Log.w(TAG, "Failed $name: ${e.message}", e)
        }
    }

    private fun ret(name: String): String {
        return try {
            inputManager.javaClass.getMethod(name).invoke(inputManager)?.toString() ?: "null"
        } catch (e: Exception) {
            "error:${e.javaClass.simpleName}"
        }
    }
}
