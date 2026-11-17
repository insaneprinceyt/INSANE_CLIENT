package com.ashmeet.hyperlauncher.migration

import android.content.Context
import android.os.Build
import com.ashmeet.hyperlauncher.activity.TestStorageActivity
import com.ashmeet.hyperlauncher.screens.settings.preferences.LauncherPreferences
import com.ashmeet.hyperlauncher.utils.HyperUtils
import java.io.File
import java.io.IOException

/** Runs before accounts or instances can be read, including the storage-permission retry. */
object LegacyUpgrade {
    private const val COMPLETED = "insane_legacy_migration_v1"

    @JvmStatic
    fun hasStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 || TestStorageActivity.isStorageAllowed(context)

    @JvmStatic
    @Synchronized
    @Throws(IOException::class)
    fun ensure(context: Context) {
        // An unreadable legacy external folder looks absent to File.exists(). Never
        // record completion until the permission gate can actually expose its contents.
        if (!hasStoragePermission(context) || !HyperUtils.checkStorageRoot(context)) {
            throw IOException("Storage access is required before upgrading your existing installations.")
        }
        val prefs = LauncherPreferences.DEFAULT_PREF
            ?: throw IOException("Launcher preferences are not ready for the upgrade.")
        if (prefs.getBoolean(COMPLETED, false)) return
        val account = context.getSharedPreferences("pojav_profile", Context.MODE_PRIVATE)
            .getString("file", "") ?: ""
        val result = LegacyMigration(File(HyperUtils.DIR_DATA!!), File(HyperUtils.DIR_GAME_HOME))
            .migrate(account, prefs.getString("currentProfile", "") ?: "")
        val editor = prefs.edit()
        if (prefs.getString("selected_account_file", "").isNullOrEmpty() && result.selectedAccount.isNotEmpty()) {
            editor.putString("selected_account_file", result.selectedAccount)
        }
        if (prefs.getString(LauncherPreferences.PREF_KEY_CURRENT_INSTANCE, "").isNullOrEmpty()
            && result.selectedInstance.isNotEmpty()) {
            editor.putString(LauncherPreferences.PREF_KEY_CURRENT_INSTANCE, result.selectedInstance)
        }
        // Commit selection and completion together. The file migration can safely be retried
        // if this commit fails or the process stops between files and preferences.
        if (!editor.putBoolean(COMPLETED, true).commit()) {
            throw IOException("Could not save the upgrade selection. Reopen INSANE CLIENT to retry; your data is kept.")
        }
    }
}
