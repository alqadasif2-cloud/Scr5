import React, { useState } from 'react';
import { DeviceProfile, FeatureCompatibility } from '../types/android';
import { FEATURES_COMPATIBILITY_LIST, ANDROID_VERSIONS } from '../data/compatibilityData';
import {
  Grid,
  Search,
  Filter,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Info,
  ChevronDown,
  ChevronUp,
  Layers,
  Smartphone,
  ExternalLink,
} from 'lucide-react';

interface CompatibilityMatrixViewProps {
  currentDevice: DeviceProfile;
}

export const CompatibilityMatrixView: React.FC<CompatibilityMatrixViewProps> = ({
  currentDevice,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [filterLevel, setFilterLevel] = useState<'all' | 'core' | 'functional'>('all');
  const [filterCategory, setFilterCategory] = useState<string>('all');
  const [expandedFeatureId, setExpandedFeatureId] = useState<string | null>(null);

  const filteredFeatures = FEATURES_COMPATIBILITY_LIST.filter((feature) => {
    // Search filter
    const matchesSearch =
      feature.featureNameAr.toLowerCase().includes(searchTerm.toLowerCase()) ||
      feature.featureNameEn.toLowerCase().includes(searchTerm.toLowerCase()) ||
      feature.implementationMethod.toLowerCase().includes(searchTerm.toLowerCase());

    // Level filter
    const matchesLevel = filterLevel === 'all' || feature.compatibilityLevel === filterLevel;

    // Category filter
    const matchesCategory = filterCategory === 'all' || feature.category === filterCategory;

    return matchesSearch && matchesLevel && matchesCategory;
  });

  return (
    <div className="space-y-6">
      {/* Header Overview Card */}
      <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-slate-900 rounded-2xl p-6 border border-slate-700/80 shadow-xl">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="p-2 rounded-lg bg-indigo-500/20 text-indigo-400">
                <Grid className="w-6 h-6" />
              </span>
              <h2 className="text-xl sm:text-2xl font-bold text-white">
                مصفوفة التوافقية الشاملة بين إصدارات Android والميزات
              </h2>
            </div>
            <p className="text-sm text-slate-300 max-w-3xl leading-relaxed">
              وفقاً للبند 11، يتم الفصل الصارم بين مستويين من التوافق: 
              <span className="text-emerald-400 font-bold mx-1">«التوافق الأساسي»</span> 
              (إمكانية التثبيت وتشغيل الواجهة وإدارة الأجهزة من API 21)، 
              و <span className="text-indigo-400 font-bold mx-1">«التوافق الوظيفي»</span> 
              (مشاركة الشاشة، ونقل الفيديو، والتحكم باللمس وفق قيود النظام المتاحة).
            </p>
          </div>

          {/* Current Device Pill */}
          <div className="bg-slate-950/80 p-3 rounded-xl border border-slate-700 shrink-0 text-xs">
            <span className="text-slate-400 block mb-1">الجهاز النشط للمقارنة:</span>
            <div className="flex items-center gap-2">
              <Smartphone className="w-4 h-4 text-indigo-400" />
              <strong className="text-white">{currentDevice.name}</strong>
              <span className="px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 font-bold">
                API {currentDevice.apiLevel}
              </span>
            </div>
          </div>
        </div>

        {/* Filters and Search Bar */}
        <div className="mt-6 pt-4 border-t border-slate-800 grid grid-cols-1 sm:grid-cols-12 gap-3">
          {/* Search Box */}
          <div className="sm:col-span-5 relative">
            <Search className="w-4 h-4 absolute right-3 top-3 text-slate-400" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="ابحث عن ميزة، تقنية، أو واجهة برمجة..."
              className="w-full bg-slate-950/80 border border-slate-700 text-xs sm:text-sm rounded-xl pr-9 pl-4 py-2.5 text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          {/* Compatibility Level Tabs */}
          <div className="sm:col-span-4 flex rounded-xl bg-slate-950/80 p-1 border border-slate-700 text-xs">
            <button
              onClick={() => setFilterLevel('all')}
              className={`flex-1 py-1.5 rounded-lg font-medium transition ${
                filterLevel === 'all'
                  ? 'bg-indigo-600 text-white shadow'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              الكل ({FEATURES_COMPATIBILITY_LIST.length})
            </button>
            <button
              onClick={() => setFilterLevel('core')}
              className={`flex-1 py-1.5 rounded-lg font-medium transition ${
                filterLevel === 'core'
                  ? 'bg-emerald-600 text-white shadow'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              أساسي (Core)
            </button>
            <button
              onClick={() => setFilterLevel('functional')}
              className={`flex-1 py-1.5 rounded-lg font-medium transition ${
                filterLevel === 'functional'
                  ? 'bg-indigo-600 text-white shadow'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              وظيفي (Functional)
            </button>
          </div>

          {/* Category Dropdown */}
          <div className="sm:col-span-3">
            <select
              value={filterCategory}
              onChange={(e) => setFilterCategory(e.target.value)}
              className="w-full bg-slate-950/80 border border-slate-700 text-xs sm:text-sm rounded-xl px-3 py-2.5 text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="all">كافة الفئات</option>
              <option value="connection">الاتصال والبلوتوث</option>
              <option value="screen">بث ومشاركة الشاشة</option>
              <option value="input">التحكم واللمس</option>
              <option value="performance">الأداء والترميز</option>
              <option value="security">الأمان والتشفير</option>
            </select>
          </div>
        </div>
      </div>

      {/* Features List */}
      <div className="space-y-3">
        {filteredFeatures.map((feature) => {
          const isSupportedOnActiveDevice = currentDevice.apiLevel >= feature.minApiLevel;
          const isExpanded = expandedFeatureId === feature.id;

          return (
            <div
              key={feature.id}
              className={`rounded-2xl border transition-all ${
                isSupportedOnActiveDevice
                  ? 'bg-slate-900/80 border-slate-800 hover:border-slate-700'
                  : 'bg-slate-900/40 border-slate-800/60 opacity-80'
              }`}
            >
              {/* Feature Summary Bar */}
              <div
                onClick={() => setExpandedFeatureId(isExpanded ? null : feature.id)}
                className="p-4 sm:p-5 flex items-center justify-between gap-4 cursor-pointer select-none"
              >
                <div className="flex items-center gap-3">
                  <span
                    className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${
                      isSupportedOnActiveDevice
                        ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                        : 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
                    }`}
                  >
                    {isSupportedOnActiveDevice ? (
                      <CheckCircle2 className="w-5 h-5" />
                    ) : (
                      <AlertTriangle className="w-5 h-5" />
                    )}
                  </span>

                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="font-bold text-white text-sm sm:text-base">
                        {feature.featureNameAr}
                      </h3>
                      <span
                        className={`text-[10px] px-2 py-0.5 rounded-full font-bold uppercase tracking-wider ${
                          feature.compatibilityLevel === 'core'
                            ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40'
                            : 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/40'
                        }`}
                      >
                        {feature.compatibilityLevel === 'core' ? 'توافق أساسي' : 'توافق وظيفي'}
                      </span>
                    </div>
                    <span className="text-xs text-slate-400 font-mono block mt-0.5">
                      {feature.featureNameEn}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  {/* Min API Badge */}
                  <span className="hidden sm:inline-block text-xs bg-slate-800 text-slate-300 px-3 py-1 rounded-lg border border-slate-700 font-mono">
                    الحد الأدنى: API {feature.minApiLevel} ({ANDROID_VERSIONS[feature.minApiLevel].versionName})
                  </span>

                  {/* Active Device Support Status */}
                  <span
                    className={`text-xs px-2.5 py-1 rounded-full font-semibold border ${
                      isSupportedOnActiveDevice
                        ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
                        : 'bg-amber-500/15 text-amber-400 border-amber-500/30'
                    }`}
                  >
                    {isSupportedOnActiveDevice ? 'مدعوم في هاتفك ✓' : 'يتطلب إصداراً أحدث'}
                  </span>

                  {isExpanded ? (
                    <ChevronUp className="w-4 h-4 text-slate-400" />
                  ) : (
                    <ChevronDown className="w-4 h-4 text-slate-400" />
                  )}
                </div>
              </div>

              {/* Expanded Detailed Breakdown */}
              {isExpanded && (
                <div className="px-5 pb-5 pt-2 border-t border-slate-800/80 grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                  {/* Column 1: Technical Implementation & Permissions */}
                  <div className="space-y-3 bg-slate-950/60 p-4 rounded-xl border border-slate-800">
                    <div>
                      <span className="font-bold text-indigo-400 block mb-1">
                        طريقة التنفيذ البرمجية (Implementation Method):
                      </span>
                      <p className="font-mono text-slate-300 bg-slate-900 p-2 rounded-lg border border-slate-800">
                        {feature.implementationMethod}
                      </p>
                    </div>

                    <div>
                      <span className="font-bold text-slate-300 block mb-1">
                        الصلاحيات المطلوبة:
                      </span>
                      <div className="flex flex-wrap gap-1">
                        {feature.requiredPermissions.length === 0 ? (
                          <span className="text-emerald-400">لا تتطلب صلاحيات خاصة (Zero Permission)</span>
                        ) : (
                          feature.requiredPermissions.map((perm, idx) => (
                            <span
                              key={idx}
                              className="font-mono bg-slate-900 text-indigo-300 px-2 py-0.5 rounded border border-slate-700 text-[11px]"
                            >
                              {perm}
                            </span>
                          ))
                        )}
                      </div>
                    </div>

                    <div>
                      <span className="font-bold text-slate-300 block mb-1">
                        الأجهزة والإصدارات المختبرة فعلياً:
                      </span>
                      <ul className="text-slate-400 list-disc list-inside space-y-0.5">
                        {feature.verifiedDevices.map((dev, idx) => (
                          <li key={idx}>{dev}</li>
                        ))}
                      </ul>
                    </div>
                  </div>

                  {/* Column 2: Limitations & Graceful Fallback */}
                  <div className="space-y-3 bg-slate-950/60 p-4 rounded-xl border border-slate-800">
                    <div>
                      <span className="font-bold text-amber-400 flex items-center gap-1 mb-1">
                        <AlertTriangle className="w-3.5 h-3.5" />
                        <span>القيود المعروفة وسلوك النظام:</span>
                      </span>
                      <p className="text-slate-300 leading-relaxed">
                        {feature.knownLimitationsAr}
                      </p>
                    </div>

                    <div className="pt-2 border-t border-slate-800">
                      <span className="font-bold text-emerald-400 flex items-center gap-1 mb-1">
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>البديل المتاح عند عدم الدعم (Graceful Fallback):</span>
                      </span>
                      <p className="text-slate-300 leading-relaxed">
                        {feature.fallbackStrategyAr}
                      </p>
                    </div>
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};
