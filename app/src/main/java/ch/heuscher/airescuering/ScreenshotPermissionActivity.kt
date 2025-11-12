package ch.heuscher.airescuering

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * Transparent activity to request screenshot permission from the user
 * This is required because MediaProjection permission can only be requested from an Activity
 */
class ScreenshotPermissionActivity : AppCompatActivity() {

    private val TAG = "ScreenshotPermission"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate: Requesting screenshot permission")

        // Request screen capture permission
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Log.d(TAG, "onActivityResult: Permission granted")

                // Send broadcast with permission result
                val intent = Intent(ACTION_SCREENSHOT_PERMISSION_RESULT).apply {
                    putExtra(EXTRA_RESULT_CODE, resultCode)
                    putExtra(EXTRA_RESULT_DATA, data)
                }
                sendBroadcast(intent)
            } else {
                Log.d(TAG, "onActivityResult: Permission denied")

                // Send broadcast with denial
                val intent = Intent(ACTION_SCREENSHOT_PERMISSION_RESULT).apply {
                    putExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                }
                sendBroadcast(intent)
            }

            // Close the activity
            finish()
        }
    }

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
        const val ACTION_SCREENSHOT_PERMISSION_RESULT = "ch.heuscher.airescuering.SCREENSHOT_PERMISSION_RESULT"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        fun createIntent(context: Context): Intent {
            return Intent(context, ScreenshotPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}
