package ch.heuscher.back_home_dot.di

import android.content.Context
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import ch.heuscher.back_home_dot.data.local.SettingsDataSource
import ch.heuscher.back_home_dot.data.local.SharedPreferencesDataSource
import ch.heuscher.back_home_dot.data.repository.SettingsRepositoryImpl
import ch.heuscher.back_home_dot.domain.repository.SettingsRepository
import ch.heuscher.back_home_dot.service.overlay.GestureDetector
import ch.heuscher.back_home_dot.service.overlay.KeyboardDetector
import ch.heuscher.back_home_dot.service.overlay.OverlayViewManager
// import dagger.Module
// import dagger.Provides
// import dagger.hilt.InstallIn
// import dagger.hilt.android.qualifiers.ApplicationContext
// import dagger.hilt.components.SingletonComponent
// import javax.inject.Singleton

/**
 * Dependency injection module for the application.
 * Provides all dependencies for the clean architecture layers.
 * Currently commented out - using ServiceLocator for manual DI.
 */
// @Module
// @InstallIn(SingletonComponent::class)
object AppModule {

    // System Services
    // @Provides
    // @Singleton
    fun provideWindowManager(
        // @ApplicationContext
        context: Context
    ): WindowManager {
        return context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    // @Provides
    // @Singleton
    fun provideInputMethodManager(
        // @ApplicationContext
        context: Context
    ): InputMethodManager {
        return context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    // Data Layer
    // @Provides
    // @Singleton
    fun provideSettingsDataSource(
        // @ApplicationContext
        context: Context
    ): SettingsDataSource {
        return SharedPreferencesDataSource(context)
    }

    // @Provides
    // @Singleton
    fun provideSettingsRepository(
        dataSource: SettingsDataSource
    ): SettingsRepository {
        return SettingsRepositoryImpl(dataSource)
    }

    // Service Layer
    // @Provides
    // @Singleton
    fun provideKeyboardDetector(
        windowManager: WindowManager,
        inputMethodManager: InputMethodManager
    ): KeyboardDetector {
        return KeyboardDetector(windowManager, inputMethodManager)
    }

    // @Provides
    // @Singleton
    fun provideOverlayViewManager(
        // @ApplicationContext
        context: Context,
        windowManager: WindowManager
    ): OverlayViewManager {
        return OverlayViewManager(context, windowManager)
    }
}