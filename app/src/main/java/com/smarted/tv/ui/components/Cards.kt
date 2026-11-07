package com.smarted.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.util.TextUtils
import com.smarted.tv.ui.theme.Ed
import com.smarted.tv.ui.theme.EdEasing

/** Dimensões de card por tipo de conteúdo (iguais às da referência). */
object CardSize {
    val posterWidth = 168.dp
    val wideWidth = 268.dp
    val wideHeight = 151.dp
    val channelWidth = 196.dp
    val channelHeight = 110.dp
}

private val PosterShape = RoundedCornerShape(14.dp)
private val TileShape = RoundedCornerShape(12.dp)
private val PosterRatio = 250f / 168f

/**
 * Card de pôster (filmes/séries) — imagem 2:3, título e metadados abaixo,
 * lift de 4% + anel de acento no foco do D-pad.
 */
@Composable
fun PosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    showMeta: Boolean = true,
    width: Dp? = CardSize.posterWidth,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val focused = interaction.isFocused
    val scaleFactor by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(180, easing = EdEasing),
        label = "poster-scale",
    )
    val ringColor by animateColorAsState(
        targetValue = if (focused) palette.accent else Color.Transparent,
        animationSpec = tween(160, easing = EdEasing),
        label = "poster-ring",
    )

    val sizing = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
    Column(modifier = modifier.then(sizing).then(interaction.modifier)) {
        Box(
            modifier = Modifier
                .then(sizing)
                .then(if (width == null) Modifier.aspectRatio(168f / 250f) else Modifier)
                .height((width ?: 168.dp) * PosterRatio)
                .scale(scaleFactor)
                .clip(PosterShape)
                .background(palette.surface2)
                .then(if (focused) Modifier.border(3.dp, ringColor, PosterShape) else Modifier),
        ) {
            Artwork(item.poster ?: item.backdrop, item.title, Modifier.fillMaxSize())
            item.quality?.let { QualityBadge(it, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
            item.rating?.takeIf { it > 0 }?.let {
                RatingPill(it, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
            if (item.kind == MediaKind.LIVE) LiveDot(Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        if (showMeta) {
            Text(
                text = item.title,
                style = type.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (focused) palette.fg else palette.fg2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = metaLine(item),
                style = type.caption,
                color = palette.fg3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun metaLine(item: MediaItem): String {
    val parts = ArrayList<String>(2)
    item.year?.let { parts.add(it.toString()) }
    item.rating?.takeIf { it > 0 }?.let { parts.add(String.format("%.1f", it)) }
    item.group?.takeIf { it.length <= 18 && parts.size < 2 }?.let { parts.add(it) }
    return parts.joinToString(" · ")
}

/** Card de canal ao vivo: logo centralizado, nome e "agora" abaixo. */
@Composable
fun ChannelCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    nowPlaying: String? = null,
    progress: Float? = null,
    number: Int? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val focused = interaction.isFocused

    Column(modifier = modifier.width(CardSize.channelWidth).then(interaction.modifier)) {
        Box(
            modifier = Modifier
                .width(CardSize.channelWidth)
                .height(CardSize.channelHeight)
                .clip(TileShape)
                .background(palette.surface2)
                .then(if (focused) Modifier.border(3.dp, palette.accent, TileShape) else Modifier)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                url = item.poster,
                title = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                shape = RoundedCornerShape(6.dp),
            )
            number?.let {
                Text(
                    text = "%02d".format(it),
                    style = type.label,
                    color = palette.fg3,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            if (progress != null && progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(palette.bg.copy(alpha = 0.6f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(palette.accent),
                    )
                }
            }
        }
        Text(
            text = item.title,
            style = type.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = if (focused) palette.fg else palette.fg2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = nowPlaying ?: item.group ?: "Ao vivo",
            style = type.caption,
            color = palette.fg3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Card 16:9 para episódios, continuar assistindo e recentes. */
@Composable
fun WideCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    overline: String? = null,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val focused = interaction.isFocused

    Column(modifier = modifier.width(CardSize.wideWidth).then(interaction.modifier)) {
        Box(
            modifier = Modifier
                .width(CardSize.wideWidth)
                .height(CardSize.wideHeight)
                .clip(TileShape)
                .background(palette.surface2)
                .then(if (focused) Modifier.border(3.dp, palette.accent, TileShape) else Modifier),
        ) {
            Artwork(item.backdrop ?: item.poster, item.title, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to palette.bg.copy(alpha = 0.9f),
                        ),
                    ),
            )
            overline?.let {
                Text(
                    text = it.uppercase(),
                    style = type.label,
                    color = palette.accent,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                )
            }
            Text(
                text = item.title,
                style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = palette.fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
            if (progress != null && progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(palette.fg3.copy(alpha = 0.3f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(palette.accent),
                    )
                }
            }
        }
        item.subtitle?.let {
            Text(
                text = it,
                style = type.caption,
                color = palette.fg3,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Miniatura circular de elenco. */
@Composable
fun CastCard(
    name: String,
    character: String?,
    photo: String?,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = {}, focusRequester = focusRequester)
    val focused = interaction.isFocused
    val circle = RoundedCornerShape(percent = 50)

    Column(
        modifier = modifier.width(96.dp).then(interaction.modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(circle)
                .background(palette.surface2)
                .then(if (focused) Modifier.border(3.dp, palette.accent, circle) else Modifier),
        ) {
            Artwork(photo, name, Modifier.fillMaxSize())
        }
        Text(
            text = name,
            style = type.caption.copy(fontWeight = FontWeight.Medium),
            color = if (focused) palette.fg else palette.fg2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (!character.isNullOrBlank()) {
            Text(
                text = character,
                style = type.caption,
                color = palette.fg3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ------------------------------------------------------------------- peças

/** Imagem com fallback de monograma (nunca deixa "buraco" no layout). */
@Composable
fun Artwork(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RoundedCornerShape(0.dp),
) {
    if (url.isNullOrBlank()) {
        Monogram(title, modifier, shape)
        return
    }
    val context = LocalContext.current
    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        Monogram(title, Modifier.fillMaxSize(), shape)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(180)
                .build(),
            contentDescription = title,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun Monogram(title: String, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(0.dp)) {
    val palette = Ed.palette
    val type = Ed.type
    Box(
        modifier = modifier.clip(shape).background(palette.surface2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = TextUtils.initials(title),
            style = type.title,
            color = palette.fg3.copy(alpha = 0.75f),
        )
    }
}

@Composable
fun QualityBadge(quality: String, modifier: Modifier = Modifier) {
    val palette = Ed.palette
    val type = Ed.type
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(palette.bg.copy(alpha = 0.82f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(quality, style = type.label, color = palette.fg.copy(alpha = 0.92f))
    }
}

@Composable
fun RatingPill(rating: Double, modifier: Modifier = Modifier) {
    val palette = Ed.palette
    val type = Ed.type
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(palette.bg.copy(alpha = 0.86f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(palette.accent, RoundedCornerShape(percent = 50)))
            Text(text = "  ${String.format("%.1f", rating)}", style = type.label, color = palette.fg)
        }
    }
}

@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    val palette = Ed.palette
    val type = Ed.type
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(palette.bad.copy(alpha = 0.9f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("AO VIVO", style = type.label, color = Color.White)
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    Box(modifier.clip(shape).background(Ed.palette.surface2))
}

@Composable
fun IconText(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ed.palette.fg2,
    iconSize: Dp = 14.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
        Text(text, style = Ed.type.caption, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier, color: Color = Ed.palette.line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = EdIcons.folder,
) {
    val palette = Ed.palette
    val type = Ed.type
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(palette.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.fg3, modifier = Modifier.size(26.dp))
        }
        Text(title, style = type.sectionTitle, color = palette.fg)
        Text(message, style = type.bodySmall, color = palette.fg3, textAlign = TextAlign.Center)
    }
}

@Composable
fun AccentDot(color: Color = Ed.palette.accent, size: Dp = 6.dp) {
    Box(Modifier.size(size).background(color, RoundedCornerShape(percent = 50)))
}
