package com.smarted.tv.ui.kodi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt 07 — reprodutor de app Android: o launch do Kodi original precisa ser
 * SEGURO quando o Kodi não está instalado (caso do emulador): resolve=null e
 * launch=false, sem crash e sem simulação (a UI mostra o aviso honesto).
 */
@RunWith(AndroidJUnit4::class)
class KodiLauncherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun resolveAndLaunchAreSafeWithoutKodiInstalled() {
        // Emulador AOSP não traz o Kodi: ausência tratada com null/false.
        val intent = KodiLauncher.resolve(context)
        if (intent == null) {
            assertFalse(KodiLauncher.launchFullscreen(context))
        }
        // Com Kodi instalado, resolve devolve um intent de launch válido.
        intent?.let {
            assertFalse(it.`package`.isNullOrBlank())
        }
    }
}
