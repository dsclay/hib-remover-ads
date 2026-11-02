package com.dex.adfree

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TabSwitcher(
    state: BrowserState,
    onSelect: (Tab) -> Unit,
    onClose: (Tab) -> Unit,
    onNew: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = GlassScrim) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.tabs.size} tabs",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onNew(true) }) { Text("+ Private", color = PrivateFg) }
                TextButton(onClick = { onNew(false) }) { Text("+ Tab", color = Amber) }
                TextButton(onClick = onDismiss) { Text("Done", color = Amber) }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                items(state.tabs.toList(), key = { it.id }) { tab ->
                    val shape = RoundedCornerShape(18.dp)
                    Surface(
                        shape = shape,
                        color = if (tab.isPrivate) PrivateBg.copy(alpha = 0.7f) else GlassFill,
                        border = if (tab.id == state.currentId) BorderStroke(1.5.dp, Amber) else glassBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .clickable { onSelect(tab) },
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tab.title.ifBlank { "New tab" },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    (if (tab.isPrivate) "Private · " else "") + display(tab.url),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (tab.isPrivate) PrivateFg else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onClose(tab) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close tab")
                            }
                        }
                    }
                }
            }
        }
    }
}
