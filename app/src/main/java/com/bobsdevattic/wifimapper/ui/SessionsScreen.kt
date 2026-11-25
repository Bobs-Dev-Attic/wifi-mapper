package com.bobsdevattic.wifimapper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.wifimapper.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onNewCapture: () -> Unit,
    onOpenSession: (Long) -> Unit,
    vm: SessionsViewModel = viewModel(),
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Mapper") },
                actions = { VersionBadge() },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewCapture,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New map") },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No maps yet.\nTap “New map” to walk a space and record WiFi strength in 3D.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(sessions, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = {
                            val geo = if (s.anchorLat != null)
                                "geo-anchored" else "no GPS anchor"
                            Text("${dateFmt.format(Date(s.createdAt))} · $geo")
                        },
                        trailingContent = {
                            IconButton(onClick = { vm.delete(s.id) }) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        },
                        modifier = Modifier.clickable { onOpenSession(s.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun VersionBadge() {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text("v${BuildConfig.VERSION_NAME}") },
        modifier = Modifier.padding(end = 8.dp),
    )
}
