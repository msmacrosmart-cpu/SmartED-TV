package com.smarted.tv.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.smarted.tv.core.data.AppGraph
import com.smarted.tv.data.system.isSystemTelevision
import com.smarted.tv.core.model.DeviceMode
import com.smarted.tv.ui.mode.ModeSelectScreen
import com.smarted.tv.ui.nav.MainShell
import com.smarted.tv.ui.theme.SmartEDTheme

/** Primeira execução → escolha Celular/TV. Depois → shell adaptativo. */
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val prefs = AppGraph.modePrefs
    var mode by remember { mutableStateOf(prefs.get()) }
    val suggested = remember { if (ctx.isSystemTelevision()) DeviceMode.TV else DeviceMode.PHONE }

    val m = mode
    if (m == null) {
        SmartEDTheme(suggested) {
            ModeSelectScreen(suggested = suggested) { chosen ->
                prefs.set(chosen)
                mode = chosen
            }
        }
    } else {
        SmartEDTheme(m) {
            MainShell(mode = m, onChangeMode = { prefs.clear(); mode = null })
        }
    }
}
