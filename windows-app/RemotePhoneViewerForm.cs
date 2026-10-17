using System;
using System.Drawing;
using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using SmartRemoteControl.Windows.Models;

namespace SmartRemoteControl.Windows
{
    /// <summary>
    /// نافذة عرض شاشة هاتف Android والتحكم الكامل فيه من كمبيوتر Windows
    /// الوظيفة الثانية: التحكم من كمبيوتر Windows إلى هاتف Android
    /// </summary>
    public class RemotePhoneViewerForm : Form
    {
        private readonly string _phoneIp;
        private readonly int _phonePort;
        private readonly string _phonePin;

        private ClientWebSocket? _clientWs;
        private CancellationTokenSource? _cts;
        private PictureBox _screenPictureBox = null!;
        private Label _statusLabel = null!;
        private Button _disconnectButton = null!;
        private Button _backButton = null!;
        private Button _homeButton = null!;
        private Button _recentsButton = null!;

        private string _sessionId = string.Empty;
        private bool _isAuthenticated = false;
        private Image? _currentFrame = null;

        public RemotePhoneViewerForm(string phoneIp, int phonePort, string phonePin)
        {
            _phoneIp = phoneIp;
            _phonePort = phonePort;
            _phonePin = phonePin;

            InitializeFormUi();
            ConnectToAndroidPhone();
        }

        private void InitializeFormUi()
        {
            Text = $"Smart Remote Control - التحكم في هاتف Android ({_phoneIp})";
            Width = 520;
            Height = 850;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(15, 23, 42); // slate-900
            ForeColor = Color.White;

            var topPanel = new Panel
            {
                Dock = DockStyle.Top,
                Height = 45,
                BackColor = Color.FromArgb(30, 41, 59),
                Padding = new Padding(10, 5, 10, 5)
            };

            _statusLabel = new Label
            {
                Text = $"جاري الاتصال بهاتف Android على {_phoneIp}:{_phonePort}...",
                Font = new Font("Segoe UI", 9, FontStyle.Regular),
                ForeColor = Color.Yellow,
                Dock = DockStyle.Fill,
                TextAlign = ContentAlignment.MiddleLeft
            };

            _disconnectButton = new Button
            {
                Text = "إنهاء الجلسة",
                BackColor = Color.FromArgb(220, 38, 38),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Dock = DockStyle.Right,
                Width = 100
            };
            _disconnectButton.Click += (s, e) => Close();

            topPanel.Controls.Add(_statusLabel);
            topPanel.Controls.Add(_disconnectButton);

            // شريط أزرار نظام أندرويد
            var bottomNavPanel = new Panel
            {
                Dock = DockStyle.Bottom,
                Height = 55,
                BackColor = Color.FromArgb(30, 41, 59),
                Padding = new Padding(10)
            };

            var navFlow = new FlowLayoutPanel
            {
                Dock = DockStyle.Fill,
                FlowDirection = FlowDirection.LeftToRight,
                WrapContents = false
            };

            _backButton = new Button
            {
                Text = "◀ الرجوع (Back)",
                BackColor = Color.FromArgb(51, 65, 85),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Width = 150,
                Height = 35
            };
            _backButton.Click += (s, e) => SendGlobalAction("BACK");

            _homeButton = new Button
            {
                Text = "● الرئيسية (Home)",
                BackColor = Color.FromArgb(51, 65, 85),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Width = 150,
                Height = 35
            };
            _homeButton.Click += (s, e) => SendGlobalAction("HOME");

            _recentsButton = new Button
            {
                Text = "■ المهام (Recents)",
                BackColor = Color.FromArgb(51, 65, 85),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Width = 150,
                Height = 35
            };
            _recentsButton.Click += (s, e) => SendGlobalAction("RECENTS");

            navFlow.Controls.Add(_backButton);
            navFlow.Controls.Add(_homeButton);
            navFlow.Controls.Add(_recentsButton);
            bottomNavPanel.Controls.Add(navFlow);

            // عرض شاشة الهاتف
            _screenPictureBox = new PictureBox
            {
                Dock = DockStyle.Fill,
                SizeMode = PictureBoxSizeMode.Zoom,
                BackColor = Color.Black
            };

            // أحداث الماوس للتحكم باللمس في الهاتف
            _screenPictureBox.MouseDown += OnScreenMouseDown;
            _screenPictureBox.MouseUp += OnScreenMouseUp;
            _screenPictureBox.MouseWheel += OnScreenMouseWheel;

            Controls.Add(_screenPictureBox);
            Controls.Add(bottomNavPanel);
            Controls.Add(topPanel);

            FormClosing += (s, e) => CleanupResources();
        }

