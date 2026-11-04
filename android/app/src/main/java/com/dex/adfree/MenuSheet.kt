package com.dex.adfree

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Grid-style menu in a glass sheet above the bottom bar. */
@Composable
fun MenuSheet(
    blocked: Int,
    host: String?,
    blockPaused: Boolean,
    url: String?,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onDismiss: () -> Unit,
    onNewTab: (Boolean) -> Unit,
    onDownloads: () -> Unit,
    onFindVideos: () -> Unit,
    onToggleBlock: () -> Unit,
    onReload: () -> Unit,
    onSettings: () -> Unit,
    onShowBlocked: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onDismiss)
    val canShare = url != null && url.isNotBlank() && !Urls.isHome(url)
    fun act(f: () -> Unit): () -> Unit = { onDismiss(); f() }

    Box(
        Modifier
            .fillMaxSize()
            .background(GlassScrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = GlassDialog,
            border = glassBorder(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(start = 12.dp, end = 12.dp, bottom = 84.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(Modifier.padding(16.dp)) {
                // Top pills: blocked counter and private tab
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Pill(onClick = act(onShowBlocked)) {
                        Text("$blocked", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Amber)
                        Text("  blocked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    Pill(onClick = act { onNewTab(true) }) {
                        Icon(Icons.Default.Lock, null, tint = PrivateFg, modifier = Modifier.size(18.dp))
                        Text("  Private", style = MaterialTheme.typography.labelLarge, color = PrivateFg)
                    }
                }
                Spacer(Modifier.height(18.dp))

                Row(Modifier.fillMaxWidth()) {
                    Cell(Icons.Default.Add, "New tab", Modifier.weight(1f), onClick = act { onNewTab(false) })
                    Cell(Icons.Default.ArrowDropDown, "Downloads", Modifier.weight(1f), onClick = act(onDownloads))
                    Cell(Icons.Default.PlayArrow, "Video", Modifier.weight(1f), onClick = act(onFindVideos))
                    Cell(Icons.Default.Refresh, "Reload", Modifier.weight(1f), onClick = act(onReload))
                    Cell(
                        Icons.Default.Check, if (blockPaused) "Block off" else "Block on", Modifier.weight(1f),
                        active = host != null && !blockPaused, enabled = host != null, onClick = act(onToggleBlock),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    Cell(Icons.Default.Star, if (bookmarked) "Saved" else "Bookmark", Modifier.weight(1f), active = bookmarked, enabled = canShare, onClick = act(onToggleBookmark))
                    Cell(Icons.Default.Favorite, "Bookmarks", Modifier.weight(1f), onClick = act(onOpenBookmarks))
                    Cell(Icons.Default.DateRange, "History", Modifier.weight(1f), onClick = act(onOpenHistory))
                    Cell(Icons.Default.Share, "Share", Modifier.weight(1f), enabled = canShare, onClick = act { share(context, url.orEmpty()) })
                    Cell(Icons.Default.Close, "Copy link", Modifier.weight(1f), enabled = canShare, onClick = act { copy(context, url.orEmpty()) })
                }

                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    BottomIcon(Icons.Default.Settings, "Settings", onClick = act(onSettings))
                    BottomIcon(Icons.Default.ArrowDropDown, "Close menu", onClick = onDismiss)
                    BottomIcon(Icons.Default.Close, "Exit", onClick = { (context as? Activity)?.finish() })
                }
            }
        }
    }
}

@Composable
private fun Pill(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = GlassFill, border = glassBorder(), modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun Cell(icon: ImageVector, label: String, modifier: Modifier, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.35f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = if (active) Amber.copy(alpha = 0.22f) else GlassFill, border = glassBorder(), modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = if (active) Amber else Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun BottomIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
    }
}

private fun share(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    runCatching { context.startActivity(Intent.createChooser(send, "Share link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun copy(context: Context, url: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("link", url))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}
