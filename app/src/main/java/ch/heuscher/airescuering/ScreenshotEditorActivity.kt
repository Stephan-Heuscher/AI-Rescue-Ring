package ch.heuscher.airescuering

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Activity for editing screenshots - allows users to blank out sensitive data
 */
class ScreenshotEditorActivity : AppCompatActivity() {

    private lateinit var screenshotView: ScreenshotEditorView
    private lateinit var btnDone: Button
    private lateinit var btnClear: Button
    private var screenshotPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screenshot_editor)

        screenshotPath = intent.getStringExtra(EXTRA_SCREENSHOT_PATH)
        if (screenshotPath == null) {
            finish()
            return
        }

        screenshotView = findViewById(R.id.screenshot_editor_view)
        btnDone = findViewById(R.id.btn_done)
        btnClear = findViewById(R.id.btn_clear)

        // Load screenshot
        val bitmap = android.graphics.BitmapFactory.decodeFile(screenshotPath)
        screenshotView.setScreenshot(bitmap)

        btnDone.setOnClickListener {
            // Save edited screenshot and return
            val editedBitmap = screenshotView.getEditedBitmap()
            val editedPath = saveEditedScreenshot(editedBitmap)

            val resultIntent = Intent().apply {
                putExtra(EXTRA_EDITED_SCREENSHOT_PATH, editedPath)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        btnClear.setOnClickListener {
            screenshotView.clearMarkings()
        }
    }

    private fun saveEditedScreenshot(bitmap: Bitmap): String {
        val file = File(cacheDir, "edited_screenshot_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up original screenshot
        screenshotPath?.let { path ->
            File(path).delete()
        }
    }

    companion object {
        const val EXTRA_SCREENSHOT_PATH = "screenshot_path"
        const val EXTRA_EDITED_SCREENSHOT_PATH = "edited_screenshot_path"
        const val REQUEST_EDIT_SCREENSHOT = 1002

        fun createIntent(context: Context, screenshotPath: String): Intent {
            return Intent(context, ScreenshotEditorActivity::class.java).apply {
                putExtra(EXTRA_SCREENSHOT_PATH, screenshotPath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}

/**
 * Custom view for editing screenshots with blank-out functionality
 */
class ScreenshotEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var screenshotBitmap: Bitmap? = null
    private val blankOutPaths = mutableListOf<Path>()
    private var currentPath: Path? = null

    private val blankOutPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 50f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    fun setScreenshot(bitmap: Bitmap) {
        screenshotBitmap = bitmap
        invalidate()
    }

    fun clearMarkings() {
        blankOutPaths.clear()
        currentPath = null
        invalidate()
    }

    fun getEditedBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw screenshot scaled to fit view
        screenshotBitmap?.let { bitmap ->
            val scaleX = width.toFloat() / bitmap.width
            val scaleY = height.toFloat() / bitmap.height
            val scale = minOf(scaleX, scaleY)

            val scaledWidth = bitmap.width * scale
            val scaledHeight = bitmap.height * scale
            val left = (width - scaledWidth) / 2
            val top = (height - scaledHeight) / 2

            canvas.save()
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            canvas.restore()
        }

        // Draw blank-out paths
        for (path in blankOutPaths) {
            canvas.drawPath(path, blankOutPaint)
        }

        currentPath?.let {
            canvas.drawPath(it, blankOutPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path().apply {
                    moveTo(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath?.lineTo(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentPath?.let {
                    blankOutPaths.add(it)
                }
                currentPath = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