        private async void ConnectToAndroidPhone()
        {
            _cts = new CancellationTokenSource();
            _clientWs = new ClientWebSocket();

            try
            {
                var uri = new Uri($"ws://{_phoneIp}:{_phonePort}/");
                await _clientWs.ConnectAsync(uri, _cts.Token);

                UpdateStatus("تم فتح الاتصال. جاري التحقق من رمز PIN والمصادقة...", Color.LightBlue);

                // 1. إرسال التحية
                var hello = new HandshakeHelloPacket
                {
                    DeviceId = Environment.MachineName,
                    DeviceName = $"{Environment.MachineName} (Windows Remote Client)"
                };
                await SendPacketAsync(hello);

                // 2. إرسال طلب الجلسة مع الـ PIN
                _sessionId = $"win_to_android_{DateTime.UtcNow.Ticks}";
                var req = new SessionRequestPacket
                {
                    SessionId = _sessionId,
                    InitiatorId = Environment.MachineName,
                    TargetId = "android_host",
                    RequestedRole = "full_control",
                    SessionPin = _phonePin,
                    TargetFps = 30
                };
                await SendPacketAsync(req);

                // 3. الاستماع للإطارات والرسائل
                _ = Task.Run(() => ReceiveLoop(_cts.Token));
            }
            catch (Exception ex)
            {
                UpdateStatus($"تعذر الاتصال بهاتف Android: {ex.Message}", Color.Red);
            }
        }

        private async Task ReceiveLoop(CancellationToken ct)
        {
            byte[] buffer = new byte[65536];

            try
            {
                while (_clientWs != null && _clientWs.State == WebSocketState.Open && !ct.IsCancellationRequested)
                {
                    using var ms = new MemoryStream();
                    WebSocketReceiveResult result;

                    do
                    {
                        result = await _clientWs.ReceiveAsync(new ArraySegment<byte>(buffer), ct);
                        if (result.MessageType == WebSocketMessageType.Close)
                        {
                            await _clientWs.CloseAsync(WebSocketCloseStatus.NormalClosure, "Closing", ct);
                            UpdateStatus("أغلق هاتف Android الجلسة.", Color.Orange);
                            return;
                        }
                        ms.Write(buffer, 0, result.Count);
                    } while (!result.EndOfMessage);

                    string json = Encoding.UTF8.GetString(ms.ToArray());
                    using var doc = JsonDocument.Parse(json);
                    string type = doc.RootElement.GetProperty("type").GetString() ?? "";

                    switch (type)
                    {
                        case "SESSION_RESPONSE":
                            bool accepted = doc.RootElement.GetProperty("accepted").GetBoolean();
                            if (accepted)
                            {
                                _isAuthenticated = true;
                                UpdateStatus("تمت المصادقة بنجاح! يتم الآن بث شاشة الهاتف واستقبال اللمس.", Color.LightGreen);
                            }
                            else
                            {
                                string reason = doc.RootElement.TryGetProperty("rejectionReason", out var r) ? r.GetString() ?? "" : "رمز PIN غير مطابق";
                                UpdateStatus($"رفض الهاتف الجلسة: {reason}", Color.Red);
                            }
                            break;

                        case "SCREEN_FRAME_CHUNK":
                            if (doc.RootElement.TryGetProperty("payloadBase64", out var payloadElem))
                            {
                                string b64 = payloadElem.GetString() ?? "";
                                if (!string.IsNullOrEmpty(b64))
                                {
                                    byte[] imgBytes = Convert.FromBase64String(b64);
                                    using var imgStream = new MemoryStream(imgBytes);
                                    var newBmp = new Bitmap(imgStream);

                                    _screenPictureBox.Invoke(new Action(() =>
                                    {
                                        _currentFrame?.Dispose();
                                        _currentFrame = newBmp;
                                        _screenPictureBox.Image = _currentFrame;
                                    }));
                                }
                            }
                            break;

                        case "DISCONNECT":
                            UpdateStatus("تم إنهاء الجلسة من قبل هاتف Android.", Color.Orange);
                            break;
                    }
                }
            }
            catch (Exception ex) when (!ct.IsCancellationRequested)
            {
                UpdateStatus($"انقطع الاتصال بالهاتف: {ex.Message}", Color.Red);
            }
        }

