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
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
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
import io.noties.markwon.Markwon
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
    private lateinit var screenshotButton: Button
    private lateinit var newChatButton: Button
    private lateinit var closeButton: Button
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var chatCard: CardView
    private lateinit var positionTopButton: Button
    private lateinit var positionBottomButton: Button
    private lateinit var stepNavigationContainer: LinearLayout
    private lateinit var stepBackButton: Button
    private lateinit var stepForwardButton: Button
    private lateinit var stepIndicator: TextView

    private val messages = mutableListOf<AIMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var markwon: Markwon

    private var geminiService: GeminiApiService? = null
    private var currentScreenshot: Bitmap? = null
    private var currentScreenshotPath: String? = null

    // Chat history for maintaining conversation context with the AI model
    private val chatHistory = mutableListOf<ch.heuscher.airescuering.data.api.Content>()

    // Two-stage flow: planning and execution
    private var currentUserRequest: String? = null
    private var currentPlan: String? = null
    private val executionHistory = mutableListOf<ch.heuscher.airescuering.data.api.Content>()
    private var isExecuting = false
    private var stopButton: Button? = null

    // Step navigation
    private var currentSteps: List<String> = emptyList()
    private var currentStepIndex: Int = 0

    companion object {
        private const val TAG = "AIHelperActivity"
        private const val VOICE_RECOGNITION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== onCreate: START ===")

        // Make input visible when keyboard shows
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        setContentView(R.layout.activity_ai_helper)

        initViews()
        initMarkwon()
        setupRecyclerView()
        setupListeners()
        initGeminiService()

        // Only take screenshot if there's no active chat
        if (chatHistory.isEmpty()) {
            // Check for screenshot from Intent first (captured before activity launch)
            val screenshotBase64 = intent.getStringExtra("screenshot_base64")
            Log.d(TAG, "onCreate: screenshotBase64 is ${if (screenshotBase64 == null) "NULL" else "present (${screenshotBase64.length} chars)"}")

            if (screenshotBase64 != null) {
                // Convert base64 to bitmap and set as background
                try {
                    val decodedBytes = android.util.Base64.decode(screenshotBase64, android.util.Base64.NO_WRAP)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    if (bitmap != null) {
                        currentScreenshot = bitmap
                        setScreenshotBackground(bitmap)
                        Log.d(TAG, "Screenshot set as background from intent: ${bitmap.width}x${bitmap.height}")

                        // Add welcome message
                        addMessage(AIMessage(
                            id = UUID.randomUUID().toString(),
                            content = "Hello! I'm your AI assistant. I can see what's on your screen and help you with it. What would you like to do?",
                            role = MessageRole.ASSISTANT
                        ))
                    } else {
                        Log.e(TAG, "Failed to decode bitmap from base64")
                        // Fall back to taking screenshot
                        takeInitialScreenshot()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode screenshot from Intent", e)
                    // Fall back to taking screenshot
                    takeInitialScreenshot()
                }
            } else {
                Log.w(TAG, "No screenshot provided in Intent, taking initial screenshot")
                // Take screenshot first, then show welcome message
                takeInitialScreenshot()
            }
        } else {
            Log.d(TAG, "Active chat exists (${chatHistory.size} messages), skipping initial screenshot")
        }
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
        screenshotButton = findViewById(R.id.screenshotButton)
        newChatButton = findViewById(R.id.newChatButton)
        closeButton = findViewById(R.id.closeButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        chatCard = findViewById(R.id.chatCard)
        positionTopButton = findViewById(R.id.positionTopButton)
        positionBottomButton = findViewById(R.id.positionBottomButton)
        stepNavigationContainer = findViewById(R.id.stepNavigationContainer)
        stepBackButton = findViewById(R.id.stepBackButton)
        stepForwardButton = findViewById(R.id.stepForwardButton)
        stepIndicator = findViewById(R.id.stepIndicator)
    }

    private fun initMarkwon() {
        markwon = Markwon.create(this)
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

        // Submit on Enter key
        messageInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val text = messageInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendMessage(text)
                    messageInput.text.clear()
                }
                true
            } else {
                false
            }
        }

        voiceButton.setOnClickListener {
            startVoiceRecognition()
        }

        screenshotButton.setOnClickListener {
            takeScreenshot()
        }

        newChatButton.setOnClickListener {
            startNewChat()
        }

        closeButton.setOnClickListener {
            finish()
        }

        // Position buttons
        positionTopButton.setOnClickListener {
            positionWindowAt(isTop = true)
        }

        positionBottomButton.setOnClickListener {
            positionWindowAt(isTop = false)
        }

        // Step navigation buttons
        stepBackButton.setOnClickListener {
            navigateStep(forward = false)
        }

        stepForwardButton.setOnClickListener {
            navigateStep(forward = true)
        }
    }

    /**
     * Position the chat window at top or bottom of screen
     */
    private fun positionWindowAt(isTop: Boolean) {
        val params = chatCard.layoutParams as FrameLayout.LayoutParams
        params.gravity = if (isTop) {
            android.view.Gravity.TOP
        } else {
            android.view.Gravity.BOTTOM
        }
        chatCard.layoutParams = params
        Log.d(TAG, "Window positioned at ${if (isTop) "top" else "bottom"}")
    }

    /**
     * Parse steps from AI response that start with ###
     */
    private fun parseSteps(content: String): List<String> {
        val steps = mutableListOf<String>()
        val lines = content.lines()
        var currentStep = StringBuilder()

        for (line in lines) {
            if (line.trim().startsWith("###")) {
                // Save previous step if exists
                if (currentStep.isNotEmpty()) {
                    steps.add(currentStep.toString().trim())
                    currentStep = StringBuilder()
                }
                // Start new step (remove ###)
                currentStep.append(line.trim().removePrefix("###").trim()).append("\n")
            } else if (currentStep.isNotEmpty()) {
                // Continue current step
                currentStep.append(line).append("\n")
            }
        }

        // Add last step
        if (currentStep.isNotEmpty()) {
            steps.add(currentStep.toString().trim())
        }

        return steps
    }

    /**
     * Display current step in the message list
     */
    private fun displayCurrentStep() {
        if (currentSteps.isEmpty() || currentStepIndex !in currentSteps.indices) {
            return
        }

        // Update step indicator
        stepIndicator.text = "Step ${currentStepIndex + 1} of ${currentSteps.size}"

        // Update button states
        stepBackButton.isEnabled = currentStepIndex > 0
        stepForwardButton.isEnabled = currentStepIndex < currentSteps.size - 1

        // Add or update step message
        val stepContent = "### Step ${currentStepIndex + 1}\n\n${currentSteps[currentStepIndex]}"
        val stepMessage = AIMessage(
            id = "step_${currentStepIndex}",
            content = stepContent,
            role = MessageRole.ASSISTANT
        )

        // Remove last message if it's a step message, then add new one
        if (messages.isNotEmpty() && messages.last().id.startsWith("step_")) {
            messages.removeAt(messages.size - 1)
            adapter.notifyItemRemoved(messages.size)
        }

        addMessage(stepMessage)
    }

    /**
     * Navigate to next or previous step
     */
    private fun navigateStep(forward: Boolean) {
        if (currentSteps.isEmpty()) return

        val newIndex = if (forward) {
            (currentStepIndex + 1).coerceIn(0, currentSteps.size - 1)
        } else {
            (currentStepIndex - 1).coerceIn(0, currentSteps.size - 1)
        }

        if (newIndex != currentStepIndex) {
            currentStepIndex = newIndex
            displayCurrentStep()
        }
    }

    /**
     * Start a new chat session
     * Clears all chat history and takes a fresh screenshot
     */
    private fun startNewChat() {
        AlertDialog.Builder(this)
            .setTitle("Start New Chat?")
            .setMessage("This will clear your current conversation and take a new screenshot. Are you sure?")
            .setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()

                // Clear all chat state
                chatHistory.clear()
                executionHistory.clear()
                messages.clear()
                adapter.notifyDataSetChanged()
                currentScreenshot = null
                currentScreenshotPath = null
                currentUserRequest = null
                currentPlan = null
                isExecuting = false

                // Clear screenshot background
                val screenshotBackground = findViewById<android.widget.ImageView>(R.id.screenshotBackground)
                val dimmingOverlay = findViewById<View>(R.id.dimmingOverlay)
                screenshotBackground?.visibility = View.GONE
                dimmingOverlay?.visibility = View.GONE

                // Take new screenshot to start fresh chat
                takeInitialScreenshot()

                Log.d(TAG, "New chat started, all state cleared")
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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

        // Add user message to chat history
        chatHistory.add(ch.heuscher.airescuering.data.api.Content(
            role = "user",
            parts = listOf(ch.heuscher.airescuering.data.api.Part(text = text))
        ))

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

                Log.d(TAG, "Stage 1: Generating solution plan with chat history (${chatHistory.size} messages)...")
                val planResult = service.generateSolutionPlan(
                    userRequest = text,
                    screenshot = currentScreenshot,
                    context = "The user is on their Android device",
                    conversationHistory = chatHistory.toList()
                )

                planResult.onSuccess { plan ->
                    Log.d(TAG, "Stage 1: Plan generated successfully")

                    // Add assistant response to chat history
                    chatHistory.add(ch.heuscher.airescuering.data.api.Content(
                        role = "model",
                        parts = listOf(ch.heuscher.airescuering.data.api.Part(text = plan))
                    ))

                    // Parse steps from plan
                    val steps = parseSteps(plan)

                    if (steps.isNotEmpty()) {
                        // Show step navigation for step-based responses
                        currentSteps = steps
                        currentStepIndex = 0
                        stepNavigationContainer.visibility = View.VISIBLE
                        displayCurrentStep()
                    } else {
                        // Show full plan if no steps found
                        stepNavigationContainer.visibility = View.GONE
                        val planMessage = AIMessage(
                            id = UUID.randomUUID().toString(),
                            content = plan,
                            role = MessageRole.ASSISTANT
                        )
                        addMessage(planMessage)
                    }

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
        Log.d(TAG, "executePlan: ENTER")
        val request = currentUserRequest ?: return
        val screenshot = currentScreenshot
        Log.d(TAG, "executePlan: currentScreenshot is ${if (screenshot == null) "NULL" else "available (${screenshot.width}x${screenshot.height})"}")

        if (screenshot == null) {
            Log.e(TAG, "executePlan: FAIL - No screenshot available, showing toast")
            Toast.makeText(this, "No screenshot available for execution", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "executePlan: Screenshot OK, proceeding with execution")

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

                Log.d(TAG, "Stage 2: Starting execution with computer use model...")
                
                // Move to background so AI can interact with the actual apps
                moveTaskToBack(true)
                kotlinx.coroutines.delay(500) // Give time for UI to go to background
                
                // Hide activity after moving to background (this keeps the activity alive)
                hideActivity()

                Log.d(TAG, "Stage 2: Activity moved to background, executing round 1...")
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
        val accessibilityService = AIRescueRingAccessibilityService.instance
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
            val service = AIRescueRingAccessibilityService.instance
            if (service != null) {
                service.onScreenshotCaptured = { bitmap ->
                    continuation.resume(bitmap)
                    service.onScreenshotCaptured = null
                }
                service.takeScreenshot()
            } else {
                Log.w(TAG, "AIRescueRingAccessibilityService not available")
                continuation.resume(null)
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

    /**
     * Take initial screenshot when activity opens
     */
    private fun takeInitialScreenshot() {
        if (!ScreenshotHelper.isAvailable()) {
            // Show error and welcome message
            val errorMessage = AIMessage(
                id = UUID.randomUUID().toString(),
                content = "⚠️ Screenshot Unavailable\n\n" +
                        "The screenshot feature requires the Accessibility Service to be enabled.\n\n" +
                        ScreenshotHelper.getEnableInstructions(),
                role = MessageRole.ASSISTANT,
                messageType = MessageType.ERROR
            )
            addMessage(errorMessage)

            // Add welcome message
            addMessage(AIMessage(
                id = UUID.randomUUID().toString(),
                content = "Hello! I'm your AI assistant. You can type your questions below, use the microphone button 🎤 for voice input, or take a screenshot 📸 of what you need help with.",
                role = MessageRole.ASSISTANT
            ))
            return
        }

        // Add status message
        val statusMessage = AIMessage(
            id = UUID.randomUUID().toString(),
            content = "📸 Taking screenshot of your current screen...",
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
                currentScreenshotPath = filePath

                // Also try to load the bitmap for the execution flow
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(filePath)
                    if (bitmap != null) {
                        currentScreenshot = bitmap
                        setScreenshotBackground(bitmap)
                        Log.d(TAG, "Screenshot bitmap loaded for execution: ${bitmap.width}x${bitmap.height}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load bitmap from file for execution", e)
                }

                val successMessage = AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "✅ Screenshot captured!\n\nI've captured a screenshot of what you were viewing before opening this chat.\n\nSaved to: $filePath\n\nYou can now ask me questions about what you see in the screenshot, or take another screenshot 📸 anytime.",
                    role = MessageRole.ASSISTANT,
                    messageType = MessageType.SCREENSHOT
                )
                addMessage(successMessage)

                // Add welcome message
                addMessage(AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "What would you like help with?",
                    role = MessageRole.ASSISTANT
                ))

                // Re-enable buttons
                screenshotButton.isEnabled = true
                sendButton.isEnabled = true
                voiceButton.isEnabled = true
            },
            onFailure = { error ->
                val errorMessage = AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "❌ Screenshot Failed\n\n$error\n\nPlease make sure the Accessibility Service is properly enabled. You can try taking a screenshot again using the 📸 button.",
                    role = MessageRole.ASSISTANT,
                    messageType = MessageType.ERROR
                )
                addMessage(errorMessage)

                // Add welcome message anyway
                addMessage(AIMessage(
                    id = UUID.randomUUID().toString(),
                    content = "Hello! I'm your AI assistant. You can type your questions below, use the microphone button 🎤 for voice input, or take a screenshot 📸 of what you need help with.",
                    role = MessageRole.ASSISTANT
                ))

                // Re-enable buttons
                screenshotButton.isEnabled = true
                sendButton.isEnabled = true
                voiceButton.isEnabled = true
            },
            showToast = false  // We handle messages in chat
        )
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
                currentScreenshotPath = filePath

                // Also try to load the bitmap for the execution flow
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(filePath)
                    if (bitmap != null) {
                        currentScreenshot = bitmap
                        Log.d(TAG, "Screenshot bitmap loaded for execution: ${bitmap.width}x${bitmap.height}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load bitmap from file for execution", e)
                }

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

            // Render markdown for assistant messages, plain text for others
            if (message.role == MessageRole.ASSISTANT) {
                markwon.setMarkdown(holder.messageText, message.content)
            } else {
                holder.messageText.text = message.content
            }
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
