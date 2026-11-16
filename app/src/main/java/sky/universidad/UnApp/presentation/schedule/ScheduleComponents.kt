package sky.universidad.UnApp.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.core.common.CourseColorPalette
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.core.common.ScheduleTimeBlock
import sky.universidad.UnApp.domain.model.Course
import sky.universidad.UnApp.domain.model.ScheduleSlot
import sky.universidad.UnApp.presentation.components.AppleBottomSheet
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleSectionContainer
import sky.universidad.UnApp.presentation.components.AppleSubtleDivider
import sky.universidad.UnApp.presentation.components.applePressable
import sky.universidad.UnApp.ui.theme.LocalUnAppColors

@Composable
fun ScheduleTimeBlockHeader(
    block: ScheduleTimeBlock,
    slotCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = block.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = block.rangeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (slotCount == 1) "1 clase" else "$slotCount clases",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ScheduleSlotRowCard(
    slot: ScheduleSlot,
    courses: List<Course>,
    onOpenCourse: ((Course) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val matchedCourse = remember(slot.courseId, slot.courseName, courses) {
        CourseColorPalette.resolveCourse(slot.courseId, slot.courseName, courses)
    }
    val courseColor = remember(slot.courseId, slot.courseName, courses) {
        CourseColorPalette.resolveCourseColor(slot.courseId, slot.courseName, courses)
    }
    val courseCode = matchedCourse?.code
    val startAmPm = remember(slot.startTime) { DateFormatters.formatTimeAmPm(slot.startTime) }
    val endAmPm = remember(slot.endTime) { DateFormatters.formatTimeAmPm(slot.endTime) }

    AppleCard(
        modifier = modifier.fillMaxWidth(),
        accentColor = courseColor,
        onClick = if (matchedCourse != null && onOpenCourse != null) {
            { onOpenCourse(matchedCourse) }
        } else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.width(74.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = startAmPm,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (endAmPm.isNotBlank()) {
                    Text(
                        text = endAmPm,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(courseColor)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!courseCode.isNullOrBlank()) {
                        ApplePillBadge(
                            text = courseCode,
                            containerColor = courseColor.copy(alpha = 0.16f),
                            contentColor = courseColor
                        )
                    }
                    if (slot.slotType.isNotBlank()) {
                        ApplePillBadge(
                            text = slot.slotType,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = slot.courseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    slot.classroom.takeIf { it.isNotBlank() },
                    slot.teacher.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DailyScheduleBlocksColumn(
    slots: List<ScheduleSlot>,
    courses: List<Course>,
    onOpenCourse: ((Course) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val grouped = remember(slots) {
        slots.groupBy { DateFormatters.resolveTimeBlock(it.startTime) }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var isFirstBlock = true
        ScheduleTimeBlock.entries.forEach { block ->
            val blockSlots = grouped[block].orEmpty()
            if (blockSlots.isNotEmpty()) {
                if (!isFirstBlock) {
                    AppleSubtleDivider()
                }
                isFirstBlock = false
                ScheduleTimeBlockHeader(block = block, slotCount = blockSlots.size)
                blockSlots.forEach { slot ->
                    ScheduleSlotRowCard(
                        slot = slot,
                        courses = courses,
                        onOpenCourse = onOpenCourse
                    )
                }
            }
        }
    }
}

private data class PlacedScheduleSlot(
    val slot: ScheduleSlot,
    val startMin: Int,
    val endMin: Int,
    val laneIndex: Int,
    val totalLanes: Int,
    val clusterSlots: List<ScheduleSlot>
)

private fun layoutDaySlotsContinuously(daySlots: List<ScheduleSlot>): List<PlacedScheduleSlot> {
    if (daySlots.isEmpty()) return emptyList()
    data class Timed(val slot: ScheduleSlot, val start: Int, val end: Int)

    val sorted = daySlots.mapNotNull { s ->
        val start = DateFormatters.parseMinutesFromMidnight(s.startTime) ?: return@mapNotNull null
        val parsedEnd = DateFormatters.parseMinutesFromMidnight(s.endTime)
        val end = if (parsedEnd != null && parsedEnd > start) parsedEnd else start + 60
        Timed(s, start, end)
    }.sortedWith(compareBy<Timed>({ it.start }, { it.end }))

    if (sorted.isEmpty()) return emptyList()

    // Group into connected overlap clusters so non-overlapping items use 100% column width
    // while overlapping items share horizontal lanes within their continuous span.
    val clusters = mutableListOf<List<Timed>>()
    var currentCluster = mutableListOf<Timed>()
    var clusterMaxEnd = -1
    for (item in sorted) {
        if (currentCluster.isEmpty() || item.start < clusterMaxEnd) {
            currentCluster.add(item)
            clusterMaxEnd = maxOf(clusterMaxEnd, item.end)
        } else {
            clusters.add(currentCluster)
            currentCluster = mutableListOf(item)
            clusterMaxEnd = item.end
        }
    }
    if (currentCluster.isNotEmpty()) clusters.add(currentCluster)

    val result = mutableListOf<PlacedScheduleSlot>()
    for (cluster in clusters) {
        val laneEnds = mutableListOf<Int>()
        val assignedLanes = mutableListOf<Pair<Timed, Int>>()
        for (item in cluster) {
            val reusableLane = laneEnds.indexOfFirst { it <= item.start }
            val lane = if (reusableLane >= 0) {
                laneEnds[reusableLane] = item.end
                reusableLane
            } else {
                laneEnds.add(item.end)
                laneEnds.lastIndex
            }
            assignedLanes.add(item to lane)
        }
        val totalLanes = laneEnds.size.coerceAtLeast(1)
        val rawSlots = cluster.map { it.slot }
        for ((timed, lane) in assignedLanes) {
            result.add(
                PlacedScheduleSlot(
                    slot = timed.slot,
                    startMin = timed.start,
                    endMin = timed.end,
                    laneIndex = lane,
                    totalLanes = totalLanes,
                    clusterSlots = rawSlots
                )
            )
        }
    }
    return result
}

/**
 * Tabla real de horario semanal en AM/PM donde cada curso abarca de forma continua
 * desde su hora de inicio hasta su hora de fin sin dividirse por hora.
 * En caso de cruce, los cursos concurrentes comparten el ancho de la columna.
 */
@Composable
fun WeeklyScheduleTableGrid(
    schedule: List<ScheduleSlot>,
    courses: List<Course>,
    onOpenCourse: ((Course) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val extended = LocalUnAppColors.current
    var conflictDetailRequest by remember { mutableStateOf<Pair<String, List<ScheduleSlot>>?>(null) }

    val daysToShow = remember(schedule) {
        val hasSaturday = schedule.any { it.dayOfWeek == 6 }
        if (hasSaturday) (1..6).toList() else (1..5).toList()
    }

    val hourRows = remember(schedule) {
        val starts = schedule.mapNotNull { DateFormatters.parseHour24(it.startTime) }
        val ends = schedule.mapNotNull { slot ->
            val endMin = DateFormatters.parseMinutesFromMidnight(slot.endTime) ?: return@mapNotNull null
            val h = endMin / 60
            val m = endMin % 60
            if (m > 0) h else (h - 1).coerceAtLeast(0)
        }
        val minHour = (starts.minOrNull() ?: 7).coerceAtMost(8)
        val maxHour = (ends.maxOrNull() ?: 18).coerceAtLeast(minHour + 4)
        (minHour..maxHour).toList()
    }

    val hScroll = rememberScrollState()
    val dayColWidth = 114.dp
    val timeColWidth = 66.dp
    val hourRowHeight = 64.dp
    val gridStartMinutes = (hourRows.firstOrNull() ?: 7) * 60
    val gridTotalHeight = hourRowHeight * hourRows.size

    AppleSectionContainer(
        title = "Horario semanal",
        subtitle = null,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
        ) {
            // Fixed sticky Hour axis column in AM/PM
            Column(modifier = Modifier.width(timeColWidth)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .border(0.4.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Hora",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                hourRows.forEach { hour ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hourRowHeight)
                            .border(0.4.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = DateFormatters.formatTimeAmPm("%02d:00".format(hour)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // Scrollable Day columns
            Row(modifier = Modifier.horizontalScroll(hScroll)) {
                daysToShow.forEach { day ->
                    val placedSlots = remember(schedule, day) {
                        layoutDaySlotsContinuously(schedule.filter { it.dayOfWeek == day })
                    }
                    Column(modifier = Modifier.width(dayColWidth)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .border(0.4.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = DateFormatters.dayName(day),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(gridTotalHeight)
                        ) {
                            // Subtle background hour lines (strictly behind continuous blocks)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                hourRows.forEach { _ ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(hourRowHeight)
                                            .border(0.35.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
                                    )
                                }
                            }

                            // Continuous vertical blocks spanning startTime -> endTime
                            placedSlots.forEach { placed ->
                                val s = placed.slot
                                val matched = CourseColorPalette.resolveCourse(s.courseId, s.courseName, courses)
                                val color = CourseColorPalette.resolveCourseColor(s.courseId, s.courseName, courses)
                                val isConflict = placed.totalLanes > 1

                                val topDp = hourRowHeight * ((placed.startMin - gridStartMinutes).coerceAtLeast(0) / 60f)
                                val spanHeightDp = (hourRowHeight * ((placed.endMin - placed.startMin).coerceAtLeast(35) / 60f)) - 4.dp
                                val laneWidth = dayColWidth / placed.totalLanes
                                val xOffsetDp = (laneWidth * placed.laneIndex) + 2.dp
                                val cardWidthDp = (laneWidth - 4.dp).coerceAtLeast(42.dp)

                                Box(
                                    modifier = Modifier
                                        .offset(x = xOffsetDp, y = topDp + 2.dp)
                                        .width(cardWidthDp)
                                        .height(spanHeightDp.coerceAtLeast(40.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(color.copy(alpha = if (isConflict) 0.22f else 0.16f))
                                        .border(
                                            width = if (isConflict) 1.dp else 0.85.dp,
                                            color = if (isConflict) extended.warning.copy(alpha = 0.78f) else color.copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .applePressable {
                                            if (isConflict) {
                                                val rangeLabel = DateFormatters.formatTimeRangeAmPm(s.startTime, s.endTime)
                                                conflictDetailRequest = "${DateFormatters.dayName(day)} · $rangeLabel" to placed.clusterSlots
                                            } else if (matched != null && onOpenCourse != null) {
                                                onOpenCourse(matched)
                                            } else {
                                                val rangeLabel = DateFormatters.formatTimeRangeAmPm(s.startTime, s.endTime)
                                                conflictDetailRequest = "${DateFormatters.dayName(day)} · $rangeLabel" to placed.clusterSlots
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 5.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = matched?.code ?: s.courseName.take(8),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = color,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = DateFormatters.formatTimeRangeAmPm(s.startTime, s.endTime),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = s.courseName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = if (spanHeightDp >= 95.dp) 3 else 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (s.classroom.isNotBlank() && spanHeightDp >= 78.dp) {
                                            Text(
                                                text = s.classroom,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    conflictDetailRequest?.let { (slotLabel, overlappingSlots) ->
        AppleBottomSheet(onDismiss = { conflictDetailRequest = null }) {
            ApplePillBadge(
                text = if (overlappingSlots.size > 1) "Cruce de horario (${overlappingSlots.size} cursos)" else "Detalle de clase",
                containerColor = if (overlappingSlots.size > 1) {
                    extended.warningContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (overlappingSlots.size > 1) {
                    extended.onWarningContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = slotLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                overlappingSlots.forEach { slot ->
                    ScheduleSlotRowCard(
                        slot = slot,
                        courses = courses,
                        onOpenCourse = { course ->
                            conflictDetailRequest = null
                            onOpenCourse?.invoke(course)
                        }
                    )
                }
            }
        }
    }
}
