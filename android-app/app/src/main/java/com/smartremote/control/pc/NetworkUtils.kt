package com.smartremote.control.pc

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * أدوات مساعدة لفحص عناوين الشبكة المحلية والـ IP
 */
object NetworkUtils {

    /**
     * استخراج عنوان IPv4 المحلي للجهاز المتصل عبر Wi-Fi أو الإيثرنت
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue

                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: ""
                        if (hostAddress.isNotEmpty() && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    /**
     * توليد رمز PIN عشوائي من 6 أرقام
     */
    fun generatePin(): String {
        val randomNum = (100000..999999).random()
        return randomNum.toString()
    }
}
