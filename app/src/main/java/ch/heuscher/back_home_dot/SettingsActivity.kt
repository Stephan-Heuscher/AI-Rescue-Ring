package ch.heuscher.back_home_dot

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import androidx.lifecycle.lifecycleScope
import ch.heuscher.back_home_dot.di.ServiceLocator
import ch.heuscher.back_home_dot.domain.repository.SettingsRepository
import ch.heuscher.back_home_dot.util.AppConstants
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var alphaSeekBar: SeekBar
    private lateinit var alphaValueText: TextView
    private lateinit var timeoutSeekBar: SeekBar
    private lateinit var timeoutValueText: TextView
    private lateinit var keyboardAvoidanceSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var tapBehaviorRadioGroup: android.widget.RadioGroup
    private lateinit var tapBehaviorStandard: android.widget.RadioButton
    private lateinit var tapBehaviorBack: android.widget.RadioButton
    private lateinit var tapBehaviorSafeHome: android.widget.RadioButton
    private lateinit var advancedToggleCard: androidx.cardview.widget.CardView
    private lateinit var advancedContent: androidx.cardview.widget.CardView
    private lateinit var advancedArrow: TextView
    private var isAdvancedExpanded = false

    private lateinit var settingsRepository: SettingsRepository

    // UI state holders
    private var currentAlpha = 255
    private var currentTimeout = 100L
    private var currentColor = 0xFF2196F3.toInt()
    private var keyboardAvoidanceEnabled = false
    private var currentTapBehavior = "NAVI"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()

        settingsRepository = ServiceLocator.settingsRepository

        initializeViews()
        observeSettings()
        setupBackButton()
        setupImpressumButton()
        setupAdvancedToggle()
        setupAlphaSeekBar()
        setupTimeoutSeekBar()
        setupKeyboardAvoidanceSwitch()
        setupTapBehaviorRadioGroup()
        setupColorButtons()
    }

    private fun initializeViews() {
        alphaSeekBar = findViewById(R.id.alpha_seekbar)
        alphaValueText = findViewById(R.id.alpha_value_text)
        timeoutSeekBar = findViewById(R.id.timeout_seekbar)
        timeoutValueText = findViewById(R.id.timeout_value_text)
        keyboardAvoidanceSwitch = findViewById(R.id.keyboard_avoidance_switch)
        tapBehaviorRadioGroup = findViewById(R.id.tap_behavior_radio_group)
        tapBehaviorStandard = findViewById(R.id.tap_behavior_standard)
        tapBehaviorBack = findViewById(R.id.tap_behavior_back)
        tapBehaviorSafeHome = findViewById(R.id.tap_behavior_safe_home)
        advancedToggleCard = findViewById(R.id.advanced_toggle_card)
        advancedContent = findViewById(R.id.advanced_content)
        advancedArrow = findViewById(R.id.advanced_arrow)
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

    private fun setupAdvancedToggle() {
        advancedToggleCard.setOnClickListener {
            isAdvancedExpanded = !isAdvancedExpanded
            advancedContent.visibility = if (isAdvancedExpanded) View.VISIBLE else View.GONE
            advancedArrow.text = if (isAdvancedExpanded) "▲" else "▼"
        }
    }

    private fun setupAlphaSeekBar() {
        alphaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentAlpha = progress
                updateAlphaText(progress)
                lifecycleScope.launch {
                    settingsRepository.setAlpha(progress)
                }
                broadcastSettingsUpdate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateAlphaText(alpha: Int) {
        val percentage = (alpha * 100) / 255
        alphaValueText.text = "$percentage%"
    }

    private fun setupTimeoutSeekBar() {
        timeoutSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentTimeout = progress.toLong()
                updateTimeoutText(progress)
                lifecycleScope.launch {
                    settingsRepository.setRecentsTimeout(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateTimeoutText(timeout: Int) {
        timeoutValueText.text = "$timeout ms"
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

    private fun setupTapBehaviorRadioGroup() {
        tapBehaviorRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val behavior = when (checkedId) {
                R.id.tap_behavior_standard -> "STANDARD"
                R.id.tap_behavior_back -> "NAVI"
                R.id.tap_behavior_safe_home -> "SAFE_HOME"
                else -> "NAVI"
            }
            currentTapBehavior = behavior
            lifecycleScope.launch {
                settingsRepository.setTapBehavior(behavior)
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
            settingsRepository.getAlpha().collect { alpha ->
                currentAlpha = alpha
                alphaSeekBar.progress = alpha
                updateAlphaText(alpha)
            }
        }

        lifecycleScope.launch {
            settingsRepository.getRecentsTimeout().collect { timeout ->
                currentTimeout = timeout
                timeoutSeekBar.progress = timeout.toInt()
                updateTimeoutText(timeout.toInt())
            }
        }

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

        lifecycleScope.launch {
            settingsRepository.getTapBehavior().collect { behavior ->
                currentTapBehavior = behavior
                when (behavior) {
                    "STANDARD" -> tapBehaviorStandard.isChecked = true
                    "NAVI" -> tapBehaviorBack.isChecked = true
                    "SAFE_HOME" -> tapBehaviorSafeHome.isChecked = true
                }
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

        fun updateColor() {
            val color = Color.rgb(redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress)
            colorPreview.setBackgroundColor(color)
            redValue.text = redSeekBar.progress.toString()
            greenValue.text = greenSeekBar.progress.toString()
            blueValue.text = blueSeekBar.progress.toString()
        }
        updateColor()

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateColor()
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
}
