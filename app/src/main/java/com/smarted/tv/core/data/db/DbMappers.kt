package com.smarted.tv.core.data.db

import com.smarted.tv.core.addons.AddonKind
import com.smarted.tv.core.addons.InstalledAddon
import com.smarted.tv.core.addons.RepoStatus
import com.smarted.tv.core.addons.Repository
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.PublicSource
import com.smarted.tv.core.model.SourceFormat

/** Mapeadores entre entidades e modelos de domínio. */
object DbMappers {

    fun RepositoryEntity.toDomain() = com.smarted.tv.core.addons.Repository(
        id = id,
        name = name,
        url = url,
        isDefault = isDefault,
        enabled = enabled,
        addedAt = addedAt,
        lastSyncAt = lastSyncAt,
        addonCount = addonCount,
        status = runCatching { com.smarted.tv.core.addons.RepoStatus.valueOf(status) }
            .getOrDefault(com.smarted.tv.core.addons.RepoStatus.UNKNOWN),
        message = message,
        icon = icon,
        kinds = com.smarted.tv.core.util.SourceTargeting.parse(kinds),
    )

    fun com.smarted.tv.core.addons.Repository.toEntity() = RepositoryEntity(
        id = id,
        name = name,
        url = url,
        isDefault = isDefault,
        enabled = enabled,
        addedAt = addedAt,
        lastSyncAt = lastSyncAt,
        addonCount = addonCount,
        status = status.name,
        message = message,
        icon = icon,
        kinds = com.smarted.tv.core.util.SourceTargeting.encode(kinds),
    )

    fun InstalledAddonEntity.toDomain() = com.smarted.tv.core.addons.InstalledAddon(
        id = id,
        repositoryId = repositoryId,
        name = name,
        version = version,
        summary = summary,
        summaryLong = summaryLong,
        author = author,
        icon = icon,
        enabled = enabled,
        installedAt = installedAt,
        updatedAt = updatedAt,
        kind = runCatching { com.smarted.tv.core.addons.AddonKind.valueOf(kind) }
            .getOrDefault(com.smarted.tv.core.addons.AddonKind.REMOTE),
        types = types.split(',')
            .mapNotNull { runCatching { com.smarted.tv.core.model.MediaKind.valueOf(it.trim()) }.getOrNull() }
            .toSet(),
    )

    fun PublicSourceEntity.toDomain() = com.smarted.tv.core.model.PublicSource(
        id = id,
        name = name,
        url = url,
        // O campo `kind` guarda a marcação completa ("LIVE,MOVIE"): assim a
        // seleção de abas persiste sem coluna nova nem migração.
        kind = com.smarted.tv.core.util.SourceTargeting.parse(kind).firstOrNull()
            ?: com.smarted.tv.core.model.MediaKind.LIVE,
        kinds = com.smarted.tv.core.util.SourceTargeting.parse(kind),
        format = runCatching { com.smarted.tv.core.model.SourceFormat.valueOf(format) }
            .getOrDefault(com.smarted.tv.core.model.SourceFormat.M3U),
        group = group,
        logo = logo,
        enabled = enabled,
        priority = priority,
        lastCheckedMs = lastCheckedMs,
        healthy = healthy,
        lastError = lastError,
    )

    fun com.smarted.tv.core.model.PublicSource.toEntity(discoveredFrom: String? = null) = PublicSourceEntity(
        id = id,
        name = name,
        url = url,
        kind = com.smarted.tv.core.util.SourceTargeting.encode(targets),
        format = format.name,
        group = group,
        logo = logo,
        enabled = enabled,
        priority = priority,
        lastCheckedMs = lastCheckedMs,
        healthy = healthy,
        lastError = lastError,
        discoveredFrom = discoveredFrom,
    )
}
