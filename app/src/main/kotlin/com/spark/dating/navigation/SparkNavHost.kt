package com.spark.dating.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spark.dating.core.auth.AuthState
import com.spark.dating.feature.auth.LoginScreen
import com.spark.dating.feature.auth.RegisterScreen
import com.spark.dating.feature.auth.WelcomeScreen
import com.spark.dating.feature.chat.ChatListScreen
import com.spark.dating.feature.chat.ChatScreen
import com.spark.dating.feature.discovery.DiscoverScreen
import com.spark.dating.feature.matching.MatchesScreen
import com.spark.dating.feature.notifications.NotificationsScreen
import com.spark.dating.feature.onboarding.OnboardingNavHost
import com.spark.dating.feature.profile.EditProfileScreen
import com.spark.dating.feature.profile.ProfileDetailScreen
import com.spark.dating.feature.profile.MyProfileScreen
import com.spark.dating.feature.safety.ReportScreen
import com.spark.dating.feature.safety.SafetyCenterScreen
import com.spark.dating.feature.settings.SettingsScreen
import com.spark.dating.feature.subscription.SubscriptionScreen
import com.spark.dating.ui.MainScaffold

sealed interface SparkRoute {
    // Auth
    data object Welcome : SparkRoute { const val ROUTE = "welcome" }
    data object Login : SparkRoute { const val ROUTE = "login" }
    data object Register : SparkRoute { const val ROUTE = "register" }

    // Onboarding (its own nested graph)
    data object Onboarding : SparkRoute { const val ROUTE = "onboarding" }

    // Main graph (shown after auth + onboarding complete)
    data object Main : SparkRoute { const val ROUTE = "main" }

    // Deeper screens launched from main
    data object ProfileDetail : SparkRoute { const val ROUTE = "profile/{userId}" }
    data object Chat : SparkRoute { const val ROUTE = "chat/{conversationId}" }
    data object EditProfile : SparkRoute { const val ROUTE = "edit-profile" }
    data object Report : SparkRoute { const val ROUTE = "report/{userId}" }
    data object Notifications : SparkRoute { const val ROUTE = "notifications" }
    data object Subscription : SparkRoute { const val ROUTE = "subscription" }
    data object Settings : SparkRoute { const val ROUTE = "settings" }
    data object SafetyCenter : SparkRoute { const val ROUTE = "safety-center" }
}

@Composable
fun SparkNavHost(
    authState: AuthState,
    navController: NavHostController = rememberNavController(),
) {
    val startDestination = when (authState) {
        AuthState.Loading -> SparkRoute.Welcome.ROUTE // Splash blocks until resolved
        is AuthState.Authenticated -> {
            if (authState.user.onboardingComplete) SparkRoute.Main.ROUTE
            else SparkRoute.Onboarding.ROUTE
        }
        AuthState.Unauthenticated -> SparkRoute.Welcome.ROUTE
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(300)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { fadeOut(tween(300)) },
    ) {

        // ── Auth ──────────────────────────────────────────────────────────
        composable(SparkRoute.Welcome.ROUTE) {
            WelcomeScreen(
                onLogin = { navController.navigate(SparkRoute.Login.ROUTE) },
                onRegister = { navController.navigate(SparkRoute.Register.ROUTE) },
            )
        }

        composable(SparkRoute.Login.ROUTE) {
            LoginScreen(
                onSuccess = {
                    navController.navigate(SparkRoute.Main.ROUTE) {
                        popUpTo(SparkRoute.Welcome.ROUTE) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(SparkRoute.Register.ROUTE) {
                        popUpTo(SparkRoute.Login.ROUTE) { inclusive = true }
                    }
                },
                onBack = navController::popBackStack,
            )
        }

        composable(SparkRoute.Register.ROUTE) {
            RegisterScreen(
                onSuccess = {
                    navController.navigate(SparkRoute.Onboarding.ROUTE) {
                        popUpTo(SparkRoute.Welcome.ROUTE) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(SparkRoute.Login.ROUTE) {
                        popUpTo(SparkRoute.Register.ROUTE) { inclusive = true }
                    }
                },
                onBack = navController::popBackStack,
            )
        }

        // ── Onboarding ────────────────────────────────────────────────────
        composable(SparkRoute.Onboarding.ROUTE) {
            OnboardingNavHost(
                onComplete = {
                    navController.navigate(SparkRoute.Main.ROUTE) {
                        popUpTo(SparkRoute.Onboarding.ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // ── Main App (bottom-nav scaffold) ────────────────────────────────
        composable(SparkRoute.Main.ROUTE) {
            MainScaffold(rootNavController = navController)
        }

        // ── Deep screens ──────────────────────────────────────────────────
        composable(
            route = SparkRoute.ProfileDetail.ROUTE,
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(350))
            },
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            ProfileDetailScreen(
                userId = userId,
                onBack = navController::popBackStack,
                onReport = { navController.navigate("report/$userId") },
            )
        }

        composable(
            route = SparkRoute.Chat.ROUTE,
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
            },
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId = conversationId,
                onBack = navController::popBackStack,
                onProfile = { userId -> navController.navigate("profile/$userId") },
                onReport = { userId -> navController.navigate("report/$userId") },
            )
        }

        composable(SparkRoute.EditProfile.ROUTE) {
            EditProfileScreen(onBack = navController::popBackStack)
        }

        composable(SparkRoute.Report.ROUTE) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            ReportScreen(
                reportedUserId = userId,
                onBack = navController::popBackStack,
                onSubmitted = navController::popBackStack,
            )
        }

        composable(SparkRoute.Notifications.ROUTE) {
            NotificationsScreen(onBack = navController::popBackStack)
        }

        composable(SparkRoute.Subscription.ROUTE) {
            SubscriptionScreen(onBack = navController::popBackStack)
        }

        composable(SparkRoute.Settings.ROUTE) {
            SettingsScreen(
                onBack = navController::popBackStack,
                onSafetyCenter = { navController.navigate(SparkRoute.SafetyCenter.ROUTE) },
                onSubscription = { navController.navigate(SparkRoute.Subscription.ROUTE) },
            )
        }

        composable(SparkRoute.SafetyCenter.ROUTE) {
            SafetyCenterScreen(onBack = navController::popBackStack)
        }
    }
}
