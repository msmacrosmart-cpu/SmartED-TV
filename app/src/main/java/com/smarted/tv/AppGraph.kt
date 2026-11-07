package com.smarted.tv

import android.content.Context
import com.smarted.tv.core.catalog.CatalogPager
import com.smarted.tv.core.catalog.ChannelGuide
import com.smarted.tv.core.data.AppRepository
import com.smarted.tv.core.data.PersistedState
import com.smarted.tv.core.playback.PlaybackResolver
import com.smarted.tv.core.protocol.StremioApi
import com.smarted.tv.core.protocol.StremioClient
import com.smarted.tv.data.PreferencesState
import okhttp3.OkHttpClient

/** Explicit dependency graph; tests inject the same ports used in production. */
class AppGraph(context: Context, persistence: PersistedState = PreferencesState(context), val api: StremioApi = StremioClient()) {
    val repository = AppRepository(persistence, api)
    val pager = CatalogPager(api)
    val guide = ChannelGuide(api)
    val resolver = PlaybackResolver(api)
}

class SmartEdApplication : android.app.Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
