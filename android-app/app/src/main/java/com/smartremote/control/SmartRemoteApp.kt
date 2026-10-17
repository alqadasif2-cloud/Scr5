package com.smartremote.control

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * فئة التطبيق الرئيسية المسؤولة عن تهيئة قنوات الإشعارات وفحص قدرات النظام
 */
class SmartRemoteApp : Application() {

    companion object {
        const val SCREEN_CAST_CHANNEL_ID = "SmartRemote_ScreenCast_Channel"
        const val PC_HOST_CHANNEL_ID = "SmartRemote_PcHost_Channel"
        lateinit var instance: SmartRemoteApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // قناة إشعار بث الشاشة الدائم
            val castChannel = NotificationChannel(
                SCREEN_CAST_CHANNEL_ID,
                getString(R.string.channel_screen_cast),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_screen_cast_desc)
                setShowBadge(false)
            }

            // قناة إشعار استضافة جلسة الكمبيوتر
            val pcChannel = NotificationChannel(
                PC_HOST_CHANNEL_ID,
                getString(R.string.channel_pc_host),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_pc_host_desc)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(castChannel)
            notificationManager.createNotificationChannel(pcChannel)
        }
    }
}
