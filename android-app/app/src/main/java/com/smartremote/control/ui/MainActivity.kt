package com.smartremote.control.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.smartremote.control.R
import com.smartremote.control.compat.DeviceCapabilityMatrix
import com.smartremote.control.pc.AndroidHostService
import com.smartremote.control.pc.NetworkUtils
import com.smartremote.control.pc.PcScreenViewerActivity
import com.smartremote.control.service.RemoteAccessibilityService
import com.smartremote.control.service.ScreenCaptureService

/**
 * الشاشة الرئيسية لتطبيق Smart Remote Control على Android
 * تدعم الخيارين الرئيسيين والسيناريوهات الثلاثة للتحكم عن بُعد
 */
class MainActivity : AppCompatActivity() {

    // التبويبات الرئيسية
    private lateinit var btnTabControl: Button
    private lateinit var btnTabShare: Button
    private lateinit var layoutControlMode: LinearLayout
    private lateinit var layoutShareMode: LinearLayout

    // لوحة معلومات النظام
    private lateinit var tvSystemStatus: TextView

    // عناصر وضع التحكم في جهاز آخر (Client Mode)
    private lateinit var btnTargetWindows: Button
    private lateinit var btnTargetAndroid: Button
    private lateinit var tvTargetDesc: TextView
    private lateinit var etTargetIp: EditText
    private lateinit var etTargetPort: EditText
    private lateinit var etTargetPin: EditText
    private lateinit var btnConnectTarget: Button
    private var isAndroidTarget: Boolean = false

    // عناصر وضع مشاركة شاشة هذا الجهاز (Host Mode)
    private lateinit var tvHostDeviceName: TextView
    private lateinit var tvHostDeviceType: TextView
    private lateinit var tvHostIpAddress: TextView
    private lateinit var tvHostPinDisplay: TextView
    private lateinit var btnGeneratePin: Button
    private lateinit var tvHostConnectionStatus: TextView
    private lateinit var btnToggleCast: Button
    private lateinit var btnEmergencyStop: Button
    private lateinit var btnAccessibilitySettings: Button

