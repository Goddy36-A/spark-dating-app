package com.spark.dating.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spark.dating.core.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        val trimmedEmail = email.trim()
        val validationError = validateLoginInput(trimmedEmail, password)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                authRepository.login(trimmedEmail, password)
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = mapAuthError(e))
                }
            }
        }
    }

    fun register(email: String, password: String, confirmPassword: String) {
        val trimmedEmail = email.trim()
        val validationError = validateRegisterInput(trimmedEmail, password, confirmPassword)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                authRepository.register(trimmedEmail, password)
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = mapAuthError(e))
                }
            }
        }
    }

    fun loginWithGoogle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                authRepository.loginWithGoogle()
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = mapAuthError(e))
                }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            _uiState.update { it.copy(error = "Enter your email address") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                authRepository.sendPasswordReset(trimmedEmail)
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = mapAuthError(e))
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateLoginInput(email: String, password: String): String? {
        if (email.isBlank()) return "Email is required"
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Enter a valid email"
        if (password.isBlank()) return "Password is required"
        return null
    }

    private fun validateRegisterInput(email: String, password: String, confirm: String): String? {
        if (email.isBlank()) return "Email is required"
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Enter a valid email"
        if (password.length < 8) return "Password must be at least 8 characters"
        if (password != confirm) return "Passwords don't match"
        return null
    }

    private fun mapAuthError(e: Exception): String = when {
        e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
            "Incorrect email or password"
        e.message?.contains("Email not confirmed", ignoreCase = true) == true ->
            "Please verify your email before logging in"
        e.message?.contains("User already registered", ignoreCase = true) == true ->
            "An account with this email already exists"
        e.message?.contains("Network", ignoreCase = true) == true ->
            "No internet connection"
        else -> "Something went wrong. Please try again"
    }
}
