package com.spark.dating.feature.matching

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.spark.dating.core.auth.AuthRepository
import com.spark.dating.core.model.Match
import com.spark.dating.core.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Matches ViewModel ─────────────────────────────────────────────────────────

data class MatchesUiState(
    val matches: List<Match> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MatchesViewModel @Inject constructor(
    private val postgrest: Postgrest,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchesUiState())
    val uiState: StateFlow<MatchesUiState> = _uiState.asStateFlow()

    init { loadMatches() }

    fun loadMatches() {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val matches = postgrest["matches"]
                    .select {
                        filter {
                            or {
                                eq("user1_id", userId)
                                eq("user2_id", userId)
                            }
                            eq("is_unmatched", false)
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Match>()
                _uiState.update { it.copy(matches = matches, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Couldn't load matches") }
            }
        }
    }
}

// ── Likes ViewModel ───────────────────────────────────────────────────────────

data class LikesUiState(
    val likersCount: Int = 0,
    val isLoading: Boolean = false,
    val isPremium: Boolean = false,
)

@HiltViewModel
class LikesViewModel @Inject constructor(
    private val postgrest: Postgrest,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LikesUiState())
    val uiState: StateFlow<LikesUiState> = _uiState.asStateFlow()

    init { loadLikesCount() }

    private fun loadLikesCount() {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            try {
                // Get count of people who liked me that I haven't responded to
                val result = postgrest["likes"]
                    .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id"), {
                        filter { eq("liked_id", userId) }
                        // Only count likes I haven't seen (not matched yet)
                    })
                    .decodeList<Map<String, String>>()
                _uiState.update { it.copy(likersCount = result.size) }
            } catch (_: Exception) {}
        }
    }
}

// ── MatchesScreen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    onChat: (String) -> Unit,
    viewModel: MatchesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Matches") }) }) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.matches.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("💝", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(16.dp))
                        Text("No matches yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Keep swiping — your matches appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.padding(innerPadding).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.matches, key = { it.id }) { match ->
                        MatchCard(match = match, onClick = { onChat(match.conversationId) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchCard(match: Match, onClick: () -> Unit) {
    val profile = match.profile
    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(0.7f),
        shape = MaterialTheme.shapes.large,
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = profile?.photos?.find { it.isPrimary }?.url,
                contentDescription = profile?.firstName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            ) {
                Text(
                    text = "${profile?.firstName ?: "Match"}, ${profile?.age ?: ""}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

// ── LikesScreen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikesScreen(
    onSubscription: () -> Unit,
    viewModel: LikesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Likes") }) }) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            if (uiState.isPremium) {
                // Premium: show who liked
                Text("Premium likes grid — coming soon")
            } else {
                // Free: show blur + upgrade prompt
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text("✨", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "${uiState.likersCount} people liked you",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Upgrade to Spark Gold to see who liked you and match instantly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onSubscription,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                    ) {
                        Text("Upgrade to Gold")
                    }
                }
            }
        }
    }
}
