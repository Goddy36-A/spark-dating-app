package com.spark.dating.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spark.dating.core.auth.AuthRepository
import com.spark.dating.core.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

data class DiscoveryUiState(
    val profiles: List<Profile> = emptyList(),
    val isLoading: Boolean = false,
    val isEmpty: Boolean = false,
    val error: String? = null,
    val currentIndex: Int = 0,
    val matchedWith: Profile? = null,   // non-null → show match celebration
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val postgrest: Postgrest,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private var offset = 0
    private val pageSize = 20

    init { loadProfiles() }

    fun loadProfiles() {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = postgrest.rpc(
                    function = "discover_profiles",
                    parameters = buildJsonObject {
                        put("p_user_id", userId)
                        put("p_limit", pageSize)
                        put("p_offset", offset)
                    }
                ).decodeList<Profile>()

                offset += result.size
                _uiState.update { state ->
                    val updated = if (offset == pageSize) result else state.profiles + result
                    state.copy(
                        profiles = updated,
                        isLoading = false,
                        isEmpty = updated.isEmpty(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Couldn't load profiles. Pull to refresh.")
                }
            }
        }
    }

    fun like(profile: Profile, isSuperLike: Boolean = false) {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            try {
                postgrest["likes"].insert(
                    mapOf(
                        "liker_id" to userId,
                        "liked_id" to profile.id,
                        "is_super_like" to isSuperLike,
                    )
                )
                // Check if this created a match
                val isMatch = checkForMatch(userId, profile.id)
                advanceCard(if (isMatch) profile else null)
            } catch (_: Exception) {
                advanceCard()
            }
        }
    }

    fun pass(profile: Profile) {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            try {
                postgrest["passes"].insert(
                    mapOf("passer_id" to userId, "passed_id" to profile.id)
                )
            } catch (_: Exception) { /* silent */ }
            advanceCard()
        }
    }

    fun dismissMatch() = _uiState.update { it.copy(matchedWith = null) }

    fun refresh() {
        offset = 0
        loadProfiles()
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun checkForMatch(userId: String, likedId: String): Boolean {
        return try {
            val count = postgrest["matches"]
                .select {
                    filter {
                        or {
                            and {
                                eq("user1_id", minOf(userId, likedId))
                                eq("user2_id", maxOf(userId, likedId))
                            }
                        }
                    }
                    limit(1)
                }
                .decodeList<Map<String, String>>()
                .size
            count > 0
        } catch (_: Exception) { false }
    }

    private fun advanceCard(matchedProfile: Profile? = null) {
        _uiState.update { state ->
            val newIndex = state.currentIndex + 1
            // Pre-load more profiles when approaching the end
            if (newIndex >= state.profiles.size - 3) {
                loadProfiles()
            }
            state.copy(
                currentIndex = newIndex,
                matchedWith = matchedProfile,
            )
        }
    }
}
