import express from 'express';
import { createServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const server = createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

app.use(express.json());

interface ConnectedDevice {
  id: string;
  name: string;
  platform: 'windows' | 'android' | 'web_client';
  ip: string;
  pin: string;
  role: 'host' | 'client' | 'bidirectional';
  ws: WebSocket;
  lastPing: number;
}

const activeDevices = new Map<string, ConnectedDevice>();
const activeSessions = new Map<string, { hostId: string; clientId: string }>();

// WebSocket Signaling & Real-time Local Transport
wss.on('connection', (ws: WebSocket, req) => {
  const clientIp = req.socket.remoteAddress || '127.0.0.1';
  let registeredDeviceId: string | null = null;

  ws.on('message', (message: string) => {
    try {
      const data = JSON.parse(message.toString());

      switch (data.type) {
        case 'REGISTER_DEVICE': {
          registeredDeviceId = data.deviceId || `dev_${Date.now()}`;
          const device: ConnectedDevice = {
            id: registeredDeviceId!,
            name: data.deviceName || 'Smart Remote Endpoint',
            platform: data.platform || 'web_client',
            ip: clientIp,
            pin: data.pin || Math.floor(100000 + Math.random() * 900000).toString(),
            role: data.role || 'bidirectional',
            ws,
            lastPing: Date.now(),
          };
          activeDevices.set(registeredDeviceId!, device);

          ws.send(JSON.stringify({
            type: 'REGISTER_CONFIRMED',
            deviceId: device.id,
            pin: device.pin,
            activeDeviceCount: activeDevices.size,
          }));
          broadcastDeviceList();
          break;
        }

        case 'LIST_DEVICES': {
          ws.send(JSON.stringify({
            type: 'DEVICE_LIST',
            devices: getSanitizedDeviceList(),
          }));
          break;
        }

        case 'SESSION_REQUEST': {
          const target = activeDevices.get(data.targetId);
          if (target && target.ws.readyState === WebSocket.OPEN) {
            // Forward request to target host
            target.ws.send(JSON.stringify(data));
          } else {
            ws.send(JSON.stringify({
              type: 'SESSION_RESPONSE',
              sessionId: data.sessionId,
              accepted: false,
              rejectionReason: 'الجهاز المستهدف غير متصل حالياً على الشبكة المحلية.',
            }));
          }
          break;
        }

        case 'SESSION_RESPONSE': {
          const initiator = activeDevices.get(data.initiatorId);
          if (data.accepted) {
            activeSessions.set(data.sessionId, {
              hostId: registeredDeviceId || '',
              clientId: data.initiatorId,
            });
          }
          if (initiator && initiator.ws.readyState === WebSocket.OPEN) {
            initiator.ws.send(JSON.stringify(data));
          }
          break;
        }

        case 'INPUT_EVENT':
        case 'SCREEN_FRAME_CHUNK':
        case 'QUALITY_ADJUSTMENT':
        case 'DISCONNECT': {
          // Route session packets between Host and Client
          const session = activeSessions.get(data.sessionId);
          if (session) {
            const peerId = session.hostId === registeredDeviceId ? session.clientId : session.hostId;
            const peer = activeDevices.get(peerId);
            if (peer && peer.ws.readyState === WebSocket.OPEN) {
              peer.ws.send(JSON.stringify(data));
            }
          }
          break;
        }

        case 'HEARTBEAT': {
          ws.send(JSON.stringify({
            type: 'HEARTBEAT_ACK',
            clientTimestamp: data.clientTimestamp,
            serverTimestamp: Date.now(),
          }));
          break;
        }
      }
    } catch (e) {
      // Ignore malformed packets
    }
  });

  ws.on('close', () => {
    if (registeredDeviceId) {
      activeDevices.delete(registeredDeviceId);
      broadcastDeviceList();
    }
  });
});

function getSanitizedDeviceList() {
  return Array.from(activeDevices.values()).map(d => ({
    id: d.id,
    name: d.name,
    platform: d.platform,
    ip: d.ip,
    role: d.role,
  }));
}

function broadcastDeviceList() {
  const payload = JSON.stringify({
    type: 'DEVICE_LIST',
    devices: getSanitizedDeviceList(),
  });
  activeDevices.forEach(d => {
    if (d.ws.readyState === WebSocket.OPEN) {
      d.ws.send(payload);
    }
  });
}

// REST Endpoints
app.get('/api/status', (req, res) => {
  res.json({
    status: 'ONLINE',
    protocolVersion: '1.1.0',
    localPort: 52840,
    serverPort: 3000,
    connectedDevicesCount: activeDevices.size,
    activeSessionsCount: activeSessions.size,
    platformCapabilities: {
      windowsAppSupported: true,
      androidAppSupported: true,
      localWebSocketRelay: true,
    },
  });
});

app.get('/api/devices', (req, res) => {
  res.json({
    devices: getSanitizedDeviceList(),
  });
});

// Vite or Static file serving
async function setupViteOrStatic() {
  const isProduction = process.env.NODE_ENV === 'production';
  if (!isProduction) {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    app.use(express.static(path.resolve(__dirname, 'dist')));
    app.get('*', (req, res) => {
      res.sendFile(path.resolve(__dirname, 'dist', 'index.html'));
    });
  }

  const PORT = Number(process.env.PORT) || 3000;
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`[SmartRemote Engine] يعمل الخادم على المنفذ: http://0.0.0.0:${PORT}`);
  });
}

setupViteOrStatic();
