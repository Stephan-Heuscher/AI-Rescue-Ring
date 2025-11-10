package ch.heuscher.airescuering

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
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
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.heuscher.airescuering.data.api.GeminiApiService
import ch.heuscher.airescuering.di.ServiceLocator
import ch.heuscher.airescuering.domain.model.AIMessage
import ch.heuscher.airescuering.domain.model.MessageRole
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

        // Set up window for overlay with 85% alpha (15% transparency)
        window.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            // Make status bar and navigation bar transparent
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            // Set dim amount to 15% (making content 85% visible)
            attributes = attributes.apply {
                dimAmount = 0.15f
                flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            }
        }

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
                    val assistantMessage = AIMessage(
                        id = UUID.randomUUID().toString(),
                        content = response,
                        role = MessageRole.ASSISTANT
                    )
                    addMessage(assistantMessage)

                    // Check if response contains actionable suggestions
                    if (containsActionableSuggestion(response)) {
                        showSuggestionDialog(response)
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
     * Checks if the AI response contains actionable suggestions that require user confirmation.
     * This includes keywords like "suggest", "recommend", "should", "can", "try", etc.
     */
    private fun containsActionableSuggestion(response: String): Boolean {
        val actionKeywords = listOf(
            "suggest", "recommend", "should", "could", "try",
            "open", "go to", "navigate", "tap", "click",
            "enable", "disable", "turn on", "turn off",
            "install", "uninstall", "download", "delete",
            "send", "call", "message", "email",
            "settings", "permission", "allow", "grant"
        )

        val lowerResponse = response.lowercase()
        return actionKeywords.any { keyword -> lowerResponse.contains(keyword) }
    }

    /**
     * Shows confirmation dialog for AI-suggested actions.
     * Users must explicitly approve actions before they are performed.
     */
    private fun showSuggestionDialog(suggestion: String) {
        val dialog = ch.heuscher.airescuering.ui.AISuggestionDialog(
            context = this,
            suggestion = suggestion,
            onApprove = {
                Toast.makeText(this, "Action approved - executing...", Toast.LENGTH_SHORT).show()
                executeApprovedAction(suggestion)
            },
            onRefine = {
                Toast.makeText(this, "Let's refine the suggestion", Toast.LENGTH_SHORT).show()
                messageInput.setText("I'd like to refine that suggestion: ")
                messageInput.setSelection(messageInput.text.length)
                messageInput.requestFocus()
            },
            onCancel = {
                Toast.makeText(this, "Action cancelled", Toast.LENGTH_SHORT).show()
            }
        )
        dialog.show()
    }

    /**
     * Executes the action approved by the user.
     * TODO: Implement actual action execution based on the suggestion content.
     */
    private fun executeApprovedAction(suggestion: String) {
        // Add a message to show the action is being executed
        val executionMessage = AIMessage(
            id = UUID.randomUUID().toString(),
            content = "✓ Executing approved action...\n\nAction: ${suggestion.take(100)}${if (suggestion.length > 100) "..." else ""}",
            role = MessageRole.SYSTEM
        )
        addMessage(executionMessage)

        // TODO: Parse the suggestion and execute the corresponding action
        // This would involve:
        // - Parsing the suggestion to extract the action type and parameters
        // - Using Android APIs to perform the action (e.g., opening apps, changing settings)
        // - Reporting success/failure back to the user

        Toast.makeText(
            this,
            "Action execution not yet implemented. This is a placeholder.",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Adapter for chat messages
     */
    private class ChatAdapter(
        private val messages: List<AIMessage>
    ) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val messageCard: CardView = view.findViewById(R.id.messageCard)
            val roleText: TextView = view.findViewById(R.id.messageRole)
            val messageText: TextView = view.findViewById(R.id.messageText)
            val timeText: TextView = view.findViewById(R.id.messageTime)
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
    }
}
