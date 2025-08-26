package com.example.stocklosers

// Essential Android imports
import android.Manifest
// import android.app.Application // Not directly used in this MainActivity structure
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

// Jetpack Compose UI imports
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
// import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

// Navigation
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

// Coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState

// Network
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

// Project specific classes - Assuming these are in their own files now
// in 'package com.example.stocklosers'
import com.example.stocklosers.AlertsUiState // Assuming this is defined
import com.example.stocklosers.AlertsViewModel
import com.example.stocklosers.AlertsViewModelFactory // Assuming this is defined
import com.example.stocklosers.Quote
import com.example.stocklosers.SettingsRepository
import com.example.stocklosers.StockUpdateWorker
import com.example.stocklosers.YahooMarketApi


// Define navigation routes as top-level constants
private const val ROUTE_ALERTS = "alerts"
private const val ROUTE_DIAGNOSTICS = "diagnostics"
private const val ROUTE_SETTINGS = "settings"
private const val STOCK_UPDATE_WORK_NAME = "stockPeriodicUpdateWork"


class MainActivity : ComponentActivity() {

    private val requestNotifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("Permissions", "Notification permission granted.")
        } else {
            Log.d("Permissions", "Notification permission denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        schedulePeriodicStockUpdates(applicationContext)

        setContent {
            StockLosersTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }

    private fun schedulePeriodicStockUpdates(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<StockUpdateWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            STOCK_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Log.d("MainActivity", "Periodic stock update work scheduled: $STOCK_UPDATE_WORK_NAME")
    }
}

@Composable
fun StockLosersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content
    )
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    Scaffold(
        bottomBar = { BottomBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_ALERTS,
            modifier = Modifier.padding(innerPadding)
        ) {
            alertsScreenRoute(navController = navController)
            composable(route = ROUTE_DIAGNOSTICS) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }
            composable(route = ROUTE_SETTINGS) {
                // Assuming SettingsRepository is correctly defined in its own file
                // and its constructor takes an applicationContext.
                SettingsScreen(settingsRepository = SettingsRepository(LocalContext.current.applicationContext))
            }
        }
    }
}

private fun NavGraphBuilder.alertsScreenRoute(navController: NavHostController) {
    composable(route = ROUTE_ALERTS) {
        AlertsScreen(navController = navController)
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == ROUTE_ALERTS,
            onClick = {
                navController.navigate(ROUTE_ALERTS) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            },
            icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = "Alerts") },
            label = { Text("Alerts") }
        )
        NavigationBarItem(
            selected = currentRoute == ROUTE_DIAGNOSTICS,
            onClick = {
                navController.navigate(ROUTE_DIAGNOSTICS) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            },
            icon = { Icon(Icons.Filled.Build, contentDescription = "Diagnostics") },
            label = { Text("Diag") }
        )
        NavigationBarItem(
            selected = currentRoute == ROUTE_SETTINGS,
            onClick = {
                navController.navigate(ROUTE_SETTINGS) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            },
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}

