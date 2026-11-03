package com.dex.adfree

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LibraryScreen(library: Library, state: BrowserState, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val showBookmarks = state.libraryTab == 0
    val list = if (showBookmarks) library.bookmarks else library.history

    Surface(modifier = Modifier.fillMaxSize(), color = GlassScrim) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (showBookmarks) "Bookmarks" else "History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done", color = Amber) }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Segment("Bookmarks", showBookmarks) { state.libraryTab = 0 }
                Segment("History", !showBookmarks) { state.libraryTab = 1 }
                Row(Modifier.weight(1f)) {}
                if (!showBookmarks && library.history.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear all", color = Amber) }
                }
            }
            if (list.isEmpty()) {
                Text(
                    if (showBookmarks) "No bookmarks yet. Use Bookmark in the menu to save a page." else "No history yet.",
                    color = dim,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            LazyColumn(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list.toList(), key = { it.url + it.time }) { e ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = GlassFill,
                        border = glassBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpen(e.url) },
                    ) {
                        Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.title.ifBlank { e.url }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(display(e.url), style = MaterialTheme.typography.labelMedium, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!showBookmarks) {
                                    Text(
                                        DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = dim,
                                    )
                                }
                            }
                            IconButton(onClick = { if (showBookmarks) library.removeBookmark(e) else library.removeHistory(e) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = dim, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = GlassDialog,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Clear history?") },
            text = { Text("All visited pages will be removed from this list. Bookmarks are kept.") },
            confirmButton = {
                TextButton(onClick = { library.clearHistory(); confirmClear = false }) { Text("Clear", color = Amber) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = Amber) } },
        )
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) Amber.copy(alpha = 0.25f) else GlassFill,
        border = glassBorder(),
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (selected) Amber else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
