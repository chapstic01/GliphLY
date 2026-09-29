package dev.gliphly

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlin.math.abs
import kotlin.math.max

/**
 * Foreground service that holds the MediaProjection grant and records the
 * device's playback mix via AudioPlaybackCaptureConfiguration (API 29+).
 * This is the real, still-supported replacement for the old Visualizer(0)
 * "capture the output mix" trick, which OEMs increasingly block outright.
 */
class PlaybackCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    @Volatile private var reading = false
    private var readThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        startForeground(1, buildNotification())

        if (data == null || resultCode != Activity.RESULT_OK) {
            Log.e("PlaybackCapture", "Missing projection grant")
            stopSelf(); return START_NOT_STICKY
        }

        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mgr.getMediaProjection(resultCode, data)
        mediaProjection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture(); stopSelf() }
        }, null)

        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(44100)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

        try {
            val rec = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuf * 2)
                .setAudioPlaybackCaptureConfig(config)
                .build()
            audioRecord = rec
            rec.startRecording()
            reading = true
            AudioBus.running.value = true
            readThread = Thread { readLoop(rec, minBuf) }.also { it.start() }
        } catch (e: Exception) {
            Log.e("PlaybackCapture", "AudioRecord setup failed", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun readLoop(rec: AudioRecord, minBuf: Int) {
        val buf = ShortArray(minBuf)
        while (reading) {
            val n = rec.read(buf, 0, buf.size)
            if (n > 0) {
                var peak = 0
                for (i in 0 until n) peak = max(peak, abs(buf[i].toInt()))
                AudioBus.level.value = (peak / 32768f).coerceIn(0f, 1f)
            }
        }
    }

    private fun stopCapture() {
        reading = false
        readThread?.join(200)
        audioRecord?.let { runCatching { it.stop(); it.release() } }
        audioRecord = null
        mediaProjection?.stop()
        mediaProjection = null
        AudioBus.running.value = false
        AudioBus.level.value = 0f
    }

    override fun onDestroy() { stopCapture(); super.onDestroy() }

    private fun buildNotification(): Notification {
        val channelId = "gliphly_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Glyph audio capture", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("GliphLY visualizer running")
            .setContentText("Mirroring playback to the Glyph")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    companion object {
        fun serviceInfoType(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
    }
}
