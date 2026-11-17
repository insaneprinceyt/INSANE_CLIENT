package com.ashmeet.hyperlauncher.utils.horizon

import android.content.Context
import android.util.AtomicFile
import com.ashmeet.hyperbackend.authenticator.AuthType
import com.ashmeet.hyperbackend.authenticator.accounts.Account
import com.ashmeet.hyperbackend.authenticator.impl.MicrosoftBackgroundLogin
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

object HorizonAuth {
    /** Called on a worker after resource installation, so sessions are fresh at launch. */
    fun prepareAuthArgs(context: Context, original: Account): List<String> {
        if (original.authType != AuthType.LOCAL && original.authType != AuthType.MICROSOFT) {
            throw IOException("Choose a Microsoft or local account for Rust Edition.")
        }
        var account = original
        if (account.authType == AuthType.MICROSOFT) {
            // Imported accounts may have an estimated expiry. Obtain a real session
            // and Xbox ID for this native launch instead of trusting that estimate.
            if (account.refreshToken.isNullOrBlank() || account.refreshToken == "0") {
                throw IOException("Sign in to your Microsoft account again before launching Rust Edition.")
            }
            account = MicrosoftBackgroundLogin.performRefresh(account)
        }
        val username = account.username?.takeIf { it.isNotBlank() }
            ?: throw IOException("The selected account has no player name.")
        if (account.authType == AuthType.LOCAL) return listOf("--name", username)
        if (account.accessToken.isNullOrBlank() || account.accessToken == "0"
            || account.expiresAt <= System.currentTimeMillis()) {
            throw IOException("Sign in to your Microsoft account again before launching Rust Edition.")
        }
        val uuid = account.profileId?.replace("-", "") ?: ""
        if (!uuid.matches(Regex("[a-fA-F0-9]{32}"))) throw IOException("Invalid Minecraft profile.")
        val sessions = File(context.filesDir, "horizon-sessions")
        if (!sessions.isDirectory && !sessions.mkdirs()) throw IOException("Cannot prepare account session.")
        // The engine consumes and deletes its session. Clean only our own stale files.
        sessions.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 120_000 }
            ?.forEach { it.delete() }
        val target = File(sessions, "session-${UUID.randomUUID()}.json")
        val session = JSONObject().apply {
            put("name", username)
            put("uuid", uuid)
            put("access_token", account.accessToken)
            put("user_type", "msa")
            put("xuid", account.xuid ?: "")
        }
        writePrivateJson(target, session)
        target.setReadable(false, false)
        target.setWritable(false, false)
        if (!target.setReadable(true, true) || !target.setWritable(true, true)) {
            target.delete()
            throw IOException("Cannot protect account session.")
        }
        return listOf("--session-file", target.absolutePath)
    }

    internal fun writePrivateJson(file: File, json: JSONObject) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        }
    }
}
