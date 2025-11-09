package ch.heuscher.airescuering.data.repository

import ch.heuscher.airescuering.data.local.AIHelperDataSource
import ch.heuscher.airescuering.domain.model.AIHelperConfig
import ch.heuscher.airescuering.domain.repository.AIHelperRepository
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of AIHelperRepository.
 * Delegates to AIHelperDataSource for data persistence.
 */
class AIHelperRepositoryImpl(
    private val dataSource: AIHelperDataSource
) : AIHelperRepository {

    override fun getConfig(): Flow<AIHelperConfig> = dataSource.getConfig()

    override suspend fun updateConfig(config: AIHelperConfig) {
        dataSource.setConfig(config)
    }

    override fun isEnabled(): Flow<Boolean> = dataSource.isEnabled()

    override suspend fun setEnabled(enabled: Boolean) {
        dataSource.setEnabled(enabled)
    }

    override fun getApiKey(): Flow<String> = dataSource.getApiKey()

    override suspend fun setApiKey(apiKey: String) {
        dataSource.setApiKey(apiKey)
    }

    override fun useVoiceInput(): Flow<Boolean> = dataSource.useVoiceInput()

    override suspend fun setUseVoiceInput(enabled: Boolean) {
        dataSource.setUseVoiceInput(enabled)
    }

    override fun getAutoExecuteSuggestions(): Flow<Boolean> = dataSource.getAutoExecuteSuggestions()

    override suspend fun setAutoExecuteSuggestions(enabled: Boolean) {
        dataSource.setAutoExecuteSuggestions(enabled)
    }

    override fun getModel(): Flow<String> = dataSource.getModel()

    override suspend fun setModel(model: String) {
        dataSource.setModel(model)
    }
}
