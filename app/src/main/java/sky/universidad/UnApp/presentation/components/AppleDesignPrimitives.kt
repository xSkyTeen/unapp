package sky.universidad.UnApp.presentation.components

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sky.universidad.UnApp.ui.theme.LocalUnAppColors
import kotlin.math.abs

/**
 * Checks Android's system reduced-motion setting (`ANIMATOR_DURATION_SCALE == 0`).
 */
@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        scale <= 0f
    }
}

/**
 * Critically damped default spring (damping 1.0, crisp ~0.35s response, zero overshoot).
 */
fun <T> appleSpringDefault(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
    if (reducedMotion) {
        tween(durationMillis = 160)
    } else {
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 460f)
    }

/**
 * Momentum/flick spring (damping ~0.8, subtle physical settle reserved for gesture commits).
 */
fun <T> appleSpringMomentum(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
    if (reducedMotion) {
        tween(durationMillis = 160)
    } else {
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    }

/**
 * Apple rubber-band boundary resistance function:
 * progressively damps overshoots past bounds instead of hard-stopping.
 */
fun rubberBandOffset(overshootPx: Float, dimensionPx: Float, constant: Float = 0.55f): Float {
    if (dimensionPx <= 0f) return 0f
    return (overshootPx * dimensionPx * constant) / (dimensionPx + constant * abs(overshootPx))
}

/**
 * Exponential decay projection from WWDC Designing Fluid Interfaces.
 */
fun projectMomentum(velocityPxPerSec: Float, decelerationRate: Float = 0.998f): Float {
    return (velocityPxPerSec / 1000f) * decelerationRate / (1f - decelerationRate)
}

/**
 * Provides immediate pointer-down scale feedback (zero latency, interruptible)
 * with synchronized haptic commit and no harsh Material ripple flash.
 */
fun Modifier.applePressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    hapticOnPress: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val reducedMotion = rememberReducedMotionEnabled()
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(enabled, reducedMotion) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                if (!reducedMotion) {
                    scope.launch {
                        scale.animateTo(
                            targetValue = pressedScale,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 900f
                            )
                        )
                    }
                }
                val up = waitForUpOrCancellation()
                scope.launch {
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = appleSpringDefault(reducedMotion)
                    )
                }
                if (up != null && hapticOnPress) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

class ModalBlurController {
    private val sheetFractions = mutableStateMapOf<Int, Float>()
    private var nextId by mutableIntStateOf(0)

    val blurFraction: Float
        get() = (sheetFractions.values.maxOrNull() ?: 0f).coerceIn(0f, 1f)

    internal fun register(): Int = ++nextId

    internal fun updateFraction(id: Int, fraction: Float) {
        sheetFractions[id] = fraction.coerceIn(0f, 1f)
    }

    internal fun release(id: Int) {
        sheetFractions.remove(id)
    }
}

val LocalModalBlurController = compositionLocalOf { ModalBlurController() }

/**
 * Applies progressive live backdrop blur directly driven by modal drag/presentation progress (`0f..1f`),
 * keeping full-bleed geometry (`scale = 1f`, `clip(RectangleShape)`) so no bright border artifact appears around screen edges.
 */
