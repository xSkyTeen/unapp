package sky.universidad.UnApp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import sky.universidad.UnApp.data.preferences.ThemeMode

@Immutable
data class UnAppExtendedColors(
    val isDark: Boolean,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val surfaceElevated: Color,
    val brutalShadow: Color
)

val LocalUnAppColors = staticCompositionLocalOf {
    UnAppExtendedColors(
        isDark = false,
        success = MikuSuccessLight,
        successContainer = MikuSuccessContainerLight,
        onSuccessContainer = MikuOnSuccessContainerLight,
        warning = MikuWarningLight,
        warningContainer = MikuWarningContainerLight,
        onWarningContainer = MikuOnWarningContainerLight,
        glassSurface = Color(0xD9F1F6FC),
        glassBorder = Color(0x99CBD8EA),
        surfaceElevated = MikuSurfaceContainerLight,
        brutalShadow = Color(0x0D0F172A)
    )
}

private val LightExtended = UnAppExtendedColors(
    isDark = false,
    success = MikuSuccessLight,
    successContainer = MikuSuccessContainerLight,
    onSuccessContainer = MikuOnSuccessContainerLight,
    warning = MikuWarningLight,
    warningContainer = MikuWarningContainerLight,
    onWarningContainer = MikuOnWarningContainerLight,
    glassSurface = Color(0xD9F1F6FC),
    glassBorder = Color(0x99CBD8EA),
    surfaceElevated = MikuSurfaceContainerLight,
    brutalShadow = Color(0x0D0F172A)
)

private val DarkExtended = UnAppExtendedColors(
    isDark = true,
    success = MikuSuccessDark,
    successContainer = MikuSuccessContainerDark,
    onSuccessContainer = MikuOnSuccessContainerDark,
    warning = MikuWarningDark,
    warningContainer = MikuWarningContainerDark,
    onWarningContainer = MikuOnWarningContainerDark,
    glassSurface = Color(0xD91D2A42),
    glassBorder = Color(0x99364B6E),
    surfaceElevated = MikuSurfaceContainerDark,
    brutalShadow = Color(0x28000000)
)

private val DarkColorScheme = darkColorScheme(
    primary = MikuPrimaryDark,
    onPrimary = MikuOnPrimaryDark,
    primaryContainer = MikuPrimaryContainerDark,
    onPrimaryContainer = MikuOnPrimaryContainerDark,
    secondary = MikuSecondaryDark,
    onSecondary = MikuOnSecondaryDark,
    secondaryContainer = MikuSecondaryContainerDark,
    onSecondaryContainer = MikuOnSecondaryContainerDark,
    tertiary = MikuTertiaryDark,
    onTertiary = MikuOnTertiaryDark,
    tertiaryContainer = MikuTertiaryContainerDark,
    onTertiaryContainer = MikuOnTertiaryContainerDark,
    background = MikuBackgroundDark,
    onBackground = MikuOnSurfaceDark,
    surface = MikuSurfaceDark,
    onSurface = MikuOnSurfaceDark,
    surfaceContainer = MikuSurfaceContainerDark,
    surfaceVariant = MikuSurfaceVariantDark,
    onSurfaceVariant = MikuOnSurfaceVariantDark,
    outline = MikuOutlineDark,
    outlineVariant = MikuOutlineVariantDark,
    error = MikuErrorDark,
    errorContainer = MikuErrorContainerDark,
    onErrorContainer = MikuOnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = MikuPrimaryLight,
    onPrimary = MikuOnPrimaryLight,
    primaryContainer = MikuPrimaryContainerLight,
    onPrimaryContainer = MikuOnPrimaryContainerLight,
    secondary = MikuSecondaryLight,
    onSecondary = MikuOnSecondaryLight,
    secondaryContainer = MikuSecondaryContainerLight,
    onSecondaryContainer = MikuOnSecondaryContainerLight,
    tertiary = MikuTertiaryLight,
    onTertiary = MikuOnTertiaryLight,
    tertiaryContainer = MikuTertiaryContainerLight,
    onTertiaryContainer = MikuOnTertiaryContainerLight,
    background = MikuBackgroundLight,
    onBackground = MikuOnSurfaceLight,
    surface = MikuSurfaceLight,
    onSurface = MikuOnSurfaceLight,
    surfaceContainer = MikuSurfaceContainerLight,
    surfaceVariant = MikuSurfaceVariantLight,
    onSurfaceVariant = MikuOnSurfaceVariantLight,
    outline = MikuOutlineLight,
    outlineVariant = MikuOutlineVariantLight,
    error = MikuErrorLight,
    errorContainer = MikuErrorContainerLight,
    onErrorContainer = MikuOnErrorContainerLight
)

private val UnAppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun UnAppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtended else LightExtended

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalUnAppColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = UnAppShapes,
            content = content
        )
    }
}
