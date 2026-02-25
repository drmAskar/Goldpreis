package com.goldpulse.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.goldpulse.MainViewModel
import com.goldpulse.R
import com.goldpulse.data.local.SettingsState
import com.goldpulse.ui.components.PriceChart
import com.goldpulse.util.formatPrice

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoldPulseScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val animatedAlpha by animateFloatAsState(targetValue = if (state.loading) 0.65f else 1f, label = "alpha")

    var threshold by remember(state.settings.thresholdPercent) { mutableStateOf(state.settings.thresholdPercent.toString()) }
    var interval by remember(state.settings.checkIntervalMinutes) { mutableStateOf(state.settings.checkIntervalMinutes.toString()) }
    var currenciesCsv by remember(state.settings.currenciesCsv) { mutableStateOf(state.settings.currenciesCsv) }
    var themeName by remember(state.settings.themeName) { mutableStateOf(state.settings.themeName) }

    var currenciesExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }

    val selectedCurrencies = state.settings.currenciesCsv.split(',').map { it.trim().uppercase() }.filter { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .alpha(animatedAlpha),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "GoldPulse", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.pricesByCurrency.forEach { (currency, price) ->
                        Card {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(text = currency, style = MaterialTheme.typography.labelMedium)
                                Text(text = formatPrice(price, currency), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Text(text = "Last update: ${state.lastUpdatedText}", style = MaterialTheme.typography.bodySmall)
                if (!state.error.isNullOrBlank()) Text(text = state.error ?: "")
            }
        }

        AnimatedVisibility(visible = state.history.isNotEmpty(), enter = fadeIn()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(stringResource(R.string.title_trend))
                    PriceChart(state.history)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.title_settings), style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text(stringResource(R.string.label_threshold)) },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(expanded = currenciesExpanded, onExpandedChange = { currenciesExpanded = !currenciesExpanded }) {
                    OutlinedTextField(
                        value = currenciesCsv,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Currencies") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currenciesExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    DropdownMenu(expanded = currenciesExpanded, onDismissRequest = { currenciesExpanded = false }) {
                        listOf("USD", "EUR", "GBP", "AED", "TRY", "SAR").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                val current = currenciesCsv.split(',').map { it.trim().uppercase() }.filter { it.isNotBlank() }.toMutableSet()
                                if (option in current) current.remove(option) else current.add(option)
                                currenciesCsv = current.joinToString(",")
                            })
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = themeExpanded, onExpandedChange = { themeExpanded = !themeExpanded }) {
                    OutlinedTextField(
                        value = themeName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Theme") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    DropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                        listOf("Purple", "Blue", "Emerald", "Dark").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                themeName = option
                                themeExpanded = false
                            })
                        }
                    }
                }

                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text(stringResource(R.string.label_interval)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Selected: ${selectedCurrencies.joinToString(" • ")}", style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val parsed = currenciesCsv.split(',').map { it.trim().uppercase() }.filter { it.isNotBlank() }
                        val primary = parsed.firstOrNull() ?: "USD"
                        val newSettings = SettingsState(
                            thresholdPercent = threshold.toDoubleOrNull() ?: state.settings.thresholdPercent,
                            currency = primary,
                            currenciesCsv = parsed.joinToString(","),
                            themeName = themeName,
                            checkIntervalMinutes = interval.toIntOrNull() ?: state.settings.checkIntervalMinutes
                        )
                        viewModel.updateSettings(newSettings)
                        viewModel.refreshPrice()
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                    TextButton(onClick = viewModel::refreshPrice) { Text(stringResource(R.string.action_refresh)) }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
