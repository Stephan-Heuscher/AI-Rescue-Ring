package ch.heuscher.airescuering.service.overlay

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import ch.heuscher.airescuering.AIHelperActivity
import ch.heuscher.airescuering.BackHomeAccessibilityService
import ch.heuscher.airescuering.MainActivity
import ch.heuscher.airescuering.ScreenshotEditorActivity
import ch.heuscher.airescuering.ScreenshotPermissionActivity
import ch.heuscher.airescuering.di.ServiceLocator
import ch.heuscher.airescuering.domain.model.DotPosition
import ch.heuscher.airescuering.domain.model.Gesture
import ch.heuscher.airescuering.domain.model.OverlayMode
import ch.heuscher.airescuering.domain.repository.SettingsRepository
import ch.heuscher.airescuering.service.screenshot.ScreenshotCaptureManager
import ch.heuscher.airescuering.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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

    // New components for screenshot and chat
    private lateinit var screenshotCaptureManager: ScreenshotCaptureManager
    private lateinit var chatOverlayManager: ChatOverlayManager

    // Service scope for coroutines
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // State tracking
    private var isUserDragging = false
    private var isOrientationChanging = false
    private var isChatOpen = false
    private var savedRingPosition: DotPosition? = null
    private var currentScreenshotPath: String? = null

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

    // Broadcast receiver for screenshot permission result
    private val screenshotPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenshotPermissionActivity.ACTION_SCREENSHOT_PERMISSION_RESULT) {
                val resultCode = intent.getIntExtra(ScreenshotPermissionActivity.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(ScreenshotPermissionActivity.EXTRA_RESULT_DATA)

                if (resultCode == Activity.RESULT_OK && data != null) {
                    Log.d(TAG, "Screenshot permission granted, starting projection")
                    screenshotCaptureManager.startProjection(resultCode, data)
                    captureAndShowScreenshot()
                } else {
                    Log.d(TAG, "Screenshot permission denied, showing chat without screenshot")
                    showChatOverlay(null)
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

        // Initialize screenshot and chat managers
        screenshotCaptureManager = ScreenshotCaptureManager(this)
        chatOverlayManager = ChatOverlayManager(this)

        // Set up chat overlay callbacks
        setupChatCallbacks()

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
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up
        keyboardManager.stopMonitoring()
        positionAnimator.cancel()
        serviceScope.cancel()
        updateHandler.removeCallbacksAndMessages(null)

        unregisterReceiver(settingsReceiver)
        unregisterReceiver(keyboardReceiver)
        unregisterReceiver(configurationReceiver)
        unregisterReceiver(screenshotPermissionReceiver)

        // Clean up chat and screenshot managers
        chatOverlayManager.hideChatOverlay()
        screenshotCaptureManager.stopProjection()

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
            gestureDetector.onTouch(event)
        }

        viewManager.setTouchListener(listener)
    }

    private fun registerBroadcastReceivers() {
        val settingsFilter = IntentFilter(AppConstants.ACTION_UPDATE_SETTINGS)
        val keyboardFilter = IntentFilter(AppConstants.ACTION_UPDATE_KEYBOARD)
        val configFilter = IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)
        val screenshotPermissionFilter = IntentFilter(ScreenshotPermissionActivity.ACTION_SCREENSHOT_PERMISSION_RESULT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, settingsFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(keyboardReceiver, keyboardFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(configurationReceiver, configFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(screenshotPermissionReceiver, screenshotPermissionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(settingsReceiver, settingsFilter)
            @Suppress("DEPRECATION")
            registerReceiver(keyboardReceiver, keyboardFilter)
            @Suppress("DEPRECATION")
            registerReceiver(configurationReceiver, configFilter)
            @Suppress("DEPRECATION")
            registerReceiver(screenshotPermissionReceiver, screenshotPermissionFilter)
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.getAllSettings().collectLatest { settings ->
                Log.d(TAG, "observeSettings: Settings changed, tapBehavior=${settings.tapBehavior}")

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
        val layoutSize = (AppConstants.OVERLAY_LAYOUT_SIZE_DP * resources.displayMetrics.density).toInt()
        val buttonSize = (AppConstants.DOT_SIZE_DP * resources.displayMetrics.density).toInt()
        val offset = (layoutSize - buttonSize) / 2

        // Get actual navigation bar margin from OverlayViewManager
        val navBarMargin = viewManager.getNavigationBarMargin()

        // Use same logic as OverlayViewManager.constrainPositionToBounds
        val constrainedX = settings.position.x.coerceIn(-offset, screenSize.x - buttonSize - offset)
        val constrainedY = settings.position.y.coerceIn(-offset, screenSize.y - buttonSize - offset - navBarMargin)
        val constrainedPosition = DotPosition(constrainedX, constrainedY)

        Log.d(TAG, "updateOverlayAppearance: screenSize=${screenSize.x}x${screenSize.y}, layoutSize=$layoutSize, buttonSize=$buttonSize, offset=$offset")
        Log.d(TAG, "updateOverlayAppearance: navBarMargin=$navBarMargin (detected height + 8dp safety)")
        Log.d(TAG, "updateOverlayAppearance: savedPosition=(${settings.position.x},${settings.position.y}) -> constrainedPosition=($constrainedX,$constrainedY)")
        Log.d(TAG, "updateOverlayAppearance: maxX=${screenSize.x - buttonSize - offset}, maxY=${screenSize.y - buttonSize - offset - navBarMargin}")

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

        // If chat is already open, do nothing
        if (isChatOpen) {
            Log.d(TAG, "handleTap: Chat already open, ignoring tap")
            return
        }

        // Start screenshot workflow
        startScreenshotWorkflow()
    }

    private fun handleQuadrupleTap() {
        Log.d(TAG, "handleQuadrupleTap: 4+ taps detected, switching to main app")

        serviceScope.launch {
            try {
                // Close chat if open
                if (isChatOpen) {
                    closeChatOverlay()
                }

                // Launch MainActivity
                val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                Log.d(TAG, "handleQuadrupleTap: MainActivity launched successfully")
            } catch (e: Exception) {
                Log.e(TAG, "handleQuadrupleTap: Error launching MainActivity", e)
            }
        }
    }

    private fun handleLongPress() {
        // Long press + drag repositions the button
        // The drag mode is already activated by GestureDetector's onDragModeChanged callback
        Log.d(TAG, "Long press detected - drag mode activated (repositioning rescue ring)")
    }

    private fun isOnHomeScreen(): Boolean {
        // Use AccessibilityService to detect home screen (more reliable than getRunningTasks)
        val accessibilityService = BackHomeAccessibilityService.instance
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

    // ========== New methods for screenshot and chat functionality ==========

    private fun setupChatCallbacks() {
        chatOverlayManager.onCloseListener = {
            closeChatOverlay()
        }

        chatOverlayManager.onSendMessageListener = { message ->
            // Handle send message
            handleChatMessage(message)
        }

        chatOverlayManager.onVoiceInputListener = {
            // Handle voice input
            // This can be implemented later
            Log.d(TAG, "Voice input requested (not yet implemented)")
        }
    }

    private fun startScreenshotWorkflow() {
        Log.d(TAG, "startScreenshotWorkflow: Starting screenshot workflow")

        // Check if we already have media projection permission
        if (screenshotCaptureManager.isProjectionActive()) {
            Log.d(TAG, "startScreenshotWorkflow: Projection already active, capturing screenshot")
            captureAndShowScreenshot()
        } else {
            Log.d(TAG, "startScreenshotWorkflow: Requesting screenshot permission")
            // Request screenshot permission
            val intent = ScreenshotPermissionActivity.createIntent(this)
            startActivity(intent)
        }
    }

    private fun captureAndShowScreenshot() {
        Log.d(TAG, "captureAndShowScreenshot: Capturing screenshot")

        screenshotCaptureManager.captureScreenshot { bitmap ->
            if (bitmap != null) {
                Log.d(TAG, "captureAndShowScreenshot: Screenshot captured successfully")

                // Save screenshot to temporary file
                val file = File(cacheDir, "screenshot_${System.currentTimeMillis()}.png")
                try {
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    currentScreenshotPath = file.absolutePath

                    // Launch screenshot editor
                    val intent = ScreenshotEditorActivity.createIntent(this, file.absolutePath)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    startActivity(intent)

                    // Wait for editor result via broadcast or activity result
                    // For now, show chat with screenshot after a delay
                    updateHandler.postDelayed({
                        showChatOverlay(file.absolutePath)
                    }, 2000)

                } catch (e: Exception) {
                    Log.e(TAG, "captureAndShowScreenshot: Error saving screenshot", e)
                    showChatOverlay(null)
                }
            } else {
                Log.e(TAG, "captureAndShowScreenshot: Screenshot capture failed")
                showChatOverlay(null)
            }
        }
    }

    private fun showChatOverlay(screenshotPath: String?) {
        Log.d(TAG, "showChatOverlay: Showing chat overlay with screenshot=$screenshotPath")

        if (isChatOpen) {
            Log.d(TAG, "showChatOverlay: Chat already open")
            return
        }

        // Save current ring position
        savedRingPosition = viewManager.getCurrentPosition()
        Log.d(TAG, "showChatOverlay: Saved ring position: $savedRingPosition")

        // Calculate center-top position for the ring
        val screenSize = orientationHandler.getUsableScreenSize()
        val buttonSize = (AppConstants.DOT_SIZE_DP * resources.displayMetrics.density).toInt()
        val layoutSize = (AppConstants.OVERLAY_LAYOUT_SIZE_DP * resources.displayMetrics.density).toInt()
        val offset = (layoutSize - buttonSize) / 2

        // Center horizontally, top position
        val centerX = (screenSize.x - buttonSize) / 2 - offset
        val topY = 20 // 20px from top

        val centerTopPosition = DotPosition(centerX, topY)
        Log.d(TAG, "showChatOverlay: Moving ring to center-top: $centerTopPosition")

        // Animate ring to center-top
        animateToPosition(centerTopPosition, 300L)

        // Wait for animation, then show chat
        updateHandler.postDelayed({
            // Calculate position below the ring
            val belowY = topY + layoutSize

            // Show chat overlay
            chatOverlayManager.showChatOverlay(belowY)

            // Load screenshot if available
            if (screenshotPath != null) {
                val bitmap = BitmapFactory.decodeFile(screenshotPath)
                chatOverlayManager.setScreenshotPreview(bitmap)
            }

            isChatOpen = true
            Log.d(TAG, "showChatOverlay: Chat overlay shown")
        }, 350) // Wait slightly longer than animation
    }

    private fun closeChatOverlay() {
        Log.d(TAG, "closeChatOverlay: Closing chat overlay")

        if (!isChatOpen) {
            Log.d(TAG, "closeChatOverlay: Chat not open")
            return
        }

        // Hide chat overlay
        chatOverlayManager.hideChatOverlay()

        // Restore ring position
        savedRingPosition?.let { position ->
            Log.d(TAG, "closeChatOverlay: Restoring ring position: $position")
            animateToPosition(position, 300L)
        }

        // Clean up screenshot
        currentScreenshotPath?.let { path ->
            File(path).delete()
        }
        currentScreenshotPath = null

        isChatOpen = false
        Log.d(TAG, "closeChatOverlay: Chat overlay closed")
    }

    private fun handleChatMessage(message: String) {
        Log.d(TAG, "handleChatMessage: Message received: $message")

        // Show loading
        chatOverlayManager.setLoading(true)

        // TODO: Integrate with Gemini API
        // For now, just show a placeholder response
        updateHandler.postDelayed({
            chatOverlayManager.setLoading(false)
            // Add response to chat
            Log.d(TAG, "handleChatMessage: Response sent (placeholder)")
        }, 1000)
    }
}
