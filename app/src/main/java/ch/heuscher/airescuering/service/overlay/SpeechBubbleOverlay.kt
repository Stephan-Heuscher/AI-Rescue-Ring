package ch.heuscher.airescuering.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import ch.heuscher.airescuering.R

/**
 * Manages J-AI-mes' speech bubble overlay - the minimal visual element
 * that shows what J-AI-mes is saying near the butler button.
 *
 * Features:
 * - Appears near the butler button position
 * - Auto-dismisses after speaking finishes  
 * - Max 4 lines with elegant typography
 * - Shows listening indicator when J-AI-mes is listening
 * - Tap to keep visible, tap again to dismiss
 */
class SpeechBubbleOverlay(
    private val context: Context,
    private val windowManager: WindowManager
) {
    companion object {
        private const val TAG = "SpeechBubbleOverlay"
        private const val AUTO_DISMISS_MS = 5000L // Auto-hide after 5s of silence
        private const val BUBBLE_OFFSET_X = -280 // Offset from button (left)
        private const val BUBBLE_OFFSET_Y = -120 // Offset from button (above)
    }

    private var bubbleView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var bubbleText: TextView? = null
    private var listeningIndicator: TextView? = null
    private var isShowing = false
    private var isPinned = false // User tapped to keep visible
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    /**
     * Show the speech bubble with text near the butler button position.
     */
    fun showText(text: String, buttonX: Int, buttonY: Int) {
        handler.post {
            createBubbleIfNeeded()
            
            bubbleText?.text = text
            listeningIndicator?.visibility = View.GONE
            
            updatePosition(buttonX, buttonY)
            
            if (!isShowing) {
                try {
                    windowManager.addView(bubbleView, layoutParams)
                    isShowing = true
                    Log.d(TAG, "Speech bubble shown: \"$text\"")
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing speech bubble", e)
                }
            } else {
                try {
                    windowManager.updateViewLayout(bubbleView, layoutParams)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating speech bubble", e)
                }
            }
            
            // Schedule auto-dismiss
            scheduleAutoDismiss()
        }
    }

    /**
     * Show the listening indicator (microphone) in the bubble.
     */
    fun showListening(buttonX: Int, buttonY: Int) {
        handler.post {
            createBubbleIfNeeded()
            
            val isGerman = java.util.Locale.getDefault().language == "de"
            bubbleText?.text = if (isGerman) "Ich höre zu..." else "Listening..."
            listeningIndicator?.visibility = View.VISIBLE
            
            updatePosition(buttonX, buttonY)
            
            if (!isShowing) {
                try {
                    windowManager.addView(bubbleView, layoutParams)
                    isShowing = true
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing listening bubble", e)
                }
            } else {
                try {
                    windowManager.updateViewLayout(bubbleView, layoutParams)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating listening bubble", e)
                }
            }
            
            // Cancel auto-dismiss while listening
            cancelAutoDismiss()
        }
    }

    /**
     * Show the "thinking" state in the bubble.
     */
    fun showThinking(buttonX: Int, buttonY: Int) {
        handler.post {
            createBubbleIfNeeded()
            
            bubbleText?.text = "..."
            listeningIndicator?.visibility = View.GONE
            
            updatePosition(buttonX, buttonY)
            
            if (!isShowing) {
                try {
                    windowManager.addView(bubbleView, layoutParams)
                    isShowing = true
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing thinking bubble", e)
                }
            } else {
                try {
                    windowManager.updateViewLayout(bubbleView, layoutParams)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating thinking bubble", e)
                }
            }
            
            cancelAutoDismiss()
        }
    }

    /**
     * Hide the speech bubble.
     */
    fun hide() {
        handler.post {
            if (isShowing && bubbleView != null) {
                try {
                    windowManager.removeView(bubbleView)
                    isShowing = false
                    isPinned = false
                    Log.d(TAG, "Speech bubble hidden")
                } catch (e: Exception) {
                    Log.e(TAG, "Error hiding speech bubble", e)
                }
            }
            cancelAutoDismiss()
        }
    }

    /**
     * Destroy the overlay and clean up resources.
     */
    fun destroy() {
        hide()
        cancelAutoDismiss()
        bubbleView = null
        bubbleText = null
        listeningIndicator = null
    }

    fun isShowing(): Boolean = isShowing

    private fun createBubbleIfNeeded() {
        if (bubbleView != null) return
        
        bubbleView = LayoutInflater.from(context).inflate(R.layout.speech_bubble_layout, null)
        bubbleText = bubbleView?.findViewById(R.id.speech_bubble_text)
        listeningIndicator = bubbleView?.findViewById(R.id.listening_indicator)
        
        // Tap to pin/unpin
        bubbleView?.setOnClickListener {
            if (isPinned) {
                hide()
            } else {
                isPinned = true
                cancelAutoDismiss()
            }
        }
        
        setupLayoutParams()
    }

    private fun setupLayoutParams() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun updatePosition(buttonX: Int, buttonY: Int) {
        layoutParams?.let { params ->
            // Position bubble above and to the left of the button
            params.x = (buttonX + BUBBLE_OFFSET_X).coerceAtLeast(8)
            params.y = (buttonY + BUBBLE_OFFSET_Y).coerceAtLeast(8)
        }
    }

    private fun scheduleAutoDismiss() {
        if (isPinned) return
        
        cancelAutoDismiss()
        dismissRunnable = Runnable {
            if (!isPinned) {
                hide()
            }
        }
        handler.postDelayed(dismissRunnable!!, AUTO_DISMISS_MS)
    }

    private fun cancelAutoDismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
    }
}
