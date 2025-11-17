package ch.heuscher.airescuering

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Unified helper for accessibility service management
 * Provides centralized logic for checking service status and removing duplicates
 */
object AccessibilityServiceHelper {

    private const val TAG = "AccessibilityServiceHelper"

    /**
     * Get the ComponentName for the BackHomeAccessibilityService
     */
    fun getServiceComponent(context: Context): ComponentName {
        return ComponentName(context, BackHomeAccessibilityService::class.java)
    }

    /**
     * Get the flattened string representation of the service component
     * This ensures consistency across all permission checks
     */
    fun getServiceComponentString(context: Context): String {
        return getServiceComponent(context).flattenToString()
    }

    /**
     * Check if the accessibility service is enabled in system settings
     * This is the authoritative check that queries Android's ENABLED_ACCESSIBILITY_SERVICES
     */
    fun isServiceEnabled(context: Context): Boolean {
        // First check if accessibility is enabled globally
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility enabled status", e)
            0
        }

        if (accessibilityEnabled != 1) {
            return false
        }

        // Get the list of enabled accessibility services
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // Check if our service is in the list
        val serviceComponent = getServiceComponentString(context)
        return enabledServices.split(":").contains(serviceComponent)
    }

    /**
     * Remove duplicate entries of our service from ENABLED_ACCESSIBILITY_SERVICES
     * This fixes the issue where the service appears multiple times in Settings
     *
     * Note: This requires WRITE_SECURE_SETTINGS permission which normal apps don't have.
     * The function will log the duplicates found but cannot fix them programmatically.
     * Users need to manually disable/re-enable the service to clean up duplicates.
     */
    fun detectAndLogDuplicates(context: Context) {
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return

            val serviceComponent = getServiceComponentString(context)
            val services = enabledServices.split(":").filter { it.isNotEmpty() }

            // Count how many times our service appears
            val ourServiceCount = services.count { it == serviceComponent }

            if (ourServiceCount > 1) {
                Log.w(TAG, "DUPLICATE DETECTED: Rescue Ring accessibility service appears $ourServiceCount times in system settings!")
                Log.w(TAG, "To fix: Go to Settings > Accessibility, disable 'Rescue Ring', then re-enable it")
                Log.w(TAG, "Current enabled services: $enabledServices")
            } else if (ourServiceCount == 1) {
                Log.d(TAG, "Accessibility service correctly enabled (no duplicates)")
            } else {
                Log.d(TAG, "Accessibility service not found in enabled services")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting duplicates", e)
        }
    }
}
