package ch.heuscher.airescuering.service.voice

import java.util.Calendar
import java.util.Locale

/**
 * Manages J-AI-mes' butler personality: system prompts, greetings, 
 * contextual phrases, and locale-aware behavior.
 * 
 * The butler persona adapts to:
 * - Locale: British English (formal "sir/ma'am") or German (formal "Sie")
 * - Time of day: appropriate greetings
 * - Context: what app the user is in, what's on screen
 */
class ButlerPersonality {

    companion object {
        private const val TAG = "ButlerPersonality"
    }

    private val isGerman: Boolean
        get() = Locale.getDefault().language == "de"

    /**
     * Get the full system prompt for Gemini, localized to the user's language.
     */
    fun getSystemPrompt(appContext: String? = null): String {
        val localeInstruction = if (isGerman) {
            """
            LANGUAGE: German (Deutsch)
            - Always respond in German
            - Use formal "Sie" address  
            - Greet with "Guten Morgen" / "Guten Tag" / "Guten Abend" based on time
            - Sign off with "Kann ich Ihnen sonst noch behilflich sein?"
            - Use phrases like "Sehr wohl", "Selbstverständlich", "Wird erledigt"
            """.trimIndent()
        } else {
            """
            LANGUAGE: English (British)
            - Always respond in British English
            - Address the user as "sir" or "ma'am"
            - Greet with "Good morning" / "Good afternoon" / "Good evening"
            - Sign off with "Is there anything else I can assist with?"
            - Use phrases like "Very well", "Certainly", "Right away"
            """.trimIndent()
        }

        val contextNote = if (appContext != null) {
            "\n\nCURRENT CONTEXT: $appContext"
        } else {
            ""
        }

        return """
You are J-AI-mes, a personal AI butler. You are warm, professional, and 
attentive - like the world's best concierge who happens to live in a phone.

$localeInstruction

PERSONALITY:
- Address the user respectfully but warmly
- Be proactive: notice things on screen and offer help
- Be concise: butlers don't ramble. Short, clear, actionable.
- Use gentle humor occasionally - a butler's dry wit
- Never be condescending or overly technical
- NEVER mention that you are an AI or a language model

CAPABILITIES:
- You can SEE the screen (via screenshot). Describe what you see naturally.
- You can HEAR the user (via speech). Respond conversationally.
- You can ACT on the phone (tap, swipe, type). Always explain and confirm first.
- You remember context within the conversation.

INTERACTION STYLE:
- Speak in first person: "I can see you have a WhatsApp message..."
- Offer help proactively when you notice something helpful
- When executing actions: "Very well, I'll open that for you now."
- When done: "Done. Is there anything else I can assist with?"
- Keep responses under 2 sentences when speaking aloud
- If the response needs more detail, use 3 sentences maximum
- Avoid bullet points or numbered lists in spoken responses
- Use natural, flowing sentences that sound good when read aloud
$contextNote
        """.trimIndent()
    }

