/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { DeviceProfile, PermissionKey, PermissionState } from './types/android';
import { PRESET_DEVICES } from './data/compatibilityData';
import { Navbar } from './components/Navbar';
import { DeviceSimulator } from './components/DeviceSimulator';
import { PermissionManagerModal } from './components/PermissionManagerModal';
import { CompatibilityMatrixView } from './components/CompatibilityMatrixView';
import { TestingSuiteView } from './components/TestingSuiteView';
import { CodeViewer } from './components/CodeViewer';
import { DocumentationView } from './components/DocumentationView';
import { PcConnectionView } from './components/PcConnectionView';

const INITIAL_PERMISSIONS: Record<PermissionKey, PermissionState> = {
  BLUETOOTH: 'GRANTED',
  BLUETOOTH_SCAN: 'GRANTED',
  BLUETOOTH_CONNECT: 'GRANTED',
  ACCESS_FINE_LOCATION: 'NOT_REQUESTED',
  POST_NOTIFICATIONS: 'GRANTED',
  FOREGROUND_SERVICE_MEDIA_PROJECTION: 'GRANTED',
  MEDIA_PROJECTION_CONSENT: 'GRANTED',
  ACCESSIBILITY_SERVICE: 'GRANTED',
  IGNORE_BATTERY_OPTIMIZATIONS: 'NOT_REQUESTED',
};

export default function App() {
  const [currentDevice, setCurrentDevice] = useState<DeviceProfile>(PRESET_DEVICES[0]); // Pixel 8 Pro (API 35)
  const [permissionsState, setPermissionsState] =
    useState<Record<PermissionKey, PermissionState>>(INITIAL_PERMISSIONS);
  const [activeTab, setActiveTab] = useState<
    'simulator' | 'pcRemote' | 'permissions' | 'matrix' | 'tests' | 'code' | 'docs'
  >('pcRemote');

  const handleUpdatePermission = (key: PermissionKey, state: PermissionState) => {
    setPermissionsState((prev) => ({
      ...prev,
      [key]: state,
    }));
  };

  const handleResetAllPermissions = () => {
    const resetState: Record<PermissionKey, PermissionState> = {} as any;
    Object.keys(INITIAL_PERMISSIONS).forEach((k) => {
      resetState[k as PermissionKey] = 'NOT_REQUESTED';
    });
    setPermissionsState(resetState);
  };

  const handleRequestSinglePermission = (key: PermissionKey) => {
    setActiveTab('permissions');
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-['Cairo',sans-serif] selection:bg-indigo-500 selection:text-white">
      {/* Top Navbar */}
      <Navbar
        currentDevice={currentDevice}
        onSelectDevice={setCurrentDevice}
        activeTab={activeTab}
        onSelectTab={setActiveTab}
      />

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {activeTab === 'simulator' && (
          <DeviceSimulator
            currentDevice={currentDevice}
            permissionsState={permissionsState}
            onRequestPermission={handleRequestSinglePermission}
            onNavigateToPermissionsTab={() => setActiveTab('permissions')}
          />
        )}

        {activeTab === 'pcRemote' && (
          <PcConnectionView />
        )}

        {activeTab === 'permissions' && (
          <PermissionManagerModal
            currentDevice={currentDevice}
            permissionsState={permissionsState}
            onUpdatePermission={handleUpdatePermission}
            onResetAllPermissions={handleResetAllPermissions}
          />
        )}

        {activeTab === 'matrix' && (
          <CompatibilityMatrixView currentDevice={currentDevice} />
        )}

        {activeTab === 'tests' && <TestingSuiteView />}

        {activeTab === 'code' && <CodeViewer />}

        {activeTab === 'docs' && <DocumentationView />}
      </main>

      {/* Footer */}
      <footer className="bg-slate-900 border-t border-slate-800 text-xs text-slate-400 py-6">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <span className="font-bold text-white">Smart Remote Control</span>
            <span>-</span>
            <span>نظام التوافقية الواسعة وإدارة الصلاحيات (Android 5.0 إلى Android 15)</span>
          </div>

          <div className="flex items-center gap-4 text-[11px]">
            <span className="text-emerald-400 font-mono">minSdk: 21 | targetSdk: 35 | compileSdk: 35</span>
            <span className="text-slate-600">|</span>
            <span>جاهز للإنتاج والتوزيع على Google Play</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
