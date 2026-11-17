package com.ashmeet.hyperlauncher.utils.horizon

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.io.File

/** Check before touching a JNI class: the bundled engine is Android 9 ARM64 only. */
object HorizonSupport {
    @JvmStatic
    fun getUnsupportedReason(context: Context): String? = when {
        Build.VERSION.SDK_INT < 28 -> "Rust Edition needs Android 9 or newer."
        !Process.is64Bit() || !Build.SUPPORTED_ABIS.contains("arm64-v8a") ->
            "Rust Edition needs an ARM64 device and the 64-bit launcher."
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION) ->
            "Rust Edition needs a Vulkan-capable graphics driver."
        !File(context.applicationInfo.nativeLibraryDir, "libhorizon_android.so").isFile ->
            "This APK does not include the ARM64 Rust engine. Install the universal or ARM64 APK."
        else -> null
    }
}
