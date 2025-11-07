package ch.heuscher.back_home_dot.domain.model

import ch.heuscher.back_home_dot.util.AppConstants

/**
 * Domain model representing all overlay settings.
 * This is separate from the data layer implementation.
 */
data class OverlaySettings(
    val isEnabled: Boolean = false,
    val color: Int = AppConstants.DEFAULT_COLOR,
    val alpha: Int = AppConstants.ALPHA_DEFAULT,
    val position: DotPosition = DotPosition(
        AppConstants.DEFAULT_POSITION_X_PX,
        AppConstants.DEFAULT_POSITION_Y_PX
    ),
    val positionPercent: DotPositionPercent = DotPositionPercent(
        AppConstants.DEFAULT_POSITION_X_PERCENT,
        AppConstants.DEFAULT_POSITION_Y_PERCENT
    ),
    val recentsTimeout: Long = AppConstants.RECENTS_TIMEOUT_DEFAULT_MS,
    val keyboardAvoidanceEnabled: Boolean = AppConstants.DEFAULT_KEYBOARD_AVOIDANCE,
    val tapBehavior: String = AppConstants.DEFAULT_TAP_BEHAVIOR,
    val screenWidth: Int = AppConstants.DEFAULT_SCREEN_WIDTH,
    val screenHeight: Int = AppConstants.DEFAULT_SCREEN_HEIGHT,
    val rotation: Int = 0
) {
    /**
     * Creates a color with alpha applied.
     */
    fun getColorWithAlpha(): Int {
        val baseColor = color
        return android.graphics.Color.argb(
            alpha.coerceIn(AppConstants.ALPHA_MIN, AppConstants.ALPHA_MAX),
            android.graphics.Color.red(baseColor),
            android.graphics.Color.green(baseColor),
            android.graphics.Color.blue(baseColor)
        )
    }

    /**
     * Gets the current overlay mode based on settings.
     */
    fun getOverlayMode(): OverlayMode {
        return OverlayMode.NORMAL
    }
}