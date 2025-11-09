package ch.heuscher.airescuering

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
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
        setContentView(R.layout.activity_ai_helper)

        initViews()
        setupRecyclerView()
        setupListeners()
        initGeminiService()

        // Add welcome message
        addMessage(AIMessage(
            id = UUID.randomUUID().toString(),
            content = "Hello! I'm your AI assistant. Tap the rescue ring and tell me what you'd like help with.",
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
                Toast.makeText(
                    this@AIHelperActivity,
                    "Please set API key in settings",
                    Toast.LENGTH_LONG
                ).show()
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

                    // Check if we should show suggestion dialog
                    if (response.contains("suggest", ignoreCase = true) ||
                        response.contains("recommend", ignoreCase = true)) {
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

    private fun showSuggestionDialog(suggestion: String) {
        val dialog = ch.heuscher.airescuering.ui.AISuggestionDialog(
            context = this,
            suggestion = suggestion,
            onApprove = {
                Toast.makeText(this, "Suggestion approved", Toast.LENGTH_SHORT).show()
                // TODO: Execute the approved action
            },
            onRefine = {
                Toast.makeText(this, "Let's refine the suggestion", Toast.LENGTH_SHORT).show()
                messageInput.setText("I'd like to refine that suggestion...")
                messageInput.requestFocus()
            }
        )
        dialog.show()
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
            val layoutParams = holder.messageCard.layoutParams as ViewGroup.MarginLayoutParams
            when (message.role) {
                MessageRole.USER -> {
                    (holder.messageCard.parent as View).layoutParams =
                        (holder.messageCard.parent.layoutParams as ViewGroup.LayoutParams).apply {
                            if (this is LinearLayout.LayoutParams) {
                                gravity = android.view.Gravity.END
                            }
                        }
                    holder.messageCard.setCardBackgroundColor(0xFF2196F3.toInt())
                    holder.roleText.setTextColor(0xFFFFFFFF.toInt())
                    holder.messageText.setTextColor(0xFFFFFFFF.toInt())
                }
                MessageRole.ASSISTANT -> {
                    (holder.messageCard.parent as View).layoutParams =
                        (holder.messageCard.parent.layoutParams as ViewGroup.LayoutParams).apply {
                            if (this is LinearLayout.LayoutParams) {
                                gravity = android.view.Gravity.START
                            }
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
