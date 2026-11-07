package com.smarted.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.util.MediaMeta
import com.smarted.tv.ui.theme.Ed
import com.smarted.tv.ui.theme.EdEasing

/** Dimensões das trilhas da nova Home. */
object ShelfMetrics {
    /** Largura de um cartão de trilha (16:10) — cabe 6 na tela de 1080p. */
    val cardWidth: Dp = 236.dp
    val cardRatio: Float = 10f / 16f
    /** Altura total do cartão incluindo a legenda interna. */
    val cardHeight: Dp = 148.dp
    val gap: Dp = 16.dp
}

/**
 * Cartão de mídia da trilha horizontal.
 *
 * A grande mudança em relação ao pôster antigo: **título e metadados vivem
 * dentro da arte**, sobre um degradê escuro, como nos streamers. O cartão é
 * largo (16:10), então a fileira lê como um mural de capas — e não como uma
 * grade de pôsteres.
 *
 * Foco do D-pad: anel branco puro de 2dp + leve crescimento de 3%, sem sombra
 * colorida em nenhum momento.
 */
@Composable
fun FeatureCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = ShelfMetrics.cardWidth,
    rank: Int? = null,
    progress: Float? = null,
    overline: String? = null,
    focusRequester: FocusRequester? = null,
    dimmed: Boolean = false,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val focused = interaction.isFocused

    val scaleFactor by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(180, easing = EdEasing),
        label = "card-scale",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 0.dp,
        animationSpec = tween(160, easing = EdEasing),
        label = "card-ring",
    )
    val shape = RoundedCornerShape(14.dp)
    val meta = MediaMeta.shortMeta(item)

    Column(
        modifier = modifier
            .width(width)
            .scale(scaleFactor)
            .then(interaction.modifier),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(ShelfMetrics.cardHeight)
                .clip(shape)
                .background(palette.surface2, shape)
                .then(if (ringWidth > 0.dp) Modifier.border(ringWidth, palette.onArt, shape) else Modifier),
        ) {
            val art = item.backdrop ?: item.poster
            Artwork(
                url = art,
                title = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // véu inferior: garante contraste do título sobre qualquer arte
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            0.72f to palette.bg.copy(alpha = 0.55f),
                            1f to palette.bg.copy(alpha = 0.95f),
                        ),
                    ),
            )
            if (dimmed) {
                Box(Modifier.fillMaxSize().background(palette.bg.copy(alpha = 0.45f)))
            }

            rank?.let {
                RankBadge(rank = it, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            overline?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.glassDark, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = it.uppercase(),
                        style = type.label,
                        color = palette.onArt.copy(alpha = 0.9f),
                        maxLines = 1,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 11.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
                    color = palette.onArt,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = type.caption.copy(fontSize = 10.5.sp),
                    color = palette.onArt.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (progress != null && progress > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(palette.onArt.copy(alpha = 0.22f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(palette.onArt),
                    )
                }
            }

            // realce de foco: brilho interno branco bem discreto
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.onArt.copy(alpha = 0.05f)),
                )
            }
        }
        if (item.kind == com.smarted.tv.core.model.MediaKind.LIVE && item.subtitle != null) {
            Text(
                text = item.subtitle.orEmpty(),
                style = type.caption,
                color = palette.fg3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

/** Selo de posição do "Top 10": caixa de vidro com o número em branco puro. */
@Composable
fun RankBadge(rank: Int, modifier: Modifier = Modifier) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.bg.copy(alpha = 0.72f), shape)
            .border(1.dp, palette.onArt.copy(alpha = 0.28f), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = String.format("%02d", rank),
            style = type.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = palette.onArt,
        )
    }
}

/** Cartão de canal ao vivo com o mesmo vocabulário visual das trilhas. */
@Composable
fun LiveChannelCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = ShelfMetrics.cardWidth,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val focused = interaction.isFocused
    val scaleFactor by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(180, easing = EdEasing),
        label = "live-scale",
    )
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .width(width)
            .scale(scaleFactor)
            .then(interaction.modifier),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(ShelfMetrics.cardHeight)
                .clip(shape)
                .background(palette.surface2, shape)
                .then(if (focused) Modifier.border(2.dp, palette.onArt, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                url = item.poster,
                title = item.title,
                modifier = Modifier.fillMaxSize().padding(18.dp),
                contentScale = ContentScale.Fit,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.glassDark, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(5.dp).background(palette.onArt, RoundedCornerShape(percent = 50)))
                Text(
                    text = "AO VIVO",
                    style = type.label,
                    color = palette.onArt,
                    maxLines = 1,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to palette.bg.copy(alpha = 0.92f),
                        ),
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
                    color = palette.onArt,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle ?: item.group ?: "",
                    style = type.caption.copy(fontSize = 10.5.sp),
                    color = palette.onArt.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Pílula de gênero / filtro rápido das trilhas. */
@Composable
fun GenrePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val focused = interaction.isFocused
    val shape = RoundedCornerShape(percent = 50)

    Row(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .background(if (focused) palette.glassStrong else palette.surface, shape)
            .then(
                if (focused) {
                    Modifier.border(2.dp, palette.onArt, shape)
                } else {
                    Modifier.border(1.dp, palette.lineSoft, shape)
                },
            )
            .then(interaction.modifier)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = if (focused) palette.onArt else palette.fg2,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = type.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = if (focused) palette.onArt else palette.fg2,
            maxLines = 1,
        )
    }
}

/** Trilha vazia: mantém o ritmo vertical da Home sem "buracos". */
@Composable
fun ShelfPlaceholder(count: Int = 6, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShelfMetrics.gap),
    ) {
        repeat(count) {
            ShimmerBox(
                modifier = Modifier
                    .width(ShelfMetrics.cardWidth)
                    .aspectRatio(16f / 10f),
                shape = RoundedCornerShape(14.dp),
            )
        }
    }
}
