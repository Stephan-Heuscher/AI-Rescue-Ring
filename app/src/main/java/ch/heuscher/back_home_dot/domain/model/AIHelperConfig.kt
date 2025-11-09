package ch.heuscher.back_home_dot.domain.model

/**
 * Configuration for the AI Helper feature
 */
data class AIHelperConfig(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val useVoiceInput: Boolean = true,
    val autoExecuteSuggestions: Boolean = false,
    val model: String = "gemini-2.0-flash-exp" // Gemini 2.0 Flash with thinking mode
)
