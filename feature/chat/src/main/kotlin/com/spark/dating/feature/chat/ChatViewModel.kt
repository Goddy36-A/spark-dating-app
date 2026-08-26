package com.spark.dating.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spark.dating.core.auth.AuthRepository
import com.spark.dating.core.model.Message
import com.spark.dating.core.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val partner: Profile? = null,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val isTyping: Boolean = false,       // remote user typing indicator
    val messageInput: String = "",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val postgrest: Postgrest,
    private val realtime: Realtime,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var channel: RealtimeChannel? = null
    private var page = 0
    private val pageSize = 40

    init {
        loadMessages()
        loadPartnerProfile()
        subscribeToRealtime()
    }

    fun setMessageInput(text: String) = _uiState.update { it.copy(messageInput = text) }

    fun sendMessage() {
        val text = _uiState.value.messageInput.trim()
        if (text.isBlank()) return
        val userId = authRepository.currentUserId() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, messageInput = "") }
            try {
                postgrest["messages"].insert(
                    mapOf(
                        "conversation_id" to conversationId,
                        "sender_id" to userId,
                        "content" to text,
                        "message_type" to "text",
                    )
                )
                // Realtime will deliver the message back via subscription
            } catch (e: Exception) {
                // Restore the text if sending failed
                _uiState.update { it.copy(
                    isSending = false,
                    messageInput = text,
                    error = "Message failed to send. Tap to retry.",
                )}
            }
        }
    }

    fun loadMoreMessages() {
        page++
        loadMessages(append = true)
    }

    fun markAsRead() {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            try {
                postgrest["conversation_members"].update(
                    mapOf("last_read_at" to java.time.Instant.now().toString())
                ) {
                    filter {
                        eq("conversation_id", conversationId)
                        eq("user_id", userId)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun loadMessages(append: Boolean = false) {
        if (!append) page = 0
        viewModelScope.launch {
            if (!append) _uiState.update { it.copy(isLoading = true) }
            try {
                val messages = postgrest["messages"]
                    .select {
                        filter { eq("conversation_id", conversationId) }
                        order("created_at", order = Order.DESCENDING)
                        limit(pageSize.toLong())
                        range(
                            from = (page * pageSize).toLong(),
                            to = (page * pageSize + pageSize - 1).toLong(),
                        )
                    }
                    .decodeList<Message>()
                    .reversed()     // show oldest at top

                _uiState.update { state ->
                    val updated = if (append) messages + state.messages else messages
                    state.copy(messages = updated, isLoading = false)
                }
                markAsRead()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Couldn't load messages")
                }
            }
        }
    }

    private fun loadPartnerProfile() {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            try {
                // Find the other member of this conversation
                val members = postgrest["conversation_members"]
                    .select {
                        filter {
                            eq("conversation_id", conversationId)
                            neq("user_id", userId)
                        }
                        limit(1)
                    }
                    .decodeList<Map<String, String>>()

                val partnerId = members.firstOrNull()?.get("user_id") ?: return@launch

                val partner = postgrest["profiles"]
                    .select {
                        filter { eq("id", partnerId) }
                        limit(1)
                        single()
                    }
                    .decodeAs<Profile>()

                _uiState.update { it.copy(partner = partner) }
            } catch (_: Exception) {}
        }
    }

    private fun subscribeToRealtime() {
        viewModelScope.launch {
            try {
                channel = realtime.channel("chat:$conversationId")

                // Listen for new messages in this conversation
                channel!!.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                    filter = "conversation_id=eq.$conversationId"
                }.onEach { change ->
                    // Decode the new message and append it
                    val newMessage = try {
                        kotlinx.serialization.json.Json.decodeFromString<Message>(
                            change.record.toString()
                        )
                    } catch (_: Exception) { return@onEach }

                    _uiState.update { state ->
                        // Avoid duplicates
                        if (state.messages.any { it.id == newMessage.id }) return@update state
                        state.copy(
                            messages = state.messages + newMessage,
                            isSending = false,
                        )
                    }
                    markAsRead()
                }.launchIn(viewModelScope)

                channel!!.subscribe()
            } catch (e: Exception) {
                // Realtime unavailable — polling fallback would go here
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                channel?.unsubscribe()
                realtime.removeChannel(channel!!)
            } catch (_: Exception) {}
        }
    }
}
