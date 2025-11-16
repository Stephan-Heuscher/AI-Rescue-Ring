package ch.heuscher.airescuering

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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

    // AI Helper fields
    private lateinit var apiKeyInput: EditText
    private lateinit var voiceInputSwitch: androidx.appcompat.widget.SwitchCompat

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
        setupKeyboardAvoidanceSwitch()
        setupAIHelperControls()
    }

    private fun initializeViews() {
        keyboardAvoidanceSwitch = findViewById(R.id.keyboard_avoidance_switch)

        // AI Helper views
        apiKeyInput = findViewById(R.id.api_key_input)
        voiceInputSwitch = findViewById(R.id.voice_input_switch)
    }

    private fun setupBackButton() {
        findViewById<Button>(R.id.back_button).setOnClickListener {
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

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepository.isKeyboardAvoidanceEnabled().collect { enabled ->
                keyboardAvoidanceEnabled = enabled
                keyboardAvoidanceSwitch.isChecked = enabled
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
    }
}