        private void OnScreenMouseDown(object? sender, MouseEventArgs e)
        {
            if (!_isAuthenticated) return;
            var (normX, normY) = CalculateNormalizedCoordinates(e.X, e.Y);
            if (normX.HasValue && normY.HasValue)
            {
                var input = new InputEventPacket
                {
                    SessionId = _sessionId,
                    SubType = "TOUCH_TAP",
                    NormalizedX = normX.Value,
                    NormalizedY = normY.Value,
                    Button = e.Button == MouseButtons.Right ? "right" : "left",
                    Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                };
                _ = SendPacketAsync(input);
            }
        }

        private void OnScreenMouseUp(object? sender, MouseEventArgs e)
        {
            // إرسال إنهاء النقر إن لزم
        }

        private void OnScreenMouseWheel(object? sender, MouseEventArgs e)
        {
            if (!_isAuthenticated) return;
            var input = new InputEventPacket
            {
                SessionId = _sessionId,
                SubType = "MOUSE_WHEEL",
                WheelDelta = e.Delta,
                Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            };
            _ = SendPacketAsync(input);
        }

        private void SendGlobalAction(string action)
        {
            if (!_isAuthenticated) return;
            var input = new InputEventPacket
            {
                SessionId = _sessionId,
                SubType = "ANDROID_GLOBAL_ACTION",
                GlobalAction = action,
                Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            };
            _ = SendPacketAsync(input);
        }

        private (double? x, double? y) CalculateNormalizedCoordinates(int mouseX, int mouseY)
        {
            if (_screenPictureBox.Image == null) return (null, null);

            // حساب الإحداثيات النسبية مع الأخذ بالاعتبار نسبة العرض لـ PictureBoxSizeMode.Zoom
            int imageWidth = _screenPictureBox.Image.Width;
            int imageHeight = _screenPictureBox.Image.Height;

            float imageAspect = (float)imageWidth / imageHeight;
            float boxAspect = (float)_screenPictureBox.Width / _screenPictureBox.Height;

            float scale;
            float offsetX = 0;
            float offsetY = 0;

            if (boxAspect > imageAspect)
            {
                scale = (float)_screenPictureBox.Height / imageHeight;
                offsetX = (_screenPictureBox.Width - imageWidth * scale) / 2;
            }
            else
            {
                scale = (float)_screenPictureBox.Width / imageWidth;
                offsetY = (_screenPictureBox.Height - imageHeight * scale) / 2;
            }

            float imgX = (mouseX - offsetX) / scale;
            float imgY = (mouseY - offsetY) / scale;

            if (imgX < 0 || imgX > imageWidth || imgY < 0 || imgY > imageHeight)
            {
                return (null, null);
            }

            double normX = Math.Clamp(imgX / imageWidth, 0.0, 1.0);
            double normY = Math.Clamp(imgY / imageHeight, 0.0, 1.0);

            return (normX, normY);
        }

        private async Task SendPacketAsync<T>(T packet)
        {
            if (_clientWs == null || _clientWs.State != WebSocketState.Open) return;
            try
            {
                string json = JsonSerializer.Serialize(packet);
                byte[] bytes = Encoding.UTF8.GetBytes(json);
                await _clientWs.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, CancellationToken.None);
            }
            catch { }
        }

        private void UpdateStatus(string message, Color color)
        {
            if (InvokeRequired)
            {
                BeginInvoke(new Action(() => UpdateStatus(message, color)));
                return;
            }
            _statusLabel.Text = message;
            _statusLabel.ForeColor = color;
        }

        private void CleanupResources()
        {
            _cts?.Cancel();
            try
            {
                if (_clientWs != null && _clientWs.State == WebSocketState.Open)
                {
                    _clientWs.CloseAsync(WebSocketCloseStatus.NormalClosure, "Form Closed", CancellationToken.None).Wait(500);
                }
            }
            catch { }

            _clientWs?.Dispose();
            _clientWs = null;
            _currentFrame?.Dispose();
            _currentFrame = null;
        }
    }
}
