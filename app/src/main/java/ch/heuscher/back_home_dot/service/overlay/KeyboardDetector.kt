package ch.heuscher.back_home_dot.service.overlay

import android.view.inputmethod.InputMethodManager
import android.view.WindowManager
import ch.heuscher.back_home_dot.util.AppConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Detects keyboard visibility and provides keyboard height information.
 * Separated from OverlayService to follow single responsibility principle.
 */
class KeyboardDetector(
    private val windowManager: WindowManager,
    private val inputMethodManager: InputMethodManager
) {

    /**
     * Returns a flow that emits the current keyboard visibility state.
     * This is a simplified version - in a full implementation, this would
     * use more sophisticated detection methods.
     */
    fun observeKeyboardVisibility(): Flow<Boolean> = flow {
        // For now, emit false as default
        // In a full implementation, this would monitor actual keyboard state
        emit(false)
    }

    /**
     * Checks if keyboard is currently visible using available system APIs.
     */
    fun isKeyboardVisible(): Boolean {
        return try {
            // Use keyboard insets for reliable detection
            var keyboardInsetHeight = 0
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val windowMetrics = windowManager.currentWindowMetrics
                    val insets = windowMetrics.windowInsets
                    val imeInset = insets.getInsets(android.view.WindowInsets.Type.ime())
                    keyboardInsetHeight = imeInset.bottom
                } catch (e: Exception) {
                    // Fallback if insets API fails
                }
            }

            // Keyboard is visible if inset height > 0
            keyboardInsetHeight > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Estimates keyboard height based on screen dimensions.
     */
    fun estimateKeyboardHeight(screenHeight: Int): Int {
        return (screenHeight * AppConstants.KEYBOARD_HEIGHT_ESTIMATE_PERCENT).toInt()
    }

    /**
     * Gets the actual keyboard height if available.
     * Returns 0 if keyboard is not visible.
     * Only use this when keyboard is known to be visible.
     */
    fun getKeyboardHeight(screenHeight: Int): Int {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val windowMetrics = windowManager.currentWindowMetrics
                val insets = windowMetrics.windowInsets
                val imeInset = insets.getInsets(android.view.WindowInsets.Type.ime())
                if (imeInset.bottom > 0) {
                    return imeInset.bottom
                }
            } catch (e: Exception) {
                // Fall back to estimation only if keyboard should be visible
            }
        }

        // Only estimate if keyboard is actually visible
        return if (isKeyboardVisible()) {
            estimateKeyboardHeight(screenHeight)
        } else {
            0
        }
    }
}