using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace SmartRemoteControl.Windows.Models
{
    public class HandshakeHelloPacket
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = "HANDSHAKE_HELLO";

        [JsonPropertyName("deviceId")]
        public string DeviceId { get; set; } = string.Empty;

        [JsonPropertyName("deviceName")]
        public string DeviceName { get; set; } = Environment.MachineName;

        [JsonPropertyName("platform")]
        public string Platform { get; set; } = "windows";

        [JsonPropertyName("appVersion")]
        public string AppVersion { get; set; } = "1.1.0";

        [JsonPropertyName("supportedRoles")]
        public List<string> SupportedRoles { get; set; } = new() { "host", "client", "bidirectional" };

        [JsonPropertyName("capabilities")]
        public DeviceCapabilities Capabilities { get; set; } = new();
    }

    public class DeviceCapabilities
    {
        [JsonPropertyName("maxResolution")]
        public string MaxResolution { get; set; } = "1920x1080";

        [JsonPropertyName("maxFps")]
        public int MaxFps { get; set; } = 60;

        [JsonPropertyName("codecs")]
        public List<string> Codecs { get; set; } = new() { "JPEG", "H264" };

        [JsonPropertyName("supportsMouseInjection")]
        public bool SupportsMouseInjection { get; set; } = true;

        [JsonPropertyName("supportsKeyboardInjection")]
        public bool SupportsKeyboardInjection { get; set; } = true;

        [JsonPropertyName("supportsTouchInjection")]
        public bool SupportsTouchInjection { get; set; } = true;
    }

    public class SessionRequestPacket
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = "SESSION_REQUEST";

        [JsonPropertyName("sessionId")]
        public string SessionId { get; set; } = Guid.NewGuid().ToString("N");

        [JsonPropertyName("initiatorId")]
        public string InitiatorId { get; set; } = string.Empty;

        [JsonPropertyName("targetId")]
        public string TargetId { get; set; } = string.Empty;

        [JsonPropertyName("requestedRole")]
        public string RequestedRole { get; set; } = "full_control";

        [JsonPropertyName("sessionPin")]
        public string SessionPin { get; set; } = string.Empty;

        [JsonPropertyName("requestedResolution")]
        public string RequestedResolution { get; set; } = "1280x720";

        [JsonPropertyName("targetFps")]
        public int TargetFps { get; set; } = 30;
    }

    public class SessionResponsePacket
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = "SESSION_RESPONSE";

        [JsonPropertyName("sessionId")]
        public string SessionId { get; set; } = string.Empty;

        [JsonPropertyName("accepted")]
        public bool Accepted { get; set; }

        [JsonPropertyName("rejectionReason")]
        public string? RejectionReason { get; set; }

        [JsonPropertyName("assignedStreamPort")]
        public int AssignedStreamPort { get; set; } = 52840;
    }

    public class InputEventPacket
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = "INPUT_EVENT";

        [JsonPropertyName("sessionId")]
        public string SessionId { get; set; } = string.Empty;

        [JsonPropertyName("sequence")]
        public long Sequence { get; set; }

        [JsonPropertyName("timestamp")]
        public long Timestamp { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

        [JsonPropertyName("subType")]
        public string SubType { get; set; } = string.Empty; // MOUSE_MOVE, MOUSE_DOWN, KEY_DOWN, etc.

        [JsonPropertyName("normalizedX")]
        public double? NormalizedX { get; set; } // 0.0 to 1.0

        [JsonPropertyName("normalizedY")]
        public double? NormalizedY { get; set; } // 0.0 to 1.0

        [JsonPropertyName("button")]
        public string? Button { get; set; } // left, right, middle

        [JsonPropertyName("wheelDelta")]
        public int? WheelDelta { get; set; }

        [JsonPropertyName("keyCode")]
        public int? KeyCode { get; set; }

        [JsonPropertyName("keyText")]
        public string? KeyText { get; set; }

        [JsonPropertyName("modifiers")]
        public List<string>? Modifiers { get; set; }

        [JsonPropertyName("globalAction")]
        public string? GlobalAction { get; set; }
    }

    public class ScreenFramePacket
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = "SCREEN_FRAME_CHUNK";

        [JsonPropertyName("sessionId")]
        public string SessionId { get; set; } = string.Empty;

        [JsonPropertyName("frameIndex")]
        public long FrameIndex { get; set; }

        [JsonPropertyName("format")]
        public string Format { get; set; } = "JPEG";

        [JsonPropertyName("width")]
        public int Width { get; set; }

        [JsonPropertyName("height")]
        public int Height { get; set; }

        [JsonPropertyName("payloadBase64")]
        public string PayloadBase64 { get; set; } = string.Empty;
    }

    public class DisconnectPacket
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = "DISCONNECT";

        [JsonPropertyName("sessionId")]
        public string SessionId { get; set; } = string.Empty;

        [JsonPropertyName("reason")]
        public string Reason { get; set; } = "user_requested";
    }
}
