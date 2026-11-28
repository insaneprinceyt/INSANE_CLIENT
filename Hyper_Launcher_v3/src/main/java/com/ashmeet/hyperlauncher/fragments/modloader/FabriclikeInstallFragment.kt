package com.ashmeet.hyperlauncher.fragments.modloader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ashmeet.hyperlauncher.screens.modloader.fabric.FabriclikeInstallScreen
import com.ashmeet.hyperlauncher.theme.PojavTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ashmeet.hyperlauncher.R
import com.ashmeet.hyperlauncher.utils.HyperUtils
import com.ashmeet.hyperlauncher.extra.ExtraCore
import com.ashmeet.hyperbackend.instances.Instances
import com.ashmeet.hyperbackend.modloaders.FabricVersion
import com.ashmeet.hyperbackend.modloaders.FabriclikeUtils
import com.ashmeet.hyperbackend.modloaders.ModloaderDownloadListener
import com.ashmeet.hyperbackend.modloaders.ModloaderListenerProxy
import com.ashmeet.hyperbackend.progresskeeper.ProgressKeeper
import java.io.File
import java.io.IOException
import com.ashmeet.hyperlauncher.utils.client.InsaneClientInstaller

abstract class FabriclikeInstallFragment(
    private val mFabriclikeUtils: FabriclikeUtils,
    private val mFragmentTag: String
) : Fragment(), ModloaderDownloadListener {
    private val mExtraTag: String = mFragmentTag + "_proxy"

    private var gameVersions by mutableStateOf<List<FabricVersion>>(emptyList())
    private var loaderVersions by mutableStateOf<List<FabricVersion>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var isInstalling by mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                PojavTheme {
                    FabriclikeInstallScreen(
                        loaderName = mFabriclikeUtils.name,
                        isLoading = isLoading,
                        isInstalling = isInstalling,
                        gameVersions = gameVersions,
                        loaderVersions = loaderVersions,
                        supportsClient = mFabriclikeUtils === FabriclikeUtils.FABRIC_UTILS,
                        onInstall = { gameVersion, loaderVersion, includeClient ->
                            performInstallation(gameVersion, loaderVersion, includeClient)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val proxy = getListenerProxy()
        if (proxy != null) {
            isInstalling = true
            proxy.attachListener(this)
        }
        loadVersions()
    }

    private fun loadVersions() {
        isLoading = true
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val gv = mFabriclikeUtils.downloadGameVersions()
                val lv = mFabriclikeUtils.downloadLoaderVersions()
                withContext(Dispatchers.Main) {
                    gameVersions = gv?.toList() ?: emptyList()
                    loaderVersions = lv?.toList() ?: emptyList()
                    isLoading = false
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    HyperUtils.showError(requireContext(), e)
                }
            }
        }
    }

    private fun performInstallation(gameVersion: String, loaderVersion: String, includeClient: Boolean) {
        if (isInstalling || ProgressKeeper.hasOngoingTasks()) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show()
            return
        }
        if (includeClient && (mFabriclikeUtils !== FabriclikeUtils.FABRIC_UTILS ||
                gameVersion != InsaneClientInstaller.MINECRAFT_VERSION ||
                !InsaneClientInstaller.isCompatibleLoader(loaderVersion))) {
            HyperUtils.showError(requireContext(), IOException("INSANE CLIENT requires Minecraft ${InsaneClientInstaller.MINECRAFT_VERSION} and Fabric ${InsaneClientInstaller.MIN_LOADER_VERSION} or newer."))
            return
        }
        val assets = requireContext().applicationContext.assets
        val proxy = ModloaderListenerProxy()
        proxy.attachListener(this)
        setListenerProxy(proxy)
        isInstalling = true

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val versionId = mFabriclikeUtils.install(gameVersion, loaderVersion)
                if (versionId == null) {
                    withContext(Dispatchers.Main) {
                        getListenerProxy()?.onDataNotAvailable()
                    }
                    return@launch
                }
                val coroutineContext = currentCoroutineContext()
                coroutineContext.ensureActive()
                if (includeClient) {
                    Instances.createPreparedInstance({ i ->
                        i.name = "INSANE CLIENT"
                        i.icon = "default"
                        i.versionId = versionId
                        i.sharedData = false
                        i.insaneClientVersion = InsaneClientInstaller.CLIENT_VERSION
                    }, "insane-client") { instance ->
                        assets.open("client/insane-client-1.1.2.jar").use { client ->
                            InsaneClientInstaller.install(instance.gameDirectory, client)
                        }
                        coroutineContext.ensureActive()
                    }
                } else {
                    Instances.createInstance({ i ->
                        i.name = mFabriclikeUtils.name
                        i.icon = mFabriclikeUtils.iconName
                        i.versionId = versionId
                    }, versionId)
                }

                withContext(Dispatchers.Main) {
                    getListenerProxy()?.onDownloadFinished(null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (isAdded && view != null) onDownloadError(e)
                }
            } finally {
                // Cancellation can arrive while an IO error is being delivered to the UI.
                // Always release this operation's proxy, including after the view disappears.
                withContext(NonCancellable + Dispatchers.Main) {
                    if (getListenerProxy() === proxy) {
                        proxy.detachListener()
                        setListenerProxy(null)
                        isInstalling = false
                    }
                }
            }
        }
    }

    override fun onDownloadFinished(downloadedFile: File?) {
        HyperUtils.runOnUiThread {
            getListenerProxy()?.detachListener()
            setListenerProxy(null)
            isInstalling = false
            parentFragmentManager.popBackStackImmediate()
        }
    }

    override fun onDataNotAvailable() {
        HyperUtils.runOnUiThread {
            val context = requireContext()
            getListenerProxy()?.detachListener()
            setListenerProxy(null)
            isInstalling = false
            HyperUtils.dialog(
                context,
                context.getString(R.string.global_error),
                context.getString(R.string.fabric_dl_cant_read_meta, mFabriclikeUtils.name)
            )
        }
    }

    override fun onDownloadError(e: Exception) {
        HyperUtils.runOnUiThread {
            getListenerProxy()?.detachListener()
            setListenerProxy(null)
            isInstalling = false
            HyperUtils.showError(requireContext(), e)
        }
    }

    private fun getListenerProxy(): ModloaderListenerProxy? {
        return ExtraCore.getValue(mExtraTag) as? ModloaderListenerProxy
    }

    private fun setListenerProxy(listenerProxy: ModloaderListenerProxy?) {
        ExtraCore.setValue(mExtraTag, listenerProxy)
    }
}
