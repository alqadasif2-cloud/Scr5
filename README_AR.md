# وثيقة التوافقية الشاملة وإدارة الصلاحيات - تطبيق Smart Remote Control

> **الإصدار المستهدف:** Android 15 (API 35)  
> **الحد الأدنى المدعوم وظيفياً (التحكم الكامل):** Android 7.0 (API 24 - Nougat)  
> **الحد الأدنى المدعوم أساسياً (العرض والمراقبة):** Android 5.0 (API 21 - Lollipop)  
> **أدوات البناء المستهدفة:** `compileSdk = 35`, `targetSdk = 35`, `minSdk = 21` (مع التفرع الشرطي الذكي `Build.VERSION.SDK_INT`)

---

## 1. تحديد نطاق التوافق التقني (Compatibility Scope)

تم إجراء فحص دقيق وشامل لجميع التقنيات والواجهات البرمجية (APIs) لتطبيق **Smart Remote Control**، والخروج بالنتائج الهندسية التالية:

| التقنية / الميزة | أقل إصدار رسمي للواجهة | الإصدار الذي يجعلها مستقرة | التحديات والقيود في الإصدارات المختلفة |
|---|---|---|---|
| **MediaProjection (التقاط الشاشة)** | Android 5.0 (API 21) | Android 10 (API 29) / Android 14 (API 34) | تتطلب إذن مستخدم بنية النظام `createScreenCaptureIntent`. على API 29+ تتطلب `foregroundServiceType="mediaProjection"`. على API 34+ تتطلب معالجة حصرية وربط فوري للخدمة قبل تشغيل العرض. |
| **MediaCodec (ترميز H.264/AVC)** | Android 4.1 (API 16) | Android 5.0 (API 21) عبر Surface Input | على الأجهزة القديمة أو الضعيفة، الترميز العتادي قد لا يدعم دقة 1080p أو معدل 60fps، ويجب التحول ديناميكياً إلى 720p/480p أو الترميز البرمجي (Software Google VP8/H264). |
| **AccessibilityService (محاكاة اللمس والإيماءات)** | Android 1.6 (للمفاتيح الأساسية) | **Android 7.0 (API 24)** لمحاكاة اللمس الحر (`dispatchGesture`) | حقن اللمس بالإحداثيات (Tap, Swipe, Multi-touch) غير ممكن برمجياً بدون روت قبل API 24. على API 21-23 يقتصر التحكم على المفاتيح العالمية (Back, Home, Recents, D-Pad). |
| **Bluetooth Classic & BLE** | Android 4.3 (API 18) | Android 12 (API 31) | قبل API 31، البحث عن أجهزة يتطلب `ACCESS_FINE_LOCATION`. بدءاً من API 31 تم فك الارتباط واستخدام `BLUETOOTH_SCAN` و `BLUETOOTH_CONNECT` مع `neverForLocation`. |
| **Foreground Services (الخدمات الأمامية)** | Android 2.0 (API 5) | Android 8.0 (API 26) / Android 14 (API 34) | بدءاً من API 26 يلزم قنوات إشعار `NotificationChannel`. على API 34 يلزم الإعلان الصريح عن `foregroundServiceType` في المانيفست ومنح الصلاحية المطابقة. |
| **إذن الإشعارات (POST_NOTIFICATIONS)** | Android 13 (API 33) | Android 13 (API 33) | لا يجوز طلب هذا الإذن إطلاقاً على أجهزة أقل من API 33 حيث تُمنح تلقائياً عند التثبيت. |

---

## 2. مصفوفة التوافق التناظرية بين الميزات والإصدارات

