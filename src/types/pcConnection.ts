export type ConnectionDirection = 'ANDROID_TO_PC' | 'PC_TO_ANDROID' | 'ANDROID_TO_ANDROID';

export type SessionConnectionState =
  | 'DISCONNECTED'
  | 'DISCOVERING'
  | 'CONNECTING'
  | 'AUTHENTICATING_PIN'
  | 'SESSION_ACTIVE'
  | 'ERROR';

export interface LocalDeviceEndpoint {
  id: string;
  name: string;
  platform: 'windows' | 'android' | 'web_client';
  ip: string;
  role: 'host' | 'client' | 'bidirectional';
  isOnline: boolean;
  pinRequired?: boolean;
}

export interface RealtimeTelemetry {
  rttMs: number;
  fps: number;
  bitrateKbps: number;
  packetsSent: number;
  packetsReceived: number;
  lastPacketTime: string;
}

export interface PlatformBuildStatus {
  platform: 'Android' | 'Windows' | 'WebDashboard';
  titleAr: string;
  environmentAvailable: boolean;
  sourceCodeReady: boolean;
  buildToolRequired: string;
  targetArtifact: string;
  compilationInstructionsAr: string;
  statusNotesAr: string;
}
