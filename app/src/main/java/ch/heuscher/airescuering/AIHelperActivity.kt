package ch.heuscher.airescuering

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.heuscher.airescuering.data.api.GeminiApiService
import ch.heuscher.airescuering.di.ServiceLocator
import ch.heuscher.airescuering.domain.model.AIMessage
import ch.heuscher.airescuering.domain.model.ActionData
import ch.heuscher.airescuering.domain.model.MessageRole
import ch.heuscher.airescuering.domain.model.MessageType
import ch.heuscher.airescuering.util.ScreenshotHelper
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for AI-powered assistance using Gemini API.
 * Provides chat interface with voice input support.
 */
class AIHelperActivity : AppCompatActivity() {

    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var voiceButton: Button
    private lateinit var screenshotButton: Button
    private lateinit var closeButton: Button
    private lateinit var loadingIndicator: ProgressBar

    private val messages = mutableListOf<AIMessage>()
    private lateinit var adapter: ChatAdapter

    private var geminiService: GeminiApiService? = null

    companion object {
        private const val VOICE_RECOGNITION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_helper)

        initViews()
        setupRecyclerView()
        setupListeners()
        initGeminiService()

        // Add welcome message
        addMessage(AIMessage(
            id = UUID.randomUUID().toString(),
            content = "Hello! I'm your AI assistant. You can type your questions below or use the microphone button 🎤 for voice input.",
            role = MessageRole.ASSISTANT
        ))
    }

    private fun initViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        voiceButton = findViewById(R.id.voiceButton)
        screenshotButton = findViewById(R.id.screenshotButton)
        closeButton = findViewById(R.id.closeButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(messages)
        messagesRecyclerView.adapter = adapter
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
    }

    private fun setupListeners() {
        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                messageInput.text.clear()
            }
        }

        voiceButton.setOnClickListener {
            startVoiceRecognition()
        }

        screenshotButton.setOnClickListener {
            takeScreenshot()
        }

        closeButton.setOnClickListener {
            finish()
        }
    }

    private fun initGeminiService() {
        lifecycleScope.launch {
            val apiKey = ServiceLocator.aiHelperRepository.getApiKey().firstOrNull() ?: ""
            if (apiKey.isEmpty()) {
                // Show prominent warning in chat with option to enter API key
                val warningMessage = AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "⚠️ API Key Not Configured\n\nTo use the AI assistant, you need to set up a Gemini API key.\n\nTap the button below to enter your API key now, or you can set it later in the app settings.",
                    role = MessageRole.ASSISTANT
                )
                addMessage(warningMessage)

                // Show dialog to enter API key
                showApiKeyDialog()
                return@launch
            }
            geminiService = GeminiApiService(apiKey, debug = true)
        }
    }

    private fun showApiKeyDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Enter your Gemini API key"
            setPadding(50, 40, 50, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Enter API Key")
            .setMessage("Enter your Google Gemini API key to enable AI assistance.\n\nYou can get a free API key from: https://aistudio.google.com/app/apikey")
            .setView(input)
            .setPositiveButton("Save") { dialog, _ ->
                val apiKey = input.text.toString().trim()
                if (apiKey.isNotEmpty()) {
                    lifecycleScope.launch {
                        ServiceLocator.aiHelperRepository.setApiKey(apiKey)
                        ServiceLocator.aiHelperRepository.setEnabled(true)

                        // Initialize Gemini service with new key
                        geminiService = GeminiApiService(apiKey, debug = true)

                        // Add success message
                        val successMessage = AIMessage(
                            id = UUID.randomUUID().toString(),
                            content = "✅ API Key Configured!\n\nYour API key has been saved. You can now chat with the AI assistant!",
                            role = MessageRole.ASSISTANT
                        )
                        addMessage(successMessage)

                        Toast.makeText(
                            this@AIHelperActivity,
                            "API key saved successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@AIHelperActivity,
                        "API key cannot be empty",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Open Settings") { dialog, _ ->
                val intent = Intent(this@AIHelperActivity, MainActivity::class.java)
                startActivity(intent)
                dialog.dismiss()
            }
            .show()
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
        loadingIndicator.visibility = View.VISIBLE
        sendButton.isEnabled = false
        voiceButton.isEnabled = false

        // Get AI response
        lifecycleScope.launch {
            try {
                val service = geminiService
                if (service == null) {
                    Toast.makeText(
                        this@AIHelperActivity,
                        "AI service not initialized. Please check API key.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Convert messages to API format
                val conversationHistory = messages
                    .filter { it.role != MessageRole.SYSTEM }
                    .map { message ->
                        val role = when (message.role) {
                            MessageRole.USER -> "user"
                            MessageRole.ASSISTANT -> "model"
                            MessageRole.SYSTEM -> "user"
                        }
                        role to message.content
                    }

                val result = service.generateAssistanceSuggestion(
                    userRequest = text,
                    context = "The user is on their Android device"
                )

                result.onSuccess { response ->
                    // Check if we should show suggestion with action buttons
                    if (response.contains("suggest", ignoreCase = true) ||
                        response.contains("recommend", ignoreCase = true)) {
                        showSuggestionInChat(response)
                    } else {
                        val assistantMessage = AIMessage(
                            id = UUID.randomUUID().toString(),
                            content = response,
                            role = MessageRole.ASSISTANT
                        )
                        addMessage(assistantMessage)
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        this@AIHelperActivity,
                        "Error: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    val errorMessage = AIMessage(
                        id = UUID.randomUUID().toString(),
                        content = "Sorry, I encountered an error: ${error.message}",
                        role = MessageRole.ASSISTANT
                    )
                    addMessage(errorMessage)
                }
            } finally {
                loadingIndicator.visibility = View.GONE
                sendButton.isEnabled = true
                voiceButton.isEnabled = true
            }
        }
    }

    private fun addMessage(message: AIMessage) {
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        messagesRecyclerView.scrollToPosition(messages.size - 1)
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you need help with?")
        }

        try {
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice recognition not available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.firstOrNull()?.let { text ->
                messageInput.setText(text)
                sendMessage(text)
            }
        }
    }

    /**
     * Take a screenshot and add it to the chat
     */
    private fun takeScreenshot() {
        if (!ScreenshotHelper.isAvailable()) {
            val errorMessage = AIMessage(
                id = UUID.randomUUID().toString(),
                content = "⚠️ Screenshot Unavailable\n\n" +
                        "The screenshot feature requires the Accessibility Service to be enabled.\n\n" +
                        ScreenshotHelper.getEnableInstructions(),
                role = MessageRole.ASSISTANT,
                messageType = MessageType.ERROR
            )
            addMessage(errorMessage)
            return
        }

        // Add status message
        val statusMessage = AIMessage(
            id = UUID.randomUUID().toString(),
            content = "📸 Taking screenshot...",
            role = MessageRole.SYSTEM,
            messageType = MessageType.SCREENSHOT
        )
        addMessage(statusMessage)

        // Disable buttons while taking screenshot
        screenshotButton.isEnabled = false
        sendButton.isEnabled = false
        voiceButton.isEnabled = false

        ScreenshotHelper.takeScreenshot(
            context = this,
            onSuccess = { filePath ->
                val successMessage = AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "✅ Screenshot captured successfully!\n\nSaved to: $filePath\n\nYou can now ask me questions about this screenshot.",
                    role = MessageRole.ASSISTANT,
                    messageType = MessageType.SCREENSHOT
                )
                addMessage(successMessage)

                // Re-enable buttons
                screenshotButton.isEnabled = true
                sendButton.isEnabled = true
                voiceButton.isEnabled = true
            },
            onFailure = { error ->
                val errorMessage = AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "❌ Screenshot Failed\n\n$error\n\nPlease make sure the Accessibility Service is properly enabled and try again.",
                    role = MessageRole.ASSISTANT,
                    messageType = MessageType.ERROR
                )
                addMessage(errorMessage)

                // Re-enable buttons
                screenshotButton.isEnabled = true
                sendButton.isEnabled = true
                voiceButton.isEnabled = true
            },
            showToast = false  // We handle messages in chat
        )
    }

    private fun showSuggestionInChat(suggestion: String) {
        // Instead of showing a dialog, add the suggestion as a message with action buttons
        val actionId = UUID.randomUUID().toString()
        val suggestionMessage = AIMessage(
            id = UUID.randomUUID().toString(),
            content = suggestion,
            role = MessageRole.ASSISTANT,
            messageType = MessageType.ACTION_REQUIRED,
            actionData = ActionData(
                actionId = actionId,
                actionText = "Do you want to proceed with this suggestion?",
                showApproveButton = true,
                showRefineButton = true
            )
        )
        addMessage(suggestionMessage)
    }

    /**
     * Adapter for chat messages
     */
    private inner class ChatAdapter(
        private val messages: List<AIMessage>
    ) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val messageCard: CardView = view.findViewById(R.id.messageCard)
            val roleText: TextView = view.findViewById(R.id.messageRole)
            val messageText: TextView = view.findViewById(R.id.messageText)
            val timeText: TextView = view.findViewById(R.id.messageTime)
            val actionButtonsContainer: LinearLayout = view.findViewById(R.id.actionButtonsContainer)
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

            // Handle action buttons
            if (message.messageType == MessageType.ACTION_REQUIRED && message.actionData != null) {
                holder.actionButtonsContainer.visibility = View.VISIBLE

                // Configure buttons based on actionData
                holder.approveButton.visibility = if (message.actionData.showApproveButton) View.VISIBLE else View.GONE
                holder.refineButton.visibility = if (message.actionData.showRefineButton) View.VISIBLE else View.GONE

                holder.approveButton.setOnClickListener {
                    Toast.makeText(this@AIHelperActivity, "Suggestion approved", Toast.LENGTH_SHORT).show()
                    // Hide buttons after approval
                    holder.actionButtonsContainer.visibility = View.GONE
                    // TODO: Execute the approved action
                }

                holder.refineButton.setOnClickListener {
                    messageInput.setText("I'd like to refine that suggestion...")
                    messageInput.requestFocus()
                    Toast.makeText(this@AIHelperActivity, "Let's refine the suggestion", Toast.LENGTH_SHORT).show()
                }
            } else {
                holder.actionButtonsContainer.visibility = View.GONE
            }

            // Style based on role and message type
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

                    // Different colors for different message types
                    when (message.messageType) {
                        MessageType.ERROR -> {
                            holder.messageCard.setCardBackgroundColor(0xFFFFCDD2.toInt()) // Light red
                        }
                        MessageType.ACTION_REQUIRED -> {
                            holder.messageCard.setCardBackgroundColor(0xFFE1F5FE.toInt()) // Light blue
                        }
                        else -> {
                            holder.messageCard.setCardBackgroundColor(0xFFEEEEEE.toInt())
                        }
                    }
                    holder.roleText.setTextColor(0xFF000000.toInt())
                    holder.messageText.setTextColor(0xFF000000.toInt())
                }
                MessageRole.SYSTEM -> {
                    val params = holder.messageCard.layoutParams
                    if (params is LinearLayout.LayoutParams) {
                        params.gravity = android.view.Gravity.START
                        holder.messageCard.layoutParams = params
                    }
                    holder.messageCard.setCardBackgroundColor(0xFFFFF9C4.toInt()) // Light yellow
                    holder.roleText.setTextColor(0xFF000000.toInt())
                    holder.messageText.setTextColor(0xFF000000.toInt())
                }
            }
        }

        override fun getItemCount() = messages.size
    }
}
