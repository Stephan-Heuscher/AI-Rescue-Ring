package ch.heuscher.airescuering.data.repository

import ch.heuscher.airescuering.data.local.SettingsDataSource
import ch.heuscher.airescuering.domain.model.DotPosition
import ch.heuscher.airescuering.domain.model.DotPositionPercent
import ch.heuscher.airescuering.domain.model.OverlaySettings
import ch.heuscher.airescuering.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Repository implementation that coordinates data operations.
 * Combines multiple data sources and provides domain-level operations.
 */
class SettingsRepositoryImpl(
    private val dataSource: SettingsDataSource
) : SettingsRepository {

    override fun isOverlayEnabled(): Flow<Boolean> = dataSource.isOverlayEnabled()

    override suspend fun setOverlayEnabled(enabled: Boolean) {
        dataSource.setOverlayEnabled(enabled)
    }

    override fun getColor(): Flow<Int> = dataSource.getColor()

    override suspend fun setColor(color: Int) {
        dataSource.setColor(color)
    }

    override fun getAlpha(): Flow<Int> = dataSource.getAlpha()

    override suspend fun setAlpha(alpha: Int) {
        dataSource.setAlpha(alpha)
    }

    override fun getSize(): Flow<Int> = dataSource.getSize()

    override suspend fun setSize(size: Int) {
        dataSource.setSize(size)
    }

    override fun getPosition(): Flow<DotPosition> = combine(
        dataSource.getPositionX(),
        dataSource.getPositionY(),
        dataSource.getScreenWidth(),
        dataSource.getScreenHeight(),
        dataSource.getRotation()
    ) { x, y, width, height, rotation ->
        DotPosition(x, y, width, height, rotation)
    }

    override suspend fun setPosition(position: DotPosition) {
        dataSource.setPositionX(position.x)
        dataSource.setPositionY(position.y)
        dataSource.setScreenWidth(position.screenWidth)
        dataSource.setScreenHeight(position.screenHeight)
        dataSource.setRotation(position.rotation)
        val percent = position.toPercentages()
        setPositionPercent(percent)
    }

    override fun getPositionPercent(): Flow<DotPositionPercent> = combine(
        dataSource.getPositionXPercent(),
        dataSource.getPositionYPercent()
    ) { xPercent, yPercent ->
        DotPositionPercent(xPercent, yPercent)
    }

    override suspend fun setPositionPercent(percent: DotPositionPercent) {
        dataSource.setPositionXPercent(percent.xPercent)
        dataSource.setPositionYPercent(percent.yPercent)
    }

    override fun getRecentsTimeout(): Flow<Long> = dataSource.getRecentsTimeout()

    override suspend fun setRecentsTimeout(timeout: Long) {
        dataSource.setRecentsTimeout(timeout)
    }

    override fun isKeyboardAvoidanceEnabled(): Flow<Boolean> = dataSource.isKeyboardAvoidanceEnabled()

    override suspend fun setKeyboardAvoidanceEnabled(enabled: Boolean) {
        dataSource.setKeyboardAvoidanceEnabled(enabled)
    }

    override fun getTapBehavior(): Flow<String> = dataSource.getTapBehavior()

    override suspend fun setTapBehavior(behavior: String) {
        dataSource.setTapBehavior(behavior)
    }

    override fun isVibrationEnabled(): Flow<Boolean> = dataSource.isVibrationEnabled()

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        dataSource.setVibrationEnabled(enabled)
    }

    override fun isPositionLocked(): Flow<Boolean> = dataSource.isPositionLocked()

    override suspend fun setPositionLocked(locked: Boolean) {
        dataSource.setPositionLocked(locked)
    }

    override fun getScreenWidth(): Flow<Int> = dataSource.getScreenWidth()

    override suspend fun setScreenWidth(width: Int) {
        dataSource.setScreenWidth(width)
    }

    override fun getScreenHeight(): Flow<Int> = dataSource.getScreenHeight()

    override suspend fun setScreenHeight(height: Int) {
        dataSource.setScreenHeight(height)
    }

    override fun getRotation(): Flow<Int> = dataSource.getRotation()

    override suspend fun setRotation(rotation: Int) {
        dataSource.setRotation(rotation)
    }

    override fun getAllSettings(): Flow<OverlaySettings> = combine(
        isOverlayEnabled(),
        getColor(),
        getAlpha(),
        getSize(),
        getPosition(),
        getPositionPercent(),
        getRecentsTimeout(),
        isKeyboardAvoidanceEnabled(),
        getTapBehavior(),
        isVibrationEnabled(),
        isPositionLocked(),
        getScreenWidth(),
        getScreenHeight(),
        getRotation()
    ) { values ->
        OverlaySettings(
            isEnabled = values[0] as Boolean,
            color = values[1] as Int,
            alpha = values[2] as Int,
            size = values[3] as Int,
            position = values[4] as DotPosition,
            positionPercent = values[5] as DotPositionPercent,
            recentsTimeout = values[6] as Long,
            keyboardAvoidanceEnabled = values[7] as Boolean,
            tapBehavior = values[8] as String,
            vibrationEnabled = values[9] as Boolean,
            positionLocked = values[10] as Boolean,
            screenWidth = values[11] as Int,
            screenHeight = values[12] as Int,
            rotation = values[13] as Int
        )
    }

    override suspend fun updateSettings(settings: OverlaySettings) {
        setOverlayEnabled(settings.isEnabled)
        setColor(settings.color)
        setAlpha(settings.alpha)
        setSize(settings.size)
        setPosition(settings.position)
        setPositionPercent(settings.positionPercent)
        setRecentsTimeout(settings.recentsTimeout)
        setKeyboardAvoidanceEnabled(settings.keyboardAvoidanceEnabled)
        setTapBehavior(settings.tapBehavior)
        setVibrationEnabled(settings.vibrationEnabled)
        setPositionLocked(settings.positionLocked)
        setScreenWidth(settings.screenWidth)
        setScreenHeight(settings.screenHeight)
        setRotation(settings.rotation)
    }
}