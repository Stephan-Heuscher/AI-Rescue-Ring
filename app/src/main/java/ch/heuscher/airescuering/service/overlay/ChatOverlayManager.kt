package ch.heuscher.airescuering.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.heuscher.airescuering.R

/**
 * Manages the chat overlay window
 */
class ChatOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var chatView: View? = null
    private var isShowing = false

    // UI components
    private var rvMessages: RecyclerView? = null
    private var etMessage: EditText? = null
    private var btnSend: ImageButton? = null
    private var btnVoice: ImageButton? = null
    private var btnClose: ImageView? = null
    private var progressLoading: ProgressBar? = null
    private var ivScreenshotPreview: ImageView? = null

    // Callbacks
    var onCloseListener: (() -> Unit)? = null
    var onSendMessageListener: ((String) -> Unit)? = null
    var onVoiceInputListener: (() -> Unit)? = null

    /**
     * Show the chat overlay below the rescue ring
     * @param belowY The Y position below which the chat should appear
     */
    fun showChatOverlay(belowY: Int) {
        if (isShowing) return

        val inflater = LayoutInflater.from(context)
        chatView = inflater.inflate(R.layout.overlay_chat, null)

        // Initialize UI components
        rvMessages = chatView?.findViewById(R.id.rv_messages)
        etMessage = chatView?.findViewById(R.id.et_message)
        btnSend = chatView?.findViewById(R.id.btn_send)
        btnVoice = chatView?.findViewById(R.id.btn_voice)
        btnClose = chatView?.findViewById(R.id.btn_close_chat)
        progressLoading = chatView?.findViewById(R.id.progress_loading)
        ivScreenshotPreview = chatView?.findViewById(R.id.iv_screenshot_preview)

        // Setup RecyclerView
        rvMessages?.apply {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
        }

        // Setup click listeners
        btnClose?.setOnClickListener {
            onCloseListener?.invoke()
        }

        btnSend?.setOnClickListener {
            val message = etMessage?.text?.toString()?.trim()
            if (!message.isNullOrEmpty()) {
                onSendMessageListener?.invoke(message)
                etMessage?.text?.clear()
            }
        }

        btnVoice?.setOnClickListener {
            onVoiceInputListener?.invoke()
        }

        // Create layout params
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = belowY + 20 // Small margin below the ring
        }

        windowManager.addView(chatView, params)
        isShowing = true
    }

    /**
     * Hide and remove the chat overlay
     */
    fun hideChatOverlay() {
        if (!isShowing) return

        chatView?.let {
            windowManager.removeView(it)
        }
        chatView = null
        isShowing = false
    }

    /**
     * Set the RecyclerView adapter
     */
    fun setChatAdapter(adapter: RecyclerView.Adapter<*>) {
        rvMessages?.adapter = adapter
    }

    /**
     * Show loading indicator
     */
    fun setLoading(loading: Boolean) {
        progressLoading?.visibility = if (loading) View.VISIBLE else View.GONE
    }

    /**
     * Set screenshot preview
     */
    fun setScreenshotPreview(bitmap: android.graphics.Bitmap?) {
        if (bitmap != null) {
            ivScreenshotPreview?.setImageBitmap(bitmap)
            ivScreenshotPreview?.visibility = View.VISIBLE
        } else {
            ivScreenshotPreview?.visibility = View.GONE
        }
    }

    /**
     * Scroll to bottom of messages
     */
    fun scrollToBottom() {
        rvMessages?.scrollToPosition((rvMessages?.adapter?.itemCount ?: 1) - 1)
    }

    /**
     * Check if chat is currently showing
     */
    fun isChatShowing(): Boolean = isShowing

    /**
     * Update chat position
     */
    fun updatePosition(belowY: Int) {
        if (!isShowing) return

        chatView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.y = belowY + 20
            windowManager.updateViewLayout(view, params)
        }
    }
}
