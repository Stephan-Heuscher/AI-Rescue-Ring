package ch.heuscher.airescuering.service.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.heuscher.airescuering.R
import ch.heuscher.airescuering.data.api.GeminiApiService
import ch.heuscher.airescuering.data.local.ChatPersistenceManager
import ch.heuscher.airescuering.domain.model.AIMessage
import ch.heuscher.airescuering.domain.model.ChatSession
import ch.heuscher.airescuering.domain.model.MessageRole
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.Locale

/**
 * Manages the floating chat overlay window.
 * Handles chat UI, message display, user input, and AI interactions.
 */
class ChatOverlayManager(
    private val context: Context,
    private val geminiApiKey: String,
    private val scope: CoroutineScope,
    private val aiHelperRepository: ch.heuscher.airescuering.domain.repository.AIHelperRepository? = null
) {
    companion object {
        private const val TAG = "ChatOverlayManager"
        private const val VOICE_RECOGNITION_REQUEST_CODE = 2001
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var chatOverlayView: View? = null
    private var isVisible = false

    // UI Components
    private var messagesRecyclerView: RecyclerView? = null
    private var messageInput: EditText? = null
    private var sendButton: ImageButton? = null
    private var voiceButton: ImageButton? = null
    private var screenshotButton: ImageButton? = null
    private var hideButton: ImageButton? = null
    private var loadingIndicator: ProgressBar? = null
    private var screenshotPreviewContainer: View? = null
    private var screenshotPreviewImage: android.widget.ImageView? = null
    private var deleteScreenshotButton: ImageButton? = null
    private var stepForwardButton: Button? = null
    private var stepBackwardButton: Button? = null
    private var stepIndicator: TextView? = null
    private var stepTitle: TextView? = null
    private var stepsContainer: View? = null
    private var newChatButton: ImageButton? = null
    private var chatHistorySpinner: Spinner? = null

    // Chat state
    private val messages = mutableListOf<AIMessage>()
    private var adapter: ChatOverlayAdapter? = null
    private var geminiService: GeminiApiService? = null
    private var currentScreenshotBitmap: Bitmap? = null
    private var pendingScreenshot: Bitmap? = null
    private var markwon: Markwon? = null

    // Chat session management
    private var currentSession: ChatSession? = null
    private var chatSessions = mutableListOf<ChatSession>()
    private val persistenceManager = ChatPersistenceManager(context)

    // Step navigation state (legacy inline - now using PiP)
    private var currentSteps = listOf<String>()
    private var currentStepIndex = 0

    // PiP step window
    private var stepPipManager: StepPipManager? = null

    // Inactivity timer for auto-new-chat
    private var inactivityHandler: Handler? = null
    private var inactivityRunnable: Runnable? = null
    private val INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

    // Voice-first mode properties
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceFirstMode = false
    private var autoSpeakResponses = false

    // Callbacks
    var onHideRequest: (() -> Unit)? = null
    var onScreenshotRequest: (() -> Unit)? = null
    var onVoiceInputRequest: (() -> Unit)? = null
    var onShowIndicator: ((Int, Int) -> Unit)? = null
    var onHideIndicator: (() -> Unit)? = null



    /**
     * Update the API key securely.
     */
    fun updateApiKey(apiKey: String) {
        geminiService = GeminiApiService(apiKey, debug = true)
        Log.d(TAG, "API key updated")
    }

    init {
        geminiService = GeminiApiService(geminiApiKey, debug = true)
        stepPipManager = StepPipManager(context)
        stepPipManager?.onClose = {
            // When PiP is closed, clear the steps
            currentSteps = listOf()
            currentStepIndex = 0
            // Hide indicator when closed
            onHideIndicator?.invoke()
        }
        stepPipManager?.onShowIndicator = { x, y ->
            onShowIndicator?.invoke(x, y)
        }
        stepPipManager?.onHideIndicator = {
            onHideIndicator?.invoke()
        }
        stepPipManager?.onStepComplete = { stepIndex ->
            // Step completed - speak brief confirmation and move to next
            if (stepIndex + 1 < currentSteps.size) {
                // There's a next step - speak it
                val nextStep = currentSteps[stepIndex + 1]
                val cleanText = cleanMarkdownForSpeech(nextStep)
                speakText("Step ${stepIndex + 2}. $cleanText")
            } else {
                // Last step completed
                speakText("All steps completed. Great job!")
            }
        }

        // Initialize inactivity handler
        inactivityHandler = Handler(Looper.getMainLooper())
        inactivityRunnable = Runnable {
            Log.d(TAG, "Inactivity timeout reached, starting new chat")
            if (messages.isNotEmpty() && messages.size > 1) {
                startNewChat()
            }
        }

        // Initialize TextToSpeech for voice-first mode
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(0.9f) // Slower speech for elderly users
                Log.d(TAG, "TextToSpeech initialized successfully")
            } else {
                Log.e(TAG, "TextToSpeech initialization failed")
                ttsReady = false
            }
        }

        // Load voice-first settings from repository
        loadVoiceFirstSettings()

        // Load chat history
        loadChatHistory()
    }

    /**
     * Load voice-first mode settings from repository
     */
    private fun loadVoiceFirstSettings() {
        aiHelperRepository?.let { repo ->
            scope.launch {
                repo.isVoiceFirstMode().collect { voiceFirstMode = it }
            }
            scope.launch {
                repo.isAutoSpeakResponses().collect { autoSpeakResponses = it }
            }
        }
    }

    /**
     * Create and show the chat overlay
     */
    fun show() {
        if (isVisible) {
            Log.d(TAG, "Chat overlay already visible")
            return
        }

        // Check for inactivity timeout - start new chat if needed
        checkAndResetForInactivity()

        try {
            Log.d(TAG, "=== show: Creating chat overlay ===")

            // Inflate the chat overlay layout
            val inflater = LayoutInflater.from(context)
            chatOverlayView = inflater.inflate(R.layout.chat_overlay_layout, null)

            // Get screen dimensions
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val maxHeight = (screenHeight * 0.5).toInt()

            Log.d(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}px")
            Log.d(TAG, "Max overlay height: ${maxHeight}px (50% of screen)")

            // Set up the window layout parameters with dynamic height
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, // Allow interaction with outside window
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM  // Position at bottom by default
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN // Pan for keyboard visibility
            }

            Log.d(TAG, "WindowManager params: width=${params.width}, height=${params.height}, gravity=${params.gravity}")

            // Add view to window manager
            windowManager.addView(chatOverlayView, params)
            isVisible = true

            // Initialize UI components
            initializeViews()
            
            // Set max height for RecyclerView to prevent overlay from taking too much space
            messagesRecyclerView?.let { recyclerView ->
                val layoutParams = recyclerView.layoutParams
                // We can't set maxHeight directly on LayoutParams, but we can use a constraint
                // or just rely on the fact that WRAP_CONTENT will grow.
                // To strictly limit it, we would need a custom view or ConstraintLayout.
                // For now, let's set a fixed height if it gets too big, or just let it be.
                // A better approach is to set the height of the RecyclerView to a specific value
                // when it has content, or use a custom MaxHeightRecyclerView.
                
                // Let's try to set a max height on the root view if possible, 
                // but WindowManager handles the root.
                // We can set a max height on the RecyclerView programmatically.
                recyclerView.viewTreeObserver.addOnGlobalLayoutListener {
                    if (recyclerView.height > maxHeight) {
                        val params = recyclerView.layoutParams
                        params.height = maxHeight
                        recyclerView.layoutParams = params
                    }
                }
            }

            setupListeners()
            setupRecyclerView()

            // Don't add welcome here - it's handled in loadChatHistory() and startNewChat()

            // Process any pending screenshot now that views are initialized
            pendingScreenshot?.let { bitmap ->
                Log.d(TAG, "Processing pending screenshot after show()")
                processScreenshotInternal(bitmap)
                pendingScreenshot = null
            }

            // Voice-first greeting if enabled
            if (voiceFirstMode && ttsReady && messages.size <= 1) {
                val greeting = "Hi! I'm your AI rescue assistant. How can I help you today?"
                speakText(greeting)
            }

            Log.d(TAG, "=== show: Chat overlay shown successfully ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing chat overlay", e)
            Toast.makeText(context, "Error showing chat: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Hide the chat overlay
     */
    fun hide() {
        if (!isVisible) {
            Log.d(TAG, "Chat overlay already hidden")
            return
        }

        try {
            chatOverlayView?.let {
                windowManager.removeView(it)
                chatOverlayView = null
            }
            isVisible = false

            // Clean up
            messagesRecyclerView = null
            messageInput = null
            sendButton = null
            voiceButton = null
            screenshotButton = null
            hideButton = null
            loadingIndicator = null
            screenshotPreviewContainer = null
            screenshotPreviewImage = null
            deleteScreenshotButton = null
            adapter = null

            Log.d(TAG, "Chat overlay hidden successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding chat overlay", e)
        }
    }

    /**
     * Toggle visibility
     */
    fun toggle() {
        if (isVisible) {
            hide()
        } else {
            show()
        }
    }

    /**
     * Check if chat is visible
     */
    fun isShowing(): Boolean = isVisible

    /**
     * Initialize UI components
     */
    private fun initializeViews() {
        chatOverlayView?.let { view ->
            messagesRecyclerView = view.findViewById(R.id.chatMessagesRecyclerView)
            messageInput = view.findViewById(R.id.chatMessageInput)
            sendButton = view.findViewById(R.id.chatSendButton)
            voiceButton = view.findViewById(R.id.chatVoiceButton)
            screenshotButton = view.findViewById(R.id.chatScreenshotButton)
            hideButton = view.findViewById(R.id.hideButton)
            loadingIndicator = view.findViewById(R.id.chatLoadingIndicator)
            screenshotPreviewContainer = view.findViewById(R.id.screenshotPreviewContainer)
            screenshotPreviewImage = view.findViewById(R.id.screenshotPreviewImage)
            deleteScreenshotButton = view.findViewById(R.id.deleteScreenshotButton)
            stepForwardButton = view.findViewById(R.id.stepForwardButton)
            stepBackwardButton = view.findViewById(R.id.stepBackwardButton)
            stepIndicator = view.findViewById(R.id.stepIndicator)
            stepTitle = view.findViewById(R.id.stepTitle)
            stepsContainer = view.findViewById(R.id.stepsContainer)
            newChatButton = view.findViewById(R.id.newChatButton)
            chatHistorySpinner = view.findViewById(R.id.chatHistorySpinner)

            // Initialize Markwon for markdown rendering
            markwon = Markwon.create(context)

            // Setup chat history spinner
            setupChatHistorySpinner()
        }
    }

    /**
     * Set up button listeners
     */
    private fun setupListeners() {
        hideButton?.setOnClickListener {
            onHideRequest?.invoke()
        }

        newChatButton?.setOnClickListener {
            startNewChat()
        }

        stepForwardButton?.setOnClickListener {
            if (currentStepIndex < currentSteps.size - 1) {
                currentStepIndex++
                updateStepUI()
            }
        }

        stepBackwardButton?.setOnClickListener {
            if (currentStepIndex > 0) {
                currentStepIndex--
                updateStepUI()
            }
        }

        sendButton?.setOnClickListener {
            val text = messageInput?.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                sendMessage(text)
                messageInput?.text?.clear()
            }
        }

        // Send on Enter
        messageInput?.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                val text = messageInput?.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    sendMessage(text)
                    messageInput?.text?.clear()
                }
                true
            } else {
                false
            }
        }

        voiceButton?.setOnClickListener {
            // Note: Voice input via overlay is not supported on Android
            // (overlays cannot access RecognizerIntent). Users can enable
            // voice via accessibility service or settings.
            Toast.makeText(context, "Voice input is available via accessibility service", Toast.LENGTH_SHORT).show()
        }

        screenshotButton?.setOnClickListener {
            onScreenshotRequest?.invoke()
            // The screenshot will be taken by the service and passed back via processScreenshot()
        }

        deleteScreenshotButton?.setOnClickListener {
            clearScreenshot()
        }
    }

    /**
     * Set up RecyclerView for messages
     */
    private fun setupRecyclerView() {
        messagesRecyclerView?.let { recyclerView ->
            adapter = ChatOverlayAdapter(messages)
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
        }
    }

    /**
     * Add welcome message for new chat sessions
     */
    private fun addWelcomeMessage() {
        // Only add if truly empty (no messages at all)
        if (messages.isNotEmpty()) return
        
        val welcomeMessage = AIMessage(
            id = UUID.randomUUID().toString(),
            content = "👋 Hi! I'm your AI rescue assistant. How can I help you today?",
            role = MessageRole.ASSISTANT
        )
        addMessage(welcomeMessage)
    }

    /**
     * Send a message to the AI
     */
    private fun sendMessage(text: String, screenshotBase64: String? = null) {
        // Reset inactivity timer on user interaction
        resetInactivityTimer()

        // Reset steps for new request
        Handler(Looper.getMainLooper()).post {
            currentSteps = emptyList()
            updateStepUI()
        }

        // Check if we have a current screenshot to include
        val screenshot = screenshotBase64 ?: currentScreenshotBitmap?.let { bitmapToBase64(it) }

        // Add user message
        val userMessage = AIMessage(
            id = UUID.randomUUID().toString(),
            content = text,
            role = MessageRole.USER
        )
        addMessage(userMessage)

        // Clear screenshot after sending
        if (screenshot != null) {
            clearScreenshot()
        }

        // Show loading
        loadingIndicator?.visibility = View.VISIBLE
        sendButton?.isEnabled = false
        voiceButton?.isEnabled = false
        screenshotButton?.isEnabled = false

        // Get AI response
        scope.launch {
            try {
                val service = geminiService
                if (service == null) {
                    showError("AI service not available")
                    return@launch
                }

                val result = if (screenshot != null) {
                    // Send with screenshot
                    service.generateAssistanceWithImage(
                        userRequest = text,
                        imageBase64 = screenshot,
                        context = "The user needs help with their Android device. Guide them step-by-step."
                    )
                } else {
                    // Send text only
                    service.generateAssistanceSuggestion(
                        userRequest = text,
                        context = "The user needs help with their Android device. Guide them step-by-step."
                    )
                }

                result.onSuccess { response ->
                    Log.d(TAG, "=== RAW RESPONSE ===")
                    Log.d(TAG, response)
                    Log.d(TAG, "=== END RAW RESPONSE ===")
                    
                    // Parse steps if present and show in PiP window
                    // Steps are marked with "###" headers (with or without space after)
                    // The LLM may use various formats:
                    // - ### Step 1: Title
                    // - ###Step 1: Title  
                    // - ### 1. Title
                    // - ### First Step
                    var hasSteps = false
                    if (response.contains("###")) {
                        // Use regex to find all ### headers and extract each step
                        // This handles cases where there's intro text before the first ###
                        val stepHeaderPattern = Regex("""###\s*""")
                        
                        // Find all positions where ### occurs
                        val matches = stepHeaderPattern.findAll(response).toList()
                        Log.d(TAG, "Found ${matches.size} ### headers in response")
                        
                        val steps = mutableListOf<String>()
                        
                        for (i in matches.indices) {
                            val startPos = matches[i].range.first
                            val endPos = if (i + 1 < matches.size) {
                                matches[i + 1].range.first
                            } else {
                                response.length
                            }
                            
                            val stepText = response.substring(startPos, endPos).trim()
                            if (stepText.isNotBlank()) {
                                steps.add(stepText)
                                Log.d(TAG, "Extracted step $i: '${stepText.take(80)}...'")
                            }
                        }

                        Log.d(TAG, "Parsed ${steps.size} valid steps from response")
                        steps.forEachIndexed { index, step -> 
                            Log.d(TAG, "Final step $index: ${step.take(100)}...")
                        }

                        if (steps.isNotEmpty()) {
                            hasSteps = true
                            Handler(Looper.getMainLooper()).post {
                                currentSteps = steps
                                currentStepIndex = 0
                                // Show steps in PiP window and hide chat
                                stepPipManager?.show(steps, 0)
                                hide()
                                
                                // Speak the first step
                                if (autoSpeakResponses && ttsReady) {
                                    val firstStep = steps[0]
                                    val cleanText = cleanMarkdownForSpeech(firstStep)
                                    speakText("Step 1. $cleanText")
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "No ### found in response")
                    }

                    val assistantMessage = AIMessage(
                        id = UUID.randomUUID().toString(),
                        content = response,
                        role = MessageRole.ASSISTANT
                    )
                    // Don't auto-speak if we have steps (steps are spoken individually)
                    addMessage(assistantMessage, scrollToEnd = false, skipSpeaking = hasSteps)
                }.onFailure { error ->
                    showError("Error: ${error.message}")

                    val errorMessage = AIMessage(
                        id = UUID.randomUUID().toString(),
                        content = "Sorry, I encountered an error: ${error.message}\n\nPlease try again or ask a different question.",
                        role = MessageRole.ASSISTANT
                    )
                    addMessage(errorMessage)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    loadingIndicator?.visibility = View.GONE
                    sendButton?.isEnabled = true
                    voiceButton?.isEnabled = true
                    screenshotButton?.isEnabled = true
                }
            }
        }
    }

    /**
     * Process a screenshot and display it as a deletable preview
     */
    fun processScreenshot(bitmap: Bitmap) {
        if (screenshotPreviewContainer == null || screenshotPreviewImage == null) {
            // Views not initialized yet, store as pending
            Log.d(TAG, "Views not initialized, storing screenshot as pending")
            pendingScreenshot = bitmap
            return
        }

        processScreenshotInternal(bitmap)
    }

    /**
     * Internal method to process screenshot when views are guaranteed to be initialized
     */
    private fun processScreenshotInternal(bitmap: Bitmap) {
        Log.d(TAG, "Processing screenshot: ${bitmap.width}x${bitmap.height}")
        currentScreenshotBitmap = bitmap

        // Show message that screenshot was captured
        Handler(Looper.getMainLooper()).post {
            // Display screenshot preview
            screenshotPreviewImage?.setImageBitmap(bitmap)
            screenshotPreviewContainer?.visibility = View.VISIBLE

            Log.d(TAG, "Screenshot preview displayed, container visible: ${screenshotPreviewContainer?.visibility == View.VISIBLE}")
            Log.d(TAG, "Screenshot image set: ${screenshotPreviewImage?.drawable != null}")

            Toast.makeText(context, "Screenshot captured! It will be sent with your next message.", Toast.LENGTH_SHORT).show()

            // Focus the message input so user can type
            messageInput?.requestFocus()
        }
    }

    /**
     * Clear the current screenshot preview
     */
    private fun clearScreenshot() {
        Handler(Looper.getMainLooper()).post {
            currentScreenshotBitmap?.recycle()
            currentScreenshotBitmap = null
            screenshotPreviewContainer?.visibility = View.GONE
            screenshotPreviewImage?.setImageBitmap(null)
            Toast.makeText(context, "Screenshot removed", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Convert bitmap to base64 string
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Add a message to the chat
     */
    private fun addMessage(message: AIMessage, scrollToEnd: Boolean = true, skipSpeaking: Boolean = false) {
        Handler(Looper.getMainLooper()).post {
            messages.add(message)
            adapter?.notifyItemInserted(messages.size - 1)
            if (scrollToEnd) {
                messagesRecyclerView?.scrollToPosition(messages.size - 1)
            }

            // Auto-speak assistant responses if enabled
            if (!skipSpeaking && autoSpeakResponses && message.role == MessageRole.ASSISTANT && ttsReady) {
                // Clean markdown from response before speaking
                val cleanText = cleanMarkdownForSpeech(message.content)
                speakText(cleanText)
            }

            // Auto-save session
            saveCurrentSession()
        }
    }

    /**
     * Clean markdown from text for TTS
     */
    private fun cleanMarkdownForSpeech(text: String): String {
        return text
            .replace(Regex("#+\\s+"), "") // Remove heading markers
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1") // Remove bold
            .replace(Regex("\\*(.+?)\\*"), "$1") // Remove italic
            .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1") // Remove links
            .replace(Regex("`(.+?)`"), "$1") // Remove code blocks
            .replace(Regex("###.+"), "") // Remove step headers
            .trim()
    }

    /**
     * Process voice input results from speech recognition
     */
    fun processVoiceInput(text: String) {
        if (text.isNotEmpty()) {
            messageInput?.setText(text)
            // Auto-send the voice input
            Handler(Looper.getMainLooper()).post {
                sendMessage(text)
                messageInput?.text?.clear()
            }
        }
    }

    /**
     * Speak text using TextToSpeech
     */
    private fun speakText(text: String) {
        if (!ttsReady || tts == null) {
            Log.w(TAG, "TTS not ready, cannot speak")
            return
        }

        if (text.isBlank()) {
            Log.w(TAG, "Cannot speak empty text")
            return
        }

        try {
            // Limit text length for TTS (Android has limits)
            val textToSpeak = if (text.length > 500) {
                text.take(500) + "..."
            } else {
                text
            }

            Log.d(TAG, "Speaking: ${textToSpeak.take(100)}...")
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text", e)
        }
    }

    /**
     * Show an error toast
     */
    private fun showError(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Update the window position (Top or Bottom)
     */

    /**
     * Update the step navigation UI
     */
    private fun updateStepUI() {
        if (currentSteps.isEmpty()) {
            stepsContainer?.visibility = View.GONE
            return
        }

        stepsContainer?.visibility = View.VISIBLE

        // Update step indicator
        stepIndicator?.text = "Step ${currentStepIndex + 1} of ${currentSteps.size}"

        // Parse step to extract title and content
        val stepText = currentSteps[currentStepIndex].trim()
        val lines = stepText.lines()

        // Extract title (first line, often a heading)
        var title = ""
        var contentStartIndex = 0

        if (lines.isNotEmpty()) {
            val firstLine = lines[0].trim()
            // Check if first line is a markdown heading
            if (firstLine.startsWith("#")) {
                title = firstLine.replace(Regex("^#+\\s*"), "") // Remove heading markers
                contentStartIndex = 1
            } else {
                // Use first line as title if it's short, otherwise use generic title
                title = if (firstLine.length <= 50) firstLine else "Step ${currentStepIndex + 1}"
                contentStartIndex = if (firstLine.length <= 50) 1 else 0
            }
        }

        // Get content (everything after title)
        val content = lines.drop(contentStartIndex).joinToString("\n").trim()

        // Update step title
        stepTitle?.text = title

        // Update step text with markdown rendering
        val currentStepTextView = chatOverlayView?.findViewById<android.widget.TextView>(R.id.currentStepText)
        if (content.isNotEmpty()) {
            markwon?.setMarkdown(currentStepTextView!!, content)
        } else {
            currentStepTextView?.text = ""
        }

        // Update buttons
        stepBackwardButton?.isEnabled = currentStepIndex > 0
        stepForwardButton?.isEnabled = currentStepIndex < currentSteps.size - 1
    }

    /**
     * Load chat history from persistence
     */
    private fun loadChatHistory() {
        chatSessions.clear()
        chatSessions.addAll(persistenceManager.loadAllSessions())

        // Load current session or create new one
        val currentSessionId = persistenceManager.loadCurrentSessionId()
        currentSession = if (currentSessionId != null) {
            chatSessions.find { it.id == currentSessionId }
        } else {
            null
        }

        // If no current session, create a new one
        if (currentSession == null) {
            currentSession = ChatSession()
            chatSessions.add(0, currentSession!!)
            persistenceManager.saveCurrentSessionId(currentSession!!.id)
        }

        // Load messages from current session
        messages.clear()
        messages.addAll(currentSession!!.messages)

        // Add welcome message only if this is a truly empty new session
        if (messages.isEmpty()) {
            val welcomeMessage = AIMessage(
                id = UUID.randomUUID().toString(),
                content = "👋 Hi! I'm your AI rescue assistant. How can I help you today?",
                role = MessageRole.ASSISTANT
            )
            messages.add(welcomeMessage)
        }

        Log.d(TAG, "Loaded ${chatSessions.size} sessions, current: ${currentSession?.id}")
    }

    /**
     * Setup the chat history spinner
     */
    private fun setupChatHistorySpinner() {
        val displayNames = chatSessions.map { it.getDisplayName() }
        val adapter = ArrayAdapter(context, R.layout.spinner_item_white, displayNames)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)

        chatHistorySpinner?.adapter = adapter

        // Set current session as selected
        val currentIndex = chatSessions.indexOfFirst { it.id == currentSession?.id }
        if (currentIndex >= 0) {
            chatHistorySpinner?.setSelection(currentIndex)
        }

        // Handle session selection
        chatHistorySpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSession = chatSessions[position]
                if (selectedSession.id != currentSession?.id) {
                    switchToSession(selectedSession)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    /**
     * Start a new chat session
     */
    private fun startNewChat() {
        // Save current session
        saveCurrentSession()

        // Create new session
        val newSession = ChatSession()
        currentSession = newSession
        chatSessions.add(0, newSession)
        persistenceManager.saveCurrentSessionId(newSession.id)

        // Clear messages and UI
        messages.clear()
        adapter?.notifyDataSetChanged()

        // Update spinner
        setupChatHistorySpinner()

        // Add welcome message
        addWelcomeMessage()

        Log.d(TAG, "Started new chat session: ${newSession.id}")
        Toast.makeText(context, "New chat started", Toast.LENGTH_SHORT).show()
    }

    /**
     * Switch to a different chat session
     */
    private fun switchToSession(session: ChatSession) {
        // Save current session
        saveCurrentSession()

        // Switch to new session
        currentSession = session
        persistenceManager.saveCurrentSessionId(session.id)

        // Load messages
        messages.clear()
        messages.addAll(session.messages)
        adapter?.notifyDataSetChanged()

        // Scroll to bottom
        messagesRecyclerView?.scrollToPosition(messages.size - 1)

        Log.d(TAG, "Switched to session: ${session.id} with ${messages.size} messages")
    }

    /**
     * Save the current session to persistence
     */
    private fun saveCurrentSession() {
        currentSession?.let { session ->
            // Update session messages
            session.messages.clear()
            session.messages.addAll(messages)

            // Save to persistence
            persistenceManager.saveSession(session)

            Log.d(TAG, "Saved session ${session.id} with ${messages.size} messages")
        }
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        // Save current session before cleanup
        saveCurrentSession()

        // Cancel inactivity timer
        inactivityHandler?.removeCallbacks(inactivityRunnable!!)
        inactivityHandler = null
        inactivityRunnable = null

        // Shutdown TextToSpeech
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false

        hide()
        stepPipManager?.destroy()
        stepPipManager = null
        currentScreenshotBitmap?.recycle()
        currentScreenshotBitmap = null
    }

    /**
     * Reset the inactivity timer - call this when user interacts with the chat
     */
    private fun resetInactivityTimer() {
        inactivityHandler?.removeCallbacks(inactivityRunnable!!)
        inactivityHandler?.postDelayed(inactivityRunnable!!, INACTIVITY_TIMEOUT_MS)
        Log.d(TAG, "Inactivity timer reset - will trigger in ${INACTIVITY_TIMEOUT_MS / 1000 / 60} minutes")
    }

    /**
     * Check if inactivity timer has expired and reset chat if needed
     */
    private fun checkAndResetForInactivity() {
        // If we have messages and last interaction was more than 5 minutes ago, start new chat
        currentSession?.let { session ->
            val lastMessageTime = session.messages.lastOrNull()?.timestamp ?: session.startTime
            val timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime
            if (timeSinceLastMessage > INACTIVITY_TIMEOUT_MS && session.messages.size > 1) {
                Log.d(TAG, "Last message was ${timeSinceLastMessage / 1000 / 60} minutes ago, starting new chat")
                startNewChat()
            }
        }
        // Reset timer for new interaction
        resetInactivityTimer()
    }
}
