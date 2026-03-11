package com.fcm.nanochat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.ChatSession

@Composable
internal fun SessionsDrawer(
    state: ChatScreenState,
    onCreateSession: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onPinSession: (Long, Boolean) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onRenameSession: (Long, String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val pinned = state.sessions.filter { it.isPinned }
    val recents = state.sessions.filterNot { it.isPinned }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "NanoChat",
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = onCreateSession) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New chat")
        }

        if (pinned.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pinned",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            pinned.forEach { session ->
                SessionRow(
                    session = session,
                    isSelected = state.selectedSessionId == session.id,
                    onSelectSession = onSelectSession,
                    onPinSession = onPinSession,
                    onDeleteSession = onDeleteSession,
                    onRenameSession = onRenameSession
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Recents",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (recents.isEmpty()) {
            Text(
                text = "No recent sessions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(recents, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        isSelected = state.selectedSessionId == session.id,
                        onSelectSession = onSelectSession,
                        onPinSession = onPinSession,
                        onDeleteSession = onDeleteSession,
                        onRenameSession = onRenameSession
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings")
        }
    }
}

@Composable
private fun SessionRow(
    session: ChatSession,
    isSelected: Boolean,
    onSelectSession: (Long) -> Unit,
    onPinSession: (Long, Boolean) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onRenameSession: (Long, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.surface
            )
            .clickable { onSelectSession(session.id) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = session.title,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box {
            var menuExpanded by remember { mutableStateOf(false) }

            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Session actions")
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (session.isPinned) "Unpin chat" else "Pin chat") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = if (session.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alpha(if (session.isPinned) 1f else 0.75f)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onPinSession(session.id, !session.isPinned)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename chat") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRenameSession(session.id, session.title)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete chat") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDeleteSession(session.id)
                    }
                )
            }
        }
    }
}

@Composable
internal fun SessionsRail(
    state: ChatScreenState,
    modifier: Modifier = Modifier,
    onCreateSession: () -> Unit,
    onSelectSession: () -> Unit
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chats", fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onCreateSession) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSelectSession, modifier = Modifier.fillMaxWidth()) {
            Text("Open Sessions")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "${state.sessions.size} sessions",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
