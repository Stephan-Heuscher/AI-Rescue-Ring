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
 * Dialog for showing AI suggestions with approve/reject/refine options.
 */
class AISuggestionDialog(
    context: Context,
    private val suggestion: String,
    private val onApprove: () -> Unit,
    private val onRefine: () -> Unit,
    private val onReject: () -> Unit = {}
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_ai_suggestion)

        val suggestionText = findViewById<TextView>(R.id.suggestionText)
        val approveButton = findViewById<Button>(R.id.approveButton)
        val refineButton = findViewById<Button>(R.id.refineButton)
        val rejectButton = findViewById<Button>(R.id.rejectButton)

        suggestionText.text = suggestion

        approveButton.setOnClickListener {
            onApprove()
            dismiss()
        }

        refineButton.setOnClickListener {
            onRefine()
            dismiss()
        }

        rejectButton.setOnClickListener {
            onReject()
            dismiss()
        }

        // Dialog must be explicitly dismissed - prevent accidental dismissal
        setCancelable(false)
    }
}
