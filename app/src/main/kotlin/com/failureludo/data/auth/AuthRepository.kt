package com.failureludo.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.failureludo.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {

    private val auth = Firebase.auth
    private val firestore = Firebase.firestore

    val currentProfile: UserProfile?
        get() = auth.currentUser?.toProfile()

    /** Emits the current user profile whenever auth state changes. */
    val authStateFlow: Flow<UserProfile?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toProfile()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Signs in with Google using the Credential Manager API.
     * [activityContext] must be an Activity context so the sign-in bottom sheet can appear.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<UserProfile> {
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(activityContext.getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user ?: return Result.failure(Exception("Sign-in failed"))
                val profile = user.toProfile()
                upsertProfile(user, isGuest = false)
                Result.success(profile)
            } else {
                Result.failure(Exception("Unexpected credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(CancelledException())
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Signs in anonymously and assigns a deterministic guest username. */
    suspend fun signInAsGuest(): Result<UserProfile> {
        return try {
            val authResult = auth.signInAnonymously().await()
            val user = authResult.user ?: return Result.failure(Exception("Guest sign-in failed"))
            val profile = user.toProfile()
            upsertProfile(user, isGuest = true)
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    private suspend fun upsertProfile(user: FirebaseUser, isGuest: Boolean) {
        try {
            val profile = mapOf(
                "uid" to user.uid,
                "name" to user.toProfile().name,
                "platform" to "android",
                "isGuest" to isGuest
            )
            firestore.collection("users")
                .document(user.uid)
                .set(profile, SetOptions.merge())
                .await()
        } catch (_: Exception) {
            // Profile write is best-effort; auth succeeds regardless
        }
    }

    private fun FirebaseUser.toProfile(): UserProfile {
        val name = if (isAnonymous) {
            "Guest#${uid.takeLast(4).uppercase()}"
        } else {
            displayName?.takeIf { it.isNotBlank() } ?: "Player"
        }
        return UserProfile(uid = uid, name = name, isGuest = isAnonymous)
    }
}

/** Thrown when the user actively cancels the sign-in flow — not an error worth showing. */
class CancelledException : Exception("Sign-in cancelled")
