package sky.universidad.UnApp.presentation.courses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sky.universidad.UnApp.core.common.CourseColorPalette
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.core.network.UnapEndpoints
import sky.universidad.UnApp.domain.model.AcademicTask
import sky.universidad.UnApp.domain.model.Course
import sky.universidad.UnApp.domain.model.CourseFullDetail
import sky.universidad.UnApp.domain.model.CourseResourceItem
import sky.universidad.UnApp.domain.model.CourseResourceType
import sky.universidad.UnApp.domain.model.ForumReplyItem
import sky.universidad.UnApp.domain.model.ForumTopic
import sky.universidad.UnApp.domain.model.HomeworkDetailInfo
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.AppleCollapsibleSection
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleSectionContainer
import sky.universidad.UnApp.presentation.components.AppleSubtleDivider
import sky.universidad.UnApp.presentation.components.CourseDetailSkeleton
import sky.universidad.UnApp.presentation.components.DocumentOpenRequest
import sky.universidad.UnApp.presentation.components.EmptyStateView
import sky.universidad.UnApp.presentation.components.ErrorRetryBanner
import sky.universidad.UnApp.presentation.components.ForumDiscussionSheet
import sky.universidad.UnApp.presentation.components.ReadOnlyTaskDetailSheet
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.components.applePressable
import sky.universidad.UnApp.presentation.components.isUnapInternalUrl
import sky.universidad.UnApp.presentation.components.normalizeResourceUrl
import sky.universidad.UnApp.presentation.components.openExternalUri
import sky.universidad.UnApp.presentation.schedule.ScheduleSlotRowCard
import sky.universidad.UnApp.presentation.tasks.ReadOnlyForumCard
import sky.universidad.UnApp.presentation.tasks.ReadOnlyTaskCard
import sky.universidad.UnApp.ui.theme.LocalUnAppColors
import kotlin.math.roundToInt

enum class CourseSectionTab(val label: String) {
    UNITS("Unidades"),
    HOMEWORKS("Tareas"),
    FORUMS("Foros"),
    EXAMS("Exámenes"),
    READINGS("Lecturas"),
    QUALIFICATIONS("Calificaciones"),
    CALENDAR("Calendario"),
    CHAT("Chat"),
    GROUPS("Grupos"),
    CONFERENCES("Videoconf.")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    course: Course,
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    onLoadDetail: (Course, Boolean) -> Unit,
    onLoadHomeworkDetail: (AcademicTask, (HomeworkDetailInfo?) -> Unit) -> Unit,
    onLoadForumDiscussion: (ForumTopic, (String, List<ForumReplyItem>) -> Unit) -> Unit,
    onCreateNoteForCourse: (courseId: String, courseName: String) -> Unit,
    onOpenUrlInPortal: (url: String) -> Unit,
    onOpenDocument: (DocumentOpenRequest) -> Unit = { req -> onOpenUrlInPortal(req.url) },
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val courseColor = remember(course.colorHex) { CourseColorPalette.parseColor(course.colorHex) }
    val detail: CourseFullDetail? = state.courseDetailsCache[course.remoteId]
    val isLoading = state.loadingCourseIds.contains(course.remoteId)

    val courseTasks = remember(state.tasks, course.remoteId) {
        state.tasks
            .filter { it.courseId == course.remoteId }
            .sortedWith(
                compareBy<AcademicTask>(
                    { it.isEnded && !it.isSubmittedRemote },
                    { it.dueTimestamp ?: Long.MAX_VALUE }
                )
            )
    }
    val courseForums = remember(state.forums, course.remoteId) {
        state.forums.filter { it.courseId == course.remoteId }
    }
    val courseExams = remember(state.exams, course) {
        val normName = CourseColorPalette.normalizeCode(null, course.name)
        state.exams.filter {
            it.courseId == course.remoteId ||
                CourseColorPalette.normalizeCode(null, it.courseName) == normName
        }
    }
    val courseSchedule = remember(state.schedule, course) {
        val normName = CourseColorPalette.normalizeCode(null, course.name)
        state.schedule.filter {
            it.courseId == course.remoteId ||
                CourseColorPalette.normalizeCode(null, it.courseName) == normName
        }
    }

    var selectedTask by remember { mutableStateOf<AcademicTask?>(null) }
    var selectedForum by remember { mutableStateOf<ForumTopic?>(null) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { CourseSectionTab.entries.size })
    val coroutineScope = rememberCoroutineScope()

