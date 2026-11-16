package sky.universidad.UnApp.presentation.forums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.core.common.CourseColorPalette
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.domain.model.Announcement
import sky.universidad.UnApp.domain.model.ForumReplyItem
import sky.universidad.UnApp.domain.model.ForumTopic
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleBottomSheet
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.AppleCollapsibleSection
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleSegmentedControl
import sky.universidad.UnApp.presentation.components.AppleStickyCurvedHeader
import sky.universidad.UnApp.presentation.components.EmptyStateView
import sky.universidad.UnApp.presentation.components.ForumDiscussionSheet
import sky.universidad.UnApp.presentation.components.ForumsListSkeleton
import sky.universidad.UnApp.presentation.components.OfflineStatusBanner
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.tasks.ReadOnlyForumCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumsAndNoticesScreen(
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    initialTab: Int = 0,
    onEnsureForumsLoaded: (forceRefresh: Boolean) -> Unit = {},
    onLoadForumDiscussion: (ForumTopic, (String, List<ForumReplyItem>) -> Unit) -> Unit = { f, cb ->
        cb(f.description, emptyList())
    },
    onBack: () -> Unit
) {
    var tabIndex by remember(initialTab) { mutableIntStateOf(initialTab) }
    var selectedForum by remember { mutableStateOf<ForumTopic?>(null) }
    var selectedAnnouncement by remember { mutableStateOf<Announcement?>(null) }

    LaunchedEffect(Unit) {
        onEnsureForumsLoaded(false)
    }

    val sortedForums = remember(state.forums) {
        state.forums.sortedBy {
            DateFormatters.forumPriorityScore(it.closeTimestamp, it.startDateRaw, it.isEnded)
        }
    }
    val activeForums = remember(sortedForums) {
        sortedForums.filter { DateFormatters.isForumActiveNow(it.closeTimestamp, it.startDateRaw, it.isEnded) }
    }
    val archivedForums = remember(sortedForums) {
        sortedForums.filter { !DateFormatters.isForumActiveNow(it.closeTimestamp, it.startDateRaw, it.isEnded) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleStickyCurvedHeader(
            title = "Foros y anuncios",
            subtitle = null,
            onBack = onBack,
            bottomContent = {
                AppleSegmentedControl(
                    options = listOf("Foros (${activeForums.size})", "Anuncios (${state.announcements.size})"),
                    selectedIndex = tabIndex,
                    onSelect = { tabIndex = it }
                )
            }
        )

        PullToRefreshBox(
            isRefreshing = state.isLoadingForums || state.isSyncing,
            onRefresh = { onEnsureForumsLoaded(true) },
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
                    bottom = bottomContentPadding.calculateBottomPadding() + 24.dp
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

                if (tabIndex == 0) {
                    if (state.isLoadingForums && sortedForums.isEmpty()) {
                        item { ForumsListSkeleton(count = 3) }
                    } else if (sortedForums.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = UnAppIcons.Forum,
                                title = "Sin foros"
                            )
                        }
                    } else {
                        if (activeForums.isNotEmpty()) {
                            item(key = "fn_active_forums") {
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
                            item(key = "fn_archived_forums") {
                                AppleCollapsibleSection(
                                    title = "Foros anteriores",
                                    subtitle = null,
                                    countBadge = "${archivedForums.size}",
                                    initiallyExpanded = activeForums.isEmpty()
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
                } else {
                    val orderedAnn = state.announcements.sortedByDescending { it.publishedTimestamp ?: 0L }
                    if (state.isLoadingForums && orderedAnn.isEmpty()) {
                        item { ForumsListSkeleton(count = 3) }
                    } else if (orderedAnn.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = UnAppIcons.Bell,
                                title = "Sin anuncios recientes",
                                subtitle = "Los avisos publicados en Aula Virtual e Intranet aparecerán aquí."
                            )
                        }
                    } else {
                        items(orderedAnn, key = { it.remoteId }) { ann ->
                            AppleCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedAnnouncement = ann }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ApplePillBadge(text = ann.courseName)
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
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = ann.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
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
            Text(
                text = ann.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!ann.publishedAtRaw.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ann.publishedAtRaw,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = ann.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
