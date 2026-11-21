package com.cookiepatrol.detect

import android.content.Context
import androidx.core.content.edit

internal enum class RemoteCheckScope { OFF, SUSPICIOUS, ALL_MESSAGE_LINKS }

object PrivacySettings {
    fun remoteEnabled(context: Context): Boolean = scope(context) != RemoteCheckScope.OFF

    internal fun scope(context: Context): RemoteCheckScope {
        val settings = context.getSharedPreferences("privacy", Context.MODE_PRIVATE)
        if (!settings.getBoolean("remote_url_checks", false)) return RemoteCheckScope.OFF
        return if (settings.getBoolean("all_message_links", false)) RemoteCheckScope.ALL_MESSAGE_LINKS
        else RemoteCheckScope.SUSPICIOUS
    }

    fun setRemoteEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("privacy", Context.MODE_PRIVATE).edit {
            putBoolean("remote_url_checks", enabled)
            putBoolean("all_message_links", enabled)
        }
    }
}
