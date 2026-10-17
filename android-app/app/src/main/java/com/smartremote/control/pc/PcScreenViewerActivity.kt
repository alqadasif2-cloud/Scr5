package com.smartremote.control.pc

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * شاشة المشاهدة والتحكم الكامل في الجهاز المستهدف
 * تدعم التحكم في هاتف Android آخر أو كمبيوتر Windows مع:
 * 1. اتجاه شاشة متكيف (عمودي للهاتف افتراضياً، وأفقي للكمبيوتر)
 * 2. دقة تحويل إحداثيات اللمس بدون إزاحة مع استبعاد المساحات السوداء
 * 3. دعم كامل لإيماءات السحب والتمرير بالأبعاد الأربعة (أفقياً وعمودياً)
 * 4. زر تحكم عائم قابل للسحب وقائمة سريعة لجلسات Windows
 */
class PcScreenViewerActivity : Activity() {

    private lateinit var rootLayout: RelativeLayout
    private lateinit var screenImageView: ImageView
    private lateinit var statusTextView: TextView
    private lateinit var disconnectButton: Button
    private lateinit var keyboardButton: Button
    private lateinit var orientationButton: Button
    private lateinit var hiddenInputEditText: EditText

    // أزرار التحكم بالنظام لأندرويد
    private lateinit var bottomNavPanel: LinearLayout
    private lateinit var btnBack: Button
    private lateinit var btnHome: Button
    private lateinit var btnRecents: Button

    // عناصر الزر العائم وقائمة التحكم بكمبيوتر Windows
    private lateinit var floatingButton: FrameLayout
    private lateinit var floatingMenuPanel: LinearLayout

    private val connectionManager = PcConnectionManager()
    private var isAndroidTarget = false
    private var isLandscapeForced = false

    // أبعاد الإطار الوارد الفعلي من الجهاز المستهدف
    private var sourceFrameWidth = 1280f
    private var sourceFrameHeight = 720f

    // متغيرات معالجة اللمس والإيماءات
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var isDraggingGesture = false
    private var touchSlop = 20

    // موضع الزر العائم
    private var floatBtnStartX = 0f
    private var floatBtnStartY = 0f
    private var floatBtnDownRawX = 0f
    private var floatBtnDownRawY = 0f
    private var isFloatingBtnDragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetIp = intent.getStringExtra("EXTRA_TARGET_IP") ?: intent.getStringExtra("EXTRA_PC_IP") ?: "192.168.1.100"
        val targetPort = intent.getIntExtra("EXTRA_TARGET_PORT", 52840)
        val targetPin = intent.getStringExtra("EXTRA_TARGET_PIN") ?: intent.getStringExtra("EXTRA_PC_PIN") ?: "123456"
        isAndroidTarget = intent.getBooleanExtra("EXTRA_IS_ANDROID_TARGET", false)

        // 1. ضبط اتجاه الشاشة الافتراضي المناسب لطبيعة الجهاز
        if (isAndroidTarget) {
            // الهاتف المستهدف يكون عمودياً افتراضياً
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            sourceFrameWidth = 720f
            sourceFrameHeight = 1280f
        } else {
            // كمبيوتر Windows يكون شاشة عريضة أفقية
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            sourceFrameWidth = 1920f
            sourceFrameHeight = 1080f
        }

        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        initUi()
        setupTouchPipeline()