    // Continuous 1:1 gesture-driven collapsible header offset
    var headerFullHeightPx by remember { mutableFloatStateOf(0f) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }

    val headerNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val maxCollapse = headerFullHeightPx
                if (maxCollapse <= 0f) return Offset.Zero
                val delta = available.y
                if (delta < 0f && headerOffsetPx > -maxCollapse) {
                    val prev = headerOffsetPx
                    headerOffsetPx = (headerOffsetPx + delta).coerceIn(-maxCollapse, 0f)
                    val consumedY = headerOffsetPx - prev
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val maxCollapse = headerFullHeightPx
                if (maxCollapse <= 0f) return Offset.Zero
                val delta = available.y
                if (delta > 0f && headerOffsetPx < 0f) {
                    val prev = headerOffsetPx
                    headerOffsetPx = (headerOffsetPx + delta).coerceIn(-maxCollapse, 0f)
                    val consumedY = headerOffsetPx - prev
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(course.remoteId) {
        onLoadDetail(course, false)
    }

    val openResourceOrUrl: (String, String, Boolean, String) -> Unit = { title, url, isExt, ext ->
        val normalized = normalizeResourceUrl(url)
        if (isExt || !isUnapInternalUrl(normalized)) {
            if (!openExternalUri(context, normalized)) {
                onOpenUrlInPortal(normalized)
            }
        } else {
            onOpenDocument(
                DocumentOpenRequest(
                    title = title,
                    url = normalized,
                    isExternalLink = false,
                    fileExtension = ext
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .nestedScroll(headerNestedScroll)
            .padding(top = 8.dp)
    ) {
        // Persistent compact top navigation strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = UnAppIcons.ArrowBack,
                    contentDescription = "Volver a cursos",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        course.code.takeIf { it.isNotBlank() },
                        detail?.sectionLabel?.takeIf { it.isNotBlank() }
                            ?: course.sectionGroup.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        // Collapsible General Info Header Card (shrinks smoothly 1:1 on downward scroll)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val fullH = placeable.height.toFloat()
                    if (fullH > 0f) {
                        headerFullHeightPx = fullH
                    }
                    val clampedOffset = headerOffsetPx.coerceIn(-fullH, 0f)
                    val visibleH = (fullH + clampedOffset).roundToInt().coerceIn(0, placeable.height)
                    layout(placeable.width, visibleH) {
                        placeable.placeRelative(0, clampedOffset.roundToInt())
                    }
                }
                .graphicsLayer {
                    val progress = if (headerFullHeightPx > 0f) {
                        ((headerFullHeightPx + headerOffsetPx) / headerFullHeightPx).coerceIn(0f, 1f)
                    } else 1f
                    alpha = progress
                }
        ) {
            AppleCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                accentColor = courseColor,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
            ) {
                Text(
                    text = "Información general",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                val teacherName = detail?.teachers?.takeIf { it.isNotEmpty() }?.joinToString(" / ")
                    ?: course.teacher.ifBlank { "Docente asignado" }
                val sectionText = detail?.sectionLabel?.takeIf { it.isNotBlank() }
                    ?: course.sectionGroup.ifBlank { "Sección matriculada" }

                Text(
                    text = "Responsable de asignatura",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = DateFormatters.formatTitleCase(teacherName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ApplePillBadge(
                        text = "Sección: $sectionText",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (course.credits > 0) {
                        ApplePillBadge(
                            text = "${course.credits} créditos",
                            containerColor = courseColor.copy(alpha = 0.16f),
                            contentColor = courseColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val syllabusUrl = detail?.syllabusUrl
                    ?: "${UnapEndpoints.BASE_AULA_VIRTUAL}/web/course/syllabus/${course.sectionId}"
                Button(
                    onClick = {
                        onOpenDocument(
                            DocumentOpenRequest(
                                title = "Sílabo · ${course.code}",
                                url = syllabusUrl,
                                isExternalLink = false,
                                fileExtension = "pdf"
                            )
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(
                        imageVector = UnAppIcons.Document,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ver sílabo del curso", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Sticky horizontal pill tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CourseSectionTab.entries.forEachIndexed { idx, tab ->
                val selected = pagerState.currentPage == idx
                val countSuffix = when (tab) {
                    CourseSectionTab.UNITS -> detail?.units?.size?.let { if (it > 0) " ($it)" else "" }.orEmpty()
                    CourseSectionTab.HOMEWORKS -> if (courseTasks.isNotEmpty()) " (${courseTasks.size})" else ""
                    CourseSectionTab.FORUMS -> if (courseForums.isNotEmpty()) " (${courseForums.size})" else ""
                    CourseSectionTab.READINGS -> detail?.readings?.size?.let { if (it > 0) " ($it)" else "" }.orEmpty()
                    else -> ""
                }
                val shape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        )
                        .border(
                            0.8.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            shape
                        )
                        .applePressable {
                            coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${tab.label}$countSuffix",
                        style = MaterialTheme.typography.labelMedium,
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

        state.courseDetailError?.let { err ->
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                ErrorRetryBanner(message = err, onRetry = { onLoadDetail(course, true) })
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { onLoadDetail(course, true) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val section = CourseSectionTab.entries[page]
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 6.dp,
                        bottom = bottomContentPadding.calculateBottomPadding() + 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (section) {
                        CourseSectionTab.UNITS -> {
                            val units = detail?.units.orEmpty()
                            if (isLoading && detail == null) {
                                item {
                                    CourseDetailSkeleton()
                                }
                            } else if (units.isEmpty() && !isLoading) {
                                item {
                                    EmptyStateView(
                                        icon = UnAppIcons.Book,
                                        title = "Sin unidades"
                                    )
                                }
                            } else {
                                items(units, key = { it.id }) { unit ->
                                    AppleCollapsibleSection(
                                        title = unit.name,
                                        subtitle = null,
                                        countBadge = "${unit.topics.size}",
                                        accentColor = courseColor,
                                        initiallyExpanded = true
                                    ) {
                                        if (unit.topics.isEmpty()) {
                                            Text(
                                                text = "Sin temas publicados.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            unit.topics.forEach { topic ->
                                                AppleCard(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                                                ) {
                                                    Text(
                                                        text = topic.name,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (topic.description.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = topic.description,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    if (topic.resources.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        AppleSubtleDivider()
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            topic.resources.forEach { res ->
                                                                UnitResourceRow(
                                                                    resource = res,
                                                                    onClick = {
                                                                        when (res.type) {
                                                                            CourseResourceType.HOMEWORK -> {
                                                                                val matched = courseTasks.firstOrNull {
                                                                                    it.homeworkId == res.id ||
                                                                                        it.title.equals(res.title, ignoreCase = true)
                                                                                }
                                                                                if (matched != null) {
                                                                                    selectedTask = matched
                                                                                } else {
                                                                                    coroutineScope.launch {
                                                                                        pagerState.animateScrollToPage(CourseSectionTab.HOMEWORKS.ordinal)
                                                                                    }
                                                                                }
                                                                            }
                                                                            CourseResourceType.FORUM -> {
                                                                                val matched = courseForums.firstOrNull {
                                                                                    it.rawForumId == res.id ||
                                                                                        it.title.equals(res.title, ignoreCase = true)
                                                                                }
                                                                                if (matched != null) {
                                                                                    selectedForum = matched
                                                                                } else {
                                                                                    coroutineScope.launch {
                                                                                        pagerState.animateScrollToPage(CourseSectionTab.FORUMS.ordinal)
                                                                                    }
                                                                                }
                                                                            }
                                                                            CourseResourceType.EXAM -> {
                                                                                coroutineScope.launch {
                                                                                    pagerState.animateScrollToPage(CourseSectionTab.EXAMS.ordinal)
                                                                                }
                                                                            }
                                                                            CourseResourceType.READING -> {
                                                                                val url = res.url
                                                                                if (!url.isNullOrBlank()) {
                                                                                    openResourceOrUrl(res.title, url, false, "pdf")
                                                                                } else {
                                                                                    coroutineScope.launch {
                                                                                        pagerState.animateScrollToPage(CourseSectionTab.READINGS.ordinal)
                                                                                    }
                                                                                }
                                                                            }
                                                                            CourseResourceType.EXTERNAL_LINK -> {
                                                                                res.url?.takeIf { it.isNotBlank() }?.let { url ->
                                                                                    openResourceOrUrl(res.title, url, true, res.fileExtension)
                                                                                }
                                                                            }
                                                                            else -> {
                                                                                res.url?.takeIf { it.isNotBlank() }?.let { url ->
                                                                                    openResourceOrUrl(res.title, url, false, res.fileExtension)
                                                                                }
                                                                            }
                                                                        }
                                                                    }
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

                        CourseSectionTab.HOMEWORKS -> {
                            if (courseTasks.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = UnAppIcons.CheckSquare,
                                        title = "Sin tareas"
                                    )
                                }
                            } else {
                                val activeCourseTasks = courseTasks.filter {
                                    DateFormatters.isTaskVisibleInGlobalTab(it.dueTimestamp, it.isEnded)
                                }.sortedBy { it.dueTimestamp ?: Long.MAX_VALUE }
                                val pastCourseTasks = courseTasks.filter {
                                    !DateFormatters.isTaskVisibleInGlobalTab(it.dueTimestamp, it.isEnded)
                                }.sortedByDescending { it.dueTimestamp ?: 0L }

                                if (activeCourseTasks.isNotEmpty()) {
                                    item(key = "course_hw_active") {
                                        AppleCollapsibleSection(
                                            title = "Activas y próximas",
                                            subtitle = null,
                                            countBadge = "${activeCourseTasks.size}",
                                            accentColor = courseColor,
                                            initiallyExpanded = true
                                        ) {
                                            activeCourseTasks.forEach { task ->
                                                ReadOnlyTaskCard(
                                                    task = task,
                                                    courseColor = courseColor,
                                                    courseCode = course.code,
                                                    onCreateNote = null,
                                                    onClick = { selectedTask = task }
                                                )
                                            }
                                        }
                                    }
                                }
                                if (pastCourseTasks.isNotEmpty()) {
                                    item(key = "course_hw_past") {
                                        AppleCollapsibleSection(
                                            title = "Anteriores",
                                            subtitle = null,
                                            countBadge = "${pastCourseTasks.size}",
                                            accentColor = courseColor,
                                            initiallyExpanded = activeCourseTasks.isEmpty()
                                        ) {
                                            pastCourseTasks.forEach { task ->
                                                ReadOnlyTaskCard(
                                                    task = task,
                                                    courseColor = courseColor,
                                                    courseCode = course.code,
                                                    onCreateNote = null,
                                                    onClick = { selectedTask = task }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        CourseSectionTab.FORUMS -> {
                            val sortedForums = courseForums.sortedBy {
                                DateFormatters.forumPriorityScore(it.closeTimestamp, it.startDateRaw, it.isEnded)
                            }
                            if (sortedForums.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = UnAppIcons.Forum,
                                        title = "Sin foros"
                                    )
                                }
                            } else {
                                val activeForums = sortedForums.filter {
                                    DateFormatters.isForumActiveNow(it.closeTimestamp, it.startDateRaw, it.isEnded)
                                }
                                val closedForums = sortedForums.filter {
                                    !DateFormatters.isForumActiveNow(it.closeTimestamp, it.startDateRaw, it.isEnded)
                                }
                                if (activeForums.isNotEmpty()) {
                                    item(key = "course_forum_active") {
                                        AppleCollapsibleSection(
                                            title = "Foros activos",
                                            subtitle = null,
                                            countBadge = "${activeForums.size}",
                                            accentColor = courseColor,
                                            initiallyExpanded = true
                                        ) {
                                            activeForums.forEach { forum ->
                                                ReadOnlyForumCard(
                                                    forum = forum,
                                                    courseColor = courseColor,
                                                    onClick = { selectedForum = forum }
                                                )
                                            }
                                        }
                                    }
                                }
                                if (closedForums.isNotEmpty()) {
                                    item(key = "course_forum_closed") {
                                        AppleCollapsibleSection(
                                            title = "Foros anteriores",
                                            subtitle = null,
                                            countBadge = "${closedForums.size}",
                                            accentColor = courseColor,
                                            initiallyExpanded = activeForums.isEmpty()
                                        ) {
                                            closedForums.forEach { forum ->
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

                        CourseSectionTab.EXAMS -> {
                            val examQuals = detail?.qualifications
                                ?.filter { it.category == "Examen" }
                                .orEmpty()
                            if (courseExams.isEmpty() && examQuals.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = UnAppIcons.Award,
                                        title = "Sin exámenes registrados",
                                        subtitle = "Los cuestionarios y exámenes publicados aparecerán aquí."
                                    )
                                }
                            } else {
                                items(examQuals, key = { it.id }) { q ->
                                    AppleCard(modifier = Modifier.fillMaxWidth(), accentColor = courseColor) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ApplePillBadge(text = q.unit.ifBlank { "Evaluación" })
                                            ApplePillBadge(
                                                text = "Nota: ${q.score}",
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = q.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                items(courseExams, key = { it.remoteId }) { exam ->
                                    AppleCard(modifier = Modifier.fillMaxWidth(), accentColor = courseColor) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ApplePillBadge(text = exam.examType)
                                            if (!exam.grade.isNullOrBlank()) {
                                                ApplePillBadge(text = "Nota: ${exam.grade}")
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = exam.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val meta = listOfNotNull(
                                            exam.dateRaw?.takeIf { it.isNotBlank() },
                                            exam.classroom.takeIf { it.isNotBlank() }
                                        ).joinToString(" · ")
                                        if (meta.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = meta,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        CourseSectionTab.READINGS -> {
                            val readings = detail?.readings.orEmpty()
                            if (readings.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = UnAppIcons.Book,
                                        title = "Sin lecturas registradas",
                                        subtitle = "Las lecturas obligatorias o libros en PDF aparecerán aquí."
                                    )
                                }
                            } else {
                                items(readings, key = { it.id }) { reading ->
                                    AppleCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        accentColor = courseColor,
                                        onClick = reading.fileUrl?.let { url ->
                                            { openResourceOrUrl(reading.title, url, false, "pdf") }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ApplePillBadge(text = reading.type)
                                            if (reading.fileUrl != null) {
                                                Icon(
                                                    imageVector = UnAppIcons.ExternalLink,
                                                    contentDescription = "Abrir lectura",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = reading.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (reading.unit.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = reading.unit,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (reading.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = reading.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        CourseSectionTab.QUALIFICATIONS -> {
                            val quals = detail?.qualifications.orEmpty()
                            if (quals.isEmpty() && courseExams.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = UnAppIcons.Award,
                                        title = "Sin calificaciones aún",
                                        subtitle = "Las notas publicadas en Aula Virtual e Intranet se mostrarán aquí."
                                    )
                                }
                            } else {
                                items(quals, key = { it.id }) { item ->
                                    AppleCard(modifier = Modifier.fillMaxWidth(), accentColor = courseColor) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                ApplePillBadge(text = item.category)
                                                if (item.unit.isNotBlank()) {
                                                    ApplePillBadge(
                                                        text = item.unit,
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            ApplePillBadge(
                                                text = item.score,
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (item.status.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.status,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                items(courseExams.filter { !it.grade.isNullOrBlank() }, key = { "int_${it.remoteId}" }) { exam ->
                                    AppleCard(modifier = Modifier.fillMaxWidth(), accentColor = courseColor) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ApplePillBadge(text = exam.examType)
                                            ApplePillBadge(text = exam.grade.orEmpty())
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = exam.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        CourseSectionTab.CALENDAR -> {
                            val events = detail?.calendarEvents.orEmpty()
                            if (events.isEmpty() && courseSchedule.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = UnAppIcons.Calendar,
                                        title = "Sin eventos en calendario",
                                        subtitle = "Los horarios y fechas programadas del curso aparecerán aquí."
                                    )
                                }
                            } else {
                                if (courseSchedule.isNotEmpty()) {
                                    item {
                                        AppleSectionContainer(
                                            title = "Horario del curso",
                                            subtitle = "${courseSchedule.size} bloques"
                                        ) {
                                            courseSchedule.forEach { slot ->
                                                ApplePillBadge(text = DateFormatters.dayName(slot.dayOfWeek))
                                                ScheduleSlotRowCard(slot = slot, courses = state.courses)
                                            }
                                        }
                                    }
                                }
                                items(events, key = { it.id }) { ev ->
                                    AppleCard(modifier = Modifier.fillMaxWidth(), accentColor = courseColor) {
                                        Text(
                                            text = ev.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val timeMeta = listOfNotNull(
                                            ev.startRaw?.takeIf { it.isNotBlank() },
                                            ev.endRaw?.takeIf { it.isNotBlank() }
                                        ).joinToString(" – ")
                                        if (timeMeta.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = timeMeta,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (ev.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = ev.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        CourseSectionTab.CHAT -> {
                            val participants = detail?.participants.orEmpty()
                            item {
                                OutlinedButton(
                                    onClick = {
                                        onOpenUrlInPortal("${UnapEndpoints.BASE_AULA_VIRTUAL}/web/chat?s=${course.sectionId}")
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = UnAppIcons.ExternalLink,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Abrir chat web del curso")
                                }
                            }
                            if (participants.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = UnAppIcons.Users,
                                        title = "Lista de participantes vacía",
                                        subtitle = "Los docentes y compañeros del curso se listarán aquí."
                                    )
                                }
                            } else {
                                items(participants, key = { it.id }) { person ->
                                    AppleCard(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = DateFormatters.formatTitleCase(person.name),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            ApplePillBadge(
                                                text = person.role,
                                                containerColor = if (person.role == "Docente") {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                contentColor = if (person.role == "Docente") {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        CourseSectionTab.GROUPS -> {
                            val groups = detail?.groups.orEmpty()
                            if (groups.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = UnAppIcons.Users,
                                        title = "Sin grupos formados",
                                        subtitle = "Cuando el docente cree grupos de trabajo en Aula Virtual, los verás aquí."
                                    )
                                }
                            } else {
                                items(groups, key = { it.id }) { group ->
                                    AppleCard(modifier = Modifier.fillMaxWidth(), accentColor = courseColor) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = group.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (group.membersCount > 0) {
                                                ApplePillBadge(text = "${group.membersCount} integrantes")
                                            }
                                        }
                                        if (group.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = group.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        CourseSectionTab.CONFERENCES -> {
                            val confs = detail?.conferences.orEmpty()
                            item {
                                OutlinedButton(
                                    onClick = {
                                        onOpenUrlInPortal("${UnapEndpoints.BASE_AULA_VIRTUAL}/web/conference?s=${course.sectionId}")
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = UnAppIcons.Video,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Abrir sala de videoconferencias web")
                                }
                            }
                            if (confs.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = UnAppIcons.Video,
                                        title = "Sin videoconferencias registradas",
                                        subtitle = "Las sesiones virtuales programadas o grabadas aparecerán aquí."
                                    )
                                }
                            } else {
                                items(confs, key = { it.id }) { conf ->
                                    AppleCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        accentColor = courseColor,
                                        onClick = conf.url?.takeIf { it.isNotBlank() }?.let { url ->
                                            { openResourceOrUrl(conf.title, url, !url.contains("unap.edu.pe"), "") }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = conf.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (conf.status.isNotBlank()) {
                                                ApplePillBadge(text = conf.status)
                                            }
                                        }
                                        if (!conf.dateRaw.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = conf.dateRaw,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

    selectedTask?.let { task ->
        ReadOnlyTaskDetailSheet(
            task = task,
            courseColor = courseColor,
            courseCode = course.code,
            onLoadDetail = onLoadHomeworkDetail,
            onCreateNote = {
                selectedTask = null
                onCreateNoteForCourse(course.remoteId, course.name)
            },
            onOpenUrl = { url -> openResourceOrUrl(task.title, url, !isUnapInternalUrl(url), "") },
            onDismiss = { selectedTask = null }
        )
    }

    selectedForum?.let { forum ->
        ForumDiscussionSheet(
            forum = forum,
            courseColor = courseColor,
            onLoadDiscussion = onLoadForumDiscussion,
            onDismiss = { selectedForum = null }
        )
    }
}

@Composable
private fun UnitResourceRow(
    resource: CourseResourceItem,
    onClick: () -> Unit
) {
    val extended = LocalUnAppColors.current
    val (icon, tint, bg) = when (resource.type) {
        CourseResourceType.DOCUMENT -> Triple(
            UnAppIcons.Document,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        )
        CourseResourceType.EXTERNAL_LINK -> Triple(
            UnAppIcons.ExternalLink,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        )
        CourseResourceType.HOMEWORK -> Triple(
            UnAppIcons.CheckSquare,
            extended.success,
            extended.successContainer.copy(alpha = 0.65f)
        )
        CourseResourceType.FORUM -> Triple(
            UnAppIcons.Forum,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
        )
        CourseResourceType.EXAM -> Triple(
            UnAppIcons.Award,
            extended.warning,
            extended.warningContainer.copy(alpha = 0.65f)
        )
        CourseResourceType.READING -> Triple(
            UnAppIcons.Book,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
        CourseResourceType.VIRTUAL_CLASS -> Triple(
            UnAppIcons.Video,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .applePressable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(17.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = resource.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = resource.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (!resource.url.isNullOrBlank()) UnAppIcons.ExternalLink else UnAppIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}
