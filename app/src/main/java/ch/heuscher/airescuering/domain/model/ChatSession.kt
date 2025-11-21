package ch.heuscher.airescuering.domain.model

import java.util.*

/**
 * Represents a chat session with messages
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val messages: MutableList<AIMessage> = mutableListOf()
) {
    /**
     * Get a display name for this chat session based on start time
     */
    fun getDisplayName(): String {
        val date = Date(startTime)
        val calendar = Calendar.getInstance()
        calendar.time = date

        val now = Calendar.getInstance()
        val isToday = calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

        val isYesterday = calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1

        val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = java.text.SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        return when {
            isToday -> "Today ${timeFormat.format(date)}"
            isYesterday -> "Yesterday ${timeFormat.format(date)}"
            else -> dateFormat.format(date)
        }
    }

    /**
     * Get a preview of the last message
     */
    fun getPreview(): String {
        return messages.lastOrNull()?.content?.take(50) ?: "New Chat"
    }
}
