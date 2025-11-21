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
        private const val EDGE_THRESHOLD = 60 // pixels - edge detection for resizing (larger for elderly)
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var pipView: View? = null
    private var isVisible = false

    // UI Components
    private var expandedView: CardView? = null
    private var pipDragHandle: View? = null
    private var pipStepIndicatorExpanded: TextView? = null
    private var pipStepTitleExpanded: TextView? = null
    private var pipStepContent: TextView? = null
    private var pipPreviousButton: Button? = null
    private var pipNextButton: Button? = null
    private var pipCloseButton: ImageButton? = null
    private var leftEdgeIndicator: View? = null
    private var bottomEdgeIndicator: View? = null

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
    private var resizeMode = ResizeMode.NONE

    private enum class ResizeMode {
        NONE, LEFT, BOTTOM, BOTTOM_LEFT
    }

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

            // Set up touch listener for dragging, double-tap, and edge resizing
            setupTouchListener(params)

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
            // Get UI components
            expandedView = view.findViewById(R.id.stepPipExpanded)
            pipDragHandle = view.findViewById(R.id.pipDragHandle)
            pipStepIndicatorExpanded = view.findViewById(R.id.pipStepIndicatorExpanded)
            pipStepTitleExpanded = view.findViewById(R.id.pipStepTitleExpanded)
            pipStepContent = view.findViewById(R.id.pipStepContent)
            pipPreviousButton = view.findViewById(R.id.pipPreviousButton)
            pipNextButton = view.findViewById(R.id.pipNextButton)
            pipCloseButton = view.findViewById(R.id.pipCloseButton)
            leftEdgeIndicator = view.findViewById(R.id.leftEdgeIndicator)
            bottomEdgeIndicator = view.findViewById(R.id.bottomEdgeIndicator)

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
                // Close button hides the step window
                hide()
                onClose?.invoke()
            }
        }
    }

    /**
     * Set up touch listener for dragging and edge resizing
     */
    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        // Drag listener for header
        val dragTouchListener = View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasMoved = false
                    true // Consume to receive further events
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
                        true // Consume but don't move yet
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        // Resize listener for edge indicators
        val resizeTouchListener = View.OnTouchListener { edgeView, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Store initial dimensions and position
                    expandedView?.let {
                        initialWidth = it.width
                        initialHeight = it.height
                    }
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    // Determine resize mode based on which edge view was touched
                    val density = context.resources.displayMetrics.density
                    val edgeThreshold = (EDGE_THRESHOLD * density).toInt()

                    resizeMode = when (edgeView) {
                        leftEdgeIndicator -> {
                            // Check if also near bottom for corner resize
                            val viewHeight = expandedView?.height ?: 0
                            val bottomThreshold = viewHeight - edgeThreshold
                            if (event.rawY > params.y + bottomThreshold) {
                                ResizeMode.BOTTOM_LEFT
                            } else {
                                ResizeMode.LEFT
                            }
                        }
                        bottomEdgeIndicator -> {
                            // Check if also near left for corner resize
                            val viewWidth = expandedView?.width ?: 0
                            if (event.rawX < params.x + edgeThreshold) {
                                ResizeMode.BOTTOM_LEFT
                            } else {
                                ResizeMode.BOTTOM
                            }
                        }
                        else -> ResizeMode.NONE
                    }

                    isResizing = resizeMode != ResizeMode.NONE
                    Log.d(TAG, "Resize mode: $resizeMode from edge view at rawX=${event.rawX}, rawY=${event.rawY}")
                    isResizing
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isResizing && resizeMode != ResizeMode.NONE) {
                        handleResize(event, params)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val wasResizing = isResizing
                    isResizing = false
                    resizeMode = ResizeMode.NONE
                    wasResizing
                }
                else -> false
            }
        }

        // Set drag listener on drag handle only
        pipDragHandle?.setOnTouchListener(dragTouchListener)

        // Set resize listener on edge indicators only
        leftEdgeIndicator?.setOnTouchListener(resizeTouchListener)
        bottomEdgeIndicator?.setOnTouchListener(resizeTouchListener)
    }


    /**
     * Handle window resizing based on touch movement
     * NOTE: Window uses Gravity.END, so right edge is fixed by default when changing width
     */
    private fun handleResize(event: MotionEvent, params: WindowManager.LayoutParams) {
        expandedView?.let { card ->
            val deltaX = event.rawX - initialTouchX
            val deltaY = event.rawY - initialTouchY

            // Calculate new dimensions with constraints
            val density = context.resources.displayMetrics.density
            val minWidth = (280 * density).toInt()
            val maxWidth = (600 * density).toInt()
            val minHeight = (350 * density).toInt()
            val maxHeight = (800 * density).toInt()

            val layoutParams = card.layoutParams
            val oldWidth = layoutParams.width
            val oldHeight = layoutParams.height

            when (resizeMode) {
                ResizeMode.LEFT -> {
                    // For left edge with Gravity.END:
                    // - Dragging left (negative deltaX) increases width
                    // - Right edge stays fixed automatically (no need to adjust params.x)
                    // - Left edge moves left
                    val newWidth = (initialWidth - deltaX).toInt().coerceIn(minWidth, maxWidth)
                    layoutParams.width = newWidth

                    Log.d(TAG, "Resize LEFT: deltaX=$deltaX, initialWidth=$initialWidth, newWidth=$newWidth")
                }
                ResizeMode.BOTTOM -> {
                    // For bottom edge, delta Y is positive when dragging down
                    val newHeight = (initialHeight + deltaY).toInt().coerceIn(minHeight, maxHeight)
                    layoutParams.height = newHeight
                    Log.d(TAG, "Resize BOTTOM: deltaY=$deltaY, initialHeight=$initialHeight, newHeight=$newHeight")
                }
                ResizeMode.BOTTOM_LEFT -> {
                    // Resize both dimensions
                    // Right edge stays fixed (Gravity.END), left edge moves
                    // Bottom edge moves down
                    val newWidth = (initialWidth - deltaX).toInt().coerceIn(minWidth, maxWidth)
                    val newHeight = (initialHeight + deltaY).toInt().coerceIn(minHeight, maxHeight)

                    layoutParams.width = newWidth
                    layoutParams.height = newHeight

                    Log.d(TAG, "Resize BOTH: deltaX=$deltaX, deltaY=$deltaY, newWidth=$newWidth, newHeight=$newHeight")
                }
                ResizeMode.NONE -> {}
            }

            if (layoutParams.width != oldWidth || layoutParams.height != oldHeight) {
                card.layoutParams = layoutParams
                card.requestLayout()
                pipView?.requestLayout()
                windowManager.updateViewLayout(pipView, params)
            }
        }
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
