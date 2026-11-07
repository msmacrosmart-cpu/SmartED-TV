package com.smarted.tv

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.smarted.tv.ui.SmartEdRoot
import java.util.Locale

/**
 * Única Activity do app.
 *
 * - Orientação travada em `sensorLandscape` no manifest (requisito).
 * - Tela sempre ligada durante a reprodução (FLAG_KEEP_SCREEN_ON).
 * - Modo imersivo (esconde barras), como um media center de TV.
 * - Idioma fixo em **português do Brasil**: o app é 100% pt-BR por decisão de
 *   produto. Forçar via `createConfigurationContext` garante que formatação de
 *   datas/números e qualquer recurso localizado nunca apareçam em inglês,
 *   mesmo em TV box com o sistema em outro idioma.
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(withPortuguese(newBase))
    }

    /** Devolve um Context com locale pt-BR fixo. */
    private fun withPortuguese(base: android.content.Context): android.content.Context {
        val locale = Locale.forLanguageTag("pt-BR")
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val graph = (application as SmartEdApplication).graph

        setContent {
            SmartEdRoot(graph = graph)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }
}
