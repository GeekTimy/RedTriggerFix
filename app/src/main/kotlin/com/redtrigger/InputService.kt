package com.redtrigger

import android.content.ComponentName
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

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

    private data class TgkInputDevice(
        val path: String,
        val side: String,
        val name: String
    )

    @Volatile private var probeLeft = 0
    @Volatile private var probeRight = 0
    @Volatile private var probeResult = "idle"
    @Volatile private var probeDevicesInfo = ""
    private val probeProcesses = java.util.concurrent.CopyOnWriteArrayList<java.lang.Process>()
    private val probeThreads = java.util.concurrent.CopyOnWriteArrayList<Thread>()

    private val inputManager: Any by lazy {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager
            .getMethod("getService", String::class.java)
            .invoke(null, "input") as IBinder
        val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
        stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
    }

    override fun grantPermission(packageName: String, permission: String) {
        try {
            Runtime.getRuntime()
                .exec(arrayOf("pm", "grant", packageName, permission))
                .waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "grantPermission failed: ${e.message}")
        }
    }

    override fun prepareNativeOwner(ownerPackageName: String): String {
        val owner = ownerPackageName.ifBlank { "com.redtriggerfix" }
        val myPid = Process.myPid()
        val script = """
            CURRENT_PID="$myPid"
            OWNER="$owner"

            force_stop_if_present() {
              package_name="${'$'}1"
              if cmd package resolve-activity --brief "${'$'}package_name" >/dev/null 2>&1; then
                am force-stop "${'$'}package_name" 2>/dev/null
                echo "force-stop:${'$'}package_name"
              fi
            }

            kill_matches() {
              pattern="${'$'}1"
              signal="${'$'}2"
              label="${'$'}3"
              ps -A -o PID,ARGS 2>/dev/null | grep -E "${'$'}pattern" | grep -v grep | while read -r pid rest; do
                [ "${'$'}pid" = "${'$'}CURRENT_PID" ] && continue
                kill "${'$'}signal" "${'$'}pid" 2>/dev/null
                echo "${'$'}label:${'$'}signal:${'$'}pid:${'$'}rest"
              done
            }

            force_stop_if_present com.redtrigger
            kill_matches '[t]gk_native_tool_supervisor.sh' '-TERM' 'lite-supervisor'
            kill_matches '[T]gkNativeTool auto' '-TERM' 'lite-child'
            kill_matches 'com\.redtrigger:tgk' '-TERM' 'old-app-user-service'
            kill_matches "${'$'}OWNER:tgk" '-TERM' 'stale-user-service'
            sleep 0.5
            kill_matches '[t]gk_native_tool_supervisor.sh' '-KILL' 'lite-supervisor'
            kill_matches '[T]gkNativeTool auto' '-KILL' 'lite-child'
            kill_matches 'com\.redtrigger:tgk' '-KILL' 'old-app-user-service'
            kill_matches "${'$'}OWNER:tgk" '-KILL' 'stale-user-service'
        """.trimIndent()
        val result = runCommand("sh", "-c", script).trim()
        return result.ifBlank { "clean" }
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
        call("setKeyTouchPoint", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!), -1, LEFT_TGK, 0, leftX, leftY, true)
        call("setKeyTouchPoint", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!), -1, RIGHT_TGK, 0, rightX, rightY, true)
        call("setKeyTouchPoint", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!), -1, MIDDLE_TGK, 0, -1, -1, true)

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
        call("setKeyTouchPoint", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!), -1, LEFT_TGK, 0, -1, -1, true)
        call("setKeyTouchPoint", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!), -1, RIGHT_TGK, 0, -1, -1, true)
        call("setKeyTouchPoint", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!), -1, MIDDLE_TGK, 0, -1, -1, true)
        call("setGlobalKeyEnable", arrayOf(Boolean::class.javaPrimitiveType!!), false)
        // Official clean-release primitive: fully resets TGK sub-state (mode/drive/points)
        // instead of leaving them lingering across sessions.
        call("releaseTgk", arrayOf<Class<*>>())
    }

    override fun releaseTgk() {
        call("releaseTgk", arrayOf<Class<*>>())
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

    override fun getActivePackages(): String {
        val packages = linkedSetOf<String>()
        getForegroundPackage().takeIf { it.isNotBlank() }?.let(packages::add)
        parsePackagesFromActivity(runCommand("dumpsys", "activity", "recents")).forEach(packages::add)
        return packages
            .filterNot { isLauncherOrSystemTask(it) }
            .take(10)
            .joinToString("\n")
    }

    override fun probeShoulderKeys(timeoutMs: Int): String {
        val devices = findTgkInputDevices()
        if (devices.isEmpty()) {
            return "devices=\nleft=0\nright=0\nresult=no_tgk_input_device"
        }
        val sampleMs = timeoutMs.coerceIn(800, 10000)
        val output = sampleTgkDevices(devices, sampleMs)
        val (left, right) = countShoulderEvents(output)
        val result = when {
            left > 0 || right > 0 -> "event_seen"
            output.isBlank() -> "no_event"
            else -> "raw_seen"
        }
        return listOf(
            "devices=${devices.joinToString(",") { "${it.path}:${it.side}:${it.name}" }}",
            "left=$left",
            "right=$right",
            "result=$result",
            "raw=${output.takeLast(1200).replace('\n', '|')}"
        ).joinToString("\n")
    }

    override fun startShoulderProbe() {
        stopShoulderProbe()
        probeLeft = 0
        probeRight = 0
        val devices = findTgkInputDevices()
        probeDevicesInfo = devices.joinToString(",") { "${it.side}:${it.path}" }
        if (devices.isEmpty()) {
            probeResult = "no_tgk_input_device"
            return
        }
        probeResult = "sampling"
        devices.forEach { device ->
            val thread = Thread {
                try {
                    val proc = ProcessBuilder("getevent", "-lt", device.path)
                        .redirectErrorStream(true)
                        .start()
                    probeProcesses.add(proc)
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        if (isShoulderDownLine(line)) {
                            when (device.side) {
                                "left" -> probeLeft++
                                "right" -> probeRight++
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            thread.isDaemon = true
            probeThreads.add(thread)
            thread.start()
        }
    }

    override fun stopShoulderProbe() {
        probeProcesses.forEach { runCatching { it.destroyForcibly() } }
        probeProcesses.clear()
        probeThreads.forEach { runCatching { it.interrupt() } }
        probeThreads.clear()
        if (probeResult == "sampling") probeResult = "stopped"
    }

    override fun getProbeCounts(): String {
        return "left=$probeLeft\nright=$probeRight\nresult=$probeResult\ndevices=$probeDevicesInfo"
    }

    override fun setShowTouches(enable: Boolean) {
        runCommand("settings", "put", "system", "show_touches", if (enable) "1" else "0")
    }

    override fun setPointerLocation(enable: Boolean) {
        runCommand("settings", "put", "system", "pointer_location", if (enable) "1" else "0")
    }

    override fun getDebugToggles(): String {
        val showTouches = runCommand("settings", "get", "system", "show_touches").trim()
        val pointer = runCommand("settings", "get", "system", "pointer_location").trim()
        return "show_touches=$showTouches\npointer_location=$pointer"
    }

    /** getevent -l 把 EV_KEY 的按下翻译为 DOWN（抬起为 UP）。只计按下沿，避免噪声。 */
    private fun isShoulderDownLine(line: String): Boolean {
        return line.contains("DOWN")
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

    private fun runCommand(vararg command: String): String {
        return try {
            val proc = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val value = proc.inputStream.bufferedReader().readText()
            proc.waitFor(4, TimeUnit.SECONDS)
            value
        } catch (_: Exception) {
            ""
        }
    }

    private fun runTimedCommand(command: List<String>, timeoutMs: Int): String {
        return try {
            val proc = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = Thread {
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        output.append(line).append('\n')
                    }
                } catch (_: Exception) {
                }
            }
            reader.start()
            if (!proc.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly()
            }
            reader.join(500)
            output.toString()
        } catch (e: Exception) {
            "error:${e.javaClass.simpleName}:${e.message}"
        }
    }

    private fun parsePackagesFromActivity(text: String): List<String> {
        val packages = linkedSetOf<String>()
        val patterns = listOf(
            Regex("""cmp=([A-Za-z0-9_.$]+)/"""),
            Regex("""(?:baseActivity|topActivity|realActivity|mActivityComponent)=\{?([A-Za-z0-9_.$]+)/"""),
            Regex("""A=\d+:([A-Za-z0-9_.$]+)""")
        )
        text.lineSequence().forEach { line ->
            patterns.forEach { pattern ->
                pattern.findAll(line).forEach { match ->
                    match.groupValues.getOrNull(1)?.let { packages += normalizePackage(it) }
                }
            }
        }
        return packages.filter { it.isNotBlank() }
    }

    private fun normalizePackage(value: String): String {
        return value
            .substringBefore(":")
            .trim()
            .takeIf { it.contains('.') && it != "android" }
            ?: ""
    }

    private fun isLauncherOrSystemTask(packageName: String): Boolean {
        return packageName == "com.zte.mifavor.launcher" ||
            packageName == "com.android.systemui" ||
            packageName == "android"
    }

    private fun findTgkInputDevices(): List<TgkInputDevice> {
        val devices = mutableListOf<TgkInputDevice>()
        var currentDevice = ""
        var currentName = ""
        var currentKeys = StringBuilder()

        fun flushDevice() {
            if (currentDevice.isBlank()) return
            val haystack = "$currentName $currentKeys".lowercase()
            val side = when {
                haystack.contains("sar0") || haystack.contains("key_f7") -> "left"
                haystack.contains("sar1") || haystack.contains("key_f8") -> "right"
                else -> "unknown"
            }
            val isTgk = haystack.contains("nubia_tgk") ||
                haystack.contains("key_f7") ||
                haystack.contains("key_f8")
            if (isTgk && side != "unknown") {
                devices += TgkInputDevice(currentDevice, side, currentName.ifBlank { "tgk" })
            }
        }

        runCommand("getevent", "-p").lineSequence().forEach { line ->
            val addMatch = Regex("""add device \d+: (/dev/input/event\d+)""").find(line)
            if (addMatch != null) {
                flushDevice()
                currentDevice = addMatch.groupValues[1]
                currentName = ""
                currentKeys = StringBuilder()
                return@forEach
            }
            val nameMatch = Regex("name:\\s+\"([^\"]+)\"").find(line)
            if (nameMatch != null) {
                currentName = nameMatch.groupValues[1]
            }
            if (
                line.contains("nubia_tgk", ignoreCase = true) ||
                line.contains("KEY_F", ignoreCase = true)
            ) {
                currentKeys.append(line).append(' ')
            }
        }
        flushDevice()
        return devices.distinctBy { it.path }
    }

    private fun sampleTgkDevices(devices: List<TgkInputDevice>, timeoutMs: Int): String {
        val seconds = maxOf(1, (timeoutMs + 999) / 1000)
        val script = buildString {
            devices.forEach { device ->
                append("(timeout ")
                append(seconds)
                append(" getevent -lt ")
                append(device.path)
                append(" 2>&1 | sed 's#^#side=")
                append(device.side)
                append(" device=")
                append(device.path)
                append(" #') &\n")
            }
            append("wait\n")
        }
        return runTimedCommand(listOf("sh", "-c", script), timeoutMs + 1500)
    }

    private fun countShoulderEvents(output: String): Pair<Int, Int> {
        val hexF7 = Regex("""\b0041\b""")
        val hexF8 = Regex("""\b0042\b""")
        val evKey = Regex("""\b0001\b""")
        val evAbs = Regex("""\b0003\b""")
        val absTgk = Regex("""\b0019\b""")
        val downValue = Regex("""\b00000001\b""")
        var left = 0
        var right = 0
        output.lineSequence().forEach { line ->
            val isKeyDown = (line.contains(" DOWN") || downValue.containsMatchIn(line)) &&
                (line.contains("EV_KEY") || line.contains("KEY_") || evKey.containsMatchIn(line))
            val isAbsTgk = (line.contains("EV_ABS") || evAbs.containsMatchIn(line)) &&
                (line.contains("ABS_DISTANCE") || absTgk.containsMatchIn(line))
            if (!isKeyDown && !isAbsTgk) return@forEach
            val value = line.trim().substringAfterLast(' ')
            val absPressed = isAbsTgk && value != "00000000" && value != "ffffffff"
            if (!isKeyDown && !absPressed) return@forEach
            when {
                line.contains("side=left") || line.contains("KEY_F7") || hexF7.containsMatchIn(line) -> left++
                line.contains("side=right") || line.contains("KEY_F8") || hexF8.containsMatchIn(line) -> right++
            }
        }
        return left to right
    }

    override fun destroy() {
        stopShoulderProbe()
        Log.i(TAG, "destroy")
    }

    private fun call(name: String, types: Array<Class<*>>, vararg args: Any) {
        try {
            val method: Method = inputManager.javaClass.getMethod(name, *types)
            method.invoke(inputManager, *args)
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
