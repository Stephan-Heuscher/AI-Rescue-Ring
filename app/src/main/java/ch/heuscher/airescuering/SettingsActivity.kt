package ch.heuscher.airescuering

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ch.heuscher.airescuering.di.ServiceLocator
import ch.heuscher.airescuering.domain.repository.AIHelperRepository
import ch.heuscher.airescuering.domain.repository.SettingsRepository
import ch.heuscher.airescuering.util.AppConstants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var keyboardAvoidanceSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var vibrationSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var longPressDragSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var lockPositionSwitch: androidx.appcompat.widget.SwitchCompat

    // Appearance fields
    private lateinit var sizeSeekBar: android.widget.SeekBar
    private lateinit var alphaSeekBar: android.widget.SeekBar
    private lateinit var transparencyValue: android.widget.TextView
    private lateinit var colorBlue: Button
    private lateinit var colorRed: Button
    private lateinit var colorGreen: Button
    private lateinit var colorBlack: Button

    // AI Helper fields
    private lateinit var apiKeyInput: EditText
    private lateinit var voiceInputSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var voiceFirstModeSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var autoSpeakSwitch: androidx.appcompat.widget.SwitchCompat

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var aiHelperRepository: AIHelperRepository

    // UI state holders
    private var keyboardAvoidanceEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()

        settingsRepository = ServiceLocator.settingsRepository
        aiHelperRepository = ServiceLocator.aiHelperRepository

        initializeViews()
        observeSettings()
        observeAIHelperSettings()
        setupBackButton()
        setupImpressumButton()
        setupAppearanceControls()
        setupKeyboardAvoidanceSwitch()
        setupVibrationSwitch()
        setupLongPressDragSwitch()
        setupLockPositionSwitch()
        setupAdvancedFeatures()
        setupAIHelperControls()
        setupApiKeyHelpLink()
    }



    override fun onPause() {
        super.onPause()
        saveApiKey()
    }

    private fun saveApiKey() {
        val apiKey = apiKeyInput.text.toString().trim()
        lifecycleScope.launch {
            aiHelperRepository.setApiKey(apiKey)
            // Auto-enable AI helper when API key is provided
            if (apiKey.isNotEmpty()) {
                aiHelperRepository.setEnabled(true)
            }
        }
    }

    private fun setupApiKeyHelpLink() {
        findViewById<android.widget.TextView>(R.id.api_key_help_link).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev"))
            startActivity(intent)
        }
    }

    private fun setupAppearanceControls() {
        sizeSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    lifecycleScope.launch {
                        // Map 0-100 to 32dp-96dp
                        val size = 32 + (progress * 64 / 100)
                        settingsRepository.setSize(size)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        alphaSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                // Update the percentage display (min 25% = 64, max 100% = 255)
                val percentage = (progress * 100 / 255)
                transparencyValue.text = "$percentage%"
                if (fromUser) {
                    lifecycleScope.launch {
                        settingsRepository.setAlpha(progress)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        colorBlue.setOnClickListener { updateColor(0xFF2196F3.toInt()) }
        colorRed.setOnClickListener { updateColor(0xFFF44336.toInt()) }
        colorGreen.setOnClickListener { updateColor(0xFF4CAF50.toInt()) }
        colorBlack.setOnClickListener { updateColor(0xFF000000.toInt()) }
    }

    private fun updateColor(color: Int) {
        lifecycleScope.launch {
            settingsRepository.setColor(color)
        }
    }

    private fun initializeViews() {
        keyboardAvoidanceSwitch = findViewById(R.id.keyboard_avoidance_switch)
        vibrationSwitch = findViewById(R.id.vibration_switch)
        longPressDragSwitch = findViewById(R.id.long_press_drag_switch)
        lockPositionSwitch = findViewById(R.id.lock_position_switch)

        // Appearance views
        sizeSeekBar = findViewById(R.id.size_seekbar)
        alphaSeekBar = findViewById(R.id.alpha_seekbar)
        transparencyValue = findViewById(R.id.transparency_value)
        colorBlue = findViewById(R.id.color_blue)
        colorRed = findViewById(R.id.color_red)
        colorGreen = findViewById(R.id.color_green)
        colorBlack = findViewById(R.id.color_black)

        // AI Helper views
        apiKeyInput = findViewById(R.id.api_key_input)
        voiceInputSwitch = findViewById(R.id.voice_input_switch)
        voiceFirstModeSwitch = findViewById(R.id.voice_first_mode_switch)
        autoSpeakSwitch = findViewById(R.id.auto_speak_switch)
    }

    private fun setupBackButton() {
        findViewById<android.widget.ImageButton>(R.id.back_button).setOnClickListener {
            finish()
        }
    }

    private fun setupImpressumButton() {
        findViewById<Button>(R.id.impressum_button).setOnClickListener {
            val intent = Intent(this, ImpressumActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupKeyboardAvoidanceSwitch() {
        keyboardAvoidanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            keyboardAvoidanceEnabled = isChecked
            lifecycleScope.launch {
                settingsRepository.setKeyboardAvoidanceEnabled(isChecked)
            }
        }
    }

    private fun setupVibrationSwitch() {
        vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsRepository.setVibrationEnabled(isChecked)
            }
        }
    }

    private fun setupLongPressDragSwitch() {
        longPressDragSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                // When checked, use SAFE_HOME mode (requires long-press to drag)
                // When unchecked, use STANDARD mode (immediate drag)
                settingsRepository.setTapBehavior(if (isChecked) "SAFE_HOME" else "STANDARD")
            }
        }
    }

    private fun setupLockPositionSwitch() {
        lockPositionSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsRepository.setPositionLocked(isChecked)
            }
        }
    }

    private fun setupAdvancedFeatures() {
        val header = findViewById<android.widget.LinearLayout>(R.id.advanced_features_header)
        val content = findViewById<android.widget.LinearLayout>(R.id.advanced_features_content)
        val arrow = findViewById<android.widget.TextView>(R.id.advanced_features_arrow)

        header.setOnClickListener {
            if (content.visibility == android.view.View.GONE) {
                content.visibility = android.view.View.VISIBLE
                arrow.text = "▲"
            } else {
                content.visibility = android.view.View.GONE
                arrow.text = "▼"
            }
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepository.getSize().collect { size ->
                // Reverse mapping: progress = (size - 32) * 100 / 64
                val progress = ((size - 32) * 100 / 64).coerceIn(0, 100)
                if (sizeSeekBar.progress != progress) {
                    sizeSeekBar.progress = progress
                }
            }
        }

        lifecycleScope.launch {
            settingsRepository.getAlpha().collect { alpha ->
                // Ensure minimum of 64 (25%)
                val clampedAlpha = alpha.coerceAtLeast(64)
                if (alphaSeekBar.progress != clampedAlpha) {
                    alphaSeekBar.progress = clampedAlpha
                }
                val percentage = (clampedAlpha * 100 / 255)
                transparencyValue.text = "$percentage%"
            }
        }

        lifecycleScope.launch {
            settingsRepository.isKeyboardAvoidanceEnabled().collect { enabled ->
                keyboardAvoidanceEnabled = enabled
                keyboardAvoidanceSwitch.isChecked = enabled
            }
        }

        lifecycleScope.launch {
            settingsRepository.isVibrationEnabled().collect { enabled ->
                if (vibrationSwitch.isChecked != enabled) {
                    vibrationSwitch.isChecked = enabled
                }
            }
        }

        lifecycleScope.launch {
            settingsRepository.getTapBehavior().collect { behavior ->
                val requiresLongPress = behavior == "SAFE_HOME"
                if (longPressDragSwitch.isChecked != requiresLongPress) {
                    longPressDragSwitch.isChecked = requiresLongPress
                }
            }
        }

        lifecycleScope.launch {
            settingsRepository.isPositionLocked().collect { locked ->
                if (lockPositionSwitch.isChecked != locked) {
                    lockPositionSwitch.isChecked = locked
                }
            }
        }
    }

    private fun setupAIHelperControls() {
        // API Key input with save on focus loss
        apiKeyInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val apiKey = apiKeyInput.text.toString().trim()
                lifecycleScope.launch {
                    aiHelperRepository.setApiKey(apiKey)
                    // Auto-enable AI helper when API key is provided
                    if (apiKey.isNotEmpty()) {
                        aiHelperRepository.setEnabled(true)
                    }
                }
            }
        }

        // Voice input switch
        voiceInputSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                aiHelperRepository.setUseVoiceInput(isChecked)
            }
        }

        // Voice-first mode switch (elderly-friendly)
        voiceFirstModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                aiHelperRepository.setVoiceFirstMode(isChecked)
            }
        }

        // Auto-speak responses switch
        autoSpeakSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                aiHelperRepository.setAutoSpeakResponses(isChecked)
            }
        }
    }

    private fun observeAIHelperSettings() {
        lifecycleScope.launch {
            // Observe API key
            aiHelperRepository.getApiKey().collect { apiKey ->
                if (apiKeyInput.text.toString() != apiKey && !apiKeyInput.hasFocus()) {
                    apiKeyInput.setText(apiKey)
                }
            }
        }

        lifecycleScope.launch {
            // Observe voice input setting
            aiHelperRepository.useVoiceInput().collect { useVoice ->
                if (voiceInputSwitch.isChecked != useVoice) {
                    voiceInputSwitch.isChecked = useVoice
                }
            }
        }

        lifecycleScope.launch {
            // Observe voice-first mode setting
            aiHelperRepository.isVoiceFirstMode().collect { enabled ->
                if (voiceFirstModeSwitch.isChecked != enabled) {
                    voiceFirstModeSwitch.isChecked = enabled
                }
            }
        }

        lifecycleScope.launch {
            // Observe auto-speak setting
            aiHelperRepository.isAutoSpeakResponses().collect { enabled ->
                if (autoSpeakSwitch.isChecked != enabled) {
                    autoSpeakSwitch.isChecked = enabled
                }
            }
        }
    }
}
