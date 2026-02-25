package com.goldpulse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PurpleScheme = lightColorScheme()

private val BlueScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF1565C0),
    secondary = androidx.compose.ui.graphics.Color(0xFF42A5F5)
)

private val EmeraldScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF00796B),
    secondary = androidx.compose.ui.graphics.Color(0xFF26A69A)
)

private val DarkScheme = darkColorScheme()

@Composable
fun GoldPulseTheme(
    themeName: String = "Purple",
    content: @Composable () -> Unit
) {
    val colors = when (themeName) {
        "Blue" -> BlueScheme
        "Emerald" -> EmeraldScheme
        "Dark" -> DarkScheme
        else -> PurpleScheme
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
