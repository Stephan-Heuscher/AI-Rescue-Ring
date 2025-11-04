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
 * Refactored OverlayService with clear separation of concerns.
 * Delegates keyboard management, animations, and orientation handling to specialized components.
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        private const val ORIENTATION_CHANGE_DELAY_MS = 500L
    }

    // Core dependencies
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewManager: OverlayViewManager
    private lateinit var gestureDetector: GestureDetector

    // Specialized components
    private lateinit var keyboardManager: KeyboardManager
    private lateinit var positionAnimator: PositionAnimator
    private lateinit var orientationHandler: OrientationHandler

    // Service scope for coroutines
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // State tracking
    private var isUserDragging = false
    private var isOrientationChanging = false

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
            getUsableScreenSize = { orientationHandler.getUsableScreenSize() }
        )

        positionAnimator = ServiceLocator.createPositionAnimator(
            onPositionUpdate = { position -> viewManager.updatePosition(position) },
            onAnimationComplete = { position -> onAnimationComplete(position) }
        )

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

    private fun registerBroadcastReceivers() {
        val settingsFilter = IntentFilter(AppConstants.ACTION_UPDATE_SETTINGS)
        val keyboardFilter = IntentFilter(AppConstants.ACTION_UPDATE_KEYBOARD)
        val configFilter = IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, settingsFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(keyboardReceiver, keyboardFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(configurationReceiver, configFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(settingsReceiver, settingsFilter)
            @Suppress("DEPRECATION")
            registerReceiver(keyboardReceiver, keyboardFilter)
            @Suppress("DEPRECATION")
            registerReceiver(configurationReceiver, configFilter)
        }
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
            val size = orientationHandler.getUsableScreenSize()
            val rotation = orientationHandler.getCurrentRotation()
            settingsRepository.setScreenWidth(size.x)
            settingsRepository.setScreenHeight(size.y)
            settingsRepository.setRotation(rotation)
            Log.d(TAG, "initializeScreenDimensions: width=${size.x}, height=${size.y}, rotation=$rotation")
        }
    }

    private fun updateOverlayAppearance() {
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            viewManager.updateAppearance(settings)

            val screenSize = orientationHandler.getUsableScreenSize()
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
            val settings = settingsRepository.getAllSettings().first()
            val mode = if (settings.rescueRingEnabled) OverlayMode.RESCUE_RING else OverlayMode.NORMAL

            when (gesture) {
                Gesture.TAP -> handleTap(mode)
                Gesture.DOUBLE_TAP -> handleDoubleTap(mode)
                Gesture.TRIPLE_TAP -> handleTripleTap(mode)
                Gesture.QUADRUPLE_TAP -> handleQuadrupleTap()
                Gesture.LONG_PRESS -> handleLongPress()
                else -> { /* No-op */ }
            }
        }
    }

    private fun handleTap(mode: OverlayMode) {
        when (mode) {
            OverlayMode.NORMAL -> {
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

    private fun onDragEnd() {
        serviceScope.launch {
            viewManager.getCurrentPosition()?.let { finalPos ->
                if (keyboardManager.keyboardVisible) {
                    val settings = settingsRepository.getAllSettings().first()
                    keyboardManager.handleKeyboardChange(
                        visible = true,
                        height = keyboardManager.currentKeyboardHeight,
                        settings = settings
                    )
                } else {
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
            settingsRepository.setPosition(positionWithScreen)
            settingsRepository.setScreenWidth(screenSize.x)
            settingsRepository.setScreenHeight(screenSize.y)
            settingsRepository.setRotation(rotation)
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

        // Hide overlay during transition
        viewManager.setVisibility(View.GONE)
        isOrientationChanging = true
        keyboardManager.setOrientationChanging(true)

        updateHandler.postDelayed({
            serviceScope.launch {
                val oldSettings = settingsRepository.getAllSettings().first()
                val newSize = orientationHandler.getUsableScreenSize()
                val newRotation = orientationHandler.getCurrentRotation()

                // Determine baseline state (use current settings)
                val baselinePosition = oldSettings.position
                val baselineWidth = if (oldSettings.screenWidth > 0) oldSettings.screenWidth else newSize.x
                val baselineHeight = if (oldSettings.screenHeight > 0) oldSettings.screenHeight else newSize.y
                val baselineRotation = oldSettings.rotation

                Log.d(TAG, "Orientation: old rot=$baselineRotation, new rot=$newRotation, old size=${baselineWidth}x${baselineHeight}")

                // Transform position if rotation changed
                if (newRotation != baselineRotation) {
                    val dotSizePx = (AppConstants.DOT_SIZE_DP * resources.displayMetrics.density).toInt()
                    val half = dotSizePx / 2

                    // Calculate center point
                    val centerX = baselinePosition.x + half
                    val centerY = baselinePosition.y + half
                    val centerPosition = DotPosition(centerX, centerY, baselineWidth, baselineHeight, baselineRotation)

                    // Transform center to new rotation
                    val transformedCenter = orientationHandler.transformPosition(
                        centerPosition, baselineWidth, baselineHeight, baselineRotation, newRotation
                    )

                    // Calculate new top-left position
                    val newTopLeftX = transformedCenter.x - half
                    val newTopLeftY = transformedCenter.y - half
                    val transformedPosition = DotPosition(newTopLeftX, newTopLeftY, newSize.x, newSize.y, newRotation)

                    Log.d(TAG, "Orientation transformed: (${baselinePosition.x},${baselinePosition.y}) -> ($newTopLeftX,$newTopLeftY)")
                    settingsRepository.setPosition(transformedPosition)
                }

                // Update screen dimensions
                settingsRepository.setScreenWidth(newSize.x)
                settingsRepository.setScreenHeight(newSize.y)
                settingsRepository.setRotation(newRotation)

                // Clear keyboard snapshot without restore
                keyboardManager.clearSnapshotForOrientationChange()

                // Restore visibility
                isOrientationChanging = false
                keyboardManager.setOrientationChanging(false)
                viewManager.setVisibility(View.VISIBLE)
            }
        }, ORIENTATION_CHANGE_DELAY_MS)
    }

    private fun performRescueAction() {
        try {
            BackHomeAccessibilityService.instance?.performBackAction()
            Handler(Looper.getMainLooper()).postDelayed({
                BackHomeAccessibilityService.instance?.performHomeAction()
            }, AppConstants.ACCESSIBILITY_BACK_DELAY_MS)
        } catch (e: Exception) {
            BackHomeAccessibilityService.instance?.performHomeAction()
        }
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
