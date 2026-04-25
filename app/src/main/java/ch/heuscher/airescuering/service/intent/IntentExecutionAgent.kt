package ch.heuscher.airescuering.service.intent

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import ch.heuscher.airescuering.data.api.FunctionDeclaration
import ch.heuscher.airescuering.data.api.Schema
import ch.heuscher.airescuering.domain.repository.AIHelperRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class IntentExecutionAgent(
    private val context: Context,
    private val aiHelperRepository: AIHelperRepository
) {
    companion object {
        private const val TAG = "IntentExecutionAgent"
    }

    /**
     * Define the functions Gemini can call.
     */
    fun getIntentTools(): List<FunctionDeclaration> {
        return listOf(
            FunctionDeclaration(
                name = "openUrl",
                description = "Opens a specific website. ONLY use this if the user provides a full URL or if no other specialized tool fits. Never use this for general searches.",
                parameters = Schema(
                    type = "object",
                    properties = mapOf(
                        "url" to Schema(type = "string", description = "The full URL starting with https://")
                    ),
                    required = listOf("url")
                )
            ),
            FunctionDeclaration(
                name = "searchWeb",
                description = "Searches the web for general knowledge or information. Use this if no other tool (like Maps) is appropriate.",
                parameters = Schema(
                    type = "object",
                    properties = mapOf(
                        "query" to Schema(type = "string", description = "The search query")
                    ),
                    required = listOf("query")
                )
            ),
            FunctionDeclaration(
                name = "callNumber",
                description = "Dials a phone number using the phone app.",
                parameters = Schema(
                    type = "object",
                    properties = mapOf(
                        "number" to Schema(type = "string", description = "The phone number to dial")
                    ),
                    required = listOf("number")
                )
            ),
            FunctionDeclaration(
                name = "openMaps",
                description = "Opens the Google Maps app for a specific address, business, or search term. ALWAYS use this for anything related to locations, navigation, or finding places.",
                parameters = Schema(
                    type = "object",
                    properties = mapOf(
                        "location" to Schema(type = "string", description = "Address or location name to search in Maps")
                    ),
                    required = listOf("location")
                )
            ),
            FunctionDeclaration(
                name = "setTimer",
                description = "Sets a countdown timer in the clock app.",
                parameters = Schema(
                    type = "object",
                    properties = mapOf(
                        "seconds" to Schema(type = "integer", description = "Duration in seconds"),
                        "message" to Schema(type = "string", description = "Optional label for the timer")
                    ),
                    required = listOf("seconds")
                )
            ),
            FunctionDeclaration(
                name = "setAlarm",
                description = "Sets a one-time alarm in the clock app.",
                parameters = Schema(
                    type = "object",
                    properties = mapOf(
                        "hour" to Schema(type = "integer", description = "Hour of day (0-23)"),
                        "minute" to Schema(type = "integer", description = "Minute (0-59)"),
                        "message" to Schema(type = "string", description = "Optional label for the alarm")
                    ),
                    required = listOf("hour", "minute")
                )
            )
        )
    }

    /**
     * Handle the function call from Gemini. Returns true if successfully handled.
     */
    suspend fun executeIntent(
        functionName: String,
        args: Map<String, JsonElement>,
        onRequireConfirmation: (String, Intent) -> Unit
    ): Boolean {
        Log.d(TAG, "Executing intent function: $functionName with args: $args")
        
        val autonomyLevel = aiHelperRepository.getAutonomyLevel().first()
        val requireConfirmation = autonomyLevel == 0

        val intent = when (functionName) {
            "openUrl" -> {
                val url = args["url"]?.jsonPrimitive?.content ?: return false
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            "searchWeb" -> {
                val query = args["query"]?.jsonPrimitive?.content ?: return false
                Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, query)
                }
            }
            "callNumber" -> {
                val number = args["number"]?.jsonPrimitive?.content ?: return false
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            }
            "openMaps" -> {
                val loc = args["location"]?.jsonPrimitive?.content ?: return false
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(loc)}")).apply {
                    setPackage("com.google.android.apps.maps")
                }
            }
            "setTimer" -> {
                val secondsStr = args["seconds"]?.jsonPrimitive?.content ?: return false
                val msg = args["message"]?.jsonPrimitive?.content ?: "Timer"
                val seconds = secondsStr.toIntOrNull() ?: return false
                Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, msg)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, autonomyLevel > 0) // Skip UI if autonomous
                }
            }
            "setAlarm" -> {
                val hourStr = args["hour"]?.jsonPrimitive?.content ?: return false
                val minuteStr = args["minute"]?.jsonPrimitive?.content ?: return false
                val msg = args["message"]?.jsonPrimitive?.content ?: "Alarm"
                val hour = hourStr.toIntOrNull() ?: return false
                val minute = minuteStr.toIntOrNull() ?: return false
                Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, msg)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, autonomyLevel > 0) // Skip UI if autonomous
                }
            }
            else -> return false
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (requireConfirmation) {
            val userFriendlyName = getFriendlyName(functionName, args)
            onRequireConfirmation(userFriendlyName, intent)
        } else {
            try {
                // If Maps package not found, let it fall back
                if (intent.resolveActivity(context.packageManager) == null && intent.getPackage() != null) {
                    intent.setPackage(null)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start activity for intent", e)
                return false
            }
        }
        
        return true
    }
    
    private fun getFriendlyName(functionName: String, args: Map<String, JsonElement>): String {
        return when (functionName) {
            "openUrl" -> "Open website"
            "searchWeb" -> "Search for '${args["query"]?.jsonPrimitive?.content}'"
            "callNumber" -> "Call ${args["number"]?.jsonPrimitive?.content}"
            "openMaps" -> "Open Maps for '${args["location"]?.jsonPrimitive?.content}'"
            "setTimer" -> "Set a timer"
            "setAlarm" -> "Set an alarm"
            else -> "Perform action"
        }
    }
}
