package metrovoice.app

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

data class Account(
    val uid: String,
    val name: String?,
    val email: String?,
    /** "google.com", "apple.com" or "guest". */
    val provider: String,
) {
    val isGuest: Boolean get() = provider == GUEST

    companion object {
        const val GUEST = "guest"
        val Guest = Account("guest", null, null, GUEST)
    }
}

sealed interface SignInResult {
    data object Success : SignInResult
    data object Cancelled : SignInResult
    data class NotConfigured(val what: String) : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * Sign-in through Firebase Authentication:
 *  - Google: Credential Manager account picker → ID token → Firebase credential.
 *  - Apple: Firebase OAuth web flow (Android has no native Sign in with Apple).
 * Firebase is optional: without app/google-services.json the app works as a guest.
 */
object AuthManager {
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val user = firebaseAuth()?.currentUser
        _account.value = when {
            user != null -> user.toAccount()
            ProfileStore.isGuest(context) -> Account.Guest
            // No sign-in backend in this build: a sign-in screen would be a dead end, go straight in.
            !isConfigured(context) -> Account.Guest
            else -> null
        }
    }

    /** True when Firebase was initialised from google-services.json. */
    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private fun firebaseAuth(): FirebaseAuth? =
        appContext?.takeIf { isConfigured(it) }?.let { FirebaseAuth.getInstance() }

    fun continueAsGuest(context: Context) {
        ProfileStore.setGuest(context, true)
        _account.value = Account.Guest
    }

    suspend fun signInWithGoogle(activity: Activity): SignInResult {
        val auth = firebaseAuth() ?: return SignInResult.NotConfigured("Firebase (google-services.json)")
        val webClientId = activity.resources
            .getIdentifier("default_web_client_id", "string", activity.packageName)
            .takeIf { it != 0 }?.let(activity::getString)
            ?: return SignInResult.NotConfigured("Google OAuth client (default_web_client_id)")
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                .build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignInResult.Failed("Unexpected credential type")
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
            onSignedIn(activity, user)
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: Exception) {
            SignInResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun signInWithApple(activity: Activity): SignInResult {
        val auth = firebaseAuth() ?: return SignInResult.NotConfigured("Firebase (google-services.json) + Apple provider")
        return try {
            val provider = OAuthProvider.newBuilder("apple.com").setScopes(listOf("email", "name")).build()
            val result = auth.pendingAuthResult?.await() ?: auth.startActivityForSignInWithProvider(activity, provider).await()
            onSignedIn(activity, result.user)
        } catch (e: Exception) {
            SignInResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    fun signOut(context: Context) {
        firebaseAuth()?.signOut()
        ProfileStore.setGuest(context, false)
        _account.value = null
    }

    private fun onSignedIn(context: Context, user: FirebaseUser?): SignInResult {
        user ?: return SignInResult.Failed("No user")
        ProfileStore.setGuest(context, false)
        _account.value = user.toAccount()
        return SignInResult.Success
    }

    private fun FirebaseUser.toAccount() = Account(
        uid = uid,
        name = displayName,
        email = email,
        provider = providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: "firebase",
    )
}
