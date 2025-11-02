package ch.heuscher.back_home_dot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if accessibility service is enabled
            if (isAccessibilityServiceEnabled(context)) {
                // Start the overlay service
                val overlayIntent = Intent(context, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(overlayIntent)
                } else {
                    context.startService(overlayIntent)
                }
            } else {
                // Show notification that accessibility service needs to be enabled
                Toast.makeText(
                    context,
                    "Bitte aktivieren Sie den Accessibility Service für Assistive Tap",
                    Toast.LENGTH_LONG
                ).show()
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