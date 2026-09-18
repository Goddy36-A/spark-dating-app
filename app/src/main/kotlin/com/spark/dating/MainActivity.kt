package com.spark.dating

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spark.dating.core.auth.AuthState
import com.spark.dating.core.ui.theme.SparkTheme
import com.spark.dating.navigation.SparkNavHost
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    // launchMode="singleTop" in the manifest means the same Activity instance handles
    // both the cold-start deep link (via onCreate/getIntent) and the warm-resume case
    // (via onNewIntent) — both paths must hand the intent to Supabase, or a password
    // reset / OAuth redirect link silently does nothing and the app just falls back
    // to the login screen, which looks like an infinite loop as the user retries.
    @Inject
    lateinit var supabaseClient: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash visible while determining auth state
        splashScreen.setKeepOnScreenCondition {
            mainViewModel.authState.value == AuthState.Loading
        }

        enableEdgeToEdge()

        supabaseClient.handleDeeplinks(intent)

        setContent {
            val authState by mainViewModel.authState.collectAsStateWithLifecycle()
            val darkTheme by mainViewModel.darkTheme.collectAsStateWithLifecycle()

            SparkTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SparkNavHost(authState = authState)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        supabaseClient.handleDeeplinks(intent)
    }
}
