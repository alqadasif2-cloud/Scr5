using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using SmartRemoteControl.Windows.Models;

namespace SmartRemoteControl.Windows.Services
{
    /// <summary>
    /// خدمة النقل الشبكي لبرنامج Windows عبر WebSocket محلي على المنفذ 52840
    /// تدعم الاستماع المزدوج (HttpListener مع بديل TcpListener المباشر للعمل دون صلاحيات المسؤول)
    /// مزودة بحماية أمنية من هجمات التخمين ومنع تنفيذ الأوامر قبل المصادقة
    /// </summary>
    public class NetworkTransportService : IDisposable
    {
        private HttpListener? _httpListener;
        private TcpListener? _tcpListener;
        private CancellationTokenSource? _cts;
        private WebSocket? _activeClientWebSocket;

        private readonly InputInjectionService _inputService;
        private readonly ScreenCaptureService _captureService;

        // متغيرات الأمان والتحقق من الجلسة
        private bool _isSessionAuthenticated = false;
        private int _failedAttempts = 0;
        private DateTime _lockoutUntil = DateTime.MinValue;
        private int _isSendingFrame = 0;

        public event Action<string>? LogMessage;
        public event Action<bool, string>? ConnectionStateChanged;
        public event Action<SessionRequestPacket, Action<bool>>? SessionRequestReceived;

        public string DevicePin { get; set; } = GenerateNewPin();
        public bool IsConnected => _activeClientWebSocket != null && _activeClientWebSocket.State == WebSocketState.Open && _isSessionAuthenticated;

        public NetworkTransportService(InputInjectionService inputService, ScreenCaptureService captureService)
        {
            _inputService = inputService;
            _captureService = captureService;

            _captureService.FrameCaptured += OnFrameCaptured;
        }

        public static string GenerateNewPin()
        {
            var random = new Random();
            return random.Next(100000, 999999).ToString();
        }

        public void StartServer(int port = 52840)
        {
            _cts = new CancellationTokenSource();
            bool startedWithHttp = false;

            // 1. محاولة بدء HttpListener القياسي
            try
            {
                _httpListener = new HttpListener();
                _httpListener.Prefixes.Add($"http://*:{port}/");
                _httpListener.Start();
                startedWithHttp = true;
                LogMessage?.Invoke($"[Windows Server] تم بدء تشغيل خادم الاتصال المحلي (HttpListener) على 0.0.0.0:{port} بنجاح.");
                Task.Run(() => ListenLoopHttp(_cts.Token));
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke($"[Windows Server] تنبيه HttpListener: {ex.Message}. سيتم تشغيل خادم TcpListener المباشر للعمل دون اشتراط صلاحيات المسؤول...");
                try { _httpListener?.Close(); } catch { }
                _httpListener = null;
            }

            // 2. إذا فشل HttpListener (مثلاً Access is denied للمستخدم غير المسؤول)، يتم تشغيل خادم TcpListener المباشر فوراً
            if (!startedWithHttp)
            {
                try
                {
                    _tcpListener = new TcpListener(IPAddress.Any, port);
                    _tcpListener.Start();
                    LogMessage?.Invoke($"[Windows Server] تم تشغيل خادم المقابس المباشر (TcpListener) على كافة واجهات الشبكة 0.0.0.0:{port} بنجاح.");
                    Task.Run(() => ListenLoopTcp(_cts.Token));
                }
                catch (Exception ex)
                {
                    LogMessage?.Invoke($"[Windows Server] تعذر بدء خادم المنفذ {port}: {ex.Message}. تأكد من عدم استخدام المنفذ بواسطة تطبيق آخر.");
                }
            }
        }

