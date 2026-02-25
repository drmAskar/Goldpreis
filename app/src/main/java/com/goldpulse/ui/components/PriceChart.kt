package com.goldpulse.ui.components

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.goldpulse.R
import com.goldpulse.data.model.PricePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Timeframe { DAY_1, WEEK_1, MONTH_1, YEAR_1 }

private fun timeframeWindowSeconds(timeframe: Timeframe): Long = when (timeframe) {
    Timeframe.DAY_1 -> 24L * 60 * 60
    Timeframe.WEEK_1 -> 7L * 24 * 60 * 60
    Timeframe.MONTH_1 -> 30L * 24 * 60 * 60
    Timeframe.YEAR_1 -> 365L * 24 * 60 * 60
}

private fun segmentedHistory(history: List<PricePoint>, timeframe: Timeframe): List<PricePoint> {
    if (history.isEmpty()) return emptyList()
    val now = System.currentTimeMillis() / 1000
    val minTimestamp = now - timeframeWindowSeconds(timeframe)
    val filtered = history.filter { it.timestamp >= minTimestamp }

    return when {
        filtered.size >= 2 -> filtered
        history.size >= 2 -> history.takeLast(30)
        else -> history
    }
}

@Composable
fun PriceChart(
    history: List<PricePoint>,
    timeframe: Timeframe
) {
    val visibleHistory = segmentedHistory(history, timeframe)

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        factory = { context -> LineChart(context) },
        update = { chart ->
            val entries = visibleHistory.mapIndexed { index, point -> Entry(index.toFloat(), point.price.toFloat()) }
            val dataSet = LineDataSet(entries, "Gold").apply {
                color = Color.parseColor("#D4AF37")
                valueTextColor = Color.TRANSPARENT
                lineWidth = 2.5f
                setDrawCircles(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = Color.parseColor("#33D4AF37")
            }

            chart.data = LineData(dataSet)
            chart.description.isEnabled = false
            chart.axisRight.isEnabled = false
            chart.legend.isEnabled = false
            chart.setViewPortOffsets(56f, 24f, 24f, 56f)
            chart.setExtraBottomOffset(8f)

            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textSize = 11f
                labelRotationAngle = -25f
                labelCount = 4
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt().coerceIn(0, (visibleHistory.size - 1).coerceAtLeast(0))
                        val millis = visibleHistory.getOrNull(index)?.timestamp?.times(1000) ?: return ""
                        val pattern = when (timeframe) {
                            Timeframe.DAY_1 -> "HH:mm"
                            Timeframe.WEEK_1 -> "EEE"
                            Timeframe.MONTH_1 -> "dd MMM"
                            Timeframe.YEAR_1 -> "MMM yy"
                        }
                        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
                    }
                }
            }

            chart.axisLeft.apply {
                setDrawGridLines(true)
                textSize = 11f
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = String.format(Locale.getDefault(), "%.0f", value)
                }
            }

            chart.setNoDataText(chart.context.getString(R.string.no_data_trend))
            chart.invalidate()
        }
    )
}
