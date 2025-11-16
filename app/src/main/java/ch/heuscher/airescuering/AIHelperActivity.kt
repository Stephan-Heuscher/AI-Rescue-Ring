package ch.heuscher.airescuering

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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

    // Two-stage flow: planning and execution
    private var currentUserRequest: String? = null
    private var currentPlan: String? = null
    private val executionHistory = mutableListOf<ch.heuscher.airescuering.data.api.Content>()
    private var isExecuting = false
    private var stopButton: Button? = null

    companion object {
        private const val TAG = "AIHelperActivity"
        private const val VOICE_RECOGNITION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_helper)

        initViews()
        setupRecyclerView()
        setupListeners()
        initGeminiService()

        // Get screenshot from Intent (captured before activity launch)
        val screenshotBase64 = intent.getStringExtra("screenshot_base64")
        if (screenshotBase64 != null) {
            // Convert base64 to bitmap and set as background
            try {
                val decodedBytes = android.util.Base64.decode(screenshotBase64, android.util.Base64.NO_WRAP)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bitmap != null) {
                    currentScreenshot = bitmap
                    setScreenshotBackground(bitmap)
                    Log.d(TAG, "Screenshot set as background: ${bitmap.width}x${bitmap.height}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode screenshot from Intent", e)
            }
        } else {
            Log.w(TAG, "No screenshot provided in Intent")
        }

        // Add welcome message
        addMessage(AIMessage(
            id = UUID.randomUUID().toString(),
            content = "Hello! I'm your AI assistant. I can see what's on your screen and help you with it. What would you like to do?",
            role = MessageRole.ASSISTANT
        ))
    }

    /**
     * Set screenshot as dimmed background
     */
    private fun setScreenshotBackground(bitmap: Bitmap) {
        val screenshotBackground = findViewById<android.widget.ImageView>(R.id.screenshotBackground)
        val dimmingOverlay = findViewById<View>(R.id.dimmingOverlay)

        screenshotBackground?.let {
            it.setImageBitmap(bitmap)
            it.visibility = View.VISIBLE
        }

        dimmingOverlay?.visibility = View.VISIBLE
    }

    /**
     * Hide AI Helper activity during execution so AI can interact with apps
     */
    private fun hideActivity() {
        runOnUiThread {
            window.decorView.visibility = View.GONE
            Log.d(TAG, "Activity hidden for execution")
        }
    }

    /**
     * Show AI Helper activity after execution
     */
    private fun showActivity() {
        runOnUiThread {
            window.decorView.visibility = View.VISIBLE
            Log.d(TAG, "Activity shown after execution")
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
                    content = "ÔÜá´©Å API Key Not Configured\n\nTo use the AI assistant, you need to set up a Gemini API key.\n\nTap the button below to enter your API key now, or you can set it later in the app settings.",
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
                            content = "Ô£à API Key Configured!\n\nYour API key has been saved. You can now chat with the AI assistant!",
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

        // Store current request for two-stage flow
        currentUserRequest = text

        // Show loading
        loadingIndicator.visibility = View.VISIBLE
        sendButton.isEnabled = false
        voiceButton.isEnabled = false

        // STAGE 1: Generate solution plan using Gemini 2.5 Pro
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

                Log.d(TAG, "Stage 1: Generating solution plan...")
                val planResult = service.generateSolutionPlan(
                    userRequest = text,
                    screenshot = currentScreenshot,
                    context = "The user is on their Android device"
                )

                planResult.onSuccess { plan ->
                    Log.d(TAG, "Stage 1: Plan generated successfully")

                    // Show plan to user
                    val planMessage = AIMessage(
                        id = UUID.randomUUID().toString(),
                        content = "📋 Here's my plan to help you:\n\n$plan",
                        role = MessageRole.ASSISTANT
                    )
                    addMessage(planMessage)

                    // Store the plan
                    currentPlan = plan

                    // Show approval dialog
                    showPlanApprovalDialog(plan)
                }.onFailure { error ->
                    Log.e(TAG, "Stage 1: Error generating plan", error)
                    Toast.makeText(
                        this@AIHelperActivity,
                        "Error: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    val errorMessage = AIMessage(
                        id = UUID.randomUUID().toString(),
                        content = "Sorry, I encountered an error while creating a plan: ${error.message}",
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

    /**
     * Show dialog to approve or reject the generated plan
     */
    private fun showPlanApprovalDialog(plan: String) {
        AlertDialog.Builder(this)
            .setTitle("Approve Plan")
            .setMessage("Do you want me to execute this plan?")
            .setPositiveButton("Execute") { dialog, _ ->
                dialog.dismiss()
                executePlan(plan)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                val cancelMessage = AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "Plan cancelled. Feel free to ask me to try a different approach!",
                    role = MessageRole.ASSISTANT
                )
                addMessage(cancelMessage)
            }
            .setNeutralButton("Refine") { dialog, _ ->
                dialog.dismiss()
                messageInput.setText("Please refine the plan: ")
                messageInput.setSelection(messageInput.text.length)
                messageInput.requestFocus()
            }
            .show()
    }

    /**
     * STAGE 2: Execute the approved plan using computer use model
     */
    private fun executePlan(plan: String) {
        val request = currentUserRequest ?: return
        val screenshot = currentScreenshot

        if (screenshot == null) {
            Toast.makeText(this, "No screenshot available for execution", Toast.LENGTH_SHORT).show()
            return
        }

        // Clear previous execution history
        executionHistory.clear()
        isExecuting = true

        val executingMessage = AIMessage(
            id = UUID.randomUUID().toString(),
            content = "🤖 Executing plan...",
            role = MessageRole.ASSISTANT
        )
        addMessage(executingMessage)

        // Show stop button
        showStopButton()

        loadingIndicator.visibility = View.VISIBLE
        sendButton.isEnabled = false
        voiceButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val service = geminiService
                if (service == null) {
                    Toast.makeText(
                        this@AIHelperActivity,
                        "AI service not initialized.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Hide activity so AI can interact with the actual apps
                hideActivity()
                kotlinx.coroutines.delay(300) // Give time for UI to hide

                Log.d(TAG, "Stage 2: Executing plan with computer use model...")
                executeRound(service, request, plan, screenshot, 1)
            } catch (e: Exception) {
                Log.e(TAG, "Stage 2: Error during execution", e)
                val errorMessage = AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "Error during execution: ${e.message}",
                    role = MessageRole.ASSISTANT
                )
                addMessage(errorMessage)
                hideStopButton()
                isExecuting = false
                showActivity() // Show activity on error
            } finally {
                loadingIndicator.visibility = View.GONE
                sendButton.isEnabled = true
                voiceButton.isEnabled = true
            }
        }
    }

    /**
     * Execute one round of computer use interaction (initial round without function response)
     */
    private suspend fun executeRound(
        service: GeminiApiService,
        request: String,
        plan: String,
        screenshot: Bitmap,
        roundNumber: Int
    ) {
        executeRoundInternal(service, request, plan, screenshot, null, roundNumber)
    }

    /**
     * Execute one round of computer use interaction (subsequent round with function response)
     */
    private suspend fun executeRoundWithResponse(
        service: GeminiApiService,
        request: String,
        plan: String,
        screenshot: Bitmap,
        functionResponse: ch.heuscher.airescuering.data.api.FunctionResponse,
        roundNumber: Int
    ) {
        executeRoundInternal(service, request, plan, screenshot, functionResponse, roundNumber)
    }

    /**
     * Internal method to execute one round of computer use interaction
     */
    private suspend fun executeRoundInternal(
        service: GeminiApiService,
        request: String,
        plan: String,
        screenshot: Bitmap,
        functionResponse: ch.heuscher.airescuering.data.api.FunctionResponse?,
        roundNumber: Int
    ) {
        // Check if execution was stopped
        if (!isExecuting) {
            Log.d(TAG, "Execution stopped by user")
            val stoppedMessage = AIMessage(
                id = UUID.randomUUID().toString(),
                content = "⛔ Execution stopped by user.",
                role = MessageRole.ASSISTANT
            )
            addMessage(stoppedMessage)
            hideStopButton()
            showActivity() // Show activity again
            return
        }

        if (roundNumber > 10) {
            val maxRoundsMessage = AIMessage(
                id = UUID.randomUUID().toString(),
                content = "⚠️ Maximum rounds reached. Stopping execution.",
                role = MessageRole.ASSISTANT
            )
            addMessage(maxRoundsMessage)
            hideStopButton()
            showActivity() // Show activity again
            return
        }

        Log.d(TAG, "Executing round $roundNumber...")

        val result = service.executeWithComputerUse(
            userRequest = request,
            approvedPlan = plan,
            screenshot = screenshot,
            conversationHistory = executionHistory.toList(),
            functionResponse = functionResponse
        )

        result.onSuccess { response ->
            Log.d(TAG, "Round $roundNumber: Response received")

            // Add model's response to history
            response.candidates.firstOrNull()?.content?.let { content ->
                executionHistory.add(content)
            }

            // Check for function calls
            val firstPart = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()

            if (firstPart?.functionCall != null) {
                val functionCall = firstPart.functionCall
                Log.d(TAG, "Round $roundNumber: Function call - ${functionCall.name}")

                // Show what action is being performed
                val actionMessage = AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "🔧 Action: ${functionCall.name} ${functionCall.args}",
                    role = MessageRole.ASSISTANT
                )
                addMessage(actionMessage)

                // Actually perform the action
                val actionSuccess = performAction(functionCall)

                // Create function response (don't add to history yet - will be sent with next screenshot)
                val functionResponse = ch.heuscher.airescuering.data.api.FunctionResponse(
                    name = functionCall.name,
                    response = mapOf(
                        "status" to kotlinx.serialization.json.JsonPrimitive(
                            if (actionSuccess) "success" else "failed"
                        )
                    )
                )

                // Wait a bit for the action to complete and screen to update
                kotlinx.coroutines.delay(500)

                // Capture new screenshot after action
                hideStopButton() // Hide stop button before screenshot
                val newScreenshot = captureScreenshotSync()
                showStopButton() // Show stop button again after screenshot

                if (newScreenshot != null) {
                    currentScreenshot = newScreenshot
                    // Continue to next round with new screenshot and function response
                    executeRoundWithResponse(service, request, plan, newScreenshot, functionResponse, roundNumber + 1)
                } else {
                    // Continue with old screenshot if capture failed
                    executeRoundWithResponse(service, request, plan, screenshot, functionResponse, roundNumber + 1)
                }

            } else if (firstPart?.text != null) {
                // Text response - execution complete
                Log.d(TAG, "Round $roundNumber: Text response (execution complete)")
                val completionMessage = AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "✅ ${firstPart.text}",
                    role = MessageRole.ASSISTANT
                )
                addMessage(completionMessage)
                hideStopButton()
                isExecuting = false
                showActivity() // Show activity again after completion
            } else {
                Log.w(TAG, "Round $roundNumber: No function call or text in response")
                hideStopButton()
                isExecuting = false
                showActivity() // Show activity again
            }
        }.onFailure { error ->
            Log.e(TAG, "Round $roundNumber: Error", error)
            val errorMessage = AIMessage(
                id = UUID.randomUUID().toString(),
                content = "Error in round $roundNumber: ${error.message}",
                role = MessageRole.ASSISTANT
            )
            addMessage(errorMessage)
            hideStopButton()
            isExecuting = false
            showActivity() // Show activity again on error
        }
    }

    /**
     * Perform the action specified by the function call
     */
    private suspend fun performAction(functionCall: ch.heuscher.airescuering.data.api.FunctionCall): Boolean {
        val accessibilityService = ch.heuscher.airescuering.service.accessibility.AIAssistantAccessibilityService.getInstance()
        if (accessibilityService == null) {
            Log.e(TAG, "Accessibility service not available")
            Toast.makeText(this, "Accessibility service not enabled", Toast.LENGTH_SHORT).show()
            return false
        }

        // Get screen dimensions
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        return when (functionCall.name) {
            "click_at" -> {
                val x = functionCall.args["x"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                val y = functionCall.args["y"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                Log.d(TAG, "Performing click at ($x, $y)")
                accessibilityService.performTap(x, y, screenWidth, screenHeight)
            }
            "scroll" -> {
                val direction = functionCall.args["direction"]?.toString()?.trim('"') ?: "down"
                Log.d(TAG, "Performing scroll: $direction")

                // Define swipe coordinates based on direction
                val (startX, startY, endX, endY) = when (direction.lowercase()) {
                    "up" -> listOf(500, 700, 500, 300) // Swipe up
                    "down" -> listOf(500, 300, 500, 700) // Swipe down
                    "left" -> listOf(700, 500, 300, 500) // Swipe left
                    "right" -> listOf(300, 500, 700, 500) // Swipe right
                    else -> listOf(500, 700, 500, 300) // Default to up
                }

                accessibilityService.performSwipe(startX, startY, endX, endY, screenWidth, screenHeight)
            }
            "type_text" -> {
                val text = functionCall.args["text"]?.toString()?.trim('"') ?: ""
                Log.d(TAG, "Performing type text: $text")
                accessibilityService.performTypeText(text)
            }
            else -> {
                Log.w(TAG, "Unknown action: ${functionCall.name}")
                false
            }
        }
    }

    /**
     * Capture screenshot synchronously (returns bitmap or null)
     */
    private suspend fun captureScreenshotSync(): Bitmap? = suspendCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BackHomeAccessibilityService.captureScreen { bitmap ->
                continuation.resume(bitmap)
            }
        } else {
            continuation.resume(null)
        }
    }

    /**
     * Show stop button during execution
     */
    private fun showStopButton() {
        runOnUiThread {
            if (stopButton == null) {
                stopButton = Button(this).apply {
                    text = "⛔ STOP"
                    textSize = 18f
                    setBackgroundColor(0xFFFF0000.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(40, 20, 40, 20)
                    setOnClickListener {
                        isExecuting = false
                        val stoppedMessage = AIMessage(
                            id = UUID.randomUUID().toString(),
                            content = "⛔ Stopping execution...",
                            role = MessageRole.ASSISTANT
                        )
                        addMessage(stoppedMessage)
                    }
                }

                // Add to layout at the top
                val messagesContainer = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.messagesRecyclerView)
                val rootLayout = messagesContainer?.parent as? android.view.ViewGroup
                rootLayout?.addView(stopButton, 0)
            }
            stopButton?.visibility = View.VISIBLE
        }
    }

    /**
     * Hide stop button
     */
    private fun hideStopButton() {
        runOnUiThread {
            stopButton?.visibility = View.GONE
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
