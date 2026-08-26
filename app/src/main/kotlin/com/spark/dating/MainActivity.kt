package com.spark.dating

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash visible while determining auth state
        splashScreen.setKeepOnScreenCondition {
            mainViewModel.authState.value == AuthState.Loading
        }

        enableEdgeToEdge()

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
}
