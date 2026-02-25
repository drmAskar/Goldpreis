package com.goldpulse.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenu
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPulseScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val animatedAlpha by animateFloatAsState(targetValue = if (state.loading) 0.65f else 1f, label = "alpha")

    var threshold by remember(state.settings.thresholdPercent) { mutableStateOf(state.settings.thresholdPercent.toString()) }
    var interval by remember(state.settings.checkIntervalMinutes) { mutableStateOf(state.settings.checkIntervalMinutes.toString()) }
    var currency by remember(state.settings.currency) { mutableStateOf(state.settings.currency) }
    var expanded by remember { mutableStateOf(false) }

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
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "${state.currentPrice?.let { formatPrice(it, state.settings.currency) } ?: "--"}")
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = state.error ?: "")
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

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.label_currency)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("USD", "EUR", "GBP", "AED").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                currency = option
                                expanded = false
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val newSettings = SettingsState(
                            thresholdPercent = threshold.toDoubleOrNull() ?: state.settings.thresholdPercent,
                            currency = currency,
                            checkIntervalMinutes = interval.toIntOrNull() ?: state.settings.checkIntervalMinutes
                        )
                        viewModel.updateSettings(newSettings)
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                    TextButton(onClick = viewModel::refreshPrice) { Text(stringResource(R.string.action_refresh)) }
                }
            }
        }
    }
}
