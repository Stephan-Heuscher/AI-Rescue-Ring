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
        strokeWidth = 12f
        color = Color.RED
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
        alpha = 80 // Slightly more visible fill
    }
    
    // Additional glow paint
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.RED
        alpha = 100
        maskFilter = android.graphics.BlurMaskFilter(30f, android.graphics.BlurMaskFilter.Blur.OUTER)
    }

    private var pulseAnimator: ValueAnimator? = null
    private var currentRadius = 0f
    private var maxRadius = 150f // Larger max radius
    private var currentAlpha = 255

    init {
        // Start pulsing immediately
        startPulsing()
    }

    fun setColor(color: Int) {
        paint.color = color
        fillPaint.color = color
        fillPaint.alpha = 80
        glowPaint.color = color
        invalidate()
    }

    private fun startPulsing() {
        pulseAnimator?.cancel()
        
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200 // Faster pulse
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                // Radius grows from 0% to 100% of view size (clamped)
                val size = Math.min(width, height) / 2f
                currentRadius = fraction * size
                
                // Alpha fades out as it grows
                currentAlpha = ((1f - fraction) * 255).toInt()
                paint.alpha = currentAlpha
                fillPaint.alpha = (currentAlpha * 0.3f).toInt()
                glowPaint.alpha = (currentAlpha * 0.5f).toInt()
                
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val cx = width / 2f
        val cy = height / 2f
        
        // Draw glow
        canvas.drawCircle(cx, cy, currentRadius, glowPaint)

        // Draw the fill
        canvas.drawCircle(cx, cy, currentRadius, fillPaint)
        
        // Draw the stroke/ring
        canvas.drawCircle(cx, cy, currentRadius, paint)
        
        // Draw a solid center dot that always stays visible
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 15f, paint) // Larger center dot
        
        // Draw a second, static ring for constant visibility
        paint.style = Paint.Style.STROKE
        paint.alpha = 150
        canvas.drawCircle(cx, cy, 30f, paint)

        // Restore paint style
        paint.style = Paint.Style.STROKE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }
}