        private async Task ListenLoopHttp(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested && _httpListener != null && _httpListener.IsListening)
            {
                try
                {
                    var context = await _httpListener.GetContextAsync();
                    if (context.Request.IsWebSocketRequest)
                    {
                        var wsContext = await context.AcceptWebSocketAsync(null);
                        _activeClientWebSocket = wsContext.WebSocket;
                        _isSessionAuthenticated = false;
                        ConnectionStateChanged?.Invoke(false, context.Request.RemoteEndPoint.ToString());
                        LogMessage?.Invoke($"[Windows Server] اتصال وارد جديد من: {context.Request.RemoteEndPoint}. في انتظار إرسال رمز PIN...");

                        _ = Task.Run(() => HandleClientConnection(_activeClientWebSocket, ct));
                    }
                    else
                    {
                        byte[] responseBytes = Encoding.UTF8.GetBytes("{\"status\":\"SmartRemoteWindowsReady\",\"protocolVersion\":\"1.1.0\",\"pinRequired\":true}");
                        context.Response.ContentType = "application/json";
                        context.Response.OutputStream.Write(responseBytes, 0, responseBytes.Length);
                        context.Response.Close();
                    }
                }
                catch (Exception ex) when (!ct.IsCancellationRequested)
                {
                    LogMessage?.Invoke($"[Windows Server] خطأ في الاستماع: {ex.Message}");
                }
            }
        }

