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
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.NORMAL,
    val actionData: ActionData? = null
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class MessageType {
    NORMAL,
    ACTION_REQUIRED,
    SCREENSHOT,
    ERROR
}

@Serializable
data class ActionData(
    val actionId: String,
    val actionText: String? = null,
    val showApproveButton: Boolean = true,
    val showRefineButton: Boolean = true
)