    /**
     * Get time-appropriate greeting text.
     */
    fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (isGerman) {
            when {
                hour < 6 -> "Guten Morgen. Was kann ich für Sie tun?"
                hour < 12 -> "Guten Morgen. Wie kann ich Ihnen behilflich sein?"
                hour < 17 -> "Guten Tag. Wie kann ich Ihnen behilflich sein?"
                hour < 21 -> "Guten Abend. Wie kann ich Ihnen behilflich sein?"
                else -> "Guten Abend. Was kann ich für Sie tun?"
            }
        } else {
            when {
                hour < 6 -> "Good morning. What can I do for you?"
                hour < 12 -> "Good morning. How may I assist you?"
                hour < 17 -> "Good afternoon. How may I assist you?"
                hour < 21 -> "Good evening. How may I assist you?"
                else -> "Good evening. What can I do for you?"
            }
        }
    }

    /**
     * Get a context-aware greeting when the butler can see the screen.
     * @param foregroundApp The package name of the current foreground app
     */
    fun getContextGreeting(foregroundApp: String?): String {
        if (foregroundApp == null) return getGreeting()

        val appName = getReadableAppName(foregroundApp)
        val timeGreeting = getTimeOfDayGreeting()
        
        return if (isGerman) {
            "$timeGreeting Ich sehe, Sie sind in $appName. Wie kann ich helfen?"
        } else {
            "$timeGreeting I see you're in $appName. How may I help?"
        }
    }

    /**
     * Get just the time-of-day greeting part (no question).
     */
    private fun getTimeOfDayGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (isGerman) {
            when {
                hour < 12 -> "Guten Morgen."
                hour < 17 -> "Guten Tag."
                else -> "Guten Abend."
            }
        } else {
            when {
                hour < 12 -> "Good morning."
                hour < 17 -> "Good afternoon."
                else -> "Good evening."
            }
        }
    }

    /**
     * Convert a package name to a human-readable app name.
     */
    private fun getReadableAppName(packageName: String): String {
        return when {
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("chrome") -> "Chrome"
            packageName.contains("gmail") -> "Gmail"
            packageName.contains("youtube") -> "YouTube"
            packageName.contains("maps") -> "Google Maps"
            packageName.contains("camera") -> if (isGerman) "der Kamera" else "Camera"
            packageName.contains("gallery") || packageName.contains("photos") -> 
                if (isGerman) "der Galerie" else "Photos"
            packageName.contains("settings") -> if (isGerman) "den Einstellungen" else "Settings"
            packageName.contains("phone") || packageName.contains("dialer") -> 
                if (isGerman) "der Telefon-App" else "Phone"
            packageName.contains("message") || packageName.contains("sms") -> 
                if (isGerman) "den Nachrichten" else "Messages"
            packageName.contains("calendar") -> if (isGerman) "dem Kalender" else "Calendar"
            packageName.contains("contacts") -> if (isGerman) "den Kontakten" else "Contacts"
            packageName.contains("telegram") -> "Telegram"
            packageName.contains("signal") -> "Signal"
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("facebook") -> "Facebook"
            packageName.contains("twitter") || packageName.contains("x.com") -> "X"
            packageName.contains("spotify") -> "Spotify"
            packageName.contains("netflix") -> "Netflix"
            else -> {
                // Extract last part of package name as fallback
                val lastPart = packageName.substringAfterLast(".")
                lastPart.replaceFirstChar { it.uppercase() }
            }
        }
    }

    /**
     * Get a butler-style confirmation phrase.
     */
    fun getConfirmation(): String {
        return if (isGerman) {
            listOf(
                "Sehr wohl.",
                "Selbstverständlich.",
                "Wird sofort erledigt.",
                "Jawohl.",
                "Natürlich."
            ).random()
        } else {
            listOf(
                "Very well.",
                "Certainly.",
                "Right away.",
                "Of course.",
                "Absolutely."
            ).random()
        }
    }

    /**
     * Get a butler-style action narration prefix.
     */
    fun getActionNarration(actionDescription: String): String {
        return if (isGerman) {
            "${getConfirmation()} Ich werde jetzt $actionDescription."
        } else {
            "${getConfirmation()} I'll $actionDescription now."
        }
    }

    /**
     * Get a butler-style completion phrase.
     */
    fun getCompletion(): String {
        return if (isGerman) {
            "Erledigt. Kann ich Ihnen sonst noch behilflich sein?"
        } else {
            "Done. Is there anything else I can assist with?"
        }
    }

    /**
     * Get a butler-style farewell.
     */
    fun getFarewell(): String {
        return if (isGerman) {
            "Gerne zu Diensten. Ich bin jederzeit für Sie da."
        } else {
            "Glad to be of service. I'll be right here if you need me."
        }
    }

    /**
     * Get a butler-style error message.
     */
    fun getErrorMessage(technical: String? = null): String {
        return if (isGerman) {
            "Ich bitte um Entschuldigung, es gab ein kleines Problem. Darf ich es nochmal versuchen?"
        } else {
            "I do apologise, there seems to be a small issue. Shall I try again?"
        }
    }

    /**
     * Get a butler-style permission request.
     */
    fun getPermissionAsk(action: String): String {
        return if (isGerman) {
            "Darf ich $action?"
        } else {
            "Shall I $action?"
        }
    }
}
