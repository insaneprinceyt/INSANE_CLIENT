package com.ashmeet.hyperlauncher.screens.modloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashmeet.hyperlauncher.screens.settings.preferences.LauncherPreferences
import com.ashmeet.hyperlauncher.utils.horizon.HorizonReleaseCatalog
import com.ashmeet.hyperlauncher.utils.translation.translatedText

@Composable
fun RustInstallScreen(
    releases: List<HorizonReleaseCatalog.Release>,
    loading: Boolean,
    installing: Boolean,
    notice: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: (HorizonReleaseCatalog.Release) -> Unit,
    onReleaseInfo: (HorizonReleaseCatalog.Release) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var compatibleOnly by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(releases, query, compatibleOnly) {
        val search = query.trim()
        releases.filter { release ->
            (!compatibleOnly || release.bundled) &&
                (search.isEmpty() || release.name.contains(search, ignoreCase = true) ||
                    release.tag.contains(search, ignoreCase = true) ||
                    release.gameVersion.contains(search, ignoreCase = true))
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (LauncherPreferences.PREF_LAUNCHER_BACKGROUND_PATH != null) Color.Transparent
            else MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !installing) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = translatedText("Back"))
                }
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(translatedText("Rust Edition versions"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        translatedText("Starling / Horizon engine releases"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh, enabled = !loading && !installing) {
                    Icon(Icons.Rounded.Refresh, contentDescription = translatedText("Refresh releases"))
                }
            }
            if (loading || installing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        translatedText("The bundled v0.7.7 build runs Minecraft 26.3 inside INSANE. Other releases are listed for reference and are not integrated. Java Minecraft versions are separate."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(translatedText("Search versions")) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        enabled = !installing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !compatibleOnly,
                            onClick = { compatibleOnly = false },
                            label = { Text(translatedText("All releases")) }
                        )
                        FilterChip(
                            selected = compatibleOnly,
                            onClick = { compatibleOnly = true },
                            label = { Text(translatedText("In INSANE")) }
                        )
                    }
                }
                if (!notice.isNullOrBlank()) {
                    item {
                        Text(
                            translatedText(notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            translatedText("No matching Rust releases. Try another version or clear the search."),
                            modifier = Modifier.padding(vertical = 20.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                items(filtered, key = { it.id }) { release ->
                    RustReleaseItem(release, installing, onInstall, onReleaseInfo)
                }
            }
        }
    }
}

@Composable
private fun RustReleaseItem(
    release: HorizonReleaseCatalog.Release,
    installing: Boolean,
    onInstall: (HorizonReleaseCatalog.Release) -> Unit,
    onReleaseInfo: (HorizonReleaseCatalog.Release) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (release.bundled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = translatedText(if (release.bundled) "BUNDLED • RUNS IN INSANE" else if (release.prerelease) "PRE-RELEASE • NOT INTEGRATED" else "OFFICIAL RELEASE • NOT INTEGRATED"),
                style = MaterialTheme.typography.labelSmall,
                color = if (release.bundled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (release.bundled) translatedText("Minecraft 26.3 • native ARM64 engine")
                            else listOf(release.tag, release.publishedAt.take(10)).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (release.bundled) {
                    Button(onClick = { onInstall(release) }, enabled = !installing) {
                        Text(translatedText(if (installing) "Installing…" else "Install"))
                    }
                } else {
                    OutlinedButton(onClick = { onReleaseInfo(release) }, enabled = !installing) {
                        Text(translatedText("Release info"))
                    }
                }
            }
            if (release.bundled) {
                Text(
                    translatedText("Android 9+ • ARM64 • Vulkan. Installs game resources, then creates your instance. Local and Microsoft accounts; Java mods are incompatible."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
