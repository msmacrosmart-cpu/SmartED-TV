package com.smarted.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smarted.tv.core.data.PersistedState
import com.smarted.tv.core.model.AppData
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.stateStore: DataStore<Preferences> by preferencesDataStore(name = "smarted_addons_v2")

class PreferencesState(context: Context) : PersistedState {
    private val store = context.applicationContext.stateStore
    private val key = stringPreferencesKey("state")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override suspend fun read(): AppData {
        val prefs = store.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }.first()
        val raw = prefs[key] ?: return AppData()
        return try { json.decodeFromString<AppData>(raw) }
        catch (error: IllegalArgumentException) { throw IOException("Os dados locais estão corrompidos. Não foram substituídos silenciosamente.", error) }
    }
    override suspend fun write(data: AppData) {
        require(data.addons.size <= 3)
        val encoded = json.encodeToString(AppData.serializer(), data)
        store.edit { it[key] = encoded }
    }
}
