package ch.heuscher.back_home_dot.service.overlay

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
 * Refactored OverlayService - now focused only on lifecycle management.
 * Delegates specific responsibilities to dedicated components.
 */
class OverlayService : Service() {

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

    // Broadcast receiver for settings changes
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConstants.ACTION_UPDATE_SETTINGS) {
                updateOverlayAppearance()
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

        // Register broadcast receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                settingsReceiver,
                IntentFilter(AppConstants.ACTION_UPDATE_SETTINGS),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                settingsReceiver,
                IntentFilter(AppConstants.ACTION_UPDATE_SETTINGS)
            )
        }

        // Start observing settings changes
        observeSettings()

        // Start keyboard monitoring
        keyboardHandler.post(keyboardRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up
        serviceScope.cancel()
        keyboardHandler.removeCallbacks(keyboardRunnable)
        unregisterReceiver(settingsReceiver)
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
                adjustPositionForKeyboard(settings)
            }
        }
    }

    private fun adjustPositionForKeyboard(settings: ch.heuscher.back_home_dot.domain.model.OverlaySettings) {
        val currentPos = viewManager.getCurrentPosition() ?: return
        val keyboardHeight = keyboardDetector.getKeyboardHeight(settings.screenHeight)
        val dotSize = (AppConstants.DOT_SIZE_DP * resources.displayMetrics.density).toInt()

        // Check if dot would be covered by keyboard
        val dotBottom = currentPos.y + dotSize
        val keyboardTop = settings.screenHeight - keyboardHeight

        if (dotBottom > keyboardTop) {
            // Move dot up
            val margin = (dotSize * AppConstants.KEYBOARD_MARGIN_MULTIPLIER).toInt()
            val newY = (keyboardTop - dotSize - margin).coerceAtLeast(0)
            val newPosition = currentPos.copy(y = newY)
            viewManager.updatePosition(newPosition)
        }
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