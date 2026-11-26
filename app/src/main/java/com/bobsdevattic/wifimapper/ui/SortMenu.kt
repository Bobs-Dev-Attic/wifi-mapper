package com.bobsdevattic.wifimapper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment

/**
 * "Sort: <option> ▾" drop-down plus a reverse-order toggle, for the nearby
 * Wi-Fi / Bluetooth lists.
 */
@Composable
fun <T> SortMenu(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    reversed: Boolean,
    onSelect: (T) -> Unit,
    onToggleReverse: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            AssistChip(
                onClick = { open = true },
                label = { Text("Sort: ${label(selected)}") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(label(o)) },
                        onClick = { onSelect(o); open = false },
                        trailingIcon = if (o == selected) ({ Icon(Icons.Default.Check, "Selected") }) else null,
                    )
                }
            }
        }
        IconToggleButton(checked = reversed, onCheckedChange = { onToggleReverse() }) {
            Icon(Icons.Default.SwapVert, if (reversed) "Reversed order" else "Reverse order")
        }
    }
}
