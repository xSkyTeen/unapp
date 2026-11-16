package sky.universidad.UnApp.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.ui.theme.LocalUnAppColors

@Composable
fun OfflineStatusBanner(
    isOnline: Boolean,
    lastSyncAt: Long,
    modifier: Modifier = Modifier
) {
    val extended = LocalUnAppColors.current
    val reducedMotion = rememberReducedMotionEnabled()

    AnimatedVisibility(
        visible = !isOnline,
        enter = fadeIn(appleSpringDefault(reducedMotion)) + expandVertically(appleSpringDefault(reducedMotion)),
        exit = fadeOut(appleSpringDefault(reducedMotion)) + shrinkVertically(appleSpringDefault(reducedMotion)),
        modifier = modifier
    ) {
        val container = extended.warningContainer
        val ink = extended.onWarningContainer
        val syncedText = DateFormatters.formatSyncTime(lastSyncAt)
        val message = "Sin conexión · Caché local ($syncedText)."

        val bannerShape = RoundedCornerShape(18.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(bannerShape)
                .background(container)
                .border(0.8.dp, ink.copy(alpha = 0.25f), bannerShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = UnAppIcons.WifiOff,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = ink
            )
        }
    }
}

@Composable
fun ErrorRetryBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .border(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = UnAppIcons.AlertCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        TextButton(onClick = onRetry) {
            Text(
                text = "Reintentar",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun LoadingStateCard(
    label: String = "Sincronizando información académica...",
    modifier: Modifier = Modifier
) {
    AppleCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun rememberSkeletonBrush(): Brush {
    val base = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
    val highlight = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f)
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonShift"
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(shift - 320f, 0f),
        end = Offset(shift, 180f)
    )
}

@Composable
fun SkeletonBox(
    widthFraction: Float = 1f,
    height: Dp = 14.dp,
    cornerRadius: Dp = 8.dp,
    brush: Brush = rememberSkeletonBrush(),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

@Composable
fun TasksSectionSkeleton(count: Int = 3) {
    val brush = rememberSkeletonBrush()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonBox(widthFraction = 0.42f, height = 18.dp, cornerRadius = 50.dp, brush = brush)
                    SkeletonBox(widthFraction = 0.22f, height = 18.dp, cornerRadius = 50.dp, brush = brush)
                }
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(widthFraction = 0.85f, height = 16.dp, cornerRadius = 8.dp, brush = brush)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(widthFraction = 0.65f, height = 12.dp, cornerRadius = 6.dp, brush = brush)
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(widthFraction = 0.48f, height = 11.dp, cornerRadius = 6.dp, brush = brush)
            }
        }
    }
}

@Composable
fun ScheduleSectionSkeleton() {
    val brush = rememberSkeletonBrush()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(6) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(brush)
                )
            }
        }
        repeat(3) {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonBox(widthFraction = 0.34f, height = 18.dp, cornerRadius = 50.dp, brush = brush)
                    SkeletonBox(widthFraction = 0.24f, height = 18.dp, cornerRadius = 50.dp, brush = brush)
                }
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(widthFraction = 0.78f, height = 16.dp, cornerRadius = 8.dp, brush = brush)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(widthFraction = 0.52f, height = 12.dp, cornerRadius = 6.dp, brush = brush)
            }
        }
    }
}

@Composable
fun CoursesListSkeleton(count: Int = 4) {
    val brush = rememberSkeletonBrush()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SkeletonBox(widthFraction = 0.30f, height = 20.dp, cornerRadius = 50.dp, brush = brush)
                    SkeletonBox(widthFraction = 0.24f, height = 20.dp, cornerRadius = 50.dp, brush = brush)
                }
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(widthFraction = 0.82f, height = 17.dp, cornerRadius = 8.dp, brush = brush)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(widthFraction = 0.56f, height = 13.dp, cornerRadius = 6.dp, brush = brush)
            }
        }
    }
}

@Composable
fun ForumsListSkeleton(count: Int = 3) {
    val brush = rememberSkeletonBrush()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SkeletonBox(widthFraction = 0.45f, height = 18.dp, cornerRadius = 50.dp, brush = brush)
                    SkeletonBox(widthFraction = 0.20f, height = 18.dp, cornerRadius = 50.dp, brush = brush)
                }
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(widthFraction = 0.75f, height = 16.dp, cornerRadius = 8.dp, brush = brush)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(widthFraction = 0.90f, height = 12.dp, cornerRadius = 6.dp, brush = brush)
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(widthFraction = 0.44f, height = 11.dp, cornerRadius = 6.dp, brush = brush)
            }
        }
    }
}

@Composable
fun CourseDetailSkeleton() {
    val brush = rememberSkeletonBrush()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppleCard(modifier = Modifier.fillMaxWidth()) {
            SkeletonBox(widthFraction = 0.38f, height = 14.dp, cornerRadius = 6.dp, brush = brush)
            Spacer(modifier = Modifier.height(10.dp))
            SkeletonBox(widthFraction = 0.80f, height = 17.dp, cornerRadius = 8.dp, brush = brush)
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBox(widthFraction = 0.55f, height = 13.dp, cornerRadius = 6.dp, brush = brush)
            Spacer(modifier = Modifier.height(12.dp))
            SkeletonBox(widthFraction = 1f, height = 40.dp, cornerRadius = 14.dp, brush = brush)
        }
        repeat(2) {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                SkeletonBox(widthFraction = 0.48f, height = 16.dp, cornerRadius = 8.dp, brush = brush)
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(widthFraction = 0.92f, height = 13.dp, cornerRadius = 6.dp, brush = brush)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(widthFraction = 0.70f, height = 13.dp, cornerRadius = 6.dp, brush = brush)
            }
        }
    }
}

@Composable
fun IntranetModuleSkeleton() {
    val brush = rememberSkeletonBrush()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SkeletonBox(widthFraction = 0.35f, height = 18.dp, cornerRadius = 50.dp, brush = brush)
                    SkeletonBox(widthFraction = 0.22f, height = 18.dp, cornerRadius = 50.dp, brush = brush)
                }
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(widthFraction = 0.76f, height = 16.dp, cornerRadius = 8.dp, brush = brush)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(widthFraction = 0.50f, height = 12.dp, cornerRadius = 6.dp, brush = brush)
            }
        }
    }
}

@Composable
fun StudentProfileSkeleton() {
    val brush = rememberSkeletonBrush()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBox(widthFraction = 1f, height = 176.dp, cornerRadius = 24.dp, brush = brush)
        AppleCard(modifier = Modifier.fillMaxWidth()) {
            repeat(5) { idx ->
                SkeletonBox(widthFraction = 0.32f, height = 11.dp, cornerRadius = 5.dp, brush = brush)
                Spacer(modifier = Modifier.height(5.dp))
                SkeletonBox(widthFraction = 0.74f, height = 15.dp, cornerRadius = 7.dp, brush = brush)
                if (idx < 4) Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
