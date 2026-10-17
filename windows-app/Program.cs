using System;
using System.Collections.Generic;
using System.Drawing;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Windows.Forms;
using SmartRemoteControl.Windows.Services;

namespace SmartRemoteControl.Windows
{
    /// <summary>
    /// نقطة انطلاق برنامج Windows Smart Remote Control
    /// يدعم السيناريوهات: هاتف -> كمبيوتر، و كمبيوتر -> هاتف
    /// </summary>
    public static class Program
    {
        [STAThread]
        public static void Main(string[] args)
        {
            Application.SetHighDpiMode(HighDpiMode.SystemAware);
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            Console.WriteLine("=================================================");
            Console.WriteLine(" Smart Remote Control for Windows (v1.1.0)       ");
            Console.WriteLine(" برمجية التحكم عن بعد والاتصال المحلي مع Android ");
            Console.WriteLine("=================================================");

            var inputService = new InputInjectionService();
            var captureService = new ScreenCaptureService();
            var networkService = new NetworkTransportService(inputService, captureService);

            // بدء الخادم المحلي على المنفذ 52840
            networkService.StartServer(52840);

            // تشغيل واجهة النوافذ الرسومية للتحكم
            Application.Run(new MainForm(networkService));
        }
    }

    /// <summary>
    /// نافذة التحكم الرئيسية لسطح المكتب
    /// </summary>
    public class MainForm : Form
    {
        private readonly NetworkTransportService _networkService;
        private readonly Label _statusLabel;
        private readonly Label _pinLabel;
        private readonly TextBox _logTextBox;
        private readonly Button _disconnectButton;
        private readonly Button _newPinButton;

        // عناصر التحكم بهاتف Android من الكمبيوتر (PC -> Android)
        private readonly TextBox _phoneIpTextBox;
        private readonly TextBox _phonePortTextBox;
        private readonly TextBox _phonePinTextBox;
        private readonly Button _connectToPhoneButton;

