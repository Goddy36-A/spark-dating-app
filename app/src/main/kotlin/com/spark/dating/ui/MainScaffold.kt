package com.spark.dating.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spark.dating.feature.chat.ChatListScreen
import com.spark.dating.feature.discovery.DiscoverScreen
import com.spark.dating.feature.matching.LikesScreen
import com.spark.dating.feature.matching.MatchesScreen
import com.spark.dating.feature.profile.MyProfileScreen

private sealed class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String,
) {
    data object Discover : BottomNavTab("tab/discover", "Discover", Icons.Filled.Search, Icons.Outlined.Search, "Discover people")
    data object Likes : BottomNavTab("tab/likes", "Likes", Icons.Filled.Star, Icons.Outlined.StarOutline, "Likes")
    data object Matches : BottomNavTab("tab/matches", "Matches", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "Matches")
    data object Messages : BottomNavTab("tab/messages", "Messages", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "Messages")
    data object Profile : BottomNavTab("tab/profile", "Profile", Icons.Filled.Person, Icons.Outlined.PersonOutline, "My profile")
}

private val TABS = listOf(
    BottomNavTab.Discover,
    BottomNavTab.Likes,
    BottomNavTab.Matches,
    BottomNavTab.Messages,
    BottomNavTab.Profile,
)

@Composable
fun MainScaffold(rootNavController: NavController) {
    val tabNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            NavigationBar {
                TABS.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            tabNavController.navigate(tab.route) {
                                popUpTo(tabNavController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.contentDescription,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = BottomNavTab.Discover.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(BottomNavTab.Discover.route) {
                DiscoverScreen(
                    onProfileClick = { userId -> rootNavController.navigate("profile/$userId") },
                    onReport = { userId -> rootNavController.navigate("report/$userId") },
                    onSubscription = { rootNavController.navigate("subscription") },
                )
            }
            composable(BottomNavTab.Likes.route) {
                LikesScreen(
                    onSubscription = { rootNavController.navigate("subscription") },
                )
            }
            composable(BottomNavTab.Matches.route) {
                MatchesScreen(
                    onChat = { conversationId -> rootNavController.navigate("chat/$conversationId") },
                )
            }
            composable(BottomNavTab.Messages.route) {
                ChatListScreen(
                    onChat = { conversationId -> rootNavController.navigate("chat/$conversationId") },
                )
            }
            composable(BottomNavTab.Profile.route) {
                MyProfileScreen(
                    onEdit = { rootNavController.navigate("edit-profile") },
                    onSettings = { rootNavController.navigate("settings") },
                    onSubscription = { rootNavController.navigate("subscription") },
                )
            }
        }
    }
}
