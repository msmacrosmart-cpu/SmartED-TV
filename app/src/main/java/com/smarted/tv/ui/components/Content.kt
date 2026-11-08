package com.smarted.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smarted.tv.core.model.VodItem
import com.smarted.tv.core.util.asRating
import com.smarted.tv.ui.theme.LocalDimens
import com.smarted.tv.ui.theme.Smoke
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/**
 * Capa vertical 2:3 — a proporção padrão de pôster usada pelo IPTV Smarters Pro
 * (grade limpa, título em até 2 linhas abaixo). A largura vem do grid
 * (GridCells.Adaptive(posterMinWidth)), então a altura sempre = largura × 1,5.
 * Se quiser casar pixel a pixel com o Smarters, ajuste POSTER_ASPECT e posterMinWidth.
 */
const val POSTER_ASPECT = 2f / 3f

@Composable
fun PosterImage(url: String?, title: String, modifier: Modifier = Modifier) {
    val d = LocalDimens.current
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF2B2E36), Color(0xFF0E0F12))))) {
        Text(
            title, Modifier.align(Alignment.Center).padding(8.dp), maxLines = 4,
            fontSize = d.textCaption, textAlign = TextAlign.Center, color = Smoke.TextSecondary,
        )
        if (url != null) {
            AsyncImage(
                model = url, contentDescription = title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun PosterCard(
    item: VodItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
) {
    val d = LocalDimens.current
    Column(modifier) {
        GlassClickable(
            onClick = onClick, onFocused = onFocused, corner = d.corner - 4.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(POSTER_ASPECT),
        ) {
            PosterImage(item.posterUrl, item.title, Modifier.fillMaxSize())
            if (isFavorite) {
                Icon(
                    Icons.Rounded.Favorite, null, tint = Smoke.TextPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                )
            }
            item.rating?.let {
                Text(
                    "★ ${it.asRating()}", fontSize = d.textCaption, fontWeight = FontWeight.SemiBold,
                    color = Smoke.TextPrimary,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            item.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontSize = d.textCaption, color = Smoke.TextSecondary,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}

data class CategoryEntry(val id: String, val name: String, val icon: ImageVector? = null)

@Composable
fun CategoryList(
    entries: List<CategoryEntry>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = LocalDimens.current
    Box(modifier.glass().padding(6.dp)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(entries, key = { it.id }) { e ->
                val sel = e.id == selectedId
                GlassClickable(
                    onClick = { onSelect(e.id) }, selected = sel, flat = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = d.chipHeight + 6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) { focused ->
                    Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                        e.icon?.let {
                            Icon(it, null, Modifier.size(20.dp), tint = Smoke.TextPrimary)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            e.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = d.textBody,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel || focused) Smoke.TextPrimary else Smoke.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** Data e hora (canto superior) em pílula de vidro fumê. */
@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    val d = LocalDimens.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L * (60 - now.second))
        }
    }
    val locale = Locale("pt", "BR")
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale))
        .replaceFirstChar { it.uppercase() }
    Row(
        modifier.glass(RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, fontSize = d.textTitle, fontWeight = FontWeight.Bold, color = Smoke.TextPrimary)
        Spacer(Modifier.width(12.dp))
        Text(date, fontSize = d.textCaption, color = Smoke.TextSecondary, maxLines = 1)
    }
}
