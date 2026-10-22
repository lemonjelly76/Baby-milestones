package io.github.lemonjelly76.babymilestones.app.ui

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

private val Light = lightColorScheme(
    primary = Color(0xFF3F7D6E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4ECDF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF8A5A44),
    secondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFF4B6280),
    tertiaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFFFBFDF9),
    surface = Color(0xFFFBFDF9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA8D0C3),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF235F51),
    secondary = Color(0xFFFFB597),
    secondaryContainer = Color(0xFF6E4330),
    tertiary = Color(0xFFB3C8E8),
    tertiaryContainer = Color(0xFF334A66),
)

@Composable
fun BabyMilestonesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
