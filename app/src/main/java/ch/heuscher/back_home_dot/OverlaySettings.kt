package ch.heuscher.back_home_dot

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class OverlaySettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "overlay_settings"
        private const val KEY_ENABLED = "overlay_enabled"
        private const val KEY_COLOR = "overlay_color"
        private const val KEY_ALPHA = "overlay_alpha"
        private const val KEY_POSITION_X = "position_x"
        private const val KEY_POSITION_Y = "position_y"
        private const val KEY_POSITION_X_PERCENT = "position_x_percent"
        private const val KEY_POSITION_Y_PERCENT = "position_y_percent"
        private const val KEY_SCREEN_WIDTH = "screen_width"
        private const val KEY_SCREEN_HEIGHT = "screen_height"
        private const val KEY_ROTATION = "rotation"
        private const val KEY_RECENTS_TIMEOUT = "recents_timeout"
        private const val KEY_KEYBOARD_AVOIDANCE = "keyboard_avoidance"
        private const val KEY_RESCUE_RING = "rescue_ring"

        private const val DEFAULT_COLOR = 0xFF2196F3.toInt() // Blue
        private const val DEFAULT_ALPHA = 255 // Fully opaque
        private const val DEFAULT_POSITION_X = 100
        private const val DEFAULT_POSITION_Y = 100
        private const val DEFAULT_RECENTS_TIMEOUT = 100 // milliseconds
    }

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var color: Int
        get() = prefs.getInt(KEY_COLOR, DEFAULT_COLOR)
        set(value) = prefs.edit().putInt(KEY_COLOR, value).apply()

    var alpha: Int
        get() = prefs.getInt(KEY_ALPHA, DEFAULT_ALPHA)
        set(value) = prefs.edit().putInt(KEY_ALPHA, value.coerceIn(0, 255)).apply()

    var positionX: Int
        get() = prefs.getInt(KEY_POSITION_X, DEFAULT_POSITION_X)
        set(value) = prefs.edit().putInt(KEY_POSITION_X, value.coerceAtLeast(0)).apply()

    var positionY: Int
        get() = prefs.getInt(KEY_POSITION_Y, DEFAULT_POSITION_Y)
        set(value) = prefs.edit().putInt(KEY_POSITION_Y, value.coerceAtLeast(0)).apply()

    var recentsTimeout: Long
        get() = prefs.getLong(KEY_RECENTS_TIMEOUT, DEFAULT_RECENTS_TIMEOUT.toLong())
        set(value) = prefs.edit().putLong(KEY_RECENTS_TIMEOUT, value.coerceIn(50, 300)).apply()

    var keyboardAvoidanceEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEYBOARD_AVOIDANCE, false) // Default to false (disabled)
        set(value) = prefs.edit().putBoolean(KEY_KEYBOARD_AVOIDANCE, value).apply()

    var rescueRingEnabled: Boolean
        get() = prefs.getBoolean(KEY_RESCUE_RING, false) // Default to false (disabled)
        set(value) = prefs.edit().putBoolean(KEY_RESCUE_RING, value).apply()

    var positionXPercent: Float
        get() = prefs.getFloat(KEY_POSITION_X_PERCENT, 0.1f)
        set(value) = prefs.edit().putFloat(KEY_POSITION_X_PERCENT, value.coerceIn(0f, 1f)).apply()

    var positionYPercent: Float
        get() = prefs.getFloat(KEY_POSITION_Y_PERCENT, 0.1f)
        set(value) = prefs.edit().putFloat(KEY_POSITION_Y_PERCENT, value.coerceIn(0f, 1f)).apply()

    var screenWidth: Int
        get() = prefs.getInt(KEY_SCREEN_WIDTH, 1080)
        set(value) = prefs.edit().putInt(KEY_SCREEN_WIDTH, value.coerceAtLeast(0)).apply()

    var screenHeight: Int
        get() = prefs.getInt(KEY_SCREEN_HEIGHT, 1920)
        set(value) = prefs.edit().putInt(KEY_SCREEN_HEIGHT, value.coerceAtLeast(0)).apply()

    var rotation: Int
        get() = prefs.getInt(KEY_ROTATION, 0)
        set(value) = prefs.edit().putInt(KEY_ROTATION, value).apply()

    fun getColorWithAlpha(): Int {
        val baseColor = color
        return Color.argb(
            alpha,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )
    }
}