// This function seems generally useful and not tied to a specific class structure here.
suspend fun checkHttpConnectivity(hostUrl: String): String {
    return withContext(Dispatchers.IO) {
        try {
            Log.d("checkHttp", "Checking HTTP connectivity to $hostUrl")
            val url = URL(if (!hostUrl.startsWith("http")) "https://$hostUrl" else hostUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            val responseCode = connection.responseCode
            connection.disconnect()
            Log.d("checkHttp", "Response for $hostUrl: $responseCode")
            if (responseCode in 200..399) "HTTP to $hostUrl: Success (Code $responseCode)"
            else "HTTP to $hostUrl: Failed (Code $responseCode)"
        } catch (e: IOException) {
            Log.e("checkHttp", "HTTP check IO error for $hostUrl", e)
            "HTTP to $hostUrl: IO Error (${e.message})"
        } catch (e: Exception) {
            Log.e("checkHttp", "HTTP check general error for $hostUrl", e)
            "HTTP to $hostUrl: Error (${e.message})"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Assuming YahooMarketApi is correctly defined in its own file.
    val marketApi = remember { YahooMarketApi() }

    var httpTestResult by remember { mutableStateOf<String?>(null) }
    var apiTestResult by remember { mutableStateOf<String?>(null) }
    var isLoadingHttpTest by remember { mutableStateOf(false) }
    var isLoadingApiTest by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isLoadingHttpTest = true
                        httpTestResult = checkHttpConnectivity("https://finance.yahoo.com")
                        apiTestResult = null
                        isLoadingHttpTest = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoadingHttpTest && !isLoadingApiTest
            ) {
                if (isLoadingHttpTest) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text("Test HTTP to API Host")
            }
            httpTestResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Button(
                onClick = {
                    scope.launch {
                        isLoadingApiTest = true
                        apiTestResult = try {
                            val losers = marketApi.getDayLosers(limit = 3) // Assumes getDayLosers exists
                            if (losers.isNotEmpty()) "Market API: OK, ${losers.size} losers. First: ${losers.first().symbol}"
                            else "Market API: OK, 0 losers."
                        } catch (e: Exception) {
                            Log.e("DiagnosticsScreen", "Market API Test Failed", e)
                            "Market API: FAILED (${e.message})"
                        }
                        httpTestResult = null
                        isLoadingApiTest = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoadingHttpTest && !isLoadingApiTest
            ) {
                if (isLoadingApiTest) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text("Test Market API Function")
            }
            apiTestResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun QuoteCard(
    quote: Quote, // Assuming Quote is correctly defined in its own file
    isWatched: Boolean,
    onNavigateToYahoo: (String) -> Unit,
    onDeleteForDay: (Quote) -> Unit,
    onToggleWatchlist: (Quote) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onNavigateToYahoo(quote.symbol) },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(quote.symbol, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Change: ${String.format("%.2f", quote.percentChange)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = if (quote.percentChange < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Price: \$${String.format("%.2f", quote.price)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onToggleWatchlist(quote) }) {
                    Icon(
                        imageVector = if (isWatched) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isWatched) "Remove from Watchlist" else "Add to Watchlist",
                        tint = if (isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { onNavigateToYahoo(quote.symbol) }) {
                    Icon(Icons.Filled.Language, contentDescription = "Open in Web")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { onDeleteForDay(quote) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Dismiss for today")
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertsScreen(
    navController: NavHostController
) {
    val applicationContext = LocalContext.current.applicationContext
    // Assuming these are correctly defined in their own files
    val marketApi = remember { YahooMarketApi() }
    val settingsRepository = remember { SettingsRepository(applicationContext) }

    // Assuming AlertsViewModel and AlertsViewModelFactory are correctly defined and imported.
    val viewModel: AlertsViewModel = viewModel(
        factory = AlertsViewModelFactory(
            marketApi = marketApi,
            settingsRepository = settingsRepository,
            applicationContext = applicationContext
        )
    )

    val uiState by viewModel.uiState.collectAsState()
    val currentThresholdDisplay by settingsRepository.alertDropPercent.collectAsState(initial = 30.0)
    val context = LocalContext.current
    val watchlist by viewModel.watchlist.collectAsState()

    val handleNavigateToYahoo: (String) -> Unit = { symbol ->
        Log.d("AlertsScreen", "Navigating to Yahoo for $symbol")
        val url = "https://finance.yahoo.com/quote/$symbol"
        val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
        try { context.startActivity(intent) }
        catch (e: Exception) { Log.e("AlertsScreen", "Could not open web browser for $url", e) }
    }

    val handleDeleteForDay: (Quote) -> Unit = { quoteToDismiss ->
        Log.d("AlertsScreen", "Dismissing ${quoteToDismiss.symbol} for today")
        viewModel.dismissQuoteForToday(quoteToDismiss)
    }

    val handleToggleWatchlist: (Quote) -> Unit = { quoteToToggle ->
        if (watchlist.contains(quoteToToggle.symbol)) {
            viewModel.removeFromWatchlist(quoteToToggle.symbol)
        } else {
            viewModel.addToWatchlist(quoteToToggle.symbol)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock Alerts (-${currentThresholdDisplay.toInt()}%)") },
                actions = {
                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Data")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Assuming AlertsUiState is defined with these subtypes
            when (val state = uiState) {
                is AlertsUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text("Fetching alerts...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                is AlertsUiState.Success -> {
                    if (state.quotes.isEmpty()) {
                        EmptyState("No stocks currently meet the -${currentThresholdDisplay.toInt()}% drop criteria.")
                    } else {
                        Text(
                            "Displaying ${state.quotes.size} alerts:",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp).align(Alignment.Start)
                        )
                        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.quotes, key = { q -> q.symbol }) { quoteItem ->
                                QuoteCard(
                                    quote = quoteItem,
                                    isWatched = watchlist.contains(quoteItem.symbol),
                                    onNavigateToYahoo = handleNavigateToYahoo,
                                    onDeleteForDay = handleDeleteForDay,
                                    onToggleWatchlist = handleToggleWatchlist
                                )
                            }
                        }
                    }
                }
                is AlertsUiState.Empty -> {
                    EmptyState("No stocks meet the -${currentThresholdDisplay.toInt()}% drop criteria. Adjust threshold or refresh.")
                }
                is AlertsUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { viewModel.refreshData() }, modifier = Modifier.padding(top = 16.dp)) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsRepository: SettingsRepository) { // Assumes SettingsRepository is defined in its own file
    val scope = rememberCoroutineScope()
    // Using .collectAsState from kotlinx.coroutines.flow
    val threshold by settingsRepository.alertDropPercent.collectAsState(initial = 30.0)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Set Alert Drop Percentage", style = MaterialTheme.typography.titleLarge)
            Text("Current: ${threshold.toInt()}%", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = threshold.toFloat(),
                onValueChange = { newValueFloat ->
                    scope.launch {
                        settingsRepository.setAlertDropPercent(newValueFloat.toDouble().coerceIn(1.0, 99.0))
                    }
                },
                valueRange = 1f..99f,
                steps = 97,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            OutlinedTextField(
                value = String.format("%.1f", threshold),
                onValueChange = { textValue ->
                    textValue.toDoubleOrNull()?.let { numericValue ->
                        scope.launch {
                            settingsRepository.setAlertDropPercent(numericValue.coerceIn(1.0, 99.0))
                        }
                    }
                },
                label = { Text("Or type % (1-99)") },
                singleLine = true,
                modifier = Modifier.width(200.dp)
            )
            Text(
                "Alerts will trigger for stocks down by ${threshold.toInt()}% or more.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

// NOTE: The dummy/placeholder classes (Quote, SettingsRepository, YahooMarketApi, AlertHistory)
// have been REMOVED from this MainActivity.kt file.
// YOU MUST ENSURE they exist in their own separate .kt files
// (e.g., Quote.kt, SettingsRepository.kt) in the 'com.example.stocklosers' package
// and that those files are correct and do not have internal compile errors.
