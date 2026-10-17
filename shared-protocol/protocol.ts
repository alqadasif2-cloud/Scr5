/**
 * Shared Protocol Types for Smart Remote Control
 * Version 1.1.0
 */

export type PlatformType = 'windows' | 'android' | 'web_client';
export type SessionRole = 'view_only' | 'full_control' | 'audio_only';

export interface DeviceHandshakeHello {
  type: 'HANDSHAKE_HELLO';
  deviceId: string;
  deviceName: string;
  platform: PlatformType;
  appVersion: string;
  supportedRoles: ('host' | 'client' | 'bidirectional')[];
  capabilities: {
    maxResolution: string;
    maxFps: number;
    codecs: string[];
    supportsMouseInjection: boolean;
    supportsKeyboardInjection: boolean;
    supportsTouchInjection: boolean;
  };
}

export interface SessionRequestPacket {
  type: 'SESSION_REQUEST';
  sessionId: string;
  initiatorId: string;
  targetId: string;
  requestedRole: SessionRole;
  sessionPin: string;
  requestedResolution: string;
  targetFps: number;
}

export interface SessionResponsePacket {
  type: 'SESSION_RESPONSE';
  sessionId: string;
  accepted: boolean;
  rejectionReason?: string;
  assignedStreamPort?: number;
  encryptionPublicKey?: string;
}

export type InputEventType =
  | 'MOUSE_MOVE'
  | 'MOUSE_DOWN'
  | 'MOUSE_UP'
  | 'MOUSE_WHEEL'
  | 'KEY_DOWN'
  | 'KEY_UP'
  | 'TOUCH_TAP'
  | 'TOUCH_SWIPE'
  | 'ANDROID_GLOBAL_ACTION';

export interface InputEventPacket {
  type: 'INPUT_EVENT';
  sessionId: string;
  sequence: number;
  timestamp: number;
  subType: InputEventType;
  normalizedX?: number; // 0.0 to 1.0
  normalizedY?: number; // 0.0 to 1.0
  button?: 'left' | 'right' | 'middle';
  wheelDelta?: number;
  keyCode?: number;
  keyText?: string;
  modifiers?: ('ctrl' | 'alt' | 'shift' | 'win')[];
  globalAction?: 'BACK' | 'HOME' | 'RECENTS' | 'NOTIFICATIONS';
}

export interface ScreenFrameChunkPacket {
  type: 'SCREEN_FRAME_CHUNK';
  sessionId: string;
  frameIndex: number;
  format: 'H264_NALU' | 'JPEG' | 'WEBP';
  width: number;
  height: number;
  payloadBase64: string;
}

export interface QualityAdjustmentPacket {
  type: 'QUALITY_ADJUSTMENT';
  targetBitrateKbps: number;
  targetFps: number;
  scaleFactor: number;
}

export interface HeartbeatPacket {
  type: 'HEARTBEAT';
  clientTimestamp: number;
  serverTimestamp?: number;
}

export interface DisconnectPacket {
  type: 'DISCONNECT';
  sessionId: string;
  reason: 'user_requested' | 'timeout' | 'security_revoke' | 'error';
}

export type SmartRemotePacket =
  | DeviceHandshakeHello
  | SessionRequestPacket
  | SessionResponsePacket
  | InputEventPacket
  | ScreenFrameChunkPacket
  | QualityAdjustmentPacket
  | HeartbeatPacket
  | DisconnectPacket;
