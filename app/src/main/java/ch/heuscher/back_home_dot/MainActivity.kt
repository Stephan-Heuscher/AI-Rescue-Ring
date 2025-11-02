package ch.heuscher.back_home_dot

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ch.heuscher.back_home_dot.di.ServiceLocator
import ch.heuscher.back_home_dot.domain.repository.SettingsRepository
import ch.heuscher.back_home_dot.service.overlay.OverlayService
import ch.heuscher.back_home_dot.util.AppConstants

class MainActivity : AppCompatActivity() {

    private lateinit var permissionsSection: LinearLayout
    private lateinit var overlayPermissionCard: CardView
    private lateinit var overlayStatusIcon: TextView
    private lateinit var overlayStatusText: TextView
    private lateinit var overlayPermissionButton: Button
    private lateinit var accessibilityPermissionCard: CardView
    private lateinit var accessibilityStatusIcon: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var accessibilityButton: Button
    private lateinit var overlaySwitch: SwitchCompat
    private lateinit var rescueRingSwitch: SwitchCompat
    private lateinit var settingsButton: Button
    private lateinit var stopServiceButton: Button
    private lateinit var instructionsText: TextView
    private lateinit var versionInfo: TextView

    private lateinit var settingsRepository: SettingsRepository

    // UI state holders for synchronous access
    private var isOverlayEnabled = false
    private var isRescueRingEnabled = false

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting MainActivity")

        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate: Content view set")

        // Initialize service locator for dependency injection
        ServiceLocator.initialize(this)
        Log.d(TAG, "onCreate: ServiceLocator initialized")

        settingsRepository = ServiceLocator.settingsRepository
        Log.d(TAG, "onCreate: SettingsRepository obtained")

