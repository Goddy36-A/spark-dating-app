package com.spark.dating.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spark.dating.core.model.Conversation
import com.spark.dating.core.model.Message
import com.spark.dating.core.ui.components.ProfileAvatar
import com.spark.dating.core.ui.components.SparkLoading
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── ChatListScreen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChat: (String) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Messages") })
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> SparkLoading(Modifier.padding(innerPadding))
            uiState.conversations.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💬", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(16.dp))
                        Text("No messages yet", style = MaterialTheme.typography.titleMedium)
                        Text("Match with someone to start chatting",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(innerPadding),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(uiState.conversations, key = { it.id }) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = { onChat(conversation.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                conversation.partner?.firstName ?: "Match",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Text(
                conversation.lastMessage?.content ?: "Say hello!",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            ProfileAvatar(
                imageUrl = conversation.partner?.photos?.find { it.isPrimary }?.url,
                size = 52,
                contentDescription = conversation.partner?.firstName,
            )
        },
        trailingContent = {
            if (conversation.unreadCount > 0) {
                Badge { Text(conversation.unreadCount.toString()) }
            }
        },
        modifier = androidx.compose.ui.Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 72.dp))
}

// ── ChatScreen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onProfile: (String) -> Unit,
    onReport: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    // Scroll to bottom when new message arrives
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(uiState.messages.lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            uiState.partner?.let { onProfile(it.id) }
                        },
                    ) {
                        ProfileAvatar(
                            imageUrl = uiState.partner?.photos?.find { it.isPrimary }?.url,
                            size = 36,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(uiState.partner?.firstName ?: "Chat")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        uiState.partner?.let { partner ->
                            DropdownMenuItem(
                                text = { Text("View profile") },
                                onClick = { showMenu = false; onProfile(partner.id) },
                            )
                            DropdownMenuItem(
                                text = { Text("Report", color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; onReport(partner.id) },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            MessageInputBar(
                value = uiState.messageInput,
                onValueChange = viewModel::setMessageInput,
                onSend = viewModel::sendMessage,
                isSending = uiState.isSending,
            )
        },
    ) { innerPadding ->
        val currentUserId = remember { /* injected from VM */ "" }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    isMine = message.senderId == uiState.partner?.id?.let { "" } ?: "",
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean) {
    val bubbleColor = if (isMine)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant

    val textColor = if (isMine)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    val shape = if (isMine)
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    else
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (message.isDeleted) "Message deleted" else message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isDeleted) textColor.copy(alpha = 0.5f) else textColor,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatMessageTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Message…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isSending,
            ) {
                if (isSending) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send message")
                }
            }
        }
    }
}

private fun formatMessageTime(isoString: String): String = try {
    val instant = Instant.parse(isoString)
    DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(instant)
} catch (_: Exception) { "" }

