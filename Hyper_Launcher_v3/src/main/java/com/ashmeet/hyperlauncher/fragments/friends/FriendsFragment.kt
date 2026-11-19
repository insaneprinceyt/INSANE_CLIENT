package com.ashmeet.hyperlauncher.fragments.friends

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ashmeet.hyperbackend.authenticator.AuthType
import com.ashmeet.hyperbackend.authenticator.accounts.Accounts
import com.ashmeet.hyperbackend.instances.Instance
import com.ashmeet.hyperbackend.instances.Instances
import com.ashmeet.hyperbackend.progresskeeper.ProgressKeeper
import com.ashmeet.hyperlauncher.extra.ExtraConstants
import com.ashmeet.hyperlauncher.extra.ExtraCore
import com.ashmeet.hyperlauncher.extra.ExtraListener
import com.ashmeet.hyperlauncher.fragments.auth.AuthHostFragment
import com.ashmeet.hyperlauncher.fragments.instances.InstanceDirectoryFragment
import com.ashmeet.hyperlauncher.fragments.instances.InstanceSelectionFragment
import com.ashmeet.hyperlauncher.screens.friends.FriendsScreen
import com.ashmeet.hyperlauncher.theme.PojavTheme
import com.ashmeet.hyperlauncher.utils.HyperUtils
import com.ashmeet.hyperlauncher.utils.friends.WorldHostInstaller
import com.ashmeet.hyperlauncher.utils.horizon.HorizonUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ashmeet.hyperlauncher.R

/** Installs the real in-game friends service; no synthetic online status or friend list. */
class FriendsFragment : Fragment() {
    companion object {
        const val TAG = "FriendsFragment"
        private const val INSTALL_PROGRESS = "insane_world_host_install"
    }

    private data class Selection(
        val instance: Instance?,
        val minecraftVersion: String?,
        val accountName: String?,
        val microsoftAccount: Boolean,
        val problem: String?
    )

    private var selection by mutableStateOf<Selection?>(null)
    private var release by mutableStateOf<WorldHostInstaller.Release?>(null)
    private var installed by mutableStateOf<WorldHostInstaller.Installed?>(null)
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf<String?>(null)
    private var generation = 0
    private var installing = false

