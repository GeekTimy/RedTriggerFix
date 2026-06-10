package com.redtrigger

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * Short-lived overlay picker for editing profile trigger points over the real target app.
 *
 * This does not call TGK/vendor APIs. It only draws draggable L/R markers and saves their
 * center coordinates back to [ProfileStore].
 */
class OverlayPickService : Service() {
    private lateinit var windowManager: WindowManager
    private var profile: AppProfile? = null
    private var leftPoint = TriggerPoint(-1, -1)
    private var rightPoint = TriggerPoint(-1, -1)
    private var leftParams: WindowManager.LayoutParams? = null
    private var rightParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var leftMarker: TextView? = null
    private var rightMarker: TextView? = null
    private var panel: View? = null
    private var statusText: TextView? = null
    private var mode: Mode = Mode.PICKER

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == ACTION_HIDE_MARKERS) {
            if (isPickerActive()) return START_NOT_STICKY
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == ACTION_SHOW_MARKERS && isPickerActive()) {
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限才能在目标应用上取点", Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }

        val packageName = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val loaded = ProfileStore.findProfile(this, packageName)
        if (loaded == null) {
            Toast.makeText(this, "未找到应用配置", Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mode = if (action == ACTION_SHOW_MARKERS) Mode.RUNTIME else Mode.PICKER
        profile = loaded
        val cfg = loaded.configFor(isLandscape())
        leftPoint = cfg.left
        rightPoint = cfg.right
        removeViews()
        if (mode == Mode.RUNTIME) {
            addRuntimeViews(loaded)
        } else {
            addPickerViews()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeViews()
        super.onDestroy()
    }

    private fun addPickerViews() {
        panel = createPanel().also { view ->
            panelParams = baseParams(dp(320), WindowManager.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = dp(22)
            }
            windowManager.addView(view, panelParams)
        }

        val markerSize = dp(PICKER_MARKER_DP)
        leftMarker = createMarker("L", LEFT_MARKER_COLOR, enabled = true, markerSize, editable = true).also { marker ->
            leftParams = markerParams(leftPoint, markerSize)
            makeDraggable(marker, leftParams!!, markerSize) { x, y ->
                leftPoint = TriggerPoint(x, y)
                updateStatus()
            }
            windowManager.addView(marker, leftParams)
        }

        rightMarker = createMarker("R", RIGHT_MARKER_COLOR, enabled = true, markerSize, editable = true).also { marker ->
            rightParams = markerParams(rightPoint, markerSize)
            makeDraggable(marker, rightParams!!, markerSize) { x, y ->
                rightPoint = TriggerPoint(x, y)
                updateStatus()
            }
            windowManager.addView(marker, rightParams)
        }

        updateStatus()
    }

    private fun addRuntimeViews(profile: AppProfile) {
        val markerSize = dp(RUNTIME_MARKER_DP)
        val cfg = profile.configFor(isLandscape())
        if (profile.leftEnabled && cfg.left.isSet) {
            leftMarker = createMarker("L", LEFT_MARKER_COLOR, enabled = true, markerSize = markerSize, editable = false).also { marker ->
                leftParams = markerParams(cfg.left, markerSize, touchable = false)
                windowManager.addView(marker, leftParams)
            }
        }
        if (profile.rightEnabled && cfg.right.isSet) {
            rightMarker = createMarker("R", RIGHT_MARKER_COLOR, enabled = true, markerSize = markerSize, editable = false).also { marker ->
                rightParams = markerParams(cfg.right, markerSize, touchable = false)
                windowManager.addView(marker, rightParams)
            }
        }
    }

    private fun createPanel(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = rounded(0xE61A1C27.toInt(), 0x66FFFFFF, dp(14))
        }
        val title = TextView(this).apply {
            text = "拖动 L / R 设置触发位置"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        statusText = TextView(this).apply {
            setTextColor(0xFFB9BDCA.toInt())
            textSize = 12f
            maxLines = 2
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val save = panelButton("保存") {
            val saved = saveProfile()
            Toast.makeText(this, "坐标已保存", Toast.LENGTH_SHORT).show()
            stopSelf()
            if (saved?.showOverlayMarkers == true) {
                Handler(Looper.getMainLooper()).postDelayed({
                    showMarkers(applicationContext, saved.packageName)
                }, 160L)
            }
        }
        val close = panelButton("关闭") { stopSelf() }
        actions.addView(save, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) })
        actions.addView(close, LinearLayout.LayoutParams(0, dp(42), 1f))

        root.addView(title)
        root.addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
            bottomMargin = dp(10)
        })
        root.addView(actions)
        return root
    }

    private fun panelButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 13f
            background = rounded(0x33FF1744, 0xAAFF1744.toInt(), dp(20))
            setOnClickListener { onClick() }
        }

    private fun createMarker(label: String, color: Int, enabled: Boolean, markerSize: Int, editable: Boolean): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = if (editable) 14f else 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (enabled) color else 0xFF9AA0AE.toInt())
            background = rounded(
                fill = if (enabled) MARKER_FILL else 0x66222222,
                stroke = if (enabled) color else 0xFF9AA0AE.toInt(),
                radius = markerSize / 2,
                strokeWidth = dp(1)
            )
            alpha = if (enabled) 0.88f else 0.52f
        }

    private fun makeDraggable(
        view: View,
        params: WindowManager.LayoutParams,
        markerSize: Int,
        onCenterChanged: (Int, Int) -> Unit
    ) {
        var touchLocalX = markerSize / 2f
        var touchLocalY = markerSize / 2f

        fun updateFromRaw(event: MotionEvent) {
            val centerX = event.rawX + markerSize / 2f - touchLocalX
            val centerY = event.rawY + markerSize / 2f - touchLocalY
            params.x = centerX.roundToInt() - markerSize / 2
            params.y = centerY.roundToInt() - markerSize / 2
            windowManager.updateViewLayout(view, params)
            onCenterChanged(centerX.roundToInt(), centerY.roundToInt())
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchLocalX = event.x
                    touchLocalY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    updateFromRaw(event)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    updateFromRaw(event)
                    true
                }
                else -> false
            }
        }
    }

    private fun markerParams(point: TriggerPoint, markerSize: Int, touchable: Boolean = true): WindowManager.LayoutParams {
        val display = resources.displayMetrics
        val cx = if (point.x >= 0) point.x else display.widthPixels / 2
        val cy = if (point.y >= 0) point.y else display.heightPixels / 2
        return baseParams(markerSize, markerSize, touchable = touchable).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cx - markerSize / 2
            y = cy - markerSize / 2
        }
    }

    private fun baseParams(width: Int, height: Int, touchable: Boolean = true): WindowManager.LayoutParams {
        val windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowFlags,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun updateStatus() {
        val p = profile ?: return
        statusText?.text = "${p.label} · 正在编辑${if (isLandscape()) "横屏" else "竖屏"} · " +
            "L(${leftPoint.x},${leftPoint.y}) R(${rightPoint.x},${rightPoint.y})"
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    private fun saveProfile(): AppProfile? {
        val p = profile ?: return null
        // 取点绑定当前设备方向：写进该方向那套并启用它。
        val cfg = OrientationConfig(enabled = true, left = leftPoint, right = rightPoint)
        val saved = if (isLandscape()) p.copy(landscape = cfg) else p.copy(portrait = cfg)
        ProfileStore.upsertProfile(this, saved)
        return saved
    }

    private fun removeViews() {
        if (!::windowManager.isInitialized) return
        listOf(leftMarker, rightMarker, panel).forEach { view ->
            if (view != null) runCatching { windowManager.removeView(view) }
        }
        leftMarker = null
        rightMarker = null
        panel = null
    }

    private fun isPickerActive(): Boolean =
        ::windowManager.isInitialized && mode == Mode.PICKER && panel != null

    private fun rounded(fill: Int, stroke: Int, radius: Int, strokeWidth: Int = dp(1)): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(fill)
            setStroke(strokeWidth, stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val ACTION_SHOW = "com.redtriggerfix.action.SHOW_OVERLAY_PICKER"
        private const val ACTION_HIDE = "com.redtriggerfix.action.HIDE_OVERLAY_PICKER"
        private const val ACTION_SHOW_MARKERS = "com.redtriggerfix.action.SHOW_OVERLAY_MARKERS"
        private const val ACTION_HIDE_MARKERS = "com.redtriggerfix.action.HIDE_OVERLAY_MARKERS"
        private const val EXTRA_PACKAGE = "packageName"
        private const val PICKER_MARKER_DP = 32
        private const val RUNTIME_MARKER_DP = 28
        private val LEFT_MARKER_COLOR = 0xFFE95D92.toInt()
        private val RIGHT_MARKER_COLOR = 0xFF4AA4E8.toInt()
        private val MARKER_FILL = 0xB00E1018.toInt()

        fun show(context: Context, packageName: String) {
            context.startService(Intent(context, OverlayPickService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_PACKAGE, packageName)
            })
        }

        fun hide(context: Context) {
            context.startService(Intent(context, OverlayPickService::class.java).apply {
                action = ACTION_HIDE
            })
        }

        fun showMarkers(context: Context, packageName: String) {
            context.startService(Intent(context, OverlayPickService::class.java).apply {
                action = ACTION_SHOW_MARKERS
                putExtra(EXTRA_PACKAGE, packageName)
            })
        }

        fun hideMarkers(context: Context) {
            context.startService(Intent(context, OverlayPickService::class.java).apply {
                action = ACTION_HIDE_MARKERS
            })
        }
    }

    private enum class Mode { PICKER, RUNTIME }
}
