package com.smarted.tv.ui.kodi

import android.content.Context
import android.content.Intent

/**
 * Reprodutor de app Android (prompt 07): abre o app ORIGINAL do Kodi instalado
 * no aparelho em TELA CHEIA a partir do Menu Kodi/Navegar.
 *
 * Otimização (anti-travamento): resolução barata do pacote, launch direto com
 * flags de launcher (retoma Kodi em execução em vez de reiniciar), e nenhum
 * trabalho pesado no caminho — o decoder que nosso player estiver usando é
 * liberado porque a UI que lança está sem player ativo.
 *
 * O Android não permite embutir a UI de outro app dentro do nosso (sandbox de
 * processo); lançá-lo em tela cheia é o caminho nativo — e é exatamente o que
 * o usuário vê: Kodi ocupando a tela inteira.
 */
object KodiLauncher {

    /** Pacotes oficiais (release e debug/nightly). */
    private val PACKAGES = listOf("org.xbmc.kodi", "org.xbmc.kodi.debug")

    /** Intent de launch do Kodi instalado, ou null quando ausente. */
    fun resolve(context: Context): Intent? {
        val pm = context.packageManager
        for (pkg in PACKAGES) {
            val intent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
            if (intent != null) return intent
        }
        // Fallback: launcher de TV cujo rótulo é "Kodi" (forks com outro id).
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(leanback, 0) }.getOrDefault(emptyList())
        val kodi = resolved.firstOrNull {
            it.activityInfo?.loadLabel(pm)?.toString().equals("Kodi", ignoreCase = true)
        } ?: return null
        return runCatching { pm.getLaunchIntentForPackage(kodi.activityInfo.packageName) }.getOrNull()
    }

    /**
     * Abre o Kodi em tela cheia (retoma se já estiver aberto). Retorna false
     * quando não está instalado — o chamador avisa honestamente; nada é simulado.
     */
    fun launchFullscreen(context: Context): Boolean {
        val intent = resolve(context) ?: return false
        // Flags de launcher: nova task + retoma a task existente (sem reiniciar
        // o Kodi e sem cinema de transição — resposta imediata ao clique).
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        context.startActivity(intent)
        return true
    }
}
