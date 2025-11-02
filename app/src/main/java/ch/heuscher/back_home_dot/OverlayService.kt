package ch.heuscher.back_home_dot

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var displayManager: DisplayManager
    private var floatingView: View? = null
    private var floatingDot: View? = null
    private var rescueRing: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private lateinit var settings: OverlaySettings

    // System-defined gesture timeouts and slop
    private var longPressTimeout: Long = 500L
    private var doubleTapTimeout: Long = 300L
    private var touchSlop: Int = 10

    // Touch event variables
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    // Gesture detection variables
    private var isLongPress = false
    private var hasMoved = false
    private var clickCount = 0
    private var lastClickTime = 0L

    // Handlers for long press and click events
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val clickHandler = Handler(Looper.getMainLooper())

    // Listener for display changes (e.g., rotation)
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            handleConfigurationChange()
        }
    }

    // Keyboard avoidance variables
    private var keyboardHeight = 0
    private var originalY = 0
    private var isKeyboardVisible = false
    private val keyboardCheckHandler = Handler(Looper.getMainLooper())
    private val keyboardCheckRunnable = object : Runnable {
        override fun run() {
            checkKeyboardVisibility()
            keyboardCheckHandler.postDelayed(this, 100) // Check every 100ms for responsiveness
        }
    }

    // Runnable for long press action
    private val longPressRunnable = Runnable {
        isLongPress = true
        performHapticFeedback()
        BackHomeAccessibilityService.instance?.performHomeAction()
    }

    // Runnable for click timeout
    private val clickTimeoutRunnable = Runnable {
        handleClicks()
    }

    // Broadcast receiver for settings changes
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_UPDATE_SETTINGS) {
                applyColorSettings()
                // Re-setup keyboard detection in case the setting changed
                keyboardCheckHandler.removeCallbacksAndMessages(null)
                setupKeyboardDetection()
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_SETTINGS = "ch.heuscher.back_home_dot.UPDATE_SETTINGS"
    }

    /**
     * Performs haptic feedback for gestures.
     */
    private fun performHapticFeedback() {
        floatingView?.performHapticFeedback(
            android.view.HapticFeedbackConstants.VIRTUAL_KEY,
            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    /**
     * Sets up keyboard detection using polling approach.
     */
    private fun setupKeyboardDetection() {
        if (!settings.keyboardAvoidanceEnabled) return

        // Start polling for keyboard visibility
        keyboardCheckHandler.post(keyboardCheckRunnable)
    }
    private fun performRescueAction() {
        try {
            // Single back press to exit current screen, then immediately go home
            // This provides clean UX without multiple jarring screen transitions
            BackHomeAccessibilityService.instance?.performBackAction()

            // Immediately go to home screen (no delay needed)
            Handler(Looper.getMainLooper()).postDelayed({
                BackHomeAccessibilityService.instance?.performHomeAction()
            }, 100) // Small delay to let back action complete

            performHapticFeedback()
        } catch (e: Exception) {
            // Fallback: just go home
            BackHomeAccessibilityService.instance?.performHomeAction()
            performHapticFeedback()
        }
    }

    /**
     * Checks if the soft keyboard is visible and adjusts dot position accordingly.
     */
    private fun checkKeyboardVisibility() {
        if (!settings.keyboardAvoidanceEnabled) return

        try {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val screenHeight = getUsableScreenSize().y

            // Method 1: Check if keyboard is active and estimate height
            val isAcceptingText = inputMethodManager.isAcceptingText
            val hasActiveInput = inputMethodManager.isActive

            // Method 2: Try to get keyboard insets (Android R+)
            var keyboardInsetHeight = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val windowMetrics = windowManager.currentWindowMetrics
                    val insets = windowMetrics.windowInsets
                    val imeInset = insets.getInsets(android.view.WindowInsets.Type.ime())
                    keyboardInsetHeight = imeInset.bottom
                } catch (e: Exception) {
                    // Fallback if insets API fails
                }
            }

            // Determine if keyboard is visible
            val isKeyboardNowVisible = if (keyboardInsetHeight > 0) {
                // Use insets data if available (most reliable)
                true
            } else if (isAcceptingText && hasActiveInput) {
                // Fallback: assume keyboard is visible if input is active
                // We'll estimate height based on typical keyboard size
                true
            } else {
                false
            }

            if (isKeyboardNowVisible != isKeyboardVisible) {
                isKeyboardVisible = isKeyboardNowVisible

                if (isKeyboardVisible) {
                    // Keyboard appeared
                    originalY = params?.y ?: 0

                    // Calculate keyboard height
                    keyboardHeight = if (keyboardInsetHeight > 0) {
                        keyboardInsetHeight
                    } else {
                        // Estimate keyboard height as 35-40% of screen
                        (screenHeight * 0.38).toInt()
                    }

                    // Only move the dot if it would be covered by the keyboard
                    val dotSize = (48 * resources.displayMetrics.density).toInt()
                    val dotBottom = originalY + dotSize
                    val keyboardTop = screenHeight - keyboardHeight

                    if (dotBottom > keyboardTop) {
                        // Dot would be covered by keyboard, move it up
                        val marginAboveKeyboard = (dotSize * 1.5).toInt() // 1.5 dot diameters above keyboard
                        val newY = (keyboardTop - dotSize - marginAboveKeyboard).coerceAtLeast(0)
                        params?.y = newY
                        windowManager.updateViewLayout(floatingView, params)
                    }
                } else {
                    // Keyboard disappeared - move dot back to original position
                    params?.y = originalY
                    windowManager.updateViewLayout(floatingView, params)
                    keyboardHeight = 0
                }
            }
        } catch (e: Exception) {
            // If keyboard detection fails completely, disable it to prevent crashes
            settings.keyboardAvoidanceEnabled = false
        }
    }

    /**
     * Applies color settings to the floating dot or shows rescue ring.
     */
    private fun applyColorSettings() {
        if (settings.rescueRingEnabled) {
            // Show rescue ring
            floatingDot?.visibility = View.GONE
            rescueRing?.visibility = View.VISIBLE
        } else {
            // Show normal colored dot
            floatingDot?.visibility = View.VISIBLE
            rescueRing?.visibility = View.GONE

            val dotView = floatingDot
            dotView?.let {
                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.OVAL

                val colorWithAlpha = settings.getColorWithAlpha()
                drawable.setColor(colorWithAlpha)
                drawable.setStroke(3, android.graphics.Color.WHITE)
                it.background = drawable
            }
        }
    }

    /**
     * Gets the usable screen size.
     */
    private fun getUsableScreenSize(): Point {
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

    /**
     * Constrains the floating dot's position to the screen bounds.
     */
    private fun constrainPositionToBounds(x: Int, y: Int): Pair<Int, Int> {
        val size = getUsableScreenSize()
        val screenWidth = size.x
        val screenHeight = size.y
        val dotSize = (48 * resources.displayMetrics.density).toInt()
        val constrainedX = x.coerceIn(0, screenWidth - dotSize)
        val constrainedY = y.coerceIn(0, screenHeight - dotSize)
        return Pair(constrainedX, constrainedY)
    }

    /**
     * Transforms the floating dot's position on screen rotation.
     */
    private fun transformPosition(
        x: Int, y: Int,
        fromWidth: Int, fromHeight: Int, fromRotation: Int,
        toRotation: Int
    ): Pair<Int, Int> {
        val rotationDiff = (toRotation - fromRotation + 4) % 4
        if (rotationDiff == 0) return Pair(x, y)

        val dotSize = (48 * resources.displayMetrics.density).toInt()
        var centerX = x + dotSize / 2
        var centerY = y + dotSize / 2
        var currentWidth = fromWidth
        var currentHeight = fromHeight

        repeat(rotationDiff) {
            val tempCenterX = centerX
            centerX = centerY
            centerY = currentWidth - tempCenterX
            val temp = currentWidth
            currentWidth = currentHeight
            currentHeight = temp
        }

        val newX = centerX - dotSize / 2
        val newY = centerY - dotSize / 2
        return Pair(newX, newY)
    }

    /**
     * Handles configuration changes, such as screen rotation.
     */
    private fun handleConfigurationChange() {
        params?.let { layoutParams ->
            val size = getUsableScreenSize()
            val newWidth = size.x
            val newHeight = size.y
            @Suppress("DEPRECATION")
            val newRotation = windowManager.defaultDisplay.rotation

            if (newWidth != settings.screenWidth || newHeight != settings.screenHeight || newRotation != settings.rotation) {
                val (transformedX, transformedY) = transformPosition(
                    settings.positionX,
                    settings.positionY,
                    settings.screenWidth,
                    settings.screenHeight,
                    settings.rotation,
                    newRotation
                )
                val (constrainedX, constrainedY) = constrainPositionToBounds(transformedX, transformedY)

                layoutParams.x = constrainedX
                layoutParams.y = constrainedY
                windowManager.updateViewLayout(floatingView, layoutParams)

                settings.screenWidth = newWidth
                settings.screenHeight = newHeight
                settings.rotation = newRotation
                settings.positionX = constrainedX
                settings.positionY = constrainedY
            }
        }
    }

    /**
     * Handles click gestures.
     */
    private fun handleClicks() {
        when (clickCount) {
            1 -> BackHomeAccessibilityService.instance?.performBackAction()
            2 -> BackHomeAccessibilityService.instance?.performRecentsAction()
            3 -> BackHomeAccessibilityService.instance?.performRecentsOverviewAction()
            4 -> openMainActivity()
        }
        if (clickCount > 0) performHapticFeedback()
        clickCount = 0
    }

    /**
     * Opens the main activity.
     */
    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onCreate() {
        super.onCreate()

        settings = OverlaySettings(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        LocalBroadcastManager.getInstance(this).registerReceiver(settingsReceiver, IntentFilter(ACTION_UPDATE_SETTINGS))

        // Set up keyboard avoidance detection
        setupKeyboardDetection()

        val viewConfig = ViewConfiguration.get(this)
        longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        touchSlop = viewConfig.scaledTouchSlop

        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        floatingDot = floatingView?.findViewById<View>(R.id.floating_dot)
        rescueRing = floatingView?.findViewById<TextView>(R.id.rescue_ring)
        applyColorSettings()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val size = getUsableScreenSize()
        val (constrainedX, constrainedY) = constrainPositionToBounds(settings.positionX, settings.positionY)
        settings.screenWidth = size.x
        settings.screenHeight = size.y
        @Suppress("DEPRECATION")
        settings.rotation = windowManager.defaultDisplay.rotation
        settings.positionX = constrainedX
        settings.positionY = constrainedY

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = constrainedX
            y = constrainedY
        }

        windowManager.addView(floatingView, params)
        setupTouchListener()
    }

    /**
     * Sets up the touch listener for the floating view.
     */
    private fun setupTouchListener() {
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isLongPress = false
                    hasMoved = false

                    if (settings.rescueRingEnabled) {
                        // In rescue-ring mode, start a short timer for rescue action
                        longPressHandler.postDelayed(longPressRunnable, 200L) // Short delay for rescue action
                    } else {
                        // Normal mode - start long press timer
                        longPressHandler.postDelayed(longPressRunnable, longPressTimeout)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Allow dragging in both modes
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop) {
                        hasMoved = true
                        longPressHandler.removeCallbacks(longPressRunnable) // Cancel any pending action
                        val (constrainedX, constrainedY) = constrainPositionToBounds(initialX + deltaX.toInt(), initialY + deltaY.toInt())
                        params?.x = constrainedX
                        params?.y = constrainedY
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (hasMoved) {
                        // Save new position after drag
                        settings.positionX = params?.x ?: 0
                        settings.positionY = params?.y ?: 0
                    } else if (!isLongPress) {
                        if (settings.rescueRingEnabled) {
                            // In rescue-ring mode, perform rescue action on quick tap
                            performRescueAction()
                        } else {
                            // Normal mode click handling
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime < doubleTapTimeout) {
                                clickCount++
                                clickHandler.removeCallbacks(clickTimeoutRunnable)
                            } else {
                                clickCount = 1
                            }
                            lastClickTime = currentTime
                            clickHandler.postDelayed(clickTimeoutRunnable, doubleTapTimeout)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.unregisterDisplayListener(displayListener)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(settingsReceiver)
        floatingView?.let { windowManager.removeView(it) }

        // Clean up keyboard detection
        keyboardCheckHandler.removeCallbacksAndMessages(null)

        longPressHandler.removeCallbacks(longPressRunnable)
        clickHandler.removeCallbacks(clickTimeoutRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
