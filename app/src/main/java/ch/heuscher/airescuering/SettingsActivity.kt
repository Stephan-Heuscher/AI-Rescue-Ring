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

    // Appearance fields
    private lateinit var sizeSeekBar: android.widget.SeekBar
    private lateinit var alphaSeekBar: android.widget.SeekBar
    private lateinit var colorBlue: Button
    private lateinit var colorRed: Button
    private lateinit var colorGreen: Button
    private lateinit var colorBlack: Button

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
        setupAppearanceControls()
        setupKeyboardAvoidanceSwitch()
        setupAIHelperControls()
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

        // Appearance views
        sizeSeekBar = findViewById(R.id.size_seekbar)
        alphaSeekBar = findViewById(R.id.alpha_seekbar)
        colorBlue = findViewById(R.id.color_blue)
        colorRed = findViewById(R.id.color_red)
        colorGreen = findViewById(R.id.color_green)
        colorBlack = findViewById(R.id.color_black)

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
                if (alphaSeekBar.progress != alpha) {
                    alphaSeekBar.progress = alpha
                }
            }
        }

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
