package com.smartremote.control.pc

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * بيانات اتصال QR Code للربط السريع
 */
data class QrConnectData(
    val version: Int,
    val type: String,
    val hostType: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val pairingPin: String,
    val expiresAt: Long
)

/**
 * أدوات إنشاء وفحص وتشفير رموز الاستجابة السريعة QR Code
 * مع معايير أمنية صارمة للتحقق من سلامة البنية والصلاحية
 */
object QrCodeUtils {

    private const val QR_TYPE = "smart-remote-connect"
    private const val CURRENT_VERSION = 1

    private val IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    /**
     * بناء حزمة JSON مهيكلة لرمز الاقتران QR
     */
    fun buildConnectQrJson(
        deviceName: String,
        hostIp: String,
        port: Int,
        pin: String,
        isAndroid: Boolean,
        validityMinutes: Int = 10
    ): String {
        val expiresAt = System.currentTimeMillis() + (validityMinutes * 60 * 1000L)
        val json = JSONObject().apply {
            put("version", CURRENT_VERSION)
            put("type", QR_TYPE)
            put("hostType", if (isAndroid) "android" else "windows")
            put("deviceName", deviceName)
            put("host", hostIp)
            put("port", port)
            put("transport", "websocket")
            put("pairingPin", pin)
            put("expiresAt", expiresAt)
        }
        return json.toString()
    }

    /**
     * فحص وتدقيق محتوى رمز الـ QR والتحقق من صحته وسلامة عنوان IP والمنفذ والصلاحية
     */
    fun parseAndValidateQrJson(content: String): Result<QrConnectData> {
        return try {
            val trimmed = content.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return Result.failure(IllegalArgumentException("المحتوى ليس بصيغة JSON صالحة"))
            }

            val json = JSONObject(trimmed)

            val type = json.optString("type")
            if (type != QR_TYPE) {
                return Result.failure(IllegalArgumentException("رمز QR غير مخصص لتطبيق Smart Remote Control"))
            }

            val version = json.optInt("version", 0)
            if (version != CURRENT_VERSION) {
                return Result.failure(IllegalArgumentException("إصدار رمز QR غير مدعوم (الإصدار الحالي: $CURRENT_VERSION)"))
            }

            val host = json.optString("host", "").trim()
            if (host.isEmpty() || !IPV4_PATTERN.matcher(host).matches() || host == "0.0.0.0") {
                return Result.failure(IllegalArgumentException("عنوان IP المستهدف غير صالح: $host"))
            }

            val port = json.optInt("port", 0)
            if (port !in 1..65535) {
                return Result.failure(IllegalArgumentException("منفذ الاتصال غير صالح: $port"))
            }

            val expiresAt = json.optLong("expiresAt", 0L)
            if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
                return Result.failure(IllegalArgumentException("انتهت صلاحية رمز الاقتران المؤقت، يرجى إنشاء رمز جديد"))
            }

            val hostType = json.optString("hostType", "android")
            val deviceName = json.optString("deviceName", "Smart Remote Endpoint")
            val pairingPin = json.optString("pairingPin", "")

            Result.success(
                QrConnectData(
                    version = version,
                    type = type,
                    hostType = hostType,
                    deviceName = deviceName,
                    host = host,
                    port = port,
                    pairingPin = pairingPin,
                    expiresAt = expiresAt
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("فشل تحليل رمز QR: ${e.message}"))
        }
    }

    /**
     * توليد صورة Bitmap لرمز الاستجابة السريعة بدقة عالية
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val bitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * فك تشفير وقراءة النص من صورة رمز الاستجابة السريعة
     */
    fun decodeQrBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = MultiFormatReader().decode(binaryBitmap)
            result.text
        } catch (_: Exception) {
            null
        }
    }
}
