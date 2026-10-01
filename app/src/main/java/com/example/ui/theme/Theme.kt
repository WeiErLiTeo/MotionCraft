package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val PurpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
)

private val PurpleDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6750A4),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF6750A4),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141518),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1E2024),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
)

private fun buildLightScheme(primary: Color, primaryContainer: Color, onPrimaryContainer: Color, surfaceTint: Color): ColorScheme {
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = primary,
        onSecondary = Color.White,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = primary,
        onTertiary = Color.White,
        background = Color(0xFFFBF8F5),
        onBackground = Color(0xFF1B1B1F),
        surface = Color(0xFFF7F2EE),
        onSurface = Color(0xFF1B1B1F),
        surfaceVariant = surfaceTint,
        onSurfaceVariant = Color(0xFF43474E),
        outline = Color(0xFF73777F)
    )
}

private fun buildDarkScheme(primary: Color, primaryContainer: Color, onPrimaryContainer: Color, surfaceTint: Color): ColorScheme {
    return darkColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = primary,
        onSecondary = Color.White,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = primary,
        onTertiary = Color.White,
        background = Color(0xFF141518),
        onBackground = Color(0xFFE2E2E6),
        surface = Color(0xFF1E2024),
        onSurface = Color(0xFFE2E2E6),
        surfaceVariant = surfaceTint,
        onSurfaceVariant = Color(0xFFC3C7CF),
        outline = Color(0xFF8D9199)
    )
}

private fun getCustomColorScheme(darkTheme: Boolean, themeColor: String): ColorScheme {
    return when (themeColor) {
        "cyan" -> if (darkTheme) {
            buildDarkScheme(
                primary = Color(0xFF00838F),
                primaryContainer = Color(0xFF00838F),
                onPrimaryContainer = Color.White,
                surfaceTint = Color(0xFF263238)
            )
        } else {
            buildLightScheme(
                primary = Color(0xFF00838F),
                primaryContainer = Color(0xFFB2EBF2),
                onPrimaryContainer = Color(0xFF00363D),
                surfaceTint = Color(0xFFE0F7FA)
            )
        }
        "blue" -> if (darkTheme) {
            buildDarkScheme(
                primary = Color(0xFF1976D2),
                primaryContainer = Color(0xFF1976D2),
                onPrimaryContainer = Color.White,
                surfaceTint = Color(0xFF232B36)
            )
        } else {
            buildLightScheme(
                primary = Color(0xFF1976D2),
                primaryContainer = Color(0xFFBBDEFB),
                onPrimaryContainer = Color(0xFF0D47A1),
                surfaceTint = Color(0xFFE3F2FD)
            )
        }
        "green" -> if (darkTheme) {
            buildDarkScheme(
                primary = Color(0xFF2E7D32),
                primaryContainer = Color(0xFF2E7D32),
                onPrimaryContainer = Color.White,
                surfaceTint = Color(0xFF212C23)
            )
        } else {
            buildLightScheme(
                primary = Color(0xFF2E7D32),
                primaryContainer = Color(0xFFC8E6C9),
                onPrimaryContainer = Color(0xFF1B5E20),
                surfaceTint = Color(0xFFE8F5E9)
            )
        }
        "orange" -> if (darkTheme) {
            buildDarkScheme(
                primary = Color(0xFFE65100),
                primaryContainer = Color(0xFFE65100),
                onPrimaryContainer = Color.White,
                surfaceTint = Color(0xFF33261C)
            )
        } else {
            buildLightScheme(
                primary = Color(0xFFE65100),
                primaryContainer = Color(0xFFFFE0B2),
                onPrimaryContainer = Color(0xFFBF360C),
                surfaceTint = Color(0xFFFFF3E0)
            )
        }
        "pink" -> if (darkTheme) {
            buildDarkScheme(
                primary = Color(0xFFC2185B),
                primaryContainer = Color(0xFFC2185B),
                onPrimaryContainer = Color.White,
                surfaceTint = Color(0xFF332029)
            )
        } else {
            buildLightScheme(
                primary = Color(0xFFC2185B),
                primaryContainer = Color(0xFFF8BBD0),
                onPrimaryContainer = Color(0xFF880E4F),
                surfaceTint = Color(0xFFFCE4EC)
            )
        }
        "purple" -> if (darkTheme) {
            buildDarkScheme(
                primary = Color(0xFF7B1FA2),
                primaryContainer = Color(0xFF7B1FA2),
                onPrimaryContainer = Color.White,
                surfaceTint = Color(0xFF2E2236)
            )
        } else {
            buildLightScheme(
                primary = Color(0xFF7B1FA2),
                primaryContainer = Color(0xFFE1BEE7),
                onPrimaryContainer = Color(0xFF4A148C),
                surfaceTint = Color(0xFFF3E5F5)
            )
        }
        "red" -> if (darkTheme) {
            buildDarkScheme(
                primary = Color(0xFFC62828),
                primaryContainer = Color(0xFFC62828),
                onPrimaryContainer = Color.White,
                surfaceTint = Color(0xFF352020)
            )
        } else {
            buildLightScheme(
                primary = Color(0xFFC62828),
                primaryContainer = Color(0xFFFFCDD2),
                onPrimaryContainer = Color(0xFFB71C1C),
                surfaceTint = Color(0xFFFFEBEE)
            )
        }
        else -> if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
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
