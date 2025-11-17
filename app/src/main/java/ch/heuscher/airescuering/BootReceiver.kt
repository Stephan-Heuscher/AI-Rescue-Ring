package ch.heuscher.airescuering

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import ch.heuscher.airescuering.service.overlay.OverlayService
import ch.heuscher.airescuering.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if overlay permissions and accessibility are enabled
            if (hasOverlayPermission(context) && isAccessibilityServiceEnabled(context)) {
                // Initialize ServiceLocator
                ServiceLocator.initialize(context)

                // Check if overlay is enabled in settings before starting
                CoroutineScope(Dispatchers.IO).launch {
                    val isEnabled = ServiceLocator.settingsRepository.isOverlayEnabled().first()
                    if (isEnabled) {
                        // Start the overlay service
                        val overlayIntent = Intent(context, OverlayService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(overlayIntent)
                        } else {
                            context.startService(overlayIntent)
                        }
                    }
                }
            }
        }
    }

    private fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
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

            return serviceString.contains(context.packageName + "/" + AIRescueRingAccessibilityService::class.java.name)
        }

        return false
    }
}