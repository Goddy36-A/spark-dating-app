package com.spark.dating.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spark.dating.core.model.Gender
import com.spark.dating.core.model.RelationshipIntent
import com.spark.dating.core.ui.components.SparkButton
import com.spark.dating.core.ui.components.SparkTextField

@Composable
fun OnboardingNavHost(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    LaunchedEffect(state.error) {
        // errors are shown inline in each step
    }

    val stepIndex = state.currentStep.ordinal
    val totalSteps = OnboardingStep.PERMISSIONS.ordinal + 1

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // Progress bar + back button
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.currentStep != OnboardingStep.NAME) {
                IconButton(onClick = viewModel::prevStep) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / totalSteps },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Spacer(Modifier.size(48.dp))
        }

        AnimatedContent(
            targetState = state.currentStep,
            transitionSpec = {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            },
            label = "onboarding_step",
        ) { step ->
            when (step) {
                OnboardingStep.NAME -> NameStep(state, viewModel)
                OnboardingStep.DATE_OF_BIRTH -> DobStep(state, viewModel)
                OnboardingStep.GENDER -> GenderStep(state, viewModel)
                OnboardingStep.PREFERENCE -> PreferenceStep(state, viewModel)
                OnboardingStep.INTENT -> IntentStep(state, viewModel)
                OnboardingStep.BIO -> BioStep(state, viewModel)
                OnboardingStep.INTERESTS -> InterestsStep(state, viewModel)
                OnboardingStep.PHOTOS -> PhotosStep(state, viewModel)
                OnboardingStep.PERMISSIONS -> PermissionsStep(viewModel)
                OnboardingStep.DONE -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

// ── Shared step scaffold ──────────────────────────────────────────────────────

@Composable
private fun StepContainer(
    title: String,
    subtitle: String? = null,
    error: String? = null,
    isLoading: Boolean = false,
    ctaText: String = "Continue",
    onCta: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(32.dp))
        content()
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(16.dp))
        SparkButton(text = ctaText, onClick = onCta, loading = isLoading)
        Spacer(Modifier.height(24.dp))
    }
}

// ── Step: Name ────────────────────────────────────────────────────────────────

@Composable
private fun NameStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepContainer(
        title = "What's your first name?",
        subtitle = "This is how you'll appear on Spark.",
        error = state.error,
        onCta = vm::nextStep,
    ) {
        SparkTextField(
            value = state.firstName,
            onValueChange = vm::setFirstName,
            label = "First name",
        )
    }
}

// ── Step: Date of birth ───────────────────────────────────────────────────────

