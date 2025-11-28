package ch.heuscher.airescuering.domain.model

/**
 * Configuration for the AI Helper feature
 * Voice-first mode optimized for elderly users
 */
data class AIHelperConfig(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val useVoiceInput: Boolean = true,
    val autoExecuteSuggestions: Boolean = false,
    val model: String = "gemini-3-pro-preview", // Gemini 3 Pro for best reasoning
    val voiceFirstMode: Boolean = true, // Voice-first for elderly users
    val autoSpeakResponses: Boolean = true // Always speak AI responses aloud
)
