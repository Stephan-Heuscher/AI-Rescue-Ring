package ch.heuscher.airescuering.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import ch.heuscher.airescuering.R
import io.noties.markwon.Markwon
import kotlin.math.abs

/**
 * Manages a Picture-in-Picture style floating window for step-by-step instructions.
 * Features:
 * - Draggable window that can be positioned anywhere on screen
 * - Compact mode showing just step title
 * - Expanded mode showing full step content with markdown
 * - Double-tap to toggle between compact and expanded modes
 */
class StepPipManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "StepPipManager"
        private const val DOUBLE_TAP_DELTA = 300L // milliseconds
        private const val MOVE_THRESHOLD = 10 // pixels - distinguish tap from drag
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var pipView: View? = null
    private var isVisible = false
    private var isExpanded = false

    // UI Components - Compact Mode
    private var compactView: CardView? = null
    private var pipStepIndicator: TextView? = null
    private var pipStepTitle: TextView? = null

    // UI Components - Expanded Mode
    private var expandedView: CardView? = null
    private var pipStepIndicatorExpanded: TextView? = null
    private var pipStepTitleExpanded: TextView? = null
    private var pipStepContent: TextView? = null
    private var pipPreviousButton: Button? = null
    private var pipNextButton: Button? = null
    private var pipCloseButton: ImageButton? = null
    private var resizeHandle: View? = null

    // Step data
    private var currentSteps = listOf<String>()
    private var currentStepIndex = 0

    // Markdown renderer
    private val markwon: Markwon = Markwon.create(context)

    // Touch handling for dragging
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var hasMoved = false

    // Resize handling
    private var initialWidth = 0
    private var initialHeight = 0
    private var isResizing = false

    // Callbacks
    var onClose: (() -> Unit)? = null

    /**
     * Show the PiP window with steps
     */
    fun show(steps: List<String>, startIndex: Int = 0) {
        if (steps.isEmpty()) {
            Log.d(TAG, "No steps to show")
            return
        }

        currentSteps = steps
        currentStepIndex = startIndex.coerceIn(0, steps.size - 1)

        if (isVisible) {
            // Update existing window
            updateStepDisplay()
            return
        }

        try {
            Log.d(TAG, "Creating PiP step window")

            // Inflate layout
            val inflater = LayoutInflater.from(context)
            pipView = inflater.inflate(R.layout.step_pip_window, null)

            // Get screen dimensions
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // Set up window parameters
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                // Position in top-right corner initially
                gravity = Gravity.TOP or Gravity.END
                x = 16 // offset from right edge
                y = 100 // offset from top
            }

            // Add view to window
            windowManager.addView(pipView, params)
            isVisible = true

            // Initialize views
            initializeViews()

            // Set up touch listener for dragging and double-tap
            setupTouchListener(params)

            // Set up resize listener
            setupResizeListener(params)

            // Display initial step
            updateStepDisplay()

            Log.d(TAG, "PiP window shown successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing PiP window", e)
        }
    }

    /**
     * Hide the PiP window
     */
    fun hide() {
        if (!isVisible) return

        try {
            pipView?.let { windowManager.removeView(it) }
            pipView = null
            isVisible = false
            Log.d(TAG, "PiP window hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding PiP window", e)
        }
    }

    /**
     * Update which step is displayed
     */
    fun setCurrentStep(index: Int) {
        if (index in currentSteps.indices) {
            currentStepIndex = index
            updateStepDisplay()
        }
    }

    /**
     * Initialize UI components
     */
    private fun initializeViews() {
        pipView?.let { view ->
            // Compact mode views
            compactView = view.findViewById(R.id.stepPipCompact)
            pipStepIndicator = view.findViewById(R.id.pipStepIndicator)
            pipStepTitle = view.findViewById(R.id.pipStepTitle)

            // Expanded mode views
            expandedView = view.findViewById(R.id.stepPipExpanded)
            pipStepIndicatorExpanded = view.findViewById(R.id.pipStepIndicatorExpanded)
            pipStepTitleExpanded = view.findViewById(R.id.pipStepTitleExpanded)
            pipStepContent = view.findViewById(R.id.pipStepContent)
            pipPreviousButton = view.findViewById(R.id.pipPreviousButton)
            pipNextButton = view.findViewById(R.id.pipNextButton)
            pipCloseButton = view.findViewById(R.id.pipCloseButton)
            resizeHandle = view.findViewById(R.id.resizeHandle)

            // Set up button listeners
            pipPreviousButton?.setOnClickListener {
                if (currentStepIndex > 0) {
                    currentStepIndex--
                    updateStepDisplay()
                }
            }

            pipNextButton?.setOnClickListener {
                if (currentStepIndex < currentSteps.size - 1) {
                    currentStepIndex++
                    updateStepDisplay()
                }
            }

            pipCloseButton?.setOnClickListener {
                // Close button should only collapse to compact mode
                if (isExpanded) {
                    toggleExpanded()
                }
            }
        }
    }

    /**
     * Set up touch listener for dragging and double-tap detection
     */
    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        val touchListener = View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasMoved = false

                    // Check for double-tap
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime < DOUBLE_TAP_DELTA) {
                        // Double-tap detected - toggle expanded/compact
                        toggleExpanded()
                        lastTapTime = 0L // Reset to prevent triple-tap
                        true // Consume the event
                    } else {
                        lastTapTime = currentTime
                        false // Don't consume yet - allow click events
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate movement distance
                    val deltaX = abs(initialTouchX - event.rawX)
                    val deltaY = abs(initialTouchY - event.rawY)

                    // Only start dragging if moved beyond threshold
                    if (deltaX > MOVE_THRESHOLD || deltaY > MOVE_THRESHOLD) {
                        hasMoved = true

                        // Calculate new position
                        val positionDeltaX = initialTouchX - event.rawX
                        val positionDeltaY = event.rawY - initialTouchY

                        params.x = (initialX + positionDeltaX).toInt()
                        params.y = (initialY + positionDeltaY).toInt()

                        // Update window position
                        windowManager.updateViewLayout(pipView, params)
                        true // Consume the event during drag
                    } else {
                        false // Small movement - might be a tap
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // If we moved, consume the event to prevent click
                    // If we didn't move, allow click events to process
                    hasMoved
                }
                else -> false
            }
        }

        // Set touch listener on both CardViews so it works regardless of which is visible
        compactView?.setOnTouchListener(touchListener)
        expandedView?.setOnTouchListener(touchListener)
    }

    /**
     * Set up resize listener for the expanded window
     */
    private fun setupResizeListener(params: WindowManager.LayoutParams) {
        resizeHandle?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isResizing = true
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    expandedView?.let {
                        initialWidth = it.layoutParams.width
                        initialHeight = it.layoutParams.height
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isResizing) {
                        expandedView?.let { card ->
                            val deltaX = event.rawX - initialTouchX
                            val deltaY = event.rawY - initialTouchY

                            // Calculate new dimensions (minimum 200dp, maximum 400dp)
                            val minWidth = (200 * context.resources.displayMetrics.density).toInt()
                            val maxWidth = (400 * context.resources.displayMetrics.density).toInt()
                            val newWidth = (initialWidth + deltaX).toInt().coerceIn(minWidth, maxWidth)

                            // Update the CardView layout params
                            val layoutParams = card.layoutParams
                            layoutParams.width = newWidth
                            card.layoutParams = layoutParams

                            // Force window update
                            windowManager.updateViewLayout(pipView, params)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isResizing = false
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Toggle between compact and expanded modes
     */
    private fun toggleExpanded() {
        isExpanded = !isExpanded

        if (isExpanded) {
            compactView?.visibility = View.GONE
            expandedView?.visibility = View.VISIBLE
        } else {
            compactView?.visibility = View.VISIBLE
            expandedView?.visibility = View.GONE
        }

        Log.d(TAG, "PiP mode: ${if (isExpanded) "expanded" else "compact"}")
    }

    /**
     * Update the step display with current step data
     */
    private fun updateStepDisplay() {
        if (currentSteps.isEmpty() || currentStepIndex !in currentSteps.indices) {
            return
        }

        val stepText = currentSteps[currentStepIndex].trim()
        val lines = stepText.lines()

        // Extract title and content
        var title = ""
        var contentStartIndex = 0

        if (lines.isNotEmpty()) {
            val firstLine = lines[0].trim()
            if (firstLine.startsWith("#")) {
                title = firstLine.replace(Regex("^#+\\s*"), "")
                contentStartIndex = 1
            } else {
                title = if (firstLine.length <= 50) firstLine else "Step ${currentStepIndex + 1}"
                contentStartIndex = if (firstLine.length <= 50) 1 else 0
            }
        }

        val content = lines.drop(contentStartIndex).joinToString("\n").trim()
        val stepIndicatorText = "Step ${currentStepIndex + 1} of ${currentSteps.size}"

        // Update compact mode
        pipStepIndicator?.text = stepIndicatorText
        pipStepTitle?.text = title

        // Update expanded mode
        pipStepIndicatorExpanded?.text = stepIndicatorText
        pipStepTitleExpanded?.text = title

        if (content.isNotEmpty()) {
            markwon.setMarkdown(pipStepContent!!, content)
        } else {
            pipStepContent?.text = ""
        }

        // Update button states
        pipPreviousButton?.isEnabled = currentStepIndex > 0
        pipNextButton?.isEnabled = currentStepIndex < currentSteps.size - 1
    }

    /**
     * Check if PiP window is visible
     */
    fun isShowing(): Boolean = isVisible

    /**
     * Clean up resources
     */
    fun destroy() {
        hide()
    }
}
