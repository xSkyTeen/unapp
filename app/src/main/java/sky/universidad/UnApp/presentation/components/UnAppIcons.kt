package sky.universidad.UnApp.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Monoline vector icon set crafted with uniform stroke weights (1.8f, rounded caps/joins)
 * to keep a calm, minimalist Apple-inspired look without emojis.
 */
object UnAppIcons {

    private fun monoline(name: String, block: PathBuilder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color.Black),
                strokeAlpha = 1f,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
                pathBuilder = block
            )
        }.build()
    }

    val Home: ImageVector by lazy {
        monoline("Home") {
            moveTo(3f, 10.5f)
            lineTo(12f, 3f)
            lineTo(21f, 10.5f)
            moveTo(5f, 9.5f)
            verticalLineTo(20f)
            curveTo(5f, 20.55f, 5.45f, 21f, 6f, 21f)
            horizontalLineTo(10f)
            verticalLineTo(15f)
            horizontalLineTo(14f)
            verticalLineTo(21f)
            horizontalLineTo(18f)
            curveTo(18.55f, 21f, 19f, 20.55f, 19f, 20f)
            verticalLineTo(9.5f)
        }
    }

    val Book: ImageVector by lazy {
        monoline("Book") {
            moveTo(4f, 5f)
            curveTo(4f, 3.9f, 4.9f, 3f, 6f, 3f)
            horizontalLineTo(19f)
            verticalLineTo(18f)
            horizontalLineTo(6f)
            curveTo(4.9f, 18f, 4f, 18.9f, 4f, 20f)
            close()
            moveTo(4f, 20f)
            curveTo(4f, 20.55f, 4.45f, 21f, 5f, 21f)
            horizontalLineTo(19f)
            moveTo(8f, 7f)
            horizontalLineTo(15f)
        }
    }

    val CheckSquare: ImageVector by lazy {
        monoline("CheckSquare") {
            moveTo(9f, 12f)
            lineTo(11.2f, 14.2f)
            lineTo(15.5f, 9.5f)
            moveTo(5f, 3f)
            horizontalLineTo(19f)
            curveTo(20.1f, 3f, 21f, 3.9f, 21f, 5f)
            verticalLineTo(19f)
            curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
            horizontalLineTo(5f)
            curveTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
            verticalLineTo(5f)
            curveTo(3f, 3.9f, 3.9f, 3f, 5f, 3f)
            close()
        }
    }

    val Calendar: ImageVector by lazy {
        monoline("Calendar") {
            moveTo(4f, 7f)
            curveTo(4f, 5.9f, 4.9f, 5f, 6f, 5f)
            horizontalLineTo(18f)
            curveTo(19.1f, 5f, 20f, 5.9f, 20f, 7f)
            verticalLineTo(19f)
            curveTo(20f, 20.1f, 19.1f, 21f, 18f, 21f)
            horizontalLineTo(6f)
            curveTo(4.9f, 21f, 4f, 20.1f, 4f, 19f)
            close()
            moveTo(8f, 3f)
            verticalLineTo(7f)
            moveTo(16f, 3f)
            verticalLineTo(7f)
            moveTo(4f, 10f)
            horizontalLineTo(20f)
        }
    }

    val Grid: ImageVector by lazy {
        monoline("Grid") {
            moveTo(4f, 4f); horizontalLineTo(10f); verticalLineTo(10f); horizontalLineTo(4f); close()
            moveTo(14f, 4f); horizontalLineTo(20f); verticalLineTo(10f); horizontalLineTo(14f); close()
            moveTo(4f, 14f); horizontalLineTo(10f); verticalLineTo(20f); horizontalLineTo(4f); close()
            moveTo(14f, 14f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(14f); close()
        }
    }

    val Note: ImageVector by lazy {
        monoline("Note") {
            moveTo(6f, 3f)
            horizontalLineTo(15f)
            lineTo(19f, 7f)
            verticalLineTo(20f)
            curveTo(19f, 20.55f, 18.55f, 21f, 18f, 21f)
            horizontalLineTo(6f)
            curveTo(5.45f, 21f, 5f, 20.55f, 5f, 20f)
            verticalLineTo(4f)
            curveTo(5f, 3.45f, 5.45f, 3f, 6f, 3f)
            close()
            moveTo(9f, 11f); horizontalLineTo(15f)
            moveTo(9f, 15f); horizontalLineTo(13f)
        }
    }

    val Forum: ImageVector by lazy {
        monoline("Forum") {
            moveTo(4f, 6f)
            curveTo(4f, 4.9f, 4.9f, 4f, 6f, 4f)
            horizontalLineTo(18f)
            curveTo(19.1f, 4f, 20f, 4.9f, 20f, 6f)
            verticalLineTo(14f)
            curveTo(20f, 15.1f, 19.1f, 16f, 18f, 16f)
            horizontalLineTo(9f)
            lineTo(5f, 20f)
            verticalLineTo(16f)
            horizontalLineTo(6f)
            curveTo(4.9f, 16f, 4f, 15.1f, 4f, 14f)
            close()
            moveTo(8f, 9f); horizontalLineTo(16f)
            moveTo(8f, 12f); horizontalLineTo(13f)
        }
    }

    val Bell: ImageVector by lazy {
        monoline("Bell") {
            moveTo(18f, 15f)
            verticalLineTo(10f)
            curveTo(18f, 6.69f, 15.31f, 4f, 12f, 4f)
            curveTo(8.69f, 4f, 6f, 6.69f, 6f, 10f)
            verticalLineTo(15f)
            lineTo(4f, 17.5f)
            horizontalLineTo(20f)
            close()
            moveTo(10f, 19.5f)
            curveTo(10.5f, 20.5f, 11.2f, 21f, 12f, 21f)
            curveTo(12.8f, 21f, 13.5f, 20.5f, 14f, 19.5f)
        }
    }

    val Bus: ImageVector by lazy {
        monoline("Bus") {
            moveTo(5f, 5f)
            curveTo(5f, 3.9f, 6.2f, 3f, 8f, 3f)
            horizontalLineTo(16f)
            curveTo(17.8f, 3f, 19f, 3.9f, 19f, 5f)
            verticalLineTo(17f)
            curveTo(19f, 18.1f, 18.1f, 19f, 17f, 19f)
            horizontalLineTo(7f)
            curveTo(5.9f, 19f, 5f, 18.1f, 5f, 17f)
            close()
            moveTo(5f, 11f); horizontalLineTo(19f)
            moveTo(7f, 19f); verticalLineTo(21f)
            moveTo(17f, 19f); verticalLineTo(21f)
            moveTo(8f, 15f); horizontalLineTo(8.5f)
            moveTo(15.5f, 15f); horizontalLineTo(16f)
        }
    }

    val Settings: ImageVector by lazy {
        monoline("Settings") {
            moveTo(12f, 8.5f)
            curveTo(10.07f, 8.5f, 8.5f, 10.07f, 8.5f, 12f)
            curveTo(8.5f, 13.93f, 10.07f, 15.5f, 12f, 15.5f)
            curveTo(13.93f, 15.5f, 15.5f, 13.93f, 15.5f, 12f)
            curveTo(15.5f, 10.07f, 13.93f, 8.5f, 12f, 8.5f)
            close()
            moveTo(12f, 3f); verticalLineTo(5f)
            moveTo(12f, 19f); verticalLineTo(21f)
            moveTo(4.22f, 7.5f); lineTo(5.95f, 8.5f)
            moveTo(18.05f, 15.5f); lineTo(19.78f, 16.5f)
            moveTo(4.22f, 16.5f); lineTo(5.95f, 15.5f)
            moveTo(18.05f, 8.5f); lineTo(19.78f, 7.5f)
        }
    }

    val Refresh: ImageVector by lazy {
        monoline("Refresh") {
            moveTo(20f, 11f)
            curveTo(19.45f, 6.9f, 15.95f, 4f, 11.8f, 4f)
            curveTo(8.55f, 4f, 5.75f, 5.95f, 4.5f, 8.8f)
            moveTo(4f, 4f); verticalLineTo(9f); horizontalLineTo(9f)
            moveTo(4f, 13f)
            curveTo(4.55f, 17.1f, 8.05f, 20f, 12.2f, 20f)
            curveTo(15.45f, 20f, 18.25f, 18.05f, 19.5f, 15.2f)
            moveTo(20f, 20f); verticalLineTo(15f); horizontalLineTo(15f)
        }
    }

    val Lock: ImageVector by lazy {
        monoline("Lock") {
            moveTo(5f, 11f)
            horizontalLineTo(19f)
            verticalLineTo(20f)
            curveTo(19f, 20.55f, 18.55f, 21f, 18f, 21f)
            horizontalLineTo(6f)
            curveTo(5.45f, 21f, 5f, 20.55f, 5f, 20f)
            close()
            moveTo(8f, 11f)
            verticalLineTo(7f)
            curveTo(8f, 4.79f, 9.79f, 3f, 12f, 3f)
            curveTo(14.21f, 3f, 16f, 4.79f, 16f, 7f)
            verticalLineTo(11f)
        }
    }

    val Eye: ImageVector by lazy {
        monoline("Eye") {
            moveTo(2f, 12f)
            curveTo(4.5f, 7.5f, 8f, 5.5f, 12f, 5.5f)
            curveTo(16f, 5.5f, 19.5f, 7.5f, 22f, 12f)
            curveTo(19.5f, 16.5f, 16f, 18.5f, 12f, 18.5f)
            curveTo(8f, 18.5f, 4.5f, 16.5f, 2f, 12f)
            close()
            moveTo(12f, 9.5f)
            curveTo(10.62f, 9.5f, 9.5f, 10.62f, 9.5f, 12f)
            curveTo(9.5f, 13.38f, 10.62f, 14.5f, 12f, 14.5f)
            curveTo(13.38f, 14.5f, 14.5f, 13.38f, 14.5f, 12f)
            curveTo(14.5f, 10.62f, 13.38f, 9.5f, 12f, 9.5f)
            close()
        }
    }

    val EyeOff: ImageVector by lazy {
        monoline("EyeOff") {
            moveTo(3f, 3f); lineTo(21f, 21f)
            moveTo(10.6f, 5.7f)
            curveTo(11.06f, 5.57f, 11.53f, 5.5f, 12f, 5.5f)
            curveTo(16f, 5.5f, 19.5f, 7.5f, 22f, 12f)
            curveTo(21.1f, 13.6f, 20f, 14.9f, 18.7f, 15.9f)
            moveTo(6.2f, 7.3f)
            curveTo(4.5f, 8.5f, 3.1f, 10.1f, 2f, 12f)
            curveTo(4.5f, 16.5f, 8f, 18.5f, 12f, 18.5f)
            curveTo(13.4f, 18.5f, 14.8f, 18.2f, 16.1f, 17.6f)
        }
    }

    val WifiOff: ImageVector by lazy {
        monoline("WifiOff") {
            moveTo(3f, 3f); lineTo(21f, 21f)
            moveTo(8.5f, 16.5f)
            curveTo(10.4f, 14.8f, 13.6f, 14.8f, 15.5f, 16.5f)
            moveTo(5f, 12.6f)
            curveTo(6.5f, 11.2f, 8.3f, 10.4f, 10.2f, 10.2f)
            moveTo(2f, 8.8f)
            curveTo(4.3f, 6.7f, 7.1f, 5.4f, 10.1f, 5.1f)
            moveTo(14.5f, 5.3f)
            curveTo(17.4f, 5.8f, 20f, 7f, 22f, 8.8f)
            moveTo(12f, 19.5f); horizontalLineTo(12.05f)
        }
    }

    val AlertCircle: ImageVector by lazy {
        monoline("AlertCircle") {
            moveTo(12f, 3f)
            curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
            curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
            curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
            curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
            close()
            moveTo(12f, 8f); verticalLineTo(13f)
            moveTo(12f, 16.5f); horizontalLineTo(12.05f)
        }
    }

    val Check: ImageVector by lazy {
        monoline("Check") {
            moveTo(5f, 12.5f); lineTo(9.5f, 17f); lineTo(19f, 7.5f)
        }
    }

    val Plus: ImageVector by lazy {
        monoline("Plus") {
            moveTo(12f, 5f); verticalLineTo(19f)
            moveTo(5f, 12f); horizontalLineTo(19f)
        }
    }

    val Trash: ImageVector by lazy {
        monoline("Trash") {
            moveTo(4f, 7f); horizontalLineTo(20f)
            moveTo(9f, 7f); verticalLineTo(4f); horizontalLineTo(15f); verticalLineTo(7f)
            moveTo(6f, 7f); verticalLineTo(19f)
            curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
            horizontalLineTo(16f)
            curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
            verticalLineTo(7f)
        }
    }

    val Pin: ImageVector by lazy {
        monoline("Pin") {
            moveTo(15f, 4f); lineTo(20f, 9f)
            lineTo(14.5f, 14.5f); lineTo(15.5f, 18.5f); lineTo(11f, 14f)
            lineTo(5f, 20f)
            moveTo(11f, 14f); lineTo(6.5f, 9.5f); lineTo(10.5f, 10.5f); lineTo(15f, 4f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        monoline("ChevronRight") {
            moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
        }
    }

    val ArrowBack: ImageVector by lazy {
        monoline("ArrowBack") {
            moveTo(19f, 12f); horizontalLineTo(5f)
            moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
        }
    }

    val ExternalLink: ImageVector by lazy {
        monoline("ExternalLink") {
            moveTo(14f, 4f); horizontalLineTo(20f); verticalLineTo(10f)
            moveTo(10f, 14f); lineTo(20f, 4f)
            moveTo(19f, 14f); verticalLineTo(19f)
            curveTo(19f, 19.55f, 18.55f, 20f, 18f, 20f)
            horizontalLineTo(5f)
            curveTo(4.45f, 20f, 4f, 19.55f, 4f, 19f)
            verticalLineTo(6f)
            curveTo(4f, 5.45f, 4.45f, 5f, 5f, 5f)
            horizontalLineTo(10f)
        }
    }

    val LogOut: ImageVector by lazy {
        monoline("LogOut") {
            moveTo(15f, 16f); lineTo(20f, 12f); lineTo(15f, 8f)
            moveTo(20f, 12f); horizontalLineTo(9f)
            moveTo(11f, 4f); horizontalLineTo(6f)
            curveTo(4.9f, 4f, 4f, 4.9f, 4f, 6f)
            verticalLineTo(18f)
            curveTo(4f, 19.1f, 4.9f, 20f, 6f, 20f)
            horizontalLineTo(11f)
        }
    }

    val Clock: ImageVector by lazy {
        monoline("Clock") {
            moveTo(12f, 3f)
            curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
            curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
            curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
            curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
            close()
            moveTo(12f, 7.5f); verticalLineTo(12f); lineTo(15f, 14f)
        }
    }

    val MapPin: ImageVector by lazy {
        monoline("MapPin") {
            moveTo(12f, 21f)
            curveTo(12f, 21f, 18f, 14.7f, 18f, 9.8f)
            curveTo(18f, 6.6f, 15.3f, 4f, 12f, 4f)
            curveTo(8.7f, 4f, 6f, 6.6f, 6f, 9.8f)
            curveTo(6f, 14.7f, 12f, 21f, 12f, 21f)
            close()
            moveTo(12f, 8f)
            curveTo(10.9f, 8f, 10f, 8.9f, 10f, 10f)
            curveTo(10f, 11.1f, 10.9f, 12f, 12f, 12f)
            curveTo(13.1f, 12f, 14f, 11.1f, 14f, 10f)
            curveTo(14f, 8.9f, 13.1f, 8f, 12f, 8f)
            close()
        }
    }

    val Search: ImageVector by lazy {
        monoline("Search") {
            moveTo(11f, 4f)
            curveTo(7.13f, 4f, 4f, 7.13f, 4f, 11f)
            curveTo(4f, 14.87f, 7.13f, 18f, 11f, 18f)
            curveTo(14.87f, 18f, 18f, 14.87f, 18f, 11f)
            curveTo(18f, 7.13f, 14.87f, 4f, 11f, 4f)
            close()
            moveTo(16f, 16f); lineTo(20.5f, 20.5f)
        }
    }

    val Close: ImageVector by lazy {
        monoline("Close") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Document: ImageVector by lazy {
        monoline("Document") {
            moveTo(6f, 3f)
            horizontalLineTo(14f)
            lineTo(18f, 7f)
            verticalLineTo(20f)
            curveTo(18f, 20.55f, 17.55f, 21f, 17f, 21f)
            horizontalLineTo(7f)
            curveTo(6.45f, 21f, 6f, 20.55f, 6f, 20f)
            close()
            moveTo(14f, 3f); verticalLineTo(7f); horizontalLineTo(18f)
            moveTo(9f, 12f); horizontalLineTo(15f)
            moveTo(9f, 16f); horizontalLineTo(14f)
        }
    }

    val Users: ImageVector by lazy {
        monoline("Users") {
            moveTo(16f, 21f); verticalLineTo(19f)
            curveTo(16f, 17.34f, 14.66f, 16f, 13f, 16f)
            horizontalLineTo(6f)
            curveTo(4.34f, 16f, 3f, 17.34f, 3f, 19f)
            verticalLineTo(21f)
            moveTo(9.5f, 12f)
            curveTo(11.43f, 12f, 13f, 10.43f, 13f, 8.5f)
            curveTo(13f, 6.57f, 11.43f, 5f, 9.5f, 5f)
            curveTo(7.57f, 5f, 6f, 6.57f, 6f, 8.5f)
            curveTo(6f, 10.43f, 7.57f, 12f, 9.5f, 12f)
            close()
            moveTo(21f, 21f); verticalLineTo(19f)
            curveTo(21f, 17.5f, 19.9f, 16.3f, 18.5f, 16f)
            moveTo(15.5f, 5.2f)
            curveTo(16.9f, 5.6f, 17.9f, 6.9f, 17.9f, 8.5f)
            curveTo(17.9f, 10.1f, 16.9f, 11.4f, 15.5f, 11.8f)
        }
    }

    val Video: ImageVector by lazy {
        monoline("Video") {
            moveTo(4f, 7f)
            curveTo(3.45f, 7f, 3f, 7.45f, 3f, 8f)
            verticalLineTo(16f)
            curveTo(3f, 16.55f, 3.45f, 17f, 4f, 17f)
            horizontalLineTo(14f)
            curveTo(14.55f, 17f, 15f, 16.55f, 15f, 16f)
            verticalLineTo(8f)
            curveTo(15f, 7.45f, 14.55f, 7f, 14f, 7f)
            close()
            moveTo(15f, 10.5f); lineTo(21f, 7.5f); verticalLineTo(16.5f); lineTo(15f, 13.5f)
        }
    }

    val Award: ImageVector by lazy {
        monoline("Award") {
            moveTo(12f, 15f)
            curveTo(15.31f, 15f, 18f, 12.31f, 18f, 9f)
            curveTo(18f, 5.69f, 15.31f, 3f, 12f, 3f)
            curveTo(8.69f, 3f, 6f, 5.69f, 6f, 9f)
            curveTo(6f, 12.31f, 8.69f, 15f, 12f, 15f)
            close()
            moveTo(8.5f, 14f); lineTo(7.5f, 21f); lineTo(12f, 18.5f); lineTo(16.5f, 21f); lineTo(15.5f, 14f)
        }
    }
}
