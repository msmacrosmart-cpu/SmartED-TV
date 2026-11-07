package com.smarted.tv.core.addons.stremio

import android.content.Context
import android.content.SharedPreferences
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.net.Http
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado observável dos addons Stremio instalados.
 *
 * [bindings] é a fonte da verdade do que aparece em cada aba: nenhum catálogo
 * entra em Filmes/Séries/Canais sem um [CatalogBinding] explícito.
 */
data class StremioState(
    val addons: List<StremioAddonSource> = emptyList(),
    val bindings: List<CatalogBinding> = emptyList(),
    val loading: Boolean = false,
    val status: String = "",
    val error: String? = null,
) {
    val isEmpty: Boolean get() = addons.isEmpty()

    fun bindingsFor(kind: MediaKind): List<CatalogBinding> =
        bindings.filter { it.scope.kind == kind }

    fun isBound(addonUrl: String, catalogId: String, kind: MediaKind): Boolean =
        bindings.any { it.addonUrl == addonUrl && it.catalogId == catalogId && it.scope.kind == kind }

    /** Trilhas que alimentam uma aba: (addon, catálogo) na ordem dos vínculos. */
    fun shelvesFor(kind: MediaKind): List<Pair<StremioAddonSource, StremioCatalog>> =
        StremioScopePolicy.resolve(bindings, addons, kind)

    /** Catálogos do addon que podem ser vinculados a uma aba. */
    fun bindableCatalogs(addon: StremioAddonSource, kind: MediaKind): List<StremioCatalog> =
        StremioScopePolicy.bindable(addon.catalogs, kind)
}

/**
 * Registro persistente de addons Stremio.
 *
 * Guarda em **SharedPreferences** o texto original de cada manifesto e a lista
 * de vínculos de aba — sem rede no start e sem tocar no banco Room.
 *
 * É deliberadamente independente do repositório estilo Kodi (`AddonManager`):
 * as duas arquiteturas coexistem. O Kodi traz addons empacotados do Vikings; o
 * Stremio traz catálogos HTTP/JSON declarados por manifesto.
 */
