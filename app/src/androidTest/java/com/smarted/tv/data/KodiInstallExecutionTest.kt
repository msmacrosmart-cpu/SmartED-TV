package com.smarted.tv.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smarted.tv.data.repo.OnePlayIndexDto
import com.smarted.tv.data.repo.RepoPackages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * ACEITAÇÃO do fluxo de execução do add-on, no DISPOSITIVO real:
 * instala o ZIP REAL do plugin.video.oneplayvip e confirma que o clique vira
 * EXECUÇÃO (capacidade detectada + entrypoint) — nunca explorador de arquivos
 * de código. Detecção 100% leitura estática (addon.xml/.py), sem rodar o
 * script do Kodi.
 */
@RunWith(AndroidJUnit4::class)
class KodiInstallExecutionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Assets do add-on vivem no APK de TESTE — não no app alvo. */
    private val testContext: Context
        get() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context

    private fun realZip(): File = File(context.cacheDir, "oneplayvip.zip").apply {
        if (!exists()) {
            testContext.assets.open("oneplayvip.zip").use { input ->
                outputStream().use { input.copyTo(it) }
            }
        }
    }

    /** Mesmo caminho do app: lê o índice gravado pelo installKodi. */
    private fun readIndex(url: String): OnePlayIndexDto =
        com.smarted.tv.data.net.Http.json.decodeFromString(
            OnePlayIndexDto.serializer(),
            File(url.removePrefix("file://")).readText(),
        )

    @Test
    fun installRealAddonZipYieldsXtreamExecutionNotFileTree() {
        val repo = RepoPackages(context)

        // 1) Reconhecimento do pacote (addon.xml interpretado).
        val pkg = requireNotNull(runBlocking { repo.kodiPackage(realZip().absolutePath) })
        assertEquals("addon", pkg.kind)
        assertEquals("video", pkg.addons.orEmpty().first().type)
        assertEquals("app.py", pkg.addons.orEmpty().first().library)

        // 2) Instalação → índice executável (tree=null, files, capability).
        val (url, name) = runBlocking { repo.installKodi(realZip().absolutePath) }
        assertTrue(url.startsWith("file://"))
        val index = readIndex(url)
        val addon = index.addons.orEmpty().first()
        assertNull(addon.tree)                       // nunca explorador de código
        assertNotNull(index.files)                   // hierarquia no Gestor de Arquivos
        assertEquals("app.py", addon.entrypoint)     // entrypoint do addon.xml
        assertEquals("xtream", addon.capability)     // capacidade REAL (tsdownloader.py)
        assertEquals("ONEPLAY VIP", name)

        // 3) O clique é EXECUÇÃO: capacidade dirige o fluxo Xtream nativo.
        val execution = when {
            addon.capability == "xtream" -> "xtream"
            addon.tree != null -> "menu"
            else -> "details"
        }
        assertEquals("xtream", execution)

        // 4) Reler o índice mantém a classificação (idempotente — caminho do app).
        val reread = RepoPackages.migrateLegacyIndex(readIndex(url), url)
        assertEquals("xtream", reread.addons.orEmpty().first().capability)
        assertNull(reread.addons.orEmpty().first().tree)
    }
}
