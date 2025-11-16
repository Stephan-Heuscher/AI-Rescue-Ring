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
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.heuscher.airescuering.R
import ch.heuscher.airescuering.data.api.GeminiApiService
import ch.heuscher.airescuering.domain.model.AIMessage
import ch.heuscher.airescuering.domain.model.MessageRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * Manages the floating chat overlay window.
 * Handles chat UI, message display, user input, and AI interactions.
 */
class ChatOverlayManager(
    private val context: Context,
    private val geminiApiKey: String,
    private val scope: CoroutineScope
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
    private var sendButton: Button? = null
    private var voiceButton: Button? = null
    private var screenshotButton: Button? = null
    private var hideButton: Button? = null
    private var loadingIndicator: ProgressBar? = null

    // Chat state
    private val messages = mutableListOf<AIMessage>()
    private var adapter: ChatOverlayAdapter? = null
    private var geminiService: GeminiApiService? = null
    private var currentScreenshotBitmap: Bitmap? = null

    // Callbacks
    var onHideRequest: (() -> Unit)? = null
    var onScreenshotRequest: (() -> Unit)? = null

    init {
        geminiService = GeminiApiService(geminiApiKey, debug = true)
    }

    /**
     * Create and show the chat overlay
     */
    fun show() {
        if (isVisible) {
            Log.d(TAG, "Chat overlay already visible")
            return
        }

        try {
            // Inflate the chat overlay layout
            val inflater = LayoutInflater.from(context)
            chatOverlayView = inflater.inflate(R.layout.chat_overlay_layout, null)

            // Set up the window layout parameters
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                // Make it focusable when shown to allow text input
                flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            }

            // Add view to window manager
            windowManager.addView(chatOverlayView, params)
            isVisible = true

            // Initialize UI components
            initializeViews()
            setupListeners()
            setupRecyclerView()

            // Add welcome message
            addWelcomeMessage()

            Log.d(TAG, "Chat overlay shown successfully")
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
        }
    }

    /**
     * Set up button listeners
     */
    private fun setupListeners() {
        hideButton?.setOnClickListener {
            onHideRequest?.invoke()
        }

        sendButton?.setOnClickListener {
            val text = messageInput?.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                sendMessage(text)
                messageInput?.text?.clear()
            }
        }

        voiceButton?.setOnClickListener {
            Toast.makeText(context, "Voice input: Please use the app directly for voice features", Toast.LENGTH_SHORT).show()
            // Note: Voice recognition requires an Activity context, so we can't use it directly in overlay
        }

        screenshotButton?.setOnClickListener {
            onScreenshotRequest?.invoke()
            // The screenshot will be taken by the service and passed back via processScreenshot()
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
     * Add welcome message
     */
    private fun addWelcomeMessage() {
        val welcomeMessage = AIMessage(
            id = UUID.randomUUID().toString(),
            content = "👋 Hello! I'm your AI rescue assistant. I'm here to help you step-by-step with any task on your device.\n\nWhat would you like to do today?",
            role = MessageRole.ASSISTANT
        )
        addMessage(welcomeMessage)
    }

    /**
     * Send a message to the AI
     */
    private fun sendMessage(text: String, screenshotBase64: String? = null) {
        // Add user message
        val userMessage = AIMessage(
            id = UUID.randomUUID().toString(),
            content = text,
            role = MessageRole.USER
        )
        addMessage(userMessage)

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

                val result = if (screenshotBase64 != null) {
                    // Send with screenshot
                    service.generateAssistanceWithImage(
                        userRequest = text,
                        imageBase64 = screenshotBase64,
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
                    val assistantMessage = AIMessage(
                        id = UUID.randomUUID().toString(),
                        content = response,
                        role = MessageRole.ASSISTANT
                    )
                    addMessage(assistantMessage)
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
     * Process a screenshot and send it with a message
     */
    fun processScreenshot(bitmap: Bitmap) {
        currentScreenshotBitmap = bitmap

        // Convert to base64
        val base64 = bitmapToBase64(bitmap)

        // Show message that screenshot was captured
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "📸 Screenshot captured! Now tell me what you need help with.", Toast.LENGTH_SHORT).show()

            // Auto-fill a message prompt
            messageInput?.setText("I need help with this screen. What should I do?")
            messageInput?.requestFocus()

            // Auto-send with screenshot
            val text = messageInput?.text?.toString()?.trim() ?: "What should I do here?"
            sendMessage(text, base64)
            messageInput?.text?.clear()
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
    private fun addMessage(message: AIMessage) {
        Handler(Looper.getMainLooper()).post {
            messages.add(message)
            adapter?.notifyItemInserted(messages.size - 1)
            messagesRecyclerView?.scrollToPosition(messages.size - 1)
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
     * Clean up resources
     */
    fun destroy() {
        hide()
        currentScreenshotBitmap?.recycle()
        currentScreenshotBitmap = null
    }
}
