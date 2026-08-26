package com.spark.dating.feature.subscription

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spark.dating.core.ui.theme.SparkColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class SubscriptionUiState(
    val selectedPlan: Plan = Plan.GOLD,
    val isLoading: Boolean = false,
    val error: String? = null,
    val purchaseSuccess: Boolean = false,
)

enum class Plan(val productId: String, val label: String, val price: String, val period: String) {
    PLUS("spark_plus_monthly", "Plus", "$9.99", "/month"),
    GOLD("spark_gold_monthly", "Gold", "$19.99", "/month"),
    PLATINUM("spark_platinum_monthly", "Platinum", "$29.99", "/month"),
}

@HiltViewModel
class SubscriptionViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    fun selectPlan(plan: Plan) = _uiState.update { it.copy(selectedPlan = plan) }

    /** Initiates Google Play Billing purchase flow.
     *  The activity receives the purchase result via PurchasesUpdatedListener,
     *  then calls [onPurchaseCompleted] with the token to verify server-side. */
    fun startPurchase(activityContext: android.app.Activity, billingClient: Any?) {
        // In production: use BillingClient.launchBillingFlow()
        // Purchase result → verify via Supabase Edge Function (verify-subscription)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Placeholder — wire BillingClient here
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            Text("✦", style = MaterialTheme.typography.displaySmall,
                color = SparkColors.CoralFlame)
            Text("Upgrade Spark", style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Find your person faster.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(28.dp))

            // Plan selection
            Plan.entries.forEach { plan ->
                PlanCard(
                    plan = plan,
                    isSelected = uiState.selectedPlan == plan,
                    onClick = { viewModel.selectPlan(plan) },
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(24.dp))

            // Feature list for selected plan
            FeatureList(plan = uiState.selectedPlan)

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { /* launch billing */ },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondary)
                } else {
                    Text("Subscribe · ${uiState.selectedPlan.price}${uiState.selectedPlan.period}",
                        style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Cancel any time. Subscriptions auto-renew unless cancelled at least 24 hours before renewal. " +
                "Managed via Google Play.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.secondary
                      else MaterialTheme.colorScheme.outline
    val bgColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                  else MaterialTheme.colorScheme.surface

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plan.label, style = MaterialTheme.typography.titleMedium)
                if (plan == Plan.GOLD) {
                    Text("Most popular", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
            Text(
                "${plan.price}${plan.period}",
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun FeatureList(plan: Plan) {
    val features: List<Pair<ImageVector, String>> = when (plan) {
        Plan.PLUS -> listOf(
            Icons.Filled.AllInclusive to "Unlimited likes",
            Icons.Filled.Replay to "Rewind last swipe",
            Icons.Filled.Star to "5 Super Likes per day",
        )
        Plan.GOLD -> listOf(
            Icons.Filled.AllInclusive to "Unlimited likes",
            Icons.Filled.Visibility to "See who liked you",
            Icons.Filled.Star to "10 Super Likes per day",
            Icons.Filled.Replay to "Unlimited rewinds",
            Icons.Filled.Bolt to "1 Profile Boost/month",
        )
        Plan.PLATINUM -> listOf(
            Icons.Filled.AllInclusive to "Unlimited likes",
            Icons.Filled.Visibility to "See who liked you",
            Icons.Filled.Star to "Unlimited Super Likes",
            Icons.Filled.Bolt to "5 Profile Boosts/month",
            Icons.Filled.TravelExplore to "Passport — swipe anywhere",
            Icons.Filled.LabelImportant to "Priority in discovery",
        )
    }

    Column(modifier = Modifier.padding(horizontal = 32.dp)) {
        features.forEach { (icon, label) ->
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
