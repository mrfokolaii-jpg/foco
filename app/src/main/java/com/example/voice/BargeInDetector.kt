package com.example.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * وقتی فوکو حرف می‌زند، میکروفون را با حذف پژواک گوش می‌دهد؛
 * اگر کاربر شروع به صحبت کرد (صدای بلندتر از آستانه برای مدتی کوتاه)، onSpeech صدا زده می‌شود
 * تا فوکو ساکت شود و گوش بدهد.
 */
class BargeInDetector(private val context: Context) {

    private var record: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    @Volatile private var running = false
    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    fun start(onSpeech: () -> Unit) {
        if (running) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
        } catch (_: SecurityException) { return } catch (_: Exception) { return }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return }
        try {
            if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
            if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
        } catch (_: Exception) {}
        record = rec
        running = true
        thread = Thread {
            val buf = ShortArray(minBuf / 2)
            var loudFrames = 0
            // نیم ثانیه اول را نادیده بگیر تا شروع صدای خود فوکو اشتباه گرفته نشود
            val ignoreUntil = System.currentTimeMillis() + 600
            try {
                rec.startRecording()
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) sum += buf[i] * buf[i].toDouble()
                    val rms = sqrt(sum / n)
                    if (System.currentTimeMillis() < ignoreUntil) continue
                    if (rms > THRESHOLD) loudFrames++ else loudFrames = maxOf(0, loudFrames - 1)
                    if (loudFrames >= REQUIRED_FRAMES) {
                        running = false
                        main.post { onSpeech() }
                    }
                }
            } catch (_: Exception) {
            } finally {
                release()
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
    }

    private fun release() {
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        try { aec?.release() } catch (_: Exception) {}
        try { ns?.release() } catch (_: Exception) {}
        record = null; aec = null; ns = null
    }

    companion object {
        private const val THRESHOLD = 2200.0
        private const val REQUIRED_FRAMES = 5
    }
}
