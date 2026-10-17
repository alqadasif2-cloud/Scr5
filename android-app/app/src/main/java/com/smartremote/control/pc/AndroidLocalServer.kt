package com.smartremote.control.pc

import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * خادم WebSocket محلي عالي الكفاءة مدمج في أندرويد
 * يتيح لكومبيوتر Windows أو هاتف Android آخر الاتصال المباشر على الشبكة المحلية
 * متوافق بالكامل مع معيار RFC 6455 وإطارات التحكم Ping (0x09) / Pong (0x0A) / Close (0x08)
 */
class AndroidLocalServer(private val port: Int = 52840) {

    companion object {
        private const val TAG = "AndroidLocalServer"
        private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val MAX_HANDSHAKE_HEADER_SIZE = 16384
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val threadPool = Executors.newCachedThreadPool()

    private var activeClientSocket: Socket? = null
    private var activeClientOut: OutputStream? = null
    private val writeLock = Any()

    var onPacketReceived: ((jsonString: String) -> Unit)? = null
    var onClientConnected: ((clientAddress: String) -> Unit)? = null
    var onClientDisconnected: (() -> Unit)? = null

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)

        threadPool.execute {
            try {
                // تفعيل إعادة استخدام المنفذ لتجنب خطأ Address already in use عند إعادة التشغيل
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(port))
                serverSocket = ss
                Log.i(TAG, "خادم أندرويد المحلي يعمل الآن على كافة الواجهات 0.0.0.0:$port")

                while (isRunning.get()) {
                    val socket = serverSocket?.accept() ?: break
                    // تحسين أداء المقبس لتقليل زمن التأخير ونبضات TCP
                    socket.tcpNoDelay = true
                    Log.i(TAG, "اتصال وارد من: ${socket.inetAddress.hostAddress}:${socket.port}")

                    threadPool.execute {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "خطأ في خادم أندرويد المحلي: ${e.message}")
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        var clientStreamOut: OutputStream? = null
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            clientStreamOut = output

            // 1. قراءة طلب الـ HTTP Handshake بايت تلو الآخر حتى ترويسة \r\n\r\n
            // هذا الإجراء جوهري لمنع ابتلاع أي بايتات من إطارات WebSocket اللاحقة بواسطة BufferedReader
            val headerBytes = ByteArrayOutputStream()
            val singleByte = ByteArray(1)
            var foundEnd = false

            while (input.read(singleByte) != -1) {
                headerBytes.write(singleByte[0].toInt())
                val current = headerBytes.toByteArray()
                val len = current.size
                if (len >= 4 &&
                    current[len - 4] == '\r'.code.toByte() &&
                    current[len - 3] == '\n'.code.toByte() &&
                    current[len - 2] == '\r'.code.toByte() &&
                    current[len - 1] == '\n'.code.toByte()
                ) {
                    foundEnd = true
                    break
                }
                if (len > MAX_HANDSHAKE_HEADER_SIZE) {
                    break
                }
            }

            if (!foundEnd) {
                Log.w(TAG, "لم يتم العثور على ترويسات HTTP صالحة من العميل")
                socket.close()
                return
            }

            val headerText = String(headerBytes.toByteArray(), Charsets.UTF_8)
            var webSocketKey = ""

            for (rawLine in headerText.split("\r\n", "\n")) {
                if (rawLine.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                    webSocketKey = rawLine.substringAfter(":").trim()
                    break
                }
            }

            if (webSocketKey.isEmpty()) {
                // ليس طلب WebSocket ترقية
                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"AndroidRemoteHostReady\",\"port\":$port}\r\n"
                output.write(resp.toByteArray(Charsets.UTF_8))
                output.flush()
                socket.close()
                return
            }

            // 2. إرسال استجابة المصافحة الرسمية 101 Switching Protocols
            val acceptKey = generateWebSocketAccept(webSocketKey)
            val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"

            synchronized(writeLock) {
                output.write(response.toByteArray(Charsets.UTF_8))
                output.flush()
            }

            synchronized(this) {
                activeClientSocket?.close()
                activeClientSocket = socket
                activeClientOut = output
            }

            val clientIp = socket.inetAddress.hostAddress ?: "Unknown"
            Log.i(TAG, "تمت مصافحة WebSocket بنجاح مع العميل: $clientIp")
            onClientConnected?.invoke(clientIp)

            // 3. قراءة إطارات الـ WebSocket وفق معيار RFC 6455 بما فيها إطارات Ping و Pong
            readWebSocketFrames(input, output)

        } catch (e: Exception) {
            Log.e(TAG, "انقطع اتصال العميل: ${e.message}")
        } finally {
            synchronized(this) {
                if (activeClientSocket == socket) {
                    activeClientSocket = null
                    activeClientOut = null
                }
            }
            try { socket.close() } catch (_: Exception) {}
            onClientDisconnected?.invoke()
        }
    }

