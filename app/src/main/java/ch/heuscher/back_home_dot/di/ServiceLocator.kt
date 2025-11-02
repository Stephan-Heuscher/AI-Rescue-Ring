package ch.heuscher.back_home_dot.di

import android.content.Context
import android.view.ViewConfiguration
import ch.heuscher.back_home_dot.data.local.SettingsDataSource
import ch.heuscher.back_home_dot.data.local.SharedPreferencesDataSource
import ch.heuscher.back_home_dot.data.repository.SettingsRepositoryImpl
import ch.heuscher.back_home_dot.domain.repository.SettingsRepository
import ch.heuscher.back_home_dot.service.overlay.GestureDetector
import ch.heuscher.back_home_dot.service.overlay.KeyboardDetector
import ch.heuscher.back_home_dot.service.overlay.OverlayViewManager

/**
 * Simple service locator for dependency injection.
 * Used during the refactoring transition before full Hilt migration.
 */
object ServiceLocator {

    private lateinit var applicationContext: Context

    fun initialize(context: Context) {
        if (!::applicationContext.isInitialized) {
            applicationContext = context.applicationContext
        }
    }

    // Lazy initialization of dependencies
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(settingsDataSource)
    }

    val settingsDataSource: SettingsDataSource by lazy {
        SharedPreferencesDataSource(applicationContext)
    }

    val keyboardDetector: KeyboardDetector by lazy {
        KeyboardDetector(windowManager, inputMethodManager)
    }

    val gestureDetector: GestureDetector by lazy {
        GestureDetector(ViewConfiguration.get(applicationContext))
    }

    val overlayViewManager: OverlayViewManager by lazy {
        OverlayViewManager(applicationContext, windowManager)
    }

    // System services
    private val windowManager by lazy {
        applicationContext.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
    }

    private val inputMethodManager by lazy {
        applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
    }
}