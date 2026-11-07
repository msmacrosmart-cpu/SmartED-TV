package com.smarted.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.smarted.tv.ui.SmartEdApp
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import android.content.res.Configuration
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        val locale = Locale.forLanguageTag("pt-BR")
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        val graph = (application as SmartEdApplication).graph
        setContent {
            SmartEdApp(graph)
        }
    }
}

class SmartEdApplication : android.app.Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.initialize()
    }
}
