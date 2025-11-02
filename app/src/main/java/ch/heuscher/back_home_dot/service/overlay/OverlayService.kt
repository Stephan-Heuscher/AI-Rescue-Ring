package ch.heuscher.back_home_dot.service.overlay

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

    // Debounce keyboard adjustments
    private var lastKeyboardAdjustmentTime = 0L
    private val KEYBOARD_ADJUSTMENT_DEBOUNCE_MS = 500L

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
        unregisterReceiver(settingsReceiver)
        unregisterReceiver(keyboardReceiver)
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
            settingsRepository.setScreenWidth(size.x)
            settingsRepository.setScreenHeight(size.y)
            Log.d(TAG, "initializeScreenDimensions: screenWidth=${size.x}, screenHeight=${size.y}")
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
            viewManager.updatePosition(settings.position)
        }
    }

    private fun handleGesture(gesture: Gesture) {
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            val mode = if (settings.rescueRingEnabled) OverlayMode.RESCUE_RING else OverlayMode.NORMAL

            when (gesture) {
                Gesture.TAP -> handleTap(mode)
                Gesture.DOUBLE_TAP -> handleDoubleTap(mode)
                Gesture.TRIPLE_TAP -> handleTripleTap(mode)
                Gesture.QUADRUPLE_TAP -> handleQuadrupleTap()
                Gesture.LONG_PRESS -> handleLongPress()
                else -> { /* Other gestures handled elsewhere */ }
            }
        }
    }

    private fun handleTap(mode: OverlayMode) {
        when (mode) {
            OverlayMode.NORMAL -> BackHomeAccessibilityService.instance?.performBackAction()
            OverlayMode.RESCUE_RING -> performRescueAction()
        }
    }

    private fun handleDoubleTap(mode: OverlayMode) {
        if (mode == OverlayMode.NORMAL) {
            BackHomeAccessibilityService.instance?.performRecentsAction()
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

        val (constrainedX, constrainedY) = viewManager.constrainPositionToBounds(newX, newY)
        val newPosition = DotPosition(constrainedX, constrainedY)
        viewManager.updatePosition(newPosition)

        // Save new position
        serviceScope.launch {
            settingsRepository.setPosition(newPosition)
        }
    }

    private fun checkKeyboardAvoidance() {
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            if (!settings.keyboardAvoidanceEnabled) return@launch

            val isVisible = keyboardDetector.isKeyboardVisible()
            if (isVisible) {
                // Save position before adjusting
                if (positionBeforeKeyboard == null) {
                    positionBeforeKeyboard = viewManager.getCurrentPosition()
                }
                adjustPositionForKeyboard(settings)
            } else {
                // Restore position when keyboard hides
                positionBeforeKeyboard?.let { originalPos ->
                    viewManager.updatePosition(originalPos)
                    positionBeforeKeyboard = null
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

        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            Log.d(TAG, "handleKeyboardChange: keyboardAvoidanceEnabled=${settings.keyboardAvoidanceEnabled}")
            if (!settings.keyboardAvoidanceEnabled) return@launch

            if (visible) {
                // Save position before adjusting
                if (positionBeforeKeyboard == null) {
                    positionBeforeKeyboard = viewManager.getCurrentPosition()
                    Log.d(TAG, "handleKeyboardChange: saved position=$positionBeforeKeyboard")
                }
                adjustPositionForKeyboard(settings, height)
            } else {
                // Restore position when keyboard hides
                positionBeforeKeyboard?.let { originalPos ->
                    Log.d(TAG, "handleKeyboardChange: restoring position=$originalPos")
                    viewManager.updatePosition(originalPos)
                    positionBeforeKeyboard = null
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

        // Calculate keyboard top and safe zone (2 diameters above keyboard)
        val keyboardTop = screenHeight - height
        val safeZoneY = keyboardTop - 2 * dotSize - margin

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
        viewManager.updatePosition(newPosition)
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
}