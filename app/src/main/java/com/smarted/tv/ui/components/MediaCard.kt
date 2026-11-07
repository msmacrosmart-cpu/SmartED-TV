package com.smarted.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.Scope
import com.smarted.tv.ui.theme.*

@Composable
fun RemoteImage(url: String?, description: String?, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF292A2E), Color(0xFF121315)))), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) AsyncImage(model = url, contentDescription = description, modifier = Modifier.matchParentSize(), contentScale = scale)
        else Text("SmartED", color = Color(0xFF77797E), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun MediaCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.testTag("card-${item.key}").onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
        .whiteFocus(focused, radius = 17.dp).padding(3.dp).clip(shape)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (item.scope == Scope.CHANNELS) 16f / 9 else 2f / 3)) {
            RemoteImage(item.imageUrl, item.name, Modifier.matchParentSize(), if (item.scope == Scope.CHANNELS) ContentScale.Fit else ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xEE090A0C)))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.year?.toString(), item.rating?.let { "Nota $it" }, item.releaseInfo.takeIf { item.year == null && it.isNotBlank() })
                    .joinToString(" · ").ifBlank { if (item.scope == Scope.SERIES) "Série" else if (item.scope == Scope.CHANNELS) "Ao vivo" else "Filme" },
                    color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
