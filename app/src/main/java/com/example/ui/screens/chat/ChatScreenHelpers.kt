package com.example.ui.screens.chat

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChatConversationEntity
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.ui.theme.PersianTurquoise

@Composable
fun PluginOptionRow(
    emoji: String,
    title: String,
    description: String,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) PersianTurquoise.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isConnected) {
                Icon(Icons.Default.CheckCircle, null, tint = PersianTurquoise, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ModelOptionCard(
    title: String,
    description: String,
    tag: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PersianTurquoise.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Surface(
                    color = if (isSelected) PersianTurquoise else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ModernChatInputBar(
    inputText: String,
    onTextChanged: (String) -> Unit,
    activeModelLabel: String,
    isLoading: Boolean,
    isListening: Boolean,
    hasImages: Boolean,
    hasFiles: Boolean,
    onSendMessage: () -> Unit,
    onAttachClick: () -> Unit,
    onAttachFileClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onSelectModelClick: () -> Unit,
    onPluginsClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onAttachClick) { Icon(Icons.Default.PhotoLibrary, "تصویر") }
            IconButton(onClick = onAttachFileClick) { Icon(Icons.Default.AttachFile, "فایل") }
            IconButton(onClick = onVoiceClick) { Icon(Icons.Default.Mic, "صدا", tint = if (isListening) PersianTurquoise else MaterialTheme.colorScheme.onSurface) }
            IconButton(onClick = onSelectModelClick) { Text(activeModelLabel, style = MaterialTheme.typography.labelSmall, color = PersianTurquoise) }
            IconButton(onClick = onPluginsClick) { Icon(Icons.Default.Settings, "پلاگین") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("پیام خود را بنویسید...") },
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendMessage,
                enabled = !isLoading && (inputText.isNotBlank() || hasImages || hasFiles)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "ارسال", tint = PersianTurquoise)
            }
        }
    }
}

@Composable
fun TwoWayVoiceModal(
    onDismiss: () -> Unit,
    onSendMessage: (String, (String) -> Unit) -> Unit,
    ttsEngine: TextToSpeech?
) {
    // Simplified voice modal
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مکالمه صوتی") },
        text = { Text("قابلیت مکالمه صوتی دوطرفه") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}

@Composable
fun ChatDrawerContent(
    conversations: List<ChatConversationEntity>,
    currentId: String?,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenTwoWayVoice: () -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("گفتگوها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onNewChat, modifier = Modifier.fillMaxWidth()) {
            Text("گفتگوی جدید")
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn {
            items(conversations) { conv ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectConversation(conv.id) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conv.title.ifBlank { "بدون عنوان" },
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    IconButton(onClick = { onDeleteConversation(conv.id) }) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onSaveToLibrary: () -> Unit
) {
    val isUser = message.sender == MessageSender.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) PersianTurquoise.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = message.text)
                if (!isUser) {
                    Row {
                        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onSpeak, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyChatGreeting(onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("FOKO AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PersianTurquoise)
        Spacer(modifier = Modifier.height(8.dp))
        Text("چطور می‌تونم کمکت کنم؟", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))
        listOf("یک شعر بنویس", "کد پایتون بنویس", "ترجمه کن").forEach { suggestion ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSuggestionClick(suggestion) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(text = suggestion, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
