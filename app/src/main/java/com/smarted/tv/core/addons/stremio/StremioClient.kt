package com.smarted.tv.core.addons.stremio

import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.net.Http

/**
 * Cliente HTTP do protocolo Stremio (addons de manifesto).
 *
 * Só existem duas operações de leitura:
 *   - [manifest] -> descobre nome, versão, escopos e catálogos do addon;
 *   - [catalog]  -> lê a estrutura de diretório/catálogo como JSON.
 *
 * Não há "busca engessada de torrentes" nem scraping: se o addon não publica um
 * catálogo no manifesto, o app simplesmente não o oferece.
 */
class StremioClient(private val http: Http) {

    /**
     * Lê e valida o manifesto a partir da URL digitada.
     *
     * Aceita tanto a base do addon quanto a URL completa do `manifest.json`.
     * O texto original é preservado em [StremioAddonSource.rawManifest] para que
     * o próximo start não precise de rede.
     */
    suspend fun manifest(urlOrManifest: String): StremioAddonSource {
        val base = StremioUrls.base(urlOrManifest)
            ?: throw StremioException("Endereço inválido: use uma URL http(s) de manifesto")
        val raw = http.getText(StremioUrls.manifestUrl(base))
        val manifest = StremioParser.manifest(raw)
        if (manifest.name.isBlank() && manifest.id.isBlank()) {
            throw StremioException("O manifesto não declara nome nem id")
        }
        if (manifest.usableCatalogs.isEmpty()) {
            throw StremioException("O addon não declara catálogos de filmes, séries ou canais")
        }
        return StremioAddonSource(
            url = base,
            manifest = manifest,
            installedAt = System.currentTimeMillis(),
            rawManifest = raw,
        )
    }

    /** Lê uma página de catálogo e converte nos itens do app. */
    suspend fun catalog(
        addon: StremioAddonSource,
        catalog: StremioCatalog,
        skip: Int = 0,
        search: String? = null,
    ): List<MediaItem> = catalogPage(addon, catalog, skip, search).map { it.toMediaItem(addon.addonId) }

    suspend fun catalogPage(
        addon: StremioAddonSource,
        catalog: StremioCatalog,
        skip: Int = 0,
        search: String? = null,
    ): List<StremioPreview> {
        val url = StremioUrls.catalogUrl(addon.url, catalog, skip = skip, search = search)
        return StremioParser.catalogPage(http.getText(url))
    }
}

/** Falha de addon Stremio com mensagem pronta para a UI (nunca stack trace). */
class StremioException(message: String) : Exception(message)
