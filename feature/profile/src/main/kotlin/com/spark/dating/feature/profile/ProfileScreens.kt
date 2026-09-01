package com.spark.dating.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.spark.dating.core.auth.AuthRepository
import com.spark.dating.core.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ProfileViewModel ──────────────────────────────────────────────────────────

data class ProfileUiState(
    val profile: Profile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val postgrest: Postgrest,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadMyProfile() {
        val userId = authRepository.currentUserId() ?: return
        loadProfile(userId)
    }

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val profile = postgrest["profiles"]
                    .select {
                        filter { eq("id", userId) }
                        limit(1)
                        single()
                    }
                    .decodeAs<Profile>()
                _uiState.update { it.copy(profile = profile, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Couldn't load profile") }
            }
        }
    }
}

// ── MyProfileScreen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onSubscription: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadMyProfile() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        val profile = uiState.profile
        if (uiState.isLoading || profile == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                // Hero photo + basic info
                Box(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                    val primaryPhoto = profile.photos.find { it.isPrimary } ?: profile.photos.firstOrNull()
                    AsyncImage(
                        model = primaryPhoto?.url,
                        contentDescription = "My profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Edit button overlay
                    FloatingActionButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(Icons.Filled.Edit, "Edit profile")
                    }
                }
            }

            item {
                ProfileInfoSection(profile = profile)
            }

            // Premium upsell
            if (!profile.isPremium) {
                item {
                    PremiumBanner(onClick = onSubscription)
                }
            }
        }
    }
}

// ── ProfileDetailScreen ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    userId: String,
    onBack: () -> Unit,
    onReport: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(userId) { viewModel.loadProfile(userId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onReport) {
                        Icon(Icons.Filled.Flag, "Report user")
                    }
                },
            )
        }
    ) { innerPadding ->
        val profile = uiState.profile
        if (uiState.isLoading || profile == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Photo gallery
            if (profile.photos.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.height(400.dp),
                    ) {
                        items(profile.photos.sortedBy { it.sortOrder }) { photo ->
                            AsyncImage(
                                model = photo.url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillParentMaxWidth().fillMaxHeight(),
                            )
                        }
                    }
                }
            }

            item { ProfileInfoSection(profile = profile) }
        }
    }
}

// ── Shared: ProfileInfoSection ────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileInfoSection(profile: Profile) {
    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${profile.firstName}, ${profile.age}",
                style = MaterialTheme.typography.headlineLarge,
            )
            if (profile.isVerified) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Verified, "Verified",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp))
            }
        }

        if (profile.occupation.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Work, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(profile.occupation, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        profile.distanceKm?.let { dist ->
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("${dist.toInt()} km away",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (profile.bio.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text("About", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(profile.bio, style = MaterialTheme.typography.bodyLarge)
        }

        if (profile.interests.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Interests", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.interests.forEach { interest ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${interest.emoji} ${interest.name}") },
                    )
                }
            }
        }

        if (profile.prompts.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            profile.prompts.sortedBy { it.sortOrder }.forEach { prompt ->
                PromptCard(question = prompt.question, answer = prompt.answer)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PromptCard(question: String, answer: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(question, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(answer, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PremiumBanner(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Upgrade to Premium", style = MaterialTheme.typography.titleSmall)
                Text("See who likes you, unlimited likes, and more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Icon(Icons.Filled.ChevronRight, null)
        }
    }
}

// ── EditProfileScreen ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadMyProfile() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        // Edit profile form would go here — mirrors the onboarding steps
        // but pre-populated with existing profile data
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            Text("Edit profile — populated from existing profile data",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

