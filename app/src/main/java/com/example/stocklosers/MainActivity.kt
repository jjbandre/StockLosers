package com.example.stocklosers

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ needs explicit notification permission
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Periodic background checks (~15 min) via WorkManager
        WorkScheduler.schedulePeriodic(this)

        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                Scaffold(
                    bottomBar = { BottomBar(nav) }
                ) { pad ->
                    NavHost(
                        navController = nav,
                        startDestination = "alerts",
                        modifier = Modifier.padding(pad)
                    ) {
                        composable("alerts") { AlertsScreen() }
                        composable("settings") {
                            SettingsScreen(SettingsRepository(this@MainActivity))
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Bottom navigation ---------- */
@Composable
private fun BottomBar(nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    NavigationBar {
        NavigationBarItem(
            selected = route == "alerts",
            onClick = { nav.navigate("alerts") },
            icon = { Icon(Icons.Default.Refresh, contentDescription = "Alerts") },
            label = { Text("Alerts") }
        )
        NavigationBarItem(
            selected = route == "settings",
            onClick = { nav.navigate("settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}

/* ---------- Alerts screen (manual refresh + online dot + ping + test API) ---------- */
@Composable
fun AlertsScreen() {
    val ctx = LocalContext.current
    val settings = remember { SettingsRepository(ctx) }
    val uiStore = remember { UiStateStore(ctx) }
    val api: MarketApi = remember { YahooMarketApi() }

    var threshold by remember { mutableStateOf(35.0) }
    var losers by remember { mutableStateOf(emptyList<Quote>()) }
    var pinned by remember { mutableStateOf(emptySet<String>()) }
    var dismissed by remember { mutableStateOf(emptySet<String>()) }
    var diagText by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // connectivity monitor
    val monitor = remember { NetworkMonitor(ctx) }
    DisposableEffect(Unit) { onDispose { monitor.dispose() } }
    val isOnline by monitor.isOnline // State<Boolean> exposed directly

    // collect settings & UI-state flows
    LaunchedEffect(Unit) {
        launch { settings.alertDropPercent.collect { threshold = it } }
        launch { uiStore.pinned.collect { pinned = it } }
        launch { uiStore.dismissedToday.collect { dismissed = it } }
    }

    // auto-refresh while this screen is visible (~60s)
    LaunchedEffect(threshold) {
        while (true) {
            losers = api.getDayLosers(limit = 100)
            delay(60_000L)
        }
    }

    val hits = remember(losers, threshold, dismissed) {
        losers.filter { it.percentChange <= -threshold && it.symbol !in dismissed }
    }
    val pinnedHits = hits.filter { it.symbol in pinned }
    val otherHits = hits.filterNot { it.symbol in pinned }

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        // Header row: status dot + title + refresh icon
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            StatusDot(isOnline)
            Spacer(Modifier.width(6.dp))
            Text(
                if (isOnline) "Online" else "Offline",
                color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Losers ≤ −${"%.1f".format(threshold)}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { scope.launch { losers = api.getDayLosers(100) } }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { scope.launch { losers = api.getDayLosers(100) } }) {
                Text("Refresh now")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    diagText = if (quickPing()) "Ping: OK" else "Ping: Failed"
                }
            }) { Text("Ping") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        val list = api.getDayLosers(50)
                        val sample = list.take(5)
                            .joinToString { "${it.symbol} ${"%.1f".format(it.percentChange)}%" }
                        diagText = "API OK: ${list.size} items. Sample: $sample"
                    } catch (e: Exception) {
                        diagText = "API ERROR: ${e.javaClass.simpleName}: ${e.message}"
                    }
                }
            }) { Text("Test API") }
        }
        if (diagText != null) {
            Spacer(Modifier.height(8.dp))
            Text(diagText!!)
        }

        if (hits.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("No matches right now. Try lowering the threshold or refresh.")
        } else {
            LazyColumn {
                if (pinnedHits.isNotEmpty()) {
                    item { SectionHeader("Pinned") }
                    items(pinnedHits, key = { it.symbol }) { q ->
                        QuoteRow(
                            q,
                            pinned = true,
                            onTogglePin = { scope.launch { uiStore.togglePin(q.symbol) } },
                            onDismiss = { scope.launch { uiStore.dismiss(q.symbol) } }
                        )
                    }
                }
                if (otherHits.isNotEmpty()) {
                    if (pinnedHits.isNotEmpty()) item { Spacer(Modifier.height(8.dp)) }
                    item { SectionHeader("Other") }
                    items(otherHits, key = { it.symbol }) { q ->
                        QuoteRow(
                            q,
                            pinned = false,
                            onTogglePin = { scope.launch { uiStore.togglePin(q.symbol) } },
                            onDismiss = { scope.launch { uiStore.dismiss(q.symbol) } }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Pinned stays visible; Delete hides for the day. " +
                    "Notifications still run every ~15 min."
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun QuoteRow(
    q: Quote,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            q.symbol,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(80.dp)
        )
        Text("${"%.1f".format(q.percentChange)}%", modifier = Modifier.width(80.dp))
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onTogglePin) {
            if (pinned) Icon(Icons.Filled.Star, contentDescription = "Unpin")
            else Icon(Icons.Outlined.StarBorder, contentDescription = "Pin")
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}

/* ---------- Settings screen ---------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: SettingsRepository) {
    val scope = rememberCoroutineScope()
    var threshold by remember { mutableStateOf(35.0) }

    LaunchedEffect(Unit) {
        settings.alertDropPercent.collect { threshold = it }
    }

    val ctx = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Stock Losers Alerts") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Alert when stock is down at least:")
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = String.format("%.1f", threshold),
                    onValueChange = { t ->
                        t.toDoubleOrNull()?.let { v ->
                            scope.launch { settings.setAlertDropPercent(v) }
                        }
                    },
                    label = { Text("% drop") },
                    singleLine = true,
                    modifier = Modifier.width(160.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text("Current: ≤ −${"%.1f".format(threshold)}%")
            }

            Spacer(Modifier.height(16.dp))
            Slider(
                value = threshold.toFloat(),
                onValueChange = { v -> scope.launch { settings.setAlertDropPercent(v.toDouble()) } },
                valueRange = 1f..90f,
                steps = 178
            )

            Spacer(Modifier.height(24.dp))
            Button(onClick = { WorkScheduler.runOnceNow(ctx) }) {
                Text("Run check now (send notification)")
            }

            Spacer(Modifier.height(8.dp))
            Text("Background checks run ~every 15 minutes via WorkManager.")
        }
    }
}

/* ---------- Connectivity helpers (green/red dot + ping) ---------- */

class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun hasInternet(): Boolean {
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val _isOnline = mutableStateOf(hasInternet())
    val isOnline: State<Boolean> get() = _isOnline

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _isOnline.value = true }
        override fun onLost(network: Network) { _isOnline.value = hasInternet() }
        override fun onCapabilitiesChanged(network: Network, nc: NetworkCapabilities) {
            _isOnline.value = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    init {
        cm.registerDefaultNetworkCallback(callback)
    }

    fun dispose() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}

@Composable
private fun StatusDot(online: Boolean) {
    val color = if (online) Color(0xFF2E7D32) else Color(0xFFC62828)
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// Fast HTTP reachability check (Google 204 endpoint)
private suspend fun quickPing(): Boolean = withContext(Dispatchers.IO) {
    try {
        OkHttpClient()
            .newCall(Request.Builder().url("https://www.google.com/generate_204").build())
            .execute()
            .use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }
}
