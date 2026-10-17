# دليل بناء وتشغيل تطبيق Android (Smart Remote Control for Android)

مشروع أصلي مكتوب بلغة **Kotlin** لنظام **Android** (يدعم من Android 5.0 Lollipop API 21 حتى Android 15 API 35) للتحكم المتبادل مع كمبيوتر Windows.

---

## 1. متطلبات بيئة البناء
- بيئة التطوير: **Android Studio Hedgehog أو أحدث** (أو JDK 17 + Android Command Line Tools).
- أدوات البناء: `Gradle 8.5+` و `Android Gradle Plugin 8.5.2`.
- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 21`.

---

## 2. أوامر بناء ملف الـ APK

في المجلد `android-app`:

### بناء حزمة التطبيق التجريبية (Debug APK):
```bash
./gradlew assembleDebug
```
ينتج الملف في المسار:
`android-app/app/build/outputs/apk/debug/app-debug.apk`

### بناء حزمة الإنتاج الموقعة (Release APK):
```bash
./gradlew assembleRelease
```

---

## 3. وضعيات العمل مع كمبيوتر Windows
1. **وضع التحكم بالكمبيوتر (Client Mode):**
   - يفتح التطبيق `PcScreenViewerActivity`.
   - يبث شاشة سطح مكتب Windows في الوقت الحقيقي.
   - يحول اللمس والإيماءات إلى نقرات ماوس وأوامر لوحة مفاتيح.
2. **وضع بث شاشة الهاتف للكمبيوتر (Host Mode):**
   - يطلب إذن `MediaProjection` لبدء خدمة `AndroidHostService`.
   - يعرض شاشة الهاتف داخل نافذة برنامج Windows.
   - يستقبل نقرات ماوس Windows ويحقنها عبر `RemoteAccessibilityService` (على API 24+).
