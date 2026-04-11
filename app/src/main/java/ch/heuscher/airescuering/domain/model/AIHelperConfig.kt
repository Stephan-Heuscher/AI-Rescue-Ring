package ch.heuscher.airescuering.domain.model

/**
 * Configuration for J-AI-mes Butler
 * Voice-first design with butler personality
 */
data class AIHelperConfig(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val useVoiceInput: Boolean = true,
    val autoExecuteSuggestions: Boolean = false,
    val model: String = "gemini-3.1-flash-lite-preview",
    val voiceFirstMode: Boolean = true,
    val autoSpeakResponses: Boolean = true,
    val speakingSpeed: Float = 0.88f,
    val proactiveLevel: Int = 0 // 0=passive, 1=notifications, 2=screen, 3=full
)
