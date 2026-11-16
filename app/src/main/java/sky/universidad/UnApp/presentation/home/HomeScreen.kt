package sky.universidad.UnApp.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.R
import sky.universidad.UnApp.core.common.CourseColorPalette
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.domain.model.AcademicTask
import sky.universidad.UnApp.domain.model.Announcement
import sky.universidad.UnApp.domain.model.Course
import sky.universidad.UnApp.domain.model.ForumReplyItem
import sky.universidad.UnApp.domain.model.ForumTopic
import sky.universidad.UnApp.domain.model.HomeworkDetailInfo
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleBottomSheet
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.AppleCollapsibleSection
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleSectionContainer
import sky.universidad.UnApp.presentation.components.AppleSegmentedControl
import sky.universidad.UnApp.presentation.components.AppleStickyCurvedHeader
import sky.universidad.UnApp.presentation.components.AppleSubtleDivider
import sky.universidad.UnApp.presentation.components.EmptyStateView
import sky.universidad.UnApp.presentation.components.ErrorRetryBanner
import sky.universidad.UnApp.presentation.components.ForumDiscussionSheet
import sky.universidad.UnApp.presentation.components.OfflineStatusBanner
import sky.universidad.UnApp.presentation.components.ReadOnlyTaskDetailSheet
import sky.universidad.UnApp.presentation.components.ScheduleSectionSkeleton
import sky.universidad.UnApp.presentation.components.TasksSectionSkeleton
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.components.applePressable
import sky.universidad.UnApp.presentation.schedule.DailyScheduleBlocksColumn
import sky.universidad.UnApp.presentation.schedule.WeeklyScheduleTableGrid
import sky.universidad.UnApp.presentation.tasks.ReadOnlyForumCard
import sky.universidad.UnApp.presentation.tasks.ReadOnlyTaskCard
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    onSync: () -> Unit,
    onDismissError: () -> Unit,
    onToggleTask: (String, Boolean) -> Unit = { _, _ -> },
    onOpenTasksTab: () -> Unit,
    onOpenCoursesTab: () -> Unit,
    onOpenScheduleTab: () -> Unit,
    onCreateNoteForCourse: (courseId: String, courseName: String) -> Unit,
    onOpenCourse: (Course) -> Unit = {},
    onLoadHomeworkDetail: (AcademicTask, (HomeworkDetailInfo?) -> Unit) -> Unit = { _, cb -> cb(null) },
    onLoadForumDiscussion: (ForumTopic, (String, List<ForumReplyItem>) -> Unit) -> Unit = { f, cb ->
        cb(f.description, emptyList())
    },
    onOpenUrl: (String) -> Unit = {}
) {
    var selectedTask by remember { mutableStateOf<AcademicTask?>(null) }
    var selectedForum by remember { mutableStateOf<ForumTopic?>(null) }
    var selectedAnnouncement by remember { mutableStateOf<Announcement?>(null) }
    var scheduleModeIndex by remember { mutableIntStateOf(0) } // 0: Del día, 1: Tabla semanal

    val todayIsoDay = remember {
        when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 1
        }
    }
    var selectedDay by remember { mutableIntStateOf(todayIsoDay) }

    val selectedDaySlots = remember(state.schedule, selectedDay) {
        state.schedule.filter { it.dayOfWeek == selectedDay }
    }

    val activeOrTodayTasks = remember(state.tasks) {
        state.tasks
            .filter { DateFormatters.isTaskVisibleInGlobalTab(it.dueTimestamp, it.isEnded) }
            .sortedWith(
                compareBy<AcademicTask>(
                    { it.isSubmittedRemote },
                    { it.dueTimestamp ?: Long.MAX_VALUE }
                )
            )
    }

    val openForums = remember(state.forums) {
        state.forums
            .filter { DateFormatters.isForumActiveNow(it.closeTimestamp, it.startDateRaw, it.isEnded) }
            .sortedBy { DateFormatters.forumPriorityScore(it.closeTimestamp, it.startDateRaw, it.isEnded) }
    }

    val nextClassToday = remember(state.schedule, todayIsoDay) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val todayClasses = state.schedule.filter { it.dayOfWeek == todayIsoDay }
        todayClasses.firstOrNull { (DateFormatters.parseHour24(it.startTime) ?: 0) >= currentHour }
            ?: todayClasses.firstOrNull()
    }

    // Privacy: display only given name on Home (never student code or DNI on the main screen)
    val givenName = remember(state.student?.fullName) {
        state.student?.fullName?.takeIf { it.isNotBlank() && it.any { ch -> ch.isLetter() } }?.let {
            DateFormatters.extractGivenName(it)
        } ?: "Estudiante"
    }

    val careerLine = remember(state.student?.career, state.student?.semester) {
        listOfNotNull(
            state.student?.career?.takeIf { it.isNotBlank() }?.let { DateFormatters.formatTitleCase(it) },
            state.student?.semester?.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { "Universidad Nacional del Altiplano" }
    }

    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 28
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleStickyCurvedHeader(
            title = "Hola, $givenName",
            subtitle = careerLine,
            subtitleVisible = !isScrolled,
            trailingContent = {
                Image(
                    painter = painterResource(id = R.drawable.logo_psg),
                    contentDescription = "Universidad Nacional del Altiplano",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(34.dp)
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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Segment 1: Offline/Error banner + Startup upcoming event alerts card
                if (!state.isOnline || state.syncError != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OfflineStatusBanner(
                                isOnline = state.isOnline,
                                lastSyncAt = state.lastSyncAt
                            )
                            state.syncError?.let { err ->
                                ErrorRetryBanner(
                                    message = err,
                                    onRetry = {
                                        onDismissError()
                                        onSync()
                                    }
                                )
                            }
                        }
                    }
                }

                val pendingCountNow = activeOrTodayTasks.count { !it.isSubmittedRemote }
                if (pendingCountNow > 0 || openForums.isNotEmpty()) {
                    item {
                        UpcomingEventsAlertStrip(
                            pendingTasksCount = pendingCountNow,
                            openForumsCount = openForums.size,
                            nextTask = activeOrTodayTasks.firstOrNull { !it.isSubmittedRemote },
                            onOpenTasks = onOpenTasksTab
                        )
                    }
                }

                // Segment 2: High-Priority Summary Cards (Tasks + Next Class / Open Forums)
                item {
                    val nextPendingTask = activeOrTodayTasks.firstOrNull { !it.isSubmittedRemote }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Tareas activas y hoy",
                            value = pendingCountNow.toString(),
                            subtitle = nextPendingTask?.let {
                                DateFormatters.formatRelativeAcademicDate(it.dueDateRaw, it.dueTimestamp)
                            } ?: if (pendingCountNow == 0) "Todo al día" else "Ver entregas",
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            inkColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            onClick = onOpenTasksTab,
                            modifier = Modifier.weight(1f)
                        )

                        val rightTitle = if (nextClassToday != null) "Próxima clase hoy" else "Foros abiertos"
                        val rightValue = if (nextClassToday != null) {
                            DateFormatters.formatTimeAmPm(nextClassToday.startTime)
                        } else {
                            openForums.size.toString()
                        }
                        val rightSubtitle = if (nextClassToday != null) {
                            listOfNotNull(
                                CourseColorPalette.resolveCourseCode(
                                    nextClassToday.courseId,
                                    nextClassToday.courseName,
                                    state.courses
                                ) ?: nextClassToday.courseName.take(14),
                                nextClassToday.classroom.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                        } else {
                            if (openForums.isEmpty()) "Sin foros activos" else "Participación abierta"
                        }

                        MetricCard(
                            title = rightTitle,
                            value = rightValue,
                            subtitle = rightSubtitle,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                            inkColor = MaterialTheme.colorScheme.onSurface,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                            onClick = {
                                val matchedCourse = nextClassToday?.let {
                                    CourseColorPalette.resolveCourse(it.courseId, it.courseName, state.courses)
                                }
                                if (matchedCourse != null) onOpenCourse(matchedCourse) else onOpenScheduleTab()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Segment 3: TASKS FIRST (Collapsible list)
                item {
                    AppleCollapsibleSection(
                        title = "Tareas",
                        subtitle = null,
                        countBadge = "${activeOrTodayTasks.size}",
                        initiallyExpanded = true,
                        headerTrailing = {
                            Text(
                                text = "Ver todas",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .applePressable(onClick = onOpenTasksTab)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    ) {
                        if (state.isSyncing && state.tasks.isEmpty()) {
                            TasksSectionSkeleton(count = 2)
                        } else if (activeOrTodayTasks.isEmpty()) {
                            EmptyStateView(
                                icon = UnAppIcons.CheckSquare,
                                title = "Sin tareas pendientes"
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                activeOrTodayTasks.take(5).forEach { task ->
                                    val courseColor = CourseColorPalette.resolveCourseColor(
                                        courseId = task.courseId,
                                        courseName = task.courseName,
                                        courses = state.courses
                                    )
                                    val courseCode = CourseColorPalette.resolveCourseCode(
                                        courseId = task.courseId,
                                        courseName = task.courseName,
                                        courses = state.courses
                                    )
                                    ReadOnlyTaskCard(
                                        task = task,
                                        courseColor = courseColor,
                                        courseCode = courseCode,
                                        onCreateNote = null,
                                        onClick = { selectedTask = task }
                                    )
                                }
                            }
                        }
                    }
                }

                // Segment 4: Schedule Block
                item {
                    AppleSectionContainer(
                        title = "Horario",
                        subtitle = if (scheduleModeIndex == 0) DateFormatters.dayName(selectedDay) else null,
                        actionLabel = "Ampliar",
                        onAction = onOpenScheduleTab
                    ) {
                        AppleSegmentedControl(
                            options = listOf("Del día", "Semanal"),
                            selectedIndex = scheduleModeIndex,
                            onSelect = { scheduleModeIndex = it }
                        )

                        if (state.isSyncing && state.schedule.isEmpty()) {
                            ScheduleSectionSkeleton()
                        } else if (scheduleModeIndex == 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (1..6).forEach { day ->
                                    val selected = day == selectedDay
                                    val chipShape = RoundedCornerShape(14.dp)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(chipShape)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                            )
                                            .border(
                                                0.8.dp,
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                                chipShape
                                            )
                                            .applePressable { selectedDay = day }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = DateFormatters.dayShort(day),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }

                            if (selectedDaySlots.isEmpty()) {
                                EmptyStateView(
                                    icon = UnAppIcons.Calendar,
                                    title = "Sin clases el ${DateFormatters.dayName(selectedDay)}"
                                )
                            } else {
                                DailyScheduleBlocksColumn(
                                    slots = selectedDaySlots,
                                    courses = state.courses,
                                    onOpenCourse = onOpenCourse
                                )
                            }
                        } else {
                            if (state.schedule.isEmpty()) {
                                EmptyStateView(
                                    icon = UnAppIcons.Calendar,
                                    title = "Sin horario disponible"
                                )
                            } else {
                                WeeklyScheduleTableGrid(
                                    schedule = state.schedule,
                                    courses = state.courses,
                                    onOpenCourse = onOpenCourse
                                )
                            }
                        }
                    }
                }

                // Segment 5: Active Forums (Collapsible)
                if (openForums.isNotEmpty()) {
                    item {
                        AppleCollapsibleSection(
                            title = "Foros abiertos",
                            subtitle = null,
                            countBadge = "${openForums.size}",
                            initiallyExpanded = false
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                openForums.take(4).forEach { forum ->
                                    val courseColor = CourseColorPalette.resolveCourseColor(
                                        courseId = forum.courseId,
                                        courseName = forum.courseName,
                                        courses = state.courses
                                    )
                                    ReadOnlyForumCard(
                                        forum = forum,
                                        courseColor = courseColor,
                                        onClick = { selectedForum = forum }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedTask?.let { task ->
        val courseColor = CourseColorPalette.resolveCourseColor(
            courseId = task.courseId,
            courseName = task.courseName,
            courses = state.courses
        )
        val courseCode = CourseColorPalette.resolveCourseCode(
            courseId = task.courseId,
            courseName = task.courseName,
            courses = state.courses
        )
        ReadOnlyTaskDetailSheet(
            task = task,
            courseColor = courseColor,
            courseCode = courseCode,
            onLoadDetail = onLoadHomeworkDetail,
            onCreateNote = {
                selectedTask = null
                onCreateNoteForCourse(task.courseId, task.courseName)
            },
            onOpenUrl = onOpenUrl,
            onDismiss = { selectedTask = null }
        )
    }

    selectedForum?.let { forum ->
        val courseColor = CourseColorPalette.resolveCourseColor(
            courseId = forum.courseId,
            courseName = forum.courseName,
            courses = state.courses
        )
        ForumDiscussionSheet(
            forum = forum,
            courseColor = courseColor,
            onLoadDiscussion = onLoadForumDiscussion,
            onDismiss = { selectedForum = null }
        )
    }

    selectedAnnouncement?.let { announcement ->
        AppleBottomSheet(onDismiss = { selectedAnnouncement = null }) {
            ApplePillBadge(text = announcement.courseName)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = announcement.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!announcement.publishedAtRaw.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = announcement.publishedAtRaw,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            AppleSubtleDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = announcement.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun UpcomingEventsAlertStrip(
    pendingTasksCount: Int,
    openForumsCount: Int,
    nextTask: AcademicTask?,
    onOpenTasks: () -> Unit
) {
    val summaryParts = buildList {
        if (pendingTasksCount > 0) add("$pendingTasksCount tarea(s) activa(s)")
        if (openForumsCount > 0) add("$openForumsCount foro(s) abierto(s)")
    }

    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
        onClick = onOpenTasks
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = UnAppIcons.Bell,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Alertas académicas próximas",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = nextTask?.let { "${summaryParts.joinToString(" · ")} — ${it.title}" }
                        ?: summaryParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = UnAppIcons.ChevronRight,
                contentDescription = "Abrir actividades",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    containerColor: Color,
    inkColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppleCard(
        modifier = modifier,
        containerColor = containerColor,
        borderColor = borderColor,
        onClick = onClick
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = inkColor.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        AnimatedContent(targetState = value, label = "metricCount") { count ->
            Text(
                text = count,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = inkColor,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = inkColor.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
