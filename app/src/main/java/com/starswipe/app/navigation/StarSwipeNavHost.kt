package com.starswipe.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.starswipe.app.feature.album.AlbumScreen
import com.starswipe.app.feature.footprint.FootprintScreen
import com.starswipe.app.feature.my.MyScreen
import com.starswipe.app.feature.organize.OrganizeScreen
import com.starswipe.app.feature.similar.SimilarScreen

@Composable
fun StarSwipeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Organize.route

    Box(modifier = modifier.fillMaxSize()) {
        // NavHost maintains navigation graph and backstack state for NavController
        NavHost(
            navController = navController,
            startDestination = Screen.Organize.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Organize.route) {}
            composable(Screen.Similar.route) {}
            composable(Screen.Footprint.route) {}
            composable(Screen.Albums.route) {}
            composable(Screen.My.route) {}
        }

        // Direction-aware horizontal parallax sliding page container
        val screens = Screen.bottomNavScreens
        AnimatedContent(
            targetState = currentRoute,
            transitionSpec = {
                val targetIndex = screens.indexOfFirst { it.route == targetState }.coerceAtLeast(0)
                val initialIndex = screens.indexOfFirst { it.route == initialState }.coerceAtLeast(0)
                val direction = if (targetIndex >= initialIndex) 1 else -1

                (slideInHorizontally(
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialOffsetX = { fullWidth -> direction * fullWidth }
                ) + fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        targetOffsetX = { fullWidth -> -direction * (fullWidth / 3) } // 1/3 Parallax slide out
                    ) + fadeOut(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                )
            },
            label = "tabScreenSlideTransition",
            modifier = Modifier.fillMaxSize()
        ) { targetRoute ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (targetRoute) {
                    Screen.Organize.route -> OrganizeScreen(isActive = (targetRoute == currentRoute))
                    Screen.Similar.route -> SimilarScreen()
                    Screen.Footprint.route -> FootprintScreen()
                    Screen.Albums.route -> AlbumScreen()
                    Screen.My.route -> MyScreen()
                }
            }
        }
    }
}
