package com.smartremote.control

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * اختبارات الوحدة للبروتوكول ومعادلات الإحداثيات والـ PIN
 */
class ProtocolUnitTest {

    @Test
    fun testPinValidation() {
        val validPin = "582914"
        val invalidPin = "123"

        assertTrue(validPin.length == 6 && validPin.all { it.isDigit() })
        assertFalse(invalidPin.length == 6)
    }

    @Test
    fun testNormalizedCoordinatesConversion() {
        val screenWidth = 1920
        val screenHeight = 1080

        val normX = 0.5f
        val normY = 0.5f

        val pixelX = Math.round(normX * screenWidth)
        val pixelY = Math.round(normY * screenHeight)

        assertEquals(960, pixelX)
        assertEquals(540, pixelY)

        val winSendInputX = Math.round(normX * 65535.0)
        val winSendInputY = Math.round(normY * 65535.0)

        assertEquals(32768L, winSendInputX)
        assertEquals(32768L, winSendInputY)
    }

    @Test
    fun testInputPacketJsonStructure() {
        val json = JSONObject().apply {
            put("type", "INPUT_EVENT")
            put("sessionId", "test_session_123")
            put("subType", "MOUSE_DOWN")
            put("normalizedX", 0.75)
            put("normalizedY", 0.25)
            put("button", "left")
            put("timestamp", 1700000000L)
        }

        assertEquals("INPUT_EVENT", json.getString("type"))
        assertEquals("MOUSE_DOWN", json.getString("subType"))
        assertEquals(0.75, json.getDouble("normalizedX"), 0.001)
        assertEquals("left", json.getString("button"))
    }
}
