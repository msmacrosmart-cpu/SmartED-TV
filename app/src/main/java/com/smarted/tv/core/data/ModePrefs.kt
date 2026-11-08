package com.smarted.tv.core.data

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import com.smarted.tv.core.model.DeviceMode

class ModePrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("smarted_prefs", Context.MODE_PRIVATE)

    fun get(): DeviceMode? =
        p.getString("mode", null)?.let { runCatching { DeviceMode.valueOf(it) }.getOrNull() }

    fun set(mode: DeviceMode) { p.edit().putString("mode", mode.name).apply() }
    fun clear() { p.edit().remove("mode").apply() }
}

/** Sugestão automática para pré-focar o card correto na tela de escolha. */
fun Context.isSystemTelevision(): Boolean =
    (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType ==
        Configuration.UI_MODE_TYPE_TELEVISION
