package dev.horizon.android

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ashmeet.hyperlauncher.components.dialog.HorizonInstallerDialog
import com.ashmeet.hyperlauncher.theme.PojavTheme
import com.ashmeet.hyperlauncher.utils.helper.LauncherComposeHelper
import com.ashmeet.hyperlauncher.utils.horizon.HorizonEngineAssets
import com.ashmeet.hyperlauncher.utils.horizon.HorizonInstallCoordinator
import com.ashmeet.hyperlauncher.utils.horizon.HorizonSupport
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

@Keep
interface HorizonInstallCallback {
    fun onProgress(stage: Int, currentBytes: Long, totalBytes: Long)
    fun onComplete(success: Boolean, errorMessage: String?)
}

/** JNI signatures match the pinned Hyper/Starling Android engine. */
@Suppress("KotlinJniMissingFunction")
@Keep
object HorizonInstaller {
    private const val TAG = "HorizonInstaller"
    private val main = Handler(Looper.getMainLooper())
    private val installing = AtomicBoolean(false)
    private var libraryLoaded = false

    @JvmStatic private external fun nativeIsReady(homePath: String): Boolean
    @JvmStatic private external fun nativeInstall(homePath: String, callback: HorizonInstallCallback): Boolean

    fun getHorizonHomeDir(context: Context): File = File(context.filesDir, ".local/horizon-client/26.3")

    @Synchronized
    private fun loadEngine(context: Context) {
        HorizonSupport.getUnsupportedReason(context)?.let { throw IOException(it) }
        if (!libraryLoaded) {
            System.loadLibrary("horizon_android")
            libraryLoaded = true
        }
    }

    private fun prepareEngineData(context: Context) {
        HorizonEngineAssets.ensure(File(context.filesDir, "engine-data.zip"),
            { context.assets.open("engine-data.zip") },
            HorizonEngineAssets.BUNDLED_SIZE, HorizonEngineAssets.BUNDLED_SHA256)
    }

    fun isReady(context: Context): Boolean {
        if (installing.get()) return false
        return try {
            loadEngine(context)
            prepareEngineData(context)
            nativeIsReady(context.filesDir.absolutePath)
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    fun showInstallDialogAndRun(context: Context, onComplete: () -> Unit) =
        showInstallDialogAndRun(context, onComplete, {})

    fun showInstallDialogAndRun(context: Context, onComplete: () -> Unit, onFinished: () -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { showInstallDialogAndRun(context, onComplete, onFinished) }
            return
        }
        val activity = findActivity(context)
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onFinished()
            return
        }
        HorizonSupport.getUnsupportedReason(activity)?.let {
            Toast.makeText(activity, it, Toast.LENGTH_LONG).show()
            onFinished()
            return
        }
        if (installing.get()) {
            Toast.makeText(activity, "Rust Edition setup is already running.", Toast.LENGTH_LONG).show()
            onFinished()
            return
        }

        val application = activity.applicationContext
        val abandoned = AtomicBoolean(false)
        val completed = AtomicBoolean(false)
        var statusText by mutableStateOf("Preparing Rust Edition…")
        var indeterminate by mutableStateOf(true)
        var progress by mutableFloatStateOf(0f)
        val composeView = ComposeView(activity).apply {
            LauncherComposeHelper.ensureViewTreeOwners(this)
            setContent {
                PojavTheme { HorizonInstallerDialog(statusText, indeterminate, progress) }
            }
        }
        val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar).apply {
            setContentView(composeView)
            setCancelable(false)
        }
        val owner = activity as? LifecycleOwner
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                abandoned.set(true)
                if (dialog.isShowing) dialog.dismiss()
            }
        }
        owner?.lifecycle?.addObserver(observer)

        fun finish(success: Boolean, message: String?) {
            if (!completed.compareAndSet(false, true)) return
            main.post {
                owner?.lifecycle?.removeObserver(observer)
                if (dialog.isShowing) dialog.dismiss()
                try {
                    if (!abandoned.get() && !activity.isFinishing && !activity.isDestroyed) {
                        if (success) onComplete()
                        else MaterialAlertDialogBuilder(activity)
                            .setTitle("Rust Edition setup failed")
                            .setMessage(message?.take(400) ?: "Resources could not be prepared. Check your connection and storage, then try again.")
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                } finally {
                    onFinished()
                }
            }
        }

        val installation = HorizonInstallCoordinator.tryStart(
            installing,
            { nativeIsReady(application.filesDir.absolutePath) },
            { success, message -> finish(success, message) }
        )
        if (installation == null) {
            owner?.lifecycle?.removeObserver(observer)
            Toast.makeText(activity, "Rust Edition setup is already running.", Toast.LENGTH_LONG).show()
            onFinished()
            return
        }

        try {
            dialog.show()
            Thread({
                try {
                    loadEngine(application)
                    prepareEngineData(application)
                    val returned = nativeInstall(application.filesDir.absolutePath, object : HorizonInstallCallback {
                        override fun onProgress(stage: Int, currentBytes: Long, totalBytes: Long) {
                            main.post {
                                if (completed.get() || abandoned.get()) return@post
                                statusText = when (stage) {
                                    0 -> "Checking Minecraft 26.3…"
                                    1 -> "Downloading game resources and sounds…"
                                    2 -> "Unpacking verified files…"
                                    3 -> "Preparing worlds and registries…"
                                    4 -> "Downloading touch controls…"
                                    5 -> "Finishing setup…"
                                    else -> "Installing…"
                                }
                                indeterminate = totalBytes <= 0
                                if (!indeterminate) progress = (currentBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
                            }
                        }
                        override fun onComplete(success: Boolean, errorMessage: String?) {
                            installation.onComplete(success, errorMessage)
                        }
                    })
                    // JNI starts and detaches a native worker. A true return is not
                    // completion: keep progress and admission active until its callback.
                    installation.onStartupReturned(returned)
                } catch (error: IOException) {
                    Log.e(TAG, "Rust resource preparation failed")
                    installation.onStartupFailed(error.message ?: "Could not prepare Rust engine data. Check available storage and retry setup.")
                } catch (error: Exception) {
                    Log.e(TAG, "Rust resource installation failed (${error.javaClass.simpleName})")
                    installation.onStartupFailed("Rust setup could not start (${error.javaClass.simpleName}). Reopen the launcher and try again.")
                } catch (_: LinkageError) {
                    Log.e(TAG, "Rust native engine could not load")
                    installation.onStartupFailed("The Rust engine could not load on this device. Check Android and device requirements.")
                }
            }, "insane-rust-setup").start()
        } catch (_: Exception) {
            installation.onStartupFailed("Could not start Rust Edition setup. Reopen the launcher and try again.")
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val next = current.baseContext
            if (next === current) return null
            current = next
        }
        return current as? Activity
    }
}
