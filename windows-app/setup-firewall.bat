@echo off
chcp 65001 > nul
echo ==========================================================
echo  إعداد جدار حماية Windows وشبكة Smart Remote Control
echo ==========================================================
echo.

echo 1. التحقق من فتح المنفذ 52840 في جدار حماية Windows (Windows Defender Firewall)...
netsh advfirewall firewall show rule name="Smart Remote Control Local (Port 52840)" >nul 2>&1
if %errorlevel% equ 0 (
    echo [✓] قاعدة جدار الحماية موجودة بالفعل ومفعلة للمنفذ 52840 TCP.
) else (
    echo جاري فتح المنفذ المحلي 52840 TCP على الشبكات الخاصة والدومين...
    netsh advfirewall firewall add rule name="Smart Remote Control Local (Port 52840)" dir=in action=allow protocol=TCP localport=52840 profile=private,domain
    if %errorlevel% equ 0 (
        echo [✓] تم فتح المنفذ 52840 بنجاح في جدار الحماية!
    ) else (
        echo [✕] فشل فتح المنفذ. يرجى الضغط بزر الفأرة الأيمن على الملف واختيار "تشغيل كمسؤول" (Run as administrator).
    )
)

echo.
echo 2. تسجيل بادئة خادم HttpListener للمنفذ 52840...
netsh http add urlacl url=http://*:52840/ user=Everyone >nul 2>&1
if %errorlevel% equ 0 (
    echo [✓] تم حجز بادئة URL لخادم HttpListener بنجاح!
) else (
    echo [!] بادئة URL مسجلة مسبقاً أو يعمل البرنامج عبر خادم TcpListener المباشر.
)

echo.
echo ==========================================================
echo [✓] اكتملت الإعدادات. يمكنك الآن تشغيل SmartRemoteControl.Windows.exe
echo ==========================================================
pause
