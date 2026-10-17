package com.smartremote.control.pc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartremote.control.service.RemoteAccessibilityService
import com.smartremote.control.service.ScreenCaptureService
import org.json.JSONObject

/**
 * خدمة استضافة شاشة الهاتف (Host Mode)
 * تستمع للاتصالات الواردة من Windows PC أو هاتف Android آخر
 * وتنفذ أوامر اللمس وإيماءات السحب وأزرار النظام
 */
class AndroidHostService : Service() {

    companion object {
        private const val TAG = "AndroidHostService"
        const val CHANNEL_ID = "SmartRemote_PcHost_Channel"
        const val NOTIFICATION_ID = 1002

        var instance: AndroidHostService? = null
            private set

        var currentSessionId: String = ""
        var activePin: String = "123456"

        var isClientConnected: Boolean = false
            private set

        var isSessionAuthenticated: Boolean = false
            private set

        var onHostStateChanged: ((status: String, connected: Boolean) -> Unit)? = null

        fun broadcastFrame(frameJson: String) {
            instance?.localServer?.sendText(frameJson)
        }
    }

    private var localServer: AndroidLocalServer? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        localServer = AndroidLocalServer(52840).apply {
            onClientConnected = { clientIp ->
                isClientConnected = true
                isSessionAuthenticated = false
                onHostStateChanged?.invoke("اتصال وارد من $clientIp. في انتظار المصادقة برمز PIN...", false)
            }

            onClientDisconnected = {
                isClientConnected = false
                isSessionAuthenticated = false
                onHostStateChanged?.invoke("انقطع اتصال الطرف الآخر أو تم إنهاء الجلسة.", false)
            }

            onPacketReceived = { jsonString ->
                handleIncomingPacket(jsonString)
            }
        }

        localServer?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pin = intent?.getStringExtra("EXTRA_PIN")
        if (!pin.isNullOrEmpty()) {
            activePin = pin
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("استضافة شاشة الهاتف نشطة (Host Mode)")
            .setContentText("الخادم جاهز لاستقبال اتصالات Windows أو هاتف Android آخر (PIN: $activePin)")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    private fun handleIncomingPacket(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val packetType = json.optString("type")

            when (packetType) {
                "HANDSHAKE_HELLO" -> {
                    val helloAck = JSONObject().apply {
                        put("type", "HANDSHAKE_HELLO")
                        put("deviceId", Build.MODEL)
                        put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL} (Host Android)")
                        put("platform", "android")
                        put("appVersion", "1.1.0")
                    }
                    localServer?.sendText(helloAck.toString())
                }

                "SESSION_REQUEST" -> {
                    val reqSessionId = json.optString("sessionId", "session_${System.currentTimeMillis()}")
                    val receivedPin = json.optString("sessionPin", "")

                    if (receivedPin == activePin) {
                        currentSessionId = reqSessionId
                        isSessionAuthenticated = true

                        val isCasting = ScreenCaptureService.isRunning
                        val resp = JSONObject().apply {
                            put("type", "SESSION_RESPONSE")
                            put("sessionId", currentSessionId)
                            put("accepted", true)
                            put("screenSharingActive", isCasting)
                        }
                        localServer?.sendText(resp.toString())

                        if (isCasting) {
                            onHostStateChanged?.invoke("تم قبول الجلسة والمصادقة بنجاح! يتم الآن استقبال الأوامر وبث الشاشة.", true)
                        } else {
                            onHostStateChanged?.invoke("تم قبول الجلسة بنجاح! تنبيه: يرجى تفعيل (بدء المشاركة) لبدء إرسال إطارات الشاشة للجهاز المتحكم.", true)
                        }
                    } else {
                        isSessionAuthenticated = false
                        val resp = JSONObject().apply {
                            put("type", "SESSION_RESPONSE")
                            put("sessionId", reqSessionId)
                            put("accepted", false)
                            put("rejectionReason", "رمز PIN غير مطابق لرمز الهاتف المستضيف.")
                        }
                        localServer?.sendText(resp.toString())
                        onHostStateChanged?.invoke("محاولة اتصال غير مصرح بها برمز PIN خاطئ: $receivedPin", false)
                    }
                }

                "INPUT_EVENT" -> {
                    // حظر أوامر التحكم إذا لم تكن الجلسة معتمدة أو إذا لم تكن المشاركة جارية!
                    if (!isSessionAuthenticated || !ScreenCaptureService.isRunning) {
                        Log.w(TAG, "تم حظر أمر إدخال غير مصرح به أو لعدم تشغيل بث الشاشة")
                        return
                    }

                    executeInputEvent(json)
                }

                "DISCONNECT" -> {
                    isSessionAuthenticated = false
                    onHostStateChanged?.invoke("طلب الطرف الآخر إنهاء الجلسة.", false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في معالجة الحزمة الواردة: ${e.message}")
        }
    }

    private fun executeInputEvent(json: JSONObject) {
        val subType = json.optString("subType")
        val normX = json.optDouble("normalizedX", 0.0).toFloat()
        val normY = json.optDouble("normalizedY", 0.0).toFloat()

        val displayMetrics = resources.displayMetrics
        val pixelX = normX * displayMetrics.widthPixels
        val pixelY = normY * displayMetrics.heightPixels

        val accessibility = RemoteAccessibilityService.instance

        when (subType) {
            "MOUSE_DOWN", "TOUCH_TAP" -> {
                if (accessibility != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    accessibility.performTap(pixelX, pixelY, 50)
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    Log.i(TAG, "محاكاة اللمس الحر غير مدعومة على إصدارات أقل من Android 7.0 (API 24)")
                }
            }

            "TOUCH_SWIPE", "MOUSE_WHEEL" -> {
                if (accessibility != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // سحب بسيط من الإحداثيات الحالية
                    val endY = if (subType == "MOUSE_WHEEL") {
                        val delta = json.optInt("wheelDelta", 120)
                        pixelY + (if (delta > 0) 300f else -300f)
                    } else {
                        pixelY + 200f
                    }
                    accessibility.performSwipe(pixelX, pixelY, pixelX, endY, 250)
                }
            }

            "ANDROID_GLOBAL_ACTION" -> {
                if (accessibility != null) {
                    when (json.optString("globalAction")) {
                        "BACK" -> accessibility.triggerBack()
                        "HOME" -> accessibility.triggerHome()
                        "RECENTS" -> accessibility.triggerRecents()
                        "NOTIFICATIONS" -> accessibility.triggerNotifications()
                    }
                }
            }
        }
    }

    fun disconnect() {
        if (isClientConnected) {
            val disconnectJson = JSONObject().apply {
                put("type", "DISCONNECT")
                put("sessionId", currentSessionId)
                put("reason", "user_requested")
            }
            localServer?.sendText(disconnectJson.toString())
        }
        isSessionAuthenticated = false
        isClientConnected = false
        onHostStateChanged?.invoke("تم قطع الاتصال وإنهاء الجلسة فورياً بطلب من المستخدم.", false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة مشاركة شاشة الهاتف واستقبال التحكم",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        disconnect()
        localServer?.stop()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