    private val accountListener = ExtraListener<Any> { _, _ ->
        view?.post { if (!busy) refresh() }
        false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PojavTheme {
                    val selected = selection
                    FriendsScreen(
                        instanceName = selected?.instance?.name ?: "No instance selected",
                        minecraftVersion = selected?.minecraftVersion,
                        accountName = selected?.accountName,
                        installedVersion = installed?.let { it.version + if (it.disabled) " (disabled)" else "" },
                        availableVersion = release?.version,
                        busy = busy,
                        status = status,
                        canInstall = selected?.problem == null && selected?.microsoftAccount == true &&
                            release != null && installed == null,
                        canLaunch = selected?.problem == null && selected?.microsoftAccount == true &&
                            installed?.matches(release) == true,
                        onBack = { if (!installing) HyperUtils.removeCurrentFragment(requireActivity()) },
                        onChooseInstance = { navigate(InstanceSelectionFragment::class.java, InstanceSelectionFragment.TAG) },
                        onChooseAccount = { navigate(AuthHostFragment::class.java, AuthHostFragment.TAG) },
                        onRefresh = { refresh() },
                        onInstall = { install() },
                        onLaunch = { launchMinecraft() },
                        onManageMods = { navigate(InstanceDirectoryFragment::class.java, InstanceDirectoryFragment.TAG) }
                    )
                }
            }
        }

    override fun onResume() {
        super.onResume()
        ExtraCore.addExtraListener(ExtraConstants.REFRESH_ACCOUNT_SPINNER, accountListener)
        refresh()
    }

    override fun onPause() {
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, accountListener)
        super.onPause()
    }

    override fun onDestroyView() {
        generation++
        if (!installing) busy = false
        super.onDestroyView()
    }

    private fun navigate(fragment: Class<out Fragment>, tag: String) {
        if (!busy && !parentFragmentManager.isStateSaved) {
            HyperUtils.swapFragment(requireActivity(), fragment, tag, null)
        }
    }

    private fun readSelection(): Selection {
        val instance = Instances.loadSelectedInstance()
        val account = Accounts.getCurrent()
        val microsoft = account?.authType == AuthType.MICROSOFT && !account.isLocal
        var version: String? = null
        val problem = when {
            instance == null -> "Choose a Fabric Java instance to set up friends."
            HorizonUtils.isHorizonInstance(instance) -> "World Host is a Java mod. Rust Edition cannot use this friends service. Choose a Fabric instance."
            instance.installer != null -> "Finish installing this instance from Home, then return to Friends & Worlds."
            else -> {
                // Read loader metadata, never infer the game version from a loader's version number.
                val metadata = try { HyperUtils.getVersionInfo(instance.versionId, true) } catch (_: Exception) { null }
                val fabric = metadata?.libraries?.any { it.name?.startsWith("net.fabricmc:fabric-loader:") == true } == true
                version = metadata?.inheritsFrom?.takeIf { it.isNotBlank() }
                when {
                    !fabric -> "Choose an installed Fabric instance. For example, create Fabric for Minecraft 1.21.1."
                    version == null -> "Could not identify this instance's Minecraft version. Finish its Fabric setup and refresh."
                    else -> null
                }
            }
        }
        return Selection(instance, version, account?.username, microsoft, problem)
    }

    private fun sameInstance(first: Selection, second: Selection): Boolean =
        first.instance?.mInstanceRoot == second.instance?.mInstanceRoot &&
            first.instance?.versionId == second.instance?.versionId &&
            first.instance?.gameDirectory == second.instance?.gameDirectory

    private fun refresh() {
        if (busy || view == null) return
        val request = ++generation
        busy = true
        release = null
        installed = null
        status = "Checking instance and compatible World Host releases…"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val selected = withContext(Dispatchers.IO) { readSelection() }
                if (request != generation) return@launch
                selection = selected
                if (selected.problem != null) {
                    status = selected.problem
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    val found = WorldHostInstaller.findRelease(selected.minecraftVersion!!)
                    val existing = WorldHostInstaller.inspectInstalled(selected.instance!!.gameDirectory)
                    found to existing
                }
                if (request != generation) return@launch
                release = result.first
                installed = result.second
                status = setupMessage(selected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == generation) status = "Could not check World Host. Check your connection and instance files, then tap Refresh."
            } finally {
                if (request == generation) busy = false
            }
        }
    }

    private fun setupMessage(selected: Selection): String = when {
        release == null -> "No supported World Host release for this Minecraft version. Choose another Fabric instance, such as 1.21.1."
        !selected.microsoftAccount -> "Select a Microsoft account that owns Minecraft Java Edition. World Host's authenticated friends service does not support local or Ely.by accounts."
        installed?.disabled == true -> "World Host is disabled. Enable it in Manage mods, then refresh."
        installed != null && installed?.matches(release) != true -> "A different World Host file is installed. Review it in Manage mods; remove that file before installing the compatible release."
        installed != null -> "World Host is ready. Launch Minecraft, then open Multiplayer → Friends. Android multiplayer still needs testing on your device."
        else -> "Install World Host for this instance. Both players should use the same Minecraft version and compatible mods."
    }

    private fun install() {
        val expected = selection ?: return
        val target = release ?: return
        if (busy || installed != null || expected.problem != null || !expected.microsoftAccount) return
        if (ProgressKeeper.getTaskCount() > 0) {
            status = "Wait for other launcher tasks to finish, then try again."
            return
        }
        busy = true
        installing = true
        status = "Downloading and verifying World Host…"
        ProgressKeeper.submitProgress(INSTALL_PROGRESS, 0, R.string.friends_installing)
        lifecycleScope.launch {
            try {
                // Finish the atomic operation even if the user backgrounds or leaves the screen.
                val existing = withContext(NonCancellable + Dispatchers.IO) {
                    val current = readSelection()
                    check(sameInstance(expected, current) && current.problem == null && current.microsoftAccount)
                    WorldHostInstaller.install(target, current.instance!!.gameDirectory)
                    WorldHostInstaller.inspectInstalled(current.instance.gameDirectory)
                }
                installed = existing
                status = setupMessage(expected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status = "World Host could not be installed. Check storage and connection, or review existing files in Manage mods. Refresh before retrying."
            } finally {
                ProgressKeeper.submitProgress(INSTALL_PROGRESS, -1, -1)
                installing = false
                busy = false
            }
        }
    }

    private fun launchMinecraft() {
        val expected = selection ?: return
        val target = release ?: return
        if (busy || installed?.matches(target) != true) return
        val request = ++generation
        busy = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ready = withContext(Dispatchers.IO) {
                    val current = readSelection()
                    sameInstance(expected, current) && current.problem == null && current.microsoftAccount &&
                        WorldHostInstaller.inspectInstalled(current.instance!!.gameDirectory)?.matches(target) == true
                }
                if (!ready) {
                    status = "The selected account, instance or mod changed. Refresh before launching."
                } else if (ProgressKeeper.getTaskCount() > 0) {
                    status = "Wait for other launcher tasks to finish, then launch again."
                } else {
                    // Start at Minecraft's menu, not a stale quick-play destination from another screen.
                    ExtraCore.consumeValue(ExtraConstants.QUICK_PLAY_WORLD)
                    ExtraCore.consumeValue(ExtraConstants.QUICK_PLAY_SERVER)
                    ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status = "Could not validate this instance. Refresh and try again."
            } finally {
                if (request == generation) busy = false
            }
        }
    }
}
