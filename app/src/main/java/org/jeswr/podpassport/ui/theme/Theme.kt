package org.jeswr.podpassport.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Passport-ink palette: a deep indigo primary with a warm gold accent — a
// deliberate, considered scheme rather than default Material purple/grey.
private val Indigo = Color(0xFF3B3F8C)
private val IndigoLight = Color(0xFFB9BCEC)
private val Gold = Color(0xFF9A6B12)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E2FF),
    onPrimaryContainer = Color(0xFF14164A),
    secondary = Gold,
    onSecondary = Color.White,
    tertiary = Color(0xFF166A4E),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF1B1E54),
    primaryContainer = Color(0xFF2A2D6E),
    onPrimaryContainer = Color(0xFFE2E2FF),
    secondary = Color(0xFFE8C07A),
    onSecondary = Color(0xFF402D00),
    tertiary = Color(0xFF7FD8B2),
)

@Composable
fun PodPassportTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
