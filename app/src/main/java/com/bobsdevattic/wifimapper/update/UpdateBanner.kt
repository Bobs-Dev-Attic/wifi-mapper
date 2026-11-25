package com.bobsdevattic.wifimapper.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Self-update banner. Checks GitHub Releases once on first composition and shows
 * a card only when a newer version is available, then walks through
 * download → install. Shown at the top of the Sessions screen.
 */
@Composable
fun UpdateBanner(
    modifier: Modifier = Modifier,
    vm: UpdateViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.checkInBackground() }

    val visible = state !is UpdateViewModel.State.Idle &&
        state !is UpdateViewModel.State.UpToDate &&
        state !is UpdateViewModel.State.Checking

    AnimatedVisibility(visible = visible, modifier = modifier) {
        ElevatedCard(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Update available", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::dismiss) { Icon(Icons.Default.Close, "Dismiss") }
                }

                when (val s = state) {
                    is UpdateViewModel.State.Available -> {
                        Text("Version ${s.info.versionName} is ready to install.",
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = vm::download, modifier = Modifier.fillMaxWidth()) {
                            Text("Download & install")
                        }
                    }
                    is UpdateViewModel.State.Downloading -> {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Downloading ${(s.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    is UpdateViewModel.State.ReadyToInstall -> {
                        Text("Downloaded v${s.info.versionName}.",
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (!vm.install()) context.startActivity(vm.unknownSourcesIntent())
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Install now") }
                        Text(
                            "If prompted, allow WiFi Mapper to install unknown apps, then tap Install again.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    is UpdateViewModel.State.Error -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = vm::checkInBackground) { Text("Retry") }
                    }
                    else -> {}
                }
            }
        }
    }
}
