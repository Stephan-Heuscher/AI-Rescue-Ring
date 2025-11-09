package ch.heuscher.airescuering.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a message in the AI chat
 */
@Serializable
data class AIMessage(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
