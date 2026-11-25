package com.bobsdevattic.wifimapper.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.wifimapper.WifiMapperApp
import com.bobsdevattic.wifimapper.model.FloorInfo
import com.bobsdevattic.wifimapper.model.FloorLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FloorsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WifiMapperApp).repository
    private val sessionId = MutableStateFlow<Long?>(null)

    data class Row(val floor: FloorInfo, val points: Int)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<Row>> = sessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else combine(repo.observeFloors(id), repo.observeFloorPointCounts(id)) { floors, counts ->
            FloorLayout.topDown(floors).map { Row(it, counts[it.id] ?: 0) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun bind(id: Long) { sessionId.value = id }
    fun rename(floorId: Long, label: String) = viewModelScope.launch { repo.renameFloor(floorId, label) }
    fun move(floorId: Long, step: Int) = viewModelScope.launch {
        sessionId.value?.let { repo.moveFloor(it, floorId, step) }
    }
    fun delete(floorId: Long) = viewModelScope.launch { repo.deleteFloor(floorId) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorsScreen(
    sessionId: Long,
    onBack: () -> Unit,
    onAddFloor: () -> Unit,
    vm: FloorsViewModel = viewModel(),
) {
    LaunchedEffect(sessionId) { vm.bind(sessionId) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<FloorInfo?>(null) }
    var deleting by remember { mutableStateOf<FloorInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Floors") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddFloor,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add floor") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Top to bottom, as stacked in the 3D view. Tap a name to rename; use the " +
                    "arrows to reorder. Each floor was mapped from its own start point.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(rows, key = { _, r -> r.floor.id }) { i, r ->
                    ListItem(
                        headlineContent = { Text(r.floor.label) },
                        supportingContent = { Text("${r.points} points · tap to rename") },
                        trailingContent = {
                            Row {
                                // Top of the list = highest floor, so "up" means step +1.
                                IconButton(onClick = { vm.move(r.floor.id, +1) }, enabled = i > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Move up")
                                }
                                IconButton(onClick = { vm.move(r.floor.id, -1) }, enabled = i < rows.lastIndex) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Move down")
                                }
                                IconButton(onClick = { deleting = r.floor }) {
                                    Icon(Icons.Default.Delete, "Delete floor")
                                }
                            }
                        },
                        modifier = Modifier.clickable { renaming = r.floor },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    renaming?.let { f ->
        var text by remember(f.id) { mutableStateOf(f.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename floor") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                    label = { Text("Label (e.g. Basement, Ground, Upstairs)") })
            },
            confirmButton = {
                TextButton(onClick = { vm.rename(f.id, text.ifBlank { f.label }); renaming = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${f.label}”?") },
            text = { Text("This removes the floor and every point and wall captured on it.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(f.id); deleting = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
