package com.starswipe.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Organize : Screen("organize", "整理", Icons.Default.AutoAwesome)
    data object Similar : Screen("similar", "相似", Icons.Default.PhotoLibrary)
    data object Footprint : Screen("footprint", "足迹", Icons.Default.Place)
    data object Albums : Screen("albums", "相簿", Icons.Default.Folder)
    data object My : Screen("my", "我的", Icons.Default.Person)

    companion object {
        val bottomNavScreens: List<Screen>
            get() = listOf(Organize, Similar, Footprint, Albums, My)
    }
}
