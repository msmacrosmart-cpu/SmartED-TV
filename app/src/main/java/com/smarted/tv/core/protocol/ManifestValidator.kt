package com.smarted.tv.core.protocol

import com.smarted.tv.core.model.Manifest
import com.smarted.tv.core.model.Scope

object ManifestValidator {
    fun validate(manifest: Manifest, scope: Scope) {
        if (manifest.configurationRequired) {
            throw AddonException("Este addon precisa ser configurado pelo provedor antes da instalação. Abra o configurador, conclua a configuração e cole a URL final do manifesto.")
        }
        val catalogs = manifest.catalogsFor(scope)
        if (catalogs.isNotEmpty()) return
        // A stream-only addon is valid in Stremio and may enrich another addon in this scope.
        val compatibleResource = manifest.resources.any { resource ->
            resource.name in setOf("stream", "meta", "catalog") &&
                (resource.types.isEmpty() || resource.types.any { it in scope.wireTypes })
        }
        if (!compatibleResource) {
            val label = when (scope) { Scope.CHANNELS -> "Canais"; Scope.MOVIES -> "Filmes"; Scope.SERIES -> "Séries" }
            throw AddonException("O manifesto não oferece catálogos ou recursos compatíveis com $label. Escolha um escopo declarado ou use outro addon.")
        }
    }
}
