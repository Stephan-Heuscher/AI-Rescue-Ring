package ch.heuscher.airescuering

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.heuscher.airescuering.data.api.GeminiApiService
import ch.heuscher.airescuering.di.ServiceLocator
import ch.heuscher.airescuering.domain.model.AIMessage
import ch.heuscher.airescuering.domain.model.MessageRole
import ch.heuscher.airescuering.service.accessibility.AIAssistantAccessibilityService
import ch.heuscher.airescuering.service.computeruse.ComputerUseAgent
import ch.heuscher.airescuering.service.screencapture.ScreenCaptureManager
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
    private var currentScreenshot: Bitmap? = null

    companion object {
        private const val TAG = "AIHelperActivity"
    }

    // Activity Result Launcher for voice recognition
    private lateinit var voiceRecognitionLauncher: ActivityResultLauncher<Intent>

    // Computer Use components
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var computerUseAgent: ComputerUseAgent? = null
    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    private var isComputerUseMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_helper)

        // Initialize voice recognition launcher
        voiceRecognitionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                results?.firstOrNull()?.let { text ->
                    messageInput.setText(text)
                    sendMessage(text)
                }
            }
        }

        // Initialize screen capture launcher
        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                onScreenCapturePermissionGranted(result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        initViews()
        setupRecyclerView()
        setupListeners()
        initGeminiService()
        initScreenCaptureManager()

        // Capture screenshot of the screen before opening chat
        captureScreenshot()

        // Add welcome message
        addMessage(AIMessage(
            id = UUID.randomUUID().toString(),
            content = "Hello! I'm your AI assistant.\n\n💬 **Chat Mode** (current)\nAsk me anything and I'll provide answers\n\n🤖 **Computer Use Mode**\nType \"/computer\" to let me control your device\n• I can tap, swipe, scroll, and interact with your screen\n• I'll show you what I'm doing in real-time\n• Type \"/chat\" to switch back\n\n🎤 Tip: Use the microphone button for voice input!",
            role = MessageRole.ASSISTANT
        ))
    }

    /**
     * Capture a screenshot of the screen using accessibility service
     */
    private fun captureScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, "Requesting screenshot capture...")
            BackHomeAccessibilityService.captureScreen { bitmap ->
                if (bitmap != null) {
                    currentScreenshot = bitmap
                    Log.d(TAG, "Screenshot captured: ${bitmap.width}x${bitmap.height}")

                    // Add message indicating screenshot was captured
                    runOnUiThread {
                        val screenshotMessage = AIMessage(
                            id = UUID.randomUUID().toString(),
                            content = "📸 I've captured a screenshot of what was on your screen (${bitmap.width}x${bitmap.height}). I can analyze it to help you.",
                            role = MessageRole.ASSISTANT
                        )
                        addMessage(screenshotMessage)
                    }
                } else {
                    Log.w(TAG, "Screenshot capture failed or returned null")
                    runOnUiThread {
                        val failureMessage = AIMessage(
                            id = UUID.randomUUID().toString(),
                            content = "⚠️ Couldn't capture a screenshot. Make sure accessibility service is enabled. I can still help with text-based questions!",
                            role = MessageRole.ASSISTANT
                        )
                        addMessage(failureMessage)
                    }
                }
            }
        } else {
            Log.w(TAG, "Screenshot capture requires Android R (API 30+)")
            val notSupportedMessage = AIMessage(
                id = UUID.randomUUID().toString(),
                content = "⚠️ Screenshot capture requires Android 11+. I can still help with text-based questions!",
                role = MessageRole.ASSISTANT
            )
            addMessage(notSupportedMessage)
        }
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
        // Check for special commands
        when {
            text.equals("enable computer use", ignoreCase = true) ||
            text.equals("/computer", ignoreCase = true) -> {
                enableComputerUseMode(true)
                return
            }
            text.equals("disable computer use", ignoreCase = true) ||
            text.equals("/chat", ignoreCase = true) -> {
                enableComputerUseMode(false)
                return
            }
        }

        // Check if Computer Use mode is enabled
        if (isComputerUseMode) {
            startComputerUseTask(text)
            return
        }

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
                    screenshot = currentScreenshot,
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
            voiceRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice recognition not available", Toast.LENGTH_SHORT).show()
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

    // ===== Computer Use Methods =====

    private fun initScreenCaptureManager() {
        screenCaptureManager = ScreenCaptureManager(this)
    }

    fun enableComputerUseMode(enable: Boolean) {
        // Check prerequisites
        if (enable) {
            if (!AIAssistantAccessibilityService.isEnabled()) {
                showAccessibilityServiceDialog()
                return
            }

            // Request screen capture permission
            val captureManager = screenCaptureManager
            if (captureManager != null) {
                val intent = captureManager.createScreenCaptureIntent()
                screenCaptureLauncher.launch(intent)
            }
        } else {
            isComputerUseMode = false
            addMessage(AIMessage(
                id = UUID.randomUUID().toString(),
                content = "Computer Use mode disabled. Switched back to chat mode.",
                role = MessageRole.SYSTEM
            ))
        }
    }

    private fun showAccessibilityServiceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable AI Assistant Service")
            .setMessage("To use Computer Use mode, you need to enable the AI Assistant accessibility service.\n\nThis allows the AI to see your screen and perform actions on your behalf.\n\nGo to Settings > Accessibility > AI Assistant and turn it on.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onScreenCapturePermissionGranted(resultCode: Int, data: Intent) {
        screenCaptureManager?.initializeMediaProjection(resultCode, data)
        isComputerUseMode = true

        addMessage(AIMessage(
            id = UUID.randomUUID().toString(),
            content = "🤖 Computer Use mode enabled!\n\nI can now see your screen and control your device. Tell me what you'd like me to do, and I'll handle it for you.\n\nExamples:\n• \"Open Settings and turn on Bluetooth\"\n• \"Find and open the Gmail app\"\n• \"Scroll down and click the first link\"",
            role = MessageRole.SYSTEM
        ))
    }

    private fun startComputerUseTask(userGoal: String) {
        val service = geminiService
        val captureManager = screenCaptureManager

        if (service == null) {
            Toast.makeText(this, "AI service not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        if (captureManager == null) {
            Toast.makeText(this, "Screen capture not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        // Add user message
        addMessage(AIMessage(
            id = UUID.randomUUID().toString(),
            content = userGoal,
            role = MessageRole.USER
        ))

        // Disable input during automation
        sendButton.isEnabled = false
        voiceButton.isEnabled = false
        messageInput.isEnabled = false
        loadingIndicator.visibility = View.VISIBLE

        // Create agent and start task
        computerUseAgent = ComputerUseAgent(this, service, captureManager)

        lifecycleScope.launch {
            computerUseAgent?.startAgentLoop(
                userGoal = userGoal,
                callback = object : ComputerUseAgent.AgentCallback {
                    override fun onThinking(message: String) {
                        runOnUiThread {
                            addMessage(AIMessage(
                                id = UUID.randomUUID().toString(),
                                content = "💭 $message",
                                role = MessageRole.SYSTEM
                            ))
                        }
                    }

                    override fun onActionExecuted(action: String, success: Boolean) {
                        runOnUiThread {
                            val icon = if (success) "✅" else "❌"
                            addMessage(AIMessage(
                                id = UUID.randomUUID().toString(),
                                content = "$icon $action",
                                role = MessageRole.SYSTEM
                            ))
                        }
                    }

                    override fun onCompleted(finalMessage: String) {
                        runOnUiThread {
                            addMessage(AIMessage(
                                id = UUID.randomUUID().toString(),
                                content = "✨ Task completed!\n\n$finalMessage",
                                role = MessageRole.ASSISTANT
                            ))

                            // Re-enable input
                            sendButton.isEnabled = true
                            voiceButton.isEnabled = true
                            messageInput.isEnabled = true
                            loadingIndicator.visibility = View.GONE
                        }
                    }

                    override fun onError(error: String) {
                        runOnUiThread {
                            addMessage(AIMessage(
                                id = UUID.randomUUID().toString(),
                                content = "⚠️ Error: $error",
                                role = MessageRole.SYSTEM
                            ))

                            // Re-enable input
                            sendButton.isEnabled = true
                            voiceButton.isEnabled = true
                            messageInput.isEnabled = true
                            loadingIndicator.visibility = View.GONE

                            Toast.makeText(
                                this@AIHelperActivity,
                                "Error: $error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    override fun onConfirmationRequired(
                        message: String,
                        onConfirm: () -> Unit,
                        onDeny: () -> Unit
                    ) {
                        runOnUiThread {
                            AlertDialog.Builder(this@AIHelperActivity)
                                .setTitle("Confirm Action")
                                .setMessage(message)
                                .setPositiveButton("Allow") { _, _ ->
                                    onConfirm()
                                }
                                .setNegativeButton("Deny") { _, _ ->
                                    onDeny()
                                }
                                .setCancelable(false)
                                .show()
                        }
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenCaptureManager?.release()
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
