using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using SmartRemoteControl.Windows.Models;

namespace SmartRemoteControl.Windows.Services
{
    /// <summary>
    /// خدمة التقاط شاشة سطح المكتب في Windows وضغط الإطارات للنقل اللحظي
    /// متكيفة ديناميكياً مع تغير دقة الشاشة وتعدد الشاشات
    /// </summary>
    public class ScreenCaptureService : IDisposable
    {
        public event Action<ScreenFramePacket>? FrameCaptured;

        private CancellationTokenSource? _cts;
        private Task? _captureTask;
        private long _frameCounter = 0;

        public bool IsRunning { get; private set; }
        public int TargetFps { get; set; } = 30;
        public long JpegQuality { get; set; } = 60L;
        public double ScaleFactor { get; set; } = 0.75; // 75% resolution for balanced network performance

        private readonly ImageCodecInfo? _jpegEncoder;
        private readonly EncoderParameters _encoderParams;

        public ScreenCaptureService()
        {
            _jpegEncoder = GetEncoder(ImageFormat.Jpeg);
            _encoderParams = new EncoderParameters(1);
            _encoderParams.Param[0] = new EncoderParameter(Encoder.Quality, JpegQuality);
        }

        public void Start(string sessionId, int targetFps = 30)
        {
            if (IsRunning) return;

            TargetFps = Math.Clamp(targetFps, 10, 60);
            _cts = new CancellationTokenSource();
            IsRunning = true;

            _captureTask = Task.Run(() => CaptureLoop(sessionId, _cts.Token));
        }

        public void Stop()
        {
            if (!IsRunning) return;

            _cts?.Cancel();
            try
            {
                _captureTask?.Wait(1000);
            }
            catch { }

            IsRunning = false;
        }

        private async Task CaptureLoop(string sessionId, CancellationToken ct)
        {
            int lastWidth = 0;
            int lastHeight = 0;
            Bitmap? rawBitmap = null;
            Graphics? rawGraphics = null;

            int targetDelayMs = 1000 / TargetFps;

            try
            {
                while (!ct.IsCancellationRequested)
                {
                    var stopwatch = System.Diagnostics.Stopwatch.StartNew();

                    try
                    {
                        int currentWidth = System.Windows.Forms.Screen.PrimaryScreen?.Bounds.Width ?? 1920;
                        int currentHeight = System.Windows.Forms.Screen.PrimaryScreen?.Bounds.Height ?? 1080;

                        // إعادة تهيئة الـ Bitmap إن تغيرت أبعاد الشاشة فجأة
                        if (currentWidth != lastWidth || currentHeight != lastHeight || rawBitmap == null || rawGraphics == null)
                        {
                            rawGraphics?.Dispose();
                            rawBitmap?.Dispose();

                            lastWidth = currentWidth;
                            lastHeight = currentHeight;
                            rawBitmap = new Bitmap(currentWidth, currentHeight, PixelFormat.Format32bppArgb);
                            rawGraphics = Graphics.FromImage(rawBitmap);
                        }

                        int targetWidth = Math.Max(320, (int)(currentWidth * ScaleFactor));
                        int targetHeight = Math.Max(240, (int)(currentHeight * ScaleFactor));

                        // التقاط سطح المكتب
                        rawGraphics.CopyFromScreen(0, 0, 0, 0, new Size(currentWidth, currentHeight), CopyPixelOperation.SourceCopy);

                        // رسم مؤشر الفأرة الحقيقي على لقطة الشاشة
                        try
                        {
                            var cursorPosition = System.Windows.Forms.Cursor.Position;
                            System.Windows.Forms.Cursors.Default.Draw(rawGraphics, new Rectangle(cursorPosition, new Size(32, 32)));
                        }
                        catch { }

                        // تصغير الحجم للسرعة
                        using var scaledBitmap = new Bitmap(targetWidth, targetHeight);
                        using (var scaledGraphics = Graphics.FromImage(scaledBitmap))
                        {
                            scaledGraphics.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.Bilinear;
                            scaledGraphics.DrawImage(rawBitmap, 0, 0, targetWidth, targetHeight);
                        }

                        // ترميز إلى JPEG
                        using var ms = new MemoryStream();
                        if (_jpegEncoder != null)
                        {
                            _encoderParams.Param[0] = new EncoderParameter(Encoder.Quality, JpegQuality);
                            scaledBitmap.Save(ms, _jpegEncoder, _encoderParams);
                        }
                        else
                        {
                            scaledBitmap.Save(ms, ImageFormat.Jpeg);
                        }

                        string base64 = Convert.ToBase64String(ms.ToArray());

                        var framePacket = new ScreenFramePacket
                        {
                            SessionId = sessionId,
                            FrameIndex = Interlocked.Increment(ref _frameCounter),
                            Format = "JPEG",
                            Width = targetWidth,
                            Height = targetHeight,
                            PayloadBase64 = base64
                        };

                        FrameCaptured?.Invoke(framePacket);
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[ScreenCapture] استثناء أثناء التقاط الشاشة: {ex.Message}");
                    }

                    stopwatch.Stop();
                    int sleepTime = Math.Max(5, targetDelayMs - (int)stopwatch.ElapsedMilliseconds);
                    await Task.Delay(sleepTime, ct);
                }
            }
            finally
            {
                rawGraphics?.Dispose();
                rawBitmap?.Dispose();
            }
        }

        private static ImageCodecInfo? GetEncoder(ImageFormat format)
        {
            var codecs = ImageCodecInfo.GetImageDecoders();
            foreach (var codec in codecs)
            {
                if (codec.FormatID == format.Guid)
                {
                    return codec;
                }
            }
            return null;
        }

        public void Dispose()
        {
            Stop();
            _encoderParams?.Dispose();
        }
    }
}
