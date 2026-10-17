import React, { useState, useEffect, useRef } from 'react';
import {
  DeviceProfile,
  PermissionKey,
  PermissionState,
  StreamQualityProfile,
} from '../types/android';
import {
  ANDROID_VERSIONS,
  STREAM_QUALITY_PROFILES,
} from '../data/compatibilityData';
import {
  Cast,
  Cast as ScreenCastIcon,
  Bluetooth,
  BluetoothSearching,
  Wifi,
  Power,
  Volume2,
  VolumeX,
  Volume1,
  RotateCw,
  Camera,
  Layers,
  Home,
  ArrowRight,
  Maximize2,
  MousePointer,
  Hand,
  Sliders,
  AlertTriangle,
  CheckCircle,
  HelpCircle,
  Smartphone,
  Flame,
  ShieldAlert,
  Radio,
  Play,
  Square,
  Activity,
} from 'lucide-react';

interface DeviceSimulatorProps {
  currentDevice: DeviceProfile;
  permissionsState: Record<PermissionKey, PermissionState>;
  onRequestPermission: (key: PermissionKey) => void;
  onNavigateToPermissionsTab: () => void;
}

export const DeviceSimulator: React.FC<DeviceSimulatorProps> = ({
  currentDevice,
  permissionsState,
  onRequestPermission,
  onNavigateToPermissionsTab,
}) => {
  // Simulator State
  const [isCastActive, setIsCastActive] = useState<boolean>(false);
  const [selectedQuality, setSelectedQuality] = useState<string>('auto');
  const [isBtConnected, setIsBtConnected] = useState<boolean>(false);
  const [isBtScanning, setIsBtScanning] = useState<boolean>(false);
  const [controlMode, setControlMode] = useState<'touch' | 'trackpad' | 'presentation'>('touch');
  const [deviceOrientation, setDeviceOrientation] = useState<'portrait' | 'landscape'>('portrait');
  const [touchRipples, setTouchRipples] = useState<Array<{ id: number; x: number; y: number; text?: string }>>([]);
  const [lastActionLog, setLastActionLog] = useState<string>('جاهز للاستخدام. تم تهيئة المحاكي.');
  const [activeApp, setActiveApp] = useState<'home' | 'settings' | 'camera' | 'gallery' | 'remoteApp'>('home');
  const [volumeLevel, setVolumeLevel] = useState<number>(75);
  const [isScreenLocked, setIsScreenLocked] = useState<boolean>(false);
  const [warningAlert, setWarningAlert] = useState<{ title: string; message: string } | null>(null);

  // Simulated metrics
  const [metrics, setMetrics] = useState({
    fps: 30,
    bitrateMbps: 2.4,
    latencyMs: 18,
    frameDropPercent: 0.1,
  });

  const screenRef = useRef<HTMLDivElement>(null);

  // Dynamic capabilities check
  const isGestureSupported = currentDevice.apiLevel >= 24;
  const isAccessibilityGranted = permissionsState['ACCESSIBILITY_SERVICE'] === 'GRANTED';
  const isScreenCastGranted = permissionsState['MEDIA_PROJECTION_CONSENT'] === 'GRANTED';

  const hasBtPermissions =
    currentDevice.apiLevel >= 31
      ? permissionsState['BLUETOOTH_SCAN'] === 'GRANTED' &&
        permissionsState['BLUETOOTH_CONNECT'] === 'GRANTED'
      : currentDevice.apiLevel >= 23
      ? permissionsState['ACCESS_FINE_LOCATION'] === 'GRANTED'
      : true;

  // Auto-adapt stream quality based on device RAM and CPU
  useEffect(() => {
    if (selectedQuality === 'auto') {
      if (currentDevice.ramGb <= 2 || !currentDevice.hasHwH264) {
        setMetrics((m) => ({ ...m, fps: 15, bitrateMbps: 0.6, latencyMs: 38 }));
      } else if (currentDevice.ramGb <= 4) {
        setMetrics((m) => ({ ...m, fps: 24, bitrateMbps: 1.2, latencyMs: 25 }));
      } else {
        setMetrics((m) => ({ ...m, fps: 60, bitrateMbps: 4.8, latencyMs: 14 }));
      }
    }
  }, [currentDevice, selectedQuality]);

  // Handle Touch on the virtual screen
  const handleScreenTouch = (e: React.MouseEvent<HTMLDivElement>) => {
    if (isScreenLocked) {
      setLastActionLog('الشاشة مقفلة! اضغط على زر الطاقة لفتح القفل.');
      return;
    }

    if (!screenRef.current) return;
    const rect = screenRef.current.getBoundingClientRect();
    const x = Math.round(e.clientX - rect.left);
    const y = Math.round(e.clientY - rect.top);

    // Add visual ripple
    const newRipple = { id: Date.now(), x, y };
    setTouchRipples((prev) => [...prev.slice(-3), newRipple]);
    setTimeout(() => {
      setTouchRipples((prev) => prev.filter((r) => r.id !== newRipple.id));
    }, 600);

    // Check version capability
    if (!isGestureSupported) {
      setWarningAlert({
        title: 'محاكاة اللمس غير مدعومة على هذا الإصدار',
        message: `جهازك يعمل بإصدار ${ANDROID_VERSIONS[currentDevice.apiLevel].versionName} (API ${currentDevice.apiLevel}). ميزة حقن إحداثيات اللمس الحر dispatchGesture تتطلب نظام Android 7.0 (API 24) فما فوق. يمكنك استخدام أزرار التنقل السفلية أو لوحة المؤشر البديلة.`,
      });
      setLastActionLog(`[تنبيه API ${currentDevice.apiLevel}] محاكاة اللمس الحر محجوبة نظامياً.`);
      return;
    }

    // Check accessibility permission
    if (!isAccessibilityGranted) {
      setWarningAlert({
        title: 'صلاحية إمكانية الوصول غير مفعلة',
        message: 'للتحكم باللمس عن بعد، يتطلب نظام Android تفعيل خدمة AccessibilityService الخاصة بالتطبيق من إعدادات النظام.',
      });
      setLastActionLog('[تحذير] لم يتم تفعيل إمكانية الوصول للتحكم باللمس.');
      return;
    }

    setLastActionLog(`[Accessibility API 24+] تم تنفيذ نقرة لمس في الإحداثيات: X=${x}, Y=${y}`);
  };

  // Bluetooth Scan handler
  const handleStartBtScan = () => {
    if (isBtScanning) return;

    if (!hasBtPermissions) {
      if (currentDevice.apiLevel >= 31) {
        onRequestPermission('BLUETOOTH_SCAN');
      } else if (currentDevice.apiLevel >= 23) {
        onRequestPermission('ACCESS_FINE_LOCATION');
      }
      return;
    }

    setIsBtScanning(true);
    setLastActionLog(`[Bluetooth] جاري البحث عن أجهزة التحكم القريبة باستخدام واجهة ${currentDevice.apiLevel >= 31 ? 'BLUETOOTH_SCAN (بدون موقع)' : 'BluetoothLeScanner + Location'}...`);

    setTimeout(() => {
      setIsBtScanning(false);
      setIsBtConnected(true);
      setLastActionLog('[Bluetooth] تم الاقتران والاتصال بنجاح بجهاز التحكم الذكي!');
    }, 1800);
  };

  // Screen Cast Toggle
  const handleToggleScreenCast = () => {
    if (isCastActive) {
      setIsCastActive(false);
      setLastActionLog('[MediaProjection] تم إيقاف جلسة بث الشاشة واستعادة الموارد.');
      return;
    }

    if (!isScreenCastGranted) {
      onRequestPermission('MEDIA_PROJECTION_CONSENT');
      return;
    }

    // Check Android 14+ Foreground Service restriction
    if (currentDevice.apiLevel >= 34 && permissionsState['FOREGROUND_SERVICE_MEDIA_PROJECTION'] !== 'GRANTED') {
      onRequestPermission('FOREGROUND_SERVICE_MEDIA_PROJECTION');
      return;
    }

    setIsCastActive(true);
    setLastActionLog(`[MediaProjection] بدأت جلسة البث بدقة ${currentDevice.screenResolution} بمعدل ${metrics.fps} إطار/ثانية.`);
  };

  // System navigation triggers
  const handleGlobalAction = (action: 'BACK' | 'HOME' | 'RECENTS') => {
    if (!isAccessibilityGranted) {
      onRequestPermission('ACCESSIBILITY_SERVICE');
      return;
    }

    if (action === 'BACK') {
      setActiveApp('home');
      setLastActionLog('[Accessibility Global] تنفيذ زر الرجوع GLOBAL_ACTION_BACK.');
    } else if (action === 'HOME') {
      setActiveApp('home');
      setLastActionLog('[Accessibility Global] تنفيذ زر الشاشة الرئيسية GLOBAL_ACTION_HOME.');
    } else if (action === 'RECENTS') {
      setLastActionLog('[Accessibility Global] استعراض التطبيقات الأخيرة GLOBAL_ACTION_RECENTS.');
    }
  };

  const activeQualityProfile = STREAM_QUALITY_PROFILES[selectedQuality] || STREAM_QUALITY_PROFILES.auto;

  return (
    <div className="space-y-6">
      {/* Top Banner Alert if limitations exist */}
      {!isGestureSupported && (
        <div className="bg-amber-950/60 border border-amber-500/40 rounded-xl p-4 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
          <div className="text-xs sm:text-sm text-amber-200">
            <strong className="font-bold text-white block mb-0.5">
              تنبيه توافقية العتاد: Android 5.0 (API {currentDevice.apiLevel})
            </strong>
            هذا الإصدار لا يدعم حقن لمسات الشاشة الحرة (dispatchGesture) لأنها ظهرت لأول مرة في 
            <span className="text-amber-300 font-bold"> Android 7.0 (API 24)</span>.
            يعمل التطبيق حالياً في <strong className="text-white">«نمط المشاهدة والتحكم بالمفاتيح العالمية»</strong> دون أي انهيار.
          </div>
        </div>
      )}

      {/* Grid: Virtual Phone Viewport + Remote Control Console */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        
        {/* Left / Center: Interactive Phone Frame (Col 7) */}
        <div className="lg:col-span-7 flex flex-col items-center">
          
          {/* Phone Outer Chassis */}
          <div className="relative w-full max-w-[340px] sm:max-w-[380px] bg-slate-900 p-3 sm:p-4 rounded-[42px] border-[6px] border-slate-700 shadow-2xl ring-1 ring-white/10">
            
            {/* Camera Hole / Speaker Notch */}
            <div className="absolute top-5 left-1/2 -translate-x-1/2 z-30 flex items-center gap-2">
              <div className="w-3.5 h-3.5 rounded-full bg-slate-950 border border-slate-800 flex items-center justify-center">
                <div className="w-1.5 h-1.5 rounded-full bg-indigo-950" />
              </div>
              <div className="w-12 h-1 rounded-full bg-slate-800" />
            </div>

            {/* Screen Bezel */}
            <div
              ref={screenRef}
              onClick={handleScreenTouch}
              className={`relative overflow-hidden rounded-[32px] select-none transition-all cursor-crosshair bg-slate-950 border border-slate-800 ${
                deviceOrientation === 'portrait' ? 'aspect-[9/19.5] min-h-[560px]' : 'aspect-[19.5/9] min-h-[300px]'
              }`}
            >
              {/* Screen Locked Overlay */}
              {isScreenLocked ? (
                <div className="absolute inset-0 bg-slate-950/90 backdrop-blur-md z-20 flex flex-col items-center justify-center text-center p-6">
                  <Smartphone className="w-12 h-12 text-slate-600 mb-2 animate-pulse" />
                  <p className="text-sm font-bold text-slate-300">الهاتف في وضع السكون</p>
                  <p className="text-xs text-slate-500 mt-1">اضغط على زر الطاقة في لوحة التحكم لتشغيل الشاشة</p>
                </div>
              ) : (
                <>
                  {/* Status Bar */}
                  <div className="h-7 pt-1 px-5 flex items-center justify-between text-[11px] font-semibold text-slate-300 bg-black/40 backdrop-blur-xs z-10">
                    <span className="font-mono">18:14</span>
                    <div className="flex items-center gap-2">
                      {isBtConnected && <Bluetooth className="w-3.5 h-3.5 text-indigo-400" />}
                      {isCastActive && (
                        <span className="flex items-center gap-1 text-[10px] text-emerald-400 bg-emerald-500/20 px-1.5 rounded">
                          <Cast className="w-3 h-3 animate-pulse" />
                          <span>بث</span>
                        </span>
                      )}
                      <Wifi className="w-3.5 h-3.5 text-slate-300" />
                      <span className="text-[10px] font-mono">89%</span>
                    </div>
                  </div>

                  {/* Active Cast Watermark if Mirroring */}
                  {isCastActive && (
                    <div className="absolute top-8 left-3 z-10 flex items-center gap-1.5 bg-emerald-950/80 border border-emerald-500/40 text-emerald-300 px-2 py-0.5 rounded-full text-[10px] font-bold">
                      <Radio className="w-3 h-3 animate-ping text-emerald-400" />
                      <span>جلسة البث المباشر نشطة</span>
                    </div>
                  )}

                  {/* Touch Ripples */}
                  {touchRipples.map((ripple) => (
                    <div
                      key={ripple.id}
                      style={{ left: ripple.x, top: ripple.y }}
                      className="absolute -translate-x-1/2 -translate-y-1/2 pointer-events-none z-30 flex items-center justify-center"
                    >
                      <span className="w-10 h-10 rounded-full border-2 border-indigo-400 bg-indigo-500/30 animate-ping" />
                      <span className="absolute w-3 h-3 rounded-full bg-white shadow-md shadow-indigo-500" />
                    </div>
                  ))}

                  {/* Phone Apps Content */}
                  <div className="h-full pt-4 pb-14 px-4 flex flex-col justify-between">
                    
                    {activeApp === 'home' && (
                      <div className="space-y-6">
                        {/* Clock Widget */}
                        <div className="text-center pt-8">
                          <h1 className="text-4xl font-extrabold text-white font-mono tracking-tight">18:14</h1>
                          <p className="text-xs text-slate-400 mt-1">الجمعة، 16 أكتوبر</p>
                          <div className="inline-flex items-center gap-1 mt-2 px-2.5 py-0.5 rounded-full bg-slate-800/80 text-[11px] text-slate-300 border border-slate-700">
                            <span>{currentDevice.name}</span>
                          </div>
                        </div>

                        {/* App Icons Grid */}
                        <div className="grid grid-cols-4 gap-4 pt-4">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setActiveApp('remoteApp');
                              setLastActionLog('تم فتح تطبيق Smart Remote Control داخل الهاتف.');
                            }}
                            className="flex flex-col items-center gap-1 group"
                          >
                            <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-indigo-600 to-emerald-400 flex items-center justify-center text-white shadow-lg shadow-indigo-500/20 group-hover:scale-105 transition">
                              <Smartphone className="w-6 h-6" />
                            </div>
                            <span className="text-[10px] text-slate-200 font-medium">Smart Remote</span>
                          </button>

                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setActiveApp('settings');
                              setLastActionLog('تم فتح إعدادات الهاتف.');
                            }}
                            className="flex flex-col items-center gap-1 group"
                          >
                            <div className="w-12 h-12 rounded-2xl bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300 group-hover:scale-105 transition">
                              <Sliders className="w-6 h-6" />
                            </div>
                            <span className="text-[10px] text-slate-200 font-medium">الإعدادات</span>
                          </button>

                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setActiveApp('camera');
                              setLastActionLog('تم فتح الكاميرا.');
                            }}
                            className="flex flex-col items-center gap-1 group"
                          >
                            <div className="w-12 h-12 rounded-2xl bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300 group-hover:scale-105 transition">
                              <Camera className="w-6 h-6" />
                            </div>
                            <span className="text-[10px] text-slate-200 font-medium">الكاميرا</span>
                          </button>

                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setActiveApp('gallery');
                              setLastActionLog('تم فتح معرض الصور.');
                            }}
                            className="flex flex-col items-center gap-1 group"
                          >
                            <div className="w-12 h-12 rounded-2xl bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300 group-hover:scale-105 transition">
                              <Layers className="w-6 h-6" />
                            </div>
                            <span className="text-[10px] text-slate-200 font-medium">الاستوديو</span>
                          </button>
                        </div>
                      </div>
                    )}

                    {activeApp === 'remoteApp' && (
                      <div className="p-3 bg-slate-900/90 rounded-2xl border border-indigo-500/30 space-y-3">
                        <div className="flex items-center justify-between pb-2 border-b border-slate-800">
                          <span className="font-bold text-xs text-white">Smart Remote Control</span>
                          <span className="text-[10px] text-emerald-400 font-mono">Service Running</span>
                        </div>
                        <div className="space-y-1.5 text-[11px] text-slate-300">
                          <div className="flex justify-between">
                            <span>نظام التشغيل:</span>
                            <strong className="text-white">API {currentDevice.apiLevel}</strong>
                          </div>
                          <div className="flex justify-between">
                            <span>التحكم باللمس:</span>
                            <span className={isGestureSupported ? 'text-emerald-400' : 'text-amber-400'}>
                              {isGestureSupported ? 'مدعوم (API 24+)' : 'محجوب (قديم)'}
                            </span>
                          </div>
                          <div className="flex justify-between">
                            <span>حالة البلوتوث:</span>
                            <span className={isBtConnected ? 'text-emerald-400' : 'text-slate-400'}>
                              {isBtConnected ? 'متصل ومقترن' : 'غير متصل'}
                            </span>
                          </div>
                        </div>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setActiveApp('home');
                          }}
                          className="w-full py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs"
                        >
                          إغلاق الشاشة
                        </button>
                      </div>
                    )}

                    {activeApp === 'settings' && (
                      <div className="p-3 bg-slate-900 rounded-2xl border border-slate-800 space-y-2 text-xs">
                        <h3 className="font-bold text-white mb-2 pb-1 border-b border-slate-800">إعدادات الجهاز</h3>
                        <div className="p-2 bg-slate-800/80 rounded-lg flex justify-between items-center">
                          <span>إمكانية الوصول (Accessibility):</span>
                          <span className={isAccessibilityGranted ? 'text-emerald-400 font-bold' : 'text-rose-400'}>
                            {isAccessibilityGranted ? 'مفعلة ✓' : 'معطلة ✕'}
                          </span>
                        </div>
                        <div className="p-2 bg-slate-800/80 rounded-lg flex justify-between items-center">
                          <span>واجهة النظام OEM:</span>
                          <span className="text-slate-300">{currentDevice.oemSkin}</span>
                        </div>
                        <div className="p-2 bg-slate-800/80 rounded-lg flex justify-between items-center">
                          <span>إصدار Bluetooth:</span>
                          <span className="text-slate-300">{currentDevice.bluetoothVersion}</span>
                        </div>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setActiveApp('home');
                          }}
                          className="w-full py-1.5 mt-2 bg-indigo-600 text-white rounded-lg text-xs font-semibold"
                        >
                          الرجوع للرئيسية
                        </button>
                      </div>
                    )}

                    {activeApp === 'camera' && (
                      <div className="flex flex-col items-center justify-center h-48 bg-slate-900 rounded-2xl border border-slate-800">
                        <Camera className="w-10 h-10 text-indigo-400 mb-2 animate-pulse" />
                        <span className="text-xs text-slate-300 font-medium">معاينة الكاميرا (Live Stream)</span>
                        <span className="text-[10px] text-slate-500 mt-1">التقاط الفيديو عبر MediaCodec H.264</span>
                      </div>
                    )}

                    {activeApp === 'gallery' && (
                      <div className="p-2 grid grid-cols-2 gap-2">
                        <div className="h-20 bg-slate-800 rounded-lg flex items-center justify-center text-[10px] text-slate-400">صورة 1</div>
                        <div className="h-20 bg-slate-800 rounded-lg flex items-center justify-center text-[10px] text-slate-400">صورة 2</div>
                        <div className="h-20 bg-slate-800 rounded-lg flex items-center justify-center text-[10px] text-slate-400">صورة 3</div>
                        <div className="h-20 bg-slate-800 rounded-lg flex items-center justify-center text-[10px] text-slate-400">صورة 4</div>
                      </div>
                    )}

                    {/* Virtual Android Navigation Bar (Lollipop - Android 15 standard) */}
                    <div className="absolute bottom-2 left-0 right-0 h-10 flex items-center justify-around px-8 bg-black/60 backdrop-blur-md border-t border-slate-800/60 z-20">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleGlobalAction('RECENTS');
                        }}
                        className="text-slate-400 hover:text-white p-2 transition"
                        title="التطبيقات الأخيرة (Recent Apps)"
                      >
                        <Square className="w-4 h-4" />
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleGlobalAction('HOME');
                        }}
                        className="text-slate-400 hover:text-white p-2 transition"
                        title="الشاشة الرئيسية (Home)"
                      >
                        <div className="w-4 h-4 rounded-full border-2 border-current" />
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleGlobalAction('BACK');
                        }}
                        className="text-slate-400 hover:text-white p-2 transition"
                        title="الرجوع (Back)"
                      >
                        <ArrowRight className="w-4 h-4" />
                      </button>
                    </div>

                  </div>
                </>
              )}
            </div>

            {/* Hardware Side Buttons Simulation */}
            <div className="absolute -left-2 top-28 flex flex-col gap-2">
              <button
                onClick={() => {
                  setVolumeLevel((v) => Math.min(100, v + 10));
                  setLastActionLog(`[Hardware Key] رفع مستوى الصوت: ${Math.min(100, volumeLevel + 10)}%`);
                }}
                className="w-1.5 h-10 bg-slate-700 hover:bg-indigo-500 rounded-l transition"
                title="زر رفع الصوت العتادي"
              />
              <button
                onClick={() => {
                  setVolumeLevel((v) => Math.max(0, v - 10));
                  setLastActionLog(`[Hardware Key] خفض مستوى الصوت: ${Math.max(0, volumeLevel - 10)}%`);
                }}
                className="w-1.5 h-10 bg-slate-700 hover:bg-indigo-500 rounded-l transition"
                title="زر خفض الصوت العتادي"
              />
            </div>

            <div className="absolute -right-2 top-36">
              <button
                onClick={() => {
                  setIsScreenLocked((prev) => !prev);
                  setLastActionLog(
                    !isScreenLocked ? '[Hardware Key] تم قفل الشاشة.' : '[Hardware Key] تم إيقاظ الشاشة.'
                  );
                }}
                className="w-1.5 h-12 bg-slate-700 hover:bg-rose-500 rounded-r transition"
                title="زر الطاقة والقفل العتادي"
              />
            </div>

          </div>

          {/* Action Log Bar below phone */}
          <div className="w-full max-w-[380px] mt-3 p-2.5 bg-slate-900/90 rounded-xl border border-slate-800 text-[11px] font-mono text-slate-300 flex items-center justify-between gap-2 shadow">
            <span className="text-emerald-400 font-bold shrink-0">سجل الأحداث:</span>
            <span className="truncate">{lastActionLog}</span>
          </div>

        </div>

        {/* Right: Remote Control Studio Console (Col 5) */}
        <div className="lg:col-span-5 space-y-4">
          
          {/* Main Control Panel Card */}
          <div className="bg-slate-900/90 rounded-2xl p-5 border border-slate-800 shadow-xl space-y-5">
            
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <Sliders className="w-5 h-5 text-indigo-400" />
                <h3 className="text-base font-bold text-white">لوحة تحكم Smart Remote</h3>
              </div>
              <span className={`text-xs px-2.5 py-0.5 rounded-full font-bold border ${
                isCastActive 
                  ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30' 
                  : 'bg-slate-800 text-slate-400 border-slate-700'
              }`}>
                {isCastActive ? 'متصل ويبث الشاشة' : 'وضع الاستعداد'}
              </span>
            </div>

            {/* Quick Connect Actions */}
            <div className="grid grid-cols-2 gap-3">
              {/* Bluetooth Connect */}
              <button
                onClick={handleStartBtScan}
                disabled={isBtScanning}
                className={`flex items-center justify-center gap-2 p-3 rounded-xl border font-semibold text-xs transition ${
                  isBtConnected
                    ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300'
                    : 'bg-slate-800 hover:bg-slate-700 border-slate-700 text-white'
                }`}
              >
                {isBtScanning ? (
                  <BluetoothSearching className="w-4 h-4 animate-spin text-indigo-400" />
                ) : (
                  <Bluetooth className="w-4 h-4 text-indigo-400" />
                )}
                <span>
                  {isBtConnected
                    ? 'البلوتوث متصل ✓'
                    : isBtScanning
                    ? 'جاري البحث...'
                    : 'بحث واتصال بالبلوتوث'}
                </span>
              </button>

              {/* Screen Mirroring Toggle */}
              <button
                onClick={handleToggleScreenCast}
                className={`flex items-center justify-center gap-2 p-3 rounded-xl border font-semibold text-xs transition ${
                  isCastActive
                    ? 'bg-rose-900/40 border-rose-500/40 text-rose-300 hover:bg-rose-900/60'
                    : 'bg-indigo-600 hover:bg-indigo-500 border-indigo-500 text-white shadow-lg shadow-indigo-600/20'
                }`}
              >
                <ScreenCastIcon className="w-4 h-4" />
                <span>{isCastActive ? 'إيقاف بث الشاشة' : 'بدء بث الشاشة (Cast)'}</span>
              </button>
            </div>

            {/* Stream Settings & Adaptive Codec */}
            <div className="space-y-2 pt-2">
              <label className="text-xs font-bold text-slate-300 flex items-center justify-between">
                <span>جودة البث والترميز المتكيف:</span>
                <span className="text-[11px] text-slate-400 font-mono">
                  {currentDevice.hasHwH264 ? 'مسرّع عتادي (HW AVC)' : 'ترميز برمجي (SW)'}
                </span>
              </label>

              <select
                value={selectedQuality}
                onChange={(e) => setSelectedQuality(e.target.value)}
                className="w-full bg-slate-800 border border-slate-700 text-xs rounded-xl p-2.5 text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                {Object.entries(STREAM_QUALITY_PROFILES).map(([key, prof]) => (
                  <option key={key} value={key}>
                    {prof.name} - ({prof.resolution})
                  </option>
                ))}
              </select>
            </div>

            {/* Real-time Video Stream Diagnostics */}
            {isCastActive && (
              <div className="p-3 bg-slate-950/80 rounded-xl border border-slate-800 space-y-2 text-xs font-mono">
                <div className="flex items-center justify-between text-slate-400 border-b border-slate-800 pb-1.5">
                  <span className="flex items-center gap-1 text-slate-300">
                    <Activity className="w-3.5 h-3.5 text-indigo-400" />
                    <span>مؤشرات البث المباشر (Telemetry)</span>
                  </span>
                  <span className="text-emerald-400">ممتازة (Zero Jitter)</span>
                </div>
                <div className="grid grid-cols-3 gap-2 text-center pt-1">
                  <div className="bg-slate-900/80 p-2 rounded-lg border border-slate-800">
                    <span className="text-[10px] text-slate-400 block">معدل الإطارات</span>
                    <strong className="text-white text-sm">{metrics.fps} FPS</strong>
                  </div>
                  <div className="bg-slate-900/80 p-2 rounded-lg border border-slate-800">
                    <span className="text-[10px] text-slate-400 block">عرض النطاق</span>
                    <strong className="text-indigo-400 text-sm">{metrics.bitrateMbps} Mbps</strong>
                  </div>
                  <div className="bg-slate-900/80 p-2 rounded-lg border border-slate-800">
                    <span className="text-[10px] text-slate-400 block">زمن الاستجابة</span>
                    <strong className="text-emerald-400 text-sm">{metrics.latencyMs} ms</strong>
                  </div>
                </div>
              </div>
            )}

            {/* Mode Switcher: Touch vs Trackpad vs Presentation */}
            <div className="space-y-2">
              <span className="text-xs font-bold text-slate-300 block">طريقة التحكم والمدخلات:</span>
              <div className="grid grid-cols-3 gap-1.5 p-1 bg-slate-950 rounded-xl border border-slate-800 text-xs">
                <button
                  onClick={() => setControlMode('touch')}
                  className={`py-1.5 rounded-lg flex items-center justify-center gap-1 font-semibold transition ${
                    controlMode === 'touch'
                      ? 'bg-indigo-600 text-white shadow'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Hand className="w-3.5 h-3.5" />
                  <span>اللمس المباشر</span>
                </button>
                <button
                  onClick={() => setControlMode('trackpad')}
                  className={`py-1.5 rounded-lg flex items-center justify-center gap-1 font-semibold transition ${
                    controlMode === 'trackpad'
                      ? 'bg-indigo-600 text-white shadow'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <MousePointer className="w-3.5 h-3.5" />
                  <span>لوحة الفأرة</span>
                </button>
                <button
                  onClick={() => setControlMode('presentation')}
                  className={`py-1.5 rounded-lg flex items-center justify-center gap-1 font-semibold transition ${
                    controlMode === 'presentation'
                      ? 'bg-indigo-600 text-white shadow'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Sliders className="w-3.5 h-3.5" />
                  <span>جهاز عرض</span>
                </button>
              </div>
            </div>

            {/* Virtual Trackpad if selected or on older API */}
            {controlMode === 'trackpad' && (
              <div
                onMouseMove={(e) => {
                  setLastActionLog(`[Trackpad Virtual Cursor] تحريك المؤشر: X=${e.movementX}, Y=${e.movementY}`);
                }}
                className="h-28 bg-slate-950 rounded-xl border border-dashed border-slate-700 flex flex-col items-center justify-center text-slate-500 hover:text-slate-300 hover:border-indigo-500 cursor-move transition select-none"
              >
                <MousePointer className="w-6 h-6 mb-1 text-indigo-400" />
                <span className="text-xs">مرر إصبعك هنا للتحكم بمؤشر الفأرة اللاسلكي</span>
                <span className="text-[10px] text-slate-500 mt-0.5">يعمل حتى على الأجهزة القديمة (API 21+) كبديل للمس المباشر</span>
              </div>
            )}

            {/* Android Global System Navigation Keypad */}
            <div className="space-y-2 pt-2 border-t border-slate-800">
              <span className="text-xs font-bold text-slate-300 block">أزرار النظام العالمية (Global System Actions):</span>
              <div className="grid grid-cols-4 gap-2">
                <button
                  onClick={() => handleGlobalAction('BACK')}
                  className="p-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold border border-slate-700 flex flex-col items-center gap-1 transition"
                >
                  <ArrowRight className="w-4 h-4 text-indigo-400" />
                  <span>الرجوع</span>
                </button>
                <button
                  onClick={() => handleGlobalAction('HOME')}
                  className="p-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold border border-slate-700 flex flex-col items-center gap-1 transition"
                >
                  <Home className="w-4 h-4 text-emerald-400" />
                  <span>الرئيسية</span>
                </button>
                <button
                  onClick={() => handleGlobalAction('RECENTS')}
                  className="p-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold border border-slate-700 flex flex-col items-center gap-1 transition"
                >
                  <Layers className="w-4 h-4 text-amber-400" />
                  <span>الأخيرة</span>
                </button>
                <button
                  onClick={() => {
                    setLastActionLog('[Hardware] تم التقاط لقطة شاشة وحفظها.');
                  }}
                  className="p-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold border border-slate-700 flex flex-col items-center gap-1 transition"
                >
                  <Camera className="w-4 h-4 text-cyan-400" />
                  <span>لقطة شاشة</span>
                </button>
              </div>
            </div>

            {/* Quick Permission Check Status */}
            <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800 flex items-center justify-between text-xs">
              <div className="flex items-center gap-2">
                <Smartphone className="w-4 h-4 text-slate-400" />
                <span className="text-slate-300">
                  الصلاحيات المفعلة: <strong className="text-white">
                    {Object.values(permissionsState).filter((s) => s === 'GRANTED').length} / {Object.keys(permissionsState).length}
                  </strong>
                </span>
              </div>
              <button
                onClick={onNavigateToPermissionsTab}
                className="text-indigo-400 hover:text-indigo-300 font-bold transition"
              >
                إدارة الصلاحيات ←
              </button>
            </div>

          </div>

        </div>

      </div>

      {/* Warning Alert Modal */}
      {warningAlert && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm">
          <div className="bg-slate-900 border border-amber-500/40 rounded-2xl max-w-md w-full p-6 shadow-2xl">
            <div className="flex items-start gap-3 mb-3">
              <div className="p-2.5 bg-amber-500/20 text-amber-400 rounded-xl shrink-0">
                <AlertTriangle className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">{warningAlert.title}</h3>
                <p className="text-xs text-slate-300 mt-2 leading-relaxed">
                  {warningAlert.message}
                </p>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-4 border-t border-slate-800 mt-4">
              <button
                onClick={() => setWarningAlert(null)}
                className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl text-xs font-semibold"
              >
                فهمت ذلك
              </button>
              <button
                onClick={() => {
                  setWarningAlert(null);
                  onNavigateToPermissionsTab();
                }}
                className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-bold"
              >
                فتح مدير الصلاحيات
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
