package com.smartremote.control.pc

/**
 * مستطيل منطقة العرض (Viewport) بدقة هندسية خالصة تعمل في جميع البيئات
 */
data class ViewportRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun width(): Float = (right - left).coerceAtLeast(0f)
    fun height(): Float = (bottom - top).coerceAtLeast(0f)
}

/**
 * أدوات رياضية دقيقة لتحويل إحداثيات اللمس بين شاشة العرض (Viewport)
 * والشاشة الحقيقية للجهاز المستهدف، مع المعالجة الكاملة للمساحات السوداء (Letterbox/Pillarbox)
 */
object CoordinateTransformUtils {

    /**
     * حساب المستطيل الفعلي للصورة المعروضة داخل الحاوية وفق نمط Fit-Center / Zoom
     */
    fun calculateViewport(
        containerWidth: Float,
        containerHeight: Float,
        sourceWidth: Float,
        sourceHeight: Float
    ): ViewportRect {
        if (containerWidth <= 0f || containerHeight <= 0f || sourceWidth <= 0f || sourceHeight <= 0f) {
            return ViewportRect(0f, 0f, containerWidth.coerceAtLeast(0f), containerHeight.coerceAtLeast(0f))
        }

        val sourceAspect = sourceWidth / sourceHeight
        val containerAspect = containerWidth / containerHeight

        val scale: Float
        val left: Float
        val top: Float

        if (containerAspect > sourceAspect) {
            // شريط أسود على اليمين واليسار (Pillarbox)
            scale = containerHeight / sourceHeight
            left = (containerWidth - sourceWidth * scale) / 2f
            top = 0f
        } else {
            // شريط أسود في الأعلى والأسفل (Letterbox)
            scale = containerWidth / sourceWidth
            left = 0f
            top = (containerHeight - sourceHeight * scale) / 2f
        }

        val right = left + sourceWidth * scale
        val bottom = top + sourceHeight * scale

        return ViewportRect(left, top, right, bottom)
    }

    /**
     * تحويل إحداثيات اللمس على شاشة المتحكم إلى إحداثيات نسبية [0.0, 1.0]
     * يُرجع null إذا كانت النقرة خارج حدود الصورة (في المساحات السوداء)
     */
    fun viewToNormalized(touchX: Float, touchY: Float, viewport: ViewportRect): Pair<Float, Float>? {
        if (viewport.width() <= 0f || viewport.height() <= 0f) return null

        // تجاهل اللمسات الواقعة في المساحات السوداء خارج الصورة
        if (touchX < viewport.left || touchX > viewport.right ||
            touchY < viewport.top || touchY > viewport.bottom
        ) {
            return null
        }

        val normX = ((touchX - viewport.left) / viewport.width()).coerceIn(0f, 1f)
        val normY = ((touchY - viewport.top) / viewport.height()).coerceIn(0f, 1f)

        return Pair(normX, normY)
    }

    /**
     * تحويل الإحداثيات النسبية [0.0, 1.0] إلى إحداثيات بكسل حقيقية على شاشة الهاتف المستهدف
     */
    fun normalizedToRealPixels(normX: Float, normY: Float, realWidth: Int, realHeight: Int): Pair<Float, Float> {
        val pixelX = (normX * realWidth).coerceIn(0f, realWidth.toFloat())
        val pixelY = (normY * realHeight).coerceIn(0f, realHeight.toFloat())
        return Pair(pixelX, pixelY)
    }
}
