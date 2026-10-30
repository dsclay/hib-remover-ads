package com.dex.adfree

import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val Amber = Color(0xFFF3B23C)

@Composable
fun AdFreeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber,
            background = Color(0xFF111418),
            surface = Color(0xFF111418),
            surfaceVariant = Color(0xFF1E2329),
            onSurface = Color(0xFFE6E9ED),
        ),
        content = content,
    )
}

/** Shown while the field is not being edited: just the site name. */
private fun display(url: String): String =
    Uri.parse(url).host?.removePrefix("www.") ?: url

@Composable
fun BrowserScreen(
    state: BrowserState,
    webView: WebView,
    onGo: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val colors = MaterialTheme.colorScheme
    var field by remember { mutableStateOf(TextFieldValue(display(state.url))) }
    var editing by remember { mutableStateOf(false) }

    // Follow the page unless the user is typing.
    LaunchedEffect(state.url, editing) {
        if (!editing) field = TextFieldValue(display(state.url))
    }

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
                    color = colors.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(44.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                                    if (it.isFocused) field = TextFieldValue(state.url, TextRange(0, state.url.length))
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
                    if (state.progress in 1..99) {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = Amber,
                            trackColor = Color.Transparent,
                        )
                    }
                }

                AndroidView(
                    factory = { webView },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                )

                // Floating bottom bar
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack, enabled = state.canGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = onForward, enabled = state.canGoForward) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                        }
                        IconButton(onClick = onReload) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload")
                        }
                        IconButton(onClick = onHome) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${state.blocked} blocked",
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .clip(CircleShape)
                                .background(Amber.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
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
        }
    }
}
