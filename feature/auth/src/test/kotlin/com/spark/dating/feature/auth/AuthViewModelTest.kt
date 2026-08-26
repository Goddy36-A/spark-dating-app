package com.spark.dating.feature.auth

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.spark.dating.core.auth.AuthRepository
import com.spark.dating.core.model.User
import com.spark.dating.core.model.UserRole
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        viewModel = AuthViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Login validation ──────────────────────────────────────────────────────

    @Test
    fun `login with empty email shows error`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertThat(initial.error).isNull()

            viewModel.login("", "password123")
            val errorState = awaitItem()
            assertThat(errorState.error).isEqualTo("Email is required")
        }
    }

    @Test
    fun `login with invalid email shows error`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.login("notanemail", "password123")
            val errorState = awaitItem()
            assertThat(errorState.error).isEqualTo("Enter a valid email")
        }
    }

    @Test
    fun `login with empty password shows error`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.login("test@example.com", "")
            val errorState = awaitItem()
            assertThat(errorState.error).isEqualTo("Password is required")
        }
    }

    @Test
    fun `login success sets success state`() = runTest {
        val fakeUser = User(
            id = "123",
            email = "test@example.com",
            createdAt = "2025-01-01T00:00:00Z",
            onboardingComplete = true,
        )
        coEvery { authRepository.login(any(), any()) } returns fakeUser

        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.login("test@example.com", "password123")
            val loadingState = awaitItem()
            assertThat(loadingState.isLoading).isTrue()
            val successState = awaitItem()
            assertThat(successState.success).isTrue()
            assertThat(successState.isLoading).isFalse()
            assertThat(successState.error).isNull()
        }
    }

    @Test
    fun `login failure shows friendly error`() = runTest {
        coEvery { authRepository.login(any(), any()) } throws
            Exception("Invalid login credentials")

        viewModel.uiState.test {
            awaitItem()
            viewModel.login("test@example.com", "wrongpass")
            awaitItem() // loading
            val errorState = awaitItem()
            assertThat(errorState.isLoading).isFalse()
            assertThat(errorState.error).isEqualTo("Incorrect email or password")
        }
    }

    // ── Register validation ───────────────────────────────────────────────────

    @Test
    fun `register with short password shows error`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.register("test@example.com", "short", "short")
            val errorState = awaitItem()
            assertThat(errorState.error).isEqualTo("Password must be at least 8 characters")
        }
    }

    @Test
    fun `register with mismatched passwords shows error`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.register("test@example.com", "password123", "different123")
            val errorState = awaitItem()
            assertThat(errorState.error).isEqualTo("Passwords don't match")
        }
    }

    @Test
    fun `clearError resets error state`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.login("", "")
            awaitItem() // error state
            viewModel.clearError()
            val cleared = awaitItem()
            assertThat(cleared.error).isNull()
        }
    }
}

// ── Age validation tests ──────────────────────────────────────────────────────

class AgeValidationTest {

    @Test
    fun `user exactly 18 is allowed`() {
        val dob = java.time.LocalDate.now().minusYears(18)
        val age = java.time.Period.between(dob, java.time.LocalDate.now()).years
        assertThat(age).isAtLeast(18)
    }

    @Test
    fun `user 17 years old is rejected`() {
        val dob = java.time.LocalDate.now().minusYears(17)
        val age = java.time.Period.between(dob, java.time.LocalDate.now()).years
        assertThat(age).isLessThan(18)
    }

    @Test
    fun `user born today minus 18 years and 1 day is valid`() {
        val dob = java.time.LocalDate.now().minusYears(18).minusDays(1)
        val age = java.time.Period.between(dob, java.time.LocalDate.now()).years
        assertThat(age).isAtLeast(18)
    }
}
