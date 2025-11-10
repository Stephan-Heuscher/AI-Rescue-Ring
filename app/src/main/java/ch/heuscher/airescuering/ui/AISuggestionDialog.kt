package ch.heuscher.airescuering.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView
import ch.heuscher.airescuering.R
import ch.heuscher.airescuering.domain.model.AISuggestion

/**
 * Dialog for showing AI suggestions with approve/refine/cancel options.
 * This dialog ensures users confirm all actions before the AI performs them.
 */
class AISuggestionDialog(
    context: Context,
    private val suggestion: String,
    private val onApprove: () -> Unit,
    private val onRefine: () -> Unit,
    private val onCancel: (() -> Unit)? = null
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_ai_suggestion)

        val suggestionText = findViewById<TextView>(R.id.suggestionText)
        val approveButton = findViewById<Button>(R.id.approveButton)
        val refineButton = findViewById<Button>(R.id.refineButton)
        val cancelButton = findViewById<Button>(R.id.cancelButton)

        suggestionText.text = suggestion

        approveButton.setOnClickListener {
            onApprove()
            dismiss()
        }

        refineButton.setOnClickListener {
            onRefine()
            dismiss()
        }

        cancelButton.setOnClickListener {
            onCancel?.invoke()
            dismiss()
        }

        // Prevent dismissal by clicking outside
        setCancelable(false)
    }
}
