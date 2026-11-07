package com.smarted.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smarted.tv.ui.theme.*

@Composable
fun TvAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Box(modifier
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .whiteFocus(focused, selected, 14.dp)
        .padding(if (focused) 3.dp else 0.dp)
        .clip(shape)
        .background(if (primary) Color.White else if (focused || selected) Color(0xFF343538) else Color(0xFF1B1C1F))
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label }
        .defaultMinSize(minHeight = if (compact) 38.dp else 48.dp)
        .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 8.dp else 12.dp),
        contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(19.dp), tint = if (primary) Color.Black else Color.White)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (primary) Color.Black else if (enabled) Color.White else TvColors.Muted)
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, tag: String = "search", focusRequester: FocusRequester? = null) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(value = value, onValueChange = onChange, modifier = modifier
        .testTag(tag)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { focused = it.isFocused }.whiteFocus(focused, radius = 12.dp),
        placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
        singleLine = true, textStyle = MaterialTheme.typography.bodyMedium, shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedBorderColor = TvColors.Outline,
            cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedPlaceholderColor = TvColors.Muted, unfocusedPlaceholderColor = TvColors.Muted,
            focusedContainerColor = Color(0xFF17181B), unfocusedContainerColor = Color(0xFF121315),
            selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(Color(0x66FFFFFF), Color.White)))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.smokedGlass().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
        action?.invoke()
    }
}

@Composable
fun Notice(message: String, modifier: Modifier = Modifier) {
    if (message.isNotBlank()) Text(message, modifier.padding(vertical = 6.dp), color = TvColors.Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun BackAction(onBack: () -> Unit, label: String = "Início") {
    TvAction(label, onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, compact = true)
}

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvAction(label, onClick, modifier, icon = if (selected) Icons.Default.Check else null, selected = selected, compact = true)
}

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.smokedGlass().padding(padding), content = content)
}
