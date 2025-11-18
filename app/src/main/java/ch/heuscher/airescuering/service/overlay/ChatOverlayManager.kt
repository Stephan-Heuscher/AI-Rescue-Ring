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
    private var screenshotPreviewContainer: View? = null
    private var screenshotPreviewImage: android.widget.ImageView? = null
    private var deleteScreenshotButton: Button? = null

    // Chat state
    private val messages = mutableListOf<AIMessage>()
    private var adapter: ChatOverlayAdapter? = null
    private var geminiService: GeminiApiService? = null
    private var currentScreenshotBitmap: Bitmap? = null
    private var pendingScreenshot: Bitmap? = null

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
            Log.d(TAG, "=== show: Creating chat overlay ===")

            // Inflate the chat overlay layout
            val inflater = LayoutInflater.from(context)
            chatOverlayView = inflater.inflate(R.layout.chat_overlay_layout, null)

            // Get screen dimensions
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val targetHeight = (screenHeight / 3.0).toInt()

            Log.d(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}px")
            Log.d(TAG, "Target overlay height: ${targetHeight}px (1/3 of screen)")

            // Set up the window layout parameters with dynamic height
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                targetHeight,  // Use 1/3 of screen height instead of MATCH_PARENT
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
                gravity = Gravity.BOTTOM  // Position at bottom by default
                // Make it focusable when shown to allow text input
                flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            }

            Log.d(TAG, "WindowManager params: width=${params.width}, height=${params.height}, gravity=${params.gravity}")

            // Add view to window manager
            windowManager.addView(chatOverlayView, params)
            isVisible = true

            // Initialize UI components
            initializeViews()
            setupListeners()
            setupRecyclerView()

            // Add welcome message
            addWelcomeMessage()

            // Process any pending screenshot now that views are initialized
            pendingScreenshot?.let { bitmap ->
                Log.d(TAG, "Processing pending screenshot after show()")
                processScreenshotInternal(bitmap)
                pendingScreenshot = null
            }

            Log.d(TAG, "=== show: Chat overlay shown successfully at height ${targetHeight}px ===")
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
