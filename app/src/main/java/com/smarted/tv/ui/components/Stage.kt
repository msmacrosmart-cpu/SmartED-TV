package com.smarted.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarted.tv.R
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.util.MediaMeta
import com.smarted.tv.ui.theme.Ed
import com.smarted.tv.ui.theme.EdEasing

/**
 * Palco cinematográfico da Home.
 *
 * É a peça central do novo layout: a arte de fundo **sangra até as bordas** da
 * tela (inclusive por baixo da doca lateral), três véus em degradê constroem o
 * glassmorphism e o conteúdo — título, metadados, sinopse e ações — entra por
 * baixo, como numa vitrine de cinema.
 *
 * Nada de painel com cantos arredondados no topo: o palco se funde com a página
 * pelo degradê inferior, que é o que faz a Home parecer um app de streaming e
 * não um dashboard.
 */
@Composable
fun StageHero(
    item: MediaItem,
    eyebrow: String,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    playFocusRequester: FocusRequester? = null,
    progress: Float? = null,
    backdrops: List<String> = emptyList(),
    insetStart: Dp = 0.dp,
    refreshedLabel: String? = null,
) {
    val palette = Ed.palette
    val type = Ed.type

    // Pool de artes: capa principal primeiro, depois backdrops relacionados.
    val artPool = remember(item.id, backdrops) {
        (listOfNotNull(item.backdrop, item.poster) + backdrops).distinct().take(6)
    }
    var artIndex by remember(item.id) { mutableIntStateOf(0) }
    LaunchedEffect(artPool) {
        if (artPool.size <= 1) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(9_000L)
            artIndex = (artIndex + 1) % artPool.size
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = maxWidth > 900.dp
        val titleStyle = if (wide) {
            type.display.copy(fontSize = 44.sp, lineHeight = 50.sp)
        } else {
            type.display.copy(fontSize = 32.sp, lineHeight = 38.sp)
        }
        val synopsisWidth = if (wide) 0.56f else 0.72f

        Box(Modifier.fillMaxSize().background(palette.bg)) {
            // ---- arte ----
            key(artIndex) {
                val fade = remember(artIndex) { Animatable(0f) }
                LaunchedEffect(artIndex) {
                    fade.animateTo(1f, tween(durationMillis = 750, easing = EaseOutCubic))
                }
                Artwork(
                    url = artPool.getOrNull(artIndex) ?: item.backdrop ?: item.poster,
                    title = item.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = fade.value
                            // respiro lento (Ken Burns): dá vida ao banner parado
                            val grow = 1f + 0.03f * fade.value
                            scaleX = grow
                            scaleY = grow
                        },
                    contentScale = ContentScale.Crop,
                )
            }

            // ---- véus (glassmorphism) ----
            // 1. topo: mantém doca/relógio legíveis sobre qualquer arte
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to palette.bg.copy(alpha = 0.92f),
                            0.30f to palette.bg.copy(alpha = 0.20f),
                            0.62f to Color.Transparent,
                        ),
                    ),
            )
            // 2. laterais + base: o texto precisa de contraste absoluto
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to palette.bg.copy(alpha = 0.94f),
                            0.38f to palette.bg.copy(alpha = 0.62f),
                            0.78f to Color.Transparent,
                        ),
                    ),
            )
            // 3. base: funde o palco com a lista de trilhas
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            0.86f to palette.bg.copy(alpha = 0.82f),
                            1f to palette.bg,
                        ),
                    ),
            )

            // ---- relógio de atualização (canto superior direito) ----
            refreshedLabel?.let { label ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 28.dp, top = 20.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(palette.glassDark, RoundedCornerShape(percent = 50))
                        .border(1.dp, palette.glassBorder, RoundedCornerShape(percent = 50))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = EdIcons.refresh,
                        contentDescription = null,
                        tint = palette.onArt.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = label,
                        style = type.caption,
                        color = palette.onArt.copy(alpha = 0.9f),
                        maxLines = 1,
                    )
                }
            }

            // ---- conteúdo ----
            StageContent(
                item = item,
                eyebrow = eyebrow,
                onPlay = onPlay,
                onDetails = onDetails,
                playFocusRequester = playFocusRequester,
                titleStyle = titleStyle,
                synopsisFraction = synopsisWidth,
                insetStart = insetStart,
                modifier = Modifier.align(Alignment.BottomStart),
            )

            // ---- progresso de exibição ----
            if (progress != null && progress > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(palette.onArt.copy(alpha = 0.18f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(palette.onArt),
                    )
                }
            }
        }
    }
}

