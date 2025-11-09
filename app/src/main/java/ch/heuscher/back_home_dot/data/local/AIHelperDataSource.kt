package ch.heuscher.back_home_dot.data.local

import ch.heuscher.back_home_dot.domain.model.AIHelperConfig
import kotlinx.coroutines.flow.Flow

/**
 * Data source interface for AI Helper settings.
 * Uses encrypted storage for sensitive data like API keys.
 */
interface AIHelperDataSource {

    fun getConfig(): Flow<AIHelperConfig>
    suspend fun setConfig(config: AIHelperConfig)

    fun isEnabled(): Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)

    fun getApiKey(): Flow<String>
    suspend fun setApiKey(apiKey: String)

    fun useVoiceInput(): Flow<Boolean>
    suspend fun setUseVoiceInput(enabled: Boolean)

    fun getAutoExecuteSuggestions(): Flow<Boolean>
    suspend fun setAutoExecuteSuggestions(enabled: Boolean)

    fun getModel(): Flow<String>
    suspend fun setModel(model: String)
}
