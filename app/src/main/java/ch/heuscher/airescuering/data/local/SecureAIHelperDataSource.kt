package ch.heuscher.airescuering.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ch.heuscher.airescuering.domain.model.AIHelperConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Implementation of AIHelperDataSource using standard SharedPreferences.
 * Uses unencrypted storage to allow backup/restore across device installations.
 * Note: API keys are sensitive but stored unencrypted to enable cloud backup.
 */
class SecureAIHelperDataSource(
    private val context: Context
) : AIHelperDataSource {

    companion object {
        private const val TAG = "AIHelperDataSource"
        private const val PREFS_NAME = "ai_helper_prefs"
        private const val ENCRYPTED_PREFS_NAME = "ai_helper_secure_prefs"
        private const val KEY_ENABLED = "ai_helper_enabled"
        private const val KEY_API_KEY = "ai_helper_api_key"
        private const val KEY_VOICE_INPUT = "ai_helper_voice_input"
        private const val KEY_AUTO_EXECUTE = "ai_helper_auto_execute"
        private const val KEY_MODEL = "ai_helper_model"
        private const val DEFAULT_MODEL = "gemini-2.5-computer-use-preview-10-2025"
        private const val KEY_MIGRATED = "migrated_from_encrypted"
    }

    // Standard SharedPreferences for backup-friendly storage
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    init {
        // Migrate from encrypted storage if needed
        migrateFromEncryptedStorage()
    }

    /**
     * Migrates data from old EncryptedSharedPreferences to standard SharedPreferences.
     * This is a one-time migration to enable backup/restore functionality.
     */
    private fun migrateFromEncryptedStorage() {
        // Check if already migrated
        if (prefs.getBoolean(KEY_MIGRATED, false)) {
            return
        }

        try {
            // Try to access old encrypted preferences
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // Migrate all values
            val apiKey = encryptedPrefs.getString(KEY_API_KEY, "") ?: ""
            val enabled = encryptedPrefs.getBoolean(KEY_ENABLED, false)
            val voiceInput = encryptedPrefs.getBoolean(KEY_VOICE_INPUT, true)
            val autoExecute = encryptedPrefs.getBoolean(KEY_AUTO_EXECUTE, false)
            val model = encryptedPrefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

            if (apiKey.isNotEmpty() || enabled) {
                Log.d(TAG, "Migrating from encrypted storage to standard SharedPreferences")
                prefs.edit().apply {
                    putString(KEY_API_KEY, apiKey)
                    putBoolean(KEY_ENABLED, enabled)
                    putBoolean(KEY_VOICE_INPUT, voiceInput)
                    putBoolean(KEY_AUTO_EXECUTE, autoExecute)
                    putString(KEY_MODEL, model)
                    putBoolean(KEY_MIGRATED, true)
                    apply()
                }
                Log.d(TAG, "Migration completed successfully")
            } else {
                // No data to migrate, just mark as migrated
                prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            }
        } catch (e: Exception) {
            // Encrypted prefs may not exist or be accessible, that's fine
            Log.d(TAG, "No encrypted storage to migrate or migration failed: ${e.message}")
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        }
    }

    // Helper function to create flows that emit on preference changes
    private fun <T> getPreferenceFlow(
        key: String,
        defaultValue: T,
        getter: (SharedPreferences, String, T) -> T
    ): Flow<T> = callbackFlow {
        // Send initial value
        trySend(getter(prefs, key, defaultValue))

        // Listen for changes
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                trySend(getter(prefs, key, defaultValue))
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    override fun getConfig(): Flow<AIHelperConfig> = combine(
        isEnabled(),
        getApiKey(),
        useVoiceInput(),
        getAutoExecuteSuggestions(),
        getModel()
    ) { enabled, apiKey, voiceInput, autoExecute, model ->
        AIHelperConfig(
            enabled = enabled,
            apiKey = apiKey,
            useVoiceInput = voiceInput,
            autoExecuteSuggestions = autoExecute,
            model = model
        )
    }

    override suspend fun setConfig(config: AIHelperConfig) {
        prefs.edit().apply {
            putBoolean(KEY_ENABLED, config.enabled)
            putString(KEY_API_KEY, config.apiKey)
            putBoolean(KEY_VOICE_INPUT, config.useVoiceInput)
            putBoolean(KEY_AUTO_EXECUTE, config.autoExecuteSuggestions)
            putString(KEY_MODEL, config.model)
            apply()
        }
    }

    override fun isEnabled(): Flow<Boolean> =
        getPreferenceFlow(KEY_ENABLED, false) { sharedPrefs, key, default ->
            sharedPrefs.getBoolean(key, default)
        }

    override suspend fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    override fun getApiKey(): Flow<String> =
        getPreferenceFlow(KEY_API_KEY, "") { sharedPrefs, key, default ->
            sharedPrefs.getString(key, default) ?: default
        }

    override suspend fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    override fun useVoiceInput(): Flow<Boolean> =
        getPreferenceFlow(KEY_VOICE_INPUT, true) { sharedPrefs, key, default ->
            sharedPrefs.getBoolean(key, default)
        }

    override suspend fun setUseVoiceInput(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_INPUT, enabled).apply()
    }

    override fun getAutoExecuteSuggestions(): Flow<Boolean> =
        getPreferenceFlow(KEY_AUTO_EXECUTE, false) { sharedPrefs, key, default ->
            sharedPrefs.getBoolean(key, default)
        }

    override suspend fun setAutoExecuteSuggestions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_EXECUTE, enabled).apply()
    }

    override fun getModel(): Flow<String> =
        getPreferenceFlow(KEY_MODEL, DEFAULT_MODEL) { sharedPrefs, key, default ->
            sharedPrefs.getString(key, default) ?: default
        }

    override suspend fun setModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model).apply()
    }
}
