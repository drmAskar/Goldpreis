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

enum class Timeframe { DAY_1, WEEK_1, MONTH_1, MONTH_3, MONTH_6, YEAR_1, YEAR_5, MAX }

private fun timeframeWindowSeconds(timeframe: Timeframe): Long? = when (timeframe) {
    Timeframe.DAY_1 -> 24L * 60 * 60
    Timeframe.WEEK_1 -> 7L * 24 * 60 * 60
    Timeframe.MONTH_1 -> 30L * 24 * 60 * 60
    Timeframe.MONTH_3 -> 90L * 24 * 60 * 60
    Timeframe.MONTH_6 -> 180L * 24 * 60 * 60
    Timeframe.YEAR_1 -> 365L * 24 * 60 * 60
    Timeframe.YEAR_5 -> 365L * 24 * 60 * 60 * 5
    Timeframe.MAX -> null
}

private fun segmentedHistory(history: List<PricePoint>, timeframe: Timeframe): List<PricePoint> {
    if (history.isEmpty()) return emptyList()

    val sorted = history.sortedBy { it.timestamp }
    val filtered = timeframeWindowSeconds(timeframe)?.let { window ->
        val minTimestamp = (System.currentTimeMillis() / 1000) - window
        sorted.filter { it.timestamp >= minTimestamp }
    } ?: sorted

    val base = when {
        filtered.size >= 2 -> filtered
        sorted.size >= 2 -> sorted.takeLast(120)
        else -> sorted
    }

    val maxPoints = when (timeframe) {
        Timeframe.DAY_1 -> 96
        Timeframe.WEEK_1 -> 140
        Timeframe.MONTH_1 -> 160
        Timeframe.MONTH_3, Timeframe.MONTH_6 -> 180
        Timeframe.YEAR_1, Timeframe.YEAR_5, Timeframe.MAX -> 220
    }

    return downsample(base, maxPoints)
}

private fun downsample(points: List<PricePoint>, maxPoints: Int): List<PricePoint> {
    if (points.size <= maxPoints || maxPoints < 3) return points
    val sampled = ArrayList<PricePoint>(maxPoints)
    sampled += points.first()
    val buckets = maxPoints - 2
    val step = (points.size - 2).toFloat() / buckets
    for (i in 0 until buckets) {
        val index = 1 + (i * step).toInt().coerceIn(0, points.lastIndex - 1)
        sampled += points[index]
    }
    sampled += points.last()
    return sampled.distinctBy { it.timestamp }
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
                            Timeframe.MONTH_1, Timeframe.MONTH_3, Timeframe.MONTH_6 -> "dd MMM"
                            Timeframe.YEAR_1 -> "MMM yy"
                            Timeframe.YEAR_5, Timeframe.MAX -> "yyyy"
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
