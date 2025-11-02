package ch.heuscher.back_home_dot

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ImpressumActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_impressum)

        supportActionBar?.hide()

        // Setup back button
        findViewById<Button>(R.id.back_button).setOnClickListener {
            finish()
        }

        // Set version info
        val versionText = findViewById<TextView>(R.id.version_text)
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        versionText.text = "Version ${packageInfo.versionName} (Build ${packageInfo.longVersionCode})"
    }
}