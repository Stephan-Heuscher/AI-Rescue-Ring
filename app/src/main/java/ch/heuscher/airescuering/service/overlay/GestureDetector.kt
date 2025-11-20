package ch.heuscher.airescuering.service.overlay

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import ch.heuscher.airescuering.domain.model.Gesture
import ch.heuscher.airescuering.util.AppConstants

/**
 * Detects and processes touch gestures on the overlay.
 * Handles gesture recognition and delegates to appropriate handlers.
 */
class GestureDetector(
    private val viewConfiguration: ViewConfiguration
) {

    companion object {
        private const val TAG = "GestureDetector"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Gesture state
    private var clickCount = 0
    private var lastClickTime = 0L
    private var isLongPress = false
    private var isDragMode = false
    private var hasMoved = false
    private var initialX = 0f
    private var initialY = 0f
    private var lastX = 0f
    private var lastY = 0f

    // Configuration
    private val touchSlop = viewConfiguration.scaledTouchSlop
    private val doubleTapTimeout = AppConstants.GESTURE_DOUBLE_TAP_TIMEOUT_MS
    private val longPressTimeout = AppConstants.GESTURE_LONG_PRESS_TIMEOUT_MS

    // Mode configuration - set by OverlayService
    private var requiresLongPressToDrag = false
    private var positionLocked = false

    // Callbacks
    var onGesture: ((Gesture) -> Unit)? = null
    var onPositionChanged: ((Int, Int) -> Unit)? = null
    var onDragModeChanged: ((Boolean) -> Unit)? = null

    /**
     * Sets whether long-press is required to enable dragging.
     * Used for Safe-Home mode.
     */
    fun setRequiresLongPressToDrag(required: Boolean) {
        requiresLongPressToDrag = required
    }

    /**
     * Sets whether the position is locked (no dragging allowed at all).
     */
    fun setPositionLocked(locked: Boolean) {
        positionLocked = locked
    }

    // Runnables
    private val longPressRunnable = Runnable {
        isLongPress = true
        // Only activate drag mode if long-press is required for dragging (Safe-Home mode)
        // But don't show halo yet - wait for user to move finger
        if (requiresLongPressToDrag) {
            isDragMode = true
            // Don't invoke onDragModeChanged here - wait for movement
        }
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
        lastX = initialX
        lastY = initialY
        isLongPress = false
        hasMoved = false

        android.util.Log.d(TAG, "ACTION_DOWN at (${event.rawX}, ${event.rawY}), touchSlop=$touchSlop")

        // Start long press timer
        mainHandler.postDelayed(longPressRunnable, longPressTimeout)
    }

    private fun handleActionMove(event: MotionEvent): Boolean {
        val totalDeltaX = event.rawX - initialX
        val totalDeltaY = event.rawY - initialY

        // If position is locked, don't allow any dragging
        if (positionLocked) {
            return true
        }

        if (!hasMoved) {
            android.util.Log.d(TAG, "ACTION_MOVE: delta=(${totalDeltaX}, ${totalDeltaY}), touchSlop=$touchSlop, requiresLongPress=$requiresLongPressToDrag, isDragMode=$isDragMode")

            if (requiresLongPressToDrag) {
                // Safe-Home mode: Only allow dragging if in drag mode (long-press detected)
                if (isDragMode) {
                    if (Math.abs(totalDeltaX) > touchSlop || Math.abs(totalDeltaY) > touchSlop) {
                        hasMoved = true
                        android.util.Log.d(TAG, "ACTION_MOVE: Drag started in Safe-Home mode")
                        // Now show the halo when user starts moving
                        onDragModeChanged?.invoke(true)
                        onGesture?.invoke(Gesture.DRAG_START)
                    } else {
                        return true
                    }
                } else {
                    // Not in drag mode yet, check if user moved too much (cancel long press)
                    if (Math.abs(totalDeltaX) > touchSlop || Math.abs(totalDeltaY) > touchSlop) {
                        android.util.Log.d(TAG, "ACTION_MOVE: Movement exceeds touchSlop, canceling long press")
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    return true
                }
            } else {
                // Standard/Navi mode: Allow immediate dragging
                if (Math.abs(totalDeltaX) > touchSlop || Math.abs(totalDeltaY) > touchSlop) {
                    hasMoved = true
                    android.util.Log.d(TAG, "ACTION_MOVE: Drag started in STANDARD mode (delta exceeded touchSlop)")
                    mainHandler.removeCallbacks(longPressRunnable) // Cancel long press
                    onGesture?.invoke(Gesture.DRAG_START)
                } else {
                    return true
                }
            }
        }

        val deltaX = event.rawX - lastX
        val deltaY = event.rawY - lastY

        onPositionChanged?.invoke(deltaX.toInt(), deltaY.toInt())
        onGesture?.invoke(Gesture.DRAG_MOVE)

        lastX = event.rawX
        lastY = event.rawY
        return true
    }

    private fun handleActionUp(event: MotionEvent) {
        mainHandler.removeCallbacks(longPressRunnable)

        android.util.Log.d(TAG, "ACTION_UP: hasMoved=$hasMoved, isLongPress=$isLongPress")

        if (hasMoved) {
            // Drag ended
            android.util.Log.d(TAG, "ACTION_UP: Drag ended")
            onGesture?.invoke(Gesture.DRAG_END)
        } else if (!isLongPress) {
            // Handle click
            android.util.Log.d(TAG, "ACTION_UP: Click detected, calling handleClick()")
            handleClick()
        } else {
            android.util.Log.d(TAG, "ACTION_UP: Long press detected, no click")
        }

        // Reset drag mode
        if (isDragMode) {
            isDragMode = false
            onDragModeChanged?.invoke(false)
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

        android.util.Log.d(TAG, "processClicks: Firing $gesture gesture (clickCount=$clickCount)")
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