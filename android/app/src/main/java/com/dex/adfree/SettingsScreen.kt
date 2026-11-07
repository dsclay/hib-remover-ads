package com.dex.adfree

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    settings: Settings,
    onClearData: () -> Unit,
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
                Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done", color = Amber) }
            }

            SwitchRow(
                title = "Block ads and trackers",
                subtitle = "Blocks ad and tracker requests",
                checked = settings.blockAds,
                onChange = settings::updateBlockAds,
            )
            SwitchRow(
                title = "HTTPS-only mode",
                subtitle = "Upgrade http links to https. Off by default: some sites have no https version.",
                checked = settings.httpsOnly,
                onChange = settings::updateHttpsOnly,
            )

            SwitchRow(
                title = "Boost downloads",
                subtitle = "Fetch big files with up to 6 parallel connections",
                checked = settings.boostDownloads,
                onChange = settings::updateBoostDownloads,
            )

            SwitchRow(
                title = "Hide ad boxes",
                subtitle = "Hides empty ad spaces on pages. Turn off if big pages feel slow. Applies to new tabs.",
                checked = settings.hideAdBoxes,
                onChange = settings::updateHideAdBoxes,
            )
            SwitchRow(
                title = "SponsorBlock",
                subtitle = "Skips sponsor and self-promo parts of YouTube videos. Applies to new tabs.",
                checked = settings.sponsorBlock,
                onChange = settings::updateSponsorBlock,
            )
            SwitchRow(
                title = "Lighter Reddit",
                subtitle = "Opens Reddit as old.reddit.com. Plainer, but fast and doesn't freeze.",
                checked = settings.lightReddit,
                onChange = settings::updateLightReddit,
            )
            SwitchRow(
                title = "Faster page loading",
                subtitle = "Lazy-load images and frames. Applies to new tabs.",
                checked = settings.fasterLoading,
                onChange = settings::updateFasterLoading,
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = GlassFill,
        border = glassBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Clear browsing data", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Cookies, site storage, cache and history",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    TextButton(onClick = onClearData) { Text("Clear", color = Amber) }
                }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = GlassFill,
        border = glassBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Amber, checkedThumbColor = MaterialTheme.colorScheme.background),
            )
        }
    }
}
