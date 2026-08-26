package com.spark.dating.feature.safety

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spark.dating.core.auth.AuthRepository
import com.spark.dating.core.model.ReportCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class SafetyUiState(
    val isLoading: Boolean = false,
    val isSubmitted: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SafetyViewModel @Inject constructor(
    private val postgrest: Postgrest,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SafetyUiState())
    val uiState: StateFlow<SafetyUiState> = _uiState.asStateFlow()

    fun submitReport(reportedUserId: String, category: ReportCategory, details: String) {
        val reporterId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                postgrest["reports"].insert(
                    mapOf(
                        "reporter_id" to reporterId,
                        "reported_id" to reportedUserId,
                        "category" to category.name.lowercase(),
                        "details" to details.trim(),
                    )
                )
                // Auto-block after report
                blockUser(reporterId, reportedUserId)
                _uiState.update { it.copy(isLoading = false, isSubmitted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Couldn't submit report. Please try again.") }
            }
        }
    }

    fun blockUser(blockerId: String? = null, blockedId: String) {
        val resolvedBlockerId = blockerId ?: authRepository.currentUserId() ?: return
        viewModelScope.launch {
            try {
                postgrest["blocks"].upsert(
                    mapOf("blocker_id" to resolvedBlockerId, "blocked_id" to blockedId)
                )
            } catch (_: Exception) {}
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

// ── Report Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    reportedUserId: String,
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: SafetyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf<ReportCategory?>(null) }
    var details by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isSubmitted) {
        if (uiState.isSubmitted) onSubmitted()
    }

    val categories = listOf(
        ReportCategory.HARASSMENT to "Harassment or abuse",
        ReportCategory.SPAM to "Spam or scam",
        ReportCategory.FAKE_PROFILE to "Fake profile",
        ReportCategory.INAPPROPRIATE_CONTENT to "Inappropriate content",
        ReportCategory.IMPERSONATION to "Impersonation",
        ReportCategory.UNDERAGE_CONCERN to "I think this person is underage",
        ReportCategory.OTHER to "Other",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Why are you reporting?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your report is confidential. We review all reports seriously.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            categories.forEach { (category, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("Additional details (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                shape = MaterialTheme.shapes.small,
            )

            uiState.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    selectedCategory?.let { cat ->
                        viewModel.submitReport(reportedUserId, cat, details)
                    }
                },
                enabled = selectedCategory != null && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError)
                } else {
                    Text("Submit report")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Safety Center ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyCenterScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety Center") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))

            SafetyTip(
                emoji = "🔒",
                title = "Your privacy comes first",
                body = "We never share your exact location. Only approximate distance is shown to other users.",
            )
            SafetyTip(
                emoji = "🚫",
                title = "Block freely",
                body = "Blocking someone immediately removes them from your discovery and prevents contact.",
            )
            SafetyTip(
                emoji = "📢",
                title = "Report bad actors",
                body = "Every report is reviewed by our team. Together we keep Spark safe.",
            )
            SafetyTip(
                emoji = "👶",
                title = "Age verification",
                body = "All users must confirm they are 18 or older. We verify age on our servers.",
            )
            SafetyTip(
                emoji = "💬",
                title = "Meet in public first",
                body = "When meeting someone from Spark in person, choose a public place for your first meeting.",
            )
        }
    }
}

@Composable
private fun SafetyTip(emoji: String, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
