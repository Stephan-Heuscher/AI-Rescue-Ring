package ch.heuscher.airescuering.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ch.heuscher.airescuering.domain.model.AIHelperConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Secure implementation of AIHelperDataSource using EncryptedSharedPreferences.
 * Protects API keys and sensitive data with Android Keystore encryption.
 */
class SecureAIHelperDataSource(
    private val context: Context
) : AIHelperDataSource {

    companion object {
        private const val PREFS_NAME = "ai_helper_secure_prefs"
        private const val KEY_ENABLED = "ai_helper_enabled"
        private const val KEY_API_KEY = "ai_helper_api_key"
        private const val KEY_VOICE_INPUT = "ai_helper_voice_input"
        private const val KEY_AUTO_EXECUTE = "ai_helper_auto_execute"
        private const val KEY_MODEL = "ai_helper_model"
        private const val DEFAULT_MODEL = "gemini-2.0-flash-exp"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Helper function to create flows that emit on preference changes
    private fun <T> getPreferenceFlow(
        key: String,
        defaultValue: T,
        getter: (SharedPreferences, String, T) -> T
    ): Flow<T> = callbackFlow {
        // Send initial value
        trySend(getter(encryptedPrefs, key, defaultValue))

        // Listen for changes
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                trySend(getter(encryptedPrefs, key, defaultValue))
            }
        }

        encryptedPrefs.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            encryptedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
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
        encryptedPrefs.edit().apply {
            putBoolean(KEY_ENABLED, config.enabled)
            putString(KEY_API_KEY, config.apiKey)
            putBoolean(KEY_VOICE_INPUT, config.useVoiceInput)
            putBoolean(KEY_AUTO_EXECUTE, config.autoExecuteSuggestions)
            putString(KEY_MODEL, config.model)
            apply()
        }
    }

    override fun isEnabled(): Flow<Boolean> =
        getPreferenceFlow(KEY_ENABLED, false) { prefs, key, default ->
            prefs.getBoolean(key, default)
        }

    override suspend fun setEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    override fun getApiKey(): Flow<String> =
        getPreferenceFlow(KEY_API_KEY, "") { prefs, key, default ->
            prefs.getString(key, default) ?: default
        }

    override suspend fun setApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    override fun useVoiceInput(): Flow<Boolean> =
        getPreferenceFlow(KEY_VOICE_INPUT, true) { prefs, key, default ->
            prefs.getBoolean(key, default)
        }

    override suspend fun setUseVoiceInput(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_VOICE_INPUT, enabled).apply()
    }

    override fun getAutoExecuteSuggestions(): Flow<Boolean> =
        getPreferenceFlow(KEY_AUTO_EXECUTE, false) { prefs, key, default ->
            prefs.getBoolean(key, default)
        }

    override suspend fun setAutoExecuteSuggestions(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_AUTO_EXECUTE, enabled).apply()
    }

    override fun getModel(): Flow<String> =
        getPreferenceFlow(KEY_MODEL, DEFAULT_MODEL) { prefs, key, default ->
            prefs.getString(key, default) ?: default
        }

    override suspend fun setModel(model: String) {
        encryptedPrefs.edit().putString(KEY_MODEL, model).apply()
    }
}
