package com.spark.dating.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spark.dating.core.auth.AuthRepository
import com.spark.dating.core.model.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val postgrest: Postgrest,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init { loadConversations() }

    fun loadConversations() {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Fetch conversation IDs where current user is a member
                val memberRows = postgrest["conversation_members"]
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<Map<String, String>>()

                val conversationIds = memberRows.mapNotNull { it["conversation_id"] }
                if (conversationIds.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, conversations = emptyList()) }
                    return@launch
                }

                // Fetch conversations with last message
                val conversations = postgrest["conversations"]
                    .select {
                        filter { isIn("id", conversationIds) }
                        order("updated_at", Order.DESCENDING)
                    }
                    .decodeList<Conversation>()

                _uiState.update { it.copy(isLoading = false, conversations = conversations) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Couldn't load messages") }
            }
        }
    }
}
