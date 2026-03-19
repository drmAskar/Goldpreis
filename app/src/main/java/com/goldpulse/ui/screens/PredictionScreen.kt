package com.goldpulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.goldpulse.data.model.*
import com.goldpulse.domain.PredictionEngine
import com.goldpulse.util.formatPrice
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Prediction Screen - Gold Price Prediction with Factor Analysis
 * 
 * Features:
 * - Auto-fetches indicators: DXY, US10Y, Breakeven10Y, VIX
 * - Displays gold score with bullish/neutral/bearish badge
 * - Shows factor contribution breakdown
 * - Timeline chart with forecast band
 * - Full Arabic labels and RTL support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionScreen(
    state: PredictionState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with refresh button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تنبؤ الذهب", // Gold Prediction in Arabic
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh"
                )
            }
        }
        
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = state.error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        } else if (state.prediction != null) {
            // Score and Badge Card
            ScoreBadgeCard(prediction = state.prediction)
            
            // Factor Breakdown Card
            FactorBreakdownCard(factors = state.prediction.factors)
            
            // Indicators Summary Card
            IndicatorsSummaryCard(indicators = state.prediction.indicators)
            
            // Forecast Chart Card (requires >= 30 points)
            if (state.goldHistory.size >= 30) {
                ForecastChartCard(
                    history = state.goldHistory,
                    prediction = state.prediction
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "يحتاج الرسم البياني إلى 30 نقطة بيانات على الأقل", // Chart needs at least 30 data points
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        // Last updated text
        if (state.lastUpdated.isNotEmpty()) {
            Text(
                text = "آخر تحديث: ${state.lastUpdated}", // Last update
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScoreBadgeCard(prediction: GoldPrediction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "درجة التنبؤ", // Prediction Score
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Score display
            Text(
                text = String.format(Locale.getDefault(), "%.2f", prediction.score),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = when (prediction.badge) {
                    PredictionBadge.BULLISH -> Color(0xFF2E7D32)
                    PredictionBadge.NEUTRAL -> Color(0xFFF57C00)
                    PredictionBadge.BEARISH -> Color(0xFFC62828)
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = when (prediction.badge) {
                    PredictionBadge.BULLISH -> Color(0xFF4CAF50)
                    PredictionBadge.NEUTRAL -> Color(0xFFFFA726)
                    PredictionBadge.BEARISH -> Color(0xFFEF5350)
                }
            ) {
                Text(
                    text = prediction.badgeAr,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Interpretation
            Text(
                text = when (prediction.badge) {
                    PredictionBadge.BULLISH -> "الظروف مواتية لارتفاع سعر الذهب" // Conditions favorable for gold price increase
                    PredictionBadge.NEUTRAL -> "إشارات مختلطة، لا اتجاه واضح" // Mixed signals, no clear direction
                    PredictionBadge.BEARISH -> "الظروف تشير إلى انخفاض سعر الذهب" // Conditions indicate gold price decrease
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FactorBreakdownCard(factors: List<FactorContribution>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "توزيع العوامل", // Factor Distribution
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            factors.forEach { factor ->
                FactorItem(factor = factor)
                if (factor != factors.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun FactorItem(factor: FactorContribution) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = factor.nameAr,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = factor.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = String.format(Locale.getDefault(), "%.1f%%", factor.weight * 100),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.getDefault(), "%+.2f", factor.weightedScore),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when (factor.direction) {
                    "bullish" -> Color(0xFF2E7D32)
                    "bearish" -> Color(0xFFC62828)
                    else -> Color(0xFFF57C00)
                }
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Direction indicator
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when (factor.direction) {
                "bullish" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                "bearish" -> Color(0xFFEF5350).copy(alpha = 0.2f)
                else -> Color(0xFFFFA726).copy(alpha = 0.2f)
            }
        ) {
            Text(
                text = factor.directionAr,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = when (factor.direction) {
                    "bullish" -> Color(0xFF2E7D32)
                    "bearish" -> Color(0xFFC62828)
                    else -> Color(0xFFF57C00)
                }
            )
        }
    }
}

@Composable
private fun IndicatorsSummaryCard(indicators: List<IndicatorData>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "المؤشرات الحالية", // Current Indicators
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            indicators.forEach { indicator ->
                IndicatorItem(indicator = indicator)
                if (indicator != indicators.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            if (indicators.isEmpty()) {
                Text(
                    text = "لا توجد بيانات متاحة", // No data available
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IndicatorItem(indicator: IndicatorData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = when (indicator.symbol) {
                    "DXY" -> "مؤشر الدولار"
                    "US10Y" -> "عائد 10 سنوات"
                    "Breakeven10Y" -> "توقعات التضخم"
                    "VIX" -> "مؤشر الخوف"
                    else -> indicator.symbol
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = indicator.source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = String.format(Locale.getDefault(), "%.2f", indicator.value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format(Locale.getDefault(), "%+.2f%%", indicator.changePercent),
                style = MaterialTheme.typography.bodySmall,
                color = if (indicator.changePercent >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}

@Composable
private fun ForecastChartCard(
    history: List<PricePoint>,
    prediction: GoldPrediction
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "الرسم البياني مع نطاق التنبؤ", // Chart with Forecast Band
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Forecast band info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ForecastStat(label = "المتوسط", value = prediction.forecastMA) // MA
                ForecastStat(label = "الانحراف المعياري", value = prediction.forecastStd) // Std
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Forecast bands
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ForecastBand(
                    label = "الحد الأعلى", // Upper Band
                    value = prediction.forecastBandUpper,
                    color = Color(0xFF4CAF50)
                )
                ForecastBand(
                    label = "الحد الأدنى", // Lower Band
                    value = prediction.forecastBandLower,
                    color = Color(0xFFEF5350)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Chart
            ForecastChart(
                history = history.takeLast(60), // Show last 60 points
                upperBand = prediction.forecastBandUpper,
                lowerBand = prediction.forecastBandLower,
                ma = prediction.forecastMA
            )
        }
    }
}

@Composable
private fun ForecastStat(label: String, value: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = String.format(Locale.getDefault(), "%.2f", value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ForecastBand(label: String, value: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = String.format(Locale.getDefault(), "%.2f", value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ForecastChart(
    history: List<PricePoint>,
    upperBand: Double,
    lowerBand: Double,
    ma: Double
) {
    val context = LocalContext.current
    
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        factory = { ctx ->
            LineChart(ctx)
        },
        update = { chart ->
            if (history.isEmpty()) {
                chart.clear()
                chart.setNoDataText("لا توجد بيانات") // No data in Arabic
                return@AndroidView
            }
            
            val entries = history.mapIndexed { index, point ->
                Entry(index.toFloat(), point.price.toFloat())
            }
            
            val mainSet = LineDataSet(entries, "Gold").apply {
                color = android.graphics.Color.parseColor("#D4AF37")
                valueTextColor = android.graphics.Color.TRANSPARENT
                lineWidth = 2.5f
                setDrawCircles(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = android.graphics.Color.parseColor("#33D4AF37")
            }
            
            // Upper band line
            val upperEntries = history.mapIndexed { index, _ ->
                Entry(index.toFloat(), upperBand.toFloat())
            }
            val upperSet = LineDataSet(upperEntries, "Upper Band").apply {
                color = android.graphics.Color.parseColor("#4CAF50")
                valueTextColor = android.graphics.Color.TRANSPARENT
                lineWidth = 1.5f
                setDrawCircles(false)
                enableDashedLine(5f, 5f, 0f)
            }
            
            // Lower band line
            val lowerEntries = history.mapIndexed { index, _ ->
                Entry(index.toFloat(), lowerBand.toFloat())
            }
            val lowerSet = LineDataSet(lowerEntries, "Lower Band").apply {
                color = android.graphics.Color.parseColor("#EF5350")
                valueTextColor = android.graphics.Color.TRANSPARENT
                lineWidth = 1.5f
                setDrawCircles(false)
                enableDashedLine(5f, 5f, 0f)
            }
            
            // MA line
            val maEntries = history.mapIndexed { index, _ ->
                Entry(index.toFloat(), ma.toFloat())
            }
            val maSet = LineDataSet(maEntries, "MA").apply {
                color = android.graphics.Color.parseColor("#2196F3")
                valueTextColor = android.graphics.Color.TRANSPARENT
                lineWidth = 1.5f
                setDrawCircles(false)
            }
            
            chart.data = LineData(listOf(mainSet, upperSet, lowerSet, maSet))
            chart.description.isEnabled = false
            chart.axisRight.isEnabled = false
            chart.legend.isEnabled = true
            chart.legend.textSize = 10f
            
            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textSize = 10f
                labelRotationAngle = -20f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt().coerceIn(0, history.lastIndex)
                        val millis = history.getOrNull(index)?.timestamp?.times(1000) ?: return ""
                        return SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(millis))
                    }
                }
            }
            
            chart.axisLeft.apply {
                setDrawGridLines(true)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return String.format(Locale.getDefault(), "%.0f", value)
                    }
                }
            }
            
            chart.invalidate()
        }
    )
}
