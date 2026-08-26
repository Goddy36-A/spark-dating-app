package com.spark.dating.feature.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spark.dating.core.auth.AuthRepository
import com.spark.dating.core.model.Gender
import com.spark.dating.core.model.RelationshipIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

data class OnboardingState(
    // Step tracking
    val currentStep: OnboardingStep = OnboardingStep.NAME,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false,

    // Profile fields
    val firstName: String = "",
    val dateOfBirth: LocalDate? = null,
    val gender: Gender? = null,
    val genderPreference: List<Gender> = emptyList(),
    val bio: String = "",
    val occupation: String = "",
    val relationshipIntent: RelationshipIntent? = null,
    val selectedInterestIds: Set<String> = emptySet(),
    val photoUris: List<Uri> = emptyList(),
    val uploadedPhotoUrls: List<String> = emptyList(),
)

enum class OnboardingStep {
    NAME, DATE_OF_BIRTH, GENDER, PREFERENCE, INTENT, BIO, INTERESTS, PHOTOS, PERMISSIONS, DONE
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val postgrest: Postgrest,
    private val storage: Storage,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun setFirstName(name: String) = _state.update { it.copy(firstName = name.trim()) }
    fun setDateOfBirth(date: LocalDate) = _state.update { it.copy(dateOfBirth = date) }
    fun setGender(gender: Gender) = _state.update { it.copy(gender = gender) }
    fun toggleGenderPreference(gender: Gender) = _state.update { state ->
        val updated = if (gender in state.genderPreference)
            state.genderPreference - gender
        else
            state.genderPreference + gender
        state.copy(genderPreference = updated)
    }
    fun setBio(bio: String) = _state.update { it.copy(bio = bio) }
    fun setOccupation(occ: String) = _state.update { it.copy(occupation = occ) }
    fun setRelationshipIntent(intent: RelationshipIntent) = _state.update { it.copy(relationshipIntent = intent) }
    fun toggleInterest(id: String) = _state.update { state ->
        val updated = if (id in state.selectedInterestIds)
            state.selectedInterestIds - id
        else if (state.selectedInterestIds.size < 10)
            state.selectedInterestIds + id
        else state.selectedInterestIds
        state.copy(selectedInterestIds = updated)
    }
    fun addPhoto(uri: Uri) = _state.update { state ->
        if (state.photoUris.size < 6) state.copy(photoUris = state.photoUris + uri)
        else state
    }
    fun removePhoto(uri: Uri) = _state.update { it.copy(photoUris = it.photoUris - uri) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun nextStep() {
        val current = _state.value
        val validationError = validateCurrentStep(current)
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }

        val next = when (current.currentStep) {
            OnboardingStep.NAME          -> OnboardingStep.DATE_OF_BIRTH
            OnboardingStep.DATE_OF_BIRTH -> OnboardingStep.GENDER
            OnboardingStep.GENDER        -> OnboardingStep.PREFERENCE
            OnboardingStep.PREFERENCE    -> OnboardingStep.INTENT
            OnboardingStep.INTENT        -> OnboardingStep.BIO
            OnboardingStep.BIO           -> OnboardingStep.INTERESTS
            OnboardingStep.INTERESTS     -> OnboardingStep.PHOTOS
            OnboardingStep.PHOTOS        -> OnboardingStep.PERMISSIONS
            OnboardingStep.PERMISSIONS   -> OnboardingStep.DONE
            OnboardingStep.DONE          -> OnboardingStep.DONE
        }

        if (next == OnboardingStep.DONE) {
            saveProfile()
        } else {
            _state.update { it.copy(currentStep = next, error = null) }
        }
    }

