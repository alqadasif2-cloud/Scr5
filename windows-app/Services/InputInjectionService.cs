using System;
using System.Runtime.InteropServices;
using SmartRemoteControl.Windows.Models;

namespace SmartRemoteControl.Windows.Services
{
    /// <summary>
    /// خدمة حقن مدخلات الفأرة ولوحة المفاتيح في نظام Windows باستخدام واجهات Win32 الرسمية
    /// </summary>
    public class InputInjectionService
    {
        #region Win32 API Definitions

        private const int INPUT_MOUSE = 0;
        private const int INPUT_KEYBOARD = 1;

        private const uint MOUSEEVENTF_MOVE = 0x0001;
        private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
        private const uint MOUSEEVENTF_LEFTUP = 0x0004;
        private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
        private const uint MOUSEEVENTF_RIGHTUP = 0x0010;
        private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
        private const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
        private const uint MOUSEEVENTF_WHEEL = 0x0800;
        private const uint MOUSEEVENTF_ABSOLUTE = 0x8000;

        private const uint KEYEVENTF_EXTENDEDKEY = 0x0001;
        private const uint KEYEVENTF_KEYUP = 0x0002;
        private const uint KEYEVENTF_UNICODE = 0x0004;

        // Virtual-Key Codes
        private const ushort VK_BACK = 0x08;
        private const ushort VK_TAB = 0x09;
        private const ushort VK_RETURN = 0x0D;
        private const ushort VK_SHIFT = 0x10;
        private const ushort VK_CONTROL = 0x11;
        private const ushort VK_MENU = 0x12; // Alt
        private const ushort VK_ESCAPE = 0x1B;
        private const ushort VK_SPACE = 0x20;
        private const ushort VK_LWIN = 0x5B;

        [StructLayout(LayoutKind.Sequential)]
        private struct INPUT
        {
            public uint type;
            public InputUnion u;
        }

