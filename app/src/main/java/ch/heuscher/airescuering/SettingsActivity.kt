package ch.heuscher.airescuering

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ch.heuscher.airescuering.di.ServiceLocator
import ch.heuscher.airescuering.domain.repository.AIHelperRepository
import ch.heuscher.airescuering.domain.repository.SettingsRepository
import ch.heuscher.airescuering.service.voice.ButlerVoiceManager
import ch.heuscher.airescuering.util.AppConstants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * J-AI-mes Settings Activity
 * Organized into Voice & Speech, Butler Button, and Advanced sections.
 */
class SettingsActivity : AppCompatActivity() {

    // Behavior switches
    private lateinit var keyboardAvoidanceSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var vibrationSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var longPressDragSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var lockPositionSwitch: androidx.appcompat.widget.SwitchCompat

    // Appearance
    private lateinit var sizeSeekBar: SeekBar
    private lateinit var alphaSeekBar: SeekBar
    private lateinit var transparencyValue: TextView

    // Voice & Speech
    private lateinit var speakingSpeedSeekBar: SeekBar
    private lateinit var speakingSpeedValue: TextView
    private lateinit var voiceInputSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var voiceFirstModeSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var autoSpeakSwitch: androidx.appcompat.widget.SwitchCompat

    // API Key
    private lateinit var apiKeyInput: EditText

    // Autonomy Level
    private lateinit var autonomySpinner: Spinner

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
        setupSpeakingSpeedControl()
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
        findViewById<TextView>(R.id.api_key_help_link).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev"))
            startActivity(intent)
        }
    }

    private fun setupAppearanceControls() {
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    lifecycleScope.launch {
                        // Map 0-100 to 32dp-96dp
                        val size = 32 + (progress * 64 / 100)
                        settingsRepository.setSize(size)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        alphaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Update the percentage display (min 25% = 64, max 100% = 255)
                val percentage = (progress * 100 / 255)
                transparencyValue.text = "$percentage%"
                if (fromUser) {
                    lifecycleScope.launch {
                        settingsRepository.setAlpha(progress)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * Speaking speed slider: maps 0-100 to 0.5x-1.5x.
     * Default 0.88x = progress 38.
     */
    private fun setupSpeakingSpeedControl() {
        speakingSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progressToSpeed(progress)
                speakingSpeedValue.text = String.format("%.1fx", speed)
                if (fromUser) {
                    lifecycleScope.launch {
                        aiHelperRepository.setSpeakingSpeed(speed)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Initialize from saved value
        lifecycleScope.launch {
            val speed = aiHelperRepository.getSpeakingSpeed().first()
            val progress = speedToProgress(speed)
            speakingSpeedSeekBar.progress = progress
            speakingSpeedValue.text = String.format("%.1fx", speed)
        }
    }

    /** Maps seekbar progress (0-100) to speed (0.5-1.5) */
    private fun progressToSpeed(progress: Int): Float {
        return ButlerVoiceManager.SPEECH_RATE_MIN + 
            (progress / 100f) * (ButlerVoiceManager.SPEECH_RATE_MAX - ButlerVoiceManager.SPEECH_RATE_MIN)
    }

    /** Maps speed (0.5-1.5) to seekbar progress (0-100) */
    private fun speedToProgress(speed: Float): Int {
        return ((speed - ButlerVoiceManager.SPEECH_RATE_MIN) / 
            (ButlerVoiceManager.SPEECH_RATE_MAX - ButlerVoiceManager.SPEECH_RATE_MIN) * 100).toInt()
            .coerceIn(0, 100)
    }

    private fun initializeViews() {
        // Behavior switches
        keyboardAvoidanceSwitch = findViewById(R.id.keyboard_avoidance_switch)
        vibrationSwitch = findViewById(R.id.vibration_switch)
        longPressDragSwitch = findViewById(R.id.long_press_drag_switch)
        lockPositionSwitch = findViewById(R.id.lock_position_switch)

        // Appearance
        sizeSeekBar = findViewById(R.id.size_seekbar)
        alphaSeekBar = findViewById(R.id.alpha_seekbar)
        transparencyValue = findViewById(R.id.transparency_value)

        // Voice & Speech
        speakingSpeedSeekBar = findViewById(R.id.speaking_speed_seekbar)
        speakingSpeedValue = findViewById(R.id.speaking_speed_value)
        voiceInputSwitch = findViewById(R.id.voice_input_switch)
        voiceFirstModeSwitch = findViewById(R.id.voice_first_mode_switch)
        autoSpeakSwitch = findViewById(R.id.auto_speak_switch)

        // API Key
        apiKeyInput = findViewById(R.id.api_key_input)
        
        // Autonomy Level
        autonomySpinner = findViewById(R.id.autonomy_spinner)
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
        val arrow = findViewById<TextView>(R.id.advanced_features_arrow)

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

        // Voice-first mode switch
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

        // Autonomy level spinner
        autonomySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                lifecycleScope.launch {
                    aiHelperRepository.setAutonomyLevel(position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun observeAIHelperSettings() {
        lifecycleScope.launch {
            // Observe autonomy level
            aiHelperRepository.getAutonomyLevel().collect { level ->
                if (autonomySpinner.selectedItemPosition != level) {
                    autonomySpinner.setSelection(level)
                }
            }
        }
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

        lifecycleScope.launch {
            // Observe speaking speed
            aiHelperRepository.getSpeakingSpeed().collect { speed ->
                val progress = speedToProgress(speed)
                if (speakingSpeedSeekBar.progress != progress) {
                    speakingSpeedSeekBar.progress = progress
                    speakingSpeedValue.text = String.format("%.1fx", speed)
                }
            }
        }
    }
}