@Composable
private fun DobStep(state: OnboardingState, vm: OnboardingViewModel) {
    var year by remember { mutableStateOf(state.dateOfBirth?.year?.toString() ?: "") }
    var month by remember { mutableStateOf(state.dateOfBirth?.monthValue?.toString() ?: "") }
    var day by remember { mutableStateOf(state.dateOfBirth?.dayOfMonth?.toString() ?: "") }

    StepContainer(
        title = "Your date of birth",
        subtitle = "You must be 18 or older. Your age is shown on your profile.",
        error = state.error,
        onCta = {
            try {
                val dob = java.time.LocalDate.of(year.toInt(), month.toInt(), day.toInt())
                vm.setDateOfBirth(dob)
                vm.nextStep()
            } catch (e: Exception) {
                // ViewModel will catch on next step validation
                vm.nextStep()
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SparkTextField(value = day, onValueChange = { day = it },
                label = "Day", modifier = Modifier.weight(1f))
            SparkTextField(value = month, onValueChange = { month = it },
                label = "Month", modifier = Modifier.weight(1f))
            SparkTextField(value = year, onValueChange = { year = it },
                label = "Year", modifier = Modifier.weight(1.5f))
        }
    }
}

// ── Step: Gender ──────────────────────────────────────────────────────────────

@Composable
private fun GenderStep(state: OnboardingState, vm: OnboardingViewModel) {
    val options = listOf(
        Gender.MAN to "Man",
        Gender.WOMAN to "Woman",
        Gender.NON_BINARY to "Non-binary",
        Gender.OTHER to "Other",
    )
    StepContainer(title = "Your gender", error = state.error, onCta = vm::nextStep) {
        options.forEach { (gender, label) ->
            SelectionCard(
                label = label,
                selected = state.gender == gender,
                onClick = { vm.setGender(gender) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── Step: Preference ──────────────────────────────────────────────────────────

@Composable
private fun PreferenceStep(state: OnboardingState, vm: OnboardingViewModel) {
    val options = listOf(Gender.MAN to "Men", Gender.WOMAN to "Women", Gender.NON_BINARY to "Non-binary people")
    StepContainer(
        title = "Who do you want to meet?",
        subtitle = "Select all that apply.",
        error = state.error,
        onCta = vm::nextStep,
    ) {
        options.forEach { (gender, label) ->
            SelectionCard(
                label = label,
                selected = gender in state.genderPreference,
                onClick = { vm.toggleGenderPreference(gender) },
                multiSelect = true,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── Step: Intent ──────────────────────────────────────────────────────────────

@Composable
private fun IntentStep(state: OnboardingState, vm: OnboardingViewModel) {
    val options = listOf(
        RelationshipIntent.LONG_TERM to "Long-term relationship",
        RelationshipIntent.CASUAL to "Casual dating",
        RelationshipIntent.FRIENDSHIP to "Friendship",
        RelationshipIntent.UNSURE to "Still figuring it out",
    )
    StepContainer(title = "What are you looking for?", error = state.error, onCta = vm::nextStep) {
        options.forEach { (intent, label) ->
            SelectionCard(
                label = label,
                selected = state.relationshipIntent == intent,
                onClick = { vm.setRelationshipIntent(intent) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── Step: Bio ─────────────────────────────────────────────────────────────────

@Composable
private fun BioStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepContainer(
        title = "About you",
        subtitle = "Write a short bio. This is your first impression.",
        error = state.error,
        onCta = vm::nextStep,
    ) {
        SparkTextField(
            value = state.bio,
            onValueChange = vm::setBio,
            label = "Bio",
            singleLine = false,
            modifier = Modifier.height(140.dp),
            supportingText = "${state.bio.length}/500",
        )
        Spacer(Modifier.height(16.dp))
        SparkTextField(
            value = state.occupation,
            onValueChange = vm::setOccupation,
            label = "Occupation (optional)",
        )
    }
}

// ── Step: Interests ───────────────────────────────────────────────────────────

@Composable
private fun InterestsStep(state: OnboardingState, vm: OnboardingViewModel) {
    // Predefined interests — in production these come from the DB
    val interests = listOf(
        "🥾 Hiking", "📷 Photography", "🍳 Cooking", "✈️ Travelling",
        "🎵 Music", "📚 Reading", "🎮 Gaming", "🧘 Yoga",
        "🏃 Running", "☕ Coffee", "🍷 Wine", "🎬 Movies",
        "🎨 Art", "💃 Dancing", "💪 Fitness", "🏄 Surfing",
        "🚴 Cycling", "💻 Tech", "🐾 Pets", "🤝 Volunteering",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Your interests", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Pick up to 10.", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(interests) { interest ->
                val selected = interest in state.selectedInterestIds
                FilterChip(
                    selected = selected,
                    onClick = { vm.toggleInterest(interest) },
                    label = { Text(interest, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SparkButton(text = "Continue", onClick = vm::nextStep)
        Spacer(Modifier.height(24.dp))
    }
}

// ── Step: Photos ──────────────────────────────────────────────────────────────

@Composable
private fun PhotosStep(state: OnboardingState, vm: OnboardingViewModel) {
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.addPhoto(it) } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Add photos", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Your first photo is your main photo. Add up to 6.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.photoUris) { uri ->
                Box {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(3f / 4f)
                            .clip(MaterialTheme.shapes.medium),
                    )
                    IconButton(
                        onClick = { vm.removePhoto(uri) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(Icons.Filled.Close, "Remove photo",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (state.photoUris.size < 6) {
                item {
                    OutlinedCard(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.aspectRatio(3f / 4f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, "Add photo", modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SparkButton(text = "Continue", onClick = vm::nextStep, loading = state.isLoading)
        Spacer(Modifier.height(24.dp))
    }
}

// ── Step: Permissions ─────────────────────────────────────────────────────────

@Composable
private fun PermissionsStep(vm: OnboardingViewModel) {
    StepContainer(
        title = "Enable location",
        subtitle = "We use your location to show you people nearby. We never share your exact location with anyone.",
        ctaText = "Allow location",
        onCta = vm::nextStep,
    ) {
        // Location permission handled via Accompanist — simplified here
        Text("📍  Approximate location only",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("🔒  Never shared with other users",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("🚫  You can change this anytime in settings",
            style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Reusable selection card ───────────────────────────────────────────────────

@Composable
private fun SelectionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    multiSelect: Boolean = false,
) {
    val containerColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface
    val borderColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outline

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (selected) {
                if (multiSelect) {
                    Icon(Icons.Filled.Close, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

