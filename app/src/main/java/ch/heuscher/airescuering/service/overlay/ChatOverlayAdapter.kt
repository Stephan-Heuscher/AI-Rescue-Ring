package ch.heuscher.airescuering.service.overlay

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import ch.heuscher.airescuering.R
import ch.heuscher.airescuering.domain.model.AIMessage
import ch.heuscher.airescuering.domain.model.MessageRole
import io.noties.markwon.Markwon
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying chat messages in the overlay
 */
class ChatOverlayAdapter(
    private val messages: List<AIMessage>
) : RecyclerView.Adapter<ChatOverlayAdapter.MessageViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var markwon: Markwon? = null

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageCard: CardView = view.findViewById(R.id.messageCard)
        val roleText: TextView = view.findViewById(R.id.messageRole)
        val messageText: TextView = view.findViewById(R.id.messageText)
        val timeText: TextView = view.findViewById(R.id.messageTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        // Initialize Markwon if not already done
        if (markwon == null) {
            markwon = Markwon.create(holder.itemView.context)
        }

        holder.roleText.text = when (message.role) {
            MessageRole.USER -> "You"
            MessageRole.ASSISTANT -> "🛟 AI Rescue"
            MessageRole.SYSTEM -> "System"
        }

        // Strip metadata tags from content before displaying
        val cleanContent = stripMetadataTags(message.content)

        // Render markdown for assistant messages, plain text for user messages
        if (message.role == MessageRole.ASSISTANT) {
            markwon?.setMarkdown(holder.messageText, cleanContent)
        } else {
            holder.messageText.text = cleanContent
        }
        
        holder.timeText.text = timeFormat.format(Date(message.timestamp))

        // Style based on role
        when (message.role) {
            MessageRole.USER -> {
                val params = holder.messageCard.layoutParams
                if (params is LinearLayout.LayoutParams) {
                    params.gravity = android.view.Gravity.END
                    holder.messageCard.layoutParams = params
                }
                holder.messageCard.setCardBackgroundColor(0xFF2196F3.toInt())
                holder.roleText.setTextColor(0xFFFFFFFF.toInt())
                holder.messageText.setTextColor(0xFFFFFFFF.toInt())
                holder.timeText.setTextColor(0xFFE3F2FD.toInt())
            }
            MessageRole.ASSISTANT -> {
                val params = holder.messageCard.layoutParams
                if (params is LinearLayout.LayoutParams) {
                    params.gravity = android.view.Gravity.START
                    holder.messageCard.layoutParams = params
                }
                holder.messageCard.setCardBackgroundColor(0xFFEEEEEE.toInt())
                holder.roleText.setTextColor(0xFF000000.toInt())
                holder.messageText.setTextColor(0xFF000000.toInt())
                holder.timeText.setTextColor(0xFF757575.toInt())
            }
            MessageRole.SYSTEM -> {
                holder.messageCard.setCardBackgroundColor(0xFFFFF9C4.toInt())
                holder.roleText.setTextColor(0xFF000000.toInt())
                holder.messageText.setTextColor(0xFF000000.toInt())
                holder.timeText.setTextColor(0xFF757575.toInt())
            }
        }
    }

    override fun getItemCount() = messages.size

    /**
     * Strip metadata tags from message content
     * Removes [POSITION:...], [TAP:...], [HIGHLIGHT:...] tags that are meant for the app
     */
    private fun stripMetadataTags(content: String): String {
        var cleaned = content
        // Remove [POSITION:...] tags
        cleaned = cleaned.replace(Regex("""\[POSITION:\s*[^\]]+\]""", RegexOption.IGNORE_CASE), "")
        // Remove [TAP:...] tags
        cleaned = cleaned.replace(Regex("""\[TAP:\s*[^\]]+\]""", RegexOption.IGNORE_CASE), "")
        // Remove [HIGHLIGHT:...] tags (legacy)
        cleaned = cleaned.replace(Regex("""\[HIGHLIGHT:\s*[^\]]+\]""", RegexOption.IGNORE_CASE), "")
        // Clean up any resulting double spaces or leading/trailing spaces on lines
        cleaned = cleaned.replace(Regex("""  +"""), " ")
        return cleaned.trim()
    }
}
