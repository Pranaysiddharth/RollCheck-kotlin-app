package com.attendance.rollcheck.utils.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TtsManager(context: Context) {

    private data class PendingUtterance(
        val text: String,
        val utteranceId: String,
        val speechRateOverride: Float?,
        val onStart: (() -> Unit)?
    )

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingUtterance: PendingUtterance? = null
    private var speed = 1.0f
    private var pitch = 1.0f
    private var preferredVoiceName: String? = null
    private var speechEnabled = true
    private val onStartCallbacks = ConcurrentHashMap<String, () -> Unit>()

    init {
        val appContext = context.applicationContext
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        utteranceId?.let { id ->
                            onStartCallbacks.remove(id)?.invoke()
                        }
                    }

                    override fun onDone(utteranceId: String?) {}

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { onStartCallbacks.remove(it) }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let { onStartCallbacks.remove(it) }
                    }
                })
                isReady = true
                if (speechEnabled) {
                    applyVoice()
                    tts?.setSpeechRate(speed)
                    tts?.setPitch(pitch)
                    pendingUtterance?.let { pending ->
                        pendingUtterance = null
                        speak(pending.text, pending.utteranceId, pending.speechRateOverride, pending.onStart)
                    }
                } else {
                    pendingUtterance = null
                    tts?.stop()
                }
            }
        }
    }

    fun configure(speed: Float, preferredVoiceName: String? = null, enabled: Boolean = true) {
        this.speed = speed
        this.pitch = 1.0f
        this.preferredVoiceName = preferredVoiceName
        this.speechEnabled = enabled
        if (!enabled) {
            pendingUtterance = null
            tts?.stop()
            return
        }
        if (isReady) {
            applyVoice()
            tts?.setSpeechRate(speed)
            tts?.setPitch(pitch)
        }
    }

    private fun applyVoice() {
        val engine = tts ?: return
        val explicit = preferredVoiceName?.let { wanted ->
            (engine.voices ?: emptySet()).firstOrNull { it.name == wanted }
        }
        val chosen = explicit
        if (chosen != null) {
            engine.language = chosen.locale
            engine.voice = chosen
        } else {
            val voices = engine.voices ?: emptySet()
            val fallback = voices.firstOrNull { it.locale == Locale.US || it.locale.language == "en" }
            fallback?.let { engine.voice = it }
        }
    }

    fun selectedVoiceName(): String = preferredVoiceName ?: "Default"

    fun availableVoiceModels(): List<String> {
        val engine = tts ?: return emptyList()
        return (engine.voices ?: emptySet())
            .filter { (it.locale == Locale.US || it.locale.language == "en") && !it.isNetworkConnectionRequired }
            .map { it.name }
            .distinct()
            .sorted()
    }

    fun speakRoll(
        rollId: String,
        utteranceId: String = "qm_tts",
        speechRateOverride: Float? = null,
        onStart: (() -> Unit)? = null
    ) {
        fun digitWord(ch: Char): String = when (ch) {
            '0' -> "zero"
            '1' -> "one"
            '2' -> "two"
            '3' -> "three"
            '4' -> "four"
            '5' -> "five"
            '6' -> "six"
            '7' -> "seven"
            '8' -> "eight"
            '9' -> "nine"
            else -> ch.toString()
        }

        fun teenWord(ch: Char): String = when (ch) {
            '0' -> "ten"
            '1' -> "eleven"
            '2' -> "twelve"
            '3' -> "thirteen"
            '4' -> "fourteen"
            '5' -> "fifteen"
            '6' -> "sixteen"
            '7' -> "seventeen"
            '8' -> "eighteen"
            '9' -> "nineteen"
            else -> "ten"
        }

        fun tensWord(ch: Char): String = when (ch) {
            '1' -> "ten"
            '2' -> "twenty"
            '3' -> "thirty"
            '4' -> "forty"
            '5' -> "fifty"
            '6' -> "sixty"
            '7' -> "seventy"
            '8' -> "eighty"
            '9' -> "ninety"
            else -> digitWord(ch)
        }

        fun twoDigitWords(value: String): String {
            if (value.length != 2) return value
            val tens = value[0]
            val ones = value[1]
            return when {
                tens == '0' -> digitWord(ones)
                tens == '1' -> teenWord(ones)
                ones == '0' -> tensWord(tens)
                else -> "${tensWord(tens)} ${digitWord(ones)}"
            }
        }

        val digits = rollId.reversed().takeWhile { it.isDigit() }.reversed()
        val numStr = digits.trimStart('0').ifEmpty { "0" }

        val textToSpeak = when {
            numStr.length == 3 -> {
                val hundreds = numStr[0]
                val trailing = numStr.substring(1)
                when {
                    trailing == "00" -> if (hundreds == '1') "hundred" else "${digitWord(hundreds)} hundred"
                    trailing[0] == '0' -> "${digitWord(hundreds)} oh ${digitWord(trailing[1])}"
                    else -> "${digitWord(hundreds)} ${twoDigitWords(trailing)}"
                }
            }
            numStr.length == 2 -> twoDigitWords(numStr)
            else -> numStr.map(::digitWord).joinToString(" ")
        }
        speak(textToSpeak, utteranceId, speechRateOverride, onStart)
    }

    fun speak(
        text: String,
        utteranceId: String = "qm_tts",
        speechRateOverride: Float? = null,
        onStart: (() -> Unit)? = null
    ) {
        if (onStart != null) {
            onStartCallbacks[utteranceId] = onStart
        } else {
            onStartCallbacks.remove(utteranceId)
        }
        if (!speechEnabled) {
            pendingUtterance = null
            onStartCallbacks.remove(utteranceId)
            return
        }
        if (!isReady) {
            pendingUtterance = PendingUtterance(text, utteranceId, speechRateOverride, onStart)
            return
        }
        tts?.setSpeechRate(speechRateOverride ?: speed)
        tts?.setPitch(pitch)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun isReady(): Boolean = isReady

    private fun isAlive(): Boolean = tts != null

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        pendingUtterance = null
        onStartCallbacks.clear()
        if (rollCallEngine === this) {
            rollCallEngine = null
        }
    }

    companion object {
        @Volatile
        private var rollCallEngine: TtsManager? = null

        fun getRollCallEngine(context: Context): TtsManager {
            val existing = rollCallEngine
            if (existing != null && existing.isAlive()) return existing
            return synchronized(this) {
                val current = rollCallEngine
                if (current != null && current.isAlive()) {
                    current
                } else {
                    TtsManager(context.applicationContext).also { rollCallEngine = it }
                }
            }
        }

        fun preWarmRollCall(
            context: Context,
            speed: Float,
            preferredVoiceName: String? = null,
            enabled: Boolean = true
        ): TtsManager = getRollCallEngine(context).also {
            it.configure(speed, preferredVoiceName, enabled)
        }

        suspend fun awaitRollCallReady(
            context: Context,
            timeoutMs: Long = 1200L,
            pollMs: Long = 40L,
            enabled: Boolean = true
        ): Boolean {
            if (!enabled) return true
            val engine = getRollCallEngine(context)
            if (engine.isReady()) return true

            var waited = 0L
            while (waited < timeoutMs) {
                delay(pollMs)
                if (engine.isReady()) return true
                waited += pollMs
            }
            return engine.isReady()
        }
    }
}