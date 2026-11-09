package com.smarted.tv.data.system

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.smarted.tv.data.repo.KodiScanner
import java.io.ByteArrayOutputStream

/**
 * [KodiScanner.Vfs] sobre uma árvore SAF (`content://…`) — mesma varredura
 * recursiva do caminho `file://`, percorrendo os DocumentFile da árvore com
 * cache de listagens por caminho lógico (raiz = "/").
 */
internal class DocumentVfs(
    private val context: Context,
    private val root: DocumentFile,
) : KodiScanner.Vfs {

    /** Listagem (filhos) por caminho lógico de diretório. */
    private val childrenCache = mutableMapOf<String, List<DocumentFile>>()

    private fun childrenOf(dirPath: String): List<DocumentFile> =
        childrenCache.getOrPut(dirPath) {
            val dir: DocumentFile = if (dirPath.isEmpty() || dirPath == "/") {
                root
            } else {
                val parentPath = dirPath.substringBeforeLast('/', "").ifEmpty { "/" }
                val name = dirPath.substringAfterLast('/')
                childrenOf(parentPath).firstOrNull { it.name == name } ?: return@getOrPut emptyList()
            }
            dir.listFiles()?.toList() ?: emptyList()
        }

    private fun resolve(path: String): DocumentFile? {
        if (path.isEmpty() || path == "/") return root
        val parentPath = path.substringBeforeLast('/', "").ifEmpty { "/" }
        val name = path.substringAfterLast('/')
        return childrenOf(parentPath).firstOrNull { it.name == name }
    }

    override fun list(path: String): List<KodiScanner.Entry> =
        childrenOf(path).mapNotNull { f -> f.name?.let { KodiScanner.Entry(it, f.isDirectory) } }

    override fun readText(path: String): String? {
        val f = resolve(path) ?: return null
        if (!f.isFile || f.length() > 600_000L) return null
        return runCatching {
            context.contentResolver.openInputStream(f.uri)?.use { input ->
                val out = ByteArrayOutputStream()
                input.copyTo(out)
                out.toByteArray().toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    override fun uri(path: String): String = resolve(path)?.uri?.toString() ?: ""
}
