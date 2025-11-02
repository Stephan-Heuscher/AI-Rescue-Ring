package ch.heuscher.back_home_dot.util

/**
 * Application-wide constants extracted from various classes
 * to improve maintainability and avoid magic numbers.
 */
object AppConstants {

    // Overlay dimensions and appearance
    const val DOT_SIZE_DP = 48
    const val DOT_STROKE_WIDTH_DP = 3
    const val RESCUE_RING_SIZE_MULTIPLIER = 1.2f

    // Gesture timeouts (milliseconds)
    const val GESTURE_DOUBLE_TAP_TIMEOUT_MS = 300L
    const val GESTURE_LONG_PRESS_TIMEOUT_MS = 500L
    const val RESCUE_RING_TAP_DELAY_MS = 200L

    // Keyboard detection
    const val KEYBOARD_CHECK_INTERVAL_MS = 100L
    const val KEYBOARD_HEIGHT_ESTIMATE_PERCENT = 0.38f
    const val KEYBOARD_THRESHOLD_PERCENT = 0.15f
    const val KEYBOARD_MARGIN_MULTIPLIER = 1.5f

    // Animation and timing
    const val RECENTS_TIMEOUT_DEFAULT_MS = 100L
    const val RECENTS_TIMEOUT_MIN_MS = 50L
    const val RECENTS_TIMEOUT_MAX_MS = 300L

    // Accessibility delays
    const val ACCESSIBILITY_BACK_DELAY_MS = 100L
    const val ACCESSIBILITY_RECENTS_DELAY_MS = 150L

    // UI constraints
    const val ALPHA_MIN = 0
    const val ALPHA_MAX = 255
    const val ALPHA_DEFAULT = 255

    // Default positions (as percentage of screen)
    const val DEFAULT_POSITION_X_PERCENT = 0.1f
    const val DEFAULT_POSITION_Y_PERCENT = 0.1f
    const val DEFAULT_POSITION_X_PX = 100
    const val DEFAULT_POSITION_Y_PX = 100

    // Default screen dimensions (fallback)
    const val DEFAULT_SCREEN_WIDTH = 1080
    const val DEFAULT_SCREEN_HEIGHT = 1920

    // Intent actions
    const val ACTION_UPDATE_SETTINGS = "ch.heuscher.back_home_dot.UPDATE_SETTINGS"

    // SharedPreferences
    const val PREFS_NAME = "overlay_settings"
    const val KEY_ENABLED = "overlay_enabled"
    const val KEY_COLOR = "overlay_color"
    const val KEY_ALPHA = "overlay_alpha"
    const val KEY_POSITION_X = "position_x"
    const val KEY_POSITION_Y = "position_y"
    const val KEY_POSITION_X_PERCENT = "position_x_percent"
    const val KEY_POSITION_Y_PERCENT = "position_y_percent"
    const val KEY_SCREEN_WIDTH = "screen_width"
    const val KEY_SCREEN_HEIGHT = "screen_height"
    const val KEY_ROTATION = "rotation"
    const val KEY_RECENTS_TIMEOUT = "recents_timeout"
    const val KEY_KEYBOARD_AVOIDANCE = "keyboard_avoidance"
    const val KEY_RESCUE_RING = "rescue_ring"

    // Default values
    const val DEFAULT_COLOR = -0xBBBC // Blue: 0xFF2196F3
    const val DEFAULT_ENABLED = false
    const val DEFAULT_KEYBOARD_AVOIDANCE = false
    const val DEFAULT_RESCUE_RING = false
}