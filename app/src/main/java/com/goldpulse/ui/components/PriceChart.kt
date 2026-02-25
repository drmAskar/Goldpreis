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
import com.goldpulse.data.model.PricePoint

@Composable
fun PriceChart(history: List<PricePoint>) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        factory = { context -> LineChart(context) },
        update = { chart ->
            val entries = history.mapIndexed { index, point -> Entry(index.toFloat(), point.price.toFloat()) }
            val dataSet = LineDataSet(entries, "Gold").apply {
                color = Color.parseColor("#D4AF37")
                valueTextColor = Color.TRANSPARENT
                lineWidth = 2.5f
                setDrawCircles(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }
            chart.data = LineData(dataSet)
            chart.description.isEnabled = false
            chart.axisRight.isEnabled = false
            chart.legend.isEnabled = false
            chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
            chart.xAxis.setDrawGridLines(false)
            chart.xAxis.setDrawLabels(false)
            chart.axisLeft.setDrawGridLines(true)
            chart.axisLeft.textSize = 10f
            chart.setNoDataText("No trend data yet")
            chart.invalidate()
        }
    )
}
