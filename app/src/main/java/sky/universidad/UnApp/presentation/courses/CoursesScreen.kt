package sky.universidad.UnApp.presentation.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.core.common.CourseColorPalette
import sky.universidad.UnApp.domain.model.Course
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleStickyCurvedHeader
import sky.universidad.UnApp.presentation.components.CoursesListSkeleton
import sky.universidad.UnApp.presentation.components.EmptyStateView
import sky.universidad.UnApp.presentation.components.OfflineStatusBanner
import sky.universidad.UnApp.presentation.components.UnAppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    onSync: () -> Unit,
    onEnsureLoaded: () -> Unit = {},
    onToggleTask: (String, Boolean) -> Unit = { _, _ -> },
    onCreateNoteForCourse: (courseId: String, courseName: String) -> Unit = { _, _ -> },
    onOpenCourse: (Course) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onEnsureLoaded()
    }

    val filteredCourses = remember(state.courses, query) {
        val q = query.trim()
        if (q.isEmpty()) state.courses
        else state.courses.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.teacher.contains(q, ignoreCase = true) ||
                it.code.contains(q, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleStickyCurvedHeader(
            title = "Cursos",
            subtitle = "${state.courses.size} cursos",
            bottomContent = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar curso, código o docente") },
                    leadingIcon = {
                        Icon(
                            imageVector = UnAppIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )

        PullToRefreshBox(
            isRefreshing = state.isSyncing || state.isLoadingCourses,
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

                if ((state.isSyncing || state.isLoadingCourses) && state.courses.isEmpty()) {
                    item {
                        CoursesListSkeleton(count = 4)
                    }
                } else if (filteredCourses.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = UnAppIcons.Book,
                            title = if (query.isBlank()) "Sin cursos" else "Sin resultados"
                        )
                    }
                } else {
                    items(filteredCourses, key = { it.remoteId }) { course ->
                        CourseItemCard(
                            course = course,
                            onClick = { onOpenCourse(course) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseItemCard(
    course: Course,
    onClick: () -> Unit
) {
    val courseColor = CourseColorPalette.parseColor(course.colorHex)

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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ApplePillBadge(
                    text = course.code,
                    containerColor = courseColor.copy(alpha = 0.16f),
                    contentColor = courseColor
                )
                if (course.sectionGroup.isNotBlank()) {
                    ApplePillBadge(
                        text = course.sectionGroup,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (course.pendingTasksCount > 0) {
                ApplePillBadge(
                    text = "${course.pendingTasksCount} activa(s)"
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = course.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = course.teacher.ifBlank { "Docente asignado" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = UnAppIcons.ChevronRight,
                contentDescription = "Abrir curso",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
