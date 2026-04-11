package ch.heuscher.airescuering.service.voice

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import ch.heuscher.airescuering.data.api.GeminiApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Central engine managing J-AI-mes' conversation loop:
 * 
 * 1. User taps button → capture screenshot (J-AI-mes' "eyes")
 * 2. J-AI-mes greets user via TTS
 * 3. Auto-listen for speech input (STT)
 * 4. Send speech + screenshot to Gemini
 * 5. Speak response via TTS
 * 6. If action needed → delegate to ButlerActionAgent
 * 7. Continue conversation or retire
 * 
 * State machine: IDLE → GREETING → LISTENING → PROCESSING → SPEAKING → (loop or IDLE)
 */
class VoiceConversationEngine(
    private val context: Context,
    private val voiceManager: ButlerVoiceManager,
    private val personality: ButlerPersonality,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "VoiceConversationEngine"
        private const val MAX_LISTENING_RETRIES = 3
    }

    enum class State {
        IDLE,
        GREETING,
        LISTENING,
        PROCESSING,
        SPEAKING,
        ACTING
    }

    var state: State = State.IDLE
        private set

    // Callbacks for UI updates
    var onStateChanged: ((State) -> Unit)? = null
    var onTranscription: ((String) -> Unit)? = null
    var onResponse: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // API service for Gemini calls
    private var geminiApiService: GeminiApiService? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentScreenshot: Bitmap? = null
    private var conversationHistory: MutableList<Pair<String, String>> = mutableListOf() // role, content
    private var currentJob: Job? = null
    private var apiKey: String = ""
    private var listeningRetryCount: Int = 0

    /**
     * Set the API key and/or proxy URL for Gemini. 
     */
    fun setApiConfig(key: String, proxyUrl: String = "") {
        apiKey = key
        geminiApiService = GeminiApiService(apiKey = key, proxyUrl = proxyUrl)
    }

    /**
     * Start a new conversation. Called when user taps the butler button.
     * @param screenshot Current screen capture (J-AI-mes' "eyes")
     * @param foregroundApp Package name of the app currently in foreground
     */
    fun startConversation(screenshot: Bitmap? = null, foregroundApp: String? = null) {
        if (state != State.IDLE) {
            Log.w(TAG, "Already in conversation (state=$state), stopping first")
            stopConversation()
        }

        Log.d(TAG, "Starting new conversation. Screenshot=${screenshot != null}, app=$foregroundApp")
        currentScreenshot = screenshot
        conversationHistory.clear()
        listeningRetryCount = 0

        // Transition to greeting
        setState(State.GREETING)

        // Generate context-aware greeting
        val greeting = if (foregroundApp != null) {
            personality.getContextGreeting(foregroundApp)
        } else {
            personality.getGreeting()
        }

        // Speak greeting, then auto-listen
        voiceManager.speak(greeting) {
            // After greeting finishes, start listening
            startListening()
        }

        onResponse?.invoke(greeting)
    }

    /**
     * Process a text message (from STT or manual text input).
     */
    fun processMessage(userMessage: String) {
        if (userMessage.isBlank()) {
            retryListening("Blank message")
            return
        }

        Log.d(TAG, "Processing user message: \"$userMessage\"")
        onTranscription?.invoke(userMessage)
        
        // Reset retry count on any valid message
        listeningRetryCount = 0

        // Check for voice command to adjust speed
        if (handleVoiceCommand(userMessage)) return

        setState(State.PROCESSING)
        conversationHistory.add("user" to userMessage)

        currentJob = scope.launch {
            try {
                val response = callGemini(userMessage, currentScreenshot)
                if (response != null) {
                    conversationHistory.add("assistant" to response)
                    
                    withContext(Dispatchers.Main) {
                        setState(State.SPEAKING)
                        onResponse?.invoke(response)
                        
                        voiceManager.speak(response) {
                            // After speaking, listen for follow-up
                            startListening()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        val error = personality.getErrorMessage()
                        onError?.invoke(error)
                        voiceManager.speak(error) {
                            startListening()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
                withContext(Dispatchers.Main) {
                    val error = personality.getErrorMessage()
                    onError?.invoke(error)
                    voiceManager.speak(error) {
                        startListening()
                    }
                }
            }
        }
    }

    /**
     * Handle voice commands like "speak slower", "speak faster".
     * @return true if this was a voice command (handled internally)
     */
    private fun handleVoiceCommand(message: String): Boolean {
        val lower = message.lowercase()
        val isGerman = Locale.getDefault().language == "de"

        // Speed adjustment commands
        val slowerPatterns = if (isGerman) {
            listOf("langsamer", "spreche langsamer", "sprich langsamer", "etwas langsamer")
        } else {
            listOf("slower", "speak slower", "slow down", "a bit slower")
        }

        val fasterPatterns = if (isGerman) {
            listOf("schneller", "spreche schneller", "sprich schneller", "etwas schneller")
        } else {
            listOf("faster", "speak faster", "speed up", "a bit faster")
        }

        if (slowerPatterns.any { lower.contains(it) }) {
            val newRate = (voiceManager.getSpeechRate() - 0.1f)
                .coerceIn(ButlerVoiceManager.SPEECH_RATE_MIN, ButlerVoiceManager.SPEECH_RATE_MAX)
            voiceManager.setSpeechRate(newRate)
            
            val confirmation = if (isGerman) {
                "Selbstverständlich. Ist dieses Tempo besser?"
            } else {
                "Of course. Is this pace better?"
            }
            
            setState(State.SPEAKING)
            voiceManager.speak(confirmation) { startListening() }
            onResponse?.invoke(confirmation)
            return true
        }

        if (fasterPatterns.any { lower.contains(it) }) {
            val newRate = (voiceManager.getSpeechRate() + 0.1f)
                .coerceIn(ButlerVoiceManager.SPEECH_RATE_MIN, ButlerVoiceManager.SPEECH_RATE_MAX)
            voiceManager.setSpeechRate(newRate)
            
            val confirmation = if (isGerman) {
                "Selbstverständlich. Ist dieses Tempo besser?"
            } else {
                "Of course. Is this pace better?"
            }
            
            setState(State.SPEAKING)
            voiceManager.speak(confirmation) { startListening() }
            onResponse?.invoke(confirmation)
            return true
        }

        // Dismissal commands
        val dismissPatterns = if (isGerman) {
            listOf("danke", "nein danke", "das wars", "tschüss", "nichts weiter")
        } else {
            listOf("thank you", "no thanks", "that's all", "goodbye", "nothing else", "I'm good")
        }

        if (dismissPatterns.any { lower.contains(it) }) {
            val farewell = personality.getFarewell()
            setState(State.SPEAKING)
            onResponse?.invoke(farewell)
            voiceManager.speak(farewell) {
                stopConversation()
            }
            return true
        }

        return false
    }

    /**
     * Start listening for speech input (STT).
     */
    fun startListening() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition not available")
                onError?.invoke("Speech recognition not available")
                return@post
            }

        setState(State.LISTENING)

        try {
            // Clean up previous recognizer
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val bestResult = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "STT result: \"$bestResult\"")
                    
                    if (bestResult.isNotBlank()) {
                        processMessage(bestResult)
                    } else {
                        // Empty result - retry a few times then retire
                        retryListening("Empty STT result")
                    }
                }

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "no_match"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
                        SpeechRecognizer.ERROR_AUDIO -> "audio_error"
                        else -> "error_$error"
                    }
                    Log.d(TAG, "STT error: $errorMessage")
                    
                    // On timeout or no match, retire gracefully
                    if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || 
                        error == SpeechRecognizer.ERROR_NO_MATCH) {
                        retireGracefully()
                    } else {
                        // Real error - keep listening until retry limit
                        retryListening("STT error: $errorMessage")
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "STT ready for speech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "STT speech started")
                }
                override fun onRmsChanged(rmsdB: Float) { /* waveform level */ }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d(TAG, "STT speech ended")
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    partial?.firstOrNull()?.let { text ->
                        onTranscription?.invoke(text)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }

            speechRecognizer?.startListening(intent)
            Log.d(TAG, "STT listening started (retry count: $listeningRetryCount)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            setState(State.IDLE)
        }
        } // end of main thread post
    }

    private fun retryListening(reason: String) {
        listeningRetryCount++
        Log.d(TAG, "Retrying listening (count: $listeningRetryCount/$MAX_LISTENING_RETRIES). Reason: $reason")
        
        if (listeningRetryCount < MAX_LISTENING_RETRIES) {
            // Wait a short moment before retrying to avoid rapid cycles
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (state == State.LISTENING) {
                    startListening()
                }
            }, 500)
        } else {
            Log.w(TAG, "Max listening retries reached. Retiring.")
            retireGracefully()
        }
    }

    private fun retireGracefully() {
        val farewell = personality.getFarewell()
        setState(State.SPEAKING)
        voiceManager.speak(farewell) {
            stopConversation()
        }
    }

    /**
     * Stop the current conversation and return to idle.
     */
    fun stopConversation() {
        Log.d(TAG, "Stopping conversation")
        currentJob?.cancel()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying speech recognizer", e)
            }
        }
        voiceManager.stop()
        currentScreenshot = null
        setState(State.IDLE)
    }

    /**
     * Update the screenshot (e.g., after an action was performed).
     */
    fun updateScreenshot(screenshot: Bitmap?) {
        currentScreenshot = screenshot
    }

    /**
     * Call Gemini API with the user message and optional screenshot.
     * Uses generateAssistanceSuggestion for conversational responses.
     */
    private suspend fun callGemini(message: String, screenshot: Bitmap?): String? {
        if (apiKey.isEmpty()) {
            Log.w(TAG, "No API key set")
            return null
        }

        val service = geminiApiService ?: return null

        return withContext(Dispatchers.IO) {
            try {
                // Build context from conversation history
                val historyContext = if (conversationHistory.size > 1) {
                    conversationHistory.dropLast(1).joinToString("\n") { (role, content) ->
                        if (role == "user") "User: $content" else "J-AI-mes: $content"
                    }
                } else ""

                val result = service.generateAssistanceSuggestion(
                    userRequest = message,
                    screenshot = screenshot,
                    context = historyContext
                )

                result.getOrNull()
            } catch (e: Exception) {
                Log.e(TAG, "Gemini API call failed", e)
                null
            }
        }
    }

    private fun setState(newState: State) {
        Log.d(TAG, "State: $state → $newState")
        state = newState
        onStateChanged?.invoke(newState)
    }
}
