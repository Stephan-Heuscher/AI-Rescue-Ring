package ch.heuscher.airescuering.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import ch.heuscher.airescuering.domain.model.AIMessage
import ch.heuscher.airescuering.domain.model.ChatSession
import ch.heuscher.airescuering.domain.model.MessageRole
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages persistence of chat sessions using SharedPreferences
 */
class ChatPersistenceManager(context: Context) {
    companion object {
        private const val TAG = "ChatPersistenceManager"
        private const val PREFS_NAME = "chat_history"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_CURRENT_SESSION_ID = "current_session_id"
        private const val MAX_SESSIONS = 50 // Keep last 50 chat sessions
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save a chat session
     */
    fun saveSession(session: ChatSession) {
        try {
            val sessions = loadAllSessions().toMutableList()

            // Remove existing session with same ID if present
            sessions.removeAll { it.id == session.id }

            // Add new/updated session
            sessions.add(0, session) // Add to beginning

            // Keep only MAX_SESSIONS most recent
            val trimmedSessions = sessions.take(MAX_SESSIONS)

            // Serialize to JSON
            val jsonArray = JSONArray()
            trimmedSessions.forEach { chatSession ->
                jsonArray.put(sessionToJson(chatSession))
            }

            prefs.edit()
                .putString(KEY_SESSIONS, jsonArray.toString())
                .apply()

            Log.d(TAG, "Saved session ${session.id} with ${session.messages.size} messages")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving session", e)
        }
    }

    /**
     * Load all chat sessions
     */
    fun loadAllSessions(): List<ChatSession> {
        try {
            val sessionsJson = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
            val jsonArray = JSONArray(sessionsJson)

            val sessions = mutableListOf<ChatSession>()
            for (i in 0 until jsonArray.length()) {
                val sessionJson = jsonArray.getJSONObject(i)
                sessions.add(jsonToSession(sessionJson))
            }

            Log.d(TAG, "Loaded ${sessions.size} sessions")
            return sessions
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sessions", e)
            return emptyList()
        }
    }

    /**
     * Load a specific session by ID
     */
    fun loadSession(sessionId: String): ChatSession? {
        return loadAllSessions().find { it.id == sessionId }
    }

    /**
     * Delete a session
     */
    fun deleteSession(sessionId: String) {
        try {
            val sessions = loadAllSessions().filter { it.id != sessionId }

            val jsonArray = JSONArray()
            sessions.forEach { chatSession ->
                jsonArray.put(sessionToJson(chatSession))
            }

            prefs.edit()
                .putString(KEY_SESSIONS, jsonArray.toString())
                .apply()

            Log.d(TAG, "Deleted session $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting session", e)
        }
    }

    /**
     * Save current session ID
     */
    fun saveCurrentSessionId(sessionId: String) {
        prefs.edit()
            .putString(KEY_CURRENT_SESSION_ID, sessionId)
            .apply()
    }

    /**
     * Load current session ID
     */
    fun loadCurrentSessionId(): String? {
        return prefs.getString(KEY_CURRENT_SESSION_ID, null)
    }

    /**
     * Clear current session ID
     */
    fun clearCurrentSessionId() {
        prefs.edit()
            .remove(KEY_CURRENT_SESSION_ID)
            .apply()
    }

    /**
     * Convert ChatSession to JSON
     */
    private fun sessionToJson(session: ChatSession): JSONObject {
        val json = JSONObject()
        json.put("id", session.id)
        json.put("startTime", session.startTime)

        val messagesArray = JSONArray()
        session.messages.forEach { message ->
            val messageJson = JSONObject()
            messageJson.put("id", message.id)
            messageJson.put("content", message.content)
            messageJson.put("role", message.role.name)
            messageJson.put("timestamp", message.timestamp)
            messagesArray.put(messageJson)
        }
        json.put("messages", messagesArray)

        return json
    }

    /**
     * Convert JSON to ChatSession
     */
    private fun jsonToSession(json: JSONObject): ChatSession {
        val id = json.getString("id")
        val startTime = json.getLong("startTime")

        val messages = mutableListOf<AIMessage>()
        val messagesArray = json.getJSONArray("messages")
        for (i in 0 until messagesArray.length()) {
            val messageJson = messagesArray.getJSONObject(i)
            val message = AIMessage(
                id = messageJson.getString("id"),
                content = messageJson.getString("content"),
                role = MessageRole.valueOf(messageJson.getString("role")),
                timestamp = messageJson.getLong("timestamp")
            )
            messages.add(message)
        }

        return ChatSession(id, startTime, messages)
    }

    /**
     * Clear all sessions (for debugging/testing)
     */
    fun clearAllSessions() {
        prefs.edit()
            .remove(KEY_SESSIONS)
            .remove(KEY_CURRENT_SESSION_ID)
            .apply()
        Log.d(TAG, "Cleared all sessions")
    }
}