        private async Task ListenLoopTcp(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested && _tcpListener != null)
            {
                try
                {
                    var tcpClient = await _tcpListener.AcceptTcpClientAsync(ct);
                    _ = Task.Run(() => HandleTcpClient(tcpClient, ct), ct);
                }
                catch (Exception ex) when (!ct.IsCancellationRequested)
                {
                    LogMessage?.Invoke($"[Windows Server] خطأ في قبول اتصال TCP: {ex.Message}");
                }
            }
        }

        private async Task HandleTcpClient(TcpClient tcpClient, CancellationToken ct)
        {
            try
            {
                var stream = tcpClient.GetStream();
                
                // قراءة ترويسات HTTP بدقة بايت تلو الآخر حتى نهاية الترويسة \r\n\r\n
                // لمنع ابتلاع أي بايتات من إطارات WebSocket اللاحقة بواسطة مخزن StreamReader
                var headerBytes = new System.Collections.Generic.List<byte>();
                byte[] oneByte = new byte[1];
                string webSocketKey = string.Empty;

                while (await stream.ReadAsync(oneByte, 0, 1, ct) > 0)
                {
                    headerBytes.Add(oneByte[0]);
                    int count = headerBytes.Count;
                    if (count >= 4 &&
                        headerBytes[count - 4] == (byte)'\r' &&
                        headerBytes[count - 3] == (byte)'\n' &&
                        headerBytes[count - 2] == (byte)'\r' &&
                        headerBytes[count - 1] == (byte)'\n')
                    {
                        break;
                    }

                    if (count > 16384) // حماية أمنية من الترويسات الزائدة
                    {
                        break;
                    }
                }

                string headerText = Encoding.UTF8.GetString(headerBytes.ToArray());
                foreach (var rawLine in headerText.Split(new[] { "\r\n", "\n" }, StringSplitOptions.RemoveEmptyEntries))
                {
                    if (rawLine.StartsWith("Sec-WebSocket-Key:", StringComparison.OrdinalIgnoreCase))
                    {
                        webSocketKey = rawLine.Substring("Sec-WebSocket-Key:".Length).Trim();
                        break;
                    }
                }

                if (string.IsNullOrEmpty(webSocketKey))
                {
                    byte[] responseBytes = Encoding.UTF8.GetBytes("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"SmartRemoteWindowsReady\",\"protocolVersion\":\"1.1.0\",\"pinRequired\":true}\r\n");
                    await stream.WriteAsync(responseBytes, ct);
                    await stream.FlushAsync(ct);
                    tcpClient.Close();
                    return;
                }

                string acceptKey;
                using (var sha1 = SHA1.Create())
                {
                    byte[] hash = sha1.ComputeHash(Encoding.UTF8.GetBytes(webSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"));
                    acceptKey = Convert.ToBase64String(hash);
                }

                byte[] handshakeResp = Encoding.UTF8.GetBytes(
                    "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    $"Sec-WebSocket-Accept: {acceptKey}\r\n\r\n");

                await stream.WriteAsync(handshakeResp, ct);
                await stream.FlushAsync(ct);

                var ws = WebSocket.CreateFromStream(stream, isServer: true, subProtocol: null, keepAliveInterval: TimeSpan.FromSeconds(2));
                _activeClientWebSocket = ws;
                _isSessionAuthenticated = false;
                ConnectionStateChanged?.Invoke(false, tcpClient.Client.RemoteEndPoint?.ToString() ?? "Unknown");
                LogMessage?.Invoke($"[Windows Server] اتصال وارد جديد عبر WebSocket من: {tcpClient.Client.RemoteEndPoint}. في انتظار إرسال رمز PIN...");

                await HandleClientConnection(ws, ct);
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke($"[Windows Server] خطأ في مصافحة العميل: {ex.Message}");
            }
        }

        private async Task HandleClientConnection(WebSocket ws, CancellationToken ct)
        {
            byte[] buffer = new byte[8192];
            string currentSessionId = string.Empty;

            try
            {
                while (ws.State == WebSocketState.Open && !ct.IsCancellationRequested)
                {
                    var result = await ws.ReceiveAsync(new ArraySegment<byte>(buffer), ct);
                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "Closing", ct);
                        break;
                    }

                    string json = Encoding.UTF8.GetString(buffer, 0, result.Count);
                    using var doc = JsonDocument.Parse(json);
                    string packetType = doc.RootElement.GetProperty("type").GetString() ?? "";

                    switch (packetType)
                    {
                        case "HANDSHAKE_HELLO":
                            var helloAck = new HandshakeHelloPacket
                            {
                                DeviceId = Environment.MachineName,
                                DeviceName = $"{Environment.MachineName} (Windows PC)"
                            };
                            await SendJsonPacket(ws, helloAck);
                            break;

                        case "SESSION_REQUEST":
                            if (DateTime.UtcNow < _lockoutUntil)
                            {
                                int remainingSec = (int)(_lockoutUntil - DateTime.UtcNow).TotalSeconds;
                                var rejectResp = new SessionResponsePacket
                                {
                                    Accepted = false,
                                    RejectionReason = $"تم تجميد طلبات الجلسة مؤقتاً لتكرار الأخطاء. يرجى الانتظار {remainingSec} ثانية."
                                };
                                await SendJsonPacket(ws, rejectResp);
                                LogMessage?.Invoke($"[الأمان] تم حظر محاولة اتصال أثناء فترة التجميد ({remainingSec} ثانية متبقية).");
                                break;
                            }

                            var req = JsonSerializer.Deserialize<SessionRequestPacket>(json);
                            if (req != null)
                            {
                                currentSessionId = req.SessionId;
                                bool pinMatches = !string.IsNullOrEmpty(DevicePin) && req.SessionPin == DevicePin;

                                if (pinMatches)
                                {
                                    _failedAttempts = 0;
                                    _isSessionAuthenticated = true;

                                    var resp = new SessionResponsePacket
                                    {
                                        SessionId = currentSessionId,
                                        Accepted = true
                                    };
                                    await SendJsonPacket(ws, resp);
                                    ConnectionStateChanged?.Invoke(true, "جلسة معتمدة");
                                    // تدبير أمني: عدم تسجيل الـ PIN في السجلات لحماية الخصوصية
                                    LogMessage?.Invoke("[Windows Server] تم اعتماد الجلسة بنجاح بعد التحقق من صحة رمز المصادقة.");

                                    // بدء بث الشاشة
                                    _captureService.Start(currentSessionId, req.TargetFps);
                                }
                                else
                                {
                                    _failedAttempts++;
                                    _isSessionAuthenticated = false;

                                    if (_failedAttempts >= 3)
                                    {
                                        _lockoutUntil = DateTime.UtcNow.AddSeconds(30);
                                        LogMessage?.Invoke("[الأمان] تم رصد 3 محاولات PIN خاطئة متتالية! تفعيل الحظر المؤقت لمدة 30 ثانية.");
                                    }

                                    var resp = new SessionResponsePacket
                                    {
                                        SessionId = currentSessionId,
                                        Accepted = false,
                                        RejectionReason = "رمز PIN غير صحيح أو تم رفض الطلب."
                                    };
                                    await SendJsonPacket(ws, resp);
                                    // تدبير أمني: عدم تسجيل الـ PIN الخاطئ في السجلات
                                    LogMessage?.Invoke($"[Windows Server] محاولة اتصال غير مصرح بها برمز PIN غير مطابق. المحاولات الخاطئة: {_failedAttempts}/3.");
                                }
                            }
                            break;

                        case "INPUT_EVENT":
                            // تدبير أمني صارم: حظر أي أمر تحكم إذا لم تكن الجلسة معتمدة بالـ PIN مسبقاً!
                            if (!_isSessionAuthenticated)
                            {
                                LogMessage?.Invoke("[الأمان] تم حظر أمر تحكم (INPUT_EVENT) لعدم اكتمال المصادقة برمز PIN!");
                                break;
                            }

                            var inputEvent = JsonSerializer.Deserialize<InputEventPacket>(json);
                            if (inputEvent != null && (string.IsNullOrEmpty(currentSessionId) || inputEvent.SessionId == currentSessionId))
                            {
                                _inputService.ProcessInputEvent(inputEvent);
                            }
                            break;

                        case "DISCONNECT":
                            LogMessage?.Invoke("[Windows Server] طلب الطرف الآخر إنهاء الجلسة.");
                            _captureService.Stop();
                            _isSessionAuthenticated = false;
                            break;
                    }
                }
            }
            catch (Exception ex)
            {
                LogMessage?.Invoke($"[Windows Server] انقطع اتصال العميل: {ex.Message}");
            }
            finally
            {
                _isSessionAuthenticated = false;
                _captureService.Stop();
                ConnectionStateChanged?.Invoke(false, string.Empty);
            }
        }

        private async void OnFrameCaptured(ScreenFramePacket framePacket)
        {
            if (Interlocked.CompareExchange(ref _isSendingFrame, 1, 0) == 0)
            {
                try
                {
                    if (_isSessionAuthenticated && _activeClientWebSocket != null && _activeClientWebSocket.State == WebSocketState.Open)
                    {
                        await SendJsonPacket(_activeClientWebSocket, framePacket);
                    }
                }
                catch { }
                finally
                {
                    Interlocked.Exchange(ref _isSendingFrame, 0);
                }
            }
        }

        private async Task SendJsonPacket<T>(WebSocket ws, T packet)
        {
            if (ws.State != WebSocketState.Open) return;
            string json = JsonSerializer.Serialize(packet);
            byte[] bytes = Encoding.UTF8.GetBytes(json);
            await ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, CancellationToken.None);
        }

        public void DisconnectActiveSession()
        {
            _isSessionAuthenticated = false;
            _captureService.Stop();
            if (_activeClientWebSocket != null && _activeClientWebSocket.State == WebSocketState.Open)
            {
                try
                {
                    _activeClientWebSocket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Emergency Disconnect", CancellationToken.None);
                }
                catch { }
            }
            _activeClientWebSocket = null;
            ConnectionStateChanged?.Invoke(false, string.Empty);
            LogMessage?.Invoke("[Windows Server] تم قطع الاتصال فورياً بطلب من المستخدم.");
        }

        public void Dispose()
        {
            _cts?.Cancel();
            _captureService.Dispose();
            try { _httpListener?.Stop(); _httpListener?.Close(); } catch { }
            try { _tcpListener?.Stop(); } catch { }
        }
    }
}