    private var currentHostPin: String = NetworkUtils.generatePin()

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // 1. تشغيل خدمة بث الشاشة
            val castIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START_CAST
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(castIntent)
            } else {
                startService(castIntent)
            }

            // 2. تشغيل خدمة استقبال الاتصال المحلي
            val hostIntent = Intent(this, AndroidHostService::class.java).apply {
                putExtra("EXTRA_PIN", currentHostPin)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(hostIntent)
            } else {
                startService(hostIntent)
            }

            Toast.makeText(this, R.string.cast_started, Toast.LENGTH_SHORT).show()
            updateUiState()
        } else {
            Toast.makeText(this, R.string.cast_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updateUiState()

        // ربط أحداث حالة الاستضافة اللحظية
        AndroidHostService.onHostStateChanged = { statusMsg, isConnected ->
            runOnUiThread {
                tvHostConnectionStatus.text = statusMsg
                tvHostConnectionStatus.setTextColor(
                    if (isConnected) android.graphics.Color.parseColor("#10B981")
                    else android.graphics.Color.parseColor("#94A3B8")
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
    }

    private fun initViews() {
        btnTabControl = findViewById(R.id.btn_tab_control)
        btnTabShare = findViewById(R.id.btn_tab_share)
        layoutControlMode = findViewById(R.id.layout_control_mode)
        layoutShareMode = findViewById(R.id.layout_share_mode)
        tvSystemStatus = findViewById(R.id.tv_system_status)

        // Client Mode Views
        btnTargetWindows = findViewById(R.id.btn_target_windows)
        btnTargetAndroid = findViewById(R.id.btn_target_android)
        tvTargetDesc = findViewById(R.id.tv_target_desc)
        etTargetIp = findViewById(R.id.et_target_ip)
        etTargetPort = findViewById(R.id.et_target_port)
        etTargetPin = findViewById(R.id.et_target_pin)
        btnConnectTarget = findViewById(R.id.btn_connect_target)

        // Host Mode Views
        tvHostDeviceName = findViewById(R.id.tv_host_device_name)
        tvHostDeviceType = findViewById(R.id.tv_host_device_type)
        tvHostIpAddress = findViewById(R.id.tv_host_ip_address)
        tvHostPinDisplay = findViewById(R.id.tv_host_pin_display)
        btnGeneratePin = findViewById(R.id.btn_generate_pin)
        tvHostConnectionStatus = findViewById(R.id.tv_host_connection_status)
        btnToggleCast = findViewById(R.id.btn_toggle_cast)
        btnEmergencyStop = findViewById(R.id.btn_emergency_stop)
        btnAccessibilitySettings = findViewById(R.id.btn_accessibility_settings)

        // القيمة الافتراضية للمنفذ
        etTargetPort.setText("52840")
    }

    private fun setupListeners() {
        // التبديل بين التبويبين الرئيسيين
        btnTabControl.setOnClickListener {
            switchMainTab(isControlTab = true)
        }

        btnTabShare.setOnClickListener {
            switchMainTab(isControlTab = false)
        }

        // اختيار نوع الجهاز المستهدف (Windows أو Android)
        btnTargetWindows.setOnClickListener {
            setTargetDevice(isAndroid = false)
        }

        btnTargetAndroid.setOnClickListener {
            setTargetDevice(isAndroid = true)
        }

        // بدء الاتصال بالجهاز المستهدف
        btnConnectTarget.setOnClickListener {
            val ip = etTargetIp.text.toString().trim()
            val portStr = etTargetPort.text.toString().trim()
            val pin = etTargetPin.text.toString().trim()
            val port = portStr.toIntOrNull() ?: 52840

            if (ip.isEmpty()) {
                etTargetIp.error = getString(R.string.error_invalid_ip)
                return@setOnClickListener
            }
            if (pin.length != 6) {
                etTargetPin.error = getString(R.string.error_invalid_pin)
                return@setOnClickListener
            }

            val intent = Intent(this, PcScreenViewerActivity::class.java).apply {
                putExtra("EXTRA_TARGET_IP", ip)
                putExtra("EXTRA_TARGET_PORT", port)
                putExtra("EXTRA_TARGET_PIN", pin)
                putExtra("EXTRA_IS_ANDROID_TARGET", isAndroidTarget)
            }
            startActivity(intent)
        }

        // توليد رمز PIN جديد
        btnGeneratePin.setOnClickListener {
            currentHostPin = NetworkUtils.generatePin()
            AndroidHostService.activePin = currentHostPin
            tvHostPinDisplay.text = "رمز PIN للاقتران: $currentHostPin"
            Toast.makeText(this, "تم توليد رمز PIN جديد: $currentHostPin", Toast.LENGTH_SHORT).show()
        }

        // بدء / إيقاف مشاركة شاشة هذا الجهاز
        btnToggleCast.setOnClickListener {
            if (ScreenCaptureService.isRunning) {
                stopScreenShare()
            } else {
                startScreenShare()
            }
        }

        btnEmergencyStop.setOnClickListener {
            stopScreenShare()
        }

        // فتح إعدادات إمكانية الوصول
        btnAccessibilitySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun switchMainTab(isControlTab: Boolean) {
        if (isControlTab) {
            btnTabControl.setBackgroundColor(getColor(R.color.primary))
            btnTabControl.setTextColor(getColor(R.color.text_primary))
            btnTabShare.setBackgroundColor(getColor(R.color.surface))
            btnTabShare.setTextColor(getColor(R.color.text_secondary))
            layoutControlMode.visibility = View.VISIBLE
            layoutShareMode.visibility = View.GONE
        } else {
            btnTabShare.setBackgroundColor(getColor(R.color.primary))
            btnTabShare.setTextColor(getColor(R.color.text_primary))
            btnTabControl.setBackgroundColor(getColor(R.color.surface))
            btnTabControl.setTextColor(getColor(R.color.text_secondary))
            layoutShareMode.visibility = View.VISIBLE
            layoutControlMode.visibility = View.GONE
        }
    }

    private fun setTargetDevice(isAndroid: Boolean) {
        isAndroidTarget = isAndroid
        if (isAndroid) {
            btnTargetAndroid.setBackgroundColor(getColor(R.color.primary))
            btnTargetAndroid.setTextColor(getColor(R.color.text_primary))
            btnTargetWindows.setBackgroundColor(getColor(R.color.surface))
            btnTargetWindows.setTextColor(getColor(R.color.text_secondary))
            tvTargetDesc.text = "التحكم في هاتف Android آخر: مشاهدة الشاشة وإرسال إيماءات اللمس وأزرار النظام"
            etTargetIp.hint = "عنوان IP لهاتف Android المستهدف"
        } else {
            btnTargetWindows.setBackgroundColor(getColor(R.color.primary))
            btnTargetWindows.setTextColor(getColor(R.color.text_primary))
            btnTargetAndroid.setBackgroundColor(getColor(R.color.surface))
            btnTargetAndroid.setTextColor(getColor(R.color.text_secondary))
            tvTargetDesc.text = "التحكم في كمبيوتر Windows: مشاهدة سطح المكتب والماوس ولوحة المفاتيح"
            etTargetIp.hint = "عنوان IP لكمبيوتر Windows المستهدف"
        }
    }

    private fun startScreenShare() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopScreenShare() {
        val stopCastIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP_CAST
        }
        startService(stopCastIntent)

        val stopHostIntent = Intent(this, AndroidHostService::class.java)
        stopService(stopHostIntent)

        Toast.makeText(this, R.string.cast_stopped, Toast.LENGTH_SHORT).show()
        updateUiState()
    }

    private fun updateUiState() {
        val caps = DeviceCapabilityMatrix.inspect(this)
        val isAccEnabled = RemoteAccessibilityService.instance != null
        val isCasting = ScreenCaptureService.isRunning

        val statusInfo = buildString {
            append("الجهاز: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE} - API ${caps.apiLevel})\n")
            append("إمكانية الوصول (Accessibility): ${if (isAccEnabled) "مفعلة بنجاح ✓" else "معطلة (يتطلب تفعيلها للتحكم باللمس) ✕"}\n")
            append("حالة مشاركة الشاشة: ${if (isCasting) "نشطة ويتم البث ✓" else "متوقفة"}\n")
            append("دقة البث التكيفية: ${caps.recommendedStreamingResolution}")
        }
        tvSystemStatus.text = statusInfo

        // تحديث بطاقة الاستضافة
        tvHostDeviceName.text = "اسم الجهاز: ${Build.MANUFACTURER} ${Build.MODEL}"
        val localIp = NetworkUtils.getLocalIpAddress()
        tvHostIpAddress.text = "عنوان الاتصال المحلي (IP): $localIp:52840"
        tvHostPinDisplay.text = "رمز PIN للاقتران: $currentHostPin"

        btnToggleCast.text = if (isCasting) getString(R.string.btn_stop_cast) else getString(R.string.btn_start_cast)
        btnToggleCast.setBackgroundColor(getColor(if (isCasting) R.color.danger else R.color.accent))
        btnEmergencyStop.visibility = if (isCasting) View.VISIBLE else View.GONE

        tvHostConnectionStatus.text = when {
            isCasting && AndroidHostService.isSessionAuthenticated -> getString(R.string.host_status_connected)
            isCasting -> getString(R.string.host_status_waiting)
            else -> getString(R.string.host_status_idle)
        }
    }
}
