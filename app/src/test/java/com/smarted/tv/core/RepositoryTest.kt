package com.smarted.tv.core

import com.smarted.tv.core.data.AppRepository
import com.smarted.tv.core.data.InMemoryState
import com.smarted.tv.core.model.*
import com.smarted.tv.core.protocol.AddonException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RepositoryTest {
    @Test fun enforcesThreeManifestsEvenWithConcurrentInstalls() = runTest {
        val repo = AppRepository(InMemoryState(), FakeApi())
        repo.initialize()
        val results = (1..8).map { async { runCatching { repo.install("https://a$it.example/manifest.json", Scope.MOVIES) } } }.awaitAll()
        assertEquals(3, results.count { it.isSuccess })
        assertEquals(3, repo.data.value.addons.size)
        assertThrows(AddonException::class.java) { kotlinx.coroutines.runBlocking { repo.install("https://four.example/manifest.json", Scope.MOVIES) } }
    }
    @Test fun duplicateUrlDoesNotConsumeAnotherSlot() = runTest {
        val repo = AppRepository(InMemoryState(), FakeApi()); repo.initialize()
        repo.install("https://a.example", Scope.MOVIES)
        assertThrows(AddonException::class.java) { kotlinx.coroutines.runBlocking { repo.install("https://a.example/manifest.json", Scope.SERIES) } }
        assertEquals(1, repo.data.value.addons.size)
    }
    @Test fun favoritesAndAddonsSurvivePersistenceReload() = runTest {
        val store = InMemoryState(); val repo = AppRepository(store, FakeApi()); repo.initialize()
        val addon = repo.install("https://a.example/manifest.json", Scope.MOVIES)
        repo.favorite(testItem(addon, "movie1"))
        repo.saveProgress(testItem(addon, "movie1"), "movie1", 1000, 5000)
        val reloaded = AppRepository(store, FakeApi()); reloaded.initialize()
        assertEquals(addon.url, reloaded.data.value.addons.single().url)
        assertEquals(1, reloaded.data.value.favorites.size)
        assertEquals(1000L, reloaded.progress(testItem(addon, "movie1"), "movie1")!!.positionMs)
        reloaded.remove(addon.url)
        assertTrue(reloaded.data.value.addons.isEmpty())
        assertTrue(reloaded.data.value.favorites.isEmpty())
        assertTrue(reloaded.data.value.progress.isEmpty())
    }
    @Test fun favoriteToggleIsIdempotentAcrossToggles() = runTest {
        val repo = AppRepository(InMemoryState(), FakeApi()); repo.initialize()
        val addon = repo.install("https://a.example", Scope.SERIES); val item = testItem(addon, "s")
        repo.favorite(item); repo.favorite(item)
        assertTrue(repo.data.value.favorites.isEmpty())
    }
    @Test fun scopeChangeDropsOnlyOutOfScopeFavorites() = runTest {
        val repo = AppRepository(InMemoryState(), FakeApi()); repo.initialize()
        val addon = repo.install("https://a.example", Scope.MOVIES)
        repo.favorite(testItem(addon, "m"))
        repo.changeScope(addon.url, Scope.SERIES)
        assertEquals(Scope.SERIES, repo.data.value.addons.single().scope)
        assertTrue(repo.data.value.favorites.isEmpty())
    }
    @Test fun failedPersistenceDoesNotPublishUnsavedState() = runTest {
        val store = object : com.smarted.tv.core.data.PersistedState {
            override suspend fun read() = AppData()
            override suspend fun write(data: AppData) { throw java.io.IOException("disk full") }
        }
        val repo = AppRepository(store, FakeApi()); repo.initialize()
        assertThrows(java.io.IOException::class.java) { kotlinx.coroutines.runBlocking { repo.install("https://a.example", Scope.MOVIES) } }
        assertTrue(repo.data.value.addons.isEmpty())
    }
    @Test fun preferencesPersistAndValidateRanges() = runTest {
        val store = InMemoryState(); val repo = AppRepository(store, FakeApi()); repo.initialize()
        repo.updatePreferences { it.copy(audioLanguage = "en", subtitleSizePercent = 125, autoplayNextEpisode = false) }
        val reloaded = AppRepository(store, FakeApi()); reloaded.initialize()
        assertEquals("en", reloaded.data.value.preferences.audioLanguage)
        assertEquals(125, reloaded.data.value.preferences.subtitleSizePercent)
        assertFalse(reloaded.data.value.preferences.autoplayNextEpisode)
        assertThrows(IllegalArgumentException::class.java) { UserPreferences(subtitleSizePercent = 10) }
    }
    @Test fun clearProgressKeepsAddonsAndFavorites() = runTest {
        val repo = AppRepository(InMemoryState(), FakeApi()); repo.initialize()
        val addon = repo.install("https://a.example", Scope.MOVIES); val item = testItem(addon, "m")
        repo.favorite(item); repo.saveProgress(item, "m", 1000, 5000)
        repo.clearProgress()
        assertEquals(1, repo.data.value.addons.size)
        assertEquals(1, repo.data.value.favorites.size)
        assertTrue(repo.data.value.progress.isEmpty())
    }
    @Test fun progressIsBoundedAndNeverStoredForLive() = runTest {
        val repo = AppRepository(InMemoryState(), FakeApi()); repo.initialize()
        val movie = testAddon(Scope.MOVIES); val live = testAddon(Scope.CHANNELS)
        repeat(220) { repo.saveProgress(testItem(movie, "$it"), "$it", it.toLong(), 5000) }
        repo.saveProgress(testItem(live, "live"), "live", 100, 0)
        assertEquals(200, repo.data.value.progress.size)
        assertTrue(repo.data.value.progress.keys.none { it.contains("|tv|") })
    }
}
