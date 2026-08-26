package com.spark.dating.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.spark.dating.core.ui.components.SparkButton
import com.spark.dating.core.ui.components.SparkOutlinedButton
import com.spark.dating.core.ui.theme.SparkColors

@Composable
fun WelcomeScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to SparkColors.Ink,
                        0.5f to SparkColors.InkMid,
                        1.0f to SparkColors.PlumDeep,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // Logo / wordmark
            Text(
                text = "✦ spark",
                style = MaterialTheme.typography.displayMedium,
                color = SparkColors.CoralFlame,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = buildAnnotatedString {
                    append("Meet people who ")
                    withStyle(SpanStyle(color = SparkColors.PlumLight)) {
                        append("actually")
                    }
                    append(" match your energy")
                },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            // CTAs
            SparkButton(
                text = "Create account",
                onClick = onRegister,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            SparkOutlinedButton(
                text = "Log in",
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "By continuing you agree to our Terms of Service and Privacy Policy.",
                style = MaterialTheme.typography.labelSmall,
                color = SparkColors.Mist,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
