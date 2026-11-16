package com.cookiepatrol.detect

import android.content.Context
import androidx.core.content.edit

object PrivacySettings {
    fun remoteEnabled(context: Context) = context.getSharedPreferences("privacy", Context.MODE_PRIVATE)
        .getBoolean("remote_url_checks", false)
    fun setRemoteEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("privacy", Context.MODE_PRIVATE).edit {
            putBoolean("remote_url_checks", enabled)
        }
    }
}