@Composable
private fun StageContent(
    item: MediaItem,
    eyebrow: String,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    playFocusRequester: FocusRequester?,
    titleStyle: androidx.compose.ui.text.TextStyle,
    synopsisFraction: Float,
    insetStart: Dp,
    modifier: Modifier = Modifier,
) {
    val palette = Ed.palette
    val type = Ed.type
    val playInteraction = edInteraction(onClick = onPlay, focusRequester = playFocusRequester)
    val detailsInteraction = edInteraction(onClick = onDetails)

    val age = MediaMeta.ageRating(item)
    val runtime = MediaMeta.runtimeLabel(item.durationSec ?: item.details?.runtimeMin?.let { it * 60L })
    val genres = MediaMeta.genres(item, 3).joinToString(", ")
    val synopsis = MediaMeta.synopsis(item, 300)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = insetStart + 30.dp, end = 48.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(palette.onArt, RoundedCornerShape(percent = 50)),
            )
            Text(
                text = eyebrow.uppercase(),
                style = type.eyebrow,
                color = palette.onArt.copy(alpha = 0.86f),
                maxLines = 1,
            )
        }

        Text(
            text = item.title,
            style = titleStyle,
            color = palette.onArt,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 880.dp),
        )

        // metadados completos antes da reprodução: selo, ano, duração, gêneros
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            age?.let { AgeBadge(it) }
            item.year?.takeIf { it > 1900 }?.let {
                Text(it.toString(), style = type.bodySmall, color = palette.onArt.copy(alpha = 0.82f))
            }
            runtime?.let {
                Text(it, style = type.bodySmall, color = palette.onArt.copy(alpha = 0.82f))
            }
            if (genres.isNotBlank()) {
                Text(
                    text = genres,
                    style = type.bodySmall,
                    color = palette.onArt.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.rating?.takeIf { it > 0 }?.let { RatingPill(it) }
        }

        if (synopsis.isNotBlank()) {
            Text(
                text = synopsis,
                style = type.body,
                color = palette.onArt.copy(alpha = 0.78f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(synopsisFraction),
            )
        }

        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StageAction(
                label = stringResource(R.string.action_play),
                icon = EdIcons.play,
                primary = true,
                interaction = playInteraction,
            )
            StageAction(
                label = stringResource(R.string.action_details),
                icon = EdIcons.info,
                primary = false,
                interaction = detailsInteraction,
            )
            if (item.kind == MediaKind.LIVE) {
                LiveDot()
            }
        }
    }
}

/** Selo de classificação indicativa (caixa de vidro branca). */
@Composable
fun AgeBadge(label: String, modifier: Modifier = Modifier) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.onArt.copy(alpha = 0.16f), shape)
            .border(1.dp, palette.onArt.copy(alpha = 0.5f), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = type.label.copy(fontWeight = FontWeight.Bold),
            color = palette.onArt,
            maxLines = 1,
        )
    }
}

/**
 * Botão de ação do palco.
 *
 * [primary] = verdadeiro vira a pílula **branca** (texto escuro), o botão
 * "Assistir". O foco do D-pad acrescenta um anel branco de 3dp separado por um
 * respiro de 3dp, então o destaque é visível mesmo sobre um botão já branco.
 */
@Composable
fun StageAction(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    interaction: EdInteraction,
    modifier: Modifier = Modifier,
) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(percent = 50)
    val focused = interaction.isFocused

    val fill = when {
        primary -> palette.onArt
        focused -> palette.glassStrong
        else -> palette.glassDark
    }
    val content = if (primary) palette.onAccent else palette.onArt
    val ringShape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .clip(ringShape)
            .then(if (focused) Modifier.border(3.dp, palette.onArt, ringShape) else Modifier)
            .padding(3.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(fill, shape)
                .then(
                    if (!primary) Modifier.border(1.dp, palette.onArt.copy(alpha = 0.35f), shape) else Modifier,
                )
                .then(interaction.modifier)
                .padding(horizontal = if (primary) 26.dp else 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = content,
                maxLines = 1,
            )
        }
    }
}

/**
 * Cabeçalho de uma trilha ("Em destaque para você", "Top 10 em alta").
 * Título grande à esquerda, ação discreta à direita.
 */
@Composable
fun ShelfHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    big: Boolean = false,
) {
    val palette = Ed.palette
    val type = Ed.type
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = if (big) type.sectionTitle.copy(fontSize = 21.sp) else type.rowTitle.copy(fontSize = 15.sp),
            color = if (big) palette.onArt else palette.fg2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = type.caption, color = palette.fg3, maxLines = 1)
        }
        if (actionLabel != null && onAction != null) {
            ShelfActionPill(label = actionLabel, onClick = onAction, focusRequester = focusRequester)
        }
    }
}

/** Pílula de ação do cabeçalho — vidro discreto, foco branco puro. */
@Composable
private fun ShelfActionPill(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(percent = 50)
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val focused = interaction.isFocused
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (focused) palette.glassStrong else Color.Transparent, shape)
            .then(if (focused) Modifier.border(2.dp, palette.onArt, shape) else Modifier)
            .then(interaction.modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = type.caption.copy(fontWeight = FontWeight.Medium),
            color = if (focused) palette.onArt else palette.fg3,
            maxLines = 1,
        )
        Icon(
            imageVector = EdIcons.chevron,
            contentDescription = null,
            tint = if (focused) palette.onArt else palette.fg3,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Compat: a versão antiga do palco tinha o mesmo nome usado em outras telas. */
@Composable
fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    interaction: EdInteraction,
    modifier: Modifier = Modifier,
) = StageAction(label = label, icon = icon, primary = true, interaction = interaction, modifier = modifier)

/** Compat: botão secundário do palco. */
@Composable
fun SecondaryActionButton(
    label: String,
    icon: ImageVector,
    interaction: EdInteraction,
    modifier: Modifier = Modifier,
) = StageAction(label = label, icon = icon, primary = false, interaction = interaction, modifier = modifier)

/**
 * Selo de nota unificado (usado no palco e nos cartões).
 * Herda o formato antigo de `RatingPill` para não duplicar desenho.
 */
@Composable
fun RatingPill(rating: Double, modifier: Modifier = Modifier) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(palette.bg.copy(alpha = 0.62f), shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = EdIcons.star,
            contentDescription = null,
            tint = palette.onArt,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = String.format("%.1f", rating),
            style = type.label.copy(fontWeight = FontWeight.SemiBold),
            color = palette.onArt,
        )
    }
}
