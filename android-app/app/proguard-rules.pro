# Proguard rules for Smart Remote Control Android

# Keep OkHttp & WebSocket rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Services and Activities
-keep class com.smartremote.control.service.** { *; }
-keep class com.smartremote.control.pc.** { *; }
-keep class com.smartremote.control.compat.** { *; }
-keep class com.smartremote.control.permission.** { *; }
