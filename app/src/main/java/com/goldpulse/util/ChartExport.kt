package com.goldpulse.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.github.mikephil.charting.charts.LineChart
import java.io.File
import java.io.FileOutputStream

enum class ExportMode { QUICK, FULL }

fun renderChartForExport(chart: LineChart, mode: ExportMode): Bitmap {
    val base = chart.chartBitmap ?: Bitmap.createBitmap(chart.width.coerceAtLeast(1), chart.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val margin = if (mode == ExportMode.FULL) 96 else 56
    val widthScale = if (mode == ExportMode.FULL) 1.45f else 1f
    val outWidth = (base.width * widthScale).toInt().coerceAtLeast(base.width + margin * 2)
    val outHeight = base.height + margin * 2

    val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(Color.WHITE)
    val left = ((outWidth - base.width) / 2f)
    val top = margin.toFloat()
    canvas.drawBitmap(base, left, top, null)
    return result
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
