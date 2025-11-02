package ch.heuscher.back_home_dot.service.overlay

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import ch.heuscher.back_home_dot.domain.model.Gesture
import ch.heuscher.back_home_dot.util.AppConstants

/**
 * Detects and processes touch gestures on the overlay.
 * Handles gesture recognition and delegates to appropriate handlers.
 */
class GestureDetector(
    private val viewConfiguration: ViewConfiguration
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    // Gesture state
    private var clickCount = 0
    private var lastClickTime = 0L
    private var isLongPress = false
    private var hasMoved = false
    private var initialX = 0f
    private var initialY = 0f

    // Configuration
    private val touchSlop = viewConfiguration.scaledTouchSlop
    private val doubleTapTimeout = AppConstants.GESTURE_DOUBLE_TAP_TIMEOUT_MS
    private val longPressTimeout = AppConstants.GESTURE_LONG_PRESS_TIMEOUT_MS

    // Callbacks
    var onGesture: ((Gesture) -> Unit)? = null
    var onPositionChanged: ((Int, Int) -> Unit)? = null

    // Runnables
    private val longPressRunnable = Runnable {
        isLongPress = true
        onGesture?.invoke(Gesture.LONG_PRESS)
    }

    private val clickTimeoutRunnable = Runnable {
        processClicks()
    }

    /**
     * Processes touch events and detects gestures.
     */
    fun onTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleActionDown(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                return handleActionMove(event)
            }
            MotionEvent.ACTION_UP -> {
                handleActionUp(event)
                return true
            }
        }
        return false
    }

    private fun handleActionDown(event: MotionEvent) {
        initialX = event.rawX
        initialY = event.rawY
        isLongPress = false
        hasMoved = false

        // Start long press timer
        mainHandler.postDelayed(longPressRunnable, longPressTimeout)
    }

    private fun handleActionMove(event: MotionEvent): Boolean {
        val deltaX = event.rawX - initialX
        val deltaY = event.rawY - initialY

        if (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop) {
            hasMoved = true
            mainHandler.removeCallbacks(longPressRunnable) // Cancel long press

            // Notify position change
            onPositionChanged?.invoke(deltaX.toInt(), deltaY.toInt())
            onGesture?.invoke(Gesture.DRAG_MOVE)
        }
        return true
    }

    private fun handleActionUp(event: MotionEvent) {
        mainHandler.removeCallbacks(longPressRunnable)

        if (hasMoved) {
            // Drag ended
            onGesture?.invoke(Gesture.DRAG_END)
        } else if (!isLongPress) {
            // Handle click
            handleClick()
        }
    }

    private fun handleClick() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastClickTime < doubleTapTimeout) {
            clickCount++
            mainHandler.removeCallbacks(clickTimeoutRunnable)
        } else {
            clickCount = 1
        }

        lastClickTime = currentTime
        mainHandler.postDelayed(clickTimeoutRunnable, doubleTapTimeout)
    }

    private fun processClicks() {
        val gesture = when (clickCount) {
            1 -> Gesture.TAP
            2 -> Gesture.DOUBLE_TAP
            3 -> Gesture.TRIPLE_TAP
            4 -> Gesture.QUADRUPLE_TAP
            else -> return
        }

        onGesture?.invoke(gesture)
        clickCount = 0
    }

    /**
     * Cancels any pending gesture detection.
     */
    fun cancelPendingGestures() {
        mainHandler.removeCallbacks(longPressRunnable)
        mainHandler.removeCallbacks(clickTimeoutRunnable)
        clickCount = 0
        isLongPress = false
        hasMoved = false
    }
}