import React from 'react';
import { DeviceProfile, AndroidApiLevel } from '../types/android';
import { ANDROID_VERSIONS, PRESET_DEVICES } from '../data/compatibilityData';
import { Smartphone, ShieldCheck, Grid, Terminal, Code2, BookOpen, Cpu, BatteryMedium, Monitor } from 'lucide-react';

interface NavbarProps {
  currentDevice: DeviceProfile;
  onSelectDevice: (device: DeviceProfile) => void;
  activeTab: 'simulator' | 'pcRemote' | 'permissions' | 'matrix' | 'tests' | 'code' | 'docs';
  onSelectTab: (tab: 'simulator' | 'pcRemote' | 'permissions' | 'matrix' | 'tests' | 'code' | 'docs') => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  currentDevice,
  onSelectDevice,
  activeTab,
  onSelectTab,
}) => {
  const versionInfo = ANDROID_VERSIONS[currentDevice.apiLevel];

  return (
    <header className="sticky top-0 z-40 bg-slate-900/95 backdrop-blur border-b border-slate-800 shadow-lg">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16 gap-4">
          
          {/* Logo & Title */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-emerald-400 flex items-center justify-center shadow-md shadow-indigo-500/20 ring-1 ring-white/20">
              <Smartphone className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold text-lg text-white tracking-tight">Smart Remote Control</span>
                <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 font-medium">
                  v1.0.1 كود الإنتاج
                </span>
              </div>
              <p className="text-xs text-slate-400 hidden sm:block">
                إدارة التوافقية الشاملة لإصدارات Android (API 21 - 35) ونظام الصلاحيات الذكي
              </p>
            </div>
          </div>

          {/* Active Device Switcher */}
          <div className="flex items-center gap-3">
            <div className="flex items-center bg-slate-800/90 rounded-lg p-1.5 border border-slate-700/80 shadow-inner">
              <span className="text-xs text-slate-400 px-2 font-medium hidden md:inline">الهاتف التجريبي:</span>
              <select
                value={currentDevice.id}
                onChange={(e) => {
                  const dev = PRESET_DEVICES.find((d) => d.id === e.target.value);
                  if (dev) onSelectDevice(dev);
                }}
                className="bg-slate-900 text-slate-100 text-xs sm:text-sm font-semibold rounded-md px-3 py-1.5 border border-slate-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 cursor-pointer"
              >
                {PRESET_DEVICES.map((dev) => (
                  <option key={dev.id} value={dev.id}>
                    {dev.name} ({ANDROID_VERSIONS[dev.apiLevel].versionName} - API {dev.apiLevel})
                  </option>
                ))}
              </select>

              <div className="hidden lg:flex items-center gap-2 mr-3 pr-3 border-r border-slate-700 text-xs text-slate-300">
                <span className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                  currentDevice.apiLevel >= 31 
                    ? 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/30' 
                    : currentDevice.apiLevel >= 24 
                    ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30' 
                    : 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                }`}>
                  API {currentDevice.apiLevel} ({versionInfo.codeName})
                </span>
                <span className="flex items-center gap-1 text-slate-400">
                  <Cpu className="w-3.5 h-3.5 text-slate-500" />
                  {currentDevice.ramGb}GB RAM
                </span>
              </div>
            </div>
          </div>

        </div>

        {/* Tab Navigation */}
        <nav className="flex space-x-1 space-x-reverse overflow-x-auto pb-2 scrollbar-none text-sm font-medium border-t border-slate-800/80 pt-2">
          <button
            onClick={() => onSelectTab('simulator')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
              activeTab === 'simulator'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
            }`}
          >
            <Smartphone className="w-4 h-4" />
            <span>محاكي التحكم وبث الشاشة</span>
          </button>

          <button
            onClick={() => onSelectTab('pcRemote')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg whitespace-nowrap transition-colors border ${
              activeTab === 'pcRemote'
                ? 'bg-indigo-600 text-white shadow-sm border-indigo-500'
                : 'text-slate-300 hover:text-white bg-slate-800/80 hover:bg-slate-800 border-indigo-500/40'
            }`}
          >
            <Monitor className="w-4 h-4 text-emerald-400" />
            <span className="font-bold">الاتصال بالكمبيوتر (PC Remote)</span>
            <span className="text-[10px] bg-emerald-500/20 text-emerald-300 px-1.5 py-0.2 rounded font-mono">
              جديد
            </span>
          </button>

          <button
            onClick={() => onSelectTab('permissions')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
              activeTab === 'permissions'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
            }`}
          >
            <ShieldCheck className="w-4 h-4" />
            <span>مدير الصلاحيات العربي المركزي</span>
          </button>

          <button
            onClick={() => onSelectTab('matrix')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
              activeTab === 'matrix'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
            }`}
          >
            <Grid className="w-4 h-4" />
            <span>مصفوفة التوافقية (Core vs Functional)</span>
          </button>

          <button
            onClick={() => onSelectTab('tests')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
              activeTab === 'tests'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
            }`}
          >
            <Terminal className="w-4 h-4" />
            <span>جناح فحص السيناريوهات (Test Suite)</span>
          </button>

          <button
            onClick={() => onSelectTab('code')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
              activeTab === 'code'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
            }`}
          >
            <Code2 className="w-4 h-4" />
            <span>كود الإنتاج (Kotlin & Gradle)</span>
          </button>

          <button
            onClick={() => onSelectTab('docs')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
              activeTab === 'docs'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
            }`}
          >
            <BookOpen className="w-4 h-4" />
            <span>وثيقة التوافق (README_AR)</span>
          </button>
        </nav>
      </div>
    </header>
  );
};