    fun prevStep() {
        val prev = when (_state.value.currentStep) {
            OnboardingStep.DATE_OF_BIRTH -> OnboardingStep.NAME
            OnboardingStep.GENDER        -> OnboardingStep.DATE_OF_BIRTH
            OnboardingStep.PREFERENCE    -> OnboardingStep.GENDER
            OnboardingStep.INTENT        -> OnboardingStep.PREFERENCE
            OnboardingStep.BIO           -> OnboardingStep.INTENT
            OnboardingStep.INTERESTS     -> OnboardingStep.BIO
            OnboardingStep.PHOTOS        -> OnboardingStep.INTERESTS
            OnboardingStep.PERMISSIONS   -> OnboardingStep.PHOTOS
            else -> return
        }
        _state.update { it.copy(currentStep = prev, error = null) }
    }

    // ── Save profile to Supabase ──────────────────────────────────────────────

    private fun saveProfile() {
        val state = _state.value
        val userId = authRepository.currentUserId() ?: run {
            _state.update { it.copy(error = "Session expired. Please log in again.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // 1. Upload photos
                val photoUrls = uploadPhotos(userId, state.photoUris)

                // 2. Upsert profile
                postgrest["profiles"].upsert(
                    mapOf(
                        "id" to userId,
                        "first_name" to state.firstName,
                        "date_of_birth" to state.dateOfBirth.toString(),
                        "gender" to state.gender?.name?.lowercase(),
                        "bio" to state.bio,
                        "occupation" to state.occupation,
                        "relationship_intent" to state.relationshipIntent?.name?.lowercase(),
                        "profile_complete" to true,
                    )
                )

                // 3. Upsert preferences
                postgrest["preferences"].upsert(
                    mapOf(
                        "user_id" to userId,
                        "gender_preference" to state.genderPreference.map { it.name.lowercase() },
                    )
                )

                // 4. Insert photo records
                if (photoUrls.isNotEmpty()) {
                    val photoRecords = photoUrls.mapIndexed { index, url ->
                        mapOf(
                            "profile_id" to userId,
                            "url" to url,
                            "is_primary" to (index == 0),
                            "sort_order" to index,
                        )
                    }
                    postgrest["profile_photos"].insert(photoRecords)
                }

                // 5. Insert interests
                if (state.selectedInterestIds.isNotEmpty()) {
                    val interestRecords = state.selectedInterestIds.map { interestId ->
                        mapOf("user_id" to userId, "interest_id" to interestId)
                    }
                    postgrest["user_interests"].upsert(interestRecords)
                }

                // 6. Mark onboarding complete
                postgrest["users"].update(
                    mapOf("onboarding_complete" to true)
                ) { filter { eq("id", userId) } }

                _state.update { it.copy(isLoading = false, isComplete = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "Couldn't save your profile. Please try again.")
                }
            }
        }
    }

    private suspend fun uploadPhotos(userId: String, uris: List<Uri>): List<String> {
        return uris.mapIndexed { index, uri ->
            val bucket = storage.from("profile-photos")
            val path = "$userId/${System.currentTimeMillis()}_$index.jpg"
            // Upload returns the path; construct public URL
            bucket.upload(path, uri.toString().toByteArray(), upsert = true)
            "https://${System.getenv("SUPABASE_URL")}/storage/v1/object/public/profile-photos/$path"
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateCurrentStep(state: OnboardingState): String? = when (state.currentStep) {
        OnboardingStep.NAME -> {
            val name = state.firstName
            when {
                name.isBlank() -> "Enter your first name"
                name.length < 2 -> "Name must be at least 2 characters"
                else -> null
            }
        }
        OnboardingStep.DATE_OF_BIRTH -> {
            val dob = state.dateOfBirth
            when {
                dob == null -> "Select your date of birth"
                Period.between(dob, LocalDate.now()).years < 18 ->
                    "You must be 18 or older to use Spark"
                else -> null
            }
        }
        OnboardingStep.GENDER -> if (state.gender == null) "Select your gender" else null
        OnboardingStep.PREFERENCE -> null // optional
        OnboardingStep.INTENT -> if (state.relationshipIntent == null) "Select what you're looking for" else null
        OnboardingStep.PHOTOS -> if (state.photoUris.isEmpty()) "Add at least one photo" else null
        else -> null
    }
}