        public MainForm(NetworkTransportService networkService)
        {
            _networkService = networkService;

            Text = "Smart Remote Control for Windows - v1.1.0";
            Width = 720;
            Height = 620;
            StartPosition = FormStartPosition.CenterScreen;
            RightToLeft = RightToLeft.Yes;
            RightToLeftLayout = true;
            BackColor = Color.FromArgb(15, 23, 42); // slate-950
            ForeColor = Color.FromArgb(248, 250, 252);

            var mainPanel = new Panel { Dock = DockStyle.Fill, Padding = new Padding(15) };

            var headerLabel = new Label
            {
                Text = "منظومة التحكم عن بعد الذكية لـ Windows و Android",
                Font = new Font("Segoe UI", 14, FontStyle.Bold),
                Dock = DockStyle.Top,
                Height = 35,
                ForeColor = Color.White
            };

            // لوحة استضافة الكمبيوتر (الهاتف يتحكم بالكمبيوتر)
            var hostPanel = new GroupBox
            {
                Text = "1. استضافة هذا الكمبيوتر (التحكم من الهاتف في الكمبيوتر)",
                Dock = DockStyle.Top,
                Height = 135,
                Padding = new Padding(10),
                ForeColor = Color.FromArgb(148, 163, 184)
            };

            _pinLabel = new Label
            {
                Text = $"رمز الأمان (PIN) للاقتران: {_networkService.DevicePin}",
                Font = new Font("Segoe UI", 11, FontStyle.Bold),
                ForeColor = Color.FromArgb(56, 189, 248),
                Location = new Point(15, 25),
                AutoSize = true
            };

            var localIps = GetLocalIpAddresses();
            var ipInfoLabel = new Label
            {
                Text = $"عنوان IP لهذا الكمبيوتر: {localIps}  |  المنفذ: 52840",
                Font = new Font("Segoe UI", 9, FontStyle.Bold),
                ForeColor = Color.FromArgb(245, 158, 11), // amber-500
                Location = new Point(15, 58),
                AutoSize = true
            };

            _statusLabel = new Label
            {
                Text = $"الحالة: جاهز بانتظار اتصال هاتف Android عبر ws://{localIps.Split(new[] { " أو ", " " }, StringSplitOptions.RemoveEmptyEntries)[0]}:52840",
                Font = new Font("Segoe UI", 9),
                ForeColor = Color.FromArgb(16, 185, 129),
                Location = new Point(15, 88),
                AutoSize = true
            };

            _newPinButton = new Button
            {
                Text = "توليد رمز جديد",
                Location = new Point(530, 22),
                Size = new Size(130, 30),
                BackColor = Color.FromArgb(30, 41, 59),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat
            };
            _newPinButton.Click += (s, e) =>
            {
                _networkService.DevicePin = NetworkTransportService.GenerateNewPin();
                _pinLabel.Text = $"رمز الأمان (PIN) للاقتران: {_networkService.DevicePin}";
            };

            _disconnectButton = new Button
            {
                Text = "قطع الاتصال فوراً",
                Location = new Point(530, 58),
                Size = new Size(130, 30),
                BackColor = Color.FromArgb(220, 38, 38),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat
            };
            _disconnectButton.Click += (s, e) => _networkService.DisconnectActiveSession();

            hostPanel.Controls.Add(_pinLabel);
            hostPanel.Controls.Add(ipInfoLabel);
            hostPanel.Controls.Add(_statusLabel);
            hostPanel.Controls.Add(_newPinButton);
            hostPanel.Controls.Add(_disconnectButton);

            // لوحة التحكم بهاتف Android من الكمبيوتر (PC -> Android)
            var clientPanel = new GroupBox
            {
                Text = "2. التحكم في هاتف Android (مشاهدة شاشة الهاتف والتحكم بالماوس واللمس)",
                Dock = DockStyle.Top,
                Height = 100,
                Padding = new Padding(10),
                ForeColor = Color.FromArgb(148, 163, 184)
            };

            var ipLabel = new Label
            {
                Text = "عنوان IP الهاتف:",
                Location = new Point(15, 28),
                AutoSize = true,
                ForeColor = Color.White
            };

            _phoneIpTextBox = new TextBox
            {
                Location = new Point(125, 25),
                Width = 140,
                Text = "192.168.1.100",
                BackColor = Color.FromArgb(30, 41, 59),
                ForeColor = Color.White
            };

            var portLabel = new Label
            {
                Text = "المنفذ:",
                Location = new Point(275, 28),
                AutoSize = true,
                ForeColor = Color.White
            };

            _phonePortTextBox = new TextBox
            {
                Location = new Point(320, 25),
                Width = 60,
                Text = "52840",
                BackColor = Color.FromArgb(30, 41, 59),
                ForeColor = Color.White
            };

            var pinLabel2 = new Label
            {
                Text = "رمز PIN:",
                Location = new Point(390, 28),
                AutoSize = true,
                ForeColor = Color.White
            };

            _phonePinTextBox = new TextBox
            {
                Location = new Point(450, 25),
                Width = 70,
                MaxLength = 6,
                Text = "123456",
                BackColor = Color.FromArgb(30, 41, 59),
                ForeColor = Color.White
            };

            _connectToPhoneButton = new Button
            {
                Text = "الاتصال بالهاتف الآن",
                Location = new Point(530, 23),
                Size = new Size(130, 32),
                BackColor = Color.FromArgb(79, 70, 229),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9, FontStyle.Bold)
            };
            _connectToPhoneButton.Click += OnConnectToPhoneClick;

            var descLabel = new Label
            {
                Text = "ملاحظة: تأكد من تفعيل «مشاركة شاشة هذا الجهاز» وخدمة AccessibilityService على هاتف Android أولاً.",
                Location = new Point(15, 62),
                AutoSize = true,
                ForeColor = Color.FromArgb(148, 163, 184),
                Font = new Font("Segoe UI", 8)
            };