    private fun generateWebSocketAccept(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest((key + WEBSOCKET_GUID).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * قراءة وفك تشفير إطارات RFC 6455 ومعالجة إطارات التحكم Ping/Pong/Close
     */
    private fun readWebSocketFrames(input: InputStream, output: OutputStream) {
        while (isRunning.get()) {
            val b1 = input.read()
            if (b1 == -1) break

            val opcode = b1 and 0x0F

            val b2 = input.read()
            if (b2 == -1) break

            val isMasked = (b2 and 0x80) != 0
            var length = (b2 and 0x7F).toLong()

            if (length == 126L) {
                val byte1 = input.read()
                val byte2 = input.read()
                if (byte1 == -1 || byte2 == -1) break
                length = (((byte1 and 0xFF) shl 8) or (byte2 and 0xFF)).toLong()
            } else if (length == 127L) {
                var len: Long = 0
                for (i in 0 until 8) {
                    val b = input.read()
                    if (b == -1) return
                    len = (len shl 8) or (b.toLong() and 0xFF)
                }
                length = len
            }

            val mask = ByteArray(4)
            if (isMasked) {
                var read = 0
                while (read < 4) {
                    val r = input.read(mask, read, 4 - read)
                    if (r == -1) return
                    read += r
                }
            }

            // قراءة حمولة الإطار
            val payload = ByteArray(length.toInt())
            var totalRead = 0
            while (totalRead < length) {
                val r = input.read(payload, totalRead, (length - totalRead).toInt())
                if (r == -1) return
                totalRead += r
            }

            // فك القناع إذا كان مرسلاً من العميل
            if (isMasked) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
            }

            // معالجة نوع الإطار وفق معيار RFC 6455
            when (opcode) {
                0x01 -> { // Text frame (حزم JSON للأوامر والتحكم)
                    val text = String(payload, Charsets.UTF_8)
                    onPacketReceived?.invoke(text)
                }
                0x09 -> { // RFC 6455 Ping Control Frame
                    // إرسال رد Pong فوري مع نفس حمولة بايتات الـ Ping كما يشترط معيار WebSocket
                    // هذا هو الحل الجذري لمنع خطأ: "sent ping but didn't receive pong within 2000ms"
                    sendPongInternal(output, payload)
                }
                0x0A -> { // RFC 6455 Pong Control Frame
                    // تلقي إقرار نبضات الحياة من العميل
                    Log.v(TAG, "تم استلام Pong من العميل (الحمولة: ${payload.size} بايت)")
                }
                0x08 -> { // Close frame
                    sendCloseInternal(output, 1000, "Normal Closure")
                    break
                }
                0x02 -> { // Binary frame
                    // إطار ثنائي مدعوم اختيارياً
                }
            }
        }
    }

    /**
     * إرسال رد Pong فوري استجابةً لإطار Ping
     * يتضمن ترويسة Opcode 0x0A مع نفس حمولة البايتات وبدون قناع (كما يشترط الخادم)
     */
    private fun sendPongInternal(output: OutputStream, payload: ByteArray) {
        synchronized(writeLock) {
            try {
                val len = payload.size.coerceAtMost(125)
                val frame = ByteArray(2 + len)
                frame[0] = 0x8A.toByte() // FIN + Opcode 0x0A (Pong)
                frame[1] = len.toByte()   // Mask bit 0 + length
                if (len > 0) {
                    System.arraycopy(payload, 0, frame, 2, len)
                }
                output.write(frame)
                output.flush()
                Log.v(TAG, "تم إرسال رد Pong بنجاح (${len} بايت)")
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في إرسال Pong: ${e.message}")
            }
        }
    }

    /**
     * إرسال إطار الإغلاق Close
     */
    private fun sendCloseInternal(output: OutputStream, code: Int, reason: String) {
        synchronized(writeLock) {
            try {
                val reasonBytes = reason.toByteArray(Charsets.UTF_8).let {
                    if (it.size > 123) it.copyOf(123) else it
                }
                val payloadLen = 2 + reasonBytes.size
                val frame = ByteArray(2 + payloadLen)
                frame[0] = 0x88.toByte() // FIN + Opcode 0x08 (Close)
                frame[1] = payloadLen.toByte()
                frame[2] = ((code ushr 8) and 0xFF).toByte()
                frame[3] = (code and 0xFF).toByte()
                System.arraycopy(reasonBytes, 0, frame, 4, reasonBytes.size)

                output.write(frame)
                output.flush()
            } catch (_: Exception) {}
        }
    }

    /**
     * إرسال حزمة نصية JSON للعميل المتصل
     */
    fun sendText(text: String) {
        val out = synchronized(this) { activeClientOut } ?: return
        synchronized(writeLock) {
            try {
                val payload = text.toByteArray(Charsets.UTF_8)
                val header = mutableListOf<Byte>()

                header.add(0x81.toByte()) // FIN + Opcode 1 (Text)

                val len = payload.size
                when {
                    len <= 125 -> {
                        header.add(len.toByte())
                    }
                    len <= 65535 -> {
                        header.add(126.toByte())
                        header.add(((len ushr 8) and 0xFF).toByte())
                        header.add((len and 0xFF).toByte())
                    }
                    else -> {
                        header.add(127.toByte())
                        for (i in 7 downTo 0) {
                            header.add(((len.toLong() ushr (8 * i)) and 0xFF).toByte())
                        }
                    }
                }

                out.write(header.toByteArray())
                out.write(payload)
                out.flush()
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في إرسال الإطار: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            activeClientSocket?.close()
            serverSocket?.close()
        } catch (_: Exception) {}
        threadPool.shutdownNow()
    }
}
