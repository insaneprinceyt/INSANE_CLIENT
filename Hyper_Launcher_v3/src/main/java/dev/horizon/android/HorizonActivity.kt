package dev.horizon.android

import android.app.NativeActivity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.Keep

/**
 * Native host for Horizon's own touch controls, keyboard and rendering.
 *
 * The launcher checks device support and prepares the engine before starting this
 * non-exported activity. NativeActivity owns the input queue and surface lifecycle;
 * forwarding the same events through a second Java overlay would duplicate input.
 * Based on Starling v0.7.7's MIT-licensed Android host.
 */
@Keep
class HorizonActivity : NativeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Loading here keeps class inspection from loading an ARM64 library in a
        // Java-only process. The launcher gates API/ABI/Vulkan before this entry.
        System.loadLibrary("horizon_android")
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Each engine session owns its native event loop. Dispose the isolated
        // engine process after shutdown so the next Play starts a fresh loop.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.systemBars())
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    @Deprecated("NativeActivity's SAF bridge uses the platform result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DOCUMENT_REQUEST) {
            nativeDocumentResult(requestCode, resultCode, data?.data?.toString())
        }
    }

    @Keep
    @Suppress("KotlinJniMissingFunction")
    private external fun nativeDocumentResult(requestCode: Int, resultCode: Int, uri: String?)

    private companion object {
        const val DOCUMENT_REQUEST = 4101
    }
}
