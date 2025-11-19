package ch.heuscher.airescuering.data.local

import android.content.Context
import android.content.SharedPreferences
import ch.heuscher.airescuering.util.AppConstants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * SharedPreferences implementation of SettingsDataSource.
 * Handles all settings persistence using Android SharedPreferences.
 */
class SharedPreferencesDataSource(
    private val context: Context
) : SettingsDataSource {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        AppConstants.PREFS_NAME,
        Context.MODE_PRIVATE
    )

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

    override fun isOverlayEnabled(): Flow<Boolean> =
        getPreferenceFlow(AppConstants.KEY_ENABLED, AppConstants.DEFAULT_ENABLED) { prefs, key, default ->
            prefs.getBoolean(key, default)
        }

    override suspend fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(AppConstants.KEY_ENABLED, enabled).apply()
    }

    override fun getColor(): Flow<Int> =
        getPreferenceFlow(AppConstants.KEY_COLOR, AppConstants.DEFAULT_COLOR) { prefs, key, default ->
            prefs.getInt(key, default)
        }

    override suspend fun setColor(color: Int) {
        prefs.edit().putInt(AppConstants.KEY_COLOR, color).apply()
    }

    override fun getAlpha(): Flow<Int> =
        getPreferenceFlow(AppConstants.KEY_ALPHA, AppConstants.ALPHA_DEFAULT) { prefs, key, default ->
            prefs.getInt(key, default)
        }

    override suspend fun setAlpha(alpha: Int) {
        prefs.edit().putInt(AppConstants.KEY_ALPHA, alpha).apply()
    }

    override fun getSize(): Flow<Int> =
        getPreferenceFlow(AppConstants.KEY_SIZE, AppConstants.DEFAULT_SIZE) { prefs, key, default ->
            prefs.getInt(key, default)
        }

    override suspend fun setSize(size: Int) {
        prefs.edit().putInt(AppConstants.KEY_SIZE, size).apply()
    }

    override fun getPositionX(): Flow<Int> =
        getPreferenceFlow(AppConstants.KEY_POSITION_X, AppConstants.DEFAULT_POSITION_X_PX) { prefs, key, default ->
            prefs.getInt(key, default)
        }

    override suspend fun setPositionX(x: Int) {
        prefs.edit().putInt(AppConstants.KEY_POSITION_X, x).apply()
    }

    override fun getPositionY(): Flow<Int> =
        getPreferenceFlow(AppConstants.KEY_POSITION_Y, AppConstants.DEFAULT_POSITION_Y_PX) { prefs, key, default ->
            prefs.getInt(key, default)
        }

    override suspend fun setPositionY(y: Int) {
        prefs.edit().putInt(AppConstants.KEY_POSITION_Y, y).apply()
    }

    override fun getPositionXPercent(): Flow<Float> =
        getPreferenceFlow(AppConstants.KEY_POSITION_X_PERCENT, AppConstants.DEFAULT_POSITION_X_PERCENT) { prefs, key, default ->
            prefs.getFloat(key, default)
        }

    override suspend fun setPositionXPercent(percent: Float) {
        prefs.edit().putFloat(AppConstants.KEY_POSITION_X_PERCENT, percent).apply()
    }

    override fun getPositionYPercent(): Flow<Float> =
        getPreferenceFlow(AppConstants.KEY_POSITION_Y_PERCENT, AppConstants.DEFAULT_POSITION_Y_PERCENT) { prefs, key, default ->
            prefs.getFloat(key, default)
        }

    override suspend fun setPositionYPercent(percent: Float) {
        prefs.edit().putFloat(AppConstants.KEY_POSITION_Y_PERCENT, percent).apply()
    }

    override fun getRecentsTimeout(): Flow<Long> =
        getPreferenceFlow(AppConstants.KEY_RECENTS_TIMEOUT, AppConstants.RECENTS_TIMEOUT_DEFAULT_MS) { prefs, key, default ->
            prefs.getLong(key, default)
        }

    override suspend fun setRecentsTimeout(timeout: Long) {
        prefs.edit().putLong(AppConstants.KEY_RECENTS_TIMEOUT, timeout).apply()
    }

    override fun isKeyboardAvoidanceEnabled(): Flow<Boolean> =
        getPreferenceFlow(AppConstants.KEY_KEYBOARD_AVOIDANCE, AppConstants.DEFAULT_KEYBOARD_AVOIDANCE) { prefs, key, default ->
            prefs.getBoolean(key, default)
        }

    override suspend fun setKeyboardAvoidanceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(AppConstants.KEY_KEYBOARD_AVOIDANCE, enabled).apply()
    }

    override fun getTapBehavior(): Flow<String> =
        getPreferenceFlow(AppConstants.KEY_TAP_BEHAVIOR, AppConstants.DEFAULT_TAP_BEHAVIOR) { prefs, key, default ->
            prefs.getString(key, default) ?: default
        }

    override suspend fun setTapBehavior(behavior: String) {
        prefs.edit().putString(AppConstants.KEY_TAP_BEHAVIOR, behavior).apply()
    }

    override fun getScreenWidth(): Flow<Int> =
        getPreferenceFlow(AppConstants.KEY_SCREEN_WIDTH, AppConstants.DEFAULT_SCREEN_WIDTH) { prefs, key, default ->
            prefs.getInt(key, default)
        }

    override suspend fun setScreenWidth(width: Int) {
        prefs.edit().putInt(AppConstants.KEY_SCREEN_WIDTH, width).apply()
    }

    override fun getScreenHeight(): Flow<Int> =
        getPreferenceFlow(AppConstants.KEY_SCREEN_HEIGHT, AppConstants.DEFAULT_SCREEN_HEIGHT) { prefs, key, default ->
            prefs.getInt(key, default)
        }

    override suspend fun setScreenHeight(height: Int) {
        prefs.edit().putInt(AppConstants.KEY_SCREEN_HEIGHT, height).apply()
    }

    override fun getRotation(): Flow<Int> =
        getPreferenceFlow(AppConstants.KEY_ROTATION, 0) { prefs, key, default ->
            prefs.getInt(key, default)
        }

    override suspend fun setRotation(rotation: Int) {
        prefs.edit().putInt(AppConstants.KEY_ROTATION, rotation).apply()
    }
}