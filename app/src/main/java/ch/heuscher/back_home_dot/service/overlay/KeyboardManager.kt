package ch.heuscher.back_home_dot.service.overlay

import android.content.Context
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log
import ch.heuscher.back_home_dot.domain.model.DotPosition
import ch.heuscher.back_home_dot.domain.model.OverlaySettings
import ch.heuscher.back_home_dot.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Manages keyboard avoidance functionality for the overlay dot.
 * Handles detection, position adjustment, and restoration when keyboard appears/disappears.
 */
class KeyboardManager(
    private val context: Context,
    private val keyboardDetector: KeyboardDetector,
    private val onAdjustPosition: (DotPosition) -> Unit,
    private val getCurrentPosition: () -> DotPosition?,
    private val getCurrentRotation: () -> Int,
    private val getUsableScreenSize: () -> Point,
    private val getSettings: suspend () -> OverlaySettings,
    private val isUserDragging: () -> Boolean
) {
    companion object {
        private const val TAG = "KeyboardManager"
        private const val KEYBOARD_ADJUSTMENT_DEBOUNCE_MS = 500L
        private const val KEYBOARD_RESTORE_DELAY_MS = 250L
    }

    // Keyboard state
    var keyboardVisible = false
        private set
    var currentKeyboardHeight = 0
        private set

    // Snapshot for restoring position when keyboard closes
    private data class KeyboardSnapshot(
        val position: DotPosition,
        val screenSize: Point,
        val rotation: Int
    )

    private var keyboardSnapshot: KeyboardSnapshot? = null
    private var isOrientationChanging = false
    private var lastKeyboardAdjustmentTime = 0L

    // Coroutine scope for async operations
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Handlers for scheduling
    private val keyboardHandler = Handler(Looper.getMainLooper())
    private var pendingKeyboardRestore: Runnable? = null

    // Periodic keyboard check
    private val keyboardCheckRunnable = object : Runnable {
        override fun run() {
            managerScope.launch {
                checkKeyboardAvoidance()
            }
            keyboardHandler.postDelayed(this, AppConstants.KEYBOARD_CHECK_INTERVAL_MS)
        }
    }

    /**
     * Start periodic keyboard monitoring
     */
    fun startMonitoring() {
        keyboardHandler.post(keyboardCheckRunnable)
    }

    /**
     * Stop keyboard monitoring and cleanup
     */
    fun stopMonitoring() {
        keyboardHandler.removeCallbacks(keyboardCheckRunnable)
        cancelPendingKeyboardRestore()
        managerScope.cancel()
    }

    /**
     * Notify manager that orientation is changing
     */
    fun setOrientationChanging(changing: Boolean) {
        isOrientationChanging = changing
        if (!changing && keyboardSnapshot != null) {
            // Orientation change complete, restore if needed
            scheduleKeyboardRestore()
        }
    }

    /**
     * Handle keyboard visibility change from broadcast or other source
     */
    fun handleKeyboardChange(visible: Boolean, height: Int, settings: OverlaySettings) {
        Log.d(TAG, "handleKeyboardChange: visible=$visible, height=$height")

        // Debounce rapid changes
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastKeyboardAdjustmentTime < KEYBOARD_ADJUSTMENT_DEBOUNCE_MS) {
            Log.d(TAG, "handleKeyboardChange: debounced")
            return
        }
        lastKeyboardAdjustmentTime = currentTime

        // Update state
        keyboardVisible = visible
        currentKeyboardHeight = height

        if (!settings.keyboardAvoidanceEnabled) {
            Log.d(TAG, "handleKeyboardChange: keyboard avoidance disabled")
            return
        }

        if (visible) {
            cancelPendingKeyboardRestore()
            captureKeyboardSnapshot()
            adjustPositionForKeyboard(settings, height)
        } else {
            if (isOrientationChanging) {
                Log.d(TAG, "handleKeyboardChange: orientation in progress, keeping snapshot")
            } else {
                scheduleKeyboardRestore()
            }
        }
    }

    /**
     * Check keyboard state and adjust position if needed
     */
    suspend fun checkKeyboardAvoidance() {
        if (isUserDragging()) return

        val settings = getSettings()
        if (!settings.keyboardAvoidanceEnabled) return

        val isVisible = keyboardDetector.isKeyboardVisible()
        if (isVisible) {
            cancelPendingKeyboardRestore()
            captureKeyboardSnapshot()
            adjustPositionForKeyboard(settings)
        } else {
            if (!isOrientationChanging) {
                scheduleKeyboardRestore()
            }
        }
    }

    /**
     * Constrain a position to avoid keyboard area if keyboard is visible
     */
    fun constrainPositionWithKeyboard(x: Int, y: Int, boundedX: Int, boundedY: Int): Pair<Int, Int> {
        // If keyboard is not visible, return screen-bounded position
        if (!keyboardVisible || currentKeyboardHeight == 0) {
            return Pair(boundedX, boundedY)
        }

        // Apply keyboard constraints
        val screenHeight = context.resources.displayMetrics.heightPixels
        val layoutSize = (AppConstants.OVERLAY_LAYOUT_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val buttonSize = (AppConstants.DOT_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val offset = (layoutSize - buttonSize) / 2
        val margin = (buttonSize * AppConstants.KEYBOARD_MARGIN_MULTIPLIER).toInt()

        // Calculate the maximum Y position allowed (above keyboard with margin)
        val keyboardTop = screenHeight - currentKeyboardHeight
        val maxY = keyboardTop - buttonSize - offset - margin

        // Constrain Y to be above the keyboard area
        val constrainedY = boundedY.coerceAtMost(maxY)

        return Pair(boundedX, constrainedY)
    }

    /**
     * Clear keyboard snapshot when orientation changes begin
     */
    fun clearSnapshotForOrientationChange() {
        cancelPendingKeyboardRestore()
        keyboardSnapshot = null
    }

    /**
     * Capture current position before keyboard adjustment
     */
    private fun captureKeyboardSnapshot() {
        if (keyboardSnapshot != null) return
        cancelPendingKeyboardRestore()

        val position = getCurrentPosition() ?: return
        val screenSize = getUsableScreenSize().let { Point(it.x, it.y) }
        val rotation = getCurrentRotation()

        keyboardSnapshot = KeyboardSnapshot(position, screenSize, rotation)
        Log.d(TAG, "captureKeyboardSnapshot: saved position=$position, size=$screenSize, rotation=$rotation")
    }

    /**
     * Clear keyboard snapshot and optionally restore position
     */
    private fun clearKeyboardSnapshot(restore: Boolean) {
        cancelPendingKeyboardRestore()
        val snapshot = keyboardSnapshot ?: return

        if (restore) {
            Log.d(TAG, "clearKeyboardSnapshot: restoring position=${snapshot.position}")
            onAdjustPosition(snapshot.position)
        } else {
            Log.d(TAG, "clearKeyboardSnapshot: dropping snapshot without restore")
        }

        keyboardSnapshot = null
    }

    /**
     * Schedule keyboard position restoration
     */
    private fun scheduleKeyboardRestore() {
        if (keyboardSnapshot == null) return
        if (pendingKeyboardRestore != null) return

        val runnable = Runnable {
            pendingKeyboardRestore = null
            clearKeyboardSnapshot(restore = true)
        }
        pendingKeyboardRestore = runnable
        keyboardHandler.postDelayed(runnable, KEYBOARD_RESTORE_DELAY_MS)
        Log.d(TAG, "scheduleKeyboardRestore: will restore in ${KEYBOARD_RESTORE_DELAY_MS}ms")
    }

    /**
     * Cancel pending keyboard position restoration
     */
    private fun cancelPendingKeyboardRestore() {
        pendingKeyboardRestore?.let {
            keyboardHandler.removeCallbacks(it)
            pendingKeyboardRestore = null
            Log.d(TAG, "cancelPendingKeyboardRestore: cancelled pending restore")
        }
    }

    /**
     * Adjust dot position to avoid keyboard
     */
    private fun adjustPositionForKeyboard(settings: OverlaySettings, keyboardHeight: Int = 0) {
        val screenHeight = if (settings.screenHeight > 0) {
            settings.screenHeight
        } else {
            context.resources.displayMetrics.heightPixels
        }

        val height = if (keyboardHeight > 0) {
            keyboardHeight
        } else {
            keyboardDetector.getKeyboardHeight(screenHeight)
        }

        val layoutSize = (AppConstants.OVERLAY_LAYOUT_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val buttonSize = (AppConstants.DOT_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val offset = (layoutSize - buttonSize) / 2
        val margin = (buttonSize * AppConstants.KEYBOARD_MARGIN_MULTIPLIER).toInt()

        Log.d(TAG, "adjustPositionForKeyboard: screenHeight=$screenHeight, keyboardHeight=$height, buttonSize=$buttonSize, margin=$margin")

        // Calculate keyboard top and safe zone (margin above keyboard)
        val keyboardTop = screenHeight - height
        val safeZoneY = keyboardTop - buttonSize - offset - margin

        Log.d(TAG, "adjustPositionForKeyboard: keyboardTop=$keyboardTop, safeZoneY=$safeZoneY")

        val currentPos = getCurrentPosition()
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

        onAdjustPosition(newPosition)
    }
}
