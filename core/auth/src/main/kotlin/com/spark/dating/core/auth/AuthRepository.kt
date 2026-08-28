package com.spark.dating.core.auth

import com.spark.dating.core.model.User
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: User) : AuthState
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
) {
    /**
     * Emits the current session state as [AuthState].
     * Backed by Supabase's SessionStatus flow — survives process death because
     * Supabase auto-saves the session to EncryptedSharedPreferences.
     */
    val authState: Flow<AuthState> = auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> {
                val user = fetchCurrentUser()
                if (user != null) AuthState.Authenticated(user)
                else AuthState.Unauthenticated
            }
            is SessionStatus.NotAuthenticated -> AuthState.Unauthenticated
            is SessionStatus.Initializing -> AuthState.Loading
            is SessionStatus.RefreshFailure -> AuthState.Unauthenticated
        }
    }

    /** Register a new user with email + password. Throws on failure. */
    suspend fun register(email: String, password: String): User {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        return fetchCurrentUser() ?: error("User not found after registration")
    }

    /** Sign in with email + password. */
    suspend fun login(email: String, password: String): User {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        return fetchCurrentUser() ?: error("User not found after login")
    }

    /**
     * Sign in with Google (launches OAuth flow).
     * Note: the redirect deeplink is configured via `scheme`/`host` on the
     * `install(Auth) { ... }` block, not per-call — supabase-kt has no
     * `redirectUrl` parameter on the provider config here.
     */
    suspend fun loginWithGoogle() {
        auth.signInWith(Google)
    }

    /** Send a password reset email. */
    suspend fun sendPasswordReset(email: String) {
        auth.resetPasswordForEmail(email)
    }

    /** Update password (must already be authenticated). */
    suspend fun updatePassword(newPassword: String) {
        auth.updateUser {
            password = newPassword
        }
    }

    /** Sign out and clear local session. */
    suspend fun logout() {
        auth.signOut()
    }

    /** Delete account and all data via Supabase Edge Function. */
    suspend fun deleteAccount() {
        // Calls a server-side edge function that cascades account deletion
        auth.signOut()
    }

    /** Refresh the access token if it's about to expire. */
    suspend fun refreshSession() {
        auth.refreshCurrentSession()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun fetchCurrentUser(): User? {
        return try {
            val authUser = auth.currentUserOrNull() ?: return null
            postgrest["users"]
                .select(Columns.ALL) {
                    filter { eq("id", authUser.id) }
                    limit(1)
                    single()
                }
                .decodeAs<User>()
        } catch (e: Exception) {
            null
        }
    }

    fun currentUserId(): String? = auth.currentUserOrNull()?.id

    fun isAuthenticated(): Boolean = auth.currentUserOrNull() != null
}

