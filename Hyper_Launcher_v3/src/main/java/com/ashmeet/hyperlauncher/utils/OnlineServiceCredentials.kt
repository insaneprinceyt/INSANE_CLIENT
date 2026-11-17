package com.ashmeet.hyperlauncher.utils

import android.content.Context
import com.ashmeet.hyperlauncher.screens.settings.preferences.LauncherPreferences
import net.ashmeet.hyperlauncher.R

object OnlineServiceCredentials {
    fun curseForgeKey(context: Context): String = ServiceKeys.resolve(
        LauncherPreferences.PREF_CURSEFORGE_API_KEY,
        context.getString(R.string.curseforge_api_key)
    )
}
