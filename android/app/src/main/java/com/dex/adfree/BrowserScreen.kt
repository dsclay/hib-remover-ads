package com.dex.adfree

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

val Amber = Color(0xFFFF3B30) // Nothing-style signal red (name kept from the old amber)
val PrivateBg = Color(0xFF2A2140)
val PrivateFg = Color(0xFFB794F6)

@Composable
fun AdFreeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber,
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceVariant = Color(0xFF161616),
            onSurface = Color(0xFFE6E9ED),
        ),
        typography = androidx.compose.material3.Typography(
            titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = Dot, fontSize = 24.sp, letterSpacing = 1.sp),
            headlineSmall = androidx.compose.ui.text.TextStyle(fontFamily = Dot, fontSize = 22.sp, letterSpacing = 1.sp),
        ),
        content = content,
    )
}

/** Shown while the field is not being edited: just the site name. */
fun display(url: String): String =
    if (Urls.isHome(url)) "Home"
    else url.removePrefix("https://").removePrefix("http://").removePrefix("www.").removeSuffix("/")

@Composable
fun BrowserScreen(
    state: BrowserState,
    onGo: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onSelectTab: (Tab) -> Unit,
    onCloseTab: (Tab) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onDownloads: () -> Unit,
    downloader: Downloader,
    onClearData: () -> Unit,
    onFindVideos: () -> Unit,
    onShowBlocked: () -> Unit,
    onResetBlocked: () -> Unit,
    onPickMedia: (MediaChoice) -> Unit,
    onToggleDesktop: () -> Unit,
    onConfirmDownload: (PendingDownload) -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val colors = MaterialTheme.colorScheme
    val tab = state.current
    val host = tab?.url?.takeUnless { Urls.isHome(it) }?.let { Uri.parse(it).host }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var editing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // Follow the page (and the selected tab) unless the user is typing.
    LaunchedEffect(tab?.id, tab?.url, editing) {
        if (!editing) field = TextFieldValue(display(tab?.url.orEmpty()))
    }

    val overlayOpen = state.showSettings || state.showTabs || state.showDownloads || state.blockedReport != null ||
        state.mediaChoices != null || state.pendingDownload != null

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime)),
            ) {
                // Address pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (tab?.isPrivate == true) PrivateBg.copy(alpha = 0.8f) else GlassFill,
                    border = glassBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(44.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (tab?.isPrivate == true) {
                            Text(
                                "Private",
                                color = PrivateFg,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                        }
                        if (tab?.url?.startsWith("http://") == true) {
                            Text(
                                "Not secure",
                                color = colors.error,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                        }
                        BasicTextField(
                            value = field,
                            onValueChange = { field = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                            cursorBrush = SolidColor(Amber),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                focus.clearFocus()
                                onGo(field.text)
                            }),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged {
                                    editing = it.isFocused
                                    if (it.isFocused) {
                                        val full = tab?.url.orEmpty().takeUnless { Urls.isHome(it) }.orEmpty()
                                        field = TextFieldValue(full, TextRange(0, full.length))
                                    }
                                },
                        )
                        if (editing) {
                            Text(
                                "Paste",
                                color = Amber,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .clickable {
                                        clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                                            focus.clearFocus()
                                            onGo(it)
                                        }
                                    },
                            )
                        }
                    }
                }

                // Thin progress line (always reserves its height so the page doesn't jump)
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    val p = tab?.progress ?: 0
                    if (p in 1..99) {
                        LinearProgressIndicator(
                            progress = { p / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = if (tab?.isPrivate == true) PrivateFg else Amber,
                            trackColor = Color.Transparent,
                        )
                    }
                }

                if (tab != null) {
                    key(tab.id) {
                        AndroidView(
                            factory = { tab.webView },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            update = { v -> blurView(v, overlayOpen) },
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                // Floating bottom bar
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = GlassFill,
                    border = glassBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack, enabled = tab?.canGoBack == true) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = onForward, enabled = tab?.canGoForward == true) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                        }
                        IconButton(onClick = onReload) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${state.blocked}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = Dot, fontSize = 18.sp),
                            color = Amber,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onShowBlocked() }
                                .background(Amber.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        // Tab count button
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { state.showTabs = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .border(2.dp, colors.onSurface, RoundedCornerShape(7.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${state.tabs.size}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                        }
                    }
                }
            }

            if (menuOpen) {
                MenuSheet(
                    blocked = state.blocked,
                    desktop = tab?.desktop == true,
                    host = host,
                    blockPaused = host != null && state.settings.isPaused(host),
                    url = tab?.url,
                    onDismiss = { menuOpen = false },
                    onNewTab = onNewTab,
                    onDownloads = onDownloads,
                    onFindVideos = onFindVideos,
                    onToggleDesktop = onToggleDesktop,
                    onToggleBlock = {
                        if (host != null) {
                            state.settings.setPaused(host, !state.settings.isPaused(host))
                            onReload()
                        }
                    },
                    onReload = onReload,
                    onSettings = { state.showSettings = true },
                    onShowBlocked = onShowBlocked,
                )
            }

            if (state.showTabs) {
                TabSwitcher(
                    state = state,
                    onSelect = onSelectTab,
                    onClose = onCloseTab,
                    onNew = onNewTab,
                    onDismiss = { state.showTabs = false },
                )
            }

            if (state.showDownloads) {
                DownloadsScreen(downloader, state.settings, onDismiss = { state.showDownloads = false })
            }

            if (state.showSettings) {
                SettingsScreen(
                    settings = state.settings,
                    onClearData = onClearData,
                    onDismiss = { state.showSettings = false },
                )
            }

            // Fullscreen page video sits above everything; the WebView stays composed underneath.
            state.customView?.let { video ->
                AndroidView(
                    factory = { video },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                )
            }

            state.blockedReport?.let { r ->
                AlertDialog(
                    containerColor = GlassDialog,
                    shape = RoundedCornerShape(28.dp),
                    onDismissRequest = { state.blockedReport = null },
                    title = { Text("Blocked so far") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            if (r.categories.isEmpty()) Text("Nothing blocked yet.")
                            r.categories.forEach { (name, n) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text("$n", color = Amber, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (r.hosts.isNotEmpty()) {
                                Text(
                                    "Top sources",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                                )
                            }
                            r.hosts.forEach { (host, n) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(host, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("$n", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { state.blockedReport = null }) { Text("Close", color = Amber) } },
                    dismissButton = { TextButton(onClick = onResetBlocked) { Text("Reset", color = Amber) } },
                )
            }

            state.mediaChoices?.let { choices ->
                AlertDialog(
                    containerColor = GlassDialog,
                    shape = RoundedCornerShape(28.dp),
                    onDismissRequest = { state.mediaChoices = null },
                    title = { Text("Videos on this page") },
                    text = {
                        if (choices.isEmpty()) {
                            Text(
                                "No downloadable video found. Start playing the video, then try again. " +
                                    "Streaming sites (YouTube, HLS/DASH playlists, DRM) can't be saved.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                choices.forEach { c ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = colors.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp),
                                    ) {
                                        Row(
                                            Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(c.label, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    display(c.url),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = colors.onSurface.copy(alpha = 0.6f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Button(
                                                onClick = { onPickMedia(c) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.White),
                                            ) { Text("Download", fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { state.mediaChoices = null }) { Text("Close") } },
                )
            }

            state.pendingDownload?.let { d ->
                AlertDialog(
                    containerColor = GlassDialog,
                    shape = RoundedCornerShape(28.dp),
                    onDismissRequest = { state.pendingDownload = null },
                    title = { Text("Download file?") },
                    text = {
                        Column {
                            Text(d.fileName, fontWeight = FontWeight.SemiBold)
                            val size = if (d.length > 0) Formatter.formatShortFileSize(context, d.length) + " · " else ""
                            Text(size + display(d.url), style = MaterialTheme.typography.bodySmall)
                            if (d.risky) {
                                Text(
                                    "This file type can harm your device. Only continue if you trust this site.",
                                    color = colors.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                state.pendingDownload = null
                                onConfirmDownload(d)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.White),
                        ) { Text(if (d.risky) "Download anyway" else "Download", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { state.pendingDownload = null }) { Text("Cancel") }
                    },
                )
            }
        }
    }
}
