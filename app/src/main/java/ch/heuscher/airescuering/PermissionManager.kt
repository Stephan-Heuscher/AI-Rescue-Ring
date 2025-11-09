package ch.heuscher.back_home_dot

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Centralized permission management
 */
class PermissionManager(private val context: Context) {

    /**
     * Check if overlay permission is granted
     */
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Check if accessibility service is enabled
     */
    fun hasAccessibilityPermission(): Boolean {
        return BackHomeAccessibilityService.isServiceEnabled()
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasAllRequiredPermissions(): Boolean {
        return hasOverlayPermission() && hasAccessibilityPermission()
    }
}
