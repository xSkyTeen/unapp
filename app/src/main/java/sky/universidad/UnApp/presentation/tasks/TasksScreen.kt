package sky.universidad.UnApp.presentation.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.core.common.CourseColorPalette
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.domain.model.AcademicTask
import sky.universidad.UnApp.domain.model.Announcement
import sky.universidad.UnApp.domain.model.ForumReplyItem
import sky.universidad.UnApp.domain.model.ForumTopic
import sky.universidad.UnApp.domain.model.HomeworkDetailInfo
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleBottomSheet
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.AppleCollapsibleSection
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleSegmentedControl
import sky.universidad.UnApp.presentation.components.AppleStickyCurvedHeader
import sky.universidad.UnApp.presentation.components.AppleSubtleDivider
import sky.universidad.UnApp.presentation.components.EmptyStateView
import sky.universidad.UnApp.presentation.components.ForumDiscussionSheet
import sky.universidad.UnApp.presentation.components.ForumsListSkeleton
import sky.universidad.UnApp.presentation.components.OfflineStatusBanner
import sky.universidad.UnApp.presentation.components.ReadOnlyTaskDetailSheet
import sky.universidad.UnApp.presentation.components.TasksSectionSkeleton
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.components.applePressable
import sky.universidad.UnApp.presentation.components.taskStatusPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    onSync: () -> Unit,
    onEnsureForumsLoaded: () -> Unit = {},
    onToggleTask: (String, Boolean) -> Unit = { _, _ -> },
    onMarkSeen: (String) -> Unit,
    onCreateNoteForCourse: (courseId: String, courseName: String) -> Unit,
    onLoadHomeworkDetail: (AcademicTask, (HomeworkDetailInfo?) -> Unit) -> Unit = { _, cb -> cb(null) },
    onLoadForumDiscussion: (ForumTopic, (String, List<ForumReplyItem>) -> Unit) -> Unit = { f, cb ->
        cb(f.description, emptyList())
    },
    onOpenUrl: (String) -> Unit = {}
) {
    var segmentIndex by remember { mutableIntStateOf(0) } // 0: Tareas, 1: Foros, 2: Avisos
    var selectedCourseId by remember { mutableStateOf<String?>(null) }
    var selectedTask by remember { mutableStateOf<AcademicTask?>(null) }
    var selectedForum by remember { mutableStateOf<ForumTopic?>(null) }
    var selectedAnnouncement by remember { mutableStateOf<Announcement?>(null) }

    LaunchedEffect(segmentIndex) {
        if (segmentIndex > 0) {
            onEnsureForumsLoaded()
        }
    }

    val globalVisibleTasks = remember(state.tasks, selectedCourseId) {
        state.tasks
            .filter { DateFormatters.isTaskVisibleInGlobalTab(it.dueTimestamp, it.isEnded) }
            .filter { selectedCourseId == null || it.courseId == selectedCourseId }
            .sortedWith(
                compareBy<AcademicTask>(
                    { it.isSubmittedRemote },
                    { it.dueTimestamp ?: Long.MAX_VALUE }
                )
            )
    }

    val todayTomorrowTasks = remember(globalVisibleTasks) {
        globalVisibleTasks.filter { DateFormatters.isTaskDueTodayOrTomorrow(it.dueTimestamp) }
    }
    val remainingTasks = remember(globalVisibleTasks) {
        globalVisibleTasks.filter { !DateFormatters.isTaskDueTodayOrTomorrow(it.dueTimestamp) }
    }

    val sortedForums = remember(state.forums, selectedCourseId) {
        state.forums
            .filter { selectedCourseId == null || it.courseId == selectedCourseId }
            .sortedBy { DateFormatters.forumPriorityScore(it.closeTimestamp, it.startDateRaw, it.isEnded) }
    }
    val activeForums = remember(sortedForums) {
        sortedForums.filter { DateFormatters.isForumActiveNow(it.closeTimestamp, it.startDateRaw, it.isEnded) }
    }
    val archivedForums = remember(sortedForums) {
        sortedForums.filter { !DateFormatters.isForumActiveNow(it.closeTimestamp, it.startDateRaw, it.isEnded) }
    }

    val filteredAnnouncements = remember(state.announcements, selectedCourseId) {
        state.announcements
            .filter { selectedCourseId == null || it.courseId == selectedCourseId }
            .sortedByDescending { it.publishedTimestamp ?: 0L }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleStickyCurvedHeader(
            title = "Actividades",
            subtitle = null,
            bottomContent = {
                AppleSegmentedControl(
                    options = listOf(
                        "Tareas (${globalVisibleTasks.size})",
                        "Foros (${activeForums.size})",
                        "Avisos (${filteredAnnouncements.size})"
                    ),
                    selectedIndex = segmentIndex,
                    onSelect = { segmentIndex = it }
                )
                if (state.courses.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChipPill(
                            label = "Todos",
                            selected = selectedCourseId == null,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = { selectedCourseId = null }
                        )
                        state.courses.forEach { course ->
                            val cColor = CourseColorPalette.parseColor(course.colorHex)
                            FilterChipPill(
                                label = course.code.ifBlank { course.name.take(10) },
                                selected = selectedCourseId == course.remoteId,
                                accentColor = cColor,
                                onClick = {
                                    selectedCourseId = if (selectedCourseId == course.remoteId) null else course.remoteId
                                }
                            )
                        }
                    }
                }
            }
        )

        PullToRefreshBox(
            isRefreshing = if (segmentIndex == 0) state.isSyncing else state.isLoadingForums,
            onRefresh = {
                if (segmentIndex > 0) onEnsureForumsLoaded() else onSync()
            },
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.isOnline) {
                    item {
                        OfflineStatusBanner(
                            isOnline = state.isOnline,
                            lastSyncAt = state.lastSyncAt
                        )
                    }
                }

                when (segmentIndex) {
                    0 -> {
                        if (state.isSyncing && state.tasks.isEmpty()) {
                            item {
                                TasksSectionSkeleton(count = 3)
                            }
                        } else if (globalVisibleTasks.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = UnAppIcons.CheckSquare,
                                    title = "Sin tareas pendientes"
                                )
                            }
                        } else {
                            if (todayTomorrowTasks.isNotEmpty()) {
                                item(key = "tasks_today_tomorrow") {
                                    AppleCollapsibleSection(
                                        title = "Para hoy y mañana",
                                        subtitle = null,
                                        countBadge = "${todayTomorrowTasks.size}",
                                        initiallyExpanded = true
                                    ) {
                                        todayTomorrowTasks.forEach { task ->
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
                                                onClick = {
                                                    if (task.isNew) onMarkSeen(task.remoteId)
                                                    selectedTask = task
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (remainingTasks.isNotEmpty()) {
                                item(key = "tasks_all_active") {
                                    AppleCollapsibleSection(
                                        title = "Todas las tareas",
                                        subtitle = null,
                                        countBadge = "${remainingTasks.size}",
                                        initiallyExpanded = true
                                    ) {
                                        remainingTasks.forEach { task ->
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
                                                onClick = {
                                                    if (task.isNew) onMarkSeen(task.remoteId)
                                                    selectedTask = task
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (state.isLoadingForums && sortedForums.isEmpty()) {
                            item {
                                ForumsListSkeleton(count = 3)
                            }
                        } else if (sortedForums.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = UnAppIcons.Forum,
                                    title = "Sin foros"
                                )
                            }
                        } else {
                            if (activeForums.isNotEmpty()) {
                                item(key = "forums_active") {
                                    AppleCollapsibleSection(
                                        title = "Foros activos",
                                        subtitle = null,
                                        countBadge = "${activeForums.size}",
                                        initiallyExpanded = true
                                    ) {
                                        activeForums.forEach { forum ->
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

                            if (archivedForums.isNotEmpty()) {
                                item(key = "forums_archived") {
                                    AppleCollapsibleSection(
                                        title = "Foros anteriores",
                                        subtitle = null,
                                        countBadge = "${archivedForums.size}",
                                        initiallyExpanded = false
                                    ) {
                                        archivedForums.forEach { forum ->
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

                    else -> {
                        if (state.isLoadingForums && filteredAnnouncements.isEmpty()) {
                            item {
                                ForumsListSkeleton(count = 3)
                            }
                        } else if (filteredAnnouncements.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = UnAppIcons.Bell,
                                    title = "Sin avisos"
                                )
                            }
                        } else {
                            items(filteredAnnouncements, key = { it.remoteId }) { ann ->
                                val courseColor = CourseColorPalette.resolveCourseColor(
                                    courseId = ann.courseId,
                                    courseName = ann.courseName,
                                    courses = state.courses
                                )
                                AppleCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = courseColor,
                                    onClick = { selectedAnnouncement = ann }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ApplePillBadge(
                                            text = ann.courseName,
                                            containerColor = courseColor.copy(alpha = 0.16f),
                                            contentColor = courseColor
                                        )
                                        if (!ann.publishedAtRaw.isNullOrBlank()) {
                                            Text(
                                                text = DateFormatters.formatRelativeAcademicDate(
                                                    ann.publishedAtRaw,
                                                    ann.publishedTimestamp
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = ann.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (ann.content.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = ann.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
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

    selectedAnnouncement?.let { ann ->
        AppleBottomSheet(onDismiss = { selectedAnnouncement = null }) {
            ApplePillBadge(text = ann.courseName)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = ann.title, style = MaterialTheme.typography.headlineSmall)
            if (!ann.publishedAtRaw.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ann.publishedAtRaw,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            AppleSubtleDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = ann.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) accentColor.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (selected) 1.2.dp else 0.8.dp,
                color = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .applePressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(accentColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ReadOnlyTaskCard(
    task: AcademicTask,
    courseColor: Color,
    courseCode: String?,
    onCreateNote: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val status = DateFormatters.resolveTaskVisualStatus(
        dueTimestamp = task.dueTimestamp,
        isSubmitted = task.isSubmittedRemote,
        isEnded = task.isEnded
    )
    val palette = taskStatusPalette(status)

    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = courseColor,
        containerColor = palette.tintBackground,
        borderColor = palette.borderColor,
        onClick = onClick
    ) {
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
                if (!courseCode.isNullOrBlank()) {
                    ApplePillBadge(
                        text = courseCode,
                        containerColor = courseColor.copy(alpha = 0.16f),
                        contentColor = courseColor
                    )
                }
                Text(
                    text = task.courseName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            if (task.isNew) {
                ApplePillBadge(
                    text = "Nueva",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            ApplePillBadge(
                text = DateFormatters.statusLabel(status),
                containerColor = palette.container,
                contentColor = palette.content
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = task.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (task.description.isNotBlank() && task.description != "Sin descripción adicional.") {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val formattedDue = remember(task.dueDateRaw, task.dueTimestamp) {
                task.dueDateRaw?.takeIf { it.isNotBlank() }?.let { raw ->
                    DateFormatters.formatRelativeAcademicDate(raw, task.dueTimestamp)
                }
            }
            val bottomMeta = listOfNotNull(
                task.unit.takeIf { it.isNotBlank() },
                formattedDue
            ).joinToString(" · ")
            if (bottomMeta.isNotBlank()) {
                Text(
                    text = bottomMeta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ReadOnlyForumCard(
    forum: ForumTopic,
    courseColor: Color,
    onClick: () -> Unit
) {
    val isActive = DateFormatters.isForumActiveNow(
        closeTimestamp = forum.closeTimestamp,
        startDateRaw = forum.startDateRaw,
        isEnded = forum.isEnded
    )
    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = courseColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ApplePillBadge(
                text = forum.courseName,
                containerColor = courseColor.copy(alpha = 0.16f),
                contentColor = courseColor
            )
            ApplePillBadge(
                text = if (!isActive) "Cerrado" else "Abierto",
                containerColor = if (!isActive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (!isActive) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = forum.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (forum.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = forum.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = listOfNotNull(
                forum.unitName.takeIf { it.isNotBlank() },
                forum.closeDateRaw?.takeIf { it.isNotBlank() }?.let {
                    "Cierre: ${DateFormatters.formatRelativeAcademicDate(it, forum.closeTimestamp)}"
                } ?: forum.startDateRaw?.takeIf { it.isNotBlank() }?.let {
                    "Publicado: ${DateFormatters.formatRelativeAcademicDate(it)}"
                },
                "${forum.answersCount} respuestas"
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
