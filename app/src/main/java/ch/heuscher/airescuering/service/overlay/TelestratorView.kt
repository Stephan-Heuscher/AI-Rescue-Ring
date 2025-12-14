package ch.heuscher.airescuering.service.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * A custom view that draws a pulsing ring indicator ("radar ping") to guide the user.
 * Designed to be transparent to touches so the user can click through it.
 */
class TelestratorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.RED // Default, will be updated
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
        alpha = 50
    }

    private var pulseAnimator: ValueAnimator? = null
    private var currentRadius = 0f
    private var maxRadius = 100f
    private var currentAlpha = 255

    // Target coordinates relative to the screen (raw pixels)
    // The view itself will be positioned by WindowManager at (0,0) covering the whole screen,
    // or positioned specifically at the target. 
    // Decision: It's better to position the VIEW at the target location to avoid drawing full screen 
    // overlay which might block touches if not careful.
    // However, if we want to draw arrows, we might need more space.
    // For "Click Here" rings, a small constrained view at coordinates is best.

    init {
        // Start pulsing immediately
        startPulsing()
    }

    fun setColor(color: Int) {
        paint.color = color
        fillPaint.color = color
        fillPaint.alpha = 50
        invalidate()
    }

    private fun startPulsing() {
        pulseAnimator?.cancel()
        
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                // Radius grows from 0% to 100% of view size
                val size = Math.min(width, height) / 2f
                currentRadius = fraction * size
                
                // Alpha fades out as it grows
                currentAlpha = ((1f - fraction) * 255).toInt()
                paint.alpha = currentAlpha
                fillPaint.alpha = (currentAlpha * 0.2f).toInt() // Fill is always more transparent
                
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val cx = width / 2f
        val cy = height / 2f
        
        // Draw the fill
        canvas.drawCircle(cx, cy, currentRadius, fillPaint)
        
        // Draw the stroke/ring
        canvas.drawCircle(cx, cy, currentRadius, paint)
        
        // Draw a solid center dot that always stays
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 10f, paint)
        
        // Restore paint style
        paint.style = Paint.Style.STROKE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }
}