class StremioRegistry(
    context: Context,
    http: Http,
) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val client = StremioClient(http)

    private val _state = MutableStateFlow(
        StremioState(addons = readAddons(), bindings = readBindings()),
    )
    val state: StateFlow<StremioState> = _state.asStateFlow()

    /**
     * Instala um addon a partir da URL do manifesto (ou da base) e vincula por
     * padrão os catálogos ao escopo **declarado por eles**.
     *
     * O vínculo automático é apenas um atalho inicial: o usuário liga/desliga
     * cada catálogo na tela de addons Stremio.
     *
     * @return `null` em caso de sucesso, ou a mensagem de erro.
     */
    suspend fun install(url: String): String? {
        _state.value = _state.value.copy(loading = true, error = null, status = "Lendo manifesto…")
        val addon = runCatching { client.manifest(url) }.getOrElse { failure ->
            val message = failure.message ?: "Não foi possível ler o manifesto"
            _state.value = _state.value.copy(loading = false, error = message, status = "")
            return message
        }
        val addons = _state.value.addons.filterNot { it.url == addon.url } + addon
        val bindings = _state.value.bindings.filterNot { it.addonUrl == addon.url } +
            defaultBindings(addon)
        commit(addons, bindings, status = addon.name + " instalado")
        return null
    }

    fun remove(addonUrl: String) {
        val addons = _state.value.addons.filterNot { it.url == addonUrl }
        val bindings = _state.value.bindings.filterNot { it.addonUrl == addonUrl }
        commit(addons, bindings, status = "Addon removido")
    }

    /** Liga/desliga o vínculo de um catálogo com uma aba. */
    fun toggleBinding(addon: StremioAddonSource, catalog: StremioCatalog, target: MediaKind) {
        if (!StremioScopePolicy.canBind(catalog, target)) {
            _state.value = _state.value.copy(
                error = "Este catálogo é de " + (catalog.scope?.title ?: "tipo desconhecido") +
                    " e não alimenta " + targetLabel(target),
            )
            return
        }
        val key = addon.url + "|" + catalog.id
        val current = _state.value.bindings
        val next = if (current.any { it.key == key }) {
            current.filterNot { it.key == key }
        } else {
            val scope = AddonScope.fromKind(target) ?: return
            current + CatalogBinding(
                addonUrl = addon.url,
                catalogId = catalog.id,
                type = catalog.type,
                scope = scope,
                addonName = addon.name,
                catalogName = catalog.displayName,
            )
        }
        commit(_state.value.addons, next, status = "Vínculos atualizados")
    }

    /** Conteúdo de uma trilha vinculada (paginado quando o addon aceita `skip`). */
    suspend fun items(binding: CatalogBinding, skip: Int = 0, search: String? = null): List<MediaItem> {
        val addon = _state.value.addons.firstOrNull { it.url == binding.addonUrl } ?: return emptyList()
        val catalog = addon.catalogs.firstOrNull { it.id == binding.catalogId } ?: return emptyList()
        return client.catalog(addon, catalog, skip = skip, search = search)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    // ------------------------------------------------------------ internos

    private fun defaultBindings(addon: StremioAddonSource): List<CatalogBinding> =
        addon.catalogs.mapNotNull { catalog ->
            val scope = catalog.scope ?: return@mapNotNull null
            CatalogBinding(
                addonUrl = addon.url,
                catalogId = catalog.id,
                type = catalog.type,
                scope = scope,
                addonName = addon.name,
                catalogName = catalog.displayName,
            )
        }

    private fun commit(
        addons: List<StremioAddonSource>,
        bindings: List<CatalogBinding>,
        status: String,
    ) {
        val editor = prefs.edit()
        editor.putString(KEY_INDEX, addons.joinToString("\n") { it.url })
        addons.forEach { addon ->
            editor.putString(manifestKey(addon.url), addon.rawManifest)
        }
        editor.putString(KEY_BINDINGS, bindings.joinToString("\n") { encodeBinding(it) })
        editor.apply()
        _state.value = StremioState(
            addons = addons,
            bindings = bindings,
            loading = false,
            status = status,
            error = null,
        )
    }

    private fun readAddons(): List<StremioAddonSource> {
        val index = prefs.getString(KEY_INDEX, null).orEmpty()
        if (index.isBlank()) return emptyList()
        return index.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { url ->
                // Um manifesto que deixou de parsear (formato antigo) é
                // descartado silenciosamente em vez de derrubar a tela.
                val raw = prefs.getString(manifestKey(url), null).orEmpty()
                if (raw.isBlank()) return@mapNotNull null
                val manifest = runCatching { StremioParser.manifest(raw) }.getOrNull()
                    ?: return@mapNotNull null
                StremioAddonSource(url = url, manifest = manifest, rawManifest = raw)
            }
    }

    private fun readBindings(): List<CatalogBinding> {
        val raw = prefs.getString(KEY_BINDINGS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").mapNotNull { line -> decodeBinding(line) }
    }

    /**
     * Formato de linha: `url<TAB>catalogId<TAB>type<TAB>scope<TAB>nomeAddon<TAB>nomeCatalogo`.
     * Simples de propósito: sem dependência de serialização e legível no
     * `adb shell run-as` quando algo dá errado.
     */
    private fun encodeBinding(binding: CatalogBinding): String = listOf(
        binding.addonUrl,
        binding.catalogId,
        binding.type,
        binding.scope.name,
        binding.addonName,
        binding.catalogName,
    ).joinToString("\t")

    private fun decodeBinding(line: String): CatalogBinding? {
        val parts = line.split("\t")
        if (parts.size < 4) return null
        val scope = AddonScope.fromName(parts[3]) ?: return null
        return CatalogBinding(
            addonUrl = parts[0],
            catalogId = parts[1],
            type = parts[2],
            scope = scope,
            addonName = parts.getOrNull(4).orEmpty(),
            catalogName = parts.getOrNull(5).orEmpty(),
        )
    }

    private fun manifestKey(url: String): String = KEY_MANIFEST_PREFIX + url

    private fun targetLabel(kind: MediaKind): String = when (kind) {
        MediaKind.LIVE -> "Canais"
        MediaKind.MOVIE -> "Filmes"
        MediaKind.SERIES -> "Séries"
        MediaKind.EPISODE -> "Episódios"
        MediaKind.UNKNOWN -> "esta aba"
    }

    private companion object {
        const val PREFS = "smarted_stremio_addons"
        const val KEY_INDEX = "addons.urls"
        const val KEY_BINDINGS = "addons.bindings"
        const val KEY_MANIFEST_PREFIX = "addons.manifest."
    }
}
