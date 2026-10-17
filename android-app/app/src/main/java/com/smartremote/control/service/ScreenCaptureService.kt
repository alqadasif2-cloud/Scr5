package com.smartremote.control.service

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.smartremote.control.R
import com.smartremote.control.SmartRemoteApp
import com.smartremote.control.pc.AndroidHostService
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * خدمة التقاط وبث شاشة Android الحقيقية لحظياً
 * متوافقة مع Android 5.0 (API 21) وحتى Android 15 (API 35)
 */
class ScreenCaptureService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_CAST = "com.smartremote.START_CAST"
        const val ACTION_STOP_CAST = "com.smartremote.STOP_CAST"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        var isRunning: Boolean = false
            private set

        var onCaptureStateChanged: ((running: Boolean) -> Unit)? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var frameCounter = 0L
    private val isProcessingFrame = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAST -> {
                startForegroundWithNotification()

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    initializeProjection(resultCode, resultData)
                    isRunning = true
                    onCaptureStateChanged?.invoke(true)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP_CAST -> {
                stopScreenCapture()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP_CAST
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, SmartRemoteApp.SCREEN_CAST_CHANNEL_ID)
            .setContentTitle(getString(R.string.cast_active_title))
            .setContentText(getString(R.string.cast_active_desc))
            .setSmallIcon(R.drawable.ic_cast_connected)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_stop, getString(R.string.btn_stop_cast), stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initializeProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopScreenCapture()
                stopSelf()
            }
        }, null)

        setupImageReaderAndVirtualDisplay()
    }

    private fun setupImageReaderAndVirtualDisplay() {
        val metrics = resources.displayMetrics
        val rawWidth = metrics.widthPixels
        val rawHeight = metrics.heightPixels
        val dpi = metrics.densityDpi

        // دقة متوازنة للأداء اللحظي وسرعة الشبكة (720x1280 كحد أقصى للسرعة)
        val targetWidth = if (rawWidth > 720) 720 else rawWidth
        val targetHeight = (rawHeight * (targetWidth.toFloat() / rawWidth)).toInt()

        backgroundThread = HandlerThread("ScreenCaptureThread").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)

        imageReader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            // الشرط الأساسي: عدم بث إطارات الشاشة إطلاقاً قبل اكتمال المصادقة برمز PIN واعتماد الجلسة
            if (!AndroidHostService.isSessionAuthenticated) {
                try {
                    val pending = reader.acquireLatestImage()
                    pending?.close()
                } catch (_: Exception) {}
                return@setOnImageAvailableListener
            }

            if (isProcessingFrame.compareAndSet(false, true)) {
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        try {
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * targetWidth

                            val bitmap = Bitmap.createBitmap(
                                targetWidth + rowPadding / pixelStride,
                                targetHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.copyPixelsFromBuffer(buffer)

                            // ضغط الصورة إلى JPEG بجودة 60 لتحقيق توازن ممتاز بين النقاء وسرعة النقل
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                            bitmap.recycle()

                            val jpegBytes = stream.toByteArray()
                            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

                            val frameJson = JSONObject().apply {
                                put("type", "SCREEN_FRAME_CHUNK")
                                put("sessionId", AndroidHostService.currentSessionId)
                                put("frameIndex", ++frameCounter)
                                put("format", "JPEG")
                                put("width", targetWidth)
                                put("height", targetHeight)
                                put("payloadBase64", base64)
                            }

                            AndroidHostService.broadcastFrame(frameJson.toString())
                        } finally {
                            image.close()
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    isProcessingFrame.set(false)
                }
            }
        }, backgroundHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "SmartRemoteCast",
            targetWidth,
            targetHeight,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )
    }

    private fun stopScreenCapture() {
        isRunning = false
        onCaptureStateChanged?.invoke(false)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null

        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
    }

    override fun onDestroy() {
        stopScreenCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