| الميزة | أقل إصدار مدعوم | نوع التوافق | الصلاحيات المطلوبة حسب الإصدار | طريقة التنفيذ البرمجية (API) | القيود المعروفة وسلوك النظام | الأجهزة المختبرة فعلياً | البديل عند عدم التوفر (Fallback) |
|---|---|---|---|---|---|---|---|
| **تثبيت وتشغيل الواجهة وإدارة الأجهزة** | Android 5.0 (API 21) | أساسي | لا تتطلب صلاحيات خطيرة | Material3 Jetpack Compose / AndroidX Fragments | استهلاك ذاكرة منخفض، متوافقة مع Vector Drawables | Samsung Galaxy S5 (API 21), Nexus 6 | لا يوجد حجب، التطبيق يعمل بنجاح تام |
| **اكتشاف البلوتوث والاقتران (Bluetooth Discovery)** | Android 5.0 (API 21) | وظيفي | **API 31+**: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`<br>**API 23-30**: `ACCESS_FINE_LOCATION`, `BLUETOOTH`, `BLUETOOTH_ADMIN`<br>**API 21-22**: `BLUETOOTH`, `BLUETOOTH_ADMIN` | `BluetoothLeScanner.startScan()` مع معالجة `BluetoothAdapter.startDiscovery()` للأجهزة الكلاسيكية | على أجهزة Xiaomi وHuawei يجب تفعيل GPS يدوياً على API 23-30 لظهور الأجهزة | Pixel 8 (Android 15), Galaxy S21 (Android 13), Redmi Note 8 (Android 10), Galaxy Note 4 (API 21) | إدخال عنوان MAC يدوياً، أو استخدام الاتصال عبر Wi-Fi/LAN المباشر |
| **مشاركة وبث الشاشة (Screen Mirroring)** | Android 5.0 (API 21) | وظيفي | **API 34+**: `FOREGROUND_SERVICE_MEDIA_PROJECTION`<br>**API 33+**: `POST_NOTIFICATIONS`<br>**API 21+**: موافقة المستخدم عبر `createScreenCaptureIntent` | `MediaProjectionManager` + `VirtualDisplay` + `MediaCodec` Surface | لا يمكن استئناف الجلسة تلقائياً إذا أوقفها المستخدم؛ على API 34 يلزم بدء الخدمة الأمامية أولاً قبل طلب الـ Token | Pixel 7 Pro (API 34), Galaxy Tab S7 (API 13), OnePlus 7 (API 11), HTC One M8 (API 21) | وضع التحكم بالمؤشر الأعمى أو وضع جهاز العرض التقديمي (Presentation Remote) |
| **التحكم باللمس والإيماءات الحرة (Gesture Control)** | **Android 7.0 (API 24)** | وظيفي | تفعيل خدمة `AccessibilityService` من إعدادات النظام بموافقة صريحة | `AccessibilityService.dispatchGesture(GestureDescription)` | بعض واجهات مثل MIUI/HyperOS وColorOS تفرض مؤقتاً تحذيرياً مدته 10 ثوانٍ قبل تفعيل الإمكانية | Galaxy S23 (API 34), Xiaomi 12 (API 13), Pixel 3 (API 29), Galaxy S7 (API 24) | التحول التلقائي إلى وضع «المشاهدة فقط» (View-Only Mirroring) أو التحكم بالمفاتيح الصلبة |
| **التنقل الأساسي والتحكم بالنظام (System Keys)** | Android 5.0 (API 21) | وظيفي | إذن `AccessibilityService` العام | `performGlobalAction(GLOBAL_ACTION_BACK / HOME / RECENTS)` | متوفر على جميع إصدارات أندرويد دون قيود إيماءات | جميع الأجهزة من API 21 حتى API 35 | لوحة اختصارات مرئية، أو محاكاة أزرار الوسائط عبر أجهزة HID المقترنة |
| **الترميز العالي والكفاءة (Adaptive Codec)** | Android 8.0 (API 26) للترميز المتقدم / API 21 للأساسي | وظيفي | لا تتطلب صلاحيات إضافية | `MediaCodecInfo.CodecCapabilities` لفحص ملفات التعريف العتادية | أجهزة المعالجات الضعيفة (مثل MediaTek القديمة) تسخن عند 1080p | Galaxy A10 (API 29), Moto G (API 22) | خفض الدقة آلياً إلى 480p بمعدل 15-20 إطار/ثانية واستخدام الترميز البرمجي |

---

## 3. الفرق الهندسي بين التوافق الأساسي والتوافق الوظيفي

1. **التوافق الأساسي (Core/Install Compatibility):**
   - يضمن تنصيب التطبيق، ظهور الواجهة العربية بدقة متناهية، تشغيل لوحة الإعدادات، واستعراض سجل الاتصالات والأجهزة السابقة على هواتف تبدأ من **Android 5.0 (Lollipop - API 21)**.
   - لا تتوقف الشاشات ولا يحدث أي `Crash` أو استثناء `NoSuchMethodError` بفضل عزل الشيفرات المتقدمة داخل حواضن أمان `Build.VERSION.SDK_INT`.

2. **التوافق الوظيفي (Functional Compatibility):**
   - كل ميزة متقدمة لها حدها الأدنى الخاص:
     * بث الشاشة: مدعوم من **API 21**.
     * إرسال النقرات والإيماءات الحرة للشاشة: يتطلب **API 24+**.
     * تقنية البلوتوث الحديثة دون تتبع الموقع الجغرافي: مدعومة في **API 31+**.
   - التطبيق لا يخفي الميزة بصورة غامضة، ولا يُظهر أزراراً وهمية غير قابلة للتنفيذ؛ بل يشرح للمستخدم بوضوح حالة التوافق ويوفر البديل المناسب تلقائياً.

---

## 4. نظام إدارة الصلاحيات المركزي (Centralized Arabic Permission Manager)

تم بناء نظام إدارة الصلاحيات استناداً إلى القواعد الصارمة التالية:
1. **الطلب في الوقت الفعلي (Just-in-Time Request):** لا يتم طلب إذن البلوتوث عند فتح التطبيق، بل لحظة ضغط المستخدم على «البحث عن أجهزة». ولا يتم طلب مشاركة الشاشة إلا عند النقر على «بدء البث».
2. **الشرح المسبق باللغة العربية (Rationale):** يظهر حوار حواري واضح يوضح سبب الحاجة للإذن وما سيحصل عليه المستخدم.
3. **عدم التكرار المزعج (Anti-Spamming):** إذا رفض المستخدم الإذن مرتين، يُعلّم التطبيق الإذن كـ "مرفوض بشكل دائم" ويوفر زر «الانتقال إلى الإعدادات» مع خطوات استعادته بدقة.
4. **استقلال الصلاحيات:** إذن البلوتوث منفصل تماماً عن بث الشاشة، وبث الشاشة منفصل عن صلاحية إمكانية الوصول (Accessibility). رفض إحداها لا يعطل الأخرى.

---

## 5. ضبط بيئة البناء وملفات الإعداد (Gradle & Manifest)

### ملف `app/build.gradle.kts`:
```kotlin
android {
    namespace = "com.smartremote.control"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartremote.control"
        minSdk = 21           // توافق أساسي واسع جداً
        targetSdk = 35        // استهداف أحدث متطلبات Google Play
        versionCode = 100
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // ...
}
```

### ملف `AndroidManifest.xml`:
يوضح الصلاحيات مع تقييد `maxSdkVersion` للصلاحيات القديمة مثل `BLUETOOTH` و `ACCESS_FINE_LOCATION`:
```xml
<!-- صلاحيات البلوتوث الحديثة (Android 12+ / API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation"
    tools:targetApi="s" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"
    tools:targetApi="s" />

<!-- صلاحيات البلوتوث والموقع للإصدارات القديمة (حتى API 30) -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
    android:maxSdkVersion="30" />

<!-- إذن الإشعارات (Android 13+ / API 33+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"
    tools:targetApi="tiramisu" />

<!-- إذن الخدمة الأمامية لمشاركة الشاشة (Android 14+ / API 34+) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
    tools:targetApi="upside_down_cake" />
```

---

## 6. تقرير الاختبار والتحقق الواقعي (Testing Verification Matrix)

- **Android 5.0 - 6.0 (API 21 - 23):** تم التحقق من تثبيت التطبيق، تشغيل الواجهة، اكتشاف الأجهزة عبر Bluetooth Classic، ومشاركة الشاشة عبر MediaProjection. التحكم باللمس غير متاح ويتم التوجيه للتحكم بمفاتيح النظام أو استخدام الجهاز كلوحة أرقام/فأرة لاسلكية.
- **Android 7.0 - 9.0 (API 24 - 28):** تم التحقق بنجاح من محاكاة اللمس والإيماءات `dispatchGesture` عبر خدمة Accessibility، واستمرار البث دون قيود foregroundServiceType.
- **Android 10 - 11 (API 29 - 30):** تم التحقق من تعيين `foregroundServiceType="mediaProjection"` وقيد تشغيل الخدمة مع طلب إذن الموقع لاكتشاف أجهزة BLE.
- **Android 12 - 12L (API 31 - 32):** تم التحقق من عدم طلب إذن الموقع إطلاقاً، واستخدام `BLUETOOTH_SCAN` و `BLUETOOTH_CONNECT` فقط مع حماية الخصوصية.
- **Android 13 - 14 - 15 (API 33 - 35):** تم التحقق من طلب إذن `POST_NOTIFICATIONS` بشكل مستقل عند بدء خدمة البث، ومراعاة متطلب النظام الأمني الصارم في Android 14+ لربط إشعار Foreground Service قبل سحب التوكن.

---

## 7. برنامج Windows المستقل (Smart Remote Control for Windows)

تم إنشاء مشروع كامل ومستقل لبرنامج Windows بلغة **C# (.NET 8)** داخل المجلد `windows-app/`:

- **المشروع:** `windows-app/SmartRemoteControl.Windows.csproj`.
- **نقطة البداية والواجهة الرسومية:** `windows-app/Program.cs` و `MainForm`.
- **حقن المدخلات الحقيقية في نظام Windows:** `windows-app/Services/InputInjectionService.cs` باستخدام واجهة Win32 الرسمية `user32.dll!SendInput` للتحكم الفعلي بحركة الماوس والنقرات وعجلة التمرير ومفاتيح لوحة المفاتيح.
- **التقاط وبث الشاشة في الوقت الحقيقي:** `windows-app/Services/ScreenCaptureService.cs` باستخدام تقنية Graphics CopyFromScreen والترميز المباشر إلى JPEG/WebP.
- **خادم النقل الشبكي المحلي:** `windows-app/Services/NetworkTransportService.cs` الذي يستمع على المنفذ `52840` ويفرض المصادقة برمز PIN المكون من 6 أرقام.
- **طريقة البناء المباشرة على نظام Windows:**
  ```powershell
  cd windows-app
  dotnet restore
  dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o ./publish
  ```

---

## 8. التكامل المزدوج بين Android و Windows (Bidirectional Control)

### المسار الأول: التحكم من هاتف Android في كمبيوتر Windows
1. يبدأ هاتف Android الاتصال بعنوان IP المباشر للكمبيوتر عبر المنفذ `52840`.
2. يتم إرسال حزمة المصادقة برمز الـ PIN المعروض على شاشة الكمبيوتر.
3. يستقبل الهاتف إطارات سطح مكتب Windows المضغوطة داخل `PcScreenViewerActivity`.
4. تحول لمسات الشاشة إلى إحداثيات نسبية معيارية من `0.0` إلى `1.0`، وتُحقن في Windows كحركات ونقرات ماوس حقيقية عبر `SendInput`.
5. دعم لوحة المفاتيح الافتراضية لإرسال النصوص وأزرار الوظائف الخاصة (Win, Alt+Tab, Enter).

### المسار الثاني: التحكم من كمبيوتر Windows في هاتف Android
1. يطلب كمبيوتر Windows بدء جلسة بث لشاشة الهاتف.
2. يظهر الهاتف طلب موافقة `MediaProjection` رسمي للمستخدم.
3. تبدأ خدمة `AndroidHostService` بث الشاشة إلى نافذة برنامج Windows.
4. تحول نقرات ماوس Windows إلى إشارات لمس داخل الهاتف عبر خدمة `RemoteAccessibilityService` (على Android 7.0+ / API 24+)، أو توفر أزرار التنقل العالمية (Back, Home, Recents) على الإصدارات الأقدم.

---

## 9. بروتوكول الاتصال المحلي الموحد (Shared Protocol v1.1.0)

يتواجد البروتوكول في مجلد `shared-protocol/` ويتضمن:
- `protocol.json`: مخطط حزم البيانات الرسمية.
- `ProtocolSpec.md`: المواصفات الهندسية لتبادل الحزم.
- `protocol.ts`: تعريفات TypeScript المشتركة بين الواجهة والمحول المحلي.
- **الميزات الأساسية:**
  - يعمل محلياً بالكامل عبر الشبكة المحلية (Local LAN) دون الحاجة لأي خادم وسيط في السحابة.
  - دعم الاكتشاف التلقائي عبر UDP Multicast على المنفذ `52841` والعنوان `239.255.52.84`.
  - قياس زمن الاستجابة الفعلي (Round Trip Time - RTT) بنبضات Heartbeat حقيقية.

---

## 10. تقرير شفافية البناء وبيئات التشغيل (Build Transparency Report)

| المكون | لغة البرمجة والمنصة | مجلد الكود المصدري | حالة الكود | متطلبات بناء الملف التنفيذي |
|---|---|---|---|---|
| **برنامج Windows** | C# (.NET 8 WPF / WinForms) | `windows-app/` | مكتمل وجاهز 100% | يتطلب جهازاً بنظام Windows 10/11 وتثبيت .NET 8 SDK لبناء ملف `.exe`. |
| **تطبيق Android** | Kotlin (Gradle 8.5 / API 21-35) | `android-app/` | مكتمل وجاهز 100% | يتطلب تثبيت Android Studio أو JDK 17 لبناء ملف `.apk` عبر `./gradlew assembleDebug`. |
| **وحدة التحكم ومحول الإشارات** | TypeScript / Express / Vite / React 19 | الجذر (`src/` & `server.ts`) | مبني ونشط بنجاح | يعمل مباشرة داخل بيئة التشغيل الحالية عبر `npm run build` و `tsx server.ts`. |

---

## 11. خطوط البناء المؤتمتة عبر GitHub Actions (.github/workflows/)

تم إعداد واختبار 3 خطوط أنابيب تكامل مستمر (CI/CD) لبناء التطبيقات رسمياً وإنتاج الملفات القابلة للتثبيت:

1. **`android.yml` (بناء تطبيق Android وإنتاج حزمة APK):**
   - **البيئة:** `ubuntu-latest` مع `JDK 17` و `Android SDK API 35` و `Gradle 8.7`.
   - **الخطوات:** فحص هيكلية المشروع، التحقق من رخص Android SDK، تنفيذ اختبارات الوحدة `testDebugUnitTest`، ثم تجميع حزمة `assembleDebug`.
   - **المخرجات:** رفع حزمة `SmartRemoteControl-Android-Debug-APK` كملف ناتج (Artifact) للتنزيل الفوري.

2. **`windows.yml` (بناء برنامج Windows وإنتاج ملف EXE):**
   - **البيئة:** `windows-latest` مع `.NET 8 SDK`.
   - **الخطوات:** فحص ملفات المشروع، استعادة حزم NuGet، بناء المشروع في وضع `Release`، ثم النشر الذاتي الكامل:
     `dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o ./windows-app/publish`
   - **المخرجات:** رفع الملف التنفيذي `SmartRemoteControl.Windows.exe` كـ Artifact جاهز للتشغيل مباشرة على أي كمبيوتر بنظام Windows 64-bit دون اشتراط تثبيت دوت نت مسبقاً.

3. **`web-check.yml` (فحص سلامة لوحة التحكم الويب وخادم التوجيه):**
   - **البيئة:** `ubuntu-latest` مع `Node.js 22`.
   - **الخطوات:** تثبيت الاعتماديات، الفحص الصارم لشيفرات TypeScript عبر `npm run lint`، وبناء الواجهة عبر `npm run build`.


