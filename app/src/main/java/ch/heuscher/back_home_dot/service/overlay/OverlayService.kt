package ch.heuscher.back_home_dot.service.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import ch.heuscher.back_home_dot.BackHomeAccessibilityService
import ch.heuscher.back_home_dot.di.ServiceLocator
import ch.heuscher.back_home_dot.domain.model.DotPosition
import ch.heuscher.back_home_dot.domain.model.Gesture
import ch.heuscher.back_home_dot.domain.model.OverlayMode
import ch.heuscher.back_home_dot.domain.repository.SettingsRepository
import ch.heuscher.back_home_dot.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Refactored OverlayService - now focused only on lifecycle management.
 * Delegates specific responsibilities to dedicated components.
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
    }

    // Injected dependencies (will be replaced with Hilt injection later)
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewManager: OverlayViewManager
    private lateinit var gestureDetector: GestureDetector
    private lateinit var keyboardDetector: KeyboardDetector

    // Service scope for coroutines
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Keyboard monitoring
    private val keyboardHandler = Handler(Looper.getMainLooper())
    private val keyboardRunnable = object : Runnable {
        override fun run() {
            checkKeyboardAvoidance()
            keyboardHandler.postDelayed(this, AppConstants.KEYBOARD_CHECK_INTERVAL_MS)
        }
    }

    // Position before keyboard appeared
    private var positionBeforeKeyboard: DotPosition? = null
    private var screenDimensionsBeforeKeyboard: Point? = null
    private var rotationBeforeKeyboard: Int? = null
    private var isOrientationChanging = false

    // Keyboard state for movement constraints
    private var keyboardVisible = false
    private var currentKeyboardHeight = 0
    private var isUserDragging = false

    // Active animation state
    private var positionAnimator: ValueAnimator? = null

    // Debounce keyboard adjustments
    private var lastKeyboardAdjustmentTime = 0L
    private val KEYBOARD_ADJUSTMENT_DEBOUNCE_MS = 500L

    // Handler for delayed updates
    private val updateHandler = Handler(Looper.getMainLooper())

    // Broadcast receiver for settings changes
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConstants.ACTION_UPDATE_SETTINGS) {
                updateOverlayAppearance()
            }
        }
    }

    // Broadcast receiver for keyboard changes
    private val keyboardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConstants.ACTION_UPDATE_KEYBOARD) {
                val visible = intent.getBooleanExtra("keyboard_visible", false)
                val height = intent.getIntExtra("keyboard_height", 0)
                Log.d(TAG, "Keyboard broadcast received: visible=$visible, height=$height")
                handleKeyboardChange(visible, height)
            }
        }
    }

    // Broadcast receiver for configuration changes (e.g., orientation)
    private val configurationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                Log.d(TAG, "Configuration changed, hiding overlay during transition")
                
                // Capture keyboard state IMMEDIATELY before anything else
                val capturedKeyboardPosition = positionBeforeKeyboard
                val capturedKeyboardDimensions = screenDimensionsBeforeKeyboard
                val capturedKeyboardRotation = rotationBeforeKeyboard
                Log.d(TAG, "Captured at config change: pos=$capturedKeyboardPosition, dims=$capturedKeyboardDimensions, rot=$capturedKeyboardRotation")
                
                // Hide the overlay during orientation change to prevent flicker
                viewManager.setVisibility(View.GONE)
                
                // Set flag to prevent clearing keyboard state during rotation
                isOrientationChanging = true
                
                updateHandler.postDelayed({
                    serviceScope.launch {
                        val oldSettings = settingsRepository.getAllSettings().first()
                        val newSize = getUsableScreenSize()
                        val newRotation = getCurrentRotation()
                        
                        // Determine which state to use for transformation
                        // If we have captured keyboard state, use it; otherwise use saved settings
                        val hasKeyboardState = capturedKeyboardPosition != null && capturedKeyboardDimensions != null && capturedKeyboardRotation != null
                        
                        val capturedPosition = if (hasKeyboardState) {
                            capturedKeyboardPosition!!
                        } else {
                            oldSettings.position
                        }
                        
                        val oldWidth = if (hasKeyboardState) {
                            capturedKeyboardDimensions!!.x
                        } else {
                            if (oldSettings.screenWidth > 0) oldSettings.screenWidth else newSize.x
                        }
                        
                        val oldHeight = if (hasKeyboardState) {
                            capturedKeyboardDimensions!!.y
                        } else {
                            if (oldSettings.screenHeight > 0) oldSettings.screenHeight else newSize.y
                        }
                        
                        val oldRot = if (hasKeyboardState) {
                            capturedKeyboardRotation!!
                        } else {
                            oldSettings.rotation
                        }
                        
                        Log.d(TAG, "Using state: hasKeyboardState=$hasKeyboardState, pos=$capturedPosition, dims=${oldWidth}x${oldHeight}, rot=$oldRot")
                        
                        if (newRotation != oldRot) {
                            val dotSizePx = (AppConstants.DOT_SIZE_DP * resources.displayMetrics.density).toInt()
                            val half = dotSizePx / 2
                            
                            val centerX = capturedPosition.x + half
                            val centerY = capturedPosition.y + half
                            Log.d(TAG, "Orientation transform - OLD: rot=$oldRot, size=${oldWidth}x${oldHeight}, center=($centerX,$centerY)")
                            val centerPosition = DotPosition(centerX, centerY, oldWidth, oldHeight, oldRot)
                            val transformedCenter = transformPosition(centerPosition, oldWidth, oldHeight, oldRot, newRotation)
                            Log.d(TAG, "Orientation transform - NEW: rot=$newRotation, size=${newSize.x}x${newSize.y}, transformedCenter=(${transformedCenter.x},${transformedCenter.y})")
                            val newTopLeftX = transformedCenter.x - half
                            val newTopLeftY = transformedCenter.y - half
                            Log.d(TAG, "Orientation transform - FINAL: topLeft=($newTopLeftX,$newTopLeftY)")
                            val transformedPosition = DotPosition(newTopLeftX, newTopLeftY, newSize.x, newSize.y, newRotation)
                            settingsRepository.setPosition(transformedPosition)
                        }
                        settingsRepository.setScreenWidth(newSize.x)
                        settingsRepository.setScreenHeight(newSize.y)
                        settingsRepository.setRotation(newRotation)
                        
                        // Clear saved keyboard state after rotation
                        positionBeforeKeyboard = null
                        screenDimensionsBeforeKeyboard = null
                        rotationBeforeKeyboard = null
                        
                        // Clear the flag
                        isOrientationChanging = false
                        
                        // Show the overlay after update
                        viewManager.setVisibility(View.VISIBLE)
                    }
                }, 500) // Delay to allow orientation animation to complete
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize service locator (will be replaced with Hilt)
        ServiceLocator.initialize(this)

        // Get dependencies
        settingsRepository = ServiceLocator.settingsRepository
        viewManager = ServiceLocator.overlayViewManager
        gestureDetector = ServiceLocator.gestureDetector
        keyboardDetector = ServiceLocator.keyboardDetector

        // Create overlay view before attaching gesture listeners
        viewManager.createOverlayView()

        // Set up gesture detector callbacks
        setupGestureCallbacks()

        // Register broadcast receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                settingsReceiver,
                IntentFilter(AppConstants.ACTION_UPDATE_SETTINGS),
                Context.RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                keyboardReceiver,
                IntentFilter(AppConstants.ACTION_UPDATE_KEYBOARD),
                Context.RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                configurationReceiver,
                IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                settingsReceiver,
                IntentFilter(AppConstants.ACTION_UPDATE_SETTINGS)
            )
            @Suppress("DEPRECATION")
            registerReceiver(
                keyboardReceiver,
                IntentFilter(AppConstants.ACTION_UPDATE_KEYBOARD)
            )
            @Suppress("DEPRECATION")
            registerReceiver(
                configurationReceiver,
                IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)
            )
        }

        // Start observing settings changes
        observeSettings()

        // Initialize screen dimensions
        initializeScreenDimensions()

        // Start keyboard monitoring
        keyboardHandler.post(keyboardRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up
        serviceScope.cancel()
        keyboardHandler.removeCallbacks(keyboardRunnable)
        updateHandler.removeCallbacksAndMessages(null)
        unregisterReceiver(settingsReceiver)
        unregisterReceiver(keyboardReceiver)
        unregisterReceiver(configurationReceiver)
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

        val listener = View.OnTouchListener { _, event ->
            gestureDetector.onTouch(event)
        }

        viewManager.setTouchListener(listener)
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.getAllSettings().collectLatest { settings ->
                updateOverlayAppearance()
            }
        }
    }

    private fun initializeScreenDimensions() {
        serviceScope.launch {
            val size = getUsableScreenSize()
            val rotation = getCurrentRotation()
            settingsRepository.setScreenWidth(size.x)
            settingsRepository.setScreenHeight(size.y)
            settingsRepository.setRotation(rotation)
            Log.d(TAG, "initializeScreenDimensions: screenWidth=${size.x}, screenHeight=${size.y}, rotation=$rotation")
        }
    }

    private fun transformPosition(position: DotPosition, oldW: Int, oldH: Int, oldRot: Int, newRot: Int): DotPosition {
        val delta = (newRot - oldRot + 4) % 4  // 0,1,2,3 for 0,90,180,270 degrees clockwise
        val x = position.x
        val y = position.y
        Log.d(TAG, "transformPosition: delta=$delta, oldRot=$oldRot, newRot=$newRot, input=($x,$y), oldSize=${oldW}x${oldH}")
        val result = when (delta) {
            0 -> DotPosition(x, y)
            1 -> DotPosition(y, oldW - x)  // 90 deg clockwise
            2 -> DotPosition(oldW - x, oldH - y)  // 180 deg
            3 -> DotPosition(oldH - y, x)  // 270 deg clockwise
            else -> DotPosition(x, y)
        }
        Log.d(TAG, "transformPosition: result=(${result.x},${result.y})")
        return result
    }

    private fun getCurrentRotation(): Int {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.defaultDisplay.rotation
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    }

    private fun getUsableScreenSize(): Point {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            size.x = bounds.width()
            size.y = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(size)
        }
        return size
    }

    private fun updateOverlayAppearance() {
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            viewManager.updateAppearance(settings)
            val screenSize = getUsableScreenSize()
            val dotSize = (AppConstants.DOT_SIZE_DP * resources.displayMetrics.density).toInt()
            val constrainedX = settings.position.x.coerceIn(0, screenSize.x - dotSize)
            val constrainedY = settings.position.y.coerceIn(0, screenSize.y - dotSize)
            val constrainedPosition = DotPosition(constrainedX, constrainedY)
            viewManager.updatePosition(constrainedPosition)
        }
    }

    private fun handleGesture(gesture: Gesture) {
        when (gesture) {
            Gesture.DRAG_START -> {
                cancelPositionAnimation()
                isUserDragging = true
                return
            }

            Gesture.DRAG_MOVE -> {
                if (!isUserDragging) {
                    cancelPositionAnimation()
                    isUserDragging = true
                }
                return
            }

            Gesture.DRAG_END -> {
                cancelPositionAnimation()
                isUserDragging = false
                serviceScope.launch {
                    if (keyboardVisible) {
                        val settings = settingsRepository.getAllSettings().first()
                        adjustPositionForKeyboard(settings, currentKeyboardHeight)
                    } else {
                        viewManager.getCurrentPosition()?.let { finalPos ->
                            val screenSize = getUsableScreenSize()
                            val positionWithScreen = DotPosition(finalPos.x, finalPos.y, screenSize.x, screenSize.y)
                            settingsRepository.setPosition(positionWithScreen)
                            // Also update screen dimensions
                            settingsRepository.setScreenWidth(screenSize.x)
                            settingsRepository.setScreenHeight(screenSize.y)
                            settingsRepository.setRotation(getCurrentRotation())
                        }
                    }
                }
                return
            }

            else -> { /* continue */ }
        }

        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            val mode = if (settings.rescueRingEnabled) OverlayMode.RESCUE_RING else OverlayMode.NORMAL

            when (gesture) {
                Gesture.TAP -> handleTap(mode)
                Gesture.DOUBLE_TAP -> handleDoubleTap(mode)
                Gesture.TRIPLE_TAP -> handleTripleTap(mode)
                Gesture.QUADRUPLE_TAP -> handleQuadrupleTap()
                Gesture.LONG_PRESS -> handleLongPress()
                else -> { /* No-op for drag gestures */ }
            }
        }
    }

    private fun handleTap(mode: OverlayMode) {
        when (mode) {
            OverlayMode.NORMAL -> {
                // Check tap behavior setting
                serviceScope.launch {
                    val tapBehavior = settingsRepository.getTapBehavior().first()
                    when (tapBehavior) {
                        "STANDARD" -> BackHomeAccessibilityService.instance?.performHomeAction()
                        "BACK" -> BackHomeAccessibilityService.instance?.performBackAction()
                        else -> BackHomeAccessibilityService.instance?.performBackAction()
                    }
                }
            }
            OverlayMode.RESCUE_RING -> performRescueAction()
        }
    }

    private fun handleDoubleTap(mode: OverlayMode) {
        if (mode == OverlayMode.NORMAL) {
            serviceScope.launch {
                val tapBehavior = settingsRepository.getTapBehavior().first()
                when (tapBehavior) {
                    "STANDARD" -> BackHomeAccessibilityService.instance?.performBackAction()
                    "BACK" -> BackHomeAccessibilityService.instance?.performRecentsAction()
                    else -> BackHomeAccessibilityService.instance?.performRecentsAction()
                }
            }
        }
    }

    private fun handleTripleTap(mode: OverlayMode) {
        if (mode == OverlayMode.NORMAL) {
            BackHomeAccessibilityService.instance?.performRecentsOverviewAction()
        }
    }

    private fun handleQuadrupleTap() {
        // Open main activity
        val intent = Intent(this, ch.heuscher.back_home_dot.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun handleLongPress() {
        BackHomeAccessibilityService.instance?.performHomeAction()
    }

    private fun handlePositionChange(deltaX: Int, deltaY: Int) {
        val currentPos = viewManager.getCurrentPosition() ?: return
        val newX = currentPos.x + deltaX
        val newY = currentPos.y + deltaY

        val (constrainedX, constrainedY) = constrainPositionWithKeyboard(newX, newY)
        val newPosition = DotPosition(constrainedX, constrainedY)
        viewManager.updatePosition(newPosition)

        // Save new position with current screen dimensions
        serviceScope.launch {
            val screenSize = getUsableScreenSize()
            val positionWithScreen = DotPosition(constrainedX, constrainedY, screenSize.x, screenSize.y)
            settingsRepository.setPosition(positionWithScreen)
            // Also update screen dimensions
            settingsRepository.setScreenWidth(screenSize.x)
            settingsRepository.setScreenHeight(screenSize.y)
            settingsRepository.setRotation(getCurrentRotation())
        }
    }

    private fun checkKeyboardAvoidance() {
        serviceScope.launch {
            if (isUserDragging) return@launch
            val settings = settingsRepository.getAllSettings().first()
            if (!settings.keyboardAvoidanceEnabled) return@launch

            val isVisible = keyboardDetector.isKeyboardVisible()
            if (isVisible) {
                // Save position before adjusting
                if (positionBeforeKeyboard == null) {
                    positionBeforeKeyboard = viewManager.getCurrentPosition()
                    screenDimensionsBeforeKeyboard = getUsableScreenSize()
                    rotationBeforeKeyboard = getCurrentRotation()
                    Log.d(TAG, "checkKeyboardAvoidance: SAVED position=$positionBeforeKeyboard, dimensions=$screenDimensionsBeforeKeyboard, rotation=$rotationBeforeKeyboard")
                }
                adjustPositionForKeyboard(settings)
            } else {
                // Only restore and clear if NOT in the middle of orientation change
                if (!isOrientationChanging && positionBeforeKeyboard != null) {
                    val originalPos = positionBeforeKeyboard!!
                    Log.d(TAG, "checkKeyboardAvoidance: CLEARING and restoring to $originalPos")
                    animatePosition(originalPos)
                    positionBeforeKeyboard = null
                    screenDimensionsBeforeKeyboard = null
                    rotationBeforeKeyboard = null
                }
            }
        }
    }

    private fun handleKeyboardChange(visible: Boolean, height: Int) {
        Log.d(TAG, "handleKeyboardChange: visible=$visible, height=$height")
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastKeyboardAdjustmentTime < KEYBOARD_ADJUSTMENT_DEBOUNCE_MS) {
            Log.d(TAG, "handleKeyboardChange: debounced")
            return
        }
        lastKeyboardAdjustmentTime = currentTime

        // Update keyboard state for movement constraints
        keyboardVisible = visible
        currentKeyboardHeight = height

        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            Log.d(TAG, "handleKeyboardChange: keyboardAvoidanceEnabled=${settings.keyboardAvoidanceEnabled}")
            if (!settings.keyboardAvoidanceEnabled) return@launch

            if (visible) {
                // Save position before adjusting
                if (positionBeforeKeyboard == null) {
                    positionBeforeKeyboard = viewManager.getCurrentPosition()
                    screenDimensionsBeforeKeyboard = getUsableScreenSize()
                    rotationBeforeKeyboard = getCurrentRotation()
                    Log.d(TAG, "handleKeyboardChange: saved position=$positionBeforeKeyboard, dimensions=$screenDimensionsBeforeKeyboard, rotation=$rotationBeforeKeyboard")
                }
                if (!isUserDragging) {
                    adjustPositionForKeyboard(settings, height)
                } else {
                    Log.d(TAG, "handleKeyboardChange: skipping adjust due to active drag")
                }
            } else {
                // Restore position when keyboard hides
                positionBeforeKeyboard?.let { originalPos ->
                    Log.d(TAG, "handleKeyboardChange: restoring position=$originalPos")
                    animatePosition(originalPos)
                    positionBeforeKeyboard = null
                    screenDimensionsBeforeKeyboard = null
                    rotationBeforeKeyboard = null
                }
            }
        }
    }

    private fun adjustPositionForKeyboard(settings: ch.heuscher.back_home_dot.domain.model.OverlaySettings, keyboardHeight: Int = 0) {
        // Use settings screenHeight, or fallback to display metrics if not set
        val screenHeight = if (settings.screenHeight > 0) settings.screenHeight else resources.displayMetrics.heightPixels
        val height = if (keyboardHeight > 0) keyboardHeight else keyboardDetector.getKeyboardHeight(screenHeight)
        val dotSize = (AppConstants.DOT_SIZE_DP * resources.displayMetrics.density).toInt()
        val margin = (dotSize * AppConstants.KEYBOARD_MARGIN_MULTIPLIER).toInt()

        Log.d(TAG, "adjustPositionForKeyboard: screenHeight=$screenHeight, keyboardHeight=$height, dotSize=$dotSize, margin=$margin")

        // Calculate keyboard top and safe zone (margin above keyboard)
        val keyboardTop = screenHeight - height
        val safeZoneY = keyboardTop - dotSize - margin

        Log.d(TAG, "adjustPositionForKeyboard: keyboardTop=$keyboardTop, safeZoneY=$safeZoneY")

        val currentPos = viewManager.getCurrentPosition()
        val currentY = currentPos?.y ?: 0

        // Only move if current position would collide with keyboard area
        val newY = if (currentY > safeZoneY) {
            // Current position is too low, move to safe zone
            safeZoneY.coerceAtLeast(0) // Allow positioning at top of screen if needed
        } else {
            // Current position is already safe, keep it
            currentY
        }

        val newPosition = DotPosition(currentPos?.x ?: 0, newY)
        Log.d(TAG, "adjustPositionForKeyboard: FINAL - currentY=$currentY, safeZoneY=$safeZoneY, newY=$newY, willMove=${currentY > safeZoneY}")
        animatePosition(newPosition)
    }

    private fun constrainPositionWithKeyboard(x: Int, y: Int): Pair<Int, Int> {
        // First constrain to screen bounds
        val (boundedX, boundedY) = viewManager.constrainPositionToBounds(x, y)

        // If keyboard is not visible, return screen-bounded position
        if (!keyboardVisible || currentKeyboardHeight == 0) {
            return Pair(boundedX, boundedY)
        }

        // Apply keyboard constraints
        val screenHeight = resources.displayMetrics.heightPixels
        val dotSize = (AppConstants.DOT_SIZE_DP * resources.displayMetrics.density).toInt()
        val margin = (dotSize * AppConstants.KEYBOARD_MARGIN_MULTIPLIER).toInt()

        // Calculate the maximum Y position allowed (above keyboard with margin)
        val keyboardTop = screenHeight - currentKeyboardHeight
        val maxY = keyboardTop - dotSize - margin

        // Constrain Y to be above the keyboard area
        val constrainedY = boundedY.coerceAtMost(maxY)

        return Pair(boundedX, constrainedY)
    }

    private fun performRescueAction() {
        try {
            // Single back press to exit current screen
            BackHomeAccessibilityService.instance?.performBackAction()

            // Immediately go to home screen
            Handler(Looper.getMainLooper()).postDelayed({
                BackHomeAccessibilityService.instance?.performHomeAction()
            }, AppConstants.ACCESSIBILITY_BACK_DELAY_MS)

        } catch (e: Exception) {
            // Fallback: just go home
            BackHomeAccessibilityService.instance?.performHomeAction()
        }
    }

    private fun cancelPositionAnimation() {
        positionAnimator?.cancel()
        positionAnimator = null
    }

    private fun animatePosition(targetPosition: DotPosition, duration: Long = 250L) {
        val startPosition = viewManager.getCurrentPosition() ?: return
        if (startPosition == targetPosition) {
            serviceScope.launch { settingsRepository.setPosition(targetPosition) }
            return
        }

        cancelPositionAnimation()

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = duration
        animator.interpolator = AccelerateDecelerateInterpolator()

        animator.addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            val currentX = (startPosition.x + ((targetPosition.x - startPosition.x) * fraction)).toInt()
            val currentY = (startPosition.y + ((targetPosition.y - startPosition.y) * fraction)).toInt()
            viewManager.updatePosition(DotPosition(currentX, currentY))
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                viewManager.updatePosition(targetPosition)
                serviceScope.launch {
                    val settings = settingsRepository.getAllSettings().first()
                    val positionWithScreen = DotPosition(targetPosition.x, targetPosition.y, settings.screenWidth, settings.screenHeight)
                    settingsRepository.setPosition(positionWithScreen)
                }
                positionAnimator = null
            }

            override fun onAnimationCancel(animation: Animator) {
                positionAnimator = null
            }
        })

        positionAnimator = animator
        animator.start()
    }
}