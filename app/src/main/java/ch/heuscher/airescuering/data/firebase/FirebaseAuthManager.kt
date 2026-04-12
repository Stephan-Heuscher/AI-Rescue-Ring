package ch.heuscher.airescuering.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Manages Firebase Anonymous Authentication for J-AI-mes.
 * 
 * When the user has no API key, the app authenticates anonymously with Firebase
 * and uses the ID token to call the Gemini proxy Cloud Function.
 * 
 * Benefits:
 * - No user sign-up friction (anonymous = instant)
 * - Each device gets a unique UID for rate limiting
 * - ID tokens are short-lived and auto-refresh
 */
object FirebaseAuthManager {
    private const val TAG = "FirebaseAuthManager"

    private var firebaseAuth: FirebaseAuth? = null
    private var isInitialized = false

    /**
     * Initialize Firebase. Call from Application.onCreate() or MainActivity.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            FirebaseApp.initializeApp(context)
            firebaseAuth = FirebaseAuth.getInstance()
            isInitialized = true
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase", e)
        }
    }

    /**
     * Sign in anonymously. Creates a new anonymous user if none exists.
     * If the device already has an anonymous session, it's reused.
     */
    suspend fun signInAnonymously(): FirebaseUser? {
        val auth = firebaseAuth ?: run {
            Log.w(TAG, "Firebase not initialized")
            return null
        }

        // Already signed in?
        auth.currentUser?.let { user ->
            Log.d(TAG, "Already signed in anonymously: uid=${user.uid}")
            return user
        }

        // Sign in
        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user
            Log.d(TAG, "Signed in anonymously: uid=${user?.uid}")
            user
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in failed: ${e.message}", e)
            if (e.message?.contains("CONFIGURATION_NOT_FOUND") == true) {
                Log.e(TAG, "CRITICAL: Anonymous Authentication is not enabled in Firebase Console!")
            }
            null
        }
    }

    /**
     * Get a fresh Firebase ID token for authenticating with the Cloud Function.
     * Returns null if not signed in or if Firebase is unavailable.
     */
    suspend fun getIdToken(): String? {
        val user = firebaseAuth?.currentUser ?: run {
            // Try to sign in first
            signInAnonymously()
            firebaseAuth?.currentUser
        }

        if (user == null) {
            Log.w(TAG, "No Firebase user, cannot get ID token")
            return null
        }

        return try {
            val tokenResult = user.getIdToken(false).await()
            tokenResult.token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ID token", e)
            null
        }
    }

    /**
     * Check if Firebase is available and initialized.
     */
    fun isAvailable(): Boolean = isInitialized && firebaseAuth != null

    /**
     * Get the current user's UID (for logging/debugging).
     */
    fun getCurrentUid(): String? = firebaseAuth?.currentUser?.uid
}
