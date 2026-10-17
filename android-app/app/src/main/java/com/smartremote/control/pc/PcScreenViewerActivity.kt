package com.smartremote.control.pc

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast

/**
 * شاشة المشاهدة والتحكم الكامل في الجهاز المستهدف
 * تدعم التحكم في كمبيوتر Windows أو هاتف Android آخر
 */
class PcScreenViewerActivity : Activity() {

    private lateinit var screenImageView: ImageView
    private lateinit var statusTextView: TextView
    private lateinit var disconnectButton: Button
    private lateinit var keyboardButton: Button
    private lateinit var hiddenInputEditText: EditText

    // أزرار التحكم بالنظام لأندرويد
    private lateinit var btnBack: Button
    private lateinit var btnHome: Button
    private lateinit var btnRecents: Button

    private val connectionManager = PcConnectionManager()
    private lateinit var gestureDetector: GestureDetector
    private var isAndroidTarget = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetIp = intent.getStringExtra("EXTRA_TARGET_IP") ?: intent.getStringExtra("EXTRA_PC_IP") ?: "192.168.1.100"
        val targetPort = intent.getIntExtra("EXTRA_TARGET_PORT", 52840)
        val targetPin = intent.getStringExtra("EXTRA_TARGET_PIN") ?: intent.getStringExtra("EXTRA_PC_PIN") ?: "123456"
        isAndroidTarget = intent.getBooleanExtra("EXTRA_IS_ANDROID_TARGET", false)

        initUi()
        setupGestureDetector()

        // ربط أحداث الاستقبال
        connectionManager.onFrameReceived = { bytes, _, _ ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                runOnUiThread {
                    screenImageView.setImageBitmap(bitmap)
                }
            }
        }

        connectionManager.onSessionStateChanged = { connected, message ->
            runOnUiThread {
                statusTextView.text = message
                if (!connected) {
                    // تفريغ صورة الشاشة فوراً لمنع بقاء أي لقطات قديمة بعد فشل البث
                    screenImageView.setImageBitmap(null)
                    screenImageView.setBackgroundColor(android.graphics.Color.BLACK)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // بدء الاتصال
        connectionManager.connectToPc(targetIp, targetPin, targetPort, isAndroidTarget)
    }

    private fun initUi() {
        val rootLayout = RelativeLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
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
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            setPadding(16, 8, 16, 8)
            setBackgroundColor(android.graphics.Color.parseColor("#AA0F172A"))
        }

        disconnectButton = Button(this).apply {
            text = "إنهاء الجلسة"
            setBackgroundColor(android.graphics.Color.parseColor("#DC2626"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            setOnClickListener {
                connectionManager.disconnect()
                finish()
            }
        }

        keyboardButton = Button(this).apply {
            text = "الكتابة"
            setBackgroundColor(android.graphics.Color.parseColor("#334155"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            setOnClickListener {
                toggleVirtualKeyboard()
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
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
            }
            addView(statusTextView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(keyboardButton)
            addView(disconnectButton)
        }

        // شريط أزرار التنقل (يظهر عندما يكون الجهاز المستهدف هو Android)
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#CC0F172A"))
            setPadding(8, 6, 8, 6)
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
        }

        btnBack = Button(this).apply {
            text = "الرجوع (Back)"
            setBackgroundColor(android.graphics.Color.parseColor("#1E293B"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            setOnClickListener {
                connectionManager.sendGlobalAction("BACK")
            }
        }

        btnHome = Button(this).apply {
            text = "الرئيسية (Home)"
            setBackgroundColor(android.graphics.Color.parseColor("#1E293B"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            setOnClickListener {
                connectionManager.sendGlobalAction("HOME")
            }
        }

        btnRecents = Button(this).apply {
            text = "المهام (Recents)"
            setBackgroundColor(android.graphics.Color.parseColor("#1E293B"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            setOnClickListener {
                connectionManager.sendGlobalAction("RECENTS")
            }
        }

        bottomBar.addView(btnBack, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(btnHome, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(btnRecents, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        rootLayout.addView(screenImageView)
        rootLayout.addView(topBar)
        if (isAndroidTarget) {
            rootLayout.addView(bottomBar)
        }
        rootLayout.addView(hiddenInputEditText)

        setContentView(rootLayout)
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val (normX, normY) = getNormalizedCoordinates(e.x, e.y)
                if (isAndroidTarget) {
                    connectionManager.sendTouchEvent("TOUCH_TAP", normX, normY)
                } else {
                    connectionManager.sendMouseEvent("MOUSE_MOVE", normX, normY)
                    connectionManager.sendMouseEvent("MOUSE_DOWN", normX, normY, "left")
                    connectionManager.sendMouseEvent("MOUSE_UP", normX, normY, "left")
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val (normX, normY) = getNormalizedCoordinates(e.x, e.y)
                if (isAndroidTarget) {
                    // ضغطة مطولة على أندرويد
                    connectionManager.sendTouchEvent("TOUCH_TAP", normX, normY)
                } else {
                    // نقرة يمين على Windows
                    connectionManager.sendMouseEvent("MOUSE_MOVE", normX, normY)
                    connectionManager.sendMouseEvent("MOUSE_DOWN", normX, normY, "right")
                    connectionManager.sendMouseEvent("MOUSE_UP", normX, normY, "right")
                }
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val (normX, normY) = getNormalizedCoordinates(e2.x, e2.y)
                if (isAndroidTarget) {
                    val startNormY = (normY + (if (distanceY > 0) 0.15f else -0.15f)).coerceIn(0f, 1f)
                    connectionManager.sendSwipeEvent(normX, startNormY, normX, normY)
                } else {
                    connectionManager.sendMouseEvent("MOUSE_MOVE", normX, normY)
                    // إرسال تمرير عجلة الفأرة على Windows
                    val wheelDelta = if (distanceY > 0) -120 else 120
                    connectionManager.sendMouseEvent("MOUSE_WHEEL", normX, normY, wheelDelta = wheelDelta)
                }
                return true
            }
        })

        screenImageView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun getNormalizedCoordinates(viewX: Float, viewY: Float): Pair<Float, Float> {
        val width = screenImageView.width.toFloat().coerceAtLeast(1f)
        val height = screenImageView.height.toFloat().coerceAtLeast(1f)
        val normX = (viewX / width).coerceIn(0f, 1f)
        val normY = (viewY / height).coerceIn(0f, 1f)
        return Pair(normX, normY)
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
