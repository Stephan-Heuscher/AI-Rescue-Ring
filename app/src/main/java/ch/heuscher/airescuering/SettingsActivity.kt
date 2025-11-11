package ch.heuscher.airescuering

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.View
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
    private var currentColor = 0xFF2196F3.toInt()
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
        setupColorButtons()
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
            broadcastSettingsUpdate()
        }
    }

    private fun setupColorButtons() {
        findViewById<Button>(R.id.color_blue).setOnClickListener { setColor(0xFF2196F3.toInt()) }
        findViewById<Button>(R.id.color_red).setOnClickListener { setColor(0xFFF44336.toInt()) }
        findViewById<Button>(R.id.color_green).setOnClickListener { setColor(0xFF4CAF50.toInt()) }
        findViewById<Button>(R.id.color_orange).setOnClickListener { setColor(0xFFFF9800.toInt()) }
        findViewById<Button>(R.id.color_purple).setOnClickListener { setColor(0xFF9C27B0.toInt()) }
        findViewById<Button>(R.id.color_cyan).setOnClickListener { setColor(0xFF00BCD4.toInt()) }
        findViewById<Button>(R.id.color_yellow).setOnClickListener { setColor(0xFFFFEB3B.toInt()) }
        findViewById<Button>(R.id.color_gray).setOnClickListener { setColor(0xFF607D8B.toInt()) }
        findViewById<Button>(R.id.color_custom).setOnClickListener { showColorPickerDialog() }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepository.getColor().collect { color ->
                currentColor = color
            }
        }

        lifecycleScope.launch {
            settingsRepository.isKeyboardAvoidanceEnabled().collect { enabled ->
                keyboardAvoidanceEnabled = enabled
                keyboardAvoidanceSwitch.isChecked = enabled
            }
        }
    }

    private fun setColor(color: Int) {
        currentColor = color
        lifecycleScope.launch {
            settingsRepository.setColor(color)
        }
        broadcastSettingsUpdate()
    }

    private fun broadcastSettingsUpdate() {
        val intent = Intent(AppConstants.ACTION_UPDATE_SETTINGS)
        sendBroadcast(intent)
    }

    private fun showColorPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.color_picker_dialog, null)
        val colorPreview = dialogView.findViewById<View>(R.id.color_preview)
        val hexInput = dialogView.findViewById<EditText>(R.id.hex_input)
        val redSeekBar = dialogView.findViewById<SeekBar>(R.id.red_seekbar)
        val greenSeekBar = dialogView.findViewById<SeekBar>(R.id.green_seekbar)
        val blueSeekBar = dialogView.findViewById<SeekBar>(R.id.blue_seekbar)
        val redValue = dialogView.findViewById<TextView>(R.id.red_value)
        val greenValue = dialogView.findViewById<TextView>(R.id.green_value)
        val blueValue = dialogView.findViewById<TextView>(R.id.blue_value)

        val currentColor = this@SettingsActivity.currentColor
        redSeekBar.progress = Color.red(currentColor)
        greenSeekBar.progress = Color.green(currentColor)
        blueSeekBar.progress = Color.blue(currentColor)

        var isUpdatingFromHex = false

        fun updateColorFromSliders() {
            if (isUpdatingFromHex) return
            val color = Color.rgb(redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress)
            colorPreview.setBackgroundColor(color)
            redValue.text = redSeekBar.progress.toString()
            greenValue.text = greenSeekBar.progress.toString()
            blueValue.text = blueSeekBar.progress.toString()

            // Update hex input
            val hexColor = String.format("#%02X%02X%02X",
                redSeekBar.progress,
                greenSeekBar.progress,
                blueSeekBar.progress)
            hexInput.setText(hexColor)
        }
        updateColorFromSliders()

        // Handle hex input changes
        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val hexString = s.toString()
                if (hexString.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                    try {
                        isUpdatingFromHex = true
                        val color = Color.parseColor(hexString)
                        colorPreview.setBackgroundColor(color)
                        redSeekBar.progress = Color.red(color)
                        greenSeekBar.progress = Color.green(color)
                        blueSeekBar.progress = Color.blue(color)
                        redValue.text = Color.red(color).toString()
                        greenValue.text = Color.green(color).toString()
                        blueValue.text = Color.blue(color).toString()
                        isUpdatingFromHex = false
                    } catch (e: IllegalArgumentException) {
                        // Invalid hex color
                    }
                }
            }
        })

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateColorFromSliders()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        redSeekBar.setOnSeekBarChangeListener(seekBarListener)
        greenSeekBar.setOnSeekBarChangeListener(seekBarListener)
        blueSeekBar.setOnSeekBarChangeListener(seekBarListener)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_custom_color))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val selectedColor = Color.rgb(redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress)
                setColor(selectedColor)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