        initializeViews()
        setupClickListeners()
        observeSettings()
        updateUI()
        updateInstructionsText()
        Log.d(TAG, "onCreate: MainActivity initialization complete")
    }

    override fun onResume() {
        super.onResume()
        updateUI()

        // Start service if enabled and permissions are granted
        if (isOverlayEnabled && hasAllRequiredPermissions()) {
            startOverlayService()
        }
    }

    private fun initializeViews() {
        Log.d(TAG, "initializeViews: Starting view initialization")
        permissionsSection = findViewById(R.id.permissions_section)
        Log.d(TAG, "initializeViews: permissionsSection found")
        overlayPermissionCard = findViewById(R.id.overlay_permission_card)
        overlayStatusIcon = findViewById(R.id.overlay_status_icon)
        overlayStatusText = findViewById(R.id.overlay_status_text)
        overlayPermissionButton = findViewById(R.id.overlay_permission_button)
        accessibilityPermissionCard = findViewById(R.id.accessibility_permission_card)
        accessibilityStatusIcon = findViewById(R.id.accessibility_status_icon)
        accessibilityStatusText = findViewById(R.id.accessibility_status_text)
        accessibilityButton = findViewById(R.id.accessibility_button)
        overlaySwitch = findViewById(R.id.overlay_switch)
        rescueRingSwitch = findViewById(R.id.rescue_ring_switch)
        settingsButton = findViewById(R.id.settings_button)
        stopServiceButton = findViewById(R.id.stop_service_button)
        instructionsText = findViewById(R.id.instructions_text)
        versionInfo = findViewById(R.id.version_info)
        Log.d(TAG, "initializeViews: All views found")

        // Set version info
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        versionInfo.text = "Version ${packageInfo.versionName}"
        Log.d(TAG, "initializeViews: Version info set")
    }

    private fun setupClickListeners() {
        overlayPermissionButton.setOnClickListener { requestOverlayPermission() }
        accessibilityButton.setOnClickListener { openAccessibilitySettings() }
        stopServiceButton.setOnClickListener { showStopServiceDialog() }
        settingsButton.setOnClickListener { openSettings() }

        overlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsRepository.setOverlayEnabled(isChecked)
            }
            if (hasAllRequiredPermissions()) {
                if (isChecked) {
                    startOverlayService()
                } else {
                    stopOverlayService()
                }
            }
            updateUI()
        }

        rescueRingSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsRepository.setRescueRingEnabled(isChecked)
            }
            updateInstructionsText()
            broadcastSettingsUpdate()
        }
    }

    private fun updateUI() {
        val hasOverlay = hasOverlayPermission()
        val hasAccessibility = hasAccessibilityPermission()

        // Show/hide individual permission cards
        overlayPermissionCard.visibility = if (hasOverlay) View.GONE else View.VISIBLE
        accessibilityPermissionCard.visibility = if (hasAccessibility) View.GONE else View.VISIBLE

        // Show/hide entire permissions section
        permissionsSection.visibility = if (hasOverlay && hasAccessibility) View.GONE else View.VISIBLE

        // Update switch
        overlaySwitch.isChecked = isOverlayEnabled
        overlaySwitch.isEnabled = hasOverlay && hasAccessibility

        // Update rescue ring switch
        rescueRingSwitch.isChecked = isRescueRingEnabled
        rescueRingSwitch.isEnabled = hasOverlay && hasAccessibility

        // Update settings button
        settingsButton.isEnabled = hasOverlay && hasAccessibility
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.allow_assistipoint_display))
                    .setMessage(getString(R.string.allow_assistipoint_message))
                    .setPositiveButton(getString(R.string.open)) { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }
    }

    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.allow_navigation_title))
            .setMessage(getString(R.string.allow_navigation_message))
            .setPositiveButton(getString(R.string.open)) { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun showStopServiceDialog() {
        // Stoppe Services, aber ändere den gespeicherten Status nicht
        // So bleibt beim nächsten App-Start der Switch-Status erhalten
        stopOverlayService()
        closeApp()
    }

    private fun closeApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finishAffinity()
        }
    }

    private fun startOverlayService() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        startService(serviceIntent)
    }

    private fun stopOverlayService() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        stopService(serviceIntent)
    }

    private fun broadcastSettingsUpdate() {
        val intent = Intent(AppConstants.ACTION_UPDATE_SETTINGS)
        sendBroadcast(intent)
    }

    private fun observeSettings() {
        Log.d(TAG, "observeSettings: Starting to observe settings")
        lifecycleScope.launch {
            Log.d(TAG, "observeSettings: Launching overlay enabled observer")
            try {
                settingsRepository.isOverlayEnabled().collect { enabled ->
                    Log.d(TAG, "observeSettings: Overlay enabled changed to $enabled")
                    isOverlayEnabled = enabled
                    updateUI()
                }
            } catch (e: Exception) {
                Log.e(TAG, "observeSettings: Error observing overlay enabled", e)
            }
        }

        lifecycleScope.launch {
            Log.d(TAG, "observeSettings: Launching rescue ring observer")
            try {
                settingsRepository.isRescueRingEnabled().collect { enabled ->
                    Log.d(TAG, "observeSettings: Rescue ring enabled changed to $enabled")
                    isRescueRingEnabled = enabled
                    updateInstructionsText()
                }
            } catch (e: Exception) {
                Log.e(TAG, "observeSettings: Error observing rescue ring enabled", e)
            }
        }
        Log.d(TAG, "observeSettings: Observers launched")
    }

    private fun updateInstructionsText() {
        val instructions = if (isRescueRingEnabled) {
            getString(R.string.instructions_rescue_mode)
        } else {
            getString(R.string.instructions_normal_mode)
        }
        instructionsText.text = instructions
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // Permission not required for older versions
        }
    }

    private fun hasAccessibilityPermission(): Boolean {
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val serviceComponent = ComponentName(this, BackHomeAccessibilityService::class.java).flattenToString()
        return enabledServices?.split(":")?.contains(serviceComponent) == true
    }

    private fun hasAllRequiredPermissions(): Boolean {
        return hasOverlayPermission() && hasAccessibilityPermission()
    }
}