            clientPanel.Controls.Add(ipLabel);
            clientPanel.Controls.Add(_phoneIpTextBox);
            clientPanel.Controls.Add(portLabel);
            clientPanel.Controls.Add(_phonePortTextBox);
            clientPanel.Controls.Add(pinLabel2);
            clientPanel.Controls.Add(_phonePinTextBox);
            clientPanel.Controls.Add(_connectToPhoneButton);
            clientPanel.Controls.Add(descLabel);

            var logGroup = new GroupBox
            {
                Text = "سجل أحداث الاتصال وأوامر التحكم",
                Dock = DockStyle.Fill,
                Padding = new Padding(10),
                ForeColor = Color.FromArgb(148, 163, 184)
            };

            _logTextBox = new TextBox
            {
                Multiline = true,
                ReadOnly = true,
                ScrollBars = ScrollBars.Vertical,
                Dock = DockStyle.Fill,
                BackColor = Color.Black,
                ForeColor = Color.LimeGreen,
                Font = new Font("Consolas", 9)
            };
            logGroup.Controls.Add(_logTextBox);

            mainPanel.Controls.Add(logGroup);
            mainPanel.Controls.Add(clientPanel);
            mainPanel.Controls.Add(hostPanel);
            mainPanel.Controls.Add(headerLabel);

            Controls.Add(mainPanel);

            // ربط أحداث الخادم
            _networkService.LogMessage += msg =>
            {
                if (InvokeRequired)
                {
                    BeginInvoke(new Action(() => AppendLog(msg)));
                }
                else
                {
                    AppendLog(msg);
                }
            };

            _networkService.ConnectionStateChanged += (connected, client) =>
            {
                if (InvokeRequired)
                {
                    BeginInvoke(new Action(() => UpdateStatus(connected, client)));
                }
                else
                {
                    UpdateStatus(connected, client);
                }
            };
        }

        private void OnConnectToPhoneClick(object? sender, EventArgs e)
        {
            string ip = _phoneIpTextBox.Text.Trim();
            string portStr = _phonePortTextBox.Text.Trim();
            string pin = _phonePinTextBox.Text.Trim();

            if (string.IsNullOrEmpty(ip))
            {
                MessageBox.Show("يرجى إدخال عنوان IP لهاتف Android المستهدف.", "خطأ", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            int port = int.TryParse(portStr, out int p) ? p : 52840;

            if (pin.Length != 6)
            {
                MessageBox.Show("رمز PIN يجب أن يتكون من 6 أرقام.", "خطأ", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            AppendLog($"[Windows Client] فتح نافذة التحكم والاتصال بهاتف Android على {ip}:{port}...");
            var viewerForm = new RemotePhoneViewerForm(ip, port, pin);
            viewerForm.Show();
        }

        private void AppendLog(string message)
        {
            string time = DateTime.Now.ToString("HH:mm:ss");
            _logTextBox.AppendText($"[{time}] {message}{Environment.NewLine}");
        }

        private void UpdateStatus(bool connected, string client)
        {
            if (connected)
            {
                _statusLabel.Text = $"الحالة: متصل نشط مع {client} (جلسة بث وتحكم جارية)";
                _statusLabel.ForeColor = Color.FromArgb(16, 185, 129);
            }
            else
            {
                string localIp = GetLocalIpAddresses().Split(new[] { " أو ", " " }, StringSplitOptions.RemoveEmptyEntries)[0];
                _statusLabel.Text = $"الحالة: جاهز بانتظار اتصال هاتف Android عبر ws://{localIp}:52840";
                _statusLabel.ForeColor = Color.FromArgb(56, 189, 248);
            }
        }

        public static string GetLocalIpAddresses()
        {
            var ips = new List<string>();
            try
            {
                var host = Dns.GetHostEntry(Dns.GetHostName());
                foreach (var ip in host.AddressList)
                {
                    if (ip.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ip))
                    {
                        ips.Add(ip.ToString());
                    }
                }
            }
            catch { }
            return ips.Count > 0 ? string.Join(" أو ", ips) : "127.0.0.1";
        }
    }
}
