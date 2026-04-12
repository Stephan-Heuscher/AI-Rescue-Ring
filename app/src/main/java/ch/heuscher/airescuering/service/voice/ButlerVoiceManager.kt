package ch.heuscher.airescuering.service.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Manages J-AI-mes' speaking voice with butler personality tuning.
 * 
 * Selects the best available TTS voice for a refined butler persona:
 * - English: British male voice (en-gb) for classic butler feel
 * - German: Formal German male voice (de-de) for "Guten Tag" 
 * 
 * Butler tuning: slightly lower pitch (0.92) and measured pace (0.88x default)
 * for a calm, dignified speaking style that never rushes.
 */
class ButlerVoiceManager(
    private val context: Context,
    private val onReady: (() -> Unit)? = null
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "ButlerVoiceManager"
        
        // Butler voice tuning defaults
        const val BUTLER_PITCH = 0.92f
        const val DEFAULT_SPEECH_RATE = 0.88f
        
        // User-adjustable range
        const val SPEECH_RATE_MIN = 0.5f
        const val SPEECH_RATE_MAX = 1.5f
        const val SPEECH_RATE_STEP = 0.05f
        
        // Preferred voice names by locale (highest priority first)
        private val ENGLISH_VOICES = listOf(
            "en-gb-x-rjs-network",   // British male - calm, refined
            "en-gb-x-gbd-network",   // British male variant
            "en-gb-x-fis-network",   // British variant
        )
        
        private val GERMAN_VOICES = listOf(
            "de-de-x-deb-network",   // German male - formal, clear
            "de-de-x-deg-network",   // German male variant
            "de-de-x-nfh-network",   // German variant
        )
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var speechRate: Float = DEFAULT_SPEECH_RATE
    private var onSpeechDone: (() -> Unit)? = null
    private var currentUtteranceId: String? = null
    
    // Track whether we're currently speaking
    var isSpeaking: Boolean = false
        private set

    init {
        // Explicitly use Google TTS engine for best quality
        tts = TextToSpeech(context, this, "com.google.android.tts")
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS initialization failed with status: $status")
            // Fallback: try default engine
            tts = TextToSpeech(context, { fallbackStatus ->
                if (fallbackStatus == TextToSpeech.SUCCESS) {
                    configureTTS()
                } else {
                    Log.e(TAG, "TTS fallback initialization also failed")
                }
            })
            return
        }
        configureTTS()
    }

    private fun configureTTS() {
        val locale = Locale.getDefault()
        val isGerman = locale.language == "de"
        
        Log.d(TAG, "Configuring TTS for locale: ${locale.language} (isGerman=$isGerman)")
        
        // Set base language
        val targetLocale = if (isGerman) Locale.GERMANY else Locale.UK
        val result = tts?.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language $targetLocale not available, trying default")
            tts?.setLanguage(Locale.getDefault())
        }
        
        // Find and set best butler voice
        val bestVoice = findButlerVoice(isGerman)
        if (bestVoice != null) {
            tts?.voice = bestVoice
            Log.d(TAG, "Selected butler voice: ${bestVoice.name}")
        } else {
            Log.w(TAG, "No preferred butler voice found, using system default")
            logAvailableVoices()
        }
        
        // Apply butler tuning
        tts?.setPitch(BUTLER_PITCH)
        tts?.setSpeechRate(speechRate)
        
        // Set up utterance listener
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                Log.d(TAG, "Speaking started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                Log.d(TAG, "Speaking done: $utteranceId")
                if (utteranceId == currentUtteranceId) {
                    invokeAndClearCallback()
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                isSpeaking = false
                Log.d(TAG, "Speaking stopped: $utteranceId (interrupted=$interrupted)")
                if (utteranceId == currentUtteranceId) {
                    invokeAndClearCallback()
                }
            }

            @Deprecated("Deprecated in API")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                Log.e(TAG, "Speaking error: $utteranceId")
                if (utteranceId == currentUtteranceId) {
                    invokeAndClearCallback()
                }
            }
        })
        
        isReady = true
        onReady?.invoke()
        Log.d(TAG, "Butler voice manager ready. Rate=$speechRate, Pitch=$BUTLER_PITCH")
    }

    private fun invokeAndClearCallback() {
        val callback = onSpeechDone
        onSpeechDone = null
        currentUtteranceId = null
        callback?.invoke()
    }

    private fun clearCallback() {
        onSpeechDone = null
        currentUtteranceId = null
    }

    /**
     * Find the best available butler voice for the given locale.
     * Searches through prioritized voice name lists, then falls back
     * to any network-quality voice for the language.
     */
    private fun findButlerVoice(isGerman: Boolean): Voice? {
        val voices = tts?.voices ?: return null
        val langPrefix = if (isGerman) "de-de" else "en-gb"
        val preferredNames = if (isGerman) GERMAN_VOICES else ENGLISH_VOICES
        
        // Priority 1: Exact match on preferred voice names
        for (name in preferredNames) {
            val voice = voices.find { it.name == name }
            if (voice != null) {
                Log.d(TAG, "Found preferred voice: ${voice.name}")
                return voice
            }
        }
        
        // Priority 2: Any network voice for the target language
        val networkVoice = voices.find { 
            it.name.startsWith(langPrefix) && it.name.contains("network")
        }
        if (networkVoice != null) {
            Log.d(TAG, "Found network voice: ${networkVoice.name}")
            return networkVoice
        }
        
        // Priority 3: Any voice for the target language
        val anyVoice = voices.find { it.name.startsWith(langPrefix) }
        if (anyVoice != null) {
            Log.d(TAG, "Found language voice: ${anyVoice.name}")
            return anyVoice
        }
        
        // Priority 4: Fall back to any English/German voice
        val fallbackPrefix = if (isGerman) "de" else "en"
        val fallbackVoice = voices.find { 
            it.name.startsWith(fallbackPrefix) && it.name.contains("network")
        }
        if (fallbackVoice != null) {
            Log.d(TAG, "Found fallback voice: ${fallbackVoice.name}")
            return fallbackVoice
        }
        
        return null
    }

    /**
     * Log all available voices for debugging voice selection.
     */
    private fun logAvailableVoices() {
        val voices = tts?.voices ?: return
        Log.d(TAG, "Available voices (${voices.size}):")
        voices.sortedBy { it.name }.forEach { voice ->
            Log.d(TAG, "  ${voice.name} [${voice.locale}] quality=${voice.quality}")
        }
    }

    /**
     * Set the speaking speed (0.5x to 1.5x).
     * The "butler normal" is 0.88x (slightly slower than system default 1.0x).
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(SPEECH_RATE_MIN, SPEECH_RATE_MAX)
        tts?.setSpeechRate(speechRate)
        Log.d(TAG, "Speech rate set to: $speechRate")
    }

    /**
     * Get the current speaking speed.
     */
    fun getSpeechRate(): Float = speechRate

    /**
     * Speak text with butler voice. Interrupts any current speech.
     * @param text The text to speak
     * @param onDone Optional callback when speech completes
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady) {
            Log.w(TAG, "TTS not ready yet, queuing: $text")
            return
        }
        
        if (text.isBlank()) return
        
        val utteranceId = "jaimes_${System.nanoTime()}"
        this.currentUtteranceId = utteranceId
        this.onSpeechDone = onDone
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        Log.d(TAG, "Speaking: \"$text\" (id=$utteranceId)")
    }

    /**
     * Queue text to be spoken after current speech finishes.
     * @param text The text to queue
     */
    fun queueSpeak(text: String) {
        if (!isReady || text.isBlank()) return
        
        val utteranceId = "jaimes_q_${System.nanoTime()}"
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    /**
     * Stop any current speech immediately.
     */
    fun stop() {
        tts?.stop()
        clearCallback()
        isSpeaking = false
    }

    /**
     * Get a locale-appropriate butler greeting based on time of day.
     */
    fun getGreeting(): String {
        val locale = Locale.getDefault()
        val isGerman = locale.language == "de"
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        
        return if (isGerman) {
            when {
                hour < 12 -> "Guten Morgen. Wie kann ich Ihnen behilflich sein?"
                hour < 18 -> "Guten Tag. Wie kann ich Ihnen behilflich sein?"
                else -> "Guten Abend. Wie kann ich Ihnen behilflich sein?"
            }
        } else {
            when {
                hour < 12 -> "Good morning. How may I assist you?"
                hour < 18 -> "Good afternoon. How may I assist you?"
                else -> "Good evening. How may I assist you?"
            }
        }
    }

    /**
     * Get a butler-style confirmation phrase.
     */
    fun getConfirmation(): String {
        val isGerman = Locale.getDefault().language == "de"
        return if (isGerman) {
            listOf(
                "Sehr wohl.",
                "Selbstverständlich.",
                "Wird sofort erledigt.",
                "Jawohl."
            ).random()
        } else {
            listOf(
                "Very well.",
                "Certainly.",
                "Right away.",
                "Of course."
            ).random()
        }
    }

    /**
     * Get a butler-style completion phrase.
     */
    fun getCompletion(): String {
        val isGerman = Locale.getDefault().language == "de"
        return if (isGerman) {
            "Erledigt. Kann ich Ihnen sonst noch behilflich sein?"
        } else {
            "Done. Is there anything else I can assist with?"
        }
    }

    /**
     * Get a butler-style farewell.
     */
    fun getFarewell(): String {
        val isGerman = Locale.getDefault().language == "de"
        return if (isGerman) {
            "Gerne zu Diensten. Ich bin jederzeit für Sie da."
        } else {
            "Glad to be of service. I'll be right here if you need me."
        }
    }

    /**
     * Shutdown TTS engine. Must be called when service is destroyed.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Log.d(TAG, "Butler voice manager shutdown")
    }
}
