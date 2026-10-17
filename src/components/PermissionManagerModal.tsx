import React, { useState } from 'react';
import { DeviceProfile, PermissionKey, PermissionState, PermissionDefinition } from '../types/android';
import { PERMISSION_DEFINITIONS } from '../data/compatibilityData';
import {
  Shield,
  ShieldCheck,
  ShieldAlert,
  ShieldX,
  ExternalLink,
  Info,
  CheckCircle2,
  XCircle,
  HelpCircle,
  RotateCcw,
  Sparkles,
  Smartphone,
  Lock,
} from 'lucide-react';

interface PermissionManagerProps {
  currentDevice: DeviceProfile;
  permissionsState: Record<PermissionKey, PermissionState>;
  onUpdatePermission: (key: PermissionKey, state: PermissionState) => void;
  onResetAllPermissions: () => void;
}

export const PermissionManagerModal: React.FC<PermissionManagerProps> = ({
  currentDevice,
  permissionsState,
  onUpdatePermission,
  onResetAllPermissions,
}) => {
  // Modal states for rationale and recovery
  const [activeRationaleKey, setActiveRationaleKey] = useState<PermissionKey | null>(null);
  const [activeRecoveryKey, setActiveRecoveryKey] = useState<PermissionKey | null>(null);
  const [showSettingsSimModal, setShowSettingsSimModal] = useState(false);

  // Filter permissions applicable to current device's API level
  const applicablePermissions = Object.values(PERMISSION_DEFINITIONS).filter((p) => {
    if (p.minApi > currentDevice.apiLevel) return false;
    if (p.maxApi && currentDevice.apiLevel > p.maxApi) return false;
    return true;
  });

  const handleRequestClick = (def: PermissionDefinition) => {
    const currentState = permissionsState[def.key];
    if (currentState === 'PERMANENTLY_DENIED') {
      setActiveRecoveryKey(def.key);
      return;
    }
    // Show Arabic Rationale Dialog first
    setActiveRationaleKey(def.key);
  };

  const handleRationaleAccept = (key: PermissionKey) => {
    setActiveRationaleKey(null);
    onUpdatePermission(key, 'GRANTED');
  };

  const handleRationaleDeny = (key: PermissionKey) => {
    setActiveRationaleKey(null);
    const currentState = permissionsState[key];
    if (currentState === 'DENIED_ONCE') {
      // Second denial marks it as permanently denied on Android
      onUpdatePermission(key, 'PERMANENTLY_DENIED');
      setActiveRecoveryKey(key);
    } else {
      onUpdatePermission(key, 'DENIED_ONCE');
    }
  };

  const activeRationaleDef = activeRationaleKey ? PERMISSION_DEFINITIONS[activeRationaleKey] : null;
  const activeRecoveryDef = activeRecoveryKey ? PERMISSION_DEFINITIONS[activeRecoveryKey] : null;

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 rounded-2xl p-6 border border-indigo-500/30 shadow-xl">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="p-2 rounded-lg bg-indigo-500/20 text-indigo-400">
                <ShieldCheck className="w-6 h-6" />
              </span>
              <h2 className="text-xl sm:text-2xl font-bold text-white">
                النظام المركزي لإدارة الصلاحيات (Permission Manager)
              </h2>
            </div>
            <p className="text-sm text-slate-300 max-w-3xl leading-relaxed">
              يدير هذا النظام الصلاحيات وفق فلسفة هندسية صارمة: <strong className="text-indigo-300">الطلب عند الحاجة فقط (Just-in-Time)</strong>، 
              <strong className="text-emerald-300"> والشرح الشافي باللغة العربية قبل الطلب</strong>، مع 
              <strong className="text-amber-300"> عزل الصلاحيات بحدود إصدارات Android</strong> حتى لا ينهار التطبيق أو يطلب صلاحيات ملغاة أو غير مدعومة.
            </p>
          </div>

          <div className="flex items-center gap-2 self-start md:self-center">
            <button
              onClick={onResetAllPermissions}
              className="flex items-center gap-2 px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs sm:text-sm font-semibold border border-slate-700 transition"
              title="إعادة تعيين كافة الصلاحيات إلى الحالة الافتراضية"
            >
              <RotateCcw className="w-4 h-4 text-slate-400" />
              <span>إعادة الضبط</span>
            </button>
            <button
              onClick={() => setShowSettingsSimModal(true)}
              className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs sm:text-sm font-semibold shadow-md transition"
            >
              <ExternalLink className="w-4 h-4" />
              <span>محاكاة إعدادات النظام</span>
            </button>
          </div>
        </div>

        {/* Device Version Indicator Alert */}
        <div className="mt-4 pt-4 border-t border-slate-800 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-2 text-slate-300">
            <Smartphone className="w-4 h-4 text-indigo-400" />
            <span>الجهاز النشط:</span>
            <strong className="text-white">{currentDevice.name}</strong>
            <span className="text-slate-400">|</span>
            <span>مستوى واجهة النظام:</span>
            <strong className="text-indigo-300">API {currentDevice.apiLevel}</strong>
          </div>
          <div className="text-slate-400">
            {currentDevice.apiLevel >= 31 ? (
              <span className="text-emerald-400 font-medium">
                ✓ أندرويد 12+: تفعيل صلاحيات BLUETOOTH_SCAN/CONNECT دون الحاجة لإذن الموقع.
              </span>
            ) : currentDevice.apiLevel >= 23 ? (
              <span className="text-amber-400 font-medium">
                ⚠️ أندرويد 6-11: يتطلب النظام إذن الموقع ACCESS_FINE_LOCATION لاكتشاف البلوتوث.
              </span>
            ) : (
              <span className="text-cyan-400 font-medium">
                ℹ️ أندرويد 5.0: الصلاحيات تمنح تلقائياً عند التثبيت عبر AndroidManifest.
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Permissions Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {applicablePermissions.map((def) => {
          const state = permissionsState[def.key] || 'NOT_REQUESTED';
          const isGranted = state === 'GRANTED';
          const isPermDenied = state === 'PERMANENTLY_DENIED';
          const isOnceDenied = state === 'DENIED_ONCE';

          return (
            <div
              key={def.key}
              className={`rounded-xl p-5 border transition-all ${
                isGranted
                  ? 'bg-slate-900/90 border-emerald-500/40 shadow-sm'
                  : isPermDenied
                  ? 'bg-slate-900/90 border-rose-500/40'
                  : isOnceDenied
                  ? 'bg-slate-900/90 border-amber-500/40'
                  : 'bg-slate-900/60 border-slate-800'
              }`}
            >
              <div className="flex items-start justify-between gap-3 mb-2">
                <div className="flex items-center gap-2.5">
                  <span
                    className={`p-2 rounded-lg ${
                      isGranted
                        ? 'bg-emerald-500/20 text-emerald-400'
                        : isPermDenied
                        ? 'bg-rose-500/20 text-rose-400'
                        : isOnceDenied
                        ? 'bg-amber-500/20 text-amber-400'
                        : 'bg-slate-800 text-slate-400'
                    }`}
                  >
                    {isGranted ? (
                      <ShieldCheck className="w-5 h-5" />
                    ) : isPermDenied ? (
                      <ShieldX className="w-5 h-5" />
                    ) : (
                      <Shield className="w-5 h-5" />
                    )}
                  </span>
                  <div>
                    <h3 className="text-base font-bold text-white flex items-center gap-2">
                      {def.labelAr}
                      {def.isSystemSpecialSetting && (
                        <span className="text-[10px] px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 font-medium">
                          إعداد نظام خاص
                        </span>
                      )}
                    </h3>
                    <span className="text-[11px] font-mono text-slate-400 block">
                      {def.key}
                    </span>
                  </div>
                </div>

                {/* Status Badge */}
                <span
                  className={`text-xs px-2.5 py-1 rounded-full font-semibold border ${
                    isGranted
                      ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
                      : isPermDenied
                      ? 'bg-rose-500/15 text-rose-400 border-rose-500/30'
                      : isOnceDenied
                      ? 'bg-amber-500/15 text-amber-400 border-amber-500/30'
                      : 'bg-slate-800 text-slate-400 border-slate-700'
                  }`}
                >
                  {isGranted
                    ? 'ممنوحة ✓'
                    : isPermDenied
                    ? 'مرفوضة نهائياً ✕'
                    : isOnceDenied
                    ? 'رُفضت مرة واحدة ⚠️'
                    : 'لم تُطلب بعد'}
                </span>
              </div>

              <p className="text-xs text-slate-300 mt-2 mb-3 leading-relaxed">
                {def.shortDescAr}
              </p>

              {/* Required for tags */}
              <div className="flex flex-wrap gap-1.5 mb-4">
                {def.requiredFor.map((rf, idx) => (
                  <span
                    key={idx}
                    className="text-[11px] bg-slate-800/80 text-slate-300 px-2 py-0.5 rounded-md border border-slate-700/60"
                  >
                    • {rf}
                  </span>
                ))}
              </div>

              {/* Action buttons */}
              <div className="flex items-center justify-between pt-3 border-t border-slate-800/80 gap-2">
                <button
                  onClick={() => setActiveRationaleKey(def.key)}
                  className="text-xs text-indigo-400 hover:text-indigo-300 flex items-center gap-1 font-medium"
                >
                  <Info className="w-3.5 h-3.5" />
                  <span>عرض الشرح التفصيلي بالعربية</span>
                </button>

                <div className="flex items-center gap-2">
                  {isGranted ? (
                    <button
                      onClick={() => onUpdatePermission(def.key, 'DENIED_ONCE')}
                      className="text-xs px-3 py-1.5 bg-slate-800 hover:bg-rose-950/60 text-slate-300 hover:text-rose-400 rounded-lg border border-slate-700 hover:border-rose-700 transition"
                    >
                      إلغاء الإذن
                    </button>
                  ) : isPermDenied ? (
                    <button
                      onClick={() => setActiveRecoveryKey(def.key)}
                      className="text-xs px-3 py-1.5 bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 rounded-lg border border-rose-500/40 font-semibold transition flex items-center gap-1"
                    >
                      <HelpCircle className="w-3.5 h-3.5" />
                      <span>دليل الاستعادة</span>
                    </button>
                  ) : (
                    <button
                      onClick={() => handleRequestClick(def)}
                      className="text-xs px-3.5 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg font-bold shadow transition"
                    >
                      طلب الصلاحية الآن
                    </button>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Arabic Rationale Dialog (Modal) */}
      {activeRationaleDef && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl max-w-lg w-full p-6 shadow-2xl relative overflow-hidden">
            {/* Top Accent Bar */}
            <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-indigo-500 via-purple-500 to-emerald-400" />

            <div className="flex items-start gap-3 mb-4">
              <div className="p-3 bg-indigo-500/20 text-indigo-400 rounded-xl shrink-0">
                <Lock className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-semibold text-indigo-400">شرح أمني ومبرر الصلاحية</span>
                <h3 className="text-lg font-bold text-white mt-0.5">
                  طلب إذن: {activeRationaleDef.labelAr}
                </h3>
              </div>
            </div>

            <div className="bg-slate-800/80 rounded-xl p-4 border border-slate-700/80 mb-5 text-sm text-slate-200 leading-relaxed">
              {activeRationaleDef.detailedRationaleAr}
            </div>

            <div className="space-y-2 mb-6">
              <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                الوظائف التي تعتمد على هذا الإذن:
              </h4>
              <ul className="text-xs text-slate-300 space-y-1">
                {activeRationaleDef.requiredFor.map((item, idx) => (
                  <li key={idx} className="flex items-center gap-2">
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
              <button
                onClick={() => handleRationaleDeny(activeRationaleDef.key)}
                className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl text-sm font-semibold transition"
              >
                رفض الإذن الآن
              </button>
              <button
                onClick={() => handleRationaleAccept(activeRationaleDef.key)}
                className="px-5 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-bold shadow-lg shadow-indigo-600/30 transition flex items-center gap-1.5"
              >
                <CheckCircle2 className="w-4 h-4" />
                <span>موافق، منح الصلاحية</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Permanently Denied Recovery Guide Modal */}
      {activeRecoveryDef && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm">
          <div className="bg-slate-900 border border-rose-500/40 rounded-2xl max-w-lg w-full p-6 shadow-2xl relative">
            <div className="flex items-start gap-3 mb-4">
              <div className="p-3 bg-rose-500/20 text-rose-400 rounded-xl shrink-0">
                <ShieldAlert className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-semibold text-rose-400">إرشادات استعادة الإذن المرفوض دائمياً</span>
                <h3 className="text-lg font-bold text-white mt-0.5">
                  كيف تستعيد صلاحية «{activeRecoveryDef.labelAr}»؟
                </h3>
              </div>
            </div>

            <p className="text-xs text-slate-300 mb-4 leading-relaxed">
              نظام Android يمنع التطبيق من تكرار إظهار نافذة الطلب عند رفضها مرتين متتاليتين حمايةً لتجربتك.
              لإعادة تشغيل الوظيفة المرتبطة، يمكنك تفعيلها يدوياً من إعدادات النظام بالخطوات التالية:
            </p>

            <div className="bg-slate-800/80 rounded-xl p-4 border border-slate-700/80 mb-5 space-y-2.5">
              {activeRecoveryDef.recoveryStepsAr.map((step, idx) => (
                <div key={idx} className="flex items-start gap-2.5 text-xs text-slate-200">
                  <span className="w-5 h-5 rounded-full bg-indigo-500/20 text-indigo-300 flex items-center justify-center font-bold text-[11px] shrink-0">
                    {idx + 1}
                  </span>
                  <span className="pt-0.5">{step}</span>
                </div>
              ))}
            </div>

            <div className="flex items-center justify-between pt-4 border-t border-slate-800">
              <button
                onClick={() => setActiveRecoveryKey(null)}
                className="text-xs text-slate-400 hover:text-slate-200"
              >
                إغلاق الإرشادات
              </button>

              <button
                onClick={() => {
                  setActiveRecoveryKey(null);
                  onUpdatePermission(activeRecoveryDef.key, 'GRANTED');
                }}
                className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-xs sm:text-sm font-bold shadow transition flex items-center gap-1.5"
              >
                <CheckCircle2 className="w-4 h-4" />
                <span>محاكاة: قمت بتفعيلها من الإعدادات</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Simulated System Settings Modal */}
      {showSettingsSimModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl max-w-md w-full p-6 shadow-2xl">
            <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <Smartphone className="w-5 h-5 text-indigo-400" />
                <span className="font-bold text-white text-sm">
                  إعدادات التطبيقات في النظام (App Info Settings)
                </span>
              </div>
              <button
                onClick={() => setShowSettingsSimModal(false)}
                className="text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>

            <div className="text-center py-3">
              <div className="w-14 h-14 rounded-2xl bg-indigo-600 flex items-center justify-center mx-auto mb-2 text-white shadow-lg">
                <Smartphone className="w-8 h-8" />
              </div>
              <h4 className="font-bold text-white text-base">Smart Remote Control</h4>
              <p className="text-xs text-slate-400 font-mono">com.smartremote.control (v1.0.1)</p>
            </div>

            <div className="divide-y divide-slate-800 my-4 text-xs">
              {applicablePermissions.map((def) => {
                const isGranted = permissionsState[def.key] === 'GRANTED';
                return (
                  <div key={def.key} className="py-2.5 flex items-center justify-between">
                    <div>
                      <span className="font-medium text-slate-200 block">{def.labelAr}</span>
                      <span className="text-[10px] text-slate-500 font-mono">{def.key}</span>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        checked={isGranted}
                        onChange={(e) => {
                          onUpdatePermission(def.key, e.target.checked ? 'GRANTED' : 'DENIED_ONCE');
                        }}
                        className="sr-only peer"
                      />
                      <div className="w-9 h-5 bg-slate-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-emerald-600"></div>
                    </label>
                  </div>
                );
              })}
            </div>

            <button
              onClick={() => setShowSettingsSimModal(false)}
              className="w-full py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 font-semibold text-xs rounded-xl transition"
            >
              الرجوع للتطبيق
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
