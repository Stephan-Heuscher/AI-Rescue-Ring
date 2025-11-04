package ch.heuscher.back_home_dot.service.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import ch.heuscher.back_home_dot.domain.model.DotPosition

/**
 * Handles smooth animations for dot position changes.
 * Provides cancellable animations with callbacks for position updates.
 */
class PositionAnimator(
    private val onPositionUpdate: (DotPosition) -> Unit,
    private val onAnimationComplete: (DotPosition) -> Unit
) {
    companion object {
        private const val DEFAULT_DURATION_MS = 250L
    }

    private var currentAnimator: ValueAnimator? = null

    /**
     * Animate dot from current position to target position.
     * Cancels any active animation before starting.
     *
     * @param startPosition Starting position
     * @param targetPosition Target position
     * @param duration Animation duration in milliseconds
     */
    fun animateToPosition(
        startPosition: DotPosition,
        targetPosition: DotPosition,
        duration: Long = DEFAULT_DURATION_MS
    ) {
        // If already at target, just notify completion
        if (startPosition == targetPosition) {
            onAnimationComplete(targetPosition)
            return
        }

        // Cancel any active animation
        cancel()

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val currentX = (startPosition.x + ((targetPosition.x - startPosition.x) * fraction)).toInt()
                val currentY = (startPosition.y + ((targetPosition.y - startPosition.y) * fraction)).toInt()
                onPositionUpdate(DotPosition(currentX, currentY))
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onPositionUpdate(targetPosition)
                    onAnimationComplete(targetPosition)
                    currentAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    currentAnimator = null
                }
            })
        }

        currentAnimator = animator
        animator.start()
    }

    /**
     * Cancel any active animation
     */
    fun cancel() {
        currentAnimator?.cancel()
        currentAnimator = null
    }

    /**
     * Check if an animation is currently running
     */
    fun isAnimating(): Boolean = currentAnimator?.isRunning == true
}
