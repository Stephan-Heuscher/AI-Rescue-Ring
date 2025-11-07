package ch.heuscher.back_home_dot.domain.repository

import ch.heuscher.back_home_dot.domain.model.DotPosition
import ch.heuscher.back_home_dot.domain.model.DotPositionPercent
import ch.heuscher.back_home_dot.domain.model.OverlaySettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for overlay settings.
 * Defines the contract for settings data operations.
 */
interface SettingsRepository {

    // Overlay state
    fun isOverlayEnabled(): Flow<Boolean>
    suspend fun setOverlayEnabled(enabled: Boolean)

    // Appearance settings
    fun getColor(): Flow<Int>
    suspend fun setColor(color: Int)

    fun getAlpha(): Flow<Int>
    suspend fun setAlpha(alpha: Int)

    // Position settings
    fun getPosition(): Flow<DotPosition>
    suspend fun setPosition(position: DotPosition)

    fun getPositionPercent(): Flow<DotPositionPercent>
    suspend fun setPositionPercent(percent: DotPositionPercent)

    // Timing settings
    fun getRecentsTimeout(): Flow<Long>
    suspend fun setRecentsTimeout(timeout: Long)

    // Feature toggles
    fun isKeyboardAvoidanceEnabled(): Flow<Boolean>
    suspend fun setKeyboardAvoidanceEnabled(enabled: Boolean)

    fun getTapBehavior(): Flow<String>
    suspend fun setTapBehavior(behavior: String)

    // Screen information
    fun getScreenWidth(): Flow<Int>
    suspend fun setScreenWidth(width: Int)

    fun getScreenHeight(): Flow<Int>
    suspend fun setScreenHeight(height: Int)

    fun getRotation(): Flow<Int>
    suspend fun setRotation(rotation: Int)

    // Composite operations
    fun getAllSettings(): Flow<OverlaySettings>
    suspend fun updateSettings(settings: OverlaySettings)
}