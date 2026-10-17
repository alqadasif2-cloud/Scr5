export type AndroidApiLevel = 21 | 22 | 23 | 24 | 25 | 26 | 27 | 28 | 29 | 30 | 31 | 32 | 33 | 34 | 35;

export interface AndroidVersionInfo {
  apiLevel: AndroidApiLevel;
  versionName: string;
  codeName: string;
  releaseYear: number;
  marketShare: string;
  minSupportedFeature: string;
  primaryChanges: string[];
}

export type DeviceTier = 'flagship' | 'midrange' | 'budget';

export interface DeviceProfile {
  id: string;
  name: string;
  brand: string;
  apiLevel: AndroidApiLevel;
  tier: DeviceTier;
  ramGb: number;
  screenResolution: string;
  aspectRatio: string;
  cpuCores: number;
  hasHwH264: boolean;
  hasHwH265: boolean;
  bluetoothVersion: string;
  oemSkin: string; // e.g. "One UI 6.1", "MIUI 14", "Pixel Stock", "EMUI 12"
  backgroundRestrictions: 'strict' | 'moderate' | 'standard';
}

export type PermissionKey =
  | 'BLUETOOTH'
  | 'BLUETOOTH_SCAN'
  | 'BLUETOOTH_CONNECT'
  | 'ACCESS_FINE_LOCATION'
  | 'POST_NOTIFICATIONS'
  | 'FOREGROUND_SERVICE_MEDIA_PROJECTION'
  | 'MEDIA_PROJECTION_CONSENT'
  | 'ACCESSIBILITY_SERVICE'
  | 'IGNORE_BATTERY_OPTIMIZATIONS';

export type PermissionState = 'NOT_REQUESTED' | 'GRANTED' | 'DENIED_ONCE' | 'PERMANENTLY_DENIED';

export interface PermissionDefinition {
  key: PermissionKey;
  labelAr: string;
  shortDescAr: string;
  detailedRationaleAr: string;
  minApi: number;
  maxApi?: number;
  requiredFor: string[];
  isSystemSpecialSetting?: boolean; // e.g. Accessibility or Battery Saver which opens Settings
  recoveryStepsAr: string[];
}

export type CompatibilityLevel = 'core' | 'functional';

export interface FeatureCompatibility {
  id: string;
  featureNameAr: string;
  featureNameEn: string;
  category: 'connection' | 'screen' | 'input' | 'performance' | 'security';
  minApiLevel: AndroidApiLevel;
  compatibilityLevel: CompatibilityLevel;
  requiredPermissions: PermissionKey[];
  implementationMethod: string;
  knownLimitationsAr: string;
  verifiedDevices: string[];
  fallbackStrategyAr: string;
  isAvailableOnCurrentDevice: boolean;
}

export interface StreamQualityProfile {
  name: string;
  resolution: string;
  width: number;
  height: number;
  targetFps: number;
  bitrateKbps: number;
  codec: 'H.264 (HW)' | 'H.264 (SW)' | 'VP8 (SW)';
  minRamRecommendedGb: number;
}

export interface TestCaseResult {
  id: string;
  titleAr: string;
  descriptionAr: string;
  targetApi: AndroidApiLevel;
  status: 'pending' | 'running' | 'passed' | 'warning' | 'failed';
  log: string;
  executionMs: number;
}
