package com.dex.adfree

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DownloadsScreen(downloader: Downloader, settings: Settings, onDismiss: () -> Unit) {
    var detail by remember { mutableStateOf<DownloadItem?>(null) }
    detail?.let { DownloadDetails(it, downloader) { detail = null } }
    Surface(modifier = Modifier.fillMaxSize(), color = GlassScrim) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Downloads", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done", color = Amber) }
            }
            SwitchRow(
                title = "Boost downloads",
                subtitle = "Fetch big files with up to ${settings.connections} parallel connections",
                checked = settings.boostDownloads,
                onChange = settings::updateBoostDownloads,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Connections", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                listOf(6, 8, 12, 16).forEach { n ->
                    Text(
                        "$n",
                        color = if (settings.connections == n) Amber else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontWeight = if (settings.connections == n) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { settings.updateConnections(n) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
            if (downloader.items.isEmpty()) {
                Text(
                    "Nothing downloaded yet",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(downloader.items, key = { it.id }) { item -> DownloadRow(item, downloader) { detail = item } }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, downloader: Downloader, onOpenDetails: () -> Unit) {
    val context = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, border = glassBorder(), modifier = Modifier.fillMaxWidth().clickable { onOpenDetails() }) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val size = { n: Long -> Formatter.formatShortFileSize(context, n) }
            when (item.status) {
                DlStatus.RUNNING -> {
                    val frac = if (item.total > 0) (item.done.toFloat() / item.total).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = frac,
                        color = Amber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, end = 8.dp),
                    )
                    val of = if (item.total > 0) " of ${size(item.total)}" else ""
                    val boost = if (item.parts > 1) " · ${item.parts}x" else ""
                    val line = if (item.paused) "Paused · ${size(item.done)}$of" else "${size(item.done)}$of · ${size(item.speed)}/s$boost"
                    Text(line, style = MaterialTheme.typography.labelMedium, color = dim)
                    Row {
                        TextButton(onClick = { downloader.setPaused(item, !item.paused) }) {
                            Text(if (item.paused) "Resume" else "Pause", color = Amber)
                        }
                        TextButton(onClick = { downloader.cancel(item) }) { Text("Cancel", color = Amber) }
                    }
                }
                DlStatus.DONE -> {
                    Text(if (item.total > 0) size(item.total) else "Done", style = MaterialTheme.typography.labelMedium, color = dim)
                    Row {
                        TextButton(onClick = { downloader.open(item) }) { Text("Open", color = Amber) }
                        TextButton(onClick = { downloader.remove(item, true) }) { Text("Delete", color = Amber) }
                    }
                }
                else -> {
                    val msg = if (item.status == DlStatus.CANCELLED) "Cancelled" else "Failed: ${item.error ?: ""}"
                    Text(msg, style = MaterialTheme.typography.labelMedium, color = dim)
                    Row { TextButton(onClick = { downloader.remove(item, false) }) { Text("Remove", color = Amber) } }
                }
            }
        }
    }
}

private fun duration(seconds: Long): String = when {
    seconds < 60 -> "$seconds sec"
    seconds < 3600 -> "${seconds / 60} min ${seconds % 60} sec"
    else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Details window: address, status, size, speed, time left and one bar segment per connection. */
@Composable
private fun DownloadDetails(item: DownloadItem, downloader: Downloader, onClose: () -> Unit) {
    val context = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val size = { n: Long -> Formatter.formatShortFileSize(context, n) }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(24.dp), color = GlassDialog, border = glassBorder(), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                val percent = if (item.total > 0) (item.done * 100 / item.total).toInt() else 0
                Text(item.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.url.isNotBlank()) Text(item.url, style = MaterialTheme.typography.labelSmall, color = dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val status = when (item.status) {
                    DlStatus.RUNNING -> if (item.paused) "Paused" else "Receiving data..."
                    DlStatus.DONE -> "Complete"
                    DlStatus.CANCELLED -> "Cancelled"
                    DlStatus.FAILED -> "Failed: ${item.error ?: ""}"
                }
                Text(status, color = Amber, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                InfoRow("File size", if (item.total > 0) size(item.total) else "Unknown")
                InfoRow("Downloaded", if (item.total > 0) "${size(item.done)} ($percent%)" else size(item.done))
                InfoRow("Transfer rate", if (item.status == DlStatus.RUNNING && !item.paused) "${size(item.speed)}/s" else "-")
                val left = if (item.status == DlStatus.RUNNING && !item.paused && item.speed > 0 && item.total > 0) duration((item.total - item.done) / item.speed) else "-"
                InfoRow("Time left", left)
                InfoRow("Connections", item.parts.toString())
                InfoRow("Resume capability", if (item.resumable) "Yes" else "No")
                LinearProgressIndicator(
                    progress = if (item.total > 0) (item.done.toFloat() / item.total).coerceIn(0f, 1f) else 0f,
                    color = Amber,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                val sizes = item.segSizes
                val snap = item.segSnap
                if (sizes.isNotEmpty()) {
                    Text("Progress by connections", style = MaterialTheme.typography.labelMedium, color = dim, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(5.dp))) {
                        sizes.forEachIndexed { i, sz ->
                            val frac = if (sz > 0) ((snap.getOrNull(i) ?: 0L).toFloat() / sz).coerceIn(0f, 1f) else 0f
                            Box(
                                Modifier
                                    .weight(sz.toFloat().coerceAtLeast(1f))
                                    .fillMaxHeight()
                                    .padding(horizontal = 1.dp)
                                    .background(Color.White.copy(alpha = 0.12f)),
                            ) {
                                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Amber))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text("N.", style = MaterialTheme.typography.labelMedium, color = dim, modifier = Modifier.weight(0.4f))
                        Text("Downloaded", style = MaterialTheme.typography.labelMedium, color = dim, modifier = Modifier.weight(1.2f))
                        Text("Info", style = MaterialTheme.typography.labelMedium, color = dim, modifier = Modifier.weight(1.4f))
                    }
                    sizes.forEachIndexed { i, sz ->
                        val got = snap.getOrNull(i) ?: 0L
                        val info = when {
                            got >= sz -> "Done"
                            item.status != DlStatus.RUNNING -> "Stopped"
                            item.paused -> "Paused"
                            else -> "Receiving data..."
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("${i + 1}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.4f))
                            Text(size(got), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f))
                            Text(info, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.4f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                    if (item.status == DlStatus.RUNNING) {
                        TextButton(onClick = { downloader.setPaused(item, !item.paused) }) { Text(if (item.paused) "Resume" else "Pause", color = Amber) }
                        TextButton(onClick = { downloader.cancel(item) }) { Text("Cancel", color = Amber) }
                    }
                    TextButton(onClick = onClose) { Text("Close", color = Amber) }
                }
            }
        }
    }
}
