package com.morfoboard.app.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

/**
 * Manages Google Sign-In flow.
 * Uses Google Identity Services for token acquisition.
 */
class GoogleSignInManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleSignInManager"
        const val RC_SIGN_IN = 9001
        // TODO: Replace with actual Web Client ID from Google Cloud Console
        private const val WEB_CLIENT_ID = "729038565292-vjj9dde5na8tasbgrs2do9h2m9s3n8j6.apps.googleusercontent.com"
    }

    private val gso: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(WEB_CLIENT_ID)
        .requestEmail()
        .requestProfile()
        .build()

    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    /**
     * Returns the sign-in Intent to launch.
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * Returns the last signed-in account, if any.
     */
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Handles the result from the sign-in Intent.
     * Returns a SignInResult on success or failure.
     */
    fun handleSignInResult(task: Task<GoogleSignInAccount>): SignInResult {
        return try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            val email = account?.email
            val displayName = account?.displayName

            if (idToken != null && email != null) {
                Log.d(TAG, "Sign-in success: $email")
                SignInResult.Success(
                    idToken = idToken,
                    email = email,
                    displayName = displayName ?: email.substringBefore("@")
                )
            } else {
                Log.e(TAG, "Sign-in failed: no idToken or email")
                SignInResult.Failure("Sign-in failed: missing token or email")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed with code: ${e.statusCode}", e)
            SignInResult.Failure("Sign-in failed (${e.statusCode}): ${e.message}")
        }
    }

    /**
     * Signs the user out.
     */
    fun signOut() {
        googleSignInClient.signOut()
        Log.d(TAG, "Signed out")
    }

    sealed class SignInResult {
        data class Success(
            val idToken: String,
            val email: String,
            val displayName: String
        ) : SignInResult()

        data class Failure(val message: String) : SignInResult()
    }
}
