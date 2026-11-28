package com.ashmeet.hyperlauncher.fragments.installer

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.Fragment
import com.ashmeet.hyperlauncher.components.HyperAlertDialog
import com.ashmeet.hyperlauncher.components.dialog.DependencyDialog
import com.ashmeet.hyperlauncher.components.dialog.MissingDependency
import com.ashmeet.hyperlauncher.utils.ModMetadataReader
import com.ashmeet.hyperlauncher.utils.OnlineServiceCredentials
import com.ashmeet.hyperlauncher.utils.ServiceKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import com.ashmeet.hyperlauncher.utils.translation.Translator
import com.ashmeet.hyperlauncher.utils.translation.translatedText
import com.ashmeet.hyperlauncher.screens.installer.ContentInstallerScreen
import com.ashmeet.hyperlauncher.utils.installer.ContentInstallerType
import com.ashmeet.hyperlauncher.utils.installer.ContentSource
import com.ashmeet.hyperlauncher.utils.installer.ModrinthProject
import com.ashmeet.hyperlauncher.utils.installer.ModrinthVersion
import com.ashmeet.hyperlauncher.utils.installer.ModDependency
import com.ashmeet.hyperlauncher.utils.installer.isMcVersionCompatible
import com.ashmeet.hyperlauncher.utils.installer.isLoaderCompatible
import com.ashmeet.hyperlauncher.utils.installer.cleanMcVersion
import com.ashmeet.hyperlauncher.theme.PojavTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ashmeet.hyperlauncher.R
import com.ashmeet.hyperlauncher.activity.PojavApplication
import com.ashmeet.hyperlauncher.utils.HyperUtils
import com.ashmeet.hyperbackend.instances.Instance
import com.ashmeet.hyperbackend.instances.Instances
import com.ashmeet.hyperbackend.instances.MMCInstanceImporter
import com.ashmeet.hyperbackend.modloaders.modpacks.api.CommonApi
import com.ashmeet.hyperbackend.modloaders.modpacks.api.CurseForgeService
import com.ashmeet.hyperbackend.modloaders.modpacks.api.CurseforgeApi
import com.ashmeet.hyperbackend.modloaders.modpacks.api.ModpackApi
import com.ashmeet.hyperbackend.modloaders.modpacks.api.ModrinthApi
import com.ashmeet.hyperbackend.modloaders.modpacks.api.ModrinthService
import com.ashmeet.hyperbackend.modloaders.modpacks.models.ModItem
import com.ashmeet.hyperbackend.progresskeeper.ProgressKeeper
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.time.Duration.Companion.milliseconds

class ContentInstallerFragment : Fragment() {

