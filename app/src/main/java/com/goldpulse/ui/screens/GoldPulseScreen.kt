package com.goldpulse.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goldpulse.MainViewModel
import com.goldpulse.R
import com.goldpulse.data.local.SettingsState
import com.goldpulse.ui.components.PriceChart
import com.goldpulse.ui.components.Timeframe
import com.goldpulse.util.formatPrice
import java.util.Locale

private val allCurrencies = listOf("USD", "EUR", "GBP", "AED", "TRY", "SAR")
private val allThemes = listOf("Purple", "Blue", "Emerald", "Dark")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GoldPulseScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val animatedAlpha by animateFloatAsState(
        targetValue = if (state.loading) 0.75f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "screen_alpha"
    )

    var showSettings by remember { mutableStateOf(false) }
    var selectedTimeframe by remember { mutableStateOf(Timeframe.DAY_1) }
    val selectedCurrencies = state.settings.currenciesCsv.split(',').map { it.trim().uppercase() }.filter { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .alpha(animatedAlpha),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.last_update, state.lastUpdatedText.ifBlank { "—" }), style = MaterialTheme.typography.bodySmall)
                Text(text = stringResource(R.string.data_source_label, state.dataSourceLabel), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.open_settings))
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedCurrencies.forEach { currency ->
                        val price = state.pricesByCurrency[currency]
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = if (price != null) "$currency ${formatPrice(price, currency)}" else currency,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                val primary = state.settings.currency
                val currentPrice = state.currentPrice
                if (currentPrice != null) {
                    Text(text = stringResource(R.string.label_spot_price, formatPrice(currentPrice, primary)), style = MaterialTheme.typography.titleMedium)
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SnapshotChip("O", state.openPrice?.let { formatPrice(it, primary) } ?: "—")
                    SnapshotChip("H", state.highPrice?.let { formatPrice(it, primary) } ?: "—")
                    SnapshotChip("L", state.lowPrice?.let { formatPrice(it, primary) } ?: "—")
                    SnapshotChip("24h", state.dailyChangePercent?.let { String.format(Locale.getDefault(), "%.2f%%", it) } ?: "—")
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = viewModel::refreshPrice) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        val prices = selectedCurrencies.joinToString("\n") { currency ->
                            val price = state.pricesByCurrency[currency]
                            if (price != null) "• $currency: ${formatPrice(price, currency)}" else "• $currency: —"
                        }
                        val extra = context.getString(R.string.share_summary_line, selectedTimeframe.name)
                        val content = context.getString(R.string.share_text, "$prices\n$extra", state.lastUpdatedText.ifBlank { "—" })
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, content)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_title)))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                    }
                }

                if (state.staleData) {
                    Text(text = stringResource(R.string.stale_data_warning), color = MaterialTheme.colorScheme.error)
                }
                if (state.parityWarning) {
                    Text(text = stringResource(R.string.parity_warning), color = MaterialTheme.colorScheme.error)
                }
                if (!state.error.isNullOrBlank()) {
                    Text(text = stringResource(R.string.error_transparency, state.error ?: ""), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        AnimatedVisibility(
            visible = state.history.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
        ) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.title_trend), style = MaterialTheme.typography.titleMedium)
                    TimeframeSelector(
                        selected = selectedTimeframe,
                        onSelected = {
                            selectedTimeframe = it
                            viewModel.onTimeframeChanged(it)
                        }
                    )
                    if (state.historyLoading) {
                        Text(stringResource(R.string.loading_history))
                    }
                    PriceChart(
                        history = state.history,
                        timeframe = selectedTimeframe,
                        showMovingAverages = state.settings.showMovingAverages
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showSettings) {
        SettingsSheet(
            current = state.settings,
            onDismiss = { showSettings = false },
            onSave = {
                viewModel.updateSettings(it)
                viewModel.refreshPrice()
                showSettings = false
            }
        )
    }
}

@Composable
private fun SnapshotChip(label: String, value: String) {
    AssistChip(
        onClick = {},
        label = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "$label:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(current: SettingsState, onDismiss: () -> Unit, onSave: (SettingsState) -> Unit) {
    var threshold by remember(current.thresholdPercent) { mutableStateOf(current.thresholdPercent.toString()) }
    var interval by remember(current.checkIntervalMinutes) { mutableStateOf(current.checkIntervalMinutes.toString()) }
    var selectedTheme by remember(current.themeName) { mutableStateOf(current.themeName) }
    var bgEnabled by remember(current.backgroundNotificationsEnabled) { mutableStateOf(current.backgroundNotificationsEnabled) }
    var persistentEnabled by remember(current.persistentForegroundEnabled) { mutableStateOf(current.persistentForegroundEnabled) }
    var showMovingAverages by remember(current.showMovingAverages) { mutableStateOf(current.showMovingAverages) }
    var alertAbove by remember(current.alertAbovePrice) { mutableStateOf(current.alertAbovePrice?.toString() ?: "") }
    var alertBelow by remember(current.alertBelowPrice) { mutableStateOf(current.alertBelowPrice?.toString() ?: "") }
    val selectedCurrencies = remember(current.currenciesCsv) {
        mutableStateListOf(*current.currenciesCsv.split(',').map { it.trim().uppercase() }.filter { it.isNotBlank() }.toTypedArray())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = stringResource(R.string.title_settings), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.hint_settings_save_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                                if (currency in selectedCurrencies) selectedCurrencies.remove(currency)
                                else selectedCurrencies.add(currency)
                            },
                            label = { Text(currency) }
                        )
                    }
                }

                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text(stringResource(R.string.label_threshold)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text(stringResource(R.string.label_interval)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = alertAbove,
                    onValueChange = { alertAbove = it },
                    label = { Text(stringResource(R.string.label_alert_above)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = alertBelow,
                    onValueChange = { alertBelow = it },
                    label = { Text(stringResource(R.string.label_alert_below)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.label_background_notifications))
                    Switch(checked = bgEnabled, onCheckedChange = { bgEnabled = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.label_persistent_background))
                    Switch(checked = persistentEnabled, onCheckedChange = { persistentEnabled = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.label_show_moving_averages))
                    Switch(checked = showMovingAverages, onCheckedChange = { showMovingAverages = it })
                }
                Text(
                    text = stringResource(R.string.helper_show_moving_averages),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = {
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
                                showMovingAverages = showMovingAverages,
                                alertAbovePrice = alertAbove.toDoubleOrNull(),
                                alertBelowPrice = alertBelow.toDoubleOrNull()
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.action_save)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

@Composable
private fun BoxChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(
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
