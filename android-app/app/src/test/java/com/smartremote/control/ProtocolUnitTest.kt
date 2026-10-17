package com.smartremote.control

import com.smartremote.control.pc.CoordinateTransformUtils
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * اختبارات الوحدة الشاملة لبروتوكول التحكم ومعادلات تحويل الإحداثيات واستبعاد المساحات السوداء
 */
class ProtocolUnitTest {

    @Test
    fun testPinValidation() {
        val validPin = "582914"
        val invalidPin = "123"
        val invalidCharsPin = "58291A"

        assertTrue(validPin.length == 6 && validPin.all { it.isDigit() })
        assertFalse(invalidPin.length == 6)
        assertFalse(invalidCharsPin.all { it.isDigit() })
    }

    @Test
    fun testNormalizedCoordinatesConversion() {
        val screenWidth = 1920
        val screenHeight = 1080

        val normX = 0.5f
        val normY = 0.5f

        val (pixelX, pixelY) = CoordinateTransformUtils.normalizedToRealPixels(normX, normY, screenWidth, screenHeight)

        assertEquals(960f, pixelX, 0.01f)
        assertEquals(540f, pixelY, 0.01f)

        val winSendInputX = Math.round(normX * 65535.0)
        val winSendInputY = Math.round(normY * 65535.0)

        assertEquals(32768L, winSendInputX)
        assertEquals(32768L, winSendInputY)
    }

    @Test
    fun testViewportLetterboxCalculation() {
        // حاوية أطول من الصورة (تنشأ مساحات سوداء أعلى وأسفل Letterbox)
        val containerW = 1080f
        val containerH = 2400f
        val sourceW = 720f
        val sourceH = 1280f

        val viewport = CoordinateTransformUtils.calculateViewport(containerW, containerH, sourceW, sourceH)

        assertEquals(0f, viewport.left, 0.01f)
        assertEquals(1080f, viewport.right, 0.01f)
        assertTrue(viewport.top > 0f) // وجود شريط أسود علوي
        assertTrue(viewport.bottom < containerH) // وجود شريط أسود سفلي

        // نقرة داخل مساحة الصورة الفعلية
        val centerNorm = CoordinateTransformUtils.viewToNormalized(540f, containerH / 2f, viewport)
        assertNotNull(centerNorm)
        assertEquals(0.5f, centerNorm!!.first, 0.01f)
        assertEquals(0.5f, centerNorm.second, 0.01f)

        // نقرة في الشريط الأسود العلوي
        val topBarClick = CoordinateTransformUtils.viewToNormalized(540f, 10f, viewport)
        assertNull("يجب تجاهل النقرات الواقعة في المساحات السوداء", topBarClick)
    }

    @Test
    fun testViewportPillarboxCalculation() {
        // حاوية عريضة (تنشأ مساحات سوداء يميناً ويساراً Pillarbox)
        val containerW = 1920f
        val containerH = 1080f
        val sourceW = 720f
        val sourceH = 1280f

        val viewport = CoordinateTransformUtils.calculateViewport(containerW, containerH, sourceW, sourceH)

        assertEquals(0f, viewport.top, 0.01f)
        assertEquals(1080f, viewport.bottom, 0.01f)
        assertTrue(viewport.left > 0f) // وجود شريط أسود على اليسار
        assertTrue(viewport.right < containerW) // وجود شريط أسود على اليمين

        // نقرة في الشريط الأسود الأيسر
        val leftBlackBarClick = CoordinateTransformUtils.viewToNormalized(10f, 540f, viewport)
        assertNull(leftBlackBarClick)
    }

    @Test
    fun testTouchSwipePacketStructure() {
        val json = JSONObject().apply {
            put("type", "INPUT_EVENT")
            put("sessionId", "test_session_swipe")
            put("subType", "TOUCH_SWIPE")
            put("normalizedX", 0.5)
            put("normalizedY", 0.8)
            put("endNormalizedX", 0.5)
            put("endNormalizedY", 0.2)
            put("durationMs", 350L)
            put("timestamp", 1700000000L)
        }

        assertEquals("INPUT_EVENT", json.getString("type"))
        assertEquals("TOUCH_SWIPE", json.getString("subType"))
        assertEquals(0.8, json.getDouble("normalizedY"), 0.001)
        assertEquals(0.2, json.getDouble("endNormalizedY"), 0.001)
        assertEquals(350L, json.getLong("durationMs"))
    }

    @Test
    fun testGlobalActionPacketStructure() {
        val json = JSONObject().apply {
            put("type", "INPUT_EVENT")
            put("sessionId", "test_session_global")
            put("subType", "ANDROID_GLOBAL_ACTION")
            put("globalAction", "NOTIFICATIONS")
            put("timestamp", 1700000000L)
        }

        assertEquals("INPUT_EVENT", json.getString("type"))
        assertEquals("ANDROID_GLOBAL_ACTION", json.getString("subType"))
        assertEquals("NOTIFICATIONS", json.getString("globalAction"))
    }
}
