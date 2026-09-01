package com.spark.dating.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spark.dating.core.auth.AuthRepository
import com.spark.dating.core.database.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val darkTheme: Boolean = false,
    val notifMatch: Boolean = true,
    val notifMessage: Boolean = true,
    val notifLikes: Boolean = false,
    val isLoggingOut: Boolean = false,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.darkTheme,
                preferencesRepository.notifNewMatch,
                preferencesRepository.notifNewMessage,
                preferencesRepository.notifLikes,
            ) { dark, match, message, likes ->
                SettingsUiState(
                    darkTheme = dark,
                    notifMatch = match,
                    notifMessage = message,
                    notifLikes = likes,
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setDarkTheme(enabled)
    }

    fun setNotifMatch(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setNotifNewMatch(enabled)
    }

    fun setNotifMessage(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setNotifNewMessage(enabled)
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            try {
                authRepository.logout()
                preferencesRepository.clear()
                _uiState.update { it.copy(isLoggingOut = false, loggedOut = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoggingOut = false) }
            }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSafetyCenter: () -> Unit,
    onSubscription: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) onBack()
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            onConfirm = { /* TODO: call delete account */ },
            onDismiss = { showDeleteDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSection("Appearance") {
                SwitchRow(
                    icon = Icons.Filled.DarkMode,
                    label = "Dark mode",
                    checked = uiState.darkTheme,
                    onCheckedChange = viewModel::setDarkTheme,
                )
            }

            SettingsSection("Notifications") {
                SwitchRow(
                    icon = Icons.Filled.Favorite,
                    label = "New matches",
                    checked = uiState.notifMatch,
                    onCheckedChange = viewModel::setNotifMatch,
                )
                SwitchRow(
                    icon = Icons.Filled.Message,
                    label = "New messages",
                    checked = uiState.notifMessage,
                    onCheckedChange = viewModel::setNotifMessage,
                )
                SwitchRow(
                    icon = Icons.Filled.Star,
                    label = "Likes (Premium)",
                    checked = uiState.notifLikes,
                    onCheckedChange = viewModel::setNotifMatch,
                )
            }

            SettingsSection("Account") {
                ClickableRow(
                    icon = Icons.Filled.WorkspacePremium,
                    label = "Upgrade to Premium",
                    onClick = onSubscription,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                ClickableRow(
                    icon = Icons.Filled.Shield,
                    label = "Safety Center",
                    onClick = onSafetyCenter,
                )
                ClickableRow(
                    icon = Icons.Filled.Lock,
                    label = "Privacy & Data",
                    onClick = { /* navigate to privacy */ },
                )
                ClickableRow(
                    icon = Icons.Filled.Description,
                    label = "Terms of Service",
                    onClick = { /* open webview */ },
                )
                ClickableRow(
                    icon = Icons.Filled.Description,
                    label = "Privacy Policy",
                    onClick = { /* open webview */ },
                )
            }

            SettingsSection("Danger zone") {
                ClickableRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = "Log out",
                    onClick = viewModel::logout,
                    tint = MaterialTheme.colorScheme.error,
                    loading = uiState.isLoggingOut,
                )
                ClickableRow(
                    icon = Icons.Filled.DeleteForever,
                    label = "Delete account",
                    onClick = { showDeleteDialog = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "Spark v1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Reusable row components ───────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(icon, null, modifier = Modifier.size(22.dp))
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
private fun ClickableRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    loading: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(label, color = tint) },
        leadingContent = {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete account?") },
        text = {
            Text(
                "This permanently deletes your profile, matches, and messages. " +
                "This action cannot be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

