package com.acn.store.data

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("acn_store", Context.MODE_PRIVATE)

    fun authorName(context: Context): String = of(context).getString("author_name", "").orEmpty()
    fun setAuthorName(context: Context, name: String) = of(context).edit().putString("author_name", name.trim()).apply()

    fun updateNotifications(context: Context): Boolean = of(context).getBoolean("update_notifications", true)
    fun setUpdateNotifications(context: Context, enabled: Boolean) = of(context).edit().putBoolean("update_notifications", enabled).apply()

    fun autoUpdate(context: Context): Boolean = of(context).getBoolean("auto_update", true)
    fun setAutoUpdate(context: Context, enabled: Boolean) = of(context).edit().putBoolean("auto_update", enabled).apply()

    fun wifiOnly(context: Context): Boolean = of(context).getBoolean("wifi_only", false)
    fun setWifiOnly(context: Context, enabled: Boolean) = of(context).edit().putBoolean("wifi_only", enabled).apply()
}
