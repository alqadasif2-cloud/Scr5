package com.smartremote.control.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * خدمة إمكانية الوصول لتنفيذ أوامر اللمس والإيماءات وأزرار النظام
 * تدعم محاكاة اللمس الحر بدقة الإحداثيات على Android 7.0+ (API 24+)
 * وتوفر التراجع الآمن لأزرار النظام العالمية على Android 5.0 إلى 6.0 (API 21-23)
 */
class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RemoteAccessibilityService? = null
            private set

        val isGestureSupported: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // لا نحتاج لمراقبة أحداث النظام حفاظاً على خصوصية المستخدم واستهلاك البطارية
    }

    override fun onInterrupt() {
        // يتم استدعاؤها عند مقاطعة خدمة إمكانية الوصول
    }

    /**
     * تنفيذ نقرة لمس في إحداثيات محددة (x, y)
     * مدعومة رسمياً من Android 7.0 (API 24) فما فوق
     */
    fun performTap(x: Float, y: Float, durationMs: Long = 50): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val path = Path().apply {
            moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, null, null)
    }

    /**
     * تنفيذ سحب / تمرير (Swipe Gesture)
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val path = Path().apply {
            moveTo(startX.coerceAtLeast(0f), startY.coerceAtLeast(0f))
            lineTo(endX.coerceAtLeast(0f), endY.coerceAtLeast(0f))
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, null, null)
    }

    /**
     * أزرار النظام العالمية: مدعومة من Android 5.0 (API 21) فما فوق
     */
    fun triggerBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun triggerHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun triggerRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun triggerNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
