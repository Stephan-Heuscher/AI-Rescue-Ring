package ch.heuscher.back_home_dot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import ch.heuscher.back_home_dot.di.ServiceLocator
import ch.heuscher.back_home_dot.domain.repository.SettingsRepository
import ch.heuscher.back_home_dot.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.cancel

/**
 * Accessibility service for performing system navigation actions
 */
class BackHomeAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Cache settings values for synchronous access
    private var recentsTimeout: Long = AppConstants.RECENTS_TIMEOUT_DEFAULT_MS

    // Track current foreground package for home screen detection
    private var currentPackageName: String? = null
    private var launcherPackageName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Initialize repository through ServiceLocator
        repository = ServiceLocator.getRepository(this)

        // Observe recentsTimeout changes
        repository.getRecentsTimeout()
            .onEach { timeout ->
                recentsTimeout = timeout
                Log.d(TAG, "Recents timeout updated: $timeout ms")
            }
            .launchIn(serviceScope)

        // Configure service info
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        // Detect launcher package
        launcherPackageName = getLauncherPackageName()
        Log.d(TAG, "Launcher package detected: $launcherPackageName")

        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Track current foreground package
                    val packageName = it.packageName?.toString()
                    if (packageName != null && packageName != currentPackageName) {
                        currentPackageName = packageName
                        Log.d(TAG, "Window changed to package: $packageName")
                    }
                    detectKeyboardState(it)
                }
            }
        }
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    /**
     * Detect keyboard state changes and broadcast to overlay service
     */
    private fun detectKeyboardState(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: ""
        val packageName = event.packageName?.toString() ?: ""

        // Check if this is an input method (keyboard) window
        val isKeyboard = className.contains("InputMethod") ||
                        packageName.contains("inputmethod") ||
                        event.isFullScreen // IME windows are often full screen in terms of accessibility

        Log.d(TAG, "Window state changed: class=$className, package=$packageName, isKeyboard=$isKeyboard")

        if (isKeyboard) {
            // Try to estimate keyboard height - this is approximate
            val rootNode = rootInActiveWindow
            val keyboardHeight = if (rootNode != null) {
                // Get screen height and estimate keyboard takes bottom portion
                val displayMetrics = resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels
                (screenHeight * 0.4f).toInt() // Estimate 40% of screen height
            } else {
                800 // Fallback height
            }

            Log.d(TAG, "Keyboard detected, broadcasting height=$keyboardHeight")

            // Send broadcast to overlay service
            val intent = Intent(AppConstants.ACTION_UPDATE_KEYBOARD)
            intent.putExtra("keyboard_visible", true)
            intent.putExtra("keyboard_height", keyboardHeight)
            sendBroadcast(intent)
        }
    }

    /**
     * Perform back navigation action
     */
    fun performBackAction() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Perform home action
     */
    fun performHomeAction() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Switch to previous app using double-tap recents
     * Uses configurable timeout from settings
     */
    fun performRecentsAction() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        }, recentsTimeout)
    }

    /**
     * Open recent apps overview
     */
    fun performRecentsOverviewAction() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Get launcher package name
     */
    private fun getLauncherPackageName(): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting launcher package", e)
            null
        }
    }

    /**
     * Check if currently on home screen
     */
    fun isOnHomeScreen(): Boolean {
        val onHomeScreen = currentPackageName != null &&
                          launcherPackageName != null &&
                          currentPackageName == launcherPackageName
        Log.d(TAG, "isOnHomeScreen: current=$currentPackageName, launcher=$launcherPackageName, result=$onHomeScreen")
        return onHomeScreen
    }

    companion object {
        private const val TAG = "BackHomeAccessibilityService"
        var instance: BackHomeAccessibilityService? = null
            private set

        fun isServiceEnabled(): Boolean = instance != null
    }
}
