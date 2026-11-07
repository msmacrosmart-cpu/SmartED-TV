package com.smarted.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.smarted.tv.core.model.VodEntry
import com.smarted.tv.ui.theme.LocalCompact
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.smokedGlass
import com.smarted.tv.ui.theme.whiteFocus
import androidx.compose.foundation.layout.Box
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Imagem remota com fallback "fumê" (cartaz/logo do canal). */
@Composable
fun RemoteImage(url: String?, description: String?, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF292A2E), Color(0xFF121315)))), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = description, modifier = Modifier.matchParentSize(), contentScale = scale)
        } else {
            Text("SmartED", color = Color(0xFF77797E), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Data/hora ao vivo no topo, estilo UltraTV: "sábado, 7 de novembro" + 14:58. */
@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val dateLabel = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date(now))
        .replaceFirstChar { it.uppercaseChar() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(dateLabel, style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
        Text(
            SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(now)),
            style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 1.sp),
            color = Color.White, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "sidebarScale")
    Column(
        modifier
            .testTag("nav-$label")
            .padding(3.dp)
            .scale(scale)
            .smokedGlass(18.dp)
            .whiteFocus(focused, selected, 18.dp)
            .clip(RoundedCornerShape(15.dp))
            .then(if (selected) Modifier.background(Color(0x2EFFFFFF)) else Modifier.background(Brush.linearGradient(listOf(TvColors.GlassTop, TvColors.GlassBottom))))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (focused) 30.dp else 26.dp))
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = if (focused || selected) Color.White else TvColors.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Linha de conteúdo da Home (título + contagem + carrossel). */
@Composable
fun HomeRow(
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            if (count != null && count > 0) Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(if (LocalCompact.current) 12.dp else 16.dp), content = content)
    }
}

val HomeRowIcons = mapOf(
    "tv" to Icons.Default.LiveTv,
    "movies" to Icons.Default.Movie,
    "series" to Icons.Default.Tv,
    "home" to Icons.Default.Home,
    "settings" to Icons.Default.Settings,
    "search" to Icons.Default.Search,
    "play" to Icons.Default.PlayArrow,
)

@Composable
fun VodPosterCard(entry: VodEntry, onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .testTag("vod-card-${entry.id}")
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .whiteFocus(focused, radius = 20.dp)
            .padding(3.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            RemoteImage(entry.posterUrl, entry.name, Modifier.matchParentSize(), ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC07080A)))))
            if (entry.rating != null && entry.rating > 0) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Default.Favorite, null, Modifier.size(11.dp), tint = Color(0xFFFF6B8A))
                    Text("%.1f".format(entry.rating), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(entry.year?.toString(), entry.category.takeIf { it.isNotBlank() })
                        .joinToString(" · ").ifBlank { if (entry.kind.name == "SERIES") "Série" else "Filme" },
                    color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ContinueCard(title: String, positionMs: Long, durationMs: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.testTag("continue-$title").onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = 18.dp).padding(3.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(Modifier.width(if (LocalCompact.current) 200.dp else 240.dp).aspectRatio(16f / 9f)) {
            RemoteImage(null, title, Modifier.matchParentSize())
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (durationMs > 0) {
                    Box(
                        Modifier.fillMaxWidth().height(4.dp).background(Color(0x55FFFFFF), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(((positionMs.toFloat() / durationMs.toFloat()).coerceIn(0.01f, 1f)))
                                .height(4.dp).background(Color.White, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeartToggle(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, label: String = "favorito") {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.testTag("heart-$label").size(40.dp).onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = 12.dp).clip(RoundedCornerShape(10.dp))
            .background(Color(0x33000000), RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
            tint = if (isFavorite) Color(0xFFFF6B8A) else Color.White, modifier = Modifier.size(20.dp),
        )
    }
}

/** Chip pequeno de categoria (colunas 1 das abas). */
@Composable
fun CategoryChip(
    name: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .testTag("category-${name.filter(Char::isLetterOrDigit).take(24)}")
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, selected, 12.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x2BFFFFFF) else Color(0x14FFFFFF))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = Color.White)
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
    }
}

fun vodLabel(entry: VodEntry): String = buildString {
    append(entry.name)
    entry.year?.let { append(" ($it)") }
}
