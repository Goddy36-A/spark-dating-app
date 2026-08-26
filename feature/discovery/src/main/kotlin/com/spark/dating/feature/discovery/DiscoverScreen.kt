package com.spark.dating.feature.discovery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spark.dating.core.model.Profile
import com.spark.dating.core.ui.components.*
import com.spark.dating.core.ui.theme.SparkColors
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun DiscoverScreen(
    onProfileClick: (String) -> Unit,
    onReport: (String) -> Unit,
    onSubscription: () -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profiles = uiState.profiles
    val currentIndex = uiState.currentIndex
    val visibleProfile = profiles.getOrNull(currentIndex)

    // Match celebration
    uiState.matchedWith?.let { matchedProfile ->
        MatchCelebrationDialog(
            matchedProfile = matchedProfile,
            onDismiss = viewModel::dismissMatch,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading && profiles.isEmpty() -> {
                SparkLoading()
            }
            uiState.error != null && profiles.isEmpty() -> {
                SparkErrorState(
                    message = uiState.error ?: "Something went wrong",
                    onRetry = viewModel::refresh,
                )
            }
            uiState.isEmpty || visibleProfile == null -> {
                SparkEmptyState(
                    title = "You've seen everyone nearby",
                    body = "Check back tomorrow or expand your distance in settings.",
                )
            }
            else -> {
                // Show top 2 cards (stack effect)
                val nextProfile = profiles.getOrNull(currentIndex + 1)
                if (nextProfile != null) {
                    ProfileCard(
                        profile = nextProfile,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .scale(0.95f),
                        isInteractive = false,
                        onLike = {},
                        onPass = {},
                        onProfileClick = {},
                    )
                }

                ProfileCard(
                    profile = visibleProfile,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    isInteractive = true,
                    onLike = { viewModel.like(visibleProfile) },
                    onPass = { viewModel.pass(visibleProfile) },
                    onSuperLike = { viewModel.like(visibleProfile, isSuperLike = true) },
                    onProfileClick = { onProfileClick(visibleProfile.id) },
                )
            }
        }
    }
}

// ── Profile Card ──────────────────────────────────────────────────────────────

@Composable
private fun ProfileCard(
    profile: Profile,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    onLike: () -> Unit,
    onPass: () -> Unit,
    onSuperLike: (() -> Unit)? = null,
    onProfileClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }

    val likeAlpha by remember { derivedStateOf { (offsetX.value / 300f).coerceIn(0f, 1f) } }
    val passAlpha by remember { derivedStateOf { (-offsetX.value / 300f).coerceIn(0f, 1f) } }

    val swipeGesture = if (isInteractive) Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = {
                scope.launch {
                    when {
                        offsetX.value > 150f -> {
                            offsetX.animateTo(1000f, tween(300))
                            onLike()
                            offsetX.snapTo(0f)
                            offsetY.snapTo(0f)
                            rotation.snapTo(0f)
                        }
                        offsetX.value < -150f -> {
                            offsetX.animateTo(-1000f, tween(300))
                            onPass()
                            offsetX.snapTo(0f)
                            offsetY.snapTo(0f)
                            rotation.snapTo(0f)
                        }
                        else -> {
                            offsetX.animateTo(0f, tween(400))
                            offsetY.animateTo(0f, tween(400))
                            rotation.animateTo(0f, tween(400))
                        }
                    }
                }
            },
            onDrag = { _, dragAmount ->
                scope.launch {
                    offsetX.snapTo(offsetX.value + dragAmount.x)
                    offsetY.snapTo(offsetY.value + dragAmount.y * 0.3f)
                    rotation.snapTo(offsetX.value / 30f)
                }
            },
        )
    } else Modifier

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = rotation.value
            }
            .then(swipeGesture)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Photo
                val primaryPhoto = profile.photos.find { it.isPrimary } ?: profile.photos.firstOrNull()
                AsyncImage(
                    model = primaryPhoto?.url,
                    contentDescription = "Profile photo of ${profile.firstName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.5f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.85f),
                                )
                            )
                        )
                )

                // LIKE / NOPE labels
                if (isInteractive) {
                    Text(
                        "SPARK",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = SparkColors.CoralFlame.copy(alpha = likeAlpha),
                        modifier = Modifier.align(Alignment.TopStart).padding(24.dp).rotate(-20f),
                    )
                    Text(
                        "PASS",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = SparkColors.Error.copy(alpha = passAlpha),
                        modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).rotate(20f),
                    )
                }

                // Info overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${profile.firstName}, ${profile.age}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                        )
                        if (profile.isVerified) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.Verified, "Verified",
                                tint = SparkColors.CoralFlame, modifier = Modifier.size(22.dp))
                        }
                    }

                    if (profile.occupation.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(profile.occupation, style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.85f))
                    }

                    profile.distanceKm?.let { dist ->
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, null,
                                tint = Color.White.copy(0.7f), modifier = Modifier.size(16.dp))
                            Text("${dist.toInt()} km away",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.7f))
                        }
                    }

                    if (profile.interests.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(profile.interests.take(3)) { interest ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("${interest.emoji} ${interest.name}",
                                        style = MaterialTheme.typography.labelSmall) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        labelColor = Color.White,
                                    ),
                                )
                            }
                        }
                    }

                    if (isInteractive) {
                        Spacer(Modifier.height(16.dp))
                        // Action row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            ActionButton(
                                icon = Icons.Filled.Close,
                                contentDesc = "Pass",
                                tint = MaterialTheme.colorScheme.error,
                                size = 56,
                                onClick = onPass,
                            )
                            ActionButton(
                                icon = Icons.Filled.Star,
                                contentDesc = "Super like",
                                tint = Color(0xFF1976D2),
                                size = 44,
                                onClick = { onSuperLike?.invoke() },
                            )
                            ActionButton(
                                icon = Icons.Filled.Favorite,
                                contentDesc = "Like",
                                tint = SparkColors.CoralFlame,
                                size = 56,
                                onClick = onLike,
                            )
                        }
                    }
                }

                // Tap for profile detail
                if (isInteractive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onProfileClick)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    tint: Color,
    size: Int,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(size.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = tint,
        ),
    ) {
        Icon(icon, contentDesc, modifier = Modifier.size((size * 0.5f).dp))
    }
}

// ── Match Celebration ─────────────────────────────────────────────────────────

@Composable
private fun MatchCelebrationDialog(
    matchedProfile: Profile,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = SparkColors.Ink,
            ),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("✦", style = MaterialTheme.typography.displayLarge,
                    color = SparkColors.CoralFlame)
                Spacer(Modifier.height(8.dp))
                Text("It's a match!", style = MaterialTheme.typography.headlineMedium,
                    color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("You and ${matchedProfile.firstName} liked each other.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SparkColors.Fog)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SparkColors.CoralFlame,
                    ),
                ) {
                    Text("Say hello")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Keep swiping", color = SparkColors.Mist)
                }
            }
        }
    }
}
