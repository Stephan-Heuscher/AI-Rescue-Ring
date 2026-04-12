package ch.heuscher.airescuering.service.voice

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import ch.heuscher.airescuering.data.api.*
import ch.heuscher.airescuering.service.intent.IntentExecutionAgent
import ch.heuscher.airescuering.service.computeruse.ComputerUseAgent
import ch.heuscher.airescuering.service.screencapture.ScreenCaptureManager
import ch.heuscher.airescuering.domain.repository.AIHelperRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Central engine managing J-AI-mes' conversation loop:
 */
class VoiceConversationEngine(
    private val context: Context,
    private val voiceManager: ButlerVoiceManager,
    private val personality: ButlerPersonality,
    private val aiHelperRepository: AIHelperRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "VoiceConversationEngine"
        private const val MAX_LISTENING_RETRIES = 10
        private const val MODEL_NAME = "gemini-3.1-flash-lite-preview"
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
    var onRequireConfirmation: ((String, Intent) -> Unit)? = null

    // API service for Gemini calls
    private var geminiApiService: GeminiApiService? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentScreenshot: Bitmap? = null
    private var conversationHistory: MutableList<Pair<String, String>> = mutableListOf() // role, content
    private var currentJob: Job? = null
    private var apiKey: String = ""
    private var listeningRetryCount: Int = 0
    private var apiRetryCount: Int = 0
    
    private val intentAgent = IntentExecutionAgent(context, aiHelperRepository)

    /**
     * Set the API key and/or proxy URL for Gemini. 
     */
    fun setApiConfig(key: String, proxyUrl: String = "") {
        apiKey = key
        val service = GeminiApiService(apiKey = key, proxyUrl = proxyUrl)
        geminiApiService = service
    }

    /**
     * Start a new conversation. Called when user taps the butler button.
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

        // Speak greeting
        onResponse?.invoke(greeting)
        voiceManager.speak(greeting) {
            startListening()
        }
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
                    apiRetryCount = 0 // Reset on success
                    
                    withContext(Dispatchers.Main) {
                        setState(State.SPEAKING)
                        onResponse?.invoke(response) // Keep original for UI markdown rendering
                        
                        val ttsResponse = stripMarkdownForTTS(response)
                        Log.d(TAG, "Stripped response for TTS: $ttsResponse")
                        
                        voiceManager.speak(ttsResponse) {
                            startListening()
                        }
                    }
                } else {
                    apiRetryCount++
                    withContext(Dispatchers.Main) {
                        val error = personality.getErrorMessage()
                        onError?.invoke(error)
                        if (apiRetryCount >= 3) {
                            retireGracefully()
                        } else {
                            voiceManager.speak(error) {
                                startListening()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
                apiRetryCount++
                withContext(Dispatchers.Main) {
                    val error = personality.getErrorMessage()
                    onError?.invoke(error)
                    if (apiRetryCount >= 3) {
                        retireGracefully()
                    } else {
                        voiceManager.speak(error) {
                            startListening()
                        }
                    }
                }
            }
        }
    }

    /**
     * Strips Markdown characters (like #, *, _) to prevent TTS from reading them aloud
     * (e.g., reading "###" as "hash hash hash").
     */
    private fun stripMarkdownForTTS(text: String): String {
        return text
            // Remove markdown links but keep the text: [text](url) -> text
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            // Remove bold/italic markers
            .replace(Regex("[*_]{1,3}([^*_]+)[*_]{1,3}"), "$1")
            // Remove markdown headings (e.g. ### Heading -> Heading)
            .replace(Regex("^(#{1,6})\\s+", RegexOption.MULTILINE), "")
            // Remove backticks for inline code
            .replace("`", "")
            // Clean up any remaining isolated markdown symbols if necessary, but carefully
            .replace(Regex("\\s+"), " ") // normalize spacing
            .trim()
    }

    /**
     * Handle voice commands.
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
            
            val confirmation = if (isGerman) "Selbstverständlich. Ist dieses Tempo besser?" else "Of course. Is this pace better?"
            
            setState(State.SPEAKING)
            voiceManager.speak(confirmation) { startListening() }
            onResponse?.invoke(confirmation)
            return true
        }

        if (fasterPatterns.any { lower.contains(it) }) {
            val newRate = (voiceManager.getSpeechRate() + 0.1f)
                .coerceIn(ButlerVoiceManager.SPEECH_RATE_MIN, ButlerVoiceManager.SPEECH_RATE_MAX)
            voiceManager.setSpeechRate(newRate)
            
            val confirmation = if (isGerman) "Selbstverständlich. Ist dieses Tempo besser?" else "Of course. Is this pace better?"
            
            setState(State.SPEAKING)
            voiceManager.speak(confirmation) { startListening() }
            onResponse?.invoke(confirmation)
            return true
        }

        // Dismissal commands
        val dismissPatterns = if (isGerman) {
            listOf("danke", "nein danke", "das wars", "tschüss", "nichts weiter", "vielen dank")
        } else {
            listOf("thank you", "thankyou", "thanks", "no thanks", "that's all", "goodbye", "nothing else", "i'm good", "im good")
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
                        retryListening("Empty STT result")
                    }
                }

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "no_match"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
                        SpeechRecognizer.ERROR_AUDIO -> "audio_error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "insufficient_permissions"
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "server_disconnected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer_busy"
                        else -> "error_$error"
                    }
                    Log.e(TAG, "STT error occurred: $errorMessage (code: $error)")

                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        val msg = if (Locale.getDefault().language == "de") {
                            "Ich benötige die Berechtigung für das Mikrofon, um Sie zu hören."
                        } else {
                            "I need microphone permission to hear you."
                        }
                        setState(State.SPEAKING)
                        onResponse?.invoke(msg)
                        voiceManager.speak(msg) {
                            stopConversation()
                        }
                        return
                    }
                    
                    if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || listeningRetryCount >= 1) {
                            Log.d(TAG, "User didn't speak or too much noise. Retiring gracefully.")
                            retireGracefully()
                            return
                        }
                    }

                    retryListening("STT error: $errorMessage")
                }

                override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "STT ready") }
                override fun onBeginningOfSpeech() { Log.d(TAG, "STT started") }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { Log.d(TAG, "STT ended") }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    partial?.firstOrNull()?.let { onTranscription?.invoke(it) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }

            speechRecognizer?.startListening(intent)
            Log.d(TAG, "STT listening started (retry $listeningRetryCount)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting STT", e)
            setState(State.IDLE)
        }
        }
    }

    private fun retryListening(reason: String) {
        listeningRetryCount++
        Log.d(TAG, "Retrying ($listeningRetryCount/$MAX_LISTENING_RETRIES). Reason: $reason")
        
        if (listeningRetryCount < MAX_LISTENING_RETRIES) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (state == State.LISTENING) {
                    startListening()
                }
            }, 1000)
        } else {
            retireGracefully()
        }
    }

    private fun retireGracefully() {
        val farewell = personality.getFarewell()
        setState(State.SPEAKING)
        onResponse?.invoke(farewell)
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

    fun updateScreenshot(screenshot: Bitmap?) {
        currentScreenshot = screenshot
    }

    private suspend fun callGemini(message: String, screenshot: Bitmap?): String? {
        if (apiKey.isEmpty()) return null
        val service = geminiApiService ?: return null
        
        return withContext(Dispatchers.IO) {
            try {
                // Determine model based on whether we have a screenshot or need computer use
                // For now, always use the main model name
                val model = MODEL_NAME
                
                val messages = mutableListOf<Pair<String, String>>()
                
                // Add history context
                if (conversationHistory.size > 1) {
                    conversationHistory.dropLast(1).forEach { (role, content) ->
                        messages.add(role to content)
                    }
                }
                
                // Add current message
                messages.add("user" to message)
                
                val result = service.generateContentWithTools(
                    model = model,
                    messages = messages,
                    systemPrompt = personality.getSystemPrompt(),
                    functionDeclarations = intentAgent.getIntentTools(),
                    screenshot = screenshot
                )
                
                val response = result.getOrNull() ?: return@withContext null
                val candidate = response.candidates.firstOrNull() ?: return@withContext null
                
                // Check for function call
                val functionCall = candidate.content.parts.firstOrNull { it.functionCall != null }?.functionCall
                if (functionCall != null) {
                    Log.d(TAG, "Gemini requested function call: ${functionCall.name}")
                    
                    // Handle Intent agent calls (Variant 2)
                    if (intentAgent.getIntentTools().any { it.name == functionCall.name }) {
                        val handled = intentAgent.executeIntent(
                            functionName = functionCall.name,
                            args = functionCall.args,
                            onRequireConfirmation = { label, intent ->
                                scope.launch(Dispatchers.Main) {
                                    onRequireConfirmation?.invoke(label, intent)
                                }
                            }
                        )
                        
                        if (handled) {
                            return@withContext if (Locale.getDefault().language == "de") {
                                "Selbstverständlich, ich führe das für Sie aus."
                            } else {
                                "Certainly, I am carrying that out for you."
                            }
                        }
                    }
                }
                
                candidate.content.parts.firstOrNull { it.text != null }?.text
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
