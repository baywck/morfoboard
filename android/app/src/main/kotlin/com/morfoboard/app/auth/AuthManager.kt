package com.morfoboard.app.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages Google authentication tokens with encrypted storage.
 */
class AuthManager(private val context: Context) {

    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_FILE = "morfoboard_secure_prefs"
        private const val KEY_ID_TOKEN = "google_id_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_IS_SIGNED_IN = "is_signed_in"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var isSignedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_SIGNED_IN, false)
        private set(value) = prefs.edit().putBoolean(KEY_IS_SIGNED_IN, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        private set(value) {
            if (value == null) prefs.edit().remove(KEY_USER_EMAIL).apply()
            else prefs.edit().putString(KEY_USER_EMAIL, value).apply()
        }

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        private set(value) {
            if (value == null) prefs.edit().remove(KEY_USER_NAME).apply()
            else prefs.edit().putString(KEY_USER_NAME, value).apply()
        }

    fun getIdToken(): String? {
        return prefs.getString(KEY_ID_TOKEN, null)
    }

    fun saveSession(idToken: String, email: String, name: String) {
        Log.d(TAG, "Saving session for $email")
        prefs.edit()
            .putString(KEY_ID_TOKEN, idToken)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NAME, name)
            .putBoolean(KEY_IS_SIGNED_IN, true)
            .apply()
    }

    fun clearSession() {
        Log.d(TAG, "Clearing session")
        prefs.edit()
            .remove(KEY_ID_TOKEN)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .putBoolean(KEY_IS_SIGNED_IN, false)
            .apply()
    }

    /**
     * Returns the current ID token if signed in, null otherwise.
     * This is used by AIClient as the token provider.
     */
    fun getTokenProvider(): () -> String? {
        return { getIdToken() }
    }
}
