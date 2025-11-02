package ch.heuscher.back_home_dot.domain.usecase

import kotlinx.coroutines.flow.Flow

/**
 * Use case for detecting keyboard visibility.
 * Abstracts the keyboard detection logic from the presentation layer.
 */
interface DetectKeyboardUseCase {
    /**
     * Returns a flow that emits true when keyboard is visible, false when hidden.
     */
    fun execute(): Flow<Boolean>
}

/**
 * Use case for handling gestures based on the current overlay mode.
 */
interface HandleGestureUseCase {
    /**
     * Executes the appropriate action for the given gesture in the specified mode.
     */
    suspend fun execute(gesture: ch.heuscher.back_home_dot.domain.model.Gesture, mode: ch.heuscher.back_home_dot.domain.model.OverlayMode)
}

/**
 * Use case for positioning the dot to avoid keyboard interference.
 */
interface PositionDotUseCase {
    /**
     * Calculates the optimal position for the dot when keyboard is visible.
     */
    fun calculateKeyboardAvoidancePosition(
        currentY: Int,
        keyboardTop: Int,
        dotSize: Int
    ): Int
}