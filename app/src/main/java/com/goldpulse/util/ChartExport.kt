package com.goldpulse.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.YAxis
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class ExportMode { QUICK, FULL }

fun renderChartForExport(chart: LineChart, mode: ExportMode): Bitmap {
    val pass1 = renderWithSafeBounds(chart, mode, extraRightPadding = 0f)
    val clipped = isRightEdgePotentiallyClipped(chart)
    return if (clipped) {
        // One adaptive re-render pass as a hard no-clip fallback.
        pass1.recycle()
        renderWithSafeBounds(chart, mode, extraRightPadding = if (mode == ExportMode.FULL) 32f else 24f)
    } else {
        pass1
    }
}

private fun renderWithSafeBounds(chart: LineChart, mode: ExportMode, extraRightPadding: Float): Bitmap {
    val safeWidth = chart.width.coerceAtLeast(1)
    val safeHeight = chart.height.coerceAtLeast(1)

    val originalLeft = chart.extraLeftOffset
    val originalTop = chart.extraTopOffset
    val originalRight = chart.extraRightOffset
    val originalBottom = chart.extraBottomOffset

    val yLabelWidth = measureYAxisMaxLabelWidth(chart)
    val xRotatedHeight = measureXAxisRotatedLabelHeight(chart)

    val rightPadding = max(originalRight, yLabelWidth + if (mode == ExportMode.FULL) 36f else 24f + extraRightPadding)
    val bottomPadding = max(originalBottom, xRotatedHeight + if (mode == ExportMode.FULL) 34f else 24f)

    chart.setExtraOffsets(
        max(originalLeft, 16f),
        max(originalTop, 12f),
        rightPadding,
        bottomPadding
    )
    chart.notifyDataSetChanged()
    chart.calculateOffsets()
    chart.invalidate()

    val base = chart.chartBitmap ?: Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)

    val outerMargin = if (mode == ExportMode.FULL) 72 else 40
    val extraCanvasWidth = if (mode == ExportMode.FULL) 96 else 32
    val outWidth = base.width + outerMargin * 2 + extraCanvasWidth
    val outHeight = base.height + outerMargin * 2

    val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(Color.WHITE)
    canvas.save()
    canvas.translate(outerMargin.toFloat(), outerMargin.toFloat())
    canvas.drawBitmap(base, 0f, 0f, null)
    canvas.restore()

    chart.setExtraOffsets(originalLeft, originalTop, originalRight, originalBottom)
    chart.notifyDataSetChanged()
    chart.calculateOffsets()
    chart.invalidate()

    return result
}

private fun measureYAxisMaxLabelWidth(chart: LineChart): Float {
    val axis = when {
        chart.axisRight.isEnabled -> chart.axisRight
        chart.axisLeft.isEnabled -> chart.axisLeft
        else -> chart.axisLeft
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = axis.textSize
        typeface = axis.typeface
    }

    val maxValue = axis.axisMaximum
    val minValue = axis.axisMinimum
    val maxLabel = axis.valueFormatter?.getFormattedValue(maxValue) ?: maxValue.toString()
    val minLabel = axis.valueFormatter?.getFormattedValue(minValue) ?: minValue.toString()
    val labelWidth = max(paint.measureText(maxLabel), paint.measureText(minLabel))

    return ceil(labelWidth + axis.xOffset + 10f)
}

private fun measureXAxisRotatedLabelHeight(chart: LineChart): Float {
    val axis = chart.xAxis
    if (!axis.isEnabled) return 0f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = axis.textSize
        typeface = axis.typeface
    }

    val rightLabel = axis.valueFormatter?.getFormattedValue(chart.xChartMax) ?: ""
    val leftLabel = axis.valueFormatter?.getFormattedValue(chart.xChartMin) ?: ""
    val sample = if (rightLabel.length >= leftLabel.length) rightLabel else leftLabel

    val textWidth = paint.measureText(sample)
    val bounds = Rect()
    paint.getTextBounds(sample, 0, sample.length, bounds)
    val textHeight = bounds.height().toFloat().coerceAtLeast(axis.textSize)

    val angleRad = Math.toRadians(abs(axis.labelRotationAngle.toDouble()))
    val rotatedHeight = (sin(angleRad) * textWidth + cos(angleRad) * textHeight).toFloat()

    return ceil(rotatedHeight + axis.yOffset + 8f)
}

private fun isRightEdgePotentiallyClipped(chart: LineChart): Boolean {
    val xAxis = chart.xAxis
    if (!xAxis.isEnabled) return false

    val label = xAxis.valueFormatter?.getFormattedValue(chart.xChartMax) ?: return false
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = xAxis.textSize
        typeface = xAxis.typeface
    }

    val halfLabel = paint.measureText(label) / 2f
    val rightContent = chart.viewPortHandler.contentRight()
    val projectedRight = rightContent + halfLabel + xAxis.xOffset + 6f

    if (projectedRight >= chart.width - 2f) return true

    if (chart.axisRight.isEnabled) {
        val yAxis = chart.axisRight
        val yPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = yAxis.textSize
            typeface = yAxis.typeface
        }
        val yLabel = yAxis.valueFormatter?.getFormattedValue(yAxis.axisMaximum) ?: yAxis.axisMaximum.toString()
        val yProjectedRight = rightContent + yPaint.measureText(yLabel) + yAxis.xOffset + 6f
        if (yProjectedRight >= chart.width - 2f) return true
    }

    return false
}

fun exportChartPng(context: Context, bitmap: Bitmap, fileName: String = "goldpulse-chart-${System.currentTimeMillis()}.png"): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GoldPulse")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrNull()
    } else {
        runCatching {
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }
}
