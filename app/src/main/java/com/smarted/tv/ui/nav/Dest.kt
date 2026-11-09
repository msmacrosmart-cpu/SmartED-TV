package com.smarted.tv.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.ui.graphics.vector.ImageVector

enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Início", Icons.Rounded.Home),
    CHANNELS("channels", "Canais", Icons.Rounded.LiveTv),
    MOVIES("movies", "Filmes", Icons.Rounded.Movie),
    SERIES("series", "Séries", Icons.Rounded.Theaters),
    REPOSITORY("repositorio", "Navegar", Icons.Rounded.Storage),
    SETTINGS("settings", "Config.", Icons.Rounded.Settings),
}
