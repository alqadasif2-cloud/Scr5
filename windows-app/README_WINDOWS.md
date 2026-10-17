# دليل بناء وتشغيل برنامج Windows (Smart Remote Control for Windows)

مشروع أصلي مكتوب بلغة **C#** على منصة **.NET 8** للتحكم المتبادل بين Windows وهواتف Android عبر الشبكة المحلية.

---

## 1. المتطلبات البرمجية للبناء والتشغيل
- نظام التشغيل: **Windows 10 (إصدار 1903 أو أحدث) أو Windows 11**.
- بيئة التطوير: **.NET 8 SDK** (أو تثبيت Visual Studio 2022 مع حزمة .NET Desktop Development).
- المعمارية المدعومة: **x64** أو **ARM64**.

---

## 2. خطوات البناء وإنتاج الملف التنفيذي (.exe)

افتح سطر الأوامر (PowerShell أو CMD) في مجلد `windows-app`:

### التحقق من الحزم وبناء المشروع:
```powershell
dotnet restore
dotnet build -c Release
```

### إنتاج برنامج مستقل بدون الحاجة لتثبيت .NET مسبقاً (Self-Contained Executable):
```powershell
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o ./publish
```
سيتم إنتاج ملف `SmartRemoteControl.Windows.exe` داخل مجلد `publish`.

---

## 3. الصلاحيات وإدارة حساب المستخدم (UAC)
- يعمل البرنامج بصلاحيات المستخدم العادية لنقل الشاشة والتحكم بالتطبيقات القياسية.
- للتحكم في تطبيقات تعمل بصلاحيات المسؤول (Run as Administrator) أو شاشات قفل النظام وإدارة المهام (Task Manager)، يجب تشغيل `SmartRemoteControl.Windows.exe` كمسؤول (Run as administrator) بسبب قيود نظام Windows الأمني (UIPI - User Interface Privilege Isolation).
