package com.smarted.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.smarted.tv.core.data.AppGraph
import com.smarted.tv.core.model.PlaybackRequest
import com.smarted.tv.core.model.VodItem
import com.smarted.tv.core.model.VodKind
import com.smarted.tv.core.util.asDuration
import com.smarted.tv.core.util.asRating
import com.smarted.tv.ui.common.vmFactory
import com.smarted.tv.ui.components.*
import com.smarted.tv.ui.nav.Dest
import com.smarted.tv.ui.theme.LocalDimens
import com.smarted.tv.ui.theme.Smoke
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onNavigate: (Dest) -> Unit,
    onOpenTitle: (VodItem) -> Unit,
    onPlay: (PlaybackRequest) -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(AppGraph.content) })
    val ui by vm.ui.collectAsState()
    val favCount by vm.favoriteCount.collectAsState()
    val d = LocalDimens.current

    var heroIndex by remember { mutableIntStateOf(0) }
    val featuredSize = ui.featured.size
    LaunchedEffect(featuredSize) {
        while (featuredSize > 1) {
            delay(8_000)
            heroIndex = (heroIndex + 1) % featuredSize
        }
    }
    val hero = ui.featured.getOrNull(heroIndex)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = d.screenV + 8.dp),
            verticalArrangement = Arrangement.spacedBy(d.gutter + 4.dp),
        ) {
            item { HeroBanner(hero, onPlay, onOpenTitle) }
            item {
                val tiles = listOf(
                    Tile("Canais ao vivo", "${ui.channelCount} canais", Icons.Rounded.LiveTv) { onNavigate(Dest.CHANNELS) },
                    Tile("Filmes", "${ui.movieCount} filmes", Icons.Rounded.Movie) { onNavigate(Dest.MOVIES) },
                    Tile("Séries", "${ui.seriesCount} séries", Icons.Rounded.Theaters) { onNavigate(Dest.SERIES) },
                    Tile("Favoritos", "$favCount itens", Icons.Rounded.Favorite) { onNavigate(Dest.CHANNELS) },
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = d.screenH, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(tiles, key = { it.title }) { QuickTile(it) }
                }
            }
            item { PosterRow("Top 10 de hoje", ui.top10, onOpenTitle) }
            item { PosterRow("Adicionados recentemente · Filmes", ui.newMovies, onOpenTitle) }
            item { PosterRow("Adicionados recentemente · Séries", ui.newSeries, onOpenTitle) }
            item { PosterRow("Mais bem avaliadas · Séries", ui.topSeries, onOpenTitle) }
        }

        Text(
            "SmartED-TV", fontSize = d.textTitle, fontWeight = FontWeight.Bold, color = Smoke.TextPrimary,
            modifier = Modifier.align(Alignment.TopStart).padding(start = d.screenH, top = d.screenV)
                .glass(RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ClockHeader(Modifier.align(Alignment.TopEnd).padding(top = d.screenV, end = d.screenH))
    }
}

@Composable
private fun HeroBanner(item: VodItem?, onPlay: (PlaybackRequest) -> Unit, onOpen: (VodItem) -> Unit) {
    val d = LocalDimens.current
    Box(Modifier.fillMaxWidth().height(d.heroHeight)) {
        Crossfade(targetState = item, label = "heroBackdrop") { h ->
            val url = h?.backdropUrl ?: h?.posterUrl
            if (url != null) {
                AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF23262D), Color(0xFF0A0B0D)))))
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(0.85f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)))))

        if (item != null) {
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(0.62f)
                    .padding(start = d.screenH, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (item.kind == VodKind.MOVIE) "FILME" else "SÉRIE", fontSize = d.textCaption,
                    fontWeight = FontWeight.Bold, color = Smoke.TextSecondary)
                Text(item.title, fontSize = d.textHeadline, fontWeight = FontWeight.Bold,
                    color = Smoke.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.year?.toString(), item.genres.firstOrNull(),
                        item.durationMin?.asDuration(), item.rating?.let { "★ ${it.asRating()}" })
                        .joinToString("  ·  "),
                    fontSize = d.textBody, color = Smoke.TextSecondary,
                )
                Text(item.plot, fontSize = d.textBody, color = Smoke.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    GlassButton("Assistir", Icons.Rounded.PlayArrow, {
                        val ep = item.seasons.firstOrNull()?.episodes?.firstOrNull()
                        onPlay(
                            if (item.kind == VodKind.MOVIE) PlaybackRequest(item.title, item.streamUrl, item.headers, item.favKey, "movie")
                            else {
                                val sn = item.seasons.firstOrNull()?.number ?: 1
                                PlaybackRequest("${item.title} · T${'$'}sn E${ep?.number ?: 1}", ep?.streamUrl, item.headers, ep?.id ?: item.favKey, "series")
                            }
                        )
                    })
                    GlassButton("Mais info", Icons.Rounded.Info, { onOpen(item) })
                }
            }
        }
    }
}

private data class Tile(val title: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun QuickTile(t: Tile) {
    val d = LocalDimens.current
    GlassClickable(
        onClick = t.onClick,
        modifier = Modifier.width(d.tileWidth).height(64.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Icon(t.icon, null, Modifier.size(26.dp), tint = Smoke.TextPrimary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(t.title, fontSize = d.textBody, fontWeight = FontWeight.Bold, color = Smoke.TextPrimary, maxLines = 1)
                Text(t.subtitle, fontSize = d.textCaption, color = Smoke.TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PosterRow(title: String, items: List<VodItem>, onOpen: (VodItem) -> Unit) {
    if (items.isEmpty()) return
    val d = LocalDimens.current
    val favs by AppGraph.content.favoriteIds.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = d.textTitle, fontWeight = FontWeight.Bold, color = Smoke.TextPrimary,
            modifier = Modifier.padding(horizontal = d.screenH))
        LazyRow(
            contentPadding = PaddingValues(horizontal = d.screenH, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) {
                PosterCard(it, it.id in favs, { onOpen(it) }, Modifier.width(d.posterMinWidth + 8.dp))
            }
        }
    }
}
