package com.ashmeet.hyperlauncher.screens.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashmeet.hyperlauncher.screens.settings.preferences.LauncherPreferences
import com.ashmeet.hyperlauncher.utils.translation.translatedText

/** World Host owns the live friends list inside Minecraft; this screen prepares that session. */
@Composable
fun FriendsScreen(
    instanceName: String,
    minecraftVersion: String?,
    accountName: String?,
    installedVersion: String?,
    availableVersion: String?,
    busy: Boolean,
    status: String?,
    canInstall: Boolean,
    canLaunch: Boolean,
    onBack: () -> Unit,
    onChooseInstance: () -> Unit,
    onChooseAccount: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onManageMods: () -> Unit
) {
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
                IconButton(onClick = onBack, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, translatedText("Back"))
                }
                Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(translatedText("Friends & Worlds"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        translatedText("Play together with World Host"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh, enabled = !busy) {
                    Icon(Icons.Rounded.Refresh, translatedText("Check setup again"))
                }
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val twoColumns = maxWidth >= 760.dp
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val setup: @Composable () -> Unit = {
                        FriendsCard {
                            Text(translatedText("Your setup"), style = MaterialTheme.typography.titleMedium)
                            SelectionRow(
                                title = translatedText("Instance"),
                                value = instanceName,
                                detail = minecraftVersion?.let { "Minecraft $it" },
                                enabled = !busy,
                                onClick = onChooseInstance
                            )
                            SelectionRow(
                                title = translatedText("Account"),
                                value = accountName ?: translatedText("Choose a Microsoft account"),
                                detail = null,
                                enabled = !busy,
                                onClick = onChooseAccount
                            )
                            Text(
                                translatedText("Requires a Microsoft account that owns Java Edition and a compatible Fabric instance. Rust Edition, local accounts and Ely.by are not supported by World Host."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FriendsCard {
                            Text(translatedText("World Host"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                translatedText("Add friends, see shared worlds and join over the internet from Minecraft's Friends tab."),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                translatedText("Experimental on Android. Hosting and joining still need testing on your devices."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (installedVersion != null) {
                                Text(
                                    translatedText("Detected version") + ": " + installedVersion,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (availableVersion != null) {
                                Text(
                                    translatedText("Compatible version") + ": " + availableVersion,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (!status.isNullOrBlank()) {
                                Text(
                                    translatedText(status),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (installedVersion == null) {
                                Button(onClick = onInstall, enabled = canInstall && !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(translatedText(if (busy) "Please wait…" else "Install World Host"))
                                }
                            }
                            Button(onClick = onLaunch, enabled = canLaunch && !busy, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(translatedText("Launch Minecraft"))
                            }
                            TextButton(onClick = onManageMods, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(translatedText("Manage installed mods"))
                            }
                        }
                    }
                    val guide: @Composable () -> Unit = {
                        FriendsCard {
                            Text(translatedText("Start playing together"), style = MaterialTheme.typography.titleMedium)
                            GuideStep("1", "Add each other", "Launch Minecraft → Multiplayer → Friends → Add Friend. Both players must add each other.")
                            GuideStep("2", "Share your world", "Open a singleplayer world, pause and choose Open to Friends. Keep Minecraft and the world running.")
                            GuideStep("3", "Join a friend", "Your friend opens Multiplayer → Friends and chooses your shared world. Use the same Minecraft version and matching required mods.")
                            Text(
                                translatedText("For friends-only access, enable Whitelist Joins in World Host settings and keep security set to Secure."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        FriendsCard {
                            Text(translatedText("Share a world address"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                translatedText("The host can run /worldhost ip in Minecraft and send the address to a friend. The friend uses Multiplayer → Direct Connection. Both need the same game version, matching required mods and Microsoft Java accounts."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (twoColumns) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { setup() }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { guide() }
                        }
                    } else {
                        setup()
                        guide()
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SelectionRow(title: String, value: String, detail: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick, enabled = enabled) { Text(translatedText("Change")) }
    }
}

@Composable
private fun GuideStep(number: String, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(translatedText(title), style = MaterialTheme.typography.titleSmall)
            Text(translatedText(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
