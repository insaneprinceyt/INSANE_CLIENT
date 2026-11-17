package com.ashmeet.hyperlauncher.utils.horizon

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.ashmeet.hyperbackend.authenticator.AuthType
import com.ashmeet.hyperbackend.authenticator.accounts.Account
import com.ashmeet.hyperbackend.authenticator.accounts.Accounts
import com.ashmeet.hyperbackend.instances.DisplayInstance
import com.ashmeet.hyperbackend.instances.Instance
import com.ashmeet.hyperlauncher.activity.PojavApplication
import com.ashmeet.hyperlauncher.extra.ExtraConstants
import com.ashmeet.hyperlauncher.extra.ExtraCore
import com.ashmeet.hyperlauncher.utils.HyperUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.horizon.android.HorizonActivity
import dev.horizon.android.HorizonInstaller
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

object HorizonUtils {
    const val HORIZON_VERSION_ID = "26.3-horizon"
    const val HORIZON_RUNTIME = "HORIZON_NATIVE"
    private val launching = AtomicBoolean(false)

    @JvmStatic
    fun isHorizonInstance(instance: DisplayInstance?): Boolean = instance != null &&
        isHorizonInstance(instance.versionId, (instance as? Instance)?.selectedRuntime)

    @JvmStatic
    fun isHorizonInstance(versionId: String?, runtime: String?): Boolean =
        HORIZON_VERSION_ID.equals(versionId, true) || HORIZON_RUNTIME.equals(runtime, true)

    private fun active(context: Context): Boolean = context !is Activity ||
        (!context.isFinishing && !context.isDestroyed)

    @JvmStatic
    fun launchHorizon(context: Context, instance: Instance?): Boolean {
        val problem = HorizonSupport.getUnsupportedReason(context)
        if (problem != null) {
            Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
            return false
        }
        if (instance == null || !isHorizonInstance(instance)) return false
        val account = Accounts.getCurrent()
        if (account == null || (account.authType != AuthType.LOCAL && account.authType != AuthType.MICROSOFT)) {
            Toast.makeText(context, "Choose a Microsoft or local account for Rust Edition. Ely.by remains available for Java Edition.", Toast.LENGTH_LONG).show()
            return false
        }
        // A native engine process cannot safely reload a different world/config in place.
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (manager.runningAppProcesses?.any { it.processName == context.packageName + ":horizon" } == true) {
            Toast.makeText(context, "Close the running Rust Edition session before starting another.", Toast.LENGTH_LONG).show()
            return false
        }
        if (!launching.compareAndSet(false, true)) return false
        val checking = MaterialAlertDialogBuilder(context).setTitle("Rust Edition")
            .setMessage("Checking installed resources…").setCancelable(false).show()
        PojavApplication.sExecutorService.execute {
            val ready = try { HorizonInstaller.isReady(context) } catch (_: Exception) { false }
            HyperUtils.runOnUiThread {
                checking.dismiss()
                if (!active(context)) {
                    launching.set(false)
                } else if (ready) {
                    prepareAndLaunch(context, instance, account)
                } else {
                    val continued = AtomicBoolean(false)
                    HorizonInstaller.showInstallDialogAndRun(context, {
                        continued.set(true)
                        prepareAndLaunch(context, instance, account)
                    }, {
                        if (!continued.get()) launching.set(false)
                    })
                }
            }
        }
        return true
    }

    private fun prepareAndLaunch(context: Context, instance: Instance, account: Account) {
        val dialog = MaterialAlertDialogBuilder(context).setTitle("Rust Edition")
            .setMessage("Preparing your account and game…").setCancelable(false).show()
        PojavApplication.sExecutorService.execute {
            try {
                val current = Accounts.getCurrent()
                if (current == null || current.mSaveLocation != account.mSaveLocation) {
                    throw IOException("Selected account changed.")
                }
                if (instance.sharedData || !instance.legacyGameDirectory.isNullOrBlank()) {
                    throw IOException("Rust Edition requires its own instance folder.")
                }
                val gameDir = instance.gameDirectory
                if (!gameDir.isDirectory && !gameDir.mkdirs()) throw IOException("Cannot create game folder.")
                for (name in listOf("saves", "resourcepacks", "horizon-mods")) {
                    val folder = File(gameDir, name)
                    if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Cannot prepare game folder.")
                }
                val args = mutableListOf("--resources", HorizonInstaller.getHorizonHomeDir(context).absolutePath,
                    "--game-dir", gameDir.absolutePath)
                args.addAll(HorizonAuth.prepareAuthArgs(context, current))
                (ExtraCore.consumeValue(ExtraConstants.QUICK_PLAY_WORLD) as? String)?.takeIf { it.isNotBlank() }?.let {
                    args.addAll(listOf("--quickPlaySingleplayer", it))
                }
                (ExtraCore.consumeValue(ExtraConstants.QUICK_PLAY_SERVER) as? String)?.takeIf { it.isNotBlank() }?.let {
                    args.addAll(listOf("--quickPlayMultiplayer", it))
                }
                val scale = (instance.horizonRenderScale ?: 1f).takeIf { it.isFinite() } ?: 1f
                val config = JSONObject().apply {
                    put("args", JSONArray(args))
                    put("renderer", "vulkan")
                    put("render_scale", (scale * 100).roundToInt().coerceIn(25, 100))
                    put("input_host", "touch")
                    put("orientation", when (instance.horizonOrientation) {
                        "sensor_portrait", "portrait" -> "portrait"
                        "auto" -> "auto"
                        else -> "landscape"
                    })
                    put("immersive", true)
                    put("avoid_cutout", true)
                    put("use_horizon_controls", true)
                }
                HorizonAuth.writePrivateJson(File(context.filesDir, "launch_config.json"), config)
                HyperUtils.runOnUiThread {
                    dialog.dismiss()
                    try {
                        if (active(context)) context.startActivity(Intent(context, HorizonActivity::class.java))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open Rust Edition. Reopen the launcher and try again.", Toast.LENGTH_LONG).show()
                    } finally {
                        launching.set(false)
                    }
                }
            } catch (_: Exception) {
                HyperUtils.runOnUiThread {
                    dialog.dismiss()
                    launching.set(false)
                    if (active(context)) MaterialAlertDialogBuilder(context).setTitle("Could not start Rust Edition")
                        .setMessage("Check your account sign-in and available storage, then try again. Microsoft sign-in failures require signing in again; they do not switch to offline play.")
                        .setPositiveButton(android.R.string.ok, null).show()
                }
            }
        }
    }
}
