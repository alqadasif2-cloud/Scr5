package com.smartremote.control.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * نظام إدارة الصلاحيات المركزي المتوافق مع كافة إصدارات Android من API 21 حتى API 35
 */
object PermissionManagerCompat {

    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(context, Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            true
        }
    }

    fun getRequiredBluetoothPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> emptyArray()
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    fun hasMediaProjectionServicePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hasPermission(context, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION)
        } else {
            true
        }
    }

    fun showRationaleDialog(
        activity: Activity,
        titleAr: String,
        messageAr: String,
        positiveButtonAr: String = "موافق ومتابعة",
        negativeButtonAr: String = "إلغاء الآن",
        onProceed: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(activity)
            .setTitle(titleAr)
            .setMessage(messageAr)
            .setCancelable(false)
            .setPositiveButton(positiveButtonAr) { dialog, _ ->
                dialog.dismiss()
                onProceed()
            }
            .setNegativeButton(negativeButtonAr) { dialog, _ ->
                dialog.dismiss()
                onCancel?.invoke()
            }
            .show()
    }

    fun showPermanentlyDeniedGuide(
        activity: Activity,
        featureNameAr: String,
        recoveryStepsAr: List<String>
    ) {
        val stepsFormatted = recoveryStepsAr.mapIndexed { index, step ->
            "${index + 1}. $step"
        }.joinToString("\n")

        val message = "تم رفض صلاحية «$featureNameAr» بشكل دائم من النظام.\n\n" +
                "لتشغيل هذه الميزة يرجى استعادتها يدوياً باتباع الخطوات التالية:\n\n" +
                stepsFormatted

        AlertDialog.Builder(activity)
            .setTitle("تفعيل الصلاحية من الإعدادات")
            .setMessage(message)
            .setPositiveButton("الانتقال إلى الإعدادات") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
