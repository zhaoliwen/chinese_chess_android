package livan.chinese_chess.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * 棋盘音效（程序合成 PCM，无需外部音频文件）
 * 移植自网页版 js/sound.js（原为 Web Audio 振荡器+噪声实时合成），
 * 这里在后台线程用 AudioTrack 播放合成的 16bit PCM。
 */
object Sfx {

    private const val SAMPLE_RATE = 22050

    @Volatile
    private var enabled = true

    /** 正在播放的 AudioTrack，用于关闭音效时立即静音 */
    private val activeTracks = CopyOnWriteArrayList<AudioTrack>()

    fun setEnabled(v: Boolean) {
        enabled = v
        if (!v) stopAll() // 关闭时立即静音（停止正在播放的）
    }

    fun isEnabled(): Boolean = enabled

    /** 停止并释放所有正在播放的音效 */
    private fun stopAll() {
        val snapshot = activeTracks.toList()
        activeTracks.clear()
        for (t in snapshot) {
            try {
                t.stop()
            } catch (_: Exception) {
            }
            try {
                t.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 播放短促音：freq 频率，durMs 毫秒，type 波形，vol 音量，slideTo 可选滑音终点
     * 12ms 指数起音 + 指数衰减到结尾（与 Web Audio 版的 gain 包络一致）
     */
    private fun mixTone(
        buf: FloatArray,
        offsetMs: Int,
        freq: Double,
        durMs: Int,
        type: String,
        vol: Double,
        slideTo: Double? = null,
    ) {
        val n = SAMPLE_RATE * durMs / 1000
        if (n <= 0) return
        val start = SAMPLE_RATE * offsetMs / 1000
        if (start >= buf.size) return
        val attackN = max(1, SAMPLE_RATE * 12 / 1000) // 12ms 起音
        val decayN = max(1, n - attackN)
        var phase = 0.0
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= buf.size) break
            val t = i.toDouble() / n
            // 可选滑音（指数插值，对应 exponentialRampToValueAtTime）
            val f = if (slideTo != null) freq * (slideTo / freq).pow(t) else freq
            phase += f / SAMPLE_RATE
            val p = phase - floor(phase)
            val w = when (type) {
                "square" -> if (p < 0.5) 1.0 else -1.0
                "triangle" -> 4.0 * abs(p - 0.5) - 1.0
                "sawtooth" -> 2.0 * p - 1.0
                else -> sin(2.0 * Math.PI * p) // sine
            }
            // 包络：0.0001 -> vol（指数起音）-> 0.0001（指数衰减）
            val env = if (i < attackN) {
                vol * (0.0001 / vol).pow(1.0 - i.toDouble() / attackN)
            } else {
                vol * (0.0001 / vol).pow((i - attackN).toDouble() / decayN)
            }
            buf[idx] += (w * env).toFloat()
        }
    }

    /** 木板轻敲感：低频噪音短脉冲（50ms 指数衰减白噪声 + 简单低通） */
    private fun mixWoodTap(buf: FloatArray, offsetMs: Int, vol: Double) {
        val n = SAMPLE_RATE * 50 / 1000
        val start = SAMPLE_RATE * offsetMs / 1000
        val rnd = java.util.Random()
        // 一阶低通系数，约 900Hz 截止（对应 JS 的 BiquadFilter lowpass）
        val a = 1.0 - exp(-2.0 * Math.PI * 900.0 / SAMPLE_RATE)
        var lp = 0.0
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= buf.size) break
            val x = rnd.nextDouble() * 2.0 - 1.0
            lp += a * (x - lp) // 简单低通
            val env = exp(-i.toDouble() / (n * 0.18))
            buf[idx] += (lp * env * vol).toFloat()
        }
    }

    /** 合成整段 PCM 后在后台线程播放，音效失败不能崩游戏 */
    private fun play(durationMs: Int, mix: (FloatArray) -> Unit) {
        if (!enabled) return
        try {
            val buf = FloatArray(SAMPLE_RATE * durationMs / 1000 + SAMPLE_RATE / 20)
            mix(buf)
            val pcm = ShortArray(buf.size) { i ->
                (buf[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            playAsync(pcm)
        } catch (_: Exception) {
        }
    }

    private fun playAsync(pcm: ShortArray) {
        if (!enabled) return
        Thread {
            var track: AudioTrack? = null
            try {
                track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    pcm.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE,
                )
                activeTracks.add(track)
                if (!enabled) { // 合成期间被关闭
                    stopAll()
                    return@Thread
                }
                track.write(pcm, 0, pcm.size)
                track.play()
                // 等这段 PCM 播完再释放
                Thread.sleep(pcm.size * 1000L / SAMPLE_RATE + 30)
            } catch (_: Exception) {
            } finally {
                // 若 stopAll() 已抢先 release，这里不再重复释放
                if (track != null && activeTracks.remove(track)) {
                    try {
                        track.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }.start()
    }

    fun select() = play(60) {
        mixTone(it, 0, 520.0, 60, "triangle", 0.08)
    }

    fun move() = play(80) {
        mixWoodTap(it, 0, 0.22)
        mixTone(it, 0, 180.0, 80, "sine", 0.06)
    }

    fun capture() = play(160) {
        mixWoodTap(it, 0, 0.28)
        mixTone(it, 0, 220.0, 100, "square", 0.05)
        mixTone(it, 40, 140.0, 120, "triangle", 0.07)
    }

    fun check() = play(250) {
        mixTone(it, 0, 660.0, 120, "square", 0.07)
        mixTone(it, 90, 880.0, 160, "square", 0.06)
    }

    /** 胜利：上行三音 */
    fun win() = play(440) {
        mixTone(it, 0, 523.0, 120, "sine", 0.08)
        mixTone(it, 110, 659.0, 120, "sine", 0.08)
        mixTone(it, 220, 784.0, 220, "sine", 0.09)
    }

    fun lose() = play(440) {
        mixTone(it, 0, 392.0, 180, "triangle", 0.08, 220.0)
        mixTone(it, 160, 247.0, 280, "sine", 0.07)
    }

    fun draw() = play(340) {
        mixTone(it, 0, 440.0, 140, "sine", 0.06)
        mixTone(it, 160, 440.0, 180, "sine", 0.05)
    }

    /** 非法走子提示音 */
    fun illegal() = play(80) {
        mixTone(it, 0, 160.0, 80, "sawtooth", 0.04)
    }

    fun newGame() = play(240) {
        mixTone(it, 0, 392.0, 100, "sine", 0.06)
        mixTone(it, 100, 523.0, 140, "sine", 0.07)
    }

    fun undo() = play(80) {
        mixTone(it, 0, 330.0, 80, "triangle", 0.05, 260.0)
    }
}