        [StructLayout(LayoutKind.Explicit)]
        private struct InputUnion
        {
            [FieldOffset(0)]
            public MOUSEINPUT mi;
            [FieldOffset(0)]
            public KEYBDINPUT ki;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct MOUSEINPUT
        {
            public int dx;
            public int dy;
            public uint mouseData;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct KEYBDINPUT
        {
            public ushort wVk;
            public ushort wScan;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [DllImport("user32.dll", SetLastError = true)]
        private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

        [DllImport("user32.dll")]
        private static extern int GetSystemMetrics(int nIndex);

        private const int SM_CXSCREEN = 0;
        private const int SM_CYSCREEN = 1;

        #endregion

        public InputInjectionService()
        {
        }

        /// <summary>
        /// معالجة حزمة الإدخال الواردة من هاتف Android أو واجهة الويب
        /// </summary>
        public bool ProcessInputEvent(InputEventPacket packet)
        {
            try
            {
                switch (packet.SubType)
                {
                    case "MOUSE_MOVE":
                        if (packet.NormalizedX.HasValue && packet.NormalizedY.HasValue)
                        {
                            MoveMouse(packet.NormalizedX.Value, packet.NormalizedY.Value);
                        }
                        return true;

                    case "MOUSE_DOWN":
                        if (packet.NormalizedX.HasValue && packet.NormalizedY.HasValue)
                        {
                            MoveMouse(packet.NormalizedX.Value, packet.NormalizedY.Value);
                        }
                        MouseButtonDown(packet.Button ?? "left");
                        return true;

                    case "MOUSE_UP":
                        MouseButtonUp(packet.Button ?? "left");
                        return true;

                    case "MOUSE_WHEEL":
                        if (packet.WheelDelta.HasValue)
                        {
                            MouseWheel(packet.WheelDelta.Value);
                        }
                        return true;

                    case "KEY_DOWN":
                        if (!string.IsNullOrEmpty(packet.KeyText))
                        {
                            HandleNamedOrTextInput(packet.KeyText);
                        }
                        else if (packet.KeyCode.HasValue)
                        {
                            SendVirtualKey((ushort)packet.KeyCode.Value, false);
                        }
                        return true;

                    case "KEY_UP":
                        if (packet.KeyCode.HasValue)
                        {
                            SendVirtualKey((ushort)packet.KeyCode.Value, true);
                        }
                        return true;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[InputInjection] خطأ أثناء حقن الإدخال: {ex.Message}");
            }

            return false;
        }

        private void HandleNamedOrTextInput(string keyText)
        {
            switch (keyText.ToLowerInvariant())
            {
                case "enter":
                case "\n":
                case "\r":
                    SendVirtualKey(VK_RETURN, false);
                    SendVirtualKey(VK_RETURN, true);
                    break;

                case "backspace":
                    SendVirtualKey(VK_BACK, false);
                    SendVirtualKey(VK_BACK, true);
                    break;

                case "tab":
                    SendVirtualKey(VK_TAB, false);
                    SendVirtualKey(VK_TAB, true);
                    break;

                case "escape":
                    SendVirtualKey(VK_ESCAPE, false);
                    SendVirtualKey(VK_ESCAPE, true);
                    break;

                case "winkey":
                case "win":
                    SendVirtualKey(VK_LWIN, false);
                    SendVirtualKey(VK_LWIN, true);
                    break;

                case "alt+tab":
                    SendVirtualKey(VK_MENU, false);
                    SendVirtualKey(VK_TAB, false);
                    SendVirtualKey(VK_TAB, true);
                    SendVirtualKey(VK_MENU, true);
                    break;

                case "ctrl+alt+del":
                    // تنبيه: أمن Windows يمنع تطبيقات User-mode العادية من محاكاة Ctrl+Alt+Del
                    // يتطلب هذا تشغيل كخدمة نظام LocalSystem واستدعاء SendSAS(false)
                    Console.WriteLine("[InputInjection] تنبيه: Ctrl+Alt+Del محجوب بواسطة أمان نواة Windows SAS.");
                    break;

                default:
                    // إرسال الحروف عبر Unicode
                    foreach (char c in keyText)
                    {
                        SendUnicodeChar(c, false);
                        SendUnicodeChar(c, true);
                    }
                    break;
            }
        }

        public void MoveMouse(double normalizedX, double normalizedY)
        {
            // تحويل الإحداثيات النسبية (0.0 إلى 1.0) إلى مقياس SendInput المطلق (0 إلى 65535)
            int absX = (int)Math.Clamp(normalizedX * 65535.0, 0, 65535);
            int absY = (int)Math.Clamp(normalizedY * 65535.0, 0, 65535);

            INPUT[] inputs = new INPUT[1];
            inputs[0].type = INPUT_MOUSE;
            inputs[0].u.mi.dx = absX;
            inputs[0].u.mi.dy = absY;
            inputs[0].u.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE;

            SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
        }

        public void MouseButtonDown(string button)
        {
            uint flag = button.ToLowerInvariant() switch
            {
                "right" => MOUSEEVENTF_RIGHTDOWN,
                "middle" => MOUSEEVENTF_MIDDLEDOWN,
                _ => MOUSEEVENTF_LEFTDOWN
            };

            INPUT[] inputs = new INPUT[1];
            inputs[0].type = INPUT_MOUSE;
            inputs[0].u.mi.dwFlags = flag;
            SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
        }

        public void MouseButtonUp(string button)
        {
            uint flag = button.ToLowerInvariant() switch
            {
                "right" => MOUSEEVENTF_RIGHTUP,
                "middle" => MOUSEEVENTF_MIDDLEUP,
                _ => MOUSEEVENTF_LEFTUP
            };

            INPUT[] inputs = new INPUT[1];
            inputs[0].type = INPUT_MOUSE;
            inputs[0].u.mi.dwFlags = flag;
            SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
        }

        public void MouseWheel(int delta)
        {
            INPUT[] inputs = new INPUT[1];
            inputs[0].type = INPUT_MOUSE;
            inputs[0].u.mi.dwFlags = MOUSEEVENTF_WHEEL;
            inputs[0].u.mi.mouseData = (uint)delta;
            SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
        }

        public void SendVirtualKey(ushort vkCode, bool keyUp)
        {
            INPUT[] inputs = new INPUT[1];
            inputs[0].type = INPUT_KEYBOARD;
            inputs[0].u.ki.wVk = vkCode;
            inputs[0].u.ki.dwFlags = keyUp ? KEYEVENTF_KEYUP : 0;
            SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
        }

        public void SendUnicodeChar(char character, bool keyUp)
        {
            INPUT[] inputs = new INPUT[1];
            inputs[0].type = INPUT_KEYBOARD;
            inputs[0].u.ki.wScan = character;
            inputs[0].u.ki.dwFlags = KEYEVENTF_UNICODE | (keyUp ? KEYEVENTF_KEYUP : 0);
            SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
        }
    }
}