        // ربط أحداث الاستقبال
        connectionManager.onFrameReceived = { bytes, frameW, frameH ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                runOnUiThread {
                    // تحديث أبعاد الإطار بدقة بالاعتماد على البايتات الحقيقية للصورة بعد فك التشفير
                    val actualW = bitmap.width.toFloat()
                    val actualH = bitmap.height.toFloat()
                    if (actualW > 0 && actualH > 0 && (actualW != sourceFrameWidth || actualH != sourceFrameHeight)) {
                        sourceFrameWidth = actualW
                        sourceFrameHeight = actualH
                    }
                    screenImageView.setImageBitmap(bitmap)
                }
            }
        }

        connectionManager.onSessionStateChanged = { connected, message ->
            runOnUiThread {
                statusTextView.text = message
                if (!connected) {
                    screenImageView.setImageBitmap(null)
                    screenImageView.setBackgroundColor(Color.BLACK)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // بدء الاتصال
        connectionManager.connectToPc(targetIp, targetPin, targetPort, isAndroidTarget)
    }

    private fun initUi() {
        rootLayout = RelativeLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        screenImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
        }

        statusTextView = TextView(this).apply {
            text = if (isAndroidTarget) "جاري الاتصال بهاتف Android المستهدف..." else "جاري الاتصال بكمبيوتر Windows..."
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(14, 6, 14, 6)
            setBackgroundColor(Color.parseColor("#CC0F172A"))
        }

        disconnectButton = Button(this).apply {
            text = "إنهاء"
            setBackgroundColor(Color.parseColor("#DC2626"))
            setTextColor(Color.WHITE)
            textSize = 10f
            setOnClickListener {
                connectionManager.disconnect()
                finish()
            }
        }

        keyboardButton = Button(this).apply {
            text = "الكتابة"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            textSize = 10f
            setOnClickListener {
                toggleVirtualKeyboard()
            }
        }

        orientationButton = Button(this).apply {
            text = if (isAndroidTarget) "🔄 ملاءمة/أفقي" else "🔄 تدوير"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            textSize = 10f
            setOnClickListener {
                toggleOrientationMode()
            }
        }

        hiddenInputEditText = EditText(this).apply {
            visibility = View.INVISIBLE
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!s.isNullOrEmpty()) {
                        connectionManager.sendTextInput(s.toString())
                        hiddenInputEditText.setText("")
                    }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
            }
            addView(statusTextView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(orientationButton)
            addView(keyboardButton)
            addView(disconnectButton)
        }

        // شريط أزرار التنقل لأندرويد
        bottomNavPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC0F172A"))
            setPadding(6, 4, 6, 4)
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
        }

        btnBack = Button(this).apply {
            text = "◀ الرجوع (Back)"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setOnClickListener {
                connectionManager.sendGlobalAction("BACK")
            }
        }

        btnHome = Button(this).apply {
            text = "● الرئيسية (Home)"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setOnClickListener {
                connectionManager.sendGlobalAction("HOME")
            }
        }

        btnRecents = Button(this).apply {
            text = "■ المهام (Recents)"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setOnClickListener {
                connectionManager.sendGlobalAction("RECENTS")
            }
        }

        val btnNotifications = Button(this).apply {
            text = "🔔 الإشعارات"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            textSize = 10f
            setOnClickListener {
                connectionManager.sendGlobalAction("NOTIFICATIONS")
            }
        }

        bottomNavPanel.addView(btnBack, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottomNavPanel.addView(btnHome, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottomNavPanel.addView(btnRecents, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottomNavPanel.addView(btnNotifications, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        rootLayout.addView(screenImageView)
        rootLayout.addView(topBar)
        if (isAndroidTarget) {
            rootLayout.addView(bottomNavPanel)
        } else {
            setupWindowsFloatingControls()
        }
        rootLayout.addView(hiddenInputEditText)

        setContentView(rootLayout)
    }

    /**
     * إعداد الزر العائم وقائمة التحكم السريعة لكمبيوتر Windows
     */
    private fun setupWindowsFloatingControls() {
        // لوحة التحكم السريعة (مخفية افتراضياً)
        floatingMenuPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E60F172A"))
            setPadding(12, 10, 12, 10)
            visibility = View.GONE
            elevation = 12f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60F172A"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#38BDF8"))
            }
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
        }

        val btnLeftClick = Button(this).apply {
            text = "🖱️ نقر أيسر (Left Click)"
            setBackgroundColor(Color.parseColor("#0284C7"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setOnClickListener {
                connectionManager.sendMouseEvent("MOUSE_DOWN", 0.5f, 0.5f, "left")
                connectionManager.sendMouseEvent("MOUSE_UP", 0.5f, 0.5f, "left")
                floatingMenuPanel.visibility = View.GONE
            }
        }

        val btnRightClick = Button(this).apply {
            text = "🖱️ نقر أيمن (Right Click)"
            setBackgroundColor(Color.parseColor("#475569"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setOnClickListener {
                connectionManager.sendMouseEvent("MOUSE_DOWN", 0.5f, 0.5f, "right")
                connectionManager.sendMouseEvent("MOUSE_UP", 0.5f, 0.5f, "right")
                floatingMenuPanel.visibility = View.GONE
            }
        }

        val btnEscBack = Button(this).apply {
            text = "🔙 الرجوع (Esc / Back)"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setOnClickListener {
                connectionManager.sendTextInput("\u001B") // ESC key
                floatingMenuPanel.visibility = View.GONE
            }
        }

        val btnScrollUp = Button(this).apply {
            text = "⇡ تمرير لأعلى (Scroll Up)"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            textSize = 10f
            setOnClickListener {
                connectionManager.sendMouseEvent("MOUSE_WHEEL", 0.5f, 0.5f, wheelDelta = 120)
            }
        }

        val btnScrollDown = Button(this).apply {
            text = "⇣ تمرير لأسفل (Scroll Down)"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            textSize = 10f
            setOnClickListener {
                connectionManager.sendMouseEvent("MOUSE_WHEEL", 0.5f, 0.5f, wheelDelta = -120)
            }
        }

        val btnCloseMenu = Button(this).apply {
            text = "✕ إغلاق القائمة"
            setBackgroundColor(Color.parseColor("#DC2626"))
            setTextColor(Color.WHITE)
            textSize = 10f
            setOnClickListener {
                floatingMenuPanel.visibility = View.GONE
            }
        }

        floatingMenuPanel.addView(btnLeftClick)
        floatingMenuPanel.addView(btnRightClick)
        floatingMenuPanel.addView(btnEscBack)
        floatingMenuPanel.addView(btnScrollUp)
        floatingMenuPanel.addView(btnScrollDown)
        floatingMenuPanel.addView(btnCloseMenu)

        // الزر العائم الدائري
        floatingButton = FrameLayout(this).apply {
            elevation = 16f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2563EB"))
                setStroke(2, Color.WHITE)
            }
            val size = (48 * resources.displayMetrics.density).toInt()
            layoutParams = RelativeLayout.LayoutParams(size, size).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                setMargins(0, 0, 40, 100)
            }

            val iconText = TextView(context).apply {
                text = "🖱️"
                textSize = 18f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            addView(iconText)
        }

        // سحب الزر العائم بحرية دون التأثير على إحداثيات شاشة Windows
        floatingButton.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    floatBtnStartX = v.x
                    floatBtnStartY = v.y
                    floatBtnDownRawX = event.rawX
                    floatBtnDownRawY = event.rawY
                    isFloatingBtnDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - floatBtnDownRawX
                    val deltaY = event.rawY - floatBtnDownRawY
                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isFloatingBtnDragging = true
                        v.x = floatBtnStartX + deltaX
                        v.y = floatBtnStartY + deltaY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isFloatingBtnDragging) {
                        // فتح / إغلاق قائمة التحكم السريعة
                        floatingMenuPanel.visibility = if (floatingMenuPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    true
                }
                else -> false
            }
        }

        rootLayout.addView(floatingMenuPanel)
        rootLayout.addView(floatingButton)
    }

    /**
     * خط أنابيب معالجة اللمس بدقة هندسية عالية
     * يستبعد المساحات السوداء بالكامل ويحدد إيماءات النقر والسحب الأفقي والعمودي
     */
    private fun setupTouchPipeline() {
        screenImageView.setOnTouchListener { _, event ->
            // إذا كانت لوحة التحكم مفتوحة، النقر على الشاشة يغلقها أولاً
            if (!isAndroidTarget && ::floatingMenuPanel.isInitialized && floatingMenuPanel.visibility == View.VISIBLE) {
                floatingMenuPanel.visibility = View.GONE
                return@setOnTouchListener true
            }

            val viewport = CoordinateTransformUtils.calculateViewport(
                screenImageView.width.toFloat(),
                screenImageView.height.toFloat(),
                sourceFrameWidth,
                sourceFrameHeight
            )

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    touchDownTime = System.currentTimeMillis()
                    isDraggingGesture = false

                    val norm = CoordinateTransformUtils.viewToNormalized(event.x, event.y, viewport)
                    if (norm != null && !isAndroidTarget) {
                        // في جلسة Windows: تحريك مؤشر الماوس لموقع اللمس فوراً
                        connectionManager.sendMouseEvent("MOUSE_MOVE", norm.first, norm.second)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.x - touchDownX)
                    val dy = abs(event.y - touchDownY)

                    if (dx > touchSlop || dy > touchSlop) {
                        isDraggingGesture = true
                    }

                    if (!isAndroidTarget) {
                        // تحريك الماوس في Windows بسلاسة أثناء السحب
                        val norm = CoordinateTransformUtils.viewToNormalized(event.x, event.y, viewport)
                        if (norm != null) {
                            connectionManager.sendMouseEvent("MOUSE_MOVE", norm.first, norm.second)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - touchDownTime
                    val startNorm = CoordinateTransformUtils.viewToNormalized(touchDownX, touchDownY, viewport)
                    val endNorm = CoordinateTransformUtils.viewToNormalized(event.x, event.y, viewport)

                    if (startNorm == null) {
                        // النقر بدأ خارج صورة الشاشة (في المساحات السوداء)، يتم تجاهله تماماً
                        return@setOnTouchListener true
                    }

                    if (isAndroidTarget) {
                        if (!isDraggingGesture) {
                            if (duration > 500) {
                                // ضغطة مطولة على أندرويد
                                connectionManager.sendLongPressEvent(startNorm.first, startNorm.second, 800L)
                            } else {
                                // نقرة فردية دقيقة على أندرويد
                                connectionManager.sendTouchEvent("TOUCH_TAP", startNorm.first, startNorm.second, sourceFrameWidth.toInt(), sourceFrameHeight.toInt())
                            }
                        } else {
                            // إيماءة سحب كاملة الأبعاد (تدعم الاتجاهات الأربعة أفقياً وعمودياً)
                            val actualEndNorm = endNorm ?: startNorm
                            val gestureDuration = duration.coerceIn(150L, 800L)
                            connectionManager.sendSwipeEvent(
                                startNorm.first,
                                startNorm.second,
                                actualEndNorm.first,
                                actualEndNorm.second,
                                durationMs = gestureDuration,
                                gestureId = "g_${System.currentTimeMillis()}"
                            )
                        }
                    } else {
                        // جلسة Windows
                        if (!isDraggingGesture) {
                            connectionManager.sendMouseEvent("MOUSE_DOWN", startNorm.first, startNorm.second, "left")
                            connectionManager.sendMouseEvent("MOUSE_UP", startNorm.first, startNorm.second, "left")
                        } else {
                            // إذا سحب المستخدم في Windows: إرسال تمرير عجلة الماوس المناسب
                            val deltaY = event.y - touchDownY
                            val wheelDelta = if (deltaY > 0) 120 else -120
                            connectionManager.sendMouseEvent("MOUSE_WHEEL", startNorm.first, startNorm.second, wheelDelta = wheelDelta)
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private var orientationModeIndex = 0

    private fun toggleOrientationMode() {
        orientationModeIndex = (orientationModeIndex + 1) % 3
        when (orientationModeIndex) {
            0 -> {
                requestedOrientation = if (isAndroidTarget) ActivityInfo.SCREEN_ORIENTATION_USER else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                orientationButton.text = if (isAndroidTarget) "📱 ملاءمة" else "💻 ملاءمة"
                Toast.makeText(this, "وضع العرض: الملاءمة التلقائية مع الحفاظ على الأبعاد", Toast.LENGTH_SHORT).show()
            }
            1 -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                orientationButton.text = "🔄 أفقي موسع"
                Toast.makeText(this, "وضع العرض: العرض الأفقي الموسع", Toast.LENGTH_SHORT).show()
            }
            2 -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                orientationButton.text = "⚡ دوران حر"
                Toast.makeText(this, "وضع العرض: تدوير حر حسب حركة الجهاز", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleVirtualKeyboard() {
        hiddenInputEditText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(hiddenInputEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onDestroy() {
        connectionManager.disconnect()
        super.onDestroy()
    }
}
