package com.dex.adfree

import android.text.format.Formatter
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
                subtitle = "Fetch big files with up to 6 parallel connections",
                checked = settings.boostDownloads,
                onChange = settings::updateBoostDownloads,
            )
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
                items(downloader.items, key = { it.id }) { item -> DownloadRow(item, downloader) }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, downloader: Downloader) {
    val context = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, border = glassBorder(), modifier = Modifier.fillMaxWidth()) {
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
