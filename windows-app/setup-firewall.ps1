# PowerShell Script to configure Windows Firewall for Smart Remote Control
# Run as Administrator

$RuleName = "Smart Remote Control Local Connection (Port 52840)"
$Port = 52840

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " إعداد جدار حماية Windows لمنظومة Smart Remote Control    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Check Administrator privileges
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Warning "يرجى تشغيل هذا السكربت كمسؤول (Run as Administrator) لتطبيق قواعد جدار الحماية."
    exit 1
}

# Check if rule already exists
$existing = Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "✓ قاعدة جدار الحماية موجودة بالفعل ومفعلة للمنفذ $Port." -ForegroundColor Green
} else {
    Write-Host "جاري إضافة قاعدة جدار حماية واردة للمنفذ $Port (TCP) على الشبكات الخاصة والمحلية..." -ForegroundColor Yellow
    New-NetFirewallRule `
        -DisplayName $RuleName `
        -Direction Inbound `
        -LocalPort $Port `
        -Protocol TCP `
        -Action Allow `
        -Profile Private, Domain `
        -Description "السماح بالاتصال المحلي المباشر بين هواتف Android وبرنامج Smart Remote Control على Windows"
    Write-Host "✓ تمت إضافة قاعدة جدار الحماية بنجاح! المنفذ $Port جاهز لاستقبال اتصالات الهاتف." -ForegroundColor Green
}

# Reserve URL for HttpListener
try {
    netsh http add urlacl url="http://*:$Port/" user="Everyone" | Out-Null
    Write-Host "✓ تم تسجيل بادئة URL للمنفذ $Port بنجاح." -ForegroundColor Green
} catch {}

