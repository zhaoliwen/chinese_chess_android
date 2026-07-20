package livan.chinese_chess.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 人声播报：使用系统语音合成（中文）
 * 移植自网页版 js/voice.js（原为浏览器 speechSynthesis）。
 */
class Voice(context: Context) : TextToSpeech.OnInitListener {

    @Volatile
    private var enabled = true

    @Volatile
    private var lastSpeakAt = 0L

    /** TTS 是否初始化完成；未完成时的 speak 请求直接丢弃 */
    @Volatile
    private var ready = false

    private var tts: TextToSpeech? = null

    init {
        tts = try {
            TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            null
        }
    }

    /** TTS 初始化异步回调 */
    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val t = tts ?: return
        try {
            // 优先中文，不可用则回退默认语言
            val r = t.setLanguage(Locale.CHINESE)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                t.setLanguage(Locale.getDefault())
            }
            t.setPitch(1f)
            ready = true
        } catch (_: Exception) {
        }
    }

    fun setEnabled(v: Boolean) {
        enabled = v
        if (!v) stopNow() // 关闭时立即停止播报
    }

    fun isEnabled(): Boolean = enabled

    private fun stopNow() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    /**
     * @param text 要播报的文本
     * @param rate 语速，为空时按句长自动选择（短句稍快）
     * @param force 为 true 时跳过 280ms 防抖
     */
    fun speak(text: String?, rate: Float? = null, force: Boolean = false) {
        if (!enabled || text.isNullOrBlank()) return
        val t = tts
        if (!ready || t == null) return // 未初始化完成的请求直接丢弃
        val now = System.currentTimeMillis()
        if (!force && now - lastSpeakAt < 280) return // 280ms 防抖
        lastSpeakAt = now

        try {
            t.stop() // 先停掉上一条
            // 逗号处分句，读起来更清晰
            val chunks = text.split('，', ',', '、')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val params = Bundle()
            for (chunk in chunks) {
                // 短句（≤3 字）语速稍快一点
                t.setSpeechRate(rate ?: if (chunk.length <= 3) 1.08f else 1.0f)
                t.speak(chunk, TextToSpeech.QUEUE_ADD, params, "xq_tts")
            }
        } catch (_: Exception) {
        }
    }

    /** 释放 TTS 资源（如 Activity onDestroy 时调用） */
    fun shutdown() {
        ready = false
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }
}
