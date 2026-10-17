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
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.smartremote.control.R
import com.smartremote.control.SmartRemoteApp
import com.smartremote.control.pc.AndroidHostService
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * خدمة التقاط وبث شاشة Android الحقيقية لحظياً بدقة هندسية عالية
 * - متوافقة مع Android 5.0 (API 21) وحتى Android 15 (API 35)
 * - تعالج Stride RowPadding بدقة لمنع تشوه الصورة وإزاحة إحداثيات اللمس
 * - تدعم الكشف الديناميكي عن دوران الشاشة (Portrait / Landscape) وتحديث أبعاد البث فورياً
 * - تحسب الأبعاد الشاملة الحقيقية المتطابقة مع خدمة إمكانية الوصول
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
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

    private var displayManager: DisplayManager? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    private var frameCounter = 0L
    private val isProcessingFrame = AtomicBoolean(false)

    private var currentTargetWidth = 720
    private var currentTargetHeight = 1280
    private var currentDpi = 320
    private var lastReportedRotation = -1

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

        backgroundThread = HandlerThread("ScreenCaptureThread").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)

        setupDisplayListener()
        setupImageReaderAndVirtualDisplay()
    }

    /**
     * حساب الأبعاد الحقيقية الحالية للشاشة بدقة تشمل أشرطة النظام والتدوير
     */
    private fun getRealScreenDimensions(): Pair<Int, Int> {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    /**
     * إعداد مراقب دوران الشاشة لتحديث إطارات البث وأبعاد VirtualDisplay تلقائياً
     */
    private fun setupDisplayListener() {
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    checkAndHandleOrientationChange()
                }
            }
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
        }
        displayManager?.registerDisplayListener(displayListener, backgroundHandler)
    }

    private fun checkAndHandleOrientationChange() {
        val (realW, realH) = getRealScreenDimensions()
        val isLandscape = realW > realH
        val currentIsLandscape = currentTargetWidth > currentTargetHeight

        if (isLandscape != currentIsLandscape) {
            Log.i(TAG, "تم رصد دوران الهاتف المستهدف: ($realW x $realH). إعادة ضبط البث...")
            backgroundHandler?.post {
                setupImageReaderAndVirtualDisplay()
            }
        }
    }

    private fun setupImageReaderAndVirtualDisplay() {
        val (realWidth, realHeight) = getRealScreenDimensions()
        currentDpi = resources.displayMetrics.densityDpi

        // ضبط الدقة مع الحفاظ على النسبة الدقيقة (Aspect Ratio) وتثبيت الحد الأقصى للبعد الأصغر عند 720p
        val isLandscape = realWidth > realHeight
        val targetW: Int
        val targetH: Int

        if (isLandscape) {
            targetH = 720.coerceAtMost(realHeight)
            targetW = ((realWidth.toFloat() / realHeight) * targetH).toInt() and 1.inv()
        } else {
            targetW = 720.coerceAtMost(realWidth)
            targetH = ((realHeight.toFloat() / realWidth) * targetW).toInt() and 1.inv()
        }

        currentTargetWidth = targetW
        currentTargetHeight = targetH

        // إغلاق الموارد السابقة بأمان
        imageReader?.close()
        imageReader = null

        imageReader = ImageReader.newInstance(currentTargetWidth, currentTargetHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            // عدم بث أي إطارات إطلاقاً قبل اكتمال مصادقة PIN
            if (!AndroidHostService.isSessionAuthenticated) {
                try {
                    reader.acquireLatestImage()?.close()
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
                            val rowPadding = rowStride - pixelStride * currentTargetWidth

                            // المعالجة الدقيقة لـ Stride RowPadding لمنع إزاحة الإحداثيات وتشويه الحواف
                            val finalBitmap: Bitmap = if (rowPadding <= 0) {
                                val bmp = Bitmap.createBitmap(
                                    currentTargetWidth,
                                    currentTargetHeight,
                                    Bitmap.Config.ARGB_8888
                                )
                                bmp.copyPixelsFromBuffer(buffer)
                                bmp
                            } else {
                                val paddedWidth = currentTargetWidth + (rowPadding / pixelStride)
                                val paddedBitmap = Bitmap.createBitmap(
                                    paddedWidth,
                                    currentTargetHeight,
                                    Bitmap.Config.ARGB_8888
                                )
                                paddedBitmap.copyPixelsFromBuffer(buffer)
                                val cropped = Bitmap.createBitmap(
                                    paddedBitmap,
                                    0,
                                    0,
                                    currentTargetWidth,
                                    currentTargetHeight
                                )
                                paddedBitmap.recycle()
                                cropped
                            }

                            val stream = ByteArrayOutputStream()
                            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 65, stream)
                            finalBitmap.recycle()

                            val jpegBytes = stream.toByteArray()
                            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

                            val frameJson = JSONObject().apply {
                                put("type", "SCREEN_FRAME_CHUNK")
                                put("sessionId", AndroidHostService.currentSessionId)
                                put("frameIndex", ++frameCounter)
                                put("format", "JPEG")
                                put("width", currentTargetWidth)
                                put("height", currentTargetHeight)
                                put("orientation", if (isLandscape) "landscape" else "portrait")
                                put("realWidth", realWidth)
                                put("realHeight", realHeight)
                                put("payloadBase64", base64)
                            }

                            AndroidHostService.broadcastFrame(frameJson.toString())
                        } finally {
                            image.close()
                        }
                    }
                } catch (e: Exception) {
                    Log.v(TAG, "تجاوز إطار ملتقط: ${e.message}")
                } finally {
                    isProcessingFrame.set(false)
                }
            }
        }, backgroundHandler)

        if (virtualDisplay == null) {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "SmartRemoteCast",
                currentTargetWidth,
                currentTargetHeight,
                currentDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                backgroundHandler
            )
        } else {
            // تحديث أبعاد VirtualDisplay الحالية بسلاسة دون إعادة طلب الصلاحية
            virtualDisplay?.resize(currentTargetWidth, currentTargetHeight, currentDpi)
            virtualDisplay?.surface = imageReader?.surface
        }
    }

    private fun stopScreenCapture() {
        isRunning = false
        onCaptureStateChanged?.invoke(false)

        if (displayListener != null && displayManager != null) {
            try {
                displayManager?.unregisterDisplayListener(displayListener)
            } catch (_: Exception) {}
            displayListener = null
        }

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
