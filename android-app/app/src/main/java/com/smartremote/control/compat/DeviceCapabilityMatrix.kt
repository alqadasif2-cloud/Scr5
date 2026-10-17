package com.smartremote.control.compat

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * المحرك المركزي لفحص قدرات الجهاز وعزل الميزات غير المدعومة تلقائياً
 */
object DeviceCapabilityMatrix {

    data class Capabilities(
        val apiLevel: Int,
        val canDispatchFreeGestures: Boolean,
        val usesModernBluetoothPermissions: Boolean,
        val requiresLocationForBluetooth: Boolean,
        val requiresNotificationPermission: Boolean,
        val requiresMediaProjectionForegroundType: Boolean,
        val isLowRamDevice: Boolean,
        val recommendedStreamingResolution: String,
        val recommendedFps: Int
    )

    fun inspect(context: Context): Capabilities {
        val api = Build.VERSION.SDK_INT
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isLowRam = activityManager.isLowRamDevice || (getTotalRamGb(context) < 3.0)

        val recommendedRes = when {
            isLowRam -> "480x854"
            api >= Build.VERSION_CODES.O -> "1080x1920"
            else -> "720x1280"
        }

        val recommendedFps = if (isLowRam) 20 else 30

        return Capabilities(
            apiLevel = api,
            canDispatchFreeGestures = api >= Build.VERSION_CODES.N,
            usesModernBluetoothPermissions = api >= Build.VERSION_CODES.S,
            requiresLocationForBluetooth = api in Build.VERSION_CODES.M..Build.VERSION_CODES.R,
            requiresNotificationPermission = api >= Build.VERSION_CODES.TIRAMISU,
            requiresMediaProjectionForegroundType = api >= Build.VERSION_CODES.Q,
            isLowRamDevice = isLowRam,
            recommendedStreamingResolution = recommendedRes,
            recommendedFps = recommendedFps
        )
    }

    private fun getTotalRamGb(context: Context): Double {
        val memInfo = ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getMemoryInfo(memInfo)
        return memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
    }
}
