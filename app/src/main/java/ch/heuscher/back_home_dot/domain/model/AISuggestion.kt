package ch.heuscher.back_home_dot.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents an AI-suggested action for the user to approve or refine
 */
@Serializable
data class AISuggestion(
    val id: String,
    val title: String,
    val description: String,
    val actions: List<SuggestedAction>,
    val timestamp: Long = System.currentTimeMillis(),
    val status: SuggestionStatus = SuggestionStatus.PENDING
)

@Serializable
data class SuggestedAction(
    val actionType: ActionType,
    val description: String,
    val parameters: Map<String, String> = emptyMap()
)

enum class ActionType {
    TAP,
    LONG_PRESS,
    SWIPE,
    OPEN_APP,
    NAVIGATE_BACK,
    NAVIGATE_HOME,
    OPEN_SETTINGS,
    SEARCH,
    CALL,
    MESSAGE,
    OTHER
}

enum class SuggestionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    REFINED
}
