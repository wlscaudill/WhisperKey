using System.Diagnostics;
using NAudio.Wave;
using WhisperKeys.App;

namespace WhisperKeys.Audio;

public record AudioDeviceInfo(int DeviceNumber, string Name);

public class AudioRecorder : IDisposable
{
    private WaveInEvent? _waveIn;
    private MemoryStream? _buffer;
    private readonly object _lock = new();
    private bool _isRecording;
    private bool _firstDataReceived;
    private int _deviceNumber;
    private Stopwatch? _openStopwatch;

    public bool IsRecording => _isRecording;

    /// <summary>
    /// Fired when the first audio data arrives (mic is actually capturing).
    /// </summary>
    public event Action? RecordingStarted;
    public event Action? RecordingStopped;

    public int DeviceNumber
    {
        get => _deviceNumber;
        set
        {
            if (_isRecording)
                throw new InvalidOperationException("Cannot change device while recording");
            _deviceNumber = value;
        }
    }

    public static List<AudioDeviceInfo> GetAvailableDevices()
    {
        var devices = new List<AudioDeviceInfo>();
        for (int i = 0; i < WaveInEvent.DeviceCount; i++)
        {
            var caps = WaveInEvent.GetCapabilities(i);
            devices.Add(new AudioDeviceInfo(i, caps.ProductName));
        }
        return devices;
    }

    public void StartRecording()
    {
        lock (_lock)
        {
            if (_isRecording) return;

            var deviceNum = _deviceNumber >= 0 ? _deviceNumber : 0;
            var deviceCount = WaveInEvent.DeviceCount;

            Logger.Debug($"Starting recording on device {deviceNum} (of {deviceCount} available)");

            if (deviceCount == 0)
                throw new InvalidOperationException("No audio input devices found");

            if (deviceNum >= deviceCount)
            {
                Logger.Debug($"Device {deviceNum} not available, falling back to device 0");
                deviceNum = 0;
            }

            var caps = WaveInEvent.GetCapabilities(deviceNum);
            Logger.Debug($"Using device: {caps.ProductName}");

            _buffer = new MemoryStream();
            _firstDataReceived = false;
            _openStopwatch = Stopwatch.StartNew();

            _waveIn = new WaveInEvent
            {
                DeviceNumber = deviceNum,
                WaveFormat = new WaveFormat(16000, 16, 1), // 16kHz, 16-bit, mono
                BufferMilliseconds = 50
            };

            _waveIn.DataAvailable += OnDataAvailable;
            _waveIn.RecordingStopped += OnRecordingStopped;

            _waveIn.StartRecording();
            _isRecording = true;
        }
    }

    public float[] StopRecording()
    {
        lock (_lock)
        {
            if (!_isRecording || _waveIn == null || _buffer == null)
                return Array.Empty<float>();

            _waveIn.StopRecording();
            _isRecording = false;

            var audioData = ConvertToFloat(_buffer.ToArray());

            _waveIn.DataAvailable -= OnDataAvailable;
            _waveIn.RecordingStopped -= OnRecordingStopped;
            _waveIn.Dispose();
            _waveIn = null;

            _buffer.Dispose();
            _buffer = null;

            Logger.Log($"Recording stopped, {audioData.Length} samples ({audioData.Length / 16000.0:F1}s)");
            RecordingStopped?.Invoke();
            return audioData;
        }
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs e)
    {
        lock (_lock)
        {
            if (_buffer == null) return;

            _buffer.Write(e.Buffer, 0, e.BytesRecorded);

            if (!_firstDataReceived)
            {
                _firstDataReceived = true;
                var elapsed = _openStopwatch?.ElapsedMilliseconds ?? 0;
                Logger.Debug($"First audio data received ({elapsed}ms after open)");
                RecordingStarted?.Invoke();
            }
        }
    }

    private void OnRecordingStopped(object? sender, StoppedEventArgs e)
    {
        if (e.Exception != null)
        {
            Logger.Error("Recording error", e.Exception);
        }
    }

    private static float[] ConvertToFloat(byte[] pcmBytes)
    {
        int sampleCount = pcmBytes.Length / 2;
        var floats = new float[sampleCount];

        for (int i = 0; i < sampleCount; i++)
        {
            short sample = BitConverter.ToInt16(pcmBytes, i * 2);
            floats[i] = sample / 32768f;
        }

        return floats;
    }

    public void Dispose()
    {
        lock (_lock)
        {
            if (_isRecording)
            {
                _waveIn?.StopRecording();
                _isRecording = false;
            }
            _waveIn?.Dispose();
            _buffer?.Dispose();
        }
    }
}
