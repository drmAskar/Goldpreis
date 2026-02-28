package com.goldpulse.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.mikephil.charting.charts.LineChart
import com.goldpulse.DataQuality
import com.goldpulse.MainViewModel
import com.goldpulse.R
import com.goldpulse.data.local.AlertDirection
import com.goldpulse.data.local.SettingsState
import com.goldpulse.ui.components.PriceChart
import com.goldpulse.ui.components.Timeframe
import com.goldpulse.util.ExportMode
import com.goldpulse.util.exportChartPng
import com.goldpulse.util.formatPrice
import com.goldpulse.util.renderChartForExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val allCurrencies = listOf("USD", "EUR", "GBP", "AED", "TRY", "SAR")
private val allThemes = listOf("Purple", "Blue", "Emerald", "Dark")

enum class MainTab { HOME, CHARTS, ALERTS, SETTINGS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoldPulseScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedTimeframe by remember { mutableStateOf(Timeframe.DAY_1) }
    var chartRef by remember { mutableStateOf<LineChart?>(null) }
    var tab by remember { mutableStateOf(MainTab.HOME) }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (state.loading) 0.78f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "screen_alpha"
    )

    val currency = state.settings.currency

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == MainTab.HOME, onClick = { tab = MainTab.HOME }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationBarItem(selected = tab == MainTab.CHARTS, onClick = { tab = MainTab.CHARTS }, icon = { Icon(Icons.Default.ShowChart, null) }, label = { Text("Charts") })
                NavigationBarItem(selected = tab == MainTab.ALERTS, onClick = { tab = MainTab.ALERTS }, icon = { Icon(Icons.Default.Notifications, null) }, label = { Text("Alerts") })
                NavigationBarItem(selected = tab == MainTab.SETTINGS, onClick = { tab = MainTab.SETTINGS }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
                .alpha(animatedAlpha),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Header(state.dataSourceLabel, state.lastUpdatedText, state.quality)

            AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab_transition") { target ->
                when (target) {
                    MainTab.HOME -> HomeTab(
                        state = state,
                        selectedTimeframe = selectedTimeframe,
                        onRefresh = viewModel::refreshPrice,
                        onShare = {
                            val details = listOf(
                                "${currency} ${state.currentPrice?.let { formatPrice(it, currency) } ?: "—"}",
                                "24h: ${state.dailyChangePercent?.let { String.format(Locale.getDefault(), "%.2f%%", it) } ?: "—"}",
                                "O/H/L: ${state.openPrice?.let { formatPrice(it, currency) } ?: "—"} / ${state.highPrice?.let { formatPrice(it, currency) } ?: "—"} / ${state.lowPrice?.let { formatPrice(it, currency) } ?: "—"}",
                                "Timeframe: ${selectedTimeframe.name}",
                                "Currency: $currency",
                                "Timestamp: ${state.lastUpdatedText.ifBlank { "—" }}"
                            ).joinToString("\n")
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "GoldPulse\n$details")
                            }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_title)))
                        }
                    )

                    MainTab.CHARTS -> ChartsTab(
                        state = state,
                        selectedTimeframe = selectedTimeframe,
                        onTimeframeSelected = {
                            selectedTimeframe = it
                            viewModel.onTimeframeChanged(it)
                        },
                        onChartReady = { chartRef = it },
                        onQuickExport = {
                            val chart = chartRef ?: return@ChartsTab
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) {
                                    exportChartPng(context, renderChartForExport(chart, ExportMode.QUICK))
                                }
                                if (uri == null) Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                                else {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Quick export"))
                                }
                            }
                        },
                        onFullExport = {
                            val chart = chartRef ?: return@ChartsTab
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) {
                                    exportChartPng(context, renderChartForExport(chart, ExportMode.FULL), "goldpulse-chart-full-${System.currentTimeMillis()}.png")
                                }
                                if (uri == null) Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                                else {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Full export"))
                                }
                            }
                        }
                    )

                    MainTab.ALERTS -> AlertsTab(
                        currentCurrency = currency,
                        alerts = state.alerts,
                        onAddAlert = viewModel::addAlert,
                        onDelete = viewModel::removeAlert
                    )

                    MainTab.SETTINGS -> SettingsPanel(
                        current = state.settings,
                        onSave = {
                            viewModel.updateSettings(it)
                            viewModel.refreshPrice()
                            Toast.makeText(context, context.getString(R.string.action_save), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(source: String, updated: String, quality: DataQuality) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = stringResource(R.string.last_update, updated.ifBlank { "—" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = stringResource(R.string.data_source_label, source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val (label, color) = when (quality) {
            DataQuality.LIVE -> "Live" to Color(0xFF2E7D32)
            DataQuality.DELAYED -> "Delayed" to Color(0xFFE65100)
            DataQuality.PARTIAL -> "Partial" to Color(0xFF6A1B9A)
        }
        AssistChip(onClick = {}, label = { Text(label) }, colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = 0.16f), labelColor = color))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeTab(state: com.goldpulse.UiState, selectedTimeframe: Timeframe, onRefresh: () -> Unit, onShare: () -> Unit) {
    val primary = state.settings.currency
    val selectedCurrencies = state.settings.currenciesCsv.split(',').map { it.trim().uppercase() }.filter { it.isNotBlank() }

    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedCurrencies.forEach { c ->
                    AssistChip(onClick = {}, label = { Text("$c ${state.pricesByCurrency[c]?.let { formatPrice(it, c) } ?: "—"}") })
                }
            }
            Text(text = stringResource(R.string.label_spot_price, state.currentPrice?.let { formatPrice(it, primary) } ?: "—"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SnapshotChip("O", state.openPrice?.let { formatPrice(it, primary) } ?: "—")
                SnapshotChip("H", state.highPrice?.let { formatPrice(it, primary) } ?: "—")
                SnapshotChip("L", state.lowPrice?.let { formatPrice(it, primary) } ?: "—")
                SnapshotChip("24h", state.dailyChangePercent?.let { String.format(Locale.getDefault(), "%.2f%%", it) } ?: "—")
                SnapshotChip("TF", selectedTimeframe.name)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh)) }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share)) }
            }
        }
    }
}

