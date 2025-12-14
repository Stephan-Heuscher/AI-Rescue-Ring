package ch.heuscher.airescuering.service.overlay

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import ch.heuscher.airescuering.AIRescueRingAccessibilityService
import ch.heuscher.airescuering.di.ServiceLocator
import ch.heuscher.airescuering.domain.model.DotPosition
import ch.heuscher.airescuering.domain.model.Gesture
import ch.heuscher.airescuering.domain.model.OverlayMode
import ch.heuscher.airescuering.domain.repository.SettingsRepository
import ch.heuscher.airescuering.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Refactored OverlayService with clear separation of concerns.
 * Delegates keyboard management, animations, and orientation handling to specialized components.
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        private const val ORIENTATION_CHANGE_INITIAL_DELAY_MS = 16L  // One frame (60fps)
        private const val ORIENTATION_CHANGE_RETRY_DELAY_MS = 16L    // Check every frame
        private const val ORIENTATION_CHANGE_MAX_ATTEMPTS = 20       // Max 320ms total
    }

    // Core dependencies
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewManager: OverlayViewManager
    private lateinit var gestureDetector: GestureDetector

    // Specialized components
    private lateinit var keyboardManager: KeyboardManager
    private lateinit var positionAnimator: PositionAnimator
    private lateinit var orientationHandler: OrientationHandler
    private var chatOverlayManager: ChatOverlayManager? = null
    private var telestratorManager: TelestratorManager? = null

    // Service scope for coroutines
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // State tracking
    private var isUserDragging = false
    private var isOrientationChanging = false
    private var vibrationEnabled = true

    // Handler for delayed updates
    private val updateHandler = Handler(Looper.getMainLooper())

    // Broadcast receiver for settings changes
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConstants.ACTION_UPDATE_SETTINGS) {
                serviceScope.launch {
                    updateOverlayAppearance()
                }
            }
        }
    }

    // Broadcast receiver for keyboard changes
    private val keyboardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConstants.ACTION_UPDATE_KEYBOARD) {
                val visible = intent.getBooleanExtra("keyboard_visible", false)
                val height = intent.getIntExtra("keyboard_height", 0)
                handleKeyboardBroadcast(visible, height)
            }
        }
    }

    // Broadcast receiver for configuration changes (e.g., orientation)
    private val configurationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                handleOrientationChange()
            }
        }
    }

    // Broadcast receiver for Telestrator actions
    private val telestratorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Telestrator broadcast received: ${intent?.action}")
            when (intent?.action) {
                AppConstants.ACTION_SHOW_INDICATOR -> {
                    val x = intent.getIntExtra("x", 0)
                    val y = intent.getIntExtra("y", 0)
                    val duration = intent.getLongExtra("duration", 10000L)
                    Log.d(TAG, "Showing indicator at $x, $y for ${duration}ms")
                    telestratorManager?.showIndicator(x, y, duration)
                }
                AppConstants.ACTION_HIDE_INDICATOR -> {
                    Log.d(TAG, "Hiding indicator")
                    telestratorManager?.removeIndicator()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize service locator
        ServiceLocator.initialize(this)

        // Get core dependencies
        settingsRepository = ServiceLocator.settingsRepository
        viewManager = ServiceLocator.overlayViewManager
        gestureDetector = ServiceLocator.gestureDetector
        orientationHandler = ServiceLocator.orientationHandler

        // Create specialized components
        keyboardManager = ServiceLocator.createKeyboardManager(
            context = this,
            onAdjustPosition = { position -> animateToPosition(position) },
            getCurrentPosition = { viewManager.getCurrentPosition() },
            getCurrentRotation = { orientationHandler.getCurrentRotation() },
            getUsableScreenSize = { orientationHandler.getUsableScreenSize() },
            getSettings = { settingsRepository.getAllSettings().first() },
            isUserDragging = { isUserDragging }
        )

        positionAnimator = ServiceLocator.createPositionAnimator(
            onPositionUpdate = { position -> viewManager.updatePosition(position) },
            onAnimationComplete = { position -> onAnimationComplete(position) }
        )

        // Restore saved position before creating the overlay view
        restoreInitialPosition()

        // Create overlay view
        viewManager.createOverlayView()

        // Set up gesture callbacks
        setupGestureCallbacks()

        // Register broadcast receivers
        registerBroadcastReceivers()

        // Start observing settings changes
        observeSettings()

        // Initialize screen dimensions
        initializeScreenDimensions()

        // Start keyboard monitoring
        keyboardManager.startMonitoring()

        // Initialize chat overlay manager
        initializeChatOverlay()

        // Initialize Telestrator
        telestratorManager = TelestratorManager(this, getSystemService(Context.WINDOW_SERVICE) as WindowManager)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up
        chatOverlayManager?.destroy()
        chatOverlayManager = null
        keyboardManager.stopMonitoring()
        positionAnimator.cancel()
        serviceScope.cancel()
        updateHandler.removeCallbacksAndMessages(null)

        unregisterReceiver(settingsReceiver)
        unregisterReceiver(keyboardReceiver)
        unregisterReceiver(configurationReceiver)
        unregisterReceiver(telestratorReceiver)

        viewManager.removeOverlayView()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupGestureCallbacks() {
        gestureDetector.onGesture = { gesture ->
            handleGesture(gesture)
        }

        gestureDetector.onPositionChanged = { deltaX, deltaY ->
            handlePositionChange(deltaX, deltaY)
        }

        gestureDetector.onDragModeChanged = { enabled ->
            viewManager.setDragMode(enabled)
        }

        val listener = View.OnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN || 
                event.action == android.view.MotionEvent.ACTION_UP) {
                vibrate()
            }
            gestureDetector.onTouch(event)
        }

        viewManager.setTouchListener(listener)
    }

    private fun vibrate() {
        if (!vibrationEnabled) {
            return
        }
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    private fun registerBroadcastReceivers() {
        val settingsFilter = IntentFilter(AppConstants.ACTION_UPDATE_SETTINGS)
        val keyboardFilter = IntentFilter(AppConstants.ACTION_UPDATE_KEYBOARD)
        val configFilter = IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)
        val telestratorFilter = IntentFilter().apply {
            addAction(AppConstants.ACTION_SHOW_INDICATOR)
            addAction(AppConstants.ACTION_HIDE_INDICATOR)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, settingsFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(keyboardReceiver, keyboardFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(configurationReceiver, configFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(telestratorReceiver, telestratorFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(settingsReceiver, settingsFilter)
            @Suppress("DEPRECATION")
            registerReceiver(keyboardReceiver, keyboardFilter)
            @Suppress("DEPRECATION")
            registerReceiver(configurationReceiver, configFilter)
            @Suppress("DEPRECATION")
            registerReceiver(telestratorReceiver, telestratorFilter)
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.getAllSettings().collectLatest { settings ->
                Log.d(TAG, "observeSettings: Settings changed, tapBehavior=${settings.tapBehavior}, vibrationEnabled=${settings.vibrationEnabled}, positionLocked=${settings.positionLocked}")

                // Update vibration state
                vibrationEnabled = settings.vibrationEnabled

                // Update position locked state
                gestureDetector.setPositionLocked(settings.positionLocked)

                // Get current position before updating appearance
                val currentPosition = viewManager.getCurrentPosition()
                Log.d(TAG, "observeSettings: currentPosition before update=(${currentPosition?.x}, ${currentPosition?.y})")

                updateOverlayAppearance()
                updateGestureMode(settings.tapBehavior)

                // Restore position after appearance update to prevent jumping
                // Always restore the position, constrained to bounds if needed
                currentPosition?.let { pos ->
                    val (constrainedX, constrainedY) = viewManager.constrainPositionToBounds(pos.x, pos.y)
                    Log.d(TAG, "observeSettings: restoring position from (${pos.x}, ${pos.y}) to ($constrainedX, $constrainedY)")
                    viewManager.updatePosition(DotPosition(constrainedX, constrainedY))
                }
            }
        }
    }

    private fun updateGestureMode(tapBehavior: String) {
        // Safe-Home mode requires long-press to drag, others allow immediate dragging
        val requiresLongPress = (tapBehavior == "SAFE_HOME")
        gestureDetector.setRequiresLongPressToDrag(requiresLongPress)
        Log.d(TAG, "updateGestureMode: tapBehavior=$tapBehavior, requiresLongPress=$requiresLongPress")
    }

    private fun restoreInitialPosition() {
        // Use runBlocking to ensure position is set before view is created
        kotlinx.coroutines.runBlocking {
            try {
                // Get saved position percentages and screen dimensions
                val positionPercent = settingsRepository.getPositionPercent().first()
                val screenSize = orientationHandler.getUsableScreenSize()
                val rotation = orientationHandler.getCurrentRotation()

                // Convert percentages to absolute position for current screen
                val restoredPosition = DotPosition.fromPercentages(
                    positionPercent,
                    screenSize.x,
                    screenSize.y,
                    rotation
                )

                Log.d(TAG, "restoreInitialPosition: Restored position from percentages (${positionPercent.xPercent}, ${positionPercent.yPercent}) to pixels (${restoredPosition.x}, ${restoredPosition.y}) for screen ${screenSize.x}x${screenSize.y}")

                // Set the initial position in the view manager
                viewManager.setInitialPosition(restoredPosition)
            } catch (e: Exception) {
                Log.e(TAG, "restoreInitialPosition: Error restoring position", e)
                // If restoration fails, view will use default position
            }
        }
    }

    private fun initializeScreenDimensions() {
        serviceScope.launch {
            val size = orientationHandler.getUsableScreenSize()
            val rotation = orientationHandler.getCurrentRotation()
            settingsRepository.setScreenWidth(size.x)
            settingsRepository.setScreenHeight(size.y)
            settingsRepository.setRotation(rotation)
            Log.d(TAG, "initializeScreenDimensions: width=${size.x}, height=${size.y}, rotation=$rotation")
        }
    }

    private suspend fun updateOverlayAppearance() {
        val settings = settingsRepository.getAllSettings().first()
        viewManager.updateAppearance(settings)

        val screenSize = orientationHandler.getUsableScreenSize()
        
        // Use viewManager to constrain position with new size
        val (constrainedX, constrainedY) = viewManager.constrainPositionToBounds(settings.position.x, settings.position.y)
        val constrainedPosition = DotPosition(constrainedX, constrainedY)

        Log.d(TAG, "updateOverlayAppearance: screenSize=${screenSize.x}x${screenSize.y}")
        Log.d(TAG, "updateOverlayAppearance: savedPosition=(${settings.position.x},${settings.position.y}) -> constrainedPosition=($constrainedX,$constrainedY)")

        viewManager.updatePosition(constrainedPosition)
    }

    private fun handleGesture(gesture: Gesture) {
        when (gesture) {
            Gesture.DRAG_START -> {
                positionAnimator.cancel()
                isUserDragging = true
                return
            }

            Gesture.DRAG_MOVE -> {
                if (!isUserDragging) {
                    positionAnimator.cancel()
                    isUserDragging = true
                }
                return
            }

            Gesture.DRAG_END -> {
                positionAnimator.cancel()
                isUserDragging = false
                onDragEnd()
                return
            }

            else -> { /* continue */ }
        }

        serviceScope.launch {
            when (gesture) {
                Gesture.TAP -> handleTap()
                Gesture.QUADRUPLE_TAP -> handleQuadrupleTap()
                Gesture.LONG_PRESS -> handleLongPress()
                else -> { /* No-op */ }
            }
        }
    }

    private fun handleTap() {
        Log.d(TAG, "handleTap: Tap gesture detected on ring")
        // Single tap toggles the chat overlay
        toggleChatOverlay()
    }

    private fun handleLongPress() {
        // Long press + drag repositions the button
        // The drag mode is already activated by GestureDetector's onDragModeChanged callback
        Log.d(TAG, "Long press detected - drag mode activated (repositioning rescue ring)")
    }

    private fun handleQuadrupleTap() {
        Log.d(TAG, "handleQuadrupleTap: Quadruple tap detected - opening main app")
        // Open main activity
        val intent = Intent(this, ch.heuscher.airescuering.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun initializeChatOverlay() {
        serviceScope.launch {
            try {
                // Get API key from repository
                val apiKey = ServiceLocator.aiHelperRepository.getApiKey().first()
                if (apiKey.isEmpty()) {
                    Log.w(TAG, "API key not set, chat overlay will show warning")
                }

                // Create chat overlay manager with AI helper repository for voice settings
                chatOverlayManager = ChatOverlayManager(
                    context = this@OverlayService,
                    geminiApiKey = apiKey.ifEmpty { "dummy-key" },
                    scope = serviceScope,
                    aiHelperRepository = ServiceLocator.aiHelperRepository
                ).apply {
                    onHideRequest = {
                        hideChatOverlay()
                    }
                    onScreenshotRequest = {
                        requestScreenshot()
                    }
                    onVoiceInputRequest = {
                        startVoiceInput()
                    }
                    onShowIndicator = { x, y ->
                        Log.d(TAG, "Callback: Showing indicator at $x, $y")
                        telestratorManager?.showIndicator(x, y, 0L)
                    }
                    onHideIndicator = {
                        Log.d(TAG, "Callback: Hiding indicator")
                        telestratorManager?.removeIndicator()
                    }
                }

                // Set up screenshot callback in accessibility service
                AIRescueRingAccessibilityService.instance?.let { accessibilityService ->
                    accessibilityService.onScreenshotCaptured = { bitmap ->
                        Log.d(TAG, "Screenshot captured, passing to chat overlay")
                        chatOverlayManager?.processScreenshot(bitmap)
                    }
                    Log.d(TAG, "Screenshot callback registered with accessibility service")
                } ?: run {
                    Log.w(TAG, "Accessibility service not available for screenshots")
                }

                Log.d(TAG, "Chat overlay manager initialized")

                // Observe API key changes to update the service
                launch {
                    ServiceLocator.aiHelperRepository.getApiKey().collect { newKey ->
                        if (newKey.isNotEmpty()) {
                             Log.d(TAG, "Updating API key in ChatOverlayManager")
                             chatOverlayManager?.updateApiKey(newKey)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing chat overlay", e)
            }
        }
    }

    private fun requestScreenshot(onComplete: (() -> Unit)? = null) {
        Log.d(TAG, "Screenshot requested")
        val accessibilityService = AIRescueRingAccessibilityService.instance
        if (accessibilityService != null) {
            // Hide chat overlay before taking screenshot so it's not included in the image
            val wasVisible = chatOverlayManager?.isShowing() == true
            if (wasVisible) {
                Log.d(TAG, "Hiding chat overlay before screenshot")
                chatOverlayManager?.hide()
            }

            // Wait a bit for the overlay to fully hide, then take screenshot
            updateHandler.postDelayed({
                accessibilityService.takeScreenshot()

                // Show chat overlay again after a short delay (screenshot should be captured by then)
                if (wasVisible) {
                    updateHandler.postDelayed({
                        Log.d(TAG, "Showing chat overlay after screenshot")
                        chatOverlayManager?.show()
                        onComplete?.invoke()
                    }, 200)
                } else {
                    // If overlay wasn't visible, call onComplete after screenshot is taken
                    updateHandler.postDelayed({
                        onComplete?.invoke()
                    }, 200)
                }
            }, 100)
        } else {
            Log.w(TAG, "Accessibility service not available for screenshot")
            // Show a toast to the user
            android.widget.Toast.makeText(
                this,
                "Please enable Accessibility Service for screenshot feature",
                android.widget.Toast.LENGTH_LONG
            ).show()
            onComplete?.invoke()
        }
    }

    private fun toggleChatOverlay() {
        Log.d(TAG, "toggleChatOverlay: Toggling chat overlay")

        // Check if overlay is currently visible
        val isCurrentlyVisible = chatOverlayManager?.isShowing() == true

        if (isCurrentlyVisible) {
            // If visible, just hide it
            Log.d(TAG, "Hiding chat overlay")
            chatOverlayManager?.hide()
        } else {
            // If not visible, capture screenshot FIRST, then show overlay
            Log.d(TAG, "Capturing screenshot before showing overlay")
            requestScreenshot {
                Log.d(TAG, "Screenshot captured, now showing overlay")
                chatOverlayManager?.show()
            }
        }
    }

    private fun startVoiceInput() {
        Log.d(TAG, "startVoiceInput: Requesting voice input")
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            // Store the overlay manager reference so we can pass results back
            intent.putExtra("return_to_overlay", true)
            
            // Start activity for result with flag to allow overlay
            val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            intent.flags = flags
            
            // Use a custom receiver to capture results
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "voice_input_result") {
                        val voiceText = intent.getStringExtra("voice_text") ?: ""
                        Log.d(TAG, "Voice input received: $voiceText")
                        chatOverlayManager?.processVoiceInput(voiceText)
                    }
                }
            }
            
            // Register receiver
            val filter = IntentFilter("voice_input_result")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(receiver, filter)
            }
            
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice input", e)
            Toast.makeText(this, "Voice input not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideChatOverlay() {
        Log.d(TAG, "hideChatOverlay: Hiding chat overlay")
        chatOverlayManager?.hide()
    }

    private fun isOnHomeScreen(): Boolean {
        // Use AccessibilityService to detect home screen (more reliable than getRunningTasks)
        val accessibilityService = AIRescueRingAccessibilityService.instance
        if (accessibilityService != null) {
            return accessibilityService.isOnHomeScreen()
        }

        Log.w(TAG, "AccessibilityService not available for home screen detection")
        return false  // If accessibility service is not available, assume not on home screen for safety
    }

    private fun handlePositionChange(deltaX: Int, deltaY: Int) {
        // In Safe-Home mode, dragging is now allowed everywhere (after long-press)
        serviceScope.launch {
            val currentPos = viewManager.getCurrentPosition() ?: return@launch
            val newX = currentPos.x + deltaX
            val newY = currentPos.y + deltaY

            // First constrain to screen bounds
            val (boundedX, boundedY) = viewManager.constrainPositionToBounds(newX, newY)

            // Then apply keyboard constraints if needed
            val (constrainedX, constrainedY) = keyboardManager.constrainPositionWithKeyboard(
                newX, newY, boundedX, boundedY
            )

            val newPosition = DotPosition(constrainedX, constrainedY)
            viewManager.updatePosition(newPosition)

            // Save new position
            savePosition(newPosition)
        }
    }

    private fun onDragEnd() {
        serviceScope.launch {
            viewManager.getCurrentPosition()?.let { finalPos ->
                Log.d(TAG, "onDragEnd: finalPos=(${finalPos.x}, ${finalPos.y})")
                if (keyboardManager.keyboardVisible) {
                    val settings = settingsRepository.getAllSettings().first()
                    keyboardManager.handleKeyboardChange(
                        visible = true,
                        height = keyboardManager.currentKeyboardHeight,
                        settings = settings
                    )
                } else {
                    Log.d(TAG, "onDragEnd: calling savePosition with (${finalPos.x}, ${finalPos.y})")
                    savePosition(finalPos)
                }
            }
        }
    }

    private fun savePosition(position: DotPosition) {
        serviceScope.launch {
            val screenSize = orientationHandler.getUsableScreenSize()
            val rotation = orientationHandler.getCurrentRotation()
            val positionWithScreen = DotPosition(position.x, position.y, screenSize.x, screenSize.y)
            Log.d(TAG, "savePosition: saving position=(${position.x}, ${position.y}), screenSize=${screenSize.x}x${screenSize.y}, rotation=$rotation")
            settingsRepository.setPosition(positionWithScreen)
            settingsRepository.setScreenWidth(screenSize.x)
            settingsRepository.setScreenHeight(screenSize.y)
            settingsRepository.setRotation(rotation)
            Log.d(TAG, "savePosition: position saved to repository")
        }
    }

    private fun handleKeyboardBroadcast(visible: Boolean, height: Int) {
        Log.d(TAG, "Keyboard broadcast: visible=$visible, height=$height")
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            keyboardManager.handleKeyboardChange(visible, height, settings)
        }
    }

    private fun handleOrientationChange() {
        Log.d(TAG, "Configuration changed, handling orientation")

        isOrientationChanging = true
        keyboardManager.setOrientationChanging(true)

        serviceScope.launch {
            val oldSettings = settingsRepository.getAllSettings().first()
            val oldRotation = oldSettings.rotation
            val oldWidth = oldSettings.screenWidth
            val oldHeight = oldSettings.screenHeight

            Log.d(TAG, "Orientation change started: rot=$oldRotation, size=${oldWidth}x${oldHeight}")

            // Poll for screen dimension changes with dynamic timing
            waitForOrientationComplete(oldRotation, oldWidth, oldHeight, 0)
        }
    }

    private fun waitForOrientationComplete(
        oldRotation: Int,
        oldWidth: Int,
        oldHeight: Int,
        attempt: Int
    ) {
        if (attempt >= ORIENTATION_CHANGE_MAX_ATTEMPTS) {
            Log.w(TAG, "Orientation change timeout after ${attempt * ORIENTATION_CHANGE_RETRY_DELAY_MS}ms")
            isOrientationChanging = false
            keyboardManager.setOrientationChanging(false)
            return
        }

        val delay = if (attempt == 0) ORIENTATION_CHANGE_INITIAL_DELAY_MS else ORIENTATION_CHANGE_RETRY_DELAY_MS

        updateHandler.postDelayed({
            serviceScope.launch {
                val newSize = orientationHandler.getUsableScreenSize()
                val newRotation = orientationHandler.getCurrentRotation()

                // Check if dimensions have actually changed
                val dimensionsChanged = (newSize.x != oldWidth || newSize.y != oldHeight)
                val rotationChanged = (newRotation != oldRotation)

                Log.d(TAG, "Orientation check attempt $attempt: dimensions=${newSize.x}x${newSize.y} (changed=$dimensionsChanged), rotation=$newRotation (changed=$rotationChanged)")

                if (dimensionsChanged || rotationChanged) {
                    // Screen has changed! Apply transformation immediately
                    val detectionTimeMs = ORIENTATION_CHANGE_INITIAL_DELAY_MS + (attempt * ORIENTATION_CHANGE_RETRY_DELAY_MS)
                    Log.d(TAG, "Orientation detected after ${detectionTimeMs}ms (attempt $attempt): rot=$oldRotation→$newRotation, size=${oldWidth}x${oldHeight}→${newSize.x}x${newSize.y}")

                    applyOrientationTransformation(oldRotation, oldWidth, oldHeight, newRotation, newSize)
                } else {
                    // Not changed yet, retry
                    waitForOrientationComplete(oldRotation, oldWidth, oldHeight, attempt + 1)
                }
            }
        }, delay)
    }

    private suspend fun applyOrientationTransformation(
        oldRotation: Int,
        oldWidth: Int,
        oldHeight: Int,
        newRotation: Int,
        newSize: Point
    ) {
        val oldSettings = settingsRepository.getAllSettings().first()
        val baselinePosition = oldSettings.position

        // Transform position if rotation changed
        if (newRotation != oldRotation) {
            val layoutSizePx = (AppConstants.OVERLAY_LAYOUT_SIZE_DP * resources.displayMetrics.density).toInt()
            val half = layoutSizePx / 2

            // Calculate center point
            val centerX = baselinePosition.x + half
            val centerY = baselinePosition.y + half
            val centerPosition = DotPosition(centerX, centerY, oldWidth, oldHeight, oldRotation)

            // Transform center to new rotation
            val transformedCenter = orientationHandler.transformPosition(
                centerPosition, oldWidth, oldHeight, oldRotation, newRotation
            )

            // Calculate new top-left position
            val newTopLeftX = transformedCenter.x - half
            val newTopLeftY = transformedCenter.y - half
            val transformedPosition = DotPosition(newTopLeftX, newTopLeftY, newSize.x, newSize.y, newRotation)

            Log.d(TAG, "Position transformed: (${baselinePosition.x},${baselinePosition.y}) → ($newTopLeftX,$newTopLeftY)")

            // Update position immediately
            viewManager.updatePosition(transformedPosition)
            settingsRepository.setPosition(transformedPosition)
        }

        // Update screen dimensions
        settingsRepository.setScreenWidth(newSize.x)
        settingsRepository.setScreenHeight(newSize.y)
        settingsRepository.setRotation(newRotation)

        // Clear keyboard snapshot
        keyboardManager.clearSnapshotForOrientationChange()

        // Mark orientation change as complete
        isOrientationChanging = false
        keyboardManager.setOrientationChanging(false)

        Log.d(TAG, "Orientation change complete")
    }

    private fun animateToPosition(targetPosition: DotPosition, duration: Long = 250L) {
        val startPosition = viewManager.getCurrentPosition() ?: return
        if (startPosition == targetPosition) {
            savePosition(targetPosition)
            return
        }
        positionAnimator.animateToPosition(startPosition, targetPosition, duration)
    }

    private fun onAnimationComplete(targetPosition: DotPosition) {
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            val positionWithScreen = DotPosition(
                targetPosition.x,
                targetPosition.y,
                settings.screenWidth,
                settings.screenHeight
            )
            settingsRepository.setPosition(positionWithScreen)
        }
    }
}
