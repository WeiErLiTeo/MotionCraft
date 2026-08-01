package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SleekDarkColorScheme = darkColorScheme(
    primary = SleekPrimary,
    onPrimary = SleekOnPrimary,
    primaryContainer = SleekPrimaryContainer,
    onPrimaryContainer = SleekOnPrimaryContainer,
    background = SleekBackground,
    onBackground = SleekOnBackground,
    surface = SleekSurface,
    onSurface = SleekOnBackground,
    surfaceVariant = SleekSurfaceVariant,
    onSurfaceVariant = SleekOnSurfaceVariant,
    outline = SleekOutline,
)

private val SleekLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
)

// Define different color schemes for user to choose
private fun getCustomColorScheme(darkTheme: Boolean, themeColor: String): androidx.compose.material3.ColorScheme {
    return when (themeColor) {
        "blue" -> if (darkTheme) {
            darkColorScheme(primary = Color(0xFF82B1FF), primaryContainer = Color(0xFF1565C0), onPrimary = Color.Black)
        } else {
            lightColorScheme(primary = Color(0xFF1976D2), primaryContainer = Color(0xFFBBDEFB))
        }
        "green" -> if (darkTheme) {
            darkColorScheme(primary = Color(0xFFB9F6CA), primaryContainer = Color(0xFF1B5E20), onPrimary = Color.Black)
        } else {
            lightColorScheme(primary = Color(0xFF388E3C), primaryContainer = Color(0xFFC8E6C9))
        }
        "orange" -> if (darkTheme) {
            darkColorScheme(primary = Color(0xFFFFD180), primaryContainer = Color(0xFFE65100), onPrimary = Color.Black)
        } else {
            lightColorScheme(primary = Color(0xFFF57C00), primaryContainer = Color(0xFFFFE0B2))
        }
        "pink" -> if (darkTheme) {
            darkColorScheme(primary = Color(0xFFFF80AB), primaryContainer = Color(0xFFC51162), onPrimary = Color.White)
        } else {
            lightColorScheme(primary = Color(0xFFC2185B), primaryContainer = Color(0xFFF8BBD0))
        }
        "cyan" -> if (darkTheme) {
            darkColorScheme(primary = Color(0xFF80DEEA), primaryContainer = Color(0xFF00838F), onPrimary = Color.Black)
        } else {
            lightColorScheme(primary = Color(0xFF0097A7), primaryContainer = Color(0xFFB2EBF2))
        }
        "purple" -> if (darkTheme) {
            darkColorScheme(primary = Color(0xFFE1BEE7), primaryContainer = Color(0xFF6A1B9A), onPrimary = Color.Black)
        } else {
            lightColorScheme(primary = Color(0xFF7B1FA2), primaryContainer = Color(0xFFE1BEE7))
        }
        "red" -> if (darkTheme) {
            darkColorScheme(primary = Color(0xFFFFCDD2), primaryContainer = Color(0xFFC62828), onPrimary = Color.Black)
        } else {
            lightColorScheme(primary = Color(0xFFD32F2F), primaryContainer = Color(0xFFFFCDD2))
        }
        else -> if (darkTheme) SleekDarkColorScheme else SleekLightColorScheme
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeColor: String = "default",
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getCustomColorScheme(darkTheme, themeColor)
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

