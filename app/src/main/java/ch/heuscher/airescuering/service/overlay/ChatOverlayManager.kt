package ch.heuscher.airescuering.service.overlay

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.heuscher.airescuering.R
import ch.heuscher.airescuering.data.api.GeminiApiService
import ch.heuscher.airescuering.di.ServiceLocator
import ch.heuscher.airescuering.domain.model.AIMessage
import ch.heuscher.airescuering.domain.model.MessageRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages the chat overlay view with integrated rescue ring
 */
class ChatOverlayManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ChatOverlayManager"
    }

    private var overlayView: View? = null
    private var rescueRingContainer: View? = null
    private var chatContainer: View? = null
    private var messagesRecyclerView: RecyclerView? = null
    private var messageInput: EditText? = null
    private var sendButton: Button? = null
    private var voiceButton: Button? = null
    private var closeButton: Button? = null
    private var loadingIndicator: ProgressBar? = null

    private val messages = mutableListOf<AIMessage>()
    private var adapter: ChatAdapter? = null
    private var geminiService: GeminiApiService? = null

    private var layoutParams: WindowManager.LayoutParams? = null
    private var touchListener: View.OnTouchListener? = null

    private val handler = Handler(Looper.getMainLooper())

    var onHideOverlay: (() -> Unit)? = null

    /**
     * Creates and adds the chat overlay to the window
     */
    fun createOverlay(): View {
        if (overlayView != null) return overlayView!!

        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_chat_layout, null)

        initViews()
        setupLayoutParams()
        setupRecyclerView()
        setupListeners()
        initGeminiService()

        windowManager.addView(overlayView, layoutParams)

        // Start with chat hidden, only rescue ring visible
        chatContainer?.visibility = View.GONE

        // Add welcome message
        addMessage(AIMessage(
            id = UUID.randomUUID().toString(),
            content = "Hello! I'm your AI Rescue Assistant. How can I help you?",
            role = MessageRole.ASSISTANT
        ))

        return overlayView!!
    }

    /**
     * Removes the overlay from the window
     */
    fun removeOverlay() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        rescueRingContainer = null
        chatContainer = null
        messagesRecyclerView = null
        messageInput = null
        sendButton = null
        voiceButton = null
        closeButton = null
        loadingIndicator = null
        adapter = null
        layoutParams = null
    }

    /**
     * Shows the chat interface (expands from rescue ring)
     */
    fun showChat() {
        Log.d(TAG, "showChat: Expanding chat interface")
        chatContainer?.visibility = View.VISIBLE
        rescueRingContainer?.visibility = View.GONE

        // Make overlay focusable when chat is shown
        layoutParams?.flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                             WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        overlayView?.let { windowManager.updateViewLayout(it, layoutParams) }

        // Scroll to last message
        messagesRecyclerView?.scrollToPosition(messages.size - 1)
    }

    /**
     * Hides the chat interface (collapses to rescue ring)
     */
    fun hideChat() {
        Log.d(TAG, "hideChat: Collapsing to rescue ring")
        chatContainer?.visibility = View.GONE
        rescueRingContainer?.visibility = View.VISIBLE

        // Make overlay non-focusable when only ring is shown
        layoutParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                             WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                             WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                             WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        overlayView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    /**
     * Hides the entire overlay (including rescue ring)
     */
    fun hideOverlay() {
        Log.d(TAG, "hideOverlay: Hiding entire overlay")
        overlayView?.visibility = View.GONE
    }

    /**
     * Shows the entire overlay
     */
    fun showOverlay() {
        Log.d(TAG, "showOverlay: Showing overlay")
        overlayView?.visibility = View.VISIBLE
    }

    /**
     * Sets touch listener for the rescue ring
     */
    fun setRingTouchListener(listener: View.OnTouchListener) {
        touchListener = listener
        rescueRingContainer?.setOnTouchListener(listener)
        overlayView?.findViewById<View>(R.id.floating_dot)?.setOnTouchListener(listener)
    }

    private fun initViews() {
        overlayView?.let { view ->
            rescueRingContainer = view.findViewById(R.id.rescueRingContainer)
            chatContainer = view.findViewById(R.id.chatContainer)
            messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView)
            messageInput = view.findViewById(R.id.messageInput)
            sendButton = view.findViewById(R.id.sendButton)
            voiceButton = view.findViewById(R.id.voiceButton)
            closeButton = view.findViewById(R.id.closeButton)
            loadingIndicator = view.findViewById(R.id.loadingIndicator)
        }
    }

    private fun setupLayoutParams() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(messages) { message, action ->
            handleActionButton(message, action)
        }
        messagesRecyclerView?.adapter = adapter
        messagesRecyclerView?.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
    }

    private fun setupListeners() {
        sendButton?.setOnClickListener {
            val text = messageInput?.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                messageInput?.text?.clear()
            }
        }

        voiceButton?.setOnClickListener {
            Toast.makeText(context, "Voice input available in full activity", Toast.LENGTH_SHORT).show()
        }

        closeButton?.setOnClickListener {
            hideChat()
        }

        rescueRingContainer?.setOnClickListener {
            showChat()
        }
    }

    private fun initGeminiService() {
        scope.launch {
            val apiKey = ServiceLocator.aiHelperRepository.getApiKey().firstOrNull() ?: ""
            if (apiKey.isEmpty()) {
                val warningMessage = AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "⚠️ API Key Not Configured\n\nPlease set up your Gemini API key in the app settings.",
                    role = MessageRole.ASSISTANT
                )
                addMessage(warningMessage)
                return@launch
            }
            geminiService = GeminiApiService(apiKey, debug = true)
        }
    }

    private fun sendMessage(text: String) {
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

        // Get AI response
        scope.launch {
            try {
                val service = geminiService
                if (service == null) {
                    handler.post {
                        Toast.makeText(
                            context,
                            "AI service not initialized. Please check API key.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val result = service.generateAssistanceSuggestion(
                    userRequest = text,
                    context = "The user is on their Android device"
                )

                result.onSuccess { response ->
                    val assistantMessage = AIMessage(
                        id = UUID.randomUUID().toString(),
                        content = response,
                        role = MessageRole.ASSISTANT
                    )

                    // Check if this is a suggestion (action item)
                    val hasSuggestion = response.contains("suggest", ignoreCase = true) ||
                                      response.contains("recommend", ignoreCase = true) ||
                                      response.contains("you can", ignoreCase = true) ||
                                      response.contains("should", ignoreCase = true)

                    addMessage(assistantMessage, showActions = hasSuggestion)
                }.onFailure { error ->
                    handler.post {
                        Toast.makeText(
                            context,
                            "Error: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    val errorMessage = AIMessage(
                        id = UUID.randomUUID().toString(),
                        content = "Sorry, I encountered an error: ${error.message}",
                        role = MessageRole.ASSISTANT
                    )
                    addMessage(errorMessage)
                }
            } finally {
                handler.post {
                    loadingIndicator?.visibility = View.GONE
                    sendButton?.isEnabled = true
                    voiceButton?.isEnabled = true
                }
            }
        }
    }

    private fun addMessage(message: AIMessage, showActions: Boolean = false) {
        handler.post {
            messages.add(message)
            adapter?.addMessage(message, showActions)
            messagesRecyclerView?.scrollToPosition(messages.size - 1)
        }
    }

    private fun handleActionButton(message: AIMessage, action: String) {
        when (action) {
            "approve" -> {
                Log.d(TAG, "Approve action for message: ${message.content}")

                // Hide overlay before executing command
                onHideOverlay?.invoke()

                handler.post {
                    Toast.makeText(context, "Executing suggestion...", Toast.LENGTH_SHORT).show()
                }

                // TODO: Execute the approved action
                // For now, just show a confirmation message after a delay
                handler.postDelayed({
                    addMessage(AIMessage(
                        id = UUID.randomUUID().toString(),
                        content = "Action executed successfully!",
                        role = MessageRole.ASSISTANT
                    ))
                    showOverlay()
                    showChat()
                }, 2000)
            }
            "refine" -> {
                Log.d(TAG, "Refine action for message: ${message.content}")
                handler.post {
                    messageInput?.setText("I'd like to refine that suggestion: ")
                    messageInput?.setSelection(messageInput?.text?.length ?: 0)
                    messageInput?.requestFocus()
                }
            }
        }
    }

    /**
     * Chat adapter with action button support
     */
    private class ChatAdapter(
        private val messages: MutableList<AIMessage>,
        private val onActionClick: (AIMessage, String) -> Unit
    ) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val messageActions = mutableMapOf<String, Boolean>() // messageId -> showActions

        class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val messageCard: CardView = view.findViewById(R.id.messageCard)
            val roleText: TextView = view.findViewById(R.id.messageRole)
            val messageText: TextView = view.findViewById(R.id.messageText)
            val timeText: TextView = view.findViewById(R.id.messageTime)
            val actionButtonsContainer: ViewGroup = view.findViewById(R.id.actionButtonsContainer)
            val approveButton: Button = view.findViewById(R.id.approveButton)
            val refineButton: Button = view.findViewById(R.id.refineButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = messages[position]

            holder.roleText.text = when (message.role) {
                MessageRole.USER -> "You"
                MessageRole.ASSISTANT -> "AI Assistant"
                MessageRole.SYSTEM -> "System"
            }

            holder.messageText.text = message.content
            holder.timeText.text = timeFormat.format(Date(message.timestamp))

            // Show/hide action buttons
            val showActions = messageActions[message.id] == true && message.role == MessageRole.ASSISTANT
            holder.actionButtonsContainer.visibility = if (showActions) View.VISIBLE else View.GONE

            if (showActions) {
                holder.approveButton.setOnClickListener {
                    onActionClick(message, "approve")
                    // Hide buttons after click
                    messageActions[message.id] = false
                    notifyItemChanged(position)
                }
                holder.refineButton.setOnClickListener {
                    onActionClick(message, "refine")
                }
            }

            // Style based on role
            when (message.role) {
                MessageRole.USER -> {
                    val params = holder.messageCard.layoutParams
                    if (params is LinearLayout.LayoutParams) {
                        params.gravity = android.view.Gravity.END
                        holder.messageCard.layoutParams = params
                    }
                    holder.messageCard.setCardBackgroundColor(0xFF2196F3.toInt())
                    holder.roleText.setTextColor(0xFFFFFFFF.toInt())
                    holder.messageText.setTextColor(0xFFFFFFFF.toInt())
                }
                MessageRole.ASSISTANT -> {
                    val params = holder.messageCard.layoutParams
                    if (params is LinearLayout.LayoutParams) {
                        params.gravity = android.view.Gravity.START
                        holder.messageCard.layoutParams = params
                    }
                    holder.messageCard.setCardBackgroundColor(0xFFEEEEEE.toInt())
                    holder.roleText.setTextColor(0xFF000000.toInt())
                    holder.messageText.setTextColor(0xFF000000.toInt())
                }
                MessageRole.SYSTEM -> {
                    holder.messageCard.setCardBackgroundColor(0xFFFFF9C4.toInt())
                }
            }
        }

        override fun getItemCount() = messages.size

        fun addMessage(message: AIMessage, showActions: Boolean = false) {
            messageActions[message.id] = showActions
            notifyItemInserted(messages.size - 1)
        }
    }
}