@Composable
private fun ChartsTab(
    state: com.goldpulse.UiState,
    selectedTimeframe: Timeframe,
    onTimeframeSelected: (Timeframe) -> Unit,
    onChartReady: (LineChart) -> Unit,
    onQuickExport: () -> Unit,
    onFullExport: () -> Unit
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.title_trend), style = MaterialTheme.typography.titleMedium)
            TimeframeSelector(selectedTimeframe, onTimeframeSelected)
            PriceChart(history = state.history, timeframe = selectedTimeframe, showMovingAverages = state.settings.showMovingAverages, onChartReady = onChartReady)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onQuickExport) { Text("Quick Export") }
                Button(onClick = onFullExport) { Text("Full Export") }
            }
            if (selectedTimeframe == Timeframe.DAY_1 && state.insufficientIntradayData) {
                Text(stringResource(R.string.insufficient_intraday_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertsTab(
    currentCurrency: String,
    alerts: List<com.goldpulse.data.local.PriceAlert>,
    onAddAlert: (String, AlertDirection, Double) -> Unit,
    onDelete: (String) -> Unit
) {
    var target by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(AlertDirection.ABOVE) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Create alert", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = direction == AlertDirection.ABOVE, onClick = { direction = AlertDirection.ABOVE }, label = { Text("Above") })
                    FilterChip(selected = direction == AlertDirection.BELOW, onClick = { direction = AlertDirection.BELOW }, label = { Text("Below") })
                }
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target ($currentCurrency)") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val value = target.toDoubleOrNull() ?: return@Button
                    onAddAlert(currentCurrency, direction, value)
                    target = ""
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add alert")
                }
            }
        }

        alerts.forEach { alert ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${alert.currency} ${if (alert.direction == AlertDirection.ABOVE) "Above" else "Below"} ${formatPrice(alert.targetPrice, alert.currency)}")
                    TextButton(onClick = { onDelete(alert.id) }) { Text("Delete") }
                }
            }
        }
        if (alerts.isEmpty()) Text("No alerts yet")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPanel(current: SettingsState, onSave: (SettingsState) -> Unit) {
    var threshold by remember(current.thresholdPercent) { mutableStateOf(current.thresholdPercent.toString()) }
    var interval by remember(current.checkIntervalMinutes) { mutableStateOf(current.checkIntervalMinutes.toString()) }
    var selectedTheme by remember(current.themeName) { mutableStateOf(current.themeName) }
    var bgEnabled by remember(current.backgroundNotificationsEnabled) { mutableStateOf(current.backgroundNotificationsEnabled) }
    var persistentEnabled by remember(current.persistentForegroundEnabled) { mutableStateOf(current.persistentForegroundEnabled) }
    var persistentTickerEnabled by remember(current.persistentTickerEnabled) { mutableStateOf(current.persistentTickerEnabled) }
    var showMovingAverages by remember(current.showMovingAverages) { mutableStateOf(current.showMovingAverages) }
    val selectedCurrencies = remember(current.currenciesCsv) {
        mutableStateListOf(*current.currenciesCsv.split(',').map { it.trim().uppercase() }.filter { it.isNotBlank() }.toTypedArray())
    }

    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stringResource(R.string.title_settings), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.hint_settings_save_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = stringResource(R.string.label_theme_color), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                allThemes.forEach { theme ->
                    val selected = selectedTheme == theme
                    val color = when (theme) {
                        "Blue" -> Color(0xFF1565C0)
                        "Emerald" -> Color(0xFF00796B)
                        "Dark" -> Color(0xFF424242)
                        else -> Color(0xFF6750A4)
                    }
                    BoxChip(label = theme, color = color, selected = selected) { selectedTheme = theme }
                }
            }
            Text(text = stringResource(R.string.label_currencies), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allCurrencies.forEach { currency ->
                    FilterChip(
                        selected = currency in selectedCurrencies,
                        onClick = {
                            if (currency in selectedCurrencies) selectedCurrencies.remove(currency) else selectedCurrencies.add(currency)
                        },
                        label = { Text(currency) }
                    )
                }
            }
            OutlinedTextField(value = threshold, onValueChange = { threshold = it }, label = { Text(stringResource(R.string.label_threshold)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = interval, onValueChange = { interval = it }, label = { Text(stringResource(R.string.label_interval)) }, modifier = Modifier.fillMaxWidth())
            ToggleRow(stringResource(R.string.label_background_notifications), bgEnabled) { bgEnabled = it }
            ToggleRow(stringResource(R.string.label_persistent_background), persistentEnabled) { persistentEnabled = it }
            ToggleRow(stringResource(R.string.label_persistent_ticker), persistentTickerEnabled) { persistentTickerEnabled = it }
            ToggleRow(stringResource(R.string.label_show_moving_averages), showMovingAverages) { showMovingAverages = it }
            Button(onClick = {
                val currencies = selectedCurrencies.ifEmpty { mutableStateListOf("USD") }
                val primary = currencies.first()
                onSave(
                    SettingsState(
                        thresholdPercent = threshold.toDoubleOrNull() ?: current.thresholdPercent,
                        currency = primary,
                        currenciesCsv = currencies.joinToString(","),
                        themeName = selectedTheme,
                        checkIntervalMinutes = interval.toIntOrNull()?.coerceAtLeast(1) ?: current.checkIntervalMinutes,
                        backgroundNotificationsEnabled = bgEnabled,
                        persistentForegroundEnabled = persistentEnabled,
                        persistentTickerEnabled = persistentTickerEnabled,
                        showMovingAverages = showMovingAverages,
                        alertAbovePrice = current.alertAbovePrice,
                        alertBelowPrice = current.alertBelowPrice
                    )
                )
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun SnapshotChip(label: String, value: String) {
    AssistChip(
        onClick = {},
        shape = RoundedCornerShape(14.dp),
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        label = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeframeSelector(selected: Timeframe, onSelected: (Timeframe) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(selected = selected == Timeframe.DAY_1, onClick = { onSelected(Timeframe.DAY_1) }, label = { Text("1D") })
        FilterChip(selected = selected == Timeframe.WEEK_1, onClick = { onSelected(Timeframe.WEEK_1) }, label = { Text("1W") })
        FilterChip(selected = selected == Timeframe.MONTH_1, onClick = { onSelected(Timeframe.MONTH_1) }, label = { Text("1M") })
        FilterChip(selected = selected == Timeframe.MONTH_3, onClick = { onSelected(Timeframe.MONTH_3) }, label = { Text("3M") })
        FilterChip(selected = selected == Timeframe.MONTH_6, onClick = { onSelected(Timeframe.MONTH_6) }, label = { Text("6M") })
        FilterChip(selected = selected == Timeframe.YEAR_1, onClick = { onSelected(Timeframe.YEAR_1) }, label = { Text("1Y") })
        FilterChip(selected = selected == Timeframe.YEAR_5, onClick = { onSelected(Timeframe.YEAR_5) }, label = { Text("5Y") })
        FilterChip(selected = selected == Timeframe.MAX, onClick = { onSelected(Timeframe.MAX) }, label = { Text("MAX") })
    }
}

@Composable
private fun BoxChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(text = if (selected) "✓ $label" else label)
        }
    }
}
