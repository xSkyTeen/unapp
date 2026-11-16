package sky.universidad.UnApp.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.domain.model.Course
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleSectionContainer
import sky.universidad.UnApp.presentation.components.AppleSegmentedControl
import sky.universidad.UnApp.presentation.components.AppleStickyCurvedHeader
import sky.universidad.UnApp.presentation.components.EmptyStateView
import sky.universidad.UnApp.presentation.components.OfflineStatusBanner
import sky.universidad.UnApp.presentation.components.ScheduleSectionSkeleton
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.components.applePressable
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    onSync: () -> Unit,
    onAddManualSlot: (courseName: String, dayOfWeek: Int, startTime: String, endTime: String, classroom: String) -> Unit = { _, _, _, _, _ -> },
    onDeleteSlot: (remoteId: String) -> Unit = {},
    onOpenCourse: (Course) -> Unit = {}
) {
    // 0 = Horario del día, 1 = Horario semanal completo
    var modeIndex by remember { mutableIntStateOf(0) }
    val initialDay = remember {
        when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 1
        }
    }
    var selectedDay by remember { mutableIntStateOf(initialDay) }

    val daySlots = remember(state.schedule, selectedDay) {
        state.schedule.filter { it.dayOfWeek == selectedDay }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleStickyCurvedHeader(
            title = "Horario",
            subtitle = null,
            bottomContent = {
                AppleSegmentedControl(
                    options = listOf("Del día", "Semanal"),
                    selectedIndex = modeIndex,
                    onSelect = { modeIndex = it }
                )
            }
        )

        PullToRefreshBox(
            isRefreshing = state.isSyncing,
            onRefresh = onSync,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!state.isOnline) {
                    item {
                        OfflineStatusBanner(
                            isOnline = state.isOnline,
                            lastSyncAt = state.lastSyncAt
                        )
                    }
                }

                if (state.isSyncing && state.schedule.isEmpty()) {
                    item {
                        ScheduleSectionSkeleton()
                    }
                } else when (modeIndex) {
                    0 -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (1..6).forEach { day ->
                                    val selected = day == selectedDay
                                    val dayShape = RoundedCornerShape(16.dp)
                                    val hasClasses = state.schedule.any { it.dayOfWeek == day }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(dayShape)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surface
                                            )
                                            .border(
                                                0.8.dp,
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant,
                                                dayShape
                                            )
                                            .applePressable { selectedDay = day }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = DateFormatters.dayShort(day),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                            if (hasClasses) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(50))
                                                        .background(
                                                            if (selected) MaterialTheme.colorScheme.onPrimary
                                                            else MaterialTheme.colorScheme.primary
                                                        )
                                                        .padding(horizontal = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (daySlots.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = UnAppIcons.Calendar,
                                    title = "Sin clases el ${DateFormatters.dayName(selectedDay)}"
                                )
                            }
                        } else {
                            item {
                                AppleSectionContainer(
                                    title = DateFormatters.dayName(selectedDay),
                                    subtitle = "${daySlots.size} ${if (daySlots.size == 1) "clase" else "clases"}"
                                ) {
                                    DailyScheduleBlocksColumn(
                                        slots = daySlots,
                                        courses = state.courses,
                                        onOpenCourse = onOpenCourse
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        if (state.schedule.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = UnAppIcons.Calendar,
                                    title = "Sin horario disponible"
                                )
                            }
                        } else {
                            item {
                                WeeklyScheduleTableGrid(
                                    schedule = state.schedule,
                                    courses = state.courses,
                                    onOpenCourse = onOpenCourse
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
