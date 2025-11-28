package ch.heuscher.airescuering.service.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import ch.heuscher.airescuering.R
import io.noties.markwon.Markwon
import kotlin.math.abs

/**
 * Manages a compact floating window for step-by-step instructions.
 * Features:
 * - Draggable window that can be positioned anywhere on screen
 * - LLM-guided positioning (top/bottom based on context)
 * - Highlight overlay to guide user attention
 * - Compact design optimized for elderly users
 */
class StepPipManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "StepPipManager"
        private const val MOVE_THRESHOLD = 10 // pixels - distinguish tap from drag
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var pipView: View? = null
    private var highlightView: View? = null
    private var isVisible = false

    // UI Components
    private var expandedView: CardView? = null
    private var pipDragHandle: View? = null
    private var pipStepIndicatorExpanded: TextView? = null
    private var pipStepTitleExpanded: TextView? = null
    private var pipStepContent: TextView? = null
    private var pipPreviousButton: Button? = null
    private var pipNextButton: Button? = null
    private var pipDoneButton: Button? = null
    private var pipCloseButton: ImageButton? = null
    private var pipCoordinatesDisplay: TextView? = null

    // Highlight components
    private var highlightContainer: FrameLayout? = null
    private var highlightPulse: View? = null
    private var highlightLabel: TextView? = null
    private var pulseAnimator: ObjectAnimator? = null

    // Step data
    private var currentSteps = listOf<String>()
    private var currentStepIndex = 0
    private var positionHints = mutableMapOf<Int, String>() // step index -> position hint
    private var highlightHints = mutableMapOf<Int, String>() // step index -> highlight description
    private var coordinateHints = mutableMapOf<Int, String>() // step index -> coordinates (x, y)

    // Markdown renderer
    private val markwon: Markwon = Markwon.create(context)

    // Touch handling for dragging
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasMoved = false

    // Screen dimensions
    private var screenWidth = 0
    private var screenHeight = 0

    // Callbacks
    var onClose: (() -> Unit)? = null
    var onStepComplete: ((stepIndex: Int) -> Unit)? = null

    /**
     * Show the PiP window with steps
     * @param steps List of step texts (may contain position/highlight metadata)
     * @param startIndex Initial step to display
     */
    fun show(steps: List<String>, startIndex: Int = 0) {
        if (steps.isEmpty()) {
            Log.d(TAG, "No steps to show")
            return
        }

        // Parse steps and extract metadata
        val processedSteps = steps.mapIndexed { index, step ->
            parseAndStoreMetadata(index, step)
        }

        currentSteps = processedSteps
        currentStepIndex = startIndex.coerceIn(0, processedSteps.size - 1)

        if (isVisible) {
            // Update existing window
            updateStepDisplay()
            return
        }

        try {
            Log.d(TAG, "Creating PiP step window")

            // Get screen dimensions
            val displayMetrics = context.resources.displayMetrics
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels

            // Inflate layout
            val inflater = LayoutInflater.from(context)
            pipView = inflater.inflate(R.layout.step_pip_window, null)

            // Determine initial position based on first step's hint
            val initialPosition = positionHints[0] ?: "top"

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
                gravity = if (initialPosition == "bottom") {
                    Gravity.BOTTOM or Gravity.END
                } else {
                    Gravity.TOP or Gravity.END
                }
                x = 16 // offset from right edge
                y = 100 // offset from top/bottom
            }
            
            // Store initial gravity for drag calculations
            currentGravity = params.gravity

            // Add view to window
            windowManager.addView(pipView, params)
            isVisible = true

            // Initialize views
            initializeViews()

            // Set up touch listener for dragging
            setupTouchListener(params)

            // Display initial step
            updateStepDisplay()

            // Show highlight if available
            showHighlightForCurrentStep()

            Log.d(TAG, "PiP window shown successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing PiP window", e)
        }
    }

    /**
     * Parse step text and extract position/tap metadata
     * Returns cleaned text without metadata
     */
    private fun parseAndStoreMetadata(stepIndex: Int, stepText: String): String {
        var cleanedText = stepText

        // Extract position hint: [POSITION: top-right] or [POSITION: bottom-left]
        val positionRegex = Regex("""\[POSITION:\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
        positionRegex.find(stepText)?.let { match ->
            positionHints[stepIndex] = match.groupValues[1].lowercase().trim()
            cleanedText = cleanedText.replace(match.value, "").trim()
        }

        // Extract tap coordinates: [TAP:x,y] or [TAP:none]
        val tapRegex = Regex("""\[TAP:\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
        tapRegex.find(stepText)?.let { match ->
            val tapValue = match.groupValues[1].trim()
            if (tapValue.lowercase() != "none") {
                // Parse x,y coordinates
                val coordMatch = Regex("""(\d+)\s*,\s*(\d+)""").find(tapValue)
                if (coordMatch != null) {
                    val x = coordMatch.groupValues[1]
                    val y = coordMatch.groupValues[2]
                    coordinateHints[stepIndex] = "($x%, $y%)"
                    // Store coordinates for highlight positioning
                    highlightHints[stepIndex] = "$x,$y"
                }
            }
            cleanedText = cleanedText.replace(match.value, "").trim()
        }

        // Legacy: Also check for old HIGHLIGHT format
        val highlightRegex = Regex("""\[HIGHLIGHT:\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
        highlightRegex.find(cleanedText)?.let { match ->
            val highlightValue = match.groupValues[1].trim()
            if (!highlightHints.containsKey(stepIndex)) {
                highlightHints[stepIndex] = highlightValue
            }
            cleanedText = cleanedText.replace(match.value, "").trim()
        }

        return cleanedText
    }

    /**
     * Hide the PiP window
     */
    fun hide() {
        if (!isVisible) return

        try {
            hideHighlight()
            pipView?.let { windowManager.removeView(it) }
            pipView = null
            isVisible = false
            positionHints.clear()
            highlightHints.clear()
            coordinateHints.clear()
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
            showHighlightForCurrentStep()
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
            pipDoneButton = view.findViewById(R.id.pipDoneButton)
            pipCloseButton = view.findViewById(R.id.pipCloseButton)
            pipCoordinatesDisplay = view.findViewById(R.id.pipCoordinatesDisplay)

            // Set up button listeners
            pipDoneButton?.setOnClickListener {
                // User confirmed they completed the step
                onStepComplete?.invoke(currentStepIndex)
                // Auto-advance to next step
                if (currentStepIndex < currentSteps.size - 1) {
                    currentStepIndex++
                    updateStepDisplay()
                    showHighlightForCurrentStep()
                } else {
                    // Last step completed - show congratulations message and keep window open
                    Log.d(TAG, "All steps completed!")
                    pipStepTitleExpanded?.text = "🎉 Congratulations!"
                    pipStepContent?.text = "You've successfully completed all steps. Great job! Tap the X to close."
                    pipDoneButton?.isEnabled = false
                    pipDoneButton?.alpha = 0.5f
                    pipPreviousButton?.isEnabled = false
                    pipNextButton?.isEnabled = false
                }
            }

            pipPreviousButton?.setOnClickListener {
                if (currentStepIndex > 0) {
                    currentStepIndex--
                    updateStepDisplay()
                    showHighlightForCurrentStep()
                }
            }

            pipNextButton?.setOnClickListener {
                if (currentStepIndex < currentSteps.size - 1) {
                    currentStepIndex++
                    updateStepDisplay()
                    showHighlightForCurrentStep()
                }
            }

            pipCloseButton?.setOnClickListener {
                hide()
                onClose?.invoke()
            }
        }
    }

    // Current gravity for proper drag calculations
    private var currentGravity = Gravity.TOP or Gravity.END

    /**
     * Set up touch listener for dragging
     */
    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        currentGravity = params.gravity

        val dragTouchListener = View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(initialTouchX - event.rawX)
                    val deltaY = abs(initialTouchY - event.rawY)

                    if (deltaX > MOVE_THRESHOLD || deltaY > MOVE_THRESHOLD) {
                        hasMoved = true

                        val touchDeltaX = event.rawX - initialTouchX
                        val touchDeltaY = event.rawY - initialTouchY

                        // Account for gravity: with END gravity, x increases LEFT from right edge
                        // With START gravity, x increases RIGHT from left edge
                        val isRightAligned = (currentGravity and Gravity.END) == Gravity.END
                        val isBottomAligned = (currentGravity and Gravity.BOTTOM) == Gravity.BOTTOM

                        val positionDeltaX = if (isRightAligned) -touchDeltaX else touchDeltaX
                        val positionDeltaY = if (isBottomAligned) -touchDeltaY else touchDeltaY

                        params.x = (initialX + positionDeltaX).toInt()
                        params.y = (initialY + positionDeltaY).toInt()

                        windowManager.updateViewLayout(pipView, params)
                        true
                    } else {
                        true
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

        pipDragHandle?.setOnTouchListener(dragTouchListener)
    }

    /**
     * Show highlight overlay for current step
     */
    private fun showHighlightForCurrentStep() {
        val highlightCoords = highlightHints[currentStepIndex]
        
        if (highlightCoords.isNullOrBlank()) {
            hideHighlight()
            return
        }

        // Parse x,y from stored format "x,y"
        val coordMatch = Regex("""(\d+)\s*,\s*(\d+)""").find(highlightCoords)
        if (coordMatch == null) {
            hideHighlight()
            return
        }
        
        val xPercent = coordMatch.groupValues[1].toIntOrNull() ?: 50
        val yPercent = coordMatch.groupValues[2].toIntOrNull() ?: 50

        try {
            // Get status bar height
            val statusBarHeight = getStatusBarHeight()
            // Get navigation bar height  
            val navBarHeight = getNavigationBarHeight()
            
            // Calculate usable screen area (excluding status bar and navbar)
            val usableHeight = screenHeight - statusBarHeight - navBarHeight
            
            // Calculate actual pixel position from percentages within usable area
            val xPos = (screenWidth * xPercent / 100)
            val yPos = statusBarHeight + (usableHeight * yPercent / 100)
            
            // Get highlight size (120dp converted to pixels)
            val density = context.resources.displayMetrics.density
            val highlightSizePx = (60 * density).toInt() // Half of 120dp for centering
            
            if (highlightView == null) {
                val inflater = LayoutInflater.from(context)
                highlightView = inflater.inflate(R.layout.highlight_overlay, null)

                highlightContainer = highlightView?.findViewById(R.id.highlightContainer)
                highlightPulse = highlightView?.findViewById(R.id.highlightPulse)
                highlightLabel = highlightView?.findViewById(R.id.highlightLabel)
            }
            
            // Position at exact coordinates using TOP|START gravity
            val highlightParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                // Center the highlight on the tap position
                x = xPos - highlightSizePx
                y = yPos - highlightSizePx
            }

            // Remove and re-add to update position
            try {
                windowManager.removeView(highlightView)
            } catch (e: Exception) {
                // View not attached yet
            }
            windowManager.addView(highlightView, highlightParams)

            // Show the highlight elements
            highlightLabel?.text = "Tap here"
            highlightLabel?.visibility = View.VISIBLE
            highlightPulse?.visibility = View.VISIBLE

            // Start pulse animation
            startPulseAnimation()

            Log.d(TAG, "Showing highlight at ($xPercent%, $yPercent%) = pixel ($xPos, $yPos), statusBar=$statusBarHeight, navBar=$navBarHeight, usableHeight=$usableHeight")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing highlight", e)
        }
    }

    /**
     * Start the pulsing animation for highlight
     */
    private fun startPulseAnimation() {
        pulseAnimator?.cancel()

        highlightPulse?.let { pulse ->
            pulseAnimator = ObjectAnimator.ofFloat(pulse, "alpha", 0.3f, 1f, 0.3f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    /**
     * Hide the highlight overlay
     */
    private fun hideHighlight() {
        try {
            pulseAnimator?.cancel()
            pulseAnimator = null

            highlightView?.let { 
                windowManager.removeView(it) 
            }
            highlightView = null
            highlightContainer = null
            highlightPulse = null
            highlightLabel = null
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding highlight", e)
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
        val stepIndicatorText = "${currentStepIndex + 1}/${currentSteps.size}"

        // Update expanded mode
        pipStepIndicatorExpanded?.text = stepIndicatorText
        pipStepTitleExpanded?.text = title

        if (content.isNotEmpty()) {
            markwon.setMarkdown(pipStepContent!!, content)
        } else {
            pipStepContent?.text = ""
        }

        // Update coordinates display - show tap location only
        val coords = coordinateHints[currentStepIndex]
        if (!coords.isNullOrBlank()) {
            pipCoordinatesDisplay?.text = "🎯 Tap: $coords"
            pipCoordinatesDisplay?.visibility = View.VISIBLE
        } else {
            pipCoordinatesDisplay?.visibility = View.GONE
        }

        // Update button states
        pipPreviousButton?.isEnabled = currentStepIndex > 0
        pipNextButton?.isEnabled = currentStepIndex < currentSteps.size - 1

        // Reposition window if position hint changed
        repositionIfNeeded()
    }

    /**
     * Reposition window based on current step's position hint
     * Supports: top-left, top-right, bottom-left, bottom-right, top, bottom
     * Uses smooth animation for deliberate transitions
     */
    private fun repositionIfNeeded() {
        val positionHint = positionHints[currentStepIndex] ?: return
        
        pipView?.let { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return

            // Parse position hint
            val isBottom = positionHint.contains("bottom")
            val isLeft = positionHint.contains("left")
            
            val verticalGravity = if (isBottom) Gravity.BOTTOM else Gravity.TOP
            val horizontalGravity = if (isLeft) Gravity.START else Gravity.END
            
            val targetGravity = verticalGravity or horizontalGravity

            if (params.gravity != targetGravity) {
                // Animate the transition to new position
                val startX = params.x
                val startY = params.y
                val startGravity = params.gravity
                
                // Target position
                val targetX = 16
                val targetY = 100

                // Use ValueAnimator for smooth, deliberate movement
                val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 600 // 600ms for deliberate movement
                    interpolator = AccelerateDecelerateInterpolator()
                    
                    addUpdateListener { animation ->
                        val progress = animation.animatedValue as Float
                        
                        // Interpolate x and y positions
                        params.x = (startX + (targetX - startX) * progress).toInt()
                        params.y = (startY + (targetY - startY) * progress).toInt()
                        
                        // Update gravity at midpoint for smoother transition
                        if (progress >= 0.5f && params.gravity != targetGravity) {
                            params.gravity = targetGravity
                            currentGravity = targetGravity
                            params.x = targetX
                            params.y = targetY
                        }
                        
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating view during animation", e)
                        }
                    }
                    
                    addListener(object : android.animation.Animator.AnimatorListener {
                        override fun onAnimationStart(animation: android.animation.Animator) {}
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            // Ensure final position is correct
                            params.gravity = targetGravity
                            params.x = targetX
                            params.y = targetY
                            currentGravity = targetGravity
                            try {
                                windowManager.updateViewLayout(view, params)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error finalizing position", e)
                            }
                            Log.d(TAG, "Repositioned window to $positionHint (gravity=$targetGravity)")
                        }
                        override fun onAnimationCancel(animation: android.animation.Animator) {}
                        override fun onAnimationRepeat(animation: android.animation.Animator) {}
                    })
                }
                
                animator.start()
            }
        }
    }

    /**
     * Check if PiP window is visible
     */
    fun isShowing(): Boolean = isVisible

    /**
     * Get status bar height
     */
    private fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            // Default fallback ~24dp
            (24 * context.resources.displayMetrics.density).toInt()
        }
    }

    /**
     * Get navigation bar height
     */
    private fun getNavigationBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            // Default fallback ~48dp
            (48 * context.resources.displayMetrics.density).toInt()
        }
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        hide()
    }
}