    override fun onResume() {
        super.onResume()
        CurseForgeService.init(OnlineServiceCredentials.curseForgeKey(requireContext()))
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @SuppressLint("LocalContextGetResourceValueCall")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                PojavTheme {
                    val scope = rememberCoroutineScope()
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        CurseForgeService.init(OnlineServiceCredentials.curseForgeKey(context))
                    }

                    var projects by remember { mutableStateOf<List<ModrinthProject>>(emptyList()) }
                    var isSearching by remember { mutableStateOf(false) }
                    var contentError by remember { mutableStateOf<String?>(null) }
                    var isProjectLoading by remember { mutableStateOf(false) }
                    var viewingProject by remember { mutableStateOf<ModrinthProject?>(null) }

                    val initialType = remember {
                        arguments?.getString("type")?.let { typeStr ->
                            ContentInstallerType.entries.find { it.name == typeStr }
                        } ?: ContentInstallerType.MODS
                    }

                    var selectedType by remember { mutableStateOf(initialType) }
                    var selectedSource by remember {
                        mutableStateOf(if (initialType == ContentInstallerType.WORLDS) ContentSource.CURSEFORGE else ContentSource.MODRINTH)
                    }
                    var projectVersions by remember {
                        mutableStateOf<List<ModrinthVersion>>(
                            emptyList()
                        )
                    }
                    var selectedProjectMCVersion by remember { mutableStateOf<String?>(null) }
                    var searchQuery by remember { mutableStateOf("") }

                    var missingDependencies by remember {
                        mutableStateOf<List<MissingDependency>>(
                            emptyList()
                        )
                    }
                    var pendingIncompatibleVersion by remember { mutableStateOf<ModrinthVersion?>(null) }
                    var pendingDownloadVersion by remember {
                        mutableStateOf<Pair<ModrinthVersion, ContentInstallerType>?>(
                            null
                        )
                    }
                    var isDependencyChecking by remember { mutableStateOf(false) }

                    val instance = remember { Instances.loadSelectedInstance() }
                    val instanceVersion = remember(instance) {
                        instance?.let {
                            val id = it.versionId
                            if (id == "latest_release" || id == "latest_snapshot") return@let null

                            val v = try {
                                HyperUtils.getVersionInfo(id)
                            } catch (_: Exception) {
                                null
                            }
                            if (v?.inheritsFrom != null) return@let cleanMcVersion(v.inheritsFrom)

                            cleanMcVersion(id)
                        }
                    }

                    var selectedVersion by remember { mutableStateOf<String?>(null) }
                    var selectedLoader by remember { mutableStateOf<String?>(null) }

                    val instanceLoader = remember(instance) {
                        instance?.let {
                            val id = it.versionId
                            val v = try {
                                HyperUtils.getVersionInfo(id)
                            } catch (_: Exception) {
                                null
                            }
                            val searchStr = (id + (v?.inheritsFrom ?: "")).lowercase()

                            when {
                                searchStr.contains("neoforge") -> "neoforge"
                                searchStr.contains("fabric") -> "fabric"
                                searchStr.contains("forge") -> "forge"
                                searchStr.contains("quilt") -> "quilt"
                                searchStr.contains("optifine") -> "optifine"
                                else -> null
                            }
                        }
                    }

                    var refreshTrigger by remember { mutableIntStateOf(0) }

                    val translationTrigger by Translator.refreshTrigger
                    LaunchedEffect(translationTrigger) {
                        if (translationTrigger > 0) {
                            refreshTrigger++
                        }
                    }

                    LaunchedEffect(
                        selectedType,
                        selectedVersion,
                        selectedLoader,
                        searchQuery,
                        selectedSource,
                        refreshTrigger,
                        viewingProject?.id
                    ) {
                        // A catalog request must not overwrite a project's loading/error state.
                        if (viewingProject != null) return@LaunchedEffect
                        if (searchQuery.isNotEmpty() && refreshTrigger == 0) {
                            delay(500.milliseconds)
                        }

                        isSearching = true
                        contentError = null
                        try {
                            projects = if (selectedSource == ContentSource.MODRINTH) {
                                ModrinthService.search(
                                    searchQuery,
                                    selectedType,
                                    selectedVersion?.let(::cleanMcVersion),
                                    selectedLoader
                                )
                            } else {
                                CurseForgeService.search(
                                    searchQuery,
                                    selectedType,
                                    selectedVersion?.let(::cleanMcVersion),
                                    selectedLoader.takeIf { selectedType == ContentInstallerType.MODS || selectedType == ContentInstallerType.MODPACKS }
                                )
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            projects = emptyList()
                            contentError = sourceFailureMessage(selectedSource)
                        } finally {
                            isSearching = false
                        }
                    }

                    LaunchedEffect(viewingProject?.id, refreshTrigger) {
                        val projectId = viewingProject?.id ?: run {
                            projectVersions = emptyList()
                            selectedProjectMCVersion = null
                            isProjectLoading = false
                            return@LaunchedEffect
                        }

                        projectVersions = emptyList()
                        selectedProjectMCVersion = null
                        isProjectLoading = true
                        contentError = null

                        try {
                            val (details, versions) = coroutineScope {
                                val detailsDeferred = async(Dispatchers.IO) {
                                    if (selectedSource == ContentSource.MODRINTH) {
                                        ModrinthService.getProjectDetails(projectId)
                                    } else {
                                        CurseForgeService.getProjectDetails(projectId)
                                    }
                                }
                                val versionsDeferred = async(Dispatchers.IO) {
                                    if (selectedSource == ContentSource.MODRINTH) {
                                        ModrinthService.getProjectVersions(projectId)
                                    } else {
                                        CurseForgeService.getProjectVersions(projectId)
                                    }
                                }
                                detailsDeferred.await() to versionsDeferred.await()
                            }

                            withContext(Dispatchers.Main) {
                                viewingProject = viewingProject?.copy(
                                    fullDescription = details?.fullDescription,
                                    gallery = details?.gallery ?: emptyList()
                                )
                                projectVersions = versions
                                isProjectLoading = false
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            contentError = sourceFailureMessage(selectedSource)
                        } finally {
                            isProjectLoading = false
                        }
                    }

                    val visibleProjectVersions = remember(projectVersions, selectedVersion, selectedLoader, selectedType) {
                        val versionFilter = selectedVersion
                        projectVersions.filter { version ->
                            (versionFilter == null || version.gameVersions.any { isMcVersionCompatible(versionFilter, it) }) &&
                                (selectedType != ContentInstallerType.MODS && selectedType != ContentInstallerType.MODPACKS ||
                                    isLoaderCompatible(selectedLoader, version.loaders))
                        }
                    }
                    val availableProjectMCVersions = remember(visibleProjectVersions) {
                        visibleProjectVersions.asSequence().flatMap { it.gameVersions }
                            .map { cleanMcVersion(it) }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .sortedDescending().toList()
                    }

                    val installVersion: (ModrinthVersion) -> Unit = installVersion@{ version ->
                            if (instance == null) {
                                Toast.makeText(
                                    requireContext(),
                                    "No instance selected",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@installVersion
                            }
                            scope.launch(Dispatchers.IO) {
                                val progressKey = "download_content"
                                if (selectedType == ContentInstallerType.MODPACKS) {

                                    try {
                                        val modpackApi =
                                            if (selectedSource == ContentSource.MODRINTH) {
                                                ModrinthApi()
                                            } else {
                                                CurseforgeApi(OnlineServiceCredentials.curseForgeKey(requireContext()))
                                            }

                                        val modItem = ModItem(
                                            if (selectedSource == ContentSource.MODRINTH) CommonApi.PACK_MODRINTH.toInt() else CommonApi.PACK_CURSEFORGE.toInt(),
                                            true,
                                            viewingProject?.id,
                                            viewingProject?.title,
                                            viewingProject?.description,
                                            viewingProject?.iconUrl
                                        )
                                        val modDetail = modpackApi.getModDetails(modItem)
                                        val selectedVersionIndex =
                                            modDetail.versionUrls.indexOfFirst {
                                                it == version.downloadUrl || it.contains(version.id)
                                            }

                                        if (selectedVersionIndex != -1) {
                                            withContext(Dispatchers.Main) {
                                                modpackApi.handleModpackInstallation(
                                                    requireContext(),
                                                    modDetail,
                                                    selectedVersionIndex
                                                )
                                            }
                                        } else {
                                            performDirectDownload(
                                                version,
                                                instance,
                                                selectedType,
                                                progressKey
                                            )
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                requireContext(),
                                                "Modpack install failed: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                } else if (selectedType == ContentInstallerType.WORLDS) {
                                    installWorld(version, instance, progressKey)
                                } else if (selectedType == ContentInstallerType.MODS) {
                                    isDependencyChecking = true
                                    val missing = try {
                                        checkDependencies(version, instance, selectedSource)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, sourceFailureMessage(selectedSource), Toast.LENGTH_LONG).show()
                                        }
                                        return@launch
                                    } finally {
                                        isDependencyChecking = false
                                    }

                                    if (missing.isNotEmpty()) {
                                        missingDependencies = missing
                                        pendingDownloadVersion = version to selectedType
                                    } else {
                                        performDirectDownload(
                                            version,
                                            instance,
                                            selectedType,
                                            progressKey
                                        )
                                    }
                                } else {
                                    performDirectDownload(
                                        version,
                                        instance,
                                        selectedType,
                                        progressKey
                                    )
                                }
                            }
                    }

                    ContentInstallerScreen(
                        onBack = { HyperUtils.removeCurrentFragment(requireActivity()) },
                        onSearch = { query, type, version, loader, source ->
                            if (selectedType != type || selectedSource != source) {
                                projects = emptyList()
                            }

                            searchQuery = query
                            selectedType = type
                            selectedVersion = version
                            selectedLoader = loader
                            selectedSource = source
                        },
                        onProjectClick = { project ->
                            viewingProject = project
                        },
                        onVersionClick = { version ->
                            val mismatch = selectedType != ContentInstallerType.MODPACKS && (
                                (!instanceVersion.isNullOrEmpty() && version.gameVersions.none { isMcVersionCompatible(instanceVersion, it) }) ||
                                    (selectedType == ContentInstallerType.MODS &&
                                        (instanceLoader == null || !isLoaderCompatible(instanceLoader, version.loaders))))
                            if (mismatch) pendingIncompatibleVersion = version else installVersion(version)
                        },
                        projects = projects,
                        errorMessage = contentError,
                        isLoading = if (viewingProject != null) isProjectLoading else isSearching,
                        selectedVersion = selectedVersion,
                        selectedLoader = selectedLoader,
                        selectedSource = selectedSource,
                        instanceVersion = instanceVersion,
                        instanceLoader = instanceLoader,
                        viewingProject = viewingProject,
                        selectedType = selectedType,
                        projectVersions = visibleProjectVersions,
                        availableProjectMCVersions = availableProjectMCVersions,
                        selectedProjectMCVersion = selectedProjectMCVersion,
                        onProjectMCVersionClick = {
                            selectedProjectMCVersion = it.ifEmpty { null }
                        },
                        onBackToProjects = {
                            contentError = null
                            viewingProject = null
                            projectVersions = emptyList()
                            selectedProjectMCVersion = null
                        },
                        onRefresh = { refreshTrigger++ },
                        onImportContent = { type ->
                            mPendingImportType = type
                            importLauncher.launch("*/*")
                        }
                    )

                    pendingIncompatibleVersion?.let { version ->
                        HyperAlertDialog(
                            onDismissRequest = { pendingIncompatibleVersion = null },
                            title = { Text(translatedText("Check compatibility")) },
                            text = {
                                Text("This file does not declare support for the selected instance (${instanceVersion ?: "unknown version"}, ${instanceLoader ?: "Vanilla"}). It may fail to load. Choose a compatible release, or install it anyway.")
                            },
                            confirmText = "Install anyway",
                            onConfirm = {
                                pendingIncompatibleVersion = null
                                installVersion(version)
                            },
                            dismissText = "Cancel",
                            onDismiss = { pendingIncompatibleVersion = null }
                        )
                    }

                    if (missingDependencies.isNotEmpty()) {
                        DependencyDialog(
                            dependencies = missingDependencies,
                            onDismiss = {
                                scope.launch {
                                    pendingDownloadVersion?.let { (version, type) ->
                                        performDirectDownload(
                                            version,
                                            instance,
                                            type,
                                            "download_content"
                                        )
                                    }
                                    missingDependencies = emptyList()
                                    pendingDownloadVersion = null
                                }
                            },
                            onConfirm = {
                                val deps = missingDependencies
                                val target = pendingDownloadVersion
                                val dependencyGameVersion = selectedProjectMCVersion ?: instanceVersion
                                val targetLoaders = target?.first?.loaders.orEmpty()
                                val dependencyLoader = instanceLoader?.takeIf { isLoaderCompatible(it, targetLoaders) }
                                    ?: targetLoaders.firstOrNull { it in listOf("fabric", "forge", "quilt", "neoforge") }
                                missingDependencies = emptyList()
                                pendingDownloadVersion = null

                                scope.launch(Dispatchers.IO) {
                                    try {
                                        downloadMissingDependencies(
                                            deps,
                                            instance,
                                            selectedSource,
                                            dependencyGameVersion,
                                            dependencyLoader,
                                            target?.first?.dependencies.orEmpty()
                                        )
                                        target?.let { (version, type) ->
                                            performDirectDownload(version, instance, type, "download_content")
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Dependency install failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            onCancel = {
                                missingDependencies = emptyList()
                                pendingDownloadVersion = null
                            }
                        )
                    }

                    if (isDependencyChecking) {
                        AlertDialog(
                            onDismissRequest = {},
                            confirmButton = {},
                            title = { Text(translatedText("Checking Dependencies...")) },
                            text = {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoadingIndicator()
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
            }
        }
    }

    private fun sourceFailureMessage(source: ContentSource): String =
        if (source == ContentSource.CURSEFORGE) {
            ServiceKeys.curseForgeUnavailable(OnlineServiceCredentials.curseForgeKey(requireContext()))
        } else {
            "Could not load Modrinth content. Check your connection and refresh."
        }

    private suspend fun checkDependencies(
        version: ModrinthVersion,
        instance: Instance,
        source: ContentSource
    ): List<MissingDependency> {
        val requiredDeps = version.dependencies.filter { it.dependencyType == "required" }
        if (requiredDeps.isEmpty()) return emptyList()

        val modsFolder = File(instance.gameDirectory, "mods")
        if (!modsFolder.exists()) modsFolder.mkdirs()

        val installedMods = modsFolder.listFiles()?.mapNotNull { file ->
            ModMetadataReader.getMetadata(file)
        } ?: emptyList()

        val missing = mutableListOf<MissingDependency>()

        if (source == ContentSource.MODRINTH) {
            val projectIds = requiredDeps.mapNotNull { it.projectId }
            val projects = ModrinthService.getProjects(projectIds)

            requiredDeps.forEach { dep ->
                val project = projects.find { it.id == dep.projectId }
                val isInstalled = installedMods.any {
                    it.id == dep.projectId || it.name == project?.title
                }
                if (!isInstalled) {
                    missing.add(
                        MissingDependency(
                            dep.projectId ?: "",
                            project?.title ?: "Unknown Mod",
                            dep.dependencyType,
                            project?.iconUrl
                        )
                    )
                }
            }
        } else {
            val modIds = requiredDeps.mapNotNull { it.projectId?.toIntOrNull() }
            val mods = CurseForgeService.getMods(modIds)

            requiredDeps.forEach { dep ->
                val mod = mods.find { it.id == dep.projectId }
                val isInstalled = installedMods.any {
                    it.id == dep.projectId || it.name == mod?.title
                }
                if (!isInstalled) {
                    missing.add(
                        MissingDependency(
                            dep.projectId ?: "",
                            mod?.title ?: "Unknown Mod",
                            dep.dependencyType,
                            mod?.iconUrl
                        )
                    )
                }
            }
        }

        return missing
    }

    private suspend fun downloadMissingDependencies(
        missing: List<MissingDependency>,
        instance: Instance,
        source: ContentSource,
        mcVersion: String?,
        loader: String?,
        declaredDependencies: List<ModDependency>
    ) {
        if (mcVersion.isNullOrEmpty() || loader.isNullOrEmpty()) {
            throw IOException("Choose a game version and a supported mod loader before installing dependencies")
        }
        missing.forEach { dep ->
            try {
                val versions = if (source == ContentSource.MODRINTH) {
                    ModrinthService.getProjectVersions(dep.id)
                } else {
                    CurseForgeService.getProjectVersions(dep.id)
                }

                val requiredVersionId = declaredDependencies.firstOrNull {
                    it.dependencyType == "required" && it.projectId == dep.id
                }?.versionId
                val bestVersion = versions.find { v ->
                    (requiredVersionId == null || v.id == requiredVersionId) &&
                            v.gameVersions.any { isMcVersionCompatible(mcVersion, it) } &&
                            isLoaderCompatible(loader, v.loaders)
                } ?: throw IOException("No compatible required release of ${dep.name} for $mcVersion")

                if (!performDirectDownload(bestVersion, instance, ContentInstallerType.MODS, "download_content")) {
                    throw IOException("Could not download ${dep.name}")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("ContentInstaller", "Failed to download dependency ${dep.name}", e)
                throw e
            }
        }
    }


    private suspend fun installWorld(version: ModrinthVersion, instance: Instance, progressKey: String) {
        try {
            val savesFolder = File(instance.gameDirectory, "saves")
            savesFolder.mkdirs()
            val fileName = version.downloadUrl.substringAfterLast("/")
            val tempZip = File(instance.gameDirectory, "temp_world.zip")

            ProgressKeeper.submitProgress(progressKey, 0, -1, "Downloading $fileName...")

            withContext(Dispatchers.IO) {
                val url = URL(version.downloadUrl)
                val connection = url.openConnection()
                connection.connect()
                val totalSize = connection.contentLength.toLong()

                connection.getInputStream().use { input ->
                    tempZip.outputStream().use { output ->
                        var bytesCopied = 0L
                        val buffer = ByteArray(8192)
                        var bytes = input.read(buffer)
                        var lastProgressUpdate = 0L
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            if (totalSize > 0) {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastProgressUpdate > 100) {
                                    val progress = ((bytesCopied * 100) / totalSize).toInt()
                                    ProgressKeeper.submitProgress(
                                        progressKey,
                                        progress,
                                        -1,
                                        "Downloading $fileName..."
                                    )
                                    lastProgressUpdate = currentTime
                                }
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }

                ProgressKeeper.submitProgress(progressKey, 100, -1, "Extracting world...")

                ZipFile(tempZip).use { zip ->
                    val entries = zip.entries()
                    var levelDatEntry: ZipEntry? = null
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.name.endsWith("level.dat")) {
                            levelDatEntry = entry
                            break
                        }
                    }

                    if (levelDatEntry == null) throw IOException("No level.dat found in zip")

                    val worldPath = levelDatEntry.name.substringBeforeLast("level.dat")
                    val worldFolderName = if (worldPath.isEmpty()) {
                        fileName.substringBeforeLast(".")
                    } else {
                        worldPath.removeSuffix("/").substringAfterLast("/")
                    }

                    val finalWorldDir = File(savesFolder, worldFolderName)
                    finalWorldDir.mkdirs()

                    val extractEntries = zip.entries()
                    while (extractEntries.hasMoreElements()) {
                        val entry = extractEntries.nextElement()
                        if (entry.name.startsWith(worldPath)) {
                            val relativePath = entry.name.substring(worldPath.length)
                            if (relativePath.isEmpty()) continue

                            val destFile = File(finalWorldDir, relativePath)
                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                destFile.parentFile?.mkdirs()
                                zip.getInputStream(entry).use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    }
                }
                tempZip.delete()
            }

            ProgressKeeper.submitProgress(progressKey, -1, -1)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Installed World: ${version.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            ProgressKeeper.submitProgress(progressKey, -1, -1)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Failed to install world: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun performDirectDownload(version: ModrinthVersion, instance: Instance, type: ContentInstallerType, progressKey: String): Boolean {
        return try {
            val destFolder = when (type) {
                ContentInstallerType.MODS -> File(instance.gameDirectory, "mods")
                ContentInstallerType.RESOURCEPACKS -> File(instance.gameDirectory, "resourcepacks")
                ContentInstallerType.SHADERS -> File(instance.gameDirectory, "shaderpacks")
                else -> File(instance.gameDirectory, "downloads")
            }
            destFolder.mkdirs()
            val fileName = version.downloadUrl.substringAfterLast("/")
            val destFile = File(destFolder, fileName)

            ProgressKeeper.submitProgress(progressKey, 0, -1, "Downloading $fileName...")

            withContext(Dispatchers.IO) {
                val url = URL(version.downloadUrl)
                val connection = url.openConnection()
                connection.connect()
                val totalSize = connection.contentLength.toLong()

                connection.getInputStream().use { input ->
                    destFile.outputStream().use { output ->
                        var bytesCopied = 0L
                        val buffer = ByteArray(8192)
                        var bytes = input.read(buffer)
                        var lastProgressUpdate = 0L
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            if (totalSize > 0) {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastProgressUpdate > 100) {
                                    val progress = ((bytesCopied * 100) / totalSize).toInt()
                                    ProgressKeeper.submitProgress(
                                        progressKey,
                                        progress,
                                        -1,
                                        "Downloading $fileName..."
                                    )
                                    lastProgressUpdate = currentTime
                                }
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }
            }

            ProgressKeeper.submitProgress(progressKey, -1, -1)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Installed ${version.name}", Toast.LENGTH_SHORT)
                    .show()
            }
            true
        } catch (cancelled: CancellationException) {
            ProgressKeeper.submitProgress(progressKey, -1, -1)
            throw cancelled
        } catch (e: Exception) {
            ProgressKeeper.submitProgress(progressKey, -1, -1)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Failed to install: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            false
        }
    }

    private var mPendingImportType = ContentInstallerType.MODPACKS

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val context = requireContext()
        val contentResolver = context.contentResolver
        val type = mPendingImportType
        PojavApplication.sExecutorService.execute {
            performLocalInstall(uri, context, contentResolver, type)
        }
    }

    private fun performLocalInstall(uri: Uri, context: Context, contentResolver: ContentResolver, type: ContentInstallerType) {
        val fileName = HyperUtils.getFileName(context, uri) ?: return
        val outFile = File(HyperUtils.DIR_CACHE, "$fileName.cf")
        val progressKey = if (type == ContentInstallerType.MODPACKS) "install_modpack" else "install_content"
        ProgressKeeper.submitProgress(progressKey, 0, -1, "Caching content...")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return
        } catch (e: IOException) {
            HyperUtils.showErrorRemote("Error", e)
            ProgressKeeper.submitProgress(progressKey, -1, -1)
            return
        }

        try {
            if (type == ContentInstallerType.MODPACKS) {
                if (MMCInstanceImporter.isMMCInstance(outFile)) {
                    ProgressKeeper.submitProgress(progressKey, 50, -1, "Importing MMC Instance...")
                    MMCInstanceImporter.importInstance(fileName.substringBeforeLast("."), outFile)
                    HyperUtils.runOnUiThread {
                        Toast.makeText(context, "MMC Instance imported successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val modpackApi: ModpackApi =
                        CommonApi(OnlineServiceCredentials.curseForgeKey(requireContext()))
                    modpackApi.installLocalModpack(fileName, outFile, null)
                }
            } else {
                val instance = Instances.loadSelectedInstance()
                if (instance == null) {
                    HyperUtils.runOnUiThread { Toast.makeText(context, "No instance selected", Toast.LENGTH_SHORT).show() }
                    return
                }

                val destFolder = when (type) {
                    ContentInstallerType.MODS -> File(instance.gameDirectory, "mods")
                    ContentInstallerType.RESOURCEPACKS -> File(instance.gameDirectory, "resourcepacks")
                    ContentInstallerType.SHADERS -> File(instance.gameDirectory, "shaderpacks")
                    ContentInstallerType.WORLDS -> File(instance.gameDirectory, "saves")
                }
                destFolder.mkdirs()

                if (type == ContentInstallerType.WORLDS) {
                    ProgressKeeper.submitProgress(progressKey, 50, -1, "Extracting world...")
                    extractWorldLocal(outFile, destFolder, fileName)
                    HyperUtils.runOnUiThread {
                        Toast.makeText(context, "World $fileName imported successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val destFile = File(destFolder, fileName)
                    outFile.copyTo(destFile, overwrite = true)
                    HyperUtils.runOnUiThread {
                        Toast.makeText(context, "Installed $fileName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            HyperUtils.showErrorRemote("Error", e)
        } finally {
            outFile.delete()
            ProgressKeeper.submitProgress(progressKey, -1, -1)
        }
    }

    private fun extractWorldLocal(zipFile: File, savesFolder: File, fileName: String) {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            var levelDatEntry: ZipEntry? = null
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith("level.dat")) {
                    levelDatEntry = entry
                    break
                }
            }

            if (levelDatEntry == null) throw IOException("No level.dat found in zip")

            val worldPath = levelDatEntry.name.substringBeforeLast("level.dat")
            val worldFolderName = if (worldPath.isEmpty()) {
                fileName.substringBeforeLast(".")
            } else {
                worldPath.removeSuffix("/").substringAfterLast("/")
            }

            val finalWorldDir = File(savesFolder, worldFolderName)
            finalWorldDir.mkdirs()

            val extractEntries = zip.entries()
            while (extractEntries.hasMoreElements()) {
                val entry = extractEntries.nextElement()
                if (entry.name.startsWith(worldPath)) {
                    val relativePath = entry.name.substring(worldPath.length)
                    if (relativePath.isEmpty()) continue

                    val destFile = File(finalWorldDir, relativePath)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "ContentInstallerFragment"
    }
}
