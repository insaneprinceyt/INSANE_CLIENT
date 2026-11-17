package com.ashmeet.hyperlauncher.fragments.modloader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.ashmeet.hyperbackend.instances.Instances
import com.ashmeet.hyperlauncher.fragments.instances.InstanceEditorFragment
import com.ashmeet.hyperlauncher.screens.modloader.RustInstallScreen
import com.ashmeet.hyperlauncher.theme.PojavTheme
import com.ashmeet.hyperlauncher.utils.HyperUtils
import com.ashmeet.hyperlauncher.utils.horizon.HorizonReleaseCatalog
import com.ashmeet.hyperlauncher.utils.horizon.HorizonSupport
import com.ashmeet.hyperlauncher.utils.horizon.HorizonUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.horizon.android.HorizonInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Rust releases are engine builds, not the Java Minecraft version manifest. */
class RustInstallFragment : Fragment() {
    companion object {
        const val TAG = "RustInstallFragment"
    }

    private var releases by mutableStateOf(listOf(HorizonReleaseCatalog.bundledRelease()))
    private var loading by mutableStateOf(false)
    private var installing by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)
    private var pendingEditor = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            PojavTheme {
                RustInstallScreen(
                    releases = releases,
                    loading = loading,
                    installing = installing,
                    notice = notice,
                    onBack = { if (!installing) HyperUtils.removeCurrentFragment(requireActivity()) },
                    onRefresh = { loadCatalog(true) },
                    onInstall = { install(it) },
                    onReleaseInfo = { openReleaseInfo(it) }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCatalog(false)
    }

    override fun onResume() {
        super.onResume()
        view?.post { openCreatedInstanceWhenResumed() }
    }

    private fun openCreatedInstanceWhenResumed() {
        if (!pendingEditor || !isAdded || view == null || parentFragmentManager.isStateSaved ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        pendingEditor = false
        HyperUtils.swapFragment(
            requireActivity(), InstanceEditorFragment::class.java, InstanceEditorFragment.TAG,
            Bundle().apply { putBoolean("is_new_instance", true) }
        )
    }

    private fun loadCatalog(forceRefresh: Boolean) {
        if (loading || installing || view == null) return
        val cacheDirectory = File(requireContext().applicationContext.cacheDir, "rust-releases")
        loading = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    HorizonReleaseCatalog.load(cacheDirectory, forceRefresh)
                }
                releases = result.releases
                notice = result.notice?.takeIf { it.isNotBlank() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notice = "Could not refresh the release list. The bundled engine is still available; try Refresh again."
            } finally {
                loading = false
            }
        }
    }

    private fun install(release: HorizonReleaseCatalog.Release) {
        // Only the pinned native adapter is supported. Never relabel it as another engine.
        if (!release.bundled || installing) return
        val activity = activity ?: return
        if (view == null) return
        if (activity.isFinishing || activity.isDestroyed) return
        HorizonSupport.getUnsupportedReason(activity)?.let { reason ->
            MaterialAlertDialogBuilder(activity)
                .setTitle("Rust Edition requirements")
                .setMessage(reason)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        installing = true
        HorizonInstaller.showInstallDialogAndRun(activity, {
            // A successful native completion is required before an instance appears.
            try {
                val instance = Instances.createInstance({ created ->
                    created.name = "Rust Edition 26.3"
                    created.versionId = HorizonUtils.HORIZON_VERSION_ID
                    created.selectedRuntime = HorizonUtils.HORIZON_RUNTIME
                    created.sharedData = false
                    created.renderer = "vulkan"
                    created.icon = "horizon"
                    created.horizonRenderScale = 1.0f
                    created.horizonOrientation = "sensor_landscape"
                    created.horizonInputHost = "touch"
                    created.horizonUseHorizonControls = true
                }, "Rust-26.3")
                Instances.setSelectedInstance(instance)
                // Persist success even if onSaveInstanceState ran while downloading.
                // Only fragment navigation waits for the foreground lifecycle.
                pendingEditor = true
                openCreatedInstanceWhenResumed()
            } catch (error: IOException) {
                HyperUtils.showError(activity, error)
            }
        }, {
            installing = false
        })
    }

    private fun openReleaseInfo(release: HorizonReleaseCatalog.Release) {
        if (installing) return
        val context = context ?: return
        val uri = Uri.parse(release.releaseUrl)
        if (uri.scheme != "https" || uri.host != "github.com") return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser is available to open this release.", Toast.LENGTH_LONG).show()
        }
    }
}
