package ch.heuscher.back_home_dot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import ch.heuscher.back_home_dot.service.overlay.OverlayService

/**
 * Receiver that restarts the overlay service after an app update.
 */
class PackageUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageUpdateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "App updated - restarting overlay service")

            // Check if accessibility service is enabled
            if (isAccessibilityServiceEnabled(context)) {
                // Start the overlay service
                val overlayIntent = Intent(context, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(overlayIntent)
                } else {
                    context.startService(overlayIntent)
                }
                Log.d(TAG, "Overlay service restarted after update")
            } else {
                Log.w(TAG, "Accessibility service not enabled - not starting overlay service")
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Exception) {
            0
        }

        if (accessibilityEnabled == 1) {
            val serviceString = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return serviceString.contains(context.packageName + "/" + BackHomeAccessibilityService::class.java.name)
        }

        return false
    }
}
