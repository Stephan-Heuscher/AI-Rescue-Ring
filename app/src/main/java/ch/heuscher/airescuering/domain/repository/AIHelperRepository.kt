package ch.heuscher.airescuering.domain.repository

import ch.heuscher.airescuering.domain.model.AIHelperConfig
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for AI Helper settings and operations.
 */
interface AIHelperRepository {

    fun getConfig(): Flow<AIHelperConfig>
    suspend fun updateConfig(config: AIHelperConfig)

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
