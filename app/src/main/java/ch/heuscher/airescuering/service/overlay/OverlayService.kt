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
import android.util.Log
import android.view.View
import ch.heuscher.airescuering.AIHelperActivity
import ch.heuscher.airescuering.BackHomeAccessibilityService
import ch.heuscher.airescuering.MainActivity
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
import kotlinx.coroutines.withContext

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
    private lateinit var chatOverlayManager: ChatOverlayManager
    private lateinit var gestureDetector: GestureDetector

    // Specialized components
    private lateinit var keyboardManager: KeyboardManager
    private lateinit var orientationHandler: OrientationHandler

    // Service scope for coroutines
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // State tracking
    private var isUserDragging = false
    private var isOrientationChanging = false
    private var isChatVisible = false

    // Handler for delayed updates
    private val updateHandler = Handler(Looper.getMainLooper())

    // Broadcast receiver for settings changes
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConstants.ACTION_UPDATE_SETTINGS) {
                serviceScope.launch {
                    // Settings updated - gesture mode will be updated in observeSettings
                    Log.d(TAG, "Settings changed via broadcast")
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

    override fun onCreate() {
        super.onCreate()

        // Initialize service locator
        ServiceLocator.initialize(this)

        // Get core dependencies
        settingsRepository = ServiceLocator.settingsRepository
        gestureDetector = ServiceLocator.gestureDetector
        orientationHandler = ServiceLocator.orientationHandler

        // Create chat overlay manager
        chatOverlayManager = ChatOverlayManager(
            context = this,
            windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager,
            scope = serviceScope
        )

        // Set up overlay hide callback
        chatOverlayManager.onHideOverlay = {
            hideOverlayForCommand()
        }

        // Create specialized components
        keyboardManager = ServiceLocator.createKeyboardManager(
            context = this,
            onAdjustPosition = { position -> /* Not used with chat overlay */ },
            getCurrentPosition = { DotPosition(0, 0) }, // Not used with chat overlay
            getCurrentRotation = { orientationHandler.getCurrentRotation() },
            getUsableScreenSize = { orientationHandler.getUsableScreenSize() },
            getSettings = { settingsRepository.getAllSettings().first() },
            isUserDragging = { isUserDragging }
        )

        // Create overlay view
        chatOverlayManager.createOverlay()

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
        serviceScope.cancel()
        updateHandler.removeCallbacksAndMessages(null)

        unregisterReceiver(settingsReceiver)
        unregisterReceiver(keyboardReceiver)
        unregisterReceiver(configurationReceiver)

        chatOverlayManager.removeOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupGestureCallbacks() {
        gestureDetector.onGesture = { gesture ->
            handleGesture(gesture)
        }

        gestureDetector.onPositionChanged = { deltaX, deltaY ->
            // Position changes not needed for full-screen chat overlay
        }

        gestureDetector.onDragModeChanged = { enabled ->
            // Drag mode not needed for chat overlay
        }

        val listener = View.OnTouchListener { _, event ->
            gestureDetector.onTouch(event)
        }

        chatOverlayManager.setRingTouchListener(listener)
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
                Log.d(TAG, "observeSettings: Settings changed, tapBehavior=${settings.tapBehavior}")
                updateGestureMode(settings.tapBehavior)
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


    private fun handleGesture(gesture: Gesture) {
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
        // Single tap shows the chat overlay
        if (isChatVisible) {
            chatOverlayManager.hideChat()
            isChatVisible = false
        } else {
            chatOverlayManager.showChat()
            isChatVisible = true
        }
    }

    private fun handleQuadrupleTap() {
        Log.d(TAG, "handleQuadrupleTap: 4+ taps detected, switching to main app")

        serviceScope.launch {
            try {
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
        Log.d(TAG, "Long press detected on rescue ring")
        // Could be used for future functionality
    }

    private fun hideOverlayForCommand() {
        Log.d(TAG, "hideOverlayForCommand: Hiding overlay for AI command execution")
        chatOverlayManager.hideOverlay()

        // Show overlay again after a delay (command execution complete)
        updateHandler.postDelayed({
            chatOverlayManager.showOverlay()
            if (isChatVisible) {
                chatOverlayManager.showChat()
            }
        }, 2000)
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



    private fun handleKeyboardBroadcast(visible: Boolean, height: Int) {
        Log.d(TAG, "Keyboard broadcast: visible=$visible, height=$height")
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            keyboardManager.handleKeyboardChange(visible, height, settings)
        }
    }

    private fun handleOrientationChange() {
        Log.d(TAG, "Configuration changed, handling orientation")

        serviceScope.launch {
            val newSize = orientationHandler.getUsableScreenSize()
            val newRotation = orientationHandler.getCurrentRotation()

            // Update screen dimensions in settings
            settingsRepository.setScreenWidth(newSize.x)
            settingsRepository.setScreenHeight(newSize.y)
            settingsRepository.setRotation(newRotation)

            Log.d(TAG, "Orientation change complete: rotation=$newRotation, size=${newSize.x}x${newSize.y}")
        }
    }
}
