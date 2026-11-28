package com.ashmeet.hyperlauncher.screens.modloader.fabric

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.ashmeet.hyperlauncher.components.menu.HyperDropdownTextField
import com.ashmeet.hyperlauncher.components.switch.DefaultSwitch
import com.ashmeet.hyperlauncher.screens.settings.preferences.LauncherPreferences
import com.ashmeet.hyperlauncher.utils.translation.translatedText
import net.ashmeet.hyperlauncher.R
import com.ashmeet.hyperbackend.modloaders.FabricVersion
import com.ashmeet.hyperlauncher.utils.client.InsaneClientInstaller

@Composable
fun FabriclikeInstallScreen(
    loaderName: String,
    isLoading: Boolean,
    isInstalling: Boolean,
    gameVersions: List<FabricVersion>,
    loaderVersions: List<FabricVersion>,
    supportsClient: Boolean = false,
    onInstall: (gameVersion: String, loaderVersion: String, includeClient: Boolean) -> Unit
) {
    var selectedGameVersion by remember { mutableStateOf<FabricVersion?>(null) }
    var selectedLoaderVersion by remember { mutableStateOf<FabricVersion?>(null) }
    var onlyStable by remember { mutableStateOf(true) }
    var includeClient by remember { mutableStateOf(false) }
    val clientSelected = supportsClient && includeClient

    val filteredGameVersions = remember(gameVersions, onlyStable, clientSelected) {
        gameVersions.filter {
            (!onlyStable || it.stable) &&
                (!clientSelected || it.version == InsaneClientInstaller.MINECRAFT_VERSION)
        }
    }

    val filteredLoaderVersions = remember(loaderVersions, onlyStable, clientSelected) {
        loaderVersions.filter {
            (!onlyStable || it.stable) &&
                (!clientSelected || InsaneClientInstaller.isCompatibleLoader(it.version))
        }
    }

    LaunchedEffect(filteredGameVersions) {
        if (filteredGameVersions.none { it.version == selectedGameVersion?.version }) {
            selectedGameVersion = filteredGameVersions.firstOrNull()
        }
    }

    LaunchedEffect(filteredLoaderVersions) {
        if (filteredLoaderVersions.none { it.version == selectedLoaderVersion?.version }) {
            selectedLoaderVersion = filteredLoaderVersions.firstOrNull()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (LauncherPreferences.PREF_LAUNCHER_BACKGROUND_PATH != null) Color.Transparent else MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            val configuration = LocalConfiguration.current
            val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

            val contentModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)

            val section1: @Composable () -> Unit = {
                Column(
                    modifier = if (isPortrait) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (supportsClient) {
                        Text("Installation", style = MaterialTheme.typography.titleMedium)
                        listOf(false to "Fabric", true to "Fabric + INSANE CLIENT").forEach { (client, label) ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (clientSelected == client) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = !isInstalling) { includeClient = client }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(8.dp)) {
                                    RadioButton(selected = clientSelected == client, enabled = !isInstalling,
                                        onClick = { includeClient = client })
                                    Text(label, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        if (clientSelected) {
                            Text(
                                "Client ${InsaneClientInstaller.CLIENT_VERSION} • Minecraft ${InsaneClientInstaller.MINECRAFT_VERSION}\nIncludes the client and Fabric API in a separate instance. Java 21 or newer is required.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    HyperDropdownTextField(
                        label = translatedText(stringResource(R.string.fabric_dl_game_version)),
                        items = filteredGameVersions,
                        selectedItem = selectedGameVersion,
                        itemLabel = { it.version },
                        onItemSelected = { selectedGameVersion = it }
                    )

                    HyperDropdownTextField(
                        label = translatedText(stringResource(R.string.fabric_dl_loader_version, loaderName)),
                        items = filteredLoaderVersions,
                        selectedItem = selectedLoaderVersion,
                        itemLabel = { it.version },
                        onItemSelected = { selectedLoaderVersion = it }
                    )
                }
            }

            val section2: @Composable () -> Unit = {
                Column(
                    modifier = if (isPortrait) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onlyStable = !onlyStable }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = translatedText(stringResource(R.string.fabric_dl_only_stable)),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        DefaultSwitch(
                            checked = onlyStable,
                            onCheckedChange = { onlyStable = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val gv = selectedGameVersion?.version
                            val lv = selectedLoaderVersion?.version
                            if (gv != null && lv != null) {
                                onInstall(gv, lv, clientSelected)
                            }
                        },
                        enabled = !isLoading && !isInstalling &&
                            filteredGameVersions.any { it.version == selectedGameVersion?.version } &&
                            filteredLoaderVersions.any { it.version == selectedLoaderVersion?.version },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (isInstalling) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(if (clientSelected) "Install INSANE CLIENT" else "Install $loaderName")
                        }
                    }
                    if (!isLoading && (filteredGameVersions.isEmpty() || filteredLoaderVersions.isEmpty())) {
                        Text("No compatible versions available. Check your connection and reopen this screen.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (isPortrait) {
                Column(
                    modifier = contentModifier,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    section1()
                    section2()
                }
            } else {
                Row(
                    modifier = contentModifier,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    section1()
                    section2()
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }
        }
    }
}