fun Modifier.liveModalBlur(blurFraction: Float): Modifier = composed {
    val reducedMotion = rememberReducedMotionEnabled()
    val clamped = blurFraction.coerceIn(0f, 1f)
    val radius = if (reducedMotion) 0.dp else (clamped * 14f).dp
    this
        .clip(RectangleShape)
        .then(
            if (radius > 0.2.dp) {
                Modifier.blur(radius = radius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
            } else {
                Modifier
            }
        )
}

@Composable
fun AppleCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f),
    accentColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionModifier = if (onClick != null) {
        Modifier.applePressable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(interactionModifier)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = BorderStroke(0.8.dp, borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            if (accentColor != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .background(accentColor)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        content = content
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun AppleSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val haptic = LocalHapticFeedback.current
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(containerColor)
            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f), RoundedCornerShape(22.dp))
            .padding(4.dp)
    ) {
        val segmentWidth = if (options.isEmpty()) 0.dp else maxWidth / options.size
        val indicatorOffset: Dp by animateDpAsState(
            targetValue = segmentWidth * selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)),
            animationSpec = appleSpringDefault(reducedMotion),
            label = "segmentIndicator"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
        )

        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = appleSpringDefault(reducedMotion),
                    label = "segmentText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (index != selectedIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ApplePillBadge(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier: Modifier = Modifier
) {
    val badgeShape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(badgeShape)
            .background(containerColor)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extended = LocalUnAppColors.current
    val blurController = LocalModalBlurController.current
    val density = LocalDensity.current
    val navInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val dragHandlePx = with(density) { 24.dp.toPx() }
    val sheetId = remember(blurController) { blurController.register() }
    var contentHeightPx by remember { mutableFloatStateOf(0f) }

    DisposableEffect(blurController, sheetId) {
        onDispose {
            blurController.release(sheetId)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = Color(0xFF080E1A).copy(alpha = 0.28f),
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(extended.glassBorder.copy(alpha = 0.7f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    if (size.height > 0) {
                        contentHeightPx = size.height.toFloat()
                    }
                }
                .onGloballyPositioned { coords ->
                    val rootHeight = coords.findRootCoordinates().size.height.toFloat()
                    val expectedTotal = (contentHeightPx + dragHandlePx + navInsetPx).coerceAtLeast(1f)
                    if (rootHeight > 0f && contentHeightPx > 0f) {
                        val currentTopY = coords.positionInRoot().y
                        val visiblePx = (rootHeight - currentTopY + dragHandlePx).coerceAtLeast(0f)
                        val fraction = (visiblePx / expectedTotal).coerceIn(0f, 1f)
                        blurController.updateFraction(sheetId, fraction)
                    }
                }
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            content = content
        )
    }
}

/**
 * Sticky top header with soft frosted translucency and pronounced bottom corners.
 */
@Composable
fun AppleStickyCurvedHeader(
    title: String,
    subtitle: String? = null,
    subtitleVisible: Boolean = true,
    @DrawableRes logoDrawableRes: Int? = null,
    onBack: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val extended = LocalUnAppColors.current
    val reducedMotion = rememberReducedMotionEnabled()
    val headerShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    val gradientColors = if (extended.isDark) {
        listOf(
            Color(0xFF0F1829),
            Color(0xFF16233A).copy(alpha = 0.96f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    } else {
        listOf(
            Color(0xFFDCE6F4),
            Color(0xFFE5EEF9).copy(alpha = 0.96f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    }
    val edgeVeilColor = MaterialTheme.colorScheme.background.copy(alpha = 0.35f)

    Column(modifier = modifier.fillMaxWidth()) {
        // Seamless full-bleed header behind status bar without top/side border seams
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = headerShape,
            color = Color.Transparent,
            border = null,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .background(Brush.verticalGradient(colors = gradientColors))
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (logoDrawableRes != null) {
                    Box(
                        modifier = Modifier
                            .background(Color.Transparent)
                            .padding(vertical = 2.dp)
                    ) {
                        Image(
                            painter = painterResource(id = logoDrawableRes),
                            contentDescription = "Universidad Nacional del Altiplano",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onBack != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                .applePressable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = UnAppIcons.ArrowBack,
                                contentDescription = "Volver",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        AnimatedVisibility(
                            visible = !subtitle.isNullOrBlank() && subtitleVisible,
                            enter = expandVertically(appleSpringDefault(reducedMotion)) +
                                fadeIn(appleSpringDefault(reducedMotion)),
                            exit = shrinkVertically(appleSpringDefault(reducedMotion)) +
                                fadeOut(appleSpringDefault(reducedMotion))
                        ) {
                            Text(
                                text = subtitle.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (trailingContent != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingContent()
                }
            }
            if (bottomContent != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = bottomContent
                )
            }
        }
        }
        // Subtle frosted transition veil below header curve so scrolling content fades cleanly
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(edgeVeilColor, Color.Transparent)
                    )
                )
        )
    }
}

/**
 * Expandable/collapsible grouped list section for tasks, forums, units, and course grades.
 */
@Composable
fun AppleCollapsibleSection(
    title: String,
    subtitle: String? = null,
    countBadge: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    initiallyExpanded: Boolean = true,
    modifier: Modifier = Modifier,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val reducedMotion = rememberReducedMotionEnabled()
    var expanded by remember(title, initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = appleSpringDefault(reducedMotion),
        label = "collapsibleChevron"
    )
    val sectionShape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(sectionShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
                    )
                )
            )
            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), sectionShape)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .applePressable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = UnAppIcons.ChevronRight,
                        contentDescription = if (expanded) "Contraer" else "Desplegar",
                        tint = accentColor,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!countBadge.isNullOrBlank()) {
                    ApplePillBadge(
                        text = countBadge,
                        containerColor = accentColor.copy(alpha = 0.15f),
                        contentColor = accentColor
                    )
                }
                if (headerTrailing != null) {
                    headerTrailing()
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(appleSpringDefault(reducedMotion)) + fadeIn(appleSpringDefault(reducedMotion)),
            exit = shrinkVertically(appleSpringDefault(reducedMotion)) + fadeOut(appleSpringDefault(reducedMotion))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

/**
 * Encabezado sutil para segmentar áreas funcionales dentro de una pantalla estilo iOS.
 */
@Composable
fun AppleSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .applePressable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Divisor sutil interno para listas agrupadas estilo iOS.
 */
@Composable
fun AppleSubtleDivider(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.8.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * Contenedor agrupado con radios pronunciados y segmentación visual sutil entre módulos.
 */
@Composable
fun AppleSectionContainer(
    title: String? = null,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
    content: @Composable ColumnScope.() -> Unit
) {
    val sectionShape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(sectionShape)
            .background(containerColor)
            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), sectionShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!title.isNullOrBlank()) {
            AppleSectionHeader(
                title = title,
                subtitle = subtitle,
                actionLabel = actionLabel,
                onAction = onAction
            )
        }
        content()
    }
}
